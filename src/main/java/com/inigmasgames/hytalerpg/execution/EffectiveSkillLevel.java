package com.inigmasgames.hytalerpg.execution;

import com.inigmasgames.hytalerpg.progress.ProgressionMath;

/** Canonical mastery plus authoritative item-granted skill levels. Levels above 20 remain meaningful. */
public final class EffectiveSkillLevel {
    private EffectiveSkillLevel(){ }

    public static int resolve(long masteryXp,int itemGrantedLevels){
        return resolveBase(ProgressionMath.masteryLevel(masteryXp),itemGrantedLevels);
    }
    public static int resolveBase(int baseRank,int itemGrantedLevels){
        if(baseRank<1||baseRank>20)throw new IllegalArgumentException("INVALID_BASE_SKILL_RANK");
        if(itemGrantedLevels<0||itemGrantedLevels>1000)throw new IllegalArgumentException("INVALID_ITEM_SKILL_LEVELS");
        return Math.addExact(baseRank,itemGrantedLevels);
    }

    public static int summonSkeletonArchers(int effectiveSkillLevel){
        if(effectiveSkillLevel<1||effectiveSkillLevel>1001)throw new IllegalArgumentException("INVALID_EFFECTIVE_SKILL_LEVEL");
        return 1+(effectiveSkillLevel-1)/2;
    }
}
