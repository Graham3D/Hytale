package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.progress.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class Stage12MeaningfulUseTest {
    @TempDir Path directory;
    private RpgLoadoutService service(RpgPlayerStateRepository repository){
        var catalog=com.inigmasgames.hytalerpg.content.RpgCatalog.loadCanonical();var compatibility=new com.inigmasgames.hytalerpg.links.CompatibilityService();
        var graph=new com.inigmasgames.hytalerpg.links.RpgLinkGraphService(catalog,compatibility);
        return new RpgLoadoutService(catalog,repository,graph,new com.inigmasgames.hytalerpg.links.LinkCompiler(catalog,graph,compatibility),new OwnershipEntitlementPolicy(true),new Stage01BTestSupport.RecordingTracer());
    }
    @Test void pressingOrInvalidResultsNeverCallWriter(){var b=new MasteryRootBudget();
        assertEquals("NOT_MANUAL_ROOT",b.award(false,true,true,false,0,n->fail()));
        assertEquals("NO_MEANINGFUL_RESULT",b.award(true,false,true,false,0,n->fail()));
        assertEquals("ENCOUNTER_INELIGIBLE",b.award(true,true,false,false,0,n->fail()));}
    @Test void ordinaryRootNeverPaysPerTargetChildOrLaterTick(){var b=new MasteryRootBudget();var calls=new AtomicInteger();
        b.award(true,true,true,false,0,n->{assertEquals(0,n);calls.incrementAndGet();});
        for(long now:new long[]{0,1,5_000_000_000L,Long.MAX_VALUE})assertEquals("ROOT_MASTERY_DEDUP",b.award(true,true,true,false,now,n->calls.incrementAndGet()));
        assertEquals(1,calls.get());}
    @Test void sustainedRootsUseElapsedFiveSecondsNotAdjacentWallClockBuckets(){var b=new MasteryRootBudget();var awards=new ArrayList<Long>();
        b.award(true,true,true,true,4_999_000_000L,awards::add);
        assertEquals("ROOT_MASTERY_DEDUP",b.award(true,true,true,true,5_001_000_000L,awards::add));
        b.award(true,true,true,true,9_999_000_000L,awards::add);assertEquals(List.of(0L,1L),awards);}
    @Test void idleIntervalsDoNotCatchUpUnperformedWork(){var b=new MasteryRootBudget();var awards=new ArrayList<Long>();
        b.award(true,true,true,true,0,awards::add);b.award(true,true,true,true,100_000_000_000L,awards::add);assertEquals(List.of(0L,1L),awards);}
    @Test void uncertainWriteFreezesRootInsteadOfChoosingANewEvent(){var b=new MasteryRootBudget();
        assertThrows(IllegalStateException.class,()->b.award(true,true,true,true,0,n->{throw new IllegalStateException("disk");}));
        assertEquals("MASTERY_PERSISTENCE_UNCERTAIN",b.award(true,true,true,true,99_000_000_000L,n->fail()));}
    @Test void simultaneousChildrenUseOneWriter() throws Exception {var b=new MasteryRootBudget();var count=new AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(8)){var jobs=new ArrayList<Callable<String>>();for(int i=0;i<64;i++)jobs.add(()->b.award(true,true,true,true,0,n->count.incrementAndGet()));
            for(var result:pool.invokeAll(jobs))assertNotNull(result.get());}assertEquals(1,count.get());}
    @Test void rootIdentityAndSustainedPolicyCannotBeReboundByChildren(){var b=new MasteryRootBudget();b.bindPrimary("primary",true);b.bindPrimary("echo",false);assertEquals("primary",b.primaryInstance());assertTrue(b.sustained());}
    @Test void actualPlayerStoreReplayKeepsOneMasteryAndNoOtherRewards(){
        var repository=new FileRpgPlayerStateRepository(directory.resolve("players"));
        var service=service(repository);
        service.configureEarnedRewards(new FileEarnedRewardStore(directory.resolve("earned-rewards")));
        var actor=UUID.randomUUID();var reward=new EarnedReward("mastery/root/0",0,0,Map.of("fire_bolt",1L),"MEANINGFUL_MANUAL_ROOT","root","instance","correlation");
        service.awardEarned(actor,reward);assertEquals(EarnedRewardStore.Outcome.DUPLICATE,service.awardEarned(actor,reward).outcome());
        var state=repository.load(actor).state();assertEquals(1,state.skillMastery.get("fire_bolt"));assertEquals(0,state.currentXp);assertEquals(0,state.rewards.insight());assertEquals(0,state.unspentAttributePoints);
        var reloaded=service(repository);reloaded.configureEarnedRewards(new FileEarnedRewardStore(directory.resolve("earned-rewards")));
        assertEquals(EarnedRewardStore.Outcome.DUPLICATE,reloaded.awardEarned(actor,reward).outcome());assertEquals(1,reloaded.masteryXp(actor,"fire_bolt"));}
    @Test void masteryEntersSnapshotOnceWithoutChangingGeometryCostOrCooldown(){
        var base=new Stage10SummonTest.Harness();assertTrue(base.cast().committed());
        var mastered=new Stage10SummonTest.Harness();
        assertTrue(mastered.bundle.service().mutateProgress(mastered.actor,mastered.bundle.service().getLoadout(mastered.actor).state().revision,"mastery-fixture",state->state.skillMastery.put("wolf_summon",Long.MAX_VALUE)).success());
        assertTrue(mastered.cast().committed());var a=base.context;var b=mastered.context;
        assertEquals(a.profile(),b.profile());assertEquals(a.snapshot().modifiers().factor()*1.38,b.snapshot().modifiers().factor(),1e-10);
        assertEquals(a.snapshot().resourceCost(),b.snapshot().resourceCost());assertEquals(a.snapshot().cooldownSeconds(),b.snapshot().cooldownSeconds());
        assertEquals(a.snapshot().basePower(),b.snapshot().basePower());assertEquals(a.snapshot().criticalChance(),b.snapshot().criticalChance());
        assertSame(b.effects().mastery(),b.withSnapshot(b.snapshot()).effects().mastery());}
}
