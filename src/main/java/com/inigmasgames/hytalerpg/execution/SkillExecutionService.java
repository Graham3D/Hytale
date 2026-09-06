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
    private final Map<UUID, Prepared> windups = new LinkedHashMap<>();
    private final Map<UUID, SkillExecutionContext> activeContexts = new LinkedHashMap<>();

    public SkillExecutionService(RpgLoadoutOperations loadouts, Stage04SkillProfiles profiles,
                                 RpgCombatKernel kernel, SkillExecutorRegistry executors,
                                 SkillInstanceLifecycle lifecycle, RpgSkillTracer tracer) {
        this.loadouts = loadouts; this.profiles = profiles; this.kernel = kernel;
        this.executors = executors; this.lifecycle = lifecycle; this.tracer = tracer;
    }

    public SkillExecutionResult request(SkillExecutionRequest request, SkillExecutionPort port) {
        String root = "input-" + request.chainId() + '-' + request.correlationId().substring(0, Math.min(8, request.correlationId().length()));
        String pendingInstance = "activation-" + UUID.randomUUID();
        emit(request, RpgTraceEventType.SKILL_ACTIVATION_REQUEST, root, pendingInstance,
                Map.of("action", request.action(), "skillSlot", request.slot().externalId()));
        Prepared prepared;
        try { prepared = validate(request, port, root, pendingInstance); }
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
        Prepared windup;
        synchronized (windups) { windup = windups.remove(actor); }
        SkillExecutionContext context;
        synchronized (activeContexts) { context = activeContexts.remove(actor); }
        Optional<SkillInstanceLifecycle.Active> cancelled = lifecycle.cancel(actor);
        if (cancelled.isEmpty()) return false;
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
        var view = loadouts.getPresentationView(request.actorId());
        var skill = view.state().skill(request.slot()).orElseThrow(() -> new Rejection("EMPTY_SLOT", retainedInstance));
        CompiledSkillPlan plan = view.plans().get(request.slot());
        if (plan == null || plan.degraded()) throw new Rejection("COMPILED_PLAN_INVALID", retainedInstance);
        if (!profiles.supports(skill.value())) throw new Rejection("FAMILY_NOT_IMPLEMENTED", retainedInstance);
        Stage04SkillProfile profile = profiles.require(skill.value());
        String instance = retainedInstance == null ? skill.value() + '-' + UUID.randomUUID() : retainedInstance;
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
        var token = kernel.resources().reserveCost(prepared.request.actorId(), prepared.cost, port.resources());
        boolean resourceCommitted = false;
        boolean cooldownStarted = false;
        SkillExecutionContext context;
        try {
            DerivedStats attributes = derive(prepared.request.actorId());
            BasePowerResolver.Resolution power = resolvePower(prepared.profile, prepared.equipment);
            var cooldown = kernel.cooldowns().calculate(prepared.profile.cooldownSeconds(), 1.0,
                    attributes.cooldownRecovery(), prepared.plan.kernelModifiers());
            Map<String, Double> status = prepared.profile.authoredStatuses();
            ModifierBuckets modifiers = new ModifierBuckets(
                    prepared.plan.kernelModifiers().scalablePayloadIncreased() > 0.0
                            ? java.util.List.of(prepared.plan.kernelModifiers().scalablePayloadIncreased())
                            : java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of());
            var snapshot = kernel.snapshots().capture(prepared.rootCastId, prepared.instanceId,
                    prepared.request.actorId(), attributes, power, prepared.plan,
                    prepared.profile.damageCoefficient(),
                    modifiers, prepared.cost, cooldown.finalSeconds(), status);
            context = new SkillExecutionContext(prepared.request, prepared.rootCastId, prepared.instanceId,
                    prepared.profile, prepared.plan, snapshot, prepared.equipment);
            kernel.resources().commitCost(token, port.resources()); resourceCommitted = true;
            kernel.cooldowns().startCooldown(prepared.request.actorId(), prepared.profile.skillId(),
                    prepared.profile.cooldownSeconds(), 1.0, attributes.cooldownRecovery(), prepared.plan.kernelModifiers());
            cooldownStarted = true;
        } catch (RuntimeException error) {
            if (cooldownStarted) kernel.cooldowns().clear(prepared.request.actorId(), prepared.profile.skillId());
            try {
                if (resourceCommitted) kernel.resources().refundCommittedCost(token, port.resources());
                else kernel.resources().refundIfUncommitted(token);
            } catch (RuntimeException ignored) { }
            lifecycle.terminate(prepared.request.actorId(), prepared.instanceId);
            return reject(prepared.request, prepared.rootCastId, prepared.instanceId,
                    "COMMIT_FAILED_" + error.getClass().getSimpleName());
        }
        emit(prepared.request, RpgTraceEventType.SKILL_COMMITTED, prepared.rootCastId, prepared.instanceId,
                Map.of("skillId", prepared.profile.skillId(), "resourceCost", prepared.cost.amount(),
                        "cooldownSeconds", context.snapshot().cooldownSeconds(),
                        "compiledPlanHash", prepared.plan.planHash()));
        emit(prepared.request, RpgTraceEventType.EXECUTOR_DISPATCH, prepared.rootCastId, prepared.instanceId,
                Map.of("family", prepared.profile.family().name()));
        SkillExecutionResult result;
        try {
            result = executors.require(prepared.profile.family()).execute(context, port);
            kernel.resources().finish(token);
        }
        catch (RuntimeException error) {
            if (cooldownStarted) kernel.cooldowns().clear(prepared.request.actorId(), prepared.profile.skillId());
            try { if (resourceCommitted) kernel.resources().refundCommittedCost(token, port.resources()); }
            catch (RuntimeException ignored) { }
            terminate(context, "EXECUTOR_ERROR_" + error.getClass().getSimpleName());
            return new SkillExecutionResult(SkillExecutionResult.Status.TERMINATED,
                    "EXECUTOR_ERROR", true, 0, 0.0);
        }
        if (prepared.profile.family() == Stage04SkillProfile.Family.STRIKE
                && prepared.profile.strike().repeats() > 1
                && prepared.profile.strike().repeatIntervalSeconds() > 0.0) {
            if (!lifecycle.transition(prepared.request.actorId(), prepared.instanceId,
                    SkillInstanceLifecycle.Phase.COMMITTED, SkillInstanceLifecycle.Phase.STRIKE_REPEAT))
                throw new IllegalStateException("Strike-repeat lifecycle transition failed");
            synchronized (activeContexts) { activeContexts.put(prepared.request.actorId(), context); }
        } else if (prepared.profile.family() == Stage04SkillProfile.Family.MOVEMENT) {
            if (!lifecycle.transition(prepared.request.actorId(), prepared.instanceId,
                    SkillInstanceLifecycle.Phase.COMMITTED, SkillInstanceLifecycle.Phase.MOVEMENT))
                throw new IllegalStateException("Movement lifecycle transition failed");
            synchronized (activeContexts) { activeContexts.put(prepared.request.actorId(), context); }
        } else if (prepared.profile.family() == Stage04SkillProfile.Family.REACTION) {
            if (!lifecycle.transition(prepared.request.actorId(), prepared.instanceId,
                    SkillInstanceLifecycle.Phase.COMMITTED, SkillInstanceLifecycle.Phase.REACTION))
                throw new IllegalStateException("Reaction lifecycle transition failed");
            synchronized (activeContexts) { activeContexts.put(prepared.request.actorId(), context); }
        } else if (prepared.profile.family() == Stage04SkillProfile.Family.PROJECTILE) {
            if (!lifecycle.transition(prepared.request.actorId(), prepared.instanceId,
                    SkillInstanceLifecycle.Phase.COMMITTED, SkillInstanceLifecycle.Phase.PROJECTILE))
                throw new IllegalStateException("Projectile lifecycle transition failed");
            synchronized (activeContexts) { activeContexts.put(prepared.request.actorId(), context); }
        } else terminate(context, "STRIKE_COMPLETE");
        return result;
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
