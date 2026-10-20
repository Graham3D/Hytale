package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets;
import java.util.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.*;

/** ME provider composition for existing native Health/movement, Defense and direct-damage owners. */
public record EnemyAffixSnapshot(double rarityHealthFactor,double rarityDirectFactor,double maxHealthIncrease,
        double movementMultiplier,double recoveryRateMultiplier,double allDirectIncrease,double physicalIncrease,
        double stoneSkinDefenseRating,Map<String,Double> extraPowerFractions,double spectralFraction,Map<String,Double> resistanceAdds,
        boolean stunStaggerImmune,boolean slowImmune,boolean enraged,Selector auraSelector,int avengerStacks,
        boolean blocksExternalMutation,boolean blocksConversion) {
    public EnemyAffixSnapshot{
        extraPowerFractions=Collections.unmodifiableMap(new TreeMap<>(extraPowerFractions));
        resistanceAdds=Collections.unmodifiableMap(new TreeMap<>(resistanceAdds));
    }
    public boolean auraMember(){return auraSelector!=null;}
    public double projectedMaximumHealth(double difficultyMaximum){
        require(Double.isFinite(difficultyMaximum)&&difficultyMaximum>0,"INVALID_NATIVE_DIFFICULTY_HEALTH");
        double result=difficultyMaximum*rarityHealthFactor*(1+maxHealthIncrease);
        require(Double.isFinite(result)&&result<=Float.MAX_VALUE,"ENEMY_HEALTH_OVERFLOW");return result;
    }
    /** Durable pack receipts advance protection and Avenger without replaying birth Health or engagement time. */
    public EnemyAffixSnapshot withPackProtection(EnemyDescriptor actor,EnemyPackRecord pack,EnemyBalance balance){
        require(pack!=null&&actor.packId()!=null&&actor.packId().equals(pack.packId())
                &&actor.worldId().equals(pack.worldId())&&actor.encounterGeneration()==pack.generation()
                &&pack.contains(actor.logicalActorId())&&actor.balanceRevision().equals(balance.revision()),"ENEMY_PACK_PROTECTION_BINDING");
        int stacks=avengerStacks;double direct=allDirectIncrease,movement=movementMultiplier;
        var avenger=actor.own(Operator.AVENGER);
        if(avenger.isPresent()){
            require(actor.logicalActorId().equals(pack.leaderId()),"AVENGER_PACK_LEADER_REQUIRED");
            var source=avenger.get();
            int count=(int)Math.min(source.value("maxStacks"),pack.birthRoster().stream()
                    .filter(member->member.role()==EnemyPackRecord.Role.MINION
                            &&pack.deadMemberReceipts().containsKey(member.logicalActorId())).count());
            require(count>=avengerStacks,"AVENGER_RECEIPT_ROLLBACK");
            int gained=count-avengerStacks;stacks=count;
            direct=Math.min(balance.caps().allDirectIncrease(),direct+gained*source.value("perDefeatedMinionDirectIncrease"));
            movement=Math.min(balance.caps().movementMultiplier(),movement+gained*source.value("perDefeatedMinionMovementIncrease"));
        }
        return new EnemyAffixSnapshot(rarityHealthFactor,rarityDirectFactor,maxHealthIncrease,movement,
                recoveryRateMultiplier,direct,physicalIncrease,stoneSkinDefenseRating,extraPowerFractions,
                spectralFraction,resistanceAdds,stunStaggerImmune,slowImmune,enraged,auraSelector,stacks,
                pack.blocksExternalMutation(actor.logicalActorId()),pack.blocksConversion(actor.logicalActorId()));
    }
    public ModifierBuckets directModifiers(boolean physical){
        return new ModifierBuckets(physical?List.of(allDirectIncrease,physicalIncrease):List.of(allDirectIncrease),List.of(),List.of(),List.of());
    }
    /** Revised ME-004 is additive Dother. It never supplies the former global percentage increase. */
    public com.inigmasgames.hytalerpg.combat.defense.DefenseView.Contributions defenseContributions(
            com.inigmasgames.hytalerpg.combat.defense.DefenseView.Contributions baseline){
        return new com.inigmasgames.hytalerpg.combat.defense.DefenseView.Contributions(baseline.shieldRating(),
                baseline.otherRating()+stoneSkinDefenseRating,baseline.globalDefenseIncreased(),baseline.winningDefenseBreakFraction());
    }
    public static EnemyAffixSnapshot resolve(EnemyDescriptor descriptor,EnemyBalance balance,EnemyPackRecord pack,
            long engagedMillis,boolean mobile,boolean scalableRecovery){
        return resolve(descriptor,balance,pack,engagedMillis,mobile,scalableRecovery,null,0);
    }
    public static EnemyAffixSnapshot resolve(EnemyDescriptor descriptor,EnemyBalance balance,EnemyPackRecord pack,
            long engagedMillis,boolean mobile,boolean scalableRecovery,com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects effects,double now){
        require(descriptor.balanceRevision().equals(balance.revision())&&engagedMillis>=0,"SNAPSHOT_BALANCE_OR_TIME");
        if(descriptor.packId()!=null)require(pack!=null&&pack.packId().equals(descriptor.packId())&&pack.worldId().equals(descriptor.worldId())
                &&pack.generation()==descriptor.encounterGeneration()&&pack.contains(descriptor.logicalActorId()),"SNAPSHOT_PACK_BINDING");
        var rarity=balance.rarity(descriptor.enemyRarity(),descriptor.packRole()==EnemyDescriptor.PackRole.MINION);
        double health=0,movement=0,recovery=0,direct=0,physical=0,defense=0,spectral=0;
        boolean stun=false,slow=false,enraged=false;Selector auraSelector=null;int avenger=0;
        var extra=new TreeMap<String,Double>();var resistance=new TreeMap<String,Double>();
        var providers=new ArrayList<>(descriptor.ownAffixes());providers.addAll(descriptor.inheritedAffixes());
        for(var affix:providers){
            Operator op=Operator.values()[Integer.parseInt(affix.affixId().substring(3))-1];
            boolean inherited=affix.origin()==EnemyDescriptor.AffixOrigin.INHERITED;
            switch(op){
                case EXTRA_FAST->{movement+=affix.value("movementIncrease");if(!inherited)recovery+=affix.value("recoveryRateIncrease");}
                case EXTRA_STRONG->physical+=affix.value("physicalIncrease");
                case MAGIC_RESISTANT->{for(String channel:CHANNELS.subList(1,7))resistance.merge(channel,affix.value("elementalResistanceAdd"),Double::sum);}
                case STONE_SKIN->defense+=affix.value("defenseRatingScale")*com.inigmasgames.hytalerpg.combat.defense.DefenseView.scale(descriptor.combatLevel());
                case FIRE_ENCHANTED,COLD_ENCHANTED,LIGHTNING_ENCHANTED,POISON_ENCHANTED,WIND_ENCHANTED,EARTH_ENCHANTED,VOID_ENCHANTED->{
                    String channel=switch(op){case FIRE_ENCHANTED->"FIRE";case COLD_ENCHANTED->"WATER";case LIGHTNING_ENCHANTED->"LIGHTNING";
                        case WIND_ENCHANTED->"WIND";case POISON_ENCHANTED,EARTH_ENCHANTED->"EARTH";case VOID_ENCHANTED->"VOID";default->throw new AssertionError();};
                    extra.merge(channel,affix.value("extraPowerFraction"),Double::sum);
                    if(!inherited)resistance.merge(channel,affix.value("resistanceAdd"),Double::sum);
                }
                case SPECTRAL_HIT->spectral+=affix.value("extraPowerFraction");
                case UNWAVERING->stun=true;
                case UNSTOPPABLE->slow=true;
                case FRENZIED->{
                    long phase=engagedMillis%(long)affix.value("cycleMs");
                    enraged=phase>=affix.value("activeWindowStartMs")&&phase<affix.value("activeWindowStartMs")+affix.value("activeWindowDurationMs");
                    if(enraged){direct+=affix.value("allDirectIncrease");movement+=affix.value("movementIncrease");recovery+=affix.value("recoveryRateIncrease");}
                }
                case AVENGER->{
                    require(pack!=null&&descriptor.logicalActorId().equals(pack.leaderId()),"AVENGER_SEALED_LEADER_REQUIRED");
                    avenger=(int)Math.min(affix.value("maxStacks"),pack.birthRoster().stream().filter(m->m.role()==EnemyPackRecord.Role.MINION
                            &&pack.deadMemberReceipts().containsKey(m.logicalActorId())).count());
                    direct+=avenger*affix.value("perDefeatedMinionDirectIncrease");movement+=avenger*affix.value("perDefeatedMinionMovementIncrease");
                }
                case EMPOWERED_MINIONS->{if(inherited){health+=affix.value("minionMaxHealthIncrease");direct+=affix.value("minionDirectIncrease");movement+=affix.value("minionMovementIncrease");}}
                // These retain their receipt/status/resource/pack/shield owners; they are not unconditional stat bonuses.
                case MANA_BURN,CURSED,KNOCKBACK,VAMPIRIC,HORDE,AURA_ENCHANTED,PACKBOUND,ARMOR_BREAKER,REFLECTIVE,BULWARK->{}
            }
        }
        if(effects!=null){
            var world=descriptor.worldId();var actor=descriptor.logicalActorId();long generation=descriptor.encounterGeneration();
            double might=effects.winningStat(world,actor,generation,com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects.Stat.AURA_PHYSICAL_INCREASE,now);
            double hasteMovement=effects.winningStat(world,actor,generation,com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects.Stat.AURA_MOVEMENT_INCREASE,now);
            double hasteRecovery=effects.winningStat(world,actor,generation,com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects.Stat.AURA_RECOVERY_INCREASE,now);
            physical+=might;movement+=hasteMovement;recovery+=hasteRecovery;
            double ward=effects.winningStat(world,actor,generation,com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects.Stat.AURA_RESISTANCE_ADD,now);
            int families=(might>0?1:0)+(ward>0?1:0)+(hasteMovement>0||hasteRecovery>0?1:0);
            require(families<=1,"MULTIPLE_AURA_SELECTOR_FAMILIES");
            if(might>0)auraSelector=Selector.MIGHT;
            else if(ward>0)auraSelector=Selector.WARD;
            else if(hasteMovement>0||hasteRecovery>0)auraSelector=Selector.HASTE;
            for(String channel:CHANNELS.subList(1,7))if(ward>0)resistance.merge(channel,ward,Double::sum);
        }
        var caps=balance.caps();
        require(descriptor.spawnOrigin()==EnemyRewardContext.Origin.QA
                ||extra.size()<=1&&(extra.isEmpty()||spectral==0),"MULTIPLE_ELEMENTAL_PROVIDER_FAMILIES");
        spectral=Math.min(spectral,caps.extraElementalFraction());extra.replaceAll((channel,value)->Math.min(value,caps.extraElementalFraction()));
        var template=descriptor.templateBirth();
        return new EnemyAffixSnapshot(template==null?rarity.maxHealth():template.maxHealthFactor(),template==null?rarity.directDamage():template.directDamageFactor(),Math.min(caps.maxHealthIncrease(),health),
                mobile?Math.min(caps.movementMultiplier(),1+movement):1,scalableRecovery?Math.min(caps.recoveryMultiplier(),1+recovery):1,
                Math.min(caps.allDirectIncrease(),direct),Math.min(caps.physicalIncrease(),physical),defense,
                extra,spectral,resistance,stun,slow,enraged,auraSelector,avenger,
                pack!=null&&pack.blocksExternalMutation(descriptor.logicalActorId()),
                pack!=null&&pack.blocksConversion(descriptor.logicalActorId()));
    }
}
