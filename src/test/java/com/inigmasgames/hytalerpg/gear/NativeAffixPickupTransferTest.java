package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.inigmasgames.hytalerpg.progress.FileEncounterStore;
import com.inigmasgames.hytalerpg.ui.inventory.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Offline composition of the production material admission, bag and durable receipt owners. */
class NativeAffixPickupTransferTest {
    @TempDir Path directory;
    private static HytaleAssetStore<String,Item,DefaultAssetMap<String,Item>> assets;
    @BeforeAll static void setup() throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        var rock=new Item("Rock_Stone");
        var max=Item.class.getDeclaredField("maxStack");max.setAccessible(true);max.setInt(rock,64);
        var builder=HytaleAssetStore.builder(Item.class,new DefaultAssetMap<String,Item>(Map.of("Rock_Stone",rock)))
                .setPath("Item/Items").setCodec(Item.CODEC).setKeyFunction(Item::getId);
        assets=new HytaleAssetStore<>(builder) {
            private final EventBus events=new EventBus(false);
            @Override protected EventBus getEventBus(){return events;}
        };
        AssetRegistry.register(assets);
    }
    @AfterAll static void close(){AssetRegistry.unregister(assets);}

    @Test void wa156ExtendedAdmissionCommitsExactlyOneProtectedStockTransferAndPreservesNoFit() {
        var item=MasterAffixTestEquipment.fixture("WA-156",false);
        var accepted=GearEquipmentResolution.resolve(99,MasterAffixTestEquipment.BASELINE,
                List.of(new GearEquipmentResolution.Candidate(item,true,true,true)));
        assertEquals(List.of(item),accepted.validItems());
        var effects=accepted.effects().snapshot();
        double bonus=effects.value("WA-156");assertTrue(bonus>0);
        var material=new NativeAffixMaterialPickup.ItemKind("Rock_Stone",64,false,false,true);
        var candidate=new NativeAffixMaterialPickup.Candidate(material,2.5+bonus/2,true,true,true,true);
        assertTrue(NativeAffixMaterialPickup.admit(effects,2.5,candidate));
        assertFalse(NativeAffixMaterialPickup.admit(GearEffectSnapshot.EMPTY,2.5,candidate));
        assertFalse(NativeAffixMaterialPickup.admit(effects,2.5,
                new NativeAffixMaterialPickup.Candidate(material,candidate.distanceMetres(),true,true,false,true)));
        assertFalse(NativeAffixMaterialPickup.admit(effects,2.5,
                new NativeAffixMaterialPickup.Candidate(material,candidate.distanceMetres(),true,true,true,false)));
        var footprints=FootprintCatalog.loadDefault();var owner=UUID.randomUUID();
        var bag=new SpatialBagAggregate(owner,footprints.revision());
        var payload=new ItemStack("Rock_Stone",7);var operation=UUID.randomUUID();var source=UUID.randomUUID();
        try(var store=new FileEncounterStore(directory)) {
            var loot=new GearLootService(store,new GearDropGenerator(GearCatalog.load(),new GearBindings(),GearAffixRuntime.ENABLED));
            var planned=bag.offerStacking(operation,bag.revision(),payload,footprints);
            assertTrue(planned.accepted());
            var receipt=new GearLootService.SpatialStockReceipt(source,operation,owner,UUID.randomUUID(),
                    ItemStack.CODEC.encode(payload,new ExtraInfo()).asDocument().toJson(),bag.revision(),"PREPARED");
            loot.prepareSpatialStock(receipt);
            var saved=SpatialBagAggregate.fromBson(planned.bag().toBson(),footprints);
            assertEquals(7,saved.entries().stream().mapToInt(e->e.payload().getQuantity()).sum());
            assertEquals("FINALIZED",loot.finishSpatialStock(source,operation,"FINALIZED").stage());
            assertThrows(IllegalStateException.class,()->loot.prepareSpatialStock(receipt));
            assertEquals(1,loot.spatialStockReceipts().size());
            var replay=saved.offerStacking(operation,bag.revision(),payload,footprints);
            assertEquals(saved.entries(),replay.bag().entries());
            assertEquals(saved.revision(),replay.bag().revision());
            var full=new SpatialBagAggregate(owner,footprints.revision());
            for(int cell=0;cell<1000;cell++) {
                var fill=full.offerStacking(UUID.randomUUID(),full.revision(),new ItemStack("Rock_Stone",64),footprints);
                if(!fill.accepted())break;
                full=fill.bag();
            }
            var denied=full.offerStacking(UUID.randomUUID(),full.revision(),payload,footprints);
            assertFalse(denied.accepted());
            assertEquals(SpatialBagAggregate.Outcome.NO_FIT,denied.receipt().outcome());
            assertEquals(full.entries(),denied.bag().entries());
            assertEquals(full.revision(),denied.bag().revision());
            assertEquals(1,loot.spatialStockReceipts().size(),"no-fit is rejected before durable source reservation");
            assertEquals(7,payload.getQuantity(),"rejected source payload stays intact");
        }
    }
}
