package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.enemies.*;
import com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects;
import java.util.*;

/** Binds existing saved clock and intrinsic-shield owners to one exact staged birth roster. */
public final class NativeEnemyStateAttachment {
    private record Candidate(EnemyDescriptor actor,Ref<EntityStore> ref,
            EnemyEngagementClock clock,EnemyShieldProjection shield,
            FiniteSupportEffects.IntrinsicShieldKey shieldKey,double shieldCapacity){}
    private final FiniteSupportEffects effects;
    public NativeEnemyStateAttachment(FiniteSupportEffects effects){this.effects=Objects.requireNonNull(effects);}

    /** Fresh births may initialize saved state; rebinds must consume the native snapshot without refilling it. */
    public Prepared prepare(Store<EntityStore> store,EnemyBirthPlan birth,boolean fresh){
        return prepare(store,birth,birth.actors(),fresh);
    }
    public Prepared prepare(Store<EntityStore> store,EnemyBirthPlan birth,List<EnemyDescriptor> active,boolean fresh){
        if(!store.isInThread()||!store.getExternalData().getWorld().getWorldConfig().getUuid().equals(birth.world())
                ||birth.pack()==null)throw new IllegalStateException("ENEMY_NATIVE_STATE_WORLD_OR_BIRTH");
        if(active.isEmpty()||active.size()>birth.actors().size()||new HashSet<>(active).size()!=active.size()
                ||!birth.actors().containsAll(active))throw new IllegalArgumentException("ENEMY_NATIVE_STATE_ACTIVE_ROSTER");
        var candidates=new ArrayList<Candidate>();
        for(var actor:active){
            var ref=store.getExternalData().getRefFromUUID(actor.entityId());
            var staged=ref==null||!ref.isValid()?null:store.getComponent(ref,EnemyStaging.getComponentType());
            var savedClock=ref==null||!ref.isValid()?null:store.getComponent(ref,EnemyEngagementClock.getComponentType());
            var savedShield=ref==null||!ref.isValid()?null:store.getComponent(ref,EnemyShieldProjection.getComponentType());
            if(staged==null||!staged.state().world().equals(birth.world())
                    ||!staged.state().encounter().equals(birth.encounter())
                    ||staged.state().generation()!=birth.generation()
                    ||!staged.state().entity().equals(actor.entityId()))
                throw new IllegalStateException("ENEMY_NATIVE_STATE_STAGING_MISMATCH");
            boolean frenzied=actor.own(EnemyAffixRegistry.Operator.FRENZIED).isPresent();
            if(frenzied){
                if(savedClock!=null&&fresh)throw new IllegalStateException("ENEMY_FRENZIED_FRESH_CLOCK_CONFLICT");
                if(savedClock==null&&!fresh)throw new IllegalStateException("ENEMY_FRENZIED_SAVED_CLOCK_MISSING");
                if(savedClock!=null)savedClock.requireActor(actor);
            }else if(savedClock!=null)throw new IllegalStateException("ENEMY_FRENZIED_FOREIGN_CLOCK");
            var bulwark=actor.own(EnemyAffixRegistry.Operator.BULWARK);
            FiniteSupportEffects.IntrinsicShieldKey key=null;double capacity=0;
            if(bulwark.isPresent()){
                var stats=store.getComponent(ref,EntityStatMap.getComponentType());
                var health=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
                if(health==null||!Float.isFinite(health.getMax())||health.getMax()<=0)
                    throw new IllegalStateException("ENEMY_BULWARK_HEALTH_NOT_PROJECTED");
                capacity=health.getMax()*bulwark.get().value("initialShieldMaxHealthFraction");
                key=new FiniteSupportEffects.IntrinsicShieldKey(actor.worldId(),actor.entityId(),actor.encounterGeneration(),"ME-027");
                if(!Double.isFinite(capacity)||capacity<=0||capacity>Float.MAX_VALUE)
                    throw new IllegalStateException("ENEMY_BULWARK_CAPACITY_INVALID");
                if(savedShield!=null&&fresh)throw new IllegalStateException("ENEMY_BULWARK_FRESH_SHIELD_CONFLICT");
                if(savedShield==null&&!fresh)throw new IllegalStateException("ENEMY_BULWARK_SAVED_SHIELD_MISSING");
                if(savedShield!=null){var saved=savedShield.snapshot();
                    if(!saved.key().equals(key)||saved.capacity()!=capacity)
                        throw new IllegalStateException("ENEMY_BULWARK_SAVED_DESCRIPTOR_MISMATCH");
                }
            }else if(savedShield!=null)throw new IllegalStateException("ENEMY_BULWARK_FOREIGN_SHIELD");
            candidates.add(new Candidate(actor,ref,savedClock,savedShield,key,capacity));
        }
        return new Prepared(store,candidates,fresh);
    }

    public final class Prepared implements AutoCloseable {
        private final Store<EntityStore> store;private final List<Candidate> candidates;private final boolean fresh;
        private final List<Candidate> bound=new ArrayList<>();private final Set<UUID> addedClock=new HashSet<>(),addedShield=new HashSet<>();
        private final Set<FiniteSupportEffects.IntrinsicShieldKey> boundShields=new HashSet<>();
        private boolean attached,closed,published;
        private Prepared(Store<EntityStore> store,List<Candidate> candidates,boolean fresh){
            this.store=store;this.candidates=List.copyOf(candidates);this.fresh=fresh;
        }
        public void attach(){
            if(closed||attached||!store.isInThread())throw new IllegalStateException("ENEMY_NATIVE_STATE_ATTACH_PHASE");
            try{
                for(var candidate:candidates){
                    var actor=candidate.actor();var ref=candidate.ref();
                    if(!ref.isValid()||store.getComponent(ref,EnemyStaging.getComponentType())==null)
                        throw new IllegalStateException("ENEMY_NATIVE_STATE_ACTOR_CHANGED");
                    bound.add(candidate);
                    if(candidate.clock()==null&&actor.own(EnemyAffixRegistry.Operator.FRENZIED).isPresent()){
                        if(!fresh)throw new IllegalStateException("ENEMY_FRENZIED_REBIND_REFILL");
                        store.addComponent(ref,EnemyEngagementClock.getComponentType(),
                                new EnemyEngagementClock(new EnemyEngagementClock.State(actor.worldId(),actor.logicalActorId(),
                                        actor.entityId(),actor.encounterGeneration(),0,0)));
                        addedClock.add(actor.entityId());
                    }
                    if(candidate.shieldKey()!=null){
                        var saved=candidate.shield()==null
                                ?new FiniteSupportEffects.IntrinsicShield(candidate.shieldKey(),candidate.shieldCapacity(),0)
                                :candidate.shield().snapshot();
                        var boundShield=effects.restoreIntrinsicShield(saved);
                        boundShields.add(candidate.shieldKey());
                        if(candidate.shield()==null){
                            if(!fresh)throw new IllegalStateException("ENEMY_BULWARK_REBIND_REFILL");
                            store.addComponent(ref,EnemyShieldProjection.getComponentType(),new EnemyShieldProjection(boundShield));
                            addedShield.add(actor.entityId());
                        }else candidate.shield().consumed(boundShield);
                    }
                }
                attached=true;
            }catch(RuntimeException failure){try{close();}catch(Exception cleanup){failure.addSuppressed(cleanup);}throw failure;}
        }
        /** Mark saved components durable after pack publication, before releasing native staged flags. */
        public void published(){
            if(!store.isInThread()||closed||!attached||published)
                throw new IllegalStateException("ENEMY_NATIVE_STATE_PUBLISH_PHASE");
            published=true;
        }
        /** Release transient shield ownership on native removal; saved state remains with unloaded actors. */
        public void detachActor(UUID nativeEntity){
            if(!store.isInThread()||closed||!published)throw new IllegalStateException("ENEMY_NATIVE_STATE_ACTOR_DETACH_PHASE");
            for(int i=0;i<bound.size();i++)if(bound.get(i).actor().entityId().equals(nativeEntity)){
                var candidate=bound.remove(i);
                if(candidate.shieldKey()!=null&&boundShields.remove(candidate.shieldKey()))
                    effects.unbindIntrinsicShield(candidate.shieldKey());
                return;
            }
            throw new IllegalArgumentException("ENEMY_NATIVE_STATE_ACTOR_NOT_IN_PACK");
        }
        /** A failed prepublication attachment removes only components this birth created. */
        @Override public void close(){
            if(closed)return;
            if(!store.isInThread())throw new IllegalStateException("ENEMY_NATIVE_STATE_CLOSE_THREAD");
            closed=true;
            for(int index=bound.size()-1;index>=0;index--){
                var candidate=bound.get(index);var actor=candidate.actor();var ref=candidate.ref();
                if(boundShields.contains(candidate.shieldKey()))effects.unbindIntrinsicShield(candidate.shieldKey());
                if(!published&&ref.isValid()){
                    if(addedShield.contains(actor.entityId()))store.removeComponent(ref,EnemyShieldProjection.getComponentType());
                    if(addedClock.contains(actor.entityId()))store.removeComponent(ref,EnemyEngagementClock.getComponentType());
                }
            }
            bound.clear();
        }
    }
}
