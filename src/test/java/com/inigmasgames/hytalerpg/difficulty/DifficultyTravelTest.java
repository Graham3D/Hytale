package com.inigmasgames.hytalerpg.difficulty;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class DifficultyTravelTest {
    @TempDir Path root;
    final UUID actor=UUID.randomUUID(),normal=UUID.randomUUID(),nightmare=UUID.randomUUID(),hell=UUID.randomUUID();
    final class NativePort implements DifficultyTravel.Port {
        UUID location=normal;DifficultyProgress progress=DifficultyProgress.INITIAL;int transfers,cleanups;boolean unavailable,failedHandoff,arriveThenFail;
        CompletableFuture<DifficultyTravel.Destination> held;boolean forceSeen;
        public CompletionStage<UUID> admit(UUID player,DifficultyId mode,boolean forced){forceSeen=forced;String reject=GolemMilestones.load().rejection(progress,mode);
            return !forced&&!reject.isEmpty()?CompletableFuture.failedFuture(new IllegalStateException(reject)):CompletableFuture.completedFuture(location);}
        public CompletionStage<DifficultyTravel.Destination> prepare(UUID player,DifficultyId mode){
            if(held!=null)return held;if(unavailable)return CompletableFuture.failedFuture(new IllegalStateException("world unavailable"));return CompletableFuture.completedFuture(destination(mode));}
        public CompletionStage<Void> handoff(DifficultyTravel.Pending intent){assertTrue(Files.exists(root.resolve("travel.json")));assertEquals(location,intent.source());
            cleanups++;if(failedHandoff)return CompletableFuture.failedFuture(new IllegalStateException("native transfer uncertain"));
            transfers++;location=intent.destination().world();if(arriveThenFail)return CompletableFuture.failedFuture(new IllegalStateException("lost completion acknowledgement"));return CompletableFuture.completedFuture(null);}
        public CompletionStage<UUID> location(UUID player){return CompletableFuture.completedFuture(location);}
        void unlock(DifficultyId mode){var previous=DifficultyId.values()[mode.ordinal()-1];for(String g:GolemMilestones.REQUIRED_V1)progress=progress.complete(previous,g);progress=progress.unlockNext(previous,GolemMilestones.REQUIRED_V1);}
    }
    DifficultyTravel.Destination destination(DifficultyId mode){return new DifficultyTravel.Destination(switch(mode){case NORMAL->normal;case NIGHTMARE->nightmare;case HELL->hell;},mode.name(),mode,1,70,1,0);}
    DifficultyTravel service(NativePort p){return new DifficultyTravel(root.resolve("travel.json"),p,Runnable::run);}
    void done(CompletionStage<?> f){f.toCompletableFuture().join();}
    void failed(CompletionStage<?> f){assertThrows(CompletionException.class,()->done(f));}
    @Test void unlockedPlayerTravelsAndReturnsWithoutMutatingProgress(){var p=new NativePort();p.unlock(DifficultyId.NIGHTMARE);var before=p.progress;var t=service(p);
        done(t.request(actor,DifficultyId.NIGHTMARE,false));assertEquals(nightmare,p.location);assertFalse(t.busy(actor));assertEquals(before,p.progress);
        done(t.request(actor,DifficultyId.NORMAL,false));assertEquals(normal,p.location);assertEquals(2,p.transfers);assertEquals(2,p.cleanups);assertEquals(before,p.progress);assertFalse(service(p).busy(actor));}
    @Test void lockedEntrantAndPartyMemberCannotBorrowAnotherPlayersUnlock(){var unlocked=new NativePort();unlocked.unlock(DifficultyId.NIGHTMARE);done(service(unlocked).request(actor,DifficultyId.NIGHTMARE,false));
        var member=new NativePort();var t=service(member);failed(t.request(UUID.randomUUID(),DifficultyId.NIGHTMARE,false));assertEquals(0,member.transfers);assertEquals(0,member.cleanups);}
    @Test void recommendationsAreNotLevelGates(){var p=new NativePort();p.unlock(DifficultyId.NIGHTMARE);assertEquals("",GolemMilestones.load().rejection(p.progress,DifficultyId.NIGHTMARE));
        assertEquals(40,DifficultyId.NIGHTMARE.recommendedLevel());assertEquals(60,DifficultyId.HELL.recommendedLevel());done(service(p).request(actor,DifficultyId.NIGHTMARE,false));}
    @Test void forcedTestNeverGrantsPermanentUnlock(){var p=new NativePort();done(service(p).request(actor,DifficultyId.HELL,true));assertTrue(p.forceSeen);assertEquals(hell,p.location);assertEquals(DifficultyProgress.INITIAL,p.progress);}
    @Test void unavailableWorldDoesNotCleanOrWriteIntent(){var p=new NativePort();p.unavailable=true;var t=service(p);failed(t.request(actor,DifficultyId.NORMAL,false));assertFalse(t.busy(actor));assertEquals(0,p.cleanups);assertFalse(Files.exists(root.resolve("travel.json")));}
    @Test void simultaneousPortalTouchesProduceOneNativeHandoff(){var p=new NativePort();p.unlock(DifficultyId.NIGHTMARE);p.held=new CompletableFuture<>();var t=service(p);
        var first=t.request(actor,DifficultyId.NIGHTMARE,false);var second=t.request(actor,DifficultyId.HELL,true);assertTrue(t.busy(actor));assertEquals(0,p.transfers);
        p.held.complete(destination(DifficultyId.NIGHTMARE));done(first);done(second);assertEquals(1,p.transfers);assertEquals(nightmare,p.location);}
    @Test void uncertainHandoffStaysBlockedUntilRestartRecoveryFromSource(){var p=new NativePort();p.unlock(DifficultyId.NIGHTMARE);p.failedHandoff=true;var t=service(p);failed(t.request(actor,DifficultyId.NIGHTMARE,false));
        assertTrue(t.busy(actor));assertTrue(t.pending(actor).isPresent());failed(t.request(actor,DifficultyId.HELL,true));assertEquals(1,p.cleanups);
        p.failedHandoff=false;var restart=service(p);done(restart.recover(actor));assertEquals(normal,p.location);assertEquals(0,p.transfers);assertFalse(restart.busy(actor));}
    @Test void lostAcknowledgementAfterArrivalDoesNotReplayNativeTransfer(){var p=new NativePort();p.unlock(DifficultyId.NIGHTMARE);p.arriveThenFail=true;var t=service(p);failed(t.request(actor,DifficultyId.NIGHTMARE,false));
        assertEquals(nightmare,p.location);assertTrue(t.busy(actor));var restart=service(p);done(restart.recover(actor));assertEquals(1,p.transfers);assertEquals(1,p.cleanups);assertFalse(restart.busy(actor));}
    @Test void unexpectedReconnectWorldFallsBackToNormalWithOneHandoff(){var p=new NativePort();p.unlock(DifficultyId.NIGHTMARE);p.failedHandoff=true;failed(service(p).request(actor,DifficultyId.NIGHTMARE,false));
        p.failedHandoff=false;p.location=UUID.randomUUID();var restarted=service(p);done(restarted.recover(actor));assertEquals(normal,p.location);assertEquals(1,p.transfers);}
    @Test void corruptRecoveryJournalFailsClosed()throws Exception{var p=new NativePort();p.failedHandoff=true;p.unlock(DifficultyId.NIGHTMARE);failed(service(p).request(actor,DifficultyId.NIGHTMARE,false));
        var file=root.resolve("travel.json");Files.writeString(file,Files.readString(file).replace("sha256","broken"));assertThrows(IllegalStateException.class,()->service(p));assertEquals(0,p.transfers);}
    @Test void failedIntentWriteDoesNotTransferAndKeepsUncertaintyBlocked()throws Exception{Files.createDirectory(root.resolve("travel.json"));var p=new NativePort();
        // Construct against an absent path, then introduce a filesystem failure before the write.
        Path journal=root.resolve("other.json");var t=new DifficultyTravel(journal,p,Runnable::run);Files.createDirectory(journal);
        failed(t.request(actor,DifficultyId.HELL,true));assertTrue(t.busy(actor));assertEquals(0,p.transfers);assertEquals(0,p.cleanups);}
}
