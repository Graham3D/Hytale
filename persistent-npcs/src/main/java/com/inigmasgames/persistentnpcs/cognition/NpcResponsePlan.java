package com.inigmasgames.persistentnpcs.cognition;

import com.inigmasgames.persistentnpcs.voice.VocalState;
import java.util.List;

public record NpcResponsePlan(
        List<AttentionAction> attentionActions,
        String emote,
        String followUpQuestion,
        String requestedGameAction,
        VocalState vocalState) {

    public NpcResponsePlan {
        attentionActions = List.copyOf(attentionActions == null ? List.of() : attentionActions);
        emote = emote == null ? "" : emote;
        followUpQuestion = followUpQuestion == null ? "" : followUpQuestion;
        requestedGameAction = requestedGameAction == null ? "" : requestedGameAction;
    }
}
