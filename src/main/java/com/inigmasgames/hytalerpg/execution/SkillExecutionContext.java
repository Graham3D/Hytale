package com.inigmasgames.hytalerpg.execution;

import com.inigmasgames.hytalerpg.combat.snapshot.CombatSnapshot;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;

/** Immutable payload values with shared bounded recovery/effect ledgers owned by the committed root. */
public record SkillExecutionContext(SkillExecutionRequest request, String rootCastId, String skillInstanceId,
                                    Stage04SkillProfile profile, CompiledSkillPlan compiledPlan,
                                    CombatSnapshot snapshot, SkillExecutionPort.Equipment equipment,
                                    CommittedTarget target, boolean echo,int barrageBatch,
                                    com.inigmasgames.hytalerpg.combat.resource.RootLeechBudget leechBudget,int multistrikeIndex,
                                    RootEffectBudget effects,String secondaryKind) {
    public SkillExecutionContext(SkillExecutionRequest request,String rootCastId,String skillInstanceId,Stage04SkillProfile profile,
            CompiledSkillPlan compiledPlan,CombatSnapshot snapshot,SkillExecutionPort.Equipment equipment,CommittedTarget target,boolean echo,int barrageBatch,
            com.inigmasgames.hytalerpg.combat.resource.RootLeechBudget leechBudget,int multistrikeIndex){
        this(request,rootCastId,skillInstanceId,profile,compiledPlan,snapshot,equipment,target,echo,barrageBatch,leechBudget,multistrikeIndex,new RootEffectBudget(request.actorId(),rootCastId),"");
    }
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
                ||leechBudget==null||!leechBudget.owns(request.actorId(),rootCastId)||effects==null||!effects.owns(request.actorId(),rootCastId))
            throw new IllegalArgumentException("Committed execution context is incomplete");
        if(multistrikeIndex<0||multistrikeIndex>2||multistrikeIndex>0&&(echo||barrageBatch>0))throw new IllegalArgumentException("Invalid Multistrike child identity");
        if(secondaryKind==null||!java.util.Set.of("","cleaving_edge","phantom_reach","shockwave","cascade").contains(secondaryKind)
                ||!secondaryKind.isEmpty()&&(echo||barrageBatch>0||multistrikeIndex>0))throw new IllegalArgumentException("Invalid secondary identity");
    }
    public boolean derivedRelease() {return echo||barrageBatch>0||multistrikeIndex>0||!secondaryKind.isEmpty();}
    public SkillExecutionContext cascadeCopy(int ordinal){
        if(derivedRelease()||!compiledPlan.zones().cascade()||ordinal<1||ordinal>2)throw new IllegalStateException("CASCADE_CANNOT_RECURSE");
        String child=skillInstanceId+"/cascade-"+ordinal;var old=snapshot.withMagnitudeFactor(.45);
        var inherited=new CombatSnapshot(rootCastId,child,old.actorId(),old.rawAttributes(),old.effectiveAttributes(),old.derivedStats(),
                old.itemId(),old.weaponClass(),old.basePowerSource(),old.basePower(),old.compiledPlanHash(),old.skillCoefficient(),old.criticalChance(),old.criticalMultiplier(),old.modifiers(),old.resourceCost(),old.cooldownSeconds(),old.statusModifiers());
        return new SkillExecutionContext(request,rootCastId,child,profile,compiledPlan,inherited,equipment,target,false,0,leechBudget,0,effects,"cascade");
    }
    public SkillExecutionContext withSnapshot(CombatSnapshot inherited) {
        if(!rootCastId.equals(inherited.rootCastId())||!skillInstanceId.equals(inherited.skillInstanceId())
                ||!request.actorId().equals(inherited.actorId()))throw new IllegalArgumentException("Foreign derived snapshot");
        return new SkillExecutionContext(request,rootCastId,skillInstanceId,profile,compiledPlan,inherited,equipment,target,echo,barrageBatch,leechBudget,multistrikeIndex,effects,secondaryKind);
    }
    public SkillExecutionContext barrageCopy(int batch) {
        if(echo||multistrikeIndex>0||!secondaryKind.isEmpty()||batch<1||batch>=compiledPlan.executionModifiers().barrageBatches())throw new IllegalStateException("INVALID_BARRAGE_BATCH");
        return new SkillExecutionContext(request,rootCastId,skillInstanceId,profile,compiledPlan,snapshot,equipment,target,false,batch,leechBudget,0,effects,"");
    }
    public SkillExecutionContext echoCopy() {
        if(echo||multistrikeIndex>0||!secondaryKind.isEmpty()) throw new IllegalStateException("ECHO_CANNOT_REPEAT_ITSELF");
        String instance=skillInstanceId+"/echo";
        var old=snapshot;var buckets=old.modifiers();
        var less=new java.util.ArrayList<>(buckets.less());less.add(1-compiledPlan.executionModifiers().echoMagnitude());
        var copied=new CombatSnapshot(rootCastId,instance,old.actorId(),old.rawAttributes(),old.effectiveAttributes(),old.derivedStats(),
                old.itemId(),old.weaponClass(),old.basePowerSource(),old.basePower(),old.compiledPlanHash(),old.skillCoefficient(),
                old.criticalChance(),old.criticalMultiplier(),
                new com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets(buckets.increased(),buckets.reduced(),buckets.more(),less),
                old.resourceCost(),old.cooldownSeconds(),old.statusModifiers());
        return new SkillExecutionContext(request,rootCastId,instance,profile,compiledPlan,copied,equipment,target,true,0,leechBudget,0,effects,"");
    }
    public SkillExecutionContext multistrikeCopy(int index){
        if(!compiledPlan.strikes().multistrike()||derivedRelease()||index<1||index>2)throw new IllegalStateException("MULTISTRIKE_CANNOT_RECURSE");
        String child=skillInstanceId+"/multistrike-"+index;var old=snapshot.withMagnitudeFactor(.65);
        var inherited=new CombatSnapshot(rootCastId,child,old.actorId(),old.rawAttributes(),old.effectiveAttributes(),old.derivedStats(),
                old.itemId(),old.weaponClass(),old.basePowerSource(),old.basePower(),old.compiledPlanHash(),old.skillCoefficient(),
                old.criticalChance(),old.criticalMultiplier(),old.modifiers(),old.resourceCost(),old.cooldownSeconds(),old.statusModifiers());
        return new SkillExecutionContext(request,rootCastId,child,profile,compiledPlan,inherited,equipment,target,false,0,leechBudget,index,effects,"");
    }
    public SkillExecutionContext secondaryCopy(String kind,int ordinal,double magnitude){
        if(derivedRelease()||ordinal<1||ordinal>16||!Double.isFinite(magnitude)||magnitude<0||magnitude>1
                ||!(kind.equals("cleaving_edge")&&compiledPlan.strikes().cleavingEdge()||kind.equals("phantom_reach")&&compiledPlan.strikes().phantomReach()||kind.equals("shockwave")&&compiledPlan.strikes().shockwave()))
            throw new IllegalStateException("STRIKE_SECONDARY_CANNOT_RECURSE");
        String child=skillInstanceId+"/"+kind+"-"+ordinal;var old=snapshot.withMagnitudeFactor(magnitude);
        var inherited=new CombatSnapshot(rootCastId,child,old.actorId(),old.rawAttributes(),old.effectiveAttributes(),old.derivedStats(),
                old.itemId(),old.weaponClass(),old.basePowerSource(),old.basePower(),old.compiledPlanHash(),old.skillCoefficient(),
                old.criticalChance(),old.criticalMultiplier(),old.modifiers(),old.resourceCost(),old.cooldownSeconds(),old.statusModifiers());
        return new SkillExecutionContext(request,rootCastId,child,profile,compiledPlan,inherited,equipment,target,false,0,leechBudget,0,effects,kind);
    }
}
