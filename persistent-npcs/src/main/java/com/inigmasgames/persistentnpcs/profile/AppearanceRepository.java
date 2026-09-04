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
import java.util.function.Consumer;
import java.util.Optional;

/** Resolves Skin Swap-compatible exports without linking to or modifying Skin Swap. */
public final class AppearanceRepository {
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
        Path canonical = canonicalProfileDirectory(profileName)
                .resolve("SS_Skin_Character.json");
        if (!Files.isRegularFile(canonical)) {
            throw new IllegalStateException("Authoritative profile-local skin is missing: "
                    + canonical.getFileName());
        }
        return canonical;
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
}
