package com.inigmasgames.persistentnpcs.scene;

public record NpcSceneOutcome(
        NpcSceneService.Scene scene,
        int generatedTurns,
        boolean budgetStopped,
        String outcomeSummary) {
}
