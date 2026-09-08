package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.summon.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class Stage10CorpseTest {
    @TempDir Path directory;
    static CorpseLedger.Source source(UUID world,CorpseLedger.Rank rank){return new CorpseLedger.Source(UUID.randomUUID(),world,Vec3.ZERO,"Wolf_Black","RPG_Summon_Wolf",rank,103,27,3,false,false,false,false);}
    CorpseLedger ledger(){return new CorpseLedger(new FileCorpseConsumptionStore(directory));}
    static CorpseLedger.Claim claim(CorpseLedger ledger,CorpseLedger.Source source){assertTrue(ledger.observe(source));return ledger.reserve(source.entity(),source.world(),UUID.randomUUID(),"root");}
    @Test void concurrentOwnersCannotClaimSameBody()throws Exception{
        var ledger=ledger();var source=source(UUID.randomUUID(),CorpseLedger.Rank.COMMON);assertTrue(ledger.observe(source));
        try(var executor=Executors.newFixedThreadPool(8)){
            var jobs=new ArrayList<Callable<Boolean>>();for(int i=0;i<32;i++)jobs.add(()->{try{ledger.reserve(source.entity(),source.world(),UUID.randomUUID(),"root");return true;}catch(IllegalStateException rejected){return false;}});
            int wins=0;for(var result:executor.invokeAll(jobs))if(result.get())wins++;assertEquals(1,wins);
        }
    }
    @Test void sameRootClaimIsIdempotentButConsumptionIsOnce(){var ledger=ledger();var claim=claim(ledger,source(UUID.randomUUID(),CorpseLedger.Rank.ELITE));assertSame(claim,ledger.reserve(claim.entity(),claim.source().world(),claim.owner(),claim.root()));assertTrue(ledger.consume(claim));assertFalse(ledger.consume(claim));assertFalse(ledger.release(claim));}
    @Test void durableConsumptionSurvivesRuntimeRemovalAndRepositoryRecreation(){var ledger=ledger();var source=source(UUID.randomUUID(),CorpseLedger.Rank.COMMON);assertTrue(ledger.consume(claim(ledger,source)));ledger.remove(source.entity());assertFalse(ledger.observe(source));assertFalse(ledger().observe(source));}
    @Test void tornReceiptFailsClosed()throws Exception{var source=source(UUID.randomUUID(),CorpseLedger.Rank.COMMON);var path=directory.resolve(source.world().toString()).resolve(source.entity()+".consumed");Files.createDirectories(path.getParent());Files.createFile(path);assertFalse(ledger().observe(source));}
    @Test void twoRepositoryInstancesShareAtomicDecision(){var source=source(UUID.randomUUID(),CorpseLedger.Rank.COMMON);var first=ledger();var second=ledger();var a=claim(first,source);var b=claim(second,source);assertTrue(first.consume(a));assertFalse(second.consume(b));assertFalse(second.release(b));}
    @Test void forgedClaimCannotConsumeOrRelease(){var ledger=ledger();var original=claim(ledger,source(UUID.randomUUID(),CorpseLedger.Rank.COMMON));var forged=new CorpseLedger.Claim(original.nonce(),original.entity(),original.owner(),original.root(),original.source());assertFalse(ledger.consume(forged));assertFalse(ledger.release(forged));assertTrue(ledger.consume(original));}
    @Test void ownerCleanupReleasesOnlyUncommittedClaims(){var ledger=ledger();var source=source(UUID.randomUUID(),CorpseLedger.Rank.COMMON);var claim=claim(ledger,source);ledger.cancelUncommitted(claim.owner());assertTrue(ledger.available(source.entity(),source.world()).isPresent());assertFalse(ledger.consume(claim));}
    @Test void nativeBodyRemovalInvalidatesUncommittedClaim(){var ledger=ledger();var claim=claim(ledger,source(UUID.randomUUID(),CorpseLedger.Rank.COMMON));ledger.remove(claim.entity());assertFalse(ledger.consume(claim));assertEquals(0,ledger.size());}
    @Test void invalidWorldCannotClaim(){var ledger=ledger();var source=source(UUID.randomUUID(),CorpseLedger.Rank.COMMON);ledger.observe(source);assertTrue(ledger.available(source.entity(),UUID.randomUUID()).isEmpty());assertThrows(IllegalStateException.class,()->ledger.reserve(source.entity(),UUID.randomUUID(),UUID.randomUUID(),"root"));}
    @Test void runtimeCapacityIsBounded(){var ledger=ledger();var world=UUID.randomUUID();for(int i=0;i<1024;i++)assertTrue(ledger.observe(source(world,CorpseLedger.Rank.COMMON)));assertFalse(ledger.observe(source(world,CorpseLedger.Rank.COMMON)));}
    @Test void rankRulesAllowEliteButNeverMiniBossOrBoss(){var ledger=ledger();for(var rank:CorpseLedger.Rank.values())assertEquals(Set.of(CorpseLedger.Rank.COMMON,CorpseLedger.Rank.SPECIALIST,CorpseLedger.Rank.ELITE).contains(rank),ledger.observe(source(UUID.randomUUID(),rank)));}
    @Test void playerOwnedProtectedAndStorySourcesRejected(){for(int i=0;i<4;i++){var s=source(UUID.randomUUID(),CorpseLedger.Rank.COMMON);var invalid=new CorpseLedger.Source(s.entity(),s.world(),s.anchor(),s.role(),s.projectionRole(),s.rank(),103,27,3,i==0,i==1,i==2,i==3);assertFalse(ledger().observe(invalid));}}
    @Test void nativeSourceAllowlistRejectsUnknownRoles(){var profiles=CorpseSourceProfiles.load();assertTrue(profiles.find("Wolf_Black").isPresent());assertTrue(profiles.find("Unclassified_Boss").isEmpty());assertEquals(3,profiles.find("Wolf_Black").orElseThrow().attackInterval());}
    @Test void reviveClampsHealthAndPowerBeforeLaterModifiers(){var source=new CorpseLedger.Source(UUID.randomUUID(),UUID.randomUUID(),Vec3.ZERO,"fixture","RPG_Summon_Wolf",CorpseLedger.Rank.ELITE,1000,1000,.3,false,false,false,false);var stats=CorpseLedger.revive(source,100,20);assertEquals(200,stats.maximumHealth());assertEquals(16,stats.hitPower());assertEquals(1,stats.attackInterval());}
    @Test void weakCorpseRetainsSixtyPercentSourceStats(){var stats=CorpseLedger.revive(source(UUID.randomUUID(),CorpseLedger.Rank.COMMON),1000,1000);assertEquals(61.8,stats.maximumHealth(),1e-9);assertEquals(16.2,stats.hitPower(),1e-9);assertEquals(3,stats.attackInterval());}
    @Test void diskFailureCannotTurnAmbiguousCommitBackIntoReusableCorpse(){
        var failing=new CorpseConsumptionStore(){public boolean consumed(UUID w,UUID c){return false;}public boolean consume(CorpseLedger.Claim c){throw new UncheckedIOException(new IOException("fixture"));}};
        var ledger=new CorpseLedger(failing);var claim=claim(ledger,source(UUID.randomUUID(),CorpseLedger.Rank.COMMON));assertThrows(UncheckedIOException.class,()->ledger.consume(claim));assertFalse(ledger.release(claim));assertTrue(ledger.available(claim.entity(),claim.source().world()).isEmpty());
    }
    class Harness extends Stage09SupportRuntimeTest.Harness {
        final CorpseLedger corpses=ledger();final SummonRegistry summons=new SummonRegistry();
        final CorpseLedger.Source corpse=source(world,CorpseLedger.Rank.ELITE);List<SummonRegistry.Lease> leases=List.of();boolean failAfterClaim;
        Harness(){super("revive_fallen");corpses.observe(corpse);}
        public Equipment equipment(){return new Equipment(new Item("fixture","SPELLBOOK",new ItemPowerDescriptor("fixture",Set.of("RPG_WEAPON_MAGIC"),20d,20d)),null);}
        public Validation familyPrerequisites(Stage04SkillProfile p,CompiledSkillPlan plan){return corpses.available(corpse.entity(),world).isPresent()?Validation.pass():Validation.reject("NO_CORPSE");}
        public CommittedTarget captureTarget(Stage04SkillProfile p,CompiledSkillPlan plan,SkillExecutionRequest request){return new CommittedTarget(world,Vec3.ZERO,corpse.anchor(),Vec3.FORWARD,corpse.entity());}
        public void commitConsumable(SkillExecutionContext c){var claim=corpses.reserve(corpse.entity(),world,actor,c.rootCastId());assertTrue(corpses.commit(claim,c.skillInstanceId()));}
        public SkillExecutionResult executeSummon(SkillExecutionContext c){context=c;var claim=corpses.takeCommitted(actor,world,c.rootCastId(),c.skillInstanceId()).orElseThrow();leases=summons.reserve(c,now,claim.source());if(failAfterClaim)throw new IllegalStateException("FIXTURE_POST_COMMIT_FAILURE");return SkillExecutionResult.committed("REVIVE_RESERVED",0,0);}
    }
    @Test void reviveUsesRealCastChargeCooldownAndSnapshot(){var h=new Harness();assertTrue(h.cast().committed());assertEquals(70,h.mana);assertFalse(h.kernel.cooldowns().canActivate(h.actor,"revive_fallen"));var lease=h.leases.getFirst();assertEquals(61.8,lease.maximumHealth(),1e-9);assertEquals(25,lease.expires());assertEquals(3,lease.interval());assertEquals(h.corpse.entity(),lease.context().target().entityId());assertEquals(h.context.rootCastId(),lease.context().snapshot().rootCastId());assertFalse(h.cast().committed());assertEquals(70,h.mana);}
    @Test void missingCorpseRejectsBeforePayment(){var h=new Harness();h.corpses.remove(h.corpse.entity());assertFalse(h.cast().committed());assertEquals(100,h.mana);assertEquals(0,h.summons.size());assertTrue(h.kernel.cooldowns().canActivate(h.actor,"revive_fallen"));}
    @Test void empowermentAppliesAfterReviveCapsWithoutDuplicatingCorpse(){var h=new Harness();h.link("minion_empowerment",PassiveSlot.PASSIVE01);assertTrue(h.cast().committed());assertEquals(1,h.leases.size());var lease=h.leases.getFirst();assertEquals(61.8*1.3,lease.maximumHealth(),1e-9);assertEquals(18.75,lease.expires());double magic=h.context.snapshot().basePower()*h.context.snapshot().derivedStats().magicDamageMultiplier();assertEquals(Math.min(16.2,.8*magic)*1.3,lease.coefficient()*magic,1e-9);assertEquals(70,h.mana);}
    @Test void postClaimFailureDoesNotRefundCommittedSummonCost(){var h=new Harness();h.failAfterClaim=true;var result=h.cast();assertTrue(result.committed());assertEquals(SkillExecutionResult.Status.TERMINATED,result.status());assertEquals(70,h.mana);assertFalse(h.kernel.cooldowns().canActivate(h.actor,"revive_fallen"));assertTrue(h.corpses.available(h.corpse.entity(),h.world).isEmpty());}
    @Test void corpseCannotBeInjectedIntoNonCorpseSummon(){var h=new Stage10SummonTest.Harness();h.cast();assertThrows(IllegalArgumentException.class,()->new SummonRegistry().reserve(h.context,0,source(h.world,CorpseLedger.Rank.COMMON)));}
    @Test void committedAnchorSurvivesNativeBodyRemovalUntilOneRelease(){var ledger=ledger();var claim=claim(ledger,source(UUID.randomUUID(),CorpseLedger.Rank.COMMON));assertTrue(ledger.commit(claim,"instance"));ledger.remove(claim.entity());assertEquals(1,ledger.pendingReleases());assertSame(claim,ledger.takeCommitted(claim.owner(),claim.source().world(),claim.root(),"instance").orElseThrow());assertTrue(ledger.takeCommitted(claim.owner(),claim.source().world(),claim.root(),"instance").isEmpty());}
    @Test void committedPermitRejectsWrongOwnerRootWorldAndInstance(){var ledger=ledger();var claim=claim(ledger,source(UUID.randomUUID(),CorpseLedger.Rank.COMMON));ledger.commit(claim,"instance");assertTrue(ledger.takeCommitted(UUID.randomUUID(),claim.source().world(),claim.root(),"instance").isEmpty());assertTrue(ledger.takeCommitted(claim.owner(),UUID.randomUUID(),claim.root(),"instance").isEmpty());assertTrue(ledger.takeCommitted(claim.owner(),claim.source().world(),"other","instance").isEmpty());assertTrue(ledger.takeCommitted(claim.owner(),claim.source().world(),claim.root(),"other").isEmpty());assertEquals(1,ledger.pendingReleases());}
    @Test void ownerCancellationDropsPermitNotDurableConsumption(){var ledger=ledger();var source=source(UUID.randomUUID(),CorpseLedger.Rank.COMMON);var claim=claim(ledger,source);ledger.commit(claim,"instance");ledger.cancelUncommitted(claim.owner());assertEquals(0,ledger.pendingReleases());ledger.remove(source.entity());assertFalse(ledger().observe(source));}
    @Test void existingInstanceCannotConsumeASecondBody(){var ledger=ledger();var first=claim(ledger,source(UUID.randomUUID(),CorpseLedger.Rank.COMMON));ledger.commit(first,"instance");var source=source(first.source().world(),CorpseLedger.Rank.COMMON);ledger.observe(source);var second=ledger.reserve(source.entity(),source.world(),first.owner(),"another-root");assertFalse(ledger.commit(second,"instance"));assertTrue(ledger.release(second));assertTrue(ledger.available(source.entity(),source.world()).isPresent());}
    @Test void delayedReviveUsesConsumedSourceEvenAfterNativeBodyRemoval(){var h=new Harness();h.link("skill_delay",PassiveSlot.PASSIVE01);h.cast();assertEquals(70,h.mana);assertEquals(1,h.corpses.pendingReleases());h.corpses.remove(h.corpse.entity());h.now=2;h.execution.tickScheduled(h.actor,h);assertEquals(1,h.leases.size());assertEquals(27,h.leases.getFirst().expires());assertEquals(0,h.corpses.pendingReleases());}
    @Test void restartNeverRearmsAnAlreadyConsumedDelayedCorpse(){var ledger=ledger();var source=source(UUID.randomUUID(),CorpseLedger.Rank.COMMON);var claim=claim(ledger,source);ledger.commit(claim,"pending-instance");var restarted=ledger();assertEquals(0,restarted.pendingReleases());assertFalse(restarted.observe(source));}
}
