package com.inigmasgames.persistentnpcs.llm.orbisllm;

import com.google.gson.JsonObject;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** One duplex connection to the private user-scoped OrbisLLM named pipe. */
final class OrbisLlmIpcClient implements AutoCloseable {
    private final RandomAccessFile pipe;
    private final FileInputStream input;
    private final FileOutputStream output;
    private final Consumer<OrbisLlmProtocol.Frame> frames;
    private final Consumer<Throwable> failure;
    private final AtomicLong outbound = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean readerStarted = new AtomicBoolean();
    private final Thread reader;
    private volatile long inbound;

    private OrbisLlmIpcClient(RandomAccessFile pipe,
            Consumer<OrbisLlmProtocol.Frame> frames, Consumer<Throwable> failure)
            throws IOException {
        this.pipe = pipe;
        this.input = new FileInputStream(pipe.getFD());
        this.output = new FileOutputStream(pipe.getFD());
        this.frames = frames;
        this.failure = failure;
        reader = Thread.ofPlatform().daemon().name("orbisllm-ipc-reader").unstarted(this::readLoop);
    }

    static OrbisLlmIpcClient connect(String pipeName, Duration timeout,
            Consumer<OrbisLlmProtocol.Frame> frames, Consumer<Throwable> failure)
            throws IOException {
        long deadline = System.nanoTime() + timeout.toNanos();
        IOException latest = null;
        String path = "\\\\.\\pipe\\" + pipeName;
        while (System.nanoTime() < deadline) {
            try {
                return new OrbisLlmIpcClient(new RandomAccessFile(path, "rw"), frames, failure);
            } catch (IOException unavailable) {
                latest = unavailable;
                try { Thread.sleep(25); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted connecting to OrbisLLM", interrupted);
                }
            }
        }
        throw new IOException("Timed out connecting to OrbisLLM pipe " + pipeName, latest);
    }

    synchronized void send(OrbisLlmProtocol.Type type, UUID requestId, JsonObject body)
            throws IOException {
        if (closed.get()) throw new EOFException("OrbisLLM IPC is closed");
        byte[] bytes = OrbisLlmProtocol.encode(type, requestId,
                outbound.incrementAndGet(), body);
        output.write(bytes);
        output.flush();
        if (readerStarted.compareAndSet(false, true)) reader.start();
    }

    private void readLoop() {
        try {
            byte[] headerBytes = new byte[OrbisLlmProtocol.HEADER_BYTES];
            while (!closed.get()) {
                waitForAvailable(headerBytes.length);
                readFully(headerBytes);
                OrbisLlmProtocol.Header header = OrbisLlmProtocol.decodeHeader(headerBytes);
                if (header.sequence() <= inbound) {
                    throw new IOException("Non-monotonic OrbisLLM event sequence");
                }
                inbound = header.sequence();
                byte[] payload = new byte[header.payloadBytes()];
                waitForAvailable(payload.length);
                readFully(payload);
                frames.accept(OrbisLlmProtocol.decode(header, payload));
            }
        } catch (Throwable problem) {
            if (!closed.get()) failure.accept(problem);
        }
    }

    private void readFully(byte[] target) throws IOException {
        int offset = 0;
        while (offset < target.length) {
            int count = input.read(target, offset, target.length - offset);
            if (count < 0) throw new EOFException("OrbisLLM pipe closed");
            offset += count;
        }
    }

    /** Avoids an outstanding synchronous Windows pipe read blocking concurrent commands. */
    private void waitForAvailable(int required) throws IOException {
        while (!closed.get() && input.available() < required) {
            try { Thread.sleep(2); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted reading OrbisLLM IPC", interrupted);
            }
        }
        if (closed.get()) throw new EOFException("OrbisLLM IPC is closed");
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { pipe.close(); } catch (IOException ignored) { }
        if (readerStarted.get() && Thread.currentThread() != reader) reader.interrupt();
    }
}
