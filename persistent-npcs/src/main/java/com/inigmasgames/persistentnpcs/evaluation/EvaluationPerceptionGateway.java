package com.inigmasgames.persistentnpcs.evaluation;

import com.inigmasgames.persistentnpcs.perception.NpcPerceptionGateway;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.perception.PerceivedItem;
import com.inigmasgames.persistentnpcs.perception.RawPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Detached implementation of the production perception boundary; it never touches Hytale ECS. */
public final class EvaluationPerceptionGateway implements NpcPerceptionGateway {
    private final EvaluationContracts.ScenarioWorldState world;
    private final UUID worldId;

    public EvaluationPerceptionGateway(EvaluationContracts.ScenarioWorldState world,
            UUID worldId) {
        this.world = java.util.Objects.requireNonNull(world, "scenario world");
        this.worldId = java.util.Objects.requireNonNull(worldId, "world id");
    }

    @Override public CompletableFuture<RawPerceptionSnapshot> captureRaw(
            NpcProfile profile, UUID focusedPlayerId, UUID responseId) {
        Set<String> inventory = world.inventory().getOrDefault(focusedPlayerId, Set.of());
        PerceivedItem held = inventory.stream().sorted().findFirst().map(value ->
                new PerceivedItem(null, value, display(value), 1, 0, 0,
                        "{\"source\":\"evaluation-scenario\"}", 2)).orElse(null);
        NpcPerceptionSnapshot snapshot = new NpcPerceptionSnapshot(profile.id(), profile.id(),
                worldId, LocalDateTime.of(1, 1, 15, 12, 0), world.x(), world.y(), world.z(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                held == null ? null : 0, held, List.of());
        return CompletableFuture.completedFuture(new RawPerceptionSnapshot(responseId,
                Instant.now(), "orbis-evaluation-detached", 0, snapshot, 0, List.of()));
    }

    private static String display(String value) {
        if (value == null) return "";
        String name = value.replaceAll("^(?:Item|Weapon|Tool)_", "")
                .replace('_', ' ').strip();
        return name.isBlank() ? value : java.util.Arrays.stream(name.split(" "))
                .map(word -> word.isBlank() ? word : Character.toUpperCase(word.charAt(0))
                        + word.substring(1).toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.joining(" "));
    }
}
