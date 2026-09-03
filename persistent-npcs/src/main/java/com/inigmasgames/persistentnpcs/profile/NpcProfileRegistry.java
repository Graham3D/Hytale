package com.inigmasgames.persistentnpcs.profile;

import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Live index over the existing authored Profile Schema v1 identities. */
public final class NpcProfileRegistry {
    private final ProfileRepository repository;
    private final Map<UUID, NpcProfile> byId = new ConcurrentHashMap<>();
    private final Map<String, UUID> idByName = new ConcurrentHashMap<>();

    public NpcProfileRegistry(ProfileRepository repository) {
        this.repository = repository;
    }

    public synchronized void load() {
        byId.clear();
        idByName.clear();
        repository.loadAll().values().forEach(this::register);
        if (byId.isEmpty()) register(repository.loadTestProfile());
    }

    public synchronized NpcProfile reload(String name) {
        NpcProfile loaded = repository.load(name);
        NpcProfile previous = byId.put(loaded.id(), loaded);
        if (previous != null && !previous.name().equalsIgnoreCase(loaded.name())) {
            idByName.remove(key(previous.name()), previous.id());
        }
        idByName.put(key(loaded.name()), loaded.id());
        return loaded;
    }

    public synchronized void register(NpcProfile profile) {
        NpcProfile value = profile.validated();
        byId.put(value.id(), value);
        idByName.put(key(value.name()), value.id());
    }

    public synchronized void unregister(NpcProfile profile) {
        if (profile == null) return;
        byId.remove(profile.id(), profile);
        idByName.remove(key(profile.name()), profile.id());
    }

    public Optional<NpcProfile> byId(UUID id) {
        return Optional.ofNullable(id == null ? null : byId.get(id));
    }

    public Optional<NpcProfile> byName(String name) {
        UUID id = idByName.get(key(name));
        return Optional.ofNullable(id == null ? null : byId.get(id));
    }

    public NpcProfile requireName(String name) {
        return byName(name).orElseThrow(() -> new IllegalArgumentException(
                "Unknown NPC: " + ProfileRepository.sanitizeProfileName(name)));
    }

    public NpcProfile defaultProfile() {
        return byName("Mara").orElseGet(() -> byId.values().stream()
                .min(Comparator.comparing(NpcProfile::name, String.CASE_INSENSITIVE_ORDER))
                .orElseThrow(() -> new IllegalStateException("No NPC profiles are registered")));
    }

    public Collection<NpcProfile> profiles() {
        return java.util.List.copyOf(byId.values());
    }

    private static String key(String name) {
        return name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
    }
}
