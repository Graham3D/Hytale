package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.EntityStatsUpdate;
import com.hypixel.hytale.protocol.EntityStatOp;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entitystats.*;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.hytale.EntityStatResourcePort;
import com.inigmasgames.hytalerpg.combat.resource.*;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import java.util.*;

/** Opt-in isolated native update-queue proof; never claims delivery/rendering on a connected client. */
final class NativeManaReplicationAudit {
    static void audit(Store<EntityStore> store){
        if(!Boolean.getBoolean("rpg.projectileSpawnAudit"))throw new IllegalStateException("ISOLATED_AUDIT_DISABLED");
        var registry=EntityStore.REGISTRY.getData();
        int execution=-1,changes=-1,tracker=-1,clear=-1;
        EntityStatsSystems.EntityTrackerUpdate sender=null;
        EntityStatsSystems.ClearChanges cleaner=null;
        for(int i=0;i<registry.getSystemSize();i++){
            var system=registry.getSystem(i);
            if(system instanceof HytaleSkillExecutionSystem)execution=i;
            if(system instanceof EntityStatsSystems.Changes)changes=i;
            if(system instanceof EntityStatsSystems.EntityTrackerUpdate s){tracker=i;sender=s;}
            if(system instanceof EntityStatsSystems.ClearChanges s){clear=i;cleaner=s;}
        }
        if(execution<0||changes<=execution||tracker<=changes||clear<=tracker)
            throw new IllegalStateException("NATIVE_MANA_ORDER:"+execution+"/"+changes+"/"+tracker+"/"+clear);
        var stats=new EntityStatMap();stats.update();
        int mana=DefaultEntityStatTypes.getMana();stats.maximizeStatValue(mana);
        stats.consumeSelfNetworkOutdated();stats.consumeNetworkOutdated();stats.clearUpdates();
        var owner=UUID.randomUUID();var holder=EntityStore.REGISTRY.newHolder();
        holder.addComponent(UUIDComponent.getComponentType(),new UUIDComponent(owner));
        holder.addComponent(EntityStatMap.getComponentType(),stats);
        var visible=new EntityTrackerSystems.Visible();
        holder.addComponent(EntityTrackerSystems.Visible.getComponentType(),visible);
        holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
        var ref=store.addEntity(holder,AddReason.SPAWN);
        try{
            var viewer=new EntityTrackerSystems.EntityViewer(128,null);
            viewer.visible.add(ref);
            visible.visibleTo.put(ref,viewer);
            var resources=new EntityStatResourcePort(stats);
            var service=new RpgResourceService(com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile.loadCanonical(),new ReservationService());
            double before=resources.current(ResourceType.MANA);
            var token=service.reserveCost(owner,new ResourceCost(ResourceType.MANA,28),resources);
            service.commitCost(token,resources);
            service.commitCost(token,resources); // same receipt cannot charge twice
            if(resources.current(ResourceType.MANA)!=before-28)throw new IllegalStateException("NATIVE_MANA_DEBIT");
            var nativeSender=sender;var nativeCleaner=cleaner;
            store.forEachChunk((java.util.function.BiConsumer<ArchetypeChunk<EntityStore>,CommandBuffer<EntityStore>>)(chunk,buffer)->{
                for(int i=0;i<chunk.size();i++)if(chunk.getReferenceTo(i).equals(ref)){
                    nativeSender.tick(.05f,i,chunk,store,buffer);
                    nativeCleaner.tick(.05f,i,chunk,store,buffer);
                }
            });
            var updates=viewer.updates.get(ref);
            if(updates==null)throw new IllegalStateException("NATIVE_MANA_NO_VIEWER_UPDATE");
            var debits=Arrays.stream(updates.toUpdatesArray()).filter(u->u instanceof EntityStatsUpdate)
                    .map(u->((EntityStatsUpdate)u).entityStatUpdates.get(mana)).filter(Objects::nonNull)
                    .flatMap(Arrays::stream).filter(u->u.op==EntityStatOp.Set&&!u.predictable&&u.value==(float)(before-28)).count();
            if(debits!=1)throw new IllegalStateException("NATIVE_MANA_WRONG_VIEWER_DEBIT:"+debits);
            if(stats.getSelfUpdates().values().stream().anyMatch(v->!v.isEmpty()))throw new IllegalStateException("NATIVE_MANA_NOT_CLEARED");
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log(
                "RPG_MANA_REPLICATION_NATIVE result=PASS execution=%s changes=%s tracker=%s clear=%s debit=28 oneCharge=true nativeViewerQueued=true connectedProof=false",
                execution,changes,tracker,clear);
        }finally{if(ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);}
    }
}
