package com.inigmasgames.persistentnpcs.relationship;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.profile.AuthoredNpcRelationship;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class RelationshipStore {
    private final Path path;
    private final Map<String, RelationshipRecord> records = new LinkedHashMap<>();

    public RelationshipStore(Path dataDirectory) {
        path = dataDirectory.resolve("persistence/relationships.json");
    }

    public synchronized void load() {
        records.clear();
        if (!Files.exists(path)) {
            save();
            return;
        }
        RelationshipRecord[] loaded = JsonFiles.read(path, RelationshipRecord[].class);
        if (loaded != null) {
            Arrays.stream(loaded).map(RelationshipRecord::normalized).forEach(
                    record -> records.put(key(record.npcId(), record.playerId()), record));
        }
    }

    public synchronized RelationshipRecord getOrDefault(UUID npcId, UUID playerId, int defaultDisposition) {
        return records.getOrDefault(key(npcId, playerId),
                new RelationshipRecord(npcId, playerId, defaultDisposition, 0, null));
    }

    public synchronized RelationshipRecord recordCompletedInteraction(
            UUID npcId, UUID playerId, int defaultDisposition, Instant now) {
        RelationshipRecord current = getOrDefault(npcId, playerId, defaultDisposition);
        RelationshipRecord updated = new RelationshipRecord(npcId, playerId,
                current.disposition(), current.interactionCount() + 1, now,
                Math.min(100, current.familiarity() + 1), current.trust(),
                current.affection(), current.respect(), current.fear(),
                current.hostility(), current.obligation(), current.relationshipType(),
                current.description());
        records.put(key(npcId, playerId), updated);
        save();
        return updated;
    }

    public synchronized RelationshipRecord adjust(
            UUID npcId,
            UUID otherEntityId,
            int defaultDisposition,
            int trust,
            int affection,
            int respect,
            int fear,
            int hostility,
            int obligation,
            Instant now) {
        RelationshipRecord current = getOrDefault(npcId, otherEntityId, defaultDisposition);
        RelationshipRecord updated = new RelationshipRecord(npcId, otherEntityId,
                clamp(current.disposition() + trust + affection + respect - fear - hostility),
                current.interactionCount(), now,
                current.familiarity(), clamp(current.trust() + trust),
                clamp(current.affection() + affection), clamp(current.respect() + respect),
                clamp(current.fear() + fear), clamp(current.hostility() + hostility),
                clamp(current.obligation() + obligation), current.relationshipType(),
                current.description()).normalized();
        records.put(key(npcId, otherEntityId), updated);
        save();
        return updated;
    }

    public Path path() {
        return path;
    }

    /** Stable entity IDs are authoritative; callers may do exact-name migration resolution. */
    public synchronized Optional<RelationshipRecord> get(UUID npcId, UUID otherEntityId) {
        return Optional.ofNullable(records.get(key(npcId, otherEntityId)));
    }

    public synchronized List<RelationshipRecord> forNpc(UUID npcId) {
        return records.values().stream().filter(record -> record.npcId().equals(npcId))
                .toList();
    }

    /** Merges profile-authored NPC relationships into this authoritative relationship store. */
    public synchronized int importAuthored(
            Iterable<NpcProfile> authoredProfiles, NpcProfileRegistry registry) {
        int imported = 0;
        for (NpcProfile speaker : authoredProfiles) {
            if (speaker == null || speaker.relationships() == null) continue;
            for (AuthoredNpcRelationship authored : speaker.relationships()) {
                NpcProfile target = resolveTarget(authored, registry).orElse(null);
                if (target == null || target.id().equals(speaker.id())) continue;
                String key = key(speaker.id(), target.id());
                RelationshipRecord current = records.get(key);
                if (current != null && current.relationshipType() != null
                        && !current.relationshipType().isBlank()) continue;
                RelationshipRecord value = current == null
                        ? fromAuthored(speaker.id(), target.id(), authored)
                        : new RelationshipRecord(current.npcId(), current.playerId(),
                                current.disposition(), current.interactionCount(),
                                current.lastInteraction(),
                                Math.max(current.familiarity(), score(authored.familiarity(), 75)),
                                current.trust(), current.affection(), current.respect(),
                                current.fear(), current.hostility(), current.obligation(),
                                authored.relationship(), authored.description()).normalized();
                records.put(key, value);
                imported++;
            }
        }
        if (imported > 0) save();
        return imported;
    }

    public synchronized boolean knows(UUID npcId, UUID otherNpcId) {
        RelationshipRecord record = records.get(key(npcId, otherNpcId));
        return record != null && record.knowsEntity();
    }

    private static Optional<NpcProfile> resolveTarget(
            AuthoredNpcRelationship relationship, NpcProfileRegistry registry) {
        if (registry == null || relationship == null) return Optional.empty();
        try {
            UUID id = UUID.fromString(relationship.targetId());
            Optional<NpcProfile> byId = registry.byId(id);
            if (byId.isPresent()) return byId;
        } catch (RuntimeException ignored) { }
        Optional<NpcProfile> byIdName = registry.byName(relationship.targetId());
        return byIdName.isPresent() ? byIdName : registry.byName(relationship.targetName());
    }

    private static RelationshipRecord fromAuthored(
            UUID speaker, UUID target, AuthoredNpcRelationship authored) {
        int familiarity = score(authored.familiarity(), 75);
        int trust = score(authored.trust(), 0);
        int affection = score(authored.affection(), 0);
        int respect = score(authored.respect(), 0);
        int fear = score(authored.fear(), 0);
        int hostility = score(authored.resentment(), 0);
        int obligation = score(authored.obligation(), 0);
        int disposition = clamp((trust + affection + respect - fear - hostility) / 3);
        return new RelationshipRecord(speaker, target, disposition, 0, null,
                familiarity, trust, affection, respect, fear, hostility, obligation,
                authored.relationship(), authored.description()).normalized();
    }

    private static int score(Double value, int fallback) {
        return value == null ? fallback : clamp((int) Math.round(value * 100.0));
    }

    private void save() {
        JsonFiles.writeAtomic(path, records.values());
    }

    private static String key(UUID npcId, UUID playerId) {
        return npcId + ":" + playerId;
    }

    private static int clamp(int value) {
        return Math.max(-100, Math.min(100, value));
    }
}
