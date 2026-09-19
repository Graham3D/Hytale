package com.inigmasgames.persistentnpcs.autonomy;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import java.util.Locale;

/** Semantic labels are derived only from authoritative asset metadata. */
public final class GroundedSemanticClassifier {
    public String classifyBlock(BlockType block) {
        if (block == null || block == BlockType.EMPTY) return "";
        String identity = String.join(" ", safe(block.getId()), safe(block.getGroup()),
                block.getMaterial() == null ? "" : block.getMaterial().name(),
                safe(block.getCustomModel())).toLowerCase(Locale.ROOT);
        return classifyBlockIdentity(identity, block.getBench() != null,
                block.getSeats() != null);
    }

    public String classifyBlockIdentity(String rawIdentity, boolean workstation, boolean seat) {
        String identity = safe(rawIdentity).toLowerCase(Locale.ROOT);
        if (contains(identity, "flower", "blossom", "bloom", "daisy", "tulip", "rose")) {
            return "FLOWER";
        }
        if (contains(identity, "bush", "shrub", "hedge")) return "BUSH";
        if (contains(identity, "ore", "crystal", "gem_")) {
            return contains(identity, "adamantite", "mithril", "onyxium", "thorium",
                    "prisma", "cobalt") ? "RARE_ORE" : "ORE";
        }
        if (workstation) return "WORKSTATION";
        if (seat || contains(identity, "chair", "stool", "bench")) return "CHAIR";
        if (contains(identity, "plant", "fern", "vine", "herb", "sapling")) return "PLANT";
        return "";
    }

    public String classifyEntity(String assetId) {
        String value = safe(assetId).toLowerCase(Locale.ROOT);
        if (contains(value, "fox")) return "FOX";
        if (contains(value, "rabbit", "hare", "deer", "bird", "chicken", "cow",
                "sheep", "pig", "horse", "goat", "wolf", "bear", "animal")) {
            return "ANIMAL";
        }
        return "NPC";
    }

    public String classifyWeather(String weatherId) {
        String value = safe(weatherId).toLowerCase(Locale.ROOT);
        if (contains(value, "storm", "thunder", "lightning")) return "STORM";
        if (contains(value, "rain", "drizzle", "shower")) return "RAIN";
        return "";
    }

    private static boolean contains(String value, String... tokens) {
        for (String token : tokens) if (value.contains(token)) return true;
        return false;
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
