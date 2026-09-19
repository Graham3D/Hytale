package com.inigmasgames.persistentnpcs.epistemic;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.action.*;
import com.inigmasgames.persistentnpcs.autonomy.*;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.cognition.*;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Targeted E8 A-J gates, including the synthetic hunter/sword/merchant acceptance. */
public final class R084EpistemicE8Test {
    public static void main(String[] args) throws Exception {
        var root=Files.createTempDirectory("r084-e8-");
        var operations=new AgentOperationStore(root); operations.load();
        var library=new NpcSkillLibrary();
        AtomicBoolean pressure=new AtomicBoolean();
        Map<String,AtomicInteger> failures=new HashMap<>();
        Map<String,Integer> world=new ConcurrentHashMap<>(Map.of("hunterItems",0,"hunterGold",0));
        NpcActionRegistry registry=registry(library,failures,world);
        var service=new NpcAutonomousReActService(library,registry,operations,null,pressure::get);
        NpcProfile hunter=profile("HUNTER", library);
        var evidence=Set.of("ITEM_PERCEIVED","ITEM_OWNED","MERCHANT_KNOWN");
        var bindings=Map.of("itemId","magical_sword","merchantLocation","market");
        var context=new NpcAutonomousReActService.Context(evidence,bindings,
                SimulationTier.ACTIVE,false,false);
        var actionContext=new NpcActionContext(hunter,null,null);

        // A: deterministic known task; missing evidence/role is rejected.
        var inspect=service.plan(hunter,"INSPECT_UNUSUAL_ITEM",context,Instant.now());
        assert inspect.state()==NpcAutonomousReActService.State.PLANNED;
        assert service.plan(hunter,"RETURN_HOME",context,Instant.now()).state()
                ==NpcAutonomousReActService.State.FAILED;
        operations.complete(inspect.operationId(),false,"test reset");

        // B/J: novel output remains a proposal; code-bearing/generated skills are rejected.
        boolean codeRejected=false;
        try { library.register(new NpcSkillLibrary.Skill(null,"BAD",Set.of(),Set.of(),Set.of(),
                List.of(new NpcSkillLibrary.Step("Runtime.getRuntime().exec",Map.of(),Set.of(),null)),
                Set.of(),List.of(),"MODEL")); } catch(IllegalArgumentException expected){codeRejected=true;}
        assert codeRejected;

        // C: a pending Hytale future cannot advance the immutable plan step.
        CompletableFuture<NpcActionResult> pending=new CompletableFuture<>();
        NpcActionRegistry pendingRegistry=registryWith("INSPECT_ITEM",pending,hunter);
        var pendingService=new NpcAutonomousReActService(library,pendingRegistry,operations,null,()->false);
        var pendingRun=pendingService.plan(hunter,"INSPECT_UNUSUAL_ITEM",context,Instant.now());
        var pendingExecution=pendingService.executeNext(pendingRun,hunter,context,actionContext,null,Instant.now());
        assert !pendingExecution.isDone() && pendingRun.stepIndex()==0;
        pending.complete(NpcActionResult.success("authoritative inspection"));
        assert pendingExecution.get().state()==NpcAutonomousReActService.State.COMPLETED;

        // D/E: authoritative failure produces bounded replanning and outcome metrics.
        failures.put("INSPECT_ITEM",new AtomicInteger(1));
        var failed=service.plan(hunter,"INSPECT_UNUSUAL_ITEM",context,Instant.now());
        failed=service.executeNext(failed,hunter,context,actionContext,null,Instant.now()).get();
        assert failed.state()==NpcAutonomousReActService.State.REPLAN_REQUIRED && failed.replans()==1;
        failed=service.executeNext(failed,hunter,context,actionContext,null,Instant.now()).get();
        assert failed.state()==NpcAutonomousReActService.State.COMPLETED;
        assert failed.actionResultIds().size()==2;

        // F: changed evidence invalidates a previously eligible selection.
        assert service.plan(hunter,"INSPECT_UNUSUAL_ITEM",new NpcAutonomousReActService.Context(
                Set.of(),bindings,SimulationTier.ACTIVE,false,false),Instant.now()).state()
                ==NpcAutonomousReActService.State.FAILED;

        // F: high uncertainty/risk selects a reversible inspection before sale/pickup.
        var cautious=service.planOpportunity(hunter,"SELL_EQUIPMENT",
                new AutonomousOpportunityScore(.8,.2,.2,.8,.9,.7,.2,.8,.1),context,Instant.now());
        assert cautious.skillId().equals(library.byName("INSPECT_UNUSUAL_ITEM").orElseThrow().skillId());
        operations.complete(cautious.operationId(),false,"uncertainty test reset");

        // G/H: tier and scheduler pressure prevent physical/background execution.
        assert service.plan(hunter,"INSPECT_UNUSUAL_ITEM",new NpcAutonomousReActService.Context(
                evidence,bindings,SimulationTier.DORMANT,false,false),Instant.now()).state()
                ==NpcAutonomousReActService.State.YIELDED;
        var yielded=service.plan(hunter,"INSPECT_UNUSUAL_ITEM",context,Instant.now()); pressure.set(true);
        assert service.executeNext(yielded,hunter,context,actionContext,null,Instant.now()).get().state()
                ==NpcAutonomousReActService.State.YIELDED;
        operations.complete(yielded.operationId(),false,"pressure test"); pressure.set(false);

        // I: hunter observes/inspects, owns sword, knows merchant, travels, transacts.
        var hunt=service.plan(hunter,"SELL_EQUIPMENT",context,Instant.now());
        assert hunt.state()==NpcAutonomousReActService.State.PLANNED;
        world.put("hunterItems",1);
        while(hunt.state()==NpcAutonomousReActService.State.PLANNED)
            hunt=service.executeNext(hunt,hunter,context,actionContext,null,Instant.now()).get();
        assert hunt.state()==NpcAutonomousReActService.State.COMPLETED;
        assert hunt.actionResultIds().size()==2;
        assert world.get("hunterItems")==0 && world.get("hunterGold")==25;
        assert library.metrics(hunt.skillId()).completed()==1;

        // D/J: only the authoritative result becomes E4 belief and durable recall for both NPCs.
        UUID merchant=UUID.randomUUID(), player=UUID.randomUUID();
        MemoryStore memories=new MemoryStore(root,100); memories.load();
        try(SourcedBeliefStore beliefs=new SourcedBeliefStore(root.resolve("belief-proof"))){
            beliefs.load();
            var cognition=new NpcCognitionService(null,null,null,null,memories,null,null,
                    operations,beliefs,new CognitionTraceStore());
            var request=new NpcActionRequest("TRANSACTION_ITEM",new JsonObject(),"E8-PROOF",
                    UUID.randomUUID(),hunter.id(),merchant);
            cognition.ingestAutonomousActionResult(hunter.id(),player,merchant,request,
                    NpcActionResult.success("Hunter sold magical_sword for 25 gold."));
            assert beliefs.current(hunter.id(),null,"TRANSACTION_OCCURRED").size()==1;
            assert beliefs.current(merchant,null,"TRANSACTION_OCCURRED").size()==1;
            assert memories.forNpc(hunter.id()).stream().anyMatch(m->m.source().contains("E8-PROOF"));
            assert memories.forNpc(merchant).stream().anyMatch(m->m.source().contains("E8-PROOF"));
        }
        System.out.println("R084 E8 autonomous ReAct/skill-library A-J validation passed.");
    }

    private static NpcActionRegistry registry(NpcSkillLibrary library,
            Map<String,AtomicInteger> failures,Map<String,Integer> world){
        NpcActionRegistry registry=new NpcActionRegistry();
        library.all().stream().flatMap(s->s.steps().stream()).map(NpcSkillLibrary.Step::actionId)
                .distinct().forEach(id->registry.register(new NpcActionDefinition(id,id,new JsonObject(),
                Set.of(),Set.of(),c->true,(r,c)->NpcActionResult.success("validated"),(r,c)->{
                    AtomicInteger remaining=failures.get(id);
                    if(remaining!=null&&remaining.getAndDecrement()>0)
                        return CompletableFuture.completedFuture(NpcActionResult.failure("STALE_EVIDENCE","authoritative target changed"));
                    if(id.equals("TRANSACTION_ITEM")){
                        if(world.get("hunterItems")<1) return CompletableFuture.completedFuture(
                                NpcActionResult.failure("ITEM_NOT_OWNED","no sword to sell"));
                        world.put("hunterItems",0); world.put("hunterGold",25);
                    }
                    return CompletableFuture.completedFuture(NpcActionResult.success("authoritative "+id));
                },id+" result")));
        return registry;
    }
    private static NpcActionRegistry registryWith(String id,CompletableFuture<NpcActionResult> result,NpcProfile p){
        NpcActionRegistry r=new NpcActionRegistry();
        r.register(new NpcActionDefinition(id,id,new JsonObject(),Set.of(),Set.of(),c->true,
                (q,c)->NpcActionResult.success("validated"),(q,c)->result,id)); return r;
    }
    private static NpcProfile profile(String role,NpcSkillLibrary library){
        Set<String> actions=new HashSet<>(); library.all().forEach(s->s.steps().forEach(x->actions.add(x.actionId())));
        return new NpcProfile(UUID.randomUUID(),"E8 Hunter",role,"practical","A hunter",
                "hunt", "home","market",List.of(),List.of(),List.of(role),List.copyOf(actions),0).validated();
    }
}
