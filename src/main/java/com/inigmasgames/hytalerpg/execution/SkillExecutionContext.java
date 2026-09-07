package com.inigmasgames.hytalerpg.execution;

import com.inigmasgames.hytalerpg.combat.snapshot.CombatSnapshot;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;

/** Immutable context inherited by every effect produced by one committed activation. */
public record SkillExecutionContext(SkillExecutionRequest request, String rootCastId, String skillInstanceId,
                                    Stage04SkillProfile profile, CompiledSkillPlan compiledPlan,
                                    CombatSnapshot snapshot, SkillExecutionPort.Equipment equipment,
                                    CommittedTarget target, boolean echo) {
    public SkillExecutionContext(SkillExecutionRequest request, String rootCastId, String skillInstanceId,
            Stage04SkillProfile profile, CompiledSkillPlan compiledPlan, CombatSnapshot snapshot, SkillExecutionPort.Equipment equipment) {
        this(request,rootCastId,skillInstanceId,profile,compiledPlan,snapshot,equipment,null,false);
    }
    public SkillExecutionContext {
        if (request == null || rootCastId == null || skillInstanceId == null || profile == null
                || compiledPlan == null || snapshot == null)
            throw new IllegalArgumentException("Committed execution context is incomplete");
    }
    public SkillExecutionContext echoCopy() {
        if(echo) throw new IllegalStateException("ECHO_CANNOT_REPEAT_ITSELF");
        String instance=skillInstanceId+"/echo";
        var old=snapshot;var buckets=old.modifiers();
        var less=new java.util.ArrayList<>(buckets.less());less.add(1-compiledPlan.executionModifiers().echoMagnitude());
        var copied=new CombatSnapshot(rootCastId,instance,old.actorId(),old.rawAttributes(),old.effectiveAttributes(),old.derivedStats(),
                old.itemId(),old.weaponClass(),old.basePowerSource(),old.basePower(),old.compiledPlanHash(),old.skillCoefficient(),
                old.criticalChance(),old.criticalMultiplier(),
                new com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets(buckets.increased(),buckets.reduced(),buckets.more(),less),
                old.resourceCost(),old.cooldownSeconds(),old.statusModifiers());
        return new SkillExecutionContext(request,rootCastId,instance,profile,compiledPlan,copied,equipment,target,true);
    }
}
