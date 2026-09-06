package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.combat.status.ControlProfile;
import com.inigmasgames.hytalerpg.combat.status.RpgStatusType;
import com.inigmasgames.hytalerpg.combat.status.StatusService;
import com.inigmasgames.hytalerpg.domain.SkillId;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.SkillExecutionPort;
import com.inigmasgames.hytalerpg.execution.SkillExecutionRequest;
import com.inigmasgames.hytalerpg.execution.SkillExecutionResult;
import com.inigmasgames.hytalerpg.execution.SkillExecutionService;
import com.inigmasgames.hytalerpg.execution.SkillExecutorRegistry;
import com.inigmasgames.hytalerpg.execution.SkillFamilyExecutor;
import com.inigmasgames.hytalerpg.execution.SkillInstanceLifecycle;
import com.inigmasgames.hytalerpg.execution.Stage04SkillProfile;
import com.inigmasgames.hytalerpg.execution.Stage04SkillProfiles;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.movement.MovementPlanner;
import com.inigmasgames.hytalerpg.execution.reaction.ReactionWindowService;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleBossBarTracker;
import com.inigmasgames.hytalerpg.execution.strike.SkillHitLedger;
import com.inigmasgames.hytalerpg.execution.strike.StrikeGeometryService;
import com.inigmasgames.hytalerpg.execution.strike.StrikeRepeatSchedule;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Stage04ExecutionTest {
    @Test void sixPilotProfilesAreCanonicalAndPounceUsesInnatePower() {
        var catalog = com.inigmasgames.hytalerpg.content.RpgCatalog.loadCanonical();
        var profiles = Stage04SkillProfiles.loadCanonical(catalog);
        assertEquals(87, catalog.skills().size());
        assertEquals(66, catalog.passives().size());
        assertEquals(Set.of("quick_slash", "heavy_swing", "shield_bash", "quickstep", "pounce", "riposte"),
                profiles.all().keySet());
        assertEquals("INNATE", profiles.require("pounce").basePowerSource());
        assertEquals(20.0, profiles.require("pounce").innateBasePower());
        var pounce = catalog.skill(new SkillId("pounce")).orElseThrow();
        assertEquals("INNATE", pounce.basePowerSource());
        assertEquals(20.0, pounce.innateBasePower());
    }

    @Test void strikeArcHonorsAngleRangeAndTargetCap() {
        var strike = profiles().require("quick_slash").strike();
        var candidates = List.of(candidate("front", 0, 0, 2.6), candidate("edge", 2.25, 0, 1.3),
                candidate("behind", 0, 0, -1), candidate("far", 0, 0, 2.6001));
        var result = new StrikeGeometryService().query(Vec3.ZERO, Vec3.FORWARD, strike, candidates);
        assertEquals(Set.of("front", "edge"), result.accepted().stream().map(value -> value.stableId())
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals("OUTSIDE_ARC", reason(result, "behind"));
        assertEquals("OUT_OF_RANGE", reason(result, "far"));
    }

    @Test void lineGeometryRejectsLateralAndRearCandidates() {
        var line = new Stage04SkillProfile.Strike(Stage04SkillProfile.Geometry.LINE, 4, 0, .25,
                1, 0, 8, 1, "", 0);
        var result = new StrikeGeometryService().query(Vec3.ZERO, Vec3.FORWARD, line,
                List.of(candidate("center", .2, 0, 4), candidate("wide", .3, 0, 2), candidate("rear", 0, 0, -1)));
        assertEquals(List.of("center"), result.accepted().stream().map(value -> value.stableId()).toList());
        assertEquals("OUTSIDE_LINE_WIDTH", reason(result, "wide"));
        assertEquals("OUTSIDE_LINE_LENGTH", reason(result, "rear"));
    }

    @Test void hitLedgerDeduplicatesWithinRepeatButAllowsNextRepeat() {
        SkillHitLedger ledger = new SkillHitLedger();
        assertTrue(ledger.accept("skill", 0, "target"));
        assertFalse(ledger.accept("skill", 0, "target"));
        assertTrue(ledger.accept("skill", 1, "target"));
        ledger.clear("skill");
        assertTrue(ledger.accept("skill", 0, "target"));
    }

    @Test void authoredRepeatTimingDoesNotCollapseHitsIntoInitialDispatch() {
        StrikeRepeatSchedule schedule = new StrikeRepeatSchedule(3, .2, 1_000_000_000L);
        assertTrue(schedule.claimDue(1_199_999_999L).isEmpty());
        assertEquals(1, schedule.claimDue(1_200_000_000L).orElseThrow());
        assertTrue(schedule.claimDue(1_399_999_999L).isEmpty());
        assertEquals(2, schedule.claimDue(1_400_000_000L).orElseThrow());
        assertTrue(schedule.complete());
    }

    @Test void quickstepIsFourMetersOverPointTwoTwoSecondsAndCollisionClamps() {
        var movement = profiles().require("quickstep").movement();
        MovementPlanner planner = new MovementPlanner();
        var clear = planner.plan(Vec3.ZERO, new Vec3(1, 0, 0), 99, movement, (origin, delta) -> 1.0);
        assertEquals(4.0, clear.appliedDistance(), 1e-12);
        assertEquals(.22, clear.durationSeconds(), 1e-12);
        assertEquals(new Vec3(4, 0, 0), planner.sample(clear, 1));
        var blocked = planner.plan(Vec3.ZERO, new Vec3(1, 0, 0), 4, movement, (origin, delta) -> .375);
        assertTrue(blocked.clamped());
        assertEquals(1.5, blocked.appliedDistance(), 1e-12);
    }

    @Test void pounceTrajectoryUsesBoundedTravelAndArrivalPayload() {
        var profile = profiles().require("pounce");
        MovementPlanner planner = new MovementPlanner();
        var plan = planner.plan(Vec3.ZERO, Vec3.FORWARD, 8, profile.movement(), (origin, delta) -> 1);
        assertEquals(.5, plan.durationSeconds(), 1e-12);
        assertEquals(8, plan.appliedDistance(), 1e-12);
        assertEquals(1.2, planner.sample(plan, .5).y(), 1e-12);
        assertEquals(Stage04SkillProfile.Geometry.RADIUS, profile.strike().geometry());
        assertEquals(1.5, profile.strike().range(), 1e-12);
        assertEquals(1.05, profile.strike().coefficient(), 1e-12);
    }

    @Test void invalidWeaponAndInsufficientResourceRejectBeforeCommit() {
        Harness invalid = harness("quick_slash", 100, item("MACE", 10), item("SHIELD", 5));
        assertRejectedUnchanged(invalid, "INVALID_MAIN_HAND", 100);
        Harness empty = harness("quick_slash", 4, item("SWORD", 10), item("SHIELD", 5));
        assertRejectedUnchanged(empty, "INSUFFICIENT_RESOURCE", 4);
    }

    @Test void familyPrerequisiteRejectsBeforeResourceAndCooldownMutation() {
        Harness harness = harness("quick_slash", 100, item("SWORD", 10), null);
        harness.port.validation = SkillExecutionPort.Validation.reject("NO_VALID_TARGET");
        assertRejectedUnchanged(harness, "NO_VALID_TARGET", 100);
    }

    @Test void commitConsumesResourceStartsCooldownBeforeFamilyDispatchAndKeepsTraceIds() {
        Harness harness = harness("quick_slash", 100, item("SWORD", 10), null);
        harness.port.onDispatch = () -> {
            assertEquals(95, harness.port.resources.current(ResourceType.STAMINA), 1e-12);
            assertTrue(harness.kernel.cooldowns().remaining(harness.actor, "quick_slash") > 0);
        };
        SkillExecutionResult result = harness.execute();
        assertTrue(result.committed());
        assertNotNull(harness.port.context);
        assertEquals(.85, harness.port.context.profile().strike().coefficient(), 1e-12);
        var records = ((Stage01BTestSupport.RecordingTracer) harness.bundle.tracer()).records;
        var correlated = records.stream().filter(record -> "stage04-correlation".equals(record.correlationId())).toList();
        assertTrue(correlated.size() >= 5);
        assertEquals(1, correlated.stream().map(record -> record.details().get("rootCastId")).distinct().count());
        assertEquals(1, correlated.stream().map(record -> record.details().get("skillInstanceId")).distinct().count());
    }

    @Test void cooldownSecondActivationRejectsWithoutAdditionalConsumption() {
        Harness harness = harness("quick_slash", 100, item("SWORD", 10), null);
        assertTrue(harness.execute().committed());
        double afterFirst = harness.port.resources.current(ResourceType.STAMINA);
        SkillExecutionResult second = harness.execute();
        assertEquals("COOLDOWN_ACTIVE", second.code());
        assertEquals(afterFirst, harness.port.resources.current(ResourceType.STAMINA));
        assertEquals(1, harness.port.dispatches);
    }

    @Test void heavySwingWindupCanCancelWithoutCostCooldownOrReplacementTraceIds() {
        Harness harness = harness("heavy_swing", 100, item("LONGSWORD", 10), null);
        assertEquals(SkillExecutionResult.Status.PENDING, harness.execute().status());
        assertEquals(100, harness.port.resources.current(ResourceType.STAMINA));
        assertEquals(0, harness.kernel.cooldowns().remaining(harness.actor, "heavy_swing"));
        assertTrue(harness.service.cancel(harness.actor, "TEST_INTERRUPT"));
        assertEquals(SkillExecutionResult.Status.REJECTED, harness.service.completeWindup(harness.actor, harness.port).status());
        var records = ((Stage01BTestSupport.RecordingTracer) harness.bundle.tracer()).records;
        var correlated = records.stream().filter(record -> "stage04-correlation".equals(record.correlationId())).toList();
        assertEquals(1, correlated.stream().map(record -> record.details().get("rootCastId")).distinct().count());
        assertEquals(1, correlated.stream().map(record -> record.details().get("skillInstanceId")).distinct().count());
    }

    @Test void shieldBashCarriesPointSixStaggerAndControlPolicyRejectsProtectedTargets() {
        Harness harness = harness("shield_bash", 100, item("SWORD", 10), item("SHIELD", 7));
        assertTrue(harness.execute().committed());
        assertEquals("STAGGER", harness.port.context.profile().strike().statusId());
        assertEquals(.6, harness.port.context.profile().strike().statusSeconds(), 1e-12);
        UUID target = UUID.randomUUID();
        StatusService.Result applied = harness.kernel.statuses().apply(target, RpgStatusType.STAGGER,
                ControlProfile.NORMAL, .6);
        assertEquals(StatusService.Outcome.APPLIED, applied.outcome());
        assertEquals(.6, applied.remainingSeconds(), 1e-12);
        assertEquals(StatusService.Outcome.REJECTED, harness.kernel.statuses().apply(UUID.randomUUID(),
                RpgStatusType.STAGGER, new ControlProfile(true, false, false), .6).outcome());
        assertEquals(StatusService.Outcome.REJECTED, harness.kernel.statuses().apply(UUID.randomUUID(),
                RpgStatusType.STAGGER, new ControlProfile(false, true, false), .6).outcome());
    }

    @Test void reactionExpiresTriggersOnceAndRejectsDuplicateIncomingEvent() {
        Harness harness = harness("riposte", 100, item("SWORD", 10), null);
        assertTrue(harness.execute().committed());
        AtomicLong clock = new AtomicLong();
        ReactionWindowService reactions = new ReactionWindowService(clock::get);
        assertTrue(reactions.arm(harness.actor, harness.port.context, .8));
        assertTrue(reactions.trigger(harness.actor, "HYTALE_DAMAGE_BLOCKED", "event-1").isPresent());
        assertTrue(reactions.trigger(harness.actor, "HYTALE_DAMAGE_BLOCKED", "event-1").isEmpty());
        assertTrue(reactions.arm(harness.actor, harness.port.context, .8));
        clock.set(800_000_000L);
        assertTrue(reactions.expire(harness.actor).isPresent());
        assertTrue(reactions.active(harness.actor).isEmpty());
    }

    @Test void executorRegistryDispatchesExactlyOneSharedExecutorPerFamily() {
        List<Stage04SkillProfile.Family> calls = new ArrayList<>();
        List<SkillFamilyExecutor> executors = new ArrayList<>();
        for (Stage04SkillProfile.Family family : Stage04SkillProfile.Family.values()) executors.add(new SkillFamilyExecutor() {
            @Override public Stage04SkillProfile.Family family() { return family; }
            @Override public SkillExecutionResult execute(SkillExecutionContext context, SkillExecutionPort port) {
                calls.add(family); return SkillExecutionResult.committed("OK", 0, 0);
            }
        });
        SkillExecutorRegistry registry = new SkillExecutorRegistry(executors);
        for (Stage04SkillProfile.Family family : Stage04SkillProfile.Family.values()) assertEquals(family,
                registry.require(family).family());
        assertThrows(IllegalArgumentException.class, () -> new SkillExecutorRegistry(List.of(executors.getFirst(), executors.getFirst())));
    }

    @Test void bossClassificationUsesWorldScopedNativeBossBarIdentity() {
        HytaleBossBarTracker tracker = new HytaleBossBarTracker();
        UUID player = UUID.randomUUID(), world = UUID.randomUUID(), otherWorld = UUID.randomUUID();
        tracker.observe(player, world, 71, false);
        assertTrue(tracker.isBoss(world, 71));
        assertFalse(tracker.isBoss(otherWorld, 71));
        assertFalse(tracker.isBoss(world, 72));
        tracker.observe(player, world, 71, true);
        assertFalse(tracker.isBoss(world, 71));
    }

    private static Harness harness(String skill, double stamina, SkillExecutionPort.Item main,
                                   SkillExecutionPort.Item offhand) {
        var bundle = Stage01BTestSupport.bundle();
        UUID actor = UUID.randomUUID();
        assertTrue(bundle.service().equipSkill(actor, SkillSlot.SKILL01, new SkillId(skill)).success());
        RpgCombatKernel kernel = RpgCombatKernel.createProduction();
        FakePort port = new FakePort(stamina, main, offhand);
        SkillExecutionService service = new SkillExecutionService(bundle.service(),
                Stage04SkillProfiles.loadCanonical(bundle.catalog()), kernel, SkillExecutorRegistry.stage04(),
                new SkillInstanceLifecycle(), bundle.tracer());
        return new Harness(actor, bundle, kernel, service, port);
    }

    private static void assertRejectedUnchanged(Harness harness, String code, double stamina) {
        SkillExecutionResult result = harness.execute();
        assertEquals(SkillExecutionResult.Status.REJECTED, result.status());
        assertEquals(code, result.code());
        assertEquals(stamina, harness.port.resources.current(ResourceType.STAMINA));
        assertEquals(0, harness.kernel.cooldowns().remaining(harness.actor,
                harness.bundle.service().getPresentationView(harness.actor).state().skill(SkillSlot.SKILL01).orElseThrow().value()));
        assertEquals(0, harness.port.dispatches);
    }

    private static Stage04SkillProfiles profiles() {
        return Stage04SkillProfiles.loadCanonical(com.inigmasgames.hytalerpg.content.RpgCatalog.loadCanonical());
    }
    private static StrikeGeometryService.Candidate<String> candidate(String id, double x, double y, double z) {
        return new StrikeGeometryService.Candidate<>(id, id, new Vec3(x, y, z), true, false, false);
    }
    private static String reason(StrikeGeometryService.QueryResult<String> result, String id) {
        return result.decisions().stream().filter(value -> value.candidate().stableId().equals(id))
                .findFirst().orElseThrow().reason();
    }
    private static SkillExecutionPort.Item item(String kind, double power) {
        return new SkillExecutionPort.Item("test:" + kind.toLowerCase(), kind,
                new ItemPowerDescriptor("test:" + kind.toLowerCase(), Set.of(kind), power, null));
    }

    private record Harness(UUID actor, Stage01BTestSupport.Bundle bundle, RpgCombatKernel kernel,
                           SkillExecutionService service, FakePort port) {
        SkillExecutionResult execute() {
            return service.request(new SkillExecutionRequest(actor, SkillSlot.SKILL01, "Ability1", 41,
                    "stage04-correlation", Vec3.FORWARD), port);
        }
    }

    private static final class FakePort implements SkillExecutionPort {
        final Resources resources;
        final Equipment equipment;
        Validation validation = Validation.pass();
        SkillExecutionContext context;
        Runnable onDispatch = () -> { };
        int dispatches;
        FakePort(double stamina, Item main, Item offhand) {
            resources = new Resources(stamina); equipment = new Equipment(main, offhand);
        }
        @Override public boolean actorAliveAndUsable() { return true; }
        @Override public Equipment equipment() { return equipment; }
        @Override public NativeResourcePort resources() { return resources; }
        @Override public Validation familyPrerequisites(Stage04SkillProfile profile) { return validation; }
        @Override public SkillExecutionResult executeStrike(SkillExecutionContext value) { return dispatched(value, 1, 0); }
        @Override public SkillExecutionResult executeMovement(SkillExecutionContext value) {
            return dispatched(value, 0, value.profile().movement().maxDistance());
        }
        @Override public SkillExecutionResult executeReaction(SkillExecutionContext value) { return dispatched(value, 0, 0); }
        private SkillExecutionResult dispatched(SkillExecutionContext value, int targets, double distance) {
            context = value; dispatches++; onDispatch.run();
            return SkillExecutionResult.committed("DISPATCHED", targets, distance);
        }
    }

    private static final class Resources implements NativeResourcePort {
        private final EnumMap<ResourceType, Double> values = new EnumMap<>(ResourceType.class);
        Resources(double stamina) { values.put(ResourceType.STAMINA, stamina); values.put(ResourceType.MANA, 100.0); }
        @Override public double current(ResourceType type) { return values.getOrDefault(type, 0.0); }
        @Override public double maximum(ResourceType type) { return 100; }
        @Override public void setCurrent(ResourceType type, double value) { values.put(type, value); }
    }
}
