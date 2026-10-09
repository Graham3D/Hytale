package com.inigmasgames.hytalerpg.ui.trace;

import com.inigmasgames.hytalerpg.diagnostics.BoundedTraceWriter;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Supplier;

/** Opt-in, per-player, bounded action trace. No work beyond a map lookup when off. */
public final class UiInteractionTrace implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(UiInteractionTrace.class.getName());
    private static final int MAX_EVENTS = 2500;
    private static final long MAX_AGE_NANOS = 10L * 60 * 1_000_000_000L;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter MILLIS = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    private static volatile UiInteractionTrace installed;

    private final Path directory;
    private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final ScheduledThreadPoolExecutor expiry = new ScheduledThreadPoolExecutor(1, task -> {
        var thread = new Thread(task, "ui-interaction-trace-expiry");
        thread.setDaemon(true);
        return thread;
    });

    public UiInteractionTrace(Path directory) {
        this.directory = directory;
        expiry.setRemoveOnCancelPolicy(true);
    }
    public static void install(UiInteractionTrace trace) { installed = trace; }
    public static UiInteractionTrace current() { return installed; }
    public static boolean active(UUID player) {
        var trace = installed;
        return trace != null && trace.sessions.containsKey(player);
    }
    public static Action beginIfActive(UUID player, String component, String elementId,
                                       String action, Supplier<Map<String, ?>> details) {
        var trace = installed;
        if (trace == null || !trace.sessions.containsKey(player)) return Action.OFF;
        try { return trace.begin(player, component, elementId, action, details.get()); }
        catch (RuntimeException diagnosticFailure) {
            LOGGER.log(Level.WARNING, "UI interaction trace could not observe an action", diagnosticFailure);
            return Action.OFF;
        }
    }

    public String on(UUID player) {
        off(player);
        var id = UUID.randomUUID();
        var path = directory.resolve("ui-trace-" + STAMP.format(Instant.now()) + "-" + id.toString().substring(0, 8) + ".jsonl");
        var session = new Session(player, id, path);
        sessions.put(player, session);
        session.write("SESSION_START", "", Map.of("limitEvents", MAX_EVENTS, "limitMinutes", 10));
        session.timeout = expiry.schedule(() -> {
            if (sessions.remove(player, session)) {
                session.limit("TIME_LIMIT");
                session.close();
            }
        }, 10, TimeUnit.MINUTES);
        return "UI trace ON; session " + id + "; " + path;
    }

    public String off(UUID player) {
        var session = sessions.remove(player);
        if (session == null) return "UI trace is off.";
        session.write("SESSION_END", "", Map.of("recorded", session.count.get()));
        session.close();
        return "UI trace OFF; " + session.path;
    }

    public String mark(UUID player, String label) {
        var session = sessions.get(player);
        if (session == null) return "UI trace is off; use /rpg uitrace on first.";
        session.write("MARK", "", Map.of("label", limited(label == null ? "" : label, 64)));
        return "UI trace mark saved.";
    }

    public void disconnect(UUID player) { off(player); }

    public Action begin(UUID player, String component, String elementId, String action, Map<String, ?> details) {
        var session = sessions.get(player);
        if (session == null) return Action.OFF;
        String correlation = UUID.randomUUID().toString().substring(0, 8);
        var base = new LinkedHashMap<String, Object>();
        base.put("component", limited(component, 64));
        base.put("elementId", limited(elementId, 128));
        base.put("action", limited(action, 64));
        add(base, details);
        session.write("INPUT_RECEIVED", correlation, base);
        session.write("TARGET_RESOLVED", correlation, Map.of("elementId", base.get("elementId")));
        session.write("HANDLER_ENTER", correlation, Map.of("handler", base.get("component")));
        return new Action(session, correlation, component, elementId, action);
    }

    public void event(UUID player, String event, String correlation, Map<String, ?> details) {
        var session = sessions.get(player);
        if (session != null) try { session.write(event, correlation == null ? "" : correlation, details); }
        catch (RuntimeException error) { LOGGER.log(Level.WARNING, "UI trace observation lost", error); }
    }

    public static final class Action {
        private static final Action OFF = new Action(null, "", "", "", "");
        private final Session session;
        private final String correlation, component, element, action;
        private final AtomicBoolean completed = new AtomicBoolean();
        private Action(Session session, String correlation, String component, String element, String action) {
            this.session = session; this.correlation = correlation; this.component = component;
            this.element = element; this.action = action;
        }
        public boolean active() { return session != null; }
        public String correlation() { return correlation; }
        public void stage(String stage, Map<String, ?> details) {
            if (session != null) try { session.write(stage, correlation, details); }
            catch (RuntimeException error) { LOGGER.log(Level.WARNING, "UI trace stage lost", error); }
        }
        public void complete(String result, Map<String, ?> details) {
            if (session == null || !completed.compareAndSet(false, true)) return;
            var value = result == null || result.isBlank() ? "HANDLED" : result;
            var finished = new LinkedHashMap<String, Object>();
            finished.put("result", limited(value, 96));
            add(finished, details);
            try { session.write("HANDLER_EXIT", correlation, finished); }
            catch (RuntimeException error) { LOGGER.log(Level.WARNING, "UI trace completion lost", error); }
            LOGGER.log(Level.INFO, "UI_TRACE [" + correlation + "] " + limited(component, 40)
                    + " element=" + limited(element, 80) + " action=" + limited(action, 40)
                    + " result=" + limited(value, 96));
        }
    }

    private final class Session {
        final UUID player, id;
        final Path path;
        final BoundedTraceWriter writer;
        final AtomicInteger count = new AtomicInteger();
        final AtomicBoolean closed = new AtomicBoolean();
        final long started = System.nanoTime();
        volatile ScheduledFuture<?> timeout;
        Session(UUID player, UUID id, Path path) {
            this.player = player; this.id = id; this.path = path;
            this.writer = new BoundedTraceWriter(path, 4L * 1024 * 1024, 1,
                    error -> LOGGER.log(Level.WARNING, "UI interaction trace lost records: " + path, error));
        }
        void write(String event, String correlation, Map<String, ?> details) {
            if (closed.get()) return;
            if (System.nanoTime() - started > MAX_AGE_NANOS || count.incrementAndGet() > MAX_EVENTS) {
                if (sessions.remove(player, this)) {
                    limit("TIME_OR_EVENT_LIMIT");
                    closeAsync();
                }
                return;
            }
            var row = new LinkedHashMap<String, Object>();
            row.put("timestamp", MILLIS.format(Instant.now()));
            row.put("event", limited(event, 64));
            row.put("traceSessionId", id.toString());
            row.put("correlationId", limited(correlation, 64));
            row.put("playerUuid", player.toString());
            add(row, details);
            writer.submit(row);
        }
        void limit(String reason) {
            writer.submit(Map.of("timestamp", MILLIS.format(Instant.now()), "event", "TRACE_LIMIT",
                    "traceSessionId", id.toString(), "playerUuid", player.toString(),
                    "correlationId", "", "reason", reason));
        }
        void close() {
            if (closed.compareAndSet(false, true)) {
                if (timeout != null) timeout.cancel(false);
                writer.close();
            }
        }
        void closeAsync() {
            if (closed.compareAndSet(false, true)) {
                if (timeout != null) timeout.cancel(false);
                Thread.ofVirtual().name("ui-interaction-trace-close").start(writer::close);
            }
        }
    }

    private static void add(Map<String, Object> into, Map<String, ?> details) {
        if (details == null) return;
        int count = 0;
        for (var entry : details.entrySet()) {
            if (++count > 32) break;
            String key = limited(entry.getKey(), 64);
            Object value = entry.getValue();
            into.put(key, value instanceof Number || value instanceof Boolean ? value : limited(String.valueOf(value), 256));
        }
    }
    private static String limited(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) : value;
    }
    @Override public void close() {
        if (installed == this) installed = null;
        for (UUID player : sessions.keySet()) off(player);
        expiry.shutdownNow();
    }
}
