package com.inigmasgames.hytalerpg.gear;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.status.*;
import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.gear.GearCombatEffects.Channel;
import com.inigmasgames.hytalerpg.links.*;
import com.inigmasgames.hytalerpg.progress.*;
import com.inigmasgames.hytalerpg.MasterAffixFinalAuraHelper;
import java.util.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

/** Offline owner controls. Every positive source is admitted by the public equipment path. */
public final class MasterAffixFinalControlsTest {
    private record Pair(GearInstance affixed, GearInstance unrolled, GearEffectSnapshot positive,
                        GearEffectSnapshot control) { }

    private static GearEffectSnapshot admit(GearInstance item) {
        var result=GearEquipmentResolution.resolve(99,MasterAffixTestEquipment.BASELINE,
                List.of(new GearEquipmentResolution.Candidate(item,true,true,true)));
        assertEquals(List.of(item),result.validItems(),result.rejected().toString());
        assertEquals(item.affixes().isEmpty()?0:item.affixes().getFirst().value(),
                item.affixes().isEmpty()?0:result.effects().snapshot().value(item.affixes().getFirst().familyId()),1e-8);
        return result.effects().snapshot();
    }
    private static Pair pair(String id) {
        assertTrue(GearAffixRuntime.ENABLED.contains(id),id);
        var affixed=MasterAffixTestEquipment.fixture(id,false);
        var unrolled=MasterAffixTestEquipment.fixture(id,true);
        assertEquals(affixed.baseId(),unrolled.baseId(),id);
        assertEquals(1,affixed.affixes().size(),id);
        assertTrue(unrolled.affixes().isEmpty(),id);
        assertTrue(GearDropGenerator.eligible(GearCatalog.load().affix(id),
                GearCatalog.load().base(affixed.baseId())),id);
        var withdrawn=GearEquipmentResolution.resolve(99,MasterAffixTestEquipment.BASELINE,
                List.of(new GearEquipmentResolution.Candidate(affixed,false,true,true)));
        assertTrue(withdrawn.validItems().isEmpty(),id);
        assertEquals("NOT_EQUIPPED",withdrawn.rejected().get(affixed.identity()),id);
        assertTrue(withdrawn.effects().snapshot().empty(),id);
        return new Pair(affixed,unrolled,admit(affixed),admit(unrolled));
    }
    private static JsonObject nativeManifest(String family) {
        try(var stream=MasterAffixFinalControlsTest.class.getResourceAsStream(
                "/rpg/gear/action-templates/native-primary/manifest.json")) {
            assertNotNull(stream);
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject(family);
        } catch(java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    @Test void wa008NativeGearActionProfilesPrimaryChangesRenderedAnimationFromAcceptedUnrolled() {
        var p=pair("WA-008");
        assertThrows(IllegalArgumentException.class,()->NativeGearActionProfiles.primary(p.control(),p.unrolled().identity()));
        var profile=NativeGearActionProfiles.primary(p.positive(),p.affixed().identity());
        assertEquals(p.affixed().affixes().getFirst().value(),profile.frozenRatePercent(),1e-8);
        assertEquals(0,profile.reachMetres());
        assertEquals(p.affixed().identity(),profile.itemId());
        assertEquals(p.positive().revision(),profile.snapshotRevision());
        assertNotNull(NativePrimaryActionAssets.variantId(new GearBindings().require(p.affixed().baseId()).carrier(p.affixed().rarity()),profile));
        String family=NativeGearActionProfiles.family(p.affixed().baseId(),false);
        var manifest=nativeManifest(family);
        String animation=manifest.getAsJsonArray("attackAnimations").get(0).getAsString();
        var stock=JsonParser.parseString(NativePrimaryActionAssets.renderAnimations(family,manifest,0))
                .getAsJsonObject().getAsJsonObject("Animations").getAsJsonObject(animation);
        var changed=JsonParser.parseString(NativePrimaryActionAssets.renderAnimations(family,manifest,profile.frozenRatePercent()))
                .getAsJsonObject().getAsJsonObject("Animations").getAsJsonObject(animation);
        assertNotEquals(stock.get("Speed"),changed.get("Speed"));
    }

    @Test void wa014NativeGearActionProfilesPrimaryChangesRenderedReachFromAcceptedUnrolled() {
        var p=pair("WA-014");
        assertThrows(IllegalArgumentException.class,()->NativeGearActionProfiles.primary(p.control(),p.unrolled().identity()));
        var profile=NativeGearActionProfiles.primary(p.positive(),p.affixed().identity());
        assertEquals(p.affixed().affixes().getFirst().value(),profile.reachMetres(),1e-8);
        assertEquals(0,profile.frozenRatePercent());
        String family=NativeGearActionProfiles.family(p.affixed().baseId(),false);
        var manifest=nativeManifest(family);
        boolean changedSelector=false;
        for(var entry:manifest.getAsJsonArray("nodes")) {
            String node=entry.getAsString();
            var stock=JsonParser.parseString(NativePrimaryActionAssets.renderNode(family,node,Map.of(),manifest,
                    0,0,"test_Animations")).getAsJsonObject();
            if(!stock.has("Selector")||!stock.getAsJsonObject("Selector").has("EndDistance"))continue;
            var changed=JsonParser.parseString(NativePrimaryActionAssets.renderNode(family,node,Map.of(),manifest,
                    0,profile.reachMetres(),"test_Animations")).getAsJsonObject();
            assertTrue(changed.getAsJsonObject("Selector").get("EndDistance").getAsDouble()
                    >stock.getAsJsonObject("Selector").get("EndDistance").getAsDouble());
            changedSelector=true;
            break;
        }
        assertTrue(changedSelector,"No actual melee selector rendered for "+family);
    }

    @Test void wa016NativeAffixProjectileTravelLaunchExtendsSamePathOnlyForAcceptedRoll() {
        var p=pair("WA-016");
        assertNull(NativeAffixProjectileTravel.launch(p.control(),p.unrolled().identity(),10,20,2,false));
        var launch=NativeAffixProjectileTravel.launch(p.positive(),p.affixed().identity(),10,20,2,false);
        assertNotNull(launch);
        assertTrue(launch.maximumTravel()>20);
        assertEquals(1,launch.speedMultiplier());
        var path=new NativeAffixProjectileTravel.Path(launch,new Vector3d());
        assertFalse(path.afterNativeMove(new Vector3d(20,0,0)));
        assertTrue(path.contactWithinBudget(new Vector3d(launch.maximumTravel(),0,0)));
        assertTrue(path.afterNativeMove(new Vector3d(launch.maximumTravel(),0,0)));
        assertNull(NativeAffixProjectileTravel.launch(p.positive(),p.affixed().identity(),10,20,2,true));
    }

    private static Stream<Arguments> statusCases() { return Stream.of(
            Arguments.of("WA-053",RpgStatusType.BLEED,Channel.PHYSICAL),
            Arguments.of("WA-054",RpgStatusType.BURN,Channel.FIRE),
            Arguments.of("WA-055",RpgStatusType.POISON,Channel.EARTH),
            Arguments.of("WA-057",RpgStatusType.ELECTRIFIED,Channel.LIGHTNING),
            Arguments.of("WA-058",RpgStatusType.SLOW,Channel.PHYSICAL),
            Arguments.of("WA-059",RpgStatusType.STUN,Channel.PHYSICAL),
            Arguments.of("WA-060",RpgStatusType.SILENCE,Channel.PHYSICAL),
            Arguments.of("WA-061",RpgStatusType.BLIND,Channel.PHYSICAL),
            Arguments.of("WA-062",RpgStatusType.FEAR,Channel.PHYSICAL),
            Arguments.of("WA-063",RpgStatusType.ROOT,Channel.PHYSICAL)); }

    @ParameterizedTest(name="gearStatusRuntimeAdmitSameHitUnrolledAndProtectedOrChanceMiss({0})")
    @MethodSource("statusCases")
    void gearStatusRuntimeAdmitSameHitUnrolledAndProtectedOrChanceMiss(String id,RpgStatusType type,Channel channel) {
        var p=pair(id);
        var actor=UUID.randomUUID();var victim=UUID.randomUUID();
        var hit=new GearStatusRuntime.AppliedHit(actor,"root","strike",victim,true,true,true,false,10,1,Map.of(channel,10d));
        var positiveStatuses=new StatusService(CombatBalanceProfile.loadCanonical(),()->0);
        var controlStatuses=new StatusService(CombatBalanceProfile.loadCanonical(),()->0);
        var positivePeriodic=new PeriodicStatusRuntime<String,String>();
        var controlPeriodic=new PeriodicStatusRuntime<String,String>();
        PeriodicStatusRuntime.Port<String,String> port=new PeriodicStatusRuntime.Port<>() {
            public boolean tick(PeriodicStatusRuntime.Source s,String c,String t,int i,double coefficient,double seconds){return true;}
            public void changed(PeriodicStatusRuntime.Source s,String t,PeriodicStatusRuntime.View v) { }
        };
        GearStatusRuntime.PeriodicAdmission positiveOwner=(kind,h,source)->GearStatusRuntime.applyPeriodic(
                positivePeriodic,new PeriodicStatusRuntime.Source(actor,"root",victim,kind),"cast","victim",
                10,10,4,1,1,0,port,source);
        GearStatusRuntime.PeriodicAdmission controlOwner=(kind,h,source)->GearStatusRuntime.applyPeriodic(
                controlPeriodic,new PeriodicStatusRuntime.Source(actor,"root",victim,kind),"cast","victim",
                10,10,4,1,1,0,port,source);
        var positive=GearStatusRuntime.admit(positiveStatuses,new GearStatusRuntime.Contacts(),hit,type,
                ControlProfile.NORMAL,p.positive(),p.positive(),GearEffectSnapshot.EMPTY,0,0,0,0,positiveOwner,
                "fixture_skill",false,1);
        var control=GearStatusRuntime.admit(controlStatuses,new GearStatusRuntime.Contacts(),hit,type,
                ControlProfile.NORMAL,p.control(),p.control(),GearEffectSnapshot.EMPTY,0,0,0,0,controlOwner,
                "fixture_skill",false,1);
        assertEquals("ADMITTED",positive.gate(),id);
        assertTrue(positive.gearSucceeded(),id);
        assertEquals("CHANCE_MISS",control.gate(),id);
        if(type==RpgStatusType.BLEED||type==RpgStatusType.BURN||type==RpgStatusType.POISON) {
            assertEquals("APPLIED",positive.periodicResult(),id);
            assertEquals(1,positivePeriodic.size(),id);
            assertEquals(0,controlPeriodic.size(),id);
        } else {
            assertTrue(positiveStatuses.inspect(victim).active().containsKey(type),id);
            assertFalse(controlStatuses.inspect(victim).active().containsKey(type),id);
        }
        var missed=GearStatusRuntime.admit(new StatusService(CombatBalanceProfile.loadCanonical(),()->0),
                new GearStatusRuntime.Contacts(),hit,type,ControlProfile.NORMAL,p.positive(),p.positive(),
                GearEffectSnapshot.EMPTY,0,0,.999,0,positiveOwner,"fixture_skill",false,1);
        assertEquals("CHANCE_MISS",missed.gate(),id);
        var protectedHit=new GearStatusRuntime.AppliedHit(actor,"root","strike",victim,true,true,true,true,10,1,
                Map.of(channel,10d));
        assertEquals("INELIGIBLE_HIT",GearStatusRuntime.admit(new StatusService(CombatBalanceProfile.loadCanonical(),()->0),
                new GearStatusRuntime.Contacts(),protectedHit,type,ControlProfile.NORMAL,p.positive(),p.positive(),
                GearEffectSnapshot.EMPTY,0,0,0,0,positiveOwner,"fixture_skill",false,1).gate(),id);
    }

    @Test void wa144SkillExecutionServiceRequestSameActorUnrolledRejectedAndAcceptedGrantExecutes() {
        var p=pair("WA-144");
        String skill=p.affixed().affixes().getFirst().selector();
        assertNotNull(skill);
        var actor=UUID.randomUUID();
        class Repository implements RpgPlayerStateRepository {
            final Map<UUID,RpgPlayerState> saved=new HashMap<>();
            public LoadResult load(UUID id){var state=saved.getOrDefault(id,RpgPlayerState.create(id));
                return new LoadResult(state.copy(),saved.containsKey(id),false,state.schemaVersion,List.of());}
            public void save(RpgPlayerState state){saved.put(state.playerUuid(),state.copy());}
        }
        var repo=new Repository();var catalog=RpgCatalog.loadCanonical();
        var compatible=new CompatibilityService();var graph=new RpgLinkGraphService(catalog,compatible);
        try(var loadouts=new RpgLoadoutService(catalog,repo,graph,new LinkCompiler(catalog,graph,compatible),
                new OwnershipEntitlementPolicy(false),ignored->{})) {
            var execution=new SkillExecutionService(loadouts,Stage04SkillProfiles.loadCanonical(catalog),
                    RpgCombatKernel.createProduction(),SkillExecutorRegistry.stage04(),
                    new SkillInstanceLifecycle(),ignored->{});
            class Resources implements NativeResourcePort {
                double stamina=100;
                public double current(ResourceType type){return type==ResourceType.STAMINA?stamina:100;}
                public double maximum(ResourceType type){return 100;}
                public void setCurrent(ResourceType type,double value){if(type==ResourceType.STAMINA)stamina=value;}
            }
            var resources=new Resources();
            var weapon=new SkillExecutionPort.Item("test:battleaxe","BATTLEAXE",
                    new ItemPowerDescriptor("test:battleaxe",Set.of("BATTLEAXE"),10d,null));
            class Port implements SkillExecutionPort {
                SkillExecutionContext committed;
                public boolean actorAliveAndUsable(){return true;}
                public Equipment equipment(){return new Equipment(weapon,null);}
                public NativeResourcePort resources(){return resources;}
                public Validation familyPrerequisites(Stage04SkillProfile profile,CompiledSkillPlan plan){return Validation.pass();}
                public SkillExecutionResult executeStrike(SkillExecutionContext context){committed=context;return SkillExecutionResult.committed("DISPATCHED",1,0);}
                public SkillExecutionResult executeMovement(SkillExecutionContext context){committed=context;return SkillExecutionResult.committed("DISPATCHED",1,0);}
                public SkillExecutionResult executeReaction(SkillExecutionContext context){committed=context;return SkillExecutionResult.committed("DISPATCHED",1,0);}
                public SkillExecutionResult executeProjectile(SkillExecutionContext context){committed=context;return SkillExecutionResult.committed("DISPATCHED",1,0);}
            }
            var port=new Port();
            var request=new SkillExecutionRequest(actor,SkillSlot.SKILL01,"Ability2",41,"wa144-cast",Vec3.FORWARD);
            loadouts.publishItemSkillAvailability(actor,p.control());
            assertFalse(loadouts.equipSkill(actor,SkillSlot.SKILL01,new SkillId(skill)).success());
            assertEquals(SkillExecutionResult.Status.REJECTED,execution.request(request,port).status());
            loadouts.publishItemSkillAvailability(actor,p.positive());
            assertTrue(loadouts.equipSkill(actor,SkillSlot.SKILL01,new SkillId(skill)).success());
            assertEquals(SkillExecutionResult.Status.COMMITTED,execution.request(request,port).status());
            assertEquals(1,port.committed.effectiveSkillLevel());
            execution.terminate(port.committed,"FIXTURE_COMPLETE");
            loadouts.publishItemSkillAvailability(actor,p.control());
            var withdrawn=execution.request(request,port);
            assertEquals(SkillExecutionResult.Status.REJECTED,withdrawn.status());
            assertEquals("EMPTY_SLOT",withdrawn.code());
        }
    }

    @Test void wa149SupportRuntimeThornsRecipientEffectRemovalAndNoAuraActor() {
        var p=pair("WA-149");
        MasterAffixFinalAuraHelper.verify("WA-149","thorns_aura",p.affixed(),p.positive(),p.control());
    }

    @Test void wa150SupportRuntimePedanticismRecipientEffectRemovalAndNoAuraActor() {
        var p=pair("WA-150");
        MasterAffixFinalAuraHelper.verify("WA-150","pedanticism",p.affixed(),p.positive(),p.control());
    }
}
