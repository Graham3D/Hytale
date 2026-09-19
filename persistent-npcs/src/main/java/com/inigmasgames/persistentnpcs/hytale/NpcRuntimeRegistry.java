package com.inigmasgames.persistentnpcs.hytale;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class NpcRuntimeRegistry {
    private final ConcurrentHashMap<UUID, RuntimeNpc> byProfile = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> profileByEntity = new ConcurrentHashMap<>();

    public void register(UUID profileId, UUID worldId, UUID entityId) {
        RuntimeNpc previous = byProfile.put(profileId,
                new RuntimeNpc(profileId, worldId, entityId));
        if (previous != null && !previous.entityId().equals(entityId)) {
            profileByEntity.remove(previous.entityId(), profileId);
        }
        profileByEntity.put(entityId, profileId);
    }

    /** Keeps the first loaded entity canonical when old saves contain duplicates. */
    public boolean registerIfAbsent(UUID profileId, UUID worldId, UUID entityId) {
        return registerIfAbsent(profileId, worldId, entityId, ignored -> true);
    }

    /**
     * Keeps a loaded canonical entity, but replaces a stale registry entry left behind by
     * native commands such as /npc clean that remove entities outside this mod's lifecycle.
     */
    public boolean registerIfAbsent(UUID profileId, UUID worldId, UUID entityId,
            Predicate<UUID> entityIsLoaded) {
        RuntimeNpc candidate = new RuntimeNpc(profileId, worldId, entityId);
        RuntimeNpc selected = byProfile.compute(profileId, (ignored, current) -> {
            if (current == null || current.entityId().equals(entityId)) {
                return candidate;
            }
            if (entityIsLoaded != null && !entityIsLoaded.test(current.entityId())) {
                profileByEntity.remove(current.entityId(), profileId);
                return candidate;
            }
            // Worlds without players tick during save startup too. Never let an
            // unloaded/inactive-world copy make the visible NPC non-interactive.
            if (current.worldId() == null && worldId != null) {
                profileByEntity.remove(current.entityId(), profileId);
                return candidate;
            }
            return current;
        });
        if (!selected.entityId().equals(entityId)) {
            return false;
        }
        profileByEntity.put(entityId, profileId);
        return true;
    }

    public Optional<RuntimeNpc> forProfile(UUID profileId) {
        return Optional.ofNullable(byProfile.get(profileId));
    }

    public Optional<UUID> profileForEntity(UUID entityId) {
        return Optional.ofNullable(profileByEntity.get(entityId));
    }

    public void unregisterEntity(UUID entityId) {
        UUID profileId = profileByEntity.remove(entityId);
        if (profileId != null) {
            byProfile.computeIfPresent(profileId,
                    (ignored, current) -> current.entityId().equals(entityId) ? null : current);
        }
    }

    public void unregisterProfile(UUID profileId) {
        RuntimeNpc removed = byProfile.remove(profileId);
        if (removed != null) {
            profileByEntity.remove(removed.entityId(), profileId);
        }
        profileByEntity.entrySet().removeIf(entry -> entry.getValue().equals(profileId));
    }

    public record RuntimeNpc(UUID profileId, UUID worldId, UUID entityId) {
    }
}
