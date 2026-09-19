package com.inigmasgames.persistentnpcs.action;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface NpcActionExecutor {
    CompletableFuture<NpcActionResult> execute(
            NpcActionRequest request, NpcActionContext context);
}
