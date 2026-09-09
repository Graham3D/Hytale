package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.movement.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.area.AreaGeometry;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pure world-port boundaries: never proof of native player movement, blocking or connected input. */
class Stage13MovementClosureTest {
    final Stage04SkillProfiles profiles=Stage04SkillProfiles.loadCanonical(RpgCatalog.loadCanonical());
    final MovementPlanner planner=new MovementPlanner();
    @ParameterizedTest @CsvSource({"dive_strike,LONGSWORD,STAMINA,8,6,1.15","jump_strike,SWORD,STAMINA,12,7,1.4","charge,SPEAR,STAMINA,14,8,1.5","void_dash,LONGSWORD,MANA,12,6,.65"})
    void realSharedCommitChargesOnlyOneCostAndCooldown(String skill,String weapon,String resource,double cost,double cd,double coefficient){
        var h=new Stage11ResourcePassivesTest.H(skill);h.weapon=weapon;var result=h.cast();assertTrue(result.committed(),result.code());
        assertEquals(100-cost,h.current(ResourceType.valueOf(resource)));assertEquals(1,h.cooldownSaves);assertEquals(cd,h.last().profile().cooldownSeconds());
        assertEquals(coefficient,h.last().profile().strike().coefficient());assertEquals(Stage04SkillProfile.Family.MOVEMENT,h.last().profile().family());
        h.service.terminate(h.last(),"FIXTURE_COMPLETE");assertEquals("COOLDOWN_ACTIVE",h.cast().code());assertEquals(1,h.contexts.size());
    }
    @ParameterizedTest @ValueSource(strings={"dive_strike","jump_strike","charge","void_dash"})
    void ambiguousNativeDispatchFailureRetainsPayment(String skill){
        var h=new Stage11ResourcePassivesTest.H(skill);h.weapon="SPEAR";h.failDispatch=true;var result=h.cast();
        assertTrue(result.committed());assertEquals(SkillExecutionResult.Status.TERMINATED,result.status());assertEquals("EXECUTOR_ERROR",result.code());
        assertEquals(1,h.contexts.size());var p=profiles.require(skill);
        assertEquals(100-p.resourceCost(),h.current(ResourceType.valueOf(p.resourceType())));assertEquals(1,h.cooldownSaves);
    }
    @Test void guardCannotBecomeATimedRpgReductionOrDrain(){
        var h=new Stage11ResourcePassivesTest.H("guard");h.weapon="SWORD";
        assertEquals("NATIVE_GUARD_HELD_ITEM_RELEASE_ROUTE_UNVERIFIED",h.cast().code());assertEquals(0,h.cooldownSaves);assertTrue(h.contexts.isEmpty());
        assertEquals(100,h.current(ResourceType.STAMINA));var p=profiles.require("guard");assertEquals(0,p.resourceCost());assertEquals(.3,p.cooldownSeconds());
        assertTrue(p.reaction().nativeHeld());assertEquals(0,p.reaction().windowSeconds());assertNull(p.support());assertNull(p.strike());
        assertFalse(ProfileComponentPolicy.reactionWindow("guard").orElseThrow());
    }
    @ParameterizedTest @CsvSource({"dive_strike,1.8,2","jump_strike,1.5,2.2"})
    void groundLeapRetainsHeightDurationAndOneLandingComponent(String skill,double apex,double radius){
        var profile=profiles.require(skill);var plan=planner.groundPlan(Vec3.ZERO,new Vec3(0,2,10),profile.movement(),(a,b)->1,p->true);
        assertEquals(.625,plan.durationSeconds());assertEquals(new Vec3(0,2,10),planner.sample(plan,1));assertEquals(1+apex,planner.sample(plan,.5).y());
        assertEquals(radius,profile.strike().range());assertEquals(radius,profile.movement().landingRadius());assertEquals(1,profile.strike().repeats());
    }
    @Test void ungroundedOrOverRangeLeapCannotPassPreflight(){
        var p=profiles.require("dive_strike").movement();
        assertThrows(IllegalArgumentException.class,()->planner.groundPlan(Vec3.ZERO,new Vec3(0,0,10),p,(a,b)->1,x->false));
        assertThrows(IllegalArgumentException.class,()->planner.groundPlan(Vec3.ZERO,new Vec3(0,0,10.01),p,(a,b)->1,x->true));
    }
    @Test void elevatedArcIsSweptRatherThanSkippingAnOverheadObstacle(){
        var p=profiles.require("dive_strike").movement();
        assertThrows(IllegalArgumentException.class,()->planner.groundPlan(Vec3.ZERO,new Vec3(0,0,10),p,(a,b)->a.y()>1.5?0:1,x->true));
    }
    @Test void lowCeilingCanClampLegacyPounceWithoutInventingALandingHit(){
        var p=profiles.require("pounce").movement();var plan=planner.plan(Vec3.ZERO,Vec3.FORWARD,8,p,(a,b)->1);
        var segments=planner.segments(plan,0,1);assertTrue(segments.stream().anyMatch(v->v.y()>1));
        assertEquals(Vec3.ZERO.y(),segments.getLast().y());
    }
    @ParameterizedTest @ValueSource(doubles={Double.NaN,Double.POSITIVE_INFINITY,-.1,1.1})
    void invalidNativeCollisionFractionRejectsRatherThanGrantingMovement(double bad){
        assertThrows(IllegalArgumentException.class,()->planner.plan(Vec3.ZERO,Vec3.FORWARD,4,profiles.require("quickstep").movement(),(a,b)->bad));
    }
    @Test void chargeUsesDistanceOverTwentyNotFixedPointEight(){
        var p=profiles.require("charge").movement();
        assertEquals(.6,planner.plan(Vec3.ZERO,Vec3.FORWARD,12,p,(a,b)->1).durationSeconds());
        assertEquals(.25,planner.plan(Vec3.ZERO,Vec3.FORWARD,2,p,(a,b)->1).durationSeconds());
        assertEquals(3,p.details().knockback());assertTrue(p.details().stopAtFirstEnemy());assertEquals(1.1,p.details().pathWidth());
    }
    @Test void voidDashRemainsNineMetersPointTwoFiveSecondsAndNoImmunity(){
        var p=profiles.require("void_dash");var plan=planner.plan(Vec3.ZERO,Vec3.FORWARD,9,p.movement(),(a,b)->1);
        assertEquals(.25,plan.durationSeconds());assertEquals(9,plan.appliedDistance());assertEquals(1.4,p.movement().details().pathWidth());
        assertEquals("VOID",p.strike().details().element());assertEquals("INNATE",p.basePowerSource());assertEquals(20,p.innateBasePower());assertNull(p.support());assertNull(p.reaction());
    }
    @Test void boundedSegmentsPreserveTheSameArcAcrossFrameSizes(){
        var plan=planner.groundPlan(Vec3.ZERO,new Vec3(0,0,10),profiles.require("dive_strike").movement(),(a,b)->1,x->true);
        Vec3 last=Vec3.ZERO;for(Vec3 p:planner.segments(plan,0,1)){assertTrue(p.subtract(last).length()<=.25+1e-9);last=p;}
        assertEquals(plan.destination(),last);assertEquals(planner.sample(plan,.6),planner.segments(plan,.2,.6).getLast());
    }
    MovementContacts.Target target(String id,double x,double z){return new MovementContacts.Target(id,new AreaGeometry.Bounds(new Vec3(x-.1,0,z-.1),new Vec3(x+.1,2,z+.1)));}
    @Test void firstContactIsGeometricNotRegistryOrder(){
        var c=new MovementContacts();var hits=c.query(Vec3.ZERO,new Vec3(0,0,12),1.1,2.5,List.of(target("far",0,8),target("near",0,3)));
        assertEquals(List.of("near","far"),hits.stream().map(MovementContacts.Contact::id).toList());assertEquals(2.9/12,hits.getFirst().fraction(),1e-9);
    }
    @Test void fullWidthBoundaryAndEndpointNeverGainAnExtraForwardRadius(){
        var c=new MovementContacts();var hits=c.query(Vec3.ZERO,new Vec3(0,0,9),1.4,2.5,List.of(target("inside",.79,3),target("outside",.81,3),target("past-end",0,9.2)));
        assertEquals(List.of("inside"),hits.stream().map(MovementContacts.Contact::id).toList());
    }
    @Test void diagonalPathRetainsFullWidthRatherThanAnExpandedAxisAlignedSquare(){
        var c=new MovementContacts();var forward=new Vec3(1,0,1).horizontalNormalized();var side=new Vec3(-forward.z(),0,forward.x());
        var center=forward.multiply(4).add(side.multiply(.8));var b=new AreaGeometry.Bounds(center,center);
        assertTrue(c.query(Vec3.ZERO,forward.multiply(9),1.4,2.5,List.of(new MovementContacts.Target("outside",b))).isEmpty());
    }
    @Test void crossingTheSameVictimAgainNeverChargesASecondHit(){
        var c=new MovementContacts();var t=target("same",0,2);assertEquals(1,c.query(Vec3.ZERO,new Vec3(0,0,4),1.4,2.5,List.of(t)).size());
        assertTrue(c.claim("same"));assertFalse(c.claim("same"));assertTrue(c.query(new Vec3(0,0,4),Vec3.ZERO,1.4,2.5,List.of(t)).isEmpty());
    }
    @Test void queryOverflowAndLifetimeLedgerOverflowAreExplicit(){
        var c=new MovementContacts();var tooMany=java.util.stream.IntStream.range(0,65).mapToObj(i->target("t"+i,0,2)).toList();
        assertThrows(IllegalStateException.class,()->c.query(Vec3.ZERO,Vec3.FORWARD,1,2.5,tooMany));
        for(int i=0;i<256;i++)assertTrue(c.claim("t"+i));assertThrows(IllegalStateException.class,()->c.claim("overflow"));assertEquals(256,c.size());
    }
    @Test void observedTravelCannotCreditTeleportOrUnconfirmedNativeWrite(){
        var travel=new ValidatedTravel(Vec3.ZERO);travel.observe(Vec3.ZERO,Vec3.FORWARD,new Vec3(0,0,2),.1);assertFalse(travel.valid());assertEquals(0,travel.meters());
    }
    @Test void chargeImpactForceChangesKnockbackAndHitOnly(){
        var p=new Stage11FoundationTest().effective("charge","impact_force");assertEquals(5.25,p.movement().details().knockback());
        assertEquals(1.35,p.strike().coefficient(),1e-9);assertEquals(12,p.movement().maxDistance());assertEquals(1.1,p.movement().details().pathWidth());
    }
    @Test void concentrationScalesTheRealMovementFootprintNotTravelDistance(){
        var p=new Stage11FoundationTest().effective("void_dash","concentration");assertEquals(.98,p.movement().details().pathWidth(),1e-9);assertEquals(.49,p.strike().lineHalfWidth(),1e-9);assertEquals(9,p.movement().maxDistance());
    }
    @Test void installedGuardDataDistinguishesWeaponOverrideFromGenericWielding()throws Exception {
        try(var zip=new java.util.zip.ZipFile(java.nio.file.Path.of(System.getenv("APPDATA"),"Hytale","install","pre-release","package","game","latest","Assets.zip").toFile())){
            var item=asset(zip,"Server/Item/Items/Weapon/Sword/Weapon_Sword_Iron.json");
            var override=item.getAsJsonObject("InteractionVars").getAsJsonObject("Guard_Wield").getAsJsonArray("Interactions").get(0).getAsJsonObject();
            assertEquals("Weapon_Sword_Secondary_Guard_Wield",override.get("Parent").getAsString());assertEquals(10,override.getAsJsonObject("StaminaCost").get("Value").getAsDouble());
            var wield=asset(zip,"Server/Item/Interactions/Weapons/Sword/Attacks/Secondary/Guard/Weapon_Sword_Secondary_Guard_Wield.json");
            assertEquals("Wielding",wield.get("Type").getAsString());assertEquals("Finish",wield.get("OnItemChangeBehavior").getAsString());
            assertEquals(90,wield.getAsJsonObject("AngledWielding").get("AngleDistance").getAsDouble());
            assertEquals(7,wield.getAsJsonObject("StaminaCost").get("Value").getAsDouble());assertEquals("Damage",wield.getAsJsonObject("StaminaCost").get("CostType").getAsString());
            var spear=asset(zip,"Server/Item/Interactions/Weapons/Spear/Attacks/Block/Spear_Block.json");
            assertEquals("Simple",spear.get("Type").getAsString());assertEquals("Spear_Block_Damage",spear.getAsJsonObject("Next").get("Var").getAsString());
        }
    }
    private static com.google.gson.JsonObject asset(java.util.zip.ZipFile zip,String path)throws Exception {
        var entry=zip.getEntry(path);assertNotNull(entry,path);
        try(var reader=new java.io.InputStreamReader(zip.getInputStream(entry),java.nio.charset.StandardCharsets.UTF_8)){return com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();}
    }
    @Test void guardCapabilityDoesNotGetConfusedWithAResolvedOrdinaryReaction(){
        var matrix=new Stage11CompatibilityMatrixTest();var skill=matrix.skills.stream().filter(s->s.id().value().equals("guard")).findFirst().orElseThrow();
        var result=matrix.assess(skill,List.of(),false);assertEquals("COMPILED_PROFILE_WITH_EXPLICIT_RUNTIME_GATE",result.gate());
        assertEquals("NATIVE_GUARD_HELD_ITEM_RELEASE_ROUTE_UNVERIFIED",result.detail());
    }
}
