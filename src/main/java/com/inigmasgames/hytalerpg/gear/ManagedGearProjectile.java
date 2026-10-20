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
    public static void bind(ComponentType<EntityStore,Snapshot> type){component=Objects.requireNonNull(type);}
    public static boolean hasSnapshot(Ref<EntityStore> entity,ComponentAccessor<EntityStore> accessor){
        return component!=null&&entity!=null&&entity.isValid()&&accessor.getComponent(entity,component)!=null;
    }
    public static Snapshot snapshot(InteractionContext context){
        var entity=context.getEntity();
        return component==null||entity==null||!entity.isValid()?null:context.getCommandBuffer().getComponent(entity,component);
    }
    public static final class Snapshot implements Component<EntityStore> {
        final ItemStack stack; final GearInstance gear; final Map<String,String> variables; final double power;
        public Snapshot(){stack=null;gear=null;variables=Map.of();power=0;}
        Snapshot(ItemStack stack,GearInstance gear,Map<String,String> variables,double power){
            this.stack=Objects.requireNonNull(stack);this.gear=Objects.requireNonNull(gear);
            this.variables=Map.copyOf(variables);this.power=power;
        }
        @Override public Snapshot clone(){return this;}
    }
    @Override protected void firstRun(InteractionType type,InteractionContext context,CooldownHandler cooldowns){
        if(!GearNativeItems.canUse(context.getHeldItem(),context.getOwningEntity(),context.getCommandBuffer())){
            context.getState().state=com.hypixel.hytale.protocol.InteractionState.Failed;return;
        }
        var gear=GearNativeItems.read(context.getHeldItem());
        var chain=context.getChain();
        String strike=gear.identity()+"/projectile/"+(chain.getForkedChainId()==null?chain.getChainId():chain.getForkedChainId());
        var range=GearAffixRuntime.physical(gear);
        double power=GearPower.sample(range.minimum(),range.maximum(),strike);
        var previous=LAUNCH.get();LAUNCH.set(new Snapshot(context.getHeldItem(),gear,context.getInteractionVars(),power));
        try{super.firstRun(type,context,cooldowns);}finally{if(previous==null)LAUNCH.remove();else LAUNCH.set(previous);}
    }
    public static final class Launch extends EntityEventSystem<EntityStore,ProjectileLaunchEvent>{
        public Launch(){super(ProjectileLaunchEvent.class);}
        @Override public Query<EntityStore> getQuery(){return PlayerRef.getComponentType();}
        @Override public void handle(int i,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,ProjectileLaunchEvent event){
            var frozen=LAUNCH.get();if(frozen==null)return;
            var projectile=event.getProjectileRef();
            // Native ProjectileInteraction invokes this event before returning; installation drains before physics.
            buffer.run(s->{if(projectile.isValid()){
                s.putComponent(projectile,component,frozen);
                // No codec: a pending arrow must never restart without its launch snapshot.
                s.ensureComponent(projectile,EntityStore.REGISTRY.getNonSerializedComponentType());
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
