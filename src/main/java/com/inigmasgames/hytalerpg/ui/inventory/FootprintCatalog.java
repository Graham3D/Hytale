package com.inigmasgames.hytalerpg.ui.inventory;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit installed-item bindings. Missing IDs have no implicit 1x1 fallback. */
public final class FootprintCatalog {
    public static final String RESOURCE = "/rpg/inventory/footprints-v1.json";
    // Exact catalog packaged by HyARPG R199; existing revision-3 bags retain its rectangles.
    public static final String R199_RESOURCE = "/rpg/inventory/footprints-r199-v3.json";
    private final int revision;
    private final String sourceAssetsSha256;
    private final Map<String, SpatialLayout.Size> sizes;

    private FootprintCatalog(int revision, String sourceAssetsSha256, Map<String, SpatialLayout.Size> sizes) {
        this.revision = revision;
        this.sourceAssetsSha256 = sourceAssetsSha256;
        this.sizes = Map.copyOf(sizes);
    }

    public static FootprintCatalog loadDefault() {
        return Catalogs.DEFAULT;
    }

    public static FootprintCatalog forRevision(int revision) {
        return switch (revision) {
            case 2 -> Catalogs.DEFAULT;
            case 3 -> Revision3.CATALOG;
            default -> throw new IllegalStateException("Unsupported spatial catalog revision " + revision);
        };
    }

    public static FootprintCatalog forBag(SpatialBagAggregate bag) {
        return forRevision(bag.catalogRevision());
    }

    private static final class Catalogs {
        private static final FootprintCatalog DEFAULT = load(RESOURCE, 2);
    }

    private static final class Revision3 {
        private static final FootprintCatalog CATALOG = load(R199_RESOURCE, 3);
    }

    private static FootprintCatalog load(String resource, int expectedRevision) {
        try (var stream = FootprintCatalog.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("Missing footprint catalog " + resource);
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("schemaVersion").getAsInt() != 1) throw new IllegalStateException("Unsupported footprint schema");
            int revision = root.get("catalogRevision").getAsInt();
            if (revision != expectedRevision) throw new IllegalStateException("Unexpected footprint revision in " + resource);
            String hash = root.get("sourceAssetsSha256").getAsString();
            if (!hash.matches("[A-Fa-f0-9]{64}")) throw new IllegalStateException("Invalid assets hash");
            var sizes = new LinkedHashMap<String, SpatialLayout.Size>();
            for (var binding : root.getAsJsonObject("bindings").entrySet()) {
                String id = binding.getKey();
                var data = binding.getValue().getAsJsonObject();
                int width = data.get("width").getAsInt(), height = data.get("height").getAsInt();
                if (id.isBlank() || width < 1 || height < 1 || width > 18 || height > 4
                        || data.get("rule").getAsString().isBlank() || data.get("source").getAsString().isBlank())
                    throw new IllegalStateException("Invalid footprint binding: " + id);
                sizes.put(id, new SpatialLayout.Size(width, height));
            }
            return new FootprintCatalog(revision, hash, sizes);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot load footprint catalog", error);
        }
    }

    public int revision() { return revision; }
    public String sourceAssetsSha256() { return sourceAssetsSha256; }
    public int bindingCount() { return sizes.size(); }
    public SpatialLayout.Size size(String baseItemId) {
        // Local RPG accessory; the hashed source catalog covers the 5,608 installed stock IDs.
        if ("RPG_Ring_Copper".equals(baseItemId)) return new SpatialLayout.Size(1, 1);
        return sizes.get(baseItemId);
    }
}
