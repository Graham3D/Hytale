package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.blackboard.Blackboard;
import com.hypixel.hytale.server.npc.blackboard.view.attitude.AttitudeView;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import com.inigmasgames.hytalerpg.execution.summon.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Explicit, finite native attract requests. Does not mutate allegiance, AI roles or override memory.
 * A provider returns null after termination; Hytale's normal attitude cache clears every .1s.
 * Native higher-priority explicit overrides (priority 0) remain authoritative. */
final class DecoyNativeAttraction {
    private record Key(UUID world,UUID target){}
    private record Request(UUID lease,UUID decoy,UUID previous){}
    private final Map<Key,Request> requests=new ConcurrentHashMap<>();
    private final Map<AttitudeView,Boolean> installed=Collections.synchronizedMap(new WeakHashMap<>());
    private final SummonRegistry registry;
    DecoyNativeAttraction(SummonRegistry registry){this.registry=registry;}

    boolean request(Store<EntityStore> store,Ref<EntityStore> owner,Ref<EntityStore> decoy,Ref<EntityStore> target,SummonRegistry.Lease lease){
        var npc=store.getComponent(target,NPCEntity.getComponentType());var marked=store.getComponent(target,MarkedEntitySupport.getComponentType());
        if(npc==null||marked==null)return false;
        var previous=marked.getMarkedEntityRef(MarkedEntitySupport.DEFAULT_TARGET_SLOT);
        boolean protectedOrOwned=npc.isReserved()||npc.getRole().isInvulnerable()||store.getComponent(target,SummonProjection.getComponentType())!=null;
        if(!DecoyAttractionPolicy.accepts(npc.getRoleName(),owner.equals(previous),protectedOrOwned,HytaleAreaQueries.hostile(store,target,owner),true))return false;
        var key=new Key(lease.world(),store.getComponent(target,UUIDComponent.getComponentType()).getUuid());
        synchronized(requests){
            if(requests.containsKey(key)||requests.size()>=1024)return false;
            install(store,owner);
            requests.put(key,new Request(lease.token(),lease.entity(),lease.owner()));
        }
        marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT,decoy);
        return true;
    }
    private void install(Store<EntityStore> store,Ref<EntityStore> owner){
        var view=store.getResource(Blackboard.getResourceType()).getView(AttitudeView.class,owner,store);
        synchronized(installed){if(installed.containsKey(view))return;
            view.registerProvider(10,(npc,role,target,accessor)->{
                var a=accessor.getComponent(npc,UUIDComponent.getComponentType());var b=accessor.getComponent(target,UUIDComponent.getComponentType());
                if(a==null||b==null)return null;
                var r=requests.get(new Key(accessor.getExternalData().getWorld().getWorldConfig().getUuid(),a.getUuid()));
                if(r==null||!r.decoy().equals(b.getUuid()))return null;
                var lease=registry.find(r.lease()).orElse(null);
                return lease!=null&&System.nanoTime()/1e9<lease.expires()?Attitude.HOSTILE:null;
            });
            installed.put(view,true);
        }
    }
    void release(Store<EntityStore> store,UUID token){
        for(var entry:requests.entrySet()){
            if(!entry.getValue().lease().equals(token)||!requests.remove(entry.getKey(),entry.getValue()))continue;
            var ref=store.getExternalData().getRefFromUUID(entry.getKey().target());
            if(ref==null||!ref.isValid())continue;
            var marked=store.getComponent(ref,MarkedEntitySupport.getComponentType());if(marked==null)continue;
            var current=marked.getMarkedEntityRef(MarkedEntitySupport.DEFAULT_TARGET_SLOT);
            var id=current==null||!current.isValid()?null:store.getComponent(current,UUIDComponent.getComponentType());
            // Never overwrite a newer encounter/taunt owner.
            if(id!=null&&entry.getValue().decoy().equals(id.getUuid()))
                marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT,store.getExternalData().getRefFromUUID(entry.getValue().previous()));
        }
    }
}
