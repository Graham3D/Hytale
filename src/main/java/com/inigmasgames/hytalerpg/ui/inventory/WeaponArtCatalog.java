package com.inigmasgames.hytalerpg.ui.inventory;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Unmodified authored PNGs, with alpha bounds used only for uniform UI fitting. */
final class WeaponArtCatalog {
    private static final String RESOURCE = "/rpg/inventory/weapon-art-v1.json";
    private static final Map<String, Art> ARTS = load();

    private WeaponArtCatalog() {}

    static Art find(String nativeItemId) {
        return ARTS.get(nativeItemId);
    }

    static int count() {
        return ARTS.size();
    }

    static Set<String> ids() {
        return ARTS.keySet();
    }

    record Fit(int left, int top, int width, int height) {}

    record Art(String texture, int canvasWidth, int canvasHeight, int alphaLeft, int alphaTop,
               int alphaRight, int alphaBottom) {
        Fit fit(int width, int height) {
            // The source PNG is packaged unchanged. UI fitting uses its visible
            // alpha rectangle, so transparent square margins need not dictate
            // the item's spatial footprint.
            double scale = Math.min((width - 8.0) / (alphaRight - alphaLeft),
                    (height - 8.0) / (alphaBottom - alphaTop));
            int imageWidth = Math.max(1, (int) Math.floor(canvasWidth * scale));
            int imageHeight = Math.max(1, (int) Math.floor(canvasHeight * scale));
            int left = (int) Math.round((width - (alphaRight - alphaLeft) * scale) / 2.0
                    - alphaLeft * scale);
            int top = (int) Math.round((height - (alphaBottom - alphaTop) * scale) / 2.0
                    - alphaTop * scale);
            return new Fit(left, top, imageWidth, imageHeight);
        }
    }

    private static Map<String, Art> load() {
        try (var stream = WeaponArtCatalog.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing weapon art catalog " + RESOURCE);
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("schemaVersion").getAsInt() != 2) throw new IllegalStateException("Unsupported weapon art schema");
            var arts = new LinkedHashMap<String, Art>();
            for (var binding : root.getAsJsonObject("bindings").entrySet()) {
                String id = binding.getKey();
                var data = binding.getValue().getAsJsonObject();
                String texture = data.get("texture").getAsString();
                int canvasWidth = data.get("canvasWidth").getAsInt();
                int canvasHeight = data.get("canvasHeight").getAsInt();
                var bounds = data.getAsJsonArray("alphaBounds");
                if (bounds.size() != 4) throw new IllegalStateException("Invalid weapon alpha bounds: " + id);
                int left = bounds.get(0).getAsInt(), top = bounds.get(1).getAsInt();
                int right = bounds.get(2).getAsInt(), bottom = bounds.get(3).getAsInt();
                if (!id.matches("Weapon_[A-Za-z0-9_]+")
                        || !texture.equals("Icons/RPG/WeaponArt/" + id + ".png")
                        || canvasWidth < 1 || canvasHeight < 1 || left < 0 || top < 0
                        || right > canvasWidth || bottom > canvasHeight
                        || right <= left || bottom <= top
                        || !data.get("sha256").getAsString().matches("[0-9a-f]{64}"))
                    throw new IllegalStateException("Invalid weapon art binding: " + id);
                arts.put(id, new Art(texture, canvasWidth, canvasHeight, left, top, right, bottom));
            }
            return Map.copyOf(arts);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot load weapon art catalog", error);
        }
    }
}
