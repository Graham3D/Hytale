package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.gear.GearRandom;
import java.util.*;

/** Frozen inputs for the existing XP, acquisition and equipment owners; no reward delivery here. */
public record EnemyRewardContext(String balanceRevision,EnemyRarity rarity,Origin origin,boolean minion,
                                 int ownAffixCount,double rarityXp,double rarityQuantity,double learningMultiplier,
                                 double learningPointCap,double xpPerAdditionalAffix,double quantityPerAdditionalAffix,
                                 Me12Identity.RewardReference v2,Me12RewardMath.Factors v2Factors) {
    public EnemyRewardContext(String balanceRevision,EnemyRarity rarity,Origin origin,boolean minion,
            int ownAffixCount,double rarityXp,double rarityQuantity,double learningMultiplier,double learningPointCap,
            double xpPerAdditionalAffix,double quantityPerAdditionalAffix,Me12Identity.RewardReference v2){
        this(balanceRevision,rarity,origin,minion,ownAffixCount,rarityXp,rarityQuantity,learningMultiplier,learningPointCap,xpPerAdditionalAffix,quantityPerAdditionalAffix,v2,null);
    }
    public EnemyRewardContext(String balanceRevision,EnemyRarity rarity,Origin origin,boolean minion,
            int ownAffixCount,double rarityXp,double rarityQuantity,double learningMultiplier,double learningPointCap,
            double xpPerAdditionalAffix,double quantityPerAdditionalAffix){
        this(balanceRevision,rarity,origin,minion,ownAffixCount,rarityXp,rarityQuantity,learningMultiplier,learningPointCap,xpPerAdditionalAffix,quantityPerAdditionalAffix,null);
    }
    public enum Origin { NATURAL, AUTHORED_ENCOUNTER, INITIAL_PACK_MINION, COMBAT_SUMMON, REVIVED, PLAYER_OWNED, QA }
    public EnemyRewardContext {
        Objects.requireNonNull(rarity);Objects.requireNonNull(origin);
        EnemyAffixRegistry.require(balanceRevision!=null&&!balanceRevision.isBlank()&&ownAffixCount>=0
                &&ownAffixCount<=(v2!=null?8:origin==Origin.QA?27:4),"INVALID_ENEMY_REWARD_CONTEXT");
        EnemyAffixRegistry.require(v2==null||rarity==EnemyRarity.UNIQUE&&origin==Origin.NATURAL&&!minion&&ownAffixCount==v2.ownAffixCount(),"ME12_REWARD_SCOPE");
        EnemyAffixRegistry.require(!minion||rarity==EnemyRarity.NORMAL&&ownAffixCount==0,"MINION_CANNOT_INHERIT_REWARD_RARITY");
        EnemyAffixRegistry.require(origin!=Origin.INITIAL_PACK_MINION||minion,"INITIAL_MINION_RELATION_REQUIRED");
        for(double value:new double[]{rarityXp,rarityQuantity,learningMultiplier,learningPointCap,xpPerAdditionalAffix,quantityPerAdditionalAffix})
            EnemyAffixRegistry.require(Double.isFinite(value)&&value>=0,"INVALID_ENEMY_REWARD_FACTOR");
        EnemyAffixRegistry.require(rarityQuantity>=1&&learningMultiplier>=1&&learningPointCap<=.10,"INVALID_ENEMY_REWARD_CAP");
        if(v2!=null){
            var frozen=Me12RewardMath.freeze(v2.threatHundredths(),rarityXp,rarityQuantity);
            EnemyAffixRegistry.require(v2Factors==null||v2Factors.equals(frozen),"ME12_FROZEN_REWARD_FACTOR_MISMATCH");
            v2Factors=frozen; // Also upgrades the offline Stage 2 input-only record without consulting live policy.
        }else EnemyAffixRegistry.require(v2Factors==null,"ME12_FACTORS_REQUIRE_V2");
    }
    public boolean economic(){return origin==Origin.NATURAL||origin==Origin.AUTHORED_ENCOUNTER||origin==Origin.INITIAL_PACK_MINION;}
    public double xpFactor(){return v2!=null?Double.parseDouble(v2Factors.xp()):economic()?rarityXp*(1+xpPerAdditionalAffix*Math.clamp(ownAffixCount-1,0,3)):0;}
    public double quantityFactor(){return v2!=null?Double.parseDouble(v2Factors.quantity()):economic()?rarityQuantity*(1+quantityPerAdditionalAffix*Math.clamp(ownAffixCount-1,0,3)):0;}
    public double learningChance(double existingChance){
        EnemyAffixRegistry.require(Double.isFinite(existingChance)&&existingChance>=0&&existingChance<=.95,"INVALID_EXISTING_LEARNING_CHANCE");
        double rarityChance=economic()?Math.min(.95,existingChance+Math.min(existingChance*(learningMultiplier-1),learningPointCap)):0;
        return v2==null?rarityChance:Me12RewardMath.learning(rarityChance,v2.threatHundredths());
    }
    public int bonusEquipmentSlots(int finalizedBaseSlots,String defeatId){
        EnemyAffixRegistry.require(finalizedBaseSlots>=0&&finalizedBaseSlots<=16,"FINALIZED_EQUIPMENT_SLOT_CAP");
        if(!economic()||finalizedBaseSlots==0)return 0;
        validateEquipmentMaximum(finalizedBaseSlots);
        if(v2!=null)return Me12RewardMath.equipment(finalizedBaseSlots,v2Factors,v2.policyDigest(),defeatId)-finalizedBaseSlots;
        double bonus=finalizedBaseSlots*(quantityFactor()-1);int floor=(int)Math.floor(bonus);
        if(new GearRandom("master-enemies-v1.1/"+balanceRevision+"/"+defeatId).stream("me.quantity/defeat").nextDouble()<bonus-floor)floor++;
        return floor;
    }
    /** Validate an enabled native loot profile before birth; never erase bonus slots to fit the cap. */
    public void validateEquipmentMaximum(int profileMaximumEquipment){
        EnemyAffixRegistry.require(profileMaximumEquipment>=0&&profileMaximumEquipment<=16,"NATIVE_EQUIPMENT_PROFILE_CAP");
        if(v2==null)EnemyAffixRegistry.require(Math.ceil(profileMaximumEquipment*quantityFactor())<=16,"ENEMY_EQUIPMENT_QUANTITY_EXCEEDS_CAP");
    }
    public static EnemyRewardContext canonical(EnemyRarity rarity,Origin origin,boolean minion,int count){
        return EnemyBalance.canonical().rewards(rarity,origin,minion,count);
    }
}
