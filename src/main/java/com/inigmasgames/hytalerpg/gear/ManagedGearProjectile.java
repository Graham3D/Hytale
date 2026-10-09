package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.event.InteractionChainStartEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.projectile.event.ProjectileLaunchEvent;
import com.hypixel.hytale.server.core.modules.projectile.interaction.ProjectileInteraction;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** Native launch/physics/impact owner with a server-only immutable launch snapshot. */
public final class ManagedGearProjectile extends ProjectileInteraction {
    public static final String TYPE="RPG_GearProjectile";
    public static final BuilderCodec<ManagedGearProjectile> CODEC=BuilderCodec.builder(
            ManagedGearProjectile.class,ManagedGearProjectile::new,ProjectileInteraction.CODEC).build();
    private static final ThreadLocal<Snapshot> LAUNCH=new ThreadLocal<>();
    private static ComponentType<EntityStore,Snapshot> component;
    static double samplePower(GearInstance gear,String strike){
        var range=GearCombatEffects.physical(gear);
        return GearPower.sample(range.minimum(),range.maximum(),strike);
    }
    public static void bind(ComponentType<EntityStore,Snapshot> type){component=Objects.requireNonNull(type);}
    public static boolean hasSnapshot(Ref<EntityStore> entity,ComponentAccessor<EntityStore> accessor){
        return component!=null&&entity!=null&&entity.isValid()&&accessor.getComponent(entity,component)!=null;
    }
    public static Snapshot snapshot(InteractionContext context){
        var entity=context.getEntity();
        return component==null||entity==null||!entity.isValid()?null:context.getCommandBuffer().getComponent(entity,component);
    }
    public static Snapshot snapshot(Ref<EntityStore> projectile,ComponentAccessor<EntityStore> accessor){
        return component==null||projectile==null||!projectile.isValid()?null:accessor.getComponent(projectile,component);
    }
    public static final class Snapshot implements Component<EntityStore> {
        final ItemStack stack; final GearInstance gear; final Map<String,String> variables; final double power;
        final GearEffectSnapshot effects; final String rootId;
        private final String originalHitRoot;
        final com.inigmasgames.hytalerpg.execution.math.Vec3 origin;
        final NativeAffixProjectileTravel.Launch travel;
        final NativeGearAttackAcceptance.Chain acceptance;
        public Snapshot(){stack=null;gear=null;variables=Map.of();power=0;effects=GearEffectSnapshot.EMPTY;rootId="";origin=null;travel=null;acceptance=null;originalHitRoot=null;}
        Snapshot(ItemStack stack,GearInstance gear,Map<String,String> variables,double power,
                 GearEffectSnapshot effects,String rootId,com.inigmasgames.hytalerpg.execution.math.Vec3 origin,
                 NativeAffixProjectileTravel.Launch travel,NativeGearAttackAcceptance.Chain acceptance){
            this.stack=Objects.requireNonNull(stack);this.gear=Objects.requireNonNull(gear);
            this.variables=Map.copyOf(variables);this.power=power;
            this.effects=Objects.requireNonNull(effects);this.rootId=Objects.requireNonNull(rootId);this.origin=origin;
            this.travel=travel;this.originalHitRoot=null;
            this.acceptance=Objects.requireNonNull(acceptance);
        }
        private Snapshot(Snapshot source,String originalHitRoot){
            this.stack=source.stack;this.gear=source.gear;this.variables=source.variables;this.power=source.power;
            this.effects=source.effects;this.rootId=source.rootId;this.origin=source.origin;this.travel=source.travel;
            this.acceptance=source.acceptance;this.originalHitRoot=originalHitRoot;
        }
        public String originalHitRoot(){return originalHitRoot;}
        Snapshot withOriginalHitRoot(String root){return new Snapshot(this,root);}
        public GearEffectSnapshot effects(){return effects;}
        public String rootId(){return rootId;}
        public com.inigmasgames.hytalerpg.execution.math.Vec3 origin(){return origin;}
        public UUID itemId(){return gear.identity();}
        @Override public Snapshot clone(){return this;}
    }
    @Override protected void firstRun(InteractionType type,InteractionContext context,CooldownHandler cooldowns){
        if(!GearNativeItems.canUse(context.getHeldItem(),context.getOwningEntity(),context.getCommandBuffer())){
            context.getState().state=com.hypixel.hytale.protocol.InteractionState.Failed;return;
        }
        var gear=GearNativeItems.read(context.getHeldItem());
        var chain=context.getChain();
        String strike=gear.identity()+"/projectile/"+(chain.getForkedChainId()==null?chain.getChainId():chain.getForkedChainId());
        double power=samplePower(gear,strike);
        var acceptance=NativeGearAcceptedContext.require(context,gear);
        var effects=acceptance.snapshot();
        if(effects.forItem(gear.identity()).empty()){
            context.getState().state=com.hypixel.hytale.protocol.InteractionState.Failed;return;
        }
        var transform=context.getCommandBuffer().getComponent(context.getOwningEntity(),
                com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
        var at=transform==null?null:transform.getPosition();
        var origin=at==null?null:new com.inigmasgames.hytalerpg.execution.math.Vec3(at.x,at.y,at.z);
        var config=getConfig();
        var travel=config==null?null:NativeAffixProjectileTravel.launch(effects,gear.identity(),
                config.getMuzzleVelocity(),config.getMuzzleVelocity()*lifetime,lifetime,false);
        var previous=LAUNCH.get();LAUNCH.set(new Snapshot(context.getHeldItem(),gear,context.getInteractionVars(),power,effects,strike,origin,travel,acceptance));
        try{super.firstRun(type,context,cooldowns);}finally{if(previous==null)LAUNCH.remove();else LAUNCH.set(previous);}
    }
    public static final class Launch extends EntityEventSystem<EntityStore,ProjectileLaunchEvent>{
        public Launch(){super(ProjectileLaunchEvent.class);}
        private com.inigmasgames.hytalerpg.execution.hytale.PlayerHitRootOwner roots;
        public Launch(com.inigmasgames.hytalerpg.execution.hytale.PlayerHitRootOwner roots){this();this.roots=Objects.requireNonNull(roots);}
        @Override public Query<EntityStore> getQuery(){return PlayerRef.getComponentType();}
        @Override public void handle(int i,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,ProjectileLaunchEvent event){
            var frozen=LAUNCH.get();if(frozen==null)return;
            String originalHitRoot=null;
            if(roots!=null)try{originalHitRoot=roots.issue(store.getExternalData().getWorld().getWorldConfig().getUuid(),
                    chunk.getComponent(i,PlayerRef.getComponentType()).getUuid());}
            catch(RuntimeException ignored){} // Missing receipt cannot alter the native launch.
            var attached=frozen.withOriginalHitRoot(originalHitRoot);
            var projectile=event.getProjectileRef();
            // Native ProjectileInteraction invokes this event before returning; installation drains before physics.
            buffer.run(s->{if(projectile.isValid()){
                s.putComponent(projectile,component,attached);
                // No codec: a pending arrow must never restart without its launch snapshot.
                s.ensureComponent(projectile,EntityStore.REGISTRY.getNonSerializedComponentType());
                if(frozen.travel!=null) {
                    var provider=s.getComponent(projectile,StandardPhysicsProvider.getComponentType());
                    var velocity=s.getComponent(projectile,Velocity.getComponentType());
                    var position=s.getComponent(projectile,TransformComponent.getComponentType());
                    var despawn=s.getComponent(projectile,DespawnComponent.getComponentType());
                    if(provider==null||velocity==null||position==null||despawn==null)
                        throw new IllegalStateException("WA-015/016 native projectile physics component missing");
                    NativeAffixProjectileTravel.applyNativeSpeed(frozen.travel,velocity,provider);
                    var time=s.getResource(TimeResource.getResourceType());
                    long millis=Math.max(1L,(long)Math.ceil(frozen.travel.safetyLifetimeSeconds()*1000));
                    despawn.setDespawn(time.getNow().plusMillis(millis));
                    var pathType=NativeAffixProjectilePathSystem.pathType();
                    if(pathType==null)throw new IllegalStateException("WA-015/016 path component not registered");
                    var path=new NativeAffixProjectileTravel.Path(frozen.travel,position.getPosition());
                    provider.setImpactConsumer(NativeAffixProjectileTravel.guardImpact(path,provider.getImpactConsumer()));
                    s.putComponent(projectile,pathType,path);
                }
            }});
        }
    }
    public static final class Impact extends EntityEventSystem<EntityStore,InteractionChainStartEvent>{
        public Impact(){super(InteractionChainStartEvent.class);}
        // Native proxy chains are dispatched on the shooter's InteractionManager.
        @Override public Query<EntityStore> getQuery(){return PlayerRef.getComponentType();}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemDependency<>(Order.BEFORE,HytaleGearEquipment.Use.class));}
        @Override public void handle(int i,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,InteractionChainStartEvent event){
            var entity=event.getContext().getEntity();
            if(entity==null||!entity.isValid())return;
            var frozen=buffer.getComponent(entity,component);if(frozen==null)return;
            if(frozen.gear==null){event.setCancelled(true);return;}
            event.getContext().setHeldItem(frozen.stack);
            event.getContext().setInteractionVarsGetter(ignored->frozen.variables);
        }
    }
}
