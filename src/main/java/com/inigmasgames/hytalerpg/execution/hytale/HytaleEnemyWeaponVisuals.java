package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.dependency.SystemGroupDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.protocol.EquipmentUpdate;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.InventoryUtils;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry;
import com.inigmasgames.hytalerpg.enemies.EnemyDescriptor;
import com.inigmasgames.hytalerpg.enemies.MonsterVisualProfile;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Viewer-only native equipment image. Never writes NPC inventory or its combat item. */
public final class HytaleEnemyWeaponVisuals extends TickingSystem<EntityStore> {
    private static final String NATIVE="Weapon_Battleaxe_Stone_Trork";
    private static final String FIRE="RPG_ME_Trork_Battleaxe_FireVisual";
    private record Key(UUID world,UUID actor){}
    private final Map<Key,Map<UUID,String>> published=new ConcurrentHashMap<>();
    private final Set<String> failures=ConcurrentHashMap.newKeySet();

    public void track(EnemyDescriptor descriptor){
        var profile=MonsterVisualProfile.from(descriptor);
        if(profile.weaponVisualOwner()==null)return;
        if(descriptor.nativeRoleId().equals("Trork_Warrior")
                &&profile.weaponVisualOwner()==EnemyAffixRegistry.Operator.FIRE_ENCHANTED)
            published.computeIfAbsent(new Key(descriptor.worldId(),descriptor.entityId()),key->new HashMap<>());
        else once("UNSUPPORTED_WEAPON_REGION:"+descriptor.nativeRoleId()+":"+profile.weaponVisualOwner());
    }
    @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
            new SystemGroupDependency<>(Order.AFTER,EntityTrackerSystems.QUEUE_UPDATE_GROUP),
            new SystemDependency<>(Order.BEFORE,EntityTrackerSystems.SendPackets.class));}
    @Override public void tick(float delta,int index,Store<EntityStore> store){
        try(var span=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,
                com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.HUD)){
        UUID world=store.getExternalData().getWorld().getWorldConfig().getUuid();
        if(published.isEmpty())return;
        if(Item.getAssetMap().getAsset(FIRE)==null){once("COSMETIC_ITEM_MISSING");return;}
        for(var entry:published.entrySet()){
            if(!entry.getKey().world().equals(world))continue;
            var actor=store.getExternalData().getRefFromUUID(entry.getKey().actor());
            if(actor==null||!actor.isValid()||store.getComponent(actor,DeathComponent.getComponentType())!=null){
                published.remove(entry.getKey());continue;
            }
            try{sync(store,actor,entry.getValue());}
            catch(RuntimeException failure){once(failure.getClass().getSimpleName()+":"+failure.getMessage());}
        }
        }
    }
    private void sync(Store<EntityStore> store,Ref<EntityStore> actor,Map<UUID,String> viewers){
        var held=InventoryComponent.getItemInHand(store,actor);
        String nativeId=held==null?"Empty":held.getItemId();
        var seen=new HashSet<UUID>();
        for(var player:store.getExternalData().getWorld().getPlayerRefs()){
            var playerRef=player.getReference();if(playerRef==null||!playerRef.isValid())continue;
            var viewer=store.getComponent(playerRef,EntityTrackerSystems.EntityViewer.getComponentType());
            // A component update is valid only after the native entity spawn was sent.
            if(viewer==null||!viewer.visible.contains(actor)||!viewer.sent.containsKey(actor))continue;
            seen.add(player.getUuid());
            EquipmentUpdate queued=null;
            var updates=viewer.updates.get(actor);
            if(updates!=null)for(var packet:updates.toUpdatesArray())
                if(packet instanceof EquipmentUpdate equipment)queued=equipment;
            if(!NATIVE.equals(nativeId)){
                if(viewers.remove(player.getUuid())!=null&&queued==null)viewer.queueUpdate(actor,nativeEquipment(store,actor));
                if(!"Empty".equals(nativeId))once("NON_NATIVE_HELD_ITEM:"+nativeId);
                continue;
            }
            if(queued==null&&FIRE.equals(viewers.get(player.getUuid())))continue;
            var cosmetic=queued==null?nativeEquipment(store,actor):new EquipmentUpdate(queued);
            if(!NATIVE.equals(cosmetic.rightHandItemId)){
                once("EQUIPMENT_PACKET_MISMATCH:"+cosmetic.rightHandItemId);continue;
            }
            cosmetic.rightHandItemId=FIRE;
            viewer.queueUpdate(actor,cosmetic);
            viewers.put(player.getUuid(),FIRE);
        }
        viewers.keySet().retainAll(seen);
    }
    private static EquipmentUpdate nativeEquipment(Store<EntityStore> store,Ref<EntityStore> actor){
        return InventoryUtils.createEquipmentUpdate(actor,store,null,
                store.getComponent(actor,InventoryComponent.Armor.getComponentType()),
                store.getComponent(actor,InventoryComponent.Utility.getComponentType()));
    }
    private void once(String reason){if(failures.add(reason))
        com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                "RPG_ENEMY_WEAPON_VISUAL capability=false reason=%s gameplayContinues=true",reason);}
}
