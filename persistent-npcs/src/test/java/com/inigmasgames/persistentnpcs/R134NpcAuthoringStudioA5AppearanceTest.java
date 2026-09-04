package com.inigmasgames.persistentnpcs;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceAuthoringService;
import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService;
import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceDraft;
import com.inigmasgames.persistentnpcs.appearance.NpcSkinCodecAdapter;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.profile.AppearanceRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Deterministic A5 gate: registry IDs, lossless drafts, conflicts, and UI safety. */
public final class R134NpcAuthoringStudioA5AppearanceTest {
    private R134NpcAuthoringStudioA5AppearanceTest() { }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("r134-appearance-");
        try {
            Path plugin = root.resolve("ImmersiveNPCs");
            Path profile = plugin.resolve("profiles").resolve("A5TestNpc");
            Files.createDirectories(profile);
            Path skinPath = profile.resolve("SS_Skin_Character.json");
            JsonObject raw = new JsonObject();
            raw.addProperty("bodyCharacteristic", "Body0");
            raw.addProperty("face", "Face0");
            raw.addProperty("eyes", "Eyes0.Brown");
            raw.addProperty("ears", "Ears0");
            raw.addProperty("mouth", "Mouth0");
            raw.addProperty("underwear", "Underwear0.Blue");
            raw.addProperty("futureAssetPackField", "must-survive");
            Files.writeString(skinPath, JsonFiles.GSON.toJson(raw), StandardCharsets.UTF_8);

            NpcSkinCodecAdapter adapter = new NpcSkinCodecAdapter(
                    new NpcSkinCodecAdapter.RuntimeApi() {
                        @Override public com.hypixel.hytale.protocol.PlayerSkin parse(String json) {
                            return JsonFiles.GSON.fromJson(json,
                                    com.hypixel.hytale.protocol.PlayerSkin.class);
                        }
                        @Override public void validate(
                                com.hypixel.hytale.protocol.PlayerSkin skin) {
                            if (skin.bodyCharacteristic == null || skin.face == null
                                    || skin.eyes == null || skin.ears == null
                                    || skin.mouth == null || skin.underwear == null) {
                                throw new IllegalArgumentException("required test cosmetic missing");
                            }
                        }
                        @Override public com.hypixel.hytale.server.core.asset.type.model.config.Model
                                createModel(com.hypixel.hytale.protocol.PlayerSkin skin) {
                            return null; // persistence tests never dereference the model
                        }
                    });
            NpcAppearanceCatalogService catalog = fakeCatalog();
            NpcAppearanceAuthoringService service = new NpcAppearanceAuthoringService(
                    new AppearanceRepository(plugin, System.out::println), catalog, adapter,
                    System.out::println);

            System.out.println("R134 stage=lossless-draft-and-save");
            UUID stable = UUID.randomUUID();
            NpcAppearanceDraft draft = service.begin("A5TestNpc", stable,
                    UUID.randomUUID(), 9);
            service.select(draft, NpcAppearanceCatalogService.Category.FACE,
                    "Face1", null, null);
            assert draft.dirty();
            var saved = service.save(draft, UUID.randomUUID());
            JsonObject committed = com.google.gson.JsonParser.parseString(
                    Files.readString(skinPath)).getAsJsonObject();
            assert committed.get("face").getAsString().equals("Face1");
            assert committed.get("futureAssetPackField").getAsString()
                    .equals("must-survive") : "Unknown skin JSON must round-trip unchanged";
            assert Files.isRegularFile(saved.rollback());
            assert Files.isRegularFile(profile.resolve("skin-authoring-revision.json"));
            assert Files.isRegularFile(profile.resolve("appearance-authoring-audit.jsonl"));

            System.out.println("R134 stage=optimistic-conflict-and-invalid-selection");
            NpcAppearanceDraft stale = service.begin("A5TestNpc", stable,
                    UUID.randomUUID(), 10);
            committed.addProperty("externalWriter", true);
            Files.writeString(skinPath, JsonFiles.GSON.toJson(committed));
            service.select(stale, NpcAppearanceCatalogService.Category.FACE,
                    "Face0", null, null);
            boolean conflict = false;
            try { service.save(stale, UUID.randomUUID()); }
            catch (NpcAppearanceAuthoringService.RevisionConflictException expected) {
                conflict = true;
            }
            assert conflict : "Stale appearance drafts must not overwrite another writer";
            boolean invalid = false;
            try { service.select(stale, NpcAppearanceCatalogService.Category.FACE,
                    "INVENTED_ASSET", null, null); }
            catch (IllegalArgumentException expected) { invalid = true; }
            assert invalid : "Client-supplied IDs must be admitted by the pinned registry";

            System.out.println("R134 stage=missing-cosmetic-retention");
            NpcSkinCodecAdapter missingAdapter = new NpcSkinCodecAdapter(
                    new NpcSkinCodecAdapter.RuntimeApi() {
                        @Override public com.hypixel.hytale.protocol.PlayerSkin parse(String json) {
                            return JsonFiles.GSON.fromJson(json,
                                    com.hypixel.hytale.protocol.PlayerSkin.class);
                        }
                        @Override public void validate(
                                com.hypixel.hytale.protocol.PlayerSkin skin) {
                            throw new IllegalArgumentException("simulated removed asset");
                        }
                        @Override public com.hypixel.hytale.server.core.asset.type.model.config.Model
                                createModel(com.hypixel.hytale.protocol.PlayerSkin skin) {
                            return null;
                        }
                    });
            var retained = missingAdapter.read(skinPath);
            assert retained.raw().get("futureAssetPackField").getAsString()
                    .equals("must-survive")
                    : "Removed registry assets and extensions must remain inspectable";
            boolean missingRejectedAtAuthorityBoundary = false;
            try { missingAdapter.readValidated(skinPath); }
            catch (IllegalArgumentException expected) {
                missingRejectedAtAuthorityBoundary = true;
            }
            assert missingRejectedAtAuthorityBoundary
                    : "Invalid removed assets must still be rejected before commit/model creation";

            System.out.println("R134 stage=bounded-catalog-and-ui-contract");
            var page1 = catalog.query(NpcAppearanceCatalogService.Category.FACE, "", 0);
            var page2 = catalog.query(NpcAppearanceCatalogService.Category.FACE, "", 1);
            assert page1.options().size() == 12 && page2.options().size() == 2;
            assert catalog.query(NpcAppearanceCatalogService.Category.FACE,
                    "Face13", 0).totalMatches() == 1;
            String ui = source("src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcProfile.ui");
            int randomize = ui.indexOf("#AppearanceRandomizeButton");
            int reset = ui.indexOf("#AppearanceResetButton");
            int cancel = ui.indexOf("#AppearanceCancelButton");
            int save = ui.indexOf("#AppearanceSaveButton");
            assert ui.contains("#AppearanceEditorPage")
                    && ui.contains("#AppearancePreviewCharacter")
                    && randomize < reset && reset < cancel && cancel < save;
            assert !ui.contains("Common/Cosmetics/")
                    : "The plugin must not copy or embed Hytale-owned cosmetics assets";
            String preview = source("src/main/java/com/inigmasgames/persistentnpcs/ui/"
                    + "NpcMeshPreviewSession.java");
            assert preview.contains("authoritativeViewerEcsMutation=false")
                    && preview.contains("restoreAuthoritativeTarget")
                    && !preview.substring(preview.indexOf("applyAppearanceDraft"),
                            preview.indexOf("restoreAuthoritativeTarget"))
                            .contains("putComponent")
                    : "Draft previews must remain packet-only and restoration-safe";
            System.out.println("R134 NPC Authoring Studio A5 appearance gate passed.");
        } finally {
            deleteTree(root);
        }
    }

    private static NpcAppearanceCatalogService fakeCatalog() {
        EnumMap<NpcAppearanceCatalogService.Category,
                List<NpcAppearanceCatalogService.CosmeticOptionDescriptor>> all =
                        new EnumMap<>(NpcAppearanceCatalogService.Category.class);
        for (var category : NpcAppearanceCatalogService.Category.values()) {
            all.put(category, new ArrayList<>());
        }
        for (int index = 0; index < 14; index++) {
            all.get(NpcAppearanceCatalogService.Category.FACE).add(
                    new NpcAppearanceCatalogService.CosmeticOptionDescriptor(
                            NpcAppearanceCatalogService.Category.FACE, "Face" + index,
                            "Face" + index,
                            NpcAppearanceCatalogService.SourceKind.HYTALE_DEFAULT,
                            List.of("test"), Map.of("", List.of()), false,
                            "test validator"));
        }
        var identity = new NpcAppearanceCatalogService.CatalogIdentity(
                "TEST", "REGISTRY", "PACKS", "ADAPTER", Instant.EPOCH);
        return new NpcAppearanceCatalogService(
                new NpcAppearanceCatalogService.Snapshot(identity, all),
                System.out::println);
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
