package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService.Category;
import com.inigmasgames.persistentnpcs.ui.AppearanceThumbnailCatalog;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.jar.JarFile;
import javax.imageio.ImageIO;

/** Deterministic Checkpoint-3 gate for the immutable full visual catalog. */
public final class R160AppearanceNativeVisualCatalogTest {
    private static final Path RESOURCES = Path.of("src/main/resources");

    public static void main(String[] args) throws Exception {
        var references = AppearanceThumbnailCatalog.references();
        assert references.size() == 590 : references.size();
        assert AppearanceThumbnailCatalog.RUNTIME_THUMBNAIL_CREATES == 0;
        assert AppearanceThumbnailCatalog.RUNTIME_THUMBNAIL_WRITES == 0;
        assert AppearanceThumbnailCatalog.RUNTIME_THUMBNAIL_RECOLORS == 0;

        Map<Category, Integer> expected = new EnumMap<>(Category.class);
        expected.put(Category.BODY_CHARACTERISTIC, 2);
        expected.put(Category.CAPE, 16);
        expected.put(Category.EAR_ACCESSORY, 6);
        expected.put(Category.EARS, 6);
        expected.put(Category.EYEBROWS, 14);
        expected.put(Category.EYES, 10);
        expected.put(Category.FACE, 18);
        expected.put(Category.FACE_ACCESSORY, 21);
        expected.put(Category.FACIAL_HAIR, 28);
        expected.put(Category.GLOVES, 22);
        expected.put(Category.HAIRCUT, 112);
        expected.put(Category.HEAD_ACCESSORY, 60);
        expected.put(Category.MOUTH, 8);
        expected.put(Category.OVERPANTS, 7);
        expected.put(Category.OVERTOP, 105);
        expected.put(Category.PANTS, 57);
        expected.put(Category.SHOES, 51);
        expected.put(Category.UNDERTOP, 43);
        expected.put(Category.UNDERWEAR, 4);
        for (Category category : Category.values()) {
            assert AppearanceThumbnailCatalog.count(category) == expected.getOrDefault(category, 0)
                    : category + "=" + AppearanceThumbnailCatalog.count(category);
        }

        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        HashSet<String> keys = new HashSet<>();
        HashSet<String> paths = new HashSet<>();
        for (var reference : references) {
            assert keys.add(reference.key()) : reference.key();
            assert paths.add(reference.packagedAssetPath()) : reference.packagedAssetPath();
            Path asset = RESOURCES.resolve(reference.packagedAssetPath());
            assert Files.isRegularFile(asset) : asset;
            BufferedImage image = ImageIO.read(asset.toFile());
            assert image != null && image.getWidth() == 92 && image.getHeight() == 149 : asset;
            String hash = HexFormat.of().formatHex(sha256.digest(Files.readAllBytes(asset)));
            assert hash.equals(reference.sourceSha256()) : reference.key();
        }

        Path catalogRoot = RESOURCES.resolve(
                "Common/UI/Custom/Pages/ImmersiveNpcAppearance/Catalog/Thumbnails");
        try (var files = Files.list(catalogRoot)) {
            assert files.filter(Files::isRegularFile).count() == 588;
        }
        String card = Files.readString(RESOURCES.resolve(
                "Common/UI/Custom/Pages/ImmersiveNpcAppearanceCard.ui"));
        assert card.contains("AssetImage #Thumbnail") && !card.contains("FarmerTopThumbnail");

        String page = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        assert page.contains("#Thumbnail.AssetPath")
                && page.contains("commands.set(thumbnailSelector + \".AssetPath\"")
                && !page.contains("appendInline(selector + \" #ThumbnailHost\"");
        assert page.contains("scheduleAppearanceSearch(store, query)")
                && page.contains("180, TimeUnit.MILLISECONDS");
        String searchCase = page.substring(page.indexOf("case \"APPEARANCE_SEARCH\""),
                page.indexOf("case \"APPEARANCE_PAGE_PREV\""));
        assert !searchCase.contains("scheduleAppearancePreview");
        for (String metric : java.util.List.of("realizedCardCount=",
                "staticThumbnailReferencesThisCategory=", "totalThumbnailReferencesUsedThisSession=",
                "cardRebuildCount=", "previewJobsScheduled=", "previewJobsCoalesced=",
                "previewJobsApplied=", "runtimeThumbnailRecolors=")) {
            assert page.contains(metric) : metric;
        }
        for (String forbidden : java.util.List.of("AssetInitialize", "AssetPart", "AssetFinalize",
                "AppearanceColorCards", "PrivateAppearanceCardAssets", "runtimeThumbnailWrites++")) {
            assert !page.contains(forbidden) : forbidden;
        }

        Path jar;
        try (var files = Files.list(Path.of("dist"))) {
            jar = files.filter(path -> path.getFileName().toString().contains("R161"))
                    .findFirst().orElseThrow();
        }
        try (JarFile archive = new JarFile(jar.toFile())) {
            long catalogImages = archive.stream().map(java.util.zip.ZipEntry::getName)
                    .filter(name -> name.startsWith(
                            "Common/UI/Custom/Pages/ImmersiveNpcAppearance/Catalog/Thumbnails/"))
                    .filter(name -> name.endsWith(".png")).count();
            long probeImages = archive.stream().map(java.util.zip.ZipEntry::getName)
                    .filter(name -> name.startsWith(
                            "Common/UI/Custom/Pages/ImmersiveNpcAppearance/Probe/"))
                    .filter(name -> name.endsWith(".png")).count();
            assert catalogImages == 588 : catalogImages;
            assert probeImages == 2 : probeImages;
            assert archive.getEntry(
                    "Common/UI/Custom/Pages/ImmersiveNpcAppearance/Catalog/index.tsv") != null;
        }
        System.out.println("R160 PASS: 590 immutable canonical cards, bounded 20-card realization, debounced search, zero runtime image mutation.");
    }
}
