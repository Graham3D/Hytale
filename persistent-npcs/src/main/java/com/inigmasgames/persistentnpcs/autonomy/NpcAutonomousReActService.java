package com.inigmasgames.persistentnpcs.autonomy;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.action.*;
import com.inigmasgames.persistentnpcs.cognition.NpcCognitionService;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** E8 BDI executor: semantic plan -> validated action -> observation -> belief -> replan. */
public final class NpcAutonomousReActService {
    public enum State { PLANNED, EXECUTING, REPLAN_REQUIRED, COMPLETED, FAILED, YIELDED }
    public record Context(Set<String> evidence, Map<String,String> bindings,
            SimulationTier tier, boolean higherObligation, boolean foregroundPressure) {
        public Context { evidence=evidence==null?Set.of():Set.copyOf(evidence);
            bindings=bindings==null?Map.of():Map.copyOf(bindings);
            tier=tier==null?SimulationTier.ACTIVE:tier; }
    }
    public record Run(UUID runId, UUID operationId, UUID skillId, UUID npcId, int stepIndex,
            int replans, State state, List<String> actionResultIds, String detail) { }

    private final NpcSkillLibrary library;
    private final NpcActionRegistry actions;
    private final AgentOperationStore operations;
    private final NpcCognitionService cognition;
    private final BooleanSupplier schedulerYield;

    public NpcAutonomousReActService(NpcSkillLibrary library, NpcActionRegistry actions,
            AgentOperationStore operations, NpcCognitionService cognition,
            BooleanSupplier schedulerYield) {
        this.library=Objects.requireNonNull(library); this.actions=Objects.requireNonNull(actions);
        this.operations=Objects.requireNonNull(operations); this.cognition=cognition;
        this.schedulerYield=schedulerYield==null?()->false:schedulerYield;
    }

    public Run plan(NpcProfile profile, String skillName, Context context, Instant now) {
        if (context.tier()==SimulationTier.DORMANT)
            return rejected(profile, null, State.YIELDED, "DORMANT NPC cannot perform physical skill");
        NpcSkillLibrary.Skill skill=library.byName(skillName).orElse(null);
        if (skill==null || !library.eligible(profile, context.evidence(),
                context.higherObligation()).contains(skill))
            return rejected(profile, skill, State.FAILED, "role, capability, evidence, or obligation rejected skill");
        if (skill.steps().stream().anyMatch(s -> !actions.ids().contains(s.actionId())))
            return rejected(profile, skill, State.FAILED, "skill references unavailable action");
        AgentOperation op=operations.claim("AUTONOMOUS_SKILL:"+skill.name(), Set.of(profile.id()),
                "E8 semantic skill", now, Duration.ofMinutes(3));
        library.selected(skill.skillId());
        return new Run(UUID.randomUUID(), op.operationId(), skill.skillId(), profile.id(), 0, 0,
                State.PLANNED, List.of(), "validated authored skill");
    }

    /** High uncertainty/risk deterministically narrows the commitment to information gathering. */
    public Run planOpportunity(NpcProfile profile, String preferredSkill,
            AutonomousOpportunityScore score, Context context, Instant now) {
        String selected = score != null && score.requiresInformationAction()
                ? "INSPECT_UNUSUAL_ITEM" : preferredSkill;
        return plan(profile, selected, context, now);
    }

    public CompletableFuture<Run> executeNext(Run run, NpcProfile profile, Context planContext,
            NpcActionContext actionContext, UUID playerId, Instant now) {
        if (run.state()!=State.PLANNED && run.state()!=State.REPLAN_REQUIRED)
            return CompletableFuture.completedFuture(run);
        if (schedulerYield.getAsBoolean() || planContext.foregroundPressure())
            return CompletableFuture.completedFuture(copy(run, run.stepIndex(), run.replans(),
                    State.YIELDED, run.actionResultIds(), "yielded to Hytale/foreground work"));
        NpcSkillLibrary.Skill skill=library.all().stream().filter(s->s.skillId().equals(run.skillId()))
                .findFirst().orElseThrow();
        if (run.stepIndex()>=skill.steps().size()) return CompletableFuture.completedFuture(run);
        NpcSkillLibrary.Step step=skill.steps().get(run.stepIndex());
        if (!normalized(planContext.evidence()).containsAll(step.requiredEvidence()))
            return CompletableFuture.completedFuture(fail(run, skill, "stale step precondition"));
        String resultId="E8-"+run.runId()+"-"+run.stepIndex()+"-"+UUID.randomUUID();
        JsonObject parameters=new JsonObject();
        step.parameters().forEach((k,v)->parameters.addProperty(k, resolve(v, planContext.bindings())));
        UUID targetNpc=parseUuid(planContext.bindings().get("targetStableId"));
        NpcActionRequest request=new NpcActionRequest(step.actionId(), parameters, resultId,
                run.runId(), profile.id(), targetNpc);
        return actions.execute(request, actionContext).thenApply(result -> {
            if (cognition!=null) cognition.ingestAutonomousActionResult(profile.id(), playerId,
                    targetNpc, request, result);
            List<String> ids=new ArrayList<>(run.actionResultIds()); ids.add(resultId);
            if (!result.success()) {
                if (step.failurePolicy()==NpcSkillLibrary.FailurePolicy.REPLAN && run.replans()<2)
                    return copy(run, run.stepIndex(), run.replans()+1, State.REPLAN_REQUIRED,
                            List.copyOf(ids), result.code()+": authoritative failure; inspect/ask/wait");
                return fail(copy(run,run.stepIndex(),run.replans(),run.state(),List.copyOf(ids),""),
                        skill, result.code()+": "+result.eventDescription());
            }
            int next=run.stepIndex()+1;
            if (next==skill.steps().size()) {
                operations.complete(run.operationId(), true, result.eventDescription());
                library.outcome(skill.skillId(), true);
                return copy(run,next,run.replans(),State.COMPLETED,List.copyOf(ids),
                        result.eventDescription());
            }
            return copy(run,next,run.replans(),State.PLANNED,List.copyOf(ids),
                    result.eventDescription());
        });
    }

    private Run fail(Run run,NpcSkillLibrary.Skill skill,String detail){
        if(run.operationId()!=null) operations.complete(run.operationId(),false,detail);
        if(skill!=null) library.outcome(skill.skillId(),false);
        return copy(run,run.stepIndex(),run.replans(),State.FAILED,run.actionResultIds(),detail);
    }
    private static Run rejected(NpcProfile p,NpcSkillLibrary.Skill s,State state,String detail){
        return new Run(UUID.randomUUID(),null,s==null?null:s.skillId(),p==null?null:p.id(),0,0,
                state,List.of(),detail);
    }
    private static Run copy(Run r,int step,int replans,State state,List<String> ids,String detail){
        return new Run(r.runId(),r.operationId(),r.skillId(),r.npcId(),step,replans,state,ids,detail);
    }
    private static String resolve(String value,Map<String,String> bindings){
        return value!=null&&value.startsWith("$")?bindings.getOrDefault(value.substring(1),""):value;
    }
    private static UUID parseUuid(String value){
        try { return value==null||value.isBlank()?null:UUID.fromString(value); }
        catch(IllegalArgumentException invalid){ return null; }
    }
    private static Set<String> normalized(Set<String> values){
        Set<String> out=new HashSet<>(); for(String v:values) out.add(v.strip().toUpperCase(Locale.ROOT));
        return out;
    }
}
