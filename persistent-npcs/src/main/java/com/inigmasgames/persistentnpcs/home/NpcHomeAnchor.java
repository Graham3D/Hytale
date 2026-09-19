package com.inigmasgames.persistentnpcs.home;

import java.time.Instant;
import java.util.UUID;
import org.joml.Vector3d;

/** Persistent original home, current anchor, and exclusive locomotion state. */
public record NpcHomeAnchor(
        UUID npcId,
        UUID worldId,
        double homeX,
        double homeY,
        double homeZ,
        double anchorX,
        double anchorY,
        double anchorZ,
        boolean temporaryAnchor,
        NpcMovementState movementState,
        Double targetX,
        Double targetY,
        Double targetZ,
        Instant stateDueAt,
        Instant nextWanderAt) {

    public NpcHomeAnchor normalized() {
        return new NpcHomeAnchor(npcId, worldId, homeX, homeY, homeZ,
                anchorX, anchorY, anchorZ, temporaryAnchor,
                movementState == null ? NpcMovementState.IDLE_HOME : movementState,
                targetX, targetY, targetZ, stateDueAt,
                nextWanderAt == null ? Instant.EPOCH : nextWanderAt);
    }

    public Vector3d home() {
        return new Vector3d(homeX, homeY, homeZ);
    }

    public Vector3d anchor() {
        return new Vector3d(anchorX, anchorY, anchorZ);
    }

    public Vector3d target() {
        return targetX == null || targetY == null || targetZ == null
                ? null : new Vector3d(targetX, targetY, targetZ);
    }

    public NpcHomeAnchor withState(
            NpcMovementState state, Vector3d target, Instant dueAt, Instant nextWander) {
        return new NpcHomeAnchor(npcId, worldId, homeX, homeY, homeZ,
                anchorX, anchorY, anchorZ, temporaryAnchor, state,
                target == null ? null : target.x,
                target == null ? null : target.y,
                target == null ? null : target.z,
                dueAt, nextWander == null ? nextWanderAt : nextWander).normalized();
    }

    public NpcHomeAnchor withAnchor(Vector3d value, boolean temporary, Instant nextWander) {
        return new NpcHomeAnchor(npcId, worldId, homeX, homeY, homeZ,
                value.x, value.y, value.z, temporary, NpcMovementState.IDLE_HOME,
                null, null, null, null, nextWander).normalized();
    }
}
