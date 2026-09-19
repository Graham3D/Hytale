package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.status.ControlProfile;
import com.inigmasgames.hytalerpg.combat.status.RpgStatusType;
import com.inigmasgames.hytalerpg.combat.status.StatusService;
import com.inigmasgames.hytalerpg.domain.SkillId;
import com.inigmasgames.hytalerpg.execution.Stage04SkillProfiles;
import com.inigmasgames.hytalerpg.execution.lightning.LightningRuntime;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageLifecycleSystems;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LightningSkillUpdateTest {
    @Test void catalogAndRuntimeExposeExactlyTheNineApprovedLightningSkills(){
        var catalog=Stage01BTestSupport.bundle().catalog();
        var profiles=Stage04SkillProfiles.loadCanonical(catalog);
        var expected=Set.of("charged_bolt","lightning_bolt","ball_lightning","lightning_coil","teleport",
                "static_field","storm_strike","lightning_arrow","mantle_of_thunder");
        assertEquals(expected,catalog.skills().stream().filter(s->s.tags().contains("LIGHTNING")).map(s->s.id().value()).collect(java.util.stream.Collectors.toSet()));
        expected.forEach(id->assertTrue(profiles.supports(id),id));
        assertTrue(catalog.skill(new SkillId("chain_lightning")).isEmpty());
        assertTrue(catalog.skill(new SkillId("spark")).isEmpty());
        assertEquals("charged_bolt",catalog.resolveSkill("Spark").value().id().value());
        assertEquals(3,profiles.require("charged_bolt").projectile().details().pattern().minimumCount());
        assertEquals(5,profiles.require("charged_bolt").projectile().details().pattern().count());
        assertTrue(profiles.require("lightning_bolt").allowedMainHandKinds().isEmpty());
        assertTrue(profiles.require("ball_lightning").allowedMainHandKinds().isEmpty());
        assertEquals(java.util.Set.of("BOW"),profiles.require("lightning_arrow").allowedMainHandKinds());
    }

    @Test void electrifiedCapsRefreshesAndSharedDerivedEffectsAreExact(){
        var clock=new AtomicLong();var statuses=new StatusService(CombatBalanceProfile.loadCanonical(),clock::get);var target=UUID.randomUUID();
        for(int i=1;i<=7;i++)assertEquals(Math.min(5,i),statuses.applyElectrified(target,6).stacks());
        assertEquals(.25,statuses.physicalMissChance(target),1e-12);
        clock.set(5_000_000_000L);assertEquals(5,statuses.applyElectrified(target,6).stacks());
        clock.set(10_500_000_000L);assertEquals(5,statuses.inspect(target).active().get(RpgStatusType.ELECTRIFIED).stacks());
        statuses.apply(target,RpgStatusType.LIGHTNING_VULNERABILITY,ControlProfile.NORMAL,5);
        statuses.apply(target,RpgStatusType.ALACRITY,ControlProfile.NORMAL,6);
        assertEquals(1.5,statuses.lightningDamageFactor(target));assertEquals(.30,statuses.cooldownRecoveryRate(target));
        assertTrue(HytaleDamageLifecycleSystems.ElectrifiedPhysicalMiss.shouldMiss(.25,.249));
        assertFalse(HytaleDamageLifecycleSystems.ElectrifiedPhysicalMiss.shouldMiss(.25,.25));
    }

    @Test void boundedLightningLedgersEnforceChanceLocksExposureCoilStormAndMantle(){
        var r=new LightningRuntime();var owner=UUID.randomUUID();var target=UUID.randomUUID();
        assertEquals("APPLY",r.chargedBolt("root",target,.10));assertEquals("ROOT_TARGET_STACK_CAP",r.chargedBolt("root",target,.01));
        assertEquals("APPLY",r.ballLightning(owner,target,1,.10));assertEquals("TARGET_LOCK",r.ballLightning(owner,target,2,.10));
        assertEquals("APPLY",r.ballLightning(owner,target,2.51,.10));
        assertEquals("TRIGGER",r.stormStrike(owner,target,true,false,false,3));
        assertEquals("GLOBAL_ICD",r.stormStrike(owner,UUID.randomUUID(),true,false,false,3.5));
        assertEquals("TARGET_ICD",r.stormStrike(owner,target,true,false,false,4.1));
        assertFalse(r.expose("field",target,true,1,1).applyVulnerability());
        assertFalse(r.expose("field",target,true,1,2).applyVulnerability());
        assertTrue(r.expose("field",target,true,1,3).applyVulnerability());
        assertEquals(1,r.expose("field",target,true,1,5).exposureSeconds()); // gap resets continuity
        var placed=r.placeCoil("coil",owner,20,0);assertEquals(60,placed.capacity());
        assertEquals("INELIGIBLE_SOURCE",r.chargeCoil("coil",owner,60,false,1).code());
        assertTrue(r.chargeCoil("coil",owner,60,true,1).discharge());
        r.activateMantle(owner,0);
        for(int i=0;i<10;i++)assertNotEquals("INACTIVE",r.mantleDamage(owner,16,20,true,i+.1).code());
        assertEquals("ALACRITY_ACTIVE",r.mantleDamage(owner,16,20,true,1.2).code());
    }

    @Test void allNineProfilesRetainTheApprovedCostsCooldownsAndPrimaryMagnitudes(){
        var p=Stage04SkillProfiles.loadCanonical(Stage01BTestSupport.bundle().catalog());
        assertEquals(7,p.require("charged_bolt").resourceCost());assertEquals(1.25,p.require("charged_bolt").cooldownSeconds());
        assertEquals(.32,p.require("charged_bolt").projectile().coefficient());assertEquals(3,p.require("charged_bolt").projectile().targetCap());
        assertEquals(16,p.require("lightning_bolt").resourceCost());assertEquals(6,p.require("lightning_bolt").cooldownSeconds());assertEquals(1.45,p.require("lightning_bolt").connection().coefficient());
        assertEquals(18,p.require("ball_lightning").resourceCost());assertEquals(10,p.require("ball_lightning").cooldownSeconds());assertEquals(.35,p.require("ball_lightning").connection().coefficient());
        assertEquals(22,p.require("lightning_coil").resourceCost());assertEquals(18,p.require("lightning_coil").cooldownSeconds());assertEquals(8,p.require("lightning_coil").area().lifetimeSeconds());
        assertEquals(16,p.require("teleport").resourceCost());assertEquals(8,p.require("teleport").cooldownSeconds());assertEquals(14,p.require("teleport").movement().maxDistance());
        assertEquals(24,p.require("static_field").resourceCost());assertEquals(18,p.require("static_field").cooldownSeconds());assertEquals(0,p.require("static_field").area().coefficient());
        assertEquals(0,p.require("storm_strike").resourceCost());assertEquals(1.75,p.require("storm_strike").strike().coefficient());
        assertEquals(8,p.require("lightning_arrow").resourceCost());assertEquals(3,p.require("lightning_arrow").cooldownSeconds());assertEquals(1.05,p.require("lightning_arrow").projectile().coefficient());
        assertEquals(.15,p.require("mantle_of_thunder").support().reservationFraction());assertEquals(3,p.require("mantle_of_thunder").support().toggleLockSeconds());
    }

    @Test void customParticleAndProjectileAssetsArePackaged(){
        for(String resource:java.util.List.of(
                "/Server/Particles/Hywind/Hywind_Charged_Bolt.particlesystem",
                "/Server/Particles/Hywind/Hywind_Lightning_Strike.particlesystem",
                "/Server/Particles/Hywind/Hywind_Static_Field.particlesystem",
                "/Server/ProjectileConfigs/RPG/Projectile_Config_Hywind_Charged_Bolt.json",
                "/Common/Particles/Textures/Hywind/Lightning/chargedbolt.png",
                "/Common/Particles/Textures/Hywind/Lightning/lightning.png",
                "/Common/Particles/Textures/Hywind/Lightning/staticfield.png"))assertNotNull(getClass().getResource(resource),resource);
        var model=new com.google.gson.Gson().fromJson(new java.io.InputStreamReader(
                java.util.Objects.requireNonNull(getClass().getResourceAsStream("/Server/Models/Projectiles/Hywind_Charged_Bolt.json")),
                java.nio.charset.StandardCharsets.UTF_8),com.google.gson.JsonObject.class);
        var hitBox=model.getAsJsonObject("HitBox");
        for(String axis:java.util.List.of("X","Y","Z")){
            assertEquals(.22,hitBox.getAsJsonObject("Max").get(axis).getAsDouble());
            assertEquals(-.22,hitBox.getAsJsonObject("Min").get(axis).getAsDouble());
        }
    }

    @Test void chargedBoltCountAndScatterAreReplayStableAndInsideTheAuthoredCone(){
        var pattern=Stage04SkillProfiles.loadCanonical(Stage01BTestSupport.bundle().catalog()).require("charged_bolt").projectile().details().pattern();
        for(String root:java.util.List.of("root-a","root-b","root-c","root-d")){
            int count=pattern.selectedCount(root);assertTrue(count>=3&&count<=5);
            for(int i=0;i<count;i++){
                var first=pattern.randomizedDirection(com.inigmasgames.hytalerpg.execution.math.Vec3.FORWARD,i,count,root);
                assertEquals(first,pattern.randomizedDirection(com.inigmasgames.hytalerpg.execution.math.Vec3.FORWARD,i,count,root));
                assertEquals(1,first.length(),1e-12);assertTrue(Math.toDegrees(Math.acos(first.z()))<=31.5);
            }
        }
    }

    @Test void productionAdapterChargesOnlyObservedNativeWeaponRootsAndQueuesOneOwnedDischarge() throws Exception {
        String source=java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSkillExecutionSystem.java"));
        assertTrue(source.contains("lightning.chargeCoil(coil.instance(),actor,actualHealthLoss,true,now)"));
        assertTrue(source.contains("HytaleSupportSystem.eligibleAlly(store,ownerRef,source)"));
        assertTrue(source.contains("pendingCoilDischarges.putIfAbsent(coil.instance(),coil)"));
        assertTrue(source.contains("coil.instance()+\"/discharge\",false,true,false"));
        assertTrue(source.contains("kernel.statuses().applyElectrified(targetId,6);kernel.statuses().applyElectrified(targetId,6)"));
    }
}
