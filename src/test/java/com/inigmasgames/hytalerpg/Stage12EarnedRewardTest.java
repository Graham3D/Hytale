package com.inigmasgames.hytalerpg;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.diagnostics.*;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.links.*;
import com.inigmasgames.hytalerpg.progress.*;
import com.inigmasgames.hytalerpg.combat.cooldown.SavedCooldown;
import com.inigmasgames.hytalerpg.ui.CharacterXpProjectionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class Stage12EarnedRewardTest {
    @TempDir Path temporary;
    private final UUID player=UUID.randomUUID();
    private static final RpgCatalog CATALOG=RpgCatalog.loadCanonical();
    private final Stage01BTestSupport.RecordingTracer trace=new Stage01BTestSupport.RecordingTracer();
    private FileRpgPlayerStateRepository repository(){return new FileRpgPlayerStateRepository(temporary.resolve("players"));}
    private Path rewards(){return temporary.resolve("earned-rewards");}
    private RpgLoadoutService service(){return service(repository(),ignored->{});}
    private RpgLoadoutService service(RpgPlayerStateRepository repository,Consumer<FileEarnedRewardStore.Boundary> fault){
        var compatibility=new CompatibilityService();var graph=new RpgLinkGraphService(CATALOG,compatibility);
        var loadouts=new RpgLoadoutService(CATALOG,repository,graph,new LinkCompiler(CATALOG,graph,compatibility),
                new OwnershipEntitlementPolicy(true),trace);
        loadouts.configureEarnedRewards(new FileEarnedRewardStore(rewards(),fault));return loadouts;
    }
    private EarnedReward reward(String event,long xp){return new EarnedReward(event,xp,2,Map.of("fire_bolt",1L),"ENEMY_DEATH","root-1","skill-1","correlation-1");}
    private RpgPlayerState state(){return repository().load(player).state();}
    private Path pending(){return rewards().resolve(player.toString()).resolve("pending.json");}
    private Path head(){return rewards().resolve(player.toString()).resolve("head.json");}
    private Path receipt(String event){var hash=RewardIntent.digest(event);return rewards().resolve(player.toString()).resolve("receipts").resolve(hash.substring(0,2)).resolve(hash+".json");}

    @Test void earnedThresholdAwardsFiveOfBothPointCountersAndSameAuthorityXp(){
        var service=service();var result=service.awardEarned(player,reward("death1",100));var state=state();
        assertEquals(EarnedRewardStore.Outcome.COMMITTED,result.outcome());assertEquals(100,state.currentXp);
        assertEquals(2,state.level);assertEquals(5,state.pendingLevelUpPoints);assertEquals(5,state.unspentAttributePoints);
        assertEquals(2,state.rewards.insight());assertEquals(1,state.skillMastery.get("fire_bolt"));
        assertEquals(1,state.rewards.sequence());assertEquals(result.receiptHash(),state.rewards.lastReceiptHash());
        assertEquals(100,service.getPresentationView(player).state().currentXp);assertFalse(Files.exists(pending()));
    }
    @Test void multiLevelAwardIsOneAtomicTransaction(){
        service().awardEarned(player,reward("death1",1900));var state=state();assertEquals(5,state.level);
        assertEquals(20,state.unspentAttributePoints);assertEquals(20,state.pendingLevelUpPoints);assertEquals(1,state.rewards.sequence());
    }
    @Test void capPreservesOverflowAndDoesNotGrantFurtherPoints(){
        var state=RpgPlayerState.create(player);state.currentXp=new CharacterXpProjectionService().levelStartXp(99);state.level=99;
        state.unspentAttributePoints=490;state.pendingLevelUpPoints=100;repository().save(state);
        service().awardEarned(player,reward("cap",1000));var after=state();
        assertEquals(state.currentXp+1000,after.currentXp);assertEquals(99,after.level);
        assertEquals(490,after.unspentAttributePoints);assertEquals(100,after.pendingLevelUpPoints);
    }
    @Test void masteryOnlyAwardDoesNotInventCharacterXp(){
        var award=new EarnedReward("root1",0,0,Map.of("quick_slash",1L),"MEANINGFUL_MANUAL_ROOT","root1","skill1","cor1");
        service().awardEarned(player,award);assertEquals(0,state().currentXp);assertEquals(1,state().skillMastery.get("quick_slash"));assertEquals(0,state().rewards.insight());
    }
    @Test void insightOnlyAwardUsesSameCheckpoint(){
        service().awardEarned(player,new EarnedReward("insight1",0,15,Map.of(),"BOSS","","","cor"));
        assertEquals(15,state().rewards.insight());assertEquals(0,state().currentXp);assertTrue(state().skillMastery.isEmpty());
    }
    @Test void duplicateReceiptSurvivesLaterAwardsAndRestart(){
        var s=service();s.awardEarned(player,reward("first",10));s.awardEarned(player,reward("second",20));s.awardEarned(player,reward("third",30));
        var result=service().awardEarned(player,reward("first",10));
        assertEquals(EarnedRewardStore.Outcome.DUPLICATE,result.outcome());assertEquals(1,result.sequence());
        assertEquals(60,state().currentXp);assertEquals(3,state().rewards.sequence());assertEquals(6,state().rewards.insight());
    }
    @Test void duplicateDoesNotWriteThePlayerAgain(){
        var repo=new Stage01BTestSupport.InMemoryRepository();var s=service(repo,ignored->{});
        s.awardEarned(player,reward("same",10));int saves=repo.saves;
        assertEquals(EarnedRewardStore.Outcome.DUPLICATE,s.awardEarned(player,reward("same",10)).outcome());assertEquals(saves,repo.saves);
    }
    @Test void reusedEventWithChangedPayloadFailsInsteadOfAwardingDifference(){
        var s=service();s.awardEarned(player,reward("same",10));
        assertTrue(assertThrows(IllegalStateException.class,()->s.awardEarned(player,reward("same",20))).getMessage().contains("PAYLOAD_CONFLICT"));
        assertEquals(10,state().currentXp);assertEquals(1,state().rewards.sequence());assertFalse(Files.exists(pending()));
    }
    @Test void stableEventCanRewardDifferentEligiblePlayersExactlyOnceEach(){
        var s=service();UUID other=UUID.randomUUID();s.awardEarned(player,reward("enemy",10));s.awardEarned(other,reward("enemy",10));
        s.awardEarned(player,reward("enemy",10));assertEquals(10,state().currentXp);assertEquals(10,repository().load(other).state().currentXp);
    }
    @Test void auditPreservesRootSkillAndCorrelationWithNoFabricatedCombatEvents(){
        var s=service();s.awardEarned(player,reward("death",10));s.awardEarned(player,reward("death",10));
        var records=trace.records.stream().filter(r->r.eventType()==RpgTraceEventType.PROGRESSION_REWARD_COMMITTED).toList();
        assertEquals(1,records.size());var record=records.getFirst();assertEquals("correlation-1",record.correlationId());
        assertEquals("root-1",record.details().get("rootCastId"));assertEquals("skill-1",record.details().get("skillInstanceId"));
        assertTrue((boolean)record.details().get("playerPersisted"));
        assertEquals(1,trace.records.stream().filter(r->r.eventType()==RpgTraceEventType.PROGRESSION_REWARD_DUPLICATE).count());
        assertFalse(trace.records.stream().anyMatch(r->r.eventType().name().startsWith("DAMAGE_")||r.eventType()==RpgTraceEventType.SKILL_COMMITTED));
    }
    @Test void awardPreservesOwnershipTopologyCooldownDebtSupportAndAttributesWithoutLoadoutNotifications(){
        var s=service();assertTrue(s.equipSkill(player,SkillSlot.SKILL02,new SkillId("fire_bolt")).success());
        assertTrue(s.equipPassive(player,PassiveSlot.PASSIVE06,new PassiveId("fork")).success());
        assertTrue(s.link(player,LinkNodeId.PASSIVE06,LinkNodeId.SKILL02).success());
        s.saveCooldowns(player,Map.of("fire_bolt",new SavedCooldown(4,1.2,List.of(new SavedCooldown.Queued(5,1.2)))));
        s.mutateSupport(player,0,p->p.toggle("haste",3,true));var before=state();trace.records.clear();
        AtomicInteger loadout=new AtomicInteger(),projection=new AtomicInteger();s.addLoadoutMutationListener(ignored->loadout.incrementAndGet());s.addMutationListener(ignored->projection.incrementAndGet());
        s.awardEarned(player,reward("progress",100));var after=state();
        assertArrayEquals(before.equippedSkills,after.equippedSkills);assertArrayEquals(before.equippedPassives,after.equippedPassives);
        assertEquals(before.linkEdges(),after.linkEdges());assertEquals(before.ownedPassives,after.ownedPassives);assertEquals(before.learnedSkills,after.learnedSkills);
        assertEquals(before.cooldowns,after.cooldowns);assertEquals(before.support,after.support);assertEquals(before.attributes,after.attributes);
        assertEquals(0,loadout.get());assertEquals(0,projection.get());assertFalse(trace.records.stream().anyMatch(r->r.eventType()==RpgTraceEventType.COMPILE_BEGIN));
    }
    @ParameterizedTest @EnumSource(FileEarnedRewardStore.Boundary.class)
    void interruptedEachWriteBoundaryRecoversExactlyOnce(FileEarnedRewardStore.Boundary boundary){
        AtomicBoolean armed=new AtomicBoolean(true);var s=service(repository(),point->{if(point==boundary&&armed.getAndSet(false))throw new IllegalStateException("INJECTED_"+point);});
        assertThrows(IllegalStateException.class,()->s.awardEarned(player,reward("crash",100)));
        assertEquals(boundary==FileEarnedRewardStore.Boundary.AFTER_INTENT?0:100,state().currentXp);
        var fresh=service();assertEquals(100,fresh.getPresentationView(player).state().currentXp);
        assertEquals(EarnedRewardStore.Outcome.DUPLICATE,fresh.awardEarned(player,reward("crash",100)).outcome());
        assertEquals(100,state().currentXp);assertEquals(5,state().unspentAttributePoints);assertEquals(1,state().rewards.sequence());assertFalse(Files.exists(pending()));
    }
    @Test void sameSessionRecoversBeforeOtherMutationsCanAlterPendingPointSnapshot(){
        AtomicBoolean armed=new AtomicBoolean(true);var s=service(repository(),point->{if(point==FileEarnedRewardStore.Boundary.AFTER_HEAD&&armed.getAndSet(false))throw new IllegalStateException("crash");});
        assertThrows(IllegalStateException.class,()->s.awardEarned(player,reward("partial",100)));
        var view=s.getPresentationView(player);assertFalse(Files.exists(pending()));
        assertTrue(s.mutateProgress(player,view.state().revision,"allocate",p->{p.unspentAttributePoints--;p.pendingLevelUpPoints--;p.attributes.merge("STR",1,Integer::sum);}).success());
        assertEquals(4,state().unspentAttributePoints);assertEquals(EarnedRewardStore.Outcome.DUPLICATE,s.awardEarned(player,reward("partial",100)).outcome());
    }
    @Test void failedPlayerWriteFreezesHolderUntilRestartThenReplaysDurableIntent(){
        var memory=new Stage01BTestSupport.InMemoryRepository();var s=service(memory,ignored->{});s.getPresentationView(player);memory.failSave=true;
        assertThrows(IllegalStateException.class,()->s.awardEarned(player,reward("failed",100)));assertTrue(Files.exists(pending()));
        memory.failSave=false;assertThrows(IllegalStateException.class,()->s.getPresentationView(player));
        var fresh=service(memory,ignored->{});assertEquals(100,fresh.getPresentationView(player).state().currentXp);
        assertEquals(EarnedRewardStore.Outcome.DUPLICATE,fresh.awardEarned(player,reward("failed",100)).outcome());assertEquals(1,memory.saves);
    }
    @Test void uncertainExceptionAfterPlayerReplacementDoesNotPayAgainOnRestart(){
        var disk=repository();AtomicBoolean fail=new AtomicBoolean(true);AtomicInteger writes=new AtomicInteger();
        RpgPlayerStateRepository uncertain=new RpgPlayerStateRepository(){
            public LoadResult load(UUID id){return disk.load(id);}public void save(RpgPlayerState state){disk.save(state);writes.incrementAndGet();if(fail.getAndSet(false))throw new IllegalStateException("after actual save");}
        };
        var s=service(uncertain,ignored->{});assertThrows(IllegalStateException.class,()->s.awardEarned(player,reward("uncertain",100)));
        assertEquals(100,state().currentXp);assertThrows(IllegalStateException.class,()->s.getPresentationView(player));
        var fresh=service(uncertain,ignored->{});assertEquals(100,fresh.getPresentationView(player).state().currentXp);
        assertEquals(1,writes.get());assertEquals(EarnedRewardStore.Outcome.DUPLICATE,fresh.awardEarned(player,reward("uncertain",100)).outcome());
    }
    @Test void restoringOnlyOldPlayerFileCannotReplayOldOrNewReward(){
        var before=RpgPlayerState.create(player);repository().save(before);service().awardEarned(player,reward("paid",100));repository().save(before);
        assertTrue(assertThrows(IllegalStateException.class,()->service().getPresentationView(player)).getMessage().contains("HEAD_MISMATCH"));assertEquals(0,state().currentXp);
    }
    @Test void missingHeadWithAdvancedPlayerFailsClosed()throws Exception{
        service().awardEarned(player,reward("paid",100));Files.delete(head());assertThrows(IllegalStateException.class,()->service().getPresentationView(player));assertEquals(100,state().currentXp);
    }
    @Test void corruptedPendingIsNotDiscardedOrReplaced()throws Exception{
        var s=service(repository(),p->{if(p==FileEarnedRewardStore.Boundary.AFTER_INTENT)throw new IllegalStateException("fault");});
        assertThrows(IllegalStateException.class,()->s.awardEarned(player,reward("torn",100)));Files.writeString(pending(),"{torn");
        assertThrows(IllegalStateException.class,()->service().getPresentationView(player));assertEquals("{torn",Files.readString(pending()));assertEquals(0,state().currentXp);
    }
    @Test void corruptReceiptCannotBeTreatedAsUnseenEvent()throws Exception{
        service().awardEarned(player,reward("paid",100));Files.writeString(receipt("paid"),"{}");
        assertThrows(IllegalStateException.class,()->service().awardEarned(player,reward("paid",100)));assertEquals(100,state().currentXp);
    }
    @Test void corruptedHeadFailsClosed()throws Exception{
        service().awardEarned(player,reward("paid",100));Files.writeString(head(),"{}");assertThrows(IllegalStateException.class,()->service().getPresentationView(player));
    }
    @Test void oversizedRewardFileFailsBeforeParsing()throws Exception{
        Files.createDirectories(head().getParent());Files.writeString(head()," ".repeat(65537));assertThrows(IllegalStateException.class,()->service().getPresentationView(player));
    }
    @Test void foreignPlayerPendingCannotBeApplied()throws Exception{
        var s=service(repository(),p->{if(p==FileEarnedRewardStore.Boundary.AFTER_INTENT)throw new IllegalStateException("fault");});
        assertThrows(IllegalStateException.class,()->s.awardEarned(player,reward("foreign",100)));UUID other=UUID.randomUUID();Path target=rewards().resolve(other.toString()).resolve("pending.json");
        Files.createDirectories(target.getParent());Files.copy(pending(),target);assertThrows(IllegalStateException.class,()->service().getPresentationView(other));assertEquals(0,repository().load(other).state().currentXp);
    }
    @Test void alteredPlayerWhilePendingFailsInsteadOfRecomputingReward()throws Exception{
        var s=service(repository(),p->{if(p==FileEarnedRewardStore.Boundary.AFTER_INTENT)throw new IllegalStateException("fault");});
        assertThrows(IllegalStateException.class,()->s.awardEarned(player,reward("pending",100)));var changed=state();changed.unspentAttributePoints=9;repository().save(changed);
        assertThrows(IllegalStateException.class,()->service().getPresentationView(player));assertEquals(0,state().currentXp);assertEquals(9,state().unspentAttributePoints);assertTrue(Files.exists(pending()));
    }
    @Test void orphanTemporaryFileIsNeverARewardIntent()throws Exception{
        Files.createDirectories(pending().getParent());Files.writeString(pending().resolveSibling("pending.json.orphan.tmp"),"bad partial data");
        service().awardEarned(player,reward("new",10));assertEquals(10,state().currentXp);assertTrue(Files.exists(pending().resolveSibling("pending.json.orphan.tmp")));
    }
    @Test void concurrentDuplicateCallsSerializeAtExistingAuthority()throws Exception{
        var s=service();s.getPresentationView(player);try(var pool=Executors.newFixedThreadPool(6)){
            var futures=new ArrayList<Future<EarnedRewardStore.Result>>();for(int i=0;i<24;i++)futures.add(pool.submit(()->s.awardEarned(player,reward("concurrent",100))));
            int committed=0;for(var f:futures)if(f.get(10,TimeUnit.SECONDS).outcome()==EarnedRewardStore.Outcome.COMMITTED)committed++;
            assertEquals(1,committed);
        }assertEquals(100,state().currentXp);assertEquals(1,state().rewards.sequence());
    }
    @Test void independentStaleAuthorityCannotOverwriteNewerCommittedPlayer(){
        var first=service();var stale=service();first.getPresentationView(player);stale.getPresentationView(player);
        first.awardEarned(player,reward("first",100));assertThrows(IllegalStateException.class,()->stale.awardEarned(player,reward("stale",100)));
        assertEquals(100,state().currentXp);assertEquals(1,state().rewards.sequence());
    }
    @Test void genericProgressMutationCannotForgeRewardCheckpoint(){
        var s=service();var before=s.getPresentationView(player);var result=s.mutateProgress(player,before.state().revision,"forged",p->p.rewards=new RewardLedger(0,"",1000));
        assertFalse(result.success());assertEquals(0,s.getPresentationView(player).state().rewards.insight());
    }
    @Test void rewardStoreMustBeExplicitAndConfiguredOnlyOnceBeforeLoad(){
        var b=Stage01BTestSupport.bundle();assertThrows(IllegalStateException.class,()->b.service().awardEarned(player,reward("no store",10)));
        b.service().getPresentationView(player);assertThrows(IllegalStateException.class,()->b.service().configureEarnedRewards(new FileEarnedRewardStore(rewards())));
        var s=service();assertThrows(IllegalStateException.class,()->s.configureEarnedRewards(new FileEarnedRewardStore(rewards())));
    }
    @Test void invalidPayloadsAndUnknownMasteryNeverCreatePending(){
        assertThrows(IllegalArgumentException.class,()->new EarnedReward("",1,0,Map.of(),"reason","","","cor"));
        assertThrows(IllegalArgumentException.class,()->new EarnedReward("id",-1,0,Map.of(),"reason","","","cor"));
        assertThrows(IllegalArgumentException.class,()->new EarnedReward("id",0,0,Map.of(),"reason","","","cor"));
        assertThrows(IllegalArgumentException.class,()->new EarnedReward("id",0,0,Map.of("fire_bolt",0L),"reason","","","cor"));
        assertThrows(IllegalArgumentException.class,()->new RewardLedger(0,"wrong",0));assertThrows(IllegalArgumentException.class,()->new RewardLedger(1,"",0));
        assertThrows(IllegalArgumentException.class,()->service().awardEarned(player,new EarnedReward("unknown",0,0,Map.of("fake_skill",1L),"reason","","","cor")));
        assertFalse(Files.exists(pending()));
    }
    @Test void checkedXpOverflowLeavesNoIntent(){
        var state=RpgPlayerState.create(player);state.currentXp=Long.MAX_VALUE;state.level=99;repository().save(state);
        assertThrows(ArithmeticException.class,()->service().awardEarned(player,reward("overflow",1)));assertFalse(Files.exists(pending()));assertEquals(Long.MAX_VALUE,state().currentXp);
    }
    @Test void checkedPointOverflowLeavesNoIntent(){
        var state=RpgPlayerState.create(player);state.unspentAttributePoints=Integer.MAX_VALUE;repository().save(state);
        assertThrows(ArithmeticException.class,()->service().awardEarned(player,reward("overflow",100)));assertFalse(Files.exists(pending()));assertEquals(0,state().currentXp);
    }
    @Test void checkedInsightOverflowLeavesNoIntent(){
        var state=RpgPlayerState.create(player);state.rewards=new RewardLedger(0,"",Long.MAX_VALUE);repository().save(state);
        assertThrows(ArithmeticException.class,()->service().awardEarned(player,reward("overflow",10)));assertFalse(Files.exists(pending()));
    }
    @Test void checkedMasteryOverflowLeavesNoIntent(){
        var state=RpgPlayerState.create(player);state.skillMastery.put("fire_bolt",Long.MAX_VALUE);repository().save(state);
        assertThrows(ArithmeticException.class,()->service().awardEarned(player,reward("overflow",10)));assertFalse(Files.exists(pending()));
    }
    @Test void inconsistentLegacyXpLevelRequiresReviewWithoutAutomaticReset(){
        var state=RpgPlayerState.create(player);state.level=7;state.currentXp=1234;repository().save(state);
        assertTrue(assertThrows(IllegalStateException.class,()->service().awardEarned(player,reward("legacy",10))).getMessage().contains("LEVEL_XP_MISMATCH"));
        assertEquals(7,state().level);assertEquals(1234,state().currentXp);assertFalse(Files.exists(pending()));
    }
    @Test void schemaSevenMigrationPreservesEveryExistingProgressFieldAndImmutableCheckpoint()throws Exception{
        var state=RpgPlayerState.create(player);state.currentXp=80;state.unspentAttributePoints=7;state.pendingLevelUpPoints=3;
        state.learnedSkills.add("fire_bolt");state.ownedPassives.put("potency",2);state.skillMastery.put("fire_bolt",14L);
        state.cooldowns.put("fire_bolt",new SavedCooldown(4,1));state.inactivePassives.put("passive06","UNKNOWN_PASSIVE");
        var old=new Gson().toJsonTree(state).getAsJsonObject();old.addProperty("schemaVersion",7);old.remove("rewards");
        String original=old.toString();Files.createDirectories(repository().path(player).getParent());Files.writeString(repository().path(player),original);
        var loaded=repository().load(player);assertTrue(loaded.migrated());assertEquals(8,loaded.state().schemaVersion);assertEquals(RewardLedger.INITIAL,loaded.state().rewards);
        assertEquals(state.currentXp,loaded.state().currentXp);assertEquals(state.skillMastery,loaded.state().skillMastery);assertEquals(state.cooldowns,loaded.state().cooldowns);
        assertEquals(state.ownedPassives,loaded.state().ownedPassives);assertEquals(state.learnedSkills,loaded.state().learnedSkills);assertEquals(state.inactivePassives,loaded.state().inactivePassives);
        service().awardEarned(player,reward("earned",20));assertEquals(12,state().unspentAttributePoints);assertEquals(8,state().pendingLevelUpPoints);
        assertEquals(original,Files.readString(repository().path(player).resolveSibling(player+".json.schema-v7.bak")));
        assertFalse(repository().load(player).migrated());
    }
    @Test void currentSchemaMissingAnyRewardFieldIsRejectedNotInitialized()throws Exception{
        for(String key:List.of("sequence","lastReceiptHash","insight")){
            var raw=new Gson().toJsonTree(RpgPlayerState.create(player)).getAsJsonObject();raw.getAsJsonObject("rewards").remove(key);
            Files.createDirectories(repository().path(player).getParent());Files.writeString(repository().path(player),raw.toString());
            assertThrows(IllegalStateException.class,()->repository().load(player));
        }
    }
    @Test void eventTextCannotEscapeTheHashedReceiptDirectory(){
        String id="../../outside\\also:bad";service().awardEarned(player,reward(id,10));assertTrue(Files.isRegularFile(receipt(id)));
        assertTrue(receipt(id).normalize().startsWith(rewards()));assertFalse(Files.exists(temporary.resolve("outside")));
    }
    @Test void authorityWhichFailsToApplyCannotAdvanceTheReceiptHead(){
        var checkpoint=RewardCheckpoint.of(RpgPlayerState.create(player));var store=new FileEarnedRewardStore(rewards());
        var broken=new EarnedRewardStore.Authority(){public RewardCheckpoint current(){return checkpoint;}public void commit(RewardIntent intent){}};
        assertThrows(IllegalStateException.class,()->store.award(player,reward("broken",10),broken));assertFalse(Files.exists(head()));assertTrue(Files.exists(pending()));
    }
}
