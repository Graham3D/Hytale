package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.damage.DamageDataComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter;
import java.util.ArrayList;
import java.util.Set;

/** Native post-stamina successful-block receipt for WA-147. */
public final class NativeItemSkillBlockObserver extends DamageEventSystem {
    private final ItemSkillTriggerRuntime triggers;
    private final ItemSkillTriggerRuntime.Port queue;
    public NativeItemSkillBlockObserver(ItemSkillTriggerRuntime triggers,ItemSkillTriggerRuntime.Port queue){
        this.triggers=java.util.Objects.requireNonNull(triggers);this.queue=java.util.Objects.requireNonNull(queue);
    }
    @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getInspectDamageGroup();}
    @Override public Query<EntityStore> getQuery(){return Query.and(PlayerRef.getComponentType(),DamageDataComponent.getComponentType());}
    @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
            new SystemDependency<>(Order.AFTER,DamageSystems.DamageStamina.class));}
    @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
                                 CommandBuffer<EntityStore> buffer,Damage damage){
        if(damage.isCancelled()||!Boolean.TRUE.equals(damage.getIfPresentMetaObject(Damage.BLOCKED)))return;
        var actor=chunk.getReferenceTo(index);
        var player=chunk.getComponent(index,PlayerRef.getComponentType());
        var data=chunk.getComponent(index,DamageDataComponent.getComponentType());
        if(data==null||data.getCurrentWielding()==null)return;
        var manager=store.getComponent(actor,InteractionModule.get().getInteractionManagerComponent());
        if(manager==null)return;
        var hotbar=store.getComponent(actor,InventoryComponent.Hotbar.getComponentType());
        var utility=store.getComponent(actor,InventoryComponent.Utility.getComponentType());
        var chains=new ArrayList<NativeAffixBlockCostSystem.GuardChain>();
        for(var chain:manager.getChains().values()){
            var context=chain.getContext();
            chains.add(new NativeAffixBlockCostSystem.GuardChain(chain.getType(),chain.getServerState(),
                    context.getHeldItemSectionId(),context.getHeldItemSlot(),context.getHeldItem()));
        }
        var held=NativeAffixBlockCostSystem.guardSource(chains,
                hotbar==null?-1:hotbar.getSectionId(),hotbar==null?-1:hotbar.getActiveSlot(),
                InventoryComponent.getItemInHand(store,actor),
                utility==null?-1:utility.getSectionId(),utility==null?-1:utility.getActiveSlot(),
                utility==null?null:utility.getActiveItem());
        if(!GearNativeItems.managed(held)||!GearNativeItems.canUse(held,actor,store))return;
        GearInstance item;
        try{item=GearNativeItems.read(held);}catch(RuntimeException invalid){return;}
        var snapshot=GearNativeItems.effects(actor,store).snapshot();
        if(snapshot.forItem(item.identity()).empty())return;
        var metadata=HytaleDamageAdapter.metadata(damage);
        String receipt=Integer.toHexString(System.identityHashCode(damage))+":"+index;
        triggers.blocked(new ItemSkillTriggerRuntime.Block(player.getWorldUuid(),player.getUuid(),item.identity(),
                metadata==null||metadata.rootCastId()==null?receipt:metadata.rootCastId(),receipt,snapshot,true,
                metadata!=null&&!metadata.canProc(),metadata!=null&&metadata.origin()==
                        com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata.Origin.REFLECTED),
                System.nanoTime()/1e9,queue);
    }
}
