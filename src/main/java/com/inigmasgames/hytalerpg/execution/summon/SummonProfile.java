package com.inigmasgames.hytalerpg.execution.summon;

/** Authored summon identity; never copies a source NPC's scripts, inventory or rewards. */
public record SummonProfile(String roleId, String element, double range, int count,
                            double healthFactor, double coefficient, double lifetime,
                            double attackInterval, double leash, boolean corpseRequired, boolean decoy,
                            CountPolicy countPolicy,boolean developmentFixture) {
    public enum CountPolicy { FIXED, EFFECTIVE_LEVEL_EVERY_TWO }
    public SummonProfile(String roleId,String element,double range,int count,double healthFactor,double coefficient,double lifetime,double attackInterval,double leash,boolean corpseRequired,boolean decoy){
        this(roleId,element,range,count,healthFactor,coefficient,lifetime,attackInterval,leash,corpseRequired,decoy,CountPolicy.FIXED,false);
    }
    public SummonProfile(String roleId,String element,double range,int count,double healthFactor,double coefficient,double lifetime,double attackInterval,double leash,boolean corpseRequired){
        this(roleId,element,range,count,healthFactor,coefficient,lifetime,attackInterval,leash,corpseRequired,false);
    }
    public SummonProfile(String roleId,String element,double range,int count,double healthFactor,double coefficient,double lifetime,double attackInterval,double leash){
        this(roleId,element,range,count,healthFactor,coefficient,lifetime,attackInterval,leash,false);
    }
    public SummonProfile {
        countPolicy=countPolicy==null?CountPolicy.FIXED:countPolicy;
        boolean roleAllowed=countPolicy==CountPolicy.EFFECTIVE_LEVEL_EVERY_TWO?"RPG_Summon_Skeleton_Archer".equals(roleId):roleId!=null&&roleId.startsWith("RPG_Summon_");
        if (!roleAllowed || element == null || element.isBlank()
                || count < 1 || count > SummonRegistry.OWNER_LIMIT || !positive(range,healthFactor,lifetime,attackInterval,leash)
                || !Double.isFinite(coefficient) || (decoy ? coefficient!=0||count!=1||corpseRequired : coefficient<=0)
                || attackInterval < 1 || lifetime > 60 || leash > 24)
            throw new IllegalArgumentException("Invalid bounded summon profile");
        if(countPolicy==CountPolicy.EFFECTIVE_LEVEL_EVERY_TWO&&(count!=1||corpseRequired||decoy))
            throw new IllegalArgumentException("Invalid effective-level summon profile");
    }
    public int baseCount(int effectiveSkillLevel){return countPolicy==CountPolicy.EFFECTIVE_LEVEL_EVERY_TWO?
            com.inigmasgames.hytalerpg.execution.EffectiveSkillLevel.summonSkeletonArchers(effectiveSkillLevel):count;}
    public boolean nativeRanged(){return "RPG_Summon_Skeleton_Archer".equals(roleId);}
    private static boolean positive(double... values) {
        for (double value : values) if (!Double.isFinite(value) || value <= 0) return false;
        return true;
    }
}
