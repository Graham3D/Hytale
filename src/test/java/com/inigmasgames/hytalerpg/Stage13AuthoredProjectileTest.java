package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.domain.PassiveSlot;
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
            "bomb_toss,BOMB,STAMINA,8,9,1,0", "arcane_missiles,WAND,MANA,16,7,5,.42", "fireball,STAFF,MANA,18,7,1,1.55"})
    void oneSharedServiceCommitForAuthoredPattern(String skill,String weapon,String resource,double cost,double cooldown,int count,double coefficient){
        var h=cast(skill,weapon);var p=h.last().profile().projectile();
        assertEquals(100-cost,h.current(ResourceType.valueOf(resource)));assertEquals(1,h.cooldownSaves);
        assertEquals(cooldown,h.last().profile().cooldownSeconds());assertEquals(coefficient,p.coefficient());
        assertEquals(count,plans(h,10).size());assertEquals(0,h.last().profile().windupSeconds());
        h.service.terminate(h.last(),"FIXTURE_NATIVE_COMPLETION_NOT_CLAIMED");
        assertEquals("COOLDOWN_ACTIVE",h.cast().code());assertEquals(1,h.contexts.size());assertEquals(1,h.cooldownSaves);
    }
    @Test void snipeCannotPromoteDevelopmentFallbackIntoAProductionCast(){
        var h=new Stage11ResourcePassivesTest.H("snipe");h.weapon="BOW";
        assertEquals("NATIVE_BOW_MAX_RANGE_UNVERIFIED",h.cast().code());assertTrue(h.contexts.isEmpty());
        assertEquals(100,h.current(ResourceType.STAMINA));assertEquals(0,h.cooldownSaves);
        var p=profiles.require("snipe").projectile();assertEquals(2,p.coefficient());assertTrue(p.fullyCharged());
        assertEquals(1,p.ammoQuantity());assertEquals("Weapon_Arrow_Crude",p.ammoItemId());
        assertEquals(48,p.maxDistance());assertEquals(45,p.speed());assertEquals(.1,p.radius());assertEquals(0,p.gravity());
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
    @Test void fireballHasSeparateDirectSplashBurnAndCollisionDimensions(){
        var p=profiles.require("fireball").projectile();assertEquals(1.55,p.coefficient());assertEquals(.9,p.details().explosion().coefficient());
        assertEquals(4,p.details().explosion().radius());assertEquals(3,p.details().explosion().burnRadius());assertEquals(.5,p.radius());
        assertEquals("BURN",p.statusId());assertEquals(5,p.statusSeconds());assertEquals(.1,p.periodicCoefficient());assertEquals(5,p.periodicTicks());
    }
    @Test void concentrationChangesSplashAndBurnFootprintsNotTheCarrier(){
        var p=new Stage11FoundationTest().effective("fireball","concentration").projectile();
        assertEquals(2.8,p.details().explosion().radius(),1e-9);assertEquals(2.1,p.details().explosion().burnRadius(),1e-9);assertEquals(.5,p.radius());
    }
    @Test void combustionChangesBothAuthoredHitComponentsNotTheBurnBase(){
        var p=new Stage11FoundationTest().effective("fireball","combustion").projectile();
        assertEquals(1.55*.85,p.coefficient(),1e-9);assertEquals(.9*.85,p.details().explosion().coefficient(),1e-9);
        assertEquals(.1,p.periodicCoefficient());assertEquals(6.25,p.statusSeconds());
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
        assertTrue(instance.contactWithinRange(new Vec3(0,0,30)));
        assertFalse(instance.contactWithinRange(new Vec3(0,0,30.001)));
        instance.observe(0,new Vec3(0,0,12));assertEquals(18,instance.remainingDistance());
        assertTrue(instance.contactWithinRange(new Vec3(0,0,30)));assertFalse(instance.contactWithinRange(new Vec3(0,0,30.001)));
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
        for(String id:List.of("snipe","bone_cage")) {
            var skill=matrix.skills.stream().filter(s->s.id().value().equals(id)).findFirst().orElseThrow();
            var result=matrix.assess(skill,List.of(),false);
            assertEquals("COMPILED_PROFILE_WITH_EXPLICIT_RUNTIME_GATE",result.gate());
            assertFalse(result.detail().isBlank());assertNotNull(result.plan());
        }
    }
}
