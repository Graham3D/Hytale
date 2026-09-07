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
import com.inigmasgames.hytalerpg.execution.CommittedTarget;
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
import com.inigmasgames.hytalerpg.execution.projectile.ProjectileContinuation;
import com.inigmasgames.hytalerpg.execution.projectile.ProjectileSecondaryEffects;
import com.inigmasgames.hytalerpg.execution.projectile.ProjectileSweep;
import com.hypixel.hytale.server.core.modules.projectile.component.PierceProjectile;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider;
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
    private final Map<String, ProjectileCarrier> projectiles = new java.util.concurrent.ConcurrentHashMap<>();
    private final RpgProjectileService projectileService =
            new RpgProjectileService(new ProjectileLifecycleRegistry());
    private final ProjectileContinuation continuations=new ProjectileContinuation(projectileService.registry());
    private final ProjectileSecondaryEffects projectileSecondaries=new ProjectileSecondaryEffects(projectileService.registry());
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
        executions.tickScheduled(actor,port);
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
                var query = areaWorld().query(profile.area().footprint(areaPlacement, areaDirection, plan.executionModifiers().radiusFactor()), profile.area().candidateBudget());
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
                String admission=projectileService.registry().admission(playerRef.getUuid(),plan.projectileModifiers().rootLaunches(plan.executionModifiers().echoDelaySeconds()>0));
                if(!admission.equals("PASS"))return Validation.reject(admission);
                Vec3 eye=vec(store.getComponent(actor,TransformComponent.getComponentType()).getPosition()).add(new Vec3(0,1.35,0));
                Vec3 direction=aim(store,actor),origin=eye.add(direction.multiply(.65));
                for(int index=0;index<plan.projectileModifiers().batchSize();index++) {
                    Vec3 spread=ProjectileContinuation.yaw(direction,plan.projectileModifiers().volley()?(index-1)*12:0);
                    if(!HytaleAreaQueries.clear(store,eye,origin.add(spread.multiply(.03))))return Validation.reject("PROJECTILE_MUZZLE_BLOCKED");
                }
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
        @Override public CommittedTarget captureTarget(Stage04SkillProfile profile,
                com.inigmasgames.hytalerpg.domain.CompiledSkillPlan plan,SkillExecutionRequest request) {
            Vec3 feet=vec(store.getComponent(actor,TransformComponent.getComponentType()).getPosition());
            Vec3 direction=facing(store,actor),point=feet;UUID targetId=null;
            if(profile.area()!=null) { point=areaPlacement;direction=areaDirection; }
            else if(profile.projectile()!=null) {
                direction=aim(store,actor);Vec3 muzzle=feet.add(new Vec3(0,1.35,0)).add(direction.multiply(.65));
                point=HytaleAreaQueries.rayEndpoint(store,muzzle,direction,profile.projectile().maxDistance()*plan.projectileModifiers().distanceFactor());
            } else if(profile.movement()!=null) {
                if(profile.movement().kind()==Stage04SkillProfile.MovementKind.LEAP) {
                    if(pounceTarget==null || !pounceTarget.isValid()) throw new IllegalStateException("COMMITTED_ENTITY_TARGET_MISSING");
                    var id=store.getComponent(pounceTarget,UUIDComponent.getComponentType());
                    if(id==null) throw new IllegalStateException("COMMITTED_TARGET_UUID_MISSING");
                    targetId=id.getUuid();point=vec(store.getComponent(pounceTarget,TransformComponent.getComponentType()).getPosition());
                    direction=point.subtract(feet).horizontalNormalized();
                } else {
                    if(request.desiredMovement().horizontalLengthSquared()>1e-6)direction=request.desiredMovement().horizontalNormalized();
                    point=feet.add(direction.multiply(profile.movement().maxDistance()));
                }
            } else if(profile.strike()!=null) point=feet.add(direction.multiply(profile.strike().range()));
            return new CommittedTarget(playerRef.getWorldUuid(),feet,point,direction,targetId);
        }
        @Override public Validation validateRelease(SkillExecutionContext context) {
            var target=context.target();var profile=context.profile();
            if(target==null) return Validation.reject("COMMITTED_TARGET_MISSING");
            if(!target.worldId().equals(playerRef.getWorldUuid())) return Validation.reject("COMMITTED_WORLD_CHANGED");
            var current=equipment();var committed=context.equipment();
            if(!profile.allowedMainHandKinds().isEmpty() && !sameItem(current.mainHand(),committed.mainHand())
                    || !profile.requiredOffHandKinds().isEmpty() && !sameItem(current.offHand(),committed.offHand()))
                return Validation.reject("COMMITTED_EQUIPMENT_CHANGED");
            if(motions.containsKey(playerRef.getUuid())||windupEnds.containsKey(playerRef.getUuid())||reactions.active(playerRef.getUuid()).isPresent())
                return Validation.reject("INCOMPATIBLE_ACTIVE_STATE");
            Vec3 feet=vec(store.getComponent(actor,TransformComponent.getComponentType()).getPosition());
            if(profile.area()!=null) {
                String admission=areas.admission(playerRef.getUuid(),profile.skillId(),profile.area().trap());
                if(!admission.equals("PASS"))return Validation.reject(admission);
                double reach=profile.area().placementRange()>0?profile.area().placementRange():Math.max(.5,profile.area().radius());
                if(feet.subtract(target.point()).horizontalLength()>reach+1e-6) return Validation.reject("COMMITTED_TARGET_OUT_OF_RANGE");
                if(!HytaleAreaQueries.clear(store,feet.add(new Vec3(0,1.35,0)),target.point().add(new Vec3(0,.1,0))))
                    return Validation.reject("COMMITTED_TARGET_LOS_BLOCKED");
                areaPlacement=target.point();areaDirection=target.direction();
                var shape=profile.area().footprint(areaPlacement,areaDirection,context.compiledPlan().executionModifiers().radiusFactor());
                if(profile.family()!=Stage04SkillProfile.Family.CONE) {
                    var grounded=areaWorld().prepareImpact(areaPlacement,shape);
                    if(grounded.isEmpty()||grounded.get().origin().distanceSquared(areaPlacement)>.0001)
                        return Validation.reject("COMMITTED_GROUND_CHANGED");
                }
                if(profile.area().overheadHeight()>0 && !areaWorld().overheadClear(shape,profile.area().overheadHeight()))
                    return Validation.reject("OVERHEAD_ROOF_BLOCKED");
                return areaWorld().query(shape,profile.area().candidateBudget()).overflow()?Validation.reject("AREA_CANDIDATE_BUDGET"):Validation.pass();
            }
            if(profile.projectile()!=null) {
                Vec3 muzzle=feet.add(new Vec3(0,1.35,0));Vec3 delta=target.point().subtract(muzzle);
                if(delta.length()>profile.projectile().maxDistance()*context.compiledPlan().projectileModifiers().distanceFactor()+.65+1e-6 || delta.length()<.66)
                    return Validation.reject("COMMITTED_TARGET_OUT_OF_RANGE");
                if(!HytaleAreaQueries.clear(store,muzzle,target.point()))return Validation.reject("COMMITTED_TARGET_LOS_BLOCKED");
                long owned=projectiles.values().stream().filter(p->p.actorId.equals(playerRef.getUuid())).count();
                return owned>=context.compiledPlan().safetyBudgets().maxLiveProjectiles()
                        ?Validation.reject("PROJECTILE_LIVE_BUDGET_EXCEEDED"):Validation.pass();
            }
            if(profile.movement()!=null) {
                if(feet.subtract(target.point()).horizontalLength()>profile.movement().maxDistance()+1e-6)
                    return Validation.reject("COMMITTED_TARGET_OUT_OF_RANGE");
                if(target.entityId()!=null) {
                    var ref=store.getExternalData().getRefFromUUID(target.entityId());
                    var selected=candidate(ref);
                    if(selected==null||selected.protectedTarget()||!HytaleAreaQueries.hostile(store,ref,actor))
                        return Validation.reject("COMMITTED_ENTITY_INVALID");
                }
                if(!HytaleAreaQueries.clear(store,feet.add(new Vec3(0,.1,0)),target.point().add(new Vec3(0,.1,0))))
                    return Validation.reject("COMMITTED_TARGET_LOS_BLOCKED");
                return Validation.pass();
            }
            if(profile.strike()!=null) {
                if(feet.subtract(target.origin()).horizontalLength()>profile.strike().range())return Validation.reject("COMMITTED_TARGET_OUT_OF_RANGE");
                return select(context,profile.strike()).accepted().isEmpty()?Validation.reject("COMMITTED_STRIKE_EMPTY"):Validation.pass();
            }
            return Validation.reject("COMMITTED_TARGET_FAMILY_UNAVAILABLE");
        }
        private boolean sameItem(Item current,Item prior) {
            return current!=null && prior!=null && current.itemId().equals(prior.itemId()) && current.weaponKind().equals(prior.weaponKind());
        }
        @Override public void abandonRelease(SkillExecutionContext context) {
            if(context.profile().projectile()!=null && context.derivedRelease())
                projectileService.registry().abandonLaunch(context.request().actorId(),context.rootCastId());
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
            if(context.target()!=null) {areaPlacement=context.target().point();areaDirection=context.target().direction();}
            if (areaPlacement == null || areaDirection == null) throw new IllegalStateException("AREA_PLACEMENT_NOT_VALIDATED");
            areas.start(context, areaPlacement, areaDirection, System.nanoTime() / 1_000_000_000.0,
                    context.compiledPlan().executionModifiers().radiusFactor(), areaWorld());
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
                    vfx.presentArea(store.getExternalData().getWorld(), shape, phase, context.profile().area().element(),
                            context.profile().area().trap(), seconds);
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
            if(context.target()!=null) {
                direction=context.target().point().subtract(origin);distance=Math.min(distance,direction.horizontalLength());
            } else if (context.profile().movement().kind() == Stage04SkillProfile.MovementKind.LEAP) {
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
            var authored=context.profile().projectile();
            HytaleAmmoAdapter.Token ammo=HytaleAmmoAdapter.Token.NONE;
            var instances=new ArrayList<ProjectileInstance>();var spawned=new ArrayList<ProjectileCarrier>();
            try {
                String weaponKind=context.equipment().mainHand().weaponKind(),configId=authored.configIdFor(weaponKind);
                double speed=authored.speedFor(weaponKind);
                Vec3 eye=vec(store.getComponent(actor,TransformComponent.getComponentType()).getPosition()).add(new Vec3(0,1.35,0));
                Vec3 direction=context.target()==null?aim(store,actor):context.target().point().subtract(eye).normalized();
                Vec3 origin=eye.add(direction.multiply(.65));
                var plans=projectileService.buildBatch(context,playerRef.getUuid(),origin,direction,configId,speed,System.nanoTime());
                for(var plan:plans) {
                    if(!HytaleAreaQueries.clear(store,eye,origin.add(plan.velocity().normalized().multiply(.03))))
                        throw new IllegalStateException("PROJECTILE_MUZZLE_BLOCKED");
                    instances.add(new ProjectileInstance(plan));
                }
                // The full batch and all future Barrage/Echo promises are admitted atomically before ammo/native allocation.
                projectileService.registry().registerAll(instances);
                emit(context,RpgTraceEventType.AMMO_CHECK,Map.of("required",authored.requiresAmmo(),"itemId",authored.ammoItemId(),
                        "quantity",authored.ammoQuantity(),"available",true,"derivedRelease",context.derivedRelease()));
                if(!context.derivedRelease())ammo=ammunition.consume(actor,store,authored);
                if(authored.requiresAmmo()&&!context.derivedRelease())emit(context,RpgTraceEventType.AMMO_COMMITTED,
                        Map.of("itemId",ammo.itemId(),"quantity",ammo.quantity(),"fullyCharged",authored.fullyCharged()));
                for(var instance:instances)spawned.add(spawnProjectileCarrier(context,actor,instance,buffer));
                vfx.present(store.getExternalData().getWorld(),player,context.compiledPlan().vfxRecipeId());
                return SkillExecutionResult.committed("PROJECTILE_STARTED",0,0);
            } catch(RuntimeException error) {
                for(var carrier:spawned) {
                    projectiles.remove(carrier.instance.plan().projectileInstanceId(),carrier);
                    if(carrier.projectile.isValid())buffer.tryRemoveEntity(carrier.projectile,RemoveReason.REMOVE);
                }
                for(var instance:instances)projectileService.onForwardTermination(instance,"SPAWN_REJECTED",instance.plan().origin());
                projectileService.registry().abandonLaunch(context.request().actorId(),context.rootCastId());
                if(ammo.quantity()>0)ammunition.refund(actor,store,ammo);
                if(authored.requiresAmmo())emit(context,RpgTraceEventType.AMMO_REJECTED,
                        Map.of("reason","PROJECTILE_DISPATCH_ROLLBACK","error",error.getClass().getSimpleName()));
                emit(context,RpgTraceEventType.PROJECTILE_SPAWN_REJECTED,Map.of("reason","ATOMIC_BATCH_ROLLBACK",
                        "error",error.getClass().getSimpleName(),"batchSize",instances.size(),"barrageBatch",context.barrageBatch()));
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
            Vec3 direction=facing(store,actor);Vec3 currentOrigin=origin;
            if(context!=null && context.target()!=null && context.profile().family()==Stage04SkillProfile.Family.STRIKE) {
                origin=context.target().origin();direction=context.target().direction();
            }
            if(context!=null && strike.geometry()==Stage04SkillProfile.Geometry.RADIUS) {
                strike=new Stage04SkillProfile.Strike(strike.geometry(),strike.range()*context.compiledPlan().executionModifiers().radiusFactor(),
                        strike.angleDegrees(),strike.lineHalfWidth(),strike.repeats(),strike.repeatIntervalSeconds(),strike.targetCap(),
                        strike.coefficient(),strike.statusId(),strike.statusSeconds());
            }
            List<StrikeGeometryService.Candidate<Ref<EntityStore>>> candidates = candidates(origin, strike.range());
            if(context!=null && context.target()!=null) {
                double reach=strike.range();
                candidates=candidates.stream().filter(c->c.position().subtract(currentOrigin).horizontalLength()<=reach)
                        .filter(c->HytaleAreaQueries.clear(store,currentOrigin.add(new Vec3(0,1.35,0)),c.position().add(new Vec3(0,.5,0)))).toList();
            }
            if (forcedTarget != null && forcedTarget.isValid())
                candidates = candidates.stream().filter(value -> value.handle().equals(forcedTarget)).toList();
            var result = geometry.query(origin, direction, strike, candidates);
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
            return damage(context,target,hitIndex,coefficient,criticalChance,cause,periodic,context.skillInstanceId(),!periodic);
        }
        private DamageOutcome damage(SkillExecutionContext context,
                StrikeGeometryService.Candidate<Ref<EntityStore>> target,int hitIndex,double coefficient,double criticalChance,
                DamageCause cause,boolean periodic,String effectId,boolean canProc) {
            double effective = effectiveAttribute(context);
            DamageCalculationService.Result result = kernel.damage().calculate(new DamageCalculationService.Request(
                    context.snapshot().basePower(), effective, coefficient,
                    context.snapshot().modifiers(), !periodic, criticalChance,
                    context.snapshot().criticalMultiplier()));
            CombatTrace.Context ids = ids(context);
            trace.emit(playerRef.getUuid(), RpgTraceEventType.DAMAGE_CALC_BEGIN, ids,
                    Map.of("basePower", context.snapshot().basePower(), "effectiveAttribute", effective,
                            "coefficient", coefficient, "hitIndex", hitIndex, "periodic", periodic,"effectInstanceId",effectId,"canProc",canProc));
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
                            context.request().correlationId(), result.preMitigationDamage(), Double.NaN,effectId,canProc), result);
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

    private void dispatchNativeProjectileImpact(ProjectileCarrier carrier,Ref<EntityStore> projectileRef,
            Vector3d position,Vector3i blockPosition,Ref<EntityStore> hitEntity,CommandBuffer<EntityStore> buffer) {
        // Keep the unmodified Stage 05 callback path. Modified carriers use native Pierce, whose
        // endCourse queues Despawn AFTER its callback and omits the terrain coordinates from its arguments.
        if(!hasContinuations(carrier.context)) {onProjectileImpact(carrier,projectileRef,position,blockPosition,hitEntity,buffer,Vec3.ZERO);return;}
        var physics=buffer.getComponent(projectileRef,StandardPhysicsProvider.getComponentType());
        boolean courseEnd=hitEntity==null&&blockPosition==null;
        Vector3i nativeBlock=courseEnd&&physics!=null&&physics.isBounced()?physics.bounceBlockPosition():blockPosition;
        Vector3i savedBlock=nativeBlock==null?null:new Vector3i(nativeBlock);
        Vector3d savedPosition=position==null?null:new Vector3d(position);
        Vec3 savedNormal=physics==null?Vec3.ZERO:vec(physics.getContactNormal());
        int revision=carrier.instance.motionRevision();
        // CommandBuffer.run appends FIFO; the second enqueue executes after endCourse's queued writes.
        // No replacement entity and no native despawn cancellation on an unproven/non-terrain ending.
        buffer.run(ignored->buffer.run(afterNative->{
            if(projectiles.get(carrier.instance.plan().projectileInstanceId())!=carrier || !projectileRef.isValid()
                    || carrier.instance.motionRevision()!=revision)return;
            if(courseEnd&&savedBlock!=null)buffer.tryRemoveComponent(projectileRef,
                    com.hypixel.hytale.server.core.modules.entity.DespawnComponent.getComponentType());
            onProjectileImpact(carrier,projectileRef,savedPosition,savedBlock,hitEntity,buffer,savedNormal);
        }));
    }

    private void onProjectileImpact(ProjectileCarrier expected, Ref<EntityStore> projectileRef,
                                    Vector3d position, Vector3i blockPosition, Ref<EntityStore> hitEntity,
                                    CommandBuffer<EntityStore> buffer,Vec3 terrainNormal) {
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
        if(position==null) {cancelProjectile(carrier,"NATIVE_IMPACT_POSITION_MISSING",null,buffer);return;}
        // endCourse() invokes the callback with neither entity nor block, then schedules native despawn.
        // It is cancellation, not a terrain contact that may be resurrected by Return.
        if(hitEntity==null && blockPosition==null) {cancelProjectile(carrier,"NATIVE_COURSE_ENDED",vec(position),buffer);return;}
        if(!port.actorAliveAndUsable()) {cancelProjectile(carrier,"ACTOR_NOT_ALIVE",vec(position),buffer);return;}
        if(hasContinuations(carrier.context) && expireContinuationClock(carrier,buffer))return;
        if(carrier.instance.returning()) {
            Vec3 from=carrier.instance.flight().lastPosition(),end=ProjectileSweep.limit(from,vec(position),carrier.instance.remainingDistance());
            var caught=ProjectileSweep.catchFraction(from,end,casterPoint(carrier,store));
            if(caught.isPresent()) {
                end=from.add(end.subtract(from).multiply(caught.getAsDouble()));
                if(hitEntity!=null || !processSweptContacts(carrier,end,buffer))finishProjectileTerminal(carrier,"RETURN_CAUGHT",end,buffer);
                return;
            }
        }
        if(hasContinuations(carrier.context) && hitEntity==null && processSweptContacts(carrier,vec(position),buffer)) return;
        if(hasContinuations(carrier.context) && carrier.instance.flight().lastPosition().subtract(vec(position)).length()
                >carrier.instance.remainingDistance()+1e-6) {
            Vec3 from=carrier.instance.flight().lastPosition();Vec3 end=from.add(vec(position).subtract(from).normalized().multiply(carrier.instance.remainingDistance()));
            if(processSweptContacts(carrier,end,buffer))return;
            carrier.instance.observe(0,end);
            applyContinuation(carrier,continuations.forwardEnd(carrier.instance,end,casterPoint(carrier,store),"MAX_RANGE"),end,buffer);return;
        }
        if (hitEntity != null && hitEntity.isValid()) {
            if(hasContinuations(carrier.context) && !HytaleAreaQueries.clear(store,carrier.instance.flight().lastPosition(),vec(position))) {
                cancelProjectile(carrier,"OCCLUDED_NATIVE_CONTACT",vec(position),buffer);return;
            }
            StrikeGeometryService.Candidate<Ref<EntityStore>> target = port.candidate(hitEntity);
            if (target == null || target.protectedTarget() || !HytaleAreaQueries.hostile(store,hitEntity,carrier.actor)) {
                emitProjectile(carrier, RpgTraceEventType.PROJECTILE_TARGET_REJECTED,
                        Map.of("target", hitEntity.toString(), "reason",
                                target == null ? "INVALID_OR_NON_DAMAGEABLE" : "PROTECTED_OR_NON_HOSTILE_TARGET"));
                terminateProjectile(carrier, "TARGET_REJECTED", vec(position), buffer);
                return;
            }
            if (!projectileService.onEnemyContact(carrier.instance, target.stableId())) {
                emitProjectile(carrier, RpgTraceEventType.PROJECTILE_TARGET_DEDUP,
                        Map.of("targetId", target.stableId(), "reason", "ALREADY_HIT"));
                return;
            }
            boolean continues=hasContinuations(carrier.context);
            if(!continues) {
                projectiles.remove(projectileId, carrier);
                if (projectileRef != null && projectileRef.isValid()) buffer.tryRemoveEntity(projectileRef, RemoveReason.REMOVE);
            }
            try {
                carrier.instance.observe(0,vec(position));
                Stage04SkillProfile.Projectile authored = carrier.context.profile().projectile();
                DamageOutcome outcome = port.damage(carrier.context, target, 0, authored.coefficient(),
                        carrier.context.snapshot().criticalChance(), DamageCause.PROJECTILE,false,projectileId,true);
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
                applyShrapnel(carrier,port,vec(position),outcome.actualHealthLoss());
                if(continues) {
                    var decision=continuations.afterEnemy(carrier.instance,vec(position),casterPoint(carrier,store),
                            chainCandidates(carrier,vec(position),port),System.nanoTime());
                    applyContinuation(carrier,decision,vec(position),buffer);return;
                }
                projectileService.onForwardTermination(carrier.instance, "ENTITY_HIT", vec(position));
                emitProjectile(carrier, RpgTraceEventType.PROJECTILE_TERMINATED,
                        Map.of("reason", "ENTITY_HIT", "travelledDistance", carrier.instance.flight().travelled()));
                executions.terminate(carrier.context, "PROJECTILE_ENTITY_HIT");
            } catch (RuntimeException error) {
                projectiles.remove(projectileId,carrier);
                if(projectileRef!=null && projectileRef.isValid())buffer.tryRemoveEntity(projectileRef,RemoveReason.REMOVE);
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
        if(hasContinuations(carrier.context)) {
            carrier.instance.observe(0,vec(position));
            applyContinuation(carrier,continuations.afterTerrain(carrier.instance,vec(position),casterPoint(carrier,store),terrainNormal),vec(position),buffer);
            return;
        }
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
            if(hasContinuations(carrier.context)) {advanceModifiedProjectile(carrier,vec(transform.getPosition()),buffer);continue;}
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
        // Drop promises/registry ownership before any native teardown can throw or queue on another world.
        projectileService.cancelOwner(actorId,"OWNER_CANCELLED");
        for (ProjectileCarrier carrier : owned) {
            if (!projectiles.remove(carrier.instance.plan().projectileInstanceId(), carrier)) continue;
            projectileService.onForwardTermination(carrier.instance, "OWNER_CANCELLED", carrier.instance.flight().lastPosition());
            if (carrier.projectile.isValid()) {
                if (buffer != null) buffer.tryRemoveEntity(carrier.projectile, RemoveReason.REMOVE);
                else {
                    var projectileStore=carrier.projectile.getStore();
                    Runnable remove=()-> {if(carrier.projectile.isValid())projectileStore.removeEntity(carrier.projectile,RemoveReason.REMOVE);};
                    if(projectileStore.isInThread())remove.run();
                    else projectileStore.getExternalData().getWorld().execute(remove);
                }
            }
            emitProjectile(carrier, RpgTraceEventType.PROJECTILE_CANCELLED,
                    Map.of("reason", "OWNER_CANCELLED", "travelledDistance", carrier.instance.flight().travelled()));
            emitProjectile(carrier, RpgTraceEventType.PROJECTILE_TERMINATED,
                    Map.of("reason", "OWNER_CANCELLED", "travelledDistance", carrier.instance.flight().travelled()));
        }
    }

    private static boolean hasContinuations(SkillExecutionContext context) {
        var modifiers=context.compiledPlan().projectileModifiers();
        return modifiers.pierce()+modifiers.fork()+modifiers.chain()+modifiers.returning()+modifiers.ricochet()>0
                || modifiers.homing() || modifiers.shrapnel() || modifiers.splinterburst() || modifiers.accelerant() || modifiers.ballistics();
    }
    private boolean expireContinuationClock(ProjectileCarrier carrier,CommandBuffer<EntityStore> buffer) {
        var observation=carrier.instance.sampleNativeClock(System.nanoTime());
        if(!observation.expired())return false;
        String reason=carrier.instance.remainingDistance()<=1e-6?"MAX_RANGE":"MAX_LIFETIME";
        applyContinuation(carrier,continuations.forwardEnd(carrier.instance,observation.position(),casterPoint(carrier,buffer.getStore()),reason),
                observation.position(),buffer);
        return true;
    }
    private void advanceModifiedProjectile(ProjectileCarrier carrier,Vec3 nativePosition,CommandBuffer<EntityStore> buffer) {
        try {
            if(expireContinuationClock(carrier,buffer))return;
            Vec3 from=carrier.instance.flight().lastPosition();
            Vec3 position=ProjectileSweep.limit(from,nativePosition,carrier.instance.remainingDistance());
            Vec3 caster=casterPoint(carrier,buffer.getStore());
            var caught=carrier.instance.returning()?ProjectileSweep.catchFraction(from,position,caster):java.util.OptionalDouble.empty();
            if(caught.isPresent())position=from.add(position.subtract(from).multiply(caught.getAsDouble()));
            if(processSweptContacts(carrier,position,buffer))return;
            var observation=carrier.instance.observe(0,position);
            if(caught.isPresent()) {finishProjectileTerminal(carrier,"RETURN_CAUGHT",position,buffer);return;}
            if(observation.expired()) {
                applyContinuation(carrier,continuations.forwardEnd(carrier.instance,position,caster,"MAX_RANGE"),position,buffer);return;
            }
            if(carrier.instance.returning()) {
                carrier.instance.redirect(caster.subtract(position));resumeCarrier(carrier,position,buffer,false);
            } else if(carrier.context.compiledPlan().projectileModifiers().homing()) {
                steerHoming(carrier,position,buffer);
            }
        } catch(RuntimeException error) {
            cancelProjectile(carrier,"CONTINUATION_TICK_FAILURE_"+error.getClass().getSimpleName(),carrier.instance.flight().lastPosition(),buffer);
        }
    }
    /** The native Pierce provider only reports contact[0]; visit the remaining swept bounds without skipping a body. */
    private boolean processSweptContacts(ProjectileCarrier carrier,Vec3 destination,CommandBuffer<EntityStore> buffer) {
        var instance=carrier.instance;Vec3 from=instance.flight().lastPosition(),delta=destination.subtract(from);
        if(delta.lengthSquared()<1e-12)return false;
        double distance=Math.min(delta.length(),instance.remainingDistance());Vec3 end=from.add(delta.normalized().multiply(distance));
        var box=buffer.getComponent(carrier.projectile,BoundingBox.getComponentType());
        if(box==null) {cancelProjectile(carrier,"SWEEP_BOUNDS_MISSING",from,buffer);return true;}
        var local=new AreaGeometry.Bounds(vec(box.getBoundingBox().min),vec(box.getBoundingBox().max));
        double extent=Math.max(local.min().length(),local.max().length());Vec3 middle=from.add(end).multiply(.5);
        double span=distance/2+extent;
        var shape=new AreaGeometry(AreaGeometry.Kind.DISC,middle.add(new Vec3(0,-span,0)),Vec3.FORWARD,span,360,0,0,span*2);
        var found=HytaleAreaQueries.query(buffer.getStore(),carrier.actor,shape,64);
        if(found.overflow()){cancelProjectile(carrier,"SWEEP_CANDIDATE_BUDGET",from,buffer);return true;}
        record Contact(Ref<EntityStore> ref,String id,double fraction) { }
        var contacts=new ArrayList<Contact>();
        for(var target:found.candidates()) {
            var uuid=buffer.getStore().getComponent(target.ref(),UUIDComponent.getComponentType());
            if(uuid==null || instance.previouslyHit(uuid.getUuid().toString()))continue;
            var fraction=ProjectileSweep.contact(from,end,local,target.bounds());
            if(fraction.isPresent())contacts.add(new Contact(target.ref(),uuid.getUuid().toString(),fraction.getAsDouble()));
        }
        contacts.sort(java.util.Comparator.comparingDouble(Contact::fraction).thenComparing(Contact::id));
        for(var contact:contacts) {
            Vec3 point=from.add(end.subtract(from).multiply(contact.fraction()));
            if(!HytaleAreaQueries.clear(buffer.getStore(),from,point))break;
            int revision=instance.motionRevision();
            onProjectileImpact(carrier,carrier.projectile,vector(point),null,contact.ref(),buffer,Vec3.ZERO);
            if(projectiles.get(instance.plan().projectileInstanceId())!=carrier||instance.motionRevision()!=revision)return true;
        }
        return false;
    }
    private Vec3 casterPoint(ProjectileCarrier carrier,Store<EntityStore> store) {
        return vec(store.getComponent(carrier.actor,TransformComponent.getComponentType()).getPosition()).add(new Vec3(0,1.35,0));
    }
    private void steerHoming(ProjectileCarrier carrier,Vec3 position,CommandBuffer<EntityStore> buffer) {
        var store=buffer.getStore();
        java.util.function.Function<String,java.util.Optional<com.inigmasgames.hytalerpg.execution.projectile.ProjectileHoming.Target>> live=id->{
            var ref=store.getExternalData().getRefFromUUID(UUID.fromString(id));
            if(ref==null||!ref.isValid()||!HytaleAreaQueries.hostile(store,ref,carrier.actor))return java.util.Optional.empty();
            var targetStats=store.getComponent(ref,EntityStatMap.getComponentType());
            var transform=store.getComponent(ref,TransformComponent.getComponentType());var bounds=store.getComponent(ref,BoundingBox.getComponentType());
            if(targetStats==null||health(targetStats)<=0||transform==null||bounds==null||store.getComponent(ref,Invulnerable.getComponentType())!=null)
                return java.util.Optional.empty();
            var npc=store.getComponent(ref,NPCEntity.getComponentType());var effects=store.getComponent(ref,EffectControllerComponent.getComponentType());
            if(npc==null||npc.getRole()!=null&&npc.getRole().isInvulnerable()||effects!=null&&effects.isInvulnerable())return java.util.Optional.empty();
            Vec3 center=vec(transform.getPosition()).add(vec(bounds.getBoundingBox().min).add(vec(bounds.getBoundingBox().max)).multiply(.5));
            return java.util.Optional.of(new com.inigmasgames.hytalerpg.execution.projectile.ProjectileHoming.Target(id,center,HytaleAreaQueries.clear(store,position,center)));
        };
        var update=carrier.instance.homing().update(carrier.instance.totalSeconds(),position,carrier.instance.direction(),()->{
            var shape=new AreaGeometry(AreaGeometry.Kind.DISC,position.add(new Vec3(0,-8,0)),Vec3.FORWARD,8,360,0,0,16);
            var found=HytaleAreaQueries.query(store,carrier.actor,shape,64);
            if(found.overflow())throw new IllegalStateException("HOMING_CANDIDATE_BUDGET");
            var choices=new ArrayList<com.inigmasgames.hytalerpg.execution.projectile.ProjectileHoming.Target>();
            for(var candidate:found.candidates()) {
                var uuid=store.getComponent(candidate.ref(),UUIDComponent.getComponentType());
                if(uuid!=null&&!carrier.instance.previouslyHit(uuid.getUuid().toString()))live.apply(uuid.getUuid().toString()).ifPresent(choices::add);
            }
            return choices;
        },live);
        if(update.direction().distanceSquared(carrier.instance.direction())>1e-12) {
            carrier.instance.redirect(update.direction());resumeCarrier(carrier,position,buffer,false);
        }
        if(update.reacquired())emitProjectile(carrier,RpgTraceEventType.PROJECTILE_HOMING_QUERY,
                Map.of("reason","HOMING_ACQUISITION","targetId",update.targetId()==null?"NONE":update.targetId(),"intervalSeconds",.10,"turnCapDegreesPerSecond",120));
    }

    private ProjectileCarrier spawnProjectileCarrier(SkillExecutionContext context,Ref<EntityStore> actor,ProjectileInstance instance,
            CommandBuffer<EntityStore> buffer) {
        context=context.withSnapshot(instance.plan().snapshot());
        var plan=instance.plan();var config=ProjectileConfig.getAssetMap().getAsset(plan.configId());
        if(config==null)throw new IllegalStateException("PROJECTILE_CONFIG_MISSING");
        emit(context,RpgTraceEventType.PROJECTILE_SPAWN_REQUEST,Map.of("projectileInstanceId",plan.projectileInstanceId(),"skillId",plan.skillId(),
                "generation",plan.generation(),"caster",plan.ownerId().toString(),"compiledPlanHash",plan.compiledPlanHash(),"configId",plan.configId(),
                "originX",plan.origin().x(),"originY",plan.origin().y(),"originZ",plan.origin().z()));
        Ref<EntityStore> ref=null;ProjectileCarrier carrier=null;
        try {
            ref=ProjectileModule.get().spawnProjectile(actor,buffer,config,vector(plan.origin()),vector(instance.direction()));
            carrier=new ProjectileCarrier(context,actor,plan.ownerId(),ref,instance);
            var physics=buffer.getComponent(ref,StandardPhysicsProvider.getComponentType());
            var velocity=buffer.getComponent(ref,com.hypixel.hytale.server.core.modules.physics.component.Velocity.getComponentType());
            if(physics==null||velocity==null)throw new IllegalStateException("PROJECTILE_PHYSICS_MISSING");
            physics.getVelocity().set(vector(plan.velocity()));velocity.set(physics.getVelocity());
            projectiles.put(plan.projectileInstanceId(),carrier);configureContinuationCarrier(carrier,buffer);
            var bound=carrier;
            physics.setImpactConsumer((p,position,block,entity,interaction,commands)->dispatchNativeProjectileImpact(bound,p,position,block,entity,commands));
            emitProjectile(carrier,RpgTraceEventType.PROJECTILE_SPAWNED,Map.of("configId",plan.configId(),"speed",plan.velocity().length(),
                    "maxDistance",plan.maxDistance(),"maximumLifetimeSeconds",plan.maxLifetimeSeconds(),"radius",plan.radius(),
                    "nativeProjectileRef",ref.toString(),"barrageBatch",context.barrageBatch()));
            return carrier;
        } catch(RuntimeException error) {
            if(carrier!=null)projectiles.remove(plan.projectileInstanceId(),carrier);
            if(ref!=null&&ref.isValid())buffer.tryRemoveEntity(ref,RemoveReason.REMOVE);
            throw error;
        }
    }
    private List<ProjectileContinuation.Candidate> chainCandidates(ProjectileCarrier carrier,Vec3 point,Port port) {
        if(carrier.instance.returning()||carrier.instance.remaining("CHAIN")==0)return List.of();
        var shape=new AreaGeometry(AreaGeometry.Kind.DISC,point.add(new Vec3(0,-8,0)),Vec3.FORWARD,8,360,0,0,16);
        var found=HytaleAreaQueries.query(port.store,carrier.actor,shape,64);
        if(found.overflow()) {
            emitProjectile(carrier,RpgTraceEventType.PROJECTILE_TARGET_REJECTED,Map.of("reason","CHAIN_CANDIDATE_BUDGET"));return List.of();
        }
        var choices=new ArrayList<ProjectileContinuation.Candidate>();
        for(var value:found.candidates()) {
            var target=port.candidate(value.ref());if(target==null||target.protectedTarget())continue;
            Vec3 center=value.bounds().centre();
            choices.add(new ProjectileContinuation.Candidate(target.stableId(),center,HytaleAreaQueries.clear(port.store,point,center)));
        }
        return List.copyOf(choices);
    }
    private void configureContinuationCarrier(ProjectileCarrier carrier,CommandBuffer<EntityStore> buffer) {
        if(!hasContinuations(carrier.context))return;
        var instance=carrier.instance;var plan=instance.plan();
        // Native Pierce prevents a just-hit body from stopping the carrier. RPG owns the exact leg budgets.
        double range=instance.originalMaxDistance()*2+2;
        var component=new PierceProjectile(vector(instance.flight().lastPosition()),range,(float)(range/plan.velocity().length()+1));
        for(String target:instance.hitTargets()) {
            Ref<EntityStore> ref=buffer.getStore().getExternalData().getRefFromUUID(UUID.fromString(target));
            if(ref!=null && ref.isValid())component.markHit(ref);
        }
        buffer.putComponent(carrier.projectile,PierceProjectile.getComponentType(),component);
    }
    private void resumeCarrier(ProjectileCarrier carrier,Vec3 point,CommandBuffer<EntityStore> buffer,boolean resetHitPhase) {
        var physics=buffer.getComponent(carrier.projectile,StandardPhysicsProvider.getComponentType());
        if(physics==null)throw new IllegalStateException("CONTINUATION_PHYSICS_MISSING");
        physics.getPosition().set(vector(point));physics.getVelocity().set(vector(carrier.instance.direction().multiply(carrier.instance.plan().velocity().length())));
        physics.setState(StandardPhysicsProvider.STATE.ACTIVE);
        var transform=buffer.getComponent(carrier.projectile,TransformComponent.getComponentType());
        var velocity=buffer.getComponent(carrier.projectile,com.hypixel.hytale.server.core.modules.physics.component.Velocity.getComponentType());
        if(transform==null||velocity==null)throw new IllegalStateException("CONTINUATION_TRANSFORM_MISSING");
        transform.setPosition(physics.getPosition());velocity.set(physics.getVelocity());
        if(resetHitPhase)buffer.putComponent(carrier.projectile,PierceProjectile.getComponentType(),
                new PierceProjectile(vector(point),carrier.instance.originalMaxDistance()+1,
                        (float)(carrier.instance.originalMaxDistance()/carrier.instance.plan().velocity().length()+.5)));
    }
    private void applyContinuation(ProjectileCarrier carrier,ProjectileContinuation.Decision decision,Vec3 point,
            CommandBuffer<EntityStore> buffer) {
        var action=decision.action();
        if(action==ProjectileContinuation.Action.TERMINATE) {finishProjectileTerminal(carrier,decision.reason(),point,buffer);return;}
        if(action!=ProjectileContinuation.Action.RETURN_CONTINUE)
            emitProjectile(carrier,RpgTraceEventType.valueOf(action.name()),Map.of("reason",decision.reason(),
                    "remaining",carrier.instance.budgets(),"totalTravelled",carrier.instance.totalDistance(),"nativeMotionVerified",false));
        if(action==ProjectileContinuation.Action.FORK || action==ProjectileContinuation.Action.SPLINTERBURST) {
            var spawned=new ArrayList<ProjectileCarrier>();
            try {
                for(var child:decision.children()) {
                    spawned.add(spawnProjectileCarrier(carrier.context,carrier.actor,child,buffer));
                }
            } catch(RuntimeException error) {
                for(var next:spawned) {
                    projectiles.remove(next.instance.plan().projectileInstanceId(),next);
                    if(next.projectile.isValid())buffer.tryRemoveEntity(next.projectile,RemoveReason.REMOVE);
                }
                for(var child:decision.children())projectileService.onForwardTermination(child,"CHILD_SPAWN_FAILED",point);
                emitProjectile(carrier,RpgTraceEventType.PROJECTILE_SPAWN_REJECTED,Map.of("reason",action.name()+"_BATCH_FAILED","error",error.getClass().getSimpleName()));
            } finally {terminateProjectile(carrier,action.name()+"_PARENT_CONSUMED",point,buffer);}
        } else if(action==ProjectileContinuation.Action.CHAIN || action==ProjectileContinuation.Action.RETURN || action==ProjectileContinuation.Action.RICOCHET) {
            Vec3 offset=point.add((action==ProjectileContinuation.Action.RICOCHET?decision.surfaceNormal():decision.direction()).multiply(.03));
            if(!HytaleAreaQueries.clear(buffer.getStore(),point,offset)) {terminateProjectile(carrier,"CONTINUATION_OFFSET_BLOCKED",point,buffer);return;}
            resumeCarrier(carrier,offset,buffer,action==ProjectileContinuation.Action.RETURN);
        }
    }

    /** Only real terminal causes may spawn secondary carriers. Cancellation/failure paths call cleanup directly. */
    private void finishProjectileTerminal(ProjectileCarrier carrier,String reason,Vec3 point,CommandBuffer<EntityStore> buffer) {
        var cause=switch(reason) {
            case "MAX_RANGE","FORWARD_BUDGET_EXHAUSTED" -> ProjectileSecondaryEffects.TerminalCause.RANGE;
            case "MAX_LIFETIME" -> ProjectileSecondaryEffects.TerminalCause.LIFETIME;
            case "ENEMY_CONTINUATION_EXHAUSTED" -> ProjectileSecondaryEffects.TerminalCause.ENEMY;
            case "TERRAIN_CONTINUATION_EXHAUSTED" -> ProjectileSecondaryEffects.TerminalCause.TERRAIN;
            case "RETURN_CAUGHT" -> ProjectileSecondaryEffects.TerminalCause.RETURN_CAUGHT;
            default -> null;
        };
        if(cause!=null && carrier.instance.remaining("SPLINTERBURST")>0) {
            var decision=projectileSecondaries.terminal(carrier.instance,point,cause,System.nanoTime());
            if(decision.action()==ProjectileContinuation.Action.SPLINTERBURST) {applyContinuation(carrier,decision,point,buffer);return;}
            emitProjectile(carrier,RpgTraceEventType.PROJECTILE_SPAWN_REJECTED,
                    Map.of("reason",decision.reason(),"component","SPLINTERBURST"));
        }
        terminateProjectile(carrier,reason,point,buffer);
    }
    private void applyShrapnel(ProjectileCarrier carrier,Port port,Vec3 point,double actualHealthLoss) {
        var claim=projectileSecondaries.afterDamage(carrier.instance,carrier.context.compiledPlan(),point,actualHealthLoss);
        if(claim.burst().isEmpty()) {
            if(!claim.reason().equals("NO_DAMAGING_TRIGGER"))emitProjectile(carrier,RpgTraceEventType.PROJECTILE_SPAWN_REJECTED,
                    Map.of("component","SHRAPNEL","reason",claim.reason()));
            return;
        }
        var burst=claim.burst().get();var found=HytaleAreaQueries.query(port.store,carrier.actor,burst.geometry(),64);
        if(found.overflow()) {emitProjectile(carrier,RpgTraceEventType.AREA_QUERY_REJECTED,
                Map.of("component","SHRAPNEL","effectInstanceId",burst.id(),"reason","CANDIDATE_BUDGET"));return;}
        var context=carrier.context.withSnapshot(carrier.context.snapshot().withMagnitudeFactor(burst.coefficientFactor()));
        emitProjectile(carrier,RpgTraceEventType.SHRAPNEL,Map.of("effectInstanceId",burst.id(),"radius",burst.geometry().radius(),
                "height",burst.geometry().height(),"coefficientFactor",burst.coefficientFactor(),"canProc",burst.canProc(),"generation",carrier.instance.plan().generation()+1));
        // Reuse the finite geometry template at impact height; presentation never decides a hit.
        String element=List.of("FIRE","COLD","EARTH","POISON","NATURE","VOID","PHYSICAL").stream()
                .filter(carrier.context.compiledPlan().finalTags()::contains).findFirst().orElse("ARCANE");
        try {vfx.presentArea(port.store.getExternalData().getWorld(),burst.geometry().at(point,burst.geometry().radius()),
                "IMPACT_SHRAPNEL",element,false,.25);}catch(RuntimeException ignored){ }
        int hitIndex=0;
        var ordered=new ArrayList<>(found.candidates());ordered.sort(java.util.Comparator.comparing(value->
                port.store.getComponent(value.ref(),UUIDComponent.getComponentType()).getUuid().toString()));
        for(var value:ordered) {
            var target=port.candidate(value.ref());
            if(target==null||target.protectedTarget()||!HytaleAreaQueries.hostile(port.store,value.ref(),carrier.actor)
                    ||!HytaleAreaQueries.clear(port.store,point,value.bounds().centre())||!burst.acceptTarget(target.stableId()))continue;
            var authored=context.profile().projectile();
            var outcome=port.damage(context,target,hitIndex++,authored.coefficient(),context.snapshot().criticalChance(),
                    DamageCause.PROJECTILE,false,burst.id(),false);
            String status="NONE";
            if(outcome.actualHealthLoss()>0 && !authored.statusId().isBlank())status=authored.hasPeriodicStatus()
                    ?(port.applyBurn(context,target)?"BURN_APPLIED":"BURN_REJECTED"):port.applyProjectileStatus(context,target);
            double knockback=outcome.actualHealthLoss()>0?port.applyProjectileKnockback(context,target):0;
            emitProjectile(carrier,RpgTraceEventType.AREA_HIT,Map.of("component","SHRAPNEL","effectInstanceId",burst.id(),
                    "targetId",target.stableId(),"actualHealthLoss",outcome.actualHealthLoss(),"canProc",false,
                    "statusResult",status,"appliedKnockback",knockback));
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
