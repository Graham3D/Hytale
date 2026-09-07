package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.collision.CollisionModule;
import com.hypixel.hytale.server.core.modules.collision.CollisionResult;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.modules.projectile.system.StandardPhysicsTickSystem;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.combat.damage.DamageCalculationService;
import com.inigmasgames.hytalerpg.combat.diagnostics.CombatTrace;
import com.inigmasgames.hytalerpg.combat.hytale.EntityStatResourcePort;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata;
import com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort;
import com.inigmasgames.hytalerpg.combat.status.ControlProfile;
import com.inigmasgames.hytalerpg.combat.status.RpgStatusType;
import com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType;
import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.SkillExecutionPort;
import com.inigmasgames.hytalerpg.execution.SkillExecutionRequest;
import com.inigmasgames.hytalerpg.execution.SkillExecutionResult;
import com.inigmasgames.hytalerpg.execution.SkillExecutionService;
import com.inigmasgames.hytalerpg.execution.Stage04SkillProfile;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.area.AreaGeometry;
import com.inigmasgames.hytalerpg.execution.area.AreaRuntime;
import com.inigmasgames.hytalerpg.execution.area.AreaWorldPort;
import com.inigmasgames.hytalerpg.execution.movement.MovementPlanner;
import com.inigmasgames.hytalerpg.execution.projectile.ProjectileFlight;
import com.inigmasgames.hytalerpg.execution.projectile.ProjectileExecutionPlan;
import com.inigmasgames.hytalerpg.execution.projectile.ProjectileInstance;
import com.inigmasgames.hytalerpg.execution.projectile.ProjectileLifecycleRegistry;
import com.inigmasgames.hytalerpg.execution.projectile.RpgProjectileService;
import com.inigmasgames.hytalerpg.execution.reaction.ReactionWindowService;
import com.inigmasgames.hytalerpg.execution.strike.SkillHitLedger;
import com.inigmasgames.hytalerpg.execution.strike.StrikeGeometryService;
import com.inigmasgames.hytalerpg.execution.strike.StrikeRepeatSchedule;
import com.inigmasgames.hytalerpg.input.HytaleAbilitySkillInputAdapter;
import com.inigmasgames.hytalerpg.vfx.LinkTreeVfxService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** World-thread bridge for shared skill-family execution and bounded native authority calls. */
public final class HytaleSkillExecutionSystem extends EntityTickingSystem<EntityStore> {
    private static final String NATIVE_STAGGER_EFFECT = "Stun";
    private static final String NATIVE_BURN_VISUAL_EFFECT = "RPG_Burn_Visual";
    private final HytaleAbilitySkillInputAdapter inputs;
    private final SkillExecutionService executions;
    private final RpgCombatKernel kernel;
    private final CombatTrace trace;
    private final ReactionWindowService reactions;
    private final HytaleEquipmentAdapter equipment = new HytaleEquipmentAdapter();
    private final HytaleAmmoAdapter ammunition = new HytaleAmmoAdapter();
    private final StrikeGeometryService geometry = new StrikeGeometryService();
    private final SkillHitLedger hits = new SkillHitLedger();
    private final MovementPlanner movementPlanner = new MovementPlanner();
    private final LinkTreeVfxService vfx;
    private final HytaleBossBarTracker bosses;
    private final Map<UUID, Long> windupEnds = new HashMap<>();
    private final Map<UUID, Motion> motions = new HashMap<>();
    private final Map<UUID, Counter> counters = new HashMap<>();
    private final Map<UUID, RepeatingStrike> repeatingStrikes = new HashMap<>();
    private final Map<String, ProjectileCarrier> projectiles = new HashMap<>();
    private final RpgProjectileService projectileService =
            new RpgProjectileService(new ProjectileLifecycleRegistry());
    private final PeriodicStatusRuntime<SkillExecutionContext, PeriodicTarget> periodicStatuses = new PeriodicStatusRuntime<>();
    private final AreaRuntime areas = new AreaRuntime();
    private final com.inigmasgames.hytalerpg.combat.status.ControlProfileRegistry areaControls =
            com.inigmasgames.hytalerpg.combat.status.ControlProfileRegistry.loadCanonical();

    public HytaleSkillExecutionSystem(HytaleAbilitySkillInputAdapter inputs, SkillExecutionService executions,
                                      RpgCombatKernel kernel, CombatTrace trace,
                                      ReactionWindowService reactions, LinkTreeVfxService vfx,
                                      HytaleBossBarTracker bosses) {
        this.inputs = inputs; this.executions = executions; this.kernel = kernel;
        this.trace = trace; this.reactions = reactions; this.vfx = vfx; this.bosses = bosses;
    }

    @Override public Query<EntityStore> getQuery() {
        return Query.and(PlayerRef.getComponentType(), Player.getComponentType(), EntityStatMap.getComponentType(),
                TransformComponent.getComponentType(), BoundingBox.getComponentType());
    }

    @Override public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.AFTER, StandardPhysicsTickSystem.class));
    }

    @Override public void tick(float deltaSeconds, int index, ArchetypeChunk<EntityStore> chunk,
                               Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
        Player player = chunk.getComponent(index, Player.getComponentType());
        EntityStatMap stats = chunk.getComponent(index, EntityStatMap.getComponentType());
        UUID actor = playerRef.getUuid();
        Port port = new Port(store, ref, playerRef, player, stats, null, buffer);
        if (!port.actorAliveAndUsable()) {
            cancel(actor, "ACTOR_UNUSABLE", buffer); return;
        }
        Counter counter = counters.remove(actor);
        if (counter != null) {
            emit(counter.context, RpgTraceEventType.REACTION_TRIGGERED,
                    Map.of("signal", "HYTALE_DAMAGE_BLOCKED", "eventId", counter.eventId));
            new Port(store, ref, playerRef, player, stats, counter.attacker, buffer).executeStrike(counter.context);
            hits.clear(counter.context.skillInstanceId());
            executions.terminate(counter.context, "REACTION_COUNTER_COMPLETE");
        }
        advanceRepeatingStrike(store, ref, playerRef, player, stats, buffer);
        advanceProjectiles(actor, deltaSeconds, store, buffer);
        periodicStatuses.tick(actor, System.nanoTime() / 1e9, periodicPort());
        areas.tick(actor, System.nanoTime() / 1_000_000_000.0, port.areaWorld());
        Motion motion = motions.get(actor);
        if (motion != null) advanceMotion(deltaSeconds, store, ref, player, motion, buffer);
        Long windupEnd = windupEnds.get(actor);
        if (windupEnd != null && System.nanoTime() >= windupEnd) {
            windupEnds.remove(actor);
            executions.completeWindup(actor, port);
        }
        reactions.expire(actor).ifPresent(context -> {
            emit(context, RpgTraceEventType.REACTION_EXPIRED, Map.of("windowSeconds", context.profile().reaction().windowSeconds()));
            executions.terminate(context, "REACTION_EXPIRED");
        });
        inputs.drainFor(actor, request -> {
            SkillExecutionResult result = executions.request(new SkillExecutionRequest(request.player(), request.slot(),
                    request.action(), request.chainId(), request.correlationId(), request.desiredMovement()),
                    new Port(store, ref, playerRef, player, stats, null, buffer));
            if (result.status() == SkillExecutionResult.Status.PENDING)
                executions.activeWindupSeconds(actor).ifPresent(seconds ->
                        windupEnds.put(actor, System.nanoTime() + Math.round(seconds * 1_000_000_000.0)));
        }, 8);
    }

    /** Queues a counter for the next world tick; never nests Damage execution inside a Damage callback. */
    public void onNativeBlocked(Ref<EntityStore> defender, Ref<EntityStore> attacker,
                                Store<EntityStore> store, String eventId) {
        if (defender == null || attacker == null || !defender.isValid() || !attacker.isValid()) return;
        PlayerRef player = store.getComponent(defender, PlayerRef.getComponentType());
        if (player == null) return;
        reactions.trigger(player.getUuid(), "HYTALE_DAMAGE_BLOCKED", eventId)
                .ifPresent(context -> counters.putIfAbsent(player.getUuid(), new Counter(context, attacker, eventId)));
    }

    /** Native post-filter damage is an authoritative interruption for an active wind-up. */
    public void onIncomingDamage(UUID actor) {
        if (windupEnds.remove(actor) != null) executions.cancel(actor, "NATIVE_DAMAGE_INTERRUPT");
    }

    public void cancel(UUID actor, String reason) {
        cancel(actor, reason, null);
    }

    private void cancel(UUID actor, String reason, CommandBuffer<EntityStore> buffer) {
        windupEnds.remove(actor); motions.remove(actor); counters.remove(actor);
        RepeatingStrike repeating = repeatingStrikes.remove(actor);
        if (repeating != null) hits.clear(repeating.context.skillInstanceId());
        removeOwnedProjectiles(actor, buffer);
        removeOwnedBurns(actor);
        for (SkillExecutionContext area : areas.cancel(actor))
            emit(area, RpgTraceEventType.AREA_TERMINATED, Map.of("reason", reason));
        reactions.cancel(actor);
        executions.cancel(actor, reason);
    }

    private void advanceMotion(float deltaSeconds, Store<EntityStore> store, Ref<EntityStore> ref,
                               Player player, Motion motion, CommandBuffer<EntityStore> buffer) {
        if (!ref.isValid() || store.getComponent(ref, DeathComponent.getComponentType()) != null) {
            motions.remove(motion.context.request().actorId());
            emit(motion.context, RpgTraceEventType.MOVEMENT_CANCELLED, Map.of("reason", "DEATH_OR_REMOVAL"));
            executions.terminate(motion.context, "MOVEMENT_CANCELLED"); return;
        }
        motion.elapsed += Math.max(0.0, deltaSeconds);
        double progress = Math.min(1.0, motion.elapsed / Math.max(0.001, motion.plan.durationSeconds()));
        Vec3 requested = movementPlanner.sample(motion.plan, progress);
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        Vec3 current = vec(transform.getPosition());
        Vec3 segment = requested.subtract(current);
        double fraction = collisionFraction(store, ref, current, segment);
        Vec3 applied = current.add(segment.multiply(fraction));
        double fall = player.getCurrentFallDistance();
        player.moveTo(ref, applied.x(), applied.y(), applied.z(), store);
        player.setCurrentFallDistance(Math.max(fall, player.getCurrentFallDistance()));
        if (fraction < 1.0 - 1.0e-6) {
            emit(motion.context, RpgTraceEventType.MOVEMENT_CLAMPED,
                    Map.of("requestedSegment", segment.horizontalLength(), "appliedFraction", fraction,
                            "reason", "NATIVE_BLOCK_COLLISION"));
            finishMotion(motion, applied, true, buffer); return;
        }
        if (progress >= 1.0) finishMotion(motion, applied, motion.plan.clamped(), buffer);
    }

    private void finishMotion(Motion motion, Vec3 finalPosition, boolean clamped, CommandBuffer<EntityStore> buffer) {
        UUID actor = motion.context.request().actorId(); motions.remove(actor);
        emit(motion.context, RpgTraceEventType.MOVEMENT_END,
                Map.of("distance", finalPosition.subtract(motion.plan.origin()).horizontalLength(), "clamped", clamped,
                        "durationSeconds", motion.elapsed));
        if (motion.context.profile().hasFamily(Stage04SkillProfile.Family.STRIKE)) {
            Ref<EntityStore> ref = motion.actor;
            if (ref.isValid()) {
                Store<EntityStore> store = ref.getStore();
                PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                Player player = store.getComponent(ref, Player.getComponentType());
                EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
                if (playerRef != null && player != null && stats != null)
                    new Port(store, ref, playerRef, player, stats, null, buffer).executeStrike(motion.context);
            }
        }
        hits.clear(motion.context.skillInstanceId());
        executions.terminate(motion.context, "MOVEMENT_COMPLETE");
    }

    private final class Port implements SkillExecutionPort {
        private final Store<EntityStore> store; private final Ref<EntityStore> actor;
        private final PlayerRef playerRef; private final Player player; private final EntityStatMap stats;
        private final Ref<EntityStore> forcedTarget;
        private final CommandBuffer<EntityStore> buffer;
        private Ref<EntityStore> pounceTarget;
        private Vec3 areaPlacement;
        private Vec3 areaDirection;
        Port(Store<EntityStore> store, Ref<EntityStore> actor, PlayerRef playerRef, Player player,
             EntityStatMap stats, Ref<EntityStore> forcedTarget, CommandBuffer<EntityStore> buffer) {
            this.store = store; this.actor = actor; this.playerRef = playerRef; this.player = player;
            this.stats = stats; this.forcedTarget = forcedTarget; this.buffer = buffer;
        }
        @Override public boolean actorAliveAndUsable() {
            var health = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
            return actor.isValid() && health != null && health.get() > health.getMin()
                    && store.getComponent(actor, DeathComponent.getComponentType()) == null;
        }
        @Override public Equipment equipment() { return equipment.read(actor, store); }
        @Override public NativeResourcePort resources() { return new EntityStatResourcePort(stats); }
        @Override public Validation familyPrerequisites(Stage04SkillProfile profile,
                                                        com.inigmasgames.hytalerpg.domain.CompiledSkillPlan plan) {
            if (motions.containsKey(playerRef.getUuid()) || windupEnds.containsKey(playerRef.getUuid())
                    || reactions.active(playerRef.getUuid()).isPresent()) return Validation.reject("INCOMPATIBLE_ACTIVE_STATE");
            if (profile.area() != null) {
                String admitted = areas.admission(playerRef.getUuid(), profile.skillId(), profile.area().trap());
                if (!admitted.equals("PASS")) return Validation.reject(admitted);
                Vec3 feet = vec(store.getComponent(actor, TransformComponent.getComponentType()).getPosition());
                areaDirection = aim(store, actor);
                areaPlacement = profile.family() == Stage04SkillProfile.Family.CONE ? feet : profile.area().placementRange() > 0
                        ? HytaleAreaQueries.ground(store, feet.add(new Vec3(0, 1.35, 0)), areaDirection,
                            profile.area().placementRange()).orElse(null)
                        : HytaleAreaQueries.ground(store, feet.add(new Vec3(0, .15, 0)), new Vec3(0, -1, 0), .65).orElse(null);
                if (areaPlacement == null) return Validation.reject("NO_LEGAL_GROUND_SURFACE");
                if (profile.area().overheadHeight() > 0 && !areaWorld().overheadClear(
                        profile.area().footprint(areaPlacement, areaDirection, 1), profile.area().overheadHeight()))
                    return Validation.reject("OVERHEAD_ROOF_BLOCKED");
                if (profile.family() == Stage04SkillProfile.Family.WALL)
                    areaDirection = new Vec3(areaDirection.z(), 0, -areaDirection.x()).horizontalNormalized();
                var query = areaWorld().query(profile.area().footprint(areaPlacement, areaDirection, 1), profile.area().candidateBudget());
                if (query.overflow()) return Validation.reject("AREA_CANDIDATE_BUDGET");
                if (!HytaleAreaStatuses.available()) return Validation.reject("AREA_STATUS_ASSETS_UNAVAILABLE");
                return Validation.pass();
            }
            if (profile.family() == Stage04SkillProfile.Family.PROJECTILE) {
                if (profile.projectile() == null) return Validation.reject("PROJECTILE_PROFILE_MISSING");
                boolean configMissing = java.util.stream.Stream.concat(
                                java.util.stream.Stream.of(profile.projectile().configId()),
                                profile.projectile().configIdsByWeaponKind().values().stream())
                        .distinct().anyMatch(id -> ProjectileConfig.getAssetMap().getAsset(id) == null);
                if (configMissing)
                    return Validation.reject("PROJECTILE_CONFIG_MISSING");
                long owned = projectiles.values().stream().filter(value -> value.actorId.equals(playerRef.getUuid())).count();
                if (owned >= plan.safetyBudgets().maxLiveProjectiles())
                    return Validation.reject("PROJECTILE_LIVE_BUDGET_EXCEEDED");
                if (!ammunition.available(actor, store, profile.projectile()))
                    return Validation.reject("AMMUNITION_UNAVAILABLE");
                return Validation.pass();
            }
            if (profile.family() == Stage04SkillProfile.Family.REACTION) return Validation.pass();
            if (profile.family() == Stage04SkillProfile.Family.MOVEMENT
                    && profile.movement().kind() == Stage04SkillProfile.MovementKind.DASH) return Validation.pass();
            if (profile.family() == Stage04SkillProfile.Family.MOVEMENT) {
                pounceTarget = nearestTarget(profile.movement().maxDistance(), 120.0);
                return pounceTarget == null ? Validation.reject("NO_VALID_TARGET") : Validation.pass();
            }
            return select(profile.strike(), false).accepted().isEmpty()
                    ? Validation.reject("NO_VALID_TARGET") : Validation.pass();
        }
        @Override public SkillExecutionResult executeStrike(SkillExecutionContext context) {
            int applied = executeStrikeHit(context, 0);
            var strike = context.profile().strike();
            if (strike.repeats() > 1 && strike.repeatIntervalSeconds() > 0.0)
                repeatingStrikes.put(playerRef.getUuid(), new RepeatingStrike(context,
                        new StrikeRepeatSchedule(strike.repeats(), strike.repeatIntervalSeconds(), System.nanoTime())));
            else {
                for (int hitIndex = 1; hitIndex < strike.repeats(); hitIndex++)
                    applied += executeStrikeHit(context, hitIndex);
                hits.clear(context.skillInstanceId());
            }
            vfx.present(store.getExternalData().getWorld(), player, context.compiledPlan().vfxRecipeId());
            return SkillExecutionResult.committed("STRIKE_COMPLETE", applied, 0.0);
        }

        @Override public SkillExecutionResult executeArea(SkillExecutionContext context) {
            if (areaPlacement == null || areaDirection == null) throw new IllegalStateException("AREA_PLACEMENT_NOT_VALIDATED");
            areas.start(context, areaPlacement, areaDirection, System.nanoTime() / 1_000_000_000.0, 1, areaWorld());
            return SkillExecutionResult.committed("AREA_DISPATCHED", 0, 0);
        }

        private AreaWorldPort areaWorld() {
            return new AreaWorldPort() {
                private final Map<String, Ref<EntityStore>> refs = new HashMap<>();
                @Override public Query query(AreaGeometry shape, int budget) {
                    refs.clear();
                    var found = HytaleAreaQueries.query(store, actor, shape, budget);
                    List<Target> targets = new ArrayList<>();
                    for (var value : found.candidates()) {
                        var candidate = candidate(value.ref());
                        if (candidate == null || candidate.protectedTarget()) continue;
                        refs.put(candidate.stableId(), candidate.handle());
                        targets.add(new Target(candidate.stableId(), value.bounds(), candidate.boss()));
                    }
                    return new Query(targets, found.overflow());
                }
                @Override public boolean lineOfSight(Vec3 origin, Target target) {
                    return HytaleAreaQueries.clear(store, origin.add(new Vec3(0, .1, 0)), target.bounds().centre());
                }
                @Override public java.util.Optional<AreaGeometry> prepareImpact(Vec3 parent, AreaGeometry footprint) {
                    return HytaleAreaQueries.ground(store, footprint.origin().add(new Vec3(0, 3, 0)), new Vec3(0, -1, 0), 6)
                            .filter(point -> HytaleAreaQueries.clear(store, parent.add(new Vec3(0, .1, 0)), point.add(new Vec3(0, .1, 0))))
                            .map(point -> footprint.at(point, footprint.radius()));
                }
                @Override public boolean overheadClear(AreaGeometry footprint, double height) {
                    return HytaleAreaQueries.clear(store, footprint.origin().add(new Vec3(0, .1, 0)),
                            footprint.origin().add(new Vec3(0, height, 0)));
                }
                @Override public void descendingVisual(SkillExecutionContext context, Vec3 position, double seconds) {
                    vfx.presentDescending(store.getExternalData().getWorld(), position, context.profile().area().element(), seconds);
                }
                @Override public boolean apply(SkillExecutionContext context, Target target, Payload payload) {
                    var reference = refs.get(target.id());
                    if (reference == null || !HytaleAreaQueries.hostile(store, reference, actor)) return false;
                    var candidate = candidate(reference);
                    if (candidate == null || candidate.protectedTarget()) return false;
                    NPCEntity npc = store.getComponent(reference, NPCEntity.getComponentType());
                    ControlProfile control = areaControls.resolve(npc.getRoleName(), candidate.protectedTarget(), candidate.boss());
                    if (control.protectedEntity()) return false;
                    if (!payload.status().isBlank() && store.getComponent(reference, EffectControllerComponent.getComponentType()) == null)
                        return false;
                    String causeId = switch (payload.element()) {
                        case "COLD" -> "Ice"; case "FIRE" -> "Fire"; case "EARTH" -> "Earth";
                        case "POISON" -> "Poison"; case "PHYSICAL" -> "Physical"; case "NATURE" -> "RPG_Nature";
                        case "VOID" -> "RPG_Void"; default -> throw new IllegalStateException("UNMAPPED_AREA_DAMAGE_CHANNEL");
                    };
                    DamageCause cause = DamageCause.getAssetMap().getAsset(causeId);
                    if (cause == null) throw new IllegalStateException("MISSING_NATIVE_DAMAGE_CAUSE_" + causeId);
                    if (payload.pullBeforeDamage() && payload.pull() > 0) applyAreaPull(context, reference, npc, control, payload);
                    DamageOutcome outcome = damage(context, candidate, payload.impactIndex(), payload.coefficient(),
                            payload.periodic() ? 0 : context.snapshot().criticalChance(), cause, payload.periodic());
                    if (outcome.cancelled()) return false;
                    if (!payload.pullBeforeDamage() && payload.pull() > 0) applyAreaPull(context, reference, npc, control, payload);
                    if (payload.status().equals("BURN") || payload.status().equals("POISON")) {
                        applyPeriodicStatus(context, candidate, PeriodicStatusRuntime.Kind.valueOf(payload.status()),
                                payload.statusSeconds(), payload.status().equals("BURN") ? .10 : .06);
                    } else {
                        HytaleAreaStatuses.apply(kernel, context, candidate, payload, control, store, actor,
                                (event, details) -> emit(context, event, details));
                        if (!payload.status().isBlank()) buffer.ensureAndGetComponent(reference, AreaStatusProjection.getComponentType());
                    }
                    if (payload.displacement() > 0 && control.displacementMultiplier() > 0 && reference.isValid()
                            && npc.getRole() != null && npc.getRole().getKnockbackScale() > 0) {
                        var transform = store.getComponent(reference, TransformComponent.getComponentType());
                        Vec3 origin = vec(transform.getPosition());
                        Vec3 away = origin.subtract(payload.origin())
                                .horizontalNormalized().multiply(payload.displacement() * control.displacementMultiplier());
                        double fraction = collisionFraction(store, reference, origin, away);
                        transform.setPosition(vector(origin.add(away.multiply(fraction))));
                    }
                    return true;
                }
                @Override public void present(SkillExecutionContext context, AreaGeometry shape, String phase, double seconds) {
                    vfx.presentArea(store.getExternalData().getWorld(), shape, phase, seconds);
                    emit(context, RpgTraceEventType.AREA_PRESENTATION, Map.of("phase", phase, "radius", shape.radius(),
                            "height", shape.height(), "duration", seconds, "template", "NATIVE_GEOMETRY"));
                }
                @Override public void trace(SkillExecutionContext context, String event, Map<String, ?> details) {
                    emit(context, RpgTraceEventType.valueOf(event), details);
                }
            };
        }
        private void applyAreaPull(SkillExecutionContext context, Ref<EntityStore> reference, NPCEntity npc,
                ControlProfile control, AreaWorldPort.Payload payload) {
            if (!reference.isValid()) return;
            var liveCandidate=candidate(reference);
            if(liveCandidate == null || liveCandidate.protectedTarget()) return;
            var transform=store.getComponent(reference, TransformComponent.getComponentType());
            var bounds=store.getComponent(reference, BoundingBox.getComponentType());
            if(transform == null || bounds == null) return;
            Vec3 start=vec(transform.getPosition());
            double scale=npc.getRole() != null && npc.getRole().getKnockbackScale() > 0 ? control.displacementMultiplier() : 0;
            var plan=com.inigmasgames.hytalerpg.execution.area.AreaPullPlanner.plan(start,payload.origin(),payload.pull(),payload.pullCoreRadius(),
                    scale,npc.getRole()!=null && npc.getRole().isOnGround(),
                    (point, segment)->collisionFraction(store,reference,point,segment),
                    point->HytaleAreaQueries.ground(store,point.add(new Vec3(0,bounds.getBoundingBox().min.y()+.15,0)),
                            new Vec3(0,-1,0),.35).isPresent());
            if(plan.distance() > 0) transform.setPosition(vector(plan.destination()));
            emit(context,RpgTraceEventType.AREA_DISPLACEMENT,Map.of("targetId",liveCandidate.stableId(),
                    "requested",payload.pull(),"applied",plan.distance(),"reason",plan.reason(),
                    "beforeDamage",payload.pullBeforeDamage(),"nativeBehaviorVerified",false));
        }
        private int executeStrikeHit(SkillExecutionContext context, int hitIndex) {
            StrikeGeometryService.QueryResult<Ref<EntityStore>> selected = select(context, context.profile().strike());
            int applied = 0;
            for (var target : selected.accepted()) {
                if (!hits.accept(context.skillInstanceId(), hitIndex, target.stableId())) continue;
                DamageOutcome outcome = damage(context, target, hitIndex,
                        context.profile().strike().coefficient(), context.snapshot().criticalChance(), DamageCause.PHYSICAL);
                emit(context, RpgTraceEventType.STRIKE_HIT,
                        Map.of("targetId", target.stableId(), "hitIndex", hitIndex,
                                "preMitigationDamage", outcome.preMitigationDamage(),
                                "actualHealthLoss", outcome.actualHealthLoss()));
                if (!context.profile().strike().statusId().isBlank()) applyStatus(context, target);
                applied++;
            }
            return applied;
        }
        @Override public SkillExecutionResult executeMovement(SkillExecutionContext context) {
            TransformComponent transform = store.getComponent(actor, TransformComponent.getComponentType());
            Vec3 origin = vec(transform.getPosition());
            Vec3 direction; double distance = context.profile().movement().maxDistance();
            if (context.profile().movement().kind() == Stage04SkillProfile.MovementKind.LEAP) {
                Ref<EntityStore> target = pounceTarget != null ? pounceTarget : nearestTarget(distance, 120.0);
                if (target == null) throw new IllegalStateException("Pounce target vanished before dispatch");
                TransformComponent targetTransform = store.getComponent(target, TransformComponent.getComponentType());
                direction = vec(targetTransform.getPosition()).subtract(origin); distance = Math.min(distance, direction.horizontalLength());
            } else {
                direction = context.request().desiredMovement();
                if (direction.horizontalLengthSquared() < 1.0e-6) direction = facing(store, actor);
            }
            MovementPlanner.Plan plan = movementPlanner.plan(origin, direction, distance,
                    context.profile().movement(), (start, displacement) -> collisionFraction(store, actor, start, displacement));
            motions.put(playerRef.getUuid(), new Motion(context, actor, plan));
            emit(context, RpgTraceEventType.MOVEMENT_BEGIN,
                    Map.of("kind", plan.kind().name(), "requestedDistance", distance,
                            "plannedDistance", plan.appliedDistance(), "durationSeconds", plan.durationSeconds()));
            if (plan.clamped()) emit(context, RpgTraceEventType.MOVEMENT_CLAMPED,
                    Map.of("requestedDistance", distance, "plannedDistance", plan.appliedDistance(),
                            "reason", "NATIVE_PATH_COLLISION"));
            vfx.present(store.getExternalData().getWorld(), player, context.compiledPlan().vfxRecipeId());
            return SkillExecutionResult.committed("MOVEMENT_STARTED", 0, plan.appliedDistance());
        }
        @Override public SkillExecutionResult executeReaction(SkillExecutionContext context) {
            if (!reactions.arm(playerRef.getUuid(), context, context.profile().reaction().windowSeconds()))
                throw new IllegalStateException("Reaction already armed");
            emit(context, RpgTraceEventType.REACTION_ARMED,
                    Map.of("windowSeconds", context.profile().reaction().windowSeconds(),
                            "signals", context.profile().reaction().qualifyingSignals()));
            return SkillExecutionResult.committed("REACTION_ARMED", 0, 0.0);
        }

        @Override public SkillExecutionResult executeProjectile(SkillExecutionContext context) {
            Stage04SkillProfile.Projectile authored = context.profile().projectile();
            HytaleAmmoAdapter.Token ammo = HytaleAmmoAdapter.Token.NONE;
            Ref<EntityStore> spawned = null;
            ProjectileInstance instance = null;
            try {
                String weaponKind = context.equipment().mainHand().weaponKind();
                String configId = authored.configIdFor(weaponKind);
                double speed = authored.speedFor(weaponKind);
                Vec3 direction = aim(store, actor);
                Vec3 actorPosition = vec(store.getComponent(actor, TransformComponent.getComponentType()).getPosition());
                Vec3 origin = new Vec3(actorPosition.x(), actorPosition.y() + 1.35, actorPosition.z())
                        .add(direction.multiply(0.65));
                ProjectileExecutionPlan plan = projectileService.buildPlan(context, playerRef.getUuid(),
                        origin, direction, configId, speed, System.nanoTime());
                emit(context, RpgTraceEventType.PROJECTILE_SPAWN_REQUEST, Map.of(
                        "projectileInstanceId", plan.projectileInstanceId(), "skillId", plan.skillId(),
                        "generation", plan.generation(), "caster", plan.ownerId().toString(),
                        "compiledPlanHash", plan.compiledPlanHash(), "configId", configId,
                        "originX", origin.x(), "originY", origin.y(), "originZ", origin.z()));
                emit(context, RpgTraceEventType.AMMO_CHECK,
                        Map.of("required", authored.requiresAmmo(), "itemId", authored.ammoItemId(),
                                "quantity", authored.ammoQuantity(), "available", true));
                ammo = ammunition.consume(actor, store, authored);
                if (authored.requiresAmmo()) emit(context, RpgTraceEventType.AMMO_COMMITTED,
                        Map.of("itemId", ammo.itemId(), "quantity", ammo.quantity(),
                                "fullyCharged", authored.fullyCharged()));
                ProjectileConfig config = ProjectileConfig.getAssetMap().getAsset(configId);
                if (config == null) throw new IllegalStateException("Projectile config disappeared before dispatch");
                spawned = ProjectileModule.get().spawnProjectile(actor, buffer, config,
                        vector(origin), vector(direction));
                var physics = buffer.getComponent(spawned,
                        ProjectileModule.get().getStandardPhysicsProviderComponentType());
                if (physics == null) throw new IllegalStateException("Native projectile has no StandardPhysicsProvider");
                instance = projectileService.onProjectileSpawn(plan);
                ProjectileCarrier carrier = new ProjectileCarrier(context, actor, playerRef.getUuid(), spawned, instance);
                projectiles.put(plan.projectileInstanceId(), carrier);
                physics.setImpactConsumer((projectileRef, position, blockPosition, hitEntity, interaction, commandBuffer) ->
                        onProjectileImpact(carrier, projectileRef, position, blockPosition, hitEntity, commandBuffer));
                emit(context, RpgTraceEventType.PROJECTILE_SPAWNED,
                        Map.ofEntries(Map.entry("projectileInstanceId", plan.projectileInstanceId()),
                                Map.entry("skillId", plan.skillId()), Map.entry("generation", plan.generation()),
                                Map.entry("caster", plan.ownerId().toString()),
                                Map.entry("compiledPlanHash", plan.compiledPlanHash()),
                                Map.entry("configId", configId), Map.entry("speed", speed),
                                Map.entry("maxDistance", authored.maxDistance()),
                                Map.entry("maximumLifetimeSeconds", authored.maximumLifetimeSeconds(weaponKind)),
                                Map.entry("radius", authored.radius()), Map.entry("gravity", authored.gravity()),
                                Map.entry("nativeProjectileRef", spawned.toString())));
                vfx.present(store.getExternalData().getWorld(), player, context.compiledPlan().vfxRecipeId());
                return SkillExecutionResult.committed("PROJECTILE_STARTED", 0, 0.0);
            } catch (RuntimeException error) {
                if (instance != null) {
                    projectiles.remove(instance.plan().projectileInstanceId());
                    projectileService.onForwardTermination(instance, "SPAWN_REJECTED", instance.plan().origin());
                }
                if (spawned != null && spawned.isValid()) buffer.tryRemoveEntity(spawned, RemoveReason.REMOVE);
                if (ammo.quantity() > 0) ammunition.refund(actor, store, ammo);
                if (authored.requiresAmmo()) emit(context, RpgTraceEventType.AMMO_REJECTED,
                        Map.of("reason", "PROJECTILE_DISPATCH_ROLLBACK", "error", error.getClass().getSimpleName()));
                Map<String, Object> rejection = new HashMap<>();
                rejection.put("reason", "DISPATCH_ROLLBACK");
                rejection.put("error", error.getClass().getSimpleName());
                rejection.put("skillId", context.profile().skillId());
                rejection.put("projectileInstanceId", context.skillInstanceId() + "-projectile-0");
                emit(context, RpgTraceEventType.PROJECTILE_SPAWN_REJECTED, rejection);
                throw error;
            }
        }

        private Ref<EntityStore> nearestTarget(double range, double angle) {
            Stage04SkillProfile.Strike selector = new Stage04SkillProfile.Strike(
                    Stage04SkillProfile.Geometry.ASSIST_CONE, range, angle, 0, 1, 0, 1, 0, "", 0);
            var result = select(selector, false); return result.accepted().isEmpty() ? null : result.accepted().getFirst().handle();
        }
        private StrikeGeometryService.QueryResult<Ref<EntityStore>> select(Stage04SkillProfile.Strike strike, boolean ignored) {
            return select(null, strike);
        }
        private StrikeGeometryService.QueryResult<Ref<EntityStore>> select(SkillExecutionContext context,
                                                                           Stage04SkillProfile.Strike strike) {
            Vec3 origin = vec(store.getComponent(actor, TransformComponent.getComponentType()).getPosition());
            List<StrikeGeometryService.Candidate<Ref<EntityStore>>> candidates = candidates(origin, strike.range());
            if (forcedTarget != null && forcedTarget.isValid())
                candidates = candidates.stream().filter(value -> value.handle().equals(forcedTarget)).toList();
            var result = geometry.query(origin, facing(store, actor), strike, candidates);
            if (context != null) {
                emit(context, RpgTraceEventType.STRIKE_QUERY, Map.of("geometry", strike.geometry().name(),
                        "range", strike.range(), "angleDegrees", strike.angleDegrees(),
                        "candidateCount", candidates.size(), "acceptedCount", result.accepted().size()));
                for (var decision : result.decisions()) emit(context, decision.accepted()
                                ? RpgTraceEventType.STRIKE_TARGET_ACCEPTED : RpgTraceEventType.STRIKE_TARGET_REJECTED,
                        Map.of("targetId", decision.candidate().stableId(), "reason", decision.reason()));
            }
            return result;
        }
        private DamageOutcome damage(SkillExecutionContext context,
                                     StrikeGeometryService.Candidate<Ref<EntityStore>> target, int hitIndex,
                                     double coefficient, double criticalChance, DamageCause cause) {
            return damage(context, target, hitIndex, coefficient, criticalChance, cause, false);
        }
        private double effectiveAttribute(SkillExecutionContext context) {
            return switch (context.profile().scaling()) {
                case "HEAVY" -> context.snapshot().derivedStats().effective(RpgAttribute.STR);
                case "LIGHT" -> context.snapshot().derivedStats().effective(RpgAttribute.DEX);
                case "MAGIC" -> context.snapshot().derivedStats().effective(RpgAttribute.INT);
                case "WEAPON_CLASS" -> context.snapshot().weaponClass().scalingAttribute()
                        .map(context.snapshot().derivedStats()::effective).orElse(0.0);
                default -> 0.0;
            };
        }
        private DamageOutcome damage(SkillExecutionContext context,
                                     StrikeGeometryService.Candidate<Ref<EntityStore>> target, int hitIndex,
                                     double coefficient, double criticalChance, DamageCause cause, boolean periodic) {
            double effective = effectiveAttribute(context);
            DamageCalculationService.Result result = kernel.damage().calculate(new DamageCalculationService.Request(
                    context.snapshot().basePower(), effective, coefficient,
                    context.snapshot().modifiers(), !periodic, criticalChance,
                    context.snapshot().criticalMultiplier()));
            CombatTrace.Context ids = ids(context);
            trace.emit(playerRef.getUuid(), RpgTraceEventType.DAMAGE_CALC_BEGIN, ids,
                    Map.of("basePower", context.snapshot().basePower(), "effectiveAttribute", effective,
                            "coefficient", coefficient, "hitIndex", hitIndex, "periodic", periodic));
            trace.emit(playerRef.getUuid(), RpgTraceEventType.BASE_POWER_RESOLVED, ids,
                    Map.of("source", context.snapshot().basePowerSource(), "weaponClass", context.snapshot().weaponClass(),
                            "basePower", context.snapshot().basePower()));
            trace.emit(playerRef.getUuid(), RpgTraceEventType.SCALING_APPLIED, ids,
                    Map.of("attributeMultiplier", result.attributeMultiplier(), "scaledBasePower", result.scaledBasePower()));
            trace.emit(playerRef.getUuid(), RpgTraceEventType.MODIFIERS_APPLIED, ids,
                    Map.of("modifierFactor", result.modifierFactor(), "preCritDamage", result.preCritDamage()));
            trace.emit(playerRef.getUuid(), RpgTraceEventType.CRIT_ROLL, ids,
                    Map.of("chance", criticalChance, "critical", result.critical(),
                            "multiplier", context.snapshot().criticalMultiplier()));
            EntityStatMap targetStats = store.getComponent(target.handle(), EntityStatMap.getComponentType());
            double before = health(targetStats);
            var nativeResult = new HytaleDamageAdapter().applyObserved(target.handle(), store, actor, cause,
                    new HytaleDamageMetadata(playerRef.getUuid(), context.rootCastId(), context.skillInstanceId(),
                            context.request().correlationId(), result.preMitigationDamage(), Double.NaN), result);
            double after = health(targetStats);
            return new DamageOutcome(result.preMitigationDamage(),
                    Double.isFinite(before) && Double.isFinite(after) ? Math.max(0.0, before - after) : -1.0,
                    nativeResult.cancelled());
        }
        private void applyStatus(SkillExecutionContext context,
                                 StrikeGeometryService.Candidate<Ref<EntityStore>> target) {
            UUID targetId = UUID.fromString(target.stableId());
            emit(context, RpgTraceEventType.STATUS_REQUEST, Map.of("targetId", target.stableId(),
                    "status", context.profile().strike().statusId(),
                    "durationSeconds", context.profile().strike().statusSeconds()));
            var result = kernel.statuses().apply(targetId, RpgStatusType.valueOf(context.profile().strike().statusId()),
                    new ControlProfile(target.protectedTarget(), target.boss(), false),
                    context.profile().strike().statusSeconds());
            if ((result.outcome() == com.inigmasgames.hytalerpg.combat.status.StatusService.Outcome.APPLIED
                    || result.outcome() == com.inigmasgames.hytalerpg.combat.status.StatusService.Outcome.REFRESHED)
                    && result.type() == RpgStatusType.STAGGER && !applyNativeStagger(target.handle(), result.remainingSeconds())) {
                kernel.statuses().remove(targetId, result.type());
                trace.emit(playerRef.getUuid(), RpgTraceEventType.STATUS_REJECTED, ids(context), Map.of(
                        "targetId", target.stableId(), "status", result.type().name(),
                        "durationSeconds", result.remainingSeconds(), "detail", "NATIVE_STUN_EFFECT_UNAVAILABLE"));
                return;
            }
            RpgTraceEventType event = switch (result.outcome()) {
                case APPLIED -> RpgTraceEventType.STATUS_APPLIED;
                case REFRESHED -> RpgTraceEventType.STATUS_REFRESHED;
                case THRESHOLD -> RpgTraceEventType.STATUS_THRESHOLD;
                case REJECTED -> RpgTraceEventType.STATUS_REJECTED;
            };
            trace.emit(playerRef.getUuid(), event, ids(context), Map.of("targetId", target.stableId(),
                    "status", result.type().name(), "durationSeconds", result.remainingSeconds(),
                    "detail", result.detail()));
        }
        private boolean applyNativeStagger(Ref<EntityStore> target, double seconds) {
            EffectControllerComponent controller = store.getComponent(target, EffectControllerComponent.getComponentType());
            EntityEffect effect = EntityEffect.getAssetMap().getAsset(NATIVE_STAGGER_EFFECT);
            return controller != null && effect != null && controller.addEffect(target, effect, (float) seconds,
                    OverlapBehavior.OVERWRITE, store, actor);
        }
        private List<StrikeGeometryService.Candidate<Ref<EntityStore>>> candidates(Vec3 origin, double radius) {
            SpatialResource<Ref<EntityStore>, EntityStore> spatial = store.getResource(
                    EntityModule.get().getEntitySpatialResourceType());
            if (spatial == null) return List.of();
            List<Ref<EntityStore>> refs = new ArrayList<>();
            spatial.getSpatialStructure().collect(new Vector3d(origin.x(), origin.y(), origin.z()), radius + 1.0, refs);
            List<StrikeGeometryService.Candidate<Ref<EntityStore>>> result = new ArrayList<>();
            for (Ref<EntityStore> target : refs) {
                if (target == null || !target.isValid() || target.equals(actor)) continue;
                StrikeGeometryService.Candidate<Ref<EntityStore>> candidate = candidate(target);
                if (candidate != null) result.add(candidate);
            }
            return result;
        }

        private StrikeGeometryService.Candidate<Ref<EntityStore>> candidate(Ref<EntityStore> target) {
            if (target == null || !target.isValid() || target.equals(actor)) return null;
            NPCEntity npc = store.getComponent(target, NPCEntity.getComponentType());
            TransformComponent targetTransform = store.getComponent(target, TransformComponent.getComponentType());
            EntityStatMap targetStats = store.getComponent(target, EntityStatMap.getComponentType());
            if (npc == null || targetTransform == null || targetStats == null) return null;
            var targetHealth = targetStats.get(DefaultEntityStatTypes.getHealth());
            if (targetHealth == null || targetHealth.get() <= targetHealth.getMin()) return null;
            UUIDComponent uuid = store.getComponent(target, UUIDComponent.getComponentType());
            UUID id = uuid == null ? UUID.nameUUIDFromBytes(target.toString().getBytes(StandardCharsets.UTF_8)) : uuid.getUuid();
            boolean protectedTarget = store.getComponent(target, Invulnerable.getComponentType()) != null
                    || npc.getRole() != null && npc.getRole().isInvulnerable();
            EffectControllerComponent effects = store.getComponent(target, EffectControllerComponent.getComponentType());
            protectedTarget |= effects != null && effects.isInvulnerable();
            NetworkId networkId = store.getComponent(target, NetworkId.getComponentType());
            boolean boss = networkId != null && bosses.isBoss(playerRef.getWorldUuid(), networkId.getId());
            return new StrikeGeometryService.Candidate<>(id.toString(), target,
                    vec(targetTransform.getPosition()), true, protectedTarget, boss);
        }

        private boolean applyBurn(SkillExecutionContext context,
                StrikeGeometryService.Candidate<Ref<EntityStore>> target) {
            var profile = context.profile().projectile();
            return applyPeriodicStatus(context, target, PeriodicStatusRuntime.Kind.BURN,
                    profile.statusSeconds(), profile.periodicCoefficient() / profile.periodicIntervalSeconds());
        }

        private boolean applyPeriodicStatus(SkillExecutionContext context,
                StrikeGeometryService.Candidate<Ref<EntityStore>> target,
                PeriodicStatusRuntime.Kind kind, double duration, double coefficientPerSecond) {
            UUID targetId = UUID.fromString(target.stableId());
            var source = new PeriodicStatusRuntime.Source(playerRef.getUuid(), context.profile().skillId(), targetId, kind);
            double now = System.nanoTime() / 1e9;
            String admitted = periodicStatuses.admission(source);
            if (!admitted.equals("PASS")) {
                emit(context, RpgTraceEventType.STATUS_REJECTED, Map.of("status", kind, "targetId", targetId, "reason", admitted));
                return false;
            }
            EffectControllerComponent controller = store.getComponent(target.handle(), EffectControllerComponent.getComponentType());
            String effectId = kind == PeriodicStatusRuntime.Kind.BURN ? NATIVE_BURN_VISUAL_EFFECT : "RPG_Poison_Visual";
            EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectId);
            double visualDuration = Math.max(duration, periodicStatuses.view(targetId, kind, now).remainingSeconds());
            if (controller == null || effect == null || !controller.addEffect(target.handle(), effect,
                    (float) visualDuration, OverlapBehavior.OVERWRITE, store, actor)) {
                emit(context, RpgTraceEventType.STATUS_REJECTED, Map.of("status", kind, "targetId", targetId, "reason", "NATIVE_EFFECT_REJECTED"));
                return false;
            }
            double strength = kernel.damage().calculate(DamageCalculationService.Request.periodic(
                    context.snapshot().basePower(), effectiveAttribute(context), coefficientPerSecond,
                    context.snapshot().modifiers(), 0, context.snapshot().criticalMultiplier())).preMitigationDamage();
            String result = periodicStatuses.apply(source, context, new PeriodicTarget(actor, target.handle()),
                    coefficientPerSecond, strength, duration, 1, kind == PeriodicStatusRuntime.Kind.BURN ? 1 : 3, now, periodicPort());
            boolean accepted = result.equals("APPLIED") || result.equals("REFRESHED");
            emit(context, accepted ? RpgTraceEventType.STATUS_APPLIED : RpgTraceEventType.STATUS_REJECTED,
                    Map.of("status", kind, "targetId", targetId, "durationSeconds", duration, "result", result,
                            "authority", "RPG_SOURCE_PACKAGE", "nativeBehaviorVerified", false));
            return accepted;
        }

        private String applyProjectileStatus(SkillExecutionContext context,
                                             StrikeGeometryService.Candidate<Ref<EntityStore>> target) {
            Stage04SkillProfile.Projectile projectile = context.profile().projectile();
            RpgStatusType type = RpgStatusType.valueOf(projectile.statusId());
            UUID targetId = UUID.fromString(target.stableId());
            emit(context, RpgTraceEventType.STATUS_REQUEST, Map.of("targetId", target.stableId(),
                    "status", type.name(), "durationSeconds", projectile.statusSeconds()));
            var control = new ControlProfile(target.protectedTarget(), target.boss(), false);
            var result = projectile.statusSeconds() > 0.0
                    ? kernel.statuses().apply(targetId, type, control, projectile.statusSeconds())
                    : kernel.statuses().apply(targetId, type, control);
            RpgTraceEventType event = switch (result.outcome()) {
                case APPLIED -> RpgTraceEventType.STATUS_APPLIED;
                case REFRESHED -> RpgTraceEventType.STATUS_REFRESHED;
                case THRESHOLD -> RpgTraceEventType.STATUS_THRESHOLD;
                case REJECTED -> RpgTraceEventType.STATUS_REJECTED;
            };
            trace.emit(playerRef.getUuid(), event, ids(context), Map.of("targetId", target.stableId(),
                    "status", result.type().name(), "stacks", result.stacks(),
                    "durationSeconds", result.remainingSeconds(), "detail", result.detail()));
            return result.outcome().name() + ':' + result.type().name() + ":stacks=" + result.stacks();
        }

        private double applyProjectileKnockback(SkillExecutionContext context,
                                                StrikeGeometryService.Candidate<Ref<EntityStore>> target) {
            double requested = context.profile().projectile().knockbackDistance();
            if (requested <= 0.0 || target.protectedTarget() || target.boss() || buffer == null) return 0.0;
            TransformComponent sourceTransform = store.getComponent(actor, TransformComponent.getComponentType());
            TransformComponent targetTransform = store.getComponent(target.handle(), TransformComponent.getComponentType());
            if (sourceTransform == null || targetTransform == null) return 0.0;
            Vec3 away = vec(targetTransform.getPosition()).subtract(vec(sourceTransform.getPosition())).horizontalNormalized();
            KnockbackComponent knockback = buffer.ensureAndGetComponent(target.handle(), KnockbackComponent.getComponentType());
            if (knockback == null) return 0.0;
            knockback.setVelocity(new Vector3d(away.x() * requested, 0.15, away.z() * requested));
            knockback.setVelocityType(ChangeVelocityType.Add);
            knockback.setDuration(0.25f);
            return requested;
        }
    }

    private double collisionFraction(Store<EntityStore> store, Ref<EntityStore> actor, Vec3 origin, Vec3 displacement) {
        if (displacement.distanceSquared(new Vec3(0, 0, 0)) < 1.0e-12) return 1.0;
        BoundingBox bounds = store.getComponent(actor, BoundingBox.getComponentType());
        if (bounds == null) return 0.0;
        CollisionResult result = new CollisionResult(); result.setDefaultPlayerSettings(); result.disableCharacterCollisions();
        CollisionModule.findCollisions(new Box(bounds.getBoundingBox()),
                new Vector3d(origin.x(), origin.y(), origin.z()),
                new Vector3d(displacement.x(), displacement.y(), displacement.z()), result, store);
        double fraction = 1.0;
        for (int i = 0; i < result.getBlockCollisionCount(); i++)
            fraction = Math.min(fraction, result.getBlockCollision(i).collisionStart);
        double margin = 0.025 / Math.max(0.025, Math.sqrt(displacement.distanceSquared(new Vec3(0, 0, 0))));
        return Math.max(0.0, Math.min(1.0, fraction - (fraction < 1.0 ? margin : 0.0)));
    }

    private void advanceRepeatingStrike(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef,
                                        Player player, EntityStatMap stats, CommandBuffer<EntityStore> buffer) {
        RepeatingStrike repeating = repeatingStrikes.get(playerRef.getUuid());
        if (repeating == null) return;
        Port port = new Port(store, ref, playerRef, player, stats, null, buffer);
        if (!port.actorAliveAndUsable()) {
            cancel(playerRef.getUuid(), "REPEATED_STRIKE_ACTOR_UNUSABLE", buffer); return;
        }
        long now = System.nanoTime();
        for (var due = repeating.schedule.claimDue(now); due.isPresent(); due = repeating.schedule.claimDue(now))
            port.executeStrikeHit(repeating.context, due.getAsInt());
        if (repeating.schedule.complete()) {
            repeatingStrikes.remove(playerRef.getUuid());
            hits.clear(repeating.context.skillInstanceId());
            executions.terminate(repeating.context, "STRIKE_REPEATS_COMPLETE");
        }
    }

    private void onProjectileImpact(ProjectileCarrier expected, Ref<EntityStore> projectileRef,
                                    Vector3d position, Vector3i blockPosition, Ref<EntityStore> hitEntity,
                                    CommandBuffer<EntityStore> buffer) {
        String projectileId = expected.instance.plan().projectileInstanceId();
        ProjectileCarrier carrier = projectiles.get(projectileId);
        if (carrier != expected) {
            if (hitEntity != null && hitEntity.isValid()) {
                UUIDComponent uuid = buffer.getStore().getComponent(hitEntity, UUIDComponent.getComponentType());
                String targetId = uuid == null ? hitEntity.toString() : uuid.getUuid().toString();
                if (expected.instance.previouslyHit(targetId))
                    emitProjectile(expected, RpgTraceEventType.PROJECTILE_TARGET_DEDUP,
                            Map.of("targetId", targetId, "reason", "ALREADY_HIT"));
            }
            return;
        }
        Store<EntityStore> store = buffer.getStore();
        if (!carrier.actor.isValid()) {
            cancelProjectile(carrier, "ACTOR_REMOVED", position == null ? null : vec(position), buffer);
            return;
        }
        PlayerRef playerRef = store.getComponent(carrier.actor, PlayerRef.getComponentType());
        Player player = store.getComponent(carrier.actor, Player.getComponentType());
        EntityStatMap stats = store.getComponent(carrier.actor, EntityStatMap.getComponentType());
        if (playerRef == null || player == null || stats == null) {
            cancelProjectile(carrier, "ACTOR_COMPONENTS_MISSING", position == null ? null : vec(position), buffer);
            return;
        }
        Port port = new Port(store, carrier.actor, playerRef, player, stats, hitEntity, buffer);
        if (hitEntity != null && hitEntity.isValid()) {
            StrikeGeometryService.Candidate<Ref<EntityStore>> target = port.candidate(hitEntity);
            if (target == null || target.protectedTarget()) {
                emitProjectile(carrier, RpgTraceEventType.PROJECTILE_TARGET_REJECTED,
                        Map.of("target", hitEntity.toString(), "reason",
                                target == null ? "INVALID_OR_NON_DAMAGEABLE" : "PROTECTED_TARGET"));
                terminateProjectile(carrier, "TARGET_REJECTED", vec(position), buffer);
                return;
            }
            if (!projectileService.onEnemyContact(carrier.instance, target.stableId())) {
                emitProjectile(carrier, RpgTraceEventType.PROJECTILE_TARGET_DEDUP,
                        Map.of("targetId", target.stableId(), "reason", "ALREADY_HIT"));
                return;
            }
            projectiles.remove(projectileId, carrier);
            if (projectileRef != null && projectileRef.isValid()) buffer.tryRemoveEntity(projectileRef, RemoveReason.REMOVE);
            try {
                Stage04SkillProfile.Projectile authored = carrier.context.profile().projectile();
                DamageOutcome outcome = port.damage(carrier.context, target, 0, authored.coefficient(),
                        carrier.context.snapshot().criticalChance(), DamageCause.PROJECTILE);
                String statusResult = "NONE";
                if (outcome.actualHealthLoss() > 0.0 && !authored.statusId().isBlank())
                    statusResult = authored.hasPeriodicStatus()
                            ? (port.applyBurn(carrier.context, target) ? "BURN_APPLIED" : "BURN_REJECTED")
                            : port.applyProjectileStatus(carrier.context, target);
                double appliedKnockback = outcome.actualHealthLoss() > 0.0
                        ? port.applyProjectileKnockback(carrier.context, target) : 0.0;
                Map<String, Object> hit = new HashMap<>();
                hit.put("targetId", target.stableId());
                hit.put("preMitigationDamage", outcome.preMitigationDamage());
                hit.put("actualHealthLoss", outcome.actualHealthLoss());
                hit.put("statusResult", statusResult);
                hit.put("requestedKnockback", authored.knockbackDistance());
                hit.put("appliedKnockback", appliedKnockback);
                hit.put("targetCap", authored.targetCap());
                hit.put("impactX", position.x); hit.put("impactY", position.y); hit.put("impactZ", position.z);
                hit.put("travelledDistance", carrier.instance.flight().travelled());
                emitProjectile(carrier, RpgTraceEventType.PROJECTILE_ENTITY_HIT, hit);
                projectileService.onForwardTermination(carrier.instance, "ENTITY_HIT", vec(position));
                emitProjectile(carrier, RpgTraceEventType.PROJECTILE_TERMINATED,
                        Map.of("reason", "ENTITY_HIT", "travelledDistance", carrier.instance.flight().travelled()));
                executions.terminate(carrier.context, "PROJECTILE_ENTITY_HIT");
            } catch (RuntimeException error) {
                projectileService.onForwardTermination(carrier.instance, "PAYLOAD_FAILURE", vec(position));
                emitProjectile(carrier, RpgTraceEventType.PROJECTILE_CANCELLED,
                        Map.of("reason", "PAYLOAD_FAILURE", "error", error.getClass().getSimpleName()));
                emitProjectile(carrier, RpgTraceEventType.PROJECTILE_TERMINATED,
                        Map.of("reason", "PAYLOAD_FAILURE"));
                executions.terminate(carrier.context, "PROJECTILE_PAYLOAD_FAILURE");
            }
            return;
        }
        Map<String, Object> details = new HashMap<>();
        details.put("impactX", position.x); details.put("impactY", position.y); details.put("impactZ", position.z);
        details.put("interaction", "NATIVE_BLOCK_COLLISION");
        if (blockPosition != null) {
            details.put("blockX", blockPosition.x); details.put("blockY", blockPosition.y); details.put("blockZ", blockPosition.z);
        }
        details.put("travelledDistance", carrier.instance.flight().travelled());
        emitProjectile(carrier, RpgTraceEventType.PROJECTILE_TERRAIN_HIT, details);
        projectiles.remove(projectileId, carrier);
        if (projectileRef != null && projectileRef.isValid()) buffer.tryRemoveEntity(projectileRef, RemoveReason.REMOVE);
        projectileService.onTerrainContact(carrier.instance, vec(position));
        emitProjectile(carrier, RpgTraceEventType.PROJECTILE_TERMINATED,
                Map.of("reason", "TERRAIN_HIT", "travelledDistance", carrier.instance.flight().travelled()));
        executions.terminate(carrier.context, "PROJECTILE_TERRAIN_HIT");
    }

    private void advanceProjectiles(UUID actorId, float deltaSeconds, Store<EntityStore> store,
                                    CommandBuffer<EntityStore> buffer) {
        List<ProjectileCarrier> owned = projectiles.values().stream()
                .filter(value -> value.actorId.equals(actorId)).toList();
        for (ProjectileCarrier carrier : owned) {
            String projectileId = carrier.instance.plan().projectileInstanceId();
            if (!carrier.projectile.isValid()) {
                if (projectiles.remove(projectileId, carrier)) {
                    projectileService.onForwardTermination(carrier.instance,
                            "NATIVE_PROJECTILE_REMOVED_WITHOUT_IMPACT", carrier.instance.flight().lastPosition());
                    emitProjectile(carrier, RpgTraceEventType.PROJECTILE_CANCELLED,
                            Map.of("reason", "NATIVE_PROJECTILE_REMOVED_WITHOUT_IMPACT"));
                    emitProjectile(carrier, RpgTraceEventType.PROJECTILE_TERMINATED,
                            Map.of("reason", "NATIVE_PROJECTILE_REMOVED_WITHOUT_IMPACT"));
                    executions.terminate(carrier.context, "PROJECTILE_NATIVE_REMOVAL");
                }
                continue;
            }
            TransformComponent transform = store.getComponent(carrier.projectile, TransformComponent.getComponentType());
            if (transform == null) continue;
            ProjectileFlight.Observation observation = carrier.instance.observe(Math.max(0.0, deltaSeconds),
                    vec(transform.getPosition()));
            if (!observation.expired() || !projectiles.remove(projectileId, carrier)) continue;
            buffer.tryRemoveEntity(carrier.projectile, RemoveReason.REMOVE);
            boolean range = observation.travelled() + 1.0e-6 >= observation.maxDistance();
            String reason = range ? "MAX_RANGE" : "MAX_LIFETIME";
            projectileService.onForwardTermination(carrier.instance, reason, observation.position());
            emitProjectile(carrier, range ? RpgTraceEventType.PROJECTILE_MAX_RANGE : RpgTraceEventType.PROJECTILE_EXPIRED,
                    Map.of("reason", reason, "travelledDistance", observation.travelled(),
                            "elapsed", observation.elapsed(), "maxDistance", observation.maxDistance(),
                            "maximumLifetimeSeconds", observation.maxLifetimeSeconds(),
                            "positionX", observation.position().x(), "positionY", observation.position().y(),
                            "positionZ", observation.position().z()));
            emitProjectile(carrier, RpgTraceEventType.PROJECTILE_TERMINATED,
                    Map.of("reason", reason, "travelledDistance", observation.travelled()));
            executions.terminate(carrier.context, "PROJECTILE_" + reason);
        }
    }

    private PeriodicStatusRuntime.Port<SkillExecutionContext, PeriodicTarget> periodicPort() {
        return new PeriodicStatusRuntime.Port<>() {
            @Override public void terminated(PeriodicStatusRuntime.Source source, SkillExecutionContext context,
                    PeriodicTarget target, String reason) {
                emit(context, RpgTraceEventType.STATUS_REMOVED,
                        Map.of("targetId", source.victim(), "status", source.kind(), "sourceSkill", source.skill(), "reason", reason));
            }
            @Override public boolean tick(PeriodicStatusRuntime.Source source, SkillExecutionContext context,
                    PeriodicTarget target, int tickIndex, double coefficient, double seconds) {
                if (!target.actor.isValid() || !target.victim.isValid()
                        || target.actor.getStore() != target.victim.getStore()) return false;
                Store<EntityStore> store = target.actor.getStore();
                if (!store.isInThread()) throw new IllegalStateException("PERIODIC_DAMAGE_WRONG_WORLD_THREAD");
                PlayerRef owner = store.getComponent(target.actor, PlayerRef.getComponentType());
                Player player = store.getComponent(target.actor, Player.getComponentType());
                EntityStatMap stats = store.getComponent(target.actor, EntityStatMap.getComponentType());
                if (owner == null || player == null || stats == null || !owner.getUuid().equals(source.owner())) return false;
                Port port = new Port(store, target.actor, owner, player, stats, target.victim, null);
                if (!port.actorAliveAndUsable() || !HytaleAreaQueries.hostile(store, target.victim, target.actor)) return false;
                var candidate = port.candidate(target.victim);
                if (candidate == null || candidate.protectedTarget()) return false;
                // Preserve the Stage 05 Fire Bolt channel; newly authored area statuses use their explicit element.
                DamageCause cause = context.profile().projectile() != null ? DamageCause.PROJECTILE
                        : DamageCause.getAssetMap().getAsset(source.kind() == PeriodicStatusRuntime.Kind.BURN ? "Fire" : "Poison");
                if (cause == null) throw new IllegalStateException("PERIODIC_DAMAGE_CAUSE_UNAVAILABLE");
                DamageOutcome outcome = port.damage(context, candidate, tickIndex, coefficient, 0, cause, true);
                emit(context, source.kind() == PeriodicStatusRuntime.Kind.BURN ? RpgTraceEventType.BURN_TICK : RpgTraceEventType.POISON_TICK,
                        Map.of("targetId", source.victim(), "tickIndex", tickIndex, "coefficient", coefficient,
                                "integratedSeconds", seconds, "canCrit", false, "canTrigger", false,
                                "preMitigationDamage", outcome.preMitigationDamage(), "actualHealthLoss", outcome.actualHealthLoss()));
                return !outcome.cancelled();
            }
            @Override public void changed(PeriodicStatusRuntime.Source source, PeriodicTarget target, PeriodicStatusRuntime.View ignored) {
                double now = System.nanoTime() / 1e9;
                var view = periodicStatuses.view(source.victim(), source.kind(), now);
                kernel.statuses().projectPeriodic(source.victim(), RpgStatusType.valueOf(source.kind().name()), view.stacks(), view.remainingSeconds());
                if (!target.victim.isValid()) return;
                Store<EntityStore> targetStore = target.victim.getStore();
                Runnable reconcile = () -> {
                    if (!target.victim.isValid()) return;
                    var current = periodicStatuses.view(source.victim(), source.kind(), System.nanoTime() / 1e9);
                    var controller = targetStore.getComponent(target.victim, EffectControllerComponent.getComponentType());
                    String effectId = source.kind() == PeriodicStatusRuntime.Kind.BURN ? NATIVE_BURN_VISUAL_EFFECT : "RPG_Poison_Visual";
                    var effect = EntityEffect.getAssetMap().getAsset(effectId);
                    if (controller == null || effect == null) return;
                    if (current.remainingSeconds() <= 0) controller.removeEffect(target.victim, EntityEffect.getAssetMap().getIndex(effectId), targetStore);
                    else controller.addEffect(target.victim, effect, (float) current.remainingSeconds(), OverlapBehavior.OVERWRITE, targetStore);
                };
                if (targetStore.isInThread()) reconcile.run();
                else targetStore.getExternalData().getWorld().execute(reconcile);
            }
        };
    }

    private void removeOwnedProjectiles(UUID actorId, CommandBuffer<EntityStore> buffer) {
        List<ProjectileCarrier> owned = projectiles.values().stream()
                .filter(value -> value.actorId.equals(actorId)).toList();
        for (ProjectileCarrier carrier : owned) {
            if (!projectiles.remove(carrier.instance.plan().projectileInstanceId(), carrier)) continue;
            projectileService.onForwardTermination(carrier.instance, "OWNER_CANCELLED", carrier.instance.flight().lastPosition());
            if (carrier.projectile.isValid()) {
                if (buffer != null) buffer.tryRemoveEntity(carrier.projectile, RemoveReason.REMOVE);
                else carrier.projectile.getStore().removeEntity(carrier.projectile, RemoveReason.REMOVE);
            }
            emitProjectile(carrier, RpgTraceEventType.PROJECTILE_CANCELLED,
                    Map.of("reason", "OWNER_CANCELLED", "travelledDistance", carrier.instance.flight().travelled()));
            emitProjectile(carrier, RpgTraceEventType.PROJECTILE_TERMINATED,
                    Map.of("reason", "OWNER_CANCELLED", "travelledDistance", carrier.instance.flight().travelled()));
        }
    }

    private void removeOwnedBurns(UUID actorId) {
        periodicStatuses.cancel(actorId, System.nanoTime() / 1e9, periodicPort());
    }

    private static Vec3 facing(Store<EntityStore> store, Ref<EntityStore> actor) {
        HeadRotation head = store.getComponent(actor, HeadRotation.getComponentType());
        Vector3d direction = head == null ? null : head.getDirection();
        if (direction == null) {
            TransformComponent transform = store.getComponent(actor, TransformComponent.getComponentType());
            direction = transform.getRotation().transform(new Vector3d(0, 0, 1));
        }
        return new Vec3(direction.x, 0, direction.z).horizontalNormalized();
    }
    private static Vec3 aim(Store<EntityStore> store, Ref<EntityStore> actor) {
        HeadRotation head = store.getComponent(actor, HeadRotation.getComponentType());
        Vector3d direction = head == null ? null : head.getDirection();
        if (direction == null) {
            TransformComponent transform = store.getComponent(actor, TransformComponent.getComponentType());
            direction = transform.getRotation().transform(new Vector3d(0, 0, 1));
        }
        return new Vec3(direction.x, direction.y, direction.z).normalized();
    }
    private static Vector3d vector(Vec3 value) { return new Vector3d(value.x(), value.y(), value.z()); }
    private static Vec3 vec(org.joml.Vector3dc value) { return new Vec3(value.x(), value.y(), value.z()); }
    private static double health(EntityStatMap stats) {
        if (stats == null) return Double.NaN;
        var health = stats.get(DefaultEntityStatTypes.getHealth());
        return health == null ? Double.NaN : health.get();
    }
    private void emit(SkillExecutionContext context, RpgTraceEventType event, Map<String, ?> details) {
        trace.emit(context.request().actorId(), event, ids(context), details);
    }
    private void emitProjectile(ProjectileCarrier carrier, RpgTraceEventType event, Map<String, ?> details) {
        Map<String, Object> values = new HashMap<>();
        ProjectileExecutionPlan plan = carrier.instance.plan();
        values.put("projectileInstanceId", plan.projectileInstanceId());
        values.put("generation", plan.generation());
        values.put("caster", plan.ownerId().toString());
        values.put("skillId", plan.skillId());
        values.put("compiledPlanHash", plan.compiledPlanHash());
        values.putAll(details);
        emit(carrier.context, event, values);
    }
    private void cancelProjectile(ProjectileCarrier carrier, String reason, Vec3 position,
                                  CommandBuffer<EntityStore> buffer) {
        projectiles.remove(carrier.instance.plan().projectileInstanceId(), carrier);
        if (carrier.projectile.isValid()) buffer.tryRemoveEntity(carrier.projectile, RemoveReason.REMOVE);
        projectileService.onForwardTermination(carrier.instance, reason, position);
        emitProjectile(carrier, RpgTraceEventType.PROJECTILE_CANCELLED, Map.of("reason", reason));
        emitProjectile(carrier, RpgTraceEventType.PROJECTILE_TERMINATED, Map.of("reason", reason));
        executions.terminate(carrier.context, "PROJECTILE_" + reason);
    }
    private void terminateProjectile(ProjectileCarrier carrier, String reason, Vec3 position,
                                     CommandBuffer<EntityStore> buffer) {
        projectiles.remove(carrier.instance.plan().projectileInstanceId(), carrier);
        if (carrier.projectile.isValid()) buffer.tryRemoveEntity(carrier.projectile, RemoveReason.REMOVE);
        projectileService.onForwardTermination(carrier.instance, reason, position);
        emitProjectile(carrier, RpgTraceEventType.PROJECTILE_TERMINATED,
                Map.of("reason", reason, "travelledDistance", carrier.instance.flight().travelled()));
        executions.terminate(carrier.context, "PROJECTILE_" + reason);
    }
    private static CombatTrace.Context ids(SkillExecutionContext context) {
        return new CombatTrace.Context(context.rootCastId(), context.skillInstanceId(), context.request().correlationId());
    }
    private static final class Motion {
        final SkillExecutionContext context; final Ref<EntityStore> actor; final MovementPlanner.Plan plan; double elapsed;
        Motion(SkillExecutionContext context, Ref<EntityStore> actor, MovementPlanner.Plan plan) {
            this.context = context; this.actor = actor; this.plan = plan;
        }
    }
    private record Counter(SkillExecutionContext context, Ref<EntityStore> attacker, String eventId) { }
    private record RepeatingStrike(SkillExecutionContext context, StrikeRepeatSchedule schedule) { }
    private record ProjectileCarrier(SkillExecutionContext context, Ref<EntityStore> actor, UUID actorId,
                                     Ref<EntityStore> projectile, ProjectileInstance instance) { }
    private record PeriodicTarget(Ref<EntityStore> actor, Ref<EntityStore> victim) { }
    private record DamageOutcome(double preMitigationDamage, double actualHealthLoss, boolean cancelled) { }
}
