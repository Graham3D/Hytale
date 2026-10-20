package com.inigmasgames.hytalerpg;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.combat.barrier.ManaguardLedger;
import com.inigmasgames.hytalerpg.combat.cooldown.SavedCooldown;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
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
        var raw=new Gson().toJsonTree(state).getAsJsonObject();raw.addProperty("schemaVersion",8);raw.remove("acquisition");
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
        assertEquals(9,after.schemaVersion);assertEquals(10,after.currentXp);assertEquals(1,after.rewards.sequence());assertEquals(AcquisitionProgress.INITIAL,after.acquisition);
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
