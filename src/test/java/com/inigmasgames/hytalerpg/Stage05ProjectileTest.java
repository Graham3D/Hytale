package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.domain.SkillId;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.SkillExecutionPort;
import com.inigmasgames.hytalerpg.execution.SkillExecutionRequest;
import com.inigmasgames.hytalerpg.execution.SkillExecutionResult;
import com.inigmasgames.hytalerpg.execution.SkillExecutionService;
import com.inigmasgames.hytalerpg.execution.SkillExecutorRegistry;
import com.inigmasgames.hytalerpg.execution.SkillInstanceLifecycle;
import com.inigmasgames.hytalerpg.execution.Stage04SkillProfile;
import com.inigmasgames.hytalerpg.execution.Stage04SkillProfiles;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.projectile.ProjectileFlight;
import java.util.EnumMap;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Stage05ProjectileTest {
    @Test void projectileProfilesPreserveExactCanonicalBounds() {
        Stage04SkillProfiles profiles = profiles();
        assertEquals(8, profiles.all().size());
        var fire = profiles.require("fire_bolt");
        assertEquals(Stage04SkillProfile.Family.PROJECTILE, fire.family());
        assertEquals(Set.of("STAFF", "WAND"), fire.allowedMainHandKinds());
        assertEquals("MANA", fire.resourceType());
        assertEquals(8.0, fire.resourceCost());
        assertEquals(1.4, fire.cooldownSeconds());
        assertEquals("MAGIC_WEAPON", fire.basePowerSource());
        assertEquals("MAGIC", fire.scaling());
        assertProjectile(fire.projectile(), "Projectile_Config_RPG_Fire_Bolt",
                24, 24, .30, .95, 1);
        assertEquals("BURN", fire.projectile().statusId());
        assertEquals(4, fire.projectile().periodicTicks());
        assertEquals(.10, fire.projectile().periodicCoefficient());
        assertEquals(1.0, fire.projectile().periodicIntervalSeconds());

        var snipe = profiles.require("snipe");
        assertEquals(Set.of("BOW"), snipe.allowedMainHandKinds());
        assertEquals("STAMINA", snipe.resourceType());
        assertEquals(12.0, snipe.resourceCost());
        assertEquals(10.0, snipe.cooldownSeconds());
        assertProjectile(snipe.projectile(), "Projectile_Config_RPG_Snipe",
                45, 48, .10, 2.00, 1);
        assertEquals("Weapon_Arrow_Crude", snipe.projectile().ammoItemId());
        assertEquals(1, snipe.projectile().ammoQuantity());
        assertTrue(snipe.projectile().fullyCharged());
        assertFalse(snipe.projectile().hasPeriodicStatus());
    }

    @Test void flightUsesTotalObservedPathAndExactDistanceDerivedLifetime() {
        ProjectileFlight straight = new ProjectileFlight(Vec3.ZERO, 24, 24);
        assertFalse(straight.observe(.5, new Vec3(0, 0, 12)).expired());
        var terminal = straight.observe(.5, new Vec3(0, 0, 24));
        assertTrue(terminal.expired());
        assertEquals(24, terminal.travelled(), 1e-12);
        assertEquals(1, terminal.maxLifetimeSeconds(), 1e-12);

        ProjectileFlight swept = new ProjectileFlight(Vec3.ZERO, 100, 10);
        assertFalse(swept.observe(.04, new Vec3(3, 0, 0)).expired());
        assertFalse(swept.observe(.04, new Vec3(3, 4, 0)).expired());
        assertTrue(swept.observe(.02, new Vec3(6, 4, 0)).expired());
        assertEquals(10, swept.travelled(), 1e-12);
    }

    @Test void fireBoltCommitsManaAndCapturesMagicWeaponSnapshot() {
        Harness harness = harness("fire_bolt", item("STAFF", null, 30.0), 100, 100);
        SkillExecutionResult result = harness.execute();
        assertTrue(result.committed());
        assertEquals(92, harness.port.resources.current(ResourceType.MANA), 1e-12);
        assertEquals(100, harness.port.resources.current(ResourceType.STAMINA), 1e-12);
        assertEquals(30, harness.port.context.snapshot().basePower(), 1e-12);
        assertEquals(.95, harness.port.context.snapshot().skillCoefficient(), 1e-12);
        assertEquals(1, harness.port.dispatches);
    }

    @Test void snipeCommitsStaminaOnceAndCarriesFullyChargedAmmoContract() {
        Harness harness = harness("snipe", item("BOW", 20.0, null), 100, 100);
        assertTrue(harness.execute().committed());
        assertEquals(88, harness.port.resources.current(ResourceType.STAMINA), 1e-12);
        assertEquals(100, harness.port.resources.current(ResourceType.MANA), 1e-12);
        assertTrue(harness.port.context.profile().projectile().fullyCharged());
        assertEquals(1, harness.port.context.profile().projectile().ammoQuantity());
    }

    @Test void invalidWeaponAndInsufficientManaRejectBeforeCommit() {
        Harness weapon = harness("fire_bolt", item("SWORD", 20.0, null), 100, 100);
        assertEquals("INVALID_MAIN_HAND", weapon.execute().code());
        assertEquals(100, weapon.port.resources.current(ResourceType.MANA), 1e-12);
        assertEquals(0, weapon.port.dispatches);

        Harness mana = harness("fire_bolt", item("WAND", null, 20.0), 7, 100);
        assertEquals("INSUFFICIENT_RESOURCE", mana.execute().code());
        assertEquals(7, mana.port.resources.current(ResourceType.MANA), 1e-12);
        assertEquals(0, mana.port.dispatches);
    }

    @Test void missingAmmoPrerequisiteRejectsBeforeResourceAndCooldownMutation() {
        Harness harness = harness("snipe", item("BOW", 20.0, null), 100, 100);
        harness.port.validation = SkillExecutionPort.Validation.reject("AMMUNITION_UNAVAILABLE");
        assertEquals("AMMUNITION_UNAVAILABLE", harness.execute().code());
        assertEquals(100, harness.port.resources.current(ResourceType.STAMINA), 1e-12);
        assertEquals(0, harness.kernel.cooldowns().remaining(harness.actor, "snipe"));
        assertEquals(0, harness.port.dispatches);
    }

    @Test void synchronousProjectileDispatchFailureRefundsCostAndClearsCooldown() {
        Harness harness = harness("snipe", item("BOW", 20.0, null), 100, 100);
        harness.port.throwOnDispatch = true;
        SkillExecutionResult result = harness.execute();
        assertEquals(SkillExecutionResult.Status.TERMINATED, result.status());
        assertEquals("EXECUTOR_ERROR", result.code());
        assertEquals(100, harness.port.resources.current(ResourceType.STAMINA), 1e-12);
        assertEquals(0, harness.kernel.cooldowns().remaining(harness.actor, "snipe"));
        assertEquals(1, harness.port.dispatches);
    }

    @Test void projectileFamilyDispatchAndTraceIdentityStaySingular() {
        Harness harness = harness("fire_bolt", item("WAND", null, 20.0), 100, 100);
        assertTrue(harness.execute().committed());
        var records = ((Stage01BTestSupport.RecordingTracer) harness.bundle.tracer()).records.stream()
                .filter(record -> "stage05-correlation".equals(record.correlationId())).toList();
        assertEquals(4, records.size());
        assertEquals(1, records.stream().map(record -> record.details().get("rootCastId")).distinct().count());
        assertEquals(1, records.stream().map(record -> record.details().get("skillInstanceId")).distinct().count());
        assertEquals(Stage04SkillProfile.Family.PROJECTILE, harness.port.context.profile().family());
        assertEquals(1, harness.port.dispatches);
    }

    @Test void burnPayloadIsFourIndependentNonCriticalTenthPowerTicks() {
        var fire = profiles().require("fire_bolt");
        assertEquals(.10, fire.projectile().periodicCoefficient(), 1e-12);
        assertEquals(4, fire.projectile().periodicTicks());
        assertEquals(4.0, fire.projectile().statusSeconds(), 1e-12);
        assertEquals(4.0, fire.projectile().periodicTicks()
                * fire.projectile().periodicIntervalSeconds(), 1e-12);
        assertEquals(.95, fire.damageCoefficient(), 1e-12);
        assertEquals(4.0, fire.authoredStatuses().get("BURN"), 1e-12);
    }

    private static void assertProjectile(Stage04SkillProfile.Projectile value, String config,
                                         double speed, double range, double radius,
                                         double coefficient, int targetCap) {
        assertNotNull(value);
        assertEquals(config, value.configId());
        assertEquals(speed, value.speed(), 1e-12);
        assertEquals(range, value.maxDistance(), 1e-12);
        assertEquals(radius, value.radius(), 1e-12);
        assertEquals(0, value.gravity(), 1e-12);
        assertEquals(coefficient, value.coefficient(), 1e-12);
        assertEquals(targetCap, value.targetCap());
        assertEquals(range / speed, value.maximumLifetimeSeconds(), 1e-12);
    }

    private static Stage04SkillProfiles profiles() {
        return Stage04SkillProfiles.loadCanonical(com.inigmasgames.hytalerpg.content.RpgCatalog.loadCanonical());
    }

    private static SkillExecutionPort.Item item(String kind, Double weapon, Double magic) {
        return new SkillExecutionPort.Item("test:" + kind.toLowerCase(), kind,
                new ItemPowerDescriptor("test:" + kind.toLowerCase(), Set.of(kind), weapon, magic));
    }

    private static Harness harness(String skill, SkillExecutionPort.Item main, double mana, double stamina) {
        var bundle = Stage01BTestSupport.bundle();
        UUID actor = UUID.randomUUID();
        assertTrue(bundle.service().equipSkill(actor, SkillSlot.SKILL01, new SkillId(skill)).success());
        RpgCombatKernel kernel = RpgCombatKernel.createProduction();
        FakePort port = new FakePort(main, mana, stamina);
        SkillExecutionService service = new SkillExecutionService(bundle.service(), profiles(), kernel,
                SkillExecutorRegistry.runtime(), new SkillInstanceLifecycle(), bundle.tracer());
        return new Harness(actor, bundle, kernel, service, port);
    }

    private record Harness(UUID actor, Stage01BTestSupport.Bundle bundle, RpgCombatKernel kernel,
                           SkillExecutionService service, FakePort port) {
        SkillExecutionResult execute() {
            return service.request(new SkillExecutionRequest(actor, SkillSlot.SKILL01, "Ability1", 51,
                    "stage05-correlation", Vec3.FORWARD), port);
        }
    }

    private static final class FakePort implements SkillExecutionPort {
        final Resources resources;
        final Equipment equipment;
        Validation validation = Validation.pass();
        SkillExecutionContext context;
        boolean throwOnDispatch;
        int dispatches;
        FakePort(Item main, double mana, double stamina) {
            resources = new Resources(mana, stamina);
            equipment = new Equipment(main, null);
        }
        @Override public boolean actorAliveAndUsable() { return true; }
        @Override public Equipment equipment() { return equipment; }
        @Override public NativeResourcePort resources() { return resources; }
        @Override public Validation familyPrerequisites(Stage04SkillProfile profile, CompiledSkillPlan plan) {
            return validation;
        }
        @Override public SkillExecutionResult executeStrike(SkillExecutionContext value) { return dispatch(value); }
        @Override public SkillExecutionResult executeMovement(SkillExecutionContext value) { return dispatch(value); }
        @Override public SkillExecutionResult executeReaction(SkillExecutionContext value) { return dispatch(value); }
        @Override public SkillExecutionResult executeProjectile(SkillExecutionContext value) { return dispatch(value); }
        private SkillExecutionResult dispatch(SkillExecutionContext value) {
            context = value;
            dispatches++;
            if (throwOnDispatch) throw new IllegalStateException("test dispatch failure");
            return SkillExecutionResult.committed("PROJECTILE_STARTED", 0, 0);
        }
    }

    private static final class Resources implements NativeResourcePort {
        private final EnumMap<ResourceType, Double> values = new EnumMap<>(ResourceType.class);
        Resources(double mana, double stamina) {
            values.put(ResourceType.MANA, mana);
            values.put(ResourceType.STAMINA, stamina);
        }
        @Override public double current(ResourceType type) { return values.getOrDefault(type, 0.0); }
        @Override public double maximum(ResourceType type) { return 100; }
        @Override public void setCurrent(ResourceType type, double value) { values.put(type, value); }
    }
}
