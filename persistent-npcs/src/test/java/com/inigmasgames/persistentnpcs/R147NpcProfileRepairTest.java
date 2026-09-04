package com.inigmasgames.persistentnpcs;

import com.hypixel.hytale.protocol.ItemArmorSlot;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemArmor;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.profile.NpcInventoryState;
import com.inigmasgames.persistentnpcs.profile.NpcStatsSnapshotService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Real stats service over SDK armor fixtures; no game process or player mutation. */
public final class R147NpcProfileRepairTest {
    public static void main(String[] args) throws Exception {
        var service = new NpcStatsSnapshotService();
        UUID npc = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        var empty = service.captureEquipmentOnly(npc, new SimpleItemContainer((short) 4), session, 7, 0);
        assert empty.defense().orElseThrow().types().isEmpty();
        assert empty.health().isEmpty() && empty.stamina().isEmpty() && empty.mana().isEmpty();
        assert empty.npcEntityUuid() == null && empty.npcStableId().equals(npc);
        assert empty.sessionId().equals(session) && empty.pageGeneration() == 7;

        Map<String, Double> resistance = Map.of("Test_Head", 3.5, "Test_Chest", 12.0,
                "Test_Hands", 2.0, "Test_Legs", 6.5);
        var armor = List.of(persisted(0, "Test_Head"), persisted(1, "Test_Chest"),
                persisted(2, "Test_Hands"), persisted(3, "Test_Legs"));
        // Hidden visual slots still contribute authoritative Defense.
        var state = new NpcInventoryState(2, npc, armor, List.of(), List.of(), false,
                true, true, true, true);
        var equipped = service.captureEquipmentOnly(npc, fixture(state, resistance), session, 7, 1);
        assert equipped.defense().orElseThrow().types().get("Physical").flat() == 24.0;
        assert equipped.equipmentRevision() == 1;
        assert equipped.health().isEmpty() && equipped.stamina().isEmpty() && equipped.mana().isEmpty();

        // The production JSON schema round-trips armor; a new service derives the same value.
        // Fixture item lookup replaces the server asset registry, not the calculation under test.
        Path saved = Files.createTempFile("r147-unspawned-armor-", ".json");
        try {
            Files.writeString(saved, JsonFiles.GSON.toJson(state));
            var reopened = JsonFiles.read(saved, NpcInventoryState.class);
            var restart = new NpcStatsSnapshotService().captureEquipmentOnly(npc,
                    fixture(reopened, resistance), UUID.randomUUID(), 1, 0);
            assert restart.defense().orElseThrow().types().get("Physical").flat() == 24.0;
            var removed = new NpcInventoryState(2, npc, armor.subList(1, 4), List.of(), List.of(), false);
            var refreshed = service.captureEquipmentOnly(npc, fixture(removed, resistance), session, 7, 2);
            assert refreshed.defense().orElseThrow().types().get("Physical").flat() == 20.5;
            assert refreshed.equipmentRevision() == 2;
        } finally {
            Files.deleteIfExists(saved);
        }
        var nonArmor = new NpcInventoryState(2, npc, List.of(persisted(0, "Test_NonArmor")),
                List.of(), List.of(), false);
        assert service.captureEquipmentOnly(npc, fixture(nonArmor, resistance), session, 7, 3)
                .defense().orElseThrow().types().isEmpty();

        String page = Files.readString(Path.of("src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        String ui = Files.readString(Path.of("src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcProfile.ui"));
        assert page.contains("map(NpcProfile::name).orElse(npcName)");
        assert page.contains("statsService.captureEquipmentOnly(authoringSession.npcStableId(),");
        assert page.contains("inventory.armor(), authoringSession.sessionId()");
        assert page.contains("commands.set(\"#InfiniteAmmoCheckBox.TooltipText\"");
        assert !page.contains("#InfiniteAmmoHint") && !ui.contains("#InfiniteAmmoHint");
        assert page.contains("if (!failure) return;") && page.contains("NPC_PROFILE_INITIAL_STATUS");
        assert ui.contains("Text: \"NPC GEAR & STATS\"");
        assert ui.contains("Bottom: -50, Width: 205, Height: 198");
        assert 205 * 792 == 198 * 820 : "Ground artwork must retain its aspect ratio";
        assert ui.contains("Width: 340, Height: 326");
        assert page.contains("commands.set(\"#NpcCharacterPreview.Visible\", preview != null)");
        assert page.contains("commands, \"#NpcInventoryGrid.Slots\", inventory.inventory()");
        assert page.contains("commands, \"#PlayerInventoryGrid.Slots\", playerInventory,");
        System.out.println("R147 native-grid/cleanup and unspawned armor Defense regressions passed; connected validation pending.");
    }

    private static NpcInventoryState.PersistedItemStack persisted(int slot, String id) {
        return new NpcInventoryState.PersistedItemStack((short) slot, id, 1, 0, 0, 0, null, false);
    }

    private static SimpleItemContainer fixture(NpcInventoryState state, Map<String, Double> resistance) {
        ItemStack[] slots = new ItemStack[4];
        for (var entry : state.armor()) {
            Double value = resistance.get(entry.itemId());
            ItemArmor fixtureArmor = value == null ? null : new ItemArmor(ItemArmorSlot.VALUES[entry.slot()], value, null, null) {
                @Override public Map<com.hypixel.hytale.server.core.modules.entity.damage.DamageCause,
                        com.hypixel.hytale.server.core.modules.entity.damage.ResistanceModifier[]> getDamageResistanceValues() {
                    return Map.of(PHYSICAL, new com.hypixel.hytale.server.core.modules.entity.damage.ResistanceModifier[0]);
                }
            };
            Item item = new Item(entry.itemId()) {
                @Override public ItemArmor getArmor() { return fixtureArmor; }
            };
            slots[entry.slot()] = new FixtureStack(entry.itemId(), item);
        }
        return new SimpleItemContainer((short) 4) {
            @Override public ItemStack getItemStack(short slot) { return slots[slot]; }
        };
    }

    private static final com.hypixel.hytale.server.core.modules.entity.damage.DamageCause PHYSICAL =
            new com.hypixel.hytale.server.core.modules.entity.damage.DamageCause("Physical");
    private static final class FixtureStack extends ItemStack {
        private final Item asset;
        FixtureStack(String id, Item asset) {
            super();
            this.itemId = id;
            this.quantity = 1;
            this.asset = asset;
        }
        @Override public Item getItem() { return asset; }
    }
}
