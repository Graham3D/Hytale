package com.inigmasgames.persistentnpcs.scene;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded NPC-to-NPC scene state; dialogue generation is event-driven, never a free loop. */
public final class NpcSceneService {
    private final int maximumTurns;
    private final int cooldownSeconds;
    private final Map<UUID, Scene> active = new ConcurrentHashMap<>();
    private final Map<String, Instant> cooldowns = new ConcurrentHashMap<>();

    public NpcSceneService(int maximumTurns, int cooldownSeconds) {
        this.maximumTurns = Math.max(1, maximumTurns);
        this.cooldownSeconds = Math.max(1, cooldownSeconds);
    }

    public synchronized Scene start(
            UUID firstNpc, UUID secondNpc, String kind, double distance, Instant now) {
        if (firstNpc == null || secondNpc == null || firstNpc.equals(secondNpc)
                || distance > 8.0) {
            throw new IllegalArgumentException("Scene requires two distinct nearby NPCs");
        }
        String pair = pair(firstNpc, secondNpc);
        Instant until = cooldowns.get(pair);
        if (until != null && now.isBefore(until)) {
            throw new IllegalStateException("NPC pair is on scene cooldown");
        }
        Scene scene = new Scene(UUID.randomUUID(), firstNpc, secondNpc,
                kind == null ? "CONVERSATION" : kind, now,
                maximumTurns, new ArrayList<>(), false);
        active.put(scene.sceneId(), scene);
        return scene.snapshot();
    }

    public synchronized Scene append(UUID sceneId, UUID speakerNpc, String utterance) {
        Scene scene = active.get(sceneId);
        if (scene == null || scene.complete()) {
            throw new IllegalStateException("Scene is not active");
        }
        UUID expected = scene.turns().size() % 2 == 0 ? scene.firstNpc() : scene.secondNpc();
        if (!expected.equals(speakerNpc)) {
            throw new IllegalArgumentException("Scene turn order rejected");
        }
        scene.turns().add(new SceneTurn(speakerNpc, utterance, Instant.now()));
        if (scene.turns().size() >= scene.maximumTurns()) {
            finish(scene, Instant.now());
        }
        return scene.snapshot();
    }

    public synchronized Scene interrupt(UUID sceneId, Instant now) {
        Scene scene = active.get(sceneId);
        if (scene != null) {
            finish(scene, now);
            return scene.snapshot();
        }
        return null;
    }

    private void finish(Scene scene, Instant now) {
        scene.complete = true;
        cooldowns.put(pair(scene.firstNpc(), scene.secondNpc()),
                now.plusSeconds(cooldownSeconds));
        active.remove(scene.sceneId());
    }

    private static String pair(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    public static final class Scene {
        private final UUID sceneId;
        private final UUID firstNpc;
        private final UUID secondNpc;
        private final String kind;
        private final Instant startedAt;
        private final int maximumTurns;
        private final List<SceneTurn> turns;
        private boolean complete;

        private Scene(UUID sceneId, UUID firstNpc, UUID secondNpc, String kind,
                Instant startedAt, int maximumTurns, List<SceneTurn> turns,
                boolean complete) {
            this.sceneId = sceneId;
            this.firstNpc = firstNpc;
            this.secondNpc = secondNpc;
            this.kind = kind;
            this.startedAt = startedAt;
            this.maximumTurns = maximumTurns;
            this.turns = turns;
            this.complete = complete;
        }

        public UUID sceneId() { return sceneId; }
        public UUID firstNpc() { return firstNpc; }
        public UUID secondNpc() { return secondNpc; }
        public String kind() { return kind; }
        public Instant startedAt() { return startedAt; }
        public int maximumTurns() { return maximumTurns; }
        public List<SceneTurn> turns() { return turns; }
        public boolean complete() { return complete; }

        private Scene snapshot() {
            return new Scene(sceneId, firstNpc, secondNpc, kind, startedAt,
                    maximumTurns, new ArrayList<>(turns), complete);
        }
    }

    public record SceneTurn(UUID speakerNpcId, String utterance, Instant at) { }
}
