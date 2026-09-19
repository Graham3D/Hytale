package com.inigmasgames.persistentnpcs.llm;

import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;

/** Optional semantic model-tier routing hook preserved through provider decorators. */
public interface ConversationModelRoutingProvider {
    ModelTier selectTier(ConversationSession session, NpcProfile profile, String playerMessage);
}
