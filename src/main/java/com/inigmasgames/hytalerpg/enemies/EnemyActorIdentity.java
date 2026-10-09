package com.inigmasgames.hytalerpg.enemies;

import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** Saved native-actor pointer to a durable birth. Carries no reward or affix decision. */
public final class EnemyActorIdentity implements Component<EntityStore> {
    public record State(UUID world,UUID encounter,UUID pack,UUID logicalActor,UUID nativeEntity,
            long generation,String nativeRole){
        public State{
            Objects.requireNonNull(world);Objects.requireNonNull(encounter);Objects.requireNonNull(pack);
            Objects.requireNonNull(logicalActor);Objects.requireNonNull(nativeEntity);
            if(generation<0||nativeRole==null||nativeRole.isBlank()||nativeRole.length()>128)
                throw new IllegalArgumentException("ENEMY_ACTOR_IDENTITY_STATE");
        }
        public static State of(EnemyDescriptor actor){
            if(actor.packId()==null)throw new IllegalArgumentException("ENEMY_ACTOR_IDENTITY_REQUIRES_PACK");
            return new State(actor.worldId(),actor.encounterId(),actor.packId(),actor.logicalActorId(),
                    actor.entityId(),actor.encounterGeneration(),actor.nativeRoleId());
        }
        public void require(EnemyDescriptor actor){
            if(!equals(of(actor)))throw new IllegalStateException("ENEMY_ACTOR_IDENTITY_DESCRIPTOR_MISMATCH");
        }
    }
    private static ComponentType<EntityStore,EnemyActorIdentity> type;
    private UUID world,encounter,pack,logicalActor,nativeEntity;private long generation;private String nativeRole;
    // Captured once before rarity presentation changes Model.scale. Saved with the actor so rebind cannot compound it.
    private double nativeVisualScale;
    public static final BuilderCodec<EnemyActorIdentity> CODEC=BuilderCodec.builder(EnemyActorIdentity.class,EnemyActorIdentity::new)
            .append(new KeyedCodec<>("World",Codec.UUID_BINARY),(p,v)->p.world=v,p->p.world).add()
            .append(new KeyedCodec<>("Encounter",Codec.UUID_BINARY),(p,v)->p.encounter=v,p->p.encounter).add()
            .append(new KeyedCodec<>("Pack",Codec.UUID_BINARY),(p,v)->p.pack=v,p->p.pack).add()
            .append(new KeyedCodec<>("LogicalActor",Codec.UUID_BINARY),(p,v)->p.logicalActor=v,p->p.logicalActor).add()
            .append(new KeyedCodec<>("NativeEntity",Codec.UUID_BINARY),(p,v)->p.nativeEntity=v,p->p.nativeEntity).add()
            .append(new KeyedCodec<>("Generation",Codec.LONG),(p,v)->p.generation=v,p->p.generation).add()
            .append(new KeyedCodec<>("NativeRole",Codec.STRING),(p,v)->p.nativeRole=v,p->p.nativeRole).add()
            .append(new KeyedCodec<>("NativeVisualScale",Codec.DOUBLE),(p,v)->p.nativeVisualScale=v,p->p.nativeVisualScale).add()
            .afterDecode(EnemyActorIdentity::validateDecoded).build();
    public EnemyActorIdentity(){}
    public EnemyActorIdentity(State state){world=state.world();encounter=state.encounter();pack=state.pack();
        logicalActor=state.logicalActor();nativeEntity=state.nativeEntity();generation=state.generation();nativeRole=state.nativeRole();}
    private void validateDecoded(){
        if(world==null&&encounter==null&&pack==null&&logicalActor==null&&nativeEntity==null
                &&generation==0&&nativeRole==null)return;
        state();
        if(!Double.isFinite(nativeVisualScale)||nativeVisualScale<0)
            throw new IllegalArgumentException("ENEMY_NATIVE_VISUAL_SCALE");
    }
    public State state(){return new State(world,encounter,pack,logicalActor,nativeEntity,generation,nativeRole);}
    public double nativeVisualScale(){return nativeVisualScale;}
    public void captureNativeVisualScale(float scale){
        if(!Float.isFinite(scale)||scale<=0)throw new IllegalArgumentException("ENEMY_NATIVE_VISUAL_SCALE");
        if(nativeVisualScale==0)nativeVisualScale=scale;
    }
    public static void bind(ComponentType<EntityStore,EnemyActorIdentity> value){type=Objects.requireNonNull(value);}
    public static ComponentType<EntityStore,EnemyActorIdentity> getComponentType(){return type;}
    @Override public EnemyActorIdentity clone(){
        var copy=new EnemyActorIdentity(state());copy.nativeVisualScale=nativeVisualScale;return copy;
    }
}
