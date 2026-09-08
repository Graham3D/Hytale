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
    private final CompiledProfileResolver compiledProfiles = new CompiledProfileResolver();
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
    }

    public SkillExecutionResult request(SkillExecutionRequest request, SkillExecutionPort port) {
        String root = "input-" + request.chainId() + '-' + request.correlationId().substring(0, Math.min(8, request.correlationId().length()));
        String pendingInstance = "activation-" + UUID.randomUUID();
        emit(request, RpgTraceEventType.SKILL_ACTIVATION_REQUEST, root, pendingInstance,
                Map.of("action", request.action(), "skillSlot", request.slot().externalId()));
        Prepared prepared;
        try {
            var equipped=loadouts.getPresentationView(request.actorId()).state().skill(request.slot());
            if(equipped.isPresent()&&profiles.supports(equipped.get().value())){
                var profile=profiles.require(equipped.get().value());
                if(profile.support()!=null&&profile.support().aura()){
                    var stopped=port.stopActiveSupport(profile);
                    if(stopped!=null)return stopped.committed()?stopped:reject(request,root,pendingInstance,stopped.code());
                }
            }
            prepared = validate(request, port, root, pendingInstance);
        }
        catch (Rejection rejection) {
            return reject(request, root, rejection.skillInstanceId, rejection.code);
        } catch (RuntimeException error) {
            return reject(request, root, pendingInstance, "VALIDATION_ERROR_" + error.getClass().getSimpleName());
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
            return commitAndDispatch(current, port);
        } catch (Rejection rejection) {
            lifecycle.terminate(actor, prepared.instanceId);
            return reject(prepared.request, prepared.rootCastId, prepared.instanceId, rejection.code);
        }
    }

    public OptionalDouble activeWindupSeconds(UUID actor) {
        synchronized (windups) {
            Prepared value = windups.get(actor);
            return value == null ? OptionalDouble.empty() : OptionalDouble.of(value.profile.windupSeconds());
        }
    }

    public boolean cancel(UUID actor, String reason) {
        var queued=releases.cancel(actor);
        for(var pending:queued) emit(pending.request(),RpgTraceEventType.SKILL_RELEASE_CANCELLED,
                pending.rootCastId(),pending.skillInstanceId(),Map.of("reason",reason,"refund",false));
        Prepared windup;
        synchronized (windups) { windup = windups.remove(actor); }
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
        String instance = retainedInstance == null ? skill.value() + '-' + UUID.randomUUID() : retainedInstance;
        if(profile.cage()!=null)throw new Rejection(com.inigmasgames.hytalerpg.execution.summon.SelectiveCageProfile.BLOCKED_BOUNDARY,instance);
        if (!profile.family().name().equals(plan.finalFamily())
                && !plan.finalTags().contains(profile.family().name()))
            throw new Rejection("COMPILED_FAMILY_UNSUPPORTED", instance);
        SkillExecutionPort.Equipment equipment = port.equipment();
        try { validateEquipment(profile, equipment, instance); }
        catch (Rejection rejection) {
            emitProjectileRejection(request, root, instance, profile, rejection.code);
            throw rejection;
        }
        SkillExecutionPort.Validation family = port.familyPrerequisites(profile, plan);
        if (!family.accepted()) {
            emitProjectileRejection(request, root, instance, profile, family.code());
            throw new Rejection(family.code(), instance);
        }
        ResourceCost declared = new ResourceCost(ResourceType.valueOf(profile.resourceType()), profile.resourceCost());
        ResourceCost cost = kernel.resources().evaluate(declared, plan.kernelModifiers());
        if(profile.support()!=null&&profile.support().upkeepPerSecond()>0){
            var first=kernel.resources().evaluateUpkeep(new ResourceCost(ResourceType.MANA,profile.support().upkeepPerSecond()*.25*plan.supportModifiers().commitmentFactor()),plan.kernelModifiers());
            if(!kernel.resources().canAfford(request.actorId(),new ResourceCost(ResourceType.MANA,cost.amount()+first.amount()),port.resources()))
                throw new Rejection("AURA_INITIAL_UPKEEP_UNAFFORDABLE",retainedInstance);
        }
        if (!kernel.resources().canAfford(request.actorId(), cost, port.resources())) {
            emitProjectileRejection(request, root, instance, profile, "INSUFFICIENT_RESOURCE");
            throw new Rejection("INSUFFICIENT_RESOURCE", instance);
        }
        if (!kernel.cooldowns().canActivate(request.actorId(), profile.skillId())) {
            emitProjectileRejection(request, root, instance, profile, "COOLDOWN_ACTIVE");
            throw new Rejection("COOLDOWN_ACTIVE", instance);
        }
        return new Prepared(request, root, instance, profile, plan, cost, equipment);
    }

    private SkillExecutionResult commitAndDispatch(Prepared prepared, SkillExecutionPort port) {
        var releaseModifiers=prepared.plan.executionModifiers();
        String admission=releases.reserve(prepared.instanceId,prepared.request.actorId(),prepared.request.slot(),releaseModifiers);
        if(!admission.equals("PASS")) {
            lifecycle.terminate(prepared.request.actorId(),prepared.instanceId);
            return reject(prepared.request,prepared.rootCastId,prepared.instanceId,admission);
        }
        CommittedTarget target;
        try {
            boolean capture=releaseModifiers.scheduled()||prepared.profile.connection()!=null&&prepared.profile.connection().requiresTarget()
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
        boolean resourceCommitted = false;
        boolean cooldownStarted = false;
        SkillExecutionContext context;
        try {
            DerivedStats attributes = derive(prepared.request.actorId());
            BasePowerResolver.Resolution power = resolvePower(prepared.profile, prepared.equipment);
            var cooldown = kernel.cooldowns().calculate(prepared.request.actorId(),prepared.profile.cooldownSeconds(), 1.0,
                    attributes.cooldownRecovery(), prepared.plan.kernelModifiers());
            Map<String, Double> status = prepared.profile.authoredStatuses();
            // CombatSnapshotFactory alone installs compiled Increased modifiers (including Potency).
            var payloadLess=new java.util.ArrayList<>(prepared.plan.projectileModifiers().payloadLess());
            if(releaseModifiers.expandedRadius()&&!prepared.plan.radiusOnlyOnShrapnel())payloadLess.add(.10);
            ModifierBuckets modifiers = new ModifierBuckets(java.util.List.of(), java.util.List.of(),
                    releaseModifiers.delaySeconds()>0?java.util.List.of(1.35):java.util.List.of(),
                    payloadLess);
            if(prepared.profile.summon()!=null)modifiers=port.captureSummonModifiers(modifiers);
            var snapshot = kernel.snapshots().capture(prepared.rootCastId, prepared.instanceId,
                    prepared.request.actorId(), attributes, power, prepared.plan,
                    prepared.profile.damageCoefficient(),
                    modifiers, prepared.cost, cooldown.finalSeconds(), status);
            context = new SkillExecutionContext(prepared.request, prepared.rootCastId, prepared.instanceId,
                    prepared.profile, prepared.plan, snapshot, prepared.equipment,target,false);
            kernel.resources().commitCost(token, port.resources()); resourceCommitted = true;
            if(!channel(prepared.profile)) {
                kernel.cooldowns().startCooldown(prepared.request.actorId(), prepared.profile.skillId(),
                        prepared.profile.cooldownSeconds(), 1.0, attributes.cooldownRecovery(), prepared.plan.kernelModifiers());
                cooldownStarted = true;
            }
        } catch (RuntimeException error) {
            if (cooldownStarted) kernel.cooldowns().clear(prepared.request.actorId(), prepared.profile.skillId());
            try {
                if (resourceCommitted) kernel.resources().refundCommittedCost(token, port.resources());
                else kernel.resources().refundIfUncommitted(token);
            } catch (RuntimeException ignored) { }
            lifecycle.terminate(prepared.request.actorId(), prepared.instanceId);
            releases.finish(prepared.instanceId);
            return reject(prepared.request, prepared.rootCastId, prepared.instanceId,
                    "COMMIT_FAILED_" + error.getClass().getSimpleName());
        }
        emit(prepared.request, RpgTraceEventType.SKILL_COMMITTED, prepared.rootCastId, prepared.instanceId,
                Map.of("skillId", prepared.profile.skillId(), "resourceCost", prepared.cost.amount(),
                        "cooldownSeconds", context.snapshot().cooldownSeconds(),
                        "compiledPlanHash", prepared.plan.planHash()));
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
            result = executors.require(prepared.profile.family()).execute(context, port);
            kernel.resources().finish(token);
            if (result.committed()) {
                releases.primaryReleased(context,now());
                traceEchoSchedule(context);
            } else releases.finish(prepared.instanceId);
        }
        catch (RuntimeException error) {
            releases.finish(prepared.instanceId);
            // Spatial dispatch can already have applied a hit before a later presentation/status adapter fails.
            // A paid area must not yield free native damage through the synchronous rollback path.
            if (cooldownStarted && prepared.profile.area() == null && prepared.profile.connection()==null&&prepared.profile.support()==null&&prepared.profile.summon()==null&&prepared.profile.summonAction()==null&&prepared.profile.conversion()==null) kernel.cooldowns().clear(prepared.request.actorId(), prepared.profile.skillId());
            try { if (resourceCommitted && prepared.profile.area() == null && prepared.profile.connection()==null&&prepared.profile.support()==null&&prepared.profile.summon()==null&&prepared.profile.summonAction()==null&&prepared.profile.conversion()==null) kernel.resources().refundCommittedCost(token, port.resources());
                  else if (resourceCommitted) kernel.resources().finish(token); }
            catch (RuntimeException ignored) { }
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
                && context.profile().strike().repeats() > 1
                && context.profile().strike().repeatIntervalSeconds() > 0.0) {
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
        var calculation=kernel.cooldowns().startCooldown(context.request().actorId(),context.profile().skillId(),context.profile().cooldownSeconds(),
                1,context.snapshot().derivedStats().cooldownRecovery(),context.compiledPlan().kernelModifiers());
        emit(context.request(),RpgTraceEventType.COOLDOWN_STARTED,context.rootCastId(),context.skillInstanceId(),
                Map.of("skillId",context.profile().skillId(),"afterChannelEnd",true,"seconds",calculation.finalSeconds()));
    }
    public int pendingReleaseCount() { return releases.size(); }

    /** Called from the owner's actual world tick with a fresh native port, never a stale retained command buffer. */
    public void tickScheduled(UUID actor, SkillExecutionPort port) {
        // At most three authored releases per reservation; a late tick can drain fixed Barrage offsets.
        for(int pass=0;pass<3;pass++) for(var release:releases.due(actor,now())) {
            if(!releases.isCurrent(release)) continue;
            var context=release.context();
            try {
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
        String id = instance == null ? "pending-" + request.slot().externalId() : instance;
        emit(request, RpgTraceEventType.SKILL_VALIDATION_REJECTED, root, id, Map.of("failureCode", code));
        emit(request, RpgTraceEventType.SKILL_ACTIVATION_REJECTED, root, id, Map.of("failureCode", code));
        return SkillExecutionResult.rejected(code);
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
                            SkillExecutionPort.Equipment equipment) { }
    private static final class Rejection extends RuntimeException {
        private final String code; private final String skillInstanceId;
        private Rejection(String code, String skillInstanceId) { super(code); this.code = code; this.skillInstanceId = skillInstanceId; }
    }
}
