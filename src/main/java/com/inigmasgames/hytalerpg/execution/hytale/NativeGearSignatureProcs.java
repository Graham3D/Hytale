package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.hytale.*;
import com.inigmasgames.hytalerpg.execution.connection.ConnectionShape;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.gear.*;
import java.util.*;

/** Native destination for authenticated signature children. Enqueue only after ApplyDamage;
 * a later world tick submits every child through the installed native Gather path. */
public final class NativeGearSignatureProcs {
    public interface Execution {
        /** Must use the existing protected execute/death owner; rejection is a normal outcome. */
        boolean request(GearSignatureProcRuntime.Contact contact,Store<EntityStore> store,Ref<EntityStore> source);
    }
    private static final int MAX_PENDING=4096;
    private record Queued(GearSignatureProcRuntime.Child child,long readyTick) {}
    private final Execution execution;
    private final Map<UUID,ArrayDeque<Queued>> pending=new HashMap<>();
    private final Map<UUID,Long> worldEpoch=new HashMap<>();
    private final Map<UUID,Long> lastDispatchTick=new HashMap<>();
    public NativeGearSignatureProcs(Execution execution){this.execution=Objects.requireNonNull(execution);}
    public NativeGearSignatureProcs(java.util.function.Function<GearSignatureProcRuntime.Contact,Boolean> execution){
        this((contact,store,source)->execution.apply(contact));
    }
    public synchronized void enqueue(GearSignatureProcRuntime.Child child,long acceptedTick){
        if(acceptedTick<0||acceptedTick==Long.MAX_VALUE)throw new IllegalArgumentException("INVALID_SIGNATURE_TICK");
        var queue=pending.computeIfAbsent(child.world(),ignored->new ArrayDeque<>());
        if(queue.size()>=MAX_PENDING)throw new IllegalStateException("SIGNATURE_CHILD_QUEUE_CAPACITY");
        queue.addLast(new Queued(child,acceptedTick+1));
    }
    public boolean execute(GearSignatureProcRuntime.Contact contact,Store<EntityStore> store,Ref<EntityStore> source){
        return execution.request(contact,store,source);
    }
    /** A receipt-scoped Port binds the real source, world and LOS query to this destination. */
    public GearSignatureProcRuntime.Port at(Store<EntityStore> store,Ref<EntityStore> source){
        return new GearSignatureProcRuntime.Port(){
            @Override public void enqueue(GearSignatureProcRuntime.Child child){
                NativeGearSignatureProcs.this.enqueue(child,store.getExternalData().getWorld().getTick());}
            @Override public boolean execute(GearSignatureProcRuntime.Contact contact){
                return NativeGearSignatureProcs.this.execute(contact,store,source);}
            @Override public List<UUID> burstTargets(GearSignatureProcRuntime.Contact contact,double radius,int maximum){
                var target=store.getExternalData().getRefFromUUID(contact.target());
                if(target==null||!target.isValid()||source==null||!source.isValid())return List.of();
                var transform=store.getComponent(target,TransformComponent.getComponentType());
                if(transform==null)return List.of();
                var position=transform.getPosition();
                var center=new Vec3(position.x,position.y+1,position.z);
                var result=HytaleAreaQueries.query(store,source,
                        bounds->ConnectionShape.pointDistanceSquared(center,bounds)<=radius*radius+1e-9,maximum);
                if(result.overflow())throw new IllegalStateException("SIGNATURE_BURST_QUERY_CAPACITY");
                return result.candidates().stream().filter(candidate->HytaleAreaQueries.clear(store,center,candidate.bounds().centre()))
                        .map(candidate->store.getComponent(candidate.ref(),UUIDComponent.getComponentType()))
                        .filter(Objects::nonNull).map(UUIDComponent::getUuid).distinct().sorted().toList();
            }
        };
    }
    public synchronized int pending(UUID world){var queue=pending.get(world);return queue==null?0:queue.size();}
    public synchronized void clearActor(UUID actor){
        pending.values().forEach(queue->queue.removeIf(row->row.child().owner().equals(actor)
                ||row.child().target().equals(actor)));
        pending.entrySet().removeIf(row->row.getValue().isEmpty());
    }
    public synchronized void clearWorld(UUID world){pending.remove(world);lastDispatchTick.remove(world);worldEpoch.merge(world,1L,Long::sum);}
    public interface NativeSubmit {
        boolean eligible(GearSignatureProcRuntime.Child child);
        Damage.Source source(GearSignatureProcRuntime.Child child);
        double targetHealthBefore(GearSignatureProcRuntime.Child child);
        void submit(GearSignatureProcRuntime.Child child,GearCombatEffects.Channel channel,Damage damage);
    }
    /** Drains one accepted generation through real native Damage construction. */
    public int drain(UUID world,long currentTick,NativeSubmit sink){
        if(currentTick<0)throw new IllegalArgumentException("INVALID_SIGNATURE_TICK");
        var batch=new ArrayList<GearSignatureProcRuntime.Child>();
        long epoch;
        synchronized(this){var queue=pending.get(world);if(queue==null||queue.isEmpty())return 0;
            epoch=worldEpoch.getOrDefault(world,0L);
            int count=queue.size();for(int i=0;i<count;i++){
                var row=queue.removeFirst();if(row.readyTick()<=currentTick)batch.add(row.child());else queue.addLast(row);
            }}
        int sent=0;
        for(var child:batch){
            synchronized(this){if(worldEpoch.getOrDefault(world,0L)!=epoch)break;}
            if(!child.world().equals(world))continue;
            for(var component:child.channels().entrySet()){
                if(!sink.eligible(child))break;
                sink.submit(child,component.getKey(),nativeDamage(child,component.getKey(),sink.source(child),sink.targetHealthBefore(child)));sent++;
            }
        }
        return sent;
    }
    /** Exact native envelope used by Dispatch; also allows deterministic adapter assertions. */
    public static Damage nativeDamage(GearSignatureProcRuntime.Child child,GearCombatEffects.Channel channel,
                                      Damage.Source source,double targetHealthBefore){
        double amount=child.channels().getOrDefault(channel,0d);
        if(!Double.isFinite(amount)||amount<=0||amount>Float.MAX_VALUE||!Double.isFinite(targetHealthBefore)||targetHealthBefore<0)
            throw new IllegalArgumentException("INVALID_SIGNATURE_COMPONENT");
        var cause=DamageCause.getAssetMap().getAsset(GearCombatEffects.nativeCause(channel));
        if(cause==null)throw new IllegalStateException("SIGNATURE_NATIVE_CAUSE_MISSING:"+channel);
        var damage=new Damage(source,cause,(float)amount);
        var meta=new HytaleDamageMetadata(child.owner(),child.root(),"signature/"+child.kind(),
                child.root()+"/"+child.kind()+"/"+child.event()+"/"+child.target()+"/"+channel,amount,targetHealthBefore,
                "signature/"+child.kind(),false,child.kind()==GearSignatureProcRuntime.ChildKind.RETRIBUTION
                ?HytaleDamageMetadata.Origin.REFLECTED:HytaleDamageMetadata.Origin.TRIGGERED);
        damage.putMetaObject(HytaleDamageAdapter.RPG_METADATA,new com.google.gson.Gson().toJson(meta));
        return damage;
    }
    /** Register once; the first UUID entity tick for a world drains its bounded queue. */
    public final class Dispatch extends EntityTickingSystem<EntityStore> {
        @Override public Query<EntityStore> getQuery(){return UUIDComponent.getComponentType();}
        @Override public void tick(float dt,int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
            var world=store.getExternalData().getWorld().getWorldConfig().getUuid();
            long tick=store.getExternalData().getWorld().getTick();
            synchronized(NativeGearSignatureProcs.this){
                if(lastDispatchTick.getOrDefault(world,-1L)==tick)return;
                lastDispatchTick.put(world,tick);
            }
            try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(
                    store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.DAMAGE)){
            drain(world,tick,new NativeSubmit(){
                @Override public boolean eligible(GearSignatureProcRuntime.Child child){
                    var source=store.getExternalData().getRefFromUUID(child.owner());
                    var target=store.getExternalData().getRefFromUUID(child.target());
                    if(source==null||target==null||!source.isValid()||!target.isValid()
                            ||!HytaleAreaQueries.hostile(store,target,source))return false;
                    var stats=store.getComponent(target,EntityStatMap.getComponentType());
                    var health=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
                    return health!=null&&health.get()>0;
                }
                @Override public Damage.Source source(GearSignatureProcRuntime.Child child){
                    return new Damage.EntitySource(store.getExternalData().getRefFromUUID(child.owner()));
                }
                @Override public double targetHealthBefore(GearSignatureProcRuntime.Child child){
                    var target=store.getExternalData().getRefFromUUID(child.target());
                    var stats=store.getComponent(target,EntityStatMap.getComponentType());
                    var health=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
                    if(health==null)throw new IllegalStateException("SIGNATURE_TARGET_HEALTH_MISSING");
                    return health.get();
                }
                @Override public void submit(GearSignatureProcRuntime.Child child,GearCombatEffects.Channel channel,Damage damage){
                    var target=store.getExternalData().getRefFromUUID(child.target());
                    DamageSystems.executeDamage(target,buffer,damage);
                }
            });
            }
        }
    }
}
