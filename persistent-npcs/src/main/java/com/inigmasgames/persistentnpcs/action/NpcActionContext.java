package com.inigmasgames.persistentnpcs.action;

import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.perception.KnownNpcLocatorResult;

public record NpcActionContext(
        NpcProfile profile,
        ConversationSession session,
        NpcPerceptionSnapshot perception,
        String playerMessage,
        KnownNpcLocatorResult knownNpcLocator) {

    public NpcActionContext(
            NpcProfile profile, ConversationSession session,
            NpcPerceptionSnapshot perception, String playerMessage) {
        this(profile, session, perception, playerMessage, null);
    }

    public NpcActionContext(
            NpcProfile profile,
            ConversationSession session,
            NpcPerceptionSnapshot perception) {
        this(profile, session, perception, "", null);
    }
}
