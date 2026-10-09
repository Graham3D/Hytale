package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import com.inigmasgames.hytalerpg.diagnostics.MonsterSpawnTrace;
import java.util.*;

/** One native primary-name writer. Registration is independent of combat/reward admission. */
public final class NativeHostileNames {
    private static final int BUDGET=128, MAX_TRACKED=32768;
    private record Key(UUID world,UUID actor) {}
    private record Entry(Key key,State generation) {}
    static final class State {
        private static final java.util.concurrent.atomic.AtomicLong SEQUENCE=new java.util.concurrent.atomic.AtomicLong();
        final long generation=SEQUENCE.incrementAndGet();
        boolean ready;
        String role,owned,personal;
        long next;
        String choose(String role,String current,String base,String projected){
            boolean wasOwned=Objects.equals(current,owned);
            if(this.role!=null&&!Objects.equals(this.role,role)){owned=null;personal=null;}
            this.role=role;
            if(current!=null&&!current.isBlank()&&!wasOwned)personal=current;
            return personal!=null?personal:projected!=null&&!projected.isBlank()?projected:base;
        }
    }
    private final HytaleDifficultyCombat combat;
    private final Map<Key,State> tracked=new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<UUID> initialized=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<UUID,ArrayDeque<Entry>> queues=new java.util.concurrent.ConcurrentHashMap<>();
    public NativeHostileNames(HytaleDifficultyCombat combat){this.combat=combat;}
    private static UUID world(Store<EntityStore> store){return store.getExternalData().getWorld().getWorldConfig().getUuid();}
    public void request(Store<EntityStore> store,Ref<EntityStore> ref){
        var id=store.getComponent(ref,UUIDComponent.getComponentType());if(id==null)return;
        var key=new Key(world(store),id.getUuid());
        var state=tracked.get(key);
        if(state==null){
            if(tracked.size()>=MAX_TRACKED)return;
            state=new State();tracked.put(key,state);queues.computeIfAbsent(key.world(),ignored->new ArrayDeque<>()).add(new Entry(key,state));
        }
        state.next=0;
    }
    public void forget(UUID world,UUID actor){var key=new Key(world,actor);var old=tracked.remove(key);if(old!=null)event(key,"CLEARED_UNTRACKED",old.role);}
    public void forgetWorld(UUID world){tracked.keySet().removeIf(k->k.world().equals(world));queues.remove(world);initialized.remove(world);}
    public void close(){tracked.clear();queues.clear();initialized.clear();}
    public Map<String,Object> inspect(UUID world,UUID actor){
        var state=tracked.get(new Key(world,actor));
        return state==null?Map.of("nameOwner","PENDING","clientRendering","UNKNOWN"):
                Map.of("nameOwner",state.personal==null?"HYWIND_NATIVE":"EXTERNAL_AUTHORED","lastChosenText",Objects.toString(state.owned,""),
                        "role",Objects.toString(state.role,""),"clientRendering","UNKNOWN","revision","R244","generation",state.generation);
    }
    private void reconcile(Store<EntityStore> store,Key key,State state){
        if(tracked.get(key)!=state)return; // Exact lifetime token rejects stale deferred work.
        if(store.isProcessing())throw new IllegalStateException("HOSTILE_NAME_WRITE_DURING_PROCESSING");
        var ref=store.getExternalData().getRefFromUUID(key.actor());
        if(ref==null||!ref.isValid()){tracked.remove(key);return;}
        var npc=store.getComponent(ref,NPCEntity.getComponentType());
        var support=store.getComponent(ref,WorldSupport.getComponentType());
        if(npc==null){tracked.remove(key);return;}
        if(npc.getRole()==null||support==null){event(key,"WAITING_NATIVE_READINESS",npc.getRoleName());return;}
        if(store.getComponent(ref,DeathComponent.getComponentType())!=null
                ||store.getComponent(ref,SummonProjection.getComponentType())!=null
                ||store.getComponent(ref,ConversionProjection.getComponentType())!=null
                ||support.getDefaultPlayerAttitude()!=Attitude.HOSTILE)return;
        if(!state.ready){state.ready=true;event(key,"READY_ELIGIBLE",npc.getRoleName());}
        var plate=store.getComponent(ref,Nameplate.getComponentType());
        String base=HytaleDifficultyCombat.nativeDisplayName(store,ref,npc);
        var accepted=combat.enemyState(key.world(),key.actor()).filter(e->e.descriptor().nativeRoleId().equals(npc.getRoleName()));
        var display=accepted.isPresent()?combat.enemyDisplay(key.world(),key.actor()).orElse(null):null;
        var snapshot=combat.snapshot(key.world(),key.actor()).filter(s->s.roleId().equals(npc.getRoleName())).orElse(null);
        String projected=display==null?(snapshot==null?null:EnemyNameplateText.format(base,snapshot.level())):
                display.packRoleLabel().equals("Minion")?display.baseRoleDisplayName():display.name();
        String current=plate==null?null:plate.getText();
        var authored=store.getComponent(ref,PersistentDisplayName.getComponentType());
        String personal=authored==null||authored.getDisplayName()==null?null:authored.getDisplayName().getRawText();
        // Legacy Hywind text is a projection, not a new authored/personal name.
        if(state.owned==null&&current!=null&&(current.equals(projected)||current.equals(base)||current.startsWith(base+"  Lv.")))state.owned=current;
        String previousPersonal=state.personal;
        String text=state.choose(npc.getRoleName(),current,base,projected);
        if(personal!=null&&!personal.isBlank()){state.personal=personal;text=personal;}
        if(!Objects.equals(previousPersonal,state.personal))event(key,"SUPERSEDED_EXTERNAL",npc.getRoleName());
        if(text==null||text.isBlank()){event(key,"MISSING_BASE",npc.getRoleName());return;}
        if(!text.equals(current)){
            write(store,ref,Nameplate.getComponentType(),text);
            event(key,current==null||current.isBlank()?"NAMED":"UPDATED",npc.getRoleName());
        }
        state.owned=text;
    }
    static void write(Store<EntityStore> store,Ref<EntityStore> actor,ComponentType<EntityStore,Nameplate> type,String text){
        if(store.isProcessing())throw new IllegalStateException("HOSTILE_NAME_WRITE_DURING_PROCESSING");
        var plate=store.getComponent(actor,type);
        if(plate==null)store.addComponent(actor,type,new Nameplate(text));else if(!text.equals(plate.getText()))plate.setText(text);
    }
    private static void event(Key key,String outcome,String role){
        MonsterSpawnTrace.event("HOSTILE_NAME",key.world(),-1,Objects.toString(role,"unknown"),
                "entity="+key.actor()+" outcome="+outcome+" owner=NATIVE_COMPOSITOR revision=R244 clientRendering=UNKNOWN");
    }
    public final class Tracking extends RefSystem<EntityStore> {
        @Override public Query<EntityStore> getQuery(){return Query.and(NPCEntity.getComponentType(),UUIDComponent.getComponentType(),TransformComponent.getComponentType());}
        @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
            // No structural write during the add callback. The queue retries later readiness.
            request(store,ref);
        }
        @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
            var id=store.getComponent(ref,UUIDComponent.getComponentType());if(id!=null)forget(world(store),id.getUuid());
        }
    }
    /** RefSystem is an entity-add hook, not a component-readiness hook in this SDK. */
    public final class Readiness extends RefChangeSystem<EntityStore,WorldSupport> {
        @Override public Query<EntityStore> getQuery(){return Query.and(NPCEntity.getComponentType(),UUIDComponent.getComponentType());}
        @Override public ComponentType<EntityStore,WorldSupport> componentType(){return WorldSupport.getComponentType();}
        @Override public void onComponentAdded(Ref<EntityStore> r,WorldSupport v,Store<EntityStore> s,CommandBuffer<EntityStore> b){request(s,r);}
        @Override public void onComponentSet(Ref<EntityStore> r,WorldSupport old,WorldSupport v,Store<EntityStore> s,CommandBuffer<EntityStore> b){request(s,r);}
        @Override public void onComponentRemoved(Ref<EntityStore> r,WorldSupport v,Store<EntityStore> s,CommandBuffer<EntityStore> b){request(s,r);}
    }
    public final class Tick extends TickingSystem<EntityStore> {
        @Override public void tick(float dt,int index,Store<EntityStore> store){
            try(var metrics=NativeRpgTickMetrics.enter(store,NativeRpgTickMetrics.Phase.HUD)){
            if(initialized.add(world(store))){
                store.forEachChunk(Query.and(NPCEntity.getComponentType(),UUIDComponent.getComponentType(),TransformComponent.getComponentType()),
                        (chunk,buffer)->{for(int i=0;i<chunk.size()&&tracked.size()<MAX_TRACKED;i++)request(store,chunk.getReferenceTo(i));});
            }
            var queue=queues.get(world(store));if(queue==null)return;
            long now=System.nanoTime();int remaining=Math.min(BUDGET,queue.size());
            var ready=new ArrayList<Entry>();
            while(remaining-->0){
                var entry=queue.removeFirst();var key=entry.key();var state=entry.generation();if(tracked.get(key)!=state)continue;
                queue.addLast(entry);if(now<state.next)continue;state.next=now+500_000_000L;
                ready.add(entry);
            }
            if(ready.isEmpty())return;
            var worldId=world(store);
            // One coalesced batch of IDs + lifetime tokens, never retained Store/Ref objects.
            store.getExternalData().getWorld().execute(()->{
                var current=com.hypixel.hytale.server.core.universe.Universe.get().getWorld(worldId);
                if(current==null){forgetWorld(worldId);return;}
                var live=current.getEntityStore().getStore();
                try(var deferredMetrics=NativeRpgTickMetrics.enter(live,NativeRpgTickMetrics.Phase.HUD)){
                for(var entry:ready)try{reconcile(live,entry.key(),entry.generation());}
                catch(RuntimeException e){event(entry.key(),"RETRY_"+e.getClass().getSimpleName(),entry.generation().role);}
                }
            });
            }
        }
    }
}
