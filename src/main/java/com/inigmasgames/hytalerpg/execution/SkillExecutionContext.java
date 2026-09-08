package com.inigmasgames.hytalerpg.execution;

import com.inigmasgames.hytalerpg.combat.snapshot.CombatSnapshot;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;

/** Immutable payload values with a shared, bounded recovery ledger owned by the committed root. */
public record SkillExecutionContext(SkillExecutionRequest request, String rootCastId, String skillInstanceId,
                                    Stage04SkillProfile profile, CompiledSkillPlan compiledPlan,
                                    CombatSnapshot snapshot, SkillExecutionPort.Equipment equipment,
                                    CommittedTarget target, boolean echo,int barrageBatch,
                                    com.inigmasgames.hytalerpg.combat.resource.RootLeechBudget leechBudget) {
    public SkillExecutionContext(SkillExecutionRequest request,String rootCastId,String skillInstanceId,Stage04SkillProfile profile,
            CompiledSkillPlan compiledPlan,CombatSnapshot snapshot,SkillExecutionPort.Equipment equipment,CommittedTarget target,boolean echo,int barrageBatch){
        this(request,rootCastId,skillInstanceId,profile,compiledPlan,snapshot,equipment,target,echo,barrageBatch,
                new com.inigmasgames.hytalerpg.combat.resource.RootLeechBudget(request.actorId(),rootCastId));
    }
    public SkillExecutionContext(SkillExecutionRequest request,String rootCastId,String skillInstanceId,Stage04SkillProfile profile,
            CompiledSkillPlan compiledPlan,CombatSnapshot snapshot,SkillExecutionPort.Equipment equipment,CommittedTarget target,boolean echo) {
        this(request,rootCastId,skillInstanceId,profile,compiledPlan,snapshot,equipment,target,echo,0);
    }
    public SkillExecutionContext(SkillExecutionRequest request, String rootCastId, String skillInstanceId,
            Stage04SkillProfile profile, CompiledSkillPlan compiledPlan, CombatSnapshot snapshot, SkillExecutionPort.Equipment equipment) {
        this(request,rootCastId,skillInstanceId,profile,compiledPlan,snapshot,equipment,null,false);
    }
    public SkillExecutionContext {
        if (request == null || rootCastId == null || skillInstanceId == null || profile == null
                || compiledPlan == null || snapshot == null || barrageBatch<0 || barrageBatch>2 || echo&&barrageBatch>0
                ||leechBudget==null||!leechBudget.owns(request.actorId(),rootCastId))
            throw new IllegalArgumentException("Committed execution context is incomplete");
    }
    public boolean derivedRelease() {return echo||barrageBatch>0;}
    public SkillExecutionContext withSnapshot(CombatSnapshot inherited) {
        if(!rootCastId.equals(inherited.rootCastId())||!skillInstanceId.equals(inherited.skillInstanceId())
                ||!request.actorId().equals(inherited.actorId()))throw new IllegalArgumentException("Foreign derived snapshot");
        return new SkillExecutionContext(request,rootCastId,skillInstanceId,profile,compiledPlan,inherited,equipment,target,echo,barrageBatch,leechBudget);
    }
    public SkillExecutionContext barrageCopy(int batch) {
        if(echo||batch<1||batch>=compiledPlan.executionModifiers().barrageBatches())throw new IllegalStateException("INVALID_BARRAGE_BATCH");
        return new SkillExecutionContext(request,rootCastId,skillInstanceId,profile,compiledPlan,snapshot,equipment,target,false,batch,leechBudget);
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
        return new SkillExecutionContext(request,rootCastId,instance,profile,compiledPlan,copied,equipment,target,true,0,leechBudget);
    }
}
