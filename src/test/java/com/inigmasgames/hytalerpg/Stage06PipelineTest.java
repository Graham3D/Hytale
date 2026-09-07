package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.damage.CriticalRoller;
import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.combat.status.ControlProfile;
import com.inigmasgames.hytalerpg.combat.status.RpgStatusType;
import com.inigmasgames.hytalerpg.combat.status.StatusService;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.domain.SkillId;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.EnumMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage06PipelineTest {
    @Test void pilotsReuseOneCommitOneCooldownAndSnapshotAuthority() {
        for (String id : new String[]{"ground_slam", "frost_nova", "root_snare", "powder_mine", "cold_wave",
                "venom_spray", "blizzard", "wall_of_fire", "poison_cloud"}) {
            Harness harness = harness(id);
            var profile = harness.profiles.require(id); var pool = ResourceType.valueOf(profile.resourceType());
            assertTrue(harness.execute().committed());
            assertEquals(100 - profile.resourceCost(), harness.port.current(pool), 1e-12);
            assertEquals(1, harness.port.dispatches);
            assertTrue(harness.kernel.cooldowns().remaining(harness.actor, id) > 0);
            assertEquals(harness.port.context.rootCastId(), harness.port.context.snapshot().rootCastId());
            assertEquals(harness.port.context.skillInstanceId(), harness.port.context.snapshot().skillInstanceId());
            assertEquals("COOLDOWN_ACTIVE", harness.execute().code()); assertEquals(1, harness.port.dispatches);
        }
    }
    @Test void rejectedNativePlacementDoesNotCommitAnyResourceOrCooldown() {
        Harness harness = harness("root_snare"); harness.port.validation = SkillExecutionPort.Validation.reject("NO_LEGAL_GROUND_SURFACE");
        assertEquals("NO_LEGAL_GROUND_SURFACE", harness.execute().code());
        assertEquals(100, harness.port.current(ResourceType.MANA)); assertEquals(0, harness.port.dispatches);
        assertEquals(0, harness.kernel.cooldowns().remaining(harness.actor, "root_snare"));
    }
    @Test void partialAreaAdapterFailureCannotRefundDamageAndEnableFreeRepeatedCasts() {
        Harness harness = harness("frost_nova"); harness.port.failAfterDispatch = true;
        assertEquals(SkillExecutionResult.Status.TERMINATED, harness.execute().status());
        assertEquals(82, harness.port.current(ResourceType.MANA));
        assertTrue(harness.kernel.cooldowns().remaining(harness.actor, "frost_nova") > 0);
        assertEquals("COOLDOWN_ACTIVE", harness.execute().code()); assertEquals(1, harness.port.dispatches);
    }
    @Test void strongestSlowDoesNotMultiplyAndWeakerChillResumesAfterExpiry() {
        AtomicLong clock = new AtomicLong(); var statuses = new StatusService(CombatBalanceProfile.loadCanonical(), clock::get);
        UUID target = UUID.randomUUID();
        statuses.apply(target, RpgStatusType.CHILL, ControlProfile.NORMAL);
        statuses.apply(target, RpgStatusType.CHILL, ControlProfile.NORMAL);
        statuses.applySlow(target, "root-snare", .35, 3);
        assertEquals(.35, statuses.strongestSlow(target).magnitude());
        clock.set(3_100_000_000L); assertEquals(.10, statuses.strongestSlow(target).magnitude(), 1e-12);
        clock.set(6_100_000_000L); assertEquals(0, statuses.strongestSlow(target).magnitude());
    }
    @Test void authoredSlowRejectsMalformedValuesAndCapsAtSixtyPercent() {
        var statuses = new StatusService(CombatBalanceProfile.loadCanonical(), () -> 0); UUID target = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> statuses.applySlow(target, "bad", Double.NaN, 3));
        assertThrows(IllegalArgumentException.class, () -> statuses.applySlow(target, "bad", .3, -1));
        statuses.applySlow(target, "cap", .95, 1); assertEquals(.6, statuses.strongestSlow(target).magnitude());
    }
    @Test void controlResistanceIsSharedRollingAndEliteDurationHalves() {
        AtomicLong clock = new AtomicLong(); var statuses = new StatusService(CombatBalanceProfile.loadCanonical(), clock::get);
        UUID target = UUID.randomUUID();
        assertEquals(3, statuses.apply(target, RpgStatusType.ROOT, ControlProfile.NORMAL, 3).remainingSeconds());
        assertEquals(.2, statuses.apply(target, RpgStatusType.STAGGER, ControlProfile.NORMAL, .4).remainingSeconds());
        assertEquals(.5, statuses.apply(target, RpgStatusType.FROZEN, ControlProfile.NORMAL, 2).remainingSeconds());
        assertEquals(StatusService.Outcome.REJECTED, statuses.apply(target, RpgStatusType.ROOT, ControlProfile.NORMAL, 3).outcome());
        clock.set(10_000_000_000L);
        assertEquals(1.5, statuses.apply(target, RpgStatusType.ROOT, new ControlProfile(false, false, false, true), 3).remainingSeconds());
    }
    @Test void chillRemainsAtFourDuringFrozenImmunityAndCanFreezeOnLaterLegalHit() {
        AtomicLong clock = new AtomicLong(); var statuses = new StatusService(CombatBalanceProfile.loadCanonical(), clock::get);
        UUID target = UUID.randomUUID(); statuses.apply(target, RpgStatusType.FROZEN, ControlProfile.NORMAL);
        clock.set(2_100_000_000L);
        for (int i = 0; i < 7; i++) statuses.apply(target, RpgStatusType.CHILL, ControlProfile.NORMAL);
        assertEquals(4, statuses.inspect(target).active().get(RpgStatusType.CHILL).stacks());
        assertFalse(statuses.inspect(target).active().containsKey(RpgStatusType.FROZEN));
        clock.set(5_100_000_000L);
        assertEquals(StatusService.Outcome.THRESHOLD, statuses.apply(target, RpgStatusType.CHILL, ControlProfile.NORMAL).outcome());
    }
    @Test void lateInspectionDoesNotExtendImmunityAndRemovalDropsAllStatusMemory() {
        AtomicLong clock = new AtomicLong(); var statuses = new StatusService(CombatBalanceProfile.loadCanonical(), clock::get);
        UUID target = UUID.randomUUID(); statuses.apply(target, RpgStatusType.FROZEN, ControlProfile.NORMAL);
        clock.set(8_000_000_000L);
        assertEquals(StatusService.Outcome.APPLIED, statuses.apply(target, RpgStatusType.FROZEN, ControlProfile.NORMAL).outcome());
        statuses.applySlow(target, "source", .35, 3); statuses.forget(target);
        assertTrue(statuses.inspect(target).active().isEmpty()); assertEquals(0, statuses.strongestSlow(target).magnitude());
        assertEquals(3, statuses.apply(target, RpgStatusType.ROOT, ControlProfile.NORMAL, 3).remainingSeconds());
    }
    private Harness harness(String id) {
        var bundle = Stage01BTestSupport.bundle(); UUID actor = UUID.randomUUID();
        assertTrue(bundle.service().equipSkill(actor, SkillSlot.SKILL01, new SkillId(id)).success());
        var profiles = Stage04SkillProfiles.loadCanonical(RpgCatalog.loadCanonical());
        var kernel = new RpgCombatKernel(CombatBalanceProfile.loadCanonical(), new CriticalRoller(() -> 1));
        String kind = switch (id) { case "ground_slam" -> "MACE"; case "powder_mine" -> "BOMB";
            case "venom_spray" -> "NONE"; default -> "STAFF"; };
        var port = new Port(kind);
        var service = new SkillExecutionService(bundle.service(), profiles, kernel, SkillExecutorRegistry.runtime(),
                new SkillInstanceLifecycle(), bundle.tracer());
        return new Harness(actor, id, profiles, kernel, service, port);
    }
    private record Harness(UUID actor, String id, Stage04SkillProfiles profiles, RpgCombatKernel kernel,
                           SkillExecutionService service, Port port) {
        SkillExecutionResult execute() {
            return service.request(new SkillExecutionRequest(actor, SkillSlot.SKILL01, "fixture", 6,
                    "stage06-pipeline", Vec3.FORWARD), port);
        }
    }
    private static final class Port implements SkillExecutionPort, NativeResourcePort {
        final EnumMap<ResourceType, Double> values = new EnumMap<>(ResourceType.class);
        final Equipment equipment; SkillExecutionContext context; int dispatches; boolean failAfterDispatch;
        Validation validation = Validation.pass();
        Port(String kind) {
            values.put(ResourceType.MANA, 100d); values.put(ResourceType.STAMINA, 100d);
            equipment = new Equipment(kind.equals("NONE") ? null : new Item("fixture", kind,
                    new ItemPowerDescriptor("fixture", Set.of(kind), 20d, 20d)), null);
        }
        public boolean actorAliveAndUsable() { return true; }
        public Equipment equipment() { return equipment; }
        public NativeResourcePort resources() { return this; }
        public Validation familyPrerequisites(Stage04SkillProfile profile, CompiledSkillPlan plan) { return validation; }
        public SkillExecutionResult executeArea(SkillExecutionContext value) {
            context = value; dispatches++;
            if (failAfterDispatch) throw new IllegalStateException("fixture partial native failure");
            return SkillExecutionResult.committed("AREA_DISPATCHED", 1, 0);
        }
        public SkillExecutionResult executeStrike(SkillExecutionContext value) { throw new AssertionError("Wrong family"); }
        public SkillExecutionResult executeMovement(SkillExecutionContext value) { throw new AssertionError("Wrong family"); }
        public SkillExecutionResult executeReaction(SkillExecutionContext value) { throw new AssertionError("Wrong family"); }
        public SkillExecutionResult executeProjectile(SkillExecutionContext value) { throw new AssertionError("Wrong family"); }
        public double current(ResourceType type) { return values.getOrDefault(type, 0d); }
        public double maximum(ResourceType type) { return 100; }
        public void setCurrent(ResourceType type, double value) { values.put(type, value); }
    }
}
