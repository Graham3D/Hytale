package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.damage.CriticalRoller;
import com.inigmasgames.hytalerpg.combat.damage.DamageCalculationService;
import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.combat.status.ControlProfile;
import com.inigmasgames.hytalerpg.combat.status.RpgStatusType;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.domain.LinkNodeId;
import com.inigmasgames.hytalerpg.domain.PassiveId;
import com.inigmasgames.hytalerpg.domain.PassiveSlot;
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
import com.inigmasgames.hytalerpg.execution.projectile.ProjectileExecutionPlan;
import com.inigmasgames.hytalerpg.execution.projectile.ProjectileFlight;
import com.inigmasgames.hytalerpg.execution.projectile.ProjectileInstance;
import com.inigmasgames.hytalerpg.execution.projectile.ProjectileLifecycleRegistry;
import com.inigmasgames.hytalerpg.execution.projectile.RpgProjectileService;
import java.util.EnumMap;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Stage05ProjectileTest {
    @Test void sixProjectileProfilesPreserveCanonicalCohortAndExcludeSnipe() {
        Stage04SkillProfiles profiles = profiles();
        assertEquals(12, profiles.all().size());
        assertFalse(profiles.supports("snipe"));
        assertProjectile(profiles.require("fire_bolt"), Set.of("STAFF", "WAND"), "MANA", 8, 1.4,
                "MAGIC_WEAPON", "MAGIC", "Projectile_Config_RPG_Fire_Bolt", 24, 24, .30, .95);
        assertProjectile(profiles.require("frost_bolt"), Set.of("STAFF", "WAND"), "MANA", 8, 1.5,
                "MAGIC_WEAPON", "MAGIC", "Projectile_Config_RPG_Frost_Bolt", 22, 24, .30, .85);
        assertProjectile(profiles.require("arcane_bolt"), Set.of("WAND", "SPELLBOOK"), "MANA", 7, 1.2,
                "MAGIC_WEAPON", "MAGIC", "Projectile_Config_RPG_Arcane_Bolt", 26, 26, .28, .90);
        assertProjectile(profiles.require("stone_bolt"), Set.of("STAFF", "WAND"), "MANA", 8, 2.2,
                "MAGIC_WEAPON", "MAGIC", "Projectile_Config_RPG_Stone_Bolt", 17, 20, .45, 1.20);
        assertProjectile(profiles.require("quick_shot"), Set.of("BOW", "CROSSBOW"), "STAMINA", 4, .9,
                "WEAPON", "LIGHT", "Projectile_Config_RPG_Quick_Shot_Bow", 30, 28, .075, .80);
        assertProjectile(profiles.require("axe_toss"), Set.of("BATTLEAXE"), "STAMINA", 8, 5,
                "WEAPON", "HEAVY", "Projectile_Config_RPG_Axe_Toss", 18, 20, .45, 1.20);
    }

    @Test void elementalAndPhysicalPayloadContractsAreExact() {
        var profiles = profiles();
        var fire = profiles.require("fire_bolt").projectile();
        assertEquals("BURN", fire.statusId()); assertEquals(4, fire.periodicTicks());
        assertEquals(.10, fire.periodicCoefficient()); assertEquals(1, fire.periodicIntervalSeconds());
        var frost = profiles.require("frost_bolt").projectile();
        assertEquals("CHILL", frost.statusId()); assertFalse(frost.hasPeriodicStatus());
        assertEquals(1.5, profiles.require("stone_bolt").projectile().knockbackDistance());
        assertTrue(profiles.require("arcane_bolt").projectile().statusId().isBlank());
        assertEquals("Weapon_Arrow_Crude", profiles.require("quick_shot").projectile().ammoItemId());
        assertEquals(1, profiles.require("quick_shot").projectile().ammoQuantity());
        assertFalse(profiles.require("axe_toss").projectile().requiresAmmo());
    }

    @Test void quickShotSelectsAuditedNativeBowAndCrossbowCarrierSpeeds() {
        var shot = profiles().require("quick_shot").projectile();
        assertEquals("Projectile_Config_RPG_Quick_Shot_Bow", shot.configIdFor("BOW"));
        assertEquals("Projectile_Config_RPG_Quick_Shot_Crossbow", shot.configIdFor("CROSSBOW"));
        assertEquals(30, shot.speedFor("BOW"));
        assertEquals(40, shot.speedFor("CROSSBOW"));
        assertEquals(28.0 / 30.0, shot.maximumLifetimeSeconds("BOW"), 1e-12);
        assertEquals(.7, shot.maximumLifetimeSeconds("CROSSBOW"), 1e-12);
    }

    @Test void flightUsesTotalObservedPathAndExactDistanceDerivedLifetime() {
        ProjectileFlight straight = new ProjectileFlight(Vec3.ZERO, 24, 24);
        assertFalse(straight.observe(.5, new Vec3(0, 0, 12)).expired());
        var terminal = straight.observe(.5, new Vec3(0, 0, 24));
        assertTrue(terminal.expired()); assertEquals(24, terminal.travelled(), 1e-12);
        assertEquals(1, terminal.maxLifetimeSeconds(), 1e-12);
        ProjectileFlight swept = new ProjectileFlight(Vec3.ZERO, 100, 10);
        assertFalse(swept.observe(.04, new Vec3(3, 0, 0)).expired());
        assertFalse(swept.observe(.04, new Vec3(3, 4, 0)).expired());
        assertTrue(swept.observe(.02, new Vec3(6, 4, 0)).expired());
        assertEquals(10, swept.travelled(), 1e-12);
    }

    @Test void planNormalizesVelocityAndFreezesOwnerSnapshotAndEquipment() {
        Harness harness = harness("fire_bolt", item("STAFF", null, 30.0), 100, 100);
        assertTrue(harness.execute().committed());
        SkillExecutionContext context = harness.port.context;
        var service = new RpgProjectileService(new ProjectileLifecycleRegistry());
        ProjectileExecutionPlan plan = service.buildPlan(context, harness.actor, new Vec3(1, 2, 3),
                new Vec3(20, 0, 20), context.profile().projectile().configId(), 24, 123);
        assertEquals(24, plan.velocity().length(), 1e-12);
        assertEquals(harness.actor, plan.ownerId());
        assertSame(context.snapshot(), plan.snapshot());
        assertEquals(context.snapshot().compiledPlanHash(), plan.compiledPlanHash());
        assertEquals(context.equipment(), harness.port.equipment);
        assertEquals(30, plan.snapshot().basePower(), 1e-12);
        assertEquals(0, plan.generation());
    }

    @Test void lifecycleValidatesTargetDeduplicatesAndTerminatesTerrain() {
        Harness harness = harness("arcane_bolt", item("WAND", null, 20.0), 100, 100);
        assertTrue(harness.execute().committed());
        var registry = new ProjectileLifecycleRegistry();
        var service = new RpgProjectileService(registry);
        ProjectileExecutionPlan plan = service.buildPlan(harness.port.context, harness.actor, Vec3.ZERO,
                Vec3.FORWARD, "test-config", 26, 1);
        ProjectileInstance instance = service.onProjectileSpawn(plan);
        assertFalse(service.onEnemyContact(instance, ""));
        assertTrue(service.onEnemyContact(instance, "target-a"));
        assertFalse(service.onEnemyContact(instance, "target-a"));
        assertTrue(instance.previouslyHit("target-a"));
        assertTrue(service.onTerrainContact(instance, new Vec3(0, 0, 4)));
        assertEquals("TERRAIN_HIT", instance.termination().orElseThrow().reason());
        assertEquals(0, registry.size());
    }

    @Test void ownerCancellationRemovesEveryRegisteredCarrierWithoutOrphans() {
        Harness first = harness("fire_bolt", item("WAND", null, 20.0), 100, 100);
        assertTrue(first.execute().committed());
        var registry = new ProjectileLifecycleRegistry(); var service = new RpgProjectileService(registry);
        var plan = service.buildPlan(first.port.context, first.actor, Vec3.ZERO, Vec3.FORWARD, "config", 24, 1);
        service.onProjectileSpawn(plan);
        assertEquals(1, service.cancelOwner(first.actor, "WORLD_UNLOAD").size());
        assertEquals(0, registry.size());
    }

    @Test void fireBoltCommitsManaAndCapturesMagicDamageAndBurnRequest() {
        Harness harness = harness("fire_bolt", item("STAFF", null, 30.0), 100, 100);
        assertTrue(harness.execute().committed());
        assertEquals(92, harness.port.resources.current(ResourceType.MANA), 1e-12);
        assertEquals(30, harness.port.context.snapshot().basePower(), 1e-12);
        assertEquals(.95, harness.port.context.snapshot().skillCoefficient(), 1e-12);
        assertEquals(4.0, harness.port.context.snapshot().statusModifiers().get("BURN"));
    }

    @Test void frostBoltRequestsExactlyOneChillStack() {
        Harness harness = harness("frost_bolt", item("WAND", null, 30.0), 100, 100);
        assertTrue(harness.execute().committed());
        UUID target = UUID.randomUUID();
        var applied = harness.kernel.statuses().apply(target, RpgStatusType.CHILL, ControlProfile.NORMAL);
        assertEquals(1, applied.stacks());
        assertEquals(1, harness.kernel.statuses().inspect(target).active().get(RpgStatusType.CHILL).stacks());
    }

    @Test void arcaneBoltIsPlainSingleHitMagicAndStoneRequestsKnockback() {
        Harness arcane = harness("arcane_bolt", item("SPELLBOOK", null, 25.0), 100, 100);
        assertTrue(arcane.execute().committed());
        assertTrue(arcane.port.context.profile().projectile().statusId().isBlank());
        assertEquals(.90, arcane.port.context.snapshot().skillCoefficient());
        Harness stone = harness("stone_bolt", item("STAFF", null, 25.0), 100, 100);
        assertTrue(stone.execute().committed());
        assertEquals(1.20, stone.port.context.snapshot().skillCoefficient());
        assertEquals(1.5, stone.port.context.profile().projectile().knockbackDistance());
    }

    @Test void quickShotAndAxeTossUsePhysicalSnapshotsAndExactCommit() {
        Harness shot = harness("quick_shot", item("CROSSBOW", 20.0, null), 100, 100);
        assertTrue(shot.execute().committed());
        assertEquals(96, shot.port.resources.current(ResourceType.STAMINA), 1e-12);
        assertEquals(20, shot.port.context.snapshot().basePower(), 1e-12);
        assertEquals(.80, shot.port.context.snapshot().skillCoefficient(), 1e-12);
        Harness axe = harness("axe_toss", item("BATTLEAXE", 35.0, null), 100, 100);
        assertTrue(axe.execute().committed());
        assertEquals(92, axe.port.resources.current(ResourceType.STAMINA), 1e-12);
        assertEquals(35, axe.port.context.snapshot().basePower(), 1e-12);
        assertEquals("HEAVY", axe.port.context.profile().scaling());
    }

    @Test void invalidWeaponInsufficientResourceAndMissingAmmoRejectBeforeSpawn() {
        Harness weapon = harness("fire_bolt", item("SWORD", 20.0, null), 100, 100);
        assertEquals("INVALID_MAIN_HAND", weapon.execute().code()); assertEquals(0, weapon.port.dispatches);
        Harness mana = harness("fire_bolt", item("WAND", null, 20.0), 7, 100);
        assertEquals("INSUFFICIENT_RESOURCE", mana.execute().code()); assertEquals(7, mana.port.resources.current(ResourceType.MANA));
        Harness ammo = harness("quick_shot", item("BOW", 20.0, null), 100, 100);
        ammo.port.validation = SkillExecutionPort.Validation.reject("AMMUNITION_UNAVAILABLE");
        assertEquals("AMMUNITION_UNAVAILABLE", ammo.execute().code());
        assertEquals(100, ammo.port.resources.current(ResourceType.STAMINA));
        assertEquals(0, ammo.kernel.cooldowns().remaining(ammo.actor, "quick_shot"));
    }

    @Test void synchronousSpawnFailureRefundsCostAndClearsCooldown() {
        Harness harness = harness("quick_shot", item("BOW", 20.0, null), 100, 100);
        harness.port.throwOnDispatch = true;
        SkillExecutionResult result = harness.execute();
        assertEquals(SkillExecutionResult.Status.TERMINATED, result.status());
        assertEquals(100, harness.port.resources.current(ResourceType.STAMINA), 1e-12);
        assertEquals(0, harness.kernel.cooldowns().remaining(harness.actor, "quick_shot"));
    }

    @Test void projectileFamilyDispatchAndCorrelationStaySingular() {
        Harness harness = harness("fire_bolt", item("WAND", null, 20.0), 100, 100);
        assertTrue(harness.execute().committed());
        var records = ((Stage01BTestSupport.RecordingTracer) harness.bundle.tracer()).records.stream()
                .filter(record -> "stage05-correlation".equals(record.correlationId())).toList();
        assertEquals(4, records.size());
        assertEquals(1, records.stream().map(record -> record.details().get("rootCastId")).distinct().count());
        assertEquals(1, records.stream().map(record -> record.details().get("skillInstanceId")).distinct().count());
        assertEquals(1, harness.port.dispatches);
    }

    @Test void compiledForkRemainsMetadataAndDoesNotSpawnChildrenBeforeStage07() {
        Harness harness = harness("fire_bolt", item("WAND", null, 20.0), 100, 100);
        assertTrue(harness.bundle.service().equipPassive(harness.actor, PassiveSlot.PASSIVE06,
                new PassiveId("fork")).success());
        assertTrue(harness.bundle.service().link(harness.actor, LinkNodeId.PASSIVE06, LinkNodeId.SKILL01).success());
        assertTrue(harness.execute().committed());
        assertTrue(harness.port.context.compiledPlan().continuation().stream().anyMatch(value -> value.startsWith("FORK")));
        var service = new RpgProjectileService(new ProjectileLifecycleRegistry());
        service.onProjectileSpawn(service.buildPlan(harness.port.context, harness.actor, Vec3.ZERO, Vec3.FORWARD,
                "config", 24, 1));
        assertEquals(1, service.registry().size());
    }

    @Test void stage02CritPolicyIsReusedWithoutProjectileFormula() {
        Harness harness = harness("axe_toss", item("BATTLEAXE", 30.0, null), 100, 100);
        assertTrue(harness.execute().committed());
        var snapshot = harness.port.context.snapshot();
        var noncrit = new DamageCalculationService(harness.kernel.scaling(), new CriticalRoller(() -> 1.0))
                .calculate(DamageCalculationService.Request.direct(snapshot.basePower(), 10,
                        snapshot.skillCoefficient(), snapshot.modifiers(), 1.0, snapshot.criticalMultiplier()));
        var crit = new DamageCalculationService(harness.kernel.scaling(), new CriticalRoller(() -> 0.0))
                .calculate(DamageCalculationService.Request.direct(snapshot.basePower(), 10,
                        snapshot.skillCoefficient(), snapshot.modifiers(), 1.0, snapshot.criticalMultiplier()));
        assertFalse(noncrit.critical()); assertTrue(crit.critical());
        assertEquals(noncrit.preMitigationDamage() * snapshot.criticalMultiplier(),
                crit.preMitigationDamage(), 1e-9);
    }

    @Test void canonicalGenerationAndSpawnBudgetsRejectInvalidDerivedPlan() {
        Harness harness = harness("fire_bolt", item("WAND", null, 20.0), 100, 100);
        assertTrue(harness.execute().committed());
        var valid = ProjectileExecutionPlan.generationZero(harness.port.context, harness.actor,
                Vec3.ZERO, Vec3.FORWARD, "config", 24, 1);
        var invalid = new ProjectileExecutionPlan(valid.rootCastId(), valid.skillInstanceId(), "derived-4",
                valid.ownerId(), valid.skillId(), valid.compiledPlanHash(), valid.snapshot(), 4,
                valid.remainingContinuationBudgets(),
                valid.remainingSpawnedEffects(), valid.remainingTriggeredSecondaries(), 2,
                valid.configId(), valid.origin(), valid.velocity(), valid.radius(), valid.maxDistance(),
                valid.maxLifetimeSeconds());
        assertThrows(IllegalStateException.class,
                () -> new ProjectileLifecycleRegistry().register(new ProjectileInstance(invalid)));
        assertEquals(3, harness.port.context.compiledPlan().safetyBudgets().maxGeneration());
        assertEquals(48, harness.port.context.compiledPlan().safetyBudgets().maxSpawnedEffects());
        assertEquals(16, harness.port.context.compiledPlan().safetyBudgets().maxTriggeredSecondaries());
    }

    private static void assertProjectile(Stage04SkillProfile value, Set<String> weapons, String resource,
                                         double cost, double cooldown, String power, String scaling,
                                         String config, double speed, double range, double radius, double coefficient) {
        assertEquals(Stage04SkillProfile.Family.PROJECTILE, value.family()); assertEquals(weapons, value.allowedMainHandKinds());
        assertEquals(resource, value.resourceType()); assertEquals(cost, value.resourceCost());
        assertEquals(cooldown, value.cooldownSeconds()); assertEquals(power, value.basePowerSource());
        assertEquals(scaling, value.scaling()); assertEquals(config, value.projectile().configId());
        assertEquals(speed, value.projectile().speed()); assertEquals(range, value.projectile().maxDistance());
        assertEquals(radius, value.projectile().radius()); assertEquals(coefficient, value.projectile().coefficient());
        assertEquals(range / speed, value.projectile().maximumLifetimeSeconds(), 1e-12);
    }

    private static Stage04SkillProfiles profiles() {
        return Stage04SkillProfiles.loadCanonical(com.inigmasgames.hytalerpg.content.RpgCatalog.loadCanonical());
    }
    private static SkillExecutionPort.Item item(String kind, Double weapon, Double magic) {
        return new SkillExecutionPort.Item("test:" + kind.toLowerCase(), kind,
                new ItemPowerDescriptor("test:" + kind.toLowerCase(), Set.of(kind), weapon, magic));
    }
    private static Harness harness(String skill, SkillExecutionPort.Item main, double mana, double stamina) {
        var bundle = Stage01BTestSupport.bundle(); UUID actor = UUID.randomUUID();
        assertTrue(bundle.service().equipSkill(actor, SkillSlot.SKILL01, new SkillId(skill)).success());
        RpgCombatKernel kernel = new RpgCombatKernel(com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile.loadCanonical(),
                new CriticalRoller(() -> 1.0));
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
        final Resources resources; Equipment equipment; Validation validation = Validation.pass();
        SkillExecutionContext context; boolean throwOnDispatch; int dispatches;
        FakePort(Item main, double mana, double stamina) {
            resources = new Resources(mana, stamina); equipment = new Equipment(main, null);
        }
        @Override public boolean actorAliveAndUsable() { return true; }
        @Override public Equipment equipment() { return equipment; }
        @Override public NativeResourcePort resources() { return resources; }
        @Override public Validation familyPrerequisites(Stage04SkillProfile profile, CompiledSkillPlan plan) { return validation; }
        @Override public SkillExecutionResult executeStrike(SkillExecutionContext value) { return dispatch(value); }
        @Override public SkillExecutionResult executeMovement(SkillExecutionContext value) { return dispatch(value); }
        @Override public SkillExecutionResult executeReaction(SkillExecutionContext value) { return dispatch(value); }
        @Override public SkillExecutionResult executeProjectile(SkillExecutionContext value) { return dispatch(value); }
        private SkillExecutionResult dispatch(SkillExecutionContext value) {
            context = value; dispatches++; if (throwOnDispatch) throw new IllegalStateException("test dispatch failure");
            return SkillExecutionResult.committed("PROJECTILE_STARTED", 0, 0);
        }
    }
    private static final class Resources implements NativeResourcePort {
        private final EnumMap<ResourceType, Double> values = new EnumMap<>(ResourceType.class);
        Resources(double mana, double stamina) { values.put(ResourceType.MANA, mana); values.put(ResourceType.STAMINA, stamina); }
        @Override public double current(ResourceType type) { return values.getOrDefault(type, 0.0); }
        @Override public double maximum(ResourceType type) { return 100; }
        @Override public void setCurrent(ResourceType type, double value) { values.put(type, value); }
    }
}
