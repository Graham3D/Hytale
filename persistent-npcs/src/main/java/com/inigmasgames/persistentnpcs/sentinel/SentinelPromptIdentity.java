package com.inigmasgames.persistentnpcs.sentinel;

import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Minimal safe deterministic prompt checkpoint. Full prompt text is never retained. */
public final class SentinelPromptIdentity {
    private SentinelPromptIdentity() { }
    public static String hash(List<ChatMessage> messages) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (ChatMessage message : messages == null ? List.<ChatMessage>of() : messages) {
                update(digest, message.role());
                update(digest, message.content());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
    private static void update(MessageDigest digest, String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }
}
