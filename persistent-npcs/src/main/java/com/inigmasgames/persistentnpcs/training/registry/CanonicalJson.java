package com.inigmasgames.persistentnpcs.training.registry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.TreeSet;

/** Language-neutral canonical JSON and SHA-256 boundary. */
public final class CanonicalJson {
    private static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();
    private CanonicalJson() { }

    public static String serialize(Object value) {
        return COMPACT.toJson(normalize(JsonFiles.GSON.toJsonTree(value)));
    }

    public static String sha256(Object value) {
        return sha256Bytes(serialize(value).getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Text(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value,
                Normalizer.Form.NFC).replace("\r\n", "\n").replace('\r', '\n');
        return sha256Bytes(normalized.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonElement normalize(JsonElement value) {
        if (value == null || value.isJsonNull()) return JsonNull.INSTANCE;
        if (value.isJsonObject()) {
            JsonObject result = new JsonObject();
            JsonObject source = value.getAsJsonObject();
            for (String key : new TreeSet<>(source.keySet())) {
                result.add(Normalizer.normalize(key, Normalizer.Form.NFC),
                        normalize(source.get(key)));
            }
            return result;
        }
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement item : value.getAsJsonArray()) result.add(normalize(item));
            return result;
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isString()) {
            return new JsonPrimitive(Normalizer.normalize(primitive.getAsString(),
                    Normalizer.Form.NFC).replace("\r\n", "\n").replace('\r', '\n'));
        }
        return primitive.deepCopy();
    }

    private static String sha256Bytes(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
