package com.inigmasgames.hytalerpg;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.links.*;
import com.inigmasgames.hytalerpg.progress.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class Stage12AcquisitionTest {
    static final RpgCatalog C=RpgCatalog.loadCanonical();
    @TempDir Path temp;final UUID player=UUID.randomUUID();
    FileRpgPlayerStateRepository repo(){return new FileRpgPlayerStateRepository(temp.resolve("players"));}
    RpgLoadoutService service(){return service(C,new FileEarnedRewardStore(temp.resolve("rewards")));}
    RpgLoadoutService service(RpgCatalog catalog,FileEarnedRewardStore rewards){
        var compatibility=new CompatibilityService();var graph=new RpgLinkGraphService(catalog,compatibility);
        var service=new RpgLoadoutService(catalog,repo(),graph,new LinkCompiler(catalog,graph,compatibility),new OwnershipEntitlementPolicy(true),new Stage01BTestSupport.RecordingTracer());
        service.configureEarnedRewards(rewards);return service;
    }
    RpgPlayerState state(){return repo().load(player).state();}
    static EarnedReward use(String event,String skill){return new EarnedReward(event,0,0,Map.of(skill,1L),"MEANINGFUL_MANUAL_ROOT",event,event,event,ProgressionDelta.meaningful(skill));}
    static EarnedReward death(String event){return new EarnedReward(event,10,1,Map.of(),"ENEMY_DEATH","","",event);}
    void fund(RpgLoadoutService s){s.awardEarned(player,new EarnedReward("fund",0,240,Map.of(),"ELIGIBLE_ENCOUNTER_FIXTURE","","","fund"));}
    static RpgCatalog verifiedFixture(){
        var json=new Gson().toJsonTree(C.skill(new SkillId("quick_slash")).orElseThrow()).getAsJsonObject();
        json.getAsJsonObject("sourceAcquisition").addProperty("validationState","VERIFIED_CONNECTED");
        var skill=new Gson().fromJson(json,SkillDefinition.class);
        return new RpgCatalog(C.skills().stream().map(s->s.id().equals(skill.id())?skill:s).toList(),C.passives());
    }
    @Test void shippedCatalogHas66ProposedAnd21UnassignedNoPublicRoll(){
        var sources=new LearningSources(C,List.of(new LearningSources.Binding("goblin_scrapper","goblin_scrapper",ProgressionMath.AcquisitionRarity.COMMON)));
        assertEquals(66,sources.assignedSources());assertEquals(0,sources.verifiedBindings());
        assertTrue(sources.resolve("goblin_scrapper",ProgressionMath.Rank.BOSS,500).isEmpty());
        assertEquals(23,C.skills().stream().filter(s->s.sourceAcquisition().signatureEnemyId().startsWith("UNASSIGNED")).count());
    }
    @Test void verifiedExplicitAliasesShareOneSourceAndUnknownIdentitiesCannotRoll(){
        var sources=new LearningSources(verifiedFixture(),List.of(new LearningSources.Binding("goblin_scrapper","goblin_scrapper",ProgressionMath.AcquisitionRarity.COMMON),new LearningSources.Binding("goblin_patrol","goblin_scrapper",ProgressionMath.AcquisitionRarity.COMMON)));
        assertEquals(sources.resolve("goblin_scrapper",ProgressionMath.Rank.COMMON,10),sources.resolve("goblin_patrol",ProgressionMath.Rank.COMMON,10));
        assertTrue(sources.resolve("goblin_name_guess",ProgressionMath.Rank.BOSS,10).isEmpty());
    }
    @Test void conflictingAliasRarityAndUnknownSourcesRejected(){
        assertThrows(IllegalArgumentException.class,()->new LearningSources(C,List.of(new LearningSources.Binding("a","goblin_scrapper",ProgressionMath.AcquisitionRarity.COMMON),new LearningSources.Binding("b","goblin_scrapper",ProgressionMath.AcquisitionRarity.RARE))));
        assertThrows(IllegalArgumentException.class,()->new LearningSources(C,List.of(new LearningSources.Binding("a","invented_source",ProgressionMath.AcquisitionRarity.COMMON))));
        assertThrows(IllegalArgumentException.class,()->LearningSources.sourceKey("UNASSIGNED - no source"));
    }
    @ParameterizedTest @EnumSource(ProgressionMath.AcquisitionRarity.class)
    void pityGuaranteesTheNextDefeatAfterExactFailureThreshold(ProgressionMath.AcquisitionRarity rarity){
        var checkpoint=RewardCheckpoint.of(RpgPlayerState.create(player));var opportunity=new LearningSources.Opportunity("quick_slash","goblin_scrapper",rarity,0);
        for(int i=0;i<ProgressionMath.pityFailures(rarity);i++){
            var reward=opportunity.decide(death("d"+i),checkpoint,()->.999);assertEquals(ProgressionDelta.Kind.LEARNING_FAILURE,reward.progression().kind());
            checkpoint=RewardIntent.create(player,reward,checkpoint).after();
        }
        var success=opportunity.decide(death("guarantee"),checkpoint,()->{fail("Guaranteed result must not roll");return 0;});
        assertEquals(ProgressionDelta.Kind.LEARNING_SUCCESS,success.progression().kind());
        var after=RewardIntent.create(player,success,checkpoint).after();assertTrue(after.acquisition().learnedSkills().contains("quick_slash"));assertTrue(after.acquisition().progress().pity().isEmpty());
        assertEquals(checkpoint.acquisition().ownedPassives(),after.acquisition().ownedPassives());
    }
    @Test void knownSkillDoesNotRollOrModifyPityOrPassives(){
        var s=RpgPlayerState.create(player);s.learnedSkills.add("quick_slash");
        var d=death("known");assertSame(d,new LearningSources.Opportunity("quick_slash","goblin_scrapper",ProgressionMath.AcquisitionRarity.UNIQUE,0).decide(d,RewardCheckpoint.of(s),()->{fail("Known skill RNG");return 0;}));
    }
    @Test void wisdomChangesChanceNotAccumulatedSourcePity(){
        var s=RpgPlayerState.create(player);s.acquisition=new AcquisitionProgress(Set.of(),Map.of("goblin_scrapper",19),0);
        var before=RewardCheckpoint.of(s);
        assertEquals(ProgressionDelta.Kind.LEARNING_FAILURE,new LearningSources.Opportunity("quick_slash","goblin_scrapper",ProgressionMath.AcquisitionRarity.COMMON,0).decide(death("low"),before,()->.3).progression().kind());
        assertEquals(ProgressionDelta.Kind.LEARNING_SUCCESS,new LearningSources.Opportunity("quick_slash","goblin_scrapper",ProgressionMath.AcquisitionRarity.COMMON,200).decide(death("high"),before,()->.3).progression().kind());
        assertEquals(19,before.acquisition().progress().pity().get("goblin_scrapper"));
    }
    @Test void proposedSourceCannotWriteEvenAnIntent(){
        var s=service();assertThrows(IllegalArgumentException.class,()->s.awardGenerated(player,"d",before->new LearningSources.Opportunity("quick_slash","goblin_scrapper",ProgressionMath.AcquisitionRarity.COMMON,0).decide(death("d"),before,()->0)));
        assertFalse(Files.exists(temp.resolve("rewards").resolve(player.toString()).resolve("pending.json")));assertEquals(0,state().rewards.sequence());
    }
    @Test void generatedLearningRollRunsOnceAcrossReplayAndRestart(){
        var s=service(verifiedFixture(),new FileEarnedRewardStore(temp.resolve("rewards")));var count=new AtomicInteger();
        var op=new LearningSources.Opportunity("quick_slash","goblin_scrapper",ProgressionMath.AcquisitionRarity.COMMON,0);
        s.awardGenerated(player,"d",before->op.decide(death("d"),before,()->{count.incrementAndGet();return .99;}));
        var again=service(verifiedFixture(),new FileEarnedRewardStore(temp.resolve("rewards")));
        assertEquals(EarnedRewardStore.Outcome.DUPLICATE,again.awardGenerated(player,"d",before->{fail("Duplicate must not recompute pity");return null;}).outcome());
        assertEquals(1,count.get());assertEquals(1,state().acquisition.pity().get("goblin_scrapper"));assertEquals(10,state().currentXp);
    }
    @Test void meaningfulUseUnlocksPurchaseNotFreeOwnershipAndPricesEveryCopy(){
        var s=service();fund(s);assertThrows(IllegalArgumentException.class,()->s.purchasePassive(player,new PassiveId("potency"),state().revision,UUID.randomUUID()));
        s.awardEarned(player,use("use","quick_slash"));assertTrue(state().ownedPassives.isEmpty());
        s.purchasePassive(player,new PassiveId("potency"),state().revision,UUID.randomUUID());s.purchasePassive(player,new PassiveId("potency"),state().revision,UUID.randomUUID());
        assertEquals(2,state().ownedPassives.get("potency"));assertEquals(40,state().acquisition.spentInsight());assertEquals(240,state().rewards.insight());assertEquals(200,state().acquisition.availableInsight(240));
    }
    @Test void familyUnlockDoesNotEnableWrongFamilyAndPreservesCompilerGates(){
        var s=service();fund(s);s.awardEarned(player,use("strike","quick_slash"));
        assertThrows(IllegalArgumentException.class,()->s.purchasePassive(player,new PassiveId("fork"),state().revision,UUID.randomUUID()));
        s.awardEarned(player,use("projectile","fire_bolt"));s.purchasePassive(player,new PassiveId("fork"),state().revision,UUID.randomUUID());assertEquals(40,state().acquisition.spentInsight());
        assertFalse(new CompatibilityService().assess(C.skill(new SkillId("quick_slash")).orElseThrow(),C.passive(new PassiveId("fork")).orElseThrow()).accepted());
    }
    @Test void purchaseReplayWorksWithStaleRevisionButDifferentPayloadConflicts(){
        var s=service();fund(s);s.awardEarned(player,use("use","fire_bolt"));var id=UUID.randomUUID();long revision=state().revision;
        s.purchasePassive(player,new PassiveId("potency"),revision,id);
        assertEquals(EarnedRewardStore.Outcome.DUPLICATE,service().purchasePassive(player,new PassiveId("potency"),revision,id).outcome());
        assertThrows(IllegalStateException.class,()->service().purchasePassive(player,new PassiveId("efficiency"),revision,id));assertEquals(20,state().acquisition.spentInsight());
    }
    @Test void staleNewPurchaseDoesNotLeaveIntentOrCharge(){
        var s=service();fund(s);s.awardEarned(player,use("use","quick_slash"));
        assertThrows(IllegalArgumentException.class,()->s.purchasePassive(player,new PassiveId("potency"),0,UUID.randomUUID()));assertEquals(0,state().acquisition.spentInsight());
        assertFalse(Files.exists(temp.resolve("rewards").resolve(player.toString()).resolve("pending.json")));
    }
    @Test void insufficientInsightNeverGrantsPartialCopy(){
        var s=service();s.awardEarned(player,use("use","quick_slash"));
        assertThrows(IllegalArgumentException.class,()->s.purchasePassive(player,new PassiveId("potency"),state().revision,UUID.randomUUID()));assertTrue(state().ownedPassives.isEmpty());assertEquals(0,state().acquisition.spentInsight());
    }
    @Test void genericMutationCannotForgePityOrRefundSpending(){
        var s=service();var result=s.mutateProgress(player,0,"forge",p->p.acquisition=new AcquisitionProgress(Set.of("quick_slash"),Map.of(),0));assertFalse(result.success());assertEquals(AcquisitionProgress.INITIAL,s.getPresentationView(player).state().acquisition);
    }
    @ParameterizedTest @EnumSource(FileEarnedRewardStore.Boundary.class)
    void purchaseRecoveryAtEveryDurableBoundaryChargesAndGrantsExactlyOnce(FileEarnedRewardStore.Boundary boundary){
        var s=service();fund(s);s.awardEarned(player,use("use","quick_slash"));long revision=state().revision;UUID id=UUID.randomUUID();
        var broken=service(C,new FileEarnedRewardStore(temp.resolve("rewards"),at->{if(at==boundary)throw new IllegalStateException("injected");}));
        assertThrows(IllegalStateException.class,()->broken.purchasePassive(player,new PassiveId("potency"),revision,id));
        assertEquals(EarnedRewardStore.Outcome.DUPLICATE,service().purchasePassive(player,new PassiveId("potency"),revision,id).outcome());
        assertEquals(1,state().ownedPassives.get("potency"));assertEquals(20,state().acquisition.spentInsight());assertEquals(3,state().rewards.sequence());
    }
    @Test void concurrentSameRevisionPurchasesCannotOverspend()throws Exception{
        var s=service();s.awardEarned(player,new EarnedReward("fund",0,20,Map.of(),"FIXTURE","","","fund"));s.awardEarned(player,use("use","quick_slash"));long revision=state().revision;
        var success=new AtomicInteger();try(var executor=Executors.newFixedThreadPool(8)){
            var futures=new ArrayList<Future<?>>();for(int i=0;i<8;i++)futures.add(executor.submit(()->{try{s.purchasePassive(player,new PassiveId("potency"),revision,UUID.randomUUID());success.incrementAndGet();}catch(IllegalArgumentException expected){assertEquals("STALE_REVISION",expected.getMessage());}}));
            for(var f:futures)f.get();
        }
        assertEquals(1,success.get());assertEquals(0,state().acquisition.availableInsight(20));assertEquals(1,state().ownedPassives.get("potency"));
    }
    @Test void acquisitionBoundsAndInvalidRollAreRejected(){
        assertThrows(IllegalArgumentException.class,()->new AcquisitionProgress(Set.of(),Map.of("a",151),0));
        assertThrows(IllegalStateException.class,()->new AcquisitionProgress(Set.of(),Map.of(),1).availableInsight(0));
        for(double roll:new double[]{-1,1,Double.NaN,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->new LearningSources.Opportunity("quick_slash","goblin_scrapper",ProgressionMath.AcquisitionRarity.COMMON,0).decide(death("d"),RewardCheckpoint.of(RpgPlayerState.create(player)),()->roll));
    }
}
