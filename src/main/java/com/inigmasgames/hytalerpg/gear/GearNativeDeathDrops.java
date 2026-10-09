package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.*;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.NPCDamageSystems;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleDifficultyCombat;
import java.util.*;

/** Extends the installed NPC death-drop transaction, sharing its Role once-only flag and native drop owner.
 * Only registered difficulty enemies are intercepted. Ingredients and already-owned pickup storage survive.
 */
public final class GearNativeDeathDrops extends EntityTickingSystem<EntityStore> {
    private final HytaleDifficultyCombat encounters;
    private final NPCDamageSystems.DropDeathItems nativeOwner=new NPCDamageSystems.DropDeathItems();
    public GearNativeDeathDrops(HytaleDifficultyCombat encounters){this.encounters=encounters;}
    @Override public Query<EntityStore> getQuery(){return Query.and(nativeOwner.getQuery(),UUIDComponent.getComponentType());}
    @Override public Set<Dependency<EntityStore>> getDependencies(){
        var dependencies=new HashSet<>(nativeOwner.getDependencies());
        dependencies.add(new SystemDependency<>(Order.BEFORE,NPCDamageSystems.DropDeathItems.class));return Set.copyOf(dependencies);
    }
    public static boolean randomEquipment(ItemStack item){
        var definition=item.getItem();return definition!=null&&(definition.getWeapon()!=null||definition.getArmor()!=null);
    }
    @Override public void tick(float dt,int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        var enemy=chunk.getComponent(index,UUIDComponent.getComponentType()).getUuid();
        var world=store.getExternalData().getWorld().getWorldConfig().getUuid();
        if(encounters.snapshot(world,enemy).isEmpty())return;
        var death=chunk.getComponent(index,DeathComponent.getComponentType());
        if(death.getItemsLossMode()!=com.hypixel.hytale.server.core.asset.type.gameplay.DeathConfig.ItemsLossMode.ALL)return;
        var role=chunk.getComponent(index,NPCEntity.getComponentType()).getRole();
        if(role==null||role.hasDroppedDeathItems())return;
        var spawn=encounters.snapshot(world,enemy).orElseThrow();
        if(spawn.enemyRewards()!=null&&spawn.enemyRewards().origin()==
                com.inigmasgames.hytalerpg.enemies.EnemyRewardContext.Origin.QA){
            role.setDeathItemsDropped(); // Same native once-only owner, with no QA loot projection.
            return;
        }
        if(!role.isDropDeathItemsInstantly()){
            var corpse=chunk.getComponent(index,DeferredCorpseRemoval.getComponentType());
            if(corpse!=null&&!corpse.shouldRemove())return;
        }
        var drops=new ArrayList<ItemStack>();
        var module=ItemModule.get();
        if(role.getDropListId()!=null&&module.isEnabled())
            for(var item:module.getRandomItemDrops(role.getDropListId()))if(!randomEquipment(item))drops.add(item);
        // Pickup storage is restitution of existing ownership, not another random equipment opportunity.
        var storage=chunk.getComponent(index,InventoryComponent.Storage.getComponentType());
        if(role.isPickupDropOnDeath()&&storage!=null)drops.addAll(storage.getInventory().dropAllItemStacks());
        role.setDeathItemsDropped();
        if(!drops.isEmpty()){
            var position=new org.joml.Vector3d(chunk.getComponent(index,TransformComponent.getComponentType()).getPosition()).add(0,1,0);
            var rotation=chunk.getComponent(index,HeadRotation.getComponentType()).getRotation();
            buffer.addEntities(ItemComponent.generateItemDrops(store,drops,position,rotation),AddReason.SPAWN);
        }
    }
}
