package com.inigmasgames.hytalerpg.execution.hytale;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
/** Real native NPC DeathComponent boundary; despawn/logout is not a status-spreading death. */
public final class HytaleStatusDeathSystem extends DeathSystems.OnDeathSystem {
    private final HytaleSkillExecutionSystem skills;
    public HytaleStatusDeathSystem(HytaleSkillExecutionSystem skills){this.skills=skills;}
    @Override public Query<EntityStore> getQuery(){return Query.and(NPCEntity.getComponentType(),UUIDComponent.getComponentType(),TransformComponent.getComponentType(),EntityStatMap.getComponentType());}
    @Override public void onComponentAdded(Ref<EntityStore> ref,DeathComponent death,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.STATUS_FIELD)){
        if(death.getDeathInfo()==null||store.getComponent(ref,PlayerRef.getComponentType())!=null)return;
        var hp=store.getComponent(ref,EntityStatMap.getComponentType()).get(DefaultEntityStatTypes.getHealth());
        if(hp==null||hp.get()>hp.getMin())return;
        skills.nativeStatusDeath(store,buffer,ref);

        }
    }
    public static final class Removal extends RefSystem<EntityStore>{
        private final HytaleSkillExecutionSystem skills;
        public Removal(HytaleSkillExecutionSystem skills){this.skills=skills;}
        @Override public Query<EntityStore> getQuery(){return UUIDComponent.getComponentType();}
        @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){}
        @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.STATUS_FIELD)){skills.forgetStatusVictim(store.getComponent(ref,UUIDComponent.getComponentType()).getUuid());
        }
    }
    }
}
