package com.inigmasgames.persistentnpcs.action;

@FunctionalInterface
public interface NpcActionValidator {
    NpcActionResult validate(NpcActionRequest request, NpcActionContext context);
}
