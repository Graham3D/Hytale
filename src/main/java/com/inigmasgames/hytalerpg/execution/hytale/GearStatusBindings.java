package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageLifecycleSystems;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata;
import com.inigmasgames.hytalerpg.combat.status.ControlProfile;
import com.inigmasgames.hytalerpg.combat.status.GearStatusRuntime;
import com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime;
import com.inigmasgames.hytalerpg.combat.status.RpgStatusType;
import com.inigmasgames.hytalerpg.combat.status.StatusService;
import com.inigmasgames.hytalerpg.gear.GearCombatEffects;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Production status admission from frozen contact facts, native target policy and the shared status owners. */
public final class GearStatusBindings implements NativeGearStatusObserver.Opportunity {
    private static final com.hypixel.hytale.logger.HytaleLogger LOGGER=
            com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass();
    private static final com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile STATUS_BALANCE=
            com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile.loadCanonical();
    private final StatusService statuses;
    private final HytaleBossBarTracker bosses;
    private final HytaleSkillExecutionSystem skills;
    @FunctionalInterface public interface TargetGear {
        com.inigmasgames.hytalerpg.gear.GearEffectSnapshot effects(Ref<EntityStore> target,CommandBuffer<EntityStore> buffer);
    }
    private final TargetGear targetGear;
    private final GearStatusRuntime.Contacts contacts=new GearStatusRuntime.Contacts();
    private record Accepted(NativeGearContactContext context,long expiresAt) { }
    private record SkillKey(UUID actor,String root,RpgStatusType type) { }
    private final IdentityHashMap<GearCombatEffects.Hit,Accepted> accepted=new IdentityHashMap<>();
    private final java.util.Map<SkillKey,Long> sharedSkills=new java.util.HashMap<>();
    private final PeriodicStatusRuntime<Object,Object> periodic;
    private record Pending(NativeGearContactContext context,PeriodicStatusRuntime.Kind kind,
                           GearStatusRuntime.AppliedHit hit,com.inigmasgames.hytalerpg.gear.GearEffectSnapshot gear,
                           long expiresAt) { }
    private final java.util.ArrayDeque<Pending> pending=new java.util.ArrayDeque<>();
    private final java.util.Map<UUID,UUID> gearVictimWorld=new java.util.HashMap<>();
    private final java.util.Map<PeriodicStatusRuntime.Source,UUID> gearSourceWorld=new java.util.HashMap<>();

    public GearStatusBindings(StatusService statuses,HytaleBossBarTracker bosses,HytaleSkillExecutionSystem skills){
        this(statuses,bosses,skills,com.inigmasgames.hytalerpg.gear.GearNativeItems::recipientEffects);
    }
    public GearStatusBindings(StatusService statuses,HytaleBossBarTracker bosses,HytaleSkillExecutionSystem skills,
                              TargetGear targetGear){
        this.statuses=Objects.requireNonNull(statuses);this.bosses=Objects.requireNonNull(bosses);
        this.skills=Objects.requireNonNull(skills);
        this.targetGear=Objects.requireNonNull(targetGear);
        this.periodic=skills.sharedPeriodicRuntime();
    }
    @Override public com.inigmasgames.hytalerpg.gear.GearEffectSnapshot targetGear(
            Ref<EntityStore> target,CommandBuffer<EntityStore> buffer){
        return targetGear.effects(target,buffer);
    }
    /** Acceptance hook: call once when the managed strike/shot is frozen, before its first native channel. */
    public synchronized void accept(NativeGearContactContext context){
        Objects.requireNonNull(context);
        long now=System.nanoTime();accepted.values().removeIf(a->a.expiresAt()<=now);
        sharedSkills.values().removeIf(end->end<=now);
        if(accepted.size()>=4096&&!accepted.containsKey(context.hit()))
            throw new IllegalStateException("GEAR_STATUS_ACCEPTED_CONTACT_BUDGET");
        if(accepted.putIfAbsent(context.hit(),new Accepted(context,now+120_000_000_000L))!=null)
            throw new IllegalStateException("GEAR_STATUS_CONTACT_ALREADY_ACCEPTED");
        context.skillChances().forEach((type,chance)->{
            if(chance>0)sharedSkills.put(new SkillKey(context.actorId(),context.hit().rootId(),type),
                    now+120_000_000_000L);
        });
    }
    public NativeGearStatusObserver observer(){return new NativeGearStatusObserver(statuses,contacts,this);}
    public NativeAcceptance nativeAcceptance(){return new NativeAcceptance(this);}
    public TickSystem tickSystem(){return new TickSystem(this);}
    public Removal removal(){return new Removal(this);}
    public PeriodicStatusRuntime.View periodicView(UUID victim,PeriodicStatusRuntime.Kind kind,double now){
        return periodic.view(victim,kind,now);
    }
    public synchronized boolean attempted(UUID actor,String root,UUID victim,RpgStatusType type){
        return sharedSkills.containsKey(new SkillKey(actor,root,type))&&contacts.contains(actor,root,victim,type);
    }
    public static boolean requires(GearCombatEffects.Hit hit){
        if(hit==null||hit.itemId()==null)return false;
        var local=hit.snapshot().forItem(hit.itemId());
        for(String id:new String[]{"WA-053","WA-054","WA-055","WA-056","WA-057","WA-058",
                "WA-059","WA-060","WA-061","WA-062","WA-063","WA-134"})
            if(local.percent(id)>0)return true;
        return false;
    }
    public synchronized void forget(UUID actor,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        accepted.values().removeIf(c->c.context().actorId().equals(actor));
        sharedSkills.keySet().removeIf(key->key.actor().equals(actor));
        pending.removeIf(p->p.hit().actor().equals(actor));
        contacts.forget(actor);statuses.forgetSource(actor);
        periodic.cancel(actor,System.nanoTime()/1e9,periodicPort(store,buffer));
        gearSourceWorld.keySet().removeIf(source->source.owner().equals(actor));
    }
    public synchronized void forgetVictim(UUID victim){
        pending.removeIf(p->p.hit().target().equals(victim));
        gearVictimWorld.remove(victim);
    }
    public synchronized void clearWorld(UUID world){
        var owners=new java.util.HashSet<UUID>();
        gearSourceWorld.forEach((source,sourceWorld)->{if(sourceWorld.equals(world))owners.add(source.owner());});
        accepted.values().forEach(value->{if(value.context().worldId().equals(world))owners.add(value.context().actorId());});
        accepted.values().removeIf(c->c.context().worldId().equals(world));
        pending.removeIf(p->p.context().worldId().equals(world));
        gearVictimWorld.values().removeIf(world::equals);
        gearSourceWorld.values().removeIf(world::equals);
        sharedSkills.keySet().removeIf(key->owners.contains(key.actor()));
        for(var actor:owners){
            contacts.forget(actor);statuses.forgetSource(actor);
            periodic.cancel(actor,System.nanoTime()/1e9,new PeriodicStatusRuntime.Port<>(){
                @Override public boolean tick(PeriodicStatusRuntime.Source source,Object payload,Object target,
                                              int index,double amount,double seconds){return false;}
                @Override public void changed(PeriodicStatusRuntime.Source source,Object target,
                                              PeriodicStatusRuntime.View view){
                    statuses.projectPeriodic(source.victim(),RpgStatusType.valueOf(source.kind().name()),
                            view.stacks(),view.remainingSeconds());
                }
            });
        }
    }
    synchronized UUID gearWorld(UUID victim){return gearVictimWorld.get(victim);}
    synchronized void periodicTerminated(PeriodicStatusRuntime.Source source){gearSourceWorld.remove(source);}
    /** Native Gather has attached the frozen hit, while Apply has not yet run. */
    public static final class NativeAcceptance extends DamageEventSystem {
        private final GearStatusBindings bindings;
        private NativeAcceptance(GearStatusBindings bindings){this.bindings=bindings;}
        @Override public Query<EntityStore> getQuery(){return Query.any();}
        @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getGatherDamageGroup();}
        @Override public java.util.Set<Dependency<EntityStore>> getDependencies(){return java.util.Set.of(
                new SystemDependency<>(Order.AFTER,HytaleDamageAdapter.ManagedGearGather.class));}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer,Damage damage){
            try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(
                    store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.DAMAGE)){
                var source=HytaleDamageAdapter.gearHit(damage);
                if(source==null||!requires(source.hit())||bindings.contains(source.hit())
                        ||!(damage.getSource() instanceof Damage.EntitySource entity)||entity.getRef()==null)return;
                var actor=buffer.getComponent(entity.getRef(),UUIDComponent.getComponentType());
                var metadata=HytaleDamageAdapter.metadata(damage);
                if(actor==null||metadata==null||metadata.origin()!=HytaleDamageMetadata.Origin.DIRECT
                        ||!actor.getUuid().equals(metadata.actorId()))return;
                var world=store.getExternalData().getWorld().getWorldConfig().getUuid();
                var committed=com.inigmasgames.hytalerpg.gear.NativeGearAttackAcceptance.find(
                        world,actor.getUuid(),source.hit());
                if(committed==null)return;
                bindings.accept(NativeGearContactContext.basic(source.hit(),actor.getUuid(),world,
                        committed.procCoefficient(),STATUS_BALANCE.burnDurationSeconds,STATUS_BALANCE.poisonDurationSeconds));
            }
        }
    }
    private synchronized boolean contains(GearCombatEffects.Hit hit){return accepted.containsKey(hit);}
    private synchronized NativeGearContactContext context(HytaleDamageLifecycleSystems.AppliedReceipt receipt){
        var gear=receipt.gearHit();
        if(gear==null)throw new IllegalStateException("GEAR_STATUS_NATIVE_HIT_MISSING");
        var entry=accepted.get(gear.hit());
        if(entry==null||entry.expiresAt()<=System.nanoTime())throw new IllegalStateException("GEAR_STATUS_ACCEPTED_CONTEXT_MISSING");
        var value=entry.context();
        if(!value.hit().rootId().equals(receipt.metadata().rootCastId())
                ||!value.actorId().equals(receipt.metadata().actorId()))
            throw new IllegalStateException("GEAR_STATUS_ROOT_MISMATCH");
        return value;
    }
    @Override public synchronized boolean accepted(HytaleDamageLifecycleSystems.AppliedReceipt receipt){
        return receipt.gearHit()!=null&&accepted.containsKey(receipt.gearHit().hit());
    }
    @Override public boolean hostile(Ref<EntityStore> source,Ref<EntityStore> target,CommandBuffer<EntityStore> buffer){
        return HytaleAreaQueries.hostile(buffer.getStore(),target,source);
    }
    @Override public ControlProfile control(Ref<EntityStore> target,CommandBuffer<EntityStore> buffer){
        return SupportNativeEffects.control(buffer.getStore(),target,bosses);
    }
    @Override public double existingStatusResistance(Ref<EntityStore> target,CommandBuffer<EntityStore> buffer){
        // The current non-gear native status owner has no Status Resistance stat. WA-079 is read separately.
        return 0;
    }
    @Override public double itemProcCoefficient(HytaleDamageLifecycleSystems.AppliedReceipt receipt){
        return receipt.procCoefficient();
    }
    @Override public double skillChance(RpgStatusType type,HytaleDamageLifecycleSystems.AppliedReceipt receipt){
        // Existing strike/projectile statuses require Health loss. An item affix may
        // still treat an accepted, fully absorbed contact as a hit independently.
        return canonicalSkillChance(context(receipt).skillChances().getOrDefault(type,0d),receipt.actualHealthLoss());
    }
    static double canonicalSkillChance(double authoredChance,double healthLoss){
        if(!Double.isFinite(authoredChance)||authoredChance<0||authoredChance>1
                ||!Double.isFinite(healthLoss)||healthLoss<0)throw new IllegalArgumentException("INVALID_SKILL_STATUS_RECEIPT");
        return healthLoss>0?authoredChance:0;
    }
    @Override public int skillStacks(RpgStatusType type,HytaleDamageLifecycleSystems.AppliedReceipt receipt){
        var skill=context(receipt).skillContext();
        return type==RpgStatusType.CHILL&&skill!=null&&skill.profile().projectile()!=null
                ?skill.profile().projectile().details().chillStacks():1;
    }
    @Override public boolean deepFreeze(HytaleDamageLifecycleSystems.AppliedReceipt receipt){
        var skill=context(receipt).skillContext();
        return skill!=null&&skill.compiledPlan().controls().deepFreeze();
    }
    @Override public String canonicalSkill(HytaleDamageLifecycleSystems.AppliedReceipt receipt){
        return context(receipt).canonicalSkill();
    }
    @Override public boolean targetChannelActive(Ref<EntityStore> target,CommandBuffer<EntityStore> buffer){
        var id=buffer.getComponent(target,UUIDComponent.getComponentType());
        return id!=null&&skills.channelActive(id.getUuid());
    }
    @Override public double draw(RpgStatusType type,HytaleDamageLifecycleSystems.AppliedReceipt receipt,String purpose){
        var gear=receipt.gearHit();
        return stableRoll(gear.hit().rootId(),receipt.targetId(),type,purpose);
    }
    public static double stableRoll(String root,UUID victim,RpgStatusType type,String purpose){
        if(root==null||root.isBlank()||victim==null||type==null||purpose==null)
            throw new IllegalArgumentException("INVALID_STATUS_DRAW_KEY");
        long hash=0xcbf29ce484222325L;
        for(char ch:(root+'/'+victim+'/'+type+'/'+purpose).toCharArray()){hash^=ch;hash*=0x100000001b3L;}
        return (hash>>>11)*0x1.0p-53;
    }
    @Override public GearStatusRuntime.PeriodicAdmission periodic(HytaleDamageLifecycleSystems.AppliedReceipt receipt,
            Ref<EntityStore> target,CommandBuffer<EntityStore> buffer){
        var context=context(receipt);
        return (kind,hit,sourceGear)->{
            var type=RpgStatusType.valueOf(kind.name());
            var local=GearStatusRuntime.sourceScoped(context.hit().snapshot(),context.hit().itemId());
            double gearBase=local.percent(switch(kind){
                case BLEED->"WA-053";case BURN->"WA-054";case POISON->"WA-055";
            })+(kind==PeriodicStatusRuntime.Kind.BLEED?local.percent("WA-134"):0);
            var defense=targetGear(target,buffer);
            var selected=skillPackageSucceeded(skillChance(type,receipt),gearBase,
                    receipt.procCoefficient(),existingStatusResistance(target,buffer),sourceGear,defense,
                    draw(type,receipt,"opportunity"))?context:
                    NativeGearContactContext.basic(context.hit(),context.actorId(),context.worldId(),
                            receipt.procCoefficient(),STATUS_BALANCE.burnDurationSeconds,
                            STATUS_BALANCE.poisonDurationSeconds);
            var profile=selected.periodicProfiles().get(kind);
            if(profile==null)throw new IllegalStateException("ACCEPTED_PERIODIC_PROFILE_MISSING_"+kind);
            var source=new PeriodicStatusRuntime.Source(hit.actor(),selected.canonicalSkill(),hit.target(),kind);
            String admission=periodic.admission(source);
            if(!admission.equals("PASS"))return admission;
            synchronized(this){
                if(pending.size()>=4096)throw new IllegalStateException("GEAR_STATUS_PERIODIC_ADMISSION_BUDGET");
                pending.addLast(new Pending(selected,kind,hit,sourceGear,System.nanoTime()+3_000_000_000L));
            }
            return "QUEUED";
        };
    }
    static boolean skillPackageSucceeded(double skillChance,double gearBase,double coefficient,
            double resistance,com.inigmasgames.hytalerpg.gear.GearEffectSnapshot source,
            com.inigmasgames.hytalerpg.gear.GearEffectSnapshot target,double draw){
        return GearStatusRuntime.chance(skillChance,gearBase,coefficient,resistance,source,target)
                .skillSucceeded(draw);
    }
    private String applyPending(Pending value,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        var context=value.context();
        if(!context.worldId().equals(SupportNativeEffects.world(store)))return "WORLD_CHANGED";
        var target=store.getExternalData().getRefFromUUID(value.hit().target());
        if(target==null||!target.isValid())return "TARGET_UNAVAILABLE";
        if(context.skillContext()!=null)
            return skills.admitGearPeriodic(context,value.kind(),value.hit(),value.gear(),target,buffer);
        var profile=context.periodicProfiles().get(value.kind());
        var source=new PeriodicStatusRuntime.Source(value.hit().actor(),context.canonicalSkill(),value.hit().target(),value.kind());
        synchronized(this){gearVictimWorld.put(value.hit().target(),context.worldId());}
        String result=GearStatusRuntime.applyPeriodic(periodic,source,context,value.hit().target(),profile.damagePerSecond(),
                profile.damagePerSecond(),profile.durationSeconds(),profile.addedStacks(),profile.sourceCap(),
                System.nanoTime()/1e9,periodicPort(store,buffer),value.gear());
        if(result.equals("APPLIED")||result.equals("REFRESHED"))synchronized(this){gearSourceWorld.put(source,context.worldId());}
        return result;
    }
    PeriodicStatusRuntime.Port<Object,Object> sharedPeriodicPort(Store<EntityStore> store,
            CommandBuffer<EntityStore> buffer){
        return new PeriodicStatusRuntime.Port<>() {
            @Override public void terminated(PeriodicStatusRuntime.Source source,Object payload,Object targetId,String reason){
                if(payload instanceof NativeGearContactContext)periodicTerminated(source);
                else skills.sharedPeriodicPort(buffer).terminated(source,payload,targetId,reason);
            }
            @Override public boolean tick(PeriodicStatusRuntime.Source source,Object payload,
                    Object targetId,int index,double amount,double seconds){
                if(payload instanceof com.inigmasgames.hytalerpg.execution.SkillExecutionContext)
                    return skills.sharedPeriodicPort(buffer).tick(source,payload,targetId,index,amount,seconds);
                var context=(NativeGearContactContext)payload;
                var victim=(UUID)targetId;
                var actor=store.getExternalData().getRefFromUUID(source.owner());
                var target=store.getExternalData().getRefFromUUID(victim);
                if(!context.worldId().equals(SupportNativeEffects.world(store))||actor==null||target==null
                        ||!actor.isValid()||!target.isValid()||!HytaleAreaQueries.hostile(store,target,actor))return false;
                var profile=SupportNativeEffects.control(store,target,bosses);
                if(profile.protectedEntity())return false;
                var stats=store.getComponent(target,EntityStatMap.getComponentType());
                var health=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
                if(health==null||health.get()<=health.getMin())return false;
                String causeId=switch(source.kind()){case BURN->"Fire";case POISON->"Poison";case BLEED->"Physical";};
                DamageCause cause=DamageCause.getAssetMap().getAsset(causeId);
                if(cause==null)throw new IllegalStateException("STATUS_NATIVE_CAUSE_MISSING_"+causeId);
                var metadata=new HytaleDamageMetadata(source.owner(),context.hit().rootId(),context.canonicalSkill(),
                        context.hit().rootId()+"/status/"+source.kind(),amount,Double.NaN,
                        context.hit().rootId()+"/status/"+source.kind()+"/"+index,false,HytaleDamageMetadata.Origin.PERIODIC);
                return !new HytaleDamageAdapter().applyResolved(target,buffer==null?store:buffer,actor,cause,
                        metadata,amount,null,null).cancelled();
            }
            @Override public void changed(PeriodicStatusRuntime.Source source,Object targetId,PeriodicStatusRuntime.View view){
                if(targetId instanceof HytaleSkillExecutionSystem.PeriodicTarget){
                    skills.sharedPeriodicPort(buffer).changed(source,targetId,view);return;
                }
                UUID victim=(UUID)targetId;
                RpgStatusType type=RpgStatusType.valueOf(source.kind().name());
                int stacks=view.stacks();
                statuses.projectPeriodic(victim,type,stacks,view.remainingSeconds());
                if(stacks==0)synchronized(GearStatusBindings.this){gearVictimWorld.remove(victim);}
                var target=store.getExternalData().getRefFromUUID(victim);
                if(target==null||!target.isValid())return;
                var controller=store.getComponent(target,EffectControllerComponent.getComponentType());
                if(controller==null)return;
                String id="RPG_"+switch(source.kind()){case BURN->"Burn";case POISON->"Poison";case BLEED->"Bleed";}+"_Visual";
                var effect=EntityEffect.getAssetMap().getAsset(id);
                if(effect==null)throw new IllegalStateException("STATUS_NATIVE_EFFECT_MISSING_"+id);
                if(stacks>0){
                    if(!controller.addEffect(target,effect,
                            (float)view.remainingSeconds(),
                            OverlapBehavior.OVERWRITE,store,null))
                        throw new IllegalStateException("STATUS_NATIVE_EFFECT_REJECTED_"+id);
                }else controller.removeEffect(target,EntityEffect.getAssetMap().getIndex(id),store);
            }
        };
    }
    private PeriodicStatusRuntime.Port<Object,Object> periodicPort(Store<EntityStore> store,
            CommandBuffer<EntityStore> buffer){return sharedPeriodicPort(store,buffer);}
    public void tick(UUID owner,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        java.util.List<Pending> ready=new java.util.ArrayList<>();
        synchronized(this){
            var iterator=pending.iterator();
            while(iterator.hasNext()){
                var value=iterator.next();
                if(value.expiresAt()<=System.nanoTime()){iterator.remove();continue;}
                if(value.hit().actor().equals(owner)){ready.add(value);iterator.remove();}
            }
        }
        for(var value:ready)try{
            String result=applyPending(value,store,buffer);
            if(!result.equals("APPLIED")&&!result.equals("REFRESHED"))
                LOGGER.atWarning().log("GEAR_STATUS_PERIODIC_NOT_APPLIED actor=%s root=%s kind=%s result=%s",
                        owner,value.hit().root(),value.kind(),result);
        }catch(RuntimeException error){
            LOGGER.atWarning().withCause(error).log("GEAR_STATUS_PERIODIC_ADMISSION_FAILED actor=%s root=%s kind=%s",
                    owner,value.hit().root(),value.kind());
        }
        // The skill owner advances player packages. A Sentinel has no player skill tick,
        // but its bound-item packages live in that same canonical runtime.
        var actor=store.getExternalData().getRefFromUUID(owner);
        if(actor!=null&&actor.isValid()&&store.getComponent(actor,PlayerRef.getComponentType())==null)
            periodic.tick(owner,System.nanoTime()/1e9,periodicPort(store,buffer));
    }
    public static final class TickSystem extends EntityTickingSystem<EntityStore> {
        private final GearStatusBindings bindings;
        private TickSystem(GearStatusBindings bindings){this.bindings=bindings;}
        @Override public Query<EntityStore> getQuery(){return Query.and(UUIDComponent.getComponentType(),
                Query.or(PlayerRef.getComponentType(),SummonProjection.getComponentType()));}
        @Override public void tick(float delta,int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
                                   CommandBuffer<EntityStore> buffer){
            try(var span=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(
                    store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.STATUS_FIELD)){
                bindings.tick(chunk.getComponent(index,UUIDComponent.getComponentType()).getUuid(),store,buffer);
            }
        }
    }
    public static final class Removal extends RefSystem<EntityStore> {
        private final GearStatusBindings bindings;
        private Removal(GearStatusBindings bindings){this.bindings=bindings;}
        @Override public Query<EntityStore> getQuery(){return Query.and(UUIDComponent.getComponentType(),
                Query.or(PlayerRef.getComponentType(),SummonProjection.getComponentType()));}
        @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer) { }
        @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
            var id=store.getComponent(ref,UUIDComponent.getComponentType());
            if(id!=null)bindings.forget(id.getUuid(),store,buffer);
        }
    }
}
