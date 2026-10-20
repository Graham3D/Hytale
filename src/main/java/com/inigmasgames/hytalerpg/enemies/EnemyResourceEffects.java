package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.combat.resource.*;
import com.inigmasgames.hytalerpg.execution.TimedOpportunityLedger;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Per-completed-hit native Mana debit and Health credit. No rolling resource window. */
public final class EnemyResourceEffects {
    public record BudgetKey(String kind,UUID world,UUID actor,long generation){
        public BudgetKey{
            Objects.requireNonNull(actor);
            if(!Set.of("MANA_BURN","VAMPIRIC").contains(kind)||generation<0
                    ||kind.equals("MANA_BURN")&&(world!=null||generation!=0)||kind.equals("VAMPIRIC")&&world==null)
                throw new IllegalArgumentException("ENEMY_RESOURCE_BUDGET_KEY");
        }
    }
    public record Opportunity(UUID world,UUID source,long generation,UUID victim){
        public Opportunity{Objects.requireNonNull(world);Objects.requireNonNull(source);Objects.requireNonNull(victim);
            if(generation<0)throw new IllegalArgumentException("ENEMY_RESOURCE_OPPORTUNITY");}
    }
    public record Result(String gate,double allowed,double actual){}
    public record Saved(List<RollingReceiptBudget.Saved<BudgetKey>> budgets,List<TimedOpportunityLedger.Remaining<Opportunity>> locks){
        public Saved{budgets=List.copyOf(budgets);locks=List.copyOf(locks);}
    }
    private final RpgResourceService resources;
    public EnemyResourceEffects(RpgResourceService resources){this.resources=Objects.requireNonNull(resources);}

    public synchronized Result manaBurn(EnemyDescriptor actor,EnemyAppliedHit hit,NativeResourcePort victim,long now,BooleanSupplier current){
        var affix=eligible(actor,hit,"ME-013",current);
        if(affix==null||!victim.hasResource(ResourceType.MANA))return none("INELIGIBLE");
        double maximum=victim.maximum(ResourceType.MANA);
        double request=Math.min(maximum*affix.value("maxManaFraction"),hit.actualHealthLoss()*affix.value("damageFractionCap"));
        double actual=request>0?resources.drainSpendableMana(hit.victim(),request,victim,current):0;
        return new Result(actual>0?"APPLIED":"EMPTY",request,actual);
    }
    public synchronized Result vampiric(EnemyDescriptor actor,EnemyAppliedHit hit,NativeResourcePort source,long now,BooleanSupplier current){
        var affix=eligible(actor,hit,"ME-016",current);
        if(affix==null||!source.hasResource(ResourceType.HEALTH)||source.current(ResourceType.HEALTH)<=0)return none("INELIGIBLE");
        double request=hit.actualHealthLoss()*affix.value("healthLeechFraction");
        double actual=request>0?resources.creditLivingHealth(request,source,current):0;
        return new Result(actual>0?"APPLIED":"EMPTY",request,actual);
    }
    private static EnemyDescriptor.AffixInstance eligible(EnemyDescriptor actor,EnemyAppliedHit hit,String affixId,BooleanSupplier current){
        var offense=hit.offense();
        if(!actor.logicalActorId().equals(offense.identity().actorId())||!actor.worldId().equals(offense.identity().worldId())
                ||actor.encounterGeneration()!=offense.generation()||!actor.balanceRevision().equals(offense.balanceRevision())
                ||!actor.nativeBindingRevision().equals(offense.nativeBindingRevision()))throw new IllegalArgumentException("ENEMY_RESOURCE_RECEIPT_BINDING");
        if(hit.actualHealthLoss()<=0||!current.getAsBoolean())return null;
        return actor.ownAffixes().stream().filter(value->value.affixId().equals(affixId)).findFirst().orElse(null);
    }
    private static String receipt(EnemyAppliedHit hit){
        var id=hit.offense().identity();
        return com.inigmasgames.hytalerpg.progress.RewardIntent.digest("me.resource/receipt/"+new com.google.gson.Gson().toJson(
                List.of(id.worldId(),id.actorId(),hit.offense().generation(),id.rootId(),id.executionId(),id.authoredTickId(),hit.victim())));
    }
    private static Result result(RollingReceiptBudget.Result result){return new Result(result.gate().name(),result.allowed(),result.actual());}
    private static Result none(String gate){return new Result(gate,0,0);}
    public synchronized Saved snapshot(long now){return new Saved(List.of(),List.of());}
    public synchronized void restore(Saved saved,long now){
        Objects.requireNonNull(saved);
    }
}
