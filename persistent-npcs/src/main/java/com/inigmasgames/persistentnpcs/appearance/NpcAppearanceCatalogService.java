package com.inigmasgames.persistentnpcs.appearance;

import com.hypixel.hytale.server.core.cosmetics.CosmeticRegistry;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinGradientSet;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPart;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPartTexture;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Immutable, bounded view of the current server cosmetics registry. The Hytale
 * registry is the only authority; this class never scans files or invents IDs.
 */
public final class NpcAppearanceCatalogService {
    public static final String AUDITED_HYTALE_BUILD =
            "0.6.3/ff802bf5a538f7e4b1df43a575c72f9d2bebb504";
    public static final String ADAPTER_VERSION = "R134-SKIN-CODEC-V1";
    public static final int PAGE_SIZE = 20;

    public enum PrimaryCategory { BODY, FACE, HAIR, EYES, CLOTHING, ACCESSORIES }

    public enum Category {
        BODY_CHARACTERISTIC(PrimaryCategory.BODY, "Body", "bodyCharacteristic", true),
        UNDERWEAR(PrimaryCategory.BODY, "Underwear", "underwear", true),
        SKIN_FEATURE(PrimaryCategory.BODY, "Skin Feature", "skinFeature", false),
        FACE(PrimaryCategory.FACE, "Face", "face", true),
        EARS(PrimaryCategory.FACE, "Ears", "ears", true),
        MOUTH(PrimaryCategory.FACE, "Mouth", "mouth", true),
        EYEBROWS(PrimaryCategory.FACE, "Eyebrows", "eyebrows", false),
        FACIAL_HAIR(PrimaryCategory.FACE, "Facial Hair", "facialHair", false),
        HAIRCUT(PrimaryCategory.HAIR, "Haircut", "haircut", false),
        EYES(PrimaryCategory.EYES, "Eyes", "eyes", true),
        PANTS(PrimaryCategory.CLOTHING, "Pants", "pants", false),
        OVERPANTS(PrimaryCategory.CLOTHING, "Overpants", "overpants", false),
        UNDERTOP(PrimaryCategory.CLOTHING, "Undertop", "undertop", false),
        OVERTOP(PrimaryCategory.CLOTHING, "Overtop", "overtop", false),
        SHOES(PrimaryCategory.CLOTHING, "Shoes", "shoes", false),
        GLOVES(PrimaryCategory.CLOTHING, "Gloves", "gloves", false),
        CAPE(PrimaryCategory.CLOTHING, "Cape", "cape", false),
        HEAD_ACCESSORY(PrimaryCategory.ACCESSORIES, "Head", "headAccessory", false),
        FACE_ACCESSORY(PrimaryCategory.ACCESSORIES, "Face", "faceAccessory", false),
        EAR_ACCESSORY(PrimaryCategory.ACCESSORIES, "Ears", "earAccessory", false);

        private final PrimaryCategory primary;
        private final String label;
        private final String skinField;
        private final boolean required;

        Category(PrimaryCategory primary, String label, String skinField, boolean required) {
            this.primary = primary;
            this.label = label;
            this.skinField = skinField;
            this.required = required;
        }

        public PrimaryCategory primary() { return primary; }
        public String label() { return label; }
        public String skinField() { return skinField; }
        public boolean required() { return required; }
    }

    public enum SourceKind { HYTALE_DEFAULT, ENABLED_REGISTRY_EXTENSION }

    public record CosmeticOptionDescriptor(
            Category category,
            String cosmeticId,
            String displayName,
            SourceKind source,
            List<String> tags,
            Map<String, List<String>> colorsByVariant,
            boolean removable,
            String compatibility) {
        public CosmeticOptionDescriptor {
            tags = List.copyOf(tags == null ? List.of() : tags);
            LinkedHashMap<String, List<String>> colors = new LinkedHashMap<>();
            if (colorsByVariant != null) colorsByVariant.forEach(
                    (key, value) -> colors.put(key, List.copyOf(value)));
            colorsByVariant = Map.copyOf(colors);
            compatibility = compatibility == null ? "Registry validator" : compatibility;
        }

        public List<String> variants() {
            return colorsByVariant.keySet().stream()
                    .filter(value -> !value.isBlank()).sorted().toList();
        }

        public List<String> colors(String variant) {
            List<String> direct = colorsByVariant.get(variant == null ? "" : variant);
            if (direct != null) return direct;
            return colorsByVariant.values().stream().findFirst().orElse(List.of());
        }

        public String encoded(String requestedColor, String requestedVariant) {
            if (cosmeticId.isBlank()) return null;
            if (category == Category.BODY_CHARACTERISTIC || category == Category.FACE
                    || category == Category.EARS || category == Category.MOUTH) {
                return cosmeticId;
            }
            String variant = choose(variants(), requestedVariant);
            String color = choose(colors(variant), requestedColor);
            if (color == null || color.isBlank()) {
                throw new IllegalArgumentException(displayName
                        + " has no registry-backed color/texture choice.");
            }
            return cosmeticId + "." + color
                    + (variant == null || variant.isBlank() ? "" : "." + variant);
        }

        private static String choose(List<String> values, String requested) {
            if (requested != null && values.contains(requested)) return requested;
            return values.isEmpty() ? null : values.getFirst();
        }
    }

    public record CatalogIdentity(String hytaleBuildId, String registryHash,
            String enabledAssetPackSetHash, String adapterVersion, Instant capturedAt) { }

    /**
     * Presentation-only audit of the native cosmetic icon inputs. Hytale 0.6.3
     * exposes no server-side icon property on PlayerSkinPart, so iconPath is
     * intentionally NONE while model/material/framing inputs remain observable.
     */
    public record NativeIconPresentation(String cosmeticId, String modelVariant,
            String iconPath, String framing, String textureGradient,
            boolean nativeIconAvailable) { }

    public record CatalogPage(Category category, String query, int pageIndex,
            int pageCount, int totalMatches, List<CosmeticOptionDescriptor> options) {
        public CatalogPage {
            options = List.copyOf(options);
            if (options.size() > PAGE_SIZE) throw new IllegalArgumentException("Appearance page exceeds 20 choices");
        }
        public int pageSize() { return PAGE_SIZE; }
        public int totalCount() { return totalMatches; }
        public List<CosmeticOptionDescriptor> descriptors() { return options; }
    }

    public record Snapshot(CatalogIdentity identity,
            Map<Category, List<CosmeticOptionDescriptor>> options) {
        public Snapshot {
            EnumMap<Category, List<CosmeticOptionDescriptor>> copy =
                    new EnumMap<>(Category.class);
            options.forEach((key, value) -> copy.put(key, List.copyOf(value)));
            options = Map.copyOf(copy);
        }
    }

    private final Consumer<String> diagnostics;
    private volatile Snapshot cached;

    public NpcAppearanceCatalogService(Consumer<String> diagnostics) {
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
    }

    /** Deterministic-test seam; production always uses the live-registry constructor. */
    public NpcAppearanceCatalogService(Snapshot pinned, Consumer<String> diagnostics) {
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
        this.cached = java.util.Objects.requireNonNull(pinned, "Pinned snapshot is required.");
    }

    public Snapshot snapshot() {
        Snapshot value = cached;
        if (value != null) return value;
        synchronized (this) {
            if (cached == null) cached = capture();
            return cached;
        }
    }

    public List<Category> categories(PrimaryCategory primary) {
        if (primary == null) return List.of();
        return List.of(Category.values()).stream()
                .filter(category -> category.primary() == primary).toList();
    }

    public CosmeticOptionDescriptor require(Category category, String cosmeticId) {
        if (category == null || cosmeticId == null) {
            throw new IllegalArgumentException("Appearance category and option are required.");
        }
        if (cosmeticId.isBlank() && !category.required()) {
            return new CosmeticOptionDescriptor(category, "", "None",
                    SourceKind.HYTALE_DEFAULT, List.of(), Map.of(), true,
                    "Optional registry field");
        }
        return snapshot().options().getOrDefault(category, List.of()).stream()
                .filter(option -> option.cosmeticId().equals(cosmeticId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "Cosmetic option is not present in the active registry snapshot."));
    }

    public CatalogPage query(Category category, String query, int requestedPage) {
        List<CosmeticOptionDescriptor> matches = queryAll(category, query);
        String normalized = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        int pageCount = Math.max(1, (matches.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        int from = Math.min(matches.size(), page * PAGE_SIZE);
        int to = Math.min(matches.size(), from + PAGE_SIZE);
        return new CatalogPage(category, normalized, page, pageCount, matches.size(),
                matches.subList(from, to));
    }

    /** Descriptor-only filtering. Never instantiate UI nodes or model/image objects here. */
    public List<CosmeticOptionDescriptor> queryAll(Category category, String query) {
        String normalized = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        List<CosmeticOptionDescriptor> matches = new ArrayList<>();
        if (category != null && !category.required() && normalized.isBlank()) {
            matches.add(require(category, ""));
        }
        for (CosmeticOptionDescriptor option : snapshot().options()
                .getOrDefault(category, List.of())) {
            String haystack = (option.cosmeticId() + " " + option.displayName() + " "
                    + option.source() + " " + String.join(" ", option.tags()))
                            .toLowerCase(Locale.ROOT);
            if (normalized.isBlank() || haystack.contains(normalized)) matches.add(option);
        }
        return List.copyOf(matches);
    }

    private Snapshot capture() {
        CosmeticsModule module = CosmeticsModule.get();
        if (module == null || module.getRegistry() == null) {
            throw new IllegalStateException("Hytale cosmetics registry is not available.");
        }
        CosmeticRegistry registry = module.getRegistry();
        EnumMap<Category, List<CosmeticOptionDescriptor>> all =
                new EnumMap<>(Category.class);
        StringBuilder registryMaterial = new StringBuilder();
        StringBuilder sourceMaterial = new StringBuilder();
        for (Category category : Category.values()) {
            List<CosmeticOptionDescriptor> options = registryParts(registry, category).values()
                    .stream().map(part -> describe(registry, category, part))
                    .sorted(Comparator.comparing(CosmeticOptionDescriptor::displayName,
                            String.CASE_INSENSITIVE_ORDER).thenComparing(
                                    CosmeticOptionDescriptor::cosmeticId))
                    .toList();
            all.put(category, options);
            for (CosmeticOptionDescriptor option : options) {
                registryMaterial.append(category).append('|').append(option.cosmeticId())
                        .append('|').append(option.displayName()).append('|')
                        .append(option.colorsByVariant()).append('|').append(option.tags())
                        .append('\n');
                sourceMaterial.append(option.source()).append(':')
                        .append(option.cosmeticId()).append('\n');
            }
        }
        CatalogIdentity identity = new CatalogIdentity(AUDITED_HYTALE_BUILD,
                sha256(registryMaterial.toString()), sha256(sourceMaterial.toString()),
                ADAPTER_VERSION, Instant.now());
        diagnostics.accept("NPC_AUTHORING_APPEARANCE_CATALOG_READY timestamp=" + Instant.now()
                + " build=" + identity.hytaleBuildId()
                + " registryHash=" + identity.registryHash()
                + " assetPackSetHash=" + identity.enabledAssetPackSetHash()
                + " categories=" + all.size()
                + " options=" + all.values().stream().mapToInt(List::size).sum());
        return new Snapshot(identity, all);
    }

    private static CosmeticOptionDescriptor describe(CosmeticRegistry registry,
            Category category, PlayerSkinPart part) {
        LinkedHashMap<String, List<String>> choices = new LinkedHashMap<>();
        Map<String, PlayerSkinPart.Variant> variants = part.getVariants();
        if (variants != null && !variants.isEmpty()) {
            variants.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                    choices.put(entry.getKey(), colors(registry, part,
                            entry.getValue().getTextures())));
        } else {
            choices.put("", colors(registry, part, part.getTextures()));
        }
        String display = displayName(part.getName(), part.getId());
        List<String> tags = part.getTags() == null ? List.of() : List.of(part.getTags());
        Set<String> compatibility = new LinkedHashSet<>();
        if (part.getHairType() != null) compatibility.add("hair=" + part.getHairType());
        if (part.getHeadAccessoryType() != null) {
            compatibility.add("headAccessory=" + part.getHeadAccessoryType());
        }
        if (part.doesRequireGenericHaircut()) compatibility.add("requiresGenericHaircut");
        return new CosmeticOptionDescriptor(category, part.getId(), display,
                part.isDefaultAsset() ? SourceKind.HYTALE_DEFAULT
                        : SourceKind.ENABLED_REGISTRY_EXTENSION,
                tags, choices, !category.required(), compatibility.isEmpty()
                        ? "Registry validator" : String.join(", ", compatibility));
    }

    private static List<String> colors(CosmeticRegistry registry, PlayerSkinPart part,
            Map<String, PlayerSkinPartTexture> direct) {
        Set<String> result = new LinkedHashSet<>();
        if (direct != null) result.addAll(direct.keySet());
        if (part.getGradientSet() != null) {
            PlayerSkinGradientSet gradients = registry.getGradientSets().get(part.getGradientSet());
            if (gradients != null && gradients.getGradients() != null) {
                result.addAll(gradients.getGradients().keySet());
            }
        }
        return result.stream().filter(value -> value != null && !value.isBlank())
                .sorted().toList();
    }

    /** Presentation-only native swatches; no guessed palette and no persisted format changes. */
    public List<String> swatchColors(Category category, String cosmeticId,
            String variantId, String colorId) {
        CosmeticsModule module = CosmeticsModule.get();
        if (module == null || module.getRegistry() == null) return List.of();
        CosmeticRegistry registry = module.getRegistry();
        PlayerSkinPart part = registryParts(registry, category).get(cosmeticId);
        if (part == null) return List.of();
        Map<String, PlayerSkinPartTexture> textures = part.getTextures();
        if (variantId != null && part.getVariants() != null && part.getVariants().containsKey(variantId)) {
            textures = part.getVariants().get(variantId).getTextures();
        }
        PlayerSkinPartTexture texture = textures == null ? null : textures.get(colorId);
        if (texture == null && part.getGradientSet() != null) {
            var gradients = registry.getGradientSets().get(part.getGradientSet());
            if (gradients != null && gradients.getGradients() != null) {
                texture = gradients.getGradients().get(colorId);
            }
        }
        return validSwatchColors(texture == null ? null : texture.getBaseColor());
    }

    public NativeIconPresentation nativeIconPresentation(Category category,
            String cosmeticId, String requestedVariant, String requestedColor) {
        CosmeticsModule module = CosmeticsModule.get();
        if (module == null || module.getRegistry() == null || category == null
                || cosmeticId == null || cosmeticId.isBlank()) {
            return new NativeIconPresentation(cosmeticId == null ? "" : cosmeticId,
                    "NONE", "NONE", categoryFraming(category), "NONE", false);
        }
        CosmeticRegistry registry = module.getRegistry();
        PlayerSkinPart part = registryParts(registry, category).get(cosmeticId);
        if (part == null) {
            return new NativeIconPresentation(cosmeticId, "NONE", "NONE",
                    categoryFraming(category), "NONE", false);
        }

        String variantId = requestedVariant == null ? "" : requestedVariant;
        PlayerSkinPart.Variant variant = null;
        if (part.getVariants() != null && !part.getVariants().isEmpty()) {
            variant = part.getVariants().get(variantId);
            if (variant == null) {
                variantId = part.getVariants().keySet().stream().sorted().findFirst().orElse("");
                variant = part.getVariants().get(variantId);
            }
        }
        String model = variant != null && variant.getModel() != null
                ? variant.getModel() : part.getModel();
        String greyscale = variant != null && variant.getGreyscaleTexture() != null
                ? variant.getGreyscaleTexture() : part.getGreyscaleTexture();
        Map<String, PlayerSkinPartTexture> textures = variant == null
                ? part.getTextures() : variant.getTextures();
        String colorId = requestedColor == null ? "" : requestedColor;
        PlayerSkinPartTexture texture = textures == null ? null : textures.get(colorId);
        if (texture == null && textures != null && !textures.isEmpty()) {
            colorId = textures.keySet().stream().sorted().findFirst().orElse("");
            texture = textures.get(colorId);
        }
        String material;
        if (texture != null && texture.getTexture() != null) {
            material = "texture=" + texture.getTexture() + " colorId=" + colorId;
        } else if (part.getGradientSet() != null && greyscale != null) {
            if (colorId.isBlank()) {
                PlayerSkinGradientSet gradients = registry.getGradientSets().get(part.getGradientSet());
                if (gradients != null && gradients.getGradients() != null) {
                    colorId = gradients.getGradients().keySet().stream().sorted()
                            .findFirst().orElse("");
                }
            }
            material = "greyscale=" + greyscale + " gradientSet=" + part.getGradientSet()
                    + " gradientId=" + colorId;
        } else {
            material = "NONE";
        }
        return new NativeIconPresentation(cosmeticId,
                (model == null ? "NONE" : model) + (variantId.isBlank() ? "" : "#" + variantId),
                "NONE", categoryFraming(category), material, false);
    }

    public static String categoryFraming(Category category) {
        if (category == null) return "UNKNOWN";
        return switch (category) {
            case PANTS, OVERPANTS -> "92x149:WAIST_TO_FEET";
            case UNDERTOP, OVERTOP -> "92x149:SHOULDERS_TO_WAIST";
            case SHOES -> "92x149:LOWER_LEGS_TO_FEET";
            case HAIRCUT, HEAD_ACCESSORY -> "92x149:HEAD_AND_SHOULDERS";
            case FACE, EYES, EYEBROWS, MOUTH, FACIAL_HAIR, FACE_ACCESSORY ->
                    "92x149:HEAD_CLOSEUP";
            case EARS, EAR_ACCESSORY -> "92x149:HEAD_THREE_QUARTER";
            case GLOVES -> "92x149:TORSO_ARMS_HANDS";
            case CAPE -> "92x149:REAR_BODY";
            default -> "92x149:FULL_BODY";
        };
    }

    public static List<String> validSwatchColors(String[] colors) {
        if (colors == null) return List.of();
        return java.util.Arrays.stream(colors)
                .filter(color -> color != null && color.matches("#[0-9a-fA-F]{6}"))
                .limit(2).toList();
    }

    private static Map<String, PlayerSkinPart> registryParts(
            CosmeticRegistry registry, Category category) {
        return switch (category) {
            case BODY_CHARACTERISTIC -> registry.getBodyCharacteristics();
            case UNDERWEAR -> registry.getUnderwear();
            case SKIN_FEATURE -> registry.getSkinFeatures();
            case FACE -> registry.getFaces();
            case EARS -> registry.getEars();
            case MOUTH -> registry.getMouths();
            case EYEBROWS -> registry.getEyebrows();
            case FACIAL_HAIR -> registry.getFacialHairs();
            case HAIRCUT -> registry.getHaircuts();
            case EYES -> registry.getEyes();
            case PANTS -> registry.getPants();
            case OVERPANTS -> registry.getOverpants();
            case UNDERTOP -> registry.getUndertops();
            case OVERTOP -> registry.getOvertops();
            case SHOES -> registry.getShoes();
            case GLOVES -> registry.getGloves();
            case CAPE -> registry.getCapes();
            case HEAD_ACCESSORY -> registry.getHeadAccessories();
            case FACE_ACCESSORY -> registry.getFaceAccessories();
            case EAR_ACCESSORY -> registry.getEarAccessories();
        };
    }

    private static String displayName(String registryName, String id) {
        String candidate = registryName == null || registryName.isBlank()
                || registryName.contains(".") ? id : registryName;
        if (candidate == null || candidate.isBlank()) return "Unnamed option";
        String separated = candidate.replace('_', ' ').replace('-', ' ')
                .replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ")
                .replaceAll("\\s+", " ").strip();
        if (separated.isBlank()) return "Unnamed option";
        return Character.toUpperCase(separated.charAt(0)) + separated.substring(1);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
