package com.inigmasgames.persistentnpcs.ui;

import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService.Category;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Checkpoint-2 allow-list for the two immutable cosmetic card references.
 *
 * <p>These are ordinary packaged UI resources. They are never generated,
 * recolored, assigned a session-specific ID, or written at runtime.</p>
 */
public final class AppearanceThumbnailProbe {
    public static final int DYNAMIC_THUMBNAIL_CREATES = 0;
    public static final int RUNTIME_THUMBNAIL_WRITES = 0;

    public record Reference(Category category, String cosmeticId, String elementId,
            String uiTexturePath, String packagedAssetPath, int width, int height,
            String sourceSha256) {
        public Reference {
            if (category == null || cosmeticId == null || cosmeticId.isBlank()
                    || elementId == null || elementId.isBlank()
                    || uiTexturePath == null || uiTexturePath.isBlank()
                    || packagedAssetPath == null || packagedAssetPath.isBlank()
                    || width <= 0 || height <= 0 || sourceSha256 == null
                    || sourceSha256.length() != 64) {
                throw new IllegalArgumentException("Complete immutable thumbnail reference required");
            }
        }

        public String key() { return category.name() + ":" + cosmeticId; }
    }

    private static final List<Reference> REFERENCES = List.of(
            new Reference(Category.UNDERTOP, "FarmerTop", "FarmerTopThumbnail",
                    "UI/Custom/Pages/ImmersiveNpcAppearance/Probe/UNDERTOP-FarmerTop.png",
                    "Common/UI/Custom/Pages/ImmersiveNpcAppearance/Probe/UNDERTOP-FarmerTop.png",
                    92, 149,
                    "059dc8c47afe08aac235ef33eff22f216751b046103cc208200cb3e3523cc219"),
            new Reference(Category.UNDERTOP, "FlowerShirt", "FlowerShirtThumbnail",
                    "UI/Custom/Pages/ImmersiveNpcAppearance/Probe/UNDERTOP-FlowerShirt.png",
                    "Common/UI/Custom/Pages/ImmersiveNpcAppearance/Probe/UNDERTOP-FlowerShirt.png",
                    92, 149,
                    "fb59f44840be4de56c2bdfe82221889e18308669a409f54d3c009d27a02df559"));

    private static final Map<String, Reference> BY_KEY = Map.of(
            REFERENCES.get(0).key(), REFERENCES.get(0),
            REFERENCES.get(1).key(), REFERENCES.get(1));

    private AppearanceThumbnailProbe() { }

    public static List<Reference> references() { return REFERENCES; }

    public static Optional<Reference> find(Category category, String cosmeticId) {
        if (category == null || cosmeticId == null) return Optional.empty();
        return Optional.ofNullable(BY_KEY.get(category.name() + ":" + cosmeticId));
    }
}
