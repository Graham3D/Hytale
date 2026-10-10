package com.inigmasgames.hytalerpg;

import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.difficulty.*;
import com.inigmasgames.hytalerpg.progress.*;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.links.*;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.cooldown.SavedCooldown;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class DifficultyMilestoneTest {
    @TempDir Path root;
    final UUID player=UUID.randomUUID(),world=UUID.randomUUID();
    final GolemMilestones golems=GolemMilestones.load();
    FileRpgPlayerStateRepository repository(){return new FileRpgPlayerStateRepository(root.resolve("players"));}
    RpgLoadoutService service(java.util.function.Consumer<FileEarnedRewardStore.Boundary> fault){
        var catalog=RpgCatalog.loadCanonical();var compatibility=new CompatibilityService();var graph=new RpgLinkGraphService(catalog,compatibility);
        var s=new RpgLoadoutService(catalog,repository(),graph,new LinkCompiler(catalog,graph,compatibility),new OwnershipEntitlementPolicy(true),ignored->{});
        s.configureEarnedRewards(new FileEarnedRewardStore(root.resolve("rewards"),fault));return s;
    }
    @Test void operatorUnlockIsCaseInsensitiveIdempotentAndPreservesEarnedFlags(){
        assertEquals(DifficultyId.NIGHTMARE,DifficultyOperatorUnlock.target("nIgHtMaRe"));
        assertEquals(DifficultyId.HELL,DifficultyOperatorUnlock.target(" HELL "));
        assertThrows(IllegalArgumentException.class,()->DifficultyOperatorUnlock.target("normal"));
        var earned=DifficultyProgress.INITIAL.complete(DifficultyId.NORMAL,"earned.extra").complete(DifficultyId.NORMAL,"golem.earth");
        var nightmare=DifficultyOperatorUnlock.apply(earned,DifficultyId.NIGHTMARE);
        assertTrue(nightmare.unlocked(DifficultyId.NIGHTMARE));
        assertTrue(nightmare.milestones().get(DifficultyId.NORMAL).containsAll(GolemMilestones.REQUIRED_V1));
        assertTrue(nightmare.milestones().get(DifficultyId.NORMAL).contains("earned.extra"));
        assertTrue(nightmare.milestones().get(DifficultyId.NIGHTMARE).isEmpty());
        assertEquals(DifficultyProgress.INITIAL.milestones().get(DifficultyId.NORMAL).size()+2,
                earned.milestones().get(DifficultyId.NORMAL).size());
        assertSame(nightmare,DifficultyOperatorUnlock.apply(nightmare,DifficultyId.NIGHTMARE));
        var hell=DifficultyOperatorUnlock.apply(nightmare,DifficultyId.HELL);
        assertTrue(hell.unlocked(DifficultyId.HELL));
        assertTrue(hell.milestones().get(DifficultyId.NIGHTMARE).containsAll(GolemMilestones.REQUIRED_V1));
        assertSame(hell,DifficultyOperatorUnlock.apply(hell,DifficultyId.HELL));
        var directHell=DifficultyOperatorUnlock.apply(DifficultyProgress.INITIAL,DifficultyId.HELL);
        assertTrue(directHell.unlocked(DifficultyId.HELL));
        assertTrue(directHell.milestones().get(DifficultyId.NORMAL).containsAll(GolemMilestones.REQUIRED_V1));
        assertTrue(directHell.milestones().get(DifficultyId.NIGHTMARE).containsAll(GolemMilestones.REQUIRED_V1));
        assertEquals("",golems.rejection(hell,DifficultyId.NIGHTMARE));
        assertEquals("",golems.rejection(hell,DifficultyId.HELL));
    }
    @Test void operatorUnlockPersistsThroughPlayerMutationAndRestartWithoutRewardChange(){
        var original=RpgPlayerState.create(player);original.level=7;
        original.currentXp=new com.inigmasgames.hytalerpg.ui.CharacterXpProjectionService().levelStartXp(7);
        repository().save(original);
        try(var s=service(ignored->{})){
            s.awardEarned(player,new EarnedReward("ordinary-before-operator-unlock",1,1,Map.of(),"FIXTURE","","","ordinary"));
            var before=s.getPresentationView(player).state();
            assertEquals(1,before.rewards.sequence());
            var result=s.mutateProgress(player,before.revision,"operator-nightmare",state->
                    state.difficulty=DifficultyOperatorUnlock.apply(state.difficulty,DifficultyId.NIGHTMARE));
            assertTrue(result.success(),result.message());
            var after=s.getPresentationView(player).state();
            assertTrue(after.difficulty.unlocked(DifficultyId.NIGHTMARE));
            assertEquals(before.rewards,after.rewards);assertEquals(before.currentXp,after.currentXp);
            assertEquals(before.learnedSkills,after.learnedSkills);
        }
        try(var restarted=service(ignored->{})){
            var before=restarted.getPresentationView(player).state();
            assertTrue(before.difficulty.unlocked(DifficultyId.NIGHTMARE));
            var result=restarted.mutateProgress(player,before.revision,"operator-hell",state->
                    state.difficulty=DifficultyOperatorUnlock.apply(state.difficulty,DifficultyId.HELL));
            assertTrue(result.success(),result.message());
            var after=restarted.getPresentationView(player).state();
            assertTrue(after.difficulty.unlocked(DifficultyId.HELL));
            for(var mode:List.of(DifficultyId.NORMAL,DifficultyId.NIGHTMARE))
                assertTrue(after.difficulty.milestones().get(mode).containsAll(GolemMilestones.REQUIRED_V1));
            assertEquals(before.rewards,after.rewards);assertEquals(before.currentXp,after.currentXp);
        }
        try(var restarted=service(ignored->{})){
            var state=restarted.getPresentationView(player).state();
            assertTrue(state.difficulty.unlocked(DifficultyId.HELL));
            assertEquals(1,state.rewards.sequence());
            assertEquals("",golems.rejection(state.difficulty,DifficultyId.HELL));
        }
    }
    EnemyRewardRegistry.Spawn spawn(DifficultyId mode,String key){var g=golems.require(key);UUID enemy=UUID.randomUUID();
        return new EnemyRewardRegistry.Spawn(world,enemy,g.roleId(),g.roleId(),"milestone/"+g.id(),0,ProgressionMath.Rank.BOSS,ProgressionMath.Rarity.ORDINARY,golems.profileId(),100,
                new GolemEncounter(mode,g.id(),golems.profileId(),UUID.randomUUID().toString(),GolemEncounter.Source.OPERATOR_CAMPAIGN_PLACEMENT));}
    EarnedReward reward(EnemyRewardRegistry.Spawn spawn){var share=new EncounterContributions.Share(player,0,0,1,1);return new EncounterContributions.DeathPlan(spawn,Vec3.ZERO,104,List.of(share)).reward(share);}
    void waitFor(CompletionStage<?> s)throws Exception{s.toCompletableFuture().get(5,TimeUnit.SECONDS);}
    @Test void actualProductionHandoffCompletesBothChecklistsAtLevel37AndSurvivesRestart()throws Exception{
        var state=RpgPlayerState.create(player);state.currentXp=new com.inigmasgames.hytalerpg.ui.CharacterXpProjectionService().levelStartXp(37);state.level=37;
        state.cooldowns=Map.of("quick_slash",new SavedCooldown(9,.25));repository().save(state);
        var clock=new AtomicLong();
        try(var s=service(ignored->{});var store=FileEncounterStore.durableV2(root.resolve("encounters"));var observer=new HytaleEncounterRewards(store,s,ignored->{},RpgCombatKernel.createProduction(),clock::get)){
            var notifications=new java.util.concurrent.CopyOnWriteArrayList<DifficultyId>();observer.configureUnlockNotification((id,mode)->notifications.add(mode));
            for(var mode:List.of(DifficultyId.NORMAL,DifficultyId.NIGHTMARE)){
                for(var g:golems.golems()){
                    var spawn=spawn(mode,g.id());waitFor(observer.attachObserved(world,spawn.enemy(),spawn.roleId(),Optional.of(spawn)));
                    observer.observeDamage(new HytaleEncounterRewards.DamageObservation(world,spawn.enemy(),player,100,80,100,20,101,"","","golem"),()->()->{});
                    var facts=List.of(new EncounterContributions.Participant(player,world,Vec3.ZERO,37,true,"party"));
                    observer.captureDeath(world,spawn.enemy(),Vec3.ZERO,102,facts,"fixture");
                    observer.captureDeath(world,spawn.enemy(),new Vec3(500,0,0),103,List.of(),"duplicate");
                    waitFor(observer.durableFrontier());clock.addAndGet(1_000_000_000L);observer.deliveryTick();waitFor(observer.durableFrontier());
                    assertTrue(s.getPresentationView(player).state().difficulty.milestones().get(mode).contains(g.id()));
                }
                var target=mode==DifficultyId.NORMAL?DifficultyId.NIGHTMARE:DifficultyId.HELL;
                assertEquals("",golems.rejection(s.getPresentationView(player).state().difficulty,target));
                if(mode==DifficultyId.NORMAL)assertTrue(s.getPresentationView(player).state().difficulty.milestones().get(DifficultyId.NIGHTMARE).isEmpty());
            }
            var after=s.getPresentationView(player).state();assertEquals(10,after.rewards.sequence());assertEquals(state.currentXp,after.currentXp);assertEquals(37,after.level);
            assertEquals(0,after.rewards.insight());assertEquals(state.cooldowns,after.cooldowns);assertArrayEquals(state.equippedSkills,after.equippedSkills);
            assertEquals(List.of(DifficultyId.NIGHTMARE,DifficultyId.HELL),notifications);
        }
        try(var restarted=service(ignored->{})){
            var after=restarted.getPresentationView(player).state();assertTrue(after.difficulty.unlocked(DifficultyId.HELL));assertEquals(10,after.rewards.sequence());
            restarted.awardEarned(player,new EarnedReward("ordinary-after-milestones",1,1,Map.of(),"FIXTURE","","","ordinary"));
            assertEquals(after.difficulty,restarted.getPresentationView(player).state().difficulty);
        }
    }
    @Test void forcedLockedNightmareKillDoesNotEarnFlagsOrPoisonDelivery()throws Exception{
        try(var s=service(ignored->{});var store=FileEncounterStore.durableV2(root.resolve("encounters"));var observer=new HytaleEncounterRewards(store,s,ignored->{},RpgCombatKernel.createProduction())){
            var spawn=spawn(DifficultyId.NIGHTMARE,"earth");waitFor(observer.attachObserved(world,spawn.enemy(),spawn.roleId(),Optional.of(spawn)));
            observer.observeDamage(new HytaleEncounterRewards.DamageObservation(world,spawn.enemy(),player,100,0,100,100,101,"","",""),()->()->{});
            observer.captureDeath(world,spawn.enemy(),Vec3.ZERO,102,List.of(new EncounterContributions.Participant(player,world,Vec3.ZERO,1,true,null)),"none");
            waitFor(observer.durableFrontier());observer.deliveryTick();waitFor(observer.durableFrontier());
            assertEquals(DifficultyProgress.INITIAL,s.getPresentationView(player).state().difficulty);assertEquals(0,s.getPresentationView(player).state().rewards.sequence());
        }
    }
    @ParameterizedTest @EnumSource(FileEarnedRewardStore.Boundary.class)
    void killAndUnlockRecoverAtomicallyAtEveryRewardJournalBoundary(FileEarnedRewardStore.Boundary boundary){
        try(var s=service(ignored->{})){for(var g:golems.golems().subList(0,4))s.awardEarned(player,reward(spawn(DifficultyId.NORMAL,g.id())));}
        var last=reward(spawn(DifficultyId.NORMAL,golems.golems().get(4).id()));
        try(var interrupted=service(at->{if(at==boundary)throw new IllegalStateException("simulated crash");})){assertThrows(RuntimeException.class,()->interrupted.awardEarned(player,last));}
        try(var recovered=service(ignored->{})){
            recovered.awardEarned(player,last);var state=recovered.getPresentationView(player).state();assertEquals(5,state.rewards.sequence());
            assertTrue(state.difficulty.unlocked(DifficultyId.NIGHTMARE));assertEquals(5,state.difficulty.milestones().get(DifficultyId.NORMAL).size());
            recovered.awardEarned(player,last);assertEquals(state.difficulty,recovered.getPresentationView(player).state().difficulty);
        }
    }
    @Test void supportContributorsEarnWithoutLastHitButAbsentStaleAndWrongWorldDoNot(){
        var spawn=spawn(DifficultyId.NORMAL,"earth");var ledger=new EncounterContributions();ledger.begin(spawn);
        var healer=UUID.randomUUID();var control=UUID.randomUUID();var stale=UUID.randomUUID();var far=UUID.randomUUID();var other=UUID.randomUUID();var spectator=UUID.randomUUID();
        ledger.damage(world,spawn.enemy(),stale,100,99,100,true,101);
        ledger.damage(world,spawn.enemy(),player,99,80,100,true,30_101);
        ledger.heal(world,healer,player,10,true,30_102);ledger.control(world,spawn.enemy(),control,true,false,true,30_102);
        ledger.absorb(world,spawn.enemy(),far,5,true,30_102);ledger.absorb(world,spawn.enemy(),other,5,true,30_102);
        var facts=new ArrayList<EncounterContributions.Participant>();for(UUID id:List.of(player,healer,control,stale,spectator))facts.add(new EncounterContributions.Participant(id,world,Vec3.ZERO,1,true,"p"));
        facts.add(new EncounterContributions.Participant(far,world,new Vec3(100,0,0),1,true,"p"));facts.add(new EncounterContributions.Participant(other,UUID.randomUUID(),Vec3.ZERO,1,true,"p"));
        var plan=ledger.death(world,spawn.enemy(),Vec3.ZERO,30_103,facts);assertEquals(Set.of(player,healer,control),plan.shares().stream().map(EncounterContributions.Share::player).collect(java.util.stream.Collectors.toSet()));
        assertTrue(plan.shares().stream().allMatch(s->plan.reward(s).milestone()!=null&&s.xp()==0));
    }
    @ParameterizedTest @EnumSource(FileEarnedRewardStore.Boundary.class)
    void authoredXpInsightAndFinalFlagRecoverTogether(FileEarnedRewardStore.Boundary boundary){
        try(var s=service(ignored->{})){for(var g:golems.golems().subList(0,4))s.awardEarned(player,reward(spawn(DifficultyId.NORMAL,g.id())));}
        var worlds=new WorldDifficultyRegistry(root.resolve("worlds.json"));
        worlds.register(new WorldDifficultyRegistry.Binding(world,"normal",WorldDifficultyRegistry.Kind.CAMPAIGN,DifficultyId.NORMAL,"rpg.encounters.r031.pilot","server-character",true));
        var source=AuthoredEncounterCatalog.load().resolver(worlds).author(spawn(DifficultyId.NORMAL,golems.golems().get(4).id()),AuthoredEncounterCatalog.CAMPAIGN_GOLEM).orElseThrow();
        var ledger=new EncounterContributions();ledger.begin(source);ledger.damage(world,source.enemy(),player,100,0,100,true,101);
        var plan=ledger.death(world,source.enemy(),Vec3.ZERO,102,List.of(new EncounterContributions.Participant(player,world,Vec3.ZERO,1,true,null)));
        var last=plan.reward(plan.shares().getFirst());assertTrue(last.characterXp()>0);assertTrue(last.insight()>0);
        try(var interrupted=service(at->{if(at==boundary)throw new IllegalStateException("simulated authored reward crash");})){assertThrows(RuntimeException.class,()->interrupted.awardEarned(player,last));}
        try(var recovered=service(ignored->{})){
            recovered.awardEarned(player,last);var state=recovered.getPresentationView(player).state();
            assertEquals(5,state.rewards.sequence());assertEquals(last.characterXp(),state.currentXp);assertEquals(last.insight(),state.rewards.insight());assertTrue(state.difficulty.unlocked(DifficultyId.NIGHTMARE));
            var before=RewardCheckpoint.of(state);recovered.awardEarned(player,last);assertEquals(before,RewardCheckpoint.of(recovered.getPresentationView(player).state()));
        }
    }
    @Test void repeatedDifferentEnemiesKeepSameFlagRevisionAndConversionCannotAward(){
        var first=reward(spawn(DifficultyId.NORMAL,"earth"));var second=reward(spawn(DifficultyId.NORMAL,"earth"));
        try(var s=service(ignored->{})){s.awardEarned(player,first);var before=s.getPresentationView(player).state().difficulty;s.awardEarned(player,second);assertEquals(before,s.getPresentationView(player).state().difficulty);}
        var spawn=spawn(DifficultyId.NORMAL,"flame");var ledger=new EncounterContributions();ledger.begin(spawn);ledger.damage(world,spawn.enemy(),player,100,90,100,true,101);ledger.disqualify(world,spawn.enemy());
        assertTrue(ledger.death(world,spawn.enemy(),Vec3.ZERO,102,List.of(new EncounterContributions.Participant(player,world,Vec3.ZERO,1,true,null))).shares().isEmpty());
    }
    @Test void sortedCheckpointSerializationMakesMilestoneIntentHashStable(){
        var a=DifficultyProgress.INITIAL;for(String g:GolemMilestones.REQUIRED_V1)a=a.complete(DifficultyId.NORMAL,g);a=a.unlockNext(DifficultyId.NORMAL,GolemMilestones.REQUIRED_V1);
        var b=DifficultyProgress.INITIAL;for(String g:GolemMilestones.REQUIRED_V1.stream().sorted(Comparator.reverseOrder()).toList())b=b.complete(DifficultyId.NORMAL,g);b=b.unlockNext(DifficultyId.NORMAL,GolemMilestones.REQUIRED_V1);
        assertEquals(new Gson().toJson(a),new Gson().toJson(b));var state=RpgPlayerState.create(player);state.difficulty=a;
        var reward=reward(spawn(DifficultyId.NIGHTMARE,"earth"));var intent=RewardIntent.create(player,reward,RewardCheckpoint.of(state));
        assertEquals(intent,new Gson().fromJson(new Gson().toJson(intent),RewardIntent.class));
    }
}
