package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.progress.*;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.combat.barrier.ManaguardLedger;
import com.inigmasgames.hytalerpg.links.*;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage13PlayerReadIsolationTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    static final class HeldRepository implements RpgPlayerStateRepository {
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        volatile boolean hold,fail;
        public LoadResult load(UUID id){return new LoadResult(RpgPlayerState.create(id),false,false,RpgPlayerState.CURRENT_SCHEMA,List.of());}
        public void save(RpgPlayerState state){
            if(hold){entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("test storage deadline");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
            if(fail)throw new IllegalStateException("forced persistence uncertainty");
        }
    }
    private RpgLoadoutService service(HeldRepository repository){
        var catalog=RpgCatalog.loadCanonical();var compatibility=new CompatibilityService();var graph=new RpgLinkGraphService(catalog,compatibility);
        return new RpgLoadoutService(catalog,repository,graph,new LinkCompiler(catalog,graph,compatibility),new OwnershipEntitlementPolicy(true),ignored->{});
    }
    @Test void actualHotGettersContinueWhilePlayerWriterHoldsRepositoryAndHolder() throws Exception {
        var repo=new HeldRepository();try(var service=service(repo)){
            var actor=UUID.randomUUID();var other=UUID.randomUUID();service.getPresentationView(actor);service.getPresentationView(other);service.enableNonblockingReads();repo.hold=true;
            var receipt=service.submitSupport(actor,new SupportProgress(0,0,new ManaguardLedger(100,100,50,50),Map.of())).toCompletableFuture();
            try{
                assertTrue(repo.entered.await(1,TimeUnit.SECONDS));
                assertTimeoutPreemptively(Duration.ofSeconds(1),()->{
                    assertEquals(1,service.characterLevel(actor));assertEquals(10,service.rawAttribute(actor,RpgAttribute.WIS));assertEquals(0,service.masteryXp(actor,"quick_slash"));
                    assertEquals(0,service.getPresentationView(actor).state().support.managuard().deficit());
                    service.getLoadout(actor);service.getPresentationView(other);assertTrue(service.ready(actor));
                });
                assertFalse(receipt.isDone());
            }finally{repo.release.countDown();}
            receipt.get(2,TimeUnit.SECONDS);assertEquals(100,service.getPresentationView(actor).state().support.managuard().deficit());
        }
    }
    @Test void failedWriteCannotUseCachedProgressionAsAuthority() throws Exception {
        var repo=new HeldRepository();var service=service(repo);try{
            var actor=UUID.randomUUID();service.getPresentationView(actor);service.enableNonblockingReads();repo.fail=true;
            var receipt=service.submitSupport(actor,new SupportProgress(0,0,new ManaguardLedger(100,100,50,50),Map.of())).toCompletableFuture();
            assertThrows(ExecutionException.class,()->receipt.get(2,TimeUnit.SECONDS));assertFalse(service.ready(actor));
            assertThrows(IllegalStateException.class,()->service.characterLevel(actor));assertThrows(IllegalStateException.class,()->service.masteryXp(actor,"quick_slash"));
            assertTrue(service.getPresentationView(actor).plans().isEmpty());
        }finally{assertThrows(IllegalStateException.class,service::close);}
    }
    @Test void publishedViewCannotBeMutatedByItsConsumer(){
        var repo=new HeldRepository();try(var service=service(repo)){
            var actor=UUID.randomUUID();var view=service.getPresentationView(actor);view.state().attributes.put("WIS",999);view.state().equippedSkills[0]="fire_bolt";
            assertEquals(10,view.state().attributes.get("WIS"));assertNull(view.state().equippedSkills[0]);assertEquals(10,service.rawAttribute(actor,RpgAttribute.WIS));
        }
    }
    @Test void nativeChildBindingAndMasteryCaptureDoNotWaitForRealEarnedAwardRepositorySave()throws Exception{
        var repo=new HeldRepository();try(var service=service(repo)){
            service.configureEarnedRewards(new FileEarnedRewardStore(directory.resolve("earned")));var actor=UUID.randomUUID();service.getLoadout(actor);service.enableNonblockingReads();
            var root=new MasteryRootBudget();root.bindPrimary("primary",true);repo.hold=true;
            var award=CompletableFuture.runAsync(()->root.award(true,true,true,true,1,ordinal->service.awardEarned(actor,
                    new EarnedReward("held-root",0,0,Map.of("quick_slash",1L),"MEANINGFUL_MANUAL_ROOT","root","primary","corr",ProgressionDelta.meaningful("quick_slash")))));
            try{
                assertTrue(repo.entered.await(2,TimeUnit.SECONDS));
                assertTimeoutPreemptively(Duration.ofSeconds(1),()->{
                    root.bindPrimary("later-child",false);assertEquals("primary",root.primaryInstance());assertTrue(root.sustained());
                    assertEquals(0,service.masteryXp(actor,"quick_slash"));service.getPresentationView(actor);
                });assertFalse(award.isDone());
            }finally{repo.release.countDown();}
            award.get(3,TimeUnit.SECONDS);assertEquals(1,service.masteryXp(actor,"quick_slash"));
        }
    }
}
