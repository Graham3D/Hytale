package com.inigmasgames.hytalerpg.diagnostics;

import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata;
import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleDifficultyCombat;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Opt-in, per-player observation of native Damage packets. Never submits or changes damage. */
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value="NP_NONNULL_PARAM_VIOLATION",
        justification="Installed native MetaRegistry accepts a null codec for a nonpersistent Damage metadata key.")
public final class CombatDamageTraceService implements AutoCloseable {
    public static final int MAX_EVENTS=2048, MAX_RECENT=32, RECORD_RESERVATION=32*1024;
    public static final long DURATION_MILLIS=60_000;
    private static final MetaKey<List<Hit>> HIT=Damage.META_REGISTRY.registerMetaObject(
            ignored->null,false,"InigmasGames:CombatTraceHit",null);
    public record Stage(String owner,String operation,double input,double output,Map<String,Object> details) {
        public Stage {details=Map.copyOf(details);}
    }
    public record Result(String sessionId,long sequence,String hitId,String rootId,String strikeId,String contactId,
            String direction,UUID worldId,UUID sourceId,UUID ownerId,UUID targetId,UUID projectileId,String cause,String origin,
            double attempted,double accepted,double postAbsorption,double prevented,double absorbed,
            Double healthBefore,Double healthAfter,Double healthLost,Double overkill,String reconciliation,
            boolean cancelled,boolean blocked,List<Stage> stages,Map<String,Object> source,
            Map<String,Object> target,List<String> unobservable,String timestamp) {
        public Result {stages=List.copyOf(stages);source=Map.copyOf(source);target=Map.copyOf(target);
            unobservable=List.copyOf(unobservable);}
    }
    public record Status(boolean active,String sessionId,long remainingMillis,long events,long dropped,long errors,long reconciliationErrors,
            BoundedTraceWriter.Metrics writer) {}
    private static final class Session {
        final UUID player,world;final String id;final long until;final ArrayDeque<Result> recent=new ArrayDeque<>();
        long events,reserved,dropped,errors,reconciliationErrors;boolean active=true;
        Session(UUID player,UUID world,long now){this.player=player;this.world=world;id=UUID.randomUUID().toString();until=now+DURATION_MILLIS;}
    }
    private static final class Hit {
        final Session session;final long sequence;final String id,root,strike,contact,direction;
        final UUID world,source,owner,target,projectile;final String cause,origin;
        final double attempted;final Map<String,Object> sourceInfo,targetInfo;
        final ArrayList<Stage> stages=new ArrayList<>();final ArrayList<String> unobservable=new ArrayList<>();
        Double healthBefore;double accepted;
        boolean finalized;
        Hit(Session session,long sequence,String id,String root,String strike,String contact,String direction,UUID world,
                UUID source,UUID owner,UUID target,UUID projectile,String cause,String origin,double attempted,
                Map<String,Object> sourceInfo,Map<String,Object> targetInfo){
            this.session=session;this.sequence=sequence;this.id=id;this.root=root;this.strike=strike;
            this.contact=contact;this.direction=direction;this.world=world;this.source=source;this.owner=owner;
            this.target=target;this.projectile=projectile;
            this.cause=cause;this.origin=origin;this.attempted=attempted;
            this.sourceInfo=sourceInfo;this.targetInfo=targetInfo;
        }
    }
    private final ConcurrentHashMap<UUID,Session> sessions=new ConcurrentHashMap<>();
    private final AtomicLong sequence=new AtomicLong();
    private final BoundedTraceWriter writer;
    private final Consumer<String> live;
    private volatile HytaleDifficultyCombat difficulty;
    public CombatDamageTraceService(Path path,Consumer<Throwable> failure,Consumer<String> live){
        writer=new BoundedTraceWriter(path,8L*1024*1024,8,failure);
        this.live=Objects.requireNonNull(live);
    }
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value="EI_EXPOSE_REP2",
            justification="The trace observes the shared world-owned difficulty service; copying it would lose authoritative state.")
    public void difficulty(HytaleDifficultyCombat value){difficulty=Objects.requireNonNull(value);}
    public String start(UUID player,UUID world){
        Objects.requireNonNull(player);Objects.requireNonNull(world);long now=System.currentTimeMillis();
        var existing=sessions.get(player);
        if(existing!=null&&existing.active&&now<existing.until&&existing.world.equals(world))return existing.id;
        sessions.entrySet().removeIf(entry->!entry.getValue().active&&now>entry.getValue().until+600_000);
        if((existing==null||!existing.active||now>=existing.until)
                &&sessions.values().stream().filter(value->value.active&&now<value.until).count()>=16)
            throw new IllegalStateException("COMBAT_TRACE_SESSION_CAPACITY");
        var session=new Session(player,world,now);
        sessions.put(player,session);return session.id;
    }
    public Status stop(UUID player){var session=sessions.get(player);if(session!=null)session.active=false;return status(player);}
    public void disconnect(UUID player){sessions.remove(player);}
    public Status status(UUID player){var session=sessions.get(player);long now=System.currentTimeMillis();
        if(session==null)return new Status(false,"",0,0,0,0,0,writer.metrics());
        synchronized(session){if(now>=session.until)session.active=false;
            return new Status(session.active,session.id,Math.max(0,session.until-now),
                    session.events,session.dropped,session.errors,session.reconciliationErrors,writer.metrics());}
    }
    public List<Result> last(UUID player,int count){if(count<1||count>MAX_RECENT)throw new IllegalArgumentException("COMBAT_LAST_RANGE");
        var session=sessions.get(player);if(session==null)return List.of();
        synchronized(session){var copy=new ArrayList<>(session.recent);return copy.subList(Math.max(0,copy.size()-count),copy.size());}
    }
    public boolean active(){for(var session:sessions.values())if(session.active&&System.currentTimeMillis()<session.until)return true;return false;}
    public boolean tracked(Damage damage){return damage.getIfPresentMetaObject(HIT)!=null;}
    public Map<String,Object> profile(UUID world,UUID entity,String role){
        var info=new LinkedHashMap<String,Object>();info.put("entity",entity.toString());
        if(role==null){info.put("kind","PLAYER_OR_NON_NPC");return Map.copyOf(info);}
        info.put("runtimeRole",role);
        var combat=difficulty;
        var spawn=combat==null?null:combat.snapshot(world,entity).orElse(null);
        if(spawn==null){info.put("profileOutcome","MISSING_OR_INAPPLICABLE");return Map.copyOf(info);}
        var resolved=spawn.combat();info.put("profileOutcome","APPLIED");info.put("profileId",spawn.registryProfile());
        info.put("difficulty",resolved.difficulty().toString());info.put("combatLevel",spawn.level());
        info.put("rank",spawn.rank().toString());info.put("expectedMaximumHealth",resolved.maxHealth());
        info.put("nativeHealthBaseline",resolved.nativeHealthBaseline());
        info.put("authoredAttackBasis",resolved.attackBasis());
        info.put("difficultyHealthFactor",resolved.difficultyHealthFactor());
        info.put("difficultyDamageFactor",resolved.difficultyDamageFactor());
        if(resolved.progression()!=null){info.put("progressionRevision",resolved.progression().revision());
            info.put("progressionHealthRatio",resolved.progression().healthRatio());
            info.put("progressionDamageRatio",resolved.progression().damageRatio());}
        var enemy=combat.enemyState(world,entity).orElse(null);
        if(enemy!=null){info.put("spawnOrigin",enemy.descriptor().spawnOrigin().name());
            info.put("rarity",enemy.descriptor().enemyRarity().name());
            info.put("rarityHealthFactor",enemy.providers().rarityHealthFactor());
            info.put("rarityDirectFactor",enemy.providers().rarityDirectFactor());
            info.put("maxHealthIncrease",enemy.providers().maxHealthIncrease());
            info.put("allDirectIncrease",enemy.providers().allDirectIncrease());
            info.put("physicalIncrease",enemy.providers().physicalIncrease());
            info.put("extraPowerFractions",enemy.providers().extraPowerFractions().toString());
            info.put("ownAffixes",enemy.descriptor().ownAffixes().stream().map(a->a.affixId()+a.parameters()).toList());
            info.put("inheritedAffixes",enemy.descriptor().inheritedAffixes().stream().map(a->a.affixId()+a.parameters()).toList());
        }
        info.put("resistances",resolved.resistance().resistance().toString());
        info.put("immunities",resolved.resistance().immunities().toString());
        return Map.copyOf(info);
    }
    public void begin(Damage damage,UUID world,UUID source,UUID target,String sourceRole,String targetRole,
            Double targetHealth,Double targetMaximum){
        if(!active()||damage.getIfPresentMetaObject(HIT)!=null)return;
        var meta=HytaleDamageAdapter.metadata(damage);
        Session incoming=target==null?null:sessions.get(target),outgoing=source==null?null:sessions.get(source);
        if(outgoing==null&&meta!=null&&meta.actorId()!=null)outgoing=sessions.get(meta.actorId());
        var eligible=new ArrayList<Session>(2);
        if(outgoing!=null&&outgoing.active&&world.equals(outgoing.world))eligible.add(outgoing);
        if(incoming!=null&&incoming!=outgoing&&incoming.active&&world.equals(incoming.world))eligible.add(incoming);
        if(eligible.isEmpty())return;
        String packetRoot=meta==null?"native:"+UUID.randomUUID():meta.rootCastId();
        String packetContact=meta==null?"native-contact:"+UUID.randomUUID():meta.correlationId();
        var captures=new ArrayList<Hit>(eligible.size());
        for(var session:eligible)synchronized(session){
            if(!session.active||System.currentTimeMillis()>=session.until){session.active=false;continue;}
            if(session.events+session.reserved>=MAX_EVENTS){session.active=false;session.dropped++;continue;}
            long number=sequence.incrementAndGet();
            String root=packetRoot;
            String strike=meta==null?root:meta.effectInstanceId();
            String id=session.id+":"+number;
            var sourceInfo=source==null?Map.<String,Object>of():profile(world,source,sourceRole);
            var targetInfo=new LinkedHashMap<String,Object>();
            if(target!=null)targetInfo.putAll(profile(world,target,targetRole));
            if(targetHealth!=null)targetInfo.put("currentHealthAtGather",targetHealth);
            if(targetMaximum!=null)targetInfo.put("maximumHealthAtGather",targetMaximum);
            var hit=new Hit(session,number,id,root,strike,packetContact,
                    source!=null&&source.equals(session.player)||meta!=null&&session.player.equals(meta.actorId())?"OUTGOING":"INCOMING",
                    world,source,meta==null?source:meta.actorId(),target,
                    com.inigmasgames.hytalerpg.combat.hytale.NativeDamageLeafReceipts.projectileId(damage),
                    damage.getCause()==null?"UNKNOWN":damage.getCause().getId(),
                    meta==null?"NATIVE_UNWRAPPED":meta.origin().name(),damage.getAmount(),sourceInfo,Map.copyOf(targetInfo));
            var nativeEffect=com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyOutgoingEffects.observed(damage);
            if(nativeEffect!=null)hit.stages.add(new Stage("Native source entity effects","OUTGOING_EFFECTS",
                    nativeEffect.before(),nativeEffect.after(),Map.of()));
            var enemyOffense=com.inigmasgames.hytalerpg.combat.hytale.NativeDamageLeafReceipts.offense(damage);
            if(enemyOffense!=null){String nativeCause=damage.getCause()==null?"UNKNOWN":damage.getCause().getId();
                hit.stages.add(new Stage("Hywind accepted enemy strike","SOURCE_VECTOR",
                        enemyOffense.sourceVector().getOrDefault(nativeCause,0d),
                        enemyOffense.sourceVector().getOrDefault(nativeCause,0d),Map.of(
                                "sourceVector",enemyOffense.sourceVector(),"sourcePower",enemyOffense.sourcePower(),
                                "bindingRevision",enemyOffense.nativeBindingRevision(),"balanceRevision",enemyOffense.balanceRevision())));
                hit.stages.add(new Stage("Hywind monster affix source","AFFIXED_VECTOR",
                        enemyOffense.sourceVector().getOrDefault(nativeCause,0d),
                        enemyOffense.affixedVector().getOrDefault(nativeCause,0d),Map.of(
                                "affixedVector",enemyOffense.affixedVector(),"spectralChannel",
                                enemyOffense.spectralChannel()==null?"NONE":enemyOffense.spectralChannel(),
                                "directWeakening",enemyOffense.directWeakening(),"procCoefficient",enemyOffense.procCoefficient())));
                hit.unobservable.add("Native calculator sample and pre-snapshot source factors are not retained by EnemyOffenseSnapshot");
            }
            var offense=HytaleDamageAdapter.offenseResult(damage);
            var inputs=HytaleDamageAdapter.offenseInputs(damage);
            if(inputs!=null){
                var buckets=inputs.buckets();
                hit.stages.add(new Stage("Hywind committed offensive request","MODIFIER_INPUTS",1,
                        buckets.factor(),Map.of("increased",buckets.increased(),"reduced",buckets.reduced(),
                                "more",buckets.more(),"less",buckets.less(),
                                "scalingAttribute",inputs.scalingAttribute(),
                                "effectiveAttribute",inputs.effectiveAttribute(),
                                "coefficient",inputs.coefficient(),"criticalChance",inputs.criticalChance())));
            }
            if(offense!=null){
                hit.stages.add(new Stage("Hywind offense","ATTRIBUTE_SCALING",offense.basePower(),offense.scaledBasePower(),
                        Map.of("attributeMultiplier",offense.attributeMultiplier(),"scalingAttribute",
                                inputs==null?"UNOBSERVABLE":inputs.scalingAttribute(),"effectiveAttribute",
                                inputs==null?"UNOBSERVABLE":inputs.effectiveAttribute())));
                hit.stages.add(new Stage("Hywind offense","SKILL_COEFFICIENT",offense.scaledBasePower(),offense.skillRawDamage(),
                        Map.of("coefficient",inputs==null?"UNOBSERVABLE":inputs.coefficient())));
                hit.stages.add(new Stage("Hywind offense","MODIFIER_BUCKETS",offense.skillRawDamage(),offense.preCritDamage(),
                        Map.of("factor",offense.modifierFactor())));
                hit.stages.add(new Stage("Hywind offense","CRITICAL",offense.preCritDamage(),offense.preMitigationDamage(),
                        Map.of("critical",offense.critical(),"chance",inputs==null?"UNOBSERVABLE":inputs.criticalChance())));
                hit.stages.add(new Stage("Hywind/Hytale boundary","DOUBLE_TO_NATIVE_FLOAT",offense.preMitigationDamage(),
                        damage.getAmount(),Map.of()));
            }
            if(meta!=null){var context=HytaleDamageAdapter.executionContext(damage);
                if(context!=null)addSnapshot(hit,context);
                var gear=HytaleDamageAdapter.gearHit(damage);
                if(gear!=null){var value=gear.hit();
                    var gearDetails=new LinkedHashMap<String,Object>();
                    gearDetails.put("contactId",gear.contactId());gearDetails.put("channel",gear.channel().name());
                    gearDetails.put("itemId",value.itemId()==null?"NONE":value.itemId().toString());
                    gearDetails.put("snapshotRevision",value.revision());
                    gearDetails.put("channelAmounts",value.amounts());gearDetails.put("penetration",value.penetration());
                    gearDetails.put("increased",value.increased());gearDetails.put("critical",value.critical());
                    gearDetails.put("criticalMultiplier",value.criticalMultiplier());
                    gearDetails.put("sourceKind",value.sourceKind());
                    gearDetails.put("submittedSourcePower",Double.isFinite(value.submittedSourcePower())
                            ?value.submittedSourcePower():"UNOBSERVABLE");
                    gearDetails.put("submittedCoefficient",Double.isFinite(value.submittedCoefficient())
                            ?value.submittedCoefficient():"UNOBSERVABLE");
                    gearDetails.put("appliedCriticalChance",Double.isFinite(value.appliedCriticalChance())
                            ?value.appliedCriticalChance():"UNOBSERVABLE");
                    var sourceAffixes=new ArrayList<Map<String,Object>>();
                    for(var item:value.snapshot().items()){
                        if(item.identity().equals(value.itemId())){
                            gearDetails.put("itemBaseId",item.baseId());gearDetails.put("itemIntrinsicStats",item.intrinsicStats());
                        }
                        for(var roll:item.affixes())if(sourceAffixes.size()<48)
                            sourceAffixes.add(Map.of("itemId",item.identity().toString(),"affixId",roll.familyId(),"value",roll.value()));
                    }
                    gearDetails.put("gearAffixes",sourceAffixes);
                    hit.stages.add(new Stage("Hywind committed gear hit","CHANNEL_VECTOR",damage.getAmount(),damage.getAmount(),
                            gearDetails));
                }
                if(meta.monsterAffix()!=null)hit.stages.add(new Stage("Hywind monster affix","SOURCE_IDENTITY",damage.getAmount(),damage.getAmount(),
                        Map.of("affixId",meta.monsterAffix().affixId(),"generation",meta.monsterAffix().encounterGeneration())));
            }else hit.unobservable.add("Native source base/sample/critical roll before Gather: unavailable through Damage packet");
            hit.stages.add(new Stage("Hytale Damage Gather","ATTEMPT",damage.getAmount(),damage.getAmount(),Map.of("cancelled",damage.isCancelled())));
            captures.add(hit);
            session.reserved++;
        }
        if(!captures.isEmpty())damage.putMetaObject(HIT,List.copyOf(captures));
    }
    private static void addSnapshot(Hit hit,SkillExecutionContext context){
        var snapshot=context.snapshot();var details=new LinkedHashMap<String,Object>();
        details.put("skill",snapshot.skillInstanceId());details.put("item",snapshot.itemId()==null?"NONE":snapshot.itemId());
        details.put("basePower",snapshot.basePower());details.put("coefficient",snapshot.skillCoefficient());
        var rawAttributes=new java.util.TreeMap<String,Integer>();
        snapshot.rawAttributes().forEach((name,value)->rawAttributes.put(name.name(),value));
        var effectiveAttributes=new java.util.TreeMap<String,Double>();
        snapshot.effectiveAttributes().forEach((name,value)->effectiveAttributes.put(name.name(),value));
        details.put("rawAttributes",rawAttributes);
        details.put("effectiveAttributes",effectiveAttributes);
        details.put("snapshotModifierBuckets",Map.of("increased",snapshot.modifiers().increased(),
                "reduced",snapshot.modifiers().reduced(),"more",snapshot.modifiers().more(),
                "less",snapshot.modifiers().less()));
        details.put("basePowerSource",String.valueOf(snapshot.basePowerSource()));
        details.put("weaponClass",String.valueOf(snapshot.weaponClass()));
        details.put("statusModifiers",snapshot.statusModifiers());
        details.put("criticalChance",snapshot.criticalChance());details.put("criticalMultiplier",snapshot.criticalMultiplier());
        details.put("effectiveSkillLevel",context.effectiveSkillLevel());
        details.put("compiledPlanHash",snapshot.compiledPlanHash()==null?"NONE":snapshot.compiledPlanHash());
        var affixes=new ArrayList<Map<String,Object>>();
        for(var item:context.gearSnapshot().items())for(var roll:item.affixes())if(affixes.size()<48)
            affixes.add(Map.of("itemId",item.identity().toString(),"affixId",roll.familyId(),"value",roll.value()));
        details.put("gearRevision",context.gearSnapshot().revision());details.put("gearAffixes",affixes);
        hit.stages.add(new Stage("Hywind committed skill snapshot","OFFENSIVE_SNAPSHOT",hit.attempted,hit.attempted,details));
        hit.unobservable.add("Per-modifier identities absent from committed ModifierBuckets; values and bucket order retained");
        hit.unobservable.add("Critical RNG sample is not exposed by CriticalRoller");
    }
    public void stage(Damage damage,String owner,String operation,double before,double after,Map<String,Object> details){
        var hits=damage.getIfPresentMetaObject(HIT);if(hits!=null)for(var hit:hits)if(hit.stages.size()<64)
            hit.stages.add(new Stage(owner,operation,before,after,details));
    }
    public void accepted(Damage damage){var hits=damage.getIfPresentMetaObject(HIT);if(hits!=null)for(var hit:hits){
        hit.accepted=damage.isCancelled()?0:Math.max(0,damage.getAmount());
        if(hit.stages.size()<64)hit.stages.add(new Stage("Hywind before absorption","ACCEPTED",damage.getAmount(),hit.accepted,Map.of()));
    }}
    public void beforeApply(Damage damage,Double health){var hits=damage.getIfPresentMetaObject(HIT);if(hits!=null)for(var hit:hits){
        hit.healthBefore=health;
        if(hit.stages.size()<64)hit.stages.add(new Stage("Native before ApplyDamage","AFTER_ABSORPTION",hit.accepted,
                damage.getAmount(),Map.of("cancelled",damage.isCancelled())));
    }}
    public void applied(Damage damage,Double healthAfter){var hits=damage.getIfPresentMetaObject(HIT);
        if(hits!=null)for(var hit:hits)finish(hit,damage,healthAfter);
    }
    private void finish(Hit hit,Damage damage,Double healthAfter){if(hit.finalized)return;
        hit.finalized=true;
        double before=hit.healthBefore==null?Double.NaN:hit.healthBefore;
        double after=healthAfter==null?Double.NaN:healthAfter;
        double finalAmount=damage.isCancelled()?0:Math.max(0,damage.getAmount());
        double lost=Double.isFinite(before)&&Double.isFinite(after)?Math.max(0,Math.min(before,before-after)):Double.NaN;
        double overkill=Double.isFinite(before)?Math.max(0,finalAmount-Math.max(0,before)):Double.NaN;
        double prevented=Math.max(0,hit.attempted-hit.accepted),absorbed=Math.max(0,hit.accepted-finalAmount);
        String reconciliation=!Double.isFinite(lost)?"UNOBSERVABLE":
                Math.abs(lost-Math.min(Math.max(0,before),finalAmount))<=.02?"MATCH":"MISMATCH";
        boolean blocked=Boolean.TRUE.equals(damage.getIfPresentMetaObject(Damage.BLOCKED));
        var missing=new ArrayList<>(hit.unobservable);
        if(!Double.isFinite(lost))missing.add("Per-hit Health before/after unavailable at native ApplyDamage boundary");
        if(hit.attempted!=hit.accepted)
            missing.add("One or more native Filter contributions have no individual stage hook; before/after retained");
        var result=new Result(hit.session.id,hit.sequence,hit.id,hit.root,hit.strike,hit.contact,hit.direction,
                hit.world,hit.source,hit.owner,hit.target,hit.projectile,hit.cause,hit.origin,hit.attempted,hit.accepted,
                finalAmount,prevented,absorbed,finite(before),finite(after),finite(lost),finite(overkill),reconciliation,
                damage.isCancelled(),blocked,hit.stages,hit.sourceInfo,hit.targetInfo,
                missing,Instant.now().toString());
        synchronized(hit.session){hit.session.reserved--;hit.session.events++;
            if(reconciliation.equals("MISMATCH"))hit.session.reconciliationErrors++;
            if(hit.session.recent.size()==MAX_RECENT)hit.session.recent.removeFirst();hit.session.recent.addLast(result);
            if(!writer.submitDeferred(result,RECORD_RESERVATION)){hit.session.dropped++;hit.session.errors++;}
        }
        try{live.accept("RPG_COMBAT_HIT #"+hit.sequence+" "+hit.direction+" "+hit.cause+" "+hit.source+" -> "+hit.target+
                " attempted="+hit.attempted+" accepted="+hit.accepted+" healthLoss="+lost+" cancelled="+damage.isCancelled());}
        catch(RuntimeException ignored){}
    }
    /** One bounded snapshot per observed native NPC add or profile decision, scoped to an active player's world. */
    public void npcEvent(UUID world,UUID npc,String kind,Map<String,Object> fields){
        if(!active())return;
        for(var session:sessions.values()){
            if(!session.active||!session.world.equals(world))continue;
            synchronized(session){if(System.currentTimeMillis()>=session.until){session.active=false;continue;}
                if(session.events+session.reserved>=MAX_EVENTS){session.active=false;session.dropped++;continue;}
                var record=new LinkedHashMap<String,Object>();record.put("eventType","NPC_PROFILE_"+kind);
                record.put("sessionId",session.id);record.put("sequence",sequence.incrementAndGet());
                record.put("worldId",world);record.put("npcId",npc);record.put("timestamp",Instant.now().toString());
                record.putAll(fields);session.events++;
                if(!writer.submitDeferred(Map.copyOf(record),RECORD_RESERVATION)){session.dropped++;session.errors++;}
            }
        }
    }
    private static Double finite(double value){return Double.isFinite(value)?value:null;}
    public void worldUnload(UUID world){sessions.entrySet().removeIf(entry->entry.getValue().world.equals(world));}
    @Override public void close(){for(var session:sessions.values())session.active=false;writer.close();sessions.clear();}
}
