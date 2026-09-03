package com.inigmasgames.persistentnpcs.hytale;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.asset.builder.BuilderInfo;
import com.hypixel.hytale.server.npc.asset.builder.BuilderManager;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Bridges completed Immersive profiles into Hytale's native NPC Role builder cache. */
public final class ImmersiveNpcRoleService {
    private static final String TEMPLATE_RESOURCE =
            "/Server/NPC/Roles/ImmersiveNPCs/ImmersiveNPCs_Character.json";

    private final Path profilesDirectory;
    private final NpcProfileRegistry profiles;
    private final NativeRoleRegistrar registrar;
    private final Consumer<String> diagnostics;
    private final Map<String, String> profileNameByRole = new ConcurrentHashMap<>();

    public ImmersiveNpcRoleService(
            Path dataDirectory,
            NpcProfileRegistry profiles,
            NativeRoleRegistrar registrar,
            Consumer<String> diagnostics) {
        this.profilesDirectory = dataDirectory.toAbsolutePath().normalize().resolve("profiles");
        this.profiles = java.util.Objects.requireNonNull(profiles, "profiles");
        this.registrar = java.util.Objects.requireNonNull(registrar, "registrar");
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
    }

    public static ImmersiveNpcRoleService update6(
            Path dataDirectory, NpcProfileRegistry profiles, Consumer<String> diagnostics) {
        return new ImmersiveNpcRoleService(dataDirectory, profiles,
                new Update6NativeRoleRegistrar(), diagnostics);
    }

    public synchronized void registerAll() {
        for (NpcProfile profile : profiles.profiles()) {
            registerOrUpdate(profile);
        }
    }

    public synchronized String registerOrUpdate(NpcProfile profile) {
        NpcProfile validated = profile.validated();
        String roleName = ProfileRepository.sanitizeProfileName(validated.name());
        Path roleFile = roleFile(validated.name());
        JsonObject role = loadTemplate();
        role.addProperty("NameTranslationKey",
                "server.npcRoles.ImmersiveNPCs_" + safeTranslationKey(roleName) + ".name");
        JsonFiles.writeAtomic(roleFile, role);
        registrar.registerOrUpdate(roleName, roleFile);
        profileNameByRole.put(key(roleName), validated.name());
        ManagedNpcRoles.register(roleName);
        diagnostics.accept("IMMERSIVE_NPC_NATIVE_ROLE_READY role=" + roleName
                + " profile=" + validated.id() + " file=" + roleFile);
        return roleName;
    }

    public synchronized void unregister(NpcProfile profile) {
        if (profile == null) return;
        String roleName = ProfileRepository.sanitizeProfileName(profile.name());
        profileNameByRole.remove(key(roleName));
        ManagedNpcRoles.unregister(roleName);
        diagnostics.accept("IMMERSIVE_NPC_NATIVE_ROLE_RETIRED role=" + roleName
                + " profile=" + profile.id()
                + " note=builder_cache_reloads_on_recreate_or_restart");
    }

    public Optional<NpcProfile> profileForRole(String roleName) {
        String profileName = profileNameByRole.get(key(roleName));
        return profileName == null ? Optional.empty() : profiles.byName(profileName);
    }

    public boolean isManagedRole(String roleName) {
        return profileNameByRole.containsKey(key(roleName));
    }

    public Path roleFile(String profileName) {
        Path profileDirectory = profilesDirectory.resolve(
                ProfileRepository.sanitizeProfileName(profileName)).normalize();
        if (!profileDirectory.getParent().equals(profilesDirectory)) {
            throw new IllegalArgumentException("Unsafe Immersive NPC role directory");
        }
        Path roleDirectory = profileDirectory.resolve("native-role").normalize();
        Path roleFile = roleDirectory.resolve(
                ProfileRepository.sanitizeProfileName(profileName) + ".json").normalize();
        if (!roleFile.getParent().equals(roleDirectory)) {
            throw new IllegalArgumentException("Unsafe Immersive NPC role path");
        }
        return roleFile;
    }

    private static JsonObject loadTemplate() {
        try (var input = ImmersiveNpcRoleService.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
            if (input == null) throw new IllegalStateException(
                    "Missing native Immersive NPC role template");
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException("Could not read native role template", failure);
        }
    }

    private static String safeTranslationKey(String value) {
        return value.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private static String key(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    @FunctionalInterface
    public interface NativeRoleRegistrar {
        void registerOrUpdate(String roleName, Path roleFile);
    }

    private static final class Update6NativeRoleRegistrar implements NativeRoleRegistrar {
        @Override
        public void registerOrUpdate(String roleName, Path roleFile) {
            NPCPlugin npcPlugin = NPCPlugin.get();
            if (npcPlugin == null) {
                throw new IllegalStateException("Hytale NPC plugin is unavailable");
            }
            BuilderManager builders = npcPlugin.getBuilderManager();
            boolean replacing = npcPlugin.hasRoleName(roleName);
            if (replacing) {
                BuilderInfo existing = builders.tryGetBuilderInfo(builders.getIndex(roleName));
                if (existing != null && existing.getPath() != null
                        && !sameFile(existing.getPath(), roleFile)) {
                    throw new IllegalArgumentException("Immersive NPC profile name conflicts with "
                            + "an existing native NPC role: " + roleName);
                }
            }
            ArrayList<String> errors = new ArrayList<>();
            int index = builders.loadFile(roleFile, true, errors);
            if (index == Integer.MIN_VALUE || !errors.isEmpty()) {
                throw new IllegalArgumentException("Native NPC role " + roleName
                        + " is invalid: " + String.join("; ", errors));
            }
            BuilderInfo info = builders.tryGetBuilderInfo(index);
            if (info == null) {
                throw new IllegalStateException("Native NPC role was not cached: " + roleName);
            }
            info.setForceValidation();
            if (!npcPlugin.testAndValidateRole(info)) {
                throw new IllegalArgumentException(
                        "Native NPC role failed Hytale validation: " + roleName);
            }
            npcPlugin.validateSpawnableRole(roleName);
            if (!npcPlugin.hasRoleName(roleName)) {
                throw new IllegalStateException(
                        "Native NPC role is unavailable after registration: " + roleName);
            }
            if (replacing) {
                NPCPlugin.reloadNPCsWithRole(index);
            }
        }

        private static boolean sameFile(Path left, Path right) {
            Path normalizedLeft = left.toAbsolutePath().normalize();
            Path normalizedRight = right.toAbsolutePath().normalize();
            if (normalizedLeft.equals(normalizedRight)) return true;
            try {
                return Files.isSameFile(normalizedLeft, normalizedRight);
            } catch (java.io.IOException ignored) {
                return false;
            }
        }
    }
}
