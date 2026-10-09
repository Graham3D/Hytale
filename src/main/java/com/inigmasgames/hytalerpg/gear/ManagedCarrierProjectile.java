package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.event.InteractionChainStartEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.projectile.event.ProjectileLaunchEvent;
import com.hypixel.hytale.server.core.modules.projectile.interaction.ProjectileInteraction;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** Native collision and replication with immutable source identity at launch. */
public final class ManagedCarrierProjectile extends ProjectileInteraction {
    public static final String TYPE="RPG_CarrierProjectile";
    public static final BuilderCodec<ManagedCarrierProjectile> CODEC=BuilderCodec.builder(
            ManagedCarrierProjectile.class,ManagedCarrierProjectile::new,ProjectileInteraction.CODEC).build();
    private static final ThreadLocal<Snapshot> LAUNCH=new ThreadLocal<>();
    private static ComponentType<EntityStore,Snapshot> component;
    public static void bind(ComponentType<EntityStore,Snapshot> type){component=Objects.requireNonNull(type);}
    public static boolean hasSnapshot(Ref<EntityStore> entity,ComponentAccessor<EntityStore> accessor){
        return component!=null&&entity!=null&&entity.isValid()&&accessor.getComponent(entity,component)!=null;
    }
    public static Snapshot snapshot(InteractionContext context){
        var entity=context.getEntity();return !hasSnapshot(entity,context.getCommandBuffer())?null:context.getCommandBuffer().getComponent(entity,component);
    }
    public static final class Snapshot implements Component<EntityStore> {
        private final ItemStack stack;private final GearInstance gear;private final Map<String,String> variables;
        private final double power;private final GearEffectSnapshot effects;private final String rootId;private final Vec3 origin;
        private final NativeGearAttackAcceptance.Chain acceptance;
        public Snapshot(){this(null,null,Map.of(),0,GearEffectSnapshot.EMPTY,"",null,null);}
        Snapshot(ItemStack stack,GearInstance gear,Map<String,String> variables,double power,GearEffectSnapshot effects,String rootId,Vec3 origin,NativeGearAttackAcceptance.Chain acceptance){
            this.stack=stack;this.gear=gear;this.variables=Map.copyOf(variables);this.power=power;
            this.effects=Objects.requireNonNull(effects);this.rootId=Objects.requireNonNull(rootId);this.origin=origin;
            this.acceptance=acceptance;
        }
        public GearInstance gear(){return gear;}public double power(){return power;}
        public NativeGearAttackAcceptance.Chain acceptance(){return acceptance;}
        public GearEffectSnapshot effects(){return effects;}public String rootId(){return rootId;}public Vec3 origin(){return origin;}
        @Override public Snapshot clone(){return this;}
    }
    @Override protected void firstRun(InteractionType type,InteractionContext context,CooldownHandler cooldowns){
        if(component==null)throw new IllegalStateException("Carrier projectile snapshot component not registered");
        if(!GearNativeItems.canUse(context.getHeldItem(),context.getOwningEntity(),context.getCommandBuffer())){
            context.getState().state=InteractionState.Failed;return;
        }
        var gear=GearNativeItems.read(context.getHeldItem());
        if(gear==null||!(gear.baseId().startsWith("gm.staff_")||gear.baseId().startsWith("gm.wand_")
                ||gear.baseId().startsWith("gm.book_")||gear.baseId().startsWith("gm.bomb_")))
            throw new IllegalArgumentException("Unsupported carrier projectile");
        var acceptance=NativeGearAcceptedContext.require(context,gear);
        var effects=acceptance.snapshot();
        if(effects.forItem(gear.identity()).empty()){context.getState().state=InteractionState.Failed;return;}
        var chain=context.getChain();String root=gear.identity()+"/carrier-projectile/"+
                (chain.getForkedChainId()==null?chain.getChainId():chain.getForkedChainId());
        double power=0;
        if(gear.baseId().startsWith("gm.bomb_")){
            var range=GearCombatEffects.physical(gear);power=GearPower.sample(range.minimum(),range.maximum(),root);
        }
        var transform=context.getCommandBuffer().getComponent(context.getOwningEntity(),TransformComponent.getComponentType());
        var at=transform==null?null:transform.getPosition();
        var origin=at==null?null:new Vec3(at.x,at.y,at.z);
        var previous=LAUNCH.get();LAUNCH.set(new Snapshot(context.getHeldItem(),gear,context.getInteractionVars(),power,effects,root,origin,acceptance));
        try{super.firstRun(type,context,cooldowns);}finally{if(previous==null)LAUNCH.remove();else LAUNCH.set(previous);}
    }
    public static final class Launch extends EntityEventSystem<EntityStore,ProjectileLaunchEvent> {
        public Launch(){super(ProjectileLaunchEvent.class);}
        @Override public Query<EntityStore> getQuery(){return PlayerRef.getComponentType();}
        @Override public void handle(int i,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer,ProjectileLaunchEvent event){
            var frozen=LAUNCH.get();if(frozen==null)return;
            var projectile=event.getProjectileRef();
            buffer.run(s->{if(projectile.isValid()){
                s.putComponent(projectile,component,frozen);
                s.ensureComponent(projectile,EntityStore.REGISTRY.getNonSerializedComponentType());
            }});
        }
    }
    public static final class Impact extends EntityEventSystem<EntityStore,InteractionChainStartEvent> {
        public Impact(){super(InteractionChainStartEvent.class);}
        @Override public Query<EntityStore> getQuery(){return PlayerRef.getComponentType();}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemDependency<>(Order.BEFORE,HytaleGearEquipment.Use.class));}
        @Override public void handle(int i,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer,InteractionChainStartEvent event){
            var entity=event.getContext().getEntity();if(entity==null||!entity.isValid()||component==null)return;
            var frozen=buffer.getComponent(entity,component);if(frozen==null)return;
            if(frozen.gear==null){event.setCancelled(true);return;}
            event.getContext().setHeldItem(frozen.stack);
            event.getContext().setInteractionVarsGetter(ignored->frozen.variables);
        }
    }
}
