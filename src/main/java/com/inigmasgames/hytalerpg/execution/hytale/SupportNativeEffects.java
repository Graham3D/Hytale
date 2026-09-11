package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.*;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.system.InteractionSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import com.hypixel.hytale.server.npc.systems.*;
import com.hypixel.hytale.server.npc.movement.Steering;
import com.hypixel.hytale.server.npc.movement.controllers.ProbeMoveData;
import com.hypixel.hytale.server.npc.movement.steeringforces.SteeringForceEvade;
import com.inigmasgames.hytalerpg.combat.hytale.*;
import com.inigmasgames.hytalerpg.combat.status.*;
import com.inigmasgames.hytalerpg.execution.support.*;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType;
import org.joml.Vector3d;
import java.util.*;

/** Pinned native finite-support adapters. No role replacement, teleport, animation-speed or HUD mutation. */
public final class SupportNativeEffects {
    private static final ControlProfileRegistry CONTROLS=ControlProfileRegistry.loadCanonical();
    private SupportNativeEffects(){}
    public static void requireAssets(){
        if(EntityEffect.getAssetMap().getAsset("RPG_Rally_Movement")==null)throw new IllegalStateException("RALLY_EFFECT_MISSING");
        for(String asset:List.of("RPG_Support_Hex_Tint","RPG_Support_Mark_0","RPG_Support_Mark_1","RPG_Support_Mark_2","RPG_Support_Mark_3",
                "RPG_Howl_Movement","RPG_Rally_Howl_Movement","RPG_Support_Shield_Tint","RPG_Support_Reflect_Tint","RPG_Support_Howl_Tint"))
            if(EntityEffect.getAssetMap().getAsset(asset)==null)throw new IllegalStateException("SUPPORT_PRESENTATION_ASSET_MISSING:"+asset);
        var redirect=DamageCause.getAssetMap().getAsset("RPG_Redirected");
        if(redirect==null||!redirect.doesBypassResistances())throw new IllegalStateException("REDIRECT_RESISTANCE_BYPASS_CAUSE_REQUIRED");
        for(String cause:List.of("Ice","RPG_Necrotic"))if(DamageCause.getAssetMap().getAsset(cause)==null)throw new IllegalStateException("AURA_DAMAGE_CAUSE_MISSING:"+cause);
    }
    static void requireRallyRecipient(Store<EntityStore> store,Ref<EntityStore> ref){
        if(store.getComponent(ref,EffectControllerComponent.getComponentType())==null)throw new IllegalStateException("NATIVE_MOVEMENT_EFFECT_CONTROLLER_MISSING");
    }
    static UUID world(Store<EntityStore> store){return store.getExternalData().getWorld().getWorldConfig().getUuid();}
    static ControlProfile control(Store<EntityStore> store,Ref<EntityStore> target,HytaleBossBarTracker bosses){
        var npc=store.getComponent(target,NPCEntity.getComponentType());
        var effects=store.getComponent(target,EffectControllerComponent.getComponentType());
        var network=store.getComponent(target,NetworkId.getComponentType());
        boolean protectedTarget=store.getComponent(target,Invulnerable.getComponentType())!=null||
                npc!=null&&npc.getRole()!=null&&npc.getRole().isInvulnerable()||effects!=null&&effects.isInvulnerable();
        return CONTROLS.resolve(npc==null?"":npc.getRoleName(),protectedTarget,network!=null&&bosses.isBoss(world(store),network.getId()));
    }
    static int defaultSlot(MarkedEntitySupport marked){
        if(marked==null)return -1;
        for(int i=0;i<marked.getMarkedEntitySlotCount();i++)if(MarkedEntitySupport.DEFAULT_TARGET_SLOT.equals(marked.getSlotName(i)))return i;
        return -1;
    }
    static void requireTarget(Store<EntityStore> store,Ref<EntityStore> actor,Ref<EntityStore> target,SupportProfile profile,HytaleBossBarTracker bosses){
        if(!HytaleSupportSystem.alive(store,target)||store.getComponent(target,PlayerRef.getComponentType())!=null||
                !HytaleAreaQueries.hostile(store,target,actor)||!HytaleSupportSystem.inRange(store,actor,target,profile.range()))
            throw new IllegalStateException("HOSTILE_SUPPORT_TARGET_INVALID");
        var rank=control(store,target,bosses);if(rank.protectedEntity())throw new IllegalStateException("PROTECTED_TARGET");
        var npc=store.getComponent(target,NPCEntity.getComponentType());
        if(npc==null||npc.getRole()==null)throw new IllegalStateException("NATIVE_NPC_ROLE_REQUIRED");
        if(profile.kind()==SupportProfile.Kind.TAUNT){
            if(rank.boss())throw new IllegalStateException("BOSS_TAUNT_REQUIRES_ENCOUNTER_OPT_IN");
            var marked=store.getComponent(target,MarkedEntitySupport.getComponentType());int slot=defaultSlot(marked);
            if(slot<0||marked.isRebindSlot(slot))throw new IllegalStateException("NATIVE_TARGET_SLOT_UNAVAILABLE_OR_PERSISTENT");
        }
        if(profile.kind()==SupportProfile.Kind.FEAR){
            if(rank.blocksHardControl())throw new IllegalStateException("FEAR_CONTROL_PROFILE_REJECTED");
            var motion=npc.getRole().getActiveMotionController();
            if(motion==null||!motion.is2D())throw new IllegalStateException("FEAR_GROUND_NAVIGATION_REQUIRED");
        }
    }
    private static Query<EntityStore> recipientQuery(){return Query.and(SupportEffectProjection.getComponentType(),UUIDComponent.getComponentType(),TransformComponent.getComponentType());}
    public static String markTint(UUID owner){return "RPG_Support_Mark_"+Math.floorMod(owner.hashCode(),4);}
    public static String movementAsset(double increased){
        if(increased<=0)return "";
        if(Math.abs(increased-.1)<1e-6)return "RPG_Rally_Movement";
        if(Math.abs(increased-.12)<1e-6)return "RPG_Howl_Movement";
        if(Math.abs(increased-.22)<1e-6)return "RPG_Rally_Howl_Movement";
        throw new IllegalStateException("NATIVE_SUPPORT_MOVEMENT_MAGNITUDE_UNAVAILABLE");
    }
    private static void clearMovement(EffectControllerComponent controller,Ref<EntityStore> ref,Store<EntityStore> store,String keep){
        for(String id:List.of("RPG_Rally_Movement","RPG_Howl_Movement","RPG_Rally_Howl_Movement"))
            if(!id.equals(keep))controller.removeEffect(ref,EntityEffect.getAssetMap().getIndex(id),store);
    }
    private static void presentRecipient(HytaleSupportSystem support,Store<EntityStore> store,Ref<EntityStore> ref,
                                         UUID id,SupportEffectProjection marker,double now){
        var controller=store.getComponent(ref,EffectControllerComponent.getComponentType());if(controller==null)return;
        var list=support.runtime().finite().forTarget(world(store),id,now);
        var mark=list.stream().filter(e->e.kind()==SupportProfile.Kind.MARK).sorted(Comparator.comparing(e->e.key().owner().toString()))
                .filter(e->{var owner=store.getExternalData().getRefFromUUID(e.key().owner());
                    return HytaleSupportSystem.alive(store,owner)&&HytaleSupportSystem.inRange(store,owner,ref,64);}).findFirst();
        String desired=mark.map(e->markTint(e.key().owner())).orElseGet(()->{
            if(list.stream().anyMatch(e->e.damageCap().isPresent()))return "RPG_Protection_Visual";
            if(list.stream().anyMatch(e->e.kind()==SupportProfile.Kind.WEAKEN))return "RPG_Support_Hex_Tint";
            if(list.stream().anyMatch(e->FiniteSupportEffects.isShield(e)&&e.shieldRemaining()>0)||
                    support.runtime().sharedGuards(world(store),id,now).stream().anyMatch(e->e.shieldRemaining()>0))return "RPG_Support_Shield_Tint";
            if(list.stream().anyMatch(e->e.kind()==SupportProfile.Kind.REFLECT))return "RPG_Support_Reflect_Tint";
            if(list.stream().anyMatch(e->e.kind()==SupportProfile.Kind.HOWL))return "RPG_Support_Howl_Tint";
            return "";
        });
        try{
            if(!marker.presentationAsset.isEmpty()&&!marker.presentationAsset.equals(desired))
                controller.removeEffect(ref,EntityEffect.getAssetMap().getIndex(marker.presentationAsset),store);
            marker.presentationAsset=desired;
            if(!desired.isEmpty()&&!controller.addEffect(ref,EntityEffect.getAssetMap().getAsset(desired),.2f,OverlapBehavior.OVERWRITE,store))
                throw new IllegalStateException("NATIVE_SUPPORT_TINT_REJECTED");
        }catch(RuntimeException failure){
            // Presentation has a 0.2 s lease, no damage/stat fields, and cannot cancel or refund a committed gameplay effect.
            if(!marker.presentationFailureLogged){marker.presentationFailureLogged=true;
                com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(failure).log("RPG_SUPPORT_PRESENTATION_REJECTED target=%s",id);}
        }
    }
    /** Actual native evade primitive with an explicit navigation acceptance seam, used by the production tick below. */
    public static Steering safeRetreat(org.joml.Vector3dc from,org.joml.Vector3dc source,Vector3d selector,double step,
                                       java.util.function.Predicate<Vector3d> navigable){
        if(!Double.isFinite(step)||step<=0||step>1)throw new IllegalArgumentException("Invalid bounded retreat step");
        var candidate=new Steering();var evade=new SteeringForceEvade(100,101);
        candidate.setMaxDistance(step);
        evade.setPositions(new Vector3d(from),new Vector3d(source));evade.setComponentSelector(selector);
        if(!evade.compute(candidate)||candidate.getTranslation().lengthSquared()<1e-8)return candidate.clear();
        if(!navigable.test(new Vector3d(candidate.getTranslation()).normalize(step)))return candidate.clear();
        candidate.setMaxDistance(step);return candidate;
    }
    private static void releaseTaunt(Store<EntityStore> store,Ref<EntityStore> target,SupportEffectProjection marker){
        if(marker.tauntOwner==null)return;
        var marked=store.getComponent(target,MarkedEntitySupport.getComponentType());int slot=defaultSlot(marked);
        if(slot>=0){
            var current=marked.getMarkedEntityRef(slot);
            var id=current!=null&&current.isValid()?store.getComponent(current,UUIDComponent.getComponentType()):null;
            if(id!=null&&id.getUuid().equals(marker.tauntOwner))marked.clearMarkedEntity(slot);
        }
        marker.tauntOwner=null;
    }
    /** Set the native default target immediately before role evaluation; ordinary threat evaluation resumes at expiry. */
    public static final class Projection extends EntityTickingSystem<EntityStore> {
        private final HytaleSupportSystem support;
        public Projection(HytaleSupportSystem support){this.support=support;}
        @Override public Query<EntityStore> getQuery(){return recipientQuery();}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemDependency<>(Order.BEFORE,RoleSystems.BehaviourTickSystem.class));}
        @Override public void tick(float delta,int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.SUPPORT)){
            var ref=chunk.getReferenceTo(index);var id=chunk.getComponent(index,UUIDComponent.getComponentType()).getUuid();
            var marker=chunk.getComponent(index,SupportEffectProjection.getComponentType());double now=System.nanoTime()/1e9;var world=world(store);
            var effects=support.runtime().finite();
            if(!HytaleSupportSystem.alive(store,ref))effects.forget(id);
            for(var effect:effects.forTarget(world,id,now)){
                var owner=store.getExternalData().getRefFromUUID(effect.key().owner());
                boolean invalid=!HytaleSupportSystem.alive(store,owner);
                boolean hostile=effect.context().profile().support()!=null&&effect.context().profile().support().hostileTarget();
                if(!invalid)invalid=!hostile?!HytaleSupportSystem.eligibleAlly(store,owner,ref):
                        control(store,ref,support.bosses()).protectedEntity()||!HytaleAreaQueries.hostile(store,ref,owner);
                if(invalid)effects.remove(effect.key());
            }
            var taunt=effects.control(world,id,SupportProfile.Kind.TAUNT,now);
            if(taunt.isPresent()){
                var source=store.getExternalData().getRefFromUUID(taunt.get().key().owner());
                var marked=store.getComponent(ref,MarkedEntitySupport.getComponentType());int slot=defaultSlot(marked);
                if(slot>=0&&!marked.isRebindSlot(slot)){
                    if(!marker.tauntCandidate.equals(taunt.get().skillInstanceId())){
                        var prior=marked.getMarkedEntityRef(slot);
                        marker.tauntCandidate=taunt.get().skillInstanceId();marker.tauntCredited=false;
                        marker.tauntChanged=prior==null||!prior.equals(source);
                    }
                    marked.setMarkedEntity(slot,source,false,store);marker.tauntOwner=taunt.get().key().owner();
                }else effects.remove(taunt.get().key());
            }else releaseTaunt(store,ref,marker);
            if(effects.control(world,id,SupportProfile.Kind.FEAR,now).isEmpty())support.kernel().statuses().remove(id,RpgStatusType.FEAR);
            marker.elapsed+=delta;
            if(marker.elapsed>=.1){
                marker.elapsed%=.1;
                presentRecipient(support,store,ref,id,marker,now);
                var controller=store.getComponent(ref,EffectControllerComponent.getComponentType());
                try{
                    if(effects.movementIncreased(world,id,now)>0){
                        String asset=movementAsset(effects.movementIncreased(world,id,now));
                        if(controller!=null)clearMovement(controller,ref,store,asset);
                        if(controller==null||!controller.addEffect(ref,EntityEffect.getAssetMap().getAsset(asset),.25f,OverlapBehavior.OVERWRITE,store))
                            throw new IllegalStateException("NATIVE_MOVEMENT_EFFECT_REJECTED");
                    }
                    else if(controller!=null)clearMovement(controller,ref,store,"");
                }catch(RuntimeException failure){
                    for(var e:effects.forTarget(world,id,now))if(e.movement()>0){
                        effects.remove(e.key());support.traceFinite(e,RpgTraceEventType.NATIVE_SUPPORT_REJECTED,
                                Map.of("boundary","NATIVE_MOVEMENT_EFFECT_REJECTED","exception",failure.getClass().getSimpleName()));
                    }
                }
            }
            if(effects.forTarget(world,id,now).isEmpty()&&support.runtime().sharedGuards(world,id,now).isEmpty()){
                releaseTaunt(store,ref,marker);
                var controller=store.getComponent(ref,EffectControllerComponent.getComponentType());
                try{if(controller!=null){
                    clearMovement(controller,ref,store,"");
                    if(!marker.presentationAsset.isEmpty())controller.removeEffect(ref,EntityEffect.getAssetMap().getIndex(marker.presentationAsset),store);
                }}catch(RuntimeException ignored){/* Bounded native leases expire even if removal fails during teardown. */}
                buffer.removeComponent(ref,SupportEffectProjection.getComponentType());
            }

        }
    }
    }
    /** Retreat request is generated after AI and before native avoidance, steering and interaction execution. */
    public static final class Retreat extends EntityTickingSystem<EntityStore> {
        private final HytaleSupportSystem support;
        public Retreat(HytaleSupportSystem support){this.support=support;}
        @Override public Query<EntityStore> getQuery(){return Query.and(recipientQuery(),NPCEntity.getComponentType());}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
                new SystemDependency<>(Order.AFTER,RoleSystems.BehaviourTickSystem.class),
                new SystemDependency<>(Order.BEFORE,AvoidanceSystem.class),
                new SystemDependency<>(Order.BEFORE,InteractionSystems.TickInteractionManagerSystem.class));}
        @Override public void tick(float delta,int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.SUPPORT)){
            var ref=chunk.getReferenceTo(index);var id=chunk.getComponent(index,UUIDComponent.getComponentType()).getUuid();
            double now=System.nanoTime()/1e9;
            var taunt=support.runtime().finite().control(world(store),id,SupportProfile.Kind.TAUNT,now);
            if(taunt.isPresent()){
                var e=taunt.get();var marked=store.getComponent(ref,MarkedEntitySupport.getComponentType());int slot=defaultSlot(marked);
                var selected=slot<0?null:marked.getMarkedEntityRef(slot);
                var selectedId=selected!=null&&selected.isValid()?store.getComponent(selected,UUIDComponent.getComponentType()):null;
                boolean matches=selectedId!=null&&selectedId.getUuid().equals(e.key().owner());
                var marker=chunk.getComponent(index,SupportEffectProjection.getComponentType());var observation=e.skillInstanceId()+":"+matches;
                if(matches&&marker.tauntChanged&&!marker.tauntCredited&&marker.tauntCandidate.equals(e.skillInstanceId())){
                    marker.tauntCredited=true;
                    support.controlResolved(store,ref,e.context(),true,"NATIVE_TARGET_CHANGED_AND_RETAINED_AFTER_ROLE_EVALUATION");
                }
                if(!observation.equals(marker.lastTauntObservation)){
                    marker.lastTauntObservation=observation;
                    support.traceFinite(e,RpgTraceEventType.NATIVE_SUPPORT_TARGET_OBSERVED,Map.of("afterRoleEvaluation",true,"matchesCaster",matches,
                            "selectedTarget",selectedId==null?"NONE":selectedId.getUuid().toString(),"nativeAttackExecutionProven",false));
                }
                if(!matches){support.runtime().finite().remove(e.key());
                    support.traceFinite(e,RpgTraceEventType.NATIVE_SUPPORT_REJECTED,Map.of("boundary","NATIVE_ROLE_SUPERSEDED_TAUNT_TARGET"));}
            }
            var effect=support.runtime().finite().control(world(store),id,SupportProfile.Kind.FEAR,now);
            if(effect.isEmpty())return;
            var owner=store.getExternalData().getRefFromUUID(effect.get().key().owner());
            var role=chunk.getComponent(index,NPCEntity.getComponentType()).getRole();
            if(!HytaleSupportSystem.alive(store,owner)||role==null)return;
            var motion=role.getActiveMotionController();var body=role.getBodySteering();body.clear();role.getHeadSteering().clear();
            var manager=store.getComponent(ref,InteractionModule.get().getInteractionManagerComponent());if(manager!=null)manager.clear();
            var hard=support.kernel().statuses().inspect(id).active();
            if(hard.containsKey(RpgStatusType.FROZEN)||hard.containsKey(RpgStatusType.ROOT)||motion==null||!motion.canSteer(ref,store))return;
            var from=store.getComponent(ref,TransformComponent.getComponentType()).getPosition();
            var source=store.getComponent(owner,TransformComponent.getComponentType()).getPosition();
            var marker=chunk.getComponent(index,SupportEffectProjection.getComponentType());
            var observedPosition=new com.inigmasgames.hytalerpg.execution.math.Vec3(from.x(),from.y(),from.z());
            if(marker.fearCandidate.equals(effect.get().skillInstanceId())&&!marker.fearCredited.equals(effect.get().skillInstanceId())
                    &&com.inigmasgames.hytalerpg.progress.ControlEvidence.retreatObserved(marker.fearPosition,observedPosition,
                        marker.fearSource,now-marker.fearRequestedAt,marker.fearRequested)){
                marker.fearCredited=effect.get().skillInstanceId();
                support.controlResolved(store,ref,effect.get().context(),false,"NATIVE_POSITION_RETREATED_AFTER_SAFE_STEERING_REQUEST");
            }
            // Bound the requested step and stop at unsafe edges. Native steering performs the actual move.
            double step=Math.max(.05,Math.min(1,motion.getMaximumSpeed()*Math.max(.01,Math.min(.1,delta))));
            var candidate=safeRetreat(from,source,motion.getComponentSelector(),step,displacement->{
                var probe=new ProbeMoveData();double allowed=motion.probeMove(ref,from,displacement,probe,store);
                return Double.isFinite(allowed)&&allowed>=step-1e-5&&!probe.edgeBlocked;
            });
            body.assign(candidate);
            marker.fearCandidate=effect.get().skillInstanceId();marker.fearPosition=observedPosition;
            marker.fearSource=new com.inigmasgames.hytalerpg.execution.math.Vec3(source.x(),source.y(),source.z());
            marker.fearRequestedAt=now;marker.fearRequested=candidate.getTranslation().lengthSquared()>1e-8;

        }
    }
    }
    public static final class NativeOutgoing extends DamageEventSystem {
        private final HytaleSupportSystem support;
        public NativeOutgoing(HytaleSupportSystem support){this.support=support;}
        @Override public Query<EntityStore> getQuery(){return Query.any();}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
                new SystemGroupDependency<>(Order.AFTER,DamageModule.get().getGatherDamageGroup()),
                new SystemGroupDependency<>(Order.BEFORE,DamageModule.get().getFilterDamageGroup()));}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.SUPPORT)){
            // RPG skill hits already use the combined additive bucket at calculation. Never apply it twice.
            if(damage.isCancelled()||damage.getAmount()<=0||HytaleDamageAdapter.metadata(damage)!=null||!(damage.getSource() instanceof Damage.EntitySource source))return;
            if(!source.getRef().isValid()||source.getRef().equals(chunk.getReferenceTo(index)))return;
            var id=store.getComponent(source.getRef(),UUIDComponent.getComponentType());if(id==null)return;
            damage.setAmount((float)(damage.getAmount()*support.runtime().finite().nativeOutgoingFactor(world(store),id.getUuid(),System.nanoTime()/1e9)));

        }
    }
    }
    public static final class DirectDamageBreak extends DamageEventSystem {
        private final HytaleSupportSystem support;
        public DirectDamageBreak(HytaleSupportSystem support){this.support=support;}
        @Override public Query<EntityStore> getQuery(){return Query.and(UUIDComponent.getComponentType(),SupportEffectProjection.getComponentType());}
        @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getInspectDamageGroup();}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.SUPPORT)){
            var meta=HytaleDamageAdapter.metadata(damage);
            // The pinned DamageEntityInteraction supplies INTERACTION_TYPE; a bare source is not proof of direct damage.
            boolean direct=meta!=null?meta.origin()==HytaleDamageMetadata.Origin.DIRECT:
                    damage.getIfPresentMetaObject(Damage.INTERACTION_TYPE)!=null||damage.getSource() instanceof Damage.ProjectileSource;
            var id=chunk.getComponent(index,UUIDComponent.getComponentType()).getUuid();
            if(support.runtime().finite().directDamage(world(store),id,System.nanoTime()/1e9,direct,!damage.isCancelled()&&damage.getAmount()>0))
                support.kernel().statuses().remove(id,RpgStatusType.FEAR);

        }
    }
    }
    public static final class Removal extends RefSystem<EntityStore> {
        private final HytaleSupportSystem support;
        public Removal(HytaleSupportSystem support){this.support=support;}
        @Override public Query<EntityStore> getQuery(){return UUIDComponent.getComponentType();}
        @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){}
        @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.SUPPORT)){
            var marker=store.getComponent(ref,SupportEffectProjection.getComponentType());if(marker!=null)releaseTaunt(store,ref,marker);
            var id=store.getComponent(ref,UUIDComponent.getComponentType());support.runtime().finite().forget(id.getUuid());
            support.runtime().imbues().forget(id.getUuid());

        }
    }
    }
}
