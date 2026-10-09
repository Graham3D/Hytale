package com.inigmasgames.hytalerpg.gear;

/** Player-facing rarity identity. Persisted rarity/quality IDs are intentionally unchanged. */
public enum GearRarityPresentation {
    NORMAL("Normal", "#ffffff"),
    RARE("Rare", "#1d4dff"),
    EPIC("Epic", "#6b00ff"),
    LEGENDARY("Legendary", "#ff9100"),
    SET("Set", "#51c534");

    public final String label;
    public final String color;

    GearRarityPresentation(String label, String color) {
        this.label = label;
        this.color = color;
    }

    /** Only Hywind-owned quality IDs are remapped; native item labels remain native. */
    public static GearRarityPresentation forQualityAsset(String id) {
        if (id == null) return null;
        return switch (id) {
            case "RPG_Gear_Common" -> NORMAL;
            case "RPG_Gear_Rare" -> RARE;
            case "RPG_Gear_RandomRare", "RPG_Gear_Epic" -> EPIC;
            case "RPG_Gear_Legendary", "RPG_Gear_Unique" -> LEGENDARY;
            case "RPG_Gear_Set" -> SET;
            default -> null;
        };
    }
}
