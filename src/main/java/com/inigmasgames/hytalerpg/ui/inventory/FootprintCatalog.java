package com.inigmasgames.hytalerpg.ui.inventory;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit installed-item bindings. Missing IDs have no implicit 1x1 fallback. */
public final class FootprintCatalog {
    public static final String RESOURCE = "/rpg/inventory/footprints-v1.json";
    private final int revision;
    private final String sourceAssetsSha256;
    private final Map<String, SpatialLayout.Size> sizes;
    private final Map<String, SpatialLayout.Size> previousSizes;

    private FootprintCatalog(int revision, String sourceAssetsSha256, Map<String, SpatialLayout.Size> sizes,
                             Map<String, SpatialLayout.Size> previousSizes) {
        this.revision = revision;
        this.sourceAssetsSha256 = sourceAssetsSha256;
        this.sizes = Map.copyOf(sizes);
        this.previousSizes = Map.copyOf(previousSizes);
    }

    public static FootprintCatalog loadDefault() {
        try (var stream = FootprintCatalog.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing footprint catalog " + RESOURCE);
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("schemaVersion").getAsInt() != 1) throw new IllegalStateException("Unsupported footprint schema");
            int revision = root.get("catalogRevision").getAsInt();
            if (revision <= 0) throw new IllegalStateException("Invalid footprint revision");
            String hash = root.get("sourceAssetsSha256").getAsString();
            if (!hash.matches("[A-Fa-f0-9]{64}")) throw new IllegalStateException("Invalid assets hash");
            var sizes = new LinkedHashMap<String, SpatialLayout.Size>();
            var previousSizes = new LinkedHashMap<String, SpatialLayout.Size>();
            for (var binding : root.getAsJsonObject("bindings").entrySet()) {
                String id = binding.getKey();
                var data = binding.getValue().getAsJsonObject();
                int width = data.get("width").getAsInt(), height = data.get("height").getAsInt();
                if (id.isBlank() || width < 1 || height < 1 || width > 18 || height > 4
                        || data.get("rule").getAsString().isBlank() || data.get("source").getAsString().isBlank())
                    throw new IllegalStateException("Invalid footprint binding: " + id);
                sizes.put(id, new SpatialLayout.Size(width, height));
                if (data.has("previousWidth") || data.has("previousHeight")) {
                    if (!data.has("previousWidth") || !data.has("previousHeight"))
                        throw new IllegalStateException("Incomplete previous footprint: " + id);
                    int previousWidth = data.get("previousWidth").getAsInt();
                    int previousHeight = data.get("previousHeight").getAsInt();
                    if (previousWidth < 1 || previousHeight < 1 || previousWidth > 18 || previousHeight > 4
                            || previousWidth == width && previousHeight == height)
                        throw new IllegalStateException("Invalid previous footprint: " + id);
                    previousSizes.put(id, new SpatialLayout.Size(previousWidth, previousHeight));
                }
            }
            return new FootprintCatalog(revision, hash, sizes, previousSizes);
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
    public SpatialLayout.Size previousSize(String baseItemId) {
        return previousSizes.getOrDefault(baseItemId, size(baseItemId));
    }
}
