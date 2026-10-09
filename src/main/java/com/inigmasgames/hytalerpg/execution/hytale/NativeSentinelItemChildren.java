package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.execution.summon.SummonRegistry;
import com.inigmasgames.hytalerpg.gear.ItemSkillTriggerRuntime;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** World-step queue for bound NPC item children. Holds only frozen facts, never native refs. */
public final class NativeSentinelItemChildren implements ItemSkillTriggerRuntime.Port {
    @FunctionalInterface public interface Execute {
        void run(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> sentinel,
                 Ref<EntityStore> owner,SummonRegistry.Lease lease,ItemSkillTriggerRuntime.Child child);
    }
    private final HytaleSummonSystem summons;
    private final Execute executor;
    private record Pending(ItemSkillTriggerRuntime.Child child,long eligibleTick){}
    private final Map<UUID,ArrayDeque<Pending>> queued=new HashMap<>();
    private final Map<UUID,Long> ticks=new HashMap<>();
    private final Map<UUID,UUID> actorWorld=new HashMap<>();
    public NativeSentinelItemChildren(HytaleSummonSystem summons,Execute executor){
        this.summons=Objects.requireNonNull(summons);this.executor=Objects.requireNonNull(executor);
    }
    @Override public synchronized void enqueue(ItemSkillTriggerRuntime.Child child){
        if(!accepted(child))throw new IllegalArgumentException("SENTINEL_ITEM_CHILD_SOURCE_INVALID");
        actorWorld.put(child.actor(),child.world());
        var queue=queued.computeIfAbsent(child.actor(),ignored->new ArrayDeque<>());
        if(queue.size()>=32)throw new IllegalStateException("SENTINEL_ITEM_CHILD_QUEUE_CAPACITY");
        queue.addLast(new Pending(child,ticks.getOrDefault(child.actor(),0L)+1));
    }
    public boolean accepted(ItemSkillTriggerRuntime.Child child){
        if(child==null)return false;
        var lease=summons.registry().findByEntity(child.actor()).orElse(null);
        return matches(lease,child);
    }
    static boolean matches(SummonRegistry.Lease lease,ItemSkillTriggerRuntime.Child child){
        return lease!=null&&lease.ironSentinel()&&lease.entity()!=null&&lease.entity().equals(child.actor())
                &&lease.world().equals(child.world())&&lease.boundItem().identity().equals(child.item())
                &&lease.boundEffects().items().equals(child.source().items())
                &&lease.boundEffects().revision().equals(child.source().revision())
                &&child.rank()==1&&java.util.Set.of("fire_bolt","frost_bolt","minor_heal").contains(child.skill())
                &&(!child.skill().equals("minor_heal")||child.actor().equals(child.target()));
    }
    public void drain(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> sentinel,
                      Ref<EntityStore> owner,SummonRegistry.Lease lease){
        ArrayDeque<Pending> children=new ArrayDeque<>();
        synchronized(this){
            actorWorld.put(lease.entity(),lease.world());
            long tick=ticks.merge(lease.entity(),1L,Long::sum);
            var pending=queued.get(lease.entity());
            if(pending!=null){while(!pending.isEmpty()&&pending.peekFirst().eligibleTick()<=tick)
                children.addLast(pending.removeFirst());
                if(pending.isEmpty())queued.remove(lease.entity());}
        }
        for(var pending:children)if(matches(lease,pending.child()))try{
            executor.run(store,buffer,sentinel,owner,lease,pending.child());
        }catch(RuntimeException failure){
            summons.emit(lease,com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType.SUMMON_REJECTED,
                    Map.of("phase","SENTINEL_ITEM_CHILD","skill",pending.child().skill(),
                            "boundary",String.valueOf(failure.getMessage()),"nativeActorPreserved",true));
        }
    }
    public synchronized void detach(UUID sentinel){queued.remove(sentinel);ticks.remove(sentinel);actorWorld.remove(sentinel);}
    public synchronized void worldUnload(UUID world){
        for(var actor:actorWorld.entrySet().stream().filter(row->row.getValue().equals(world))
                .map(Map.Entry::getKey).toList())detach(actor);
    }
    public synchronized java.util.List<UUID> actorsInWorld(UUID world){
        return actorWorld.entrySet().stream().filter(row->row.getValue().equals(world))
                .map(Map.Entry::getKey).toList();
    }
    public synchronized int pending(UUID sentinel){return queued.getOrDefault(sentinel,new ArrayDeque<>()).size();}
    synchronized boolean tracked(UUID sentinel){return actorWorld.containsKey(sentinel)||ticks.containsKey(sentinel);}
}
