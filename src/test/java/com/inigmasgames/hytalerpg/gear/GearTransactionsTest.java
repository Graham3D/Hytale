package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.progress.*;
import com.inigmasgames.hytalerpg.difficulty.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class GearTransactionsTest {
    @TempDir Path root;
    final UUID world=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
    GearDropGenerator generator(){return new GearDropGenerator(GearCatalog.load(),new GearBindings(),GearDropGenerator.STAGE_TWO_CANDIDATES);}
    EncounterContributions.DeathPlan plan(int n){
        UUID enemy=new UUID(7,n);var combat=new EncounterProfileResolver.Resolved(world,enemy,DifficultyId.HELL,"fixture","fixture","role","biome",95,100,10,1,1,MonsterResistanceProfile.NONE,EncounterProfileResolver.Evidence.FIXTURE_ONLY);
        var spawn=new EnemyRewardRegistry.Spawn(world,enemy,"role","role","biome",95,ProgressionMath.Rank.BOSS,ProgressionMath.Rarity.ORDINARY,"fixture",1000,null,combat);
        long xp=ProgressionMath.equalShare(ProgressionMath.enemyReward(95,spawn.rank(),spawn.rarity(),95),2);
        return new EncounterContributions.DeathPlan(spawn,Vec3.ZERO,2000,List.of(new EncounterContributions.Share(a,xp,spawn.rank().insight,95,2),new EncounterContributions.Share(b,xp,spawn.rank().insight,95,2)));
    }
    void claims(GearLootService service,EncounterContributions.DeathPlan p){var policy=new GearClaims.Policy("party",1,GearClaims.Mode.ROUND_ROBIN,List.of(a,b),a,Set.of(),true,false);service.contribute(world,p.spawn().enemy(),a,policy,1500);service.contribute(world,p.spawn().enemy(),b,policy,1600);}
    @Test void deathEmitsOpportunityEvidenceWithoutChangingItsDecision() throws Exception {
        var plan=plan(77);
        try(var trace=new GearQaTrace(root.resolve("gear-trace"));var store=new FileEncounterStore(root.resolve("encounter"))){
            GearQaTrace.install(trace);
            trace.on(a);trace.on(b);
            var loot=new GearLootService(store,generator(),()->3000);claims(loot,plan);
            var result=loot.death(plan,Map.of(a,0d,b,0d));
            var recipient=result.allocation().assigned();
            String path=trace.status(recipient).split("; ")[2];
            trace.off(recipient);
            String json=Files.readString(Path.of(path));
            assertTrue(json.contains("ENEMY_GEAR_DECISION"));
            assertTrue(json.contains("opportunityChance"));
            assertTrue(json.contains("opportunityRoll"));
            assertTrue(json.contains(plan.spawn().enemy().toString()));
        }
    }
    @Test void oneOutcomeGlobalRestartFrozenMfCursorAndInventoryFull() {
        var plan=plan(1);GearLootService.Loot first;
        try(var store=new FileEncounterStore(root)){var loot=new GearLootService(store,generator(),()->3000);claims(loot,plan);
            first=loot.death(plan,Map.of(a,0.,b,10.));assertEquals("WORLD",first.state());assertEquals(a,first.allocation().assigned());
            assertEquals(first,loot.death(plan,Map.of(a,100.,b,0.)));
            assertThrows(IllegalArgumentException.class,()->loot.reservePickup(plan.spawn().eventId(),a,0,3100,false));assertEquals(first,loot.inspect(plan.spawn().eventId()).orElseThrow());
        }
        try(var store=new FileEncounterStore(root)){var loot=new GearLootService(store,generator(),()->9000);assertEquals(first,loot.death(plan,Map.of(a,999.,b,0.)));
            var second=plan(2);claims(loot,second);assertEquals(b,loot.death(second,Map.of(a,0.,b,0.)).allocation().assigned());
            assertEquals(183000,first.allocation().expiresAt());
        }
    }
    @Test void preparedCursorCrashReplaysOriginalInputsAndAdvancesOnce(){
        var plan=plan(3);
        try(var store=new FileEncounterStore(root)){var loot=new GearLootService(store,generator(),()->3000);claims(loot,plan);
            store.configureGearFault(boundary->{if(boundary.equals("AFTER_cursors"))throw new IllegalStateException("simulated process loss");});
            assertThrows(IllegalStateException.class,()->loot.death(plan,Map.of(a,.25,b,10.)));
        }
        try(var store=new FileEncounterStore(root)){var loot=new GearLootService(store,generator(),()->9000);var recovered=loot.death(plan,Map.of(a,999.,b,999.));
            assertEquals(.25,recovered.magicFind());assertEquals(a,recovered.allocation().sponsor());assertEquals(183000,recovered.allocation().expiresAt());
            assertEquals(1,store.gearRead("cursors","party",GearLootService.Cursor.class).orElseThrow().next());
            var second=plan(4);claims(loot,second);assertEquals(b,loot.death(second,Map.of(a,0.,b,0.)).allocation().sponsor());
        }
    }
    @Test void changedDefinitionsCannotRegenerateAPreparedOutcome(){
        var p=plan(90);try(var store=new FileEncounterStore(root)){var loot=new GearLootService(store,generator(),()->3000);claims(loot,p);
            store.configureGearFault(boundary->{if(boundary.equals("AFTER_cursors"))throw new IllegalStateException("crash");});assertThrows(IllegalStateException.class,()->loot.death(p,Map.of(a,0.,b,0.)));}
        try(var store=new FileEncounterStore(root)){var changed=new GearDropGenerator(GearCatalog.load(),new GearBindings(),Set.of("WA-001"));var loot=new GearLootService(store,changed,()->9000);
            var result=loot.death(p,Map.of(a,999.,b,999.));assertEquals("CONTENT_ERROR",result.state());assertTrue(result.reason().contains("definition changed"));assertNull(result.result());assertEquals(0,store.gearRead("cursors","party",GearLootService.Cursor.class).orElseThrow().next());}
    }
    @Test void simultaneousPickupHasExactlyOneWinner()throws Exception{
        try(var store=new FileEncounterStore(root)){var loot=new GearLootService(store,generator(),()->3000);var plan=plan(5);claims(loot,plan);loot.death(plan,Map.of(a,0.,b,0.));
            try(var executor=Executors.newFixedThreadPool(2)){var start=new CountDownLatch(1);var winners=new java.util.concurrent.atomic.AtomicInteger();var futures=new ArrayList<Future<?>>();
                for(UUID actor:List.of(a,b))futures.add(executor.submit(()->{try{start.await();loot.reservePickup(plan.spawn().eventId(),actor,0,34000,true);winners.incrementAndGet();}catch(IllegalArgumentException expected){}catch(InterruptedException error){throw new RuntimeException(error);}}));
                start.countDown();for(var f:futures)f.get(5,TimeUnit.SECONDS);assertEquals(1,winners.get());assertEquals("PICKUP_PENDING_NATIVE_SAVE",loot.inspect(plan.spawn().eventId()).orElseThrow().state());
            }
        }
    }
    @Test void spatialPickupSourceReceiptSurvivesRestartAndDoesNotMintOnReplay() {
        var p = plan(105);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        String event = p.spawn().eventId();
        UUID item;
        try (var store = new FileEncounterStore(root)) {
            var loot = new GearLootService(store, generator(), () -> 3000);
            claims(loot, p);
            var row = loot.death(p, Map.of(a, 0., b, 0.));
            item = row.result().item().identity();
             loot.prepareSpatialPickup(event, first, a, item, "frozen-payload", 0);
             loot.prepareSpatialPickup(event, second, a, item, "frozen-payload", 0);
            assertEquals("WORLD", loot.inspect(event).orElseThrow().state());
        }
        try (var store = new FileEncounterStore(root)) {
            var loot = new GearLootService(store, generator(), () -> 3000);
             loot.reserveSpatialPickup(event, a, 0, 3100, first);
             assertEquals("PICKUP_PENDING_SPATIAL_SAVE", loot.inspect(event).orElseThrow().state());
             assertThrows(IllegalArgumentException.class,
                     () -> loot.reserveSpatialPickup(event, a, 1, 3100, second));
             assertThrows(IllegalArgumentException.class,
                     () -> loot.releaseUncommittedSpatialPickup(event, a, second));
             assertThrows(IllegalArgumentException.class,
                     () -> loot.reservePickup(event, b, 0, 3100, true));
        }
        try (var store = new FileEncounterStore(root)) {
            var loot = new GearLootService(store, generator(), () -> 3000);
            assertEquals("SOURCE_RESERVED", loot.spatialPickupReceipt(first).orElseThrow().stage());
            loot.releaseUncommittedSpatialPickup(event, a, first);
            assertEquals("WORLD", loot.inspect(event).orElseThrow().state());
            assertEquals("ABORTED", loot.spatialPickupReceipt(first).orElseThrow().stage());
            long revision = loot.inspect(event).orElseThrow().allocation().revision();
            loot.prepareSpatialPickup(event, second, a, item, "frozen-payload", 0);
            loot.reserveSpatialPickup(event, a, revision, 3200, second);
            loot.acknowledgeSpatialPickup(event, a, second);
        }
        try (var store = new FileEncounterStore(root)) {
            var loot = new GearLootService(store, generator(), () -> 3000);
            assertEquals("SPATIAL_BAG", loot.inspect(event).orElseThrow().state());
            assertEquals("FINALIZED", loot.spatialPickupReceipt(second).orElseThrow().stage());
            assertEquals("SPATIAL_BAG", loot.acknowledgeSpatialPickup(event, a, second).state());
            assertThrows(IllegalArgumentException.class,
                    () -> loot.releaseUncommittedSpatialPickup(event, a, second));
            assertThrows(IllegalArgumentException.class,
                    () -> loot.reservePickup(event, a, 0, 3300, true));
        }
    }
    @Test void equipmentReceiptIsDurableAcrossPreparedWriteFaultAndCannotBeReplayedAsNew() {
        UUID operation=UUID.randomUUID();
        var receipt=new GearLootService.SpatialEquipmentReceipt(operation,a,8,"{\"Revision\":8}",
                "{\"Revision\":9}",List.of(new GearLootService.EquipmentSlotChange("STORAGE",(short)1,null,
                "{\"ItemId\":\"Rock_Stone\"}")),"QA_PROOF","MIGRATION_PROOF","PREPARED");
        try(var store=new FileEncounterStore(root)){
            var loot=new GearLootService(store,generator(),()->3000);
            store.configureGearFault(boundary->{if(boundary.equals("AFTER_spatial-equipment"))
                throw new IllegalStateException("simulated process loss after durable prepare");});
            assertThrows(IllegalStateException.class,()->loot.prepareSpatialEquipment(receipt));
        }
        try(var store=new FileEncounterStore(root)){
            var loot=new GearLootService(store,generator(),()->3000);
            assertEquals(List.of(receipt),loot.spatialEquipmentReceipts());
            assertThrows(IllegalStateException.class,()->loot.prepareSpatialEquipment(receipt));
            assertEquals("FINALIZED",loot.finishSpatialEquipment(operation,"FINALIZED").stage());
            assertEquals("FINALIZED",loot.finishSpatialEquipment(operation,"ABORTED").stage());
        }
    }
    @Test void equipmentJournalCanHoldTwoLargeBagSnapshotsWithoutWideningOtherReceiptBounds() throws Exception {
        UUID operation=UUID.randomUUID();
        String bag="{\"Payload\":\""+"x".repeat(225_000)+"\"}";
        var receipt=new GearLootService.SpatialEquipmentReceipt(operation,a,440,bag,bag,
                List.of(new GearLootService.EquipmentSlotChange("ARMOR",(short)0,null,"{}")),"PREPARED");
        try(var store=new FileEncounterStore(root)){
            var loot=new GearLootService(store,generator(),()->3000);
            assertEquals(receipt,loot.prepareSpatialEquipment(receipt));
            var path=root.resolve("gear/spatial-equipment");
            try(var files=Files.list(path)){
                assertTrue(files.anyMatch(file->{try{return Files.size(file)>FileEncounterStore.MAX_FILE_BYTES;}
                    catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}}));
            }
            var stock=new GearLootService.SpatialStockReceipt(UUID.randomUUID(),UUID.randomUUID(),a,world,
                    "x".repeat(FileEncounterStore.MAX_FILE_BYTES),440,"PREPARED");
            assertEquals("ENCOUNTER_FILE_BOUNDS",assertThrows(IllegalStateException.class,
                    ()->loot.prepareSpatialStock(stock)).getMessage());
        }
        try(var store=new FileEncounterStore(root)){
            var loot=new GearLootService(store,generator(),()->3000);
            assertEquals(List.of(receipt),loot.spatialEquipmentReceipts());
            assertEquals("FINALIZED",loot.finishSpatialEquipment(operation,"FINALIZED").stage());
        }
    }
    @Test void protectedStockSourceHasOneDurableReservationAndRecoverableAbort() {
        UUID source=UUID.randomUUID(),first=UUID.randomUUID(),second=UUID.randomUUID();
        var prepared=new GearLootService.SpatialStockReceipt(source,first,a,world,"exact-payload",3,"PREPARED");
        try(var store=new FileEncounterStore(root)){
            var loot=new GearLootService(store,generator(),()->3000);
            assertEquals(prepared,loot.prepareSpatialStock(prepared));
            assertThrows(IllegalStateException.class,()->loot.prepareSpatialStock(new GearLootService.SpatialStockReceipt(
                    source,second,b,world,"same-payload",3,"PREPARED")));
        }
        try(var store=new FileEncounterStore(root)){
            var loot=new GearLootService(store,generator(),()->3000);
            assertEquals(List.of(prepared),loot.spatialStockReceipts());
            assertEquals("ABORTED",loot.finishSpatialStock(source,first,"ABORTED").stage());
            var retry=new GearLootService.SpatialStockReceipt(source,second,a,world,"exact-payload",3,"PREPARED");
            assertEquals(retry,loot.prepareSpatialStock(retry));
            assertEquals("FINALIZED",loot.finishSpatialStock(source,second,"FINALIZED").stage());
            assertThrows(IllegalStateException.class,()->loot.prepareSpatialStock(prepared));
        }
    }
    @Test void salvageIdentityCannotCreditTwoOwners(){
        try(var store=new FileEncounterStore(root)){var loot=new GearLootService(store,generator(),()->3000);GearLootService.Loot selected=null;
            for(int i=10;i<40;i++){var p=plan(i);claims(loot,p);var row=loot.death(p,Map.of(a,100.,b,100.));if(GearEconomy.salvage(row.result().item()).isPresent()){selected=row;break;}}
            assertNotNull(selected);String event=selected.source().eventId();UUID owner=selected.allocation().assigned();var item=selected.result().item();
            loot.reservePickup(event,owner,0,3100,true);loot.acknowledgePickup(event,owner,item.identity());
            var reservation=loot.reserveSalvage(event,a,item);assertEquals(reservation,loot.reserveSalvage(event,a,item));assertTrue(loot.consumed(item.identity()));
            assertThrows(IllegalArgumentException.class,()->loot.reserveSalvage(event,b,item));
        }
    }
    @Test void lateJoinCannotChangeFrozenMembership(){
        var policy=new GearClaims.Policy("party",1,GearClaims.Mode.ROUND_ROBIN,List.of(a),a,Set.of(),true,false);
        var expanded=new GearClaims.Policy("party",2,GearClaims.Mode.ROUND_ROBIN,List.of(a,b),a,Set.of(),true,false);
        var ledger=GearClaims.Ledger.EMPTY.contribute(a,policy,1000).contribute(b,expanded,1100);assertEquals(1,ledger.claims().size());assertTrue(ledger.select(Set.of(b),1200).isEmpty());
    }
    @Test void schemaMigrationPreservesAllPreviouslyAttainedMasteryAndRejectsMissingEconomy()throws Exception{
        var player=RpgPlayerState.create(a);player.skillMastery.put("quick_slash",400L);player.learnedSkills.add("quick_slash");
        var json=new com.google.gson.Gson().toJsonTree(player).getAsJsonObject();json.addProperty("schemaVersion",10);json.remove("gearEconomy");
        var migrated=new RpgStateMigrator().migrate(json);assertEquals(com.inigmasgames.hytalerpg.progress.RpgPlayerState.CURRENT_SCHEMA,migrated.targetVersion());assertEquals(400,migrated.state().getAsJsonObject("skillMastery").get("quick_slash").getAsLong());
        var repo=new FileRpgPlayerStateRepository(root);repo.save(player);var envelope=com.google.gson.JsonParser.parseString(Files.readString(repo.path(a))).getAsJsonObject();
        // A raw current-schema fixture must not silently default a missing debit ledger.
        var raw=envelope.getAsJsonObject("state");raw.remove("gearEconomy");Files.writeString(repo.path(a),raw.toString());assertThrows(IllegalStateException.class,()->repo.load(a));
    }
    private RpgLoadoutService players(RpgPlayerStateRepository repo){var catalog=com.inigmasgames.hytalerpg.content.RpgCatalog.loadCanonical();var compatibility=new com.inigmasgames.hytalerpg.links.CompatibilityService();var graph=new com.inigmasgames.hytalerpg.links.RpgLinkGraphService(catalog,compatibility);
        return new RpgLoadoutService(catalog,repo,graph,new com.inigmasgames.hytalerpg.links.LinkCompiler(catalog,graph,compatibility),new OwnershipEntitlementPolicy(true),ignored->{});}
    @Test void realPlayerOwnerCommitsDebitAndRankTogetherAndRetrySurvivesRestart(){
        var repo=new FileRpgPlayerStateRepository(root);var state=RpgPlayerState.create(a);state.learnedSkills.add("quick_slash");state.skillMastery.put("quick_slash",10L);
        state.gearEconomy=new GearEconomyProgress(Map.of("PLAIN_SCRAP/CAMPAIGN",10L),Map.of(),Map.of());repo.save(state);UUID request=UUID.randomUUID();GearEconomyProgress.Receipt receipt;
        try(var service=players(repo)){long revision=service.getPresentationView(a).state().revision;receipt=service.upgradeSkill(a,"quick_slash",revision,request);assertEquals(2,receipt.afterRank());assertEquals(2,service.baseSkillRank(a,"quick_slash"));
            var current=service.getPresentationView(a).state();assertEquals(10L,current.skillMastery.get("quick_slash"));assertTrue(current.learnedSkills.contains("quick_slash"));assertEquals(6L,current.gearEconomy.materials().get("PLAIN_SCRAP/CAMPAIGN"));}
        try(var service=players(repo)){assertEquals(receipt,service.upgradeSkill(a,"quick_slash",-1,request));assertEquals(6L,service.getPresentationView(a).state().gearEconomy.materials().get("PLAIN_SCRAP/CAMPAIGN"));}
    }
    @Test void uncertainPlayerSaveCannotRollAgainAndPersistedReceiptRecovers(){
        var repo=new FileRpgPlayerStateRepository(root);var state=RpgPlayerState.create(a);state.learnedSkills.add("quick_slash");state.gearEconomy=new GearEconomyProgress(Map.of("ARCANE_DUST/ASCENDANT",20L),Map.of("quick_slash",5),Map.of());repo.save(state);
        var crash=new java.util.concurrent.atomic.AtomicBoolean(false);var fault=new RpgPlayerStateRepository(){public LoadResult load(UUID id){return repo.load(id);}public void save(RpgPlayerState value){repo.save(value);if(crash.getAndSet(false))throw new IllegalStateException("after durable player publication");}};
        UUID request=UUID.randomUUID();try(var service=players(fault)){long revision=service.getPresentationView(a).state().revision;crash.set(true);assertThrows(IllegalStateException.class,()->service.upgradeSkill(a,"quick_slash",revision,request));assertThrows(IllegalStateException.class,()->service.upgradeSkill(a,"quick_slash",revision,request));}
        var saved=repo.load(a).state();var original=saved.gearEconomy.receipts().get("upgrade/"+request);assertNotNull(original);assertEquals(12L,saved.gearEconomy.materials().get("ARCANE_DUST/ASCENDANT"));
        try(var service=players(repo)){assertEquals(original,service.upgradeSkill(a,"quick_slash",-1,request));}
    }
    @Test void sameDurableDeathPipelineAwardsXpOnDropAndNoDropWithoutSecondEquipmentAttempt(){
        var catalog=com.inigmasgames.hytalerpg.content.RpgCatalog.loadCanonical();var compatibility=new com.inigmasgames.hytalerpg.links.CompatibilityService();var graph=new com.inigmasgames.hytalerpg.links.RpgLinkGraphService(catalog,compatibility);
        var repo=new FileRpgPlayerStateRepository(root.resolve("players"));var earned=new FileEarnedRewardStore(root.resolve("rewards"));
        try(var players=new RpgLoadoutService(catalog,repo,graph,new com.inigmasgames.hytalerpg.links.LinkCompiler(catalog,graph,compatibility),new OwnershipEntitlementPolicy(true),ignored->{});
            var store=new FileEncounterStore(root.resolve("encounters"))){players.configureEarnedRewards(earned);
            var loot=new GearLootService(store,generator(),()->3000);store.configureGearDelivery(p->loot.death(p,Map.of(a,0.)));
            try(var runtime=new PersistentEncounterRuntime(store,(actor,reward)->players.awardEarned(actor,reward))){
                boolean drop=false,noDrop=false;long expectedXp=0;
                for(int i=100;i<160&&!(drop&&noDrop);i++){
                    UUID enemy=new UUID(9,i);var combat=new EncounterProfileResolver.Resolved(world,enemy,DifficultyId.NORMAL,"fixture","fixture","role","biome",5,100,10,1,1,MonsterResistanceProfile.NONE,EncounterProfileResolver.Evidence.FIXTURE_ONLY);
                    var spawn=new EnemyRewardRegistry.Spawn(world,enemy,"role","role","biome",5,ProgressionMath.Rank.COMMON,ProgressionMath.Rarity.ORDINARY,"fixture",1000,null,combat);
                    assertTrue(runtime.attach(world,enemy,"role",Optional.of(spawn)));assertTrue(runtime.damage(world,enemy,a,100,0,100,true,1500));loot.contribute(world,enemy,a,GearClaims.Policy.solo(a),1500);
                    var p=runtime.death(world,enemy,Vec3.ZERO,2000,List.of(new EncounterContributions.Participant(a,world,Vec3.ZERO,1,true,null))).orElseThrow();
                    expectedXp+=p.shares().getFirst().xp();runtime.drain(256);
                    var item=loot.inspect(spawn.eventId()).orElseThrow();if(item.state().equals("NO_DROP"))noDrop=true;else{assertEquals("WORLD",item.state());drop=true;}
                    assertEquals(expectedXp,players.getPresentationView(a).state().currentXp);assertEquals(item,loot.death(p,Map.of(a,1000.)));assertEquals(0,runtime.drain(256));
                }
                assertTrue(drop&&noDrop);assertEquals(expectedXp,repo.load(a).state().currentXp);
            }
        }
    }
    @Test void catalogNaturalAliasReachesPersistedMagicGearAfterUnrewardableEncounter(){
        var registry=EnemyRewardRegistry.load();var worlds=new WorldDifficultyRegistry(root.resolve("natural-worlds.json"));
        worlds.register(new WorldDifficultyRegistry.Binding(world,"natural-test",WorldDifficultyRegistry.Kind.CAMPAIGN,
                DifficultyId.NORMAL,registry.profileId(),"server-character",true));
        var resolver=AuthoredEncounterCatalog.load().resolver(worlds);var generator=generator();
        var biome=registry.biomes().stream().filter(b->b.rpgBand().equals("emerald_wilds")).findFirst().orElseThrow().key();
        assertTrue(registry.resolveRole("Horse_Foal").isEmpty());
        EnemyRewardRegistry.Spawn selected=null;
        for(int n=1;n<500;n++){
            var spawn=resolver.classifyAuthored(registry,world,new UUID(77,n),"Spider_Cave_Ambush",biome,
                    EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,1000).orElseThrow();
            var generated=generator.generate(spawn.lootSource().orElseThrow(),0,spawn.eventId(),Set.of());
            if(generated.item()!=null&&generated.item().quality()!=GearQuality.NORMAL){selected=spawn;break;}
        }
        assertNotNull(selected,"A deterministic ordinary natural death should reach the existing magic-item generator");
        assertEquals("spider_cave",selected.combatIdentity());assertEquals("Spider_Cave_Ambush",selected.roleId());
        try(var store=new FileEncounterStore(root.resolve("natural-encounters"));var runtime=new PersistentEncounterRuntime(store,(player,reward)->{})){
            var loot=new GearLootService(store,generator,()->3000);store.configureGearDelivery(loot::deliver);
            UUID unrewardable=new UUID(77,0);
            assertFalse(runtime.attach(world,unrewardable,"Horse_Foal",Optional.empty()));
            var noCredit=resolver.classifyAuthored(registry,world,unrewardable,"Goblin_Scrapper",biome,
                    EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,1000).orElseThrow();
            assertTrue(runtime.attach(world,unrewardable,noCredit.roleId(),Optional.of(noCredit)));
            var rejected=runtime.death(world,unrewardable,Vec3.ZERO,2000,List.of()).orElseThrow();
            assertTrue(rejected.shares().isEmpty());runtime.drain(8);
            assertEquals("NO_BENEFICIARY",loot.inspect(noCredit.eventId()).orElseThrow().state());
            assertFalse(runtime.unavailable());assertFalse(runtime.contains(world,unrewardable));

            assertTrue(runtime.attach(world,selected.enemy(),selected.roleId(),Optional.of(selected)));
            assertTrue(runtime.damage(world,selected.enemy(),a,100,80,100,true,1500));
            loot.contribute(world,selected.enemy(),a,GearClaims.Policy.solo(a),1500);
            var plan=runtime.death(world,selected.enemy(),Vec3.ZERO,2000,
                    List.of(new EncounterContributions.Participant(a,world,Vec3.ZERO,5,true,null))).orElseThrow();
            assertEquals(1,plan.shares().size());runtime.drain(8);
            var dropped=loot.inspect(selected.eventId()).orElseThrow();assertEquals("WORLD",dropped.state());
            assertEquals(selected.eventId(),dropped.source().eventId());assertEquals(a,dropped.allocation().sponsor());
            assertNotNull(dropped.result().item().identity());assertFalse(dropped.result().item().affixes().isEmpty());
            assertNotEquals(GearQuality.NORMAL,dropped.result().item().quality());
            assertEquals(dropped,loot.deliver(plan));assertEquals(0,runtime.drain(8));
            assertEquals(0,store.pendingCount());assertFalse(runtime.contains(world,selected.enemy()));
        }
    }
}
