package com.inigmasgames.persistentnpcs.evaluation;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.action.NpcActionDefinition;
import com.inigmasgames.persistentnpcs.action.NpcActionRegistry;
import com.inigmasgames.persistentnpcs.action.NpcActionResult;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Scenario-owned action boundary: validates real action contracts, never touches Hytale ECS. */
public final class EvaluationActionRegistry {
    private EvaluationActionRegistry() { }
    public static NpcActionRegistry create(Iterable<NpcProfile> profiles) {
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        profiles.forEach(profile -> capabilities.addAll(profile.capabilities()));
        NpcActionRegistry registry = new NpcActionRegistry();
        for (String raw : capabilities) {
            String id = raw == null ? "" : raw.strip().toUpperCase(java.util.Locale.ROOT);
            if (id.isBlank()) continue;
            JsonObject schema = new JsonObject(); schema.addProperty("type", "object");
            registry.register(new NpcActionDefinition(id,
                    "Execute " + id + " against the isolated evaluation world adapter.",
                    schema, Set.of(id), Set.of(), ignored -> true,
                    (request, context) -> NpcActionResult.success(
                            "Evaluation validation accepted " + id + "."),
                    (request, context) -> CompletableFuture.completedFuture(
                            NpcActionResult.success("Evaluation world completed " + id + ".")),
                    "Evaluation world completed " + id + "."));
        }
        return registry;
    }
}
