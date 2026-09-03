package com.inigmasgames.persistentnpcs.quest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record QuestOpportunityContext(
        UUID issuerNpcId,
        Set<UUID> participantPlayerIds,
        LocalDateTime currentGameTime,
        Set<QuestType> feasibleQuestTypes,
        List<ResolvedWorldTarget> authoritativeTargets,
        List<RewardCandidate> authoritativeRewardPool,
        UUID taskWorldId,
        String groundingEventId) {

    /** Compatibility constructor for already-trusted callers predating explicit feasibility. */
    public QuestOpportunityContext(
            UUID issuerNpcId,
            Set<UUID> participantPlayerIds,
            LocalDateTime currentGameTime,
            List<ResolvedWorldTarget> authoritativeTargets,
            List<RewardCandidate> authoritativeRewardPool,
            UUID taskWorldId,
            String groundingEventId) {
        this(issuerNpcId, participantPlayerIds, currentGameTime,
                java.util.EnumSet.allOf(QuestType.class), authoritativeTargets,
                authoritativeRewardPool, taskWorldId, groundingEventId);
    }

    public QuestOpportunityContext normalized() {
        if (issuerNpcId == null || participantPlayerIds == null
                || participantPlayerIds.isEmpty()) {
            throw new IllegalArgumentException("Quest context requires issuer and participant");
        }
        return new QuestOpportunityContext(issuerNpcId, Set.copyOf(participantPlayerIds),
                currentGameTime == null ? LocalDateTime.now() : currentGameTime,
                feasibleQuestTypes == null ? Set.of() : Set.copyOf(feasibleQuestTypes),
                authoritativeTargets == null ? List.of()
                        : authoritativeTargets.stream().map(ResolvedWorldTarget::normalized).toList(),
                authoritativeRewardPool == null ? List.of()
                        : authoritativeRewardPool.stream().map(RewardCandidate::normalized).toList(),
                taskWorldId,
                groundingEventId == null ? "" : groundingEventId.strip());
    }
}
