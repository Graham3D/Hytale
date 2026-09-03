package com.inigmasgames.persistentnpcs.task;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NpcTask(
        UUID taskId,
        UUID npcId,
        UUID requesterPlayerId,
        String type,
        UUID worldId,
        Double targetX,
        Double targetY,
        Double targetZ,
        Instant scheduledGameTime,
        String purpose,
        NpcTaskState state,
        Instant createdAt,
        String lastResult,
        Map<String, String> data) {

    public NpcTask(
            UUID taskId,
            UUID npcId,
            UUID requesterPlayerId,
            String type,
            UUID worldId,
            Double targetX,
            Double targetY,
            Double targetZ,
            Instant scheduledGameTime,
            String purpose,
            NpcTaskState state,
            Instant createdAt,
            String lastResult) {
        this(taskId, npcId, requesterPlayerId, type, worldId, targetX, targetY,
                targetZ, scheduledGameTime, purpose, state, createdAt, lastResult,
                Map.of());
    }

    public NpcTask normalized() {
        return new NpcTask(taskId, npcId, requesterPlayerId, type, worldId,
                targetX, targetY, targetZ, scheduledGameTime, purpose,
                state == null ? NpcTaskState.PLANNED : state,
                createdAt == null ? Instant.now() : createdAt,
                lastResult, data == null ? Map.of() : Map.copyOf(data));
    }

    public NpcTask withState(NpcTaskState newState, String result) {
        return new NpcTask(taskId, npcId, requesterPlayerId, type, worldId,
                targetX, targetY, targetZ, scheduledGameTime, purpose,
                newState, createdAt, result, data);
    }

    public NpcTask withTarget(double x, double y, double z) {
        return new NpcTask(taskId, npcId, requesterPlayerId, type, worldId,
                x, y, z, scheduledGameTime, purpose, state, createdAt,
                lastResult, data);
    }

    public NpcTask withData(Map<String, String> newData) {
        return new NpcTask(taskId, npcId, requesterPlayerId, type, worldId,
                targetX, targetY, targetZ, scheduledGameTime, purpose, state,
                createdAt, lastResult, Map.copyOf(newData));
    }

    public boolean terminal() {
        return state == NpcTaskState.COMPLETED || state == NpcTaskState.FAILED
                || state == NpcTaskState.CANCELLED;
    }
}
