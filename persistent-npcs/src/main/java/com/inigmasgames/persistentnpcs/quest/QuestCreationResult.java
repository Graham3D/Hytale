package com.inigmasgames.persistentnpcs.quest;

public record QuestCreationResult(
        boolean accepted,
        DynamicQuest quest,
        String reason) {

    public static QuestCreationResult reject(String reason) {
        return new QuestCreationResult(false, null, reason);
    }

    public static QuestCreationResult accept(DynamicQuest quest) {
        return new QuestCreationResult(true, quest, "Quest validated and persisted");
    }
}
