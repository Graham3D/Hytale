package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.links.*;
import com.inigmasgames.hytalerpg.progress.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

/** New grouped production path composed with the unchanged real player/reward/death authorities. */
class Stage13GroupedRewardTest {
    @TempDir Path directory;
    private RpgLoadoutService rewards(){var catalog=RpgCatalog.loadCanonical();var compatible=new CompatibilityService();var graph=new RpgLinkGraphService(catalog,compatible);
        var service=new RpgLoadoutService(catalog,new FileRpgPlayerStateRepository(directory.resolve("players")),graph,new LinkCompiler(catalog,graph,compatible),new OwnershipEntitlementPolicy(true),new Stage01BTestSupport.RecordingTracer());service.configureEarnedRewards(new FileEarnedRewardStore(directory.resolve("earned-rewards")));return service;}
    @ParameterizedTest @EnumSource(value=FileEncounterStore.Boundary.class,names={"AFTER_FREEZE","AFTER_AWARD","AFTER_CURSOR","AFTER_COMPLETION","AFTER_CLEANUP"})
    void groupedContributionsThenInterruptedDeathDeliveryPayEveryParticipantExactlyOnce(FileEncounterStore.Boundary boundary)throws Exception{
        UUID world=new UUID(1,1),enemy=new UUID(2,2),a=new UUID(3,3),b=new UUID(3,4);
        var actors=List.of(a,b);var release=new CountDownLatch(1);var once=new AtomicBoolean();var service=rewards();
        try(var store=new FileEncounterStore(directory.resolve("encounters"),at->{if(at==boundary)throw new IllegalStateException("INTERRUPTED_DEATH_DELIVERY");},at->{},at->{if(at==FileEncounterStore.GroupBoundary.BEFORE_DEQUEUE&&once.compareAndSet(false,true)){try{if(!release.await(4,TimeUnit.SECONDS))throw new AssertionError("No batch release");}catch(InterruptedException e){throw new IllegalStateException(e);}}})){
            var runtime=new PersistentEncounterRuntime(store,(id,reward)->service.awardEarned(id,reward));var spawn=EnemyRewardRegistry.load().classify(world,enemy,"Wolf_Black","Default/Zone1_Tier1/Forest_Birch",EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,100).orElseThrow();assertTrue(runtime.attach(world,enemy,"Wolf_Black",Optional.of(spawn)));
            var receipts=new ArrayList<CompletionStage<Boolean>>();try{for(int i=0;i<64;i++)receipts.add(runtime.submitDamage(world,enemy,actors.get(i%2),100,90,100,true,101+i).durable());}finally{release.countDown();}
            for(var receipt:receipts)assertTrue(receipt.toCompletableFuture().get(5,TimeUnit.SECONDS));assertEquals(1,store.timings().count(EncounterPersistenceTimings.Phase.JOURNAL_FORCE));assertEquals(64,store.journalSequence());
            var participants=actors.stream().map(id->new EncounterContributions.Participant(id,world,Vec3.ZERO,5,true,"party")).toList();
            if(boundary==FileEncounterStore.Boundary.AFTER_FREEZE)assertThrows(IllegalStateException.class,()->runtime.death(world,enemy,Vec3.ZERO,165,participants));
            else{assertEquals(2,runtime.death(world,enemy,Vec3.ZERO,165,participants).orElseThrow().shares().size());assertThrows(IllegalStateException.class,()->runtime.drain(8));}
        }
        for(int restart=0;restart<3;restart++)try(var store=new FileEncounterStore(directory.resolve("encounters"))){
            var recovered=rewards();store.drain(8,(id,reward)->recovered.awardEarned(id,reward));assertEquals(64,store.journalSequence());assertEquals(0,store.pendingCount());assertEquals(2,store.death(world,enemy).orElseThrow().shares().size());
            for(var id:actors){var state=new FileRpgPlayerStateRepository(directory.resolve("players")).load(id).state();assertEquals(65,state.currentXp);assertEquals(1,state.rewards.insight());assertEquals(1,state.rewards.sequence());}
        }
    }
}
