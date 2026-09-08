package com.inigmasgames.hytalerpg.execution.summon;

/** Authored summon identity; never copies a source NPC's scripts, inventory or rewards. */
public record SummonProfile(String roleId, String element, double range, int count,
                            double healthFactor, double coefficient, double lifetime,
                            double attackInterval, double leash, boolean corpseRequired) {
    public SummonProfile(String roleId,String element,double range,int count,double healthFactor,double coefficient,double lifetime,double attackInterval,double leash){
        this(roleId,element,range,count,healthFactor,coefficient,lifetime,attackInterval,leash,false);
    }
    public SummonProfile {
        if (roleId == null || !roleId.startsWith("RPG_Summon_") || element == null || element.isBlank()
                || count < 1 || count > 8 || !positive(range,healthFactor,coefficient,lifetime,attackInterval,leash)
                || attackInterval < 1 || lifetime > 60 || leash > 24)
            throw new IllegalArgumentException("Invalid bounded summon profile");
    }
    private static boolean positive(double... values) {
        for (double value : values) if (!Double.isFinite(value) || value <= 0) return false;
        return true;
    }
}
