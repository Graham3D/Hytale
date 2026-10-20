package com.inigmasgames.hytalerpg.combat.hytale;

import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.inigmasgames.hytalerpg.combat.damage.ConditionalDamage;
import java.util.Map;
import java.util.Set;

/** A single-use per-Damage request, not a global pending-hit map. */
public final class HytaleConditionalDamage {
    private HytaleConditionalDamage(){}
    private static final MetaKey<ConditionalDamage> PENDING=Damage.META_REGISTRY.registerMetaObject(ignored->null,false,"InigmasGames:RpgConditionalGather",null);
    private static final MetaKey<Double> VICTIM_FACTOR=Damage.META_REGISTRY.registerMetaObject(ignored->1d,false,"InigmasGames:RpgVictimCoefficient",null);
    public static double victimFactor(Damage damage){var value=damage.getIfPresentMetaObject(VICTIM_FACTOR);return value==null?1:value;}
    public static boolean requiresFacing(Damage damage){var value=damage.getIfPresentMetaObject(PENDING);return value!=null&&value.victimCoefficient()==com.inigmasgames.hytalerpg.combat.damage.VictimCoefficient.BACKSTAB;}
    public static void attach(Damage damage,ConditionalDamage request){
        if(request!=null&&request.active())damage.putMetaObject(PENDING,request);
    }
    public static boolean pending(Damage damage){return damage.getIfPresentMetaObject(PENDING)!=null;}
    public static Map<String,Object> gather(Damage damage,double health,double maximum,Set<String> statuses){
        return gather(damage,health,maximum,statuses,null,null);
    }
    public static Map<String,Object> gather(Damage damage,double health,double maximum,Set<String> statuses,
            com.inigmasgames.hytalerpg.execution.math.Vec3 targetForward,com.inigmasgames.hytalerpg.execution.math.Vec3 casterMinusTarget){
        var request=damage.getIfPresentMetaObject(PENDING);if(request==null)return Map.of();
        damage.putMetaObject(PENDING,null); // Consume before any mutation: duplicate invocation cannot amplify.
        var metadata=HytaleDamageAdapter.metadata(damage);
        if(metadata==null||metadata.origin()==HytaleDamageMetadata.Origin.REDIRECTED||damage.isCancelled())return Map.of("conditionalGate","INELIGIBLE_OR_CANCELLED");
        // Installed native mitigation/outgoing-effect scaling is in Filter, after this system.
        // Refuse an unexpected earlier writer rather than overwrite or guess its arithmetic.
        if(Float.compare(damage.getAmount(),(float)request.expectedAmount())!=0){
            damage.setCancelled(true);return Map.of("conditionalGate","NATIVE_GATHER_AMOUNT_CHANGED");
        }
        double victimFactor;
        try { victimFactor=request.victimCoefficient().factor(health,maximum,targetForward,casterMinusTarget); }
        catch(IllegalArgumentException unavailable){damage.setCancelled(true);return Map.of("conditionalGate",unavailable.getMessage());}
        double increased=request.increased(health,maximum,statuses),amount=request.amount(increased)*victimFactor;
        if(!Double.isFinite(amount)||amount>Float.MAX_VALUE){damage.setCancelled(true);return Map.of("conditionalGate","CONDITIONAL_DAMAGE_OVERFLOW");}
        damage.setAmount((float)amount);
        damage.putMetaObject(VICTIM_FACTOR,victimFactor);
        damage.putMetaObject(HytaleDamageAdapter.RPG_METADATA,HytaleDamageAdapter.GSON.toJson(new HytaleDamageMetadata(metadata.actorId(),metadata.rootCastId(),metadata.skillInstanceId(),metadata.correlationId(),amount,
                Double.isFinite(health)?health:metadata.targetHealthBefore(),metadata.effectInstanceId(),metadata.canProc(),metadata.origin())));
        return Map.of("conditionalGate","RESOLVED","targetConditionalIncreased",increased,"targetHealthAtGather",Double.isFinite(health)?health:"UNAVAILABLE",
                "targetMaxHealthAtGather",Double.isFinite(maximum)?maximum:"UNAVAILABLE","targetControlAtGather",statuses,"conditionalPreMitigation",amount,
                "victimCoefficientRule",request.victimCoefficient().name(),"victimCoefficientFactor",victimFactor);
    }
}
