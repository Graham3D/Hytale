package com.inigmasgames.persistentnpcs;

import com.google.gson.JsonObject;
import com.hypixel.hytale.protocol.ItemArmorSlot;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemArmor;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.ResistanceModifier;
import com.hypixel.hytale.server.core.modules.entity.damage.ResistanceModifier.ResistanceCalculationType;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.profile.NpcConfiguredVitals;
import com.inigmasgames.persistentnpcs.profile.NpcInventoryState;
import com.inigmasgames.persistentnpcs.profile.NpcStatsSnapshotService;
import com.inigmasgames.persistentnpcs.ui.CustomInventoryBridgeUi;
import com.inigmasgames.persistentnpcs.ui.ProfileInventoryPaging;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipFile;
import org.bson.BsonDocument;

/** Executable native-container transactions and native armor aggregation; no live-world writes. */
public final class R148NpcProfilePagingResistanceTest {
    private static final DamageCause PHYSICAL = new DamageCause("Physical");
    private static final DamageCause PROJECTILE = new DamageCause("Projectile");
    public static void main(String[] args) throws Exception {
        try { run(); } catch (Throwable failure) { failure.printStackTrace(System.out); throw failure; }
    }
    private static void run() throws Exception {
        pagingAndTransfers();
        typedArmor();
        configuredVitals();
        String page = Files.readString(Path.of("src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        String stats = Files.readString(Path.of("src/main/java/com/inigmasgames/persistentnpcs/profile/NpcStatsSnapshotService.java"));
        assert page.contains("ProfileInventoryPaging.requireRevision(data.inventoryViewRevision, inventoryViewRevision)");
        assert page.contains("targetSlot = npcPaging().targetSlot(targetSlot)");
        assert page.contains("targetSlot = playerPaging().targetSlot(targetSlot)");
        assert page.contains("int sourceSlot = value(data.sourceSlotId, -1)");
        assert page.contains("statsService.capture(store,") && stats.contains("authority.npcRef(), EntityStatMap.getComponentType()");
        assert stats.contains("stat(stats, \"Health\")") && stats.contains("value.get(), value.getMin(), value.getMax()");
        assert !page.contains(" + \" base\"");
        System.out.println("R148 paging/native moves, persistence, typed Trork resistance and configured-vitals gates passed.");
    }

    private static void pagingAndTransfers() throws Exception {
        var npc = new SimpleItemContainer((short) 40);
        var player = new SimpleItemContainer((short) 36);
        var second = new ProfileInventoryPaging(40, 1);
        assert second.firstSlot() == 28 && second.slotCount() == 12 && second.targetSlot(11) == 39;
        assert second.shifted(1) == 1 && second.shifted(-1) == 0;
        var seen = new java.util.HashSet<Integer>();
        for (int page = 0; page < 2; page++) {
            var paging = new ProfileInventoryPaging(40, page);
            var commands = new UICommandBuilder();
            CustomInventoryBridgeUi.setNativeSlots(commands, "#NpcInventoryGrid.Slots", npc,
                    paging.firstSlot(), paging.slotCount());
            var encoded = BsonDocument.parse(commands.getCommands()[0].data).getArray("0");
            assert encoded.size() == paging.slotCount();
            for (int visual = 0; visual < encoded.size(); visual++) {
                int index = encoded.get(visual).asDocument().getInt32("InventorySlotIndex").getValue();
                assert index == paging.targetSlot(visual) && seen.add(index);
            }
        }
        assert seen.size() == 40 && npc.getCapacity() == 40;
        try { second.targetSlot(12); throw new AssertionError("Out-of-page move accepted"); }
        catch (IllegalArgumentException expected) { }
        ProfileInventoryPaging.requireRevision("5", 5);
        for (String stale : new String[] { null, "4", "6" }) {
            try { ProfileInventoryPaging.requireRevision(stale, 5); throw new AssertionError("Stale page accepted"); }
            catch (IllegalStateException expected) { }
        }
        Item item = new Item("Test_R148_Sand") { @Override public int getMaxStack() { return 100; } };
        npc.setItemStackForSlot((short) second.targetSlot(11), new Stack(item, 17));
        // Use the same native transaction primitive as the production bridge, never manual transfer.
        assert npc.moveItemStackFromSlotToSlot((short) 39, 17, player, (short) 35).succeeded();
        assert ItemStack.isEmpty(npc.getItemStack((short) 39)) && player.getItemStack((short) 35).getQuantity() == 17;
        assert player.moveItemStackFromSlotToSlot((short) 35, 17, npc, (short) second.targetSlot(0)).succeeded();
        assert npc.getItemStack((short) 28).getQuantity() == 17 && player.isEmpty();
        assert npc.moveItemStackFromSlotToSlot((short) 28, 17, npc, (short) second.targetSlot(11)).succeeded();
        assert npc.getItemStack((short) 39).getQuantity() == 17 && ItemStack.isEmpty(npc.getItemStack((short) 28));
        Path saved = Files.createTempFile("r148-page-two-", ".json");
        try {
            var state = new NpcInventoryState(2, UUID.randomUUID(), List.of(), List.of(),
                    List.of(new NpcInventoryState.PersistedItemStack((short) 39, item.getId(), 17, 0, 0, 0, null, false)), false);
            JsonFiles.writeAtomic(saved, state);
            var restarted = JsonFiles.read(saved, NpcInventoryState.class);
            assert restarted.equals(state) && restarted.inventory().getFirst().slot() == 39;
            var reopened = new SimpleItemContainer(NpcInventoryState.INVENTORY_CAPACITY);
            for (var entry : restarted.inventory()) reopened.setItemStackForSlot(entry.slot(), new Stack(item, entry.quantity()));
            assert reopened.getCapacity() == 40 && reopened.getItemStack((short) second.targetSlot(11)).getQuantity() == 17;
            var commands = new UICommandBuilder();
            CustomInventoryBridgeUi.setNativeSlots(commands, "#NpcInventoryGrid.Slots", reopened, 28, 12);
            assert BsonDocument.parse(commands.getCommands()[0].data).getArray("0").get(11).asDocument()
                    .getInt32("InventorySlotIndex").getValue() == 39;
        } finally { Files.deleteIfExists(saved); }
    }

    private static void typedArmor() throws Exception {
        var armor = new SimpleItemContainer((short) 4);
        float[] percents = { .05f, .09f, .04f, .07f };
        String[] slots = { "Head", "Chest", "Hands", "Legs" };
        // Verify fixtures against the installed release asset JSON, not remembered game values.
        Path assets = Path.of(System.getenv("APPDATA"), "Hytale/install/release/package/game/latest/Assets.zip");
        try (ZipFile zip = new ZipFile(assets.toFile())) {
            for (int i = 0; i < 4; i++) {
                var entry = zip.getEntry("Server/Item/Items/Armor/Trork_Warrior/Armor_Trork_" + slots[i] + ".json");
                try (var reader = new java.io.InputStreamReader(zip.getInputStream(entry), java.nio.charset.StandardCharsets.UTF_8)) {
                    var asset = JsonFiles.GSON.fromJson(reader, JsonObject.class).getAsJsonObject("Armor");
                    assert asset.get("BaseDamageResistance").getAsDouble() == 0;
                    for (String type : new String[] { "Physical", "Projectile" }) {
                        var modifier = asset.getAsJsonObject("DamageResistance").getAsJsonArray(type).get(0).getAsJsonObject();
                        assert modifier.get("CalculationType").getAsString().equals("Percent");
                        assert Math.abs(modifier.get("Amount").getAsFloat() - percents[i]) < .00001;
                    }
                }
                armor.setItemStackForSlot((short) i, armorStack(i, 0, percents[i]));
            }
        }
        var service = new NpcStatsSnapshotService();
        var snapshot = service.captureEquipmentOnly(UUID.randomUUID(), armor, UUID.randomUUID(), 1, 1);
        assert snapshot.health().isEmpty() && snapshot.npcEntityUuid() == null;
        var defense = snapshot.defense().orElseThrow();
        assert defense.types().size() == 2 && defense.summary().equals("25% Physical") : defense;
        assert Math.abs(defense.types().get("Projectile").percent() - .25) < .00001;
        assert defense.details().contains("Projectile") && defense.details().contains("Types are not summed");
        armor.setItemStackForSlot((short) 0, null);
        assert NpcStatsSnapshotService.armorProtection(armor).summary().equals("20% Physical");
        armor.setItemStackForSlot((short) 0, armorStack(0, 3, .05f));
        var withBase = NpcStatsSnapshotService.armorProtection(armor);
        assert withBase.types().get("Physical").flat() == 3 && withBase.types().get("Projectile").flat() == 3;
    }
    private static Stack armorStack(int slot, double base, float percent) {
        var modifier = new ResistanceModifier(ResistanceCalculationType.PERCENT, percent);
        var fixtureArmor = new ItemArmor(ItemArmorSlot.VALUES[slot], base, null, null) {
            @Override public Map<DamageCause, ResistanceModifier[]> getDamageResistanceValues() {
                return Map.of(PHYSICAL, new ResistanceModifier[] {modifier}, PROJECTILE, new ResistanceModifier[] {modifier});
            }
        };
        var item = new Item("Test_R148_Armor_" + slot) { @Override public ItemArmor getArmor() { return fixtureArmor; } };
        return new Stack(item, 1);
    }
    private static void configuredVitals() throws Exception {
        Path file = Files.createTempFile("r148-hoit-role-", ".json");
        try {
            Files.writeString(file, "{\"MaxHealth\":100,\"Invulnerable\":true}");
            byte[] before = Files.readAllBytes(file);
            var config = NpcConfiguredVitals.read(file);
            assert config.text("Health").equals("MAX 100");
            assert config.text("Stamina").equals("—") && config.text("Mana").equals("—");
            assert config.invulnerable().orElseThrow() && config.tooltip().contains("current vitals unavailable");
            assert !config.text("Health").contains("/");
            assert NpcConfiguredVitals.read(file).equals(config);
            assert java.util.Arrays.equals(before, Files.readAllBytes(file));
            Files.writeString(file, "{\"MaxHealth\":100,\"MaxStamina\":12,\"MaxMana\":0}");
            assert NpcConfiguredVitals.read(file).text("Stamina").equals("MAX 12");
            assert NpcConfiguredVitals.read(file).text("Mana").equals("MAX 0");
        } finally { Files.deleteIfExists(file); }
        assert NpcConfiguredVitals.read(file).equals(NpcConfiguredVitals.EMPTY);
    }
    private static final class Stack extends ItemStack {
        private final Item asset;
        Stack(Item asset, int quantity) { super(); this.asset = asset; this.itemId = asset.getId(); this.quantity = quantity; }
        @Override public Item getItem() { return asset; }
        @Override public ItemStack withQuantity(int value) { return value == 0 ? null : new Stack(asset, value); }
    }
}
