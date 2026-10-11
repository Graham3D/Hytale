package com.inigmasgames.hytalerpg.progress;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class FileEncounterStoreGearScanConcurrencyTest {
    @TempDir Path directory;
    record Row(int id,int version){}

    @Test void longGearProjectionReleasesEncounterMonitorWithoutLosingReceipts() throws Exception {
        try(var store=FileEncounterStore.durableV2(directory,ignored->{},ignored->{},false);
            var threads=Executors.newFixedThreadPool(3)){
            for(int id=0;id<20;id++){
                int rowId=id;
                store.gearTransaction("loot","row-"+id,Row.class,old->new Row(rowId,0));
            }
            var batchReached=new CountDownLatch(1);
            var releaseScan=new CountDownLatch(1);
            store.gearRecordBatchBoundary(last->{
                if(last!=16)return;
                batchReached.countDown();
                try{if(!releaseScan.await(5,TimeUnit.SECONDS))throw new AssertionError("scan test barrier timeout");}
                catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}
            });
            var scan=threads.submit(()->store.gearRecords("loot",Row.class));
            try{
                assertTrue(batchReached.await(5,TimeUnit.SECONDS));
                var foreground=threads.submit(()->{store.configureGearDelivery(ignored->{});return store.pendingCount();});
                assertEquals(0,foreground.get(2,TimeUnit.SECONDS));
                var update=threads.submit(()->store.gearTransaction("loot","row-17",Row.class,
                    old->new Row(old.orElseThrow().id(),1)));
                assertEquals(new Row(17,1),update.get(2,TimeUnit.SECONDS));
            }finally{releaseScan.countDown();}
            var rows=scan.get(5,TimeUnit.SECONDS);
            assertEquals(20,rows.size());
            var ids=new HashSet<Integer>();
            for(var row:rows)ids.add(row.id());
            assertEquals(20,ids.size());
            assertEquals(new Row(17,1),store.gearRead("loot","row-17",Row.class).orElseThrow());
        }
    }
}
