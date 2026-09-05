package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService.Category;
import com.inigmasgames.persistentnpcs.ui.AppearanceThumbnailProbe;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.jar.JarFile;
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

        String card = Files.readString(RESOURCES.resolve(
                "Common/UI/Custom/Pages/ImmersiveNpcAppearanceCard.ui"));
        for (var reference : references) {
            assert card.contains("AssetImage #" + reference.elementId());
            assert card.contains("FallbackTexturePath: \"" + reference.uiTexturePath() + "\"");
        }
        String page = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        assert page.contains("APPEARANCE_THUMBNAIL_REFERENCE");
        assert page.contains("APPEARANCE_THUMBNAIL_CARD_BUILT");
        assert page.contains("dynamicThumbnailCreates=");
        assert page.contains("runtimeThumbnailWrites=");
        for (String forbidden : java.util.List.of("AppearanceCardJobs", "AppearanceColorCards",
                "PrivateAppearanceCardAssets", "AssetInitialize", "AssetUpdate")) {
            assert !page.contains(forbidden) : forbidden;
        }
        Path jar;
        try (var files = Files.list(Path.of("dist"))) {
            jar = files.filter(path -> path.getFileName().toString().contains("R159"))
                    .findFirst().orElseThrow();
        }
        try (JarFile archive = new JarFile(jar.toFile())) {
            var probeEntries = archive.stream().map(java.util.zip.ZipEntry::getName)
                    .filter(name -> name.startsWith(
                            "Common/UI/Custom/Pages/ImmersiveNpcAppearance/Probe/"))
                    .filter(name -> !name.endsWith("/"))
                    .toList();
            assert probeEntries.size() == 2 : probeEntries;
            assert archive.getEntry("Common/UI/Custom/Pages/ImmersiveNpcAppearance/Thumbnails/") == null;
        }
        System.out.println("R159 PASS: exactly two immutable Undertop AssetImage cards; stable hashes; zero runtime creates/writes.");
    }
}
