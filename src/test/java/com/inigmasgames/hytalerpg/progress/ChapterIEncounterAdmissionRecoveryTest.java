package com.inigmasgames.hytalerpg.progress;

import java.lang.reflect.Constructor;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ChapterIEncounterAdmissionRecoveryTest {
    @TempDir Path root;
    private EnemyRewardRegistry.Spawn spawn(UUID world,UUID enemy,long observed){return EnemyRewardRegistry.load().classify(world,enemy,
            "Wolf_Black","Default/Zone1_Tier1/Forest_Birch",EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,observed).orElseThrow();}
    @Test void failedWorldRecoveryReleasesItsDeliveryFenceAndKeepsPersistenceFailClosed()throws Exception{
        var path=root.resolve("malformed-recovery");Files.createDirectories(path.resolve("enemy-birth-roots"));
        Files.writeString(path.resolve("enemy-birth-roots").resolve("invalid-shard"),"invalid");
        try(var store=new FileEncounterStore(path)){
            var runtime=new PersistentEncounterRuntime(store,(id,reward)->{});
            try{
                var recovery=runtime.recoverEnemyWorld(UUID.randomUUID());
                assertThrows(ExecutionException.class,()->recovery.toCompletableFuture().get(5,TimeUnit.SECONDS));
                assertFalse(runtime.worldRecoveryInProgress(),"A rejected recovery cannot suppress delivery forever");
                assertTrue(runtime.unavailable(),"Invalid durable indexes remain fail-closed");
            }finally{assertThrows(IllegalStateException.class,runtime::close);}
        }
    }
    @Test void unclassifiedLoadsRetireAttachmentsAndDoNotAccumulateCapacity()throws Exception {
        try(var store=new FileEncounterStore(root.resolve("contexts"));var runtime=new PersistentEncounterRuntime(store,(id,reward)->{})){
            UUID world=UUID.randomUUID();
            for(int i=0;i<320;i++)assertFalse(runtime.attachNative(world,new UUID(0,i+1),"Wolf_Black",Optional.empty())
                    .toCompletableFuture().get(4,TimeUnit.SECONDS));
            assertEquals(0,runtime.handoffMetrics().get("contexts"));
            assertEquals(0L,((Map<?,?>)runtime.handoffMetrics().get("loads")).get("rejections"));
            assertFalse(runtime.unavailable());
        }
    }
    @Test void duplicateNativeAddSharesOriginalImmutableContextDespiteDifferentObservationClock()throws Exception {
        try(var store=new FileEncounterStore(root.resolve("duplicates"));var runtime=new PersistentEncounterRuntime(store,(id,reward)->{})){
            UUID world=UUID.randomUUID(),enemy=UUID.randomUUID();
            var first=runtime.attachNative(world,enemy,"Wolf_Black",Optional.of(spawn(world,enemy,100)));
            var duplicate=runtime.attachNative(world,enemy,"Wolf_Black",Optional.of(spawn(world,enemy,101)));
            assertTrue(first.toCompletableFuture().get(4,TimeUnit.SECONDS));
            assertTrue(duplicate.toCompletableFuture().get(4,TimeUnit.SECONDS));
            assertEquals(1,runtime.handoffMetrics().get("contexts"));
            assertEquals(100,store.load(world,enemy).orElseThrow().spawn().spawnedAtMillis());
            runtime.detachNative(world,enemy);
        }
    }
    @Test void durableReservationBoundaryIsNamedAndReleasedTicketsCanBeReadmitted(){
        try(var effects=new DurableEncounterEffects()){
            var leases=new ArrayList<DurableEncounterEffects.Reservation>();
            for(int i=0;i<DurableEncounterEffects.MAX_PENDING;i++)leases.add(effects.reserve());
            var rejected=assertThrows(FileEncounterStore.CapacityRejected.class,effects::reserve);
            assertEquals("ENCOUNTER_PERSISTENCE_CAPACITY:PENDING_DURABLE_EFFECTS:256/256",rejected.getMessage());
            leases.forEach(DurableEncounterEffects.Reservation::close);
            assertEquals(0,effects.pending());
            try(var admitted=effects.reserve()){assertEquals(1,effects.pending());}
        }
    }
    @Test void burstyContextAdmissionsRejectAtExistingBoundThenDrainWithoutLeaking()throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var store=new FileEncounterStore(root.resolve("burst"),boundary->{
                    if(boundary==FileEncounterStore.Boundary.AFTER_CONTEXT){entered.countDown();
                        try{if(!release.await(8,TimeUnit.SECONDS))throw new IllegalStateException("TEST_BLOCK_TIMEOUT");}
                        catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}}
                });var runtime=new PersistentEncounterRuntime(store,(id,reward)->{})){
            UUID world=UUID.randomUUID(),firstEnemy=UUID.randomUUID();
            var first=runtime.attachNative(world,firstEnemy,"Wolf_Black",Optional.of(spawn(world,firstEnemy,100)));
            try{
                assertTrue(entered.await(4,TimeUnit.SECONDS));
                var queued=new ArrayList<CompletionStage<Boolean>>();
                for(int i=1;i<DurableEncounterEffects.MAX_PENDING;i++)queued.add(runtime.attachNative(world,new UUID(4,i),"Wolf_Black",Optional.empty()));
                var rejected=assertThrows(FileEncounterStore.CapacityRejected.class,()->runtime.attachNative(world,UUID.randomUUID(),"Wolf_Black",Optional.empty()));
                assertEquals("ENCOUNTER_PERSISTENCE_CAPACITY:PENDING_DURABLE_EFFECTS:256/256",rejected.getMessage());
                assertEquals(DurableEncounterEffects.MAX_PENDING,((Map<?,?>)runtime.handoffMetrics().get("loads")).get("pendingCount"));
                assertFalse(runtime.unavailable());
            }finally{release.countDown();}
            assertTrue(first.toCompletableFuture().get(8,TimeUnit.SECONDS));
            runtime.close();
            assertEquals(0,((Map<?,?>)runtime.handoffMetrics().get("loads")).get("pendingCount"));
            assertEquals(1,runtime.handoffMetrics().get("contexts"));
            assertFalse(runtime.unavailable());
        }finally{release.countDown();}
    }
    @Test void liveAttachmentBoundaryIsDistinctAndDoesNotReserveAnotherLoad()throws Exception {
        try(var store=new FileEncounterStore(root.resolve("limit"));var runtime=new PersistentEncounterRuntime(store,(id,reward)->{})){
            // Fill only the in-memory admission table; 4,096 real disk writes would test IO, not this bound.
            var field=PersistentEncounterRuntime.class.getDeclaredField("attachments");field.setAccessible(true);
            @SuppressWarnings("unchecked") Map<Object,Object> attachments=(Map<Object,Object>)field.get(runtime);
            var keyType=Class.forName(PersistentEncounterRuntime.class.getName()+"$Key");
            Constructor<?> key=keyType.getDeclaredConstructor(UUID.class,UUID.class);key.setAccessible(true);
            var attachmentType=Class.forName(PersistentEncounterRuntime.class.getName()+"$Attachment");
            Constructor<?> value=attachmentType.getDeclaredConstructor(String.class,Optional.class);value.setAccessible(true);
            UUID world=UUID.randomUUID();
            for(int i=0;i<EncounterContributions.MAX_ENCOUNTERS;i++)attachments.put(key.newInstance(world,new UUID(0,i+1)),value.newInstance("Wolf_Black",Optional.empty()));
            int before=((Map<?,?>)runtime.handoffMetrics().get("loads")).get("pendingCount") instanceof Integer count?count:-1;
            var rejected=assertThrows(FileEncounterStore.CapacityRejected.class,()->runtime.attachNative(world,UUID.randomUUID(),"Wolf_Black",Optional.empty()));
            assertEquals("ENCOUNTER_PERSISTENCE_CAPACITY:LIVE_ATTACHMENTS:4096/4096",rejected.getMessage());
            assertEquals(before,((Map<?,?>)runtime.handoffMetrics().get("loads")).get("pendingCount"));
            assertFalse(runtime.unavailable());
            attachments.clear();
        }
    }
}
