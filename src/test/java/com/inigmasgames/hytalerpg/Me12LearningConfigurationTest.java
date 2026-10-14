package com.inigmasgames.hytalerpg;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.content.*;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.enemies.*;
import com.inigmasgames.hytalerpg.links.*;
import com.inigmasgames.hytalerpg.progress.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class Me12LearningConfigurationTest {
    @TempDir Path directory;
    final RpgCatalog catalog=RpgCatalog.loadCanonical();
    LearningSourceConfiguration config()throws Exception{return new LearningSourceConfiguration(directory,catalog,EnemyRewardRegistry.load(),id->id.equals("fireball"));}
    JsonObject document(LearningSources.AbilityKind kind){
        var role=EnemyRewardRegistry.load().resolveRole("Trork_Warrior").orElseThrow();
        var entry=new LearningSourceConfiguration.Entry("synthetic_trork_source",role.canonical().roleId(),role.canonical().combatIdentity(),Set.of("Trork_Warrior"),
                kind,kind==LearningSources.AbilityKind.SKILL?"fireball":"potency",true,ProgressionMath.AcquisitionRarity.COMMON,ProgressionMath.Rank.COMMON,
                Set.of(com.inigmasgames.hytalerpg.difficulty.DifficultyId.HELL),Set.of(ProgressionMath.Rank.COMMON),false,true,true,"SYNTHETIC_OFFLINE_FIXTURE_NOT_OWNER_ACCEPTANCE");
        var result=new JsonObject();result.addProperty("schemaVersion",2);result.add("assignments",new Gson().toJsonTree(List.of(entry)));return result;
    }
    LearningSources.Opportunity opportunity(LearningSourceConfiguration config){
        var entry=config.snapshot().assignments().getFirst();var o=config.snapshot().registry().resolve(entry.combatIdentity(),ProgressionMath.Rank.BOSS,0).orElseThrow();
        return new LearningSources.Opportunity(o.skill(),o.source(),o.rarity(),o.effectiveWisdom(),null,o.kind(),config.snapshot().revision());
    }
    @Test void emptyDefaultAndAtomicValidationRejectInvalidEditsAcrossRestart()throws Exception{
        var config=config();assertEquals(0,config.snapshot().registry().verifiedBindings());
        Files.writeString(config.file(),document(LearningSources.AbilityKind.SKILL).toString());
        assertEquals(1,config.validate().registry().verifiedBindings());assertEquals(0,config.snapshot().registry().verifiedBindings());
        config.reload();var active=config.snapshot();
        Files.writeString(config.file(),"{\"schemaVersion\":2,\"schemaVersion\":2,\"assignments\":[]}");
        assertThrows(IllegalArgumentException.class,config::reload);assertSame(active,config.snapshot());assertEquals(active.revision(),config().snapshot().revision());
        var bad=document(LearningSources.AbilityKind.SKILL);bad.getAsJsonArray("assignments").get(0).getAsJsonObject().addProperty("abilityReady",false);
        Files.writeString(config.file(),bad.toString());assertThrows(IllegalArgumentException.class,config::reload);assertSame(active,config.snapshot());
    }
    @Test void immutableArchiveSurvivesDisableAndRejectsHistoricalPityRemapping()throws Exception{
        var config=config();Files.writeString(config.file(),document(LearningSources.AbilityKind.FIRST_COPY_PASSIVE).toString());config.reload();var frozen=opportunity(config);
        Files.writeString(config.file(),LearningSourceConfiguration.EMPTY);config.reload();assertEquals(0,config.snapshot().registry().verifiedBindings());assertTrue(config.accepts(frozen));
        var renamed=document(LearningSources.AbilityKind.FIRST_COPY_PASSIVE);renamed.getAsJsonArray("assignments").get(0).getAsJsonObject().addProperty("sourceId","renamed_pity_identity");
        Files.writeString(config.file(),renamed.toString());assertThrows(IllegalArgumentException.class,config::reload);
        Files.writeString(config.file(),document(LearningSources.AbilityKind.SKILL).toString());assertThrows(IllegalArgumentException.class,config::reload);
        Path archive=directory.resolve("learning-source-archives").resolve(frozen.assignmentRevision()+".json");String bytes=Files.readString(archive);Files.delete(archive);
        assertThrows(LearningSourceConfiguration.Hold.class,()->config.accepts(frozen));Files.writeString(archive,bytes);assertTrue(config.accepts(frozen));
        Files.writeString(archive,LearningSourceConfiguration.EMPTY);assertThrows(LearningSourceConfiguration.Hold.class,()->config.accepts(frozen));
    }
    @Test void frozenSkillAndPassiveRewardsCommitOnceAfterMappingsRemoved()throws Exception{
        for(var kind:LearningSources.AbilityKind.values()){
            Path root=directory.resolve(kind.name());var config=new LearningSourceConfiguration(root,catalog,EnemyRewardRegistry.load(),id->id.equals("fireball"));
            Files.writeString(config.file(),document(kind).toString());config.reload();var frozen=opportunity(config);
            var compatibility=new CompatibilityService();var graph=new RpgLinkGraphService(catalog,compatibility);UUID player=UUID.randomUUID();
            var repository=new FileRpgPlayerStateRepository(root.resolve("players"));var state=RpgPlayerState.create(player);
            state.acquisition=new AcquisitionProgress(Set.of(),Map.of(frozen.source(),100),0);repository.save(state);
            try(var service=new RpgLoadoutService(catalog,repository,graph,new LinkCompiler(catalog,graph,compatibility),new OwnershipEntitlementPolicy(true),new Stage01BTestSupport.RecordingTracer())){
            service.configureLearningConfiguration(config);service.configureEarnedRewards(new FileEarnedRewardStore(root.resolve("earned-rewards")));
            Files.writeString(config.file(),LearningSourceConfiguration.EMPTY);config.reload();
            var reward=new EarnedReward("fixture-death",10,0,Map.of(),"ENEMY_DEATH","","","fixture-death");
            for(int repeat=0;repeat<3;repeat++)service.awardGenerated(player,reward.eventId(),frozen,before->frozen.decide(reward,before,()->{fail("Pity must not draw");return 0;}));
            var after=service.getPresentationView(player).state();
            if(kind==LearningSources.AbilityKind.SKILL)assertTrue(after.learnedSkills.contains("fireball"));else assertEquals(1,after.ownedPassives.get("potency"));
            assertEquals(0,after.acquisition.pity().getOrDefault(frozen.source(),0));
            }
        }
    }
    @Test void schemaReadinessAliasesAndOneAbilityConstraintsAreEnforced()throws Exception{
        var config=config();
        for(String field:List.of("abilityId","canonicalRoleId","combatIdentity")){
            var bad=document(LearningSources.AbilityKind.SKILL);bad.getAsJsonArray("assignments").get(0).getAsJsonObject().addProperty(field,"unknown_fixture");
            Files.writeString(config.file(),bad.toString());assertThrows(RuntimeException.class,config::reload,field);
        }
        var duplicate=document(LearningSources.AbilityKind.FIRST_COPY_PASSIVE);duplicate.getAsJsonArray("assignments").add(duplicate.getAsJsonArray("assignments").get(0).deepCopy());
        Files.writeString(config.file(),duplicate.toString());assertThrows(IllegalArgumentException.class,config::reload);assertEquals(0,config.snapshot().registry().verifiedBindings());
    }
    @Test void bothKindsRecoverTheirArchivedAssignmentAtEveryExistingPlayerWriteBoundary()throws Exception{
        for(var kind:LearningSources.AbilityKind.values())for(var boundary:FileEarnedRewardStore.Boundary.values()){
            Path root=directory.resolve(kind+"-"+boundary);var config=new LearningSourceConfiguration(root,catalog,EnemyRewardRegistry.load(),id->id.equals("fireball"));
            Files.writeString(config.file(),document(kind).toString());config.reload();var frozen=opportunity(config);UUID player=UUID.randomUUID();
            var repository=new FileRpgPlayerStateRepository(root.resolve("players"));var state=RpgPlayerState.create(player);
            state.acquisition=new AcquisitionProgress(Set.of(),Map.of(frozen.source(),100),0);repository.save(state);
            var reward=new EarnedReward("fixture-crash",17,0,Map.of(),"ENEMY_DEATH","","","fixture-crash");
            var armed=new java.util.concurrent.atomic.AtomicBoolean(true);
            try(var service=service(root,config,new FileEarnedRewardStore(root.resolve("earned-rewards"),at->{if(at==boundary&&armed.getAndSet(false))throw new IllegalStateException("FIXTURE_CRASH");}))){
                assertThrows(IllegalStateException.class,()->service.awardGenerated(player,reward.eventId(),frozen,before->frozen.decide(reward,before,()->0)));
            }
            Files.writeString(config.file(),LearningSourceConfiguration.EMPTY);config.reload();
            var restarted=new LearningSourceConfiguration(root,catalog,EnemyRewardRegistry.load(),id->id.equals("fireball"));
            try(var service=service(root,restarted,new FileEarnedRewardStore(root.resolve("earned-rewards")))){
                service.awardGenerated(player,reward.eventId(),frozen,before->frozen.decide(reward,before,()->0));
                var after=service.getPresentationView(player).state();
                if(kind==LearningSources.AbilityKind.SKILL)assertTrue(after.learnedSkills.contains("fireball"));else assertEquals(1,after.ownedPassives.get("potency"));
                assertEquals(0,after.acquisition.pity().getOrDefault(frozen.source(),0));
            }
        }
    }
    RpgLoadoutService service(Path root,LearningSourceConfiguration config,FileEarnedRewardStore rewards){
        var compatibility=new CompatibilityService();var graph=new RpgLinkGraphService(catalog,compatibility);
        var service=new RpgLoadoutService(catalog,new FileRpgPlayerStateRepository(root.resolve("players")),graph,new LinkCompiler(catalog,graph,compatibility),new OwnershipEntitlementPolicy(true),new Stage01BTestSupport.RecordingTracer());
        service.configureLearningConfiguration(config);service.configureEarnedRewards(rewards);return service;
    }
    @Test void onlyExplicitSourceDifficultyAndRankReceiveAnOpportunity()throws Exception{
        var config=config();var document=document(LearningSources.AbilityKind.FIRST_COPY_PASSIVE);Files.writeString(config.file(),document.toString());config.reload();
        var profile=com.inigmasgames.hytalerpg.difficulty.AuthoredEncounterCatalog.load().profiles().stream()
                .filter(p->p.roleId().equals("Trork_Warrior")&&p.difficulty()==com.inigmasgames.hytalerpg.difficulty.DifficultyId.HELL).findFirst().orElseThrow();
        UUID world=UUID.randomUUID(),enemy=UUID.randomUUID();
        var combat=new com.inigmasgames.hytalerpg.difficulty.EncounterProfileResolver.Resolved(world,enemy,profile.difficulty(),profile.id(),profile.worldProfileId(),profile.roleId(),profile.biomeKey(),profile.combatLevel(),profile.roleHealth(),profile.roleAttackBasis(),1,1,profile.resistance(),profile.evidence());
        var entry=config.snapshot().assignments().getFirst();
        var spawn=new EnemyRewardRegistry.Spawn(world,enemy,"Trork_Warrior",entry.combatIdentity(),profile.biomeKey(),profile.combatLevel(),ProgressionMath.Rank.COMMON,ProgressionMath.Rarity.ORDINARY,profile.id(),1000,null,combat);
        assertTrue(config.snapshot().resolve(spawn,0).isPresent());
        var row=document.getAsJsonArray("assignments").get(0).getAsJsonObject();
        row.add("difficulties",new Gson().toJsonTree(List.of("NORMAL")));Files.writeString(config.file(),document.toString());config.reload();assertTrue(config.snapshot().resolve(spawn,0).isEmpty());
        row.add("difficulties",new Gson().toJsonTree(List.of("HELL")));row.add("encounterRanks",new Gson().toJsonTree(List.of("BOSS")));Files.writeString(config.file(),document.toString());config.reload();assertTrue(config.snapshot().resolve(spawn,0).isEmpty());
        Files.writeString(config.file(),LearningSourceConfiguration.EMPTY);config.reload();assertTrue(config.snapshot().resolve(spawn,0).isEmpty());
    }
    @Test void archiveLostAfterIntentHoldsTheSamePlayerAndRestorationCompletesWithoutReroll()throws Exception{
        var config=config();Files.writeString(config.file(),document(LearningSources.AbilityKind.FIRST_COPY_PASSIVE).toString());config.reload();var frozen=opportunity(config);
        var archive=directory.resolve("learning-source-archives").resolve(frozen.assignmentRevision()+".json");var bytes=Files.readString(archive);
        var armed=new java.util.concurrent.atomic.AtomicBoolean(true);UUID player=UUID.randomUUID();
        var store=new FileEarnedRewardStore(directory.resolve("earned-rewards"),at->{if(at==FileEarnedRewardStore.Boundary.AFTER_INTENT&&armed.getAndSet(false))try{Files.delete(archive);}catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}});
        try(var service=service(directory,config,store)){
            var reward=new EarnedReward("fixture-held",11,0,Map.of(),"ENEMY_DEATH","","","fixture-held");
            assertThrows(LearningSourceConfiguration.Hold.class,()->service.awardGenerated(player,reward.eventId(),frozen,before->frozen.decide(reward,before,()->0)));
            assertFalse(service.ready(player));
            assertThrows(LearningSourceConfiguration.Hold.class,()->service.awardGenerated(player,reward.eventId(),frozen,before->{fail("Held intent must not reroll");return reward;}));
            Files.writeString(archive,bytes);
            service.awardGenerated(player,reward.eventId(),frozen,before->{fail("Restored intent must not reroll");return reward;});
            assertTrue(service.ready(player));assertEquals(1,service.getPresentationView(player).state().ownedPassives.get("potency"));
        }
    }
}
