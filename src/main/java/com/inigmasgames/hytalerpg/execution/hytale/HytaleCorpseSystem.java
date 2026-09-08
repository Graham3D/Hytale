package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.summon.*;
import java.util.*;

/** Observes real DeathComponent creation; never spawns a fake corpse or delays native loot/removal. */
public final class HytaleCorpseSystem extends DeathSystems.OnDeathSystem {
    private final CorpseLedger ledger;
    private final CorpseSourceProfiles profiles=CorpseSourceProfiles.load();
    private final HytaleBossBarTracker bosses;
    public HytaleCorpseSystem(CorpseLedger ledger,HytaleBossBarTracker bosses){this.ledger=ledger;this.bosses=bosses;}
    @Override public Query<EntityStore> getQuery(){return Query.and(NPCEntity.getComponentType(),UUIDComponent.getComponentType(),TransformComponent.getComponentType(),EntityStatMap.getComponentType());}
    @Override public void onComponentAdded(Ref<EntityStore> ref,DeathComponent death,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        var npc=store.getComponent(ref,NPCEntity.getComponentType());var profile=profiles.find(npc.getRoleName()).orElse(null);
        if(profile==null||npc.getRole()==null||npc.isReserved()||store.getComponent(ref,SummonProjection.getComponentType())!=null
                ||store.getComponent(ref,PlayerRef.getComponentType())!=null
                ||store.getComponent(ref,EntityStore.REGISTRY.getNonSerializedComponentType())!=null)return;
        var stats=store.getComponent(ref,EntityStatMap.getComponentType());var hp=stats.get(DefaultEntityStatTypes.getHealth());
        if(hp==null||hp.getMax()<=0)return;
        UUID world=store.getExternalData().getWorld().getWorldConfig().getUuid();
        var network=store.getComponent(ref,NetworkId.getComponentType());
        if(network!=null&&bosses.isBoss(world,network.getId()))return;
        var source=new CorpseLedger.Source(store.getComponent(ref,UUIDComponent.getComponentType()).getUuid(),world,
                position(store,ref),npc.getRoleName(),profile.projectionRole(),profile.rank(),hp.getMax(),profile.basePower(),profile.attackInterval(),
                false,false,npc.getRole().isInvulnerable()||store.getComponent(ref,Invulnerable.getComponentType())!=null,false);
        boolean accepted;
        try{accepted=ledger.observe(source);}catch(java.io.UncheckedIOException failure){
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log("RPG_CORPSE_OBSERVATION_REJECTED entity=%s boundary=RECEIPT_IO_FAILED corpseUsable=false",source.entity());return;
        }
        if(accepted)com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log(
                "RPG_NATIVE_CORPSE_OBSERVED entity=%s world=%s role=%s sourceMaxHealth=%s basePower=%s sourceInterval=%s nativeDeath=true rewardCreated=false",
                source.entity(),world,source.role(),source.maximumHealth(),source.basePower(),source.attackInterval());
    }
    @Override public void onComponentRemoved(Ref<EntityStore> ref,DeathComponent death,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        var id=store.getComponent(ref,UUIDComponent.getComponentType());if(id!=null)ledger.remove(id.getUuid());
    }
    static boolean valid(Store<EntityStore> store,Ref<EntityStore> owner,CorpseLedger.Source source){
        var body=store.getExternalData().getRefFromUUID(source.entity());
        if(body==null||!body.isValid()||store.getComponent(body,DeathComponent.getComponentType())==null)return false;
        return HytaleAreaQueries.hostile(store,body,owner)&&store.getComponent(body,SummonProjection.getComponentType())==null;
    }
    private static Vec3 position(Store<EntityStore> store,Ref<EntityStore> ref){var p=store.getComponent(ref,TransformComponent.getComponentType()).getPosition();return new Vec3(p.x(),p.y(),p.z());}
    public static final class Removal extends com.hypixel.hytale.component.system.RefSystem<EntityStore>{
        private final CorpseLedger ledger;
        public Removal(CorpseLedger ledger){this.ledger=ledger;}
        @Override public Query<EntityStore> getQuery(){return Query.and(NPCEntity.getComponentType(),UUIDComponent.getComponentType());}
        @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){}
        @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
            ledger.remove(store.getComponent(ref,UUIDComponent.getComponentType()).getUuid());
        }
    }
}
