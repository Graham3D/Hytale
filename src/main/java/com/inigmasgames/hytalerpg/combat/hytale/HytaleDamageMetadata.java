package com.inigmasgames.hytalerpg.combat.hytale;

import java.util.UUID;

public record HytaleDamageMetadata(UUID actorId, String rootCastId, String skillInstanceId,
                                   String correlationId, double preMitigationDamage, double targetHealthBefore,
                                   String effectInstanceId, boolean canProc) {
    public HytaleDamageMetadata(UUID actorId,String rootCastId,String skillInstanceId,String correlationId,
            double preMitigationDamage,double targetHealthBefore) {
        this(actorId,rootCastId,skillInstanceId,correlationId,preMitigationDamage,targetHealthBefore,skillInstanceId,true);
    }
}
