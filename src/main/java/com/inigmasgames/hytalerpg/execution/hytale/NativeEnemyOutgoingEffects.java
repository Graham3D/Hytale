package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.hytale.*;
import java.util.*;

/** Same native outgoing-effect calculation, moved to acceptance only for witnessed ME source snapshots. */
public final class NativeEnemyOutgoingEffects extends DamageSystems.ScaleOutgoingDamageFromEntityEffects {
    @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
            CommandBuffer<EntityStore> buffer,Damage damage){
            try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.DAMAGE)){
        if(NativeDamageLeafReceipts.sourceFactorsResolved(damage))return;
        super.handle(index,chunk,store,buffer,damage);
    
            }}
    /** These amount carriers are never submitted to an event system or native Health. */
    public Map<String,Double> snapshot(Store<EntityStore> store,Ref<EntityStore> actor,Map<DamageCause,Float> calculatorVector){
        if(!store.isInThread()||actor==null||!actor.isValid())throw new IllegalStateException("ENEMY_OUTGOING_SNAPSHOT_BINDING");
        if(calculatorVector.isEmpty()||calculatorVector.size()>32)throw new IllegalArgumentException("ENEMY_OUTGOING_VECTOR_BOUNDS");
        var result=new TreeMap<String,Double>();var source=new Damage.EntitySource(actor);
        for(var entry:calculatorVector.entrySet()){
            if(entry.getKey()==null||entry.getValue()==null||!Float.isFinite(entry.getValue())||entry.getValue()<0)
                throw new IllegalArgumentException("ENEMY_OUTGOING_NATIVE_VECTOR");
            var amount=new Damage(source,entry.getKey(),entry.getValue());
            // Pinned native owner reads source/store/amount only. It performs no submission or resource mutation.
            super.handle(0,null,store,null,amount);
            if(!Float.isFinite(amount.getAmount())||amount.getAmount()<0)throw new IllegalStateException("ENEMY_OUTGOING_NATIVE_OVERFLOW");
            if(result.put(entry.getKey().getId(),(double)amount.getAmount())!=null)throw new IllegalStateException("ENEMY_DUPLICATE_NATIVE_CAUSE_ID");
        }
        return Collections.unmodifiableMap(result);
    }
    public NativeSystemReplacement install(ComponentRegistry<EntityStore> registry){
        return NativeSystemReplacement.install(registry,DamageSystems.ScaleOutgoingDamageFromEntityEffects.class,
                NativeEnemyOutgoingEffects.class,this,"OUTGOING_EFFECTS");
    }
}
