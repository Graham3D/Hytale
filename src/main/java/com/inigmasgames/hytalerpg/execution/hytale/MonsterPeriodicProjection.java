package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Transient source marker; the shared periodic owner holds packages, timing, caps and native submission. */
public final class MonsterPeriodicProjection implements Component<EntityStore> {
    private final com.inigmasgames.hytalerpg.combat.damage.MonsterAffixSource binding;
    public MonsterPeriodicProjection(){binding=null;}
    public MonsterPeriodicProjection(com.inigmasgames.hytalerpg.combat.damage.MonsterAffixSource binding){this.binding=java.util.Objects.requireNonNull(binding);}
    private static ComponentType<EntityStore,MonsterPeriodicProjection> type;
    public static void bind(ComponentType<EntityStore,MonsterPeriodicProjection> registered){type=registered;}
    public static ComponentType<EntityStore,MonsterPeriodicProjection> getComponentType(){return type;}
    @Override public MonsterPeriodicProjection clone(){return binding==null?new MonsterPeriodicProjection():new MonsterPeriodicProjection(binding);}
    public static final class Tick extends EntityTickingSystem<EntityStore>{
        private final HytaleSkillExecutionSystem owner;
        public Tick(HytaleSkillExecutionSystem owner){this.owner=owner;}
        @Override public Query<EntityStore> getQuery(){return Query.and(type,UUIDComponent.getComponentType());}
        @Override public boolean isParallel(int size,int tasks){return false;}
        @Override public void tick(float dt,int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
            try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.STATUS_FIELD)){
            var marker=chunk.getComponent(index,type);var id=chunk.getComponent(index,UUIDComponent.getComponentType()).getUuid();
            if(marker.binding==null||!id.equals(marker.binding.nativeActorId())
                    ||!owner.tickMonsterPeriodicOwner(marker.binding,buffer))buffer.removeComponent(chunk.getReferenceTo(index),type);
        
            }}
    }
    public static final class Removal extends RefSystem<EntityStore>{
        private final HytaleSkillExecutionSystem owner;
        public Removal(HytaleSkillExecutionSystem owner){this.owner=owner;}
        @Override public Query<EntityStore> getQuery(){return Query.and(type,UUIDComponent.getComponentType());}
        @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){}
        @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
            var marker=store.getComponent(ref,type);if(marker!=null&&marker.binding!=null)owner.forgetMonsterPeriodicOwner(marker.binding);
        }
    }
}
