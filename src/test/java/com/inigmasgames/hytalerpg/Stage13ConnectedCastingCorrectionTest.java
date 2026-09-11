package com.inigmasgames.hytalerpg;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.combat.power.*;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.diagnostics.*;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleEquipmentAdapter;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.projectile.*;
import com.inigmasgames.hytalerpg.execution.strike.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Production coordinator, equipment resolver, geometry and projectile lifecycle with bounded world ports.
 * Does not substitute for connected native animation, carrier spawn or damage evidence. */
class Stage13ConnectedCastingCorrectionTest {
    static final Path ASSETS=Path.of(System.getProperty("user.home"),"AppData/Roaming/Hytale/install/pre-release/package/game/latest/Assets.zip");
    static Map<String,String[]> installedTags(String id) throws Exception {
        String asset=NativeItemPowerRegistry.loadCanonical().find(id).map(NativeItemPowerRegistry.Entry::sourceAsset)
                .orElse("Server/Item/Items/Weapon/Staff/Weapon_Staff_Mithril.json");
        try(var zip=new ZipFile(ASSETS.toFile());var reader=new InputStreamReader(zip.getInputStream(Objects.requireNonNull(zip.getEntry(asset))),StandardCharsets.UTF_8)){
            var tags=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("Tags");
            return new Gson().fromJson(tags,new com.google.gson.reflect.TypeToken<Map<String,String[]>>(){}.getType());
        }
    }
    static class H extends Stage11ResourcePassivesTest.H {
        Item held;final UUID world=UUID.randomUUID();UUID currentWorld=world;
        final StrikeGeometryService geometry=new StrikeGeometryService();
        final RpgProjectileService projectiles=new RpgProjectileService(new ProjectileLifecycleRegistry());
        List<StrikeGeometryService.Candidate<String>> candidates=List.of();ProjectileInstance projectile;
        int queries,swings,resourceWrites;boolean failSummon;java.util.concurrent.CompletableFuture<Void> durable;
        H(String skill){super(skill);}
        public Equipment equipment(){return held==null?super.equipment():new Equipment(held,null);}
        public boolean requiresSpatialCommitContext(){return true;}
        StrikeGeometryService.QueryResult<String> query(Stage04SkillProfile p){queries++;return geometry.query(Vec3.ZERO,Vec3.FORWARD,p.strike(),candidates);}
        public Validation familyPrerequisites(Stage04SkillProfile p,CompiledSkillPlan plan){return p.strike()==null?Validation.pass():StrikeCastPrerequisites.check(()->query(p));}
        public CommittedTarget captureTarget(Stage04SkillProfile p,CompiledSkillPlan plan,SkillExecutionRequest request){
            return new CommittedTarget(world,Vec3.ZERO,Vec3.FORWARD.multiply(p.projectile()==null?2:24),Vec3.FORWARD,null);
        }
        public Validation validateDurableCompletion(SkillExecutionContext c){
            if(c.target()==null||!currentWorld.equals(c.target().worldId()))return Validation.reject("PENDING_WORLD_CHANGED");
            return validateRelease(c);
        }
        public Validation validateRelease(SkillExecutionContext c){return c.profile().strike()==null?Validation.pass():StrikeCastPrerequisites.check(()->query(c.profile()));}
        public java.util.concurrent.CompletionStage<Void> prepareDurable(SkillExecutionContext c){return durable==null?super.prepareDurable(c):durable;}
        public SkillExecutionResult executeStrike(SkillExecutionContext c){contexts.add(c);swings++;return SkillExecutionResult.committed("STRIKE_COMPLETE",query(c.profile()).accepted().size(),0);}
        public SkillExecutionResult executeProjectile(SkillExecutionContext c){
            contexts.add(c);var p=c.profile().projectile();
            projectile=projectiles.onProjectileSpawn(projectiles.buildPlan(c,actor,Vec3.ZERO,c.target().direction(),p.configIdFor(c.equipment().mainHand().weaponKind()),p.speedFor(c.equipment().mainHand().weaponKind()),0));
            return SkillExecutionResult.committed("PROJECTILE_LAUNCHED",0,0);
        }
        public void setCurrent(ResourceType type,double value){super.setCurrent(type,value);resourceWrites++;}
        public com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets captureSummonModifiers(com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets m){
            if(failSummon)throw new IllegalArgumentException("secret-token=/private/account/"+"x".repeat(500));return m;
        }
        List<RpgTraceRecord> trace(){return ((Stage01BTestSupport.RecordingTracer)b.tracer()).records;}
    }
    // N expands Mithril support. Retain the same fail-closed assertions using a genuinely unaudited ID.
    @Test void unauditedStaffReproducesExactMissingMagicPowerThenRejectsBeforeCommit() throws Exception {
        var tags=installedTags("Weapon_Staff_Mithril");assertArrayEquals(new String[]{"Staff"},tags.get("Family"));assertArrayEquals(new String[]{"Weapon"},tags.get("Type"));
        var h=new H("fire_bolt");h.held=HytaleEquipmentAdapter.describe("Unaudited_Staff_Test",tags);
        assertEquals("STAFF",h.held.weaponKind());assertNull(h.held.power().magicPower());
        var exception=assertThrows(IllegalArgumentException.class,()->h.kernel.basePower().resolve(new BasePowerResolver.Request(BasePowerSource.MAGIC_WEAPON,h.held.power(),null)));
        assertEquals("Item has no authored MagicPower: Unaudited_Staff_Test",exception.getMessage());
        assertEquals("EQUIPMENT_POWER_UNAVAILABLE",h.cast().code());assertEquals(0,h.resourceWrites);assertEquals(0,h.cooldownSaves);assertTrue(h.contexts.isEmpty());
        var trace=h.trace().stream().filter(r->r.eventType()==RpgTraceEventType.SKILL_PREPARATION_FAILED).findFirst().orElseThrow();
        assertEquals("EQUIPMENT_POWER_VALIDATION",trace.details().get("failureStage"));assertEquals("MISSING_AUTHORED_MAGIC_POWER",trace.details().get("failureCode"));
        assertEquals("Unaudited_Staff_Test",trace.details().get("itemId"));assertTrue(trace.details().containsKey("rootCastId"));assertTrue(trace.details().containsKey("skillInstanceId"));
        assertTrue(h.trace().stream().noneMatch(r->r.eventType()==RpgTraceEventType.SKILL_VALIDATION_PASS));
    }
    @Test void quickSlashEmptyGeometryCommitsOnePaidZeroHitSwing(){
        var h=new H("quick_slash");var result=h.cast();assertTrue(result.committed(),result.toString());assertEquals(0,result.affectedTargets());assertEquals(1,h.swings);
        assertTrue(h.queries>=3);assertEquals(95,h.current(ResourceType.STAMINA));assertEquals(1,h.resourceWrites);assertEquals(1,h.cooldownSaves);assertNull(h.last().target().entityId());
        assertTrue(h.trace().stream().anyMatch(r->r.eventType()==RpgTraceEventType.SKILL_COMMITTED));assertTrue(h.trace().stream().anyMatch(r->r.eventType()==RpgTraceEventType.EXECUTOR_DISPATCH));
        assertEquals(0,h.b.service().masteryXp(h.actor,"quick_slash"));
    }
    @Test void emptyStrikeStillRejectsWrongEquipmentAndInsufficientStamina(){
        var wrong=new H("quick_slash");wrong.weapon="STAFF";assertEquals("INVALID_MAIN_HAND",wrong.cast().code());assertEquals(0,wrong.queries);
        var poor=new H("quick_slash");poor.current.put(ResourceType.STAMINA,4d);assertEquals("INSUFFICIENT_RESOURCE",poor.cast().code());assertEquals(0,poor.swings);assertEquals(0,poor.resourceWrites);
    }
    @ParameterizedTest @ValueSource(strings={"Weapon_Staff_Crystal_Flame","Weapon_Staff_Crystal_Ice","Weapon_Wand_Wood"})
    void auditedMagicWeaponsCommitLaunchExpireAndNeverRefundMiss(String id) throws Exception {
        var h=new H("fire_bolt");h.held=HytaleEquipmentAdapter.describe(id,installedTags(id));
        var result=h.cast();assertTrue(result.committed(),result.toString());assertNull(h.last().target().entityId());assertNotNull(h.projectile);
        assertEquals(NativeItemPowerRegistry.loadCanonical().find(id).orElseThrow().basePower(),h.last().snapshot().basePower());
        assertEquals(92,h.current(ResourceType.MANA));assertEquals(1,h.resourceWrites);assertEquals(1,h.cooldownSaves);assertEquals(1,h.projectiles.registry().size());
        assertEquals("COOLDOWN_ACTIVE",h.cast().code());assertEquals(1,h.resourceWrites);assertEquals(1,h.cooldownSaves);
        assertTrue(h.projectile.observe(2,new Vec3(0,0,24)).expired());assertTrue(h.projectiles.onForwardTermination(h.projectile,"MAX_RANGE",new Vec3(0,0,24)));
        assertFalse(h.projectiles.onForwardTermination(h.projectile,"MAX_RANGE",new Vec3(0,0,24)));assertEquals(0,h.projectiles.registry().size());assertTrue(h.projectile.hitTargets().isEmpty());
        assertEquals(92,h.current(ResourceType.MANA));assertEquals(1,h.resourceWrites);assertTrue(h.kernel.cooldowns().remaining(h.actor,"fire_bolt")>0);assertEquals(0,h.b.service().masteryXp(h.actor,"fire_bolt"));
        assertTrue(h.trace().stream().noneMatch(r->Set.of(RpgTraceEventType.DAMAGE_APPLIED,RpgTraceEventType.PROJECTILE_ENTITY_HIT).contains(r.eventType())));
    }
    @Test void fireBoltEquipmentResourceAndCooldownGuardsRemain(){
        var wrong=new H("fire_bolt");assertEquals("INVALID_MAIN_HAND",wrong.cast().code());assertEquals(0,wrong.resourceWrites);
        var poor=new H("fire_bolt");poor.weapon="STAFF";poor.current.put(ResourceType.MANA,7d);assertEquals("INSUFFICIENT_RESOURCE",poor.cast().code());assertEquals(0,poor.resourceWrites);assertEquals(0,poor.cooldownSaves);
    }
    @Test void spatialWorldAnchorSurvivesPendingPersistenceAndWorldChangeRejectsWithoutDispatch(){
        for(boolean change:new boolean[]{false,true}){var h=new H("quick_slash");h.durable=new java.util.concurrent.CompletableFuture<>();
            assertEquals(SkillExecutionResult.Status.PENDING,h.cast().status());assertEquals(0,h.swings);assertEquals(0,h.resourceWrites);
            if(change)h.currentWorld=UUID.randomUUID();h.durable.complete(null);var result=h.service.completePersistence(h.actor,h);
            assertEquals(!change,result.committed(),result.toString());assertEquals(change?0:1,h.swings);assertEquals(change?100:95,h.current(ResourceType.STAMINA));
        }
    }
    @Test void scheduledEmptyStrikeKeepsValidPaidMissContract(){
        var h=new H("quick_slash");h.link("skill_delay",PassiveSlot.PASSIVE01);var result=h.cast();assertTrue(result.committed(),result.toString());
        h.time=2;h.service.tickScheduled(h.actor,h);assertEquals(1,h.swings);assertEquals(95,h.current(ResourceType.STAMINA));assertEquals(1,h.resourceWrites);
    }
    @Test void geometryAdmissionRetainsBothOverloadGuardsAndDoesNotSuppressOtherErrors(){
        for(String code:List.of("STRIKE_QUERY_OVERFLOW","STRIKE_TARGET_CAP_OVERFLOW"))assertEquals(code,StrikeCastPrerequisites.check(()->{throw new IllegalStateException(code);}).code());
        assertThrows(IllegalStateException.class,()->StrikeCastPrerequisites.check(()->{throw new IllegalStateException("WORLD_FAILURE");}));
        var h=new H("quick_slash");h.candidates=Collections.nCopies(65,new StrikeGeometryService.Candidate<>("x","x",Vec3.FORWARD,true,false,false));
        assertEquals("STRIKE_QUERY_OVERFLOW",h.cast().code());assertEquals(0,h.resourceWrites);assertEquals(0,h.swings);
    }
    @Test void preparationDiagnosticsRecordStageWithoutRawExceptionOrSensitiveItemData(){
        var h=new H("wolf_summon");h.weapon="SPELLBOOK";h.failSummon=true;
        var result=h.cast();if(result.status()==SkillExecutionResult.Status.PENDING)result=h.service.completeWindup(h.actor,h);
        assertEquals("COMMIT_PREPARATION_FAILED_IllegalArgumentException",result.code());assertEquals(0,h.resourceWrites);
        var r=h.trace().stream().filter(t->t.eventType()==RpgTraceEventType.SKILL_PREPARATION_FAILED).findFirst().orElseThrow();
        assertEquals("SUMMON_MODIFIERS",r.details().get("failureStage"));assertEquals("PREPARATION_EXCEPTION",r.details().get("failureCode"));assertFalse(r.details().toString().contains("secret-token"));
        var unsafe=new SkillExecutionPort.Item("/private/account", "x".repeat(1000),null);
        var fields=PreparationFailureDiagnostics.describe("SNAPSHOT_CONSTRUCTION",Stage04SkillProfiles.loadCanonical(h.b.catalog()).require("wolf_summon"),new SkillExecutionPort.Equipment(unsafe,null),new IllegalArgumentException("secret"));
        assertEquals("OMITTED",fields.get("itemId"));assertTrue(fields.toString().length()<1000);assertFalse(fields.toString().contains("secret"));
    }
    @Test void everyAuthoredStrikeGeometryAllowsZeroCandidatesWithoutWeakeningEntityConnections(){
        var profiles=Stage04SkillProfiles.loadCanonical(Stage01BTestSupport.bundle().catalog());assertEquals(89,profiles.all().size());
        var geometry=new StrikeGeometryService();
        for(var p:profiles.all().values())if(p.strike()!=null)
            assertTrue(StrikeCastPrerequisites.check(()->geometry.query(Vec3.ZERO,Vec3.FORWARD,p.strike(),List.of())).accepted(),p.skillId());
        int entityConnections=0;
        for(var p:profiles.all().values())if(p.connection()!=null&&p.connection().requiresTarget()){
            entityConnections++;var h=new Stage08ConnectionTest.Harness(p.skillId());
            assertEquals("NO_VALID_AIMED_TARGET",h.cast().code(),p.skillId());assertEquals(0,h.resourceWrites);assertTrue(h.contexts.isEmpty());
        }
        assertEquals(4,entityConnections); // Healing Beam also requires an explicitly acquired entity.
    }
    @Test void nativeAdapterWiresSharedAdmissionAndWorldAnchorWithoutRemovingIntrinsicTargetGuards() throws Exception {
        var root=Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale");
        var execution=Files.readString(root.resolve("HytaleSkillExecutionSystem.java"));
        assertTrue(execution.contains("boolean requiresSpatialCommitContext() { return true; }"));
        assertEquals(2,execution.split("StrikeCastPrerequisites.check",-1).length-1);
        assertFalse(execution.contains("COMMITTED_STRIKE_EMPTY"));
        assertFalse(execution.contains("accepted().isEmpty()&&!profile.strike().details().finisher()"));
        assertTrue(execution.contains("if(connection.requiresTarget())"));assertTrue(execution.contains("PENDING_WORLD_CHANGED"));
        assertTrue(execution.contains("pounceTarget == null ? Validation.reject(\"NO_VALID_TARGET\")"));
        assertTrue(Files.readString(root.resolve("HytaleConversionSystem.java")).contains("NO_ELIGIBLE_DOMINATABLE_NATIVE_TARGET"));
        assertTrue(Files.readString(root.resolve("HytaleSummonSystem.java")).contains("NO_VALID_CONSUMABLE_"));
        assertTrue(Files.readString(root.resolve("HytaleSupportSystem.java")).contains("if(profile.support().hostileTarget())SupportNativeEffects.requireTarget"));
    }
}
