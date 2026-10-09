package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.EmptyItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageLifecycleSystems;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata;
import com.inigmasgames.hytalerpg.gear.GearCombatEffects;
import com.inigmasgames.hytalerpg.gear.GearNativeItems;
import com.inigmasgames.hytalerpg.gear.GearSignatureProcRuntime;
import com.inigmasgames.hytalerpg.gear.NativeGearAttackAcceptance;
import com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry;
import com.inigmasgames.hytalerpg.progress.ProgressionMath;
import java.util.*;

/** Production bindings for signed native strike facts, receipt aggregation and queued children. */
public final class GearSignatureBindings implements AutoCloseable,NativeGearSignatureReceipt.Facts,
        NativeGearSignatureProcs.Execution,NativeGearSignatureDefenseFilter.DefenseView,
        HytaleEncounterRewards.SignatureKillCallback {
    private record Key(UUID world,UUID owner,UUID target,String root,String contact) {}
    static final class Aggregate {
        final NativeGearAttackAcceptance.Accepted accepted;
        final EnumSet<GearCombatEffects.Channel> observed=EnumSet.noneOf(GearCombatEffects.Channel.class);
        double loss,admitted,health,itemProcCredit;
        boolean blocked=true;
        long expires;
        Aggregate(NativeGearAttackAcceptance.Accepted accepted,long expires){this.accepted=accepted;this.expires=expires;}
        boolean observe(GearCombatEffects.Channel channel,double healthLoss,double acceptedDamage,
                        double healthAfter,boolean cancelled,boolean blocked,int expected){
            if(!observed.add(channel))throw new IllegalStateException("SIGNATURE_CONTACT_DUPLICATE_CHANNEL");
            if(!cancelled&&!blocked){
                loss+=healthLoss;admitted+=acceptedDamage;this.blocked=false;
            }
            health=healthAfter;
            return observed.size()==expected;
        }
    }
    private record Lethal(GearSignatureProcRuntime.Contact contact,List<UUID> nearby,long expires) {}
    private final GearSignatureProcRuntime runtime;
    private final NativeGearSignatureProcs children;
    private final NativeGearSignatureReceipt receipt;
    private final NativeGearSignatureDefenseFilter defenseFilter;
    private final HytaleDifficultyCombat encounters;
    private final HytaleSummonSystem summons;
    private final EnemyRewardRegistry roles=EnemyRewardRegistry.load();
    private final Map<Key,Aggregate> contacts=new HashMap<>();
    private final Map<Key,Aggregate> completedIncoming=new HashMap<>();
    private final Map<String,Lethal> lethal=new HashMap<>();
    private static final int MAX_CONTACTS=4096;
    private static final long LIFETIME=60_000_000_000L;

    public GearSignatureBindings(HytaleDifficultyCombat encounters,HytaleSummonSystem summons){
        this.encounters=Objects.requireNonNull(encounters);this.summons=Objects.requireNonNull(summons);
        runtime=new GearSignatureProcRuntime(GearSignatureBindings::roll);
        NativeGearAttackAcceptance.bind(runtime);
        children=new NativeGearSignatureProcs(this);
        receipt=new NativeGearSignatureReceipt(runtime,children,this);
        defenseFilter=new NativeGearSignatureDefenseFilter(runtime,this);
    }
    public GearSignatureProcRuntime runtime(){return runtime;}
    public NativeGearSignatureProcs children(){return children;}
    public NativeGearSignatureReceipt receipt(){return receipt;}
    public NativeGearSignatureDefenseFilter defenseFilter(){return defenseFilter;}
    public NativeGearSignatureProcs.Dispatch dispatch(){return children.new Dispatch();}
    public NativeGearSignatureReceipt.RawBefore rawBefore(){return receipt.new RawBefore();}
    public NativeGearSignatureReceipt.RawAfter rawAfter(){return receipt.new RawAfter();}
    /** Native removal owns source/target cleanup; a target's lethal fact survives for durable reward credit. */
    public final class Removal extends RefSystem<EntityStore> {
        @Override public Query<EntityStore> getQuery(){return UUIDComponent.getComponentType();}
        @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,
                                            CommandBuffer<EntityStore> buffer){}
        @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,
                                             CommandBuffer<EntityStore> buffer){
            var identity=store.getComponent(ref,UUIDComponent.getComponentType());
            if(identity!=null)clearActor(identity.getUuid());
        }
    }
    public final class Death extends DeathSystems.OnDeathSystem {
        @Override public Query<EntityStore> getQuery(){return UUIDComponent.getComponentType();}
        @Override public void onComponentAdded(Ref<EntityStore> ref,DeathComponent death,Store<EntityStore> store,
                                                CommandBuffer<EntityStore> buffer){
            var identity=store.getComponent(ref,UUIDComponent.getComponentType());
            if(identity!=null)clearActor(identity.getUuid());
        }
    }
    @Override public synchronized void close(){
        for(var world:contacts.keySet().stream().map(Key::world).distinct().toList())clearWorld(world);
        NativeGearAttackAcceptance.unbind(runtime);
        lethal.clear();
    }
    private static UUID world(Store<EntityStore> store){return store.getExternalData().getWorld().getWorldConfig().getUuid();}
    private static UUID id(Ref<EntityStore> ref,CommandBuffer<EntityStore> buffer){
        if(ref==null||!ref.isValid())return null;
        var identity=buffer.getComponent(ref,UUIDComponent.getComponentType());
        return identity==null?null:identity.getUuid();
    }
    private static double roll(String purpose){
        long hash=0xcbf29ce484222325L;
        for(int i=0;i<purpose.length();i++){hash^=purpose.charAt(i);hash*=0x100000001b3L;}
        var random=new SplittableRandom(hash);
        return random.nextDouble();
    }
    private static String deathKey(UUID world,UUID target){return world+"/"+target;}
    private static void prune(Map<Key,Aggregate> map,long now){map.entrySet().removeIf(e->e.getValue().expires<now);}
    public synchronized void clearActor(UUID actor){
        runtime.clearActor(actor);children.clearActor(actor);NativeGearAttackAcceptance.clearActor(actor);
        contacts.keySet().removeIf(k->k.owner().equals(actor)||k.target().equals(actor));
        completedIncoming.keySet().removeIf(k->k.owner().equals(actor)||k.target().equals(actor));
        lethal.entrySet().removeIf(e->e.getValue().contact().owner().equals(actor));
    }
    public synchronized void clearWorld(UUID world){
        runtime.clearWorld(world);children.clearWorld(world);NativeGearAttackAcceptance.clearWorld(world);
        contacts.keySet().removeIf(k->k.world().equals(world));
        completedIncoming.keySet().removeIf(k->k.world().equals(world));
        lethal.entrySet().removeIf(e->e.getValue().contact().world().equals(world));
    }
    private GearSignatureProcRuntime.Kind kind(UUID world,UUID target,Ref<EntityStore> ref,CommandBuffer<EntityStore> buffer){
        if(buffer.getComponent(ref,PlayerRef.getComponentType())!=null)return GearSignatureProcRuntime.Kind.PLAYER;
        var spawn=encounters.snapshot(world,target);
        var rank=spawn.map(EnemyRewardRegistry.Spawn::rank).orElseGet(()->{
            var npc=buffer.getComponent(ref,NPCEntity.getComponentType());
            return npc==null?null:roles.resolveRole(npc.getRoleName()).map(r->r.canonical().rank()).orElse(null);
        });
        if(rank==null)return GearSignatureProcRuntime.Kind.BOSS; // unknown rank fails closed for restricted procs
        return switch(rank){
            case BOSS->GearSignatureProcRuntime.Kind.BOSS;
            case ELITE,MINIBOSS->GearSignatureProcRuntime.Kind.ELITE;
            case COMMON,SPECIALIST->GearSignatureProcRuntime.Kind.COMMON;
        };
    }
    private static boolean directMelee(Damage damage){
        if(damage==null||damage.getCause()==null||damage.getCause()!=DamageCause.PHYSICAL
                ||!(damage.getSource() instanceof Damage.EntitySource))return false;
        var type=damage.getIfPresentMetaObject(Damage.INTERACTION_TYPE);
        return type==InteractionType.Primary||type==InteractionType.Secondary;
    }
    private boolean sourceAuthorized(HytaleDamageLifecycleSystems.AppliedReceipt r,Ref<EntityStore> source,
                                     CommandBuffer<EntityStore> buffer,NativeGearAttackAcceptance.Accepted accepted){
        var meta=r.metadata();
        // canProc marks the first authored channel, not the complete accepted contact.
        if(meta.origin()!=HytaleDamageMetadata.Origin.DIRECT
                ||!accepted.actor().equals(meta.actorId())||!accepted.hit().rootId().equals(meta.rootCastId()))return false;
        if(buffer.getComponent(source,PlayerRef.getComponentType())!=null)return true;
        var projection=buffer.getComponent(source,SummonProjection.getComponentType());
        if(projection==null)return false;
        var lease=summons.registry().find(projection.token).orElse(null);
        return HytaleSummonSystem.authorizedSentinelAttack(lease,meta,r.gearHit());
    }
    @Override public synchronized Optional<GearSignatureProcRuntime.Contact> outgoing(
            HytaleDamageLifecycleSystems.AppliedReceipt r,Ref<EntityStore> target,Ref<EntityStore> source,
            CommandBuffer<EntityStore> buffer){
        var gear=r.gearHit();if(gear==null||source==null||!source.isValid()||target==null||!target.isValid())return Optional.empty();
        var store=buffer.getStore();UUID world=world(store),actor=id(source,buffer),targetId=id(target,buffer);
        if(actor==null||targetId==null||!actor.equals(r.metadata().actorId()))return Optional.empty();
        var accepted=NativeGearAttackAcceptance.find(world,actor,gear.hit());
        if(accepted==null||!sourceAuthorized(r,source,buffer,accepted))return Optional.empty();
        var context=r.executionContext();
        if(context!=null&&(!context.request().actorId().equals(actor)
                ||!context.rootCastId().equals(gear.hit().rootId())))return Optional.empty();
        var key=new Key(world,actor,targetId,gear.hit().rootId(),gear.contactId());
        long now=System.nanoTime();prune(contacts,now);
        var aggregate=contacts.get(key);
        if(aggregate==null){
            if(contacts.size()>=MAX_CONTACTS)throw new IllegalStateException("SIGNATURE_CONTACT_CAPACITY");
            aggregate=new Aggregate(accepted,now+LIFETIME);contacts.put(key,aggregate);
        }
        if(aggregate.accepted.hit()!=gear.hit())
            throw new IllegalStateException("SIGNATURE_CONTACT_SOURCE_DRIFT");
        int expected=positiveChannels(gear.hit());
        if(!r.cancelled()&&!r.blocked())aggregate.itemProcCredit=Math.max(aggregate.itemProcCredit,r.procCoefficient());
        if(!aggregate.observe(gear.channel(),r.actualHealthLoss(),r.acceptedDamage(),r.after(),
                r.cancelled(),r.blocked(),expected))return Optional.empty();
        contacts.remove(key);
        if(aggregate.blocked||aggregate.admitted<=0||aggregate.loss<=0)return Optional.empty();
        if(accepted.melee()){
            prune(completedIncoming,now);
            if(completedIncoming.size()>=MAX_CONTACTS)throw new IllegalStateException("SIGNATURE_REFLECTION_CONTACT_CAPACITY");
            completedIncoming.put(key,aggregate);
        }
        var hp=buffer.getComponent(target,EntityStatMap.getComponentType());
        var health=hp==null?null:hp.get(DefaultEntityStatTypes.getHealth());
        if(health==null)return Optional.empty();
        boolean hostile=HytaleAreaQueries.hostile(store,target,source);
        var contact=new GearSignatureProcRuntime.Contact(world,actor,gear.hit().itemId(),targetId,
                gear.hit().rootId(),gear.contactId(),gear.hit().snapshot(),true,accepted.melee(),hostile,
                accepted.noProc(),false,false,aggregate.loss,Math.max(0,aggregate.health),
                com.inigmasgames.hytalerpg.combat.hytale.NativeNormalHealthMaximum.value(hp),
                kind(world,targetId,target,buffer),accepted.noncriticalPhysical(),
                gear.hit().amounts(),aggregate.itemProcCredit,!hostile,
                HytaleEncounterRewards.excluded(store,target),false);
        if(contact.targetHealth()==0){
            lethal.entrySet().removeIf(e->e.getValue().expires()<now);
            if(lethal.size()>=MAX_CONTACTS)lethal.remove(lethal.keySet().iterator().next());
            var nearby=contact.snapshot().forItem(contact.item()).value("WA-143")>0
                    ?children.at(store,source).burstTargets(contact,2,64):List.<UUID>of();
            lethal.put(deathKey(world,targetId),new Lethal(contact,List.copyOf(nearby),now+LIFETIME));
        }
        return Optional.of(contact);
    }
    static int positiveChannels(GearCombatEffects.Hit hit){
        return (int)hit.amounts().values().stream().filter(amount->amount!=null&&amount>0).count();
    }
    @Override public synchronized Optional<NativeGearSignatureReceipt.Incoming> incoming(
            HytaleDamageLifecycleSystems.AppliedReceipt r,Ref<EntityStore> target,Ref<EntityStore> source,
            CommandBuffer<EntityStore> buffer){
        var damage=r.nativeDamage();
        if(source==null||!source.isValid())return Optional.empty();
        if(r.gearHit()!=null){
            var gear=r.gearHit();
            var key=new Key(world(buffer.getStore()),r.metadata().actorId(),r.targetId(),
                    gear.hit().rootId(),gear.contactId());
            var completed=completedIncoming.remove(key);
            if(completed==null||!completed.accepted.melee())return Optional.empty();
            return incomingFrom(damage,target,source,buffer,r.metadata().rootCastId(),gear.contactId(),
                    false,completed.blocked,completed.loss);
        }
        if(!directMelee(damage))return Optional.empty();
        var meta=r.metadata();
        return incomingFrom(damage,target,source,buffer,meta.rootCastId(),meta.correlationId(),
                meta.noRetaliation(),r.blocked(),r.actualHealthLoss());
    }
    @Override public Optional<NativeGearSignatureReceipt.Incoming> rawIncoming(
            Damage damage,double before,double after,Ref<EntityStore> target,Ref<EntityStore> source,
            CommandBuffer<EntityStore> buffer){
        if(!directMelee(damage)||before<=after)return Optional.empty();
        String root="native/"+Integer.toHexString(System.identityHashCode(damage));
        return incomingFrom(damage,target,source,buffer,root,root,false,
                Boolean.TRUE.equals(damage.getIfPresentMetaObject(Damage.BLOCKED)),before-after);
    }
    private Optional<NativeGearSignatureReceipt.Incoming> incomingFrom(Damage damage,Ref<EntityStore> target,
            Ref<EntityStore> source,CommandBuffer<EntityStore> buffer,String root,String contact,
            boolean reflected,boolean blocked,double loss){
        if(target==null||source==null||!target.isValid()||!source.isValid()||loss<=0)return Optional.empty();
        var store=buffer.getStore();UUID wearer=id(target,buffer),attacker=id(source,buffer);
        if(wearer==null||attacker==null)return Optional.empty();
        com.inigmasgames.hytalerpg.gear.GearEffectSnapshot equipped;
        boolean hostile;
        if(buffer.getComponent(target,PlayerRef.getComponentType())!=null){
            equipped=GearNativeItems.effects(target,buffer).snapshot();
            hostile=HytaleAreaQueries.hostile(store,target,source);
        }else{
            var projection=buffer.getComponent(target,SummonProjection.getComponentType());
            var lease=projection==null?null:summons.registry().find(projection.token).orElse(null);
            if(lease==null||!lease.ironSentinel()||!wearer.equals(lease.entity()))return Optional.empty();
            var owner=store.getExternalData().getRefFromUUID(lease.owner());
            if(owner==null||!owner.isValid())return Optional.empty();
            equipped=lease.boundEffects();
            hostile=HytaleAreaQueries.hostile(store,source,owner);
        }
        if(!hostile)return Optional.empty();
        return Optional.of(new NativeGearSignatureReceipt.Incoming(world(store),wearer,attacker,root,contact,
                equipped,true,true,reflected,blocked,loss,false));
    }
    @Override public boolean request(GearSignatureProcRuntime.Contact contact,Store<EntityStore> store,Ref<EntityStore> source){
        if(contact.scriptedVeto()||contact.protectedTarget()||contact.targetKind()==GearSignatureProcRuntime.Kind.BOSS
                ||contact.targetKind()==GearSignatureProcRuntime.Kind.PLAYER||contact.targetHealth()<=0)return false;
        var target=store.getExternalData().getRefFromUUID(contact.target());
        if(source==null||target==null||!source.isValid()||!target.isValid()
                ||!HytaleAreaQueries.hostile(store,target,source)
                ||HytaleEncounterRewards.excluded(store,target)
                ||store.getComponent(target,DeathComponent.getComponentType())!=null)return false;
        var child=cullChild(contact);
        if(child.isEmpty())return false;
        children.enqueue(child.get(),store.getExternalData().getWorld().getTick());
        return true;
    }
    /** The native request and offline recipient use the same finite execute payload. */
    public static Optional<GearSignatureProcRuntime.Child> cullChild(GearSignatureProcRuntime.Contact contact){
        if(contact.scriptedVeto()||contact.protectedTarget()||contact.targetKind()==GearSignatureProcRuntime.Kind.BOSS
                ||contact.targetKind()==GearSignatureProcRuntime.Kind.PLAYER||contact.targetHealth()<=0
                ||contact.targetNormalMaximum()<=0||contact.targetHealth()>.05*contact.targetNormalMaximum())
            return Optional.empty();
        // The floor covers native 60% armor plus the supported shield while keeping finite float input.
        double amount=Math.min(Float.MAX_VALUE,Math.max(contact.targetNormalMaximum(),contact.targetHealth())*16);
        if(!Double.isFinite(amount)||amount<=0)return Optional.empty();
        return Optional.of(new GearSignatureProcRuntime.Child(GearSignatureProcRuntime.ChildKind.CULL,
                contact.world(),contact.owner(),contact.target(),contact.root(),contact.contact(),
                Map.of(GearCombatEffects.Channel.PHYSICAL,amount)));
    }
    @Override public Optional<NativeGearSignatureDefenseFilter.Rating> rating(Ref<EntityStore> target,
                                                                                CommandBuffer<EntityStore> buffer){
        if(target==null||!target.isValid())return Optional.empty();
        var armor=buffer.getComponent(target,InventoryComponent.Armor.getComponentType());
        var effects=buffer.getComponent(target,EffectControllerComponent.getComponentType());
        var inventory=armor==null?EmptyItemContainer.INSTANCE:armor.getInventory();
        if(effects==null)return Optional.empty();
        double reduction=HytaleDamageAdapter.nativeResistance(buffer.getStore().getExternalData().getWorld(),
                inventory,false,effects,DamageCause.PHYSICAL);
        if(!Double.isFinite(reduction)||reduction<=0)return Optional.empty();
        reduction=Math.clamp(reduction,0,.60);
        double k=110; // Native protection is a percentage; inverse rating ratio cancels K.
        return Optional.of(new NativeGearSignatureDefenseFilter.Rating(k*reduction/(1-reduction),k));
    }
    @Override public synchronized void accepted(UUID world,UUID target){
        var row=lethal.remove(deathKey(world,target));
        if(row==null||row.expires()<System.nanoTime())return;
        var contact=row.contact();
        var nativeWorld=com.hypixel.hytale.server.core.universe.Universe.get().getWorld(world);
        if(nativeWorld==null)return;
        var store=nativeWorld.getEntityStore().getStore();
        var source=store.getExternalData().getRefFromUUID(contact.owner());
        if(source==null||!source.isValid())return;
        var credited=new GearSignatureProcRuntime.Contact(contact.world(),contact.owner(),contact.item(),
                contact.target(),contact.root(),contact.contact(),contact.snapshot(),contact.direct(),contact.melee(),
                contact.hostile(),contact.noProc(),contact.reflected(),contact.blocked(),contact.actualHpLoss(),
                0,contact.targetNormalMaximum(),contact.targetKind(),contact.noncriticalPhysical(),
                contact.preMitigation(),contact.procCoefficient(),contact.protectedTarget(),contact.scriptedVeto(),true);
        runtime.creditedKill(credited,System.nanoTime()/1e9,new GearSignatureProcRuntime.Port(){
            @Override public void enqueue(GearSignatureProcRuntime.Child child){
                children.enqueue(child,store.getExternalData().getWorld().getTick());
            }
            @Override public boolean execute(GearSignatureProcRuntime.Contact ignored){return false;}
            @Override public List<UUID> burstTargets(GearSignatureProcRuntime.Contact ignored,double radius,int maximum){
                return row.nearby();
            }
        });
    }
}
