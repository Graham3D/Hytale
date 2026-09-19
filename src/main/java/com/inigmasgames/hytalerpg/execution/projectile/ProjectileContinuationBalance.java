package com.inigmasgames.hytalerpg.execution.projectile;

import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;

/** One authoritative continuation balance contract shared by player and summon projectiles. */
public final class ProjectileContinuationBalance {
    public static final double FORK_CHILD_FACTOR=.65;
    public static final double CHAIN_HIT_FACTOR=.70;
    private ProjectileContinuationBalance(){}
    public static double hitFactor(CompiledSkillPlan plan){
        return plan.projectileModifiers().chain()>0?CHAIN_HIT_FACTOR:1.0;
    }
}
