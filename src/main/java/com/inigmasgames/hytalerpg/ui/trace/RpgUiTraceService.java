package com.inigmasgames.hytalerpg.ui.trace;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.inigmasgames.hytalerpg.phase00.BuildIdentity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Bounded, asynchronous UI diagnostics. UI tracing can never become gameplay authority. */
public final class RpgUiTraceService implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(RpgUiTraceService.class.getName());
    private static final long MAX_BYTES = 4L * 1024L * 1024L;
    private static final int RETAINED_FILES = 4;
    private final Path path;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final ExecutorService writer;
    private final AtomicBoolean failureLogged = new AtomicBoolean();

    public RpgUiTraceService(Path path) {
        this.path = path;
        writer = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "rpg-ui-trace-writer");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void trace(UUID player, String event, String correlationId, Map<String, ?> details) {
        LinkedHashMap<String, Object> enriched = new LinkedHashMap<>();
        enriched.put("page", inferredPage(event));
        enriched.put("component", event.toLowerCase(java.util.Locale.ROOT));
        enriched.putAll(details);
        Record record = new Record(Instant.now().toString(), BuildIdentity.REVISION, BuildIdentity.VERSION,
                BuildIdentity.HYTALE_VERSION, player, event, correlationId, enriched);
        if (!"HUD_REFRESHED".equals(event)) {
            LOGGER.log(Level.INFO, "RPG_UI_TRACE revision=" + BuildIdentity.REVISION + " event=" + event
                    + " player=" + player + " correlation=" + correlationId + " details=" + enriched);
        }
        try { writer.execute(() -> write(record)); }
        catch (RejectedExecutionException error) { logFailureOnce(error); }
    }

    private static String inferredPage(String event) {
        if (event.startsWith("SKILLTREE")) return "skilltree";
        if (event.startsWith("CHARACTER") || event.startsWith("ATTRIBUTE")) return "character";
        if (event.startsWith("HUD") || event.startsWith("SKILLBAR") || event.startsWith("XP_")
                || event.startsWith("LEVEL_UP")) return "hud";
        return "command";
    }

    private void write(Record record) {
        try {
            Files.createDirectories(path.getParent());
            String line = gson.toJson(record) + System.lineSeparator();
            long pending = line.getBytes(StandardCharsets.UTF_8).length;
            if (Files.isRegularFile(path) && Files.size(path) + pending > MAX_BYTES) rotate();
            Files.writeString(path, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception error) { logFailureOnce(error); }
    }

    private void rotate() throws Exception {
        Files.deleteIfExists(rotated(RETAINED_FILES - 1));
        for (int index = RETAINED_FILES - 2; index >= 1; index--) {
            Path source = rotated(index);
            if (Files.exists(source)) Files.move(source, rotated(index + 1), StandardCopyOption.REPLACE_EXISTING);
        }
        if (Files.exists(path)) Files.move(path, rotated(1), StandardCopyOption.REPLACE_EXISTING);
    }

    private Path rotated(int index) { return path.resolveSibling(path.getFileName() + "." + index); }
    private void logFailureOnce(Throwable error) {
        if (failureLogged.compareAndSet(false, true))
            LOGGER.log(Level.WARNING, "RPG UI diagnostics unavailable; gameplay remains active path=" + path, error);
    }
    public Path path() { return path; }

    @Override public void close() {
        writer.shutdown();
        try { writer.awaitTermination(5, TimeUnit.SECONDS); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }

    private record Record(String timestamp, String rpgRevision, String buildVersion, String hytaleBuild,
                          UUID playerUuid, String eventType, String correlationId,
                          Map<String, Object> details) {
        private Record { details = Map.copyOf(details); }
    }
}
