package com.inigmasgames.persistentnpcs.api;

import com.inigmasgames.persistentnpcs.action.NpcActionDefinition;
import com.inigmasgames.persistentnpcs.action.NpcActionRegistry;
import com.inigmasgames.persistentnpcs.event.NpcEventBus;
import com.inigmasgames.persistentnpcs.event.NpcFrameworkEvent;
import com.inigmasgames.persistentnpcs.event.NpcTriggerDefinition;
import com.inigmasgames.persistentnpcs.event.NpcTriggerService;
import com.inigmasgames.persistentnpcs.quest.DynamicQuestDirector;
import com.inigmasgames.persistentnpcs.quest.QuestCreationResult;
import com.inigmasgames.persistentnpcs.quest.QuestOpportunityContext;
import com.inigmasgames.persistentnpcs.quest.QuestProposal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import com.inigmasgames.persistentnpcs.plan.SharedPlan;
import com.inigmasgames.persistentnpcs.plan.SharedPlanStore;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.scene.NpcAssignmentState;
import com.inigmasgames.persistentnpcs.scene.NpcAssignmentStore;
import com.inigmasgames.persistentnpcs.scene.NpcConversationTrigger;
import com.inigmasgames.persistentnpcs.scene.NpcConversationTriggerService;
import com.inigmasgames.persistentnpcs.scene.NpcSceneContext;
import com.inigmasgames.persistentnpcs.scene.NpcSceneOutcome;
import com.inigmasgames.persistentnpcs.scene.NpcSceneRunner;

/** Registration surface for other server mods without core source changes. */
public final class PersistentNpcsApi {
    private static volatile PersistentNpcsApi instance;
    private final NpcActionRegistry actions;
    private final NpcTriggerService triggers;
    private final NpcEventBus events;
    private final DynamicQuestDirector quests;
    private final SharedPlanStore sharedPlans;
    private final NpcAssignmentStore assignments;
    private final NpcConversationTriggerService conversationTriggers;
    private final NpcSceneRunner scenes;
    private final CopyOnWriteArrayList<NpcContextProvider> contextProviders =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<NpcKnowledgeProvider> knowledgeProviders =
            new CopyOnWriteArrayList<>();

    public PersistentNpcsApi(
            NpcActionRegistry actions, NpcTriggerService triggers, NpcEventBus events) {
        this(actions, triggers, events, null, null, null, null, null);
    }

    public PersistentNpcsApi(
            NpcActionRegistry actions,
            NpcTriggerService triggers,
            NpcEventBus events,
            DynamicQuestDirector quests) {
        this(actions, triggers, events, quests, null, null, null, null);
    }

    public PersistentNpcsApi(
            NpcActionRegistry actions,
            NpcTriggerService triggers,
            NpcEventBus events,
            DynamicQuestDirector quests,
            SharedPlanStore sharedPlans,
            NpcAssignmentStore assignments,
            NpcConversationTriggerService conversationTriggers,
            NpcSceneRunner scenes) {
        this.actions = actions;
        this.triggers = triggers;
        this.events = events;
        this.quests = quests;
        this.sharedPlans = sharedPlans;
        this.assignments = assignments;
        this.conversationTriggers = conversationTriggers;
        this.scenes = scenes;
    }

    public static void initialize(PersistentNpcsApi api) {
        if (instance != null) {
            throw new IllegalStateException("Persistent NPC API is already initialized");
        }
        instance = api;
    }

    public static PersistentNpcsApi get() {
        PersistentNpcsApi value = instance;
        if (value == null) {
            throw new IllegalStateException("Persistent NPC API is not initialized");
        }
        return value;
    }

    public static void shutdown() {
        instance = null;
    }

    public void registerAction(NpcActionDefinition definition) {
        actions.register(definition);
    }

    public void registerTrigger(NpcTriggerDefinition definition) {
        triggers.register(definition);
    }

    public QuestCreationResult proposeQuest(
            QuestProposal proposal, QuestOpportunityContext authoritativeContext) {
        if (quests == null) {
            return QuestCreationResult.reject("Dynamic quest director is unavailable");
        }
        return quests.createValidated(proposal, authoritativeContext);
    }

    public void emit(NpcFrameworkEvent event) {
        events.emit(event);
    }

    public NpcAssignmentState recordAssignment(NpcAssignmentState assignment) {
        if (assignments == null) {
            throw new IllegalStateException("NPC assignment persistence is unavailable");
        }
        return assignments.put(assignment);
    }

    public Optional<NpcConversationTrigger> overdueAssignmentReturn(
            UUID assignmentId,
            boolean employerPresent,
            boolean workerAtRelevantWorkplace,
            Instant now) {
        if (conversationTriggers == null) return Optional.empty();
        return conversationTriggers.overdueReturn(assignmentId, employerPresent,
                workerAtRelevantWorkplace, now);
    }

    /** Runs a bounded exact-text NPC scene and acknowledges/retries its trigger safely. */
    public CompletableFuture<NpcSceneOutcome> runNpcConversation(
            NpcProfile speaker,
            NpcProfile listener,
            NpcConversationTrigger trigger,
            NpcSceneContext spatialContext) {
        if (scenes == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("NPC scene runner is unavailable"));
        }
        return scenes.run(speaker, listener, trigger, spatialContext)
                .whenComplete((outcome, failure) -> {
                    if (conversationTriggers == null) return;
                    if (failure == null && outcome != null && outcome.generatedTurns() > 0) {
                        conversationTriggers.markAddressed(trigger.triggerId());
                    } else {
                        conversationTriggers.release(trigger.triggerId());
                    }
                });
    }

    public List<SharedPlan> sharedPlansFor(UUID participantId) {
        return sharedPlans == null ? List.of() : sharedPlans.activeFor(participantId);
    }

    public void registerContextProvider(NpcContextProvider provider) {
        contextProviders.add(provider);
    }

    public void registerKnowledgeProvider(NpcKnowledgeProvider provider) {
        knowledgeProviders.add(provider);
    }

    public List<NpcContextProvider> contextProviders() {
        return List.copyOf(contextProviders);
    }

    public List<NpcKnowledgeProvider> knowledgeProviders() {
        return List.copyOf(knowledgeProviders);
    }
}
