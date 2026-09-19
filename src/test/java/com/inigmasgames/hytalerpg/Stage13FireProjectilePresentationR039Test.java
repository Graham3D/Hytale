package com.inigmasgames.hytalerpg;

import com.google.gson.JsonParser;
import com.inigmasgames.hytalerpg.execution.RootEffectBudget;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.projectile.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage13FireProjectilePresentationR039Test {
    @Test void exactChargeVisualMappingIsDeterministicAndCappedByChargePolicy(){
        assertEquals(FireProjectilePresentation.Tier.SMALL,FireProjectilePresentation.tier(FireballCharge.stage(.5)));
        assertEquals(FireProjectilePresentation.Tier.SMALL,FireProjectilePresentation.tier(FireballCharge.stage(1.2)));
        assertEquals(FireProjectilePresentation.Tier.MEDIUM,FireProjectilePresentation.tier(FireballCharge.stage(2.2)));
        assertEquals(FireProjectilePresentation.Tier.LARGE,FireProjectilePresentation.tier(FireballCharge.stage(3.2)));
        assertEquals(FireProjectilePresentation.Tier.EXTRA_LARGE,FireProjectilePresentation.tier(FireballCharge.stage(4.2)));
        assertEquals(FireProjectilePresentation.Tier.EXTRA_LARGE,FireProjectilePresentation.tier(FireballCharge.stage(8)));
        assertArrayEquals(new double[]{1,1.5,2.1,2.8},Arrays.stream(FireProjectilePresentation.Tier.values()).mapToDouble(FireProjectilePresentation.Tier::visualScale).toArray(),1e-12);
    }

    @Test void fourProfilesChangeOnlyPresentationAndKeepOneMechanicalContract()throws Exception{
        double[] expected={1,1.5,2.1,2.8};int index=0;
        for(var tier:FireProjectilePresentation.Tier.values()){
            var config=JsonParser.parseString(resource("/Server/ProjectileConfigs/RPG/"+tier.configId()+".json")).getAsJsonObject();
            var model=JsonParser.parseString(resource("/Server/Models/Projectiles/RPG_Fireball_"+modelSuffix(tier)+".json")).getAsJsonObject();
            assertEquals(16,config.get("LaunchForce").getAsDouble(),1e-12);assertEquals(12,config.getAsJsonObject("Physics").get("Gravity").getAsDouble(),1e-12);
            assertTrue(config.getAsJsonObject("Interactions").isEmpty());
            var box=model.getAsJsonObject("HitBox");
            for(String axis:List.of("X","Y","Z")){assertEquals(-.45,box.getAsJsonObject("Min").get(axis).getAsDouble(),1e-12);assertEquals(.45,box.getAsJsonObject("Max").get(axis).getAsDouble(),1e-12);}
            var particle=model.getAsJsonArray("Particles").get(0).getAsJsonObject();
            assertEquals("RPG_Fireball_Orb_No_Line",particle.get("SystemId").getAsString());assertEquals(expected[index++],particle.get("Scale").getAsDouble(),1e-12);
        }
        var profile=com.inigmasgames.hytalerpg.execution.Stage04SkillProfiles.loadCanonical(com.inigmasgames.hytalerpg.content.RpgCatalog.loadCanonical()).require("fireball").projectile();
        assertEquals(.45,profile.radius(),1e-12);assertEquals(3.5,profile.details().explosion().radius(),1e-12);assertEquals(22,profile.maxDistance(),1e-12);
    }

    @Test void descendantsInheritRootTierConfigWithoutVisualShrink(){
        UUID owner=UUID.randomUUID();var context=Stage07ContinuationTest.context(owner,"fork");
        var registry=new ProjectileLifecycleRegistry();var service=new RpgProjectileService(registry);
        var root=service.onProjectileSpawn(service.buildPlan(context,owner,Vec3.ZERO,Vec3.FORWARD,
                FireProjectilePresentation.configId(4),24,1));
        var decision=new ProjectileContinuation(registry).afterEnemy(root,Vec3.ZERO,new Vec3(0,0,-1),List.of(),2);
        assertEquals(ProjectileContinuation.Action.FORK,decision.action());assertEquals(2,decision.children().size());
        decision.children().forEach(child->assertEquals(FireProjectilePresentation.Tier.EXTRA_LARGE.configId(),child.plan().configId()));
    }

    @Test void audioPolicyIsRootLaunchOnlyAndTerminalImpactDedupIsBounded(){
        assertEquals(FireProjectilePresentation.FIRE_BOLT_LAUNCH_SOUND,FireProjectilePresentation.launchSound("fire_bolt",false));
        assertEquals(FireProjectilePresentation.FIREBALL_LAUNCH_SOUND,FireProjectilePresentation.launchSound("fireball",false));
        assertEquals("",FireProjectilePresentation.launchSound("fireball",true));
        assertEquals("",FireProjectilePresentation.launchSound("other",false));
        assertEquals(FireProjectilePresentation.FIRE_BOLT_IMPACT_SOUND,FireProjectilePresentation.impactSound("fire_bolt"));
        assertEquals(FireProjectilePresentation.FIREBALL_IMPACT_SOUND,FireProjectilePresentation.impactSound("fireball"));
        var budget=new RootEffectBudget(UUID.randomUUID(),"root");String key=FireProjectilePresentation.impactController("fireball","projectile-1/terrain");
        assertTrue(key.length()<=64);assertTrue(budget.once(key));assertFalse(budget.once(key));
        assertTrue(budget.once(FireProjectilePresentation.impactController("fireball","projectile-2/terrain")));
    }

    @Test void productionHookPlaysLaunchOnlyAfterNativeAllocationAndNeverFromChargeUpdates()throws Exception{
        String source=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSkillExecutionSystem.java"));
        String execute=source.substring(source.indexOf("@Override public SkillExecutionResult executeProjectile"),source.indexOf("private Ref<EntityStore> nearestTarget"));
        assertTrue(execute.indexOf("spawnProjectileCarrier")<execute.indexOf("FIRE_PROJECTILE_LAUNCH_SOUND"));
        assertTrue(execute.indexOf("FIRE_PROJECTILE_LAUNCH_SOUND")<execute.indexOf("return SkillExecutionResult.committed"));
        String charging=source.substring(source.indexOf("private void updateFireballCharge"),source.indexOf("private void advanceMotion"));
        assertFalse(charging.contains("presentProjectileSound"));assertFalse(charging.contains("SFX_"));
        assertTrue(source.contains("FireProjectilePresentation.impactController(\"fire_bolt\",projectileId)"));
        assertTrue(source.contains("FireProjectilePresentation.impactController(\"fireball\",effect)"));
    }

    @Test void aimFallbackIsNeutralWhiteAndNoCountdownAudioIsAuthored()throws Exception{
        String aim=resource("/Server/Particles/RPG/RPG_Fireball_Aim_White.particlesystem");
        String marker=resource("/Server/Particles/RPG/Spawners/RPG_Fireball_Aim_White_Marker.particlespawner");
        String root=resource("/Server/Item/RootInteractions/RPG/Root_RPG_Fireball_Charge.json");
        assertTrue(aim.contains("RPG_Fireball_Aim_White_Marker"));assertTrue(marker.contains("#ffffff"));
        assertTrue(marker.contains("Particles/Textures/Basic/Glow_Direction.png"));
        assertFalse(marker.contains("Particles/Textures/Fire/"));assertFalse(marker.contains("SpawnerId\":\"Fire_"));
        assertFalse(root.contains("SoundEventId"));assertFalse(root.contains("SFX_"));assertTrue(root.contains("Fireball_Charge_To_4"));
        assertEquals(16,FireballTrajectory.PREVIEW_SAMPLES);
        String source=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSkillExecutionSystem.java"));
        assertTrue(source.contains("rendererMode\",\"SEGMENTED_WHITE_MARKERS"));
        assertTrue(source.contains("if(observed.isEmpty())"));
        for(var tier:FireProjectilePresentation.Tier.values())assertFalse(resource("/Server/Models/Projectiles/RPG_Fireball_"+modelSuffix(tier)+".json").contains(FireProjectilePresentation.AIM_PARTICLE));
    }

    @Test void actualLongLineSourceIsSuppressedOnlyForTheTwoFireProjectiles()throws Exception{
        assertTrue(FireProjectilePresentation.suppressesGenericTrail("fire_bolt"));assertTrue(FireProjectilePresentation.suppressesGenericTrail("fireball"));
        assertFalse(FireProjectilePresentation.suppressesGenericTrail("frost_bolt"));assertFalse(FireProjectilePresentation.suppressesGenericTrail("snipe"));
        String bolt=resource("/Server/Particles/RPG/RPG_Fire_Projectile_No_Line.particlesystem");
        String ball=resource("/Server/Particles/RPG/RPG_Fireball_Orb_No_Line.particlesystem");
        assertTrue(bolt.contains("Fire_Projectile_Fire_Core"));assertFalse(bolt.contains("Fire_Projectile_Fire_Trail"));assertFalse(ball.contains("Trail"));
        String source=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSkillExecutionSystem.java"));
        assertTrue(source.contains("!FireProjectilePresentation.suppressesGenericTrail"));assertTrue(source.contains("vfx.presentProjectileTrail"));
    }

    private static String modelSuffix(FireProjectilePresentation.Tier tier){return switch(tier){case SMALL->"Small";case MEDIUM->"Medium";case LARGE->"Large";case EXTRA_LARGE->"Extra_Large";};}
    private static String resource(String path)throws Exception{try(var in=Stage13FireProjectilePresentationR039Test.class.getResourceAsStream(path)){return new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8);}}
}
