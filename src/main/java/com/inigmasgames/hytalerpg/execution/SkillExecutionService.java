package com.inigmasgames.hytalerpg.execution;

import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.attribute.DerivedStats;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets;
import com.inigmasgames.hytalerpg.combat.power.BasePowerResolver;
import com.inigmasgames.hytalerpg.combat.power.BasePowerSource;
import com.inigmasgames.hytalerpg.combat.resource.ResourceCost;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.diagnostics.RpgSkillTracer;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceRecord;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.progress.RpgLoadoutOperations;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

/** Shared validate -> snapshot -> commit -> family-dispatch transaction. */
public final class SkillExecutionService {
    private final RpgLoadoutOperations loadouts;
    private final Stage04SkillProfiles profiles;
    private final RpgCombatKernel kernel;
    private final SkillExecutorRegistry executors;
    private final SkillInstanceLifecycle lifecycle;
    private final RpgSkillTracer tracer;
    private final SkillReleaseScheduler releases = new SkillReleaseScheduler();
    private final ConditionalRepeatRuntime conditionalRepeats=new ConditionalRepeatRuntime();
    private final RetaliationLedger retaliation=new RetaliationLedger();
    private final CompiledProfileResolver compiledProfiles = new CompiledProfileResolver();
    private final AttunementLedger attunement = new AttunementLedger();
    private final com.inigmasgames.hytalerpg.execution.strike.FinisherLedger finisher=new com.inigmasgames.hytalerpg.execution.strike.FinisherLedger();
    public void observeNativeBasicRootHit(UUID actor,double now){finisher.observedRoot(actor,now);}
    public int finisherPips(UUID actor){return finisher.pips(actor,now());}
    private void resolveAuthoredRelease(SkillExecutionContext context){
        if(context.profile().strike()!=null&&context.profile().strike().details().finisher()&&!context.derivedRelease())
            context.effects().resolveFinisher(()->finisher.consume(context.request().actorId()));
    }
    private final com.inigmasgames.hytalerpg.execution.strike.RuthlessLedger ruthless=new com.inigmasgames.hytalerpg.execution.strike.RuthlessLedger();
    private final java.util.function.LongSupplier nanoTime;
    private final Map<UUID, Prepared> windups = new LinkedHashMap<>();
    private final Map<UUID, SkillExecutionContext> activeContexts = new LinkedHashMap<>();

    public SkillExecutionService(RpgLoadoutOperations loadouts, Stage04SkillProfiles profiles,
                                 RpgCombatKernel kernel, SkillExecutorRegistry executors,
                                 SkillInstanceLifecycle lifecycle, RpgSkillTracer tracer) {
        this(loadouts,profiles,kernel,executors,lifecycle,tracer,System::nanoTime);
    }
    public SkillExecutionService(RpgLoadoutOperations loadouts, Stage04SkillProfiles profiles,
                                 RpgCombatKernel kernel, SkillExecutorRegistry executors,
                                 SkillInstanceLifecycle lifecycle, RpgSkillTracer tracer, java.util.function.LongSupplier nanoTime) {
        this.loadouts = loadouts; this.profiles = profiles; this.kernel = kernel;
        this.executors = executors; this.lifecycle = lifecycle; this.tracer = tracer;
        this.nanoTime=nanoTime;
        loadouts.addLoadoutMutationListener(attunement::forget);
        loadouts.addLoadoutMutationListener(ruthless::forget);
        loadouts.addLoadoutMutationListener(retaliation::clearPlans);
        loadouts.addLoadoutMutationListener(actor->{for(var c:releases.cancelConditional(actor))emit(c.request(),RpgTraceEventType.SKILL_RELEASE_CANCELLED,c.rootCastId(),c.skillInstanceId(),Map.of("reason","COMMITTED_LOADOUT_CHANGED","refund",false));});
    }

    /** A held native chain must not release a different skill assigned while the key was held. */
    public SkillExecutionResult requestNative(SkillExecutionRequest request, SkillExecutionPort port, String expectedSkill) {
        if (!expectedSkill.isEmpty() && !loadouts.getPresentationView(request.actorId()).state().skill(request.slot())
                .map(id -> id.value().equals(expectedSkill)).orElse(false))
            return reject(request, "input-" + request.chainId(), "activation-" + UUID.randomUUID(), "HELD_SKILL_CHANGED");
        return request(request, port);
    }

    public SkillExecutionResult request(SkillExecutionRequest request, SkillExecutionPort port) {
        String root = "input-" + request.chainId() + '-' + request.correlationId().substring(0, Math.min(8, request.correlationId().length()));
        String pendingInstance = "activation-" + UUID.randomUUID();
        if(persistenceCommits.containsKey(request.actorId())||channelCooldowns.containsKey(request.actorId()))return SkillExecutionResult.pending("PREVIOUS_DURABLE_COMMIT_PENDING");
        emit(request, RpgTraceEventType.SKILL_ACTIVATION_REQUEST, root, pendingInstance,
                Map.of("action", request.action(), "skillSlot", request.slot().externalId(),"origin",request.origin()));
        Prepared prepared;
        try {
            var equipped=loadouts.getPresentationView(request.actorId()).state().skill(request.slot());
            if(equipped.isPresent()&&profiles.supports(equipped.get().value())){
                var profile=profiles.require(equipped.get().value());
                if(profile.support()!=null&&profile.support().aura()){
                    var stopped=port.stopActiveSupport(profile);
                    if(stopped!=null)return stopped.committed()||stopped.status()==SkillExecutionResult.Status.PENDING?stopped:reject(request,root,pendingInstance,stopped.code());
                }
            }
            prepared = validate(request, port, root, pendingInstance);
        }
        catch (Rejection rejection) {
            return reject(request, root, rejection.skillInstanceId, rejection.code);
        } catch (RuntimeException error) {
            return reject(request, root, pendingInstance, "VALIDATION_ERROR_" + error.getClass().getSimpleName(),
                    ExecutionFailureDiagnostics.describe("VALIDATION",error));
        }
        emit(request, RpgTraceEventType.SKILL_VALIDATION_PASS, root, prepared.instanceId,
                Map.of("skillId", prepared.profile.skillId(), "family", prepared.profile.family().name(),
                        "windupSeconds", prepared.profile.windupSeconds()));
        if (prepared.profile.windupSeconds() > 0.0) {
            if (!lifecycle.begin(request.actorId(), prepared.instanceId, SkillInstanceLifecycle.Phase.WINDUP))
                return reject(request, root, prepared.instanceId, "INCOMPATIBLE_ACTIVE_STATE");
            synchronized (windups) { windups.put(request.actorId(), prepared); }
            return SkillExecutionResult.pending("WINDUP_STARTED");
        }
        if (!lifecycle.begin(request.actorId(), prepared.instanceId, SkillInstanceLifecycle.Phase.COMMITTED))
            return reject(request, root, prepared.instanceId, "INCOMPATIBLE_ACTIVE_STATE");
        return commitAndDispatch(prepared, port);
    }

    /** Called on the world thread after an interruptible authored wind-up completes. */
    public SkillExecutionResult completeWindup(UUID actor, SkillExecutionPort port) {
        Prepared prepared;
        synchronized (windups) { prepared = windups.remove(actor); }
        if (prepared == null) return SkillExecutionResult.rejected("NO_ACTIVE_WINDUP");
        if (!lifecycle.transition(actor, prepared.instanceId, SkillInstanceLifecycle.Phase.WINDUP,
                SkillInstanceLifecycle.Phase.COMMITTED)) return SkillExecutionResult.rejected("WINDUP_CANCELLED");
        try {
            Prepared current = validate(prepared.request, port, prepared.rootCastId, prepared.instanceId);
            var result=commitAndDispatch(current, port);retaliation.complete(actor,current.request.correlationId(),result.committed(),now());return result;
        } catch (Rejection rejection) {
            lifecycle.terminate(actor, prepared.instanceId);
            retaliation.complete(actor,prepared.request.correlationId(),false,now());
            return reject(prepared.request, prepared.rootCastId, prepared.instanceId, rejection.code);
        } catch (RuntimeException error) {
            lifecycle.terminate(actor, prepared.instanceId);
            retaliation.complete(actor, prepared.request.correlationId(), false, now());
            return reject(prepared.request, prepared.rootCastId, prepared.instanceId,
                    "WINDUP_REVALIDATION_ERROR_" + error.getClass().getSimpleName());
        }
    }

    public OptionalDouble activeWindupSeconds(UUID actor) {
        synchronized (windups) {
            Prepared value = windups.get(actor);
            return value == null ? OptionalDouble.empty() : OptionalDouble.of(value.profile.windupSeconds());
        }
    }
    public boolean pendingCast(UUID actor){return persistenceCommits.containsKey(actor)||lifecycle.active(actor).isPresent()||releases.pending(actor)||activeWindupSeconds(actor).isPresent();}

    public boolean cancel(UUID actor, String reason) {
        var persistence=persistenceCommits.get(actor);
        if(persistence!=null){cancelledPersistence.add(actor);kernel.resources().refundIfUncommitted(persistence.token);durableAbandon.accept(persistence.context);}
        var queued=releases.cancel(actor);
        for(var pending:queued) emit(pending.request(),RpgTraceEventType.SKILL_RELEASE_CANCELLED,
                pending.rootCastId(),pending.skillInstanceId(),Map.of("reason",reason,"refund",false));
        Prepared windup;
        synchronized (windups) { windup = windups.remove(actor); }
        if(windup!=null)retaliation.complete(actor,windup.request.correlationId(),false,now());
        SkillExecutionContext context;
        synchronized (activeContexts) { context = activeContexts.remove(actor); }
        Optional<SkillInstanceLifecycle.Active> cancelled = lifecycle.cancel(actor);
        if (cancelled.isEmpty()) return !queued.isEmpty();
        if (context == null && windup == null) return true; // No fabricated context during a claimed release.
        if(context!=null) startEndedChannelCooldown(context);
        SkillExecutionRequest request = context != null ? context.request() : windup.request;
        String root = context != null ? context.rootCastId() : windup.rootCastId;
        String instance = context != null ? context.skillInstanceId() : windup.instanceId;
        if (cancelled.get().phase() == SkillInstanceLifecycle.Phase.MOVEMENT)
            emit(request, RpgTraceEventType.MOVEMENT_CANCELLED, root, instance, Map.of("reason", reason));
        else if (cancelled.get().phase() == SkillInstanceLifecycle.Phase.REACTION)
            emit(request, RpgTraceEventType.REACTION_CANCELLED, root, instance, Map.of("reason", reason));
        emit(request, RpgTraceEventType.SKILL_TERMINATED, root, instance,
                Map.of("reason", reason, "phase", cancelled.get().phase().name()));
        return true;
    }

    /** Terminal owner cleanup; an ordinary interrupted windup retains earlier successful commits. */
    public void forgetPassiveState(UUID actor){attunement.forget(actor);ruthless.forget(actor);conditionalRepeats.forget(actor);retaliation.forget(actor);finisher.forget(actor);}
    private Map<com.inigmasgames.hytalerpg.domain.SkillSlot,String> retaliationPlans(UUID actor){
        var result=new java.util.EnumMap<com.inigmasgames.hytalerpg.domain.SkillSlot,String>(com.inigmasgames.hytalerpg.domain.SkillSlot.class);
        for(var item:loadouts.getPresentationView(actor).plans().entrySet()){var p=item.getValue();if(!p.degraded()&&p.retaliation())result.put(item.getKey(),p.planHash());}return result;
    }
    public String observeRetaliation(UUID actor,String event,double before,double after,double maximum,boolean hostile,boolean recursive){
        var plans=retaliationPlans(actor);if(plans.isEmpty())return "NO_RETALIATION_LINK";
        String code=retaliation.observe(actor,plans,event,before,after,maximum,hostile,recursive,now());
        try{tracer.trace(RpgTraceRecord.create(actor,RpgTraceEventType.RETALIATION_DAMAGE_OBSERVED,event,Map.of("verdict",code,"healthBefore",before,"healthAfter",after,"maximumHealth",maximum,"hostile",hostile,"recursive",recursive,"observationOnly",true)));}catch(RuntimeException ignored){}
        return code;
    }
    public SkillExecutionResult tickRetaliation(UUID actor,SkillExecutionPort port){
        if(!retaliation.tracked(actor)||releases.pendingRetaliation(actor))return null;
        var ticket=retaliation.claim(actor,retaliationPlans(actor),port.resources().maximum(ResourceType.HEALTH),now()).orElse(null);
        if(ticket==null)return null;
        var request=new SkillExecutionRequest(actor,ticket.slot(),"RETALIATION",ticket.correlation().hashCode(),ticket.correlation(),com.inigmasgames.hytalerpg.execution.math.Vec3.ZERO,SkillExecutionRequest.Origin.TRIGGERED);
        SkillExecutionResult result;
        try{result=request(request,port);}catch(RuntimeException failed){result=SkillExecutionResult.rejected("RETALIATION_REQUEST_FAILED_"+failed.getClass().getSimpleName());}
        if(result.status()!=SkillExecutionResult.Status.PENDING)retaliation.complete(actor,ticket.correlation(),result.committed(),now());
        try{tracer.trace(RpgTraceRecord.create(actor,RpgTraceEventType.RETALIATION_ATTEMPT,ticket.correlation(),Map.of("slot",ticket.slot(),"verdict",result.code(),"status",result.status(),"threshold",ticket.threshold(),"magnitudeFactor",.70,"normalPaymentRequired",true)));}catch(RuntimeException ignored){}
        return result;
    }
    public double retaliationAccumulated(UUID actor,com.inigmasgames.hytalerpg.domain.SkillSlot slot){return retaliation.accumulated(actor,slot,now());}
    private boolean currentConditionalPlan(SkillExecutionContext c){
        var plan=loadouts.getPresentationView(c.request().actorId()).plans().get(c.request().slot());
        return plan!=null&&!plan.degraded()&&plan.planHash().equals(c.compiledPlan().planHash());
    }
    public String observedConditionalRepeat(SkillExecutionContext source,ConditionalRepeatRuntime.Hit hit,SkillExecutionPort port){
        String verdict;
        try{verdict=currentConditionalPlan(source)?conditionalRepeats.observed(source,hit,now(),new ConditionalRepeatRuntime.Port(){
            public CommittedTarget killTarget(SkillExecutionContext c,ConditionalRepeatRuntime.Hit h){return port.conditionalKillTarget(c,h);}
            public String enqueue(SkillExecutionContext child,double due){return releases.conditional(child,due);}
        }):"COMMITTED_LOADOUT_CHANGED";}catch(RuntimeException failed){verdict="CONDITIONAL_REPEAT_OBSERVER_FAILED_"+failed.getClass().getSimpleName();}
        emit(source.request(),RpgTraceEventType.CONDITIONAL_REPEAT_RESOLVED,source.rootCastId(),source.skillInstanceId(),Map.of("kind",source.compiledPlan().conditionalRepeat(),"verdict",verdict,"victim",hit.victim(),"healthBefore",hit.before(),"healthAfter",hit.after(),"resourceCharged",false));
        return verdict;
    }
    public boolean nextRuthless(UUID actor,com.inigmasgames.hytalerpg.domain.SkillSlot slot){return ruthless.nextEmpowered(new com.inigmasgames.hytalerpg.execution.strike.RuthlessLedger.Key(actor,slot));}
    public int attunementStacks(UUID actor,com.inigmasgames.hytalerpg.domain.SkillSlot slot){return attunement.stacks(new AttunementLedger.Key(actor,slot),now());}

    public void recordExecutionFailure(SkillExecutionContext context,String stage,RuntimeException failure){
        emit(context.request(),RpgTraceEventType.SKILL_EXECUTION_FAILED,context.rootCastId(),context.skillInstanceId(),
                ExecutionFailureDiagnostics.describe(stage,failure));
    }
    public void terminate(SkillExecutionContext context, String reason) {
        if (lifecycle.terminate(context.request().actorId(), context.skillInstanceId())) {
            startEndedChannelCooldown(context);
            synchronized (activeContexts) { activeContexts.remove(context.request().actorId()); }
            emit(context.request(), RpgTraceEventType.SKILL_TERMINATED, context.rootCastId(),
                    context.skillInstanceId(), Map.of("reason", reason));
        }
    }

    private Prepared validate(SkillExecutionRequest request, SkillExecutionPort port, String root) {
        return validate(request, port, root, null);
    }
    private Prepared validate(SkillExecutionRequest request, SkillExecutionPort port, String root, String retainedInstance) {
        if (!port.actorAliveAndUsable()) throw new Rejection("ACTOR_NOT_USABLE", retainedInstance);
        if(releases.hasPendingPrimary(request.actorId(),request.slot())) throw new Rejection("PENDING_PRIMARY_FOR_SLOT",retainedInstance);
        var view = loadouts.getPresentationView(request.actorId());
        var skill = view.state().skill(request.slot()).orElseThrow(() -> new Rejection("EMPTY_SLOT", retainedInstance));
        CompiledSkillPlan plan = view.plans().get(request.slot());
        if (plan == null || plan.degraded()) throw new Rejection("COMPILED_PLAN_INVALID", retainedInstance);
        if (!profiles.supports(skill.value())) throw new Rejection("FAMILY_NOT_IMPLEMENTED", retainedInstance);
        Stage04SkillProfile profile = compiledProfiles.resolve(profiles.require(skill.value()),plan);
        if(plan.retaliation()){
            if(request.origin()==SkillExecutionRequest.Origin.MANUAL)throw new Rejection("RETALIATION_MANUAL_DISABLED",retainedInstance);
            if(!ProfileComponentPolicy.retaliation(profile)||!retaliation.authorized(request.actorId(),request.slot(),plan.planHash(),request.correlationId()))throw new Rejection("RETALIATION_RECEIPT_REQUIRED",retainedInstance);
        }
        if((plan.resources().lifeblood()||plan.resources().attunement())&&!ProfileComponentPolicy.finiteUpfront(profile))
            throw new Rejection("FINITE_UPFRONT_COMPONENT_REQUIRED",retainedInstance);
        if(plan.resources().lifeblood()&&(profile.reaction()!=null||request.origin()!=SkillExecutionRequest.Origin.MANUAL))
            throw new Rejection("LIFEBLOOD_MANUAL_UPFRONT_ONLY",retainedInstance);
        String instance = retainedInstance == null ? skill.value() + '-' + UUID.randomUUID() : retainedInstance;
        if(!profile.activationGate().isEmpty())throw new Rejection(profile.activationGate(),instance);
        if (!profile.family().name().equals(plan.finalFamily())
                && !plan.finalTags().contains(profile.family().name()))
            throw new Rejection("COMPILED_FAMILY_UNSUPPORTED", instance);
        SkillExecutionPort.Equipment equipment = port.equipment();
        try { validateEquipment(profile, equipment, instance); }
        catch (Rejection rejection) {
            emitProjectileRejection(request, root, instance, profile, rejection.code);
            throw rejection;
        }
        // A native Family classification alone is not authored damage authority. Reject before payment.
        if (java.util.Set.of("WEAPON", "MAGIC_WEAPON", "OFFHAND_WEAPON").contains(profile.basePowerSource())) {
            try { resolvePower(profile, equipment); }
            catch (IllegalArgumentException failure) {
                preparationFailure(request, root, instance, profile, equipment, "EQUIPMENT_POWER_VALIDATION", failure);
                emitProjectileRejection(request, root, instance, profile, "EQUIPMENT_POWER_UNAVAILABLE");
                throw new Rejection("EQUIPMENT_POWER_UNAVAILABLE", instance);
            }
        }
        SkillExecutionPort.Validation family = port.familyPrerequisites(profile, plan);
        if (!family.accepted()) {
            emitProjectileRejection(request, root, instance, profile, family.code());
            throw new Rejection(family.code(), instance);
        }
        ResourceCost declared = new ResourceCost(ResourceType.valueOf(profile.resourceType()), profile.resourceCost());
        int stacks=attunementFor(request,plan);
        ruthlessFor(request,plan); // Capacity and origin check before any payment or windup.
        ResourceCost cost = kernel.resources().evaluateActivation(declared, plan,stacks);
        if(profile.support()!=null&&profile.support().upkeepPerSecond()>0){
            var first=kernel.resources().evaluateUpkeep(new ResourceCost(ResourceType.MANA,profile.support().upkeepPerSecond()*.25*plan.supportModifiers().commitmentFactor()),plan.kernelModifiers());
            if(!kernel.resources().canAfford(request.actorId(),new ResourceCost(ResourceType.MANA,cost.amount()+first.amount()),port.resources()))
                throw new Rejection("AURA_INITIAL_UPKEEP_UNAFFORDABLE",retainedInstance);
        }
        if (!kernel.resources().canAfford(request.actorId(), cost, port.resources())) {
            emitProjectileRejection(request, root, instance, profile, "INSUFFICIENT_RESOURCE");
            throw new Rejection("INSUFFICIENT_RESOURCE", instance);
        }
        if (!kernel.cooldowns().canActivate(request.actorId(), profile.skillId(),plan.foundationModifiers().chargeCapacity())) {
            emitProjectileRejection(request, root, instance, profile, "COOLDOWN_ACTIVE");
            throw new Rejection("COOLDOWN_ACTIVE", instance);
        }
        return new Prepared(request, root, instance, profile, plan, cost, equipment,stacks,false);
    }

    private int attunementFor(SkillExecutionRequest request,CompiledSkillPlan plan){
        if(!plan.resources().attunement()||request.origin()!=SkillExecutionRequest.Origin.MANUAL)return 0;
        var key=new AttunementLedger.Key(request.actorId(),request.slot());
        if(!attunement.hasCapacity(key,now()))throw new Rejection("ATTUNEMENT_LEDGER_CAPACITY",null);
        return attunement.stacks(key,now());
    }
    private boolean ruthlessFor(SkillExecutionRequest request,CompiledSkillPlan plan){
        if(!plan.strikes().ruthless()||request.origin()!=SkillExecutionRequest.Origin.MANUAL)return false;
        var key=new com.inigmasgames.hytalerpg.execution.strike.RuthlessLedger.Key(request.actorId(),request.slot());
        if(!ruthless.hasCapacity(key))throw new Rejection("RUTHLESS_LEDGER_CAPACITY",null);
        return ruthless.nextEmpowered(key);
    }

    private SkillExecutionResult commitAndDispatch(Prepared prepared, SkillExecutionPort port) {
        // Re-evaluate expiry at the actual commit, not when an interruptible windup began.
        try{
            int stacks=attunementFor(prepared.request,prepared.plan);
            boolean powered=ruthlessFor(prepared.request,prepared.plan);
            var cost=kernel.resources().evaluateActivation(new ResourceCost(ResourceType.valueOf(prepared.profile.resourceType()),prepared.profile.resourceCost()),prepared.plan,stacks);
            var profile=CompiledProfileResolver.ruthless(compiledProfiles.resolve(profiles.require(prepared.profile.skillId()),prepared.plan),powered);
            prepared=new Prepared(prepared.request,prepared.rootCastId,prepared.instanceId,profile,prepared.plan,cost,prepared.equipment,stacks,powered);
        }catch(RuntimeException failed){lifecycle.terminate(prepared.request.actorId(),prepared.instanceId);return reject(prepared.request,prepared.rootCastId,prepared.instanceId,"COMMIT_RESOURCE_MODIFIER_REJECTED");}
        var releaseModifiers=prepared.plan.executionModifiers();
        String admission=releases.reserve(prepared.instanceId,prepared.request.actorId(),prepared.request.slot(),releaseModifiers);
        if(!admission.equals("PASS")) {
            lifecycle.terminate(prepared.request.actorId(),prepared.instanceId);
            return reject(prepared.request,prepared.rootCastId,prepared.instanceId,admission);
        }
        CommittedTarget target;
        try {
            boolean capture=port.requiresSpatialCommitContext()||releaseModifiers.scheduled()||!prepared.plan.conditionalRepeat().isEmpty()||prepared.plan.strikes().multistrike()||prepared.plan.zones().mobileDomain()||prepared.profile.connection()!=null&&prepared.profile.connection().requiresTarget()
                    ||prepared.profile.support()!=null||prepared.profile.summon()!=null||prepared.profile.summonAction()!=null||prepared.profile.conversion()!=null;
            target=capture?port.captureTarget(prepared.profile,prepared.plan,prepared.request):null;
            if(capture && target==null) throw new IllegalStateException("COMMITTED_TARGET_ADAPTER_UNAVAILABLE");
        } catch(RuntimeException error) {
            releases.finish(prepared.instanceId);lifecycle.terminate(prepared.request.actorId(),prepared.instanceId);
            return reject(prepared.request,prepared.rootCastId,prepared.instanceId,"TARGET_CAPTURE_FAILED_"+error.getMessage());
        }
        com.inigmasgames.hytalerpg.combat.resource.RpgResourceService.CostToken token;
        try { token=kernel.resources().reserveCost(prepared.request.actorId(),prepared.cost,port.resources()); }
        catch(RuntimeException error) {
            releases.finish(prepared.instanceId);lifecycle.terminate(prepared.request.actorId(),prepared.instanceId);
            return reject(prepared.request,prepared.rootCastId,prepared.instanceId,"RESOURCE_RESERVATION_FAILED");
        }
        SkillExecutionContext context;
        String preparationStage="ATTRIBUTE_SNAPSHOT";
        try {
            DerivedStats attributes = derive(prepared.request.actorId());
            preparationStage="POWER_RESOLUTION";
            BasePowerResolver.Resolution power = resolvePower(prepared.profile, prepared.equipment);
            preparationStage="COOLDOWN_PREPARATION";
            var cooldown = kernel.cooldowns().calculate(prepared.request.actorId(),prepared.profile.cooldownSeconds(), prepared.plan.foundationModifiers().rechargeFactor(),
                    attributes.cooldownRecovery(), prepared.plan.kernelModifiers());
            preparationStage="MODIFIER_CONSTRUCTION";
            Map<String, Double> status = prepared.profile.authoredStatuses();
            // CombatSnapshotFactory alone installs compiled Increased modifiers (including Potency).
            var payloadLess=new java.util.ArrayList<>(prepared.plan.projectileModifiers().payloadLess());
            if(releaseModifiers.expandedRadius()&&!prepared.plan.radiusOnlyOnSecondary())payloadLess.add(.10);
            if(prepared.plan.zones().mobileDomain())payloadLess.add(.20);
            if(prepared.plan.retaliation())payloadLess.add(.30);
            if(prepared.plan.positions().active()&&!prepared.plan.positionOnlyOnSecondary())payloadLess.add(.10);
            if(prepared.plan.orbit())payloadLess.add(1-com.inigmasgames.hytalerpg.execution.connection.OrbitConversionProfiles.CONFIG.magnitudeFactor());
            ModifierBuckets modifiers = new ModifierBuckets(prepared.attunementStacks>0?java.util.List.of(.03*prepared.attunementStacks):java.util.List.of(), java.util.List.of(),
                    releaseModifiers.delaySeconds()>0?java.util.List.of(1.35):java.util.List.of(),
                    payloadLess);
            preparationStage="MASTERY_PREPARATION";
            double mastery=com.inigmasgames.hytalerpg.progress.ProgressionMath.masteryMagnitude(loadouts.masteryXp(prepared.request.actorId(),prepared.profile.skillId()));
            if(mastery!=1){
                var more=new java.util.ArrayList<>(modifiers.more());more.add(mastery);
                modifiers=new ModifierBuckets(modifiers.increased(),modifiers.reduced(),more,modifiers.less());
            }
            if(prepared.ruthlessEmpowered)modifiers=modifiers.withIncreased(.60);
            preparationStage="SUMMON_MODIFIERS";
            if(prepared.profile.summon()!=null)modifiers=port.captureSummonModifiers(modifiers);
            preparationStage="SNAPSHOT_CONSTRUCTION";
            var snapshot = kernel.snapshots().capture(prepared.rootCastId, prepared.instanceId,
                    prepared.request.actorId(), attributes, power, prepared.plan,
                    prepared.profile.damageCoefficient(),
                    modifiers, prepared.cost, cooldown.finalSeconds(), status);
            preparationStage="CONTEXT_CONSTRUCTION";
            context = new SkillExecutionContext(prepared.request, prepared.rootCastId, prepared.instanceId,
                    prepared.profile, prepared.plan, snapshot, prepared.equipment,target,false);
            preparationStage="LEECH_BUDGET";
            if(prepared.plan.resources().leeching()){
                var resource=ResourceType.valueOf(prepared.profile.resourceType());
                context.leechBudget().initialize(resource,kernel.resources().spendableMaximum(prepared.request.actorId(),resource,port.resources()));
            }
            preparationStage="PERSISTENCE_PREPARATION";
            return preparePersistence(prepared,context,token,attributes,port);
        } catch(RuntimeException error){
            preparationFailure(prepared.request, prepared.rootCastId, prepared.instanceId, prepared.profile,
                    prepared.equipment, preparationStage, error);
            kernel.resources().refundIfUncommitted(token);releases.finish(prepared.instanceId);lifecycle.terminate(prepared.request.actorId(),prepared.instanceId);
            return reject(prepared.request,prepared.rootCastId,prepared.instanceId,"COMMIT_PREPARATION_FAILED_"+error.getClass().getSimpleName());
        }
    }
    private final Map<UUID,PendingCommit> persistenceCommits=new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<UUID> cancelledPersistence=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private java.util.function.Consumer<SkillExecutionContext> durableAbandon=ignored->{};
    public void configureDurableAbandon(java.util.function.Consumer<SkillExecutionContext> valuesOnly){durableAbandon=java.util.Objects.requireNonNull(valuesOnly);}
    /** Bounded owner polling also settles disconnected actors; no native reference is retained. */
    public void pollCancelledPersistence(){
        int count=0;for(var actor:cancelledPersistence){if(++count>8)break;var pending=persistenceCommits.get(actor);
            if(pending==null){cancelledPersistence.remove(actor);continue;}
            if(!pending.cooldown.isDone()||!pending.authority.isDone())continue;
            try{
                var spend=pending.cooldown.getNow(null);if(spend!=null){kernel.cooldowns().acceptSpend(spend);kernel.cooldowns().submitRefund(spend);}
            }catch(RuntimeException error){
                if(!pending.cooldown.isCompletedExceptionally())continue; // capacity rejection is retried, never a failed uncertain debt
            }
            if(channel(pending.prepared.profile))channelCooldowns.remove(actor);
            persistenceCommits.remove(actor,pending);cancelledPersistence.remove(actor);
        }
    }
    private record PendingCommit(Prepared prepared,SkillExecutionContext context,
            com.inigmasgames.hytalerpg.combat.resource.RpgResourceService.CostToken token,
            java.util.concurrent.CompletableFuture<com.inigmasgames.hytalerpg.combat.cooldown.RpgCooldownService.Spend> cooldown,
            java.util.concurrent.CompletableFuture<Void> authority){}
    private SkillExecutionResult preparePersistence(Prepared prepared,SkillExecutionContext context,
            com.inigmasgames.hytalerpg.combat.resource.RpgResourceService.CostToken token,DerivedStats attributes,SkillExecutionPort port){
        var authority=new java.util.concurrent.CompletableFuture<Void>();
        var cooldown=new java.util.concurrent.CompletableFuture<com.inigmasgames.hytalerpg.combat.cooldown.RpgCooldownService.Spend>();
        var pending=new PendingCommit(prepared,context,token,cooldown,authority);
        // The completion/settlement ticket exists BEFORE either downstream submission. Even
        // partial admission has a bounded owner; cancellation never discards an in-flight debt.
        synchronized(persistenceCommits){
            if(persistenceCommits.size()>=256||persistenceCommits.containsKey(prepared.request.actorId()))throw new IllegalStateException("PENDING_COMMIT_CAPACITY");
            persistenceCommits.put(prepared.request.actorId(),pending);
        }
        try{
            port.prepareDurable(context).whenComplete((value,error)->{if(error==null)authority.complete(null);else authority.completeExceptionally(error);});
        }catch(RuntimeException error){authority.completeExceptionally(error);cooldown.complete(null);}
        if(!cooldown.isDone())try{
            if(channel(prepared.profile)){
                synchronized(channelCooldowns){
                    if(channelCooldowns.size()>=256)throw new IllegalStateException("CHANNEL_COOLDOWN_PERSISTENCE_CAPACITY");
                    channelCooldowns.put(prepared.request.actorId(),new ChannelCooldown(context));
                }
                cooldown.complete(null);
            }else kernel.cooldowns().submitSpend(prepared.request.actorId(),prepared.profile.skillId(),prepared.plan.foundationModifiers().chargeCapacity(),
                    prepared.profile.cooldownSeconds(),prepared.plan.foundationModifiers().rechargeFactor(),attributes.cooldownRecovery(),prepared.plan.kernelModifiers())
                    .whenComplete((value,error)->{if(error==null)cooldown.complete(value);else cooldown.completeExceptionally(error);});
        }catch(RuntimeException error){cooldown.completeExceptionally(error);}
        if(cooldown.isDone()&&authority.isDone())return completePersistence(prepared.request.actorId(),port);
        return SkillExecutionResult.pending("DURABLE_COMMIT_PENDING");
    }
    /** Existing native owner tick calls this; a ready check never starts a blocking storage operation. */
    public SkillExecutionResult completePersistence(UUID actor,SkillExecutionPort port){
        pollCancelledPersistence();
        pollChannelCooldowns();
        if(cancelledPersistence.contains(actor))return SkillExecutionResult.pending("CANCELLED_COMMIT_SETTLEMENT_PENDING");
        var pending=persistenceCommits.get(actor);
        if(pending==null)return null;
        if(!pending.cooldown.isDone()||!pending.authority.isDone())return SkillExecutionResult.pending("DURABLE_COMMIT_PENDING");
        persistenceCommits.remove(actor,pending);
        return finishPersistence(pending,port);
    }
    private SkillExecutionResult finishPersistence(PendingCommit pending,SkillExecutionPort port){
        var prepared=pending.prepared;var context=pending.context;var token=pending.token;
        var releaseModifiers=prepared.plan.executionModifiers();
        boolean resourceCommitted=false;boolean cooldownStarted=false;
        com.inigmasgames.hytalerpg.combat.cooldown.RpgCooldownService.Spend cooldownSpend=null;
        AttunementLedger.Commit attunementCommit=null;
        com.inigmasgames.hytalerpg.execution.strike.RuthlessLedger.Commit ruthlessCommit=null;
        try{
            cooldownSpend=pending.cooldown.getNow(null);
            if(cooldownSpend!=null){kernel.cooldowns().acceptSpend(cooldownSpend);cooldownStarted=true;}
            pending.authority.getNow(null);
            if(!port.actorAliveAndUsable()||!port.equipment().equals(prepared.equipment)
                    ||!lifecycle.active(prepared.request.actorId()).map(a->a.instanceId().equals(prepared.instanceId)).orElse(false))
                throw new IllegalStateException("PENDING_COMMIT_OWNER_CHANGED");
            var plan=loadouts.getPresentationView(prepared.request.actorId()).plans().get(prepared.request.slot());
            if(plan==null||!plan.planHash().equals(prepared.plan.planHash()))throw new IllegalStateException("PENDING_COMMIT_LOADOUT_CHANGED");
            var ownerValidation=port.validateDurableCompletion(context);
            if(!ownerValidation.accepted())throw new IllegalStateException(ownerValidation.code());
            kernel.resources().commitCost(token,port.resources());resourceCommitted=true;
            if(prepared.plan.resources().attunement()&&prepared.request.origin()==SkillExecutionRequest.Origin.MANUAL)
                attunementCommit=attunement.committed(new AttunementLedger.Key(prepared.request.actorId(),prepared.request.slot()),prepared.rootCastId,now());
            if(prepared.plan.strikes().ruthless()&&prepared.request.origin()==SkillExecutionRequest.Origin.MANUAL)
                ruthlessCommit=ruthless.committed(new com.inigmasgames.hytalerpg.execution.strike.RuthlessLedger.Key(prepared.request.actorId(),prepared.request.slot()),prepared.rootCastId);
        }catch(RuntimeException error){
            attunement.rollback(attunementCommit);ruthless.rollback(ruthlessCommit);
            if(cooldownStarted)try{kernel.cooldowns().submitRefund(cooldownSpend);}catch(RuntimeException settlementRejected){
                // Reuse this cast's reserved ticket; a full queue cannot erase owed settlement.
                persistenceCommits.put(prepared.request.actorId(),pending);cancelledPersistence.add(prepared.request.actorId());
            }
            try{if(resourceCommitted)kernel.resources().refundCommittedCost(token,port.resources());else kernel.resources().refundIfUncommitted(token);}catch(RuntimeException ignored){}
            if(channel(prepared.profile))channelCooldowns.remove(prepared.request.actorId());
            port.abandonDurable(context);lifecycle.terminate(prepared.request.actorId(),prepared.instanceId);releases.finish(prepared.instanceId);
            return reject(prepared.request,prepared.rootCastId,prepared.instanceId,"COMMIT_FAILED_"+error.getClass().getSimpleName());
        }
        emit(prepared.request, RpgTraceEventType.SKILL_COMMITTED, prepared.rootCastId, prepared.instanceId,
                Map.of("skillId", prepared.profile.skillId(), "resourceCost", prepared.cost.amount(),"resourceType",prepared.cost.type(),"attunementStacksUsed",prepared.attunementStacks,"ruthlessEmpowered",prepared.ruthlessEmpowered,
                        "cooldownSeconds", context.snapshot().cooldownSeconds(),
                        "compiledPlanHash", prepared.plan.planHash(),"chargeCapacity",prepared.plan.foundationModifiers().chargeCapacity(),
                        "chargesRemaining",kernel.cooldowns().availableCharges(prepared.request.actorId(),prepared.profile.skillId(),prepared.plan.foundationModifiers().chargeCapacity())));
        try{port.commitConsumable(context);}catch(RuntimeException failure){
            kernel.resources().finish(token);releases.finish(prepared.instanceId);
            port.abandonRelease(context);terminate(context,"CONSUMABLE_COMMIT_FAILED_"+failure.getMessage());
            return new SkillExecutionResult(SkillExecutionResult.Status.TERMINATED,"CONSUMABLE_COMMIT_FAILED",true,0,0);
        }
        releases.arm(context,now());
        if(releaseModifiers.delaySeconds()>0) {
            kernel.resources().finish(token);lifecycle.terminate(prepared.request.actorId(),prepared.instanceId);
            emit(prepared.request,RpgTraceEventType.SKILL_RELEASE_SCHEDULED,prepared.rootCastId,prepared.instanceId,
                    Map.of("delaySeconds",releaseModifiers.delaySeconds(),"echo",false,"paid",true));
            return SkillExecutionResult.committed("RELEASE_PENDING",0,0);
        }
        emit(prepared.request, RpgTraceEventType.EXECUTOR_DISPATCH, prepared.rootCastId, prepared.instanceId,
                Map.of("family", prepared.profile.family().name()));
        SkillExecutionResult result;
        try {
            resolveAuthoredRelease(context);
            result = executors.require(prepared.profile.family()).execute(context, port);
            kernel.resources().finish(token);
            if (result.committed()) {
                releases.primaryReleased(context,now());
                traceEchoSchedule(context);
            } else {
                releases.finish(prepared.instanceId);
                terminate(context, "EXECUTOR_DID_NOT_RELEASE");
                return new SkillExecutionResult(SkillExecutionResult.Status.TERMINATED,
                        "EXECUTOR_DID_NOT_RELEASE", true, 0, 0);
            }
        }
        catch (RuntimeException error) {
            releases.finish(prepared.instanceId);
            // Dispatch is the irreversible boundary for EVERY family: a hit, movement, projectile,
            // or defensive effect may precede a late adapter failure. Retain the committed cost,
            // cooldown and commit counters; only pre-dispatch transaction failures may roll back.
            kernel.resources().finish(token);
            recordExecutionFailure(context,"EXECUTOR_DISPATCH",error);
            terminate(context, "EXECUTOR_ERROR_" + error.getClass().getSimpleName());
            return new SkillExecutionResult(SkillExecutionResult.Status.TERMINATED,
                    "EXECUTOR_ERROR", true, 0, 0.0);
        }
        retainExecutionLifecycle(context);
        return result;
    }

    private void retainExecutionLifecycle(SkillExecutionContext context) {
        if(channel(context.profile())) {
            if(!lifecycle.transition(context.request().actorId(),context.skillInstanceId(),SkillInstanceLifecycle.Phase.COMMITTED,SkillInstanceLifecycle.Phase.CHANNEL))
                throw new IllegalStateException("Channel lifecycle transition failed");
            synchronized(activeContexts){activeContexts.put(context.request().actorId(),context);}
        } else if (context.profile().area() != null || context.profile().connection()!=null || context.profile().support()!=null) {
            // The area registry owns the finite effect after dispatch; it does not lock unrelated casts for its lifetime.
            lifecycle.terminate(context.request().actorId(), context.skillInstanceId());
        } else if (context.profile().family() == Stage04SkillProfile.Family.STRIKE
                && (context.profile().strike().repeats() > 1 && context.profile().strike().repeatIntervalSeconds() > 0.0
                    || context.profile().strike().details().actionLockSeconds()>0)) {
            if (!lifecycle.transition(context.request().actorId(), context.skillInstanceId(),
                    SkillInstanceLifecycle.Phase.COMMITTED, SkillInstanceLifecycle.Phase.STRIKE_REPEAT))
                throw new IllegalStateException("Strike-repeat lifecycle transition failed");
            synchronized (activeContexts) { activeContexts.put(context.request().actorId(), context); }
        } else if (context.profile().family() == Stage04SkillProfile.Family.MOVEMENT) {
            if (!lifecycle.transition(context.request().actorId(), context.skillInstanceId(),
                    SkillInstanceLifecycle.Phase.COMMITTED, SkillInstanceLifecycle.Phase.MOVEMENT))
                throw new IllegalStateException("Movement lifecycle transition failed");
            synchronized (activeContexts) { activeContexts.put(context.request().actorId(), context); }
        } else if (context.profile().family() == Stage04SkillProfile.Family.REACTION) {
            if (!lifecycle.transition(context.request().actorId(), context.skillInstanceId(),
                    SkillInstanceLifecycle.Phase.COMMITTED, SkillInstanceLifecycle.Phase.REACTION))
                throw new IllegalStateException("Reaction lifecycle transition failed");
            synchronized (activeContexts) { activeContexts.put(context.request().actorId(), context); }
        } else if (context.profile().family() == Stage04SkillProfile.Family.PROJECTILE) {
            if (!lifecycle.transition(context.request().actorId(), context.skillInstanceId(),
                    SkillInstanceLifecycle.Phase.COMMITTED, SkillInstanceLifecycle.Phase.PROJECTILE))
                throw new IllegalStateException("Projectile lifecycle transition failed");
            synchronized (activeContexts) { activeContexts.put(context.request().actorId(), context); }
        } else terminate(context, context.profile().conversion()!=null?"CONVERSION_DISPATCH_COMPLETE":(context.profile().summon()!=null||context.profile().summonAction()!=null)?"SUMMON_DISPATCH_COMPLETE":"STRIKE_COMPLETE");
    }

    private double now() { return nanoTime.getAsLong()/1e9; }
    private static boolean channel(Stage04SkillProfile profile){return profile.connection()!=null&&profile.connection().channel();}
    private void startEndedChannelCooldown(SkillExecutionContext context) {
        if(!channel(context.profile())||context.derivedRelease())return;
        var pending=channelCooldowns.get(context.request().actorId());
        if(pending==null)throw new IllegalStateException("CHANNEL_COOLDOWN_TICKET_MISSING");
        pending.ended=true;pollChannelCooldowns();
    }
    private static final class ChannelCooldown {
        final SkillExecutionContext context;boolean ended;
        java.util.concurrent.CompletableFuture<com.inigmasgames.hytalerpg.combat.cooldown.RpgCooldownService.Spend> receipt;
        ChannelCooldown(SkillExecutionContext context){this.context=context;}
    }
    private final Map<UUID,ChannelCooldown> channelCooldowns=new java.util.concurrent.ConcurrentHashMap<>();
    public void pollChannelCooldowns(){synchronized(channelCooldowns){
        int count=0;for(var entry:channelCooldowns.entrySet()){
            if(++count>8)break;var pending=entry.getValue();if(!pending.ended)continue;
            if(pending.receipt==null){
                var c=pending.context;
                try{pending.receipt=kernel.cooldowns().submitSpend(c.request().actorId(),c.profile().skillId(),1,c.profile().cooldownSeconds(),
                        1,c.snapshot().derivedStats().cooldownRecovery(),c.compiledPlan().kernelModifiers()).toCompletableFuture();}
                catch(RuntimeException unavailable){continue;} // Reserved intent survives capacity/uncertainty; no new cast is admitted.
            }
            if(!pending.receipt.isDone())continue;
            var spend=pending.receipt.getNow(null);kernel.cooldowns().acceptSpend(spend);channelCooldowns.remove(entry.getKey(),pending);
            var context=pending.context;
            emit(context.request(),RpgTraceEventType.COOLDOWN_STARTED,context.rootCastId(),context.skillInstanceId(),
                    Map.of("skillId",context.profile().skillId(),"afterChannelEnd",true,"seconds",spend.calculation().finalSeconds()));
        }}
    }
    public int pendingReleaseCount() { return releases.size(); }

    /** Called from the owner's actual world tick with a fresh native port, never a stale retained command buffer. */
    public void tickScheduled(UUID actor, SkillExecutionPort port) {
        // At most three authored releases per reservation; a late tick can drain fixed Barrage offsets.
        for(int pass=0;pass<3;pass++) for(var release:releases.due(actor,now())) {
            if(!releases.isCurrent(release)) continue;
            var context=release.context();
            try {
                if(context.conditionalRepeat()&&!currentConditionalPlan(context)){port.abandonRelease(context);cancelRelease(release,"COMMITTED_LOADOUT_CHANGED");continue;}
                SkillExecutionPort.Validation validation=port.actorAliveAndUsable()?port.validateRelease(context)
                        :SkillExecutionPort.Validation.reject("ACTOR_NOT_USABLE");
                if(!validation.accepted()) {
                    port.abandonRelease(context);
                    cancelRelease(release,validation.code());continue;
                }
                if(!releases.isCurrent(release)) continue;
                if(!context.derivedRelease() && !lifecycle.begin(actor,context.skillInstanceId(),SkillInstanceLifecycle.Phase.COMMITTED)) {
                    port.abandonRelease(context);
                    cancelRelease(release,"INCOMPATIBLE_ACTIVE_STATE");continue;
                }
                emit(context.request(),RpgTraceEventType.SKILL_RELEASED,context.rootCastId(),context.skillInstanceId(),
                        Map.of("echo",context.echo(),"barrageBatch",context.barrageBatch(),"resourceCharged",false));
                emit(context.request(),RpgTraceEventType.EXECUTOR_DISPATCH,context.rootCastId(),context.skillInstanceId(),
                        Map.of("family",context.profile().family().name(),"echo",context.echo(),"barrageBatch",context.barrageBatch()));
                resolveAuthoredRelease(context);
                var outcome=executors.require(context.profile().family()).execute(context,port);
                if(!outcome.committed()) {
                    port.abandonRelease(context);
                    cancelRelease(release,"EXECUTOR_DID_NOT_RELEASE");continue;
                }
                if(context.derivedRelease()) releases.additionalReleased(release);
                else {
                    retainExecutionLifecycle(context);
                    releases.primaryReleased(context,now());
                    traceEchoSchedule(context);
                }
            } catch(RuntimeException error) {
                port.abandonRelease(context);
                cancelRelease(release,"RELEASE_FAILURE_"+error.getClass().getSimpleName()+":"+String.valueOf(error.getMessage()));
            }
        }
    }
    private void traceEchoSchedule(SkillExecutionContext context) {
        var modifiers=context.compiledPlan().executionModifiers();
        for(int batch=1;batch<modifiers.barrageBatches();batch++)
            emit(context.request(),RpgTraceEventType.SKILL_RELEASE_SCHEDULED,context.rootCastId(),context.skillInstanceId(),
                    Map.of("delaySeconds",batch*modifiers.barrageInterval(),"barrageBatch",batch,"echo",false,"paid",true));
        if(context.compiledPlan().executionModifiers().echoDelaySeconds()>0)
            emit(context.request(),RpgTraceEventType.SKILL_RELEASE_SCHEDULED,context.rootCastId(),context.skillInstanceId()+"/echo",
                    Map.of("delaySeconds",context.compiledPlan().executionModifiers().echoDelaySeconds(),"echo",true,"paid",true));
    }
    private void cancelRelease(SkillReleaseScheduler.Release release,String reason) {
        releases.finish(release.reservation());var context=release.context();
        if(!context.derivedRelease()) terminate(context,"RELEASE_CANCELLED");
        emit(context.request(),RpgTraceEventType.SKILL_RELEASE_CANCELLED,context.rootCastId(),context.skillInstanceId(),
                Map.of("reason",reason,"refund",false));
    }

    private DerivedStats derive(UUID actor) {
        var state = loadouts.getPresentationView(actor).state();
        EnumMap<RpgAttribute, Integer> raw = new EnumMap<>(RpgAttribute.class);
        for (RpgAttribute attribute : RpgAttribute.values()) raw.put(attribute,
                state.attributes.getOrDefault(attribute.name(), 10));
        return kernel.derivedStats().derive(raw);
    }

    private BasePowerResolver.Resolution resolvePower(Stage04SkillProfile profile, SkillExecutionPort.Equipment equipment) {
        return switch (profile.basePowerSource()) {
            case "NONE" -> kernel.basePower().resolve(new BasePowerResolver.Request(BasePowerSource.NONE, null, null));
            case "INNATE" -> kernel.basePower().resolve(new BasePowerResolver.Request(BasePowerSource.INNATE, null,
                    profile.innateBasePower()));
            case "OFFHAND_WEAPON" -> kernel.basePower().resolve(new BasePowerResolver.Request(BasePowerSource.WEAPON,
                    equipment.offHand().power(), null));
            case "MAGIC_WEAPON" -> kernel.basePower().resolve(new BasePowerResolver.Request(BasePowerSource.MAGIC_WEAPON,
                    equipment.mainHand().power(), null));
            case "WEAPON" -> kernel.basePower().resolve(new BasePowerResolver.Request(BasePowerSource.WEAPON,
                    equipment.mainHand().power(), null));
            default -> throw new IllegalArgumentException("Unsupported power source " + profile.basePowerSource());
        };
    }

    private static void validateEquipment(Stage04SkillProfile profile, SkillExecutionPort.Equipment equipment,
                                          String instance) {
        if (!profile.allowedMainHandKinds().isEmpty() && (equipment == null || equipment.mainHand() == null
                || !profile.allowedMainHandKinds().contains(equipment.mainHand().weaponKind())))
            throw new Rejection("INVALID_MAIN_HAND", instance);
        if (!profile.requiredOffHandKinds().isEmpty() && (equipment == null || equipment.offHand() == null
                || !profile.requiredOffHandKinds().contains(equipment.offHand().weaponKind())))
            throw new Rejection("INVALID_OFF_HAND", instance);
    }

    private SkillExecutionResult reject(SkillExecutionRequest request, String root, String instance, String code) {
        return reject(request,root,instance,code,Map.of());
    }
    private SkillExecutionResult reject(SkillExecutionRequest request, String root, String instance, String code,Map<String,?> diagnostic) {
        String id = instance == null ? "pending-" + request.slot().externalId() : instance;
        var details=new LinkedHashMap<String,Object>();details.putAll(diagnostic);details.put("failureCode",code);
        emit(request, RpgTraceEventType.SKILL_VALIDATION_REJECTED, root, id, details);
        emit(request, RpgTraceEventType.SKILL_ACTIVATION_REJECTED, root, id, Map.of("failureCode", code));
        return SkillExecutionResult.rejected(code);
    }
    private void preparationFailure(SkillExecutionRequest request, String root, String instance,
            Stage04SkillProfile profile, SkillExecutionPort.Equipment equipment, String stage, RuntimeException failure) {
        emit(request, RpgTraceEventType.SKILL_PREPARATION_FAILED, root, instance,
                PreparationFailureDiagnostics.describe(stage, profile, equipment, failure));
    }
    private void emitProjectileRejection(SkillExecutionRequest request, String root, String instance,
                                          Stage04SkillProfile profile, String code) {
        if (profile.family() != Stage04SkillProfile.Family.PROJECTILE) return;
        emit(request, RpgTraceEventType.PROJECTILE_SPAWN_REJECTED, root, instance,
                Map.of("projectileInstanceId", instance + "-projectile-0", "skillId", profile.skillId(),
                        "generation", 0, "caster", request.actorId().toString(), "reason", code));
    }
    private void emit(SkillExecutionRequest request, RpgTraceEventType type, String root, String instance,
                      Map<String, ?> values) {
        Map<String, Object> details = new LinkedHashMap<>(); details.put("rootCastId", root);
        details.put("skillInstanceId", instance); details.putAll(values);
        try { tracer.trace(RpgTraceRecord.create(request.actorId(), type, request.correlationId(), details)); }
        catch (Throwable ignored) { }
    }
    private record Prepared(SkillExecutionRequest request, String rootCastId, String instanceId,
                            Stage04SkillProfile profile, CompiledSkillPlan plan, ResourceCost cost,
                            SkillExecutionPort.Equipment equipment,int attunementStacks,boolean ruthlessEmpowered) { }
    private static final class Rejection extends RuntimeException {
        private final String code; private final String skillInstanceId;
        private Rejection(String code, String skillInstanceId) { super(code); this.code = code; this.skillInstanceId = skillInstanceId; }
    }
}
