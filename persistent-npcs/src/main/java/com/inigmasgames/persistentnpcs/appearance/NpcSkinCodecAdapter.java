package com.inigmasgames.persistentnpcs.appearance;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/** Explicit boundary between authored JSON and Hytale's protocol PlayerSkin. */
public final class NpcSkinCodecAdapter {
    public interface RuntimeApi {
        com.hypixel.hytale.protocol.PlayerSkin parse(String json);
        void validate(com.hypixel.hytale.protocol.PlayerSkin skin);
        com.hypixel.hytale.server.core.asset.type.model.config.Model createModel(
                com.hypixel.hytale.protocol.PlayerSkin skin);
    }

    private final RuntimeApi runtime;
    private static final String[] KNOWN_FIELDS = {
        "bodyCharacteristic", "underwear", "face", "eyes", "ears", "mouth",
        "facialHair", "haircut", "eyebrows", "pants", "overpants", "undertop",
        "overtop", "shoes", "headAccessory", "faceAccessory", "earAccessory",
        "skinFeature", "gloves", "cape"
    };

    public NpcSkinCodecAdapter() {
        this(new RuntimeApi() {
            @Override public com.hypixel.hytale.protocol.PlayerSkin parse(String json) {
                return module().parseSkinFromJson(json);
            }
            @Override public void validate(com.hypixel.hytale.protocol.PlayerSkin skin) {
                try {
                    module().validateSkin(skin);
                } catch (com.hypixel.hytale.server.core.cosmetics.CosmeticsModule.InvalidSkinException
                        failure) {
                    throw new IllegalArgumentException("Hytale rejected the skin selection: "
                            + failure.getMessage(), failure);
                }
            }
            @Override public com.hypixel.hytale.server.core.asset.type.model.config.Model
                    createModel(com.hypixel.hytale.protocol.PlayerSkin skin) {
                return module().createModel(skin, 1.0f);
            }
            private com.hypixel.hytale.server.core.cosmetics.CosmeticsModule module() {
                var module = com.hypixel.hytale.server.core.cosmetics.CosmeticsModule.get();
                if (module == null) throw new IllegalStateException(
                        "Hytale cosmetics runtime is not available.");
                return module;
            }
        });
    }

    public NpcSkinCodecAdapter(RuntimeApi runtime) {
        this.runtime = java.util.Objects.requireNonNull(runtime, "Skin runtime is required.");
    }

    public record SkinDocument(JsonObject raw,
            com.hypixel.hytale.protocol.PlayerSkin skin, String canonicalHash) {
        public SkinDocument {
            if (raw == null || skin == null || canonicalHash == null) {
                throw new IllegalArgumentException("Complete skin document is required.");
            }
            raw = raw.deepCopy();
            skin = new com.hypixel.hytale.protocol.PlayerSkin(skin);
        }

        @Override public JsonObject raw() { return raw.deepCopy(); }
        @Override public com.hypixel.hytale.protocol.PlayerSkin skin() {
            return new com.hypixel.hytale.protocol.PlayerSkin(skin);
        }
    }

    public SkinDocument read(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Authoritative NPC skin JSON is missing.");
        }
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(path,
                    StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("NPC skin JSON must be an object.");
            }
            JsonObject raw = parsed.getAsJsonObject();
            com.hypixel.hytale.protocol.PlayerSkin skin = runtime.parse(
                    JsonFiles.GSON.toJson(raw));
            return new SkinDocument(raw, skin, canonicalHash(raw));
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read NPC skin JSON.", failure);
        }
    }

    /**
     * Reads and validates a document at an authority boundary. Editor-open deliberately uses
     * {@link #read(Path)} so an asset removed from the enabled registry is retained and can be
     * shown as missing instead of making the profile impossible to repair.
     */
    public SkinDocument readValidated(Path path) {
        SkinDocument document = read(path);
        validate(document.skin());
        return document;
    }

    public SkinDocument merge(JsonObject extensionPreservingBase,
            com.hypixel.hytale.protocol.PlayerSkin skin) {
        validate(skin);
        JsonObject merged = extensionPreservingBase == null
                ? new JsonObject() : extensionPreservingBase.deepCopy();
        for (String field : KNOWN_FIELDS) {
            String value = field(skin, field);
            if (value == null || value.isBlank()) merged.remove(field);
            else merged.addProperty(field, value);
        }
        return new SkinDocument(merged, skin, canonicalHash(merged));
    }

    public com.hypixel.hytale.server.core.asset.type.model.config.Model createModel(
            com.hypixel.hytale.protocol.PlayerSkin skin) {
        validate(skin);
        return runtime.createModel(new com.hypixel.hytale.protocol.PlayerSkin(skin));
    }

    public void validate(com.hypixel.hytale.protocol.PlayerSkin skin) {
        if (skin == null) throw new IllegalArgumentException("NPC skin is required.");
        runtime.validate(skin);
    }

    public com.hypixel.hytale.protocol.PlayerSkin with(
            com.hypixel.hytale.protocol.PlayerSkin source,
            NpcAppearanceCatalogService.Category category, String encoded) {
        if (source == null || category == null) {
            throw new IllegalArgumentException("Skin and appearance category are required.");
        }
        com.hypixel.hytale.protocol.PlayerSkin candidate =
                new com.hypixel.hytale.protocol.PlayerSkin(source);
        String value = encoded == null || encoded.isBlank() ? null : encoded;
        switch (category) {
            case BODY_CHARACTERISTIC -> candidate.bodyCharacteristic = value;
            case UNDERWEAR -> candidate.underwear = value;
            case FACE -> candidate.face = value;
            case EYES -> candidate.eyes = value;
            case EARS -> candidate.ears = value;
            case MOUTH -> candidate.mouth = value;
            case FACIAL_HAIR -> candidate.facialHair = value;
            case HAIRCUT -> candidate.haircut = value;
            case EYEBROWS -> candidate.eyebrows = value;
            case PANTS -> candidate.pants = value;
            case OVERPANTS -> candidate.overpants = value;
            case UNDERTOP -> candidate.undertop = value;
            case OVERTOP -> candidate.overtop = value;
            case SHOES -> candidate.shoes = value;
            case HEAD_ACCESSORY -> candidate.headAccessory = value;
            case FACE_ACCESSORY -> candidate.faceAccessory = value;
            case EAR_ACCESSORY -> candidate.earAccessory = value;
            case SKIN_FEATURE -> candidate.skinFeature = value;
            case GLOVES -> candidate.gloves = value;
            case CAPE -> candidate.cape = value;
        }
        validate(candidate);
        return candidate;
    }

    /**
     * Produces a client-preview-only skin that reveals a cosmetic hidden by an
     * outer authored layer. The authoritative draft is never mutated: changing
     * categories or leaving the editor restores the complete composition.
     */
    public static com.hypixel.hytale.protocol.PlayerSkin focusedPreviewSkin(
            com.hypixel.hytale.protocol.PlayerSkin source,
            NpcAppearanceCatalogService.Category category) {
        if (source == null) throw new IllegalArgumentException("Skin is required.");
        com.hypixel.hytale.protocol.PlayerSkin preview =
                new com.hypixel.hytale.protocol.PlayerSkin(source);
        if (category == null) return preview;
        switch (category) {
            case UNDERWEAR -> {
                preview.pants = null;
                preview.overpants = null;
                preview.undertop = null;
                preview.overtop = null;
            }
            case PANTS -> preview.overpants = null;
            case UNDERTOP -> preview.overtop = null;
            case HAIRCUT -> preview.headAccessory = null;
            case FACE, EYES, MOUTH, EYEBROWS, FACIAL_HAIR ->
                    preview.faceAccessory = null;
            default -> { }
        }
        return preview;
    }

    public static String selection(com.hypixel.hytale.protocol.PlayerSkin skin,
            NpcAppearanceCatalogService.Category category) {
        return skin == null || category == null ? null : field(skin, category.skinField());
    }

    public static String partId(String encoded) {
        if (encoded == null || encoded.isBlank()) return "";
        int separator = encoded.indexOf('.');
        return separator < 0 ? encoded : encoded.substring(0, separator);
    }

    public static String colorId(String encoded) {
        if (encoded == null) return "";
        String[] parts = encoded.split("\\.", -1);
        return parts.length > 1 ? parts[1] : "";
    }

    public static String variantId(String encoded) {
        if (encoded == null) return "";
        String[] parts = encoded.split("\\.", -1);
        return parts.length > 2 ? parts[2] : "";
    }

    public static String canonicalHash(JsonObject raw) {
        try {
            String canonical = canonical(raw).toString();
            return HexFormat.of().withUpperCase().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static String serialized(SkinDocument document) {
        return JsonFiles.GSON.toJson(document.raw());
    }

    private static String field(com.hypixel.hytale.protocol.PlayerSkin skin, String field) {
        return switch (field) {
            case "bodyCharacteristic" -> skin.bodyCharacteristic;
            case "underwear" -> skin.underwear;
            case "face" -> skin.face;
            case "eyes" -> skin.eyes;
            case "ears" -> skin.ears;
            case "mouth" -> skin.mouth;
            case "facialHair" -> skin.facialHair;
            case "haircut" -> skin.haircut;
            case "eyebrows" -> skin.eyebrows;
            case "pants" -> skin.pants;
            case "overpants" -> skin.overpants;
            case "undertop" -> skin.undertop;
            case "overtop" -> skin.overtop;
            case "shoes" -> skin.shoes;
            case "headAccessory" -> skin.headAccessory;
            case "faceAccessory" -> skin.faceAccessory;
            case "earAccessory" -> skin.earAccessory;
            case "skinFeature" -> skin.skinFeature;
            case "gloves" -> skin.gloves;
            case "cape" -> skin.cape;
            default -> throw new IllegalArgumentException("Unknown PlayerSkin field: " + field);
        };
    }

    private static JsonElement canonical(JsonElement element) {
        if (element == null || element.isJsonNull()) return JsonNull.INSTANCE;
        if (element.isJsonArray()) {
            JsonArray result = new JsonArray();
            element.getAsJsonArray().forEach(value -> result.add(canonical(value)));
            return result;
        }
        if (element.isJsonObject()) {
            JsonObject result = new JsonObject();
            Map<String, JsonElement> sorted = new TreeMap<>();
            element.getAsJsonObject().entrySet().forEach(
                    entry -> sorted.put(entry.getKey(), entry.getValue()));
            sorted.forEach((key, value) -> result.add(key, canonical(value)));
            return result;
        }
        return element.deepCopy();
    }
}
