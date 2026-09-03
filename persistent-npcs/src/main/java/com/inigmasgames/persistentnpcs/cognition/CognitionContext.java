package com.inigmasgames.persistentnpcs.cognition;

import com.inigmasgames.persistentnpcs.autonomy.AgentOperation;
import com.inigmasgames.persistentnpcs.economy.ObligationRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.perception.RawPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.perception.SemanticWorldModel;
import com.inigmasgames.persistentnpcs.plan.SharedPlan;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.relationship.RelationshipRecord;
import com.inigmasgames.persistentnpcs.task.NpcTask;
import com.inigmasgames.persistentnpcs.conversation.CognitiveContextPlan;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The only context conversational cognition may use for current-world decisions. */
public record CognitionContext(
        UUID responseId,
        UUID conversationId,
        UUID playerId,
        Instant capturedAt,
        NpcProfile profile,
        NpcPerceptionSnapshot perception,
        LocalDateTime worldTime,
        String worldTimeSource,
        String currentActivity,
        List<NpcTask> activeTasks,
        List<ObligationRecord> obligations,
        List<RelationshipRecord> relationships,
        List<MemoryRecord> memories,
        List<SharedPlan> sharedPlans,
        List<SourcedBelief> sourcedBeliefs,
        List<String> validActions,
        AgentOperation activeOperation,
        Set<String> unknownWorldFacts,
        List<String> evidenceRefs,
        RawPerceptionSnapshot rawPerception,
        SemanticWorldModel semanticWorld,
        String memoryRetrievalQuery,
        List<MemoryStore.ScoredMemory> scoredMemories,
        CognitiveContextPlan contextPlan,
        List<MemoryStore.RejectedMemory> rejectedMemories) {

    public CognitionContext {
        activeTasks = List.copyOf(activeTasks == null ? List.of() : activeTasks);
        obligations = List.copyOf(obligations == null ? List.of() : obligations);
        relationships = List.copyOf(relationships == null ? List.of() : relationships);
        memories = List.copyOf(memories == null ? List.of() : memories);
        sharedPlans = List.copyOf(sharedPlans == null ? List.of() : sharedPlans);
        sourcedBeliefs = List.copyOf(sourcedBeliefs == null ? List.of() : sourcedBeliefs);
        validActions = List.copyOf(validActions == null ? List.of() : validActions);
        unknownWorldFacts = Set.copyOf(unknownWorldFacts == null ? Set.of() : unknownWorldFacts);
        evidenceRefs = List.copyOf(evidenceRefs == null ? List.of() : evidenceRefs);
        scoredMemories = List.copyOf(scoredMemories == null ? List.of() : scoredMemories);
        rejectedMemories = List.copyOf(
                rejectedMemories == null ? List.of() : rejectedMemories);
        contextPlan = contextPlan == null
                ? CognitiveContextPlan.full("LEGACY_FULL_CONTEXT") : contextPlan;
        memoryRetrievalQuery = memoryRetrievalQuery == null ? "" : memoryRetrievalQuery;
        worldTimeSource = worldTimeSource == null ? "UNKNOWN" : worldTimeSource;
        currentActivity = currentActivity == null ? "unknown" : currentActivity;
    }

    public boolean hasAuthoritativeWorldTime() {
        return worldTime != null && "WorldTimeResource".equals(worldTimeSource);
    }
}
