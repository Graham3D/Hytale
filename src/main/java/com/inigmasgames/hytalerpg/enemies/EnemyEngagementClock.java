package com.inigmasgames.hytalerpg.enemies;

import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** Saved ME-019 engaged-time phase. Native target admission and provider projection remain with the world owner. */
public final class EnemyEngagementClock implements Component<EntityStore> {
    public static final long CYCLE_MILLIS=12_000;
    public record State(UUID world,UUID logicalActor,UUID nativeEntity,long generation,long phaseMillis,double fractionalMillis){
        public State{
            Objects.requireNonNull(world);Objects.requireNonNull(logicalActor);Objects.requireNonNull(nativeEntity);
            if(generation<0||phaseMillis<0||phaseMillis>=CYCLE_MILLIS||!Double.isFinite(fractionalMillis)
                    ||fractionalMillis<0||fractionalMillis>=1)throw new IllegalArgumentException("ENEMY_ENGAGEMENT_CLOCK_STATE");
        }
    }
    private static ComponentType<EntityStore,EnemyEngagementClock> type;
    private UUID world,logicalActor,nativeEntity;private long generation,phaseMillis;private double fractionalMillis;
    public static final BuilderCodec<EnemyEngagementClock> CODEC=BuilderCodec.builder(EnemyEngagementClock.class,EnemyEngagementClock::new)
            .append(new KeyedCodec<>("World",Codec.UUID_BINARY),(p,v)->p.world=v,p->p.world).add()
            .append(new KeyedCodec<>("LogicalActor",Codec.UUID_BINARY),(p,v)->p.logicalActor=v,p->p.logicalActor).add()
            .append(new KeyedCodec<>("NativeEntity",Codec.UUID_BINARY),(p,v)->p.nativeEntity=v,p->p.nativeEntity).add()
            .append(new KeyedCodec<>("Generation",Codec.LONG),(p,v)->p.generation=v,p->p.generation).add()
            .append(new KeyedCodec<>("PhaseMillis",Codec.LONG),(p,v)->p.phaseMillis=v,p->p.phaseMillis).add()
            .append(new KeyedCodec<>("FractionalMillis",Codec.DOUBLE),(p,v)->p.fractionalMillis=v,p->p.fractionalMillis).add()
            .afterDecode(EnemyEngagementClock::validateDecoded).build();
    public EnemyEngagementClock(){}
    public EnemyEngagementClock(State state){set(state);}
    private void validateDecoded(){
        if(world==null&&logicalActor==null&&nativeEntity==null&&generation==0
                &&phaseMillis==0&&fractionalMillis==0)return;
        state();
    }
    public static void bind(ComponentType<EntityStore,EnemyEngagementClock> value){type=Objects.requireNonNull(value);}
    public static ComponentType<EntityStore,EnemyEngagementClock> getComponentType(){return type;}
    public State state(){return new State(world,logicalActor,nativeEntity,generation,phaseMillis,fractionalMillis);}
    private void set(State state){world=state.world();logicalActor=state.logicalActor();nativeEntity=state.nativeEntity();
        generation=state.generation();phaseMillis=state.phaseMillis();fractionalMillis=state.fractionalMillis();}
    /** Pauses on disengage/unload/suspension. Fractional time prevents 60 Hz truncation drift. */
    public boolean advance(float deltaSeconds,boolean engaged,boolean suspended,long activeWindowStartMillis){
        if(!Float.isFinite(deltaSeconds)||deltaSeconds<0)throw new IllegalArgumentException("ENEMY_ENGAGEMENT_DELTA");
        if(activeWindowStartMillis<=0||activeWindowStartMillis>=CYCLE_MILLIS)
            throw new IllegalArgumentException("ENEMY_ENGAGEMENT_WINDOW");
        if(!engaged||suspended||deltaSeconds==0)return false;
        double elapsed=deltaSeconds*1000d+fractionalMillis;
        if(!Double.isFinite(elapsed)||elapsed>Long.MAX_VALUE)throw new IllegalArgumentException("ENEMY_ENGAGEMENT_DELTA_OVERFLOW");
        long whole=(long)Math.floor(elapsed);
        fractionalMillis=elapsed-whole;
        if(fractionalMillis>=1){whole++;fractionalMillis-=1;}
        long before=phaseMillis;
        phaseMillis=(phaseMillis+whole%CYCLE_MILLIS)%CYCLE_MILLIS;
        return (before>=activeWindowStartMillis)!=(phaseMillis>=activeWindowStartMillis);
    }
    public void requireActor(EnemyDescriptor descriptor){
        if(!world.equals(descriptor.worldId())||!logicalActor.equals(descriptor.logicalActorId())
                ||!nativeEntity.equals(descriptor.entityId())||generation!=descriptor.encounterGeneration()
                ||descriptor.own(EnemyAffixRegistry.Operator.FRENZIED).isEmpty())
            throw new IllegalStateException("ENEMY_ENGAGEMENT_ACTOR_MISMATCH");
    }
    @Override public EnemyEngagementClock clone(){return new EnemyEngagementClock(state());}
}
