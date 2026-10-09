package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.gear.GearRandom;
import java.util.*;

/** Frozen inputs for the existing XP, acquisition and equipment owners; no reward delivery here. */
public record EnemyRewardContext(String balanceRevision,EnemyRarity rarity,Origin origin,boolean minion,
                                 int ownAffixCount,double rarityXp,double rarityQuantity,double learningMultiplier,
                                 double learningPointCap,double xpPerAdditionalAffix,double quantityPerAdditionalAffix) {
    public enum Origin { NATURAL, AUTHORED_ENCOUNTER, INITIAL_PACK_MINION, COMBAT_SUMMON, REVIVED, PLAYER_OWNED, QA }
    public EnemyRewardContext {
        Objects.requireNonNull(rarity);Objects.requireNonNull(origin);
        EnemyAffixRegistry.require(balanceRevision!=null&&!balanceRevision.isBlank()&&ownAffixCount>=0
                &&ownAffixCount<=(origin==Origin.QA?27:4),"INVALID_ENEMY_REWARD_CONTEXT");
        EnemyAffixRegistry.require(!minion||rarity==EnemyRarity.NORMAL&&ownAffixCount==0,"MINION_CANNOT_INHERIT_REWARD_RARITY");
        EnemyAffixRegistry.require(origin!=Origin.INITIAL_PACK_MINION||minion,"INITIAL_MINION_RELATION_REQUIRED");
        for(double value:new double[]{rarityXp,rarityQuantity,learningMultiplier,learningPointCap,xpPerAdditionalAffix,quantityPerAdditionalAffix})
            EnemyAffixRegistry.require(Double.isFinite(value)&&value>=0,"INVALID_ENEMY_REWARD_FACTOR");
        EnemyAffixRegistry.require(rarityQuantity>=1&&learningMultiplier>=1&&learningPointCap<=.10,"INVALID_ENEMY_REWARD_CAP");
    }
    public boolean economic(){return origin==Origin.NATURAL||origin==Origin.AUTHORED_ENCOUNTER||origin==Origin.INITIAL_PACK_MINION;}
    public double xpFactor(){return economic()?rarityXp*(1+xpPerAdditionalAffix*Math.clamp(ownAffixCount-1,0,3)):0;}
    public double quantityFactor(){return economic()?rarityQuantity*(1+quantityPerAdditionalAffix*Math.clamp(ownAffixCount-1,0,3)):0;}
    public double learningChance(double existingChance){
        EnemyAffixRegistry.require(Double.isFinite(existingChance)&&existingChance>=0&&existingChance<=.95,"INVALID_EXISTING_LEARNING_CHANCE");
        return economic()?Math.min(.95,existingChance+Math.min(existingChance*(learningMultiplier-1),learningPointCap)):0;
    }
    public int bonusEquipmentSlots(int finalizedBaseSlots,String defeatId){
        EnemyAffixRegistry.require(finalizedBaseSlots>=0&&finalizedBaseSlots<=16,"FINALIZED_EQUIPMENT_SLOT_CAP");
        if(!economic()||finalizedBaseSlots==0)return 0;
        validateEquipmentMaximum(finalizedBaseSlots);
        double bonus=finalizedBaseSlots*(quantityFactor()-1);int floor=(int)Math.floor(bonus);
        if(new GearRandom("master-enemies-v1.1/"+balanceRevision+"/"+defeatId).stream("me.quantity/defeat").nextDouble()<bonus-floor)floor++;
        return floor;
    }
    /** Validate an enabled native loot profile before birth; never erase bonus slots to fit the cap. */
    public void validateEquipmentMaximum(int profileMaximumEquipment){
        EnemyAffixRegistry.require(profileMaximumEquipment>=0&&profileMaximumEquipment<=16,"NATIVE_EQUIPMENT_PROFILE_CAP");
        EnemyAffixRegistry.require(Math.ceil(profileMaximumEquipment*quantityFactor())<=16,"ENEMY_EQUIPMENT_QUANTITY_EXCEEDS_CAP");
    }
    public static EnemyRewardContext canonical(EnemyRarity rarity,Origin origin,boolean minion,int count){
        return EnemyBalance.canonical().rewards(rarity,origin,minion,count);
    }
}
