package com.inigmasgames.persistentnpcs.quest;

import com.inigmasgames.persistentnpcs.event.NpcEventType;
import com.inigmasgames.persistentnpcs.event.NpcFrameworkEvent;
import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.memory.MemoryType;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import com.inigmasgames.persistentnpcs.task.NpcTask;
import com.inigmasgames.persistentnpcs.task.NpcTaskState;
import com.inigmasgames.persistentnpcs.task.NpcTaskStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Resolves untrusted story proposals into authoritative persistent quests. */
public final class DynamicQuestDirector {
    private final DynamicQuestStore quests;
    private final RewardResolver rewards;
    private final NpcTaskStore tasks;
    private final MemoryStore memories;
    private final RelationshipStore relationships;

    public DynamicQuestDirector(
            DynamicQuestStore quests,
            RewardResolver rewards,
            NpcTaskStore tasks,
            MemoryStore memories,
            RelationshipStore relationships) {
        this.quests = quests;
        this.rewards = rewards;
        this.tasks = tasks;
        this.memories = memories;
        this.relationships = relationships;
    }

    public synchronized QuestCreationResult createValidated(
            QuestProposal untrustedProposal, QuestOpportunityContext untrustedContext) {
        QuestProposal proposal;
        QuestOpportunityContext context;
        try {
            proposal = untrustedProposal.normalized();
            context = untrustedContext.normalized();
        } catch (RuntimeException failure) {
            return QuestCreationResult.reject("Invalid quest proposal/context: "
                    + failure.getMessage());
        }
        if (!context.feasibleQuestTypes().contains(proposal.questType())) {
            return QuestCreationResult.reject("Quest type " + proposal.questType()
                    + " is not feasible in the authoritative opportunity snapshot.");
        }
        Optional<ResolvedWorldTarget> target = resolveTarget(proposal, context);
        if (target.isEmpty()) {
            return QuestCreationResult.reject("No authoritative " + expectedKind(proposal.questType())
                    + " target resolves requested target '" + proposal.requestedTarget() + "'.");
        }
        Optional<QuestReward> reward = rewards.resolve(
                proposal, context.authoritativeRewardPool());
        if (reward.isEmpty()) {
            return QuestCreationResult.reject(
                    "No validated item reward fits the server reward budget.");
        }
        ResolvedWorldTarget resolved = target.get();
        QuestObjective objective = new QuestObjective(UUID.randomUUID(),
                objectiveType(proposal.questType()), objectiveDescription(proposal, resolved),
                resolved, 1, 0, QuestObjectiveStatus.PENDING).normalized();
        DynamicQuest quest = new DynamicQuest(UUID.randomUUID(), context.issuerNpcId(),
                context.participantPlayerIds(), proposal.questType(), proposal.motivation(),
                proposal.storySummary(), List.of(objective), List.of(resolved), reward.get(),
                QuestStatus.ACTIVE, context.currentGameTime(),
                context.currentGameTime().plusDays(2), null, "").normalized();
        quests.put(quest);
        createLinkedTask(quest, resolved, context.taskWorldId());
        context.participantPlayerIds().forEach(playerId -> memories.append(new MemoryRecord(
                UUID.randomUUID(), context.issuerNpcId(), playerId, Instant.now(),
                MemoryType.COMMITMENT, 0.8,
                "Quest created: " + quest.storySummary() + " [questId="
                        + quest.questId() + ", target=" + resolved.displayName() + "]")));
        return QuestCreationResult.accept(quest);
    }

    /** Progress is accepted only from authoritative events carrying this persisted quest ID. */
    public synchronized Optional<DynamicQuest> onEvent(NpcFrameworkEvent untrustedEvent) {
        NpcFrameworkEvent event = untrustedEvent.normalized();
        String questIdText = event.facts().getOrDefault("questId", "");
        UUID questId;
        try {
            questId = UUID.fromString(questIdText);
        } catch (IllegalArgumentException failure) {
            return Optional.empty();
        }
        DynamicQuest quest = quests.get(questId).orElse(null);
        if (quest == null || quest.terminal() || !eventMatchesQuest(event, quest)) {
            return Optional.empty();
        }
        if (event.type() == NpcEventType.TASK_FAILED) {
            DynamicQuest failed = quest.withStatus(QuestStatus.FAILED,
                    gameTime(event.occurredAt()),
                    event.facts().getOrDefault("reason", "Authoritative task failed"));
            quests.put(failed);
            persistConsequence(failed, false);
            return Optional.of(failed);
        }
        List<QuestObjective> objectives = new ArrayList<>(quest.objectives());
        for (int index = 0; index < objectives.size(); index++) {
            QuestObjective objective = objectives.get(index);
            if (objective.status() == QuestObjectiveStatus.PENDING) {
                objectives.set(index, objective.progress(1));
                break;
            }
        }
        boolean complete = objectives.stream()
                .allMatch(value -> value.status() == QuestObjectiveStatus.COMPLETE);
        DynamicQuest updated = quest.withObjectives(objectives,
                complete ? QuestStatus.COMPLETED : QuestStatus.ACTIVE,
                gameTime(event.occurredAt()), complete ? "All authoritative objectives completed" : "");
        quests.put(updated);
        if (complete) {
            persistConsequence(updated, true);
        }
        return Optional.of(updated);
    }

    public DynamicQuest fail(UUID questId, String reason, LocalDateTime gameTime) {
        DynamicQuest quest = quests.get(questId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown quest " + questId));
        DynamicQuest failed = quest.withStatus(QuestStatus.FAILED,
                gameTime == null ? LocalDateTime.now() : gameTime,
                reason == null ? "Quest failed" : reason);
        quests.put(failed);
        persistConsequence(failed, false);
        return failed;
    }

    private void persistConsequence(DynamicQuest quest, boolean success) {
        for (UUID playerId : quest.participantPlayerIds()) {
            memories.append(new MemoryRecord(UUID.randomUUID(), quest.issuerNpcId(), playerId,
                    Instant.now(), MemoryType.TASK, success ? 0.9 : 0.85,
                    (success ? "Player completed" : "Player failed") + " quest: "
                            + quest.storySummary() + " [questId=" + quest.questId() + "]"));
            relationships.adjust(quest.issuerNpcId(), playerId, 0,
                    success ? 4 : -3, 0, success ? 3 : -2, 0, 0,
                    success ? 1 : 0, Instant.now());
        }
    }

    private void createLinkedTask(
            DynamicQuest quest, ResolvedWorldTarget target, UUID taskWorldId) {
        UUID player = quest.participantPlayerIds().iterator().next();
        tasks.put(new NpcTask(UUID.randomUUID(), quest.issuerNpcId(), player,
                taskType(quest.questType()), target.worldId() == null ? taskWorldId : target.worldId(),
                target.x(), target.y(), target.z(), Instant.now(), quest.storySummary(),
                NpcTaskState.PLANNED, Instant.now(), "",
                Map.of("questId", quest.questId().toString(),
                        "targetId", target.authoritativeId())));
    }

    private static Optional<ResolvedWorldTarget> resolveTarget(
            QuestProposal proposal, QuestOpportunityContext context) {
        QuestTargetKind kind = expectedKind(proposal.questType());
        String query = normalize(proposal.requestedTarget());
        List<ResolvedWorldTarget> candidates = context.authoritativeTargets().stream()
                .filter(target -> target.kind() == kind).toList();
        if (query.isBlank()) {
            return candidates.stream().findFirst();
        }
        return candidates.stream().filter(target -> normalize(target.displayName()).contains(query)
                        || normalize(target.authoritativeId()).contains(query)
                        || target.tags().stream().map(DynamicQuestDirector::normalize)
                                .anyMatch(tag -> tag.contains(query)))
                .findFirst();
    }

    private static QuestTargetKind expectedKind(QuestType type) {
        return switch (type) {
            case FETCH, DELIVER, CRAFT -> QuestTargetKind.ITEM;
            case ESCORT, HUNT, HELP_NPC -> QuestTargetKind.ENTITY;
            case RETURN_HOME, DEFEND, MEET, INVESTIGATE -> QuestTargetKind.LOCATION;
        };
    }

    private static String objectiveType(QuestType type) {
        return type.name() + "_VALIDATED_TARGET";
    }

    private static String objectiveDescription(
            QuestProposal proposal, ResolvedWorldTarget target) {
        return proposal.questType() + ": " + target.displayName();
    }

    private static String taskType(QuestType type) {
        return switch (type) {
            case FETCH -> "FETCH_ITEM";
            case DELIVER -> "DELIVER_ITEM";
            case ESCORT -> "ESCORT";
            case RETURN_HOME -> "RETURN_HOME";
            case HUNT -> "HUNT";
            case DEFEND -> "DEFEND";
            case MEET -> "MEET_PLAYER";
            case INVESTIGATE -> "SEARCH_WITH_PLAYER";
            case CRAFT -> "CRAFT_FOR_PLAYER";
            case HELP_NPC -> "HELP_NPC";
        };
    }

    private static boolean eventMatchesQuest(NpcFrameworkEvent event, DynamicQuest quest) {
        if (event.type() != NpcEventType.TASK_COMPLETED
                && event.type() != NpcEventType.TASK_FAILED
                && event.type() != NpcEventType.ITEM_GIVEN
                && event.type() != NpcEventType.LOCATION_CHANGED
                && event.type() != NpcEventType.COMBAT_ENDED) {
            return false;
        }
        String eventTarget = event.facts().get("targetId");
        return eventTarget == null || quest.resolvedWorldTargets().stream()
                .anyMatch(target -> target.authoritativeId().equals(eventTarget));
    }

    private static LocalDateTime gameTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ").strip();
    }
}
