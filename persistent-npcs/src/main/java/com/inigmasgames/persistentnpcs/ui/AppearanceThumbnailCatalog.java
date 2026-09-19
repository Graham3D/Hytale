package com.inigmasgames.persistentnpcs.ui;

import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService.Category;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable build-time catalog of canonical Hytale cosmetic card artwork.
 *
 * <p>The index names packaged resources only. It never creates, recolors,
 * writes, registers, or assigns session identities to images at runtime.</p>
 */
public final class AppearanceThumbnailCatalog {
    public static final int EXPECTED_REFERENCE_COUNT = 590;
    public static final int WIDTH = 92;
    public static final int HEIGHT = 149;
    public static final int RUNTIME_THUMBNAIL_CREATES = 0;
    public static final int RUNTIME_THUMBNAIL_WRITES = 0;
    public static final int RUNTIME_THUMBNAIL_RECOLORS = 0;
    private static final String INDEX =
            "/Common/UI/Custom/Pages/ImmersiveNpcAppearance/Catalog/index.tsv";

    public record Reference(Category category, String cosmeticId, String uiTexturePath,
            String packagedAssetPath, int width, int height, String sourceSha256) {
        public Reference {
            if (category == null || cosmeticId == null || cosmeticId.isBlank()
                    || uiTexturePath == null || uiTexturePath.isBlank()
                    || !uiTexturePath.matches("UI/Custom/Pages/ImmersiveNpcAppearance/(Probe|Catalog/Thumbnails)/[A-Za-z0-9_.-]+\\.png")
                    || packagedAssetPath == null || packagedAssetPath.isBlank()
                    || width != WIDTH || height != HEIGHT || sourceSha256 == null
                    || !sourceSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Complete immutable thumbnail reference required");
            }
        }

        public String key() { return category.name() + ":" + cosmeticId; }
    }

    private static final List<Reference> REFERENCES;
    private static final Map<String, Reference> BY_KEY;
    private static final Map<Category, Integer> COUNTS;

    static {
        List<Reference> references = new ArrayList<>();
        Map<String, Reference> byKey = new LinkedHashMap<>();
        EnumMap<Category, Integer> counts = new EnumMap<>(Category.class);
        try (var stream = AppearanceThumbnailCatalog.class.getResourceAsStream(INDEX)) {
            if (stream == null) throw new IllegalStateException("Missing packaged thumbnail index: " + INDEX);
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    String[] parts = line.split("\\t", -1);
                    if (parts.length != 5) throw new IllegalStateException("Malformed thumbnail index row");
                    Category category = Category.valueOf(parts[0]);
                    Reference reference = new Reference(category, parts[1], parts[2], parts[3],
                            WIDTH, HEIGHT, parts[4]);
                    if (byKey.putIfAbsent(reference.key(), reference) != null) {
                        throw new IllegalStateException("Duplicate thumbnail reference: " + reference.key());
                    }
                    references.add(reference);
                    counts.merge(category, 1, Integer::sum);
                }
            }
        } catch (IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
        if (references.size() != EXPECTED_REFERENCE_COUNT) {
            throw new ExceptionInInitializerError("Expected " + EXPECTED_REFERENCE_COUNT
                    + " canonical thumbnails; found " + references.size());
        }
        REFERENCES = List.copyOf(references);
        BY_KEY = Map.copyOf(byKey);
        COUNTS = Map.copyOf(counts);
    }

    private AppearanceThumbnailCatalog() { }

    public static List<Reference> references() { return REFERENCES; }

    public static Optional<Reference> find(Category category, String cosmeticId) {
        if (category == null || cosmeticId == null || cosmeticId.isBlank()) return Optional.empty();
        return Optional.ofNullable(BY_KEY.get(category.name() + ":" + cosmeticId));
    }

    public static int count(Category category) {
        return category == null ? 0 : COUNTS.getOrDefault(category, 0);
    }

    /** True only when the indexed immutable image is present in this exact plugin artifact. */
    public static boolean packagedAssetPresent(Reference reference) {
        return reference != null && AppearanceThumbnailCatalog.class.getResource(
                "/" + reference.packagedAssetPath()) != null;
    }
}
