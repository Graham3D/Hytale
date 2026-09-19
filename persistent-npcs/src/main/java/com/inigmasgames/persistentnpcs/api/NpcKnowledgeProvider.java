package com.inigmasgames.persistentnpcs.api;

import com.inigmasgames.persistentnpcs.profile.NpcProfile;

@FunctionalInterface
public interface NpcKnowledgeProvider {
    String provide(NpcProfile profile, String query);
}
