package com.inigmasgames.taverns;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** In-memory Core store with chunk indexing for bounded spatial queries. */
final class CoreRegistry {
    private final Map<UUID, CoreRecord> cores = new LinkedHashMap<>();
    private final Map<UUID, Map<Long, Set<UUID>>> chunkIndex = new LinkedHashMap<>();

    Collection<CoreRecord> all() {
        return new ArrayList<>(cores.values());
    }

    Optional<CoreRecord> findById(UUID coreId) {
        return Optional.ofNullable(cores.get(coreId));
    }

    Collection<CoreRecord> findByTavern(UUID tavernId) {
        return cores.values().stream().filter(core -> core.tavernId().equals(tavernId)).toList();
    }

    Optional<CoreRecord> findAt(UUID worldId, int x, int y, int z) {
        return candidates(worldId, Cuboid.normalized(x, y, z, x, y, z)).stream()
                .filter(core -> core.coreX() == x && core.coreY() == y && core.coreZ() == z)
                .findFirst();
    }

    Optional<CoreRecord> findContainingPrimary(UUID worldId, int x, int y, int z) {
        return findContaining(worldId, CoreType.TAVERN, x, y, z);
    }

    Optional<CoreRecord> findContaining(UUID worldId, CoreType type, int x, int y, int z) {
        Cuboid point = Cuboid.normalized(x, y, z, x, y, z);
        return candidates(worldId, point).stream()
                .filter(core -> core.type() == type)
                .filter(core -> core.bounds().contains(x, y, z))
                .findFirst();
    }

    Collection<CoreRecord> findIntersecting(UUID worldId, Cuboid bounds, UUID ignoredCoreId) {
        return candidates(worldId, bounds).stream()
                .filter(core -> ignoredCoreId == null || !core.coreId().equals(ignoredCoreId))
                .filter(core -> core.bounds().intersects(bounds))
                .toList();
    }

    void add(CoreRecord core) {
        if (cores.putIfAbsent(core.coreId(), core) != null) {
            throw new IllegalStateException("Duplicate Core " + core.coreId());
        }
        index(core);
    }

    void update(CoreRecord core) {
        CoreRecord previous = cores.get(core.coreId());
        if (previous == null) {
            throw new IllegalStateException("Unknown Core " + core.coreId());
        }
        unindex(previous);
        cores.put(core.coreId(), core);
        index(core);
    }

    Optional<CoreRecord> remove(UUID coreId) {
        CoreRecord removed = cores.remove(coreId);
        if (removed != null) {
            unindex(removed);
        }
        return Optional.ofNullable(removed);
    }

    void replaceAll(Collection<CoreRecord> replacements) {
        cores.clear();
        chunkIndex.clear();
        for (CoreRecord core : replacements) {
            add(core);
        }
    }

    private Collection<CoreRecord> candidates(UUID worldId, Cuboid bounds) {
        Map<Long, Set<UUID>> worldIndex = chunkIndex.get(worldId);
        if (worldIndex == null) {
            return java.util.List.of();
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (long chunk : bounds.intersectedChunks()) {
            ids.addAll(worldIndex.getOrDefault(chunk, Set.of()));
        }
        return ids.stream().map(cores::get).filter(java.util.Objects::nonNull).toList();
    }

    private void index(CoreRecord core) {
        Map<Long, Set<UUID>> worldIndex = chunkIndex.computeIfAbsent(core.worldId(), ignored -> new LinkedHashMap<>());
        for (long chunk : core.intersectedChunks()) {
            worldIndex.computeIfAbsent(chunk, ignored -> new LinkedHashSet<>()).add(core.coreId());
        }
    }

    private void unindex(CoreRecord core) {
        Map<Long, Set<UUID>> worldIndex = chunkIndex.get(core.worldId());
        if (worldIndex == null) {
            return;
        }
        for (long chunk : core.intersectedChunks()) {
            Set<UUID> ids = worldIndex.get(chunk);
            if (ids == null) {
                continue;
            }
            ids.remove(core.coreId());
            if (ids.isEmpty()) {
                worldIndex.remove(chunk);
            }
        }
        if (worldIndex.isEmpty()) {
            chunkIndex.remove(core.worldId());
        }
    }
}
