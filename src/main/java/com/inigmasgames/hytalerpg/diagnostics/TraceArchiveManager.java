package com.inigmasgames.hytalerpg.diagnostics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.inigmasgames.hytalerpg.phase00.BuildIdentity;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Filesystem-backed archive backlog with a single bounded diagnostic worker. */
public final class TraceArchiveManager implements AutoCloseable {
    public enum Compression { GZIP, NONE }
    enum FaultPoint { DURING_GZIP_WRITE, AFTER_GZIP_CLOSE, AFTER_VERIFICATION, AFTER_FINAL_MOVE, AFTER_MANIFEST }
    @FunctionalInterface interface FaultInjector { void at(FaultPoint point) throws IOException; }
    public static final int ARCHIVE_QUEUE_CAPACITY = 8;
    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private final Path archiveDirectory;
    private final String baseName;
    private final String traceKind;
    private final Compression compression;
    private final boolean verify;
    private final Consumer<Throwable> failure;
    private final FaultInjector fault;
    private final ThreadPoolExecutor worker;
    private final AtomicLong sequence;
    private final Set<Path> scheduled = java.util.Collections.synchronizedSet(new HashSet<>());
    private final Set<String> warnings = java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());
    private final AtomicLong completed = new AtomicLong(), compressionFailures = new AtomicLong(), verificationFailures = new AtomicLong();
    private volatile String lastSuccessfulArchiveTimestamp;

    public record Metrics(int queuedArchiveTasks, long archivesCompleted, long compressionFailures,
                          long verificationFailures, long rawPendingBytes, long rawPendingSegments,
                          String lastSuccessfulArchiveTimestamp, String archiveThreadName) {}

    TraceArchiveManager(Path activePath, String traceKind, Compression compression, boolean verify,
                        Consumer<Throwable> failure) {
        this(activePath, traceKind, compression, verify, failure, point -> {});
    }

    TraceArchiveManager(Path activePath, String traceKind, Compression compression, boolean verify,
                        Consumer<Throwable> failure, FaultInjector fault) {
        Path absolute = activePath.toAbsolutePath();
        this.archiveDirectory = absolute.getParent().resolve("archive");
        String filename = absolute.getFileName().toString();
        this.baseName = filename.endsWith(".jsonl") ? filename.substring(0, filename.length() - 6) : filename;
        this.traceKind = traceKind;
        this.compression = compression;
        this.verify = verify;
        this.failure = failure;
        this.fault = fault;
        this.sequence = new AtomicLong(findHighestSequence());
        this.worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(ARCHIVE_QUEUE_CAPACITY), task -> {
                    Thread thread = new Thread(task, "rpg-trace-archive-" + this.traceKind.toLowerCase());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        execute(this::recoverAndDrain, "startup-recovery");
    }

    Path allocatePendingPath() {
        long next = sequence.incrementAndGet();
        String id = baseName + "-" + ID_TIME.format(Instant.now()) + "-" + String.format("%06d", next);
        return archiveDirectory.resolve(id + ".jsonl.pending");
    }

    void enqueue(Path pending) {
        Path absolute = pending.toAbsolutePath();
        if (!scheduled.add(absolute)) return;
        if (!execute(() -> {
            try { archive(absolute); }
            finally {
                scheduled.remove(absolute);
                drainPendingOnWorker();
            }
        }, "queue-full:" + absolute.getFileName())) scheduled.remove(absolute);
    }

    private boolean execute(Runnable task, String warningKey) {
        try { worker.execute(task); return true; }
        catch (RejectedExecutionException rejected) {
            warnOnce(warningKey, new IOException("TRACE_ARCHIVE_QUEUE_FULL_RAW_PENDING_RETAINED", rejected));
            return false;
        }
    }

    private void recoverAndDrain() {
        try {
            Files.createDirectories(archiveDirectory);
            recoverTemporaryArtifacts();
            recoverFinalWithoutManifest();
            recoverRawCompressedPairs();
            drainPendingOnWorker();
        } catch (Exception error) {
            warnOnce("startup-recovery", error);
        }
    }

    private void drainPendingOnWorker() {
        Set<Path> attempted = new HashSet<>();
        try {
            if (!Files.isDirectory(archiveDirectory)) return;
            while (true) {
                Path pending;
                try (var paths = Files.list(archiveDirectory)) {
                    pending = paths.filter(path -> path.getFileName().toString().endsWith(".jsonl.pending"))
                            .filter(path -> !scheduled.contains(path.toAbsolutePath()))
                            .filter(path -> !attempted.contains(path.toAbsolutePath()))
                            .sorted().findFirst().orElse(null);
                }
                if (pending == null) return;
                Path absolute = pending.toAbsolutePath();
                attempted.add(absolute);
                if (!scheduled.add(absolute)) continue;
                try { archive(absolute); }
                finally { scheduled.remove(absolute); }
            }
        } catch (Exception error) {
            warnOnce("filesystem-backlog", error);
        }
    }

    private void archive(Path pending) {
        if (!Files.isRegularFile(pending)) return;
        try {
            pending = preservePartialFinalLine(pending);
            if (pending == null || !Files.isRegularFile(pending)) return;
            SegmentStats raw = inspect(Files.readAllBytes(pending));
            String stem = pending.getFileName().toString().replace(".jsonl.pending", "");
            Path temporary = archiveDirectory.resolve(stem + (compression == Compression.GZIP ? ".jsonl.gz.tmp" : ".jsonl.tmp"));
            Path artifact = archiveDirectory.resolve(stem + (compression == Compression.GZIP ? ".jsonl.gz" : ".jsonl"));
            Path manifestPath = archiveDirectory.resolve(stem + ".manifest.json");
            Files.deleteIfExists(temporary);
            if (compression == Compression.GZIP) writeGzip(pending, temporary);
            else Files.copy(pending, temporary, StandardCopyOption.REPLACE_EXISTING);
            fault.at(FaultPoint.AFTER_GZIP_CLOSE);
            byte[] reconstructed;
            try { reconstructed = readArtifact(temporary, compression); }
            catch (IOException invalidArchive) { verificationFailures.incrementAndGet(); throw invalidArchive; }
            SegmentStats restored = inspect(reconstructed);
            if (verify && (!raw.sha256.equals(restored.sha256) || raw.bytes != restored.bytes || raw.eventCount != restored.eventCount)) {
                verificationFailures.incrementAndGet();
                throw new IOException("TRACE_ARCHIVE_VERIFICATION_MISMATCH");
            }
            fault.at(FaultPoint.AFTER_VERIFICATION);
            atomicMove(temporary, artifact);
            forceFile(artifact);
            fault.at(FaultPoint.AFTER_FINAL_MOVE);
            TraceArchiveManifest manifest = manifest(stem, artifact, raw);
            writeManifest(manifestPath, manifest);
            fault.at(FaultPoint.AFTER_MANIFEST);
            Files.deleteIfExists(pending);
            completed.incrementAndGet();
            lastSuccessfulArchiveTimestamp = Instant.now().toString();
        } catch (Exception error) {
            compressionFailures.incrementAndGet();
            warnOnce("archive:" + pending.getFileName() + ":" + error.getMessage(), error);
        }
    }

    private void writeGzip(Path source, Path target) throws IOException {
        try (var input = Files.newInputStream(source);
             var output = new GZIPOutputStream(Files.newOutputStream(target, StandardOpenOption.CREATE_NEW))) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            boolean injected = false;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
                if (!injected) { injected = true; fault.at(FaultPoint.DURING_GZIP_WRITE); }
            }
        }
    }

    private TraceArchiveManifest manifest(String stem, Path artifact, SegmentStats raw) throws IOException {
        long sequence = parseSequence(stem);
        String compressedHash = sha256(Files.readAllBytes(artifact));
        return new TraceArchiveManifest(TraceArchiveManifest.SCHEMA_VERSION, traceKind, stem, sequence,
                Files.getLastModifiedTime(artifact).toInstant().toString(), Instant.now().toString(), raw.eventCount,
                raw.firstTimestamp, raw.lastTimestamp, raw.firstSequence, raw.lastSequence, raw.bytes,
                Files.size(artifact), compression.name(), raw.sha256, compressedHash, BuildIdentity.REVISION,
                BuildIdentity.VERSION, BuildIdentity.HYTALE_VERSION, TraceArchiveManifest.VERIFIED);
    }

    private void writeManifest(Path path, TraceArchiveManifest manifest) throws IOException {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, JSON.toJson(manifest) + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        forceFile(temporary);
        atomicMove(temporary, path);
    }

    private void recoverTemporaryArtifacts() throws IOException {
        try (var paths = Files.list(archiveDirectory)) {
            for (Path temporary : paths.filter(path -> path.getFileName().toString().endsWith(".tmp")).toList()) {
                String name = temporary.getFileName().toString();
                String rawName = name.replace(".jsonl.gz.tmp", ".jsonl.pending").replace(".jsonl.tmp", ".jsonl.pending");
                if (Files.exists(archiveDirectory.resolve(rawName))) Files.deleteIfExists(temporary);
                else quarantine(temporary, "orphan-temp");
            }
        }
    }

    private void recoverFinalWithoutManifest() throws IOException {
        try (var paths = Files.list(archiveDirectory)) {
            for (Path artifact : paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jsonl.gz") ||
                            (path.getFileName().toString().endsWith(".jsonl") && path.getFileName().toString().startsWith(baseName + "-"))).toList()) {
                String stem = artifact.getFileName().toString().replace(".jsonl.gz", "").replace(".jsonl", "");
                Path manifest = archiveDirectory.resolve(stem + ".manifest.json");
                if (Files.exists(manifest)) continue;
                try {
                    Compression kind = artifact.getFileName().toString().endsWith(".gz") ? Compression.GZIP : Compression.NONE;
                    SegmentStats raw = inspect(readArtifact(artifact, kind));
                    String compressedHash = sha256(Files.readAllBytes(artifact));
                    var rebuilt = new TraceArchiveManifest(TraceArchiveManifest.SCHEMA_VERSION, traceKind, stem,
                            parseSequence(stem), Files.getLastModifiedTime(artifact).toInstant().toString(), Instant.now().toString(),
                            raw.eventCount, raw.firstTimestamp, raw.lastTimestamp, raw.firstSequence, raw.lastSequence,
                            raw.bytes, Files.size(artifact), kind.name(), raw.sha256, compressedHash,
                            BuildIdentity.REVISION, BuildIdentity.VERSION, BuildIdentity.HYTALE_VERSION, TraceArchiveManifest.VERIFIED);
                    writeManifest(manifest, rebuilt);
                } catch (Exception invalid) {
                    verificationFailures.incrementAndGet();
                    quarantine(artifact, "unverified-final");
                    warnOnce("recover-final:" + stem, invalid);
                }
            }
        }
    }

    private void recoverRawCompressedPairs() throws IOException {
        try (var paths = Files.list(archiveDirectory)) {
            for (Path pending : paths.filter(path -> path.getFileName().toString().endsWith(".jsonl.pending")).toList()) {
                String stem = pending.getFileName().toString().replace(".jsonl.pending", "");
                Path gzip = archiveDirectory.resolve(stem + ".jsonl.gz");
                Path plain = archiveDirectory.resolve(stem + ".jsonl");
                Path artifact = Files.exists(gzip) ? gzip : plain;
                if (!Files.exists(artifact)) continue;
                Compression kind = Files.exists(gzip) ? Compression.GZIP : Compression.NONE;
                try {
                    String rawHash = sha256(Files.readAllBytes(pending));
                    String restoredHash = sha256(readArtifact(artifact, kind));
                    if (!rawHash.equals(restoredHash)) {
                        verificationFailures.incrementAndGet();
                        quarantine(artifact, "hash-mismatch");
                    } else if (Files.exists(archiveDirectory.resolve(stem + ".manifest.json"))) {
                        Files.deleteIfExists(pending);
                    }
                } catch (Exception invalid) {
                    verificationFailures.incrementAndGet();
                    quarantine(artifact, "invalid-compressed");
                    warnOnce("recover-pair:" + stem, invalid);
                }
            }
        }
    }

    private Path preservePartialFinalLine(Path pending) throws IOException {
        byte[] bytes = Files.readAllBytes(pending);
        if (bytes.length == 0 || bytes[bytes.length - 1] == '\n') return pending;
        int newline = bytes.length - 1;
        while (newline >= 0 && bytes[newline] != '\n') newline--;
        Path partial = pending.resolveSibling(pending.getFileName() + ".partial-" + System.nanoTime());
        Files.write(partial, java.util.Arrays.copyOfRange(bytes, newline + 1, bytes.length), StandardOpenOption.CREATE_NEW);
        if (newline < 0) {
            Files.deleteIfExists(pending);
            warnOnce("partial:" + pending.getFileName(), new IOException("TRACE_PARTIAL_FINAL_LINE_QUARANTINED"));
            return null;
        }
        Path repaired = pending.resolveSibling(pending.getFileName() + ".repair.tmp");
        Files.write(repaired, java.util.Arrays.copyOf(bytes, newline + 1), StandardOpenOption.CREATE_NEW);
        atomicMove(repaired, pending);
        warnOnce("partial:" + pending.getFileName(), new IOException("TRACE_PARTIAL_FINAL_LINE_QUARANTINED"));
        return pending;
    }

    private static byte[] readArtifact(Path path, Compression compression) throws IOException {
        if (compression == Compression.NONE) return Files.readAllBytes(path);
        try (var input = new GZIPInputStream(Files.newInputStream(path)); var output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            return output.toByteArray();
        }
    }

    private static SegmentStats inspect(byte[] bytes) {
        String hash = sha256(bytes);
        long count = 0;
        String firstTimestamp = null, lastTimestamp = null;
        Long firstSequence = null, lastSequence = null;
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                count++;
                try {
                    JsonObject object = JsonParser.parseString(line).getAsJsonObject();
                    if (object.has("timestamp") && !object.get("timestamp").isJsonNull()) {
                        String timestamp = object.get("timestamp").getAsString();
                        if (firstTimestamp == null) firstTimestamp = timestamp;
                        lastTimestamp = timestamp;
                    }
                    if (object.has("traceSequence") && !object.get("traceSequence").isJsonNull()) {
                        long value = object.get("traceSequence").getAsLong();
                        if (firstSequence == null) firstSequence = value;
                        lastSequence = value;
                    }
                } catch (RuntimeException ignored) {
                    // Preserve legacy bytes even when optional envelope metadata is not parseable.
                }
            }
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
        return new SegmentStats(bytes.length, count, hash, firstTimestamp, lastTimestamp, firstSequence, lastSequence);
    }

    private long findHighestSequence() {
        Path parent = archiveDirectory;
        if (!Files.isDirectory(parent)) return 0;
        try (var paths = Files.list(parent)) {
            return paths.map(path -> path.getFileName().toString()).filter(name -> name.startsWith(baseName + "-"))
                    .mapToLong(this::parseSequence).max().orElse(0);
        } catch (IOException ignored) { return 0; }
    }

    private long parseSequence(String name) {
        var matcher = Pattern.compile("-(\\d{6})(?:\\.|$)").matcher(name);
        long result = 0;
        while (matcher.find()) result = Math.max(result, Long.parseLong(matcher.group(1)));
        return result;
    }

    private void quarantine(Path path, String reason) throws IOException {
        if (!Files.exists(path)) return;
        Path target = path.resolveSibling(path.getFileName() + "." + reason + "-" + System.nanoTime());
        atomicMove(path, target);
    }

    private void warnOnce(String key, Throwable error) {
        synchronized (warnings) {
            if (warnings.contains(key)) return;
            if (warnings.size() >= 64) warnings.remove(warnings.iterator().next());
            warnings.add(key);
        }
        try { failure.accept(error); } catch (RuntimeException ignored) {}
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void forceFile(Path path) throws IOException {
        try (FileChannel channel=FileChannel.open(path,StandardOpenOption.WRITE)) { channel.force(true); }
    }

    public Metrics metrics() {
        long bytes = 0, segments = 0;
        if (Files.isDirectory(archiveDirectory)) {
            try (var paths = Files.list(archiveDirectory)) {
                for (Path path : paths.filter(item -> item.getFileName().toString().endsWith(".jsonl.pending")).toList()) {
                    segments++;
                    try { bytes += Files.size(path); } catch (IOException ignored) {}
                }
            } catch (IOException ignored) {}
        }
        return new Metrics(worker.getQueue().size(), completed.get(), compressionFailures.get(), verificationFailures.get(),
                bytes, segments, lastSuccessfulArchiveTimestamp, "rpg-trace-archive-" + traceKind.toLowerCase());
    }

    boolean awaitIdle(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (worker.getActiveCount() == 0 && worker.getQueue().isEmpty() && scheduled.isEmpty()) return true;
            Thread.sleep(10);
        }
        return false;
    }

    @Override public void close() {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(5, TimeUnit.SECONDS))
                warnOnce("close-timeout", new IOException("TRACE_ARCHIVE_CLOSE_TIMEOUT_RAW_PENDING_RETAINED"));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            warnOnce("close-interrupted", interrupted);
        }
    }

    private record SegmentStats(long bytes, long eventCount, String sha256, String firstTimestamp,
                                String lastTimestamp, Long firstSequence, Long lastSequence) {}
}
