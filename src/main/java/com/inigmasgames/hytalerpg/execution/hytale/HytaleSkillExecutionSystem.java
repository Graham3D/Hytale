package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.execution.ConditionalRepeatRuntime;
import com.inigmasgames.hytalerpg.execution.ConditionalKillTargeting;
import com.inigmasgames.hytalerpg.execution.HitProcRuntime;
import com.inigmasgames.hytalerpg.execution.HitProcProfiles;
import com.inigmasgames.hytalerpg.execution.ChillSourceRegistry;
import com.inigmasgames.hytalerpg.execution.ProliferationRuntime;
import com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy;

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
import com.inigmasgames.hytalerpg.execution.OwnedFieldBudget;
import com.inigmasgames.hytalerpg.execution.connection.ConnectionRuntime;
import com.inigmasgames.hytalerpg.execution.connection.ConnectionShape;
import com.inigmasgames.hytalerpg.execution.connection.ConnectionWorldPort;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.execution.projectile.ProjectileSweep;
import com.hypixel.hytale.server.core.modules.projectile.component.PierceProjectile;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider;
import com.inigmasgames.hytalerpg.execution.projectile.ProjectileLifecycleRegistry;
import com.inigmasgames.hytalerpg.execution.projectile.RpgProjectileService;
import com.inigmasgames.hytalerpg.execution.projectile.ProjectileBallistics;
import com.inigmasgames.hytalerpg.execution.projectile.ProjectileDetonation;
import com.inigmasgames.hytalerpg.execution.projectile.ProjectileLaunchQueue;
import com.inigmasgames.hytalerpg.execution.reaction.ReactionWindowService;
import com.inigmasgames.hytalerpg.execution.strike.SkillHitLedger;
import com.inigmasgames.hytalerpg.execution.strike.StrikeGeometryService;
import com.inigmasgames.hytalerpg.execution.strike.StrikeRepeatSchedule;
import com.inigmasgames.hytalerpg.execution.strike.StrikeSecondaryRuntime;
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
    private final ProjectileLaunchQueue<QueuedProjectile> projectileLaunches=new ProjectileLaunchQueue<>();
    private final Map<String,GroundedProjectile> projectileFuses=new java.util.concurrent.ConcurrentHashMap<>();
    private final RpgProjectileService projectileService =
            new RpgProjectileService(new ProjectileLifecycleRegistry());
    private final ProjectileContinuation continuations=new ProjectileContinuation(projectileService.registry());
    private final ProjectileSecondaryEffects projectileSecondaries=new ProjectileSecondaryEffects(projectileService.registry());
    private final PeriodicStatusRuntime<SkillExecutionContext, PeriodicTarget> periodicStatuses = new PeriodicStatusRuntime<>();
    private final HitProcRuntime hitProcs=new HitProcRuntime();
    private final ChillSourceRegistry chillSources=new ChillSourceRegistry();
    private final ProliferationRuntime proliferation=new ProliferationRuntime();
    private final OwnedFieldBudget fieldCapacity=new OwnedFieldBudget();
    private final AreaRuntime areas = new AreaRuntime(fieldCapacity);
    private final ConnectionRuntime connections=new ConnectionRuntime(fieldCapacity);
    private HytaleSupportSystem support;
    private HytaleSummonSystem summons;
    private HytaleConversionSystem conversions;
    public HytaleConversionSystem configureConversions(){
        if(conversions!=null)throw new IllegalStateException("Conversions already configured");
        conversions=new HytaleConversionSystem(trace,bosses,vfx);return conversions;
    }
    public HytaleSummonSystem configureSummons(java.nio.file.Path consumptionDirectory){
        if(summons!=null)throw new IllegalStateException("Summons already configured");
        summons=new HytaleSummonSystem(trace,bosses,(store,buffer,owner,target,lease,attack)->{
            var playerRef=store.getComponent(owner,PlayerRef.getComponentType());
            var port=new Port(store,owner,playerRef,store.getComponent(owner,Player.getComponentType()),
                    store.getComponent(owner,EntityStatMap.getComponentType()),null,buffer);
            var candidate=port.candidate(target);var context=lease.context();
            if(candidate==null||candidate.protectedTarget()||!HytaleAreaQueries.hostile(store,target,owner))return;
            var result=port.damage(context,candidate,attack,lease.coefficient(),context.snapshot().criticalChance(),
                    connectionCause(context.profile().summon().element()),false,lease.token()+"/attack/"+attack,false,true);
            summons.emit(lease,RpgTraceEventType.SUMMON_ATTACK,Map.of("entity",lease.entity(),"target",candidate.stableId(),
                    "attack",attack,"actualHealthLoss",result.actualHealthLoss(),"cancelled",result.cancelled()));
        },new com.inigmasgames.hytalerpg.execution.summon.CorpseLedger(
                new com.inigmasgames.hytalerpg.execution.summon.FileCorpseConsumptionStore(consumptionDirectory)),
                (store,buffer,owner,context)->{
                    support.runtime().finite().consumeMinion(context,System.nanoTime()/1e9);
                    trace.emit(context.request().actorId(),RpgTraceEventType.FINITE_SUPPORT_APPLIED,ids(context),
                            Map.of("kind","CONSUME_MINION","seconds",context.profile().summonAction().duration(),"damageIncreased",context.profile().summonAction().damageIncreased(),
                                    "shieldCreated",context.snapshot().derivedStats().maxHealth()*context.profile().summonAction().shieldFraction()));
                },(store,buffer,owner,context,point,radius,coefficient,effect,frozen)->{
                    if(!HytaleSummonSystem.alive(store,owner))throw new IllegalStateException("BURST_OWNER_UNAVAILABLE");
                    var player=store.getComponent(owner,PlayerRef.getComponentType());
                    if(!context.target().worldId().equals(player.getWorldUuid()))throw new IllegalStateException("BURST_WORLD_CHANGED");
                    var port=new Port(store,owner,player,store.getComponent(owner,Player.getComponentType()),store.getComponent(owner,EntityStatMap.getComponentType()),null,buffer);
                    return port.summonBurst(context,point,radius,coefficient,effect,frozen);
                });return summons;
    }
    public HytaleSupportSystem configureSupport(com.inigmasgames.hytalerpg.progress.RpgLoadoutService loadouts){
        if(support!=null)throw new IllegalStateException("Support already configured");
        support=new HytaleSupportSystem(loadouts,kernel,fieldCapacity,trace,vfx,bosses,this::auraPayload);return support;
    }
    /** Aura pulses use the same calculation/native damage boundary as the other families. */
    private void observeControl(Store<EntityStore> store,Ref<EntityStore> target,SkillExecutionContext context,Runnable operation){
        if(support==null)operation.run();else support.observeControl(store,target,context,operation);
    }
    private void auraPayload(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> actor,SkillExecutionContext context,List<UUID> targets,int tick,boolean chill){
        var port=new Port(store,actor,store.getComponent(actor,PlayerRef.getComponentType()),store.getComponent(actor,Player.getComponentType()),
                store.getComponent(actor,EntityStatMap.getComponentType()),null,null);
        for(var id:targets){
            var ref=store.getExternalData().getRefFromUUID(id);var target=port.candidate(ref);
            if(target==null||target.protectedTarget()||!HytaleAreaQueries.hostile(store,ref,actor)
                    ||!HytaleSupportSystem.auraInRange(store,actor,ref,com.inigmasgames.hytalerpg.execution.support.SupportRuntime.radius(context)))continue;
            if(chill){
                if(buffer==null||store.getComponent(ref,EffectControllerComponent.getComponentType())==null)throw new IllegalStateException("AURA_NATIVE_STATUS_ADAPTER_UNAVAILABLE");
                buffer.ensureComponent(ref,AreaStatusProjection.getComponentType());
                observeControl(store,ref,context,()->{HytaleAreaStatuses.applyChill(kernel,context,id,SupportNativeEffects.control(store,ref,bosses),1,
                        (event,details)->emit(context,event,details),chillSources);
                HytaleAreaStatuses.synchronize(kernel.statuses(),id,ref,store,actor);});
                port.applyPassiveAreaPosition(context,ref,vec(store.getComponent(actor,TransformComponent.getComponentType()).getPosition()));
            }else{
                var cause=context.profile().support().element().equals("COLD")?DamageCause.getAssetMap().getAsset("Ice"):
                        connectionCause(context.profile().support().element());if(cause==null)throw new IllegalStateException("AURA_NATIVE_CAUSE_MISSING");
                var outcome=port.damage(context,target,tick,context.profile().support().coefficient()*context.compiledPlan().supportModifiers().effectFactor(),0,cause,true,
                        context.skillInstanceId()+"/aura/"+tick,false);
                emit(context,RpgTraceEventType.AURA_PULSE,Map.of("targetId",id,"tick",tick,"actualHealthLoss",outcome.actualHealthLoss(),"cancelled",outcome.cancelled()));
                if(!outcome.cancelled())port.applyPassiveAreaPosition(context,ref,vec(store.getComponent(actor,TransformComponent.getComponentType()).getPosition()));
            }
        }
    }
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
        var ownedRepeat=repeatingStrikes.get(actor);
        if(ownedRepeat==null||!NativeStrikeActionLock.required(ownedRepeat.context))NativeStrikeActionLock.clear(store,ref);
        Port port = new Port(store, ref, playerRef, player, stats, null, buffer);
        if (!port.actorAliveAndUsable()) {
            NativeStrikeActionLock.clear(store,ref);
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
        connections.tick(actor,System.nanoTime()/1e9,port.connectionWorld());
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
        var retaliated=executions.tickRetaliation(actor,port);
        if(retaliated!=null&&retaliated.status()==SkillExecutionResult.Status.PENDING)executions.activeWindupSeconds(actor).ifPresent(seconds->windupEnds.put(actor,System.nanoTime()+Math.round(seconds*1e9)));
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
    public void onRetaliationDamage(UUID actor,String event,double before,double after,double maximum,boolean hostile,boolean recursive){executions.observeRetaliation(actor,event,before,after,maximum,hostile,recursive);}

    public void onIncomingDamage(UUID actor) {
        if (windupEnds.remove(actor) != null) executions.cancel(actor, "NATIVE_DAMAGE_INTERRUPT");
        for(var channel:connections.cancel(actor,true)) {
            emit(channel,RpgTraceEventType.CONNECTION_TERMINATED,Map.of("reason","NATIVE_DAMAGE_INTERRUPT"));
            executions.terminate(channel,"NATIVE_DAMAGE_INTERRUPT");
        }
    }

    public void cancel(UUID actor, String reason) {
        cancel(actor, reason, null);
    }

    private void cancel(UUID actor, String reason, CommandBuffer<EntityStore> buffer) {
        if(support!=null)support.forgetProgressionPlayer(actor);
        executions.forgetPassiveState(actor);
        kernel.statuses().forgetSource(actor);
        if(summons!=null)summons.cancel(actor,reason);
        if(conversions!=null)conversions.cancel(actor);
        windupEnds.remove(actor); motions.remove(actor); counters.remove(actor);
        RepeatingStrike repeating = repeatingStrikes.remove(actor);
        if (repeating != null) hits.clear(repeating.context.skillInstanceId());
        removeOwnedProjectiles(actor, buffer);
        removeOwnedBurns(actor);
        for (SkillExecutionContext area : areas.cancel(actor))
            emit(area, RpgTraceEventType.AREA_TERMINATED, Map.of("reason", reason));
        for(var connection:connections.cancel(actor,false)) {
            emit(connection,RpgTraceEventType.CONNECTION_TERMINATED,Map.of("reason",reason));
            executions.terminate(connection,reason);
        }
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
        Vec3 observed=vec(store.getComponent(ref,TransformComponent.getComponentType()).getPosition());
        motion.travel.observe(current,applied,observed,deltaSeconds);
        player.setCurrentFallDistance(Math.max(fall, player.getCurrentFallDistance()));
        if (fraction < 1.0 - 1.0e-6) {
            emit(motion.context, RpgTraceEventType.MOVEMENT_CLAMPED,
                    Map.of("requestedSegment", segment.horizontalLength(), "appliedFraction", fraction,
                            "reason", "NATIVE_BLOCK_COLLISION"));
            finishMotion(motion, observed, true, buffer); return;
        }
        if (progress >= 1.0) finishMotion(motion, observed, motion.plan.clamped(), buffer);
    }

    private void finishMotion(Motion motion, Vec3 finalPosition, boolean clamped, CommandBuffer<EntityStore> buffer) {
        UUID actor = motion.context.request().actorId(); motions.remove(actor);
        emit(motion.context, RpgTraceEventType.MOVEMENT_END,
                Map.of("distance", finalPosition.subtract(motion.plan.origin()).horizontalLength(), "clamped", clamped,
                        "durationSeconds", motion.elapsed,"validatedTravelMeters",motion.travel.meters(),
                        "travelEvidenceValid",motion.travel.valid(),"momentumIncreased",motion.travel.increased(motion.context)));
        if (motion.context.profile().hasFamily(Stage04SkillProfile.Family.STRIKE)) {
            Ref<EntityStore> ref = motion.actor;
            if (ref.isValid()) {
                Store<EntityStore> store = ref.getStore();
                PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                Player player = store.getComponent(ref, Player.getComponentType());
                EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
                if (playerRef != null && player != null && stats != null)
                    new Port(store, ref, playerRef, player, stats, null, buffer).executeStrike(motion.travel.impact(motion.context));
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
        @Override public NativeResourcePort resources() { return new EntityStatResourcePort(stats,()->{if(support!=null)support.invalidateHealthCredit(playerRef.getWorldUuid(),playerRef.getUuid());}); }
        @Override public SkillExecutionResult stopActiveSupport(Stage04SkillProfile profile){return support==null?null:
                support.runtime().stopActive(playerRef.getUuid(),profile.skillId(),support.port(store,actor));}
        @Override public Validation familyPrerequisites(Stage04SkillProfile profile,
                                                        com.inigmasgames.hytalerpg.domain.CompiledSkillPlan plan) {
            if(profile.cage()!=null)return Validation.reject(com.inigmasgames.hytalerpg.execution.summon.SelectiveCageProfile.BLOCKED_BOUNDARY);
            if(profile.projectile()!=null&&!profile.projectile().details().nativeCapabilityGate().isEmpty())
                return Validation.reject(profile.projectile().details().nativeCapabilityGate());
            if (profile.family() == Stage04SkillProfile.Family.STRIKE) {
                var probe = profile.strike().geometry() == Stage04SkillProfile.Geometry.RADIUS
                        ? profile.strike().withRange(profile.strike().range() * plan.executionModifiers().radiusFactor()) : profile.strike();
                try { select(probe, false); }
                catch (IllegalStateException overflow) {
                    if (overflow.getMessage().startsWith("STRIKE_")) return Validation.reject(overflow.getMessage());
                    throw overflow;
                }
            }
            if(plan.strikes().multistrike()&&!NativeStrikeActionLock.available(store,actor))return Validation.reject("MULTISTRIKE_NATIVE_ACTION_LOCK_UNAVAILABLE");
            if(profile.strike()!=null && profile.strike().details().actionLockSeconds()>0
                    && !NativeStrikeActionLock.available(store,actor,profile.strike().details().movementFactor()))
                return Validation.reject("STRIKE_NATIVE_ACTION_LOCK_UNAVAILABLE");
            if(profile.strike()!=null && strikeCause(profile.strike())==null)return Validation.reject("STRIKE_NATIVE_DAMAGE_CAUSE_MISSING");
            if (motions.containsKey(playerRef.getUuid()) || windupEnds.containsKey(playerRef.getUuid())
                    || reactions.active(playerRef.getUuid()).isPresent()) return Validation.reject("INCOMPATIBLE_ACTIVE_STATE");
            if(profile.support()!=null)return support==null?Validation.reject("SUPPORT_NATIVE_ADAPTER_UNAVAILABLE"):
                    support.preflight(store,actor,profile,plan);
            if(profile.conversion()!=null)return conversions==null?Validation.reject("CONVERSION_NATIVE_ADAPTER_UNAVAILABLE"):
                    conversions.preflight(store,actor,profile,aim(store,actor));
            if(profile.summonAction()!=null)return summons==null?Validation.reject("SUMMON_NATIVE_ADAPTER_UNAVAILABLE"):
                    summons.preflightAction(store,actor,profile,aim(store,actor));
            if(profile.summon()!=null)return summons==null?Validation.reject("SUMMON_NATIVE_ADAPTER_UNAVAILABLE"):
                    summons.preflight(store,actor,profile,plan,aim(store,actor));
            if(profile.connection()!=null) {
                var connection=profile.connection();String admitted=connections.admission(playerRef.getUuid(),profile,plan);
                if(!admitted.equals("PASS"))return Validation.reject(admitted);
                if(profile.projectile()==null&&connectionCause(connection.element())==null)return Validation.reject("CONNECTION_DAMAGE_CAUSE_MISSING");
                if(plan.orbit()&&profile.projectile()!=null&&!ammunition.available(actor,store,profile.projectile()))return Validation.reject("AMMUNITION_UNAVAILABLE");
                if(connection.requiresTarget()) {
                    var selection=com.inigmasgames.hytalerpg.execution.connection.ConnectionTargeting.select(connection,connectionWorld());
                    if(!selection.verdict().equals("PASS"))return Validation.reject(selection.verdict());
                }
                if(connection.channel()) {
                    var cost=kernel.resources().evaluateUpkeep(new com.inigmasgames.hytalerpg.combat.resource.ResourceCost(
                            ResourceType.MANA,connection.upkeepPerSecond()*connection.intervalSeconds()),plan.kernelModifiers());
                    if(!kernel.resources().canAfford(playerRef.getUuid(),cost,resources()))return Validation.reject("INSUFFICIENT_UPKEEP");
                }
                Vec3 feet=vec(store.getComponent(actor,TransformComponent.getComponentType()).getPosition());
                return HytaleAreaQueries.clear(store,feet.add(new Vec3(0,.1,0)),feet.add(new Vec3(0,connection.originHeight(),0)))
                        ?Validation.pass():Validation.reject("CONNECTION_ORIGIN_BLOCKED");
            }
            if (profile.area() != null) {
                String admitted = areas.admission(playerRef.getUuid(), profile.skillId(), profile.area().trap());
                if (!admitted.equals("PASS")) return Validation.reject(admitted);
                Vec3 feet = vec(store.getComponent(actor, TransformComponent.getComponentType()).getPosition());
                areaDirection = aim(store, actor);
                areaPlacement = profile.family() == Stage04SkillProfile.Family.CONE ? feet : profile.area().placementRange() > 0&&!plan.zones().mobileDomain()
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
                if (NativeProjectilePayloads.cause(profile.projectile()) == null)
                    return Validation.reject("PROJECTILE_DAMAGE_CAUSE_MISSING");
                if (!profile.projectile().statusId().isBlank() && !HytaleAreaStatuses.available())
                    return Validation.reject("PROJECTILE_STATUS_ASSETS_UNAVAILABLE");
                if (plan.projectileModifiers().ballistics() && profile.projectile().gravity() != 0)
                    return Validation.reject("BALLISTICS_GRAVITY_UNSUPPORTED");
                boolean configMissing = java.util.stream.Stream.concat(
                                java.util.stream.Stream.of(profile.projectile().configId()),
                                profile.projectile().configIdsByWeaponKind().values().stream())
                        .distinct().anyMatch(id -> ProjectileConfig.getAssetMap().getAsset(id) == null);
                if (configMissing)
                    return Validation.reject("PROJECTILE_CONFIG_MISSING");
                long owned = projectiles.values().stream().filter(value -> value.actorId.equals(playerRef.getUuid())).count();
                if (owned >= plan.safetyBudgets().maxLiveProjectiles())
                    return Validation.reject("PROJECTILE_LIVE_BUDGET_EXCEEDED");
                String admission=projectileService.registry().admission(playerRef.getUuid(),profile.projectile().details().pattern().rootLaunches(plan));
                if(!admission.equals("PASS"))return Validation.reject(admission);
                Vec3 eye=vec(store.getComponent(actor,TransformComponent.getComponentType()).getPosition()).add(new Vec3(0,1.35,0));
                Vec3 direction=aim(store,actor),origin=eye.add(direction.multiply(.65));
                if(profile.projectile().details().pattern().ballisticAim()) {
                    Vec3 point=projectileAimPoint(profile,plan,origin,direction);
                    var solution=ballisticSolution(profile,plan,origin,point);
                    if(solution.isEmpty())return Validation.reject("BALLISTIC_TARGET_UNREACHABLE");
                    if(!solution.get().clear((a,b)->HytaleAreaQueries.projectileClear(store,a,b,profile.projectile().radius())))
                        return Validation.reject("BALLISTIC_PATH_BLOCKED");
                }
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
            if(profile.support()!=null)return support.capture(store,actor,profile);
            if(profile.conversion()!=null)return conversions.capture(store,actor,profile,aim(store,actor));
            if(profile.summonAction()!=null)return summons.captureAction(store,actor,profile,aim(store,actor));
            if(profile.summon()!=null)return summons.capture(store,actor,profile,aim(store,actor));
            Vec3 feet=vec(store.getComponent(actor,TransformComponent.getComponentType()).getPosition());
            Vec3 direction=facing(store,actor),point=feet;UUID targetId=null;
            if(profile.area()!=null) { point=areaPlacement;direction=areaDirection; }
            else if(profile.connection()!=null) {
                direction=aim(store,actor);
                if(profile.connection().kind()==com.inigmasgames.hytalerpg.execution.connection.ConnectionProfile.Kind.WAVE)direction=direction.horizontalNormalized();
                Vec3 origin=feet.add(new Vec3(0,profile.connection().originHeight(),0));
                point=HytaleAreaQueries.rayEndpoint(store,origin,direction,profile.connection().range());
                if(profile.connection().requiresTarget()){
                    var selected=com.inigmasgames.hytalerpg.execution.connection.ConnectionTargeting.select(profile.connection(),connectionWorld());
                    if(!selected.verdict().equals("PASS"))throw new IllegalStateException(selected.verdict());
                    point=selected.target().bounds().centre();targetId=UUID.fromString(selected.target().id());
                }
            }
            else if(profile.projectile()!=null) {
                direction=aim(store,actor);Vec3 muzzle=feet.add(new Vec3(0,1.35,0)).add(direction.multiply(.65));
                point=projectileAimPoint(profile,plan,muzzle,direction);
                if(profile.projectile().details().pattern().homingTurnDegrees()>0) {
                    var selected=nearestTarget(profile.projectile().maxDistance(),12);
                    if(selected!=null){targetId=store.getComponent(selected,UUIDComponent.getComponentType()).getUuid();point=vec(store.getComponent(selected,TransformComponent.getComponentType()).getPosition()).add(new Vec3(0,.5,0));}
                }
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
        private Vec3 projectileAimPoint(Stage04SkillProfile profile,com.inigmasgames.hytalerpg.domain.CompiledSkillPlan plan,Vec3 origin,Vec3 direction) {
            Vec3 point=HytaleAreaQueries.rayEndpoint(store,origin,direction,profile.projectile().maxDistance()*plan.projectileModifiers().distanceFactor());
            return profile.projectile().details().pattern().ballisticAim()?point.add(new Vec3(0,profile.projectile().radius()+.04,0)):point;
        }
        private java.util.Optional<ProjectileBallistics.Solution> ballisticSolution(Stage04SkillProfile profile,com.inigmasgames.hytalerpg.domain.CompiledSkillPlan plan,Vec3 origin,Vec3 point) {
            var p=profile.projectile();return ProjectileBallistics.low(origin,point,p.speedFor(equipment().mainHand().weaponKind())*plan.projectileModifiers().speedFactor(),
                    p.gravity(),p.maxDistance()*plan.projectileModifiers().distanceFactor(),p.independentLifetimeSeconds());
        }
        @Override public Validation validateRelease(SkillExecutionContext context) {
            if(context.profile().support()!=null)return support.validateRelease(store,actor,context);
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
            if(context.conditionalRepeat()&&target.entityId()!=null){
                var reference=store.getExternalData().getRefFromUUID(target.entityId());var selected=candidate(reference);
                if(selected==null||selected.protectedTarget()||!HytaleAreaQueries.hostile(store,reference,actor))return Validation.reject("CONDITIONAL_ENTITY_INVALID");
                if(!HytaleAreaQueries.clear(store,feet.add(new Vec3(0,1.35,0)),selected.position().add(new Vec3(0,.5,0))))return Validation.reject("CONDITIONAL_ENTITY_LOS_BLOCKED");
                if(selected.position().subtract(feet).length()>conditionalReach(context)+1e-6)return Validation.reject("CONDITIONAL_ENTITY_OUT_OF_REACH");
            }
            if(profile.conversion()!=null)return conversions.validate(store,actor,context);
            if(profile.summonAction()!=null)return summons.validateAction(store,actor,context);
            if(profile.summon()!=null){
                if(context.derivedRelease())return Validation.reject("SUMMON_REPEAT_FORBIDDEN");
                if(feet.subtract(target.point()).length()>profile.summon().range())return Validation.reject("SUMMON_COMMITTED_TARGET_OUT_OF_RANGE");
                if(!HytaleAreaQueries.clear(store,feet.add(new Vec3(0,1.35,0)),target.point()))return Validation.reject("SUMMON_COMMITTED_LOS_BLOCKED");
                if(profile.summon().corpseRequired()){
                    if(summons.committedCorpse(context).isEmpty())return Validation.reject("COMMITTED_CORPSE_PERMIT_UNAVAILABLE");
                }
                String capacity=summons.registry().admission(playerRef.getUuid(),context.compiledPlan().summonModifiers().count(profile.summon().count()),profile.summon().decoy());
                return capacity.equals("PASS")?Validation.pass():Validation.reject(capacity);
            }
            if(profile.connection()!=null) {
                var origin=feet.add(new Vec3(0,profile.connection().originHeight(),0));
                if(context.compiledPlan().orbit()){
                    // The converted payload belongs to the current caster, not a stale aim endpoint.
                    if(!HytaleAreaQueries.clear(store,feet.add(new Vec3(0,.1,0)),origin))return Validation.reject("CONNECTION_ORIGIN_BLOCKED");
                }else if(target.entityId()!=null){
                    var resolved=connectionWorld().resolveTarget(target.entityId().toString()).orElse(null);
                    if(resolved==null)return Validation.reject("COMMITTED_ENTITY_TARGET_INVALID");
                    if(ConnectionShape.pointDistanceSquared(origin,resolved.bounds())>profile.connection().range()*profile.connection().range()+1e-9)return Validation.reject("COMMITTED_TARGET_OUT_OF_RANGE");
                    if(!HytaleAreaQueries.clear(store,origin,resolved.bounds().centre()))return Validation.reject("COMMITTED_TARGET_LOS_BLOCKED");
                }else{
                    if(target.point().subtract(origin).length()>profile.connection().range()+1e-6)return Validation.reject("COMMITTED_TARGET_OUT_OF_RANGE");
                    if(!HytaleAreaQueries.clear(store,origin,target.point()))return Validation.reject("COMMITTED_TARGET_LOS_BLOCKED");
                }
                String admission=connections.admission(playerRef.getUuid(),profile,context.compiledPlan());
                return admission.equals("PASS")?Validation.pass():Validation.reject(admission);
            }
            if(profile.area()!=null) {
                String admission=areas.admission(playerRef.getUuid(),profile.skillId(),profile.area().trap());
                if(!admission.equals("PASS"))return Validation.reject(admission);
                if(context.compiledPlan().zones().mobileDomain()){
                    if(!actorAliveAndUsable())return Validation.reject("MOBILE_OWNER_ANCHOR_UNAVAILABLE");
                    // After Skill Delay, placement belongs to the current caster, not the old aim point.
                    areaPlacement=HytaleAreaQueries.ground(store,feet.add(new Vec3(0,.15,0)),new Vec3(0,-1,0),.65).orElse(null);
                    if(areaPlacement==null)return Validation.reject("NO_LEGAL_GROUND_SURFACE");
                    areaDirection=target.direction();
                    var attached=profile.area().footprint(feet,areaDirection,context.compiledPlan().executionModifiers().radiusFactor());
                    return areaWorld().query(attached,profile.area().candidateBudget()).overflow()?Validation.reject("AREA_CANDIDATE_BUDGET"):Validation.pass();
                }
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
                if(profile.projectile().details().pattern().ballisticAim()) {
                    var solution=ballisticSolution(profile,context.compiledPlan(),muzzle.add(target.direction().multiply(.65)),target.point());
                    if(solution.isEmpty())return Validation.reject("BALLISTIC_TARGET_UNREACHABLE");
                    return solution.get().clear((a,b)->HytaleAreaQueries.projectileClear(store,a,b,profile.projectile().radius()))?Validation.pass():Validation.reject("BALLISTIC_PATH_BLOCKED");
                }
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
        private double conditionalReach(SkillExecutionContext c){
            var p=c.profile();
            if(p.connection()!=null)return p.connection().range();
            if(p.projectile()!=null)return p.projectile().maxDistance()*c.compiledPlan().projectileModifiers().distanceFactor();
            if(p.area()!=null)return p.area().placementRange()>0?p.area().placementRange():p.area().radius()*c.compiledPlan().executionModifiers().radiusFactor();
            if(p.strike()!=null)return p.strike().range()*(p.strike().geometry()==Stage04SkillProfile.Geometry.RADIUS?c.compiledPlan().executionModifiers().radiusFactor():1);
            return 0;
        }
        @Override public CommittedTarget conditionalKillTarget(SkillExecutionContext c,ConditionalRepeatRuntime.Hit hit){
            if(!actorAliveAndUsable()||c.target()==null||!c.target().worldId().equals(playerRef.getWorldUuid()))return null;
            var queried=HytaleAreaQueries.query(store,actor,b->ConnectionShape.pointDistanceSquared(hit.position(),b)<=144+1e-9,256);
            if(queried.overflow())throw new IllegalStateException("KILL_TRIGGER_CANDIDATE_BUDGET");
            Vec3 feet=vec(store.getComponent(actor,TransformComponent.getComponentType()).getPosition());var p=c.profile();
            var solutions=new java.util.ArrayList<ConditionalKillTargeting.Target>();
            for(var value:queried.candidates()){
                var victim=candidate(value.ref());if(victim==null)continue;
                UUID id=UUID.fromString(victim.stableId());Vec3 point=value.bounds().centre();
                boolean legal=point.subtract(feet).length()<=conditionalReach(c)+1e-6;
                boolean los=HytaleAreaQueries.clear(store,feet.add(new Vec3(0,1.35,0)),point);
                Vec3 direction=point.subtract(feet).normalized();
                if(p.area()!=null){
                    if(p.area().placementRange()>0){
                        point=HytaleAreaQueries.ground(store,new Vec3(point.x(),value.bounds().min().y()+.5,point.z()),new Vec3(0,-1,0),1).orElse(null);
                        if(point==null)continue;
                    }else point=feet; // Caster bursts/cones do not become free remote ground casts.
                    direction=direction.horizontalNormalized();
                }else if(p.strike()!=null)direction=direction.horizontalNormalized();
                solutions.add(new ConditionalKillTargeting.Target(id,value.bounds(),new CommittedTarget(playerRef.getWorldUuid(),feet,point,direction,id),true,
                        HytaleAreaQueries.hostile(store,value.ref(),actor),victim.protectedTarget(),los,legal));
            }
            return ConditionalKillTargeting.nearest(hit.victim(),hit.position(),solutions,false);
        }
        private boolean sameItem(Item current,Item prior) {
            return current!=null && prior!=null && current.itemId().equals(prior.itemId()) && current.weaponKind().equals(prior.weaponKind());
        }
        @Override public SkillExecutionResult executeConnection(SkillExecutionContext context) {
            if(context.compiledPlan().orbit()&&context.profile().projectile()!=null&&!context.derivedRelease()){
                String gate=connections.admission(playerRef.getUuid(),context.profile(),context.compiledPlan());if(!gate.equals("PASS"))throw new IllegalStateException(gate);
                var payload=context.profile().projectile();var ammo=ammunition.consume(actor,store,payload);
                emit(context,RpgTraceEventType.AMMO_CHECK,Map.of("required",payload.requiresAmmo(),"itemId",payload.ammoItemId(),"quantity",payload.ammoQuantity(),"available",true,"convertedOrbit",true));
                if(ammo.quantity()>0)emit(context,RpgTraceEventType.AMMO_COMMITTED,Map.of("itemId",ammo.itemId(),"quantity",ammo.quantity(),"convertedOrbit",true));
                // Once the connection starts it can hit immediately; no ambiguous late failure refunds ammunition.
            }
            connections.start(context,System.nanoTime()/1e9,connectionWorld());
            return SkillExecutionResult.committed("CONNECTION_STARTED",0,0);
        }
        @Override public SkillExecutionResult executeSupport(SkillExecutionContext context){return support.execute(store,actor,context,buffer);}
        @Override public SkillExecutionResult executeConversion(SkillExecutionContext context){return conversions.execute(store,buffer,actor,context);}
        @Override public com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets captureSummonModifiers(com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets authored){
            return support==null?authored:support.runtime().finite().outgoingModifiers(playerRef.getWorldUuid(),playerRef.getUuid(),authored,System.nanoTime()/1e9);
        }
        @Override public void commitConsumable(SkillExecutionContext context){if(summons!=null)summons.commitConsumable(store,actor,context);}
        @Override public SkillExecutionResult executeSummon(SkillExecutionContext context){return context.profile().summonAction()!=null?
                summons.executeAction(store,buffer,actor,context):summons.execute(store,buffer,actor,context);}
        private int summonBurst(SkillExecutionContext context,Vec3 point,double radius,double coefficient,String effect,boolean frozen){
            var shape=new AreaGeometry(AreaGeometry.Kind.DISC,point.add(new Vec3(0,-1,0)),Vec3.FORWARD,radius,0,0,0,3);
            var query=HytaleAreaQueries.query(store,actor,shape,256);
            if(query.overflow())throw new IllegalStateException("SUMMON_BURST_QUERY_CAP");
            var accepted=query.candidates().stream().filter(c->HytaleSummonSystem.alive(store,c.ref()))
                    .filter(c->store.getComponent(c.ref(),SummonProjection.getComponentType())==null)
                    .filter(c->HytaleAreaQueries.clear(store,point.add(new Vec3(0,.2,0)),c.bounds().centre()))
                    .map(c->candidate(c.ref())).filter(java.util.Objects::nonNull).filter(c->!c.protectedTarget())
                    .sorted(java.util.Comparator.comparing(c->c.stableId())).toList();
            if(accepted.size()>64)throw new IllegalStateException("SUMMON_BURST_ACCEPTED_CAP");
            String element=context.profile().summon()!=null?context.profile().summon().element():"NECROTIC";
            int hit=0;for(var target:accepted){
                if(!target.handle().isValid()||!HytaleSummonSystem.alive(store,target.handle())||!HytaleAreaQueries.hostile(store,target.handle(),actor))continue;
                var outcome=damage(context,target,++hit,coefficient,context.snapshot().criticalChance(),connectionCause(element),false,effect,false,frozen);
                if(!outcome.cancelled()&&context.profile().summonAction()!=null)applyPassiveAreaPosition(context,target.handle(),point);
            }
            try{vfx.presentConnection(store.getExternalData().getWorld(),com.inigmasgames.hytalerpg.execution.connection.ConnectionShape.cylinder(point,radius,3),element,"SUMMON_BURST",.4);}
            catch(RuntimeException failure){emit(context,RpgTraceEventType.SUMMON_ACTION_REJECTED,Map.of("action","PRESENTATION","boundary",String.valueOf(failure.getMessage())));}
            return hit;
        }
        private ConnectionWorldPort connectionWorld() {
            return new ConnectionWorldPort() {
                private final Map<String,Ref<EntityStore>> refs=new HashMap<>();
                public Frame frame(){return new Frame(playerRef.getWorldUuid(),vec(store.getComponent(actor,TransformComponent.getComponentType()).getPosition()),aim(store,actor));}
                public String validate(SkillExecutionContext context,UUID world) {
                    if(!actorAliveAndUsable())return "ACTOR_NOT_USABLE";
                    if(!world.equals(playerRef.getWorldUuid()))return "WORLD_CHANGED";
                    var current=equipment();var prior=context.equipment();
                    if(prior==null||current==null||!sameItem(current.mainHand(),prior.mainHand()))return "COMMITTED_EQUIPMENT_CHANGED";
                    if(context.profile().connection().channel()) {
                        var active=kernel.statuses().inspect(playerRef.getUuid()).active();
                        if(active.containsKey(RpgStatusType.FROZEN)||active.containsKey(RpgStatusType.STAGGER)||active.containsKey(RpgStatusType.FEAR))
                            return "CHANNEL_CONTROL_INTERRUPT";
                        var effects=store.getComponent(actor,EffectControllerComponent.getComponentType());
                        var stun=EntityEffect.getAssetMap().getAsset(NATIVE_STAGGER_EFFECT);
                        if(effects!=null&&stun!=null&&effects.hasEffect(stun))return "CHANNEL_NATIVE_STUN_INTERRUPT";
                    }
                    return "PASS";
                }
                public Vec3 unobstructedEndpoint(Vec3 from,Vec3 to) {
                    double range=to.subtract(from).length();return range<=1e-9?from:HytaleAreaQueries.rayEndpoint(store,from,to.subtract(from),range);
                }
                public Query query(ConnectionShape shape,int cap) {
                    return query(List.of(shape),cap);
                }
                public Query query(List<ConnectionShape> shapes,int cap) {
                    refs.clear();var found=HytaleAreaQueries.query(store,actor,bounds->shapes.stream().anyMatch(shape->shape.intersects(bounds)),cap);var targets=new ArrayList<Target>();
                    for(var value:found.candidates()) {
                        var target=candidate(value.ref());if(target==null||target.protectedTarget())continue;
                        refs.put(target.stableId(),value.ref());targets.add(new Target(target.stableId(),value.bounds()));
                    }
                    return new Query(targets,found.overflow());
                }
                public java.util.Optional<Target> resolveTarget(String id) {
                    if(id==null)return java.util.Optional.empty();var ref=store.getExternalData().getRefFromUUID(UUID.fromString(id));
                    if(ref==null||!ref.isValid()||!HytaleAreaQueries.hostile(store,ref,actor))return java.util.Optional.empty();
                    var candidate=Port.this.candidate(ref);var box=store.getComponent(ref,BoundingBox.getComponentType());
                    if(candidate==null||candidate.protectedTarget()||box==null)return java.util.Optional.empty();
                    var position=vec(store.getComponent(ref,TransformComponent.getComponentType()).getPosition());var bounds=box.getBoundingBox();refs.put(id,ref);
                    return java.util.Optional.of(new Target(id,new AreaGeometry.Bounds(vec(bounds.min).add(position),vec(bounds.max).add(position))));
                }
                public boolean lineOfSight(Vec3 origin,Target target){return HytaleAreaQueries.clear(store,origin,target.bounds().centre());}
                public boolean payUpkeep(SkillExecutionContext context,int tick,double seconds) {
                    var cost=kernel.resources().evaluateUpkeep(new com.inigmasgames.hytalerpg.combat.resource.ResourceCost(ResourceType.MANA,
                            context.profile().connection().upkeepPerSecond()*seconds),context.compiledPlan().kernelModifiers());
                    var nativeResources=resources();double before=nativeResources.current(ResourceType.MANA);
                    if(!kernel.resources().canAfford(playerRef.getUuid(),cost,nativeResources))return false;
                    var token=kernel.resources().reserveCost(playerRef.getUuid(),cost,nativeResources);
                    try {
                        kernel.resources().commitCost(token,nativeResources);double after=nativeResources.current(ResourceType.MANA);
                        boolean observed=Math.abs((before-after)-cost.amount())<=1e-4;
                        emit(context,RpgTraceEventType.CHANNEL_UPKEEP,Map.of("tick",tick,"cost",cost.amount(),"before",before,"after",after,"nativeWriteObserved",observed));
                        return observed;
                    } finally {kernel.resources().finish(token);}
                }
                public double damage(SkillExecutionContext context,Target target,int tick,double coefficient,boolean periodic) {
                    return damage(context,target,tick,coefficient,periodic,null);
                }
                public double damage(SkillExecutionContext context,Target target,int tick,double coefficient,boolean periodic,Vec3 effectCenter) {
                    var ref=store.getExternalData().getRefFromUUID(UUID.fromString(target.id()));if(ref==null||!ref.isValid()||!HytaleAreaQueries.hostile(store,ref,actor))return 0;
                    var value=candidate(ref);if(value==null||value.protectedTarget())return 0;
                    DamageCause cause=context.profile().projectile()!=null?NativeProjectilePayloads.cause(context.profile().projectile()):connectionCause(context.profile().connection().element());
                    if(cause==null)throw new IllegalStateException("CONNECTION_DAMAGE_CAUSE_MISSING");
                    boolean explosionOnly=context.profile().projectile()!=null&&context.profile().projectile().coefficient()==0;
                    var outcome=explosionOnly?new DamageOutcome(0,0,false):Port.this.damage(context,value,tick,coefficient,periodic?0:context.snapshot().criticalChance(),cause,periodic,
                            context.skillInstanceId()+"/connection/"+tick,!periodic&&!context.derivedRelease());
                    var authored=context.profile().connection().details();
                    double splashHealthLost=0;
                    if(context.compiledPlan().orbit()&&context.profile().projectile()!=null&&context.profile().projectile().details().explosion().active())
                        splashHealthLost=applyAuthoredExplosion(context,Port.this,target.bounds().centre(),context.skillInstanceId()+"/orbit-impact/"+tick+"/"+target.id(),!context.derivedRelease());
                    if(context.compiledPlan().orbit()&&context.profile().projectile()!=null&&!outcome.cancelled()&&outcome.actualHealthLoss()>0){
                        var payload=context.profile().projectile();
                        if(!payload.statusId().isBlank()&&payload.details().explosion().burnRadius()==0){
                            if(payload.hasPeriodicStatus())applyProjectilePeriodicStatus(context,value);else applyProjectileStatus(context,value);
                        }
                        applyProjectileKnockback(context,value);
                    }
                    if(!outcome.cancelled()&&!authored.status().isBlank()&&ref.isValid()) {
                        observeControl(store,ref,context,()->{
                        var npc=store.getComponent(ref,NPCEntity.getComponentType());
                        var control=areaControls.resolve(npc.getRoleName(),value.protectedTarget(),value.boss());
                        var status=kernel.statuses().apply(UUID.fromString(target.id()),RpgStatusType.valueOf(authored.status()),control,authored.statusSeconds());
                        emit(context,status.outcome()==com.inigmasgames.hytalerpg.combat.status.StatusService.Outcome.REJECTED?RpgTraceEventType.STATUS_REJECTED:RpgTraceEventType.STATUS_APPLIED,
                                Map.of("status",status.type(),"targetId",target.id(),"seconds",status.remainingSeconds(),"reason",status.detail()));
                        if(buffer==null||store.getComponent(ref,EffectControllerComponent.getComponentType())==null)
                            throw new IllegalStateException("CONNECTION_NATIVE_CONTROL_UNAVAILABLE");
                        buffer.ensureComponent(ref,AreaStatusProjection.getComponentType());
                        HytaleAreaStatuses.synchronize(kernel.statuses(),UUID.fromString(target.id()),ref,store,actor);
                        });
                    }
                    if(!outcome.cancelled()&&effectCenter!=null&&ProfileComponentPolicy.enemyPosition(context.profile()))applyPassiveAreaPosition(context,ref,effectCenter);
                    return outcome.actualHealthLoss()+splashHealthLost;
                }
                public void healFromDamage(SkillExecutionContext context,int tick,double actualHealthLost) {
                    if(!actorAliveAndUsable())return;var stats=store.getComponent(actor,EntityStatMap.getComponentType());
                    var value=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());if(value==null)throw new IllegalStateException("NATIVE_HEALTH_MISSING");
                    var healing=new com.inigmasgames.hytalerpg.combat.healing.HealingCalculationService().fromActualDamage(actualHealthLost,
                            context.profile().connection().details().healFraction(),context.snapshot().derivedStats().healingMultiplier(),
                            context.compiledPlan().kernelModifiers().scalablePayloadIncreased()+context.compiledPlan().supportModifiers().healingIncreased(value.get(),value.getMax()));
                    double before=value.get(),requested=Math.min(value.getMax(),before+healing.requestedHealing());
                    stats.setStatValue(DefaultEntityStatTypes.getHealth(),(float)requested);double after=value.get();
                    if(support!=null)support.healingResolved(store,buffer,actor,context,healing.requestedHealing(),before,after,value.getMax());
                    emit(context,RpgTraceEventType.HEAL_APPLIED,Map.of("tick",tick,"sourceActualHealthLoss",actualHealthLost,
                            "baseHealing",healing.baseHealing(),"wisdomMultiplier",healing.wisdomMultiplier(),"healingIncreased",healing.healingIncreased(),
                            "requestedHealing",healing.requestedHealing(),"healthBefore",before,"healthAfter",after,"actualHealing",Math.max(0,after-before)));
                }
                public void present(SkillExecutionContext context,ConnectionShape shape,String phase,double seconds) {
                    try{vfx.presentConnection(store.getExternalData().getWorld(),shape,context.profile().connection().element(),phase,seconds);}
                    catch(RuntimeException ignored){ }
                }
                public void ended(SkillExecutionContext context,String reason){executions.terminate(context,reason);}
                public void trace(SkillExecutionContext context,String event,Map<String,?> details){emit(context,RpgTraceEventType.valueOf(event),details);}
            };
        }
        @Override public void abandonRelease(SkillExecutionContext context) {
            if(summons!=null)summons.corpses().abandon(context.request().actorId(),context.skillInstanceId());
            if(context.profile().projectile()!=null && !context.compiledPlan().orbit() && context.derivedRelease())
                projectileService.registry().abandonLaunch(context.request().actorId(),context.rootCastId());
        }
        @Override public SkillExecutionResult executeStrike(SkillExecutionContext context) {
            if(context.multistrikeIndex()>0)throw new IllegalStateException("MULTISTRIKE_CHILD_MUST_NOT_SCHEDULE_REPEATS");
            boolean multi=context.compiledPlan().strikes().multistrike();
            boolean locked=NativeStrikeActionLock.required(context);
            long releasedAt=System.nanoTime();
            if(locked)NativeStrikeActionLock.acquire(store,actor,context);
            try{
            int applied = executeStrikeHit(context, 0);
            var strike = context.profile().strike();
            if (strike.repeats() > 1 && strike.repeatIntervalSeconds() > 0.0 || strike.details().actionLockSeconds()>0)
                repeatingStrikes.put(playerRef.getUuid(), new RepeatingStrike(context,
                        new StrikeRepeatSchedule(strike.repeats(), strike.repeatIntervalSeconds(), releasedAt,strike.details().actionLockSeconds())));
            else {
                for (int hitIndex = 1; hitIndex < strike.repeats(); hitIndex++)
                    applied += executeStrikeHit(context, hitIndex);
                hits.clear(context.skillInstanceId());
            }
            vfx.present(store.getExternalData().getWorld(), player, context.compiledPlan().vfxRecipeId());
            return SkillExecutionResult.committed("STRIKE_COMPLETE", applied, 0.0);
            }catch(RuntimeException failure){repeatingStrikes.remove(playerRef.getUuid());hits.clear(context.skillInstanceId());if(locked)NativeStrikeActionLock.clear(store,actor);throw failure;}
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
                @Override public java.util.Optional<OwnerAnchor> ownerAnchor(SkillExecutionContext context){
                    if(!actorAliveAndUsable()||!context.request().actorId().equals(playerRef.getUuid()))return java.util.Optional.empty();
                    var transform=store.getComponent(actor,TransformComponent.getComponentType());
                    return transform==null?java.util.Optional.empty():java.util.Optional.of(
                            new OwnerAnchor(playerRef.getUuid(),playerRef.getWorldUuid(),vec(transform.getPosition())));
                }
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
                            payload.periodic() ? 0 : context.snapshot().criticalChance(), cause, payload.periodic(),context.skillInstanceId(),!payload.periodic()&&!context.derivedRelease());
                    if (outcome.cancelled()) return false;
                    if (!payload.pullBeforeDamage() && payload.pull() > 0) applyAreaPull(context, reference, npc, control, payload);
                    if (payload.status().equals("BURN") || payload.status().equals("POISON")) {
                        applyPeriodicStatus(context, candidate, PeriodicStatusRuntime.Kind.valueOf(payload.status()),
                                payload.statusSeconds(), payload.status().equals("BURN") ? .10 : .06);
                    } else {
                        observeControl(store,reference,context,()->HytaleAreaStatuses.apply(kernel, context, candidate, payload, control, store, actor,
                                (event, details) -> emit(context, event, details),chillSources));
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
                    applyPassiveAreaPosition(context,reference,payload.origin());
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
        /** Fresh world-thread access only. A failed optional movement cannot refund an already resolved paid hit. */
        private void applyPassiveAreaPosition(SkillExecutionContext context,Ref<EntityStore> reference,Vec3 center){
            var mode=context.compiledPlan().positions();if(!mode.active())return;
            try{
                var victim=candidate(reference);
                if(victim==null||victim.protectedTarget()||!HytaleAreaQueries.hostile(store,reference,actor))return;
                double now=System.nanoTime()/1e9;
                String admission=context.effects().displacement().claim(victim.stableId(),now);
                if(admission.equals("PASS")&&context.profile().support()!=null&&context.profile().support().aura()
                        &&(support==null||!support.runtime().claimAuraSecondary(context,now)))admission="AURA_SECONDARY_EPOCH_BUDGET";
                if(!admission.equals("PASS")){
                    emit(context,RpgTraceEventType.AREA_DISPLACEMENT,Map.of("passive",mode.vacuum()?"vacuum":"repulsion","targetId",victim.stableId(),"applied",0,"reason",admission));return;
                }
                var npc=store.getComponent(reference,NPCEntity.getComponentType());
                var transform=store.getComponent(reference,TransformComponent.getComponentType());
                var bounds=store.getComponent(reference,BoundingBox.getComponentType());
                if(npc==null||transform==null||bounds==null)throw new IllegalStateException("NATIVE_DISPLACEMENT_COMPONENT_MISSING");
                var control=areaControls.resolve(npc.getRoleName(),victim.protectedTarget(),victim.boss());
                double scale=npc.getRole()!=null&&npc.getRole().getKnockbackScale()>0?control.displacementMultiplier():0;
                var start=vec(transform.getPosition());double requested=mode.distance(context.compiledPlan().geometry().impactForce());
                var plan=com.inigmasgames.hytalerpg.execution.area.AreaDisplacementPlanner.plan(start,center,mode.vacuum(),requested,scale,
                        npc.getRole()!=null&&npc.getRole().isOnGround(),(point,segment)->collisionFraction(store,reference,point,segment),
                        point->HytaleAreaQueries.ground(store,point.add(new Vec3(0,bounds.getBoundingBox().min.y()+.15,0)),new Vec3(0,-1,0),.35).isPresent());
                if(plan.distance()>0)transform.setPosition(vector(plan.destination()));
                emit(context,RpgTraceEventType.AREA_DISPLACEMENT,Map.of("passive",mode.vacuum()?"vacuum":"repulsion","targetId",victim.stableId(),"requested",requested,
                        "applied",plan.distance(),"reason",plan.reason(),"center",center.toString(),"nativeBehaviorVerified",false));
            }catch(RuntimeException failure){emit(context,RpgTraceEventType.AREA_DISPLACEMENT,Map.of("reason","PASSIVE_NATIVE_DISPLACEMENT_FAILED","error",failure.getClass().getSimpleName(),"paidRootRetained",true));}
        }
        private int executeStrikeHit(SkillExecutionContext context, int hitIndex) {
            StrikeGeometryService.QueryResult<Ref<EntityStore>> selected = select(context, context.profile().strike());
            Vec3 origin=vec(store.getComponent(actor,TransformComponent.getComponentType()).getPosition());
            Vec3 direction=context.target()==null?facing(store,actor):context.target().direction();
            try {
                NativeStrikeFeedback.play(store,actor,context,hitIndex);
                var shape=context.profile().strike();
                if(shape.geometry()==Stage04SkillProfile.Geometry.RADIUS)
                    shape=shape.withRange(shape.range()*context.compiledPlan().executionModifiers().radiusFactor());
                for(var footprint:StrikeGeometryService.footprints(origin,direction,shape))
                    vfx.presentArea(store.getExternalData().getWorld(),footprint,"IMPACT_STRIKE",shape.details().element(),false,.12);
            } catch(RuntimeException unavailable) {
                emit(context,RpgTraceEventType.AREA_PRESENTATION,Map.of("phase","STRIKE_FEEDBACK_UNAVAILABLE","connectedProof",false));
            }
            var primaryHits=new ArrayList<StrikeSecondaryRuntime.Hit<Ref<EntityStore>>>();
            int applied = 0;
            for (var target : selected.accepted()) {
                if (!hits.accept(context.skillInstanceId(), hitIndex, target.stableId())) continue;
                DamageOutcome outcome = damage(context, target, hitIndex,
                        context.profile().strike().coefficient(), context.snapshot().criticalChance(), strikeCause(context.profile().strike()),false,context.skillInstanceId(),!context.derivedRelease());
                primaryHits.add(new StrikeSecondaryRuntime.Hit<>(target,outcome.preMitigationDamage(),outcome.actualHealthLoss(),outcome.cancelled(),outcome.increasedUnit()));
                if(outcome.actualHealthLoss()>0)try{
                    vfx.presentContact(store.getExternalData().getWorld(),target.position().add(new Vec3(0,1,0)),
                            context.profile().strike().details().element(),.12,1);
                }catch(RuntimeException unavailable){emit(context,RpgTraceEventType.AREA_PRESENTATION,Map.of("phase","STRIKE_CONTACT_UNAVAILABLE","connectedProof",false));}
                emit(context, RpgTraceEventType.STRIKE_HIT,
                        Map.of("targetId", target.stableId(), "hitIndex", hitIndex,
                                "preMitigationDamage", outcome.preMitigationDamage(),
                                "actualHealthLoss", outcome.actualHealthLoss()));
                if (!outcome.cancelled()&&outcome.actualHealthLoss()>0&&!context.profile().strike().statusId().isBlank()) applyStatus(context, target);
                if(!outcome.cancelled()&&!context.compiledPlan().positionOnlyOnSecondary()&&ProfileComponentPolicy.enemyPosition(context.profile()))applyPassiveAreaPosition(context,target.handle(),origin);
                applied++;
            }
            try{new StrikeSecondaryRuntime().afterPrimary(context,hitIndex,origin,direction,primaryHits,new StrikeSecondaryRuntime.Port<Ref<EntityStore>>(){
                public List<StrikeGeometryService.Candidate<Ref<EntityStore>>> candidates(Vec3 center,double radius){
                    var shape=new AreaGeometry(AreaGeometry.Kind.DISC,center.add(new Vec3(0,-2.5,0)),Vec3.FORWARD,radius,360,0,0,5);
                    var queried=HytaleAreaQueries.query(store,actor,shape,64);
                    if(queried.overflow()){emit(context,RpgTraceEventType.STRIKE_SECONDARY_REJECTED,Map.of("reason","BOUNDED_SPATIAL_QUERY_OVERFLOW"));return List.of();}
                    return queried.candidates().stream().map(c->candidate(c.ref())).filter(java.util.Objects::nonNull).toList();
                }
                public boolean lineOfSight(Vec3 center,StrikeGeometryService.Candidate<Ref<EntityStore>> target){
                    return HytaleAreaQueries.clear(store,center.add(new Vec3(0,.5,0)),target.position().add(new Vec3(0,.5,0)));
                }
                public AreaGeometry.Bounds bounds(StrikeGeometryService.Candidate<Ref<EntityStore>> target){
                    var box=store.getComponent(target.handle(),BoundingBox.getComponentType()).getBoundingBox();
                    var point=vec(store.getComponent(target.handle(),TransformComponent.getComponentType()).getPosition());
                    return new AreaGeometry.Bounds(vec(box.min).add(point),vec(box.max).add(point));
                }
                public List<StrikeGeometryService.Candidate<Ref<EntityStore>>> burstCandidates(AreaGeometry shape){
                    var queried=HytaleAreaQueries.query(store,actor,shape,256);
                    if(queried.overflow()){rejected("shockwave","BOUNDED_SPATIAL_QUERY_OVERFLOW");return List.of();}
                    return queried.candidates().stream().map(c->candidate(c.ref())).filter(java.util.Objects::nonNull).toList();
                }
                public void presentCleave(Vec3 center,Vec3 facing,double range,double angle){
                    emit(context,RpgTraceEventType.AREA_PRESENTATION,Map.of("phase","IMPACT_CLEAVE","range",range,"angle",angle,"height",2.5,"seconds",.25,"connectedProof",false));
                    vfx.presentArea(store.getExternalData().getWorld(),new AreaGeometry(AreaGeometry.Kind.SECTOR,center,facing,range,angle,0,0,2.5),"IMPACT_CLEAVE","PHYSICAL",false,.25);
                }
                public void presentPhantom(Vec3 impact,Vec3 destination){
                    vfx.presentConnection(store.getExternalData().getWorld(),ConnectionShape.capsule(impact.add(new Vec3(0,.5,0)),destination.add(new Vec3(0,.5,0)),.12),"PHYSICAL","IMPACT_PHANTOM",.25);
                }
                public void presentShockwave(AreaGeometry shape){
                    emit(context,RpgTraceEventType.AREA_PRESENTATION,Map.of("phase","IMPACT_SHOCKWAVE","radius",shape.radius(),"height",shape.height(),"seconds",.25,"connectedProof",false));
                    vfx.presentArea(store.getExternalData().getWorld(),shape,"IMPACT_SHOCKWAVE","PHYSICAL",false,.25);
                }
                public void rejected(String effect,String reason){emit(context,RpgTraceEventType.STRIKE_SECONDARY_REJECTED,Map.of("effectInstanceId",effect,"reason",reason));}
                public void damage(SkillExecutionContext child,StrikeGeometryService.Candidate<Ref<EntityStore>> target,Double resolved){
                    damage(child,target,resolved,null);
                }
                public void damage(SkillExecutionContext child,StrikeGeometryService.Candidate<Ref<EntityStore>> target,Double resolved,Vec3 effectCenter){
                    // No second family dispatch, root commit, status/proc controller or native projectile.
                    emit(child,RpgTraceEventType.STRIKE_SECONDARY_DISPATCH,Map.of("kind",child.secondaryKind(),"parentExecutionId",context.skillInstanceId(),"targetId",target.stableId(),"resourceCharged",false,"canProc",false));
                    DamageOutcome result=resolved==null?Port.this.damage(child,target,0,child.profile().strike().coefficient(),child.snapshot().criticalChance(),strikeCause(child.profile().strike()),false,child.skillInstanceId(),false):resolvedStrikeSecondary(child,target,resolved);
                    if(!result.cancelled()&&effectCenter!=null)applyPassiveAreaPosition(child,target.handle(),effectCenter);
                    emit(child,RpgTraceEventType.STRIKE_SECONDARY_RESOLVED,Map.of("kind",child.secondaryKind(),"targetId",target.stableId(),"preMitigationDamage",result.preMitigationDamage(),"actualHealthLoss",result.actualHealthLoss(),"cancelled",result.cancelled(),"offenseRecalculated",resolved==null));
                }
            });}catch(RuntimeException failure){
                // Primary/earlier child may already have hit. Never unwind into a free paid root or retry a claimed effect.
                emit(context,RpgTraceEventType.STRIKE_SECONDARY_REJECTED,Map.of("reason","SECONDARY_NATIVE_ADAPTER_FAILED","error",failure.getClass().getSimpleName(),"paidRootRetained",true));
            }
            return applied;
        }
        private DamageOutcome resolvedStrikeSecondary(SkillExecutionContext child,StrikeGeometryService.Candidate<Ref<EntityStore>> target,double amount){
            boolean eligible=child.compiledPlan().resources().leeching()&&!target.protectedTarget()&&HytaleAreaQueries.hostile(store,target.handle(),actor);
            var nativeResult=new HytaleDamageAdapter().applyResolved(target.handle(),store,actor,strikeCause(child.profile().strike()),
                    new HytaleDamageMetadata(playerRef.getUuid(),child.rootCastId(),child.skillInstanceId(),child.request().correlationId(),amount,Double.NaN,child.skillInstanceId(),false,HytaleDamageMetadata.Origin.DIRECT),amount,null,child);
            if(eligible)recoverObservedLeech(child,target,nativeResult,child.skillInstanceId());
            double lost=Double.isFinite(nativeResult.healthBefore())&&Double.isFinite(nativeResult.healthAfter())?Math.max(0,nativeResult.healthBefore()-nativeResult.healthAfter()):-1;
            return new DamageOutcome(nativeResult.preMitigationAmount(),lost,nativeResult.cancelled());
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
            boolean nativeAllocationEntered=false;
            var instances=new ArrayList<ProjectileInstance>();var spawned=new ArrayList<ProjectileCarrier>();
            try {
                String weaponKind=context.equipment().mainHand().weaponKind(),configId=authored.configIdFor(weaponKind);
                double speed=authored.speedFor(weaponKind);
                Vec3 eye=vec(store.getComponent(actor,TransformComponent.getComponentType()).getPosition()).add(new Vec3(0,1.35,0));
                Vec3 direction=context.target()==null?aim(store,actor):context.target().point().subtract(eye).normalized();
                Vec3 origin=eye.add(direction.multiply(.65));
                if(authored.details().pattern().ballisticAim()) {
                    origin=eye.add(context.target().direction().multiply(.65));
                    direction=ballisticSolution(context.profile(),context.compiledPlan(),origin,context.target().point())
                            .orElseThrow(()->new IllegalStateException("BALLISTIC_TARGET_UNREACHABLE")).velocity().normalized();
                }
                long launchedAt=System.nanoTime();
                var plans=projectileService.buildBatch(context,playerRef.getUuid(),origin,direction,configId,speed,launchedAt);
                for(var plan:plans) {
                    if(!HytaleAreaQueries.clear(store,eye,origin.add(plan.velocity().normalized().multiply(.03))))
                        throw new IllegalStateException("PROJECTILE_MUZZLE_BLOCKED");
                    instances.add(new ProjectileInstance(plan));
                }
                // The full batch and all future Barrage/Echo promises are admitted atomically before ammo/native allocation.
                if(context.conditionalRepeat())projectileService.registry().registerConditionalAll(instances,context.effects().projectileLifetime());
                else projectileService.registry().registerAll(instances,context.effects().projectileLifetime());
                emit(context,RpgTraceEventType.AMMO_CHECK,Map.of("required",authored.requiresAmmo(),"itemId",authored.ammoItemId(),
                        "quantity",authored.ammoQuantity(),"available",true,"derivedRelease",context.derivedRelease()));
                if(!context.derivedRelease())ammo=ammunition.consume(actor,store,authored);
                if(authored.requiresAmmo()&&!context.derivedRelease())emit(context,RpgTraceEventType.AMMO_COMMITTED,
                        Map.of("itemId",ammo.itemId(),"quantity",ammo.quantity(),"fullyCharged",authored.fullyCharged()));
                for(var instance:instances) {
                    if(instance.plan().spawnTimestampNanos()>launchedAt)projectileLaunches.add(instance,new QueuedProjectile(context,actor));
                    else {nativeAllocationEntered=true;spawned.add(spawnProjectileCarrier(context,actor,instance,buffer));}
                }
                try{NativeStrikeFeedback.play(store,actor,context,0);}catch(RuntimeException unavailable){
                    emit(context,RpgTraceEventType.AREA_PRESENTATION,Map.of("phase","PROJECTILE_CAST_ANIMATION_UNAVAILABLE","connectedProof",false));
                }
                try{vfx.present(store.getExternalData().getWorld(),player,context.compiledPlan().vfxRecipeId());}
                catch(RuntimeException unavailable){emit(context,RpgTraceEventType.AREA_PRESENTATION,Map.of("phase","PROJECTILE_RECIPE_UNAVAILABLE","connectedProof",false));}
                return SkillExecutionResult.committed("PROJECTILE_STARTED",0,0);
            } catch(RuntimeException error) {
                for(var carrier:spawned) {
                    projectiles.remove(carrier.instance.plan().projectileInstanceId(),carrier);
                    if(carrier.projectile.isValid())buffer.tryRemoveEntity(carrier.projectile,RemoveReason.REMOVE);
                }
                for(var instance:instances){projectileLaunches.cancel(instance);projectileService.onForwardTermination(instance,"SPAWN_REJECTED",instance.plan().origin());}
                projectileService.registry().abandonLaunch(context.request().actorId(),context.rootCastId());
                if(ammo.quantity()>0&&!nativeAllocationEntered)ammunition.refund(actor,store,ammo);
                if(authored.requiresAmmo())emit(context,RpgTraceEventType.AMMO_REJECTED,
                        Map.of("reason",nativeAllocationEntered?"NATIVE_ALLOCATION_ENTERED_AMMO_RETAINED":"PRE_NATIVE_ALLOCATION_AMMO_ROLLBACK","error",error.getClass().getSimpleName()));
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
                direction=context.target().direction();
            }
            if(context!=null && strike.geometry()==Stage04SkillProfile.Geometry.RADIUS) {
                strike=strike.withRange(strike.range()*context.compiledPlan().executionModifiers().radiusFactor());
            }
            List<StrikeGeometryService.Candidate<Ref<EntityStore>>> candidates = strikeCandidates(origin, direction, strike);
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
            return damage(context,target,hitIndex,coefficient,criticalChance,cause,periodic,effectId,canProc,false);
        }
        private DamageOutcome damage(SkillExecutionContext context,
                StrikeGeometryService.Candidate<Ref<EntityStore>> target,int hitIndex,double coefficient,double criticalChance,
                DamageCause cause,boolean periodic,String effectId,boolean canProc,boolean frozenOutgoingSnapshot) {
            return damage(context,target,hitIndex,coefficient,criticalChance,cause,periodic,effectId,canProc,frozenOutgoingSnapshot,true);
        }
        private DamageOutcome damage(SkillExecutionContext context,
                StrikeGeometryService.Candidate<Ref<EntityStore>> target,int hitIndex,double coefficient,double criticalChance,
                DamageCause cause,boolean periodic,String effectId,boolean canProc,boolean frozenOutgoingSnapshot,boolean rootComponent) {
            double effective = effectiveAttribute(context);
            var buckets=context.snapshot().modifiers();
            if(support!=null)buckets=frozenOutgoingSnapshot?support.runtime().finite().victimModifiers(
                    playerRef.getWorldUuid(),playerRef.getUuid(),UUID.fromString(target.stableId()),buckets,System.nanoTime()/1e9):
                    support.runtime().finite().damageModifiers(playerRef.getWorldUuid(),playerRef.getUuid(),UUID.fromString(target.stableId()),buckets,System.nanoTime()/1e9);
            DamageCalculationService.Result result = kernel.damage().calculate(new DamageCalculationService.Request(
                    context.snapshot().basePower(), effective, coefficient,
                    buckets, !periodic, criticalChance,
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
            var targetHealth=targetStats==null?null:targetStats.get(DefaultEntityStatTypes.getHealth());
            double minimum=targetHealth==null?Double.NaN:targetHealth.getMin();
            boolean frozenBefore=context.compiledPlan().hitProcs().shatter()&&kernel.statuses().inspect(UUID.fromString(target.stableId())).active().containsKey(RpgStatusType.FROZEN);
            boolean procHostile=context.compiledPlan().hitProcs().active()&&HytaleAreaQueries.hostile(store,target.handle(),actor);
            boolean leechEligible=context.compiledPlan().resources().leeching()&&!target.protectedTarget()
                    &&HytaleAreaQueries.hostile(store,target.handle(),actor);
            var nativeResult = new HytaleDamageAdapter().applyObserved(target.handle(), store, actor, cause,
                    new HytaleDamageMetadata(playerRef.getUuid(), context.rootCastId(), context.skillInstanceId(),
                            context.request().correlationId(), result.preMitigationDamage(), Double.NaN,effectId,canProc,
                            context.derivedRelease()||context.request().origin()==SkillExecutionRequest.Origin.TRIGGERED?HytaleDamageMetadata.Origin.TRIGGERED:periodic?HytaleDamageMetadata.Origin.PERIODIC:HytaleDamageMetadata.Origin.DIRECT), result,
                    com.inigmasgames.hytalerpg.combat.damage.ConditionalDamage.calculated(context.compiledPlan().hitConditions(),buckets,result,context.snapshot().criticalMultiplier()),context);
            double after = health(targetStats);
            if(leechEligible)recoverObservedLeech(context,target,nativeResult,effectId);
            if(!context.compiledPlan().conditionalRepeat().isEmpty()){
                try{executions.observedConditionalRepeat(context,new ConditionalRepeatRuntime.Hit(UUID.fromString(target.stableId()),target.position(),rootComponent,
                        !periodic,canProc,result.critical(),HytaleAreaQueries.hostile(store,target.handle(),actor),target.protectedTarget(),nativeResult.cancelled(),before,after,minimum),this);}
                catch(RuntimeException failed){emit(context,RpgTraceEventType.CONDITIONAL_REPEAT_RESOLVED,Map.of("verdict","NATIVE_RECEIPT_UNAVAILABLE","paidRootRetained",true));}
            }
            if(context.compiledPlan().hitProcs().active()){
                String element=procElement(context,cause);
                var receipt=new HitProcRuntime.Hit(UUID.fromString(target.stableId()),effectId+"/"+hitIndex,element,!periodic,canProc,procHostile,target.protectedTarget(),target.boss(),false,
                        frozenBefore,nativeResult.cancelled(),before,after,minimum,nativeResult.preMitigationAmount(),HitProcRuntime.coefficient(context),result.increasedUnit(buckets,context.snapshot().criticalMultiplier()));
                try{hitProcs.observed(context,receipt,System.nanoTime()/1e9,hitProcPort(target));}
                catch(RuntimeException failure){emit(context,RpgTraceEventType.HIT_PROC_RESOLVED,Map.of("verdict","PROC_OBSERVER_FAILED","boundary",String.valueOf(failure.getMessage()),"paidRootRetained",true));}
            }
            return new DamageOutcome(nativeResult.preMitigationAmount(),
                    Double.isFinite(before) && Double.isFinite(after) ? Math.max(0.0, before - after) : -1.0,
                    nativeResult.cancelled(),result.increasedUnit(buckets,context.snapshot().criticalMultiplier()));
        }
        private void recoverObservedLeech(SkillExecutionContext context,StrikeGeometryService.Candidate<Ref<EntityStore>> target,HytaleDamageAdapter.NativeResult nativeResult,String effectId){
            var recovered=kernel.resources().recoverLeech(context.leechBudget(),
                    new com.inigmasgames.hytalerpg.combat.resource.RootLeechBudget.HitReceipt(nativeResult.healthBefore(),nativeResult.healthAfter(),nativeResult.cancelled(),true,false),resources());
            emit(context,RpgTraceEventType.RESOURCE_RECOVERY,Map.of("source","LEECHING","resource",context.leechBudget().resource(),
                    "gate",recovered.gate(),"actualHealthLoss",recovered.healthLost(),"requested",recovered.requested(),"actualRestored",recovered.restored(),
                    "totalRootRestored",recovered.totalRestored(),"rootCap",recovered.rootCap(),"effectInstanceId",effectId,"targetId",target.stableId()));
        }
        private String procElement(SkillExecutionContext c,DamageCause cause){
            if(cause==DamageCause.PHYSICAL)return "PHYSICAL";
            if(cause==DamageCause.getAssetMap().getAsset("Ice"))return "COLD";
            if(cause==DamageCause.PROJECTILE){
                if(c.compiledPlan().finalTags().contains("PHYSICAL"))return "PHYSICAL";
                if(c.compiledPlan().finalTags().contains("COLD"))return "COLD";
            }
            return "OTHER";
        }
        private DamageOutcome resolvedProcDamage(SkillExecutionContext child,StrikeGeometryService.Candidate<Ref<EntityStore>> target,double amount,DamageCause cause,boolean periodic,String effect){
            var result=new HytaleDamageAdapter().applyResolved(target.handle(),store,actor,cause,new HytaleDamageMetadata(playerRef.getUuid(),child.rootCastId(),child.skillInstanceId(),child.request().correlationId(),amount,Double.NaN,effect,false,
                    child.derivedRelease()||child.request().origin()==SkillExecutionRequest.Origin.TRIGGERED?HytaleDamageMetadata.Origin.TRIGGERED:periodic?HytaleDamageMetadata.Origin.PERIODIC:HytaleDamageMetadata.Origin.DIRECT),amount,null,child);
            if(child.compiledPlan().resources().leeching()&&HytaleAreaQueries.hostile(store,target.handle(),actor))recoverObservedLeech(child,target,result,effect);
            return new DamageOutcome(result.preMitigationAmount(),Math.max(0,result.healthBefore()-result.healthAfter()),result.cancelled());
        }
        private HitProcRuntime.Port hitProcPort(StrikeGeometryService.Candidate<Ref<EntityStore>> struck){
            return new HitProcRuntime.Port(){
                public String bleed(SkillExecutionContext child,HitProcRuntime.Hit hit,double dps,double seconds){
                    if(!struck.handle().isValid()||candidate(struck.handle())==null)return "BLEED_TARGET_NO_LONGER_ALIVE";
                    var source=new PeriodicStatusRuntime.Source(playerRef.getUuid(),child.profile().skillId(),hit.victim(),PeriodicStatusRuntime.Kind.BLEED);
                    return periodicStatuses.apply(source,child,new PeriodicTarget(actor,struck.handle()),dps,dps,seconds,1,1,System.nanoTime()/1e9,periodicPort());
                }
                public String fear(SkillExecutionContext child,HitProcRuntime.Hit hit,double seconds){
                    if(support==null)return "TERROR_NATIVE_SUPPORT_UNAVAILABLE";
                    var current=candidate(struck.handle());if(current==null)return "TERROR_TARGET_NO_LONGER_ALIVE";
                    var effect=HitProcProfiles.fear(child,hit.victim(),playerRef.getWorldUuid(),vec(store.getComponent(actor,TransformComponent.getComponentType()).getPosition()),current.position(),seconds);
                    support.port(store,actor,buffer).finiteEffect(effect,support.runtime().finite(),System.nanoTime()/1e9);return "TERROR_APPLIED";
                }
                public String shatter(SkillExecutionContext child,HitProcRuntime.Hit hit,double radius,double amount){
                    var cause=DamageCause.getAssetMap().getAsset("Ice");if(cause==null)return "SHATTER_NATIVE_COLD_CAUSE_MISSING";
                    var center=struck.position();var shape=new AreaGeometry(AreaGeometry.Kind.DISC,center.add(new Vec3(0,-1,0)),Vec3.FORWARD,radius,0,0,0,3);
                    var query=HytaleAreaQueries.query(store,actor,shape,256);if(query.overflow())return "SHATTER_CANDIDATE_BUDGET";
                    var accepted=query.candidates().stream().filter(t->HytaleAreaQueries.clear(store,center.add(new Vec3(0,.1,0)),t.bounds().centre()))
                            .map(t->candidate(t.ref())).filter(java.util.Objects::nonNull).filter(t->!t.protectedTarget()&&!t.stableId().equals(hit.victim().toString()))
                            .sorted(java.util.Comparator.comparing(t->t.stableId())).toList();
                    if(accepted.size()>64)return "SHATTER_TARGET_BUDGET";
                    int count=0;for(var target:accepted){if(candidate(target.handle())==null||!HytaleAreaQueries.hostile(store,target.handle(),actor))continue;
                        var result=resolvedProcDamage(child,target,amount,cause,false,child.skillInstanceId()+"/"+count++);
                        if(!result.cancelled())applyPassiveAreaPosition(child,target.handle(),center);
                        emit(child,RpgTraceEventType.HIT_PROC_RESOLVED,Map.of("kind","shatter","targetId",target.stableId(),"preMitigationDamage",result.preMitigationDamage(),"actualHealthLoss",result.actualHealthLoss(),"cancelled",result.cancelled(),"canProc",false));}
                    try{vfx.presentConnection(store.getExternalData().getWorld(),ConnectionShape.cylinder(center,radius,3),"COLD","SHATTER",.3);}catch(RuntimeException ignored){}
                    return "SHATTER_RESOLVED_TARGETS="+count;
                }
                public void trace(SkillExecutionContext c,String kind,String verdict,double value){emit(c,RpgTraceEventType.HIT_PROC_RESOLVED,Map.of("kind",kind,"verdict",verdict,"value",value,"targetId",struck.stableId(),"paidRootRetained",true));}
            };
        }
        private void applyStatus(SkillExecutionContext context,
                                 StrikeGeometryService.Candidate<Ref<EntityStore>> target) {
            observeControl(store,target.handle(),context,()->applyStatusObserved(context,target));
        }
        private void applyStatusObserved(SkillExecutionContext context,
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
        private List<StrikeGeometryService.Candidate<Ref<EntityStore>>> strikeCandidates(Vec3 origin, Vec3 facing, Stage04SkillProfile.Strike strike) {
            var shapes=StrikeGeometryService.footprints(origin,facing,strike);
            var query = HytaleAreaQueries.query(store, actor,
                    bounds -> shapes.stream().anyMatch(shape->shape.intersects(bounds)), StrikeGeometryService.ORDINARY_QUERY_LIMIT);
            if (query.overflow()) throw new IllegalStateException("STRIKE_QUERY_OVERFLOW");
            List<StrikeGeometryService.Candidate<Ref<EntityStore>>> result = new ArrayList<>();
            for (var value : query.candidates()) {
                var candidate = candidate(value.ref());
                if (candidate != null && HytaleAreaQueries.clear(store, origin.add(new Vec3(0, 1.35, 0)), value.bounds().centre())) result.add(candidate);
            }
            return result;
        }

        private StrikeGeometryService.Candidate<Ref<EntityStore>> candidate(Ref<EntityStore> target) {
            if (target == null || !target.isValid() || target.equals(actor)) return null;
            NPCEntity npc = store.getComponent(target, NPCEntity.getComponentType());
            TransformComponent targetTransform = store.getComponent(target, TransformComponent.getComponentType());
            EntityStatMap targetStats = store.getComponent(target, EntityStatMap.getComponentType());
            if (npc == null || targetTransform == null || targetStats == null || !HytaleAreaQueries.hostile(store,target,actor)) return null;
            var targetHealth = targetStats.get(DefaultEntityStatTypes.getHealth());
            if (targetHealth == null || targetHealth.get() <= targetHealth.getMin()) return null;
            UUIDComponent uuid = store.getComponent(target, UUIDComponent.getComponentType());
            var bounding = store.getComponent(target, com.hypixel.hytale.server.core.modules.entity.component.BoundingBox.getComponentType());
            if (uuid == null || bounding == null) return null;
            UUID id = uuid.getUuid();
            boolean protectedTarget = store.getComponent(target, Invulnerable.getComponentType()) != null
                    || npc.getRole() != null && npc.getRole().isInvulnerable();
            EffectControllerComponent effects = store.getComponent(target, EffectControllerComponent.getComponentType());
            protectedTarget |= effects != null && effects.isInvulnerable();
            NetworkId networkId = store.getComponent(target, NetworkId.getComponentType());
            boolean boss = networkId != null && bosses.isBoss(playerRef.getWorldUuid(), networkId.getId());
            return new StrikeGeometryService.Candidate<>(id.toString(), target,
                    vec(targetTransform.getPosition()), true, protectedTarget, boss,
                    new AreaGeometry.Bounds(vec(bounding.getBoundingBox().min).add(vec(targetTransform.getPosition())),
                            vec(bounding.getBoundingBox().max).add(vec(targetTransform.getPosition()))));
        }

        private boolean applyProjectilePeriodicStatus(SkillExecutionContext context,
                StrikeGeometryService.Candidate<Ref<EntityStore>> target) {
            var profile = context.profile().projectile();
            return applyPeriodicStatus(context, target, PeriodicStatusRuntime.Kind.valueOf(profile.statusId()),
                    profile.statusSeconds(), profile.periodicCoefficient() / profile.periodicIntervalSeconds());
        }

        private boolean applyPeriodicStatus(SkillExecutionContext context,
                StrikeGeometryService.Candidate<Ref<EntityStore>> target,
                PeriodicStatusRuntime.Kind kind, double duration, double coefficientPerSecond) {
            var application=context.compiledPlan().dots().application(kind,coefficientPerSecond);
            coefficientPerSecond=application.coefficientPerSecond();
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
            var outgoing=support==null?context.snapshot().modifiers():support.runtime().finite().outgoingModifiers(
                    playerRef.getWorldUuid(),playerRef.getUuid(),context.snapshot().modifiers(),now);
            double strength = kernel.damage().calculate(DamageCalculationService.Request.periodic(
                    context.snapshot().basePower(), effectiveAttribute(context), coefficientPerSecond,
                    outgoing,
                    0, context.snapshot().criticalMultiplier())).preMitigationDamage();
            var captured=context.withSnapshot(context.snapshot().withModifiers(outgoing));
            String result = periodicStatuses.apply(source, captured, new PeriodicTarget(actor, target.handle()),
                    coefficientPerSecond, strength, duration, application.addedStacks(), application.sourceCap(), now, periodicPort());
            boolean accepted = result.equals("APPLIED") || result.equals("REFRESHED");
            var details=new java.util.LinkedHashMap<String,Object>(Map.of("status", kind, "targetId", targetId, "durationSeconds", duration, "result", result,
                            "authority", "RPG_SOURCE_PACKAGE", "nativeBehaviorVerified", false,
                            "coefficientPerSecond",coefficientPerSecond,"requestedAddedStacks",application.addedStacks(),"requestedSourceCap",application.sourceCap()));
            periodicStatuses.sourceView(source,now).ifPresent(v->{details.put("retainedSourceCap",v.sourceCap());details.put("retainedSourceStacks",v.stacks());details.put("retainedCoefficientPerSecond",v.coefficientPerSecond());});
            emit(context, accepted ? RpgTraceEventType.STATUS_APPLIED : RpgTraceEventType.STATUS_REJECTED,details);
            return accepted;
        }

        private String applyProjectileStatus(SkillExecutionContext context,
                                             StrikeGeometryService.Candidate<Ref<EntityStore>> target) {
            Stage04SkillProfile.Projectile projectile = context.profile().projectile();
            RpgStatusType type = RpgStatusType.valueOf(projectile.statusId());
            UUID targetId = UUID.fromString(target.stableId());
            emit(context, RpgTraceEventType.STATUS_REQUEST, Map.of("targetId", target.stableId(),
                    "status", type.name(), "durationSeconds", projectile.statusSeconds()));
            var control = SupportNativeEffects.control(store,target.handle(),bosses);
            if(buffer==null || !HytaleAreaStatuses.available()
                    || store.getComponent(target.handle(),EffectControllerComponent.getComponentType())==null){
                emit(context,RpgTraceEventType.STATUS_REJECTED,Map.of("targetId",targetId,"status",type.name(),"reason","PROJECTILE_NATIVE_STATUS_ADAPTER_UNAVAILABLE"));
                return "PROJECTILE_NATIVE_STATUS_ADAPTER_UNAVAILABLE";
            }
            var nativeBefore=HytaleAreaStatuses.observed(store,target.handle());
            if(type==RpgStatusType.CHILL){
                var batch=HytaleAreaStatuses.applyChill(kernel,context,targetId,control,projectile.details().chillStacks(),(event,details)->emit(context,event,details),chillSources);
                buffer.ensureComponent(target.handle(),AreaStatusProjection.getComponentType());
                HytaleAreaStatuses.synchronize(kernel.statuses(),targetId,target.handle(),store,actor);
                if(support!=null&&HytaleAreaStatuses.observed(store,target.handle()).improvedFrom(nativeBefore))
                    support.controlResolved(store,target.handle(),context,false,"NATIVE_EFFECT_STATE_CHANGED");
                var result=batch.results().getLast();return result.outcome().name()+':'+result.type().name()+":stacks="+result.stacks();
            }
            var npc=store.getComponent(target.handle(),NPCEntity.getComponentType());
            var result = type==RpgStatusType.ROOT
                    ? com.inigmasgames.hytalerpg.execution.projectile.ProjectileControlPolicy.root(kernel.statuses(),projectile,
                            targetId,context.rootCastId(),npc==null?"":npc.getRoleName(),control)
                    : projectile.statusSeconds() > 0.0
                    ? kernel.statuses().apply(targetId, type, control, projectile.statusSeconds())
                    : kernel.statuses().apply(targetId, type, control);
            if(result.outcome()!=com.inigmasgames.hytalerpg.combat.status.StatusService.Outcome.REJECTED){
                buffer.ensureComponent(target.handle(),AreaStatusProjection.getComponentType());
                HytaleAreaStatuses.synchronize(kernel.statuses(),targetId,target.handle(),store,actor);
                if(support!=null&&HytaleAreaStatuses.observed(store,target.handle()).improvedFrom(nativeBefore))
                    support.controlResolved(store,target.handle(),context,false,"NATIVE_EFFECT_STATE_CHANGED");
            }
            RpgTraceEventType event = switch (result.outcome()) {
                case APPLIED -> RpgTraceEventType.STATUS_APPLIED;
                case REFRESHED -> RpgTraceEventType.STATUS_REFRESHED;
                case THRESHOLD -> RpgTraceEventType.STATUS_THRESHOLD;
                case REJECTED -> RpgTraceEventType.STATUS_REJECTED;
            };
            trace.emit(playerRef.getUuid(), event, ids(context), Map.of("targetId", target.stableId(),
                    "status", result.type().name(), "stacks", result.stacks(),
                    "durationSeconds", result.remainingSeconds(), "detail", result.detail(),"nativeBehaviorVerified",false));
            return result.outcome().name() + ':' + result.type().name() + ":stacks=" + result.stacks();
        }

        private double applyProjectileKnockback(SkillExecutionContext context,
                                                StrikeGeometryService.Candidate<Ref<EntityStore>> target) {
            double requested = context.profile().projectile().knockbackDistance();
            if (requested <= 0.0 || target.protectedTarget() || !target.handle().isValid()) return 0.0;
            TransformComponent sourceTransform = store.getComponent(actor, TransformComponent.getComponentType());
            TransformComponent targetTransform = store.getComponent(target.handle(), TransformComponent.getComponentType());
            var npc=store.getComponent(target.handle(),NPCEntity.getComponentType());
            var bounds=store.getComponent(target.handle(),BoundingBox.getComponentType());
            if (sourceTransform == null || targetTransform == null || npc == null || bounds == null) return 0.0;
            var control=SupportNativeEffects.control(store,target.handle(),bosses);
            double scale=npc.getRole()!=null&&npc.getRole().getKnockbackScale()>0?control.displacementMultiplier():0;
            Vec3 start=vec(targetTransform.getPosition());
            var plan=com.inigmasgames.hytalerpg.execution.area.AreaDisplacementPlanner.plan(start,vec(sourceTransform.getPosition()),false,
                    requested,scale,npc.getRole()!=null&&npc.getRole().isOnGround(),
                    (point,segment)->collisionFraction(store,target.handle(),point,segment),
                    point->HytaleAreaQueries.ground(store,point.add(new Vec3(0,bounds.getBoundingBox().min.y()+.15,0)),new Vec3(0,-1,0),.35).isPresent());
            if(plan.distance()>0)targetTransform.setPosition(vector(plan.destination()));
            double observed=vec(targetTransform.getPosition()).subtract(start).horizontalLength();
            emit(context,RpgTraceEventType.AREA_DISPLACEMENT,Map.of("source","PROJECTILE","targetId",target.stableId(),
                    "requested",requested,"planned",plan.distance(),"applied",observed,"reason",plan.reason(),"nativeBehaviorVerified",false));
            return observed;
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
        boolean multi=repeating.context.compiledPlan().strikes().multistrike();
        boolean locked=NativeStrikeActionLock.required(repeating.context);
        if(locked&&repeating.schedule.exceededMaximumAge(now,Math.max(1,repeating.context.profile().strike().details().actionLockSeconds()+.5))){
            repeatingStrikes.remove(playerRef.getUuid());hits.clear(repeating.context.skillInstanceId());NativeStrikeActionLock.clear(store,ref);
            executions.terminate(repeating.context,"STRIKE_STALE_SEQUENCE_CANCELLED");return;
        }
        try{
            for (var due = repeating.schedule.claimDue(now); due.isPresent(); due = repeating.schedule.claimDue(now)){
                if(multi){
                    var child=repeating.context.multistrikeCopy(due.getAsInt());
                    String admission=child.effects().claim(child.skillInstanceId(),1,false);
                    if(!admission.equals("PASS"))throw new IllegalStateException(admission);
                    emit(child,RpgTraceEventType.EXECUTOR_DISPATCH,Map.of("family","STRIKE","multistrikeIndex",due.getAsInt(),"resourceCharged",false,"canProc",false));
                    try{port.executeStrikeHit(child,0);}finally{hits.clear(child.skillInstanceId());}
                }else port.executeStrikeHit(repeating.context,due.getAsInt());
            }
        }catch(RuntimeException failed){
            repeatingStrikes.remove(playerRef.getUuid());hits.clear(repeating.context.skillInstanceId());if(locked)NativeStrikeActionLock.clear(store,ref);
            executions.terminate(repeating.context,"STRIKE_REPEAT_ADAPTER_FAILED");return;
        }
        if (repeating.schedule.complete(now)) {
            repeatingStrikes.remove(playerRef.getUuid());
            if(locked)NativeStrikeActionLock.clear(store,ref);
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
        if(expireContinuationClock(carrier,buffer))return;
        if(!hasContinuations(carrier.context)&&!carrier.instance.contactWithinRange(vec(position))) {
            Vec3 end=ProjectileSweep.limit(carrier.instance.flight().lastPosition(),vec(position),carrier.instance.remainingDistance());
            carrier.instance.observe(0,end);finishProjectileTerminal(carrier,"MAX_RANGE",end,buffer);return;
        }
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
                DamageOutcome outcome = authored.coefficient()>0?port.damage(carrier.context, target, 0, authored.coefficient(),
                        carrier.context.snapshot().criticalChance(), NativeProjectilePayloads.cause(carrier.context.profile().projectile()),false,projectileId,true,false,carrier.instance.plan().generation()==0&&!carrier.instance.returning())
                        :new DamageOutcome(0,0,false);
                if(outcome.actualHealthLoss()>0&&carrier.context.effects().projectileVisuals().impact(target.stableId(),System.nanoTime()))projectileVisual(carrier,store,()->vfx.presentContact(store.getExternalData().getWorld(),vec(position),
                        authored.details().element(),.12,Math.max(1,Math.min(2,authored.details().chillStacks()))));
                String statusResult = "NONE";
                if (outcome.actualHealthLoss() > 0.0 && !authored.statusId().isBlank() && authored.details().explosion().burnRadius()==0)
                    statusResult = authored.hasPeriodicStatus()
                            ? (port.applyProjectilePeriodicStatus(carrier.instance.plan().generation()==0&&!carrier.instance.returning()?carrier.context:carrier.context.projectileStatusChild(projectileId), target) ? authored.statusId()+"_APPLIED" : authored.statusId()+"_REJECTED")
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
                if(authored.details().explosion().active())applyAuthoredExplosion(carrier.context,port,vec(position),projectileId+"/impact/"+target.stableId(),carrier.instance.plan().generation()==0&&!carrier.instance.returning());
                applyShrapnel(carrier,port,vec(position),outcome.actualHealthLoss());
                if(continues) {
                    var decision=continuations.afterEnemy(carrier.instance,vec(position),casterPoint(carrier,store),
                            chainCandidates(carrier,vec(position),port),System.nanoTime());
                    applyContinuation(carrier,decision,vec(position),buffer);return;
                }
                projectileService.onForwardTermination(carrier.instance, "ENTITY_HIT", vec(position));
                emitProjectile(carrier, RpgTraceEventType.PROJECTILE_TERMINATED,
                        Map.of("reason", "ENTITY_HIT", "travelledDistance", carrier.instance.flight().travelled()));
                finishProjectileContext(carrier.context, "PROJECTILE_ENTITY_HIT");
            } catch (RuntimeException error) {
                projectiles.remove(projectileId,carrier);
                if(projectileRef!=null && projectileRef.isValid())buffer.tryRemoveEntity(projectileRef,RemoveReason.REMOVE);
                projectileService.onForwardTermination(carrier.instance, "PAYLOAD_FAILURE", vec(position));
                emitProjectile(carrier, RpgTraceEventType.PROJECTILE_CANCELLED,
                        Map.of("reason", "PAYLOAD_FAILURE", "error", error.getClass().getSimpleName()));
                emitProjectile(carrier, RpgTraceEventType.PROJECTILE_TERMINATED,
                        Map.of("reason", "PAYLOAD_FAILURE"));
                finishProjectileContext(carrier.context, "PROJECTILE_PAYLOAD_FAILURE");
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
        if(carrier.context.profile().projectile().details().explosion().active()) {
            try {
                var explosion=carrier.context.profile().projectile().details().explosion();
                if(explosion.groundFuseSeconds()>0) {
                    carrier.detonation.contact(vec(position),false,System.nanoTime()/1e9);
                    projectiles.remove(projectileId,carrier);
                    if(projectileRef.isValid())buffer.tryRemoveEntity(projectileRef,RemoveReason.REMOVE);
                    projectileFuses.put(projectileId,new GroundedProjectile(carrier,vec(position)));
                    projectileVisual(carrier,store,()->vfx.presentArea(store.getExternalData().getWorld(),
                            explosionGeometry(carrier.context,vec(position)),"AUTHORED_FUSE",carrier.context.profile().projectile().details().element(),false,explosion.groundFuseSeconds()));
                    emitProjectile(carrier,RpgTraceEventType.AREA_PRESENTATION,Map.of("phase","FUSE_ARMED","seconds",explosion.groundFuseSeconds(),"nativeBehaviorVerified",false));
                    return;
                }
                applyAuthoredExplosion(carrier.context,port,vec(position),projectileId+"/terrain",carrier.instance.plan().generation()==0&&!carrier.instance.returning());
            }catch(RuntimeException failure){cancelProjectile(carrier,"EXPLOSION_PAYLOAD_FAILURE",vec(position),buffer);return;}
        }
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
        finishProjectileContext(carrier.context, "PROJECTILE_TERRAIN_HIT");
    }

    private void advanceProjectiles(UUID actorId, float deltaSeconds, Store<EntityStore> store,
                                    CommandBuffer<EntityStore> buffer) {
        advanceAuthoredProjectiles(actorId,store,buffer);
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
                    finishProjectileContext(carrier.context, "PROJECTILE_NATIVE_REMOVAL");
                }
                continue;
            }
            TransformComponent transform = store.getComponent(carrier.projectile, TransformComponent.getComponentType());
            if (transform == null) {cancelProjectile(carrier,"PROJECTILE_TRANSFORM_MISSING",null,buffer);continue;}
            carrier.visuals.sample(vec(transform.getPosition()),System.nanoTime()).ifPresent(segment->
                    projectileVisual(carrier,store,()->vfx.presentProjectileTrail(store.getExternalData().getWorld(),segment,carrier.context.profile().projectile().details().element())));
            if(hasContinuations(carrier.context)) {advanceModifiedProjectile(carrier,vec(transform.getPosition()),buffer);continue;}
            if(expireContinuationClock(carrier,buffer))continue;
            ProjectileFlight.Observation observation = carrier.instance.observe(0,ProjectileSweep.limit(carrier.instance.flight().lastPosition(),vec(transform.getPosition()),carrier.instance.remainingDistance()));
            if (!observation.expired()) continue;
            if(carrier.context.profile().projectile().details().explosion().detonateAtAirborneLimit()) {
                try{carrier.detonation.tick(observation.position(),System.nanoTime()/1e9,true).ifPresent(point->
                        applyAuthoredExplosion(carrier.context,projectilePort(carrier,store,buffer),point,projectileId+"/airborne-limit",carrier.instance.plan().generation()==0));}
                catch(RuntimeException failure){cancelProjectile(carrier,"AIRBORNE_EXPLOSION_FAILURE",observation.position(),buffer);continue;}
            }
            if(!projectiles.remove(projectileId,carrier))continue;
            buffer.tryRemoveEntity(carrier.projectile, RemoveReason.REMOVE);
            boolean range = observation.travelled() + 1.0e-6 >= observation.maxDistance();
            String reason = range ? "MAX_RANGE" : "MAX_LIFETIME";
            projectileService.onForwardTermination(carrier.instance, reason, observation.position());
            projectileVisual(carrier,store,()->vfx.presentProjectileExpiry(store.getExternalData().getWorld(),observation.position(),carrier.context.profile().projectile().details().element()));
            emitProjectile(carrier, range ? RpgTraceEventType.PROJECTILE_MAX_RANGE : RpgTraceEventType.PROJECTILE_EXPIRED,
                    Map.of("reason", reason, "travelledDistance", observation.travelled(),
                            "elapsed", observation.elapsed(), "maxDistance", observation.maxDistance(),
                            "maximumLifetimeSeconds", observation.maxLifetimeSeconds(),
                            "positionX", observation.position().x(), "positionY", observation.position().y(),
                            "positionZ", observation.position().z()));
            emitProjectile(carrier, RpgTraceEventType.PROJECTILE_TERMINATED,
                    Map.of("reason", reason, "travelledDistance", observation.travelled()));
            finishProjectileContext(carrier.context, "PROJECTILE_" + reason);
        }
    }

    private Port projectilePort(ProjectileCarrier carrier,Store<EntityStore> store,CommandBuffer<EntityStore> buffer) {
        if(!carrier.actor.isValid()||carrier.actor.getStore()!=store)throw new IllegalStateException("PROJECTILE_OWNER_REMOVED");
        var ref=store.getComponent(carrier.actor,PlayerRef.getComponentType());var player=store.getComponent(carrier.actor,Player.getComponentType());
        var stats=store.getComponent(carrier.actor,EntityStatMap.getComponentType());
        if(ref==null||player==null||stats==null||!ref.getWorldUuid().equals(carrier.context.target().worldId()))throw new IllegalStateException("PROJECTILE_OWNER_WORLD_CHANGED");
        var port=new Port(store,carrier.actor,ref,player,stats,null,buffer);
        if(!port.actorAliveAndUsable())throw new IllegalStateException("PROJECTILE_OWNER_NOT_ALIVE");
        return port;
    }
    private void advanceAuthoredProjectiles(UUID actorId,Store<EntityStore> store,CommandBuffer<EntityStore> buffer) {
        long now=System.nanoTime();
        for(var due:projectileLaunches.due(actorId,now)) {
            var c=due.owner();var instance=due.instance();
            try {
                if(!c.actor.isValid()||c.actor.getStore()!=store||!HytaleSummonSystem.alive(store,c.actor))throw new IllegalStateException("SCHEDULED_OWNER_UNAVAILABLE");
                if(!HytaleAreaQueries.projectileClear(store,instance.plan().origin(),instance.plan().origin().add(instance.direction().multiply(.03)),instance.plan().radius()))
                    throw new IllegalStateException("SCHEDULED_MUZZLE_BLOCKED");
                if(now-instance.plan().spawnTimestampNanos()>100_000_000L)throw new IllegalStateException("AUTHORED_LAUNCH_DEADLINE_MISSED");
                spawnProjectileCarrier(c.context,c.actor,instance,buffer);
            }catch(RuntimeException failure){
                projectileService.onForwardTermination(instance,"SCHEDULED_PROJECTILE_REJECTED",instance.plan().origin());
                emit(c.context,RpgTraceEventType.PROJECTILE_SPAWN_REJECTED,Map.of("projectileInstanceId",instance.plan().projectileInstanceId(),"reason",String.valueOf(failure.getMessage()),"resourceChargedAgain",false));
                finishProjectileContext(c.context,"SCHEDULED_PROJECTILE_REJECTED");
            }
        }
        for(var ground:projectileFuses.values().stream().filter(g->g.carrier.actorId.equals(actorId)).toList()) {
            var carrier=ground.carrier;String id=carrier.instance.plan().projectileInstanceId();
            try {
                var port=projectilePort(carrier,store,buffer);
                java.util.Optional<Vec3> due=java.util.Optional.empty();
                if(ground.sampleContacts(now)) {
                    double radius=carrier.instance.plan().radius();
                    var contacts=HytaleAreaQueries.query(store,carrier.actor,
                            b->ConnectionShape.pointDistanceSquared(ground.point,b)<=radius*radius+1e-9,64);
                    if(contacts.overflow())throw new IllegalStateException("GROUND_BOMB_CONTACT_BUDGET");
                    for(var contact:contacts.candidates()) {
                        var target=port.candidate(contact.ref());
                        if(target!=null&&!target.protectedTarget()&&HytaleAreaQueries.clear(store,ground.point,contact.bounds().centre())) {
                            due=carrier.detonation.contact(ground.point,true,now/1e9);break;
                        }
                    }
                }
                if(due.isEmpty())due=carrier.detonation.tick(ground.point,now/1e9,false);
                if(due.isEmpty())continue;
                if(!projectileFuses.remove(id,ground))continue;
                try {applyAuthoredExplosion(carrier.context,port,due.get(),id+"/ground-fuse",carrier.instance.plan().generation()==0);}
                finally{projectileService.onForwardTermination(carrier.instance,"FUSE_COMPLETE",ground.point);finishProjectileContext(carrier.context,"FUSE_COMPLETE");}
            }catch(RuntimeException failure){
                projectileFuses.remove(id,ground);carrier.detonation.cancel();
                projectileService.onForwardTermination(carrier.instance,"FUSE_CANCELLED",ground.point);
                emitProjectile(carrier,RpgTraceEventType.PROJECTILE_CANCELLED,Map.of("reason","FUSE_CANCELLED","boundary",String.valueOf(failure.getMessage())));
                finishProjectileContext(carrier.context,"FUSE_CANCELLED");
            }
        }
    }
    private static AreaGeometry explosionGeometry(SkillExecutionContext context,Vec3 point) {
        double radius=context.profile().projectile().details().explosion().radius()*context.compiledPlan().executionModifiers().radiusFactor();
        return new AreaGeometry(AreaGeometry.Kind.DISC,point.add(new Vec3(0,-radius,0)),Vec3.FORWARD,radius,360,0,0,radius*2);
    }
    /** One deterministic target ledger for one authored impact component; uses the existing native damage/stat authority. */
    private double applyAuthoredExplosion(SkillExecutionContext context,Port port,Vec3 point,String effect,boolean primary) {
        var authored=context.profile().projectile();var explosion=authored.details().explosion();if(!explosion.active())return 0;
        var shape=explosionGeometry(context,point);var query=HytaleAreaQueries.query(port.store,port.actor,
                bounds->ConnectionShape.pointDistanceSquared(point,bounds)<=shape.radius()*shape.radius()+1e-9,64);
        if(query.overflow())throw new IllegalStateException("AUTHORED_EXPLOSION_CANDIDATE_BUDGET");
        // This is an authored contact component, not another trigger controller every Orbit tick.
        // Projectile acceptTarget / ConnectionRuntime contact ledgers own per-contact deduplication.
        String claim=context.effects().authoredComponent(context.skillInstanceId()+"/authored-projectile-explosion");
        if(!claim.equals("PASS"))throw new IllegalStateException("AUTHORED_EXPLOSION_"+claim);
        try{vfx.presentArea(port.store.getExternalData().getWorld(),shape,"AUTHORED_PROJECTILE_EXPLOSION",authored.details().element(),false,.25);}
        catch(RuntimeException ignored){emit(context,RpgTraceEventType.AREA_PRESENTATION,Map.of("phase","EXPLOSION_TEMPLATE_UNAVAILABLE","connectedProof",false));}
        int index=0;double actualHealthLost=0;
        for(var candidate:query.candidates().stream().sorted(java.util.Comparator.comparing(c->port.store.getComponent(c.ref(),UUIDComponent.getComponentType()).getUuid().toString())).toList()) {
            var target=port.candidate(candidate.ref());
            if(target==null||target.protectedTarget()||!HytaleAreaQueries.hostile(port.store,target.handle(),port.actor)
                    ||!HytaleAreaQueries.clear(port.store,point.add(new Vec3(0,.03,0)),candidate.bounds().centre()))continue;
            var result=port.damage(context,target,index++,explosion.coefficient(),context.snapshot().criticalChance(),NativeProjectilePayloads.cause(authored),false,effect,true,false,primary);
            actualHealthLost+=result.actualHealthLoss();
            String burn="NONE";
            double burnRadius=explosion.burnRadius()*context.compiledPlan().executionModifiers().radiusFactor();
            if(result.actualHealthLoss()>0&&burnRadius>0&&ConnectionShape.pointDistanceSquared(point,candidate.bounds())<=burnRadius*burnRadius+1e-9)
                burn=port.applyProjectilePeriodicStatus(primary?context:context.projectileStatusChild(effect),target)?"BURN_APPLIED":"BURN_REJECTED";
            if(!result.cancelled())port.applyPassiveAreaPosition(context,target.handle(),point);
            emit(context,RpgTraceEventType.AREA_HIT,Map.of("component","AUTHORED_PROJECTILE_EXPLOSION","effectInstanceId",effect,"targetId",target.stableId(),"coefficient",explosion.coefficient(),"actualHealthLoss",result.actualHealthLoss(),"statusResult",burn,"rootComponent",primary));
        }
        return actualHealthLost;
    }
    private void finishProjectileContext(SkillExecutionContext context,String reason) {
        if(projectileService.registry().ownedBy(context.request().actorId()).stream().noneMatch(p->p.plan().skillInstanceId().equals(context.skillInstanceId())))
            executions.terminate(context,reason);
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
                // Transport never changes a status element: all Burn ticks use the native Fire channel.
                DamageCause cause = source.kind()==PeriodicStatusRuntime.Kind.BLEED?DamageCause.PHYSICAL
                        : DamageCause.getAssetMap().getAsset(source.kind() == PeriodicStatusRuntime.Kind.BURN ? "Fire" : "Poison");
                if (cause == null) throw new IllegalStateException("PERIODIC_DAMAGE_CAUSE_UNAVAILABLE");
                DamageOutcome outcome = source.kind()==PeriodicStatusRuntime.Kind.BLEED?port.resolvedProcDamage(context,candidate,coefficient,cause,true,context.skillInstanceId()+"/bleed/"+tickIndex):port.damage(context, candidate, tickIndex, coefficient, 0, cause, true,
                        context.skillInstanceId()+"/dot/"+source.kind()+"/"+tickIndex,false,true);
                emit(context, source.kind()==PeriodicStatusRuntime.Kind.BLEED?RpgTraceEventType.BLEED_TICK:source.kind() == PeriodicStatusRuntime.Kind.BURN ? RpgTraceEventType.BURN_TICK : RpgTraceEventType.POISON_TICK,
                        Map.of("targetId", source.victim(), "tickIndex", tickIndex, "coefficient", coefficient,
                                "integratedSeconds", seconds, "canCrit", false, "canTrigger", !context.derivedRelease()&&context.request().origin()==SkillExecutionRequest.Origin.MANUAL&&context.compiledPlan().conditionalRepeat().equals("kill_trigger"),
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
                    String effectId = source.kind()==PeriodicStatusRuntime.Kind.BLEED?"RPG_Bleed_Visual":source.kind() == PeriodicStatusRuntime.Kind.BURN ? NATIVE_BURN_VISUAL_EFFECT : "RPG_Poison_Visual";
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

    void forgetStatusVictim(UUID victim){chillSources.forget(victim);periodicStatuses.takeForDeath(victim,System.nanoTime()/1e9);}
    void nativeStatusDeath(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> dead){
        UUID victim=store.getComponent(dead,UUIDComponent.getComponentType()).getUuid();double now=System.nanoTime()/1e9;
        UUID world=store.getExternalData().getWorld().getWorldConfig().getUuid();Vec3 origin=vec(store.getComponent(dead,TransformComponent.getComponentType()).getPosition());
        var payloads=new ArrayList<ProliferationRuntime.Payload>();
        var chill=kernel.statuses().inspect(victim).active().get(RpgStatusType.CHILL);
        for(var source:chillSources.takeForDeath(victim,chill,now)){
            if(source.context().target()!=null&&source.context().target().worldId().equals(world))payloads.add(new ProliferationRuntime.Payload(source.context(),ProliferationRuntime.Kind.CHILL,source.stacks(),4,1,source.stacks(),source.ends()-now));
        }
        for(var source:periodicStatuses.takeForDeath(victim,now)){
            var c=source.context();emit(c,RpgTraceEventType.STATUS_REMOVED,Map.of("status",source.source().kind(),"targetId",victim,"reason","NATIVE_DEATH_PACKAGE_CLAIMED"));
            if(source.source().kind()!=PeriodicStatusRuntime.Kind.BLEED&&c.target()!=null&&c.target().worldId().equals(world)&&c.compiledPlan().proliferation()&&!c.derivedRelease())
                payloads.add(new ProliferationRuntime.Payload(c,ProliferationRuntime.Kind.valueOf(source.source().kind().name()),source.stacks(),source.sourceCap(),source.coefficient(),source.strength(),source.remaining()));
        }
        for(var kind:PeriodicStatusRuntime.Kind.values())kernel.statuses().projectPeriodic(victim,RpgStatusType.valueOf(kind.name()),0,0);
        if(payloads.isEmpty())return;
        proliferation.death(victim,origin,payloads,new ProliferationRuntime.Port(){
            public String claimSecondary(SkillExecutionContext c,String token){
                if(c.profile().support()!=null&&c.profile().support().aura())return support!=null&&support.runtime().claimAuraSecondary(c,System.nanoTime()/1e9)?"PASS":"AURA_EPOCH_SECONDARY_LIMIT_OR_INACTIVE";
                return c.effects().claim(token,2,true);
            }
            private Port sourcePort(SkillExecutionContext c){
                var owner=store.getExternalData().getRefFromUUID(c.request().actorId());
                if(owner==null||!owner.isValid()||!HytaleAreaQueries.hostile(store,dead,owner))throw new IllegalStateException("PROLIFERATION_SOURCE_OWNER_OR_ALLEGIANCE_INVALID");
                var playerRef=store.getComponent(owner,PlayerRef.getComponentType());var player=store.getComponent(owner,Player.getComponentType());var stats=store.getComponent(owner,EntityStatMap.getComponentType());
                if(playerRef==null||player==null||stats==null||!playerRef.getWorldUuid().equals(world))throw new IllegalStateException("PROLIFERATION_OWNER_WORLD_INVALID");
                var port=new Port(store,owner,playerRef,player,stats,null,buffer);if(!port.actorAliveAndUsable())throw new IllegalStateException("PROLIFERATION_OWNER_DEAD");return port;
            }
            public ProliferationRuntime.Query query(SkillExecutionContext c,Vec3 point,double radius,int budget){
                var port=sourcePort(c);
                var query=HytaleAreaQueries.query(store,port.actor,bounds->ConnectionShape.pointDistanceSquared(point,bounds)<=radius*radius+1e-9,budget);var targets=new ArrayList<ProliferationRuntime.Target>();
                for(var value:query.candidates()){var candidate=port.candidate(value.ref());if(candidate!=null)targets.add(new ProliferationRuntime.Target(UUID.fromString(candidate.stableId()),value.bounds(),true,true,candidate.protectedTarget(),HytaleAreaQueries.clear(store,point.add(new Vec3(0,.1,0)),value.bounds().centre())));}
                return new ProliferationRuntime.Query(targets,query.overflow());
            }
            public String apply(ProliferationRuntime.Payload payload,ProliferationRuntime.Target target){
                var c=payload.context();var port=sourcePort(c);var ref=store.getExternalData().getRefFromUUID(target.id());var candidate=port.candidate(ref);
                if(candidate==null||candidate.protectedTarget()||!HytaleAreaQueries.hostile(store,ref,port.actor)||!HytaleAreaQueries.clear(store,origin.add(new Vec3(0,.1,0)),target.bounds().centre()))return "PROLIFERATION_TARGET_REJECTED";
                String result;
                if(payload.kind()==ProliferationRuntime.Kind.CHILL){
                    if(store.getComponent(ref,EffectControllerComponent.getComponentType())==null)return "PROLIFERATION_NATIVE_CHILL_CONTROLLER_MISSING";
                    var nativeBefore=HytaleAreaStatuses.observed(store,ref);
                    var before=kernel.statuses().inspect(target.id()).active().get(RpgStatusType.CHILL);
                    var applied=kernel.statuses().applyChill(c.request().actorId(),c.rootCastId(),target.id(),SupportNativeEffects.control(store,ref,bosses),payload.stacks(),false,payload.remaining());
                    chillSources.observed(c,target.id(),before,kernel.statuses().inspect(target.id()).active().get(RpgStatusType.CHILL),System.nanoTime()/1e9);
                    buffer.ensureComponent(ref,AreaStatusProjection.getComponentType());HytaleAreaStatuses.synchronize(kernel.statuses(),target.id(),ref,store,port.actor);
                    if(support!=null&&HytaleAreaStatuses.observed(store,ref).improvedFrom(nativeBefore))
                        support.controlResolved(store,ref,c,false,"NATIVE_EFFECT_STATE_CHANGED");
                    result=applied.results().getLast().outcome()+":"+applied.results().getLast().type();
                }else{
                    var source=new PeriodicStatusRuntime.Source(c.request().actorId(),c.profile().skillId(),target.id(),PeriodicStatusRuntime.Kind.valueOf(payload.kind().name()));
                    result=periodicStatuses.apply(source,c,new PeriodicTarget(port.actor,ref),payload.coefficient(),payload.strength(),payload.remaining(),payload.stacks(),payload.sourceCap(),System.nanoTime()/1e9,periodicPort());
                }
                emit(c,RpgTraceEventType.PROLIFERATION_RESOLVED,Map.of("deathVictim",victim,"targetId",target.id(),"kind",payload.kind(),"stacks",payload.stacks(),"coefficientPerSecond",payload.coefficient(),"remainingSeconds",payload.remaining(),"result",result,"canProliferateAgain",false));
                return result;
            }
            public void trace(SkillExecutionContext c,String verdict,UUID target){emit(c,RpgTraceEventType.PROLIFERATION_RESOLVED,Map.of("deathVictim",victim,"targetId",target,"verdict",verdict,"nativeDeathObserved",true));}
        });
    }

    private void removeOwnedProjectiles(UUID actorId, CommandBuffer<EntityStore> buffer) {
        List<ProjectileCarrier> owned = projectiles.values().stream()
                .filter(value -> value.actorId.equals(actorId)).toList();
        // Drop promises/registry ownership before any native teardown can throw or queue on another world.
        projectileService.cancelOwner(actorId,"OWNER_CANCELLED");
        projectileLaunches.cancel(actorId);
        projectileFuses.values().removeIf(g->{if(!g.carrier.actorId.equals(actorId))return false;g.carrier.detonation.cancel();return true;});
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
                || modifiers.homing() || modifiers.shrapnel() || modifiers.splinterburst() || modifiers.accelerant() || modifiers.ballistics()
                || context.profile().projectile().details().pattern().homingTurnDegrees()>0;
    }
    private static DamageCause strikeCause(Stage04SkillProfile.Strike strike) {
        return switch(strike.details().element()) {
            case "PHYSICAL" -> DamageCause.PHYSICAL;
            case "FIRE" -> DamageCause.getAssetMap().getAsset("Fire");
            default -> connectionCause(strike.details().element());
        };
    }
    private static DamageCause connectionCause(String element) {
        String id=switch(element){case "WIND"->"Wind";case "LIGHTNING"->"Lightning";case "VOID"->"RPG_Void";case "NATURE"->"RPG_Nature";case "NECROTIC"->"RPG_Necrotic";default->null;};
        return id==null?null:DamageCause.getAssetMap().getAsset(id);
    }
    private boolean expireContinuationClock(ProjectileCarrier carrier,CommandBuffer<EntityStore> buffer) {
        var observation=carrier.instance.sampleNativeClock(System.nanoTime());
        // Bomb's authored airborne deadline is a terminal detonation, including a modified/returning carrier.
        if(carrier.context.profile().projectile().details().explosion().detonateAtAirborneLimit()
                &&carrier.instance.totalSeconds()+1e-6>=carrier.instance.originalMaxLifetimeSeconds()) {
            try {
                var point=carrier.detonation.tick(observation.position(),System.nanoTime()/1e9,true);
                if(point.isPresent())applyAuthoredExplosion(carrier.context,projectilePort(carrier,buffer.getStore(),buffer),point.get(),
                        carrier.instance.plan().projectileInstanceId()+"/airborne-limit",carrier.instance.plan().generation()==0&&!carrier.instance.returning());
                finishProjectileTerminal(carrier,"MAX_LIFETIME",observation.position(),buffer);
            }catch(RuntimeException failure){cancelProjectile(carrier,"AIRBORNE_EXPLOSION_FAILURE",observation.position(),buffer);}
            return true;
        }
        if(!observation.expired())return false;
        String reason=carrier.instance.remainingDistance()<=1e-6?"MAX_RANGE":"MAX_LIFETIME";
        if(!hasContinuations(carrier.context)){finishProjectileTerminal(carrier,reason,observation.position(),buffer);return true;}
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
            } else if(carrier.context.compiledPlan().projectileModifiers().homing()||carrier.context.profile().projectile().details().pattern().homingTurnDegrees()>0) {
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
        var pattern=carrier.context.profile().projectile().details().pattern();boolean authored=pattern.homingTurnDegrees()>0;
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
            var target=new com.inigmasgames.hytalerpg.execution.projectile.ProjectileHoming.Target(id,center,HytaleAreaQueries.clear(store,position,center));
            if(authored&&!com.inigmasgames.hytalerpg.execution.projectile.ProjectileHoming.withinAuthoredLock(target,carrier.instance.plan().origin(),
                    carrier.context.profile().projectile().maxDistance()*carrier.context.compiledPlan().projectileModifiers().distanceFactor()))return java.util.Optional.empty();
            return java.util.Optional.of(target);
        };
        Vec3 acquisition=authored?carrier.context.target().point():position;double radius=authored?pattern.acquisitionRadius():8;
        java.util.function.Supplier<List<com.inigmasgames.hytalerpg.execution.projectile.ProjectileHoming.Target>> candidates=()->{
            var shape=new AreaGeometry(AreaGeometry.Kind.DISC,acquisition.add(new Vec3(0,-radius,0)),Vec3.FORWARD,radius,360,0,0,radius*2);
            var found=HytaleAreaQueries.query(store,carrier.actor,shape,64);
            if(found.overflow())throw new IllegalStateException("HOMING_CANDIDATE_BUDGET");
            var choices=new ArrayList<com.inigmasgames.hytalerpg.execution.projectile.ProjectileHoming.Target>();
            for(var candidate:found.candidates()) {
                var uuid=store.getComponent(candidate.ref(),UUIDComponent.getComponentType());
                if(uuid!=null&&!carrier.instance.previouslyHit(uuid.getUuid().toString()))live.apply(uuid.getUuid().toString()).ifPresent(choices::add);
            }
            return choices;
        };
        var update=authored?carrier.instance.homing().authored(carrier.instance.totalSeconds(),position,carrier.instance.direction(),
                carrier.context.target().entityId()==null?null:carrier.context.target().entityId().toString(),acquisition,pattern,candidates,live)
                :carrier.instance.homing().update(carrier.instance.totalSeconds(),position,carrier.instance.direction(),candidates,live);
        if(update.direction().distanceSquared(carrier.instance.direction())>1e-12) {
            carrier.instance.redirect(update.direction());resumeCarrier(carrier,position,buffer,false);
        }
        if(update.reacquired())emitProjectile(carrier,RpgTraceEventType.PROJECTILE_HOMING_QUERY,
                Map.of("reason","HOMING_ACQUISITION","targetId",update.targetId()==null?"NONE":update.targetId(),"intervalSeconds",.10,"turnCapDegreesPerSecond",authored?pattern.homingTurnDegrees():120));
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
            instance.nativeSpawned(System.nanoTime());
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
            if(context.effects().projectileVisuals().cast(context.skillInstanceId()+"/"+context.barrageBatch()))projectileVisual(carrier,buffer.getStore(),()->vfx.presentContact(buffer.getStore().getExternalData().getWorld(),plan.origin(),
                    bound.context.profile().projectile().details().element(),.10,1));
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
        var plan=carrier.context.compiledPlan();
        var snapshot=carrier.context.snapshot();
        if(plan.concentrationOnlyOnSecondary())snapshot=snapshot.withModifiers(snapshot.modifiers().withIncreased(.30));
        var context=carrier.context.withSnapshot(snapshot.withMagnitudeFactor(burst.coefficientFactor()*(plan.positionOnlyOnSecondary()?.90:1)*(plan.impactOnlyOnSecondary()?.90:1)));
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
                    NativeProjectilePayloads.cause(context.profile().projectile()),false,burst.id(),false,false,false);
            String status="NONE";
            if(outcome.actualHealthLoss()>0 && !authored.statusId().isBlank())status=authored.hasPeriodicStatus()
                    ?(port.applyProjectilePeriodicStatus(context.projectileStatusChild(burst.id()),target)?authored.statusId()+"_APPLIED":authored.statusId()+"_REJECTED"):port.applyProjectileStatus(context,target);
            double knockback=outcome.actualHealthLoss()>0?port.applyProjectileKnockback(context,target):0;
            if(!outcome.cancelled())port.applyPassiveAreaPosition(context,target.handle(),point);
            emitProjectile(carrier,RpgTraceEventType.AREA_HIT,Map.of("component","SHRAPNEL","effectInstanceId",burst.id(),
                    "targetId",target.stableId(),"actualHealthLoss",outcome.actualHealthLoss(),"canProc",false,
                    "statusResult",status,"appliedKnockback",knockback));
        }
    }
    private void removeOwnedBurns(UUID actorId) {
        periodicStatuses.cancel(actorId, System.nanoTime() / 1e9, periodicPort());
        hitProcs.cancel(actorId);
        chillSources.cancel(actorId);
        proliferation.cancel(actorId);
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
        carrier.detonation.cancel();projectileFuses.remove(carrier.instance.plan().projectileInstanceId());
        if (carrier.projectile.isValid()) buffer.tryRemoveEntity(carrier.projectile, RemoveReason.REMOVE);
        projectileService.onForwardTermination(carrier.instance, reason, position);
        emitProjectile(carrier, RpgTraceEventType.PROJECTILE_CANCELLED, Map.of("reason", reason));
        emitProjectile(carrier, RpgTraceEventType.PROJECTILE_TERMINATED, Map.of("reason", reason));
        finishProjectileContext(carrier.context, "PROJECTILE_" + reason);
    }
    private void terminateProjectile(ProjectileCarrier carrier, String reason, Vec3 position,
                                     CommandBuffer<EntityStore> buffer) {
        if(reason.equals("MAX_RANGE")||reason.equals("MAX_LIFETIME")||reason.equals("FORWARD_BUDGET_EXHAUSTED"))
            projectileVisual(carrier,buffer.getStore(),()->vfx.presentProjectileExpiry(buffer.getStore().getExternalData().getWorld(),position,carrier.context.profile().projectile().details().element()));
        projectiles.remove(carrier.instance.plan().projectileInstanceId(), carrier);
        if (carrier.projectile.isValid()) buffer.tryRemoveEntity(carrier.projectile, RemoveReason.REMOVE);
        projectileService.onForwardTermination(carrier.instance, reason, position);
        emitProjectile(carrier, RpgTraceEventType.PROJECTILE_TERMINATED,
                Map.of("reason", reason, "travelledDistance", carrier.instance.flight().travelled()));
        finishProjectileContext(carrier.context, "PROJECTILE_" + reason);
    }
    private static CombatTrace.Context ids(SkillExecutionContext context) {
        return new CombatTrace.Context(context.rootCastId(), context.skillInstanceId(), context.request().correlationId());
    }
    private static final class Motion {
        final SkillExecutionContext context; final Ref<EntityStore> actor; final MovementPlanner.Plan plan; double elapsed;
        final com.inigmasgames.hytalerpg.execution.movement.ValidatedTravel travel;
        Motion(SkillExecutionContext context, Ref<EntityStore> actor, MovementPlanner.Plan plan) {
            this.context = context; this.actor = actor; this.plan = plan;
            travel=new com.inigmasgames.hytalerpg.execution.movement.ValidatedTravel(plan.origin());
        }
    }
    private record Counter(SkillExecutionContext context, Ref<EntityStore> attacker, String eventId) { }
    private record RepeatingStrike(SkillExecutionContext context, StrikeRepeatSchedule schedule) { }
    private record QueuedProjectile(SkillExecutionContext context,Ref<EntityStore> actor){}
    private static final class GroundedProjectile {
        final ProjectileCarrier carrier;final Vec3 point;long nextContacts=Long.MIN_VALUE;
        GroundedProjectile(ProjectileCarrier carrier,Vec3 point){this.carrier=carrier;this.point=point;}
        boolean sampleContacts(long now){if(now<nextContacts)return false;nextContacts=now+100_000_000L;return true;}
    }
    private void projectileVisual(ProjectileCarrier carrier,Store<EntityStore> store,Runnable presentation){
        try{presentation.run();}catch(RuntimeException unavailable){if(carrier.visuals.firstFailure())
            emitProjectile(carrier,RpgTraceEventType.AREA_PRESENTATION,Map.of("phase","PROJECTILE_TEMPLATE_UNAVAILABLE","connectedProof",false));}
    }
    private record ProjectileCarrier(SkillExecutionContext context, Ref<EntityStore> actor, UUID actorId,
            Ref<EntityStore> projectile, ProjectileInstance instance,com.inigmasgames.hytalerpg.vfx.ProjectileReadability visuals,ProjectileDetonation detonation) {
        ProjectileCarrier(SkillExecutionContext context,Ref<EntityStore> actor,UUID actorId,Ref<EntityStore> projectile,ProjectileInstance instance){
            this(context,actor,actorId,projectile,instance,new com.inigmasgames.hytalerpg.vfx.ProjectileReadability(instance.plan().origin(),System.nanoTime()),new ProjectileDetonation(context.profile().projectile().details().explosion()));
        }
    }
    private record PeriodicTarget(Ref<EntityStore> actor, Ref<EntityStore> victim) { }
    private record DamageOutcome(double preMitigationDamage, double actualHealthLoss, boolean cancelled,double increasedUnit) {
        private DamageOutcome(double preMitigationDamage,double actualHealthLoss,boolean cancelled){this(preMitigationDamage,actualHealthLoss,cancelled,Double.NaN);}
    }
}
