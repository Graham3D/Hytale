package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonString;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SpatialBagAggregateTest {
    private static HytaleAssetStore<String, Item, DefaultAssetMap<String, Item>> fixture;
    @BeforeAll static void setup() throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        var rock = new Item("Rock_Stone");
        var maxStack = Item.class.getDeclaredField("maxStack");
        maxStack.setAccessible(true);
        maxStack.setInt(rock, 64);
        var builder = HytaleAssetStore.builder(Item.class,
                new DefaultAssetMap<String, Item>(Map.of(
                        "Rock_Stone", rock,
                        "Weapon_Shortbow_Iron", new Item("Weapon_Shortbow_Iron"))))
                .setPath("Item/Items").setCodec(Item.CODEC).setKeyFunction(Item::getId);
        fixture = new HytaleAssetStore<>(builder) {
            private final EventBus events = new EventBus(false);
            @Override protected EventBus getEventBus() { return events; }
        };
        AssetRegistry.register(fixture);
    }
    @AfterAll static void teardown() { if (fixture != null) AssetRegistry.unregister(fixture); }
    private final FootprintCatalog catalog = FootprintCatalog.loadDefault();

    @Test void legacyRightEdgeReflowsWithoutChangingItemOrReceiptIdentity() {
        UUID owner=UUID.randomUUID(), id=UUID.randomUUID();
        var saved=new SpatialBagAggregate(owner,catalog.revision())
                .offer(id,0,new ItemStack("Rock_Stone",7),catalog).bag().toBson();
        saved.getArray("Entries").get(0).asDocument().put("X",new BsonInt32(17));
        saved.getArray("Entries").get(0).asDocument().put("Y",new BsonInt32(3));
        var loaded=SpatialBagAggregate.fromBson(saved,catalog);
        assertEquals(7,loaded.entry(id).orElseThrow().payload().getQuantity());
        assertEquals(new SpatialLayout.Position(0,0),loaded.entry(id).orElseThrow().position());
        assertEquals(1,loaded.revision());
        assertEquals(id,loaded.receipts().iterator().next().operationId());
        assertEquals(loaded.toBson(),SpatialBagAggregate.fromBson(loaded.toBson(),catalog).toBson());
    }

    @Test void partialPlacementAndMergeConserveQuantity() {
        UUID source=UUID.randomUUID();
        var bag=new SpatialBagAggregate(UUID.randomUUID(),catalog.revision())
                .offer(source,0,new ItemStack("Rock_Stone",16),catalog).bag();
        UUID split=UUID.randomUUID();
        var placed=bag.placeQuantity(split,bag.revision(),source,1,2,0);
        assertTrue(placed.accepted());
        assertEquals(15,placed.bag().entry(source).orElseThrow().payload().getQuantity());
        assertEquals(1,placed.bag().entry(split).orElseThrow().payload().getQuantity());
        var merged=placed.bag().placeQuantity(UUID.randomUUID(),placed.bag().revision(),source,2,2,0);
        assertTrue(merged.accepted());
        assertEquals(13,merged.bag().entry(source).orElseThrow().payload().getQuantity());
        assertEquals(3,merged.bag().entry(split).orElseThrow().payload().getQuantity());
        assertEquals(16,merged.bag().entries().stream().mapToInt(e->e.payload().getQuantity()).sum());
        assertEquals(SpatialBagAggregate.Outcome.NO_FIT,
                merged.bag().placeQuantity(UUID.randomUUID(),merged.bag().revision(),source,1,0,0)
                        .receipt().outcome());
    }

    @Test void quantityWithdrawalKeepsTheRemainderAndReplaysOnce() {
        UUID source=UUID.randomUUID(), operation=UUID.randomUUID();
        var bag=new SpatialBagAggregate(UUID.randomUUID(),catalog.revision())
                .offer(source,0,new ItemStack("Rock_Stone",16),catalog).bag();
        String fingerprint=bag.entry(source).orElseThrow().payloadJson();
        var taken=bag.withdrawQuantity(operation,bag.revision(),source,fingerprint,3);
        assertTrue(taken.accepted());
        assertEquals(13,taken.bag().entry(source).orElseThrow().payload().getQuantity());
        assertEquals(taken.receipt(),taken.bag().withdrawQuantity(operation,bag.revision(),
                source,fingerprint,3).receipt());
        assertEquals(SpatialBagAggregate.Outcome.REPLAY_MISMATCH,
                taken.bag().withdrawQuantity(operation,bag.revision(),source,fingerprint,4)
                        .receipt().outcome());
    }

    @Test void consolidationConservesQuantitiesAndPreservesDistinctMetadata() {
        var bag = new SpatialBagAggregate(UUID.randomUUID(), catalog.revision());
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), third = UUID.randomUUID();
        bag = bag.offer(first, bag.revision(), new ItemStack("Rock_Stone", 40), catalog).bag();
        bag = bag.offer(second, bag.revision(), new ItemStack("Rock_Stone", 30), catalog).bag();
        bag = bag.offer(third, bag.revision(), new ItemStack("Rock_Stone", 7)
                .withMetadata("OwnerProof", new BsonString("other")), catalog).bag();
        var consolidated = bag.consolidate(UUID.randomUUID(), bag.revision());
        assertTrue(consolidated.accepted());
        assertEquals(Math.min(70, new ItemStack("Rock_Stone").getItem().getMaxStack()), consolidated.bag().entry(first).orElseThrow().payload().getQuantity());
        assertEquals(70 - Math.min(70, new ItemStack("Rock_Stone").getItem().getMaxStack()), consolidated.bag().entry(second).map(e -> e.payload().getQuantity()).orElse(0));
        assertEquals(7, consolidated.bag().entry(third).orElseThrow().payload().getQuantity());
        assertEquals(77, consolidated.bag().entries().stream().mapToInt(e -> e.payload().getQuantity()).sum());
        assertEquals(SpatialBagAggregate.Outcome.NO_FIT,
                consolidated.bag().consolidate(UUID.randomUUID(), consolidated.bag().revision()).receipt().outcome());
    }

    @Test void protectedPickupFillsExistingStackEvenWhenEveryCellIsOccupied() {
        var bag = new SpatialBagAggregate(UUID.randomUUID(), catalog.revision());
        UUID first = UUID.randomUUID();
        int maximum = new ItemStack("Rock_Stone", 1).getItem().getMaxStack();
        assertTrue(maximum > 4);
        bag = bag.offer(first, bag.revision(), new ItemStack("Rock_Stone", maximum - 4), catalog).bag();
        for (int i = 1; i < InventoryGridGeometry.CELLS; i++)
            bag = bag.offer(UUID.randomUUID(), bag.revision(), new ItemStack("Rock_Stone", maximum), catalog).bag();
        UUID operation = UUID.randomUUID();
        var accepted = bag.offerStacking(operation, bag.revision(), new ItemStack("Rock_Stone", 4), catalog);
        assertTrue(accepted.accepted());
        assertEquals(InventoryGridGeometry.CELLS, accepted.bag().entries().size());
        assertEquals(maximum, accepted.bag().entry(first).orElseThrow().payload().getQuantity());
        assertEquals(first, accepted.receipt().entryId());
        assertEquals(accepted.receipt(), accepted.bag().offerStacking(operation, bag.revision(),
                new ItemStack("Rock_Stone", 4), catalog).receipt());
        var rejected = accepted.bag().offerStacking(UUID.randomUUID(), accepted.bag().revision(),
                new ItemStack("Rock_Stone", 1), catalog);
        assertEquals(SpatialBagAggregate.Outcome.NO_FIT, rejected.receipt().outcome());
        assertEquals(accepted.bag().revision(), rejected.bag().revision());
    }

    @Test void protectedPickupNeverPartiallyFillsWhenRemainderCannotFit() {
        var bag = new SpatialBagAggregate(UUID.randomUUID(), catalog.revision());
        int maximum = new ItemStack("Rock_Stone", 1).getItem().getMaxStack();
        UUID first = UUID.randomUUID();
        bag = bag.offer(first, bag.revision(), new ItemStack("Rock_Stone", maximum - 2), catalog).bag();
        for (int i = 1; i < InventoryGridGeometry.CELLS; i++)
            bag = bag.offer(UUID.randomUUID(), bag.revision(), new ItemStack("Rock_Stone", maximum), catalog).bag();
        var rejected = bag.offerStacking(UUID.randomUUID(), bag.revision(), new ItemStack("Rock_Stone", 3), catalog);
        assertEquals(SpatialBagAggregate.Outcome.NO_FIT, rejected.receipt().outcome());
        assertEquals(maximum - 2, rejected.bag().entry(first).orElseThrow().payload().getQuantity());
        assertEquals(bag.revision(), rejected.bag().revision());
    }

    @Test void authenticPayloadAndEveryBowGrabCellSurviveOwnedMoves() {
        var owner = UUID.randomUUID();
        var bow = new ItemStack("Weapon_Shortbow_Iron", 1, 37, 80, 2, null)
                .withMetadata("OwnerProof", new BsonString("unique-bow"));
        var offered = new SpatialBagAggregate(owner, catalog.revision()).offer(UUID.randomUUID(), 0, bow, catalog);
        assertTrue(offered.accepted());
        UUID id = offered.receipt().entryId();
        for (int y = 0; y < 4; y++) for (int x = 0; x < 2; x++) {
            var moved = offered.bag().move(UUID.randomUUID(), 1, id, x, y, 8 + x, y);
            assertTrue(moved.accepted(), x + "," + y);
            assertEquals(new SpatialLayout.Position(8, 0), moved.bag().entry(id).orElseThrow().position());
            var saved = moved.bag().entry(id).orElseThrow().payload();
            assertEquals(bow.getItemId(), saved.getItemId());
            assertEquals(bow.getQuantity(), saved.getQuantity());
            assertEquals(bow.getDurability(), saved.getDurability());
            assertEquals(bow.getMaxDurability(), saved.getMaxDurability());
            assertEquals(bow.getQualityIndex(), saved.getQualityIndex());
            assertEquals(bow.getMetadata(), saved.getMetadata());
        }
    }

    @Test void noRectangleDoesNotAdmitOrChangeAnyExistingPayload() {
        var bag = new SpatialBagAggregate(UUID.randomUUID(), catalog.revision());
        for (int i = 0; i < 68; i++) {
            var result = bag.offer(UUID.randomUUID(), bag.revision(), new ItemStack("Rock_Stone"), catalog);
            assertTrue(result.accepted()); bag = result.bag();
        }
        // Four scattered empty cells remain; none is a 2x4 bow rectangle.
        var rejected = bag.offer(UUID.randomUUID(), bag.revision(), new ItemStack("Weapon_Shortbow_Iron"), catalog);
        assertEquals(SpatialBagAggregate.Outcome.NO_FIT, rejected.receipt().outcome());
        assertEquals(68, rejected.bag().entries().size());
        assertEquals(bag.revision(), rejected.bag().revision());
    }

    @Test void replayStaleAndSplitConserveExactQuantity() {
        var start = new SpatialBagAggregate(UUID.randomUUID(), catalog.revision());
        UUID offerId = UUID.randomUUID();
        var offer = start.offer(offerId, 0, new ItemStack("Rock_Stone", 9), catalog);
        assertTrue(offer.accepted());
        var replay = offer.bag().offer(offerId, 0, new ItemStack("Rock_Stone", 9), catalog);
        assertEquals(offer.receipt(), replay.receipt());
        assertSame(offer.bag(), replay.bag());
        assertEquals(SpatialBagAggregate.Outcome.REPLAY_MISMATCH,
                offer.bag().offer(offerId, 0, new ItemStack("Rock_Stone", 8), catalog).receipt().outcome());
        UUID splitId = UUID.randomUUID();
        var split = offer.bag().split(splitId, 1, offerId, 4);
        assertTrue(split.accepted());
        assertEquals(5, split.bag().entry(offerId).orElseThrow().payload().getQuantity());
        assertEquals(4, split.bag().entry(splitId).orElseThrow().payload().getQuantity());
        assertEquals(2, split.bag().revision());
        var merged = split.bag().merge(UUID.randomUUID(), 2, splitId, offerId);
        assertTrue(merged.accepted());
        assertEquals(9, merged.bag().entry(offerId).orElseThrow().payload().getQuantity());
        assertTrue(merged.bag().entry(splitId).isEmpty());
        assertEquals(SpatialBagAggregate.Outcome.STALE,
                split.bag().split(UUID.randomUUID(), 1, offerId, 1).receipt().outcome());
        var component = new SpatialBagComponent(start.owner(), catalog);
        component.publish(start.owner(), component.state(start.owner()), offer.bag());
        component.publish(start.owner(), component.state(start.owner()), split.bag());
        component.publish(start.owner(), component.state(start.owner()), merged.bag());
        var encoded = SpatialBagComponent.CODEC.encode(component, new com.hypixel.hytale.codec.ExtraInfo());
        var restored = SpatialBagComponent.CODEC.decode(encoded, new com.hypixel.hytale.codec.ExtraInfo());
        assertEquals(component.snapshot(), restored.snapshot());
        assertEquals(SpatialBagComponent.OwnershipMode.NATIVE, restored.mode(start.owner()));
        assertThrows(IllegalStateException.class,
                () -> restored.activateSpatialAfterMigration(start.owner(), restored.state(start.owner())));
        assertEquals(split.receipt(), restored.state(start.owner()).split(splitId, 1, offerId, 4).receipt());
    }

    @Test void staleCandidateCannotOverwriteAConcurrentReceiptOrPlacement() {
        var owner = UUID.randomUUID();
        var component = new SpatialBagComponent(owner, catalog);
        var before = component.state(owner);
        var accepted = before.offer(UUID.randomUUID(), before.revision(), new ItemStack("Rock_Stone"), catalog);
        component.publish(owner, before, accepted.bag());
        var stale = before.offer(UUID.randomUUID(), before.revision(), new ItemStack("Rock_Stone"), catalog);
        assertThrows(IllegalStateException.class, () -> component.publish(owner, before, stale.bag()));
        assertSame(accepted.bag(), component.state(owner));

        var current = component.state(owner);
        var rejected = current.offer(UUID.randomUUID(), current.revision(), new ItemStack("Unmapped_Test_Item"), catalog);
        assertEquals(SpatialBagAggregate.Outcome.UNMAPPED, rejected.receipt().outcome());
        component.publish(owner, current, rejected.bag());
        assertEquals(current.revision(), component.state(owner).revision());
        assertEquals(1, component.state(owner).entries().size());
        assertEquals(2, component.state(owner).receipts().size());
    }

    @Test void playerSaveReloadAcceptsNormalized32BitRevisions() {
        var initial = new SpatialBagAggregate(UUID.randomUUID(), catalog.revision());
        var offered = initial.offer(UUID.randomUUID(), 0, new ItemStack("Rock_Stone"), catalog);
        assertTrue(offered.accepted());
        var saved = BsonDocument.parse(offered.bag().toBson().toJson());
        assertInstanceOf(BsonInt32.class, saved.get("Revision"));
        assertInstanceOf(BsonInt32.class, saved.getArray("Receipts").get(0).asDocument().get("Revision"));
        var restored = SpatialBagAggregate.fromBson(saved, catalog);
        assertEquals(offered.bag().revision(), restored.revision());
        assertEquals(offered.bag().entries().size(), restored.entries().size());
        assertEquals(offered.receipt(), restored.receipts().iterator().next());
        var component = new SpatialBagComponent(initial.owner(), catalog);
        component.activateQaProof(initial.owner());
        component.publish(initial.owner(), component.state(initial.owner()), offered.bag());
        var encoded = SpatialBagComponent.CODEC.encode(component, new com.hypixel.hytale.codec.ExtraInfo());
        var reloaded = SpatialBagComponent.CODEC.decode(BsonDocument.parse(encoded.toJson()),
                new com.hypixel.hytale.codec.ExtraInfo());
        assertEquals(component.snapshot(), reloaded.snapshot());
        assertEquals(SpatialBagComponent.OwnershipMode.QA_PROOF, reloaded.mode(initial.owner()));
    }

    @Test void copiedSaveProofModeIsExplicitAndCannotBePromotedToRelease() {
        UUID owner = UUID.randomUUID();
        var component = new SpatialBagComponent(owner, catalog);
        component.activateQaProof(owner);
        assertThrows(IllegalStateException.class, () -> component.activateQaProof(owner));
        var encoded = SpatialBagComponent.CODEC.encode(component, new com.hypixel.hytale.codec.ExtraInfo());
        var restored = SpatialBagComponent.CODEC.decode(encoded, new com.hypixel.hytale.codec.ExtraInfo());
        assertEquals(SpatialBagComponent.OwnershipMode.QA_PROOF, restored.mode(owner));
        assertThrows(IllegalStateException.class,
                () -> restored.activateSpatialAfterMigration(owner, restored.state(owner)));
    }

    @Test void batchPreflightSortAndExactWithdrawalAreAllOrNothing() {
        var owner = UUID.randomUUID();
        var empty = new SpatialBagAggregate(owner, catalog.revision());
        var bow = new ItemStack("Weapon_Shortbow_Iron", 1, 37, 80, 2, null)
                .withMetadata("OwnerProof", new BsonString("migration-bow"));
        UUID bowId = UUID.randomUUID(), rockId = UUID.randomUUID();
        var batch = empty.offerAll(UUID.randomUUID(), 0, List.of(
                new SpatialBagAggregate.OfferedItem(bowId, bow),
                new SpatialBagAggregate.OfferedItem(rockId, new ItemStack("Rock_Stone", 3))), catalog);
        assertTrue(batch.accepted());
        assertEquals(1, batch.bag().revision());
        assertEquals(2, batch.bag().entries().size());
        var beforeSort = batch.bag().entry(bowId).orElseThrow().payloadJson();
        var sorted = batch.bag().sort(UUID.randomUUID(), 1);
        assertTrue(sorted.accepted());
        assertEquals(beforeSort, sorted.bag().entry(bowId).orElseThrow().payloadJson());
        var wrong = sorted.bag().withdraw(UUID.randomUUID(), 2, bowId, "wrong fingerprint");
        assertEquals(SpatialBagAggregate.Outcome.MISSING, wrong.receipt().outcome());
        assertEquals(2, wrong.bag().entries().size());
        var withdrawn = sorted.bag().withdraw(UUID.randomUUID(), 2, bowId, beforeSort);
        assertTrue(withdrawn.accepted());
        assertEquals(1, withdrawn.bag().entries().size());
        assertEquals(3, withdrawn.bag().revision());

        var oversized = new java.util.ArrayList<SpatialBagAggregate.OfferedItem>();
        for (int i = 0; i < InventoryGridGeometry.CELLS + 1; i++) oversized.add(new SpatialBagAggregate.OfferedItem(
                UUID.nameUUIDFromBytes(("bulk-" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                new ItemStack("Rock_Stone")));
        var rejected = empty.offerAll(UUID.randomUUID(), 0, oversized, catalog);
        assertEquals(SpatialBagAggregate.Outcome.NO_FIT, rejected.receipt().outcome());
        assertTrue(rejected.bag().entries().isEmpty());
        assertEquals(0, rejected.bag().revision());
    }
    @Test void equipmentExchangeIsOneRevisionAndNeverDropsDisplacedPayload() {
        var empty=new SpatialBagAggregate(UUID.randomUUID(),catalog.revision());
        UUID source=UUID.randomUUID(),operation=UUID.randomUUID(),displaced=UUID.randomUUID();
        var original=new ItemStack("Rock_Stone").withMetadata("OwnerProof",new BsonString("bag-source"));
        var existing=empty.offer(source,0,original,catalog).bag();
        var displacedStack=new ItemStack("Weapon_Shortbow_Iron",1,41,80,2,null)
                .withMetadata("OwnerProof",new BsonString("native-displaced"));
        var result=existing.exchange(operation,1,source,existing.entry(source).orElseThrow().payloadJson(),
                List.of(new SpatialBagAggregate.OfferedItem(displaced,displacedStack)),catalog);
        assertTrue(result.accepted());
        assertEquals(2,result.bag().revision());
        assertTrue(result.bag().entry(source).isEmpty());
        assertEquals(displacedStack.getMetadata(),result.bag().entry(displaced).orElseThrow().payload().getMetadata());
        assertEquals(result.receipt(),result.bag().exchange(operation,1,source,
                existing.entry(source).orElseThrow().payloadJson(),
                List.of(new SpatialBagAggregate.OfferedItem(displaced,displacedStack)),catalog).receipt());
        var full=empty;
        for(int i=0;i<86;i++)full=full.offer(UUID.randomUUID(),full.revision(),new ItemStack("Rock_Stone"),catalog).bag();
        var first=full.entries().iterator().next();
        var noFit=full.exchange(UUID.randomUUID(),full.revision(),first.id(),first.payloadJson(),
                List.of(new SpatialBagAggregate.OfferedItem(UUID.randomUUID(),displacedStack)),catalog);
        assertEquals(SpatialBagAggregate.Outcome.NO_FIT,noFit.receipt().outcome());
        assertEquals(86,noFit.bag().entries().size());
        assertEquals(full.revision(),noFit.bag().revision());
    }
    @Test void copiedMigrationRoundTripPreservesExactPayloadAndMode() {
        UUID owner=UUID.randomUUID(),entry=UUID.randomUUID(),importOperation=UUID.randomUUID();
        var component=new SpatialBagComponent(owner,catalog);
        component.activateQaProof(owner);
        var before=component.state(owner);
        var original=new ItemStack("Weapon_Shortbow_Iron",1,37,80,2,null)
                .withMetadata("OwnerProof",new BsonString("reversible-slot"));
        var imported=before.offerAll(importOperation,0,List.of(
                new SpatialBagAggregate.OfferedItem(entry,original)),catalog);
        assertTrue(imported.accepted());
        component.publishCopiedMigration(owner,before,imported.bag(),SpatialBagComponent.OwnershipMode.MIGRATION_PROOF);
        var encoded=SpatialBagComponent.CODEC.encode(component,new com.hypixel.hytale.codec.ExtraInfo());
        var loaded=SpatialBagComponent.CODEC.decode(encoded,new com.hypixel.hytale.codec.ExtraInfo());
        assertEquals(SpatialBagComponent.OwnershipMode.MIGRATION_PROOF,loaded.mode(owner));
        var restored=loaded.state(owner).clearForExport(UUID.randomUUID(),1,Map.of(entry,
                loaded.state(owner).entry(entry).orElseThrow().payloadJson()));
        assertTrue(restored.accepted());
        assertEquals(original.getMetadata(),loaded.state(owner).entry(entry).orElseThrow().payload().getMetadata());
        loaded.publishCopiedMigration(owner,loaded.state(owner),restored.bag(),SpatialBagComponent.OwnershipMode.QA_PROOF);
        assertTrue(loaded.state(owner).entries().isEmpty());
        assertEquals(SpatialBagComponent.OwnershipMode.QA_PROOF,loaded.mode(owner));
        assertEquals(SpatialBagAggregate.Outcome.MISSING,
                imported.bag().clearForExport(UUID.randomUUID(),1,Map.of()).receipt().outcome());
    }
    @Test void copiedMigrationRetainsEarlierQaBagItemsForReverseExport() {
        UUID owner=UUID.randomUUID(),nativeEntry=UUID.randomUUID();
        var component=new SpatialBagComponent(owner,catalog);
        component.activateQaProof(owner);
        var empty=component.state(owner);
        var qa=empty.offer(UUID.randomUUID(),0,new ItemStack("Rock_Stone"),catalog);
        component.publish(owner,empty,qa.bag());
        var imported=qa.bag().offerAll(UUID.randomUUID(),1,List.of(
                new SpatialBagAggregate.OfferedItem(nativeEntry,new ItemStack("Weapon_Shortbow_Iron"))),catalog);
        assertTrue(imported.accepted());
        component.publishCopiedMigration(owner,qa.bag(),imported.bag(),SpatialBagComponent.OwnershipMode.MIGRATION_PROOF);
        assertEquals(2,component.state(owner).entries().size());
        var all=new java.util.HashMap<UUID,String>();
        component.state(owner).entries().forEach(entry->all.put(entry.id(),entry.payloadJson()));
        var exported=component.state(owner).clearForExport(UUID.randomUUID(),2,all);
        assertTrue(exported.accepted());
        component.publishCopiedMigration(owner,component.state(owner),exported.bag(),SpatialBagComponent.OwnershipMode.QA_PROOF);
        assertTrue(component.state(owner).entries().isEmpty());
    }
}
