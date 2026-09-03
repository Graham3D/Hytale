package com.inigmasgames.persistentnpcs.conversation;

import com.inigmasgames.persistentnpcs.quest.DynamicQuest;
import com.inigmasgames.persistentnpcs.task.NpcTask;
import java.util.List;

/** Detached deterministic task/quest state used by prompting and output validation. */
public record DialogueRequestState(
        DialogueMode mode,
        List<NpcTask> activeTasks,
        List<DynamicQuest> activeQuests,
        boolean directorContextIncluded) {

    public DialogueRequestState {
        mode = mode == null ? DialogueMode.ORDINARY_CONVERSATION : mode;
        activeTasks = activeTasks == null ? List.of() : List.copyOf(activeTasks);
        activeQuests = activeQuests == null ? List.of() : List.copyOf(activeQuests);
    }

    public boolean hasActiveTask() {
        return !activeTasks.isEmpty();
    }

    public boolean hasActiveQuest() {
        return !activeQuests.isEmpty();
    }

    public String currentActivityReply() {
        if (activeTasks.isEmpty()) {
            return "I'm idle right now.";
        }
        String type = activeTasks.getFirst().type().toUpperCase(java.util.Locale.ROOT);
        if (type.contains("FOLLOW")) return "I'm following you right now.";
        if (type.contains("WAIT") || type.contains("MEET")) return "I'm waiting here right now.";
        if (type.contains("CRAFT") || type.contains("COOK") || type.contains("PROCESS")) {
            return "I'm working on the active crafting task right now.";
        }
        if (type.contains("GO_TO") || type.contains("ESCORT") || type.contains("PATROL")
                || type.contains("RETURN_HOME") || type.contains("FETCH")) {
            return "I'm carrying out the active travel task right now.";
        }
        return "I'm working on my active " + type.toLowerCase(java.util.Locale.ROOT)
                .replace('_', ' ') + " task right now.";
    }
}
