package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.domain.PassiveSlot;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.projectile.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pure timing, payment and geometry evidence. Does not assert connected native casts or rendering. */
class Stage13AuthoredProjectileTest {
    final Stage04SkillProfiles profiles=Stage04SkillProfiles.loadCanonical(RpgCatalog.loadCanonical());
    Stage11ResourcePassivesTest.H cast(String skill,String kind,String... links){
        var h=new Stage11ResourcePassivesTest.H(skill);h.weapon=kind;
        for(int i=0;i<links.length;i++)h.link(links[i],PassiveSlot.values()[i]);
        var result=h.cast();assertTrue(result.committed(),result.code());return h;
    }
    List<ProjectileExecutionPlan> plans(Stage11ResourcePassivesTest.H h,long now){
        var p=h.last().profile().projectile();return ProjectileExecutionPlan.launchBatch(h.last(),h.actor,Vec3.ZERO,Vec3.FORWARD,p.configId(),p.speed(),now);
    }
    @ParameterizedTest @CsvSource({"blunderbuss_shot,GUN,STAMINA,6,3,8,.22", "explosive_flask,BOMB,STAMINA,8,8,1,0",
            "bomb_toss,BOMB,STAMINA,8,9,1,0", "arcane_missiles,WAND,MANA,16,7,5,.42", "fireball,STAFF,MANA,10,1,1,0"})
    void oneSharedServiceCommitForAuthoredPattern(String skill,String weapon,String resource,double cost,double cooldown,int count,double coefficient){
        var h=cast(skill,weapon);var p=h.last().profile().projectile();
        assertEquals(100-cost,h.current(ResourceType.valueOf(resource)));assertEquals(1,h.cooldownSaves);
        assertEquals(cooldown,h.last().profile().cooldownSeconds());assertEquals(coefficient,p.coefficient());
        assertEquals(count,plans(h,10).size());assertEquals(0,h.last().profile().windupSeconds());
        h.service.terminate(h.last(),"FIXTURE_NATIVE_COMPLETION_NOT_CLAIMED");
        assertEquals("COOLDOWN_ACTIVE",h.cast().code());assertEquals(1,h.contexts.size());assertEquals(1,h.cooldownSaves);
    }
    @Test void bombCommandBypassesActiveCooldownWithoutSecondPayment(){
        var h=new Stage11ResourcePassivesTest.H("bomb_toss"){
            boolean commandAvailable;
            @Override public SkillExecutionResult commandExisting(Stage04SkillProfile profile,CompiledSkillPlan plan,SkillExecutionRequest request){
                return commandAvailable?SkillExecutionResult.committed("EXISTING_INSTANCE_COMMANDED",0,0):null;
            }
        };h.weapon="BOMB";assertTrue(h.cast().committed());assertEquals(92,h.current(ResourceType.STAMINA));assertEquals(1,h.cooldownSaves);
        h.commandAvailable=true;var command=h.cast();assertTrue(command.committed());assertEquals("EXISTING_INSTANCE_COMMANDED",command.code());
        assertEquals(92,h.current(ResourceType.STAMINA));assertEquals(1,h.cooldownSaves);assertEquals(1,h.contexts.size());
    }
    @Test void snipeCannotPromoteDevelopmentFallbackIntoAProductionCast(){
        var h=new Stage11ResourcePassivesTest.H("snipe");h.weapon="BOW";
        // Retained test identity; owner explicitly replaced the old native-maximum requirement.
        // Owner now explicitly requires zero drop; retain audited speed/radius, paid release and authored range.
        assertTrue(h.cast().committed());assertEquals(1,h.contexts.size());
        assertEquals(88,h.current(ResourceType.STAMINA));assertEquals(1,h.cooldownSaves);
        var p=profiles.require("snipe").projectile();assertEquals(2,p.coefficient());assertTrue(p.fullyCharged());
        assertEquals(1,p.ammoQuantity());assertEquals("Weapon_Arrow_Crude",p.ammoItemId());
        assertEquals(48,p.maxDistance());assertEquals(85,p.speed());assertEquals(.075,p.radius());assertEquals(0,p.gravity());
        assertEquals("",p.details().nativeCapabilityGate());
    }
    @Test void deterministicEightPelletsAreSymmetricAndIndependentlyHitTheSameVictim(){
        var h=cast("blunderbuss_shot","GUN");var list=plans(h,0);assertEquals(list,plans(h,0));
        assertEquals(8,list.stream().map(ProjectileExecutionPlan::projectileInstanceId).distinct().count());
        double sumX=0,sumY=0;
        for(var p:list){assertEquals(0,p.generation());assertEquals(h.last().rootCastId(),p.rootCastId());assertEquals(h.last().skillInstanceId(),p.skillInstanceId());
            assertEquals(22,p.velocity().length(),1e-9);assertEquals(8d/22,p.maxLifetimeSeconds(),1e-9);assertEquals(.04,p.radius());
            sumX+=p.velocity().x();sumY+=p.velocity().y();var instance=new ProjectileInstance(p);
            assertTrue(instance.acceptTarget("same-target"));assertFalse(instance.acceptTarget("same-target"));}
        assertEquals(0,sumX,1e-9);assertEquals(0,sumY,1e-9);assertEquals(1d/8,HitProcRuntime.coefficient(h.last()));
    }
    @Test void nativeGunBaseIsTheAuditedBulletNotTheMeleeSwingOrCost(){
        var e=com.inigmasgames.hytalerpg.combat.power.NativeItemPowerRegistry.loadCanonical().find("Weapon_Gun_Blunderbuss").orElseThrow();
        assertEquals(200,e.basePower());assertEquals("Damage",e.sourceProperty());assertEquals("GUN",e.kind());
        assertEquals("STAMINA",profiles.require("blunderbuss_shot").resourceType());assertFalse(profiles.require("blunderbuss_shot").projectile().requiresAmmo());
    }
    @Test void volleyMultipliesAuthoredPelletsButNotRootPayments(){
        var h=cast("blunderbuss_shot","GUN","volley");var list=plans(h,0);assertEquals(24,list.size());assertEquals(1d/24,HitProcRuntime.coefficient(h.last()));
        var registry=new ProjectileLifecycleRegistry();registry.registerAll(list.stream().map(ProjectileInstance::new).toList(),h.last().effects().projectileLifetime());
        assertEquals(24,registry.size());assertEquals("OWNER_PROJECTILE_BUDGET",registry.admission(h.actor,1));assertEquals(1,h.cooldownSaves);
    }
    @Test void futureBarragePromisesIncludeEveryAuthoredMissile(){
        var h=cast("arcane_missiles","WAND","barrage");var pattern=h.last().profile().projectile().details().pattern();
        assertEquals(15,pattern.rootLaunches(h.last().compiledPlan()));var registry=new ProjectileLifecycleRegistry();
        registry.registerAll(plans(h,0).stream().map(ProjectileInstance::new).toList());assertEquals(15,registry.spent(h.actor,h.last().rootCastId()));
        assertEquals("OWNER_PROJECTILE_BUDGET",registry.admission(h.actor,10));assertEquals("PASS",registry.admission(h.actor,9));
    }
    @Test void invalidFortyFiveMissileLaunchIsRejectedAtomicallyByOwnerCapacity(){
        var h=cast("arcane_missiles","WAND","volley","barrage");var registry=new ProjectileLifecycleRegistry();
        assertEquals(45,h.last().profile().projectile().details().pattern().rootLaunches(h.last().compiledPlan()));
        assertEquals("OWNER_PROJECTILE_BUDGET",registry.admission(h.actor,45));
        assertThrows(IllegalStateException.class,()->registry.registerAll(plans(h,0).stream().map(ProjectileInstance::new).toList()));
        assertEquals(0,registry.size());assertEquals(0,registry.rootCount());
    }
    @Test void fiveMissilesUseFiveDeadlinesAndOneFifthProcs(){
        var h=cast("arcane_missiles","SPELLBOOK");var list=plans(h,1_000_000_000);
        for(int i=0;i<5;i++){assertEquals(1_000_000_000+i*80_000_000L,list.get(i).spawnTimestampNanos());assertEquals(26,list.get(i).maxDistance());assertEquals(2,list.get(i).maxLifetimeSeconds());assertEquals(0,list.get(i).generation());}
        assertEquals(.2,HitProcRuntime.coefficient(h.last()));
    }
    @Test void queueRemovesDueEntriesBeforeAnyNativeCallbackAndCannotRepeatThem(){
        var h=cast("arcane_missiles","WAND");var list=plans(h,0);var queue=new ProjectileLaunchQueue<String>();
        list.forEach(p->queue.add(new ProjectileInstance(p),"context"));assertEquals(5,queue.size());
        assertEquals(1,queue.due(h.actor,0).size());assertTrue(queue.due(h.actor,0).isEmpty());
        assertTrue(queue.due(UUID.randomUUID(),80_000_000).isEmpty());assertEquals(1,queue.due(h.actor,80_000_000).size());
        var remaining=queue.due(h.actor,320_000_000);assertEquals(3,remaining.size());assertEquals(0,queue.size());assertTrue(queue.due(h.actor,Long.MAX_VALUE).isEmpty());
    }
    @Test void duplicateQueueEntryRejectsWithoutReplacingOwner(){
        var h=cast("arcane_missiles","WAND");var instance=new ProjectileInstance(plans(h,0).getFirst());var queue=new ProjectileLaunchQueue<String>();
        queue.add(instance,"first");assertThrows(IllegalStateException.class,()->queue.add(instance,"second"));
        assertEquals("first",queue.due(h.actor,0).getFirst().owner());
    }
    @Test void ownerCleanupCancelsQueuedInstancesAndAllReservedCapacity(){
        var h=cast("arcane_missiles","WAND");var list=plans(h,0).stream().map(ProjectileInstance::new).toList();
        var registry=new ProjectileLifecycleRegistry();var queue=new ProjectileLaunchQueue<String>();registry.registerAll(list);list.forEach(i->queue.add(i,"context"));
        queue.cancel(h.actor);assertEquals(5,registry.removeOwnedBy(h.actor).size());assertEquals(0,queue.size());assertEquals(0,registry.rootCount());assertEquals(0,registry.size());
    }
    @ParameterizedTest @CsvSource({"14,18", "15,20"})
    void lowArcHitsItsSelectedEndpointAtExactAuthoredSpeed(double speed,double reach){
        var target=new Vec3(0,0,reach);var solution=ProjectileBallistics.low(Vec3.ZERO,target,speed,9.81,reach,3).orElseThrow();
        assertEquals(speed,solution.velocity().length(),1e-9);assertEquals(0,solution.at(solution.flightSeconds()).distanceSquared(target),1e-16);
        assertTrue(solution.flightSeconds()<=3);assertTrue(solution.clear((a,b)->true));
    }
    @Test void unreachableHighTargetAndOverRangeRejectBeforeAThrowExists(){
        assertTrue(ProjectileBallistics.low(Vec3.ZERO,new Vec3(0,10,18),14,9.81,18,3).isEmpty());
        assertTrue(ProjectileBallistics.low(Vec3.ZERO,new Vec3(0,0,19),14,9.81,18,3).isEmpty());
        assertTrue(ProjectileBallistics.low(Vec3.ZERO,new Vec3(0,0,18),14,9.81,18,.1).isEmpty());
    }
    @Test void wallAlongBallisticArcRejectsEvenWhenTheEndpointsAreClear(){
        var solution=ProjectileBallistics.low(Vec3.ZERO,new Vec3(0,0,18),14,9.81,18,3).orElseThrow();
        assertFalse(solution.clear((a,b)->!(a.z()<=9&&b.z()>=9)));
    }
    @Test void ballisticReachIsPlacementDistanceNotAPathLengthThatCutsOffTheArc(){
        var h=cast("explosive_flask","BOMB");var p=plans(h,0).getFirst();assertEquals(3,p.maxLifetimeSeconds());
        assertEquals(14*3+.5*9.81*9,p.maxDistance(),1e-9);assertEquals(18,h.last().profile().projectile().maxDistance());
    }
    @ParameterizedTest @ValueSource(doubles={Double.NaN,Double.POSITIVE_INFINITY,0,-1})
    void invalidBallisticNumbersCannotEnterNativePhysics(double bad){assertThrows(IllegalArgumentException.class,()->ProjectileBallistics.low(Vec3.ZERO,Vec3.FORWARD,bad,9.81,18,3));}
    @Test void firstGroundContactStartsOneFuseAndDuplicateContactsCannotExtendIt(){
        var d=new ProjectileDetonation(profiles.require("bomb_toss").projectile().details().explosion());var ground=new Vec3(1,2,3);
        assertTrue(d.contact(ground,false,10).isEmpty());assertTrue(d.grounded());assertTrue(d.contact(new Vec3(9,9,9),false,11).isEmpty());
        assertTrue(d.tick(ground,11.499999,false).isEmpty());assertEquals(ground,d.tick(ground,11.5,false).orElseThrow());
        assertTrue(d.tick(ground,20,true).isEmpty());assertTrue(d.contact(ground,true,20).isEmpty());
    }
    @Test void eligibleEnemyImpactWinsThePendingBombFuseOnce(){
        var d=new ProjectileDetonation(profiles.require("bomb_toss").projectile().details().explosion());d.contact(Vec3.ZERO,false,0);
        assertEquals(Vec3.FORWARD,d.contact(Vec3.FORWARD,true,.5).orElseThrow());assertTrue(d.tick(Vec3.ZERO,1.5,false).isEmpty());
    }
    @Test void bombAirborneDeadlineDetonatesButCancellationDoesNot(){
        var p=profiles.require("bomb_toss").projectile().details().explosion();var d=new ProjectileDetonation(p);
        assertTrue(d.tick(Vec3.FORWARD,2.999,false).isEmpty());assertEquals(Vec3.FORWARD,d.tick(Vec3.FORWARD,3,true).orElseThrow());
        var cancelled=new ProjectileDetonation(p);cancelled.contact(Vec3.ZERO,false,0);cancelled.cancel();assertTrue(cancelled.tick(Vec3.ZERO,50,true).isEmpty());
    }
    @Test void flaskImpactsImmediatelyAndExpiryRemainsHarmless(){
        var p=profiles.require("explosive_flask").projectile().details().explosion();var d=new ProjectileDetonation(p);
        assertEquals(Vec3.ZERO,d.contact(Vec3.ZERO,false,0).orElseThrow());assertTrue(d.contact(Vec3.ZERO,true,0).isEmpty());
        assertTrue(new ProjectileDetonation(p).tick(Vec3.ZERO,3,true).isEmpty());assertEquals(3,p.radius());assertEquals(1.3,p.coefficient());
    }
    @Test void selectedHomingEntityWinsOverAnEnemyCloserToAim(){
        var h=new ProjectileHoming();var pattern=profiles.require("arcane_missiles").projectile().details().pattern();
        var selected=new ProjectileHoming.Target("selected",new Vec3(10,0,10),true);var nearer=new ProjectileHoming.Target("nearer",new Vec3(0,0,2),true);
        var live=Map.of("selected",selected,"nearer",nearer);
        assertEquals("selected",h.authored(0,Vec3.ZERO,Vec3.FORWARD,"selected",Vec3.FORWARD,pattern,()->List.of(nearer),id->Optional.ofNullable(live.get(id))).targetId());
        var update=h.authored(.1,Vec3.ZERO,Vec3.FORWARD,"selected",Vec3.FORWARD,pattern,()->List.of(nearer),id->Optional.ofNullable(live.get(id)));
        assertEquals(Math.cos(Math.toRadians(18)),update.direction().z(),1e-9);
    }
    @Test void fallbackHomingUsesAimRadiusLosAndStableTieOrder(){
        var pattern=profiles.require("arcane_missiles").projectile().details().pattern();Vec3 aim=new Vec3(0,0,10);
        var list=List.of(new ProjectileHoming.Target("z",new Vec3(1,0,10),true),new ProjectileHoming.Target("a",new Vec3(-1,0,10),true),
                new ProjectileHoming.Target("occluded",aim,false),new ProjectileHoming.Target("outside",new Vec3(0,0,16.01),true));
        var homing=new ProjectileHoming();var update=homing.authored(0,Vec3.ZERO,Vec3.FORWARD,null,aim,pattern,()->list,id->list.stream().filter(t->t.id().equals(id)).findFirst());
        assertEquals("a",update.targetId());
    }
    @Test void authoredHomingOverflowRejectsAndNeverSilentlyTruncates(){
        var pattern=profiles.require("arcane_missiles").projectile().details().pattern();
        var targets=java.util.stream.IntStream.range(0,65).mapToObj(i->new ProjectileHoming.Target("t"+i,Vec3.FORWARD,true)).toList();
        assertThrows(IllegalStateException.class,()->new ProjectileHoming().authored(0,Vec3.ZERO,Vec3.FORWARD,null,Vec3.FORWARD,pattern,()->targets,id->Optional.empty()));
    }
    @Test void fireballHasOneExplosionNoDirectHitOrAuthoredBurnAndIndependentCollisionDimensions(){
        var p=profiles.require("fireball").projectile();assertEquals(0,p.coefficient());assertEquals(.9,p.details().explosion().coefficient());
        assertEquals(3.5,p.details().explosion().radius());assertEquals(0,p.details().explosion().burnRadius());assertEquals(.45,p.radius());
        assertTrue(p.statusId().isBlank());assertFalse(p.hasPeriodicStatus());assertTrue(p.details().burnPayoff().active());
        assertEquals(1.25,p.details().burnPayoff().multiplier());assertTrue(p.details().burnPayoff().consumeCasterOwnedBurn());
    }
    @Test void concentrationChangesSplashAndBurnFootprintsNotTheCarrier(){
        var p=new Stage11FoundationTest().effective("fireball","concentration").projectile();
        assertEquals(2.45,p.details().explosion().radius(),1e-9);assertEquals(0,p.details().explosion().burnRadius(),1e-9);assertEquals(.45,p.radius());
    }
    @Test void combustionCannotAttachToFireballBecauseFireballConsumesRatherThanAppliesBurn(){
        assertFalse(new Stage11FoundationTest().accepts("fireball","combustion"));
    }
    @Test void r035FireBoltContractReplacesEveryLegacyBalanceAndUsesInstalledPresentation(){
        var profile=profiles.require("fire_bolt");var p=profile.projectile();var v=p.details().presentation();
        assertEquals("MANA",profile.resourceType());assertEquals(5,profile.resourceCost());assertEquals(.45,profile.cooldownSeconds());
        assertEquals("MAGIC_WEAPON",profile.basePowerSource());assertEquals(.70,p.coefficient());assertNotEquals(.95,p.coefficient());
        assertEquals(26,p.maxDistance());assertEquals(32,p.speed());assertEquals(.22,p.radius());assertEquals(0,p.gravity());
        assertEquals("BURN",p.statusId());assertEquals(4,p.statusSeconds());assertEquals(4,p.periodicTicks());assertFalse(p.details().explosion().active());
        assertEquals("Fire_Charge_Charging1",v.castParticle(),"Pinned pre.2 asset ID has the authored numeric suffix");
        assertEquals("Fire_Projectile",v.projectileParticle());assertEquals("Impact_Explosion",v.impactParticle());
        var h=cast("fire_bolt","WAND");assertEquals(95,h.current(ResourceType.MANA));assertEquals(20,h.last().snapshot().basePower());
        var plan=plans(h,0).getFirst();assertFalse(plan.motion().timedBallistic());assertEquals(32,plan.velocity().length(),1e-9);
    }
    @Test void r035FireballTimedBallisticSolutionHitsEndpointAndRisesThenFalls(){
        var p=profiles.require("fireball").projectile();var motion=p.details().motion();
        assertTrue(motion.timedBallistic());assertEquals(16,motion.horizontalSpeed());assertEquals(12,motion.gravity());
        assertEquals(.65,motion.minimumTravelSeconds());assertEquals(1.4,motion.maximumTravelSeconds());
        var target=new Vec3(0,0,16);var solution=ProjectileBallistics.timed(Vec3.ZERO,target,16,12,22,.65,1.4).orElseThrow();
        assertEquals(1,solution.flightSeconds(),1e-9);assertEquals(0,solution.at(solution.flightSeconds()).distanceSquared(target),1e-16);
        assertTrue(solution.at(.4).y()>0);assertTrue(solution.at(.8).y()<solution.at(.5).y());
        assertTrue(solution.pathLength()>target.length());
    }
    @Test void r035FireballSweptCollisionFindsInterveningWallAndTerminalStatesDetonateOnce(){
        var local=new com.inigmasgames.hytalerpg.execution.area.AreaGeometry.Bounds(new Vec3(-.45,-.45,-.45),new Vec3(.45,.45,.45));
        var wall=new com.inigmasgames.hytalerpg.execution.area.AreaGeometry.Bounds(new Vec3(-1,-1,7.9),new Vec3(1,2,8.1));
        assertTrue(ProjectileSweep.contact(new Vec3(0,1,7),new Vec3(0,1,9),local,wall).isPresent());
        var explosion=profiles.require("fireball").projectile().details().explosion();
        var terrain=new ProjectileDetonation(explosion);assertEquals(new Vec3(0,0,8),terrain.contact(new Vec3(0,0,8),false,0).orElseThrow());
        assertTrue(terrain.contact(Vec3.FORWARD,true,0).isEmpty());
        var endpoint=new ProjectileDetonation(explosion);assertEquals(new Vec3(0,0,22),endpoint.tick(new Vec3(0,0,22),2,true).orElseThrow());
        var cancelled=new ProjectileDetonation(explosion);cancelled.cancel();assertTrue(cancelled.tick(Vec3.FORWARD,2,true).isEmpty());
    }
    @Test void r035FireballPresentationScaleCannotChangeItsMechanicalCollider(){
        var p=profiles.require("fireball").projectile();var v=p.details().presentation();
        assertEquals("Fire_Staff_Activation",v.castParticle());assertEquals("Fire_Charge1",v.projectileParticle());
        assertEquals("Explosion_Medium",v.impactParticle());assertEquals(1,v.projectileScale());assertEquals(1,v.impactScale());
        assertEquals(.45,p.radius());assertEquals(3.5,p.details().explosion().radius());assertTrue(p.details().explosion().continuationBeforeDetonation());
    }
    @Test void r035FireballBurnOwnershipAndConsumptionPreserveOtherCasters(){
        var runtime=new com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime<String,String>();
        UUID first=UUID.randomUUID(),other=UUID.randomUUID(),victim=UUID.randomUUID();var port=new Stage11HitProcTest.D();
        var owned=new com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime.Source(first,"fire_bolt",victim,com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime.Kind.BURN);
        var foreign=new com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime.Source(other,"fire_bolt",victim,com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime.Kind.BURN);
        runtime.apply(owned,"owned","target",.1,10,4,1,1,0,port);runtime.apply(foreign,"foreign","target",.1,10,4,1,1,0,port);
        var captured=runtime.ownedSources(first,victim,com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime.Kind.BURN,0);
        assertEquals(1,captured.size());assertEquals(1.125,.9*profiles.require("fireball").projectile().details().burnPayoff().multiplier(),1e-12);
        assertEquals(1,runtime.consume(captured,0,port));assertTrue(runtime.sourceView(owned,0).isEmpty());assertTrue(runtime.sourceView(foreign,0).isPresent());
    }
    @Test void r035FireballRootLedgerPreventsShotgunButAllowsDifferentVictimsAndCancelledRetry(){
        var budget=new RootEffectBudget(UUID.randomUUID(),"root");
        assertEquals("PASS",budget.claimFireballVictim("spider"));assertEquals("DUPLICATE_FIREBALL_VICTIM",budget.claimFireballVictim("spider"));
        assertEquals("PASS",budget.claimFireballVictim("goblin"));budget.releaseFireballVictim("spider");assertEquals("PASS",budget.claimFireballVictim("spider"));
    }
    @Test void r035GenericProjectilePassivesRemainCompatibleAndForkDescendantsPreserveBallistics(){
        var f=new Stage11FoundationTest();
        for(String skill:List.of("fire_bolt","fireball"))for(String passive:List.of("fork","chain","volley"))assertTrue(f.accepts(skill,passive),skill+"/"+passive);
        var h=cast("fireball","STAFF","fork");var registry=new ProjectileLifecycleRegistry();var root=new ProjectileInstance(plans(h,0).getFirst());registry.register(root);
        var decision=new ProjectileContinuation(registry).afterEnemy(root,new Vec3(0,0,4),Vec3.ZERO,List.of(),1);
        assertEquals(ProjectileContinuation.Action.FORK,decision.action(),decision.reason());assertEquals(2,decision.children().size());
        for(var child:decision.children()){assertEquals(root.plan().rootCastId(),child.plan().rootCastId());assertTrue(child.plan().motion().timedBallistic());assertEquals(root.plan().configId(),child.plan().configId());}
    }
    @Test void invalidPartialExplosionAndPatternsReject(){
        assertThrows(IllegalArgumentException.class,()->new ProjectileExplosion(0,1,0,0,false));
        assertThrows(IllegalArgumentException.class,()->new ProjectileExplosion(3,1,4,0,false));
        assertThrows(IllegalArgumentException.class,()->new ProjectilePattern(9,0,0,0,0,0,false));
        assertThrows(IllegalArgumentException.class,()->new ProjectilePattern(5,0,0,.08,180,0,false));
        assertThrows(IllegalArgumentException.class,()->new ProjectilePattern(5,0,0,.08,180,6,true));
    }
    @Test void nativeFlightClockStartsAtAllocationNotItsEarlierScheduledDeadline(){
        var h=cast("arcane_missiles","WAND");var p=plans(h,1_000_000_000L).get(4);var instance=new ProjectileInstance(p);
        instance.nativeSpawned(1_400_000_000L);
        assertEquals(0,instance.sampleNativeClock(1_400_000_000L).elapsed());
        assertEquals(.5,instance.sampleNativeClock(1_900_000_000L).elapsed(),1e-9);
        assertEquals(1_320_000_000L,p.spawnTimestampNanos(),"Immutable launch deadline remains evidence, not mutable flight state");
        assertThrows(IllegalStateException.class,()->instance.nativeSpawned(2_000_000_000L));
        assertEquals(.5,instance.sampleNativeClock(1_800_000_000L).elapsed(),1e-9);
    }
    @Test void nativeClockCannotBeRebasedAfterObservationOrBeforeDeadline(){
        var h=cast("fireball","STAFF");var p=plans(h,100).getFirst();
        assertThrows(IllegalStateException.class,()->new ProjectileInstance(p).nativeSpawned(99));
        var sampled=new ProjectileInstance(p);sampled.sampleNativeClock(100);
        assertThrows(IllegalStateException.class,()->sampled.nativeSpawned(200));
        var moved=new ProjectileInstance(p);moved.observe(0,Vec3.FORWARD);
        assertThrows(IllegalStateException.class,()->moved.nativeSpawned(200));
    }
    @Test void lateContactsCannotExtendRangeOrBypassNativeSafetyLifetime(){
        var h=cast("fireball","STAFF");var instance=new ProjectileInstance(plans(h,0).getFirst());instance.nativeSpawned(0);
        double path=instance.plan().maxDistance();
        assertTrue(instance.contactWithinRange(new Vec3(0,0,path)));
        assertFalse(instance.contactWithinRange(new Vec3(0,0,path+.001)));
        instance.observe(0,new Vec3(0,0,12));assertEquals(path-12,instance.remainingDistance(),1e-9);
        assertTrue(instance.contactWithinRange(new Vec3(0,0,path)));assertFalse(instance.contactWithinRange(new Vec3(0,0,path+.001)));
        assertTrue(instance.sampleNativeClock(2_000_000_000L).expired());
    }
    @Test void liveAuthoredTargetCannotMoveBeyondCommittedTwentySixMetreLock(){
        assertTrue(ProjectileHoming.withinAuthoredLock(new ProjectileHoming.Target("enemy",new Vec3(0,0,26),true),Vec3.ZERO,26));
        assertFalse(ProjectileHoming.withinAuthoredLock(new ProjectileHoming.Target("enemy",new Vec3(0,0,26.01),true),Vec3.ZERO,26));
        assertFalse(ProjectileHoming.withinAuthoredLock(new ProjectileHoming.Target("enemy",Vec3.FORWARD,false),Vec3.ZERO,26));
        assertThrows(IllegalArgumentException.class,()->ProjectileHoming.withinAuthoredLock(null,Vec3.ZERO,Double.NaN));
    }
    @Test void explicitRuntimeBlockersAreNotAdvertisedAsOnlyAwaitingConnectedQA(){
        var matrix=new Stage11CompatibilityMatrixTest();
        for(String id:List.of("bone_cage")) {
            var skill=matrix.skills.stream().filter(s->s.id().value().equals(id)).findFirst().orElseThrow();
            var result=matrix.assess(skill,List.of(),false);
            assertEquals("COMPILED_PROFILE_WITH_EXPLICIT_RUNTIME_GATE",result.gate());
            assertFalse(result.detail().isBlank());assertNotNull(result.plan());
        }
    }
}
