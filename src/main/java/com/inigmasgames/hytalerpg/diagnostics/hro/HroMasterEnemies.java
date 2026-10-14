package com.inigmasgames.hytalerpg.diagnostics.hro;

import com.inigmasgames.hytalerpg.enemies.*;
import com.inigmasgames.hytalerpg.progress.*;
import com.inigmasgames.hytalerpg.gear.GearLootService;
import java.util.*;

/** Immutable observations at existing owners. This adapter owns no capture, RNG, state or IO. */
public final class HroMasterEnemies {
    private HroMasterEnemies(){}
    private static void emit(UUID player,UUID world,String contract,String boundary,String operation,Map<String,?> facts){
        try{HroRuntime.observe(player,contract,"MasterEnemies",boundary,operation,world,null,null,null,facts);}
        catch(RuntimeException diagnostic){HroRuntime.loss(player,contract,operation,"ME12_OBSERVER_FAILURE");}
    }
    public static void planner(UUID world,UUID encounter,Me12PlannerCache.Resolved resolved,Me12PlannerCache.Metrics metrics,long elapsed){
        UUID player=HroRuntime.ownerInWorld(world);if(player==null)return;
        try{
            var graph=resolved.compiled();var plan=resolved.plan();long[] masses=graph.tierMasses();
            long selected=masses[(plan.prefix().tier()-1)*3+plan.suffix().tier()-1],total=Arrays.stream(masses).sum();
            emit(player,world,"PERFORMANCE","ME12_PLANNER",encounter.toString(),Map.of(
                    "cacheBytes",metrics.accountedBytes(),"cacheOutstanding",metrics.outstanding(),"cacheHits",metrics.hits(),"cacheMisses",metrics.misses(),
                    "compileNanos",graph.compileNanos(),"elapsedNanos",elapsed,"builderCount",metrics.builders(),"memoStates",graph.states()));
            emit(player,world,"SPAWN.PROMOTION","ME12_TIER_SUPPORT",encounter.toString(),Map.of("tierMasses",Arrays.toString(masses),
                    "conditionedProbability",selected+"/"+total,"contextKey",graph.key(),"difficultySlots",plan.difficultySlots()));
        }catch(RuntimeException unavailable){HroRuntime.loss(player,"SPAWN.PROMOTION",encounter.toString(),"ME12_PLANNER_OBSERVATION_UNAVAILABLE");}
    }
    public static void quantity(EncounterContributions.DeathPlan death,GearLootService.DeathPicks picks){
        UUID player=HroRuntime.ownerInWorld(death.spawn().world());if(player==null||picks.quantity()==null)return;
        var q=picks.quantity();var c=q.context();
        emit(player,death.spawn().world(),"PERSISTENCE.RECOVERY","ME12_QUANTITY",death.spawn().eventId(),Map.of(
                "baseCount",picks.decision().succeeded(),"finalCount",q.total(),"quantityFactor",c.quantityFactor(),"xpFactor",c.xpFactor(),
                "threatHundredths",c.v2().threatHundredths(),"policyDigest",c.v2().policyDigest(),"generatorRevision",q.generationRevision()));
        emit(player,death.spawn().world(),"PERSISTENCE.RECOVERY","ME12_QUANTITY_INPUT",death.spawn().eventId(),Map.of(
                "bonusThreat",(c.v2().threatHundredths()-100)/100.,"expectedCount",Math.min(16,picks.decision().succeeded()*c.quantityFactor()),
                "seedIdentity",RewardIntent.digest(c.v2().policyDigest()+"\n"+death.spawn().eventId()),"quantityDomain",Me12Random.QUANTITY));
    }
    public static void gear(GearLootService.Loot row){
        if(!row.guaranteedEntitlement())return;UUID player=HroRuntime.ownerInWorld(row.source().world());if(player==null)return;
        emit(player,row.source().world(),"PERSISTENCE.RECOVERY",row.result()==null?"ME12_GENERATOR_HOLD":"ME12_GEAR_RESULT",row.source().eventId(),Map.of(
                "childId",row.source().eventId(),"seedIdentity",RewardIntent.digest(row.seed()),"magicFind",row.magicFind(),"result",row.state(),
                "generatorRevision",row.generationRevision(),"quality",row.result()==null?"PENDING":row.result().item().rarity().name(),"reason",row.reason()));
    }
    public static void learning(UUID player,LearningSources.Opportunity opportunity,RewardCheckpoint before,EarnedReward result){
        if(!HroRuntime.active(player))return;
        var facts=new LinkedHashMap<String,Object>();facts.put("abilityKind",opportunity.abilityKind());facts.put("abilityId",opportunity.skill());
        facts.put("sourceId",opportunity.source());facts.put("wisdom",opportunity.effectiveWisdom());facts.put("chance",opportunity.chance());
        double base=ProgressionMath.learnChance(opportunity.rarity(),opportunity.effectiveWisdom());
        var context=opportunity.enemyRewards();double rarity=context==null?base:context.economic()?Math.min(.95,base+Math.min(base*(context.learningMultiplier()-1),context.learningPointCap())):0;
        facts.put("baseChance",base);facts.put("rarityChance",rarity);facts.put("affixDelta",Math.max(0,opportunity.chance()-rarity));
        facts.put("pityBefore",before.acquisition().progress().pity().getOrDefault(opportunity.source(),0));
        facts.put("result",result.progression()==null?"INELIGIBLE_OR_ALREADY_ACQUIRED":result.progression().kind());
        facts.put("durable",false);
        emit(player,null,"PERSISTENCE.RECOVERY","ME12_LEARN_DECISION",result.eventId(),facts);
    }
    public static void committed(UUID player,RewardIntent intent){
        if(!HroRuntime.active(player))return;var reward=intent.reward();var delta=reward.progression();
        emit(player,null,"PERSISTENCE.RECOVERY","ME12_REWARD_COMMITTED",reward.eventId(),Map.of("receiptId",intent.hash(),"value",reward.characterXp(),"units","XP","durable",true));
        if(delta!=null&&delta.source()!=null&&!delta.source().isEmpty())emit(player,null,"PERSISTENCE.RECOVERY","ME12_LEARN_COMMITTED",reward.eventId(),Map.of(
                "abilityId",delta.subject(),"sourceId",delta.source(),"result",delta.kind(),"pityBefore",intent.before().acquisition().progress().pity().getOrDefault(delta.source(),0),
                "pityAfter",intent.after().acquisition().progress().pity().getOrDefault(delta.source(),0),"receiptId",intent.hash(),"durable",true));
    }
    public static void identity(EnemyBirthPlan birth){
        UUID player=HroRuntime.ownerInWorld(birth.world());if(player==null||birth.schemaVersion()!=2)return;
        var leader=birth.actors().getFirst();var v2=leader.v2();var plan=v2.plan();
        emit(player,birth.world(),"SPAWN.PROMOTION","ME12_IDENTITY",birth.encounter().toString(),Map.of(
                "prefix",plan.prefix().text(),"suffix",plan.suffix().text(),"prefixTier",plan.prefix().tier(),"suffixTier",plan.suffix().tier(),
                "affixCount",plan.affixes().size(),"generation",birth.generation(),"policyDigest",v2.policyDigest(),"sourceReceipt",v2.nativeSourceReceipt(),
                "nativeRole",leader.nativeRoleId(),"difficulty",leader.difficulty()));
        for(var owned:plan.affixes())emit(player,birth.world(),"SPAWN.PROMOTION","ME12_SELECTED_AFFIX",birth.encounter()+"/"+owned.choice().affixId(),Map.of(
                "encounter",birth.encounter(),"affixId",owned.choice().affixId(),"selector",String.valueOf(owned.choice().selector()),"slotOwner",owned.owner(),
                "signature",owned.choice().affixId().equals(plan.prefix().signature())?"PREFIX":owned.choice().affixId().equals(plan.suffix().signature())?"SUFFIX":"NONE"));
    }
    public static void hit(EnemyDescriptor actor,EnemyAppliedHit hit){
        UUID player=HroRuntime.ownerInWorld(actor.worldId());if(player==null)return;var id=hit.offense().identity();
        String operation=RewardIntent.digest(id.rootId()+"/"+id.authoredTickId()+"/"+actor.logicalActorId()+"/"+hit.victim());
        for(var component:hit.components().entrySet()){
            var result=component.getValue();emit(player,actor.worldId(),"COMBAT.RECONCILE","ME12_NATIVE_HIT",operation+"/"+component.getKey(),Map.of(
                    "rootId",id.rootId(),"nativeAction",id.executionId(),"targetId",hit.victim(),"generation",actor.encounterGeneration(),
                    "healthBefore",result.healthBefore(),"healthAfter",result.healthAfter(),"applied",EnemyAppliedHit.healthLoss(result),
                    "before",hit.offense().sourceVector().getOrDefault(component.getKey(),0d),"after",hit.offense().affixedVector().get(component.getKey()),
                    "proof","GROUPED_NATIVE_RECEIPT_NOT_INDIVIDUAL_AFFIX_CAUSALITY"));
        }
    }
    public static void effect(EnemyDescriptor actor,EnemyAppliedHit hit,String affix,double requested,double actual,String gate){
        UUID player=HroRuntime.ownerInWorld(actor.worldId());if(player==null)return;
        emit(player,actor.worldId(),"AFFIX.PROC","ME12_EFFECT_RESULT",RewardIntent.digest(hit.offense().identity().rootId()+"/"+hit.offense().identity().authoredTickId()+"/"+hit.victim()+"/"+affix),
                Map.of("affixId",affix,"requested",requested,"applied",actual,"result",gate,"generation",actor.encounterGeneration(),"targetId",hit.victim(),"rootId",hit.offense().identity().rootId()));
    }
}
