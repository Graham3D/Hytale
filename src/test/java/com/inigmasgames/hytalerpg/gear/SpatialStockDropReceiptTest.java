package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.progress.FileEncounterStore;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class SpatialStockDropReceiptTest {
    @TempDir Path root;

    private static GearLootService service(FileEncounterStore store) {
        return new GearLootService(store, new GearDropGenerator(
                GearCatalog.load(), new GearBindings(), GearAffixRuntime.ENABLED));
    }

    @Test void preparedDropSurvivesRestartAndCannotBeFinalizedByAnotherOperation() {
        UUID source=UUID.randomUUID(), operation=UUID.randomUUID();
        var prepared=new GearLootService.SpatialStockDropReceipt(source,operation,
                UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"{\"Id\":\"Rock_Stone\"}",
                42,1,2,3,"PREPARED");
        try(var store=new FileEncounterStore(root)) {
            var loot=service(store);
            assertEquals(prepared,loot.prepareSpatialStockDrop(prepared));
            assertThrows(IllegalStateException.class,()->loot.prepareSpatialStockDrop(prepared));
        }
        try(var store=new FileEncounterStore(root)) {
            var loot=service(store);
            assertEquals(prepared,loot.spatialStockDropReceipts().getFirst());
            assertThrows(IllegalStateException.class,()->loot.finishSpatialStockDrop(source,UUID.randomUUID(),"FINALIZED"));
            assertEquals("FINALIZED",loot.finishSpatialStockDrop(source,operation,"FINALIZED").stage());
        }
    }
}
