package com.inigmasgames.hywind.readypath;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import jdk.jfr.*;

/** Opt-in observations only: never admits players, schedules work, or changes game state.
 * Wall minus thread CPU is NOT a measured lock/I/O wait. Use overlapping JFR wait events.
 * Unknown numeric measurements use -1; client J0/J5 are deliberately not synthesized. */
public final class ReadyPathProbe {
    public static final boolean ENABLED = Boolean.getBoolean("hywind.readypath.enabled");
    private static final String RUN = System.getProperty("hywind.readypath.run", UUID.randomUUID().toString());
    private static final ThreadMXBean CPU = ENABLED ? ManagementFactory.getThreadMXBean() : null;
    private static final AtomicLong IDS = new AtomicLong();
    private static final AtomicLong EMITTED = new AtomicLong();
    private static final ThreadLocal<Span> CURRENT = new ThreadLocal<>();
    private static final ConcurrentHashMap<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Span NOOP = new Span();
    private static volatile boolean bootObserved;
    private ReadyPathProbe() {}

    /** Bounded read-only operational status; native observations never imply gameplay readiness. */
    public static java.util.Map<String, Object> inspect(UUID player) {
        var session = player == null ? null : SESSIONS.get(player);
        return java.util.Map.of(
                "enabled", ENABLED,
                "run", ENABLED ? RUN : "UNKNOWN",
                "serverBootObserved", ENABLED ? Boolean.toString(bootObserved) : "UNKNOWN",
                "session", session == null ? "UNKNOWN" : session.id,
                "nativeReadyObserved", session == null ? "UNKNOWN" : Boolean.toString(session.readyAt != 0),
                "retainedSessions", ENABLED ? SESSIONS.size() : "UNKNOWN",
                "attemptedEvents", ENABLED ? EMITTED.get() : "UNKNOWN",
                "eventCap", 200_000,
                "clientClickToPlayMs", "UNKNOWN",
                "fullGameplayReady", "UNKNOWN");
    }

    private static final class Session {
        final String id = UUID.randomUUID().toString();
        final long start = System.nanoTime();
        final String cohort = bootObserved ? "ALREADY_RUNNING_SERVER" : "BOOT_OVERLAP";
        volatile long readyAt, nextSample;
        long ticks, maxTick, stalls;
        boolean firstTick = true;
    }

    @Name("hywind.ReadyPath") @Label("Hywind ReadyPath") @Category("Hywind") @StackTrace(false)
    public static final class Observation extends Event {
        public String run, session, player, phase, cohort, scope;
        public long spanId, parentId, wallNanos, selfWallNanos, cpuNanos, queueNanos;
        public long tickCount = -1, maxTickNanos = -1, stalls50ms = -1;
        public long requestedCommonAssets = -1;
        public String outcome;
    }

    public static Span span(String phase) { return span(phase, null); }
    public static Span span(String phase, UUID player) {
        if (!ENABLED) return NOOP;
        Span parent = CURRENT.get();
        return new Span(phase, player, parent, parent == null ? 0 : parent.event.spanId, -1);
    }
    /** Captures an asynchronous dependency and submission time without crossing thread ownership. */
    public record Ticket(long parent, long submitted) {}
    public static Ticket queued() {
        if (!ENABLED) return null;
        Span p = CURRENT.get();
        return new Ticket(p == null ? 0 : p.event.spanId, System.nanoTime());
    }
    public static Span execute(String phase, UUID player, Ticket ticket) {
        if (!ENABLED) return NOOP;
        return new Span(phase, player, CURRENT.get(), ticket == null ? 0 : ticket.parent,
                ticket == null ? -1 : System.nanoTime() - ticket.submitted);
    }
    public static void mark(String phase, UUID player) { try (var ignored = span(phase, player)) {} }
    public static void requestedAssets(UUID player, long count) {
        if (!ENABLED) return;
        try (var span = span("NATIVE_COMMON_ASSETS_REQUEST_OBSERVED_NOT_TRANSFER_COMPLETE", player)) {
            span.event.requestedCommonAssets = count;
        }
    }
    public static void boot() { if (ENABLED) { bootObserved = true; mark("SERVER_BOOT_OBSERVED_NOT_CLIENT_READY", null); } }
    public static synchronized void connect(UUID player) {
        if (!ENABLED) return;
        SESSIONS.entrySet().removeIf(e -> System.nanoTime() - e.getValue().start > 600_000_000_000L);
        if (SESSIONS.size() >= 256 && !SESSIONS.containsKey(player)) { mark("SESSION_CAPACITY_EXCEEDED", null); return; }
        SESSIONS.put(player, new Session());
        mark("J1_PLAYER_SETUP_CONNECT_POST_AUTH", player);
    }
    public static void ready(UUID player) {
        if (!ENABLED) return;
        var session = SESSIONS.get(player);
        if (session != null) {
            synchronized (session) {
                session.readyAt = System.nanoTime(); session.nextSample = session.readyAt + 1_000_000_000L;
                session.firstTick = true; session.ticks = session.maxTick = session.stalls = 0;
            }
        }
        mark("J3_NATIVE_READY_OBSERVED_NOT_J4", player);
    }
    public static void disconnect(UUID player) {
        if (!ENABLED) return;
        mark("DISCONNECT_OR_CANCEL", player); SESSIONS.remove(player);
    }
    /** Called on the owner tick. One aggregate per second for 60 seconds, not per-frame logging. */
    public static void observeTick(UUID player, long previousWorldTickNanos) {
        if (!ENABLED) return;
        var s = SESSIONS.get(player);
        if (s == null || s.readyAt == 0 || s.nextSample == Long.MAX_VALUE) return;
        synchronized (s) {
            // The first previous-world duration can predate entry; do not claim it as post-entry.
            if (s.firstTick) { s.firstTick = false; return; }
            s.ticks++; s.maxTick = Math.max(s.maxTick, previousWorldTickNanos);
            if (previousWorldTickNanos >= 50_000_000L) s.stalls++;
            long now = System.nanoTime();
            if (now < s.nextSample) return;
            boolean done = now - s.readyAt >= 60_000_000_000L;
            try (var span = span(done ? "ENTRY_60S_SERVER_WINDOW_END_NOT_J6_PROOF" : "ENTRY_WORLD_TICKS", player)) {
                span.event.tickCount = s.ticks; span.event.maxTickNanos = s.maxTick; span.event.stalls50ms = s.stalls;
            }
            s.ticks = s.maxTick = s.stalls = 0;
            s.nextSample = done ? Long.MAX_VALUE : now + 1_000_000_000L;
        }
    }
    private static long cpu() { return CPU.isCurrentThreadCpuTimeSupported() && CPU.isThreadCpuTimeEnabled() ? CPU.getCurrentThreadCpuTime() : -1; }
    public static final class Span implements AutoCloseable {
        private final Observation event;
        private final Span parent;
        private final long start, cpuStart;
        private long children;
        private boolean closed;
        private Span() { event = null; parent = null; start = cpuStart = 0; }
        private Span(String phase, UUID player, Span parent, long parentId, long queue) {
            this.parent = parent;
            event = new Observation(); event.run = RUN; event.phase = phase;
            event.player = player == null ? (parent == null ? "" : parent.event.player) : player.toString();
            var session = player == null ? null : SESSIONS.get(player);
            event.session = session == null ? (parent == null ? "UNKNOWN" : parent.event.session) : session.id;
            event.scope = player == null && parent == null ? "SHARED" : player == null ? parent.event.scope : "PLAYER";
            event.cohort = session == null ? (parent == null ? (bootObserved ? "POST_BOOT" : "BOOT") : parent.event.cohort) : session.cohort;
            event.spanId = IDS.incrementAndGet(); event.parentId = parentId; event.queueNanos = queue;
            event.outcome = "OBSERVED_EXIT_NOT_SUCCESS_ASSERTION";
            start = System.nanoTime(); cpuStart = cpu(); event.begin(); CURRENT.set(this);
        }
        @Override public void close() {
            if (event == null || closed) return;
            closed = true; event.end();
            event.wallNanos = System.nanoTime() - start;
            event.selfWallNanos = Math.max(0, event.wallNanos - children);
            long end = cpu(); event.cpuNanos = cpuStart < 0 || end < 0 ? -1 : Math.max(0, end - cpuStart);
            if (parent != null) parent.children += event.wallNanos;
            CURRENT.set(parent);
            long count = EMITTED.incrementAndGet();
            if (count <= 200_000) event.commit();
            else if (count == 200_001) { event.phase = "EVENT_CAP_REACHED_COUNTS_INCOMPLETE"; event.commit(); }
        }
    }
}
