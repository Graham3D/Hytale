package com.inigmasgames.persistentnpcs.quest;

import java.util.UUID;

public record QuestObjective(
        UUID objectiveId,
        String objectiveType,
        String description,
        ResolvedWorldTarget target,
        int requiredCount,
        int currentCount,
        QuestObjectiveStatus status) {

    public QuestObjective normalized() {
        int required = Math.max(1, requiredCount);
        int current = Math.max(0, Math.min(required, currentCount));
        QuestObjectiveStatus state = status == null
                ? QuestObjectiveStatus.PENDING : status;
        if (current >= required && state == QuestObjectiveStatus.PENDING) {
            state = QuestObjectiveStatus.COMPLETE;
        }
        return new QuestObjective(objectiveId == null ? UUID.randomUUID() : objectiveId,
                objectiveType == null ? "AUTHORITATIVE_EVENT" : objectiveType.strip(),
                description == null ? "Complete the validated objective" : description.strip(),
                target == null ? null : target.normalized(), required, current, state);
    }

    public QuestObjective progress(int amount) {
        if (status == QuestObjectiveStatus.COMPLETE || status == QuestObjectiveStatus.FAILED) {
            return this;
        }
        return new QuestObjective(objectiveId, objectiveType, description, target,
                requiredCount, Math.min(requiredCount, currentCount + Math.max(0, amount)),
                currentCount + Math.max(0, amount) >= requiredCount
                        ? QuestObjectiveStatus.COMPLETE : QuestObjectiveStatus.PENDING);
    }
}
