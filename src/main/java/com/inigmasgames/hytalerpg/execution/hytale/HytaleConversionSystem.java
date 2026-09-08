package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.blackboard.Blackboard;
import com.hypixel.hytale.server.npc.blackboard.view.attitude.AttitudeView;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.*;
import com.hypixel.hytale.server.npc.systems.*;
import com.inigmasgames.hytalerpg.combat.diagnostics.CombatTrace;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.area.AreaGeometry;
import com.inigmasgames.hytalerpg.execution.summon.ConversionRegistry;
import java.util.*;

/** Reversible native relationship overlay; no Role, allegiance-memory, inventory, Health or reward creation writes. */
public final class HytaleConversionSystem extends EntityTickingSystem<EntityStore>{
    private final ConversionRegistry registry=new ConversionRegistry();
    private final Map<AttitudeView,Boolean> installed=Collections.synchronizedMap(new WeakHashMap<>());
    private final CombatTrace trace;
    private final HytaleBossBarTracker bosses;
    private final com.inigmasgames.hytalerpg.vfx.LinkTreeVfxService vfx;
    private java.util.function.BiConsumer<UUID,UUID> rewardExclusion=(world,enemy)->{};
    public void configureRewardExclusion(java.util.function.BiConsumer<UUID,UUID> exclusion){rewardExclusion=Objects.requireNonNull(exclusion);}
    public HytaleConversionSystem(CombatTrace trace,HytaleBossBarTracker bosses,com.inigmasgames.hytalerpg.vfx.LinkTreeVfxService vfx){this.trace=trace;this.bosses=bosses;this.vfx=vfx;}
    public void cancel(UUID owner){for(var lease:registry.owned(owner))registry.end(lease.token());}
    private static UUID world(ComponentAccessor<EntityStore> store){return store.getExternalData().getWorld().getWorldConfig().getUuid();}
    private static UUID id(ComponentAccessor<EntityStore> store,Ref<EntityStore> ref){var c=ref==null||!ref.isValid()?null:store.getComponent(ref,UUIDComponent.getComponentType());return c==null?null:c.getUuid();}
    private static Vec3 point(Store<EntityStore> store,Ref<EntityStore> ref){var p=store.getComponent(ref,TransformComponent.getComponentType()).getPosition();return new Vec3(p.x(),p.y(),p.z());}
    private static boolean persistentThreat(MarkedEntitySupport marked){
        if(marked==null)return true;
        for(int i=0;i<marked.getMarkedEntitySlotCount();i++)if(marked.isRebindSlot(i))return true;
        return false;
    }
    private static boolean flocked(ComponentAccessor<EntityStore> store,Ref<EntityStore> ref){
        var membership=store.getComponent(ref,com.hypixel.hytale.server.flock.FlockMembership.getComponentType());
        return membership!=null&&(membership.getFlockId()!=null||membership.getFlockRef()!=null);
    }
    private ConversionRegistry.Eligibility eligibility(Store<EntityStore> store,Ref<EntityStore> target,Ref<EntityStore> owner){
        if(target==null||!target.isValid())return new ConversionRegistry.Eligibility(false,ConversionRegistry.Rank.UNKNOWN,false,false,false,true,false);
        var npc=store.getComponent(target,NPCEntity.getComponentType());var network=store.getComponent(target,NetworkId.getComponentType());
        var marked=store.getComponent(target,MarkedEntitySupport.getComponentType());boolean persistent=persistentThreat(marked);
        // Explicit authored Dominatable common-role opt-in. Unknown roles do NOT inherit COMMON.
        boolean classified=npc!=null&&"Wolf_Black".equals(npc.getRoleName());
        boolean protectedActor=npc==null||npc.getRole()==null||npc.isReserved()||npc.getRole().isInvulnerable()
                ||store.getComponent(target,Invulnerable.getComponentType())!=null||store.getComponent(target,SummonProjection.getComponentType())!=null
                ||store.getComponent(target,ConversionProjection.getComponentType())!=null
                ||store.getComponent(target,EntityStore.REGISTRY.getNonSerializedComponentType())!=null
                ||network!=null&&bosses.isBoss(world(store),network.getId())||marked==null||marked.getMarkedEntitySlotCount()>64
                ||flocked(store,target)||!HytaleSummonSystem.alive(store,target);
        return new ConversionRegistry.Eligibility(classified,classified?ConversionRegistry.Rank.COMMON:ConversionRegistry.Rank.UNKNOWN,false,
                store.getComponent(target,PlayerRef.getComponentType())!=null,!HytaleAreaQueries.hostile(store,target,owner),protectedActor,persistent);
    }
    private Optional<Ref<EntityStore>> select(Store<EntityStore> store,Ref<EntityStore> owner,Stage04SkillProfile profile,Vec3 aim){
        var origin=point(store,owner).add(new Vec3(0,1.35,0));var direction=aim.normalized();double range=profile.conversion().range();
        var result=HytaleAreaQueries.query(store,owner,new AreaGeometry(AreaGeometry.Kind.DISC,point(store,owner).add(new Vec3(0,-1,0)),direction,range,0,0,0,3),64);
        if(result.overflow())throw new IllegalStateException("CONVERSION_TARGET_QUERY_CAP");
        return result.candidates().stream().filter(c->eligibility(store,c.ref(),owner).boundary().equals("PASS"))
                .filter(c->{var delta=c.bounds().centre().subtract(origin);double along=delta.x()*direction.x()+delta.y()*direction.y()+delta.z()*direction.z();
                    return delta.length()<=range&&along>=0&&delta.subtract(direction.multiply(along)).length()<=1.25&&HytaleAreaQueries.clear(store,origin,c.bounds().centre());})
                .map(HytaleAreaQueries.Candidate::ref).min(Comparator.<Ref<EntityStore>>comparingDouble(r->point(store,r).subtract(origin).length()).thenComparing(r->id(store,r)));
    }
    public SkillExecutionPort.Validation preflight(Store<EntityStore> store,Ref<EntityStore> owner,Stage04SkillProfile profile,Vec3 aim){
        registry.expire(System.nanoTime()/1e9);
        var target=select(store,owner,profile,aim).orElse(null);if(target==null)return SkillExecutionPort.Validation.reject("NO_ELIGIBLE_DOMINATABLE_NATIVE_TARGET");
        String code=registry.admission(id(store,owner),world(store),id(store,target));
        return code.equals("PASS")?SkillExecutionPort.Validation.pass():SkillExecutionPort.Validation.reject(code);
    }
    public CommittedTarget capture(Store<EntityStore> store,Ref<EntityStore> owner,Stage04SkillProfile profile,Vec3 aim){
        var target=select(store,owner,profile,aim).orElseThrow();return new CommittedTarget(world(store),point(store,owner),point(store,target),aim,id(store,target));
    }
    public SkillExecutionPort.Validation validate(Store<EntityStore> store,Ref<EntityStore> owner,SkillExecutionContext context){
        if(context.derivedRelease()||context.target()==null||!world(store).equals(context.target().worldId()))return SkillExecutionPort.Validation.reject("CONVERSION_REPLAY_OR_WORLD_CHANGED");
        var target=store.getExternalData().getRefFromUUID(context.target().entityId());String code=eligibility(store,target,owner).boundary();
        if(!code.equals("PASS"))return SkillExecutionPort.Validation.reject(code);
        if(point(store,target).subtract(point(store,owner)).length()>context.profile().conversion().range()||
                !HytaleAreaQueries.clear(store,point(store,owner).add(new Vec3(0,1.35,0)),point(store,target).add(new Vec3(0,.5,0))))
            return SkillExecutionPort.Validation.reject("CONVERSION_RANGE_OR_LOS");
        code=registry.admission(id(store,owner),world(store),id(store,target));return code.equals("PASS")?SkillExecutionPort.Validation.pass():SkillExecutionPort.Validation.reject(code);
    }
    public SkillExecutionResult execute(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> owner,SkillExecutionContext context){
        if(buffer==null)throw new IllegalStateException("CONVERSION_WORLD_BUFFER_REQUIRED");
        var verdict=validate(store,owner,context);if(!verdict.accepted())throw new IllegalStateException(verdict.code());
        var target=store.getExternalData().getRefFromUUID(context.target().entityId());var marked=store.getComponent(target,MarkedEntitySupport.getComponentType());
        var lease=registry.begin(context,eligibility(store,target,owner),id(store,marked.getMarkedEntityRef(MarkedEntitySupport.DEFAULT_TARGET_SLOT)),System.nanoTime()/1e9);
        try{rewardExclusion.accept(world(store),id(store,target));install(store,owner);buffer.addComponent(target,ConversionProjection.getComponentType(),new ConversionProjection(lease));
            // The non-persistent default target can be restored; persistent marked targets reject at admission.
            marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT,null);
        }catch(RuntimeException failure){registry.end(lease.token());throw failure;}
        emit(lease,RpgTraceEventType.CONVERSION_STARTED,Map.of("entity",lease.entity(),"role","Wolf_Black","nativeAiObserved",false,"rewardCreated",false));
        return SkillExecutionResult.committed("NATIVE_CONVERSION_LEASE_STARTED",0,0);
    }
    private ConversionRegistry.Lease active(ComponentAccessor<EntityStore> accessor,Ref<EntityStore> ref){
        UUID identity=id(accessor,ref);if(identity==null)return null;
        var lease=registry.target(world(accessor),identity).orElse(null);
        if(lease==null||System.nanoTime()/1e9>=lease.expires())return null;
        var owner=accessor.getExternalData().getRefFromUUID(lease.owner());
        return owner!=null&&owner.isValid()&&accessor.getComponent(owner,DeathComponent.getComponentType())==null?lease:null;
    }
    private void install(Store<EntityStore> store,Ref<EntityStore> owner){
        var view=store.getResource(Blackboard.getResourceType()).getView(AttitudeView.class,owner,store);
        synchronized(installed){if(installed.containsKey(view))return;
            view.registerProvider(-10,(source,role,target,accessor)->{
                var a=active(accessor,source);var b=active(accessor,target);if(a==null&&b==null)return null;
                if(a!=null&&b!=null)return Attitude.FRIENDLY; // No indirect PvP via converted actors.
                if(a!=null&&accessor.getComponent(target,PlayerRef.getComponentType())!=null)return Attitude.FRIENDLY;
                var converted=a!=null?a:b;var other=a!=null?target:source;
                var player=accessor.getExternalData().getRefFromUUID(converted.owner());
                var nativeSupport=accessor.getComponent(other,WorldSupport.getComponentType());
                return nativeSupport==null?Attitude.IGNORE:nativeSupport.getAttitude(other,player,accessor);
            });installed.put(view,true);
        }
    }
    @Override public Query<EntityStore> getQuery(){return Query.and(ConversionProjection.getComponentType(),NPCEntity.getComponentType(),MarkedEntitySupport.getComponentType(),TransformComponent.getComponentType());}
    @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemDependency<>(Order.BEFORE,RoleSystems.BehaviourTickSystem.class));}
    @Override public void tick(float dt,int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        var ref=chunk.getReferenceTo(index);var marker=chunk.getComponent(index,ConversionProjection.getComponentType());var lease=marker.lease;
        if(lease==null){buffer.tryRemoveComponent(ref,ConversionProjection.getComponentType());return;}
        if(marker.restored){if(HytaleSummonSystem.alive(store,ref))buffer.tryRemoveComponent(ref,ConversionProjection.getComponentType());return;}
        var owner=store.getExternalData().getRefFromUUID(lease.owner());double now=System.nanoTime()/1e9;
        if(!HytaleSummonSystem.alive(store,ref)){end(store,buffer,ref,marker,"NATIVE_DEATH",true);return;}
        if(registry.find(lease.token()).isEmpty()||!HytaleSummonSystem.alive(store,owner)||now>=lease.expires()){
            end(store,buffer,ref,marker,now>=lease.expires()?"EXPIRED":"OWNER_GONE_OR_CANCELLED",false);return;}
        var npc=store.getComponent(ref,NPCEntity.getComponentType());
        var network=store.getComponent(ref,NetworkId.getComponentType());
        if(!"Wolf_Black".equals(npc.getRoleName())||npc.getRole()==null||npc.isReserved()||npc.getRole().isInvulnerable()||flocked(store,ref)
                ||persistentThreat(store.getComponent(ref,MarkedEntitySupport.getComponentType()))
                ||store.getComponent(ref,Invulnerable.getComponentType())!=null||network!=null&&bosses.isBoss(world(store),network.getId())){
            end(store,buffer,ref,marker,"NATIVE_OWNERSHIP_CHANGED",false);return;}
        if(now<marker.nextQuery)return;marker.nextQuery=now+.2;
        try{vfx.presentConversion(store.getExternalData().getWorld(),point(store,ref),lease.expires()-now<1,now);}
        catch(RuntimeException ignored){/* Optional debug-template presentation never changes control lifetime. */}
        var shape=new AreaGeometry(AreaGeometry.Kind.DISC,point(store,ref).add(new Vec3(0,-1,0)),Vec3.FORWARD,15,0,0,0,3);
        var result=HytaleAreaQueries.query(store,owner,shape,64);
        if(result.overflow()){end(store,buffer,ref,marker,"TARGET_QUERY_CAP",false);return;}
        var target=result.candidates().stream().map(HytaleAreaQueries.Candidate::ref).filter(r->!r.equals(ref)&&HytaleSummonSystem.alive(store,r))
                .filter(r->store.getComponent(r,ConversionProjection.getComponentType())==null&&store.getComponent(r,SummonProjection.getComponentType())==null)
                .filter(r->HytaleAreaQueries.clear(store,point(store,ref).add(new Vec3(0,.5,0)),point(store,r).add(new Vec3(0,.5,0))))
                .min(Comparator.<Ref<EntityStore>>comparingDouble(r->point(store,r).subtract(point(store,ref)).length()).thenComparing(r->id(store,r))).orElse(null);
        store.getComponent(ref,MarkedEntitySupport.getComponentType()).setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT,target);
    }
    private void restore(Store<EntityStore> store,Ref<EntityStore> ref,ConversionRegistry.Lease lease){
        var marked=store.getComponent(ref,MarkedEntitySupport.getComponentType());if(marked==null)return;
        // A new persistent encounter owner supersedes this temporary lease and must never be erased.
        for(int i=0;i<marked.getMarkedEntitySlotCount();i++)if(marked.isRebindSlot(i))return;
        marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT,lease.originalThreat()==null?null:store.getExternalData().getRefFromUUID(lease.originalThreat()));
    }
    private void end(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> ref,ConversionProjection marker,String reason,boolean dead){
        if(marker.lease==null||marker.restored)return;registry.end(marker.lease.token());
        restore(store,ref,marker.lease);
        marker.restored=true;
        if(!dead)buffer.tryRemoveComponent(ref,ConversionProjection.getComponentType());
        emit(marker.lease,RpgTraceEventType.CONVERSION_ENDED,Map.of("entity",marker.lease.entity(),"reason",reason,"originalAllegianceMutated",false));
    }
    private void emit(ConversionRegistry.Lease l,RpgTraceEventType event,Map<String,?> details){trace.emit(l.owner(),event,
            new CombatTrace.Context(l.context().rootCastId(),l.context().skillInstanceId(),l.context().request().correlationId()),details);}
    public static final class Removal extends com.hypixel.hytale.component.system.RefSystem<EntityStore>{
        private final HytaleConversionSystem conversions;
        public Removal(HytaleConversionSystem conversions){this.conversions=conversions;}
        @Override public Query<EntityStore> getQuery(){return ConversionProjection.getComponentType();}
        @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){}
        @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
            var marker=store.getComponent(ref,ConversionProjection.getComponentType());if(marker.lease==null)return;
            conversions.end(store,buffer,ref,marker,"NATIVE_REMOVE_"+reason,true);
        }
    }
    public static final class Death extends DeathSystems.OnDeathSystem {
        private final HytaleConversionSystem conversions;
        public Death(HytaleConversionSystem conversions){this.conversions=conversions;}
        @Override public Query<EntityStore> getQuery(){return Query.and(ConversionProjection.getComponentType(),NPCEntity.getComponentType());}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemDependency<>(Order.BEFORE,NPCDamageSystems.DropDeathItems.class));}
        @Override public void onComponentAdded(Ref<EntityStore> ref,DeathComponent death,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
            store.getComponent(ref,NPCEntity.getComponentType()).getRole().setDeathItemsDropped();
            conversions.end(store,buffer,ref,store.getComponent(ref,ConversionProjection.getComponentType()),"NATIVE_DEATH",true);
        }
    }
    public static final class DamageGuard extends DamageEventSystem {
        private final HytaleConversionSystem conversions;
        public DamageGuard(HytaleConversionSystem conversions){this.conversions=conversions;}
        @Override public Query<EntityStore> getQuery(){return Query.any();}
        @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getFilterDamageGroup();}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
            if(!(damage.getSource() instanceof Damage.EntitySource source)||source.getRef()==null||!source.getRef().isValid())return;
            var victim=store.getComponent(chunk.getReferenceTo(index),ConversionProjection.getComponentType());
            if(victim!=null&&victim.lease!=null&&conversions.registry.find(victim.lease.token()).isPresent()&&System.nanoTime()/1e9<victim.lease.expires()){
                var victimOwner=store.getExternalData().getRefFromUUID(victim.lease.owner());
                if(victimOwner==null||!victimOwner.isValid()||!HytaleAreaQueries.hostile(store,source.getRef(),victimOwner))damage.setCancelled(true);
            }
            var marker=store.getComponent(source.getRef(),ConversionProjection.getComponentType());if(marker==null)return;
            var target=chunk.getReferenceTo(index);var lease=marker.lease;var owner=lease==null?null:store.getExternalData().getRefFromUUID(lease.owner());
            var npc=store.getComponent(target,NPCEntity.getComponentType());var network=store.getComponent(target,NetworkId.getComponentType());
            if(lease==null||conversions.registry.find(lease.token()).isEmpty()||!HytaleSummonSystem.alive(store,owner)||System.nanoTime()/1e9>=lease.expires()
                    ||npc==null||npc.getRole()==null||npc.isReserved()||npc.getRole().isInvulnerable()||store.getComponent(target,Invulnerable.getComponentType())!=null
                    ||store.getComponent(target,SummonProjection.getComponentType())!=null||store.getComponent(target,ConversionProjection.getComponentType())!=null
                    ||network!=null&&conversions.bosses.isBoss(world(store),network.getId())||!HytaleAreaQueries.hostile(store,target,owner))damage.setCancelled(true);
        }
    }
}
