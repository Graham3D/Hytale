package com.inigmasgames.hytalerpg.execution;

import com.inigmasgames.hytalerpg.combat.snapshot.CombatSnapshot;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;

/** Immutable payload values with a shared, bounded recovery ledger owned by the committed root. */
public record SkillExecutionContext(SkillExecutionRequest request, String rootCastId, String skillInstanceId,
                                    Stage04SkillProfile profile, CompiledSkillPlan compiledPlan,
                                    CombatSnapshot snapshot, SkillExecutionPort.Equipment equipment,
                                    CommittedTarget target, boolean echo,int barrageBatch,
                                    com.inigmasgames.hytalerpg.combat.resource.RootLeechBudget leechBudget,int multistrikeIndex) {
    public SkillExecutionContext(SkillExecutionRequest request,String rootCastId,String skillInstanceId,Stage04SkillProfile profile,
            CompiledSkillPlan compiledPlan,CombatSnapshot snapshot,SkillExecutionPort.Equipment equipment,CommittedTarget target,boolean echo,int barrageBatch,
            com.inigmasgames.hytalerpg.combat.resource.RootLeechBudget leechBudget){
        this(request,rootCastId,skillInstanceId,profile,compiledPlan,snapshot,equipment,target,echo,barrageBatch,leechBudget,0);
    }
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
        if(multistrikeIndex<0||multistrikeIndex>2||multistrikeIndex>0&&(echo||barrageBatch>0))throw new IllegalArgumentException("Invalid Multistrike child identity");
    }
    public boolean derivedRelease() {return echo||barrageBatch>0||multistrikeIndex>0;}
    public SkillExecutionContext withSnapshot(CombatSnapshot inherited) {
        if(!rootCastId.equals(inherited.rootCastId())||!skillInstanceId.equals(inherited.skillInstanceId())
                ||!request.actorId().equals(inherited.actorId()))throw new IllegalArgumentException("Foreign derived snapshot");
        return new SkillExecutionContext(request,rootCastId,skillInstanceId,profile,compiledPlan,inherited,equipment,target,echo,barrageBatch,leechBudget,multistrikeIndex);
    }
    public SkillExecutionContext barrageCopy(int batch) {
        if(echo||multistrikeIndex>0||batch<1||batch>=compiledPlan.executionModifiers().barrageBatches())throw new IllegalStateException("INVALID_BARRAGE_BATCH");
        return new SkillExecutionContext(request,rootCastId,skillInstanceId,profile,compiledPlan,snapshot,equipment,target,false,batch,leechBudget);
    }
    public SkillExecutionContext echoCopy() {
        if(echo||multistrikeIndex>0) throw new IllegalStateException("ECHO_CANNOT_REPEAT_ITSELF");
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
    public SkillExecutionContext multistrikeCopy(int index){
        if(!compiledPlan.strikes().multistrike()||derivedRelease()||index<1||index>2)throw new IllegalStateException("MULTISTRIKE_CANNOT_RECURSE");
        String child=skillInstanceId+"/multistrike-"+index;var old=snapshot.withMagnitudeFactor(.65);
        var inherited=new CombatSnapshot(rootCastId,child,old.actorId(),old.rawAttributes(),old.effectiveAttributes(),old.derivedStats(),
                old.itemId(),old.weaponClass(),old.basePowerSource(),old.basePower(),old.compiledPlanHash(),old.skillCoefficient(),
                old.criticalChance(),old.criticalMultiplier(),old.modifiers(),old.resourceCost(),old.cooldownSeconds(),old.statusModifiers());
        return new SkillExecutionContext(request,rootCastId,child,profile,compiledPlan,inherited,equipment,target,false,0,leechBudget,index);
    }
}
