package com.inigmasgames.hytalerpg;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.attribute.DerivedStatService;
import com.inigmasgames.hytalerpg.combat.attribute.EffectiveAttributeService;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.cooldown.RpgCooldownService;
import com.inigmasgames.hytalerpg.combat.damage.CriticalRoller;
import com.inigmasgames.hytalerpg.combat.damage.DamageCalculationService;
import com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets;
import com.inigmasgames.hytalerpg.combat.damage.SkillScalingService;
import com.inigmasgames.hytalerpg.combat.diagnostics.CombatTrace;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata;
import com.inigmasgames.hytalerpg.combat.power.BasePowerResolver;
import com.inigmasgames.hytalerpg.combat.power.BasePowerSource;
import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import com.inigmasgames.hytalerpg.combat.power.ItemPowerRegistry;
import com.inigmasgames.hytalerpg.combat.power.LinkTreeWeaponClass;
import com.inigmasgames.hytalerpg.combat.resource.HomeRestorationService;
import com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort;
import com.inigmasgames.hytalerpg.combat.resource.ReservationService;
import com.inigmasgames.hytalerpg.combat.resource.ResourceCost;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.combat.resource.RpgResourceService;
import com.inigmasgames.hytalerpg.combat.snapshot.CombatSnapshot;
import com.inigmasgames.hytalerpg.combat.status.ControlProfile;
import com.inigmasgames.hytalerpg.combat.status.RpgStatusType;
import com.inigmasgames.hytalerpg.combat.status.StatusService;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.domain.LinkNodeId;
import com.inigmasgames.hytalerpg.domain.PassiveId;
import com.inigmasgames.hytalerpg.domain.PassiveSlot;
import com.inigmasgames.hytalerpg.domain.SkillId;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class Stage02CombatKernelTest {
    private CombatBalanceProfile profile;
    private EffectiveAttributeService effective;
    private DerivedStatService derived;

    @BeforeEach void setup() {
        profile = CombatBalanceProfile.loadCanonical();
        effective = new EffectiveAttributeService(profile);
        derived = new DerivedStatService(profile, effective);
    }

    @Test void balanceProfileIsVersionedAndCurveBreakpointsAreExact() {
        assertEquals(1, profile.schemaVersion);
        assertEquals("rpg.combat-kernel.r010", profile.profileId);
        assertAll(
                () -> assertEquals(150.0, effective.effective(150), 1e-12),
                () -> assertEquals(225.0, effective.effective(250), 1e-12),
                () -> assertEquals(275.0, effective.effective(350), 1e-12),
                () -> assertEquals(310.0, effective.effective(450), 1e-12),
                () -> assertEquals(320.0, effective.effective(500), 1e-12));
    }

    @Test void effectiveCurveIsContinuousAtEveryBreakpoint() {
        double epsilon = 1e-7;
        for (double point : profile.attributeCurve.breakpoints) {
            assertEquals(effective.effective(point), effective.effective(point - epsilon), epsilon * 1.01);
            assertEquals(effective.effective(point), effective.effective(point + epsilon), epsilon * 1.01);
        }
    }

    @Test void allFiveAttributesUseSameCurveAndRejectNegativeRawValues() {
        var stats = derived.derive(raw(500, 500, 500, 500, 500));
        for (RpgAttribute attribute : RpgAttribute.values()) assertEquals(320.0, stats.effective(attribute), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> effective.effective(-1));
    }

    @Test void levelOneMaximaAreExactlyOneHundredNotBaselinePlusE10() {
        var stats = derived.derive(raw(10, 10, 10, 10, 10));
        assertAll(() -> assertEquals(100.0, stats.maxHealth()),
                () -> assertEquals(100.0, stats.maxStamina()),
                () -> assertEquals(100.0, stats.maxMana()));
    }

    @Test void primaryDerivedStatsUseEffectiveStrDexIntAndWis() {
        var stats = derived.derive(raw(500, 500, 500, 500, 10));
        assertAll(() -> assertEquals(720.0, stats.maxHealth(), 1e-12),
                () -> assertEquals(255.0, stats.maxStamina(), 1e-12),
                () -> assertEquals(332.5, stats.maxMana(), 1e-12),
                () -> assertEquals(1.96, stats.heavyDamageMultiplier(), 1e-12),
                () -> assertEquals(1.96, stats.lightDamageMultiplier(), 1e-12),
                () -> assertEquals(1.96, stats.magicDamageMultiplier(), 1e-12),
                () -> assertEquals(1.96, stats.healingMultiplier(), 1e-12));
    }

    @Test void wisdomAndLuckSecondaryEquationsAndCapsAreApplied() {
        var stats = derived.derive(raw(10, 10, 10, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertTrue(stats.cooldownRecovery() < 0.30 && stats.cooldownRecovery() > 0.299);
        assertEquals(0.40, stats.learnRate(), 1e-12);
        assertTrue(stats.criticalChance() < 0.30 && stats.criticalChance() > 0.299);
        assertTrue(stats.upgradeSuccess() < 0.10 && stats.upgradeSuccess() > 0.099);
        assertTrue(stats.magicFind() > 1_000_000.0); // Magic Find is intentionally uncapped in Stage 02.
    }

    @Test void modifierBucketsUseAdditiveIncreasedReducedAndMultiplicativeMoreLess() {
        ModifierBuckets buckets = new ModifierBuckets(List.of(.20), List.of(.10), List.of(1.50), List.of(.20));
        assertEquals(1.32, buckets.factor(), 1e-12);
        assertEquals(0.0, new ModifierBuckets(List.of(), List.of(2.0), List.of(1.5), List.of()).factor(), 1e-12);
    }

    @Test void damageMathKeepsDoublePrecisionUntilOneHytaleFloatBoundary() {
        var service = new DamageCalculationService(new SkillScalingService(profile), new CriticalRoller(() -> 0.0));
        var result = service.calculate(DamageCalculationService.Request.direct(10, 100, 2,
                new ModifierBuckets(List.of(.20), List.of(.10), List.of(1.50), List.of(.20)), 1.0, 1.5));
        assertAll(() -> assertEquals(1.3, result.attributeMultiplier(), 1e-12),
                () -> assertEquals(13.0, result.scaledBasePower(), 1e-12),
                () -> assertEquals(26.0, result.skillRawDamage(), 1e-12),
                () -> assertEquals(34.32, result.preCritDamage(), 1e-12),
                () -> assertEquals(51.48, result.preMitigationDamage(), 1e-12),
                () -> assertEquals((float) 51.48, result.toHytaleDamageFloat()));
    }

    @Test void seededCritIsRepeatableDirectHitsCritAndPeriodicDoesNotByDefault() {
        var first = new DamageCalculationService(new SkillScalingService(profile), CriticalRoller.seeded(99));
        var second = new DamageCalculationService(new SkillScalingService(profile), CriticalRoller.seeded(99));
        for (int i = 0; i < 10; i++) {
            var request = DamageCalculationService.Request.direct(1, 0, 1, ModifierBuckets.NONE, .5, 1.5);
            assertEquals(first.calculate(request).critical(), second.calculate(request).critical());
        }
        var forced = new DamageCalculationService(new SkillScalingService(profile), new CriticalRoller(() -> 0.0));
        assertTrue(forced.calculate(DamageCalculationService.Request.direct(1, 0, 1, ModifierBuckets.NONE, 1, 1.5)).critical());
        assertFalse(forced.calculate(DamageCalculationService.Request.periodic(1, 0, 1, ModifierBuckets.NONE, 1, 1.5)).critical());
    }

    @Test void basePowerUsesExplicitClassificationAndNeverResourceTypeOrDisplayName() {
        var resolver = new BasePowerResolver(new ItemPowerRegistry(1, "test", List.of()));
        var light = resolver.resolve(new BasePowerResolver.Request(BasePowerSource.WEAPON,
                new ItemPowerDescriptor("vanilla:opaque-id", Set.of("DAGGER"), 12.0, null), null));
        assertEquals(LinkTreeWeaponClass.LIGHT, light.weaponClass());
        assertEquals(12.0, light.basePower());
        var magic = resolver.resolve(new BasePowerResolver.Request(BasePowerSource.MAGIC_WEAPON,
                new ItemPowerDescriptor("vanilla:another-id", Set.of("STAFF"), null, 19.0), null));
        assertEquals(LinkTreeWeaponClass.MAGIC, magic.weaponClass());
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(new BasePowerResolver.Request(
                BasePowerSource.WEAPON, new ItemPowerDescriptor("Legendary Longsword", Set.of(), 50.0, null), null)));
    }

    @Test void innateAndNoneBasePowerContractsAreExplicit() {
        var resolver = new BasePowerResolver(new ItemPowerRegistry(1, "test", List.of()));
        assertEquals(7.5, resolver.resolve(new BasePowerResolver.Request(BasePowerSource.INNATE, null, 7.5)).basePower());
        assertEquals(0.0, resolver.resolve(new BasePowerResolver.Request(BasePowerSource.NONE, null, null)).basePower());
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(new BasePowerResolver.Request(BasePowerSource.INNATE, null, null)));
    }

    @Test void declaredCostEnforcesManaStaminaOrNoneAndEfficiencyUsesMasterIntegerRule() {
        assertEquals(ResourceType.MANA, ResourceCost.fromDeclaration(10, 0).type());
        assertEquals(ResourceType.STAMINA, ResourceCost.fromDeclaration(0, 10).type());
        assertEquals(ResourceCost.NONE, ResourceCost.fromDeclaration(0, 0));
        assertThrows(IllegalArgumentException.class, () -> ResourceCost.fromDeclaration(1, 1));
        assertEquals(9.0, new ResourceCost(ResourceType.MANA, 10).modified(.85).amount());
        assertEquals(1.0, new ResourceCost(ResourceType.STAMINA, .1).modified(.01).amount());
    }

    @Test void resourceReserveCommitRefundAndInsufficientFailureAreTransactional() {
        UUID actor = UUID.randomUUID(); FakeResources port = new FakeResources(100, 100, 100, 100);
        var service = new RpgResourceService(profile, new ReservationService());
        var mana = new ResourceCost(ResourceType.MANA, 30);
        assertTrue(service.canAfford(actor, mana, port));
        var token = service.reserveCost(actor, mana, port);
        assertEquals(100, port.current(ResourceType.MANA));
        assertTrue(service.commitCost(token, port));
        assertEquals(70, port.current(ResourceType.MANA));
        assertFalse(service.refundIfUncommitted(token));
        service.finish(token);
        var held = service.reserveCost(actor, new ResourceCost(ResourceType.STAMINA, 20), port);
        assertTrue(service.refundIfUncommitted(held));
        assertEquals(100, port.current(ResourceType.STAMINA));
        assertFalse(service.canAfford(actor, new ResourceCost(ResourceType.MANA, 71), port));
        assertEquals(70, port.current(ResourceType.MANA));
    }

    @Test void passiveRegenAndNormalChargedHitRecoveryAreCappedAndDeduplicatedPerRoot() {
        UUID actor = UUID.randomUUID(); FakeResources port = new FakeResources(0, 100, 0, 100);
        var service = new RpgResourceService(profile, new ReservationService());
        assertEquals(1.5, service.regenerate(actor, ResourceType.MANA, 1, port), 1e-12);
        var normal = service.recoverHostileWeaponHit(actor, "root-a", false, port);
        assertTrue(normal.applied()); assertEquals(4.0, normal.manaRecovered(), 1e-12);
        assertFalse(service.recoverHostileWeaponHit(actor, "root-a", false, port).applied());
        var charged = service.recoverHostileWeaponHit(actor, "root-b", true, port);
        assertTrue(charged.applied()); assertEquals(12.0, charged.staminaRecovered(), 1e-12);
    }

    @Test void reservationsSumClampRegenerationRejectOversubscriptionAndReleaseWithoutMinting() {
        UUID actor = UUID.randomUUID(); FakeResources port = new FakeResources(100, 100, 100, 100);
        ReservationService reservations = new ReservationService();
        reservations.addPercentage(actor, "aura-a", .20, port);
        reservations.addFixed(actor, "aura-b", 10, port);
        assertEquals(30, reservations.reserved(actor, 100), 1e-12);
        assertEquals(70, reservations.spendableMaximum(actor, 100), 1e-12);
        assertEquals(70, port.current(ResourceType.MANA), 1e-12);
        assertThrows(IllegalStateException.class, () -> reservations.addFixed(actor, "bad", 71, port));
        assertTrue(reservations.remove(actor, "aura-a"));
        assertEquals(70, port.current(ResourceType.MANA), 1e-12);
        var service = new RpgResourceService(profile, reservations);
        service.regenerate(actor, ResourceType.MANA, 100, port);
        assertEquals(90, port.current(ResourceType.MANA), 1e-12);
    }

    @Test void bedAndHomeRestorationHonorReservationAndTiming() {
        UUID actor = UUID.randomUUID(); FakeResources port = new FakeResources(0, 100, 0, 100);
        ReservationService reservations = new ReservationService();
        reservations.addPercentage(actor, "aura", .25, port);
        var resources = new RpgResourceService(profile, reservations);
        resources.restoreBed(actor, port);
        assertEquals(75, port.current(ResourceType.MANA)); assertEquals(100, port.current(ResourceType.STAMINA));
        port.setCurrent(ResourceType.MANA, 0); port.setCurrent(ResourceType.STAMINA, 0);
        HomeRestorationService home = new HomeRestorationService(profile);
        assertFalse(home.observe(actor, true, 3, 1, resources, port));
        assertTrue(home.observe(actor, true, 3, 1, resources, port));
        assertEquals(75, port.current(ResourceType.MANA)); assertEquals(100, port.current(ResourceType.STAMINA));
    }

    @Test void cooldownUsesMasterRecoveryRateFormulaSwiftCapabilityAndRuntimeState() {
        AtomicLong clock = new AtomicLong(); UUID actor = UUID.randomUUID();
        var service = new RpgCooldownService(profile, clock::get);
        var modifiers = new CompiledSkillPlan.KernelModifiers(0, 1, .12);
        var calculation = service.startCooldown(actor, "test", 10, 1, .20, modifiers);
        assertEquals(10 / 1.32, calculation.finalSeconds(), 1e-12);
        assertFalse(service.canActivate(actor, "test"));
        clock.addAndGet(4_000_000_000L);
        assertEquals(10 / 1.32 - 4, service.remaining(actor, "test"), 1e-8);
        assertTrue(service.clear(actor, "test")); assertTrue(service.canActivate(actor, "test"));
    }

    @Test void cooldownRecoveryIsCappedAndDurationHasQuarterSecondFloor() {
        var service = new RpgCooldownService(profile, () -> 0L); UUID actor = UUID.randomUUID();
        var calculation = service.startCooldown(actor, "fast", .01, .1, 10,
                new CompiledSkillPlan.KernelModifiers(0, 1, 10));
        assertEquals(.75, calculation.appliedRecovery()); assertEquals(.25, calculation.finalSeconds());
    }

    @Test void potencyAndEfficiencyCompileIntoTypedKernelModifiersWithoutAddingACatalogPassive() {
        var bundle = Stage01BTestSupport.bundle(); UUID player = UUID.randomUUID();
        assertEquals(66, bundle.catalog().passives().size());
        assertTrue(bundle.catalog().passive(new PassiveId("swift_recovery")).isEmpty());
        bundle.service().equipSkill(player, SkillSlot.SKILL01, new SkillId("fire_bolt"));
        bundle.service().equipPassive(player, PassiveSlot.PASSIVE01, new PassiveId("potency"));
        bundle.service().equipPassive(player, PassiveSlot.PASSIVE02, new PassiveId("efficiency"));
        assertTrue(bundle.service().link(player, LinkNodeId.PASSIVE01, LinkNodeId.SKILL01).success());
        assertTrue(bundle.service().link(player, LinkNodeId.PASSIVE02, LinkNodeId.SKILL01).success());
        var modifiers = bundle.service().compile(player).plans().get(SkillSlot.SKILL01).kernelModifiers();
        assertEquals(.10, modifiers.scalablePayloadIncreased(), 1e-12);
        assertEquals(.85, modifiers.resourceCostMultiplier(), 1e-12);
        assertEquals(0, modifiers.cooldownRecoveryBonus(), 1e-12);
    }

    @Test void potencyTypedModifierFeedsDamageIncreasedBucket() {
        var service = new DamageCalculationService(new SkillScalingService(profile), new CriticalRoller(() -> 1));
        var planModifier = new CompiledSkillPlan.KernelModifiers(.10, 1, 0);
        var result = service.calculate(DamageCalculationService.Request.direct(100, 0, 1,
                new ModifierBuckets(List.of(planModifier.scalablePayloadIncreased()), List.of(), List.of(), List.of()), 0, 1.5));
        assertEquals(110, result.preMitigationDamage(), 1e-12);
    }

    @Test void chillStacksRefreshAndFifthStackConsumesIntoFrozen() {
        AtomicLong clock = new AtomicLong(); UUID target = UUID.randomUUID();
        StatusService statuses = new StatusService(profile, clock::get);
        for (int stack = 1; stack <= 4; stack++) {
            var result = statuses.apply(target, RpgStatusType.CHILL, ControlProfile.NORMAL);
            assertEquals(stack, result.stacks());
            clock.addAndGet(100_000_000L);
        }
        var threshold = statuses.apply(target, RpgStatusType.CHILL, ControlProfile.NORMAL);
        assertEquals(StatusService.Outcome.THRESHOLD, threshold.outcome());
        assertEquals(RpgStatusType.FROZEN, threshold.type());
        assertFalse(statuses.inspect(target).active().containsKey(RpgStatusType.CHILL));
        assertTrue(statuses.inspect(target).active().containsKey(RpgStatusType.FROZEN));
    }

    @Test void protectedFrozenUsesThirtyPercentSlowAndOtherHardControlsReject() {
        StatusService statuses = new StatusService(profile, () -> 0L); UUID target = UUID.randomUUID();
        ControlProfile boss = new ControlProfile(false, true, true);
        for (int i = 0; i < 5; i++) statuses.apply(target, RpgStatusType.CHILL, boss);
        assertTrue(statuses.inspect(target).active().containsKey(RpgStatusType.FROZEN_SUBSTITUTE_SLOW));
        assertEquals(StatusService.Outcome.REJECTED, statuses.apply(target, RpgStatusType.ROOT, boss).outcome());
    }

    @Test void burnPoisonAndNonstackingControlsRefreshTheirOwnTimers() {
        AtomicLong clock = new AtomicLong(); StatusService statuses = new StatusService(profile, clock::get); UUID target = UUID.randomUUID();
        assertEquals(StatusService.Outcome.APPLIED, statuses.apply(target, RpgStatusType.BURN, ControlProfile.NORMAL).outcome());
        clock.addAndGet(1_000_000_000L);
        assertEquals(StatusService.Outcome.REFRESHED, statuses.apply(target, RpgStatusType.BURN, ControlProfile.NORMAL).outcome());
        assertEquals(4.0, statuses.inspect(target).active().get(RpgStatusType.BURN).remainingSeconds(), 1e-12);
        assertEquals(StatusService.Outcome.APPLIED, statuses.apply(target, RpgStatusType.POISON, ControlProfile.NORMAL).outcome());
        assertEquals(StatusService.Outcome.APPLIED, statuses.apply(target, RpgStatusType.TAUNT, ControlProfile.NORMAL).outcome());
        assertEquals(StatusService.Outcome.REFRESHED, statuses.apply(target, RpgStatusType.TAUNT, ControlProfile.NORMAL).outcome());
    }

    @Test void frozenExpiresIntoThreeSecondImmunity() {
        AtomicLong clock = new AtomicLong(); StatusService statuses = new StatusService(profile, clock::get); UUID target = UUID.randomUUID();
        statuses.apply(target, RpgStatusType.FROZEN, ControlProfile.NORMAL);
        clock.set(2_100_000_000L); statuses.inspect(target);
        assertEquals(StatusService.Outcome.REJECTED, statuses.apply(target, RpgStatusType.FROZEN, ControlProfile.NORMAL).outcome());
        clock.set(5_200_000_000L);
        assertEquals(StatusService.Outcome.APPLIED, statuses.apply(target, RpgStatusType.FROZEN, ControlProfile.NORMAL).outcome());
    }

    @Test void combatSnapshotDefensivelyCopiesEveryMutableInput() {
        Map<RpgAttribute, Integer> raw = new EnumMap<>(raw(10, 10, 10, 10, 10));
        Map<RpgAttribute, Double> effectiveMap = new EnumMap<>(RpgAttribute.class);
        for (RpgAttribute attribute : RpgAttribute.values()) effectiveMap.put(attribute, 10.0);
        Map<String, Double> status = new HashMap<>(); status.put("burn", 1.0);
        CombatSnapshot snapshot = new CombatSnapshot("root", "instance", UUID.randomUUID(), raw, effectiveMap,
                derived.derive(raw), "item", LinkTreeWeaponClass.LIGHT, BasePowerSource.WEAPON, 5, "hash", 1,
                .05, 1.5, ModifierBuckets.NONE, ResourceCost.NONE, 2, status);
        raw.put(RpgAttribute.STR, 500); effectiveMap.put(RpgAttribute.STR, 320.0); status.put("burn", 9.0);
        assertEquals(10, snapshot.rawAttributes().get(RpgAttribute.STR));
        assertEquals(10, snapshot.effectiveAttributes().get(RpgAttribute.STR));
        assertEquals(1, snapshot.statusModifiers().get("burn"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.rawAttributes().put(RpgAttribute.STR, 20));
    }

    @Test void diagnosticsFailureCannotChangeCombatResult() {
        CombatTrace trace = new CombatTrace(record -> { throw new IllegalStateException("trace unavailable"); });
        assertDoesNotThrow(() -> trace.emit(UUID.randomUUID(), RpgTraceEventType.DAMAGE_CALC_BEGIN,
                new CombatTrace.Context("root", "instance", "correlation"), Map.of("basePower", 10)));
        var result = new DamageCalculationService(new SkillScalingService(profile), new CriticalRoller(() -> 1))
                .calculate(DamageCalculationService.Request.direct(10, 0, 1, ModifierBuckets.NONE, 0, 1.5));
        assertEquals(10, result.preMitigationDamage());
    }

    @Test void hytaleDamageAdapterIsConcreteAndAcceptsNativePipelineTypes() throws Exception {
        Method method = HytaleDamageAdapter.class.getMethod("apply", Ref.class, ComponentAccessor.class, Ref.class,
                DamageCause.class, HytaleDamageMetadata.class, DamageCalculationService.Result.class);
        assertEquals(void.class, method.getReturnType());
        assertNotNull(HytaleDamageAdapter.RPG_METADATA);
    }

    private static EnumMap<RpgAttribute, Integer> raw(int str, int dex, int intelligence, int wisdom, int luck) {
        EnumMap<RpgAttribute, Integer> map = new EnumMap<>(RpgAttribute.class);
        map.put(RpgAttribute.STR, str); map.put(RpgAttribute.DEX, dex); map.put(RpgAttribute.INT, intelligence);
        map.put(RpgAttribute.WIS, wisdom); map.put(RpgAttribute.LUCK, luck); return map;
    }

    private static final class FakeResources implements NativeResourcePort {
        private final EnumMap<ResourceType, Double> current = new EnumMap<>(ResourceType.class);
        private final EnumMap<ResourceType, Double> maximum = new EnumMap<>(ResourceType.class);
        FakeResources(double mana, double maxMana, double stamina, double maxStamina) {
            current.put(ResourceType.MANA, mana); maximum.put(ResourceType.MANA, maxMana);
            current.put(ResourceType.STAMINA, stamina); maximum.put(ResourceType.STAMINA, maxStamina);
        }
        @Override public double current(ResourceType type) { return current.get(type); }
        @Override public double maximum(ResourceType type) { return maximum.get(type); }
        @Override public void setCurrent(ResourceType type, double value) { current.put(type, value); }
    }
}
