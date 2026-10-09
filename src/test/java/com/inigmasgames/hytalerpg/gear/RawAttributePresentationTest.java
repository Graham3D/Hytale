package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.combat.attribute.*;
import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.ui.model.CharacterSheetViewModel;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class RawAttributePresentationTest {
    private final CombatBalanceProfile profile = CombatBalanceProfile.loadCanonical();
    private final DerivedStatService derived = new DerivedStatService(profile, new EffectiveAttributeService(profile));

    private CharacterSheetViewModel model(DerivedStats stats) {
        return new CharacterSheetViewModel(1, "QA", null, 0, 0, stats, null, null, null);
    }

    @Test void allFivePanelValuesRemainRawWhileCombatUsesDiminishedValues() {
        var raw = new EnumMap<RpgAttribute, Integer>(RpgAttribute.class);
        for (var stat : RpgAttribute.values()) raw.put(stat, 185);
        var stats = derived.derive(raw);
        var panel = model(stats);
        for (var stat : RpgAttribute.values()) {
            assertEquals("185", panel.attributeText(stat));
            assertEquals(176.25, stats.effective(stat), 1e-9);
        }
        double expectedScaling = 1 + profile.primaryScalingPerEffectivePoint * 176.25;
        assertEquals(expectedScaling, stats.heavyDamageMultiplier(), 1e-9);
        assertEquals(expectedScaling, stats.lightDamageMultiplier(), 1e-9);
        assertEquals(expectedScaling, stats.magicDamageMultiplier(), 1e-9);
        assertEquals(expectedScaling, stats.healingMultiplier(), 1e-9);
        assertEquals(profile.startingResourceMaximum + profile.healthPerEffectiveStrength
                * (176.25 - profile.startingRawAttribute), stats.maxHealth(), 1e-9);
    }

    @Test void tooltipColorAndEquipmentAdmissionAgreeAtRawThresholdsForEveryAttribute() {
        var seed = new GearAffixQaSuite(GearCatalog.load()).create("tooltip-common-battleaxe", UUID.randomUUID());
        for (var stat : RpgAttribute.values()) for (int required : new int[]{180, 185, 186}) {
            var baseline = Map.of(stat, 185);
            var stats = derived.derive(baseline);
            assertEquals("185", model(stats).attributeText(stat));
            var gate = new GearRequirements.Gate(1, Map.of(stat, required));
            var gear = new GearInstance(seed.schemaVersion(), seed.identity(), seed.definitionRevision(),
                    seed.baseId(), seed.baseName(), seed.category(), seed.sourceEra(), seed.itemLevel(),
                    seed.rarity(), seed.intrinsicThousandths(), seed.intrinsicStats(), gate, seed.affixes(),
                    seed.rngVersion(), seed.qaOnly());
            // This is the same candidate-aware raw map used by native tooltip presentation.
            var view = new HytaleGearEquipment.View(99, baseline, List.of(),
                    GearRequirements.resolve(99, baseline, List.of()));
            var requirementRaw = HytaleGearEquipment.requirementAttributes(view, gear.identity());
            assertEquals(185, requirementRaw.get(stat));
            var line = GearTooltip.describe(gear, 99, requirementRaw).stream()
                    .filter(l -> l.text().startsWith("Requires " + required + " ")).findFirst().orElseThrow();
            boolean accepted = required <= 185;
            assertEquals(accepted ? GearTooltip.Style.NORMAL : GearTooltip.Style.ERROR, line.style());
            assertEquals(!accepted, GearTooltip.ERROR_COLOR.equals(line.color()));
            assertEquals(accepted, gate.playerFeedback(99, requirementRaw) == null);
            // Exercise the actual native equipment reader's admission owner, not a test predicate.
            var equipped = GearEquipmentResolution.resolve(99, baseline,
                    List.of(new GearEquipmentResolution.Candidate(gear, true, true, true)));
            assertEquals(accepted, equipped.validity().valid().contains(gear.identity()));
            assertEquals(176.25, stats.effective(stat), 1e-9);
        }
    }

    @Test void permanentGearBonusesRemainIncludedBeforeDiminishingReturns() {
        var bonuses = new GearAffixRuntime.Effects(Map.of(RpgAttribute.STR, 5), 0, 0, 0, 0, 0, 0);
        var stats = bonuses.derive(derived, Map.of(RpgAttribute.STR, 180));
        assertEquals("185", model(stats).attributeText(RpgAttribute.STR));
        assertEquals(176.25, stats.effective(RpgAttribute.STR), 1e-9);
    }
}
