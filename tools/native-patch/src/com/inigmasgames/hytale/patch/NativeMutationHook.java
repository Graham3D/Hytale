package com.inigmasgames.hytale.patch;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.ChangeStatBehaviour;
import com.hypixel.hytale.protocol.ValueType;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Neutral, version-pinned native leaf hook. The mod owns all admission policy. */
public final class NativeMutationHook {
    public static final String PATCH_ID="me-packbound-0.7.0-pre.5.1-1";
    public enum Kind { CLEAR_ENTITY_EFFECT, CHANGE_STAT, CHANGE_STAT_WITH_MODIFIER }
    public record Mutation(Kind kind,Ref<EntityStore> source,Ref<EntityStore> target,
                           String effectId,Map<Integer,Float> statValues,
                           ValueType valueType,ChangeStatBehaviour behavior) {
        public Mutation { Objects.requireNonNull(kind);Objects.requireNonNull(target);
            statValues=Map.copyOf(statValues); }
    }
    @FunctionalInterface public interface Policy { boolean allow(Mutation mutation); }
    private static final AtomicReference<Policy> POLICY=new AtomicReference<>();
    private NativeMutationHook(){}
    public static boolean installed(){return POLICY.get()!=null;}
    public static AutoCloseable install(Policy policy){
        Objects.requireNonNull(policy);
        if(!POLICY.compareAndSet(null,policy))throw new IllegalStateException("NATIVE_MUTATION_POLICY_DUPLICATE");
        return ()->POLICY.compareAndSet(policy,null);
    }
    public static boolean allowClear(InteractionContext context,Ref<EntityStore> target,String effectId){
        var policy=POLICY.get();
        return policy==null||policy.allow(new Mutation(Kind.CLEAR_ENTITY_EFFECT,context.getOwningEntity(),target,
                effectId,Map.of(),null,null));
    }
    public static boolean allowStat(InteractionContext context,Ref<EntityStore> target,Int2FloatMap values,
                                    ValueType valueType,ChangeStatBehaviour behavior){
        return stat(Kind.CHANGE_STAT,context,target,values,valueType,behavior);
    }
    public static boolean allowStatWithModifier(InteractionContext context,Ref<EntityStore> target,Int2FloatMap values,
                                                ValueType valueType,ChangeStatBehaviour behavior){
        return stat(Kind.CHANGE_STAT_WITH_MODIFIER,context,target,values,valueType,behavior);
    }
    private static boolean stat(Kind kind,InteractionContext context,Ref<EntityStore> target,
                                Int2FloatMap values,ValueType valueType,ChangeStatBehaviour behavior){
        var policy=POLICY.get();if(policy==null)return true;
        var snapshot=new HashMap<Integer,Float>();
        for(var entry:values.int2FloatEntrySet())snapshot.put(entry.getIntKey(),entry.getFloatValue());
        return policy.allow(new Mutation(kind,context.getOwningEntity(),target,null,snapshot,valueType,behavior));
    }
}
