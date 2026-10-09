package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.combat.damage.*;
import com.inigmasgames.hytalerpg.combat.status.StatusApplication;
import com.inigmasgames.hytalerpg.execution.TimedOpportunityLedger;
import com.inigmasgames.hytalerpg.gear.GearRandom;
import java.util.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.require;

/** Receipt-to-package adapter. Status resistance, Fortune, stacks and native mutation remain shared owners. */
public final class EnemyStatusEffects {
    private static final Map<String,String> STATUSES=Map.of("ME-006","CHILL","ME-007","ELECTRIFIED","ME-008","POISON","ME-014","WEAKEN","ME-015","KNOCKBACK","ME-025","ARMOR_BREAK");
    /** A certified source-side opportunity snapshot; absence is not evidence of zero native chance. */
    public record Source(Map<String,Double> existingChance,double penetration){
        public Source{
            require(existingChance.keySet().equals(Set.copyOf(STATUSES.values())),"MONSTER_STATUS_SOURCE_COVERAGE");
            existingChance=Collections.unmodifiableMap(new TreeMap<>(existingChance));
            existingChance.values().forEach(v->require(v!=null&&Double.isFinite(v)&&v>=0&&v<=1,"MONSTER_STATUS_SOURCE_CHANCE"));
            require(Double.isFinite(penetration)&&penetration>=0,"MONSTER_STATUS_SOURCE_PENETRATION");
        }
        public static Source noNativeStatus(double penetration){var values=new TreeMap<String,Double>();STATUSES.values().forEach(s->values.put(s,0.0));return new Source(values,penetration);}
    }
    public record Target(UUID logicalActor,long generation){public Target{Objects.requireNonNull(logicalActor);require(generation>=0,"MONSTER_STATUS_TARGET_GENERATION");}}
    public record Key(UUID world,UUID actor,long generation,String affix,Target target){
        public Key{Objects.requireNonNull(world);Objects.requireNonNull(actor);Objects.requireNonNull(target);require(generation>=0&&STATUSES.containsKey(affix),"MONSTER_STATUS_LOCK_KEY");}
    }
    public record Package(MonsterAffixSource source,StatusApplication application,Key key,double lockSeconds,
            double poisonPower,double statFraction,double durationSeconds,double horizontalMeters,String randomSeed){
        public CriticalRoller random(){var stream=new GearRandom(randomSeed).stream("me.status/roll");return new CriticalRoller(stream::nextDouble);}
    }
    private final TimedOpportunityLedger<Key> locks=new TimedOpportunityLedger<>(8192);
    public static boolean requiresSource(EnemyDescriptor actor){return actor.ownAffixes().stream().anyMatch(a->Set.of("ME-014","ME-025").contains(a.affixId()));}
    public List<Package> prepare(EnemyDescriptor actor,EnemyAppliedHit hit,Source source,Target target,String frozenSeed){
        var offense=hit.offense();var identity=offense.identity();
        require(identity.worldId().equals(actor.worldId())&&identity.actorId().equals(actor.logicalActorId())
                &&offense.generation()==actor.encounterGeneration()&&offense.balanceRevision().equals(actor.balanceRevision())
                &&offense.nativeBindingRevision().equals(actor.nativeBindingRevision()),"MONSTER_STATUS_RECEIPT_BINDING");
        require(frozenSeed!=null&&!frozenSeed.isBlank(),"MONSTER_STATUS_RANDOM_SEED");
        if(hit.actualHealthLoss()<=0||offense.procCoefficient()<=0||!requiresSource(actor))return List.of();
        Objects.requireNonNull(source,"MONSTER_STATUS_SOURCE_NOT_CERTIFIED");Objects.requireNonNull(target);
        var affixes=new ArrayList<>(actor.ownAffixes());
        var result=new ArrayList<Package>();
        for(var affix:affixes){
            if(!Set.of("ME-014","ME-025").contains(affix.affixId()))continue;
            String status=STATUSES.get(affix.affixId());if(status==null)continue;
            double chance=StatusApplication.merge(source.existingChance().get(status),affix.value("statusChance"),offense.procCoefficient());
            var application=new StatusApplication(actor.entityId(),hit.victim(),status,chance,source.penetration(),true,false,false,false);
            var provenance=new MonsterAffixSource(actor.worldId(),actor.logicalActorId(),actor.entityId(),actor.encounterGeneration(),affix.affixId(),actor.balanceRevision(),identity.rootId(),identity.authoredTickId());
            double power=status.equals("POISON")?offense.sourcePower()*affix.value("poisonPowerFraction"):0;
            double fraction=status.equals("WEAKEN")?affix.value("outgoingDirectReduction"):status.equals("ARMOR_BREAK")?affix.value("defenseReduction"):0;
            String seed=com.inigmasgames.hytalerpg.progress.RewardIntent.digest(new com.google.gson.Gson().toJson(List.of("me.status",frozenSeed,
                    actor.worldId(),actor.logicalActorId(),actor.encounterGeneration(),identity.rootId(),identity.executionId(),identity.authoredTickId(),affix.affixId(),target.logicalActor(),target.generation())));
            result.add(new Package(provenance,application,new Key(actor.worldId(),actor.logicalActorId(),actor.encounterGeneration(),affix.affixId(),target),
                    affix.value("opportunityLockMs")/1000,power,fraction,fraction>0?affix.value("durationMs")/1000:0,
                    status.equals("KNOCKBACK")?affix.value("horizontalDisplacementMeters"):0,seed));
        }
        return List.copyOf(result);
    }
    /** Passed into StatusService.admit; never claim before native eligibility checks. */
    public boolean claim(Package packet,double now){return locks.claim(packet.key(),now,packet.lockSeconds())==TimedOpportunityLedger.Result.CLAIMED;}
    public List<TimedOpportunityLedger.Remaining<Key>> snapshot(double now){return locks.snapshot(now);}
    public void restore(List<TimedOpportunityLedger.Remaining<Key>> saved,double now){
        for(var entry:saved)require(entry.seconds()<=3,"MONSTER_STATUS_SAVED_LOCK_DURATION");locks.restore(saved,now);
    }
}
