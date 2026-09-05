package com.inigmasgames.persistentnpcs.ui;

import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService.Category;
import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService.PrimaryCategory;

/** Pure presentation mapping. These names reference packaged native UI artwork, not cosmetics IDs. */
public final class AppearanceEditorPresentation {
    public static final int CARD_COLUMNS = 5;
    public static final int COLOR_COLUMNS = 13;
    private static final java.util.Map<String, String> THUMBNAILS = loadThumbnails();
    private AppearanceEditorPresentation() { }

    /** Closed packaged index: no raw cosmetic IDs or filesystem paths become UI markup. */
    public static String thumbnail(Category category, String cosmeticId) {
        return THUMBNAILS.get(category.name() + ":" + cosmeticId);
    }

    private static java.util.Map<String, String> loadThumbnails() {
        var result = new java.util.HashMap<String, String>();
        try (var stream = AppearanceEditorPresentation.class.getResourceAsStream(
                "/Common/UI/Custom/Pages/ImmersiveNpcAppearance/Thumbnails/index.tsv")) {
            if (stream == null) return java.util.Map.of();
            for (String line : new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().toList()) {
                String[] fields = line.split("\t");
                if (fields.length == 3 && fields[1].matches("[a-f0-9]{24}\\.png")) {
                    result.put(fields[0], fields[1].substring(0, 24));
                }
            }
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Packaged appearance thumbnail index is unreadable", failure);
        }
        return java.util.Map.copyOf(result);
    }

    public static int paletteHeight(int colors) {
        return 24 + ((colors + COLOR_COLUMNS - 1) / COLOR_COLUMNS) * 38;
    }

    /** Numeric IDs only. Actual labels and authority IDs travel as values/event data. */
    public static void appendGrid(com.hypixel.hytale.server.core.ui.builder.UICommandBuilder commands,
            String host, String prefix, int count, int columns, int width, int height, int gap,
            String component) {
        commands.clear(host);
        for (int i = 0; i < count; i++) {
            int row = i / columns;
            String rowId = "#" + prefix + "Row" + row;
            if (i % columns == 0) commands.appendInline(host, "Group " + rowId
                    + " { LayoutMode: Left; Anchor: (Height: " + height + ", Bottom: " + gap + "); }");
            String id = "#" + prefix + i;
            commands.appendInline(rowId, "Group " + id + " { Anchor: (Width: " + width
                    + ", Height: " + height + ", Right: " + (i % columns == columns - 1 ? 0 : gap) + "); }");
            commands.append(id, component);
        }
    }

    public static String icon(PrimaryCategory category) {
        return switch (category) {
            case BODY -> "BodyCharacteristic";
            case FACE -> "Face";
            case HAIR -> "Haircut";
            case EYES -> "Eyes";
            case CLOTHING -> "Torso";
            case ACCESSORIES -> "HeadAccessory";
        };
    }

    public static String icon(Category category) {
        return switch (category) {
            case BODY_CHARACTERISTIC -> "BodyCharacteristic";
            case SKIN_FEATURE -> "General";
            case FACIAL_HAIR -> "FacialHair";
            case HEAD_ACCESSORY -> "HeadAccessory";
            case FACE_ACCESSORY -> "FaceAccessory";
            case EAR_ACCESSORY -> "EarAccessory";
            default -> category.name().substring(0, 1)
                    + category.name().substring(1).toLowerCase(java.util.Locale.ROOT);
        };
    }

    public static String label(String value, int limit) {
        if (value == null || value.isBlank()) return "None";
        String readable = value.replace('_', ' ').replaceAll("(?<=[a-z])(?=[A-Z])", " ");
        return readable.length() <= limit ? readable : readable.substring(0, limit - 3) + "...";
    }
}
