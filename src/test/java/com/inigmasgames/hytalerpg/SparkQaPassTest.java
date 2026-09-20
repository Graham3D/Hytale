package com.inigmasgames.hytalerpg;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.projectile.GroundSparkSteering;
import com.inigmasgames.hytalerpg.execution.projectile.ProjectileContinuation;
import com.inigmasgames.hytalerpg.execution.projectile.ProjectileInstance;
import com.inigmasgames.hytalerpg.execution.projectile.ProjectileLifecycleRegistry;
import com.inigmasgames.hytalerpg.execution.projectile.RpgProjectileService;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SparkQaPassTest {
    @Test void sparkIsCanonicalDisplayNameWhileChargedBoltRemainsTheStableLegacyIdentity() {
        var catalog=Stage01BTestSupport.bundle().catalog();
        var skill=catalog.resolveSkill("Spark").value();
        assertEquals("charged_bolt",skill.id().value());assertEquals("Spark",skill.name());
        assertEquals(skill.id(),catalog.resolveSkill("Charged Bolt").value().id());
        assertTrue(skill.aliases().containsAll(List.of("Charged Bolt","charged_bolt","Spark","spark")));
        assertTrue(catalog.skill(new com.inigmasgames.hytalerpg.domain.SkillId("spark")).isEmpty());
    }

    @Test void authoredBatchIgnoresPitchAndRetainsThreeToFiveBoltsAndSixMeterPathBudget() {
        var context=Stage06AreaRuntimeTest.context("charged_bolt");
        var service=new RpgProjectileService(new ProjectileLifecycleRegistry());
        var plans=service.buildBatch(context,context.request().actorId(),new Vec3(0,.5,0),new Vec3(.2,.97,.1),
                "Projectile_Config_Hywind_Charged_Bolt",24,1);
        assertTrue(plans.size()>=3&&plans.size()<=5);
        for(var plan:plans){assertEquals(0,plan.velocity().y(),1e-12);assertEquals(24,plan.velocity().length(),1e-12);
            assertEquals(6,plan.maxDistance(),1e-12);assertEquals(.5,plan.origin().y(),1e-12);}
    }

    @Test void steeringIsReplayStableIndependentIrregularAndAlwaysForwardBiased() {
        var a=new GroundSparkSteering("spark-a",Vec3.FORWARD,0);
        var replay=new GroundSparkSteering("spark-a",Vec3.FORWARD,0);
        var other=new GroundSparkSteering("spark-b",Vec3.FORWARD,0);
        var yaws=new ArrayList<Double>();boolean independent=false,left=false,right=false;
        for(int i=0;i<32;i++){
            Vec3 direction=a.direction(),same=replay.direction(),different=other.direction();
            assertEquals(direction,same);assertEquals(0,direction.y(),1e-12);assertEquals(1,direction.length(),1e-12);
            assertTrue(direction.z()>.15,"every segment must retain strong forward progress");
            yaws.add(Math.toDegrees(Math.atan2(direction.x(),direction.z())));left|=direction.x()<0;right|=direction.x()>0;
            independent|=direction.distanceSquared(different)>1e-12;
            a.consume(.5);replay.consume(.5);other.consume(.5);
        }
        assertTrue(independent&&left&&right);assertTrue(yaws.stream().map(v->Math.round(v)).distinct().count()>12);
        assertTrue(yaws.stream().anyMatch(v->Math.abs(v)>58),"occasional large lateral kicks are required");
    }

    @Test void eachSparkPenetratesEnemiesButItsOwnTargetLedgerRejectsRepeatHits() {
        var context=Stage06AreaRuntimeTest.context("charged_bolt");var registry=new ProjectileLifecycleRegistry();
        var service=new RpgProjectileService(registry);
        var plan=service.buildBatch(context,context.request().actorId(),new Vec3(0,.5,0),Vec3.FORWARD,"fixture",24,1).getFirst();
        ProjectileInstance spark=service.onProjectileSpawn(plan);
        assertTrue(service.onEnemyContact(spark,"enemy-a"));assertFalse(service.onEnemyContact(spark,"enemy-a"));
        var decision=new ProjectileContinuation(registry).afterEnemy(spark,new Vec3(0,.5,1),Vec3.ZERO,List.of(),2);
        assertEquals(ProjectileContinuation.Action.PIERCE,decision.action());assertEquals("SPARK_ENEMY_PENETRATION",decision.reason());
        var second=service.onProjectileSpawn(new com.inigmasgames.hytalerpg.execution.projectile.ProjectileExecutionPlan(
                plan.rootCastId(),plan.skillInstanceId(),plan.projectileInstanceId()+"-second",plan.ownerId(),plan.skillId(),plan.compiledPlanHash(),
                plan.snapshot(),plan.generation(),plan.remainingContinuationBudgets(),plan.remainingSpawnedEffects(),plan.remainingTriggeredSecondaries(),
                plan.spawnTimestampNanos(),plan.configId(),plan.origin(),plan.velocity(),plan.radius(),plan.maxDistance(),plan.maxLifetimeSeconds(),plan.motion()));
        assertTrue(service.onEnemyContact(second,"enemy-a"),"separate sparks from one cast may hit the same enemy");
    }

    @Test void customGroundMotionOwnsOnlyAContactFreeNativeCourseEnd() {
        assertTrue(GroundSparkSteering.ownsNativeCourseEnd("charged_bolt",false,false));
        assertFalse(GroundSparkSteering.ownsNativeCourseEnd("charged_bolt",true,false));
        assertFalse(GroundSparkSteering.ownsNativeCourseEnd("charged_bolt",false,true));
        assertFalse(GroundSparkSteering.ownsNativeCourseEnd("fire_bolt",false,false));
    }

    @Test void projectilePresentationIsSmallHorizontalAndHasNoCastOriginEffect() throws Exception {
        var gson=new Gson();
        JsonObject spawner=gson.fromJson(new InputStreamReader(require("/Server/Particles/Hywind/Hywind_Charged_Bolt_Frame.particlespawner"),StandardCharsets.UTF_8),JsonObject.class);
        assertEquals("None",spawner.get("ParticleRotationInfluence").getAsString());
        var initial=spawner.getAsJsonObject("Particle").getAsJsonObject("InitialAnimationFrame");
        assertEquals(90,initial.getAsJsonObject("Rotation").getAsJsonObject("X").get("Min").getAsDouble());
        assertEquals(.32,initial.getAsJsonObject("Scale").getAsJsonObject("X").get("Min").getAsDouble(),1e-12);
        JsonObject model=gson.fromJson(new InputStreamReader(require("/Server/Models/Projectiles/Hywind_Charged_Bolt.json"),StandardCharsets.UTF_8),JsonObject.class);
        assertEquals(.06,model.get("MaxScale").getAsDouble(),1e-12);
        assertEquals(1,model.getAsJsonArray("Particles").get(0).getAsJsonObject().get("Scale").getAsDouble(),1e-12);
        assertTrue(Stage06AreaRuntimeTest.profile("charged_bolt").projectile().details().presentation().castParticle().isBlank());
    }

    @Test void productionAdapterOwnsGroundSamplingManualMotionWallRicochetAndAnimationSuppression() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSkillExecutionSystem.java"));
        for(String contract:List.of("direction=direction.horizontalNormalized()","SPARK_GROUND_ORIGIN_UNAVAILABLE",
                "advanceGroundSpark(carrier,deltaSeconds,store,buffer)","HytaleAreaQueries.projectileContact",
                "instance.remaining(\"RICOCHET\")<=0","SPARK_GROUND_SUPPORT_LOST","if(!isGroundSpark(context)) {",
                "if(isGroundSpark(context))physics.getVelocity().set(0,0,0)","GROUND_CRAWLER_OWNS_MOVEMENT",
                "buffer.tryRemoveComponent(projectileRef"))assertTrue(source.contains(contract),contract);
    }

    private static java.io.InputStream require(String path){return java.util.Objects.requireNonNull(SparkQaPassTest.class.getResourceAsStream(path),path);}
}
