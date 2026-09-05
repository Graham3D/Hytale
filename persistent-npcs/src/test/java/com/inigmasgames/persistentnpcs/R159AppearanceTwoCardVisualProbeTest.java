package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService.Category;
import com.inigmasgames.persistentnpcs.ui.AppearanceThumbnailProbe;
import com.inigmasgames.persistentnpcs.ui.AppearanceThumbnailCatalog;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.imageio.ImageIO;

/** Deterministic boundary for the immutable two-card Checkpoint-2 probe. */
public final class R159AppearanceTwoCardVisualProbeTest {
    private static final Path RESOURCES = Path.of("src/main/resources");

    public static void main(String[] args) throws Exception {
        var references = AppearanceThumbnailProbe.references();
        assert references.size() == 2 : "Only two real cards are allowed in Checkpoint 2";
        assert references.stream().allMatch(reference -> reference.category() == Category.UNDERTOP);
        assert references.stream().map(AppearanceThumbnailProbe.Reference::cosmeticId)
                .collect(java.util.stream.Collectors.toSet())
                .equals(java.util.Set.of("FarmerTop", "FlowerShirt"));
        assert AppearanceThumbnailProbe.DYNAMIC_THUMBNAIL_CREATES == 0;
        assert AppearanceThumbnailProbe.RUNTIME_THUMBNAIL_WRITES == 0;
        assert AppearanceThumbnailProbe.find(Category.UNDERTOP, "FarmerTop").isPresent();
        assert AppearanceThumbnailProbe.find(Category.UNDERTOP, "FlowerShirt").isPresent();
        assert AppearanceThumbnailProbe.find(Category.UNDERTOP, "Flowy_Shirt").isEmpty();
        assert AppearanceThumbnailProbe.find(Category.OVERTOP, "FarmerTop").isEmpty();

        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        for (var reference : references) {
            Path asset = RESOURCES.resolve(reference.packagedAssetPath());
            assert Files.isRegularFile(asset) : asset;
            BufferedImage image = ImageIO.read(asset.toFile());
            assert image != null;
            assert image.getWidth() == reference.width() : reference.cosmeticId();
            assert image.getHeight() == reference.height() : reference.cosmeticId();
            String hash = HexFormat.of().formatHex(sha256.digest(Files.readAllBytes(asset)));
            assert hash.equals(reference.sourceSha256()) : reference.cosmeticId() + " " + hash;
        }

        for (var reference : references) {
            var catalogReference = AppearanceThumbnailCatalog.find(
                    reference.category(), reference.cosmeticId()).orElseThrow();
            assert catalogReference.uiTexturePath().equals(reference.uiTexturePath());
            assert catalogReference.sourceSha256().equals(reference.sourceSha256());
        }
        String page = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        assert page.contains("APPEARANCE_THUMBNAIL_REFERENCE");
        assert page.contains("AppearanceThumbnailCatalog.find");
        assert page.contains("runtimeThumbnailCreates=");
        assert page.contains("runtimeThumbnailWrites=");
        for (String forbidden : java.util.List.of("AppearanceCardJobs", "AppearanceColorCards",
                "PrivateAppearanceCardAssets", "AssetInitialize", "AssetUpdate")) {
            assert !page.contains(forbidden) : forbidden;
        }
        System.out.println("R159 retained: the two connected-proven immutable Undertop assets and hashes are unchanged in the full catalog.");
    }
}
