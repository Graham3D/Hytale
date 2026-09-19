package com.inigmasgames.persistentnpcs.cognition;

import java.util.List;

/** Compact separation of stable authored identity and evolving runtime state. */
public record NpcSelfModel(
        String identity,
        String speciesArchetype,
        String occupation,
        List<String> values,
        List<String> personalityTraits,
        List<String> fears,
        List<String> longTermGoals,
        String currentGoal,
        String currentNeed,
        NpcEmotionalState emotion,
        String focusedRelationship,
        String currentTask,
        String locationAwareness,
        String physicalState) {

    public String compact() {
        return "identity=" + identity + "; species=" + speciesArchetype
                + "; occupation=" + occupation + "; values=" + values
                + "; traits=" + personalityTraits + "; fears=" + fears
                + "; longTermGoals=" + longTermGoals + "; currentGoal=" + currentGoal
                + "; currentNeed=" + currentNeed + "; emotion=" + emotion.emotion()
                + "(" + "%.2f".formatted(emotion.intensity()) + ")"
                + "; relationship=" + focusedRelationship + "; task=" + currentTask
                + "; locationAwareness=" + locationAwareness
                + "; physicalState=" + physicalState;
    }
}
