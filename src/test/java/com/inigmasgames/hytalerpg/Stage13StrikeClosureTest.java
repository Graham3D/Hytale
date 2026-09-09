package com.inigmasgames.hytalerpg;

import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.area.AreaGeometry;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.strike.*;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Pure structure, timing and resource tests: not connected animation, hitbox or input evidence. */
class Stage13StrikeClosureTest {
    final Stage04SkillProfiles profiles = Stage04SkillProfiles.loadCanonical(RpgCatalog.loadCanonical());
    final StrikeGeometryService geometry = new StrikeGeometryService();

    @ParameterizedTest @CsvSource({"spear_thrust,SPEAR,STAMINA,6,1.1,1.1", "dagger_flurry,DAGGER,STAMINA,10,4,.35",
        "maul_swing,MACE,STAMINA,9,2,.9", "scythe_sweep,SWORD,MANA,8,4,1", "spark,SWORD,MANA,7,2,.9",
        "whirlwind,SWORD,STAMINA,16,8,.55"})
    void exactAuthoredRootCommit(String skill, String weapon, String resource, double cost, double cooldown, double coefficient) {
        var h = new Stage11ResourcePassivesTest.H(skill); h.weapon = weapon;
        var outcome = h.cast(); assertEquals(SkillExecutionResult.Status.COMMITTED, outcome.status());
        var p = h.last().profile();
        assertEquals(cost, p.resourceCost()); assertEquals(cooldown, p.cooldownSeconds());
        assertEquals(coefficient, p.strike().coefficient()); assertEquals(2.5, p.strike().details().height());
        assertEquals(100 - cost, h.current(ResourceType.valueOf(resource)), 1e-9);
        assertEquals(100, h.current(ResourceType.valueOf(resource.equals("MANA") ? "STAMINA" : "MANA")));
        assertEquals("COOLDOWN_ACTIVE", h.cast().code()); assertEquals(1, h.contexts.size());
        assertEquals(1, h.cooldownSaves);
    }
    @ParameterizedTest @ValueSource(strings={"spark", "scythe_sweep"})
    void innateMagicDoesNotBorrowHeldWeapon(String skill) {
        var h = new Stage11ResourcePassivesTest.H(skill) {
            @Override public Equipment equipment() { return new Equipment(null, null); }
        };
        assertTrue(h.cast().committed());
        assertEquals(20, h.last().snapshot().basePower());
        assertEquals("MAGIC", h.last().profile().scaling());
        assertEquals(skill.equals("spark") ? "FIRE" : "NECROTIC", h.last().profile().strike().details().element());
        assertTrue(h.last().profile().strike().statusId().isEmpty());
    }
    @Test void noInventedBurnStunOrMultistrikeEligibility() {
        var f = new Stage11FoundationTest();
        assertFalse(f.accepts("spark", "combustion"));
        assertFalse(f.accepts("dagger_flurry", "multistrike"));
        assertFalse(f.accepts("whirlwind", "multistrike"));
        assertEquals("", profiles.require("maul_swing").strike().statusId());
        assertEquals(.9, profiles.require("maul_swing").damageCoefficient());
    }
    @Test void scalingUsesTheActuallyEquippedMixedWeaponClass() {
        assertEquals("WEAPON_CLASS", profiles.require("whirlwind").scaling());
        for (String kind : List.of("SWORD", "LONGSWORD", "BATTLEAXE")) {
            var h = new Stage11ResourcePassivesTest.H("whirlwind"); h.weapon = kind;
            assertTrue(h.cast().committed());
            assertEquals(kind.equals("SWORD") ? "LIGHT" : "HEAVY", h.last().snapshot().weaponClass().name());
        }
    }
    @ParameterizedTest @CsvSource({"dagger_flurry,4,.2,.8,1", "whirlwind,3,.4,1.2,.7"})
    void authoredTicksAndActionLockHaveSeparateEndBoundaries(String skill, int repeats, double interval, double lock, double factor) {
        var s = profiles.require(skill).strike();
        assertEquals(repeats, s.repeats()); assertEquals(interval, s.repeatIntervalSeconds());
        assertEquals(lock, s.details().actionLockSeconds()); assertEquals(factor, s.details().movementFactor());
        var schedule = new StrikeRepeatSchedule(repeats, interval, 0, lock);
        var ledger = new SkillHitLedger(); assertTrue(ledger.accept("root", 0, "victim"));
        for (int i=1; i<repeats; i++) {
            long due = Math.round(interval * i * 1e9);
            assertTrue(schedule.claimDue(due-1).isEmpty());
            int tick = schedule.claimDue(due).orElseThrow(); assertEquals(i, tick);
            assertTrue(ledger.accept("root", tick, "victim")); assertFalse(ledger.accept("root", tick, "victim"));
        }
        assertTrue(schedule.complete()); assertFalse(schedule.complete(Math.round(lock*1e9)-1));
        assertTrue(schedule.complete(Math.round(lock*1e9))); assertTrue(schedule.claimDue(10_000_000_000L).isEmpty());
    }
    @Test void compiledRangePreservesElementAndFullActionWindow() {
        var f = new Stage11FoundationTest();
        var resolved = f.effective("dagger_flurry", "long_reach");
        assertEquals(3, resolved.strike().range());
        assertEquals(profiles.require("dagger_flurry").strike().details(), resolved.strike().details());
        assertEquals("FIRE", f.effective("spark", "long_reach").strike().details().element());
    }
    @Test void spearUsesFullPointEightWidthAndOnlyTwoPointFiveHeight() {
        var s = profiles.require("spear_thrust").strike(); assertEquals(.4, s.lineHalfWidth());
        assertEquals(List.of("edge"), geometry.query(Vec3.ZERO, Vec3.FORWARD, s, List.of(
            point("edge", .4, 2.5, 3.6), point("outside", .40001, 1, 3),
            point("above", 0, 2.5001, 2), point("below", 0, -.001, 2), point("rear", 0, 1, -.01))).accepted().stream().map(StrikeGeometryService.Candidate::stableId).toList());
    }
    @Test void collisionBoundsIntersectEvenWhenEntityOriginIsBeyondReach() {
        var s = profiles.require("spear_thrust").strike();
        var body = new StrikeGeometryService.Candidate<>("large", "large", new Vec3(0,0,4), true, false, false,
                new AreaGeometry.Bounds(new Vec3(-.5,0,3.5),new Vec3(.5,2,4.5)));
        assertEquals(List.of(body), geometry.query(Vec3.ZERO,Vec3.FORWARD,s,List.of(body)).accepted());
    }
    @Test void equalDistanceUsesStableIdentityNotInputOrder() {
        var s=profiles.require("spark").strike(); var a=point("a",1,0,1); var b=point("b",-1,0,1);
        assertEquals(List.of(a,b),geometry.query(Vec3.ZERO,Vec3.FORWARD,s,List.of(b,a)).accepted());
    }
    @Test void sixtyFourAcceptedTargetsAreNeverSilentlyCutToLegacySixteen() {
        var points=new ArrayList<StrikeGeometryService.Candidate<String>>();
        for(int i=0;i<64;i++)points.add(point(String.format("id%02d",i),0,0,1));
        assertEquals(64,geometry.query(Vec3.ZERO,Vec3.FORWARD,profiles.require("quick_slash").strike(),points).accepted().size());
        points.add(point("overflow",0,0,1));
        assertEquals("STRIKE_QUERY_OVERFLOW",assertThrows(IllegalStateException.class,
                ()->geometry.query(Vec3.ZERO,Vec3.FORWARD,profiles.require("quick_slash").strike(),points)).getMessage());
    }
    @Test void declaredLowerTargetCapFailsExplicitlyInsteadOfDroppingAcceptedHits() {
        var s=new Stage04SkillProfile.Strike(Stage04SkillProfile.Geometry.ARC,3,120,0,1,0,1,1,"",0);
        assertEquals("STRIKE_TARGET_CAP_OVERFLOW",assertThrows(IllegalStateException.class,
                ()->geometry.query(Vec3.ZERO,Vec3.FORWARD,s,List.of(point("a",0,0,1),point("b",0,0,2)))).getMessage());
    }
    @Test void assistedSingleTargetIsAnAuthoredSelectionNotAnOverflow() {
        var result=geometry.query(Vec3.ZERO,Vec3.FORWARD,profiles.require("shield_bash").strike(),List.of(point("b",0,0,2),point("a",0,0,1)));
        assertEquals("a",result.accepted().getFirst().stableId());
        assertEquals("SINGLE_TARGET_NOT_SELECTED",result.decisions().getFirst().reason());
    }
    @Test void protectedCollisionTargetCannotBeAccepted() {
        var target=new StrikeGeometryService.Candidate<>("protected","protected",new Vec3(0,0,1),true,true,false);
        assertTrue(geometry.query(Vec3.ZERO,Vec3.FORWARD,profiles.require("spark").strike(),List.of(target)).accepted().isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"resourceCost","cooldownSeconds","windupSeconds","innateBasePower"})
    void nonfiniteTopLevelProfileValuesCannotReachTheKernel(String field) {
        var gson=new Gson();var object=gson.toJsonTree(profiles.require("spark")).getAsJsonObject();
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            object.addProperty(field,value); assertThrows(RuntimeException.class,()->gson.fromJson(object,Stage04SkillProfile.class));
        }
    }
    @ParameterizedTest @ValueSource(strings={"range","angleDegrees","lineHalfWidth","repeatIntervalSeconds","coefficient","statusSeconds"})
    void nonfiniteStrikeInputsCannotEscapeNumericValidation(String field) {
        var gson=new Gson();var object=gson.toJsonTree(profiles.require("spark")).getAsJsonObject();
        object.getAsJsonObject("strike").addProperty(field,Double.NaN);
        assertThrows(RuntimeException.class,()->gson.fromJson(object,Stage04SkillProfile.class));
    }
    static StrikeGeometryService.Candidate<String> point(String id,double x,double y,double z) {
        return new StrikeGeometryService.Candidate<>(id,id,new Vec3(x,y,z),true,false,false);
    }
}
