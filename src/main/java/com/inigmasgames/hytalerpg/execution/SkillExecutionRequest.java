package com.inigmasgames.hytalerpg.execution;

import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.UUID;

public record SkillExecutionRequest(UUID actorId, SkillSlot slot, String action, int chainId,
                                    String correlationId, Vec3 desiredMovement, Origin origin, int fireballChargeStage) {
    public enum Origin { MANUAL, TRIGGERED }
    public SkillExecutionRequest(UUID actorId,SkillSlot slot,String action,int chainId,String correlationId,Vec3 movement){
        this(actorId,slot,action,chainId,correlationId,movement,Origin.MANUAL,0);
    }
    public SkillExecutionRequest(UUID actorId,SkillSlot slot,String action,int chainId,String correlationId,Vec3 movement,Origin origin){
        this(actorId,slot,action,chainId,correlationId,movement,origin,0);
    }
    public SkillExecutionRequest {
        if (actorId == null || slot == null || action == null || correlationId == null || correlationId.isBlank()||origin==null)
            throw new IllegalArgumentException("Execution request identity is required");
        if (desiredMovement == null) desiredMovement = new Vec3(0, 0, 0);
        if(fireballChargeStage<0||fireballChargeStage>4)throw new IllegalArgumentException("INVALID_FIREBALL_CHARGE_STAGE");
    }
}
