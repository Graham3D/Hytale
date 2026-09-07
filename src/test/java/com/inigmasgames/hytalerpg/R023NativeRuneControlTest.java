package com.inigmasgames.hytalerpg;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.codec.ExtraInfo;
import com.inigmasgames.hytalerpg.input.*;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceRecord;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.event.EventBus;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class R023NativeRuneControlTest {
    @TempDir Path temp;
    private static HytaleAssetStore<String, Item, DefaultAssetMap<String, Item>> store;

    @BeforeAll static void nativeInventoryFixture() throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        // Minimal item registry for native ItemStack codecs/container tests; NOT shipped-asset/casting proof.
        Map<String, Item> items = new HashMap<>();
        for (String id : List.of("RPG_Ability_Quick_Slash", "RPG_Ability_Fire_Bolt", "Rune_Fireball", "Rune_WindStrike"))
            items.put(id, new Item(id));
        var builder = HytaleAssetStore.builder(Item.class, new DefaultAssetMap<String, Item>(items))
                .setPath("Item/Items").setCodec(Item.CODEC).setKeyFunction(Item::getId);
        store = new HytaleAssetStore<>(builder) {
            private final EventBus events = new EventBus(false);
            @Override protected EventBus getEventBus() { return events; }
        };
        AssetRegistry.register(store);
    }

    @AfterAll static void releaseFixture() { if (store != null) AssetRegistry.unregister(store); }

    private SyncInteractionChains packet(InteractionType type, boolean initial, int id) {
        var chain = new SyncInteractionChain();
        chain.interactionType = type; chain.initial = initial; chain.chainId = id;
        var packet = new SyncInteractionChains();
        packet.updates = new SyncInteractionChain[]{chain};
        return packet;
    }

    @Test void rawObservationPrecedesInitialFilterAndRetainsNonAbilityTraffic() {
        var adapter = new HytaleAbilitySkillInputAdapter();
        var raw = new AtomicInteger();
        adapter.configureControl(p -> false, (p, packet) -> raw.incrementAndGet());
        UUID player = UUID.randomUUID();
        adapter.observe(player, packet(InteractionType.Ability2, false, 1));
        adapter.observe(player, packet(InteractionType.Primary, true, 2));
        assertEquals(2, raw.get());
        assertEquals(0, adapter.drainFor(player, ignored -> fail("Not a mapped initial ability"), 10));
    }

    @Test void nativeControlStillObservesBothSlotsButCannotExecuteRpg() {
        var observations = new ArrayList<HytaleAbilitySkillInputAdapter.Observation>();
        var adapter = new HytaleAbilitySkillInputAdapter(observations::add);
        UUID player = UUID.randomUUID();
        adapter.configureControl(p -> true, (p, packet) -> { });
        adapter.observe(player, packet(InteractionType.Ability2, true, 1));
        adapter.observe(player, packet(InteractionType.Ability3, true, 2));
        assertEquals(2, observations.size());
        assertTrue(observations.stream().allMatch(o -> o.result().equals("NATIVE_CONTROL_RPG_SUPPRESSED")));
        assertEquals(0, adapter.drainFor(player, ignored -> fail("Vanilla cast must not double-execute RPG"), 10));
    }

    @Test void preControlQueuedRequestsAreBlockedAtDrainAndOtherPlayersAreUnaffected() {
        var adapter = new HytaleAbilitySkillInputAdapter();
        var suppressed = new AtomicBoolean(false);
        UUID trial = UUID.randomUUID(), other = UUID.randomUUID();
        adapter.configureControl(p -> p.equals(trial) && suppressed.get(), (p, packet) -> { });
        adapter.observe(trial, packet(InteractionType.Ability2, true, 1));
        adapter.observe(other, packet(InteractionType.Ability3, true, 2));
        suppressed.set(true);
        assertEquals(0, adapter.drainFor(trial, ignored -> fail("Queued request escaped control"), 10));
        assertEquals(1, adapter.drainFor(other, r -> assertEquals(other, r.player()), 10));
    }

    @Test void normalDeduplicationAndAbility4PolicyAreUnchanged() {
        var adapter = new HytaleAbilitySkillInputAdapter();
        UUID player = UUID.randomUUID();
        adapter.observe(player, packet(InteractionType.Ability2, true, 1));
        adapter.observe(player, packet(InteractionType.Ability2, true, 1));
        adapter.observe(player, packet(InteractionType.Ability4, true, 2));
        assertEquals(1, adapter.drainFor(player, r -> assertEquals("Ability2", r.action()), 10));
    }

    private Path journal(UUID player, ItemStack first, ItemStack second) throws Exception {
        var doc = new BsonDocument("trial", new BsonString("interrupted-test"));
        doc.put("0", ItemStack.CODEC.encode(first, new ExtraInfo()));
        doc.put("3", ItemStack.CODEC.encode(second, new ExtraInfo()));
        Path file = temp.resolve(player + ".json");
        Files.writeString(file, doc.toJson());
        return file;
    }

    @Test void interruptedControlRestoresExactSnapshotsAndRetainsQuietPeriod() throws Exception {
        UUID player = UUID.randomUUID();
        var first = new ItemStack("RPG_Ability_Quick_Slash");
        var second = new ItemStack("RPG_Ability_Fire_Bolt");
        Path file = journal(player, first, second);
        var container = new SimpleItemContainer((short) 6);
        container.setItemStackForSlot((short) 0, new ItemStack(NativeRuneControl.RUNE));
        container.setItemStackForSlot((short) 3, new ItemStack(NativeRuneControl.RUNE));
        var control = new NativeRuneControl(temp, true, ignored -> { });
        assertTrue(control.inputSuppressed(player));
        assertFalse(control.pauseProjection(player, container));
        assertEquals(first, container.getItemStack((short) 0));
        assertEquals(second, container.getItemStack((short) 3));
        assertFalse(Files.exists(file));
        assertTrue(control.inputSuppressed(player));
    }

    @Test void foreignMutationBlocksRecoveryWithoutOverwritingAndCanBeRetried() throws Exception {
        UUID player = UUID.randomUUID();
        var first = new ItemStack("RPG_Ability_Quick_Slash");
        var second = new ItemStack("RPG_Ability_Fire_Bolt");
        Path file = journal(player, first, second);
        var container = new SimpleItemContainer((short) 6);
        var foreign = new ItemStack("Rune_WindStrike");
        container.setItemStackForSlot((short) 0, first);
        container.setItemStackForSlot((short) 3, foreign);
        var control = new NativeRuneControl(temp, true, ignored -> { });
        assertTrue(control.pauseProjection(player, container));
        assertEquals(foreign, container.getItemStack((short) 3));
        assertTrue(Files.exists(file));
        assertTrue(control.inputSuppressed(player));
        container.setItemStackForSlot((short) 3, new ItemStack(NativeRuneControl.RUNE));
        assertTrue(control.stop(player, container, "RETRY").contains("restored"));
        assertEquals(second, container.getItemStack((short) 3));
    }

    @Test void disabledControlDoesNotMutateSlots() {
        var control = new NativeRuneControl(temp, false, ignored -> { });
        var container = new SimpleItemContainer((short) 6);
        assertTrue(control.start(UUID.randomUUID(), container).contains("disabled"));
        assertTrue(container.isEmpty());
    }
}
