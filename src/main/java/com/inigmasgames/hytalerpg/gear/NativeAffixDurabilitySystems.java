package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.random.RandomExtra;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.AllLegacyLivingEntityTypesQuery;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.meta.MetaKey;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import java.util.function.DoubleSupplier;
import java.util.function.IntUnaryOperator;
import java.util.Set;

/** WA-153 substitutions for the installed DamageArmor and DamageAttackerTool writers.
 * Register only after removing those two native systems from EntityStore.REGISTRY. */
public final class NativeAffixDurabilitySystems {
    private NativeAffixDurabilitySystems() {}
    static final MetaKey<Boolean> ARMOR_DONE=Damage.META_REGISTRY.registerMetaObject(
            ignored->false,false,"Hywind:AffixArmorLossHandled",com.hypixel.hytale.codec.Codec.BOOLEAN);
    static final MetaKey<Boolean> ATTACKER_DONE=Damage.META_REGISTRY.registerMetaObject(
            ignored->false,false,"Hywind:AffixAttackerLossHandled",com.hypixel.hytale.codec.Codec.BOOLEAN);

    static GearEffectSnapshot local(ItemStack stack,Ref<EntityStore> actor,ComponentAccessor<EntityStore> accessor) {
        if(!GearNativeItems.managed(stack)||stack.getItem().getDurabilityLossOnHit()<=0
                ||accessor.getComponent(actor,PlayerRef.getComponentType())==null)
            return GearEffectSnapshot.EMPTY;
        try {
            var gear=GearNativeItems.read(stack);
            return GearNativeItems.effects(actor,accessor).snapshot().forItem(gear.identity());
        } catch(RuntimeException invalid) { return GearEffectSnapshot.EMPTY; }
    }
    static GearEffectSnapshot attackerSource(Damage damage,GearEffectSnapshot candidate) {
        var hit=HytaleDamageAdapter.gearHit(damage);
        return hit!=null&&!candidate.empty()&&candidate.items().getFirst().identity().equals(hit.hit().itemId())
                ?candidate:GearEffectSnapshot.EMPTY;
    }

    /** One producer opportunity, one pre-debit roll. The writer is the installed ItemUtils
     * path for both stock gear and an unprevented managed loss. */
    static boolean debit(Damage event,MetaKey<Boolean> done,GearEffectSnapshot local,
                         DoubleSupplier random,Runnable nativeDebit) {
        if(Boolean.TRUE.equals(event.getIfPresentMetaObject(done)))return false;
        event.putMetaObject(done,true);
        double chance=local.percent(GearEffectSnapshot.Operator.DURABILITY_EFFICIENCY);
        if(chance>0) {
            double sample=random.getAsDouble();
            if(!Double.isFinite(sample)||sample<0||sample>=1)throw new IllegalArgumentException("Invalid durability roll");
            if(sample<Math.min(1,chance))return true;
        }
        nativeDebit.run();
        return false;
    }
    static int armorSlot(ItemContainer inventory,IntUnaryOperator nativeIndex) {
        var candidates=new ShortArrayList();
        inventory.forEachWithMeta((slot,item,list)->{if(!item.isBroken())list.add(slot);},candidates);
        if(candidates.isEmpty())return -1;
        int index=nativeIndex.applyAsInt(candidates.size());
        if(index<0||index>=candidates.size())throw new IllegalArgumentException("Invalid armor slot roll");
        return candidates.getShort(index);
    }

    public static final class Armor extends DamageEventSystem {
        @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getInspectDamageGroup();}
        @Override public Query<EntityStore> getQuery(){return AllLegacyLivingEntityTypesQuery.INSTANCE;}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
                new SystemDependency<>(Order.BEFORE,DamageSystems.DamageStamina.class));}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer,Damage damage) {
            if(!damage.getCause().isDurabilityLoss()||Boolean.TRUE.equals(damage.getIfPresentMetaObject(ARMOR_DONE)))return;
            var actor=chunk.getReferenceTo(index);
            var armor=buffer.getComponent(actor,InventoryComponent.Armor.getComponentType());
            if(armor==null)return;
            var inventory=armor.getInventory();
            int selected=armorSlot(inventory,RandomExtra::randomRange);
            if(selected<0)return;
            short slot=(short)selected;
            var stack=inventory.getItemStack(slot);
            debit(damage,ARMOR_DONE,local(stack,actor,buffer),Math::random,
                    ()->ItemUtils.decreaseItemStackDurability(actor,stack,InventoryComponent.ARMOR_SECTION_ID,slot,buffer));
        }
    }

    public static final class Attacker extends DamageEventSystem {
        @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getInspectDamageGroup();}
        @Override public Query<EntityStore> getQuery(){return Query.and(AllLegacyLivingEntityTypesQuery.INSTANCE,
                InventoryComponent.Hotbar.getComponentType());}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
                new SystemDependency<>(Order.AFTER,DamageSystems.DamageStamina.class));}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer,Damage damage) {
            if(!damage.getCause().isDurabilityLoss()||!(damage.getSource() instanceof Damage.EntitySource source)
                    ||Boolean.TRUE.equals(damage.getIfPresentMetaObject(ATTACKER_DONE)))return;
            var actor=source.getRef();
            if(!actor.isValid())return;
            var hotbar=buffer.getComponent(actor,InventoryComponent.Hotbar.getComponentType());
            if(hotbar==null||hotbar.getActiveSlot()==-1)return;
            var stack=InventoryComponent.getItemInHand(buffer,actor);
            int slot=hotbar.getActiveSlot();
            var itemEffects=attackerSource(damage,local(stack,actor,buffer));
            debit(damage,ATTACKER_DONE,itemEffects,Math::random,
                    ()->ItemUtils.decreaseItemStackDurability(actor,stack,InventoryComponent.HOTBAR_SECTION_ID,slot,buffer));
        }
    }
}
