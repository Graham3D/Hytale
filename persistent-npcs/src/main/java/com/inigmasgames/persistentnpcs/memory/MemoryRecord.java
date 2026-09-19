package com.inigmasgames.persistentnpcs.memory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MemoryRecord(
        UUID memoryId,
        UUID npcId,
        UUID playerId,
        Instant timestamp,
        MemoryType type,
        double importance,
        String summary,
        Double confidence,
        String source,
        List<UUID> involvedEntities,
        String location,
        String npcPerspective,
        MemoryDurability durability,
        double emotionalValence,
        double emotionalIntensity,
        double relationshipImpact,
        double goalImpact,
        double dangerImpact,
        double novelty,
        double consequenceImpact,
        double coreValueRelevance,
        int rehearsalCount,
        Instant lastRecalledAt) {

    /** Source-compatible constructor for R033 and older memory writers. */
    public MemoryRecord(UUID memoryId, UUID npcId, UUID playerId, Instant timestamp,
            MemoryType type, double importance, String summary, Double confidence,
            String source, List<UUID> involvedEntities, String location,
            String npcPerspective) {
        this(memoryId, npcId, playerId, timestamp, type, importance, summary, confidence,
                source, involvedEntities, location, npcPerspective, null,
                0, 0, 0, 0, 0, 0, 0, 0, 0, null);
    }

    public MemoryRecord(
            UUID memoryId,
            UUID npcId,
            UUID playerId,
            Instant timestamp,
            MemoryType type,
            double importance,
            String summary) {
        this(memoryId, npcId, playerId, timestamp, type, importance, summary,
                1.0, "DIRECT", List.of(), "", "");
    }

    public MemoryRecord(
            UUID memoryId,
            UUID npcId,
            UUID playerId,
            Instant timestamp,
            double importance,
            String summary) {
        this(memoryId, npcId, playerId, timestamp,
                MemoryType.CONVERSATION, importance, summary,
                1.0, "DIRECT", List.of(), "", "");
    }

    public MemoryRecord normalized() {
        MemoryRecord sanitized = new MemoryRecord(
                memoryId == null ? UUID.randomUUID() : memoryId, npcId, playerId,
                timestamp == null ? Instant.now() : timestamp,
                type == null ? MemoryType.CONVERSATION : type,
                Math.max(0.0, Math.min(1.0, importance)), summary,
                Math.max(0.0, Math.min(1.0, confidence == null ? 1.0 : confidence)),
                source == null || source.isBlank() ? "UNKNOWN" : source.strip(),
                involvedEntities == null ? List.of() : List.copyOf(involvedEntities),
                location == null ? "" : location.strip(),
                npcPerspective == null ? "" : npcPerspective.strip(), durability,
                clampSigned(emotionalValence), clamp(emotionalIntensity),
                clamp(relationshipImpact), clamp(goalImpact), clamp(dangerImpact),
                clamp(novelty), clamp(consequenceImpact), clamp(coreValueRelevance),
                Math.max(0, rehearsalCount), lastRecalledAt);
        if (sanitized.durability() != null) return sanitized;
        MemoryImportanceEvaluator.MemoryAppraisal appraisal =
                new MemoryImportanceEvaluator().evaluate(sanitized);
        return new MemoryRecord(sanitized.memoryId(), sanitized.npcId(),
                sanitized.playerId(), sanitized.timestamp(), sanitized.type(),
                appraisal.importance(), sanitized.summary(), sanitized.confidence(),
                sanitized.source(), sanitized.involvedEntities(), sanitized.location(),
                sanitized.npcPerspective(), appraisal.durability(),
                appraisal.emotionalValence(), appraisal.emotionalIntensity(),
                appraisal.relationshipImpact(), appraisal.goalImpact(),
                appraisal.dangerImpact(), appraisal.novelty(),
                appraisal.consequenceImpact(), appraisal.coreValueRelevance(),
                sanitized.rehearsalCount(), sanitized.lastRecalledAt());
    }

    public boolean durable() {
        return durability == MemoryDurability.IMPORTANT
                || durability == MemoryDurability.LANDMARK
                || type == MemoryType.COMMITMENT || type == MemoryType.TASK;
    }

    public MemoryRecord recalled(Instant at, double reinforcement) {
        double strengthened = clamp(importance + clamp(reinforcement) * (1.0 - importance));
        MemoryDurability strengthenedTier = new MemoryImportanceEvaluator().tier(strengthened);
        if (durability != null && durability.ordinal() > strengthenedTier.ordinal()) {
            strengthenedTier = durability;
        }
        return new MemoryRecord(memoryId, npcId, playerId, timestamp, type, strengthened,
                summary, confidence, source, involvedEntities, location, npcPerspective,
                strengthenedTier, emotionalValence, emotionalIntensity,
                relationshipImpact, goalImpact, dangerImpact, novelty,
                consequenceImpact, coreValueRelevance, rehearsalCount + 1,
                at == null ? Instant.now() : at).normalized();
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double clampSigned(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }
}
