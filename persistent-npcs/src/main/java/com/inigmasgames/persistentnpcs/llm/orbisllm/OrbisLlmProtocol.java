package com.inigmasgames.persistentnpcs.llm.orbisllm;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Private, versioned, bounded local IPC contract shared with OrbisLLM.exe. */
public final class OrbisLlmProtocol {
    public static final int MAGIC = 0x4c42524f;
    public static final int MAJOR = 1;
    public static final int MINOR = 0;
    public static final int HEADER_BYTES = 40;
    public static final int MAX_REQUEST_BYTES = 2 * 1024 * 1024;
    public static final int MAX_EVENT_BYTES = 64 * 1024;

    private OrbisLlmProtocol() { }

    public enum Type {
        HELLO(1), HELLO_ACK(2), LOAD_MODEL(3), MODEL_PROGRESS(4),
        CREATE_CONTEXT(5), READY(6), GENERATE(7), REQUEST_ACCEPTED(8),
        PROMPT_PROGRESS(9), REASONING_DELTA(10), FINAL_DELTA(11),
        CONTRACT_COMPLETE(12), REQUEST_COMPLETE(13), CANCEL(14),
        CANCEL_REQUESTED(15), CANCEL_ACK(16), RELEASE_CONTEXT(17),
        UNLOAD_MODEL(18), GET_STATUS(19), STATUS(20), RESOURCE_SNAPSHOT(21),
        ERROR(22), SHUTDOWN(23), SHUTDOWN_ACK(24);

        private final int wire;
        Type(int wire) { this.wire = wire; }
        public int wire() { return wire; }
        public static Type fromWire(int value) {
            for (Type type : values()) if (type.wire == value) return type;
            throw new IllegalArgumentException("Unknown OrbisLLM message type " + value);
        }
    }

    public record Frame(Type type, UUID requestId, long sequence, JsonObject body) { }

    public static byte[] encode(Type type, UUID requestId, long sequence, JsonObject body) {
        byte[] payload = JsonFiles.GSON.toJson(body == null ? new JsonObject() : body)
                .getBytes(StandardCharsets.UTF_8);
        if (payload.length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("OrbisLLM request payload exceeds 2 MiB");
        }
        UUID id = requestId == null ? new UUID(0, 0) : requestId;
        ByteBuffer result = ByteBuffer.allocate(HEADER_BYTES + payload.length)
                .order(ByteOrder.LITTLE_ENDIAN);
        result.putInt(MAGIC).putShort((short) MAJOR).putShort((short) MINOR)
                .putShort((short) type.wire()).putShort((short) 0)
                .putInt(payload.length).putLong(sequence);
        putUuid(result, id);
        result.put(payload);
        return result.array();
    }

    public static Header decodeHeader(byte[] bytes) {
        if (bytes == null || bytes.length != HEADER_BYTES) {
            throw new IllegalArgumentException("OrbisLLM frame header must be 40 bytes");
        }
        ByteBuffer input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int magic = input.getInt();
        int major = Short.toUnsignedInt(input.getShort());
        int minor = Short.toUnsignedInt(input.getShort());
        Type type = Type.fromWire(Short.toUnsignedInt(input.getShort()));
        input.getShort();
        int payloadBytes = input.getInt();
        long sequence = input.getLong();
        UUID requestId = getUuid(input);
        if (magic != MAGIC || major != MAJOR || minor > MINOR || sequence <= 0
                || payloadBytes < 0 || payloadBytes > MAX_EVENT_BYTES) {
            throw new IllegalArgumentException("Invalid OrbisLLM frame header");
        }
        return new Header(type, requestId, sequence, payloadBytes);
    }

    public static Frame decode(Header header, byte[] payload) {
        if (payload == null || payload.length != header.payloadBytes()) {
            throw new IllegalArgumentException("Truncated OrbisLLM frame payload");
        }
        JsonObject body = payload.length == 0 ? new JsonObject()
                : JsonFiles.GSON.fromJson(new String(payload, StandardCharsets.UTF_8),
                        JsonObject.class);
        return new Frame(header.type(), header.requestId(), header.sequence(), body);
    }

    public record Header(Type type, UUID requestId, long sequence, int payloadBytes) { }

    private static void putUuid(ByteBuffer output, UUID value) {
        output.order(ByteOrder.BIG_ENDIAN).putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits()).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static UUID getUuid(ByteBuffer input) {
        long most = input.order(ByteOrder.BIG_ENDIAN).getLong();
        long least = input.getLong();
        input.order(ByteOrder.LITTLE_ENDIAN);
        return new UUID(most, least);
    }
}
