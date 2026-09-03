package com.inigmasgames.persistentnpcs.api;

import com.inigmasgames.persistentnpcs.action.NpcActionContext;

@FunctionalInterface
public interface NpcContextProvider {
    String provide(NpcActionContext context);
}
