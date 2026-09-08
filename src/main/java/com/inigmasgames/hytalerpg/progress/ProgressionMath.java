package com.inigmasgames.hytalerpg.progress;

import com.inigmasgames.hytalerpg.ui.CharacterXpProjectionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Master05 reference math. No eligibility decision or persistence write is hidden in these functions. */
public final class ProgressionMath {
    private static final CharacterXpProjectionService CHARACTER=new CharacterXpProjectionService();
    private static final int[] K_LEVELS={1,20,40,60,80,98,99};
    private static final double[] K_VALUES={10,10,14,18,24,32,32};
    public enum Rank {
        COMMON(1,1,1),SPECIALIST(1.5,2,2),ELITE(2.5,3,3),MINIBOSS(6,8,4),BOSS(15,15,5);
        public final double multiplier;public final int insight,signatureTierCeiling;
        Rank(double multiplier,int insight,int tier){this.multiplier=multiplier;this.insight=insight;signatureTierCeiling=tier;}
    }
    public enum Rarity { ORDINARY(1),RARE_SPAWN(1.5),UNIQUE(2);public final double multiplier;Rarity(double value){multiplier=value;} }
    public enum AcquisitionRarity { COMMON(.15),RARE(.05),UNIQUE(.02);public final double chance;AcquisitionRarity(double value){chance=value;} }
    public record CharacterAdvance(long totalXp,int level,int earnedLevels,int unspentPointAward,int pendingPointAward){}
    public static double killsScale(int level){
        level(level);for(int i=1;i<K_LEVELS.length;i++)if(level<=K_LEVELS[i])
            return K_VALUES[i-1]+(K_VALUES[i]-K_VALUES[i-1])*(level-K_LEVELS[i-1])/(K_LEVELS[i]-K_LEVELS[i-1]);
        throw new AssertionError("Validated level has no K segment");
    }
    public static long commonReward(int enemyLevel){return halfUp(100*Math.pow(level(enemyLevel),1.6)/killsScale(enemyLevel));}
    public static double levelDifference(int enemyLevel,int playerLevel){
        int difference=level(enemyLevel)-level(playerLevel);
        return difference>=5?1.15:difference>=-2?1:difference>=-5?.75:difference>=-10?.40:.10;
    }
    public static long enemyReward(int enemyLevel,Rank rank,Rarity rarity,int playerLevel){
        Objects.requireNonNull(rank);Objects.requireNonNull(rarity);
        double scarcity=rank==Rank.BOSS&&rarity==Rarity.UNIQUE?1:rarity.multiplier;
        return halfUp(commonReward(enemyLevel)*rank.multiplier*scarcity*levelDifference(enemyLevel,playerLevel));
    }
    /** Equal shares of one positive party pot; eligibility and its common level profile are caller-owned. */
    public static long equalShare(long pot,int eligibleMembers){
        if(pot<0||eligibleMembers<1||eligibleMembers>256)throw new IllegalArgumentException("INVALID_REWARD_POT");
        return pot==0?0:Math.max(1,pot/eligibleMembers);
    }
    public static CharacterAdvance advance(long totalXp,long awardedXp){
        if(totalXp<0||awardedXp<0)throw new IllegalArgumentException("NEGATIVE_CHARACTER_XP");
        // Same existing curve as the authoritative HUD projection, not a second formula.
        long next=Math.addExact(totalXp,awardedXp);int before=CHARACTER.project(totalXp).level(),after=CHARACTER.project(next).level();
        int levels=after-before,points=Math.multiplyExact(levels,5);return new CharacterAdvance(next,after,levels,points,points);
    }
    public static long masteryNext(int level){if(level<1||level>20)throw new IllegalArgumentException("MASTERY_LEVEL_RANGE");return level==20?0:halfUp(50*Math.pow(level,1.5));}
    public static int masteryLevel(long totalXp){
        if(totalXp<0)throw new IllegalArgumentException("NEGATIVE_MASTERY_XP");int level=1;
        while(level<20&&totalXp>=masteryNext(level)){totalXp-=masteryNext(level);level++;}return level;
    }
    public static double masteryMagnitude(long totalXp){return 1+.02*(masteryLevel(totalXp)-1);}
    public static double learnChance(AcquisitionRarity rarity,double effectiveWisdom){
        Objects.requireNonNull(rarity);if(!Double.isFinite(effectiveWisdom)||effectiveWisdom<0)throw new IllegalArgumentException("INVALID_EFFECTIVE_WISDOM");
        return Math.clamp(rarity.chance+Math.min(.40,.0015*effectiveWisdom),0,.95);
    }
    public static int pityFailures(AcquisitionRarity rarity){return BigDecimal.valueOf(3).divide(BigDecimal.valueOf(rarity.chance),0,RoundingMode.CEILING).intValueExact();}
    public static long insightCost(String tier){return switch(tier){case "FOUNDATION"->20;case "ADVANCED"->40;case "SPECIALIST"->80;default->throw new IllegalArgumentException("UNKNOWN_INSIGHT_TIER");};}
    private static int level(int level){if(level<1||level>99)throw new IllegalArgumentException("COMBAT_LEVEL_RANGE");return level;}
    private static long halfUp(double value){if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("INVALID_REWARD_MAGNITUDE");return BigDecimal.valueOf(value).setScale(0,RoundingMode.HALF_UP).longValueExact();}
    private ProgressionMath(){}
}
