package com.inigmasgames.hytalerpg.enemies;

import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects;
import java.util.*;

/** Saved in the native entity's same snapshot as Health. This projection cannot create an encounter. */
public final class EnemyShieldProjection implements Component<EntityStore> {
    private static ComponentType<EntityStore,EnemyShieldProjection> type;
    private UUID world,target;private long generation;private double capacity,consumed;
    public static final BuilderCodec<EnemyShieldProjection> CODEC=BuilderCodec.builder(EnemyShieldProjection.class,EnemyShieldProjection::new)
            .append(new KeyedCodec<>("World",Codec.UUID_BINARY),(p,v)->p.world=v,p->p.world).add()
            .append(new KeyedCodec<>("Target",Codec.UUID_BINARY),(p,v)->p.target=v,p->p.target).add()
            .append(new KeyedCodec<>("Generation",Codec.LONG),(p,v)->p.generation=v,p->p.generation).add()
            .append(new KeyedCodec<>("Capacity",Codec.DOUBLE),(p,v)->p.capacity=v,p->p.capacity).add()
            .append(new KeyedCodec<>("Consumed",Codec.DOUBLE),(p,v)->p.consumed=v,p->p.consumed).add()
            .afterDecode(EnemyShieldProjection::validateDecoded).build();
    public EnemyShieldProjection(){}
    public EnemyShieldProjection(FiniteSupportEffects.IntrinsicShield state){
        world=state.key().world();target=state.key().target();generation=state.key().generation();capacity=state.capacity();consumed=state.consumed();
    }
    private void validateDecoded(){
        // ComponentRegistry runs afterDecode on the empty codec default during registration.
        // Real saved projections are still validated, including partially populated ones.
        if(world==null&&target==null&&generation==0&&capacity==0&&consumed==0)return;
        snapshot();
    }
    public static void bind(ComponentType<EntityStore,EnemyShieldProjection> value){type=Objects.requireNonNull(value);}
    public static ComponentType<EntityStore,EnemyShieldProjection> getComponentType(){return type;}
    public FiniteSupportEffects.IntrinsicShield snapshot(){return new FiniteSupportEffects.IntrinsicShield(
            new FiniteSupportEffects.IntrinsicShieldKey(world,target,generation,"ME-027"),capacity,consumed);}
    public void consumed(FiniteSupportEffects.IntrinsicShield after){
        var previous=snapshot();
        if(!previous.key().equals(after.key())||capacity!=after.capacity()||after.consumed()<consumed)
            throw new IllegalStateException("BULWARK_PROJECTION_ROLLBACK_OR_IDENTITY_MISMATCH");
        consumed=after.consumed();
    }
    /** A validated durable descriptor supplies identity/capacity; saved component data supplies only consumption. */
    public void restore(FiniteSupportEffects owner,FiniteSupportEffects.IntrinsicShieldKey expected,double expectedCapacity){
        var saved=snapshot();
        if(!saved.key().equals(expected)||saved.capacity()!=expectedCapacity)throw new IllegalStateException("BULWARK_SAVED_DESCRIPTOR_MISMATCH");
        consumed(owner.restoreIntrinsicShield(saved));
    }
    @Override public EnemyShieldProjection clone(){return new EnemyShieldProjection(snapshot());}
}
