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
        effects.mastery().bindPrimary(skillInstanceId,profile.summon()!=null||profile.connection()!=null&&profile.connection().channel()
                ||profile.support()!=null&&profile.support().aura());
        if(multistrikeIndex<0||multistrikeIndex>2||multistrikeIndex>0&&(echo||barrageBatch>0))throw new IllegalArgumentException("Invalid Multistrike child identity");
        if(secondaryKind==null||!java.util.Set.of("","cleaving_edge","phantom_reach","shockwave","cascade","aftermath","hemorrhage","terror","shatter","proliferation","critical_trigger","kill_trigger","projectile_status").contains(secondaryKind)
                ||!secondaryKind.isEmpty()&&(echo||barrageBatch>0||multistrikeIndex>0))throw new IllegalArgumentException("Invalid secondary identity");
    }
    public boolean derivedRelease() {return echo||barrageBatch>0||multistrikeIndex>0||!secondaryKind.isEmpty();}
    public boolean conditionalRepeat(){return secondaryKind.equals("critical_trigger")||secondaryKind.equals("kill_trigger");}
    /** A continuation/secondary's later status kill is still derived even after the carrier has disappeared. */
    public SkillExecutionContext projectileStatusChild(String effect){
        if(derivedRelease())return this;
        if(effect==null||effect.isBlank()||effect.length()>512)throw new IllegalArgumentException("INVALID_PROJECTILE_STATUS_SOURCE");
        String child=skillInstanceId+"/status-child/"+java.util.UUID.nameUUIDFromBytes(effect.getBytes(java.nio.charset.StandardCharsets.UTF_8));var old=snapshot;
        var inherited=new CombatSnapshot(rootCastId,child,old.actorId(),old.rawAttributes(),old.effectiveAttributes(),old.derivedStats(),old.itemId(),old.weaponClass(),old.basePowerSource(),old.basePower(),old.compiledPlanHash(),old.skillCoefficient(),old.criticalChance(),old.criticalMultiplier(),old.modifiers(),old.resourceCost(),old.cooldownSeconds(),old.statusModifiers());
        return new SkillExecutionContext(request,rootCastId,child,profile,compiledPlan,inherited,equipment,target,false,0,leechBudget,0,effects,"projectile_status");
    }
    public SkillExecutionContext conditionalCopy(CommittedTarget solution){
        String kind=compiledPlan.conditionalRepeat();
        if(derivedRelease()||kind.isEmpty()||solution==null||target==null||!target.worldId().equals(solution.worldId()))throw new IllegalStateException("CONDITIONAL_REPEAT_INVALID");
        if(kind.equals("critical_trigger")&&!target.equals(solution))throw new IllegalStateException("CRITICAL_TRIGGER_CANNOT_RETARGET");
        String child=skillInstanceId+"/"+kind;var old=snapshot.withMagnitudeFactor(.50);
        var inherited=new CombatSnapshot(rootCastId,child,old.actorId(),old.rawAttributes(),old.effectiveAttributes(),old.derivedStats(),old.itemId(),old.weaponClass(),old.basePowerSource(),old.basePower(),old.compiledPlanHash(),old.skillCoefficient(),old.criticalChance(),old.criticalMultiplier(),old.modifiers(),old.resourceCost(),old.cooldownSeconds(),old.statusModifiers());
        return new SkillExecutionContext(request,rootCastId,child,profile,compiledPlan,inherited,equipment,solution,false,0,leechBudget,0,effects,kind);
    }
    public SkillExecutionContext proliferationCopy(String token){
        if(derivedRelease()||!compiledPlan.proliferation()||token==null||token.length()>256)throw new IllegalStateException("PROLIFERATION_CANNOT_RECURSE");
        String child=skillInstanceId+"/proliferation/"+java.util.UUID.nameUUIDFromBytes(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));var old=snapshot;
        var inherited=new CombatSnapshot(rootCastId,child,old.actorId(),old.rawAttributes(),old.effectiveAttributes(),old.derivedStats(),old.itemId(),old.weaponClass(),old.basePowerSource(),old.basePower(),old.compiledPlanHash(),old.skillCoefficient(),old.criticalChance(),old.criticalMultiplier(),old.modifiers(),old.resourceCost(),old.cooldownSeconds(),old.statusModifiers());
        return new SkillExecutionContext(request,rootCastId,child,profile,compiledPlan,inherited,equipment,target,false,0,leechBudget,0,effects,"proliferation");
    }
    public SkillExecutionContext hitProcCopy(String kind,int ordinal){
        if(derivedRelease()||ordinal<1||ordinal>16||!java.util.Set.of("hemorrhage","terror","shatter","proliferation").contains(kind))throw new IllegalStateException("HIT_PROC_CANNOT_RECURSE");
        String child=skillInstanceId+"/"+kind+"-"+ordinal;var old=snapshot;
        var inherited=new CombatSnapshot(rootCastId,child,old.actorId(),old.rawAttributes(),old.effectiveAttributes(),old.derivedStats(),old.itemId(),old.weaponClass(),old.basePowerSource(),old.basePower(),old.compiledPlanHash(),old.skillCoefficient(),old.criticalChance(),old.criticalMultiplier(),old.modifiers(),old.resourceCost(),old.cooldownSeconds(),old.statusModifiers());
        return new SkillExecutionContext(request,rootCastId,child,profile,compiledPlan,inherited,equipment,target,false,0,leechBudget,0,effects,kind);
    }
    public SkillExecutionContext aftermathCopy(){
        if(derivedRelease()||!compiledPlan.zones().aftermath())throw new IllegalStateException("AFTERMATH_CANNOT_RECURSE");
        var next=com.inigmasgames.hytalerpg.execution.area.AftermathProfiles.resolve(profile);
        String child=skillInstanceId+"/aftermath";var old=snapshot.withMagnitudeFactor(.50);
        var inherited=new CombatSnapshot(rootCastId,child,old.actorId(),old.rawAttributes(),old.effectiveAttributes(),old.derivedStats(),
                old.itemId(),old.weaponClass(),old.basePowerSource(),old.basePower(),old.compiledPlanHash(),old.skillCoefficient(),old.criticalChance(),old.criticalMultiplier(),old.modifiers(),old.resourceCost(),old.cooldownSeconds(),old.statusModifiers());
        return new SkillExecutionContext(request,rootCastId,child,next,compiledPlan,inherited,equipment,target,false,0,leechBudget,0,effects,"aftermath");
    }
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
        if(compiledPlan.orbit()){
            String child=skillInstanceId+"/barrage-"+batch;var old=snapshot;
            var inherited=new CombatSnapshot(rootCastId,child,old.actorId(),old.rawAttributes(),old.effectiveAttributes(),old.derivedStats(),old.itemId(),old.weaponClass(),old.basePowerSource(),old.basePower(),old.compiledPlanHash(),old.skillCoefficient(),old.criticalChance(),old.criticalMultiplier(),old.modifiers(),old.resourceCost(),old.cooldownSeconds(),old.statusModifiers());
            return new SkillExecutionContext(request,rootCastId,child,profile,compiledPlan,inherited,equipment,target,false,batch,leechBudget,0,effects,"");
        }
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
