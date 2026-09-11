package com.inigmasgames.hytalerpg.diagnostics;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;

/** Single-owner ordered append/rotation path; archive work is delegated after the new active file opens. */
final class TraceSegmentWriter implements AutoCloseable {
    private final Path activePath;
    private final long maxSegmentBytes;
    private final TraceArchiveManager archive;
    private final Consumer<Throwable> failure;
    private FileChannel channel;
    private long activeBytes;
    private long activeEvents;
    private boolean rotationUnavailable;

    TraceSegmentWriter(Path activePath, long maxSegmentBytes, String traceKind,
                       TraceArchiveManager.Compression compression, boolean verify,
                       Consumer<Throwable> failure) throws IOException {
        this(activePath,maxSegmentBytes,traceKind,compression,verify,failure,point -> {});
    }

    TraceSegmentWriter(Path activePath, long maxSegmentBytes, String traceKind,
                       TraceArchiveManager.Compression compression, boolean verify,
                       Consumer<Throwable> failure, TraceArchiveManager.FaultInjector fault) throws IOException {
        this.activePath = activePath.toAbsolutePath();
        this.maxSegmentBytes = maxSegmentBytes;
        this.failure = failure;
        Path parent = this.activePath.getParent();
        if (parent != null) Files.createDirectories(parent);
        this.archive = new TraceArchiveManager(this.activePath, traceKind, compression, verify, failure, fault);
        recoverPartialActive();
    }

    void write(byte[] line) throws IOException {
        if (!rotationUnavailable && activeBytes > 0 && activeBytes + line.length > maxSegmentBytes) rotate();
        ByteBuffer buffer = ByteBuffer.wrap(line);
        while (buffer.hasRemaining()) channel.write(buffer);
        activeBytes += line.length;
        activeEvents++;
    }

    private void rotate() throws IOException {
        channel.force(false);
        channel.close();
        Path pending = archive.allocatePendingPath();
        try {
            Files.createDirectories(pending.getParent());
            atomicMove(activePath, pending);
            openActive();
            archive.enqueue(pending);
        } catch (IOException archiveFailure) {
            rotationUnavailable = true;
            try { openActive(); } catch (IOException reopenFailure) { archiveFailure.addSuppressed(reopenFailure); }
            if (Files.isRegularFile(pending)) archive.enqueue(pending);
            try { failure.accept(new IOException("TRACE_ROTATION_UNAVAILABLE_ACTIVE_CONTINUES", archiveFailure)); }
            catch (RuntimeException ignored) {}
            if (channel == null || !channel.isOpen()) throw archiveFailure;
        }
    }

    private void openActive() throws IOException {
        channel = FileChannel.open(activePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        activeBytes = channel.size();
        activeEvents = 0;
    }

    private void recoverPartialActive() throws IOException {
        if (Files.isRegularFile(activePath) && Files.size(activePath) > 0) {
            try (FileChannel input = FileChannel.open(activePath, StandardOpenOption.READ)) {
                ByteBuffer last = ByteBuffer.allocate(1);
                input.position(input.size() - 1);
                input.read(last);
                if (last.array()[0] != '\n') {
                    Path pending = archive.allocatePendingPath();
                    Files.createDirectories(pending.getParent());
                    atomicMove(activePath, pending);
                    openActive();
                    archive.enqueue(pending);
                    return;
                }
            }
        }
        openActive();
    }

    long activeBytes() { return activeBytes; }
    long activeEvents() { return activeEvents; }
    TraceArchiveManager archive() { return archive; }

    @Override public void close() throws IOException {
        IOException failure = null;
        if (channel != null && channel.isOpen()) {
            try { channel.force(false); } catch (IOException error) { failure = error; }
            try { channel.close(); } catch (IOException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        archive.close();
        if (failure != null) throw failure;
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(source, target); }
    }
}
