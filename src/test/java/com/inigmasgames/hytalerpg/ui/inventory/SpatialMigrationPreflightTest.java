package com.inigmasgames.hytalerpg.ui.inventory;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class SpatialMigrationPreflightTest {
    private static final FootprintCatalog CATALOG = FootprintCatalog.loadDefault();

    @Test void catalogUsesExplicitReviewedBindingsAndNoUnknownFallback() {
        assertEquals(2, CATALOG.revision());
        assertEquals(5576, CATALOG.bindingCount());
        assertEquals(new SpatialLayout.Size(2, 4), CATALOG.size("Weapon_Shortbow_Iron"));
        assertEquals(new SpatialLayout.Size(2, 4), CATALOG.size("Weapon_Staff_Adamantite"));
        assertEquals(new SpatialLayout.Size(2, 3), CATALOG.size("Armor_Iron_Chest"));
        assertEquals(new SpatialLayout.Size(1, 1), CATALOG.size("Potion_Health"));
        assertEquals(new SpatialLayout.Size(2, 4), CATALOG.size("RPG_Gear_battleaxe_adamantite_h_Legendary"));
        assertEquals(new SpatialLayout.Size(2, 4), CATALOG.size("RPG_Gear_shortbow_copper_n"));
        assertEquals(new SpatialLayout.Size(1, 1), CATALOG.size("RPG_Ability_Fireball"));
        assertNull(CATALOG.size("Core_Tavern"));
        assertNull(CATALOG.size("Invented_Unreviewed_Weapon"));
    }

    @Test void preflightConservesAllInputsAndDistinguishesUnknownFromNoRectangle() {
        var inputs = new ArrayList<SpatialMigrationPreflight.Input>();
        for (short i = 0; i < 72; i++) inputs.add(new SpatialMigrationPreflight.Input(i, "Rock_Stone"));
        inputs.add(new SpatialMigrationPreflight.Input((short)72, "Weapon_Shortbow_Iron"));
        inputs.add(new SpatialMigrationPreflight.Input((short)73, "Invented_Unreviewed_Weapon"));
        var result = SpatialMigrationPreflight.plan(18, 4, CATALOG, inputs);
        assertEquals(72, result.placed().size());
        assertEquals(2, result.overflow().size());
        assertEquals(SpatialMigrationPreflight.Reason.NO_RECTANGLE, result.overflow().get(0).reason());
        assertEquals(SpatialMigrationPreflight.Reason.UNMAPPED, result.overflow().get(1).reason());
        assertEquals(result, SpatialMigrationPreflight.plan(18, 4, CATALOG, inputs));
        assertEquals(74, inputs.size());
    }

    @Test void randomizedPlansNeverLoseDuplicateOrOverlapAnInput() {
        var random = new Random(0x5A71A1L);
        var ids = List.of("Weapon_Shortbow_Iron", "Weapon_Sword_Iron", "Armor_Iron_Chest",
                "Potion_Health", "Rock_Stone", "Unknown_Rare_Base");
        for (int attempt = 0; attempt < 500; attempt++) {
            var inputs = new ArrayList<SpatialMigrationPreflight.Input>();
            int count = random.nextInt(95);
            for (short slot = 0; slot < count; slot++)
                inputs.add(new SpatialMigrationPreflight.Input(slot, ids.get(random.nextInt(ids.size()))));
            var result = SpatialMigrationPreflight.plan(18, 4, CATALOG, inputs);
            var seenSlots = new HashSet<Short>();
            var cells = new HashSet<String>();
            for (var entry : result.placed()) {
                assertTrue(seenSlots.add(entry.backingSlot()));
                for (int y = entry.position().y(); y < entry.position().y() + entry.size().height(); y++)
                    for (int x = entry.position().x(); x < entry.position().x() + entry.size().width(); x++) {
                        assertTrue(x >= 0 && x < 18 && y >= 0 && y < 4);
                        assertTrue(cells.add(x + "," + y));
                    }
            }
            for (var entry : result.overflow()) assertTrue(seenSlots.add(entry.backingSlot()));
            assertEquals(count, seenSlots.size());
            assertEquals(result, SpatialMigrationPreflight.plan(18, 4, CATALOG, inputs));
        }
    }

    @Test void duplicateSlotsAreRejectedBeforeProducingAPlan() {
        var duplicate = List.of(new SpatialMigrationPreflight.Input((short)2, "Rock_Stone"),
                new SpatialMigrationPreflight.Input((short)2, "Potion_Health"));
        assertThrows(IllegalArgumentException.class, () -> SpatialMigrationPreflight.plan(18, 4, CATALOG, duplicate));
    }
}
