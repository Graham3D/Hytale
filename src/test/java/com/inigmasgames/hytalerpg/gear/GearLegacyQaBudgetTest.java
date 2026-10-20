package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Saved operator-QA loot must not make every gear receipt unreadable on join. */
final class GearLegacyQaBudgetTest {
    private static GearInstance item(int count, String rngVersion, boolean qaOnly) {
        List<GearInstance.AffixRoll> rolls = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            rolls.add(new GearInstance.AffixRoll(String.format("WA-%03d", index),
                    index == 1 ? GearCatalog.Side.PREFIX : GearCatalog.Side.SUFFIX,
                    "legacy-qa-group-" + index, 5, 1.0,
                    new GearRequirements.Gate(1, Map.of()), "Frozen QA roll", "QA roll"));
        }
        return new GearInstance(1, UUID.randomUUID(), "gm.1.1.stage1.v1",
                "gm.plate_iron.head.h", "Oathsteel Helm", GearCatalog.Category.ARMOR,
                DifficultyId.HELL, 76, GearRarity.LEGENDARY, 950,
                Map.of("protectionPoints", 8.2), new GearRequirements.Gate(1, Map.of()),
                rolls, rngVersion, qaOnly);
    }

    @Test void legacyQaAffixBudgetRoundTripsWithoutChangingProductionBudget() {
        GearInstance saved = item(10, "qa159-v1/qa159-01", true);
        assertEquals(saved, GearInstance.fromJson(saved.toJson()));
        assertThrows(IllegalArgumentException.class,
                () -> item(10, "qa159-v1/qa159-01", false));
        assertThrows(IllegalArgumentException.class,
                () -> item(10, "unrecognized-qa", true));
        assertThrows(IllegalArgumentException.class,
                () -> item(11, "qa159-v1/qa159-01", true));
    }

    @Test void olderAuthoredQaEmptyAffixReceiptRemainsReadable() {
        GearInstance saved = item(0, "qa-authored-v1", true);
        // A frozen QA fixture may carry a rarity presentation without random affixes.
        assertEquals(saved, GearInstance.fromJson(saved.toJson()));
    }
}
