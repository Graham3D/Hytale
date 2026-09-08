package com.inigmasgames.hytalerpg.combat.hytale;

import java.util.UUID;

public record HytaleDamageMetadata(UUID actorId, String rootCastId, String skillInstanceId,
                                   String correlationId, double preMitigationDamage, double targetHealthBefore,
                                   String effectInstanceId, boolean canProc, Origin origin) {
    public enum Origin { DIRECT, PERIODIC, REFLECTED, REDIRECTED }
    public boolean noLeech(){return origin==Origin.REFLECTED||origin==Origin.REDIRECTED;}
    public boolean noCredit(){return origin==Origin.REFLECTED||origin==Origin.REDIRECTED;}
    public HytaleDamageMetadata(UUID actorId,String rootCastId,String skillInstanceId,String correlationId,
            double preMitigationDamage,double targetHealthBefore,String effectInstanceId,boolean canProc){
        this(actorId,rootCastId,skillInstanceId,correlationId,preMitigationDamage,targetHealthBefore,effectInstanceId,canProc,Origin.DIRECT);
    }
    public HytaleDamageMetadata(UUID actorId,String rootCastId,String skillInstanceId,String correlationId,
            double preMitigationDamage,double targetHealthBefore) {
        this(actorId,rootCastId,skillInstanceId,correlationId,preMitigationDamage,targetHealthBefore,skillInstanceId,true);
    }
}
