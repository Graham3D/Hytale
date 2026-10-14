package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.difficulty.*;
import com.inigmasgames.hytalerpg.enemies.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class Me12QuantityRecoveryTest {
    @TempDir Path directory;
    final UUID world=UUID.randomUUID(),player=UUID.randomUUID();
    final Me12Policies policies=Me12Policies.canonical();
    GearDropGenerator generator(){return new GearDropGenerator(GearCatalog.load(),new GearBindings(),GearAffixRuntime.ENABLED);}
    EncounterContributions.DeathPlan plan(){
        var enemy=UUID.randomUUID();
        var context=new EnemyRewardContext(policies.balance().revision(),EnemyRarity.UNIQUE,EnemyRewardContext.Origin.NATURAL,false,8,
                2,1.5,1.35,.10,.15,.15,new Me12Identity.RewardReference(2,policies.digest(),policies.references(),940,8));
        var combat=new EncounterProfileResolver.Resolved(world,enemy,DifficultyId.HELL,"fixture","fixture","role","biome",95,100,10,1,1,MonsterResistanceProfile.NONE,EncounterProfileResolver.Evidence.FIXTURE_ONLY);
        var spawn=new EnemyRewardRegistry.Spawn(world,enemy,"role","role","biome",95,ProgressionMath.Rank.BOSS,ProgressionMath.Rarity.ORDINARY,"fixture",1000,null,combat,context);
        return new EncounterContributions.DeathPlan(spawn,Vec3.ZERO,2000,List.of(new EncounterContributions.Share(player,spawn.rewardXp(95),15,95,1)));
    }
    void initialize(FileEncounterStore store,GearLootService loot,EncounterContributions.DeathPlan plan){
        store.archiveMe12Policies(policies);
        loot.contribute(world,plan.spawn().enemy(),player,new GearClaims.Policy("solo/"+player,1,GearClaims.Mode.SOLO,List.of(player),player,Set.of(),true,false),1500);
        loot.freezeMagicFind(plan.spawn().eventId(),Map.of(player,2.));
    }
    @Test void R03R04R11GeneratorHoldPreservesEveryEntitlementAndExactQualityUntilDependencyReturns(){
        var death=plan();var generator=generator();GearLootService.DeathPicks frozen;List<GearLootService.Loot> held;
        try(var store=new FileEncounterStore(directory)){
            var loot=new GearLootService(store,generator,()->3000,revision->Optional.empty());initialize(store,loot,death);
            held=loot.deliverPicks(death);frozen=store.gearRead("loot-picks",death.spawn().eventId(),GearLootService.DeathPicks.class).orElseThrow();
            assertEquals(frozen.quantity().total(),held.size());assertTrue(held.size()>frozen.decision().succeeded());
            assertTrue(held.stream().allMatch(r->r.state().equals("WAITING_FOR_GENERATOR_REVISION")&&r.result()==null));
            assertEquals(held,loot.deliverPicks(death));
        }
        try(var store=new FileEncounterStore(directory)){
            var loot=new GearLootService(store,generator,()->9000);
            var restored=loot.deliverPicks(death);assertEquals(held.size(),restored.size());
            assertEquals(restored,loot.deliverPicks(death));assertEquals(restored.size(),restored.stream().map(r->r.result().item().identity()).distinct().count());
            for(int i=0;i<restored.size();i++){
                var row=restored.get(i);assertEquals(held.get(i).source(),row.source());assertEquals(held.get(i).seed(),row.seed());assertEquals(2.,row.magicFind());
                assertEquals(generator.generateGuaranteed(row.source(),row.magicFind(),row.seed(),Set.of()),row.result());
            }
            for(var pick:frozen.decision().rolls())if(pick.opportunity()){
                var row=restored.stream().filter(r->r.source().eventId().equals(pick.childEventId())).findFirst().orElseThrow();assertEquals(pick.seed(),row.seed());
            }
        }
    }
    @Test void P05R11CrashAtManifestCursorOrItemPublicationResumesFrozenCountWithoutDuplicating(){
        for(String boundary:List.of("AFTER_loot-picks","AFTER_cursors","AFTER_loot")){
            var death=plan();var generator=generator();var folder=directory.resolve(boundary);
            try(var store=new FileEncounterStore(folder)){
                var loot=new GearLootService(store,generator,()->3000);initialize(store,loot,death);var armed=new AtomicBoolean(true);
                store.configureGearFault(point->{if(point.equals(boundary)&&armed.getAndSet(false))throw new IllegalStateException("PROCESS_LOSS");});
                assertThrows(IllegalStateException.class,()->loot.deliverPicks(death));
            }
            try(var store=new FileEncounterStore(folder)){
                var loot=new GearLootService(store,generator,()->9000);var rows=loot.deliverPicks(death);
                var frozen=store.gearRead("loot-picks",death.spawn().eventId(),GearLootService.DeathPicks.class).orElseThrow();
                assertEquals(frozen.quantity().total(),rows.size());assertEquals(rows,loot.deliverPicks(death));assertEquals(rows.size(),loot.records().size());
                assertEquals(rows.size(),rows.stream().map(r->r.result().item().identity()).distinct().count());
            }
        }
    }
    @Test void R11FullInventoryDisconnectAndNativeSaveAcknowledgementKeepExactItem() {
        var death=plan();var generator=generator();GearLootService.Loot row;
        try(var store=new FileEncounterStore(directory)){
            var loot=new GearLootService(store,generator,()->3000);initialize(store,loot,death);row=loot.deliverPicks(death).getFirst();
            String event=row.source().eventId();long revision=row.allocation().revision();
            assertThrows(IllegalArgumentException.class,()->loot.reservePickup(event,player,revision,3000,false));
            assertEquals(row,loot.inspect(event).orElseThrow());
            assertEquals("PICKUP_PENDING_NATIVE_SAVE",loot.reservePickup(event,player,revision,3000,true).state());
        }
        try(var store=new FileEncounterStore(directory)){
            var loot=new GearLootService(store,generator,()->4000);String event=row.source().eventId();
            assertEquals("PICKUP_PENDING_NATIVE_SAVE",loot.inspect(event).orElseThrow().state());
            var acknowledged=loot.acknowledgePickup(event,player,row.result().item().identity());
            assertEquals("INVENTORY",acknowledged.state());assertEquals(row.result(),acknowledged.result());
            assertEquals(acknowledged,loot.acknowledgePickup(event,player,row.result().item().identity()));
            assertEquals(acknowledged,loot.deliverPicks(death).getFirst());
        }
    }
    @Test void R11MissingPolicyArchiveHoldsGenerationAndResumesAfterExactRestoration()throws Exception{
        var death=plan();var generator=generator();
        try(var store=new FileEncounterStore(directory)){
            var held=new GearLootService(store,generator,()->3000,revision->Optional.empty());initialize(store,held,death);var original=held.deliverPicks(death);
            var file=directory.resolve("me12-policies/content/"+policies.references().getFirst().digest()+".json");var bytes=java.nio.file.Files.readAllBytes(file);java.nio.file.Files.delete(file);
            var restoredGenerator=new GearLootService(store,generator,()->4000);
            assertTrue(restoredGenerator.records().stream().allMatch(row->row.state().equals("WAITING_FOR_POLICY_ARCHIVE")));
            java.nio.file.Files.write(file,bytes);var finished=restoredGenerator.deliverPicks(death);assertEquals(original.size(),finished.size());
            assertTrue(finished.stream().allMatch(row->row.result()!=null&&row.result().item()!=null));
        }
    }
    @Test void R03NoBeneficiaryV2DeathRetainsDenialWithoutInvokingLegacyQuantity(){
        var death=plan();
        try(var store=new FileEncounterStore(directory)){
            store.archiveMe12Policies(policies);var loot=new GearLootService(store,generator(),()->3000);
            var denied=loot.deliverPicks(death);assertEquals(1,denied.size());
            assertEquals("NO_BENEFICIARY",denied.getFirst().state());assertNull(denied.getFirst().result());
            assertEquals(denied,loot.deliverPicks(death));
        }
    }
}
