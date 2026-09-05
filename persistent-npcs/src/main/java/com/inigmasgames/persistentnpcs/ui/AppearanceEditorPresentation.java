package com.inigmasgames.persistentnpcs.ui;

import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService.Category;
import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService.PrimaryCategory;

/** Pure presentation mapping. These names reference packaged native UI artwork, not cosmetics IDs. */
public final class AppearanceEditorPresentation {
    private AppearanceEditorPresentation() { }

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
