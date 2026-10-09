package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Immutable, shared glyph preparation. Packet entities are owned separately by each viewer. */
final class PromotedNameGlyphs {
    static final float PROMOTED_NAME_SCALE = 0.31f;
    private static final double WORLD_UNITS_PER_FONT_CELL = 0.44;

    record Glyph(int index, double horizontal, com.hypixel.hytale.protocol.Model model) { }
    record Name(String text, String rarity, List<Glyph> glyphs) {
        boolean valid() { return !glyphs.isEmpty(); }
    }

    static Name prepare(String text, String rarity) {
        if (text == null || text.isBlank() || rarityAsset(rarity) == null)
            throw new IllegalArgumentException("PROMOTED_NAME_INPUT");
        double[] centers = new double[text.length()];
        double cursor = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            double width = c == ' ' ? PromotedNameWidths.SPACE : width(c);
            centers[i] = cursor + width / 2;
            cursor += width;
        }
        var glyphs = new ArrayList<Glyph>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') continue;
            var id = assetId(rarity, c);
            var asset = ModelAsset.getAssetMap().getAsset(id);
            if (asset == null) throw new IllegalStateException("PROMOTED_NAME_GLYPH_ASSET_MISSING:" + id);
            glyphs.add(new Glyph(i, (centers[i] - cursor / 2) * WORLD_UNITS_PER_FONT_CELL,
                    Model.createStaticScaledModel(asset, 1).toPacket()));
        }
        if (glyphs.isEmpty()) throw new IllegalArgumentException("PROMOTED_NAME_EMPTY_GLYPHS");
        return new Name(text, rarity, List.copyOf(glyphs));
    }

    private static double width(char glyph) {
        if (glyph < 33 || glyph > 126)
            throw new IllegalArgumentException("PROMOTED_NAME_GLYPH_UNSUPPORTED:" + (int) glyph);
        return PromotedNameWidths.ADVANCES[glyph - 33];
    }

    static String rarityAsset(String label) {
        return switch (label) {
            case "Champion" -> "Champion";
            case "Unique" -> "Unique";
            case "Super Unique" -> "SuperUnique";
            default -> null;
        };
    }

    /** Connected R221 verified that the quad's readable face is opposite native yaw-forward. */
    static float frontFacingYaw(float viewerYaw) {
        return (float) Math.atan2(Math.sin(viewerYaw + Math.PI), Math.cos(viewerYaw + Math.PI));
    }

    static String assetId(String rarity, char glyph) {
        return "HywindName_" + rarityAsset(rarity) + "_U" + String.format(Locale.ROOT, "%04X", (int) glyph);
    }

    private PromotedNameGlyphs() { }
}
