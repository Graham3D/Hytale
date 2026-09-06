package com.inigmasgames.hytalerpg.ui.trace;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;
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

/** Bounded, asynchronous UI diagnostics. UI tracing can never become gameplay authority. */
public final class RpgUiTraceService implements AutoCloseable {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
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
        Record record = new Record(Instant.now().toString(), BuildIdentity.REVISION, BuildIdentity.VERSION,
                BuildIdentity.HYTALE_VERSION, player, event, correlationId, new LinkedHashMap<>(details));
        LOGGER.atInfo().log("RPG_UI_TRACE revision=%s event=%s player=%s correlation=%s",
                BuildIdentity.REVISION, event, player, correlationId);
        try { writer.execute(() -> write(record)); }
        catch (RejectedExecutionException error) { logFailureOnce(error); }
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
            LOGGER.atWarning().withCause(error).log("RPG UI diagnostics unavailable; gameplay remains active path=%s", path);
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
