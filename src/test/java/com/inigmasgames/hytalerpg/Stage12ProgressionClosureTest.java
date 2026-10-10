package com.inigmasgames.hytalerpg;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.combat.barrier.ManaguardLedger;
import com.inigmasgames.hytalerpg.combat.cooldown.SavedCooldown;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.gear.GearEconomyProgress;
import com.inigmasgames.hytalerpg.links.*;
import com.inigmasgames.hytalerpg.progress.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class Stage12ProgressionClosureTest {
    @TempDir Path temp;final UUID player=UUID.randomUUID();static final RpgCatalog C=RpgCatalog.loadCanonical();
    FileRpgPlayerStateRepository repo(){return new FileRpgPlayerStateRepository(temp.resolve("players"));}
    RpgLoadoutService service(){return service(C,new FileEarnedRewardStore(temp.resolve("rewards")));}
    RpgLoadoutService service(RpgCatalog catalog,FileEarnedRewardStore rewards){
        var compatibility=new CompatibilityService();var graph=new RpgLinkGraphService(catalog,compatibility);
        var s=new RpgLoadoutService(catalog,repo(),graph,new LinkCompiler(catalog,graph,compatibility),new OwnershipEntitlementPolicy(true),new Stage01BTestSupport.RecordingTracer());
        s.configureEarnedRewards(rewards);return s;
    }
    RpgPlayerState seed(){
        var s=RpgPlayerState.create(player);s.attributes.put("DEX",20);s.unspentAttributePoints=3;s.pendingLevelUpPoints=2;
        s.learnedSkills.addAll(Set.of("quick_slash","fire_bolt"));s.ownedPassives.put("potency",2);s.ownedPassives.put("fork",1);
        s.skillMastery.put("fire_bolt",27L);s.cooldowns.put("fire_bolt",new SavedCooldown(3,1));
        s.support=new SupportProgress(4,7,new ManaguardLedger(23,100,25,19),Map.of("emanatism",1.5));
        s.acquisition=new AcquisitionProgress(Set.of("fire_bolt"),Map.of("goblin_scrapper",9),0);repo().save(s);return s;
    }
    @Test void respecRefundsAllocatedPointsAndPreservesEveryDebtAndRewardCounter(){
        var before=seed();var s=service();assertTrue(s.respecAttributes(player,0,"respec").success());var after=repo().load(player).state();
        assertEquals(10,after.attributes.get("DEX"));assertEquals(13,after.unspentAttributePoints);assertEquals(2,after.pendingLevelUpPoints);
        assertEquals(before.cooldowns,after.cooldowns);assertEquals(before.support,after.support);assertEquals(before.rewards,after.rewards);
        assertEquals(before.acquisition,after.acquisition);assertEquals(before.skillMastery,after.skillMastery);assertEquals(before.learnedSkills,after.learnedSkills);assertEquals(before.ownedPassives,after.ownedPassives);
        assertEquals(before.currentXp,after.currentXp);assertEquals(before.level,after.level);assertTrue(s.respecAttributes(player,1,"again").success());assertEquals(13,repo().load(player).state().unspentAttributePoints);
    }
    @Test void operatorLevelAddsOnlyNewLevelPointsAndResetRefundsExistingAllocations(){
        var before=seed();
        try(var s=service()){
            assertTrue(s.setOperatorLevel(player,99,"level-99").success());
            var atCap=repo().load(player).state();
            assertEquals(99,atCap.level);
            assertEquals(new com.inigmasgames.hytalerpg.ui.CharacterXpProjectionService().levelStartXp(99),atCap.currentXp);
            assertEquals(493,atCap.unspentAttributePoints);
            assertEquals(492,atCap.pendingLevelUpPoints);
            assertEquals(20,atCap.attributes.get("DEX"));
            assertEquals(atCap.revision,s.setOperatorLevel(player,99,"again").revision());
            assertEquals(493,repo().load(player).state().unspentAttributePoints);
            assertTrue(s.respecAttributes(player,atCap.revision,"reset").success());
            var reset=repo().load(player).state();
            assertEquals(10,reset.attributes.get("DEX"));
            assertEquals(503,reset.unspentAttributePoints);
            assertEquals(492,reset.pendingLevelUpPoints);
            assertEquals(before.rewards,reset.rewards);
            assertEquals(before.acquisition,reset.acquisition);
            assertTrue(s.setOperatorLevel(player,1,"level-1").success());
            var lowered=repo().load(player).state();
            assertEquals(1,lowered.level);assertEquals(0,lowered.currentXp);
            assertEquals(13,lowered.unspentAttributePoints);assertEquals(2,lowered.pendingLevelUpPoints);
        }
        try(var reopened=service()){
            var loaded=reopened.getPresentationView(player).state();
            assertEquals(1,loaded.level);assertEquals(13,loaded.unspentAttributePoints);
        }
    }
    @Test void operatorSkillLevelChangesOnlyLearnedBaseRankAndPersistsAcrossReload(){
        var before=seed();
        before.skillMastery.put("fire_bolt",1000L);
        before.gearEconomy=new GearEconomyProgress(Map.of("PLAIN_SCRAP/CAMPAIGN",7L),Map.of("quick_slash",5),Map.of());
        repo().save(before);
        try(var s=service()){
            assertTrue(s.setOperatorSkillRank(player,"fire_bolt",20,"qa-rank-20").success());
            assertEquals(20,s.baseSkillRank(player,"fire_bolt"));
            assertTrue(s.setOperatorSkillRank(player,"fire_bolt",1,"qa-rank-1").success());
            assertEquals(1,s.baseSkillRank(player,"fire_bolt"));
            long revision=s.getPresentationView(player).state().revision;
            assertEquals(revision,s.setOperatorSkillRank(player,"fire_bolt",1,"qa-same").revision());
            for(var invalid:List.of(0,21))assertFalse(s.setOperatorSkillRank(player,"fire_bolt",invalid,"qa-range").success());
            assertFalse(s.setOperatorSkillRank(player,"teleport",10,"qa-unlearned").success());
            assertFalse(s.setOperatorSkillRank(player,"missing_skill",10,"qa-unknown").success());
            assertEquals(revision,s.getPresentationView(player).state().revision);
        }
        try(var reopened=service()){
            var after=reopened.getPresentationView(player).state();
            assertEquals(1,reopened.baseSkillRank(player,"fire_bolt"));
            assertEquals(5,reopened.baseSkillRank(player,"quick_slash"));
            assertEquals(before.gearEconomy.materials(),after.gearEconomy.materials());
            assertEquals(before.gearEconomy.receipts(),after.gearEconomy.receipts());
            assertEquals(before.skillMastery,after.skillMastery);
            assertEquals(before.learnedSkills,after.learnedSkills);
            assertEquals(before.cooldowns,after.cooldowns);
            assertEquals(before.rewards,after.rewards);
        }
    }
    @Test void operatorLevelRejectsOverspendingOnDowngradeAndInvalidTargets(){
        var state=RpgPlayerState.create(player);state.level=20;
        state.currentXp=new com.inigmasgames.hytalerpg.ui.CharacterXpProjectionService().levelStartXp(20);
        state.attributes.put("STR",105);repo().save(state);
        try(var s=service()){
            assertFalse(s.setOperatorLevel(player,1,"lower").success());
            assertFalse(s.setOperatorLevel(player,100,"invalid").success());
            assertEquals(20,repo().load(player).state().level);
            assertEquals(105,repo().load(player).state().attributes.get("STR"));
        }
    }
    @Test void levelResetIsAtomicRefundsPointsAndSurvivesReopenWithoutTouchingSkillsOrRewards(){
        var original=seed();
        original.level=99;
        original.currentXp=new com.inigmasgames.hytalerpg.ui.CharacterXpProjectionService().levelStartXp(99);
        repo().save(original);
        try(var s=service()){
            assertTrue(s.resetOperatorLevel(player,original.revision,"reset-level").success());
            var reset=repo().load(player).state();
            assertEquals(1,reset.level);assertEquals(0,reset.currentXp);
            assertEquals(13,reset.unspentAttributePoints);assertEquals(0,reset.pendingLevelUpPoints);
            assertTrue(reset.attributes.values().stream().allMatch(value->value==10));
            assertEquals(original.learnedSkills,reset.learnedSkills);
            assertEquals(original.rewards,reset.rewards);assertEquals(original.cooldowns,reset.cooldowns);
            assertFalse(s.resetOperatorLevel(player,original.revision,"stale").success());
            assertTrue(s.resetOperatorLevel(player,reset.revision,"repeat").success());
            assertEquals(13,repo().load(player).state().unspentAttributePoints);
        }
        try(var s=service()){
            var restored=s.getPresentationView(player).state();
            assertEquals(1,restored.level);assertEquals(0,restored.currentXp);
            assertEquals(13,restored.unspentAttributePoints);
        }
    }
    @Test void earnedRewardStillCommitsAfterOperatorLevelChange(){
        try(var s=service()){
            var previous=new EarnedReward("pre-level-kill",100,1,Map.of(),"ENEMY_DEATH","root","skill","pre-level");
            assertEquals(EarnedRewardStore.Outcome.COMMITTED,s.awardEarned(player,previous).outcome());
            assertTrue(s.setOperatorLevel(player,99,"cap").success());
            var reward=new EarnedReward("post-level-kill",100,1,Map.of(),"ENEMY_DEATH","root","skill","post-level");
            assertEquals(EarnedRewardStore.Outcome.COMMITTED,s.awardEarned(player,reward).outcome());
            var saved=repo().load(player).state();
            assertEquals(99,saved.level);assertEquals(490,saved.unspentAttributePoints);
            assertEquals(new com.inigmasgames.hytalerpg.ui.CharacterXpProjectionService().levelStartXp(99)+100,saved.currentXp);
            assertEquals(2,saved.rewards.sequence());
        }
    }
    @Test void boundedAttributeAllocationIsOnePersistedMutationAndClampsToBalance(){
        var state=RpgPlayerState.create(player);state.unspentAttributePoints=20;state.pendingLevelUpPoints=20;repo().save(state);
        try(var s=service()){
            var allocation=new AttributeAllocationService(s);
            var one=allocation.allocateUpTo(player,com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.STR,1,0,"normal");
            assertTrue(one.mutation().success());assertEquals(1,one.applied());
            var five=allocation.allocateUpTo(player,com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.STR,5,1,"bulk");
            assertTrue(five.mutation().success());assertEquals(5,five.applied());
            assertEquals(16,repo().load(player).state().attributes.get("STR"));
            assertEquals(14,repo().load(player).state().unspentAttributePoints);
            assertFalse(allocation.allocateUpTo(player,com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.STR,5,1,"stale").mutation().success());
            assertFalse(allocation.allocateUpTo(player,com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.STR,6,2,"invalid").mutation().success());
        }
    }
    @Test void boundedAttributeAllocationHandlesPartialZeroCapAndRepeatedRequests(){
        var state=RpgPlayerState.create(player);state.unspentAttributePoints=3;state.pendingLevelUpPoints=3;repo().save(state);
        try(var s=service()){
            var allocation=new AttributeAllocationService(s);
            var partial=allocation.allocateUpTo(player,com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.DEX,5,0,"partial");
            assertTrue(partial.mutation().success());assertEquals(3,partial.applied());
            assertEquals(13,repo().load(player).state().attributes.get("DEX"));
            assertEquals(0,repo().load(player).state().unspentAttributePoints);
            assertFalse(allocation.allocateUpTo(player,com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.DEX,5,1,"zero").mutation().success());
            assertEquals(1,repo().load(player).state().revision);
        }
        var cap=RpgPlayerState.create(player);cap.attributes.put("WIS",Integer.MAX_VALUE-2);
        cap.unspentAttributePoints=20;cap.pendingLevelUpPoints=20;repo().save(cap);
        try(var s=service()){
            var allocation=new AttributeAllocationService(s);
            var limited=allocation.allocateUpTo(player,com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.WIS,5,0,"cap");
            assertTrue(limited.mutation().success());assertEquals(2,limited.applied());
            assertEquals(Integer.MAX_VALUE,repo().load(player).state().attributes.get("WIS"));
            assertEquals(18,repo().load(player).state().unspentAttributePoints);
        }
    }
    @Test void repeatedAndMixedBulkAllocationsPersistWithoutOverspending()throws Exception{
        var state=RpgPlayerState.create(player);state.unspentAttributePoints=20;state.pendingLevelUpPoints=20;repo().save(state);
        try(var s=service()){
            var allocation=new AttributeAllocationService(s);
            long revision=0;
            for(int i=0;i<3;i++){
                var result=allocation.allocateUpTo(player,com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.LUCK,5,revision++,"held-ctrl-"+i);
                assertTrue(result.mutation().success());assertEquals(5,result.applied());
            }
            assertTrue(allocation.allocate(player,com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.STR,revision++,"normal").success());
            assertEquals(4,repo().load(player).state().unspentAttributePoints);
            var last=allocation.allocateUpTo(player,com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.DEX,5,revision,"partial");
            assertTrue(last.mutation().success());assertEquals(4,last.applied());
            assertEquals(0,repo().load(player).state().unspentAttributePoints);
            assertEquals(25,repo().load(player).state().attributes.get("LUCK"));
        }
        var persisted=repo().load(player).state();
        assertEquals(0,persisted.unspentAttributePoints);assertEquals(14,persisted.attributes.get("DEX"));
    }
    @Test void simultaneousBulkRequestsCannotSpendSamePointsTwice()throws Exception{
        var state=RpgPlayerState.create(player);state.unspentAttributePoints=5;state.pendingLevelUpPoints=5;repo().save(state);
        try(var s=service();var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var allocation=new AttributeAllocationService(s);
            var gate=new java.util.concurrent.CountDownLatch(1);
            var tasks=new ArrayList<java.util.concurrent.Future<AttributeAllocationService.AllocationResult>>();
            for(int i=0;i<2;i++){
                final int index=i;
                tasks.add(pool.submit(()->{gate.await();return allocation.allocateUpTo(player,
                        com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.STR,5,0,"race-"+index);}));
            }
            gate.countDown();int applied=0,successes=0;
            for(var task:tasks){var result=task.get();applied+=result.applied();if(result.mutation().success())successes++;}
            assertEquals(1,successes);assertEquals(5,applied);
            assertEquals(0,repo().load(player).state().unspentAttributePoints);
            assertEquals(15,repo().load(player).state().attributes.get("STR"));
        }
    }
    @Test void tenSecondCombatAndPendingGateApplyToAllLoadoutMutations(){
        seed();var s=service();var seconds=new AtomicReference<>(9.999);var pending=new AtomicBoolean();s.configureRespecGuard(id->RespecGate.rejection(seconds.get(),pending.get()));
        assertFalse(s.equipSkill(player,SkillSlot.SKILL01,new SkillId("quick_slash")).success());assertFalse(s.respecAttributes(player,0,"blocked").success());
        seconds.set(10.0);pending.set(true);assertFalse(s.equipSkill(player,SkillSlot.SKILL01,new SkillId("quick_slash")).success());
        pending.set(false);assertTrue(s.equipSkill(player,SkillSlot.SKILL01,new SkillId("quick_slash")).success());
        seconds.set(0.0);assertFalse(s.unequipSkill(player,SkillSlot.SKILL01).success());assertEquals("quick_slash",repo().load(player).state().equippedSkills[0]);
    }
    @Test void respecDoesNotResetChargesOrCreatePointsFromBelowBaseline(){
        var initial=seed();initial.attributes.put("DEX",9);repo().save(initial);var s=service();assertFalse(s.respecAttributes(player,0,"below").success());assertEquals(3,repo().load(player).state().unspentAttributePoints);
    }
    @Test void respecOverflowRejectsWithoutSave(){
        var initial=seed();initial.unspentAttributePoints=Integer.MAX_VALUE;repo().save(initial);assertFalse(service().respecAttributes(player,0,"overflow").success());assertEquals(20,repo().load(player).state().attributes.get("DEX"));
    }
    @Test void uncertainOrdinarySaveKeepsInspectionReadOnlyAndBlocksFurtherAuthority(){
        var memory=new Stage01BTestSupport.InMemoryRepository();var bundle=Stage01BTestSupport.bundle(memory,new Stage01BTestSupport.RecordingTracer());var s=bundle.service();
        assertTrue(s.equipSkill(player,SkillSlot.SKILL01,new SkillId("quick_slash")).success());memory.failSave=true;
        assertFalse(s.equipSkill(player,SkillSlot.SKILL02,new SkillId("fire_bolt")).success());var view=s.getPresentationView(player);
        assertEquals("quick_slash",view.state().equippedSkills[0]);assertNull(view.state().equippedSkills[1]);assertTrue(view.plans().isEmpty());assertTrue(view.warnings().getFirst().contains("READ_ONLY"));
        memory.failSave=false;assertThrows(IllegalStateException.class,()->s.equipSkill(player,SkillSlot.SKILL02,new SkillId("fire_bolt")));
        assertThrows(IllegalStateException.class,()->s.saveCooldowns(player,Map.of()));assertThrows(IllegalStateException.class,()->s.mutateSupport(player,0,p->p));
    }
    @Test void fullBuildRoundTripContainsOnlyStableIdsTopologyAndFixedLayout(){
        var before=seed();var s=service();var edge=LinkEdge.create(LinkNodeId.PASSIVE01,LinkNodeId.SKILL02);
        var transfer=new BuildTransfer(1,"STATIC_SKILL_TREE_V1",Map.of("skill01","quick_slash","skill02","fire_bolt"),Map.of("passive01","fork"),List.of(edge));
        assertTrue(s.importBuild(player,0,transfer.encode(),"import").success());assertEquals(transfer,BuildTransfer.decode(s.exportBuild(player)));
        var raw=JsonParser.parseString(s.exportBuild(player)).getAsJsonObject();assertEquals(Set.of("schemaVersion","layout","skills","passives","edges"),raw.keySet());
        var after=repo().load(player).state();assertEquals(before.acquisition,after.acquisition);assertEquals(before.support,after.support);assertEquals(before.attributes,after.attributes);assertEquals(before.cooldowns,after.cooldowns);
    }
    @Test void importCannotBypassOwnershipEvenWhenDevEntitlementsAreEnabled(){
        var s=service();var transfer=new BuildTransfer(1,"STATIC_SKILL_TREE_V1",Map.of("skill01","fire_bolt"),Map.of(),List.of());
        assertFalse(s.importBuild(player,0,transfer.encode(),"unowned").success());assertNull(s.getPresentationView(player).state().equippedSkills[0]);
    }
    @Test void twoSkillsRequireTwoOwnedPassiveCopies(){
        var initial=seed();initial.ownedPassives.put("potency",1);repo().save(initial);var s=service();
        var transfer=new BuildTransfer(1,"STATIC_SKILL_TREE_V1",Map.of("skill01","quick_slash","skill02","fire_bolt"),Map.of("passive01","potency","passive02","potency"),List.of(LinkEdge.create(LinkNodeId.PASSIVE01,LinkNodeId.SKILL01),LinkEdge.create(LinkNodeId.PASSIVE02,LinkNodeId.SKILL02)));
        assertFalse(s.importBuild(player,0,transfer.encode(),"copies").success());assertEquals(0,repo().load(player).state().revision);
    }
    @Test void invalidForkStrikeImportRollsBackWholeBuild(){
        seed();var s=service();var transfer=new BuildTransfer(1,"STATIC_SKILL_TREE_V1",Map.of("skill01","quick_slash"),Map.of("passive01","fork"),List.of(LinkEdge.create(LinkNodeId.PASSIVE01,LinkNodeId.SKILL01)));
        assertFalse(s.importBuild(player,0,transfer.encode(),"incompatible").success());assertNull(repo().load(player).state().equippedSkills[0]);assertEquals(0,repo().load(player).state().revision);
    }
    @Test void unknownAndInjectedProgressionFieldsAreRejected(){
        seed();var s=service();var transfer=new BuildTransfer(1,"STATIC_SKILL_TREE_V1",Map.of("skill01","invented_skill"),Map.of(),List.of());
        assertFalse(s.importBuild(player,0,transfer.encode(),"unknown").success());
        for(String key:List.of("currentXp","ownedPassives","attributes","acquisition","rewards")){
            var raw=JsonParser.parseString(s.exportBuild(player)).getAsJsonObject();raw.addProperty(key,100);assertThrows(IllegalArgumentException.class,()->BuildTransfer.decode(raw.toString()));
        }
        assertThrows(IllegalArgumentException.class,()->BuildTransfer.decode(" ".repeat(16385)));
    }
    @Test void currentSchemaCannotSilentlyInitializeMissingAcquisitionFields()throws Exception{
        for(String key:List.of("meaningfulSkills","pity","spentInsight")){
            var json=new Gson().toJsonTree(RpgPlayerState.create(player)).getAsJsonObject();json.getAsJsonObject("acquisition").remove(key);
            Files.createDirectories(repo().path(player).getParent());Files.writeString(repo().path(player),json.toString());assertThrows(IllegalStateException.class,()->repo().load(player));
        }
    }
    void legacyWrite(RpgPlayerState state)throws Exception{
        var raw=new Gson().toJsonTree(state).getAsJsonObject();raw.addProperty("schemaVersion",8);raw.remove("difficulty");raw.remove("gearEconomy");raw.remove("acquisition");
        Files.createDirectories(repo().path(player).getParent());Files.writeString(repo().path(player),raw.toString());
    }
    @ParameterizedTest @EnumSource(FileEarnedRewardStore.Boundary.class)
    void oldV1ReceiptAndPendingHashesSurviveSchemaNineAtEveryCrashBoundary(FileEarnedRewardStore.Boundary boundary)throws Exception{
        var old=new AtomicReference<>(RpgPlayerState.create(player));old.get().learnedSkills.add("quick_slash");old.get().ownedPassives.put("potency",2);legacyWrite(old.get());
        var reward=Stage12AcquisitionTest.death("old-event");
        var authority=new EarnedRewardStore.Authority(){
            public RewardCheckpoint current(){var s=old.get();return new RewardCheckpoint(s.currentXp,s.level,s.pendingLevelUpPoints,s.unspentAttributePoints,s.skillMastery,s.rewards);}
            public void commit(RewardIntent intent){var next=old.get().copy();intent.after().applyTo(next);next.revision++;try{legacyWrite(next);}catch(Exception e){throw new RuntimeException(e);}old.set(next);}
        };
        String oldCheckpointJson=new Gson().toJson(authority.current());assertFalse(oldCheckpointJson.contains("acquisition"));assertFalse(new Gson().toJson(reward).contains("progression"));
        var intent=RewardIntent.create(player,reward,authority.current());assertEquals(RewardIntent.digest("rpg-earned-v1\n"+player+"\n"+new Gson().toJson(reward)+"\n"+oldCheckpointJson),intent.hash());
        var store=new FileEarnedRewardStore(temp.resolve("rewards"),at->{if(at==boundary)throw new IllegalStateException("old-process-crash");});
        assertThrows(IllegalStateException.class,()->store.award(player,reward,authority));String beforeMigration=Files.readString(repo().path(player));
        var s=service();assertEquals(EarnedRewardStore.Outcome.DUPLICATE,s.awardEarned(player,reward).outcome());var after=repo().load(player).state();
        assertEquals(RpgPlayerState.CURRENT_SCHEMA,after.schemaVersion);assertEquals(10,after.currentXp);assertEquals(1,after.rewards.sequence());assertEquals(AcquisitionProgress.INITIAL,after.acquisition);
        assertEquals(Set.of("quick_slash"),after.learnedSkills);assertEquals(Map.of("potency",2),after.ownedPassives);
        assertEquals(beforeMigration,Files.readString(repo().path(player).resolveSibling(player+".json.schema-v8.bak")));
        String key=RewardIntent.digest("old-event");var receipt=temp.resolve("rewards").resolve(player.toString()).resolve("receipts").resolve(key.substring(0,2)).resolve(key+".json");
        String saved=Files.readString(receipt);assertFalse(saved.contains("acquisition"));assertFalse(saved.contains("progression"));assertTrue(saved.contains(intent.hash()));
    }
    @Test void newCheckpointCannotUseLegacyWildcardToHideAcquisitionRollback(){
        var original=RewardCheckpoint.of(RpgPlayerState.create(player));var changed=RpgPlayerState.create(player);changed.acquisition=new AcquisitionProgress(Set.of("quick_slash"),Map.of(),0);
        assertFalse(original.matches(RewardCheckpoint.of(changed)));assertNotEquals(original,RewardCheckpoint.of(changed));
    }
    @Test void frozenWisdomAndLearningSurviveDeathCursorCrashAndDoNotReroll()throws Exception{
        var catalog=Stage12AcquisitionTest.verifiedFixture();var s=service(catalog,new FileEarnedRewardStore(temp.resolve("rewards")));var world=UUID.randomUUID();var enemy=UUID.randomUUID();
        var spawn=new EnemyRewardRegistry.Spawn(world,enemy,"FixtureGoblin","goblin_scrapper","fixture/biome",5,ProgressionMath.Rank.COMMON,ProgressionMath.Rarity.ORDINARY,"explicit_test_fixture",0);
        var ledger=new EncounterContributions();assertTrue(ledger.begin(spawn));assertTrue(ledger.damage(world,enemy,player,100,0,100,true,1));
        var op=new LearningSources.Opportunity("quick_slash","goblin_scrapper",ProgressionMath.AcquisitionRarity.COMMON,200);
        var member=new EncounterContributions.Participant(player,world,Vec3.ZERO,1,true,null,op);
        var plan=ledger.death(world,enemy,Vec3.ZERO,2,PartyMembershipProvider.apply(world,List.of(member),PartyMembershipProvider.UNAVAILABLE));
        var store=new FileEncounterStore(temp.resolve("encounters"));store.create(spawn);store.save(ledger.snapshot(world,enemy));store.freeze(plan);
        assertEquals(op,store.death(world,enemy).orElseThrow().shares().getFirst().learning());
        store.close();
        var rolls=new AtomicInteger();FileEncounterStore.AwardDelivery deliver=(id,reward,learning)->s.awardGenerated(id,reward.eventId(),before->learning.decide(reward,before,()->{rolls.incrementAndGet();return .3;}));
        var broken=new FileEncounterStore(temp.resolve("encounters"),at->{if(at==FileEncounterStore.Boundary.AFTER_AWARD)throw new IllegalStateException("cursor-crash");});
        assertThrows(IllegalStateException.class,()->broken.drainLearning(8,deliver));assertTrue(repo().load(player).state().learnedSkills.contains("quick_slash"));
        broken.close();
        try(var restored=new FileEncounterStore(temp.resolve("encounters"))){restored.drainLearning(8,deliver);}assertEquals(1,rolls.get());assertEquals(1,repo().load(player).state().rewards.sequence());
    }
}
