package com.inigmasgames.persistentnpcs.profile;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;
import java.util.Optional;
import com.inigmasgames.persistentnpcs.appearance.NpcSkinCodecAdapter;

/** Resolves Skin Swap-compatible exports without linking to or modifying Skin Swap. */
public final class AppearanceRepository {
    public static final String DEFAULT_APPEARANCE_RESOURCE =
            "/defaults/profiles/neutral-appearance.json";
    private final Path saveRoot;
    private final Path profilesDirectory;
    private final Consumer<String> diagnostics;

    public AppearanceRepository(Path pluginDataDirectory, Consumer<String> diagnostics) {
        this.profilesDirectory = pluginDataDirectory.resolve("profiles")
                .toAbsolutePath().normalize();
        Path mods = pluginDataDirectory.toAbsolutePath().normalize().getParent();
        this.saveRoot = mods == null ? pluginDataDirectory : mods.getParent();
        this.diagnostics = diagnostics;
    }

    public boolean apply(
            String preset, Ref<EntityStore> npcRef, Store<EntityStore> store) {
        return apply(preset, npcRef, null, store);
    }

    public boolean apply(
            String preset, Ref<EntityStore> npcRef, NPCEntity npc,
            Store<EntityStore> store) {
        LoadedAppearance appearance = loadAppearance(preset);
        if (appearance == null) return false;
        store.putComponent(npcRef, ModelComponent.getComponentType(), appearance.model());
        store.putComponent(npcRef, PlayerSkinComponent.getComponentType(),
                new PlayerSkinComponent(appearance.skin()));
        updateMotionControllers(npc, npcRef, appearance.model(), store);
        diagnostics.accept("Appearance applied preset=" + preset
                + " mutation=STORE entity=" + npcRef);
        return true;
    }

    /** Queues the component mutation safely while an ECS system is processing. */
    public boolean queueApply(
            String preset,
            Ref<EntityStore> npcRef,
            CommandBuffer<EntityStore> commandBuffer) {
        LoadedAppearance appearance = loadAppearance(preset);
        if (appearance == null) return false;
        commandBuffer.putComponent(npcRef, ModelComponent.getComponentType(), appearance.model());
        commandBuffer.putComponent(npcRef, PlayerSkinComponent.getComponentType(),
                new PlayerSkinComponent(appearance.skin()));
        diagnostics.accept("Appearance applied preset=" + preset
                + " mutation=COMMAND_BUFFER entity=" + npcRef);
        return true;
    }

    public boolean queueApply(
            String preset,
            Ref<EntityStore> npcRef,
            NPCEntity npc,
            CommandBuffer<EntityStore> commandBuffer) {
        LoadedAppearance appearance = loadAppearance(preset);
        if (appearance == null) return false;
        commandBuffer.putComponent(npcRef, ModelComponent.getComponentType(), appearance.model());
        commandBuffer.putComponent(npcRef, PlayerSkinComponent.getComponentType(),
                new PlayerSkinComponent(appearance.skin()));
        updateMotionControllers(npc, npcRef, appearance.model(), commandBuffer);
        diagnostics.accept("Appearance applied preset=" + preset
                + " mutation=COMMAND_BUFFER entity=" + npcRef
                + " motionControllersUpdated=true");
        return true;
    }

    private static void updateMotionControllers(
            NPCEntity npc,
            Ref<EntityStore> npcRef,
            ModelComponent component,
            com.hypixel.hytale.component.ComponentAccessor<EntityStore> accessor) {
        if (npc != null && npc.getRole() != null) {
            npc.getRole().updateMotionControllers(npcRef, component.getModel(),
                    component.getModel().getBoundingBox(), accessor);
        }
    }

    private LoadedAppearance loadAppearance(String preset) {
        if (preset == null || preset.isBlank() || saveRoot == null) return null;
        Path skinFile = expectedSkinFile(preset);
        Path modelFile = expectedModelFile(preset);
        if (!Files.isRegularFile(skinFile)) {
            diagnostics.accept("Appearance preset unavailable preset=" + preset
                    + " expected=" + skinFile);
            return null;
        }
        try {
            String json = Files.readString(skinFile, StandardCharsets.UTF_8);
            PlayerSkin skin = CosmeticsModule.get().parseSkinFromJson(json);
            CosmeticsModule.get().validateSkin(skin);
            ModelComponent component = new ModelComponent(
                    CosmeticsModule.get().createModel(skin, 1.0f));
            diagnostics.accept("Appearance resolved preset=" + preset + " skin=" + skinFile
                    + " generatedModelPresent=" + Files.isRegularFile(modelFile));
            return new LoadedAppearance(skin, component);
        } catch (IOException | RuntimeException failure) {
            diagnostics.accept("Appearance apply failed preset=" + preset + " type="
                    + failure.getClass().getSimpleName() + " reason=" + failure.getMessage());
            return null;
        } catch (CosmeticsModule.InvalidSkinException failure) {
            diagnostics.accept("Appearance validation failed preset=" + preset
                    + " reason=" + failure.getMessage());
            return null;
        }
    }

    public Optional<Path> resolveSkinFile(String preset) {
        if (preset == null || preset.isBlank() || saveRoot == null) {
            return Optional.empty();
        }
        Path path = expectedSkinFile(preset);
        return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    /** Canonical profile-local skin authority used by the Authoring Studio. */
    public Path requireAuthoritativeSkinFile(String profileName) {
        Path canonical = authoritativeSkinFile(profileName);
        if (!Files.isRegularFile(canonical)) {
            throw new IllegalStateException("Authoritative profile-local skin is missing: "
                    + canonical.getFileName());
        }
        return canonical;
    }

    public Path authoritativeSkinFile(String profileName) {
        return canonicalProfileDirectory(profileName).resolve("SS_Skin_Character.json");
    }

    /**
     * Materializes the project-owned, entitlement-free neutral skin only when the canonical
     * profile skin is absent. Existing bytes are never changed here, including malformed data.
     */
    public synchronized AppearanceReadiness materializeDefaultIfMissing(
            String profileName, NpcSkinCodecAdapter adapter) {
        Path canonical = authoritativeSkinFile(profileName);
        if (Files.isRegularFile(canonical)) {
            try {
                NpcSkinCodecAdapter.SkinDocument document = adapter.readValidated(canonical);
                return new AppearanceReadiness(canonical, AppearanceState.EXISTING_VALID,
                        document, "Authoritative NPC appearance is valid.");
            } catch (RuntimeException invalid) {
                diagnostics.accept("NPC_AUTHORING_APPEARANCE_MALFORMED_PRESERVED path="
                        + canonical + " reason=" + safe(invalid));
                return new AppearanceReadiness(canonical, AppearanceState.MALFORMED_PRESERVED,
                        null, "Authored appearance is malformed and was preserved. "
                                + "A temporary neutral preview is active until repaired.");
            }
        }
        NpcSkinCodecAdapter.SkinDocument neutral = defaultSkinDocument(adapter);
        Path temporary = canonical.resolveSibling(canonical.getFileName() + ".default.tmp");
        try {
            Files.createDirectories(canonical.getParent());
            Files.writeString(temporary, NpcSkinCodecAdapter.serialized(neutral),
                    StandardCharsets.UTF_8);
            adapter.readValidated(temporary);
            try {
                Files.move(temporary, canonical, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, canonical);
            }
            diagnostics.accept("NPC_AUTHORING_DEFAULT_APPEARANCE_MATERIALIZED path="
                    + canonical + " source=" + DEFAULT_APPEARANCE_RESOURCE);
            return new AppearanceReadiness(canonical, AppearanceState.DEFAULT_MATERIALIZED,
                    neutral, "Neutral default appearance created; edit it when ready.");
        } catch (IOException failure) {
            throw new IllegalStateException("Could not materialize the neutral NPC appearance.",
                    failure);
        } finally {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    /** Creates the immutable packaged scaffold without requiring a bootstrapped cosmetics module. */
    public synchronized Path materializePackagedDefaultIfMissing(String profileName) {
        Path canonical = authoritativeSkinFile(profileName);
        if (Files.exists(canonical)) return canonical;
        Path temporary = canonical.resolveSibling(canonical.getFileName() + ".default.tmp");
        try (var input = AppearanceRepository.class.getResourceAsStream(
                DEFAULT_APPEARANCE_RESOURCE)) {
            if (input == null) throw new IllegalStateException(
                    "Packaged neutral NPC appearance is missing.");
            Files.createDirectories(canonical.getParent());
            Files.write(temporary, input.readAllBytes());
            try {
                Files.move(temporary, canonical, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, canonical);
            }
            return canonical;
        } catch (IOException failure) {
            throw new IllegalStateException("Could not create the neutral NPC appearance.",
                    failure);
        } finally {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    public NpcSkinCodecAdapter.SkinDocument defaultSkinDocument(NpcSkinCodecAdapter adapter) {
        try (var input = AppearanceRepository.class.getResourceAsStream(
                DEFAULT_APPEARANCE_RESOURCE)) {
            if (input == null) throw new IllegalStateException(
                    "Packaged neutral NPC appearance is missing.");
            Path temporary = Files.createTempFile("immersive-npc-neutral-", ".json");
            try {
                Files.write(temporary, input.readAllBytes());
                return adapter.readValidated(temporary);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read the neutral NPC appearance.", failure);
        }
    }

    public PreviewAppearance defaultPreviewAppearance(NpcSkinCodecAdapter adapter) {
        NpcSkinCodecAdapter.SkinDocument document = defaultSkinDocument(adapter);
        return new PreviewAppearance(document.skin(), adapter.createModel(document.skin()));
    }

    private static String safe(Throwable failure) {
        String message = failure == null ? null : failure.getMessage();
        return message == null || message.isBlank()
                ? (failure == null ? "UNKNOWN" : failure.getClass().getSimpleName())
                : message.replaceAll("\\s+", "_");
    }

    public Optional<Path> resolveModelFile(String preset) {
        if (preset == null || preset.isBlank() || saveRoot == null) {
            return Optional.empty();
        }
        Path path = expectedModelFile(preset);
        return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    /** Immutable display-only appearance used by the opt-in client preview probe. */
    public Optional<PreviewAppearance> resolvePreviewAppearance(String preset) {
        LoadedAppearance appearance = loadAppearance(preset);
        return appearance == null ? Optional.empty()
                : Optional.of(new PreviewAppearance(
                        appearance.skin(), appearance.model().getModel()));
    }

    private Path expectedSkinFile(String preset) {
        Path canonical = canonicalProfileDirectory(preset).resolve("SS_Skin_Character.json");
        if (Files.isRegularFile(canonical)) return canonical;
        return saveRoot.resolve("exports").resolve("skins").resolve(preset)
                .resolve("SS_SKIN_" + preset + ".json");
    }

    private Path expectedModelFile(String preset) {
        return saveRoot.resolve("exports").resolve("skins").resolve(preset)
                .resolve("SS_MODEL_" + preset + ".json");
    }

    public void validateSkinFile(Path skinFile) {
        if (skinFile == null || skinFile.getFileName() == null
                || !skinFile.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                        .endsWith(".json")
                || !Files.isRegularFile(skinFile)) {
            throw new IllegalArgumentException("Expected a valid .json skin file");
        }
        try {
            PlayerSkin skin = CosmeticsModule.get().parseSkinFromJson(
                    Files.readString(skinFile, StandardCharsets.UTF_8));
            CosmeticsModule.get().validateSkin(skin);
        } catch (IOException failure) {
            throw new IllegalArgumentException("Could not read selected skin JSON", failure);
        } catch (CosmeticsModule.InvalidSkinException failure) {
            throw new IllegalArgumentException("Invalid Hytale skin: "
                    + failure.getMessage(), failure);
        }
    }

    private Path canonicalProfileDirectory(String preset) {
        Path path = profilesDirectory.resolve(ProfileRepository.sanitizeProfileName(preset))
                .normalize();
        if (!path.startsWith(profilesDirectory)) {
            throw new IllegalArgumentException("Unsafe appearance profile directory");
        }
        return path;
    }

    private record LoadedAppearance(PlayerSkin skin, ModelComponent model) {}

    public record PreviewAppearance(
            PlayerSkin playerSkin,
            com.hypixel.hytale.server.core.asset.type.model.config.Model model) {
        public PreviewAppearance {
            if (model == null) throw new IllegalArgumentException("Preview model is required");
        }
    }

    public enum AppearanceState {
        EXISTING_VALID, DEFAULT_MATERIALIZED, MALFORMED_PRESERVED
    }

    public record AppearanceReadiness(Path path, AppearanceState state,
            NpcSkinCodecAdapter.SkinDocument document, String message) {
        public boolean degraded() { return state == AppearanceState.MALFORMED_PRESERVED; }
    }
}
