package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.projectile.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage13FireballChargeR037Test {
    @Test void exactStageBoundariesCapAtFour(){
        assertEquals(0,FireballCharge.stage(.5));assertEquals(1,FireballCharge.stage(1));
        assertEquals(2,FireballCharge.stage(2));assertEquals(3,FireballCharge.stage(3));
        assertEquals(4,FireballCharge.stage(4));assertEquals(4,FireballCharge.stage(8));
    }
    @Test void damageMovementAndBurnTablesAreExact(){
        double[] damage={1,1.1,1.2,1.3,1.4},movement={1,.9,.8,.7,.6};
        double[] base={.9,.99,1.08,1.17,1.26},burn={1.125,1.2375,1.35,1.4625,1.575};
        for(int i=0;i<5;i++){assertEquals(damage[i],FireballCharge.damageMultiplier(i),1e-12);assertEquals(movement[i],FireballCharge.movementMultiplier(i),1e-12);assertEquals(base[i],FireballCharge.coefficient(i),1e-12);assertEquals(burn[i],FireballCharge.burningCoefficient(i),1e-12);}
    }
    @Test void committedStageFourSnapshotsChargeOnceAndSpendsOnce(){
        var h=new Stage11ResourcePassivesTest.H("fireball");h.weapon="STAFF";
        var request=new SkillExecutionRequest(h.actor,SkillSlot.SKILL01,"Ability2",1,"r037-stage4",Vec3.FORWARD,SkillExecutionRequest.Origin.MANUAL,4);
        assertTrue(h.service.request(request,h).committed());
        assertEquals(90,h.current(ResourceType.MANA));assertEquals(1,h.cooldownSaves);
        assertEquals(1.4,h.last().snapshot().modifiers().factor(),1e-12);
        assertEquals(4,h.last().request().fireballChargeStage());
    }
    @Test void previewUsesSameSolutionIsBoundedAndStopsAtWall(){
        var motion=new ProjectileMotion("TIMED_BALLISTIC",16,12,.65,1.4,.1);
        var solution=FireballTrajectory.solve(Vec3.ZERO,new Vec3(0,0,16),motion,22,1).orElseThrow();
        var open=FireballTrajectory.preview(solution,.45,(a,b)->true);
        assertEquals(solution.at(solution.flightSeconds()),open.impact());assertEquals(17,open.points().size());assertFalse(open.blocked());
        var wall=FireballTrajectory.preview(solution,.45,(a,b)->b.z()<8);
        assertTrue(wall.blocked());assertTrue(wall.impact().z()<8);assertTrue(wall.points().size()<=17);
    }
    @Test void nativeReleaseRootAndRpgOnlyTrailDerivativesArePackaged()throws Exception{
        String root=resource("/Server/Item/RootInteractions/RPG/Root_RPG_Fireball_Charge.json");
        String item=resource("/Server/Item/Items/RPG/Abilities/RPG_Ability_Fireball.json");
        String bolt=resource("/Server/Models/Projectiles/RPG_Fire_Bolt.json");
        String ball=resource("/Server/Models/Projectiles/RPG_Fireball.json");
        String noLine=resource("/Server/Particles/RPG/RPG_Fire_Projectile_No_Line.particlesystem");
        assertTrue(root.contains("RPG_FireballCharge"));assertTrue(root.contains("RPG_ActivateSkill"));assertTrue(root.contains("Fireball_Charge_To_4"));
        assertTrue(item.contains("Root_RPG_Fireball_Charge"));assertTrue(bolt.contains("RPG_Fire_Projectile_No_Line"));assertTrue(ball.contains("RPG_Fireball_Orb_No_Line"));
        assertTrue(noLine.contains("Fire_Projectile_Fire_Core"));assertFalse(noLine.contains("Fire_Projectile_Fire_Trail"));
        assertEquals("Fire_Projectile",Stage04SkillProfiles.loadCanonical(com.inigmasgames.hytalerpg.content.RpgCatalog.loadCanonical()).require("fire_bolt").projectile().details().presentation().projectileParticle());
        assertEquals("Fire_Charge1",Stage04SkillProfiles.loadCanonical(com.inigmasgames.hytalerpg.content.RpgCatalog.loadCanonical()).require("fireball").projectile().details().presentation().projectileParticle());
    }
    private static String resource(String path)throws Exception{try(var in=Stage13FireballChargeR037Test.class.getResourceAsStream(path)){return new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8);}}
}
