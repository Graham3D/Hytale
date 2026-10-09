package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.damage.DamageDataComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** WA-083: alter the authenticated native block debit before DamageStamina consumes it. */
public final class NativeAffixBlockCostSystem extends DamageEventSystem {
    private static final MetaKey<Boolean> APPLIED=Damage.META_REGISTRY.registerMetaObject(
            ignored->false,false,"Hywind:AffixBlockCostApplied",com.hypixel.hytale.codec.Codec.BOOLEAN);

    @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getInspectDamageGroup();}
    @Override public Query<EntityStore> getQuery(){return Query.and(PlayerRef.getComponentType(),DamageDataComponent.getComponentType());}
    @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
            new SystemDependency<>(Order.BEFORE,DamageSystems.DamageStamina.class));}

    @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
                                 CommandBuffer<EntityStore> buffer,Damage damage){
        if(damage.isCancelled()||!Boolean.TRUE.equals(damage.getIfPresentMetaObject(Damage.BLOCKED)))return;
        var actor=chunk.getReferenceTo(index);
        var data=chunk.getComponent(index,DamageDataComponent.getComponentType());
        if(data==null||data.getCurrentWielding()==null||data.getCurrentWielding().getStaminaCost()==null)return;
        var hotbar=store.getComponent(actor,InventoryComponent.Hotbar.getComponentType());
        var utility=store.getComponent(actor,InventoryComponent.Utility.getComponentType());
        var manager=store.getComponent(actor,InteractionModule.get().getInteractionManagerComponent());
        if(manager==null)return;
        var chains=new ArrayList<GuardChain>();
        for(var chain:manager.getChains().values()) {
            var context=chain.getContext();
            chains.add(new GuardChain(chain.getType(),chain.getServerState(),context.getHeldItemSectionId(),
                    context.getHeldItemSlot(),context.getHeldItem()));
        }
        var held=guardSource(chains,hotbar==null?-1:hotbar.getSectionId(),
                hotbar==null?-1:hotbar.getActiveSlot(),InventoryComponent.getItemInHand(store,actor),
                utility==null?-1:utility.getSectionId(),utility==null?-1:utility.getActiveSlot(),
                utility==null?null:utility.getActiveItem());
        if(!GearNativeItems.managed(held)||!GearNativeItems.canUse(held,actor,store))return;
        GearInstance item;
        try{item=GearNativeItems.read(held);}catch(RuntimeException invalid){return;}
        var snapshot=GearNativeItems.effects(actor,store).snapshot();
        if(snapshot.items().stream().noneMatch(valid->valid.identity().equals(item.identity())))return;
        apply(damage,snapshot.forItem(item.identity()));
    }

    /** The native guard state stores a WieldingInteraction but no source hand. A live
     * Secondary chain supplies its original section, slot and held item. Ambiguous
     * or stale chains must not attribute a hotbar affix to an offhand guard. */
    record GuardChain(InteractionType type,InteractionState state,int section,int slot,ItemStack held) {}
    static ItemStack guardSource(Iterable<GuardChain> chains,int hotbarSection,int hotbarSlot,ItemStack hotbar,
                                 int utilitySection,int utilitySlot,ItemStack utility) {
        ItemStack selected=null;
        for(var chain:chains) {
            if(chain.type()!=InteractionType.Secondary||chain.state()!=InteractionState.NotFinished)continue;
            ItemStack current=null;
            if(chain.section()==hotbarSection&&chain.slot()==hotbarSlot)current=hotbar;
            else if(chain.section()==utilitySection&&chain.slot()==utilitySlot)current=utility;
            if(ItemStack.isEmpty(current)||!current.equals(chain.held()))continue;
            if(selected!=null)return null;
            selected=current;
        }
        return selected;
    }

    /** Same mutation used by the ECS adapter; callable with controlled legal snapshots. */
    public static boolean apply(Damage damage,GearEffectSnapshot guard){
        if(damage.isCancelled()||!Boolean.TRUE.equals(damage.getIfPresentMetaObject(Damage.BLOCKED))
                ||Boolean.TRUE.equals(damage.getIfPresentMetaObject(APPLIED)))return false;
        double reduction=guard.percent(GearEffectSnapshot.Operator.BLOCK_COST);
        if(reduction<=0)return false;
        float prior=damage.getIfPresentMetaObject(Damage.STAMINA_DRAIN_MULTIPLIER)==null
                ?1f:damage.getIfPresentMetaObject(Damage.STAMINA_DRAIN_MULTIPLIER);
        if(!Float.isFinite(prior)||prior<0)throw new IllegalArgumentException("Invalid native block multiplier");
        damage.putMetaObject(Damage.STAMINA_DRAIN_MULTIPLIER,(float)(prior*Math.max(0,1-reduction)));
        damage.putMetaObject(APPLIED,true);
        return true;
    }
}
