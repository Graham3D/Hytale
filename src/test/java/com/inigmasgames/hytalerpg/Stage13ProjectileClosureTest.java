package com.inigmasgames.hytalerpg;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.combat.status.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.hytale.*;
import com.inigmasgames.hytalerpg.execution.projectile.*;
import com.inigmasgames.hytalerpg.execution.strike.*;
import com.inigmasgames.hytalerpg.execution.area.AreaDisplacementPlanner;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

/** Local family, policy and packaged-source fixtures, never connected input/native-impact evidence. */
class Stage13ProjectileClosureTest {
    final Stage04SkillProfiles profiles=Stage04SkillProfiles.loadCanonical(RpgCatalog.loadCanonical());
    static final Gson JSON=new Gson();
    @ParameterizedTest @CsvSource({"spear_toss,SPEAR,STAMINA,7,5,1.15,22,24,.25,PHYSICAL",
        "crossbow_bolt,CROSSBOW,STAMINA,5,2,1.25,40,32,.075,PHYSICAL",
        "web_shot,SWORD,STAMINA,7,7,.25,16,18,.35,PHYSICAL",
        "void_bolt,WAND,MANA,8,1.6,1,21,24,.32,VOID",
        "bone_shard,SPELLBOOK,MANA,8,1.8,1,25,25,.25,NECROTIC",
        "cold_blast,STAFF,MANA,14,5,1.1,19,22,.45,COLD"})
    void sharedExecutorCommitsOneAuthoredCostAndCooldown(String skill,String kind,String resource,double cost,double cooldown,
            double coefficient,double speed,double range,double radius,String element) {
        var h=new Stage11ResourcePassivesTest.H(skill);h.weapon=kind;
        assertTrue(h.cast().committed());var p=h.last().profile();var shot=p.projectile();
        assertEquals(cost,p.resourceCost());assertEquals(cooldown,p.cooldownSeconds());assertEquals(0,p.windupSeconds());
        assertEquals(coefficient,shot.coefficient());assertEquals(speed,shot.speed());assertEquals(range,shot.maxDistance());
        assertEquals(radius,shot.radius());assertEquals(element,shot.details().element());assertEquals(1,shot.targetCap());
        assertEquals(100-cost,h.current(ResourceType.valueOf(resource)),1e-9);assertEquals(1,h.cooldownSaves);
        h.service.terminate(h.last(),"FIXTURE_FLIGHT_COMPLETE");
        assertEquals("COOLDOWN_ACTIVE",h.cast().code());assertEquals(1,h.contexts.size());assertEquals(1,h.cooldownSaves);
        assertEquals(100-cost,h.current(ResourceType.valueOf(resource)),1e-9);
        var plan=new RpgProjectileService(new ProjectileLifecycleRegistry()).buildPlan(h.last(),h.actor,Vec3.ZERO,Vec3.FORWARD,shot.configId(),speed,0);
        assertEquals(range/speed,plan.maxLifetimeSeconds(),1e-12);assertEquals(speed,plan.velocity().length(),1e-12);
        assertEquals(h.last().rootCastId(),plan.rootCastId());assertEquals(h.last().skillInstanceId(),plan.skillInstanceId());
    }
    @ParameterizedTest @ValueSource(strings={"spear_toss","crossbow_bolt","void_bolt","bone_shard","cold_blast"})
    void wrongEquipmentRejectsBeforeChargeOrDispatch(String skill) {
        var h=new Stage11ResourcePassivesTest.H(skill);h.weapon="SHIELD";
        var before=new EnumMap<ResourceType,Double>(ResourceType.class);
        for(var resource:ResourceType.values())before.put(resource,h.current(resource));
        assertFalse(h.cast().committed());assertTrue(h.contexts.isEmpty());assertEquals(0,h.cooldownSaves);
        for(var resource:ResourceType.values())assertEquals(before.get(resource),h.current(resource));
    }
    @Test void handCastWebUsesInnateDexterityAndNoEquipmentOrAmmunition() {
        var h=new Stage11ResourcePassivesTest.H("web_shot") {
            @Override public Equipment equipment(){return new Equipment(null,null);}
        };
        assertTrue(h.cast().committed());assertEquals(20,h.last().snapshot().basePower());
        assertEquals("LIGHT",h.last().profile().scaling());assertEquals("INNATE",h.last().profile().basePowerSource());
        assertFalse(h.last().profile().projectile().requiresAmmo());assertEquals(93,h.current(ResourceType.STAMINA));
    }
    @Test void spearIsVisualCopyButCrossbowHasExactlyOneArrowCommit() {
        assertFalse(profiles.require("spear_toss").projectile().requiresAmmo());
        var bolt=profiles.require("crossbow_bolt").projectile();assertEquals("Weapon_Arrow_Crude",bolt.ammoItemId());
        assertEquals(1,bolt.ammoQuantity());assertEquals(10,bolt.gravity());assertFalse(bolt.fullyCharged());
    }
    @ParameterizedTest @CsvSource({"PHYSICAL,Physical","FIRE,Fire","COLD,Ice","ARCANE,RPG_Arcane","VOID,RPG_Void","NECROTIC,RPG_Necrotic"})
    void payloadElementChoosesNativeCauseNotCarrierTransport(String element,String cause){assertEquals(cause,NativeProjectilePayloads.causeId(element));}
    @Test void priorPilotsNowCarryTheirActualElementsWithoutRebalancing() {
        assertEquals("FIRE",profiles.require("fire_bolt").projectile().details().element());
        assertEquals("COLD",profiles.require("frost_bolt").projectile().details().element());
        assertEquals("ARCANE",profiles.require("arcane_bolt").projectile().details().element());
        assertEquals(.95,profiles.require("fire_bolt").projectile().coefficient());
        assertEquals(.1,profiles.require("fire_bolt").projectile().periodicCoefficient());
        assertThrows(IllegalArgumentException.class,()->NativeProjectilePayloads.causeId("invented"));
    }
    @Test void authoredColdBlastHasTwoStacksAndOneAtomicFrozenThreshold() {
        var h=new Stage11ResourcePassivesTest.H("cold_blast");h.weapon="STAFF";assertTrue(h.cast().committed());
        var id=UUID.randomUUID();var statuses=h.kernel.statuses();int stacks=h.last().profile().projectile().details().chillStacks();
        assertEquals(2,stacks);assertEquals(1,profiles.require("frost_bolt").projectile().details().chillStacks());
        var first=statuses.applyChill(h.actor,h.last().rootCastId(),id,ControlProfile.NORMAL,stacks,false);
        assertEquals(2,first.results().getLast().stacks());
        statuses.applyChill(h.actor,"second",id,ControlProfile.NORMAL,stacks,false);
        assertEquals(4,statuses.inspect(id).active().get(RpgStatusType.CHILL).stacks());
        var threshold=statuses.applyChill(h.actor,"third",id,ControlProfile.NORMAL,stacks,false);
        assertEquals(1,threshold.results().size());assertEquals(StatusService.Outcome.THRESHOLD,threshold.results().getFirst().outcome());
        assertTrue(statuses.inspect(id).active().containsKey(RpgStatusType.FROZEN));assertFalse(statuses.inspect(id).active().containsKey(RpgStatusType.CHILL));
    }
    @Test void deepFreezeComposesOnceAndPreservesAuthoredTwoStacks() {
        var p=new Stage11FoundationTest().effective("cold_blast","deep_freeze");
        assertEquals(2,p.projectile().details().chillStacks());assertEquals(.99,p.projectile().coefficient(),1e-9);
        var h=new Stage11ResourcePassivesTest.H("cold_blast");var target=UUID.randomUUID();
        var result=h.kernel.statuses().applyChill(h.actor,"root",target,ControlProfile.NORMAL,2,true);
        assertEquals(3,result.results().getLast().stacks());assertEquals("BONUS_APPLIED",result.bonusGate());
    }
    @Test void normalWebRootIsExactlyTwoPointFiveSeconds() {
        var h=new Stage11ResourcePassivesTest.H("web_shot");var id=UUID.randomUUID();
        var result=ProjectileControlPolicy.root(h.kernel.statuses(),profiles.require("web_shot").projectile(),id,"root","ordinary",ControlProfile.NORMAL);
        assertEquals(StatusService.Outcome.APPLIED,result.outcome());assertEquals(RpgStatusType.ROOT,result.type());assertEquals(2.5,result.remainingSeconds());
    }
    @Test void eliteDurationAndRollingResistanceRemainSharedPolicy() {
        var h=new Stage11ResourcePassivesTest.H("web_shot");var id=UUID.randomUUID();var payload=profiles.require("web_shot").projectile();
        var elite=new ControlProfile(false,false,false,true);
        assertEquals(1.25,ProjectileControlPolicy.root(h.kernel.statuses(),payload,id,"a","elite",elite).remainingSeconds());
        assertEquals(.625,ProjectileControlPolicy.root(h.kernel.statuses(),payload,id,"b","elite",elite).remainingSeconds());
        assertEquals(.3125,ProjectileControlPolicy.root(h.kernel.statuses(),payload,id,"c","elite",elite).remainingSeconds());
        assertEquals(StatusService.Outcome.REJECTED,ProjectileControlPolicy.root(h.kernel.statuses(),payload,id,"d","elite",elite).outcome());
    }
    @Test void noImplicitBossSlowAndNoProtectedTargetMutation() {
        var h=new Stage11ResourcePassivesTest.H("web_shot");var payload=profiles.require("web_shot").projectile();
        assertTrue(payload.details().bossSlowOptInRoles().isEmpty());
        for(var control:List.of(new ControlProfile(false,true,false),new ControlProfile(true,false,false),new ControlProfile(false,false,true))){
            var id=UUID.randomUUID();assertEquals(StatusService.Outcome.REJECTED,ProjectileControlPolicy.root(h.kernel.statuses(),payload,id,"root","boss",control).outcome());
            assertTrue(h.kernel.statuses().inspect(id).active().isEmpty());assertEquals(0,h.kernel.statuses().strongestSlow(id).magnitude());
        }
    }
    @Test void explicitOptInUsesAuthoredThirtyPercentAndTwoPointFiveSecondsNotFrozenDuration() {
        var h=new Stage11ResourcePassivesTest.H("web_shot");var id=UUID.randomUUID();
        var data=JSON.toJsonTree(profiles.require("web_shot").projectile()).getAsJsonObject();
        data.getAsJsonObject("details").add("bossSlowOptInRoles",JSON.toJsonTree(Set.of("AuditedBossFixture")));
        var payload=JSON.fromJson(data,Stage04SkillProfile.Projectile.class);
        var result=ProjectileControlPolicy.root(h.kernel.statuses(),payload,id,"root","AuditedBossFixture",new ControlProfile(false,true,false));
        assertEquals(StatusService.Outcome.APPLIED,result.outcome());assertEquals(2.5,result.remainingSeconds());
        assertFalse(h.kernel.statuses().inspect(id).active().containsKey(RpgStatusType.ROOT));
        var slow=h.kernel.statuses().strongestSlow(id);assertEquals(.3,slow.magnitude());assertTrue(slow.remainingSeconds()>2.4&&slow.remainingSeconds()<=2.5);
        assertEquals(StatusService.Outcome.REJECTED,ProjectileControlPolicy.root(h.kernel.statuses(),payload,UUID.randomUUID(),"root","AuditedBossFixture",new ControlProfile(true,true,false)).outcome());
    }
    @ParameterizedTest @CsvSource({"Spear,SPEAR,RPG_WEAPON_HEAVY","Gun,GUN,RPG_WEAPON_LIGHT","Axe,BATTLEAXE,RPG_WEAPON_HEAVY","Crossbow,CROSSBOW,RPG_WEAPON_LIGHT","Spellbook,SPELLBOOK,RPG_WEAPON_MAGIC","Bomb,BOMB,RPG_WEAPON_LIGHT"})
    void equipmentUsesResolvedNativeFamily(String family,String kind,String tag){
        assertEquals(kind,HytaleEquipmentAdapter.nativeKind(Map.of("Family",new String[]{family})));
        assertEquals(Set.of(tag),HytaleEquipmentAdapter.weaponTags(kind));
    }
    @Test void absentAmbiguousOrUnknownFamilyCannotBeAuthorizedByAnItemName() {
        for(var tags:List.of(Map.<String,String[]>of(),Map.of("Family",new String[]{"Spear","Gun"}),Map.of("Family",new String[]{"Unknown"})))
            assertEquals("UNKNOWN",HytaleEquipmentAdapter.nativeKind(tags));
    }
    @ParameterizedTest @ValueSource(strings={"spear_toss","crossbow_bolt","web_shot","void_bolt","bone_shard","cold_blast"})
    void carrierSourceContainsNoNativeGameplayAndMatchesExactProfile(String skill) throws Exception {
        var payload=profiles.require(skill).projectile();var config=read("/Server/ProjectileConfigs/RPG/"+payload.configId()+".json");
        assertTrue(config.getAsJsonObject("Interactions").isEmpty());assertFalse(config.has("Parent"));
        assertEquals(payload.speed(),config.get("LaunchForce").getAsDouble());
        assertEquals(payload.gravity(),config.getAsJsonObject("Physics").get("Gravity").getAsDouble());
        var model=read("/Server/Models/Projectiles/"+config.get("Model").getAsString()+".json");
        for(String axis:List.of("X","Y","Z")){
            assertEquals(payload.radius(),model.getAsJsonObject("HitBox").getAsJsonObject("Max").get(axis).getAsDouble());
            assertEquals(-payload.radius(),model.getAsJsonObject("HitBox").getAsJsonObject("Min").get(axis).getAsDouble());
        }
    }
    @Test void noExtraVoidOrNecroticStatusAndNoImplicitColdPenetration(){
        assertEquals("",profiles.require("void_bolt").projectile().statusId());assertEquals("",profiles.require("bone_shard").projectile().statusId());
        assertEquals(1,profiles.require("cold_blast").projectile().targetCap());assertEquals(1,profiles.require("bone_shard").projectile().maximumLifetimeSeconds());
    }
    @Test void impactForceUsesSweptDistanceAndCannotPushThroughWallsOrOffLedges(){
        var wall=AreaDisplacementPlanner.plan(new Vec3(0,0,1),Vec3.ZERO,false,1.5,1,true,(p,d)->p.z()>=1.5?0:1,p->true);
        assertEquals(.5,wall.distance());assertEquals("NATIVE_COLLISION",wall.reason());
        var ledge=AreaDisplacementPlanner.plan(new Vec3(0,0,1),Vec3.ZERO,false,1.5,1,true,(p,d)->1,p->p.z()<1.5);
        assertEquals(.25,ledge.distance());assertEquals("NO_SUPPORTED_PATH",ledge.reason());
        var boss=AreaDisplacementPlanner.plan(new Vec3(0,0,1),Vec3.ZERO,false,1.5,0,true,(p,d)->1,p->true);
        assertEquals(0,boss.distance());assertEquals("CONTROL_RESISTANT",boss.reason());
    }
    @Test void sixtyFiveSecondaryCandidatesRejectExplicitlyWithoutDroppingOneAcceptedTarget(){
        var f=new Stage11StrikeSecondaryTest();var h=f.h("shockwave");
        var rejected=new ArrayList<String>();var p=new Stage11StrikeSecondaryTest.P(){@Override public void rejected(String e,String r){rejected.add(r);}};
        for(int i=0;i<65;i++)p.candidates.add(Stage11StrikeSecondaryTest.target("target"+i,0,0,1));
        assertEquals(0,f.run(h.last(),f.primary(),p));assertTrue(p.delivered.isEmpty());assertEquals(List.of("STRIKE_SECONDARY_QUERY_OVERFLOW"),rejected);
    }
    @Test void profileRejectsInconsistentControlAndNonfiniteValues(){
        var root=JSON.toJsonTree(profiles.require("web_shot").projectile()).getAsJsonObject();
        root.getAsJsonObject("details").addProperty("chillStacks",2);
        assertThrows(RuntimeException.class,()->JSON.fromJson(root,Stage04SkillProfile.Projectile.class));
        assertThrows(IllegalArgumentException.class,()->new Stage04SkillProfile.ProjectileDetails("COLD",6,0,Set.of()));
        assertThrows(IllegalArgumentException.class,()->new Stage04SkillProfile.ProjectileDetails("PHYSICAL",0,Double.NaN,Set.of()));
    }
    static JsonObject read(String path) throws Exception {
        try(var stream=Stage13ProjectileClosureTest.class.getResourceAsStream(path)){
            assertNotNull(stream,path);return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
