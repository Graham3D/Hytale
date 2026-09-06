package com.inigmasgames.hytalerpg.progress;

import com.inigmasgames.hytalerpg.domain.LinkEdge;
import com.inigmasgames.hytalerpg.domain.LinkNodeId;
import com.inigmasgames.hytalerpg.domain.PassiveId;
import com.inigmasgames.hytalerpg.domain.PassiveSlot;
import com.inigmasgames.hytalerpg.domain.SkillId;
import com.inigmasgames.hytalerpg.domain.SkillSlot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Versioned server-owned RPG player state. Live Hytale resources are intentionally not duplicated here. */
public final class RpgPlayerState {
    public static final int CURRENT_SCHEMA = 2;

    public int schemaVersion = CURRENT_SCHEMA;
    public String playerUuid;
    public int level = 1;
    public long currentXp;
    public int pendingLevelUpPoints;
    public Map<String, Integer> attributes = defaultAttributes();
    public int unspentAttributePoints;
    public Set<String> learnedSkills = new LinkedHashSet<>();
    public Map<String, Integer> ownedPassives = new LinkedHashMap<>();
    public String[] equippedSkills = new String[4];
    public String[] equippedPassives = new String[6];
    public String[] joints = {"joint01", "joint02"};
    public List<PersistedLinkEdge> graphEdges = new ArrayList<>();
    public Map<String, Long> skillMastery = new LinkedHashMap<>();
    public long revision;
    public List<String> degradedReasons = new ArrayList<>();

    public static RpgPlayerState create(UUID playerUuid) {
        RpgPlayerState state = new RpgPlayerState();
        state.playerUuid = playerUuid.toString();
        return state;
    }

    public UUID playerUuid() { return UUID.fromString(playerUuid); }

    public Optional<SkillId> skill(SkillSlot slot) {
        String value = equippedSkills[slot.index()];
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(new SkillId(value));
    }

    public void skill(SkillSlot slot, SkillId id) { equippedSkills[slot.index()] = id == null ? null : id.value(); }

    public Optional<PassiveId> passive(PassiveSlot slot) {
        String value = equippedPassives[slot.index()];
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(new PassiveId(value));
    }

    public void passive(PassiveSlot slot, PassiveId id) { equippedPassives[slot.index()] = id == null ? null : id.value(); }

    public List<LinkEdge> linkEdges() {
        List<LinkEdge> result = new ArrayList<>(graphEdges.size());
        for (PersistedLinkEdge edge : graphEdges) result.add(edge.toDomain());
        return result;
    }

    public void linkEdges(List<LinkEdge> edges) {
        graphEdges = edges.stream().map(PersistedLinkEdge::fromDomain).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    public void normalizeShape() {
        if (equippedSkills == null) equippedSkills = new String[4];
        if (equippedPassives == null) equippedPassives = new String[6];
        if (equippedSkills.length != 4) equippedSkills = Arrays.copyOf(equippedSkills, 4);
        if (equippedPassives.length != 6) equippedPassives = Arrays.copyOf(equippedPassives, 6);
        if (joints == null || joints.length != 2) joints = new String[]{"joint01", "joint02"};
        if (attributes == null) attributes = defaultAttributes();
        for (String key : List.of("STR", "DEX", "INT", "WIS", "LUCK")) attributes.putIfAbsent(key, 10);
        if (learnedSkills == null) learnedSkills = new LinkedHashSet<>();
        if (ownedPassives == null) ownedPassives = new LinkedHashMap<>();
        if (graphEdges == null) graphEdges = new ArrayList<>();
        if (skillMastery == null) skillMastery = new LinkedHashMap<>();
        if (degradedReasons == null) degradedReasons = new ArrayList<>();
    }

    public RpgPlayerState copy() {
        RpgPlayerState copy = new RpgPlayerState();
        copy.schemaVersion = schemaVersion;
        copy.playerUuid = playerUuid;
        copy.level = level;
        copy.currentXp = currentXp;
        copy.pendingLevelUpPoints = pendingLevelUpPoints;
        copy.attributes = new LinkedHashMap<>(attributes);
        copy.unspentAttributePoints = unspentAttributePoints;
        copy.learnedSkills = new LinkedHashSet<>(learnedSkills);
        copy.ownedPassives = new LinkedHashMap<>(ownedPassives);
        copy.equippedSkills = equippedSkills.clone();
        copy.equippedPassives = equippedPassives.clone();
        copy.joints = joints.clone();
        copy.graphEdges = graphEdges.stream().map(PersistedLinkEdge::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        copy.skillMastery = new LinkedHashMap<>(skillMastery);
        copy.revision = revision;
        copy.degradedReasons = new ArrayList<>(degradedReasons);
        return copy;
    }

    private static Map<String, Integer> defaultAttributes() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String key : List.of("STR", "DEX", "INT", "WIS", "LUCK")) result.put(key, 10);
        return result;
    }

    public static final class PersistedLinkEdge {
        public int schemaVersion = LinkEdge.CURRENT_SCHEMA;
        public String edgeId;
        public String sourceNodeId;
        public String targetNodeId;
        public LinkEdge toDomain() {
            return new LinkEdge(schemaVersion, new com.inigmasgames.hytalerpg.domain.EdgeId(edgeId),
                    LinkNodeId.parse(sourceNodeId), LinkNodeId.parse(targetNodeId));
        }
        static PersistedLinkEdge fromDomain(LinkEdge edge) {
            PersistedLinkEdge result = new PersistedLinkEdge();
            result.schemaVersion = edge.schemaVersion();
            result.edgeId = edge.edgeId().value();
            result.sourceNodeId = edge.sourceNodeId().externalId();
            result.targetNodeId = edge.targetNodeId().externalId();
            return result;
        }
        PersistedLinkEdge copy() {
            PersistedLinkEdge copy = new PersistedLinkEdge();
            copy.schemaVersion = schemaVersion; copy.edgeId = edgeId;
            copy.sourceNodeId = sourceNodeId; copy.targetNodeId = targetNodeId;
            return copy;
        }
    }
}
