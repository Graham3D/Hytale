package com.inigmasgames.hytalerpg.diagnostics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Always-on bounded JSONL diagnostics. Failures are isolated from gameplay mutations. */
public final class RpgSkillTraceService implements RpgSkillTracer {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final Path path;
    private final long maxBytes;
    private final int retainedFiles;
    private final boolean enabled;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final ExecutorService writer;
    private final AtomicBoolean failureLogged = new AtomicBoolean();

    public RpgSkillTraceService(Path path, SkillTraceConfiguration configuration) {
        this.path = path;
        this.maxBytes = configuration.maxFileMb() * 1024L * 1024L;
        this.retainedFiles = configuration.retainedFiles();
        this.enabled = configuration.enabled();
        this.writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "rpg-skill-trace-writer");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void trace(RpgTraceRecord record) {
        if (!enabled) return;
        LOGGER.atInfo().log("RPG_SKILL_TRACE revision=%s event=%s player=%s correlation=%s result=%s code=%s",
                record.rpgRevision(), record.eventType(), record.playerUuid(), record.correlationId(),
                record.details().getOrDefault("validationResult", "n/a"),
                record.details().getOrDefault("failureCode", "n/a"));
        try { writer.execute(() -> write(record)); }
        catch (RejectedExecutionException ignored) { logFailureOnce(ignored); }
    }

    private void write(RpgTraceRecord record) {
        try {
            Files.createDirectories(path.getParent());
            String line = gson.toJson(record) + System.lineSeparator();
            long pending = line.getBytes(StandardCharsets.UTF_8).length;
            if (Files.isRegularFile(path) && Files.size(path) + pending > maxBytes) rotate();
            Files.writeString(path, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception error) { logFailureOnce(error); }
    }

    private void rotate() throws Exception {
        if (retainedFiles <= 1) {
            Files.deleteIfExists(path);
            return;
        }
        Files.deleteIfExists(rotated(retainedFiles - 1));
        for (int index = retainedFiles - 2; index >= 1; index--) {
            Path source = rotated(index);
            if (Files.exists(source)) Files.move(source, rotated(index + 1), StandardCopyOption.REPLACE_EXISTING);
        }
        if (Files.exists(path)) Files.move(path, rotated(1), StandardCopyOption.REPLACE_EXISTING);
    }

    private Path rotated(int index) { return path.resolveSibling(path.getFileName() + "." + index); }

    private void logFailureOnce(Throwable error) {
        if (failureLogged.compareAndSet(false, true)) {
            LOGGER.atWarning().withCause(error).log("RPG skill diagnostics unavailable; gameplay remains active path=%s", path);
        }
    }

    @Override
    public void close() {
        writer.shutdown();
        try { writer.awaitTermination(5, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }

    public Path path() { return path; }
}
