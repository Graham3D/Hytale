package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.content.*;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.enemies.*;
import com.inigmasgames.hytalerpg.links.*;
import com.inigmasgames.hytalerpg.progress.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class Me12AcquisitionTest {
    @TempDir Path directory;
    final UUID player=UUID.randomUUID();final RpgCatalog catalog=RpgCatalog.loadCanonical();
    LearningSources sources(){return new LearningSources(catalog,List.of(new LearningSources.Binding("fixture_role","fixture_source",ProgressionMath.AcquisitionRarity.COMMON,
            new LearningSources.Assignment(LearningSources.AbilityKind.FIRST_COPY_PASSIVE,"potency","VERIFIED_CONNECTED","SYNTHETIC_OFFLINE_TEST_ONLY",ProgressionMath.Rank.COMMON))));}
    LearningSources.Opportunity opportunity(){
        var p=Me12Policies.canonical();var c=new EnemyRewardContext(p.balance().revision(),EnemyRarity.UNIQUE,EnemyRewardContext.Origin.NATURAL,false,8,
                2,1.5,1.35,.10,.15,.15,new Me12Identity.RewardReference(2,p.digest(),p.references(),940,8));
        return sources().resolve("fixture_role",ProgressionMath.Rank.BOSS,0).orElseThrow().withEnemyRewards(c);
    }
    FileRpgPlayerStateRepository repository(Path root){return new FileRpgPlayerStateRepository(root.resolve("players"));}
    RpgLoadoutService service(Path root,FileEarnedRewardStore rewards){
        var compatibility=new CompatibilityService();var graph=new RpgLinkGraphService(catalog,compatibility);
        var service=new RpgLoadoutService(catalog,repository(root),graph,new LinkCompiler(catalog,graph,compatibility),new OwnershipEntitlementPolicy(true),new Stage01BTestSupport.RecordingTracer());
        service.configureLearningSources(sources());service.configureEarnedRewards(rewards);return service;
    }
    EarnedReward defeat(String id){return new EarnedReward(id,10,1,Map.of(),"ENEMY_DEATH","","",id);}
    void pity(Path root){var state=RpgPlayerState.create(player);state.acquisition=new AcquisitionProgress(Set.of(),Map.of("fixture_source",20),0);repository(root).save(state);}
    @Test void R08TypedPassiveAdapterHasNoShippedSourceAndRetainsZeroCopyAcquisitionHistory(){
        assertEquals(0,LearningSources.load(catalog).verifiedBindings());
        assertTrue(LearningSources.load(catalog).resolve("fixture_role",ProgressionMath.Rank.BOSS,0).isEmpty());
        var state=RpgPlayerState.create(player);state.ownedPassives.put("potency",0);var event=defeat("consumed");
        assertSame(event,opportunity().decide(event,RewardCheckpoint.of(state),()->{fail("Acquired passive must not reroll");return 0;}));
        assertEquals(0,state.ownedPassives.get("potency"));
    }
    @Test void R05R07DeterminismAndOrdinaryPityAdvanceAreSeparateFromGuaranteedPity(){
        var op=opportunity();var before=RewardCheckpoint.of(RpgPlayerState.create(player));
        for(int i=0;i<200;i++){
            var event=defeat("d/"+i);var a=op.decide(event,before,player,()->{fail("No ambient RNG for v2");return 0;});
            assertEquals(a,op.decide(event,before,player,()->.999));
            var after=RewardIntent.create(player,a,before).after();
            if(a.progression().kind()==ProgressionDelta.Kind.PASSIVE_LEARNING_FAILURE)assertEquals(1,after.acquisition().progress().pity().get("fixture_source"));
            else assertEquals(1,after.acquisition().ownedPassives().get("potency"));
        }
        var state=RpgPlayerState.create(player);state.acquisition=new AcquisitionProgress(Set.of(),Map.of("fixture_source",20),0);
        assertEquals(ProgressionDelta.Kind.PASSIVE_LEARNING_SUCCESS,op.decide(defeat("guaranteed"),RewardCheckpoint.of(state),()->{fail("Pity bypasses roll");return 0;}).progression().kind());
    }
    @Test void P05R07PassiveGrantXpAndPityRecoverExactlyOnceAtEveryWriteBoundary(){
        for(var boundary:FileEarnedRewardStore.Boundary.values()){
            var root=directory.resolve(boundary.name());pity(root);var armed=new AtomicBoolean(true);var op=opportunity();
            try(var service=service(root,new FileEarnedRewardStore(root.resolve("rewards"),at->{if(at==boundary&&armed.getAndSet(false))throw new IllegalStateException("PROCESS_LOSS");}))){
                assertThrows(IllegalStateException.class,()->service.awardGenerated(player,"defeat",before->op.decide(defeat("defeat"),before,player,()->.9)));
            }
            try(var restarted=service(root,new FileEarnedRewardStore(root.resolve("rewards")))){
                assertEquals(EarnedRewardStore.Outcome.DUPLICATE,restarted.awardGenerated(player,"defeat",before->{fail("Replay must not reevaluate Wisdom or RNG");return null;}).outcome());
                var state=repository(root).load(player).state();assertEquals(10,state.currentXp);assertEquals(1,state.ownedPassives.get("potency"));assertFalse(state.acquisition.pity().containsKey("fixture_source"));
            }
        }
    }
    @Test void R07ConcurrentPurchaseAndLearningUseOnePlayerTransactionOwner()throws Exception{
        pity(directory);var op=opportunity();
        try(var service=service(directory,new FileEarnedRewardStore(directory.resolve("rewards")));var pool=Executors.newFixedThreadPool(2)){
            service.awardEarned(player,new EarnedReward("fund",0,100,Map.of(),"FIXTURE","","","fund"));
            service.awardEarned(player,Stage12AcquisitionTest.use("use","quick_slash"));
            long revision=repository(directory).load(player).state().revision;var start=new CountDownLatch(1);
            var purchase=pool.submit(()->{start.await();try{return service.purchasePassive(player,new PassiveId("potency"),revision,UUID.randomUUID()).outcome().name();}catch(IllegalArgumentException stale){assertEquals("STALE_REVISION",stale.getMessage());return "STALE";}});
            var learn=pool.submit(()->{start.await();return service.awardGenerated(player,"race",before->op.decide(defeat("race"),before,player,()->.9));});start.countDown();purchase.get(5,TimeUnit.SECONDS);learn.get(5,TimeUnit.SECONDS);
            var state=repository(directory).load(player).state();assertEquals(1,state.ownedPassives.get("potency"));assertEquals(10,state.currentXp);
            assertTrue(state.acquisition.spentInsight()==0||state.acquisition.spentInsight()==20);
        }
    }
    @Test void R01R12FrozenThreatXpUsesOneRarityPositionAndReplaysThroughExistingDeathAndPlayerJournals(){
        var fixture=Me12PersistenceTest.fixture(true);var actor=fixture.root().plan().actors().getFirst();
        var spawn=Me12PersistenceTest.spawn(actor,true);var world=spawn.world();var enemy=spawn.enemy();
        var point=com.inigmasgames.hytalerpg.execution.math.Vec3.ZERO;
        long initialXp=new com.inigmasgames.hytalerpg.ui.CharacterXpProjectionService().levelStartXp(50);
        var state=RpgPlayerState.create(player);state.level=50;state.currentXp=initialXp;repository(directory).save(state);
        double factor=2*(1+.15*Math.min((actor.v2().threatTotal()-100)/100.,9));
        long expected=ProgressionMath.enemyReward(50,ProgressionMath.Rank.COMMON,factor,50);
        EncounterContributions.DeathPlan frozen;
        try(var store=new FileEncounterStore(directory.resolve("encounters"));var service=service(directory,new FileEarnedRewardStore(directory.resolve("rewards")))){
            store.archiveMe12Policies(fixture.policies());store.reserveEnemyBirthRoot(fixture.root());
            store.transitionEnemyPack(world,actor.packId(),EnemyPackRecord::staged);
            store.transitionEnemyPack(world,actor.packId(),EnemyPackRecord::publish);
            var ledger=new EncounterContributions();ledger.restore(store.create(spawn));
            assertTrue(ledger.damage(world,enemy,player,100,90,100,true,1001));store.save(ledger.snapshot(world,enemy));
            frozen=ledger.death(world,enemy,point,1002,List.of(new EncounterContributions.Participant(player,world,point,50,true,null)));
            assertEquals(expected,frozen.shares().getFirst().xp());assertEquals(ProgressionMath.Rank.COMMON.insight,frozen.shares().getFirst().insight());
            var award=frozen.reward(frozen.shares().getFirst());assertTrue(award.mastery().isEmpty());assertNull(award.milestone());assertNull(award.progression());assertNull(award.goldPot());
            store.freeze(frozen);var armed=new AtomicBoolean(true);
            assertEquals("LOST_DEATH_CURSOR_AFTER_PLAYER_COMMIT",assertThrows(IllegalStateException.class,()->store.drain(256,(id,reward)->{service.awardEarned(id,reward);if(armed.getAndSet(false))throw new IllegalStateException("LOST_DEATH_CURSOR_AFTER_PLAYER_COMMIT");})).getMessage());
        }
        try(var store=new FileEncounterStore(directory.resolve("encounters"));var service=service(directory,new FileEarnedRewardStore(directory.resolve("rewards")))){
            assertEquals(frozen,store.death(world,enemy).orElseThrow());store.drain(256,service::awardEarned);
            assertEquals(0,store.drain(256,service::awardEarned));assertEquals(frozen,store.freeze(frozen));
            var restored=repository(directory).load(player).state();assertEquals(50,restored.level);assertEquals(initialXp+expected,restored.currentXp);
            assertEquals(1,restored.rewards.sequence());assertEquals(ProgressionMath.Rank.COMMON.insight,restored.rewards.insight());
        }
    }
}
