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
    public enum Scope { UI, INVENTORY, SKILLTREE }
    private record Key(UUID player, Scope scope) { }
    private static final Logger LOGGER = Logger.getLogger(UiInteractionTrace.class.getName());
    private static final int MAX_EVENTS = 2500;
    private static final int MAX_SESSIONS = 64;
    private static final long MAX_AGE_NANOS = 10L * 60 * 1_000_000_000L;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter MILLIS = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    private static volatile UiInteractionTrace installed;

    private final Path directory;
    private final Object sessionStartLock = new Object();
    private final ConcurrentHashMap<Key, Session> sessions = new ConcurrentHashMap<>();
    private final Map<Key, String> lastResults = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>() {
                @Override protected boolean removeEldestEntry(Map.Entry<Key, String> eldest) { return size() > 256; }
            });
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
        if (com.inigmasgames.hytalerpg.diagnostics.hro.HroRuntime.active(player)) return true;
        return legacyActive(player);
    }
    public static boolean legacyActive(UUID player) {
        var trace = installed;
        if (trace == null) return false;
        for (Scope scope : Scope.values()) if (trace.sessions.containsKey(new Key(player, scope))) return true;
        return false;
    }
    public static Action beginIfActive(UUID player, String component, String elementId,
                                       String action, Supplier<Map<String, ?>> details) {
        var trace = installed;
        if (!active(player)) return Action.OFF;
        boolean legacy=legacyActive(player);
        if(!legacy&&action!=null&&(action.contains("Hover")||action.contains("hover")||action.equals("pointer")))return Action.OFF;
        try { return trace.begin(player, component, elementId, action, legacy?details.get():Map.of()); }
        catch (RuntimeException diagnosticFailure) {
            LOGGER.log(Level.WARNING, "UI interaction trace could not observe an action", diagnosticFailure);
            return Action.OFF;
        }
    }

    public String on(UUID player) { return on(player, Scope.UI); }

    public String on(UUID player, Scope scope) {
        synchronized (sessionStartLock) {
            Key key = new Key(player, scope);
            Session prior = sessions.get(key);
            if (prior != null) return scope + " trace already ON; session " + prior.id + "; " + prior.path;
            if (sessions.size() >= MAX_SESSIONS) return scope + " trace unavailable: 64 active sessions already recording.";
            var id = UUID.randomUUID();
            var prefix = scope == Scope.UI ? "ui" : scope.name().toLowerCase(java.util.Locale.ROOT);
            var path = directory.resolve(prefix + "-trace-" + STAMP.format(Instant.now()) + "-" + id.toString().substring(0, 8) + ".jsonl");
            var session = new Session(key, id, path);
            prior = sessions.putIfAbsent(key, session);
            if (prior != null) { session.close(); return scope + " trace already ON; session " + prior.id + "; " + prior.path; }
            session.write("SESSION_START", "", Map.of("traceType", scope.name(), "limitEvents", MAX_EVENTS, "limitMinutes", 10));
            session.timeout = expiry.schedule(() -> {
                if (sessions.remove(key, session)) {
                    session.limit("TIME_LIMIT");
                    session.close();
                    lastResults.put(key, "expired; file=" + path + "; events=" + session.count.get()
                            + "; writer=" + session.writer.metrics());
                }
            }, 10, TimeUnit.MINUTES);
            return scope + " trace ON; session " + id + "; " + path;
        }
    }

    public String off(UUID player) { return off(player, Scope.UI); }

    public String off(UUID player, Scope scope) {
        Key key = new Key(player, scope);
        var session = sessions.remove(key);
        if (session == null) return scope + " trace is off. " + lastResults.getOrDefault(key, "");
        session.write("SESSION_END", "", Map.of("recorded", session.count.get()));
        session.close();
        lastResults.put(key, "stopped; file=" + session.path + "; events=" + session.count.get()
                + "; writer=" + session.writer.metrics());
        return scope + " trace OFF; " + session.path;
    }

    public String mark(UUID player, String label) { return mark(player, Scope.UI, label); }

    public String mark(UUID player, Scope scope, String label) {
        var session = sessions.get(new Key(player, scope));
        if (session == null) return scope + " trace is off; start it first.";
        session.write("MARK", "", Map.of("label", limited(label == null ? "" : label, 64)));
        return scope + " trace mark saved.";
    }

    public String status(UUID player, Scope scope) {
        Key key = new Key(player, scope);
        Session session = sessions.get(key);
        return session == null ? scope + " trace off; " + lastResults.getOrDefault(key, "no saved capture")
                : scope + " trace ON; session=" + session.id + "; events=" + session.count.get()
                + "; file=" + session.path + "; writer=" + session.writer.metrics();
    }

    public void disconnect(UUID player) { for (Scope scope : Scope.values()) off(player, scope); }

    public Action begin(UUID player, String component, String elementId, String action, Map<String, ?> details) {
        var matching = matching(player, component, elementId, action, details);
        boolean hro = com.inigmasgames.hytalerpg.diagnostics.hro.HroRuntime.active(player);
        if (matching.isEmpty() && !hro) return Action.OFF;
        String correlation = UUID.randomUUID().toString().substring(0, 8);
        var base = new LinkedHashMap<String, Object>();
        base.put("component", limited(component, 64));
        base.put("elementId", limited(elementId, 128));
        base.put("action", limited(action, 64));
        add(base, details);
        for (Session session : matching) {
            session.write("INPUT_RECEIVED", correlation, base);
            session.write("TARGET_RESOLVED", correlation, Map.of("elementId", base.get("elementId")));
            session.write("HANDLER_ENTER", correlation, Map.of("handler", base.get("component")));
        }
        return new Action(matching, correlation, component, elementId, action, hro ? player : null);
    }

    public void event(UUID player, String event, String correlation, Map<String, ?> details) {
        com.inigmasgames.hytalerpg.diagnostics.hro.HroRuntime.ui(player,event,correlation,details);
        for (Session session : matching(player, event, "", event, details))
            try { session.write(event, correlation == null ? "" : correlation, details); }
            catch (RuntimeException error) { LOGGER.log(Level.WARNING, "UI trace observation lost", error); }
    }

    private java.util.List<Session> matching(UUID player, String component, String element, String action,
                                              Map<String, ?> details) {
        var result = new java.util.ArrayList<Session>(3);
        String domain = (String.valueOf(component) + " " + String.valueOf(element) + " " + String.valueOf(action)
                + " " + String.valueOf(details == null ? "" : details.get("canvasId"))
                + " " + String.valueOf(details == null ? "" : details.get("page"))
                + " " + String.valueOf(details == null ? "" : details.get("handler")))
                .toLowerCase(java.util.Locale.ROOT);
        for (Scope scope : Scope.values()) {
            Session session = sessions.get(new Key(player, scope));
            if (session == null) continue;
            if (scope == Scope.UI || scope == Scope.INVENTORY && (domain.contains("inventory")
                    || domain.contains("character") || domain.contains("spatialbag"))
                    || scope == Scope.SKILLTREE && (domain.contains("skilltree")
                    || domain.contains("skill-tree") || domain.contains("skill_tree"))) result.add(session);
        }
        return result;
    }

    public static final class Action {
        private static final Action OFF = new Action(java.util.List.of(), "", "", "", "", null);
        private final java.util.List<Session> recipients;
        private final String correlation, component, element, action;
        private final UUID hroPlayer;
        private final AtomicBoolean completed = new AtomicBoolean();
        private Action(java.util.List<Session> recipients, String correlation, String component, String element, String action, UUID hroPlayer) {
            this.recipients = recipients; this.correlation = correlation; this.component = component;
            this.element = element; this.action = action;this.hroPlayer=hroPlayer;
        }
        public boolean active() { return hroPlayer!=null || !recipients.isEmpty(); }
        public boolean legacyActive() { return !recipients.isEmpty(); }
        public String correlation() { return correlation; }
        public void stage(String stage, Map<String, ?> details) {
            if(hroPlayer!=null)com.inigmasgames.hytalerpg.diagnostics.hro.HroRuntime.ui(hroPlayer,stage,correlation,details);
            for (Session session : recipients) try { session.write(stage, correlation, details); }
            catch (RuntimeException error) { LOGGER.log(Level.WARNING, "UI trace stage lost", error); }
        }
        public void complete(String result, Map<String, ?> details) {
            if (recipients.isEmpty() || !completed.compareAndSet(false, true)) return;
            var value = result == null || result.isBlank() ? "HANDLED" : result;
            var finished = new LinkedHashMap<String, Object>();
            finished.put("result", limited(value, 96));
            add(finished, details);
            for (Session session : recipients) try { session.write("HANDLER_EXIT", correlation, finished); }
            catch (RuntimeException error) { LOGGER.log(Level.WARNING, "UI trace completion lost", error); }
            LOGGER.log(Level.INFO, "UI_TRACE [" + correlation + "] " + limited(component, 40)
                    + " element=" + limited(element, 80) + " action=" + limited(action, 40)
                    + " result=" + limited(value, 96));
        }
    }

    private final class Session {
        final Key key;
        final UUID player, id;
        final Path path;
        final BoundedTraceWriter writer;
        final AtomicInteger count = new AtomicInteger();
        final AtomicBoolean closed = new AtomicBoolean();
        final long started = System.nanoTime();
        volatile ScheduledFuture<?> timeout;
        Session(Key key, UUID id, Path path) {
            this.key = key; this.player = key.player(); this.id = id; this.path = path;
            this.writer = new BoundedTraceWriter(path, 4L * 1024 * 1024, 1,
                    error -> LOGGER.log(Level.WARNING, "UI interaction trace lost records: " + path, error));
        }
        void write(String event, String correlation, Map<String, ?> details) {
            if (closed.get()) return;
            if (System.nanoTime() - started > MAX_AGE_NANOS || count.incrementAndGet() > MAX_EVENTS) {
                if (sessions.remove(key, this)) {
                    limit("TIME_OR_EVENT_LIMIT");
                    closeAsync();
                    lastResults.put(key, "limit reached; file=" + path + "; events=" + count.get()
                            + "; writer=" + writer.metrics());
                }
                return;
            }
            var row = new LinkedHashMap<String, Object>();
            row.put("timestamp", MILLIS.format(Instant.now()));
            row.put("event", limited(event, 64));
            row.put("traceType", key.scope().name());
            row.put("traceSessionId", id.toString());
            row.put("correlationId", limited(correlation, 64));
            row.put("playerUuid", player.toString());
            add(row, details);
            writer.submit(row);
        }
        void limit(String reason) {
            writer.submit(Map.of("timestamp", MILLIS.format(Instant.now()), "event", "TRACE_LIMIT",
                    "traceSessionId", id.toString(), "traceType", key.scope().name(), "playerUuid", player.toString(),
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
        for (Key key : java.util.List.copyOf(sessions.keySet())) off(key.player(), key.scope());
        expiry.shutdownNow();
    }
}
