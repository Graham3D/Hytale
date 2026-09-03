package com.inigmasgames.persistentnpcs.quest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record DynamicQuest(
        UUID questId,
        UUID issuerNpcId,
        Set<UUID> participantPlayerIds,
        QuestType questType,
        String motivation,
        String storySummary,
        List<QuestObjective> objectives,
        List<ResolvedWorldTarget> resolvedWorldTargets,
        QuestReward reward,
        QuestStatus status,
        LocalDateTime createdGameTime,
        LocalDateTime expiryGameTime,
        LocalDateTime completedGameTime,
        String completionOrFailureReason) {

    public DynamicQuest normalized() {
        if (issuerNpcId == null || questType == null || objectives == null
                || objectives.isEmpty() || reward == null) {
            throw new IllegalArgumentException("Quest requires issuer, type, objectives and reward");
        }
        return new DynamicQuest(questId == null ? UUID.randomUUID() : questId,
                issuerNpcId,
                participantPlayerIds == null ? Set.of() : Set.copyOf(participantPlayerIds),
                questType,
                motivation == null ? "Grounded need" : motivation.strip(),
                storySummary == null ? "A validated local opportunity" : storySummary.strip(),
                objectives.stream().map(QuestObjective::normalized).toList(),
                resolvedWorldTargets == null ? List.of()
                        : resolvedWorldTargets.stream().map(ResolvedWorldTarget::normalized).toList(),
                reward.normalized(), status == null ? QuestStatus.OFFERED : status,
                createdGameTime == null ? LocalDateTime.now() : createdGameTime,
                expiryGameTime, completedGameTime,
                completionOrFailureReason == null ? "" : completionOrFailureReason.strip());
    }

    public DynamicQuest withStatus(QuestStatus newStatus, LocalDateTime at, String reason) {
        return new DynamicQuest(questId, issuerNpcId, participantPlayerIds, questType,
                motivation, storySummary, objectives, resolvedWorldTargets, reward, newStatus,
                createdGameTime, expiryGameTime,
                newStatus == QuestStatus.COMPLETED || newStatus == QuestStatus.FAILED
                        ? at : completedGameTime,
                reason);
    }

    public DynamicQuest withObjectives(List<QuestObjective> updated, QuestStatus newStatus,
            LocalDateTime at, String reason) {
        return new DynamicQuest(questId, issuerNpcId, participantPlayerIds, questType,
                motivation, storySummary, List.copyOf(updated), resolvedWorldTargets, reward,
                newStatus, createdGameTime, expiryGameTime,
                newStatus == QuestStatus.COMPLETED || newStatus == QuestStatus.FAILED
                        ? at : completedGameTime,
                reason);
    }

    public boolean terminal() {
        return status == QuestStatus.COMPLETED || status == QuestStatus.FAILED
                || status == QuestStatus.EXPIRED || status == QuestStatus.CANCELLED;
    }
}
