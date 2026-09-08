package com.inigmasgames.hytalerpg.combat.hytale;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.inigmasgames.hytalerpg.combat.damage.ConditionalDamage;
import java.util.Map;
import java.util.Set;

/** A single-use per-Damage request, not a global pending-hit map. */
public final class HytaleConditionalDamage {
    private HytaleConditionalDamage(){}
    private static final MetaKey<String> PENDING=Damage.META_REGISTRY.registerMetaObject(ignored->"",false,"InigmasGames:RpgConditionalGather",Codec.STRING);
    public static void attach(Damage damage,ConditionalDamage request){
        if(request!=null&&request.conditions().active())damage.putMetaObject(PENDING,HytaleDamageAdapter.GSON.toJson(request));
    }
    public static boolean pending(Damage damage){String value=damage.getIfPresentMetaObject(PENDING);return value!=null&&!value.isBlank();}
    public static Map<String,Object> gather(Damage damage,double health,double maximum,Set<String> statuses){
        String json=damage.getIfPresentMetaObject(PENDING);if(json==null||json.isBlank())return Map.of();
        damage.putMetaObject(PENDING,""); // Consume before any mutation: duplicate invocation cannot amplify.
        var metadata=HytaleDamageAdapter.metadata(damage);
        if(metadata==null||metadata.origin()==HytaleDamageMetadata.Origin.REDIRECTED||damage.isCancelled())return Map.of("conditionalGate","INELIGIBLE_OR_CANCELLED");
        var request=HytaleDamageAdapter.GSON.fromJson(json,ConditionalDamage.class);
        // Installed native mitigation/outgoing-effect scaling is in Filter, after this system.
        // Refuse an unexpected earlier writer rather than overwrite or guess its arithmetic.
        if(Float.compare(damage.getAmount(),(float)request.expectedAmount())!=0){
            damage.setCancelled(true);return Map.of("conditionalGate","NATIVE_GATHER_AMOUNT_CHANGED");
        }
        double increased=request.increased(health,maximum,statuses),amount=request.amount(increased);
        if(!Double.isFinite(amount)||amount>Float.MAX_VALUE){damage.setCancelled(true);return Map.of("conditionalGate","CONDITIONAL_DAMAGE_OVERFLOW");}
        damage.setAmount((float)amount);
        damage.putMetaObject(HytaleDamageAdapter.RPG_METADATA,HytaleDamageAdapter.GSON.toJson(new HytaleDamageMetadata(metadata.actorId(),metadata.rootCastId(),metadata.skillInstanceId(),metadata.correlationId(),amount,
                Double.isFinite(health)?health:metadata.targetHealthBefore(),metadata.effectInstanceId(),metadata.canProc(),metadata.origin())));
        return Map.of("conditionalGate","RESOLVED","targetConditionalIncreased",increased,"targetHealthAtGather",Double.isFinite(health)?health:"UNAVAILABLE",
                "targetMaxHealthAtGather",Double.isFinite(maximum)?maximum:"UNAVAILABLE","targetControlAtGather",statuses,"conditionalPreMitigation",amount);
    }
}
