package com.inigmasgames.hytalerpg.combat.hytale;

import java.util.UUID;

public record HytaleDamageMetadata(UUID actorId, String rootCastId, String skillInstanceId,
                                   String correlationId, double preMitigationDamage, double targetHealthBefore,
                                   String effectInstanceId, boolean canProc, Origin origin,
                                   com.inigmasgames.hytalerpg.combat.damage.MonsterAffixSource monsterAffix) {
    public HytaleDamageMetadata {
        if(monsterAffix!=null&&(!monsterAffix.nativeActorId().equals(actorId)||!monsterAffix.rootId().equals(rootCastId)
                ||!"".equals(skillInstanceId)||canProc))throw new IllegalArgumentException("MONSTER_DAMAGE_PROVENANCE_MISMATCH");
    }
    public HytaleDamageMetadata(UUID actorId,String rootCastId,String skillInstanceId,String correlationId,
            double preMitigationDamage,double targetHealthBefore,String effectInstanceId,boolean canProc,Origin origin){
        this(actorId,rootCastId,skillInstanceId,correlationId,preMitigationDamage,targetHealthBefore,effectInstanceId,canProc,origin,null);
    }
    public static HytaleDamageMetadata monster(com.inigmasgames.hytalerpg.combat.damage.MonsterAffixSource source,
            String effect,Origin origin,double amount){
        return new HytaleDamageMetadata(source.nativeActorId(),source.rootId(),"",source.strikeId(),amount,0,effect,false,origin,source);
    }
    public enum Origin { DIRECT, PERIODIC, REFLECTED, REDIRECTED, TRIGGERED }
    public boolean noRetaliation(){return origin==Origin.REFLECTED||origin==Origin.REDIRECTED||origin==Origin.TRIGGERED||monsterAffix!=null&&origin==Origin.PERIODIC;}
    public boolean noLeech(){return origin==Origin.REFLECTED||origin==Origin.REDIRECTED||origin==Origin.TRIGGERED||monsterAffix!=null&&origin==Origin.PERIODIC;}
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
