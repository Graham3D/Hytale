package com.inigmasgames.hytalerpg;

import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.protocol.InteractionType;
import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.domain.SkillId;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.input.CommandOnlyRpgUiOpenInputAdapter;
import com.inigmasgames.hytalerpg.input.HytaleAbilitySkillInputAdapter;
import com.inigmasgames.hytalerpg.input.RpgSkillActivationService;
import com.inigmasgames.hytalerpg.links.ValidationCode;
import com.inigmasgames.hytalerpg.progress.AttributeAllocationService;
import com.inigmasgames.hytalerpg.ui.CharacterXpProjectionService;
import com.inigmasgames.hytalerpg.ui.HytaleResourceViewAdapter;
import com.inigmasgames.hytalerpg.ui.RpgUiProjectionService;
import com.inigmasgames.hytalerpg.ui.hud.HudVisibilityLease;
import com.inigmasgames.hytalerpg.ui.model.NativeResourceView;
import com.inigmasgames.hytalerpg.ui.model.SkillSlotView;
import com.inigmasgames.hytalerpg.ui.trace.RpgUiTraceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class Stage03PresentationTest {
    private final CharacterXpProjectionService xp = new CharacterXpProjectionService();
    @TempDir Path temporary;

    @Test void allNinetyEightXpTransitionsUseCanonicalRoundHalfUpToTenFormula() {
        long cumulative = 0;
        assertEquals(0, xp.levelStartXp(1));
        for (int level = 1; level <= 98; level++) {
            double pressure = 1.0 + 5.0 * Math.pow(Math.max(0.0, level - 80.0) / 18.0, 3.0);
            long expected = BigDecimal.valueOf(100.0 * Math.pow(level, 1.6) * pressure / 10.0)
                    .setScale(0, RoundingMode.HALF_UP).longValueExact() * 10L;
            assertEquals(expected, xp.xpToNext(level), "level " + level);
            cumulative += expected;
            assertEquals(cumulative, xp.levelStartXp(level + 1));
        }
        assertEquals(0, xp.xpToNext(99));
        assertEquals(99, xp.project(cumulative).level());
        assertEquals(1.0, xp.project(cumulative).progress());
    }

    @Test void tenPipProjectionMatchesRequiredBoundaryFixtures() {
        for (double percent : new double[]{0.0, 9.9, 10.0, 50.0, 99.9, 100.0}) {
            var view = xp.fixturePercent(percent);
            assertEquals(10, view.pipFill().size());
            double total = view.pipFill().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(percent / 10.0, total, 1e-9, percent + "%");
            assertTrue(view.pipFill().stream().allMatch(fill -> fill >= 0 && fill <= 1));
        }
    }

    @Test void allocationConsumesPendingFirstAndIsRevisionChecked() {
        var bundle = Stage01BTestSupport.bundle();
        var allocation = new AttributeAllocationService(bundle.service());
        UUID player = UUID.randomUUID();
        assertTrue(allocation.grantDevelopmentPoints(player, 5, "grant").success());
        var before = bundle.service().getPresentationView(player).state();
        assertEquals(5, before.unspentAttributePoints);
        assertEquals(5, before.pendingLevelUpPoints);
        int raw = before.attributes.get("STR");
        var accepted = allocation.allocate(player, RpgAttribute.STR, before.revision, "allocate");
        assertTrue(accepted.success());
        var after = bundle.service().getPresentationView(player).state();
        assertEquals(raw + 1, after.attributes.get("STR"));
        assertEquals(4, after.unspentAttributePoints);
        assertEquals(4, after.pendingLevelUpPoints);
        var stale = allocation.allocate(player, RpgAttribute.DEX, before.revision, "stale");
        assertFalse(stale.success());
        assertEquals(ValidationCode.STALE_REVISION, stale.code());
    }

    @Test void allocationSaveFailureRollsBackAttributeAndBothPointCounters() {
        var repository = new Stage01BTestSupport.InMemoryRepository();
        var bundle = Stage01BTestSupport.bundle(repository, new Stage01BTestSupport.RecordingTracer());
        var allocation = new AttributeAllocationService(bundle.service());
        UUID player = UUID.randomUUID();
        allocation.grantDevelopmentPoints(player, 5, "grant");
        var before = bundle.service().getPresentationView(player).state();
        repository.failSave = true;
        var failed = allocation.allocate(player, RpgAttribute.WIS, before.revision, "failure");
        assertEquals(ValidationCode.PERSISTENCE_FAILURE, failed.code());
        var after = bundle.service().getPresentationView(player).state();
        assertEquals(before.attributes, after.attributes);
        assertEquals(before.unspentAttributePoints, after.unspentAttributePoints);
        assertEquals(before.pendingLevelUpPoints, after.pendingLevelUpPoints);
        assertEquals(before.revision, after.revision);
    }

    @Test void noPointAndNegativeRequestsAreRejectedWithoutMutation() {
        var bundle = Stage01BTestSupport.bundle();
        var allocation = new AttributeAllocationService(bundle.service());
        UUID player = UUID.randomUUID();
        long revision = bundle.service().getPresentationView(player).state().revision;
        assertFalse(allocation.allocate(player, RpgAttribute.LUCK, revision, "none").success());
        assertFalse(allocation.grantDevelopmentPoints(player, -1, "negative").success());
        assertEquals(revision, bundle.service().getPresentationView(player).state().revision);
    }

    @Test void projectionHasFourLogicalSlotsAndReadsLiveResourcesInCanonicalOrder() {
        var bundle = Stage01BTestSupport.bundle();
        RpgCombatKernel kernel = RpgCombatKernel.createProduction();
        var projection = new RpgUiProjectionService(bundle.catalog(), bundle.service(),
                kernel.derivedStats(), kernel.cooldowns());
        UUID player = UUID.randomUUID();
        var nativeResources = new HytaleResourceViewAdapter.Snapshot(
                new NativeResourceView(31, 100), new NativeResourceView(72, 120), new NativeResourceView(44, 90));
        var empty = projection.hud(player, nativeResources, null);
        assertEquals(4, empty.skills().size());
        assertEquals(31, empty.mana().current());
        assertEquals(72, empty.health().current());
        assertEquals(44, empty.stamina().current());
        assertEquals("Ability1", empty.skills().getFirst().action());
        assertTrue(empty.skills().stream().allMatch(slot -> slot.state() == SkillSlotView.State.EMPTY));
        bundle.service().equipSkill(player, SkillSlot.SKILL02, new SkillId("fire_bolt"));
        var equipped = projection.hud(player, nativeResources, null).skills().get(1);
        assertEquals("Fire Bolt", equipped.name());
        assertEquals(SkillSlotView.State.UNAVAILABLE, equipped.state());
        assertEquals("EXECUTOR_NOT_IMPLEMENTED", equipped.unavailableReason());
        bundle.service().unequipSkill(player, SkillSlot.SKILL02);
        assertEquals(SkillSlotView.State.EMPTY,
                projection.hud(player, nativeResources, null).skills().get(1).state());
    }

    @Test void characterProjectionContainsAllAttributesDerivedStatsAndNativePools() {
        var bundle = Stage01BTestSupport.bundle();
        RpgCombatKernel kernel = RpgCombatKernel.createProduction();
        var projection = new RpgUiProjectionService(bundle.catalog(), bundle.service(),
                kernel.derivedStats(), kernel.cooldowns());
        UUID player = UUID.randomUUID();
        bundle.service().setDevelopmentAttribute(player, RpgAttribute.STR, 151);
        bundle.service().setDevelopmentAttribute(player, RpgAttribute.DEX, 251);
        bundle.service().setDevelopmentAttribute(player, RpgAttribute.INT, 351);
        bundle.service().setDevelopmentAttribute(player, RpgAttribute.WIS, 451);
        bundle.service().setDevelopmentAttribute(player, RpgAttribute.LUCK, 500);
        var view = projection.character(player, "Tester", new HytaleResourceViewAdapter.Snapshot(
                new NativeResourceView(80, 140), new NativeResourceView(90, 180), new NativeResourceView(70, 150)));
        assertEquals(5, view.derivedStats().rawAttributes().size());
        assertEquals(5, view.derivedStats().effectiveAttributes().size());
        assertTrue(view.derivedStats().maxHealth() > 100);
        assertTrue(view.derivedStats().heavyDamageMultiplier() > 1);
        assertTrue(view.derivedStats().cooldownRecovery() > 0);
        assertEquals(80, view.mana().current());
        assertEquals("Tester", view.displayName());
    }

    @Test void persistentIndicatorFollowsPendingPointsUntilFinalAllocation() {
        var bundle = Stage01BTestSupport.bundle();
        var allocation = new AttributeAllocationService(bundle.service());
        RpgCombatKernel kernel = RpgCombatKernel.createProduction();
        var projection = new RpgUiProjectionService(bundle.catalog(), bundle.service(),
                kernel.derivedStats(), kernel.cooldowns());
        UUID player = UUID.randomUUID();
        var nativeResources = new HytaleResourceViewAdapter.Snapshot(
                new NativeResourceView(100, 100), new NativeResourceView(100, 100), new NativeResourceView(100, 100));
        allocation.grantDevelopmentPoints(player, 2, "grant");
        assertTrue(projection.hud(player, nativeResources, null).showLevelUpNotice());
        for (int index = 0; index < 2; index++) {
            long revision = bundle.service().getPresentationView(player).state().revision;
            assertTrue(allocation.allocate(player, RpgAttribute.STR, revision, "spend" + index).success());
        }
        assertFalse(projection.hud(player, nativeResources, null).showLevelUpNotice());
    }

    @Test void abilityActivationStopsTypedBeforeResourceOrCooldownMutation() {
        var bundle = Stage01BTestSupport.bundle();
        RpgCombatKernel kernel = RpgCombatKernel.createProduction();
        UUID player = UUID.randomUUID();
        bundle.service().equipSkill(player, SkillSlot.SKILL01, new SkillId("quick_slash"));
        var activation = new RpgSkillActivationService(bundle.service(), bundle.tracer());
        var result = activation.request(new HytaleAbilitySkillInputAdapter.Request(
                player, SkillSlot.SKILL01, "Ability1", 42, "corr"));
        assertFalse(result.accepted());
        assertEquals(RpgSkillActivationService.Reason.EXECUTOR_NOT_IMPLEMENTED, result.reason());
        assertEquals(0.0, kernel.cooldowns().remaining(player, "quick_slash"));
        var tracer = (Stage01BTestSupport.RecordingTracer) bundle.tracer();
        assertTrue(tracer.records.stream().anyMatch(record -> record.eventType().name().equals("SKILL_ACTIVATION_REQUEST")));
        assertTrue(tracer.records.stream().anyMatch(record ->
                "EXECUTOR_NOT_IMPLEMENTED".equals(record.details().get("failureCode"))));
    }

    @Test void installedAbilityActionsMapExactlyToFourLogicalSlots() {
        assertEquals(SkillSlot.SKILL01, HytaleAbilitySkillInputAdapter.slot(InteractionType.Ability1));
        assertEquals(SkillSlot.SKILL02, HytaleAbilitySkillInputAdapter.slot(InteractionType.Ability2));
        assertEquals(SkillSlot.SKILL03, HytaleAbilitySkillInputAdapter.slot(InteractionType.Ability3));
        assertEquals(SkillSlot.SKILL04, HytaleAbilitySkillInputAdapter.slot(InteractionType.Ability4));
        assertNull(HytaleAbilitySkillInputAdapter.slot(InteractionType.Primary));
    }

    @Test void nativeHudVisibilityIsRestoredExactlyAndLeaseIsIdempotent() {
        Set<HudComponent> initial = EnumSet.of(HudComponent.Hotbar, HudComponent.Compass,
                HudComponent.Mana, HudComponent.Health, HudComponent.Stamina, HudComponent.Chat);
        class Port implements HudVisibilityLease.Port {
            Set<HudComponent> current = Set.copyOf(initial);
            @Override public Set<HudComponent> visible() { return current; }
            @Override public void setVisible(Set<HudComponent> components) { current = Set.copyOf(components); }
        }
        Port port = new Port();
        HudVisibilityLease lease = HudVisibilityLease.hideRpgResourceDuplicates(port);
        assertFalse(port.current.contains(HudComponent.Mana));
        assertTrue(port.current.contains(HudComponent.Hotbar));
        lease.restore();
        lease.restore();
        assertEquals(initial, port.current);
    }

    @Test void uiOpenAdapterHonestlyReportsCommandOnlyOnInstalledBuild() {
        assertEquals(com.inigmasgames.hytalerpg.input.RpgUiOpenInputAdapter.Availability.COMMAND_ONLY,
                new CommandOnlyRpgUiOpenInputAdapter().availability());
    }

    @Test void uiTraceFailureIsIsolatedFromCaller() throws Exception {
        Path parentIsFile = temporary.resolve("not-a-directory");
        Files.writeString(parentIsFile, "occupied");
        RpgUiTraceService trace = new RpgUiTraceService(parentIsFile.resolve("ui-trace.jsonl"));
        assertDoesNotThrow(() -> trace.trace(UUID.randomUUID(), "TEST", "correlation", java.util.Map.of("value", 1)));
        assertDoesNotThrow(trace::close);
    }
}
