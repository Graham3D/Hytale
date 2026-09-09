package com.inigmasgames.hytalerpg;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.links.*;
import com.inigmasgames.hytalerpg.progress.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class Stage12EncounterPersistenceTest {
    @TempDir Path directory;
    private final UUID world=UUID.randomUUID(),enemy=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
    private static final EnemyRewardRegistry REGISTRY=EnemyRewardRegistry.load();
    private static final RpgCatalog CATALOG=RpgCatalog.loadCanonical();
    private FileEncounterStore current;
    private boolean interrupted;
    @AfterEach void closeStore(){if(current!=null)current.close();}
    private FileEncounterStore store(){if(interrupted){closeStore();current=null;interrupted=false;}if(current==null)current=new FileEncounterStore(directory.resolve("encounters"));return current;}
    private FileEncounterStore restart(){closeStore();current=null;interrupted=false;return store();}
    private FileEncounterStore failing(FileEncounterStore.Boundary at){closeStore();var once=new AtomicBoolean();return current=new FileEncounterStore(directory.resolve("encounters"),boundary->{if(boundary==at&&once.compareAndSet(false,true)){interrupted=true;throw new IllegalStateException("PROCESS_INTERRUPTED");}});}
    private EnemyRewardRegistry.Spawn spawn(UUID id){return REGISTRY.classify(world,id,"Wolf_Black","Default/Zone1_Tier1/Forest_Birch",EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,100).orElseThrow();}
    private EncounterContributions ledger(){var ledger=new EncounterContributions();assertTrue(ledger.restore(store().create(spawn(enemy))));return ledger;}
    private EncounterContributions.Participant member(UUID id){return new EncounterContributions.Participant(id,world,Vec3.ZERO,5,true,"party");}
    private EncounterContributions.DeathPlan plan(){
        var ledger=ledger();assertTrue(ledger.damage(world,enemy,a,100,90,100,true,101));
        assertEquals(1,ledger.heal(world,b,a,5,true,102));store().save(ledger.snapshot(world,enemy));
        return ledger.death(world,enemy,Vec3.ZERO,103,List.of(member(a),member(b)));
    }
    private RpgLoadoutService rewards(){
        var compatible=new CompatibilityService();var graph=new RpgLinkGraphService(CATALOG,compatible);
        var service=new RpgLoadoutService(CATALOG,new FileRpgPlayerStateRepository(directory.resolve("players")),graph,
                new LinkCompiler(CATALOG,graph,compatible),new OwnershipEntitlementPolicy(true),new Stage01BTestSupport.RecordingTracer());
        service.configureEarnedRewards(new FileEarnedRewardStore(directory.resolve("earned-rewards")));return service;
    }
    private RpgPlayerState player(UUID id){return new FileRpgPlayerStateRepository(directory.resolve("players")).load(id).state();}
    private Path file(String kind,UUID id){String key=RewardIntent.digest(world+"/"+id);return directory.resolve("encounters").resolve(kind).resolve(kind.equals("pending")?"":key.substring(0,2)).resolve(key+".json");}
    private void assertPaidOnce(){for(var id:List.of(a,b)){assertEquals(65,player(id).currentXp);assertEquals(1,player(id).rewards.insight());assertEquals(1,player(id).rewards.sequence());}}

    @Test void initialContextSurvivesRestartWithOriginalBiomeAndIdentity(){var first=store().create(spawn(enemy));assertEquals(first,restart().load(world,enemy).orElseThrow());assertEquals(5,first.spawn().level());assertEquals(-1,first.firstCombat());}
    @Test void unknownLoadDoesNotClassifyOrWriteContext(){assertTrue(restart().load(world,enemy).isEmpty());assertFalse(Files.exists(file("contexts",enemy)));}
    @Test void repeatedCreationDoesNotResetContributions(){var ledger=ledger();ledger.damage(world,enemy,a,100,70,100,true,110);var saved=ledger.snapshot(world,enemy);store().save(saved);assertEquals(saved,store().create(spawn(enemy)));}
    @Test void changedBiomeOrSpawnClockIsRejected(){store().create(spawn(enemy));var changed=REGISTRY.classify(world,enemy,"Wolf_Black","Default/Zone3_Tier1/Forest_Fir",EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,101).orElseThrow();assertThrows(IllegalStateException.class,()->store().create(changed));assertEquals(5,restart().load(world,enemy).orElseThrow().spawn().level());}
    @Test void realCreditsRestoreWithoutLastHitAndRetainSupportIndex(){var ledger=ledger();ledger.damage(world,enemy,a,100,80,100,true,101);store().save(ledger.snapshot(world,enemy));var loaded=new EncounterContributions();assertTrue(loaded.restore(restart().load(world,enemy).orElseThrow()));assertEquals(1,loaded.heal(world,b,a,5,true,102));assertEquals(2,loaded.death(world,enemy,Vec3.ZERO,103,List.of(member(a),member(b))).shares().size());}
    @Test void restartDoesNotResetCaptiveFarmClock(){var ledger=ledger();ledger.damage(world,enemy,a,100,50,100,true,101);store().save(ledger.snapshot(world,enemy));var loaded=new EncounterContributions();loaded.restore(restart().load(world,enemy).orElseThrow());assertTrue(loaded.damage(world,enemy,a,100,50,100,true,60200));assertFalse(loaded.masteryEligible(world,enemy,a,5,60200));assertTrue(loaded.damage(world,enemy,a,50,40,100,true,60201));assertFalse(loaded.masteryEligible(world,enemy,a,5,60201));store().save(loaded.snapshot(world,enemy));var again=new EncounterContributions();again.restore(restart().load(world,enemy).orElseThrow());assertFalse(again.masteryEligible(world,enemy,a,5,60201));var fresh=UUID.randomUUID();again.begin(spawn(fresh));assertTrue(again.damage(world,fresh,a,100,90,100,true,60202));assertTrue(again.masteryEligible(world,fresh,a,5,60202));}
    @Test void reloadCannotRefreshExpiredCredit(){var plan=plan();var loaded=new EncounterContributions();loaded.restore(restart().load(world,enemy).orElseThrow());assertTrue(loaded.death(world,enemy,Vec3.ZERO,50000,List.of(member(a),member(b))).shares().isEmpty());assertEquals(2,plan.shares().size());}
    @Test void savingStaleWatermarkIsRejected(){var ledger=ledger();var old=ledger.snapshot(world,enemy);ledger.damage(world,enemy,a,100,80,100,true,101);store().save(ledger.snapshot(world,enemy));assertThrows(IllegalStateException.class,()->store().save(old));}
    @Test void conversionTombstoneSurvivesRestoreAndCannotBeCleared(){var ledger=ledger();var clean=ledger.snapshot(world,enemy);store().disqualify(world,enemy);assertTrue(restart().load(world,enemy).orElseThrow().disqualified());assertThrows(IllegalStateException.class,()->store().save(clean));assertThrows(IllegalStateException.class,()->store().create(spawn(enemy)));}
    @Test void conversionBeforeCapturePermanentlyPreventsClassification(){store().disqualify(world,enemy);store().disqualify(world,enemy);assertThrows(IllegalStateException.class,()->store().create(spawn(enemy)));assertTrue(restart().load(world,enemy).isEmpty());}
    @Test void disqualifiedPlanCannotGrantExistingContributors(){var plan=plan();store().disqualify(world,enemy);assertThrows(IllegalStateException.class,()->store().freeze(plan));assertEquals(0,store().pendingCount());}
    @Test void emptyDeathIsRecordedAndNeverSpawnsRewards(){var ledger=ledger();var plan=ledger.death(world,enemy,Vec3.ZERO,101,List.of());store().freeze(plan);assertEquals(0,store().drain(1,(id,reward)->fail("No recipients")));assertEquals(plan,store().death(world,enemy).orElseThrow());assertEquals(0,store().pendingCount());}
    @Test void deathNeedsPersistedContributionNotOnlyInMemoryHit(){var ledger=ledger();ledger.damage(world,enemy,a,100,90,100,true,101);var plan=ledger.death(world,enemy,Vec3.ZERO,102,List.of(member(a)));assertThrows(IllegalStateException.class,()->store().freeze(plan));}
    @Test void frozenPlanSurvivesRestartAndRejectsRecalculation(){var plan=plan();store().freeze(plan);assertEquals(plan,store().death(world,enemy).orElseThrow());assertEquals(plan,store().freeze(plan));var changed=new EncounterContributions.DeathPlan(plan.spawn(),new Vec3(1,0,0),104,plan.shares());assertThrows(IllegalStateException.class,()->store().freeze(changed));}
    @Test void cannotWriteCreditsOrRecreateAfterDeath(){var plan=plan();var snapshot=restart().load(world,enemy).orElseThrow();store().freeze(plan);assertThrows(IllegalStateException.class,()->store().save(snapshot));assertThrows(IllegalStateException.class,()->store().create(spawn(enemy)));}
    @Test void boundedPartialPartyDeliveryResumesWithoutDuplicating(){store().freeze(plan());var service=rewards();assertEquals(1,store().drain(1,service::awardEarned));assertEquals(1,store().pendingCount());assertEquals(1,store().drain(1,rewards()::awardEarned));assertEquals(0,store().pendingCount());assertPaidOnce();assertEquals(0,store().drain(1,rewards()::awardEarned));}
    @ParameterizedTest @EnumSource(value=FileEncounterStore.Boundary.class,names={"AFTER_FREEZE","AFTER_AWARD","AFTER_CURSOR","AFTER_COMPLETION","AFTER_CLEANUP"})
    void everyDeathCrashBoundaryRecoversExactOnce(FileEncounterStore.Boundary boundary){
        var plan=plan();var failing=failing(boundary);
        assertThrows(IllegalStateException.class,()->{failing.freeze(plan);failing.drain(256,rewards()::awardEarned);});
        assertEquals(plan,store().death(world,enemy).orElseThrow());store().drain(256,rewards()::awardEarned);assertPaidOnce();assertEquals(0,store().pendingCount());
    }
    @Test void interruptedContextCreationRemainsOriginalOnRestart(){assertThrows(IllegalStateException.class,()->failing(FileEncounterStore.Boundary.AFTER_CONTEXT).create(spawn(enemy)));assertEquals(spawn(enemy),restart().load(world,enemy).orElseThrow().spawn());}
    @Test void interruptedExclusionRemainsPermanent(){assertThrows(IllegalStateException.class,()->failing(FileEncounterStore.Boundary.AFTER_DISQUALIFY).disqualify(world,enemy));assertThrows(IllegalStateException.class,()->store().create(spawn(enemy)));}
    @Test void failedPlayerAwardDoesNotAdvanceCursor(){store().freeze(plan());assertThrows(IllegalStateException.class,()->store().drain(1,(id,reward)->{throw new IllegalStateException("PLAYER_UNAVAILABLE");}));assertEquals(1,store().pendingCount());assertEquals(2,store().drain(256,rewards()::awardEarned));assertPaidOnce();}
    @Test void deathAndPlayerLedgersSurviveMultipleRestartsAndReplay(){var plan=plan();store().freeze(plan);store().drain(256,rewards()::awardEarned);assertEquals(plan,store().freeze(plan));assertEquals(0,store().drain(256,rewards()::awardEarned));assertPaidOnce();}
    @Test void pendingQueueCapacityRejectsBeforeNewAward(){
        for(int i=0;i<FileEncounterStore.MAX_PENDING;i++){var id=UUID.randomUUID();var spawn=spawn(id);store().create(spawn);store().freeze(new EncounterContributions.DeathPlan(spawn,Vec3.ZERO,101,List.of()));}
        var spawn=spawn(enemy);store().create(spawn);assertThrows(IllegalStateException.class,()->store().freeze(new EncounterContributions.DeathPlan(spawn,Vec3.ZERO,101,List.of())));
        assertEquals(256,store().pendingCount());assertEquals(0,store().drain(1,(id,reward)->fail()));assertEquals(0,store().pendingCount());
    }
    @Test void mismatchedFileIdentityFailsClosed()throws Exception{store().create(spawn(enemy));var other=UUID.randomUUID();Files.createDirectories(file("contexts",other).getParent());Files.copy(file("contexts",enemy),file("contexts",other));assertThrows(IllegalStateException.class,()->restart().load(world,other));}
    @Test void corruptSnapshotIsRetainedNotReset()throws Exception{store().create(spawn(enemy));Files.writeString(file("contexts",enemy),"{broken");assertThrows(IllegalStateException.class,()->restart().load(world,enemy));assertEquals("{broken",Files.readString(file("contexts",enemy)));}
    @Test void changedChecksumCannotForgeCredit()throws Exception{var plan=plan();Path path=file("contexts",enemy);String old=Files.readString(path);Files.writeString(path,old.replace("\"level\":5","\"level\":99"));assertThrows(IllegalStateException.class,()->store().freeze(plan));}
    @Test void invalidSnapshotShapeRejectedEvenWithUpdatedChecksum()throws Exception{store().create(spawn(enemy));Path path=file("contexts",enemy);var json=JsonParser.parseString(Files.readString(path)).getAsJsonObject();json.getAsJsonObject("payload").remove("disqualified");json.addProperty("checksum",RewardIntent.digest(new Gson().toJson(json.get("payload"))));Files.writeString(path,json.toString());assertThrows(IllegalStateException.class,()->restart().load(world,enemy));}
    @Test void oversizedSnapshotRejectedBeforeParsing()throws Exception{store().create(spawn(enemy));Files.writeString(file("contexts",enemy)," ".repeat(FileEncounterStore.MAX_FILE_BYTES+1));assertThrows(IllegalStateException.class,()->restart().load(world,enemy));}
    @Test void foreignPendingFilenameIsNotDelivered()throws Exception{store().freeze(plan());Files.move(file("pending",enemy),file("pending",UUID.randomUUID()));assertThrows(IllegalStateException.class,()->store().drain(1,(id,reward)->fail()));}
    @Test void tornPendingIsNotDroppedOrDelivered()throws Exception{store().freeze(plan());Files.writeString(file("pending",enemy),"{}");assertThrows(IllegalStateException.class,()->store().drain(1,(id,reward)->fail()));assertTrue(Files.exists(file("pending",enemy)));}
    @Test void orphanTemporaryFilesCannotBecomeRewards()throws Exception{Path pending=directory.resolve("encounters/pending");Files.createDirectories(pending);Files.writeString(pending.resolve("orphan.json.temporary.tmp"),"{}");assertEquals(0,store().pendingCount());assertEquals(0,store().drain(1,(id,reward)->fail()));}
    @Test void concurrentWriterCannotEnterStore()throws Exception{store().pendingCount();closeStore();current=null;try(var channel=FileChannel.open(directory.resolve("encounters/writer.lock"),StandardOpenOption.WRITE);var lock=channel.lock()){assertThrows(IllegalStateException.class,()->store().create(spawn(enemy)));}}
    @Test void malformedAndDuplicateSnapshotCreditsRejected(){var spawn=spawn(enemy);var credit=new EncounterContributions.Credit(a,EncounterContributions.Kind.DAMAGE,101,10);assertThrows(IllegalArgumentException.class,()->new EncounterContributions.Snapshot(spawn,List.of(credit,credit),101,101,101,.9,false));assertThrows(IllegalArgumentException.class,()->new EncounterContributions.Snapshot(spawn,List.of(credit),-1,-1,101,1,false));assertThrows(IllegalArgumentException.class,()->new EncounterContributions.Snapshot(spawn,List.of(),101,100,101,1,false));}
    @Test void restoreNeverOverwritesLiveEncounter(){var ledger=ledger();assertThrows(IllegalStateException.class,()->ledger.restore(restart().load(world,enemy).orElseThrow()));assertEquals(1,ledger.size());}
    @Test void restoredIndicesCleanUpOnUnload(){var plan=plan();var ledger=new EncounterContributions();ledger.restore(restart().load(world,enemy).orElseThrow());ledger.unload(world);assertEquals(0,ledger.size());assertEquals(0,ledger.heal(world,b,a,5,true,104));assertEquals(2,plan.shares().size());}
    @Test void invalidDeathShareCannotForgeXpOrInsight(){var spawn=spawn(enemy);assertThrows(IllegalArgumentException.class,()->new EncounterContributions.DeathPlan(spawn,Vec3.ZERO,101,List.of(new EncounterContributions.Share(a,99999,1,5,1))));assertThrows(IllegalArgumentException.class,()->new EncounterContributions.DeathPlan(spawn,Vec3.ZERO,101,List.of(new EncounterContributions.Share(a,131,15,5,1))));}
    @Test void awardBudgetIsExplicit(){assertThrows(IllegalArgumentException.class,()->store().drain(0,(id,reward)->fail()));assertThrows(IllegalArgumentException.class,()->store().drain(257,(id,reward)->fail()));}
    @Test void snapshotRestoreAdmissionDoesNotPartiallyInstallCredits(){
        var loaded=new EncounterContributions();
        for(int i=0;i<EncounterContributions.MAX_SUPPORT_ENCOUNTERS;i++){
            var spawn=spawn(UUID.randomUUID());assertTrue(loaded.restore(new EncounterContributions.Snapshot(spawn,List.of(new EncounterContributions.Credit(a,EncounterContributions.Kind.DAMAGE,101,10)),101,101,101,.9,false)));
        }
        var blocked=new EncounterContributions.Snapshot(spawn(enemy),List.of(new EncounterContributions.Credit(b,EncounterContributions.Kind.DAMAGE,101,10),new EncounterContributions.Credit(a,EncounterContributions.Kind.DAMAGE,101,10)),101,101,101,.9,false);
        assertFalse(loaded.restore(blocked));assertEquals(64,loaded.size());assertEquals(0,loaded.heal(world,a,b,5,true,102));
    }
    @Test void duplicateDeathShareRejected(){var share=new EncounterContributions.Share(a,65,1,5,2);assertThrows(IllegalArgumentException.class,()->new EncounterContributions.DeathPlan(spawn(enemy),Vec3.ZERO,101,List.of(share,share)));}
    @Test void disqualifiedEmptyDeathPersistsWithoutAward(){store().create(spawn(enemy));store().disqualify(world,enemy);var loaded=new EncounterContributions();loaded.restore(restart().load(world,enemy).orElseThrow());var plan=loaded.death(world,enemy,Vec3.ZERO,101,List.of(member(a)));store().freeze(plan);assertEquals(0,store().drain(1,(id,reward)->fail()));assertTrue(store().death(world,enemy).orElseThrow().shares().isEmpty());}
}
