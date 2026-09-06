package com.inigmasgames.hytalerpg.execution;

import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.UUID;

public record SkillExecutionRequest(UUID actorId, SkillSlot slot, String action, int chainId,
                                    String correlationId, Vec3 desiredMovement) {
    public SkillExecutionRequest {
        if (actorId == null || slot == null || action == null || correlationId == null || correlationId.isBlank())
            throw new IllegalArgumentException("Execution request identity is required");
        if (desiredMovement == null) desiredMovement = new Vec3(0, 0, 0);
    }
}
