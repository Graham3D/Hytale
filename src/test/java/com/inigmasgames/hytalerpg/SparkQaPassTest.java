package com.inigmasgames.hytalerpg;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.projectile.GroundSparkSteering;
import com.inigmasgames.hytalerpg.execution.projectile.GroundSparkTerrainPolicy;
import com.inigmasgames.hytalerpg.execution.projectile.ProjectileContinuation;
import com.inigmasgames.hytalerpg.execution.projectile.ProjectileInstance;
import com.inigmasgames.hytalerpg.execution.projectile.ProjectileLifecycleRegistry;
import com.inigmasgames.hytalerpg.execution.projectile.RpgProjectileService;
import com.inigmasgames.hytalerpg.domain.LinkNodeId;
import com.inigmasgames.hytalerpg.domain.PassiveId;
import com.inigmasgames.hytalerpg.domain.PassiveSlot;
import com.inigmasgames.hytalerpg.domain.SkillId;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.links.CompatibilityService;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SparkQaPassTest {
    @Test void everyProjectileFamilyPassiveCanLinkToSparkAndCompileAnActualModifier() {
        var bundle=Stage01BTestSupport.bundle();var skill=bundle.catalog().skill(new SkillId("charged_bolt")).orElseThrow();
        var compatibility=new CompatibilityService();
        var projectilePassives=List.of("piercing","fork","chain","ricochet","return","volley","barrage","homing",
                "accelerant","ballistics","shrapnel","splinterburst","orbit");
        for(String id:projectilePassives)assertTrue(compatibility.assess(skill,bundle.catalog().passive(new PassiveId(id)).orElseThrow()).accepted(),id);

        var owner=java.util.UUID.randomUUID();assertTrue(bundle.service().equipSkill(owner,SkillSlot.SKILL01,skill.id()).success());
        assertTrue(bundle.service().equipPassive(owner,PassiveSlot.PASSIVE01,new PassiveId("volley")).success());
        assertTrue(bundle.service().link(owner,LinkNodeId.PASSIVE01,LinkNodeId.SKILL01).success());
        var plan=bundle.service().getPresentationView(owner).plans().get(SkillSlot.SKILL01);
        assertNotNull(plan);assertTrue(plan.projectileModifiers().volley());assertEquals(3,plan.projectileModifiers().batchSize());
    }

    @Test void volleyMultipliesEveryAuthoredSparkAndHomingHasAGroundCrawlerRuntimePath() throws Exception {
        var bundle=Stage01BTestSupport.bundle();var owner=java.util.UUID.randomUUID();
        assertTrue(bundle.service().equipSkill(owner,SkillSlot.SKILL01,new SkillId("charged_bolt")).success());
        assertTrue(bundle.service().equipPassive(owner,PassiveSlot.PASSIVE01,new PassiveId("volley")).success());
        assertTrue(bundle.service().link(owner,LinkNodeId.PASSIVE01,LinkNodeId.SKILL01).success());
        var base=Stage06AreaRuntimeTest.context("charged_bolt");var plan=bundle.service().getPresentationView(owner).plans().get(SkillSlot.SKILL01);
        var context=new com.inigmasgames.hytalerpg.execution.SkillExecutionContext(
                new com.inigmasgames.hytalerpg.execution.SkillExecutionRequest(owner,SkillSlot.SKILL01,"fixture",1,"spark-volley",Vec3.FORWARD),
                base.rootCastId(),base.skillInstanceId(),base.profile(),plan,base.snapshot(),base.equipment(),base.target(),false);
        var batch=new RpgProjectileService(new ProjectileLifecycleRegistry()).buildBatch(context,owner,new Vec3(0,.49,0),Vec3.FORWARD,"fixture",6,1);
        assertEquals(0,batch.size()%3);assertTrue(batch.size()>=9&&batch.size()<=15);

        String source=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSkillExecutionSystem.java"));
        assertTrue(source.contains("projectileModifiers().homing())"));
        assertTrue(source.contains("direction=homingUpdate(carrier,current,store).direction().horizontalNormalized()"));
    }

    @Test void sparkIsCanonicalDisplayNameWhileChargedBoltRemainsTheStableLegacyIdentity() {
        var catalog=Stage01BTestSupport.bundle().catalog();
        var skill=catalog.resolveSkill("Spark").value();
        assertEquals("charged_bolt",skill.id().value());assertEquals("Spark",skill.name());
        assertEquals(skill.id(),catalog.resolveSkill("Charged Bolt").value().id());
        assertTrue(skill.aliases().containsAll(List.of("Charged Bolt","charged_bolt","Spark","spark")));
        assertTrue(catalog.skill(new com.inigmasgames.hytalerpg.domain.SkillId("spark")).isEmpty());
    }

    @Test void authoredBatchIgnoresPitchAndUsesTheSlowerFifteenMeterTravelContract() {
        var context=Stage06AreaRuntimeTest.context("charged_bolt");
        var service=new RpgProjectileService(new ProjectileLifecycleRegistry());
        var plans=service.buildBatch(context,context.request().actorId(),new Vec3(0,.5,0),new Vec3(.2,.97,.1),
                "Projectile_Config_Hywind_Charged_Bolt",6,1);
        assertTrue(plans.size()>=3&&plans.size()<=5);
        for(var plan:plans){assertEquals(0,plan.velocity().y(),1e-12);assertEquals(6,plan.velocity().length(),1e-12);
            assertEquals(15,plan.maxDistance(),1e-12);assertEquals(2.5,plan.maxLifetimeSeconds(),1e-12);
            assertEquals(.5,plan.origin().y(),1e-12);}
    }

    @Test void variableBoltCountReleasesEveryRootReservationAfterTheLastSparkEnds() {
        var base=Stage06AreaRuntimeTest.context("charged_bolt");
        var registry=new ProjectileLifecycleRegistry();var service=new RpgProjectileService(registry);
        boolean exercisedVariableCount=false;
        for(int cast=0;cast<32;cast++){
            String root="spark-budget-root-"+cast,instance=root+"-skill";var old=base.snapshot();
            var snapshot=new com.inigmasgames.hytalerpg.combat.snapshot.CombatSnapshot(root,instance,old.actorId(),old.rawAttributes(),
                    old.effectiveAttributes(),old.derivedStats(),old.itemId(),old.weaponClass(),old.basePowerSource(),old.basePower(),
                    old.compiledPlanHash(),old.skillCoefficient(),old.criticalChance(),old.criticalMultiplier(),old.modifiers(),
                    old.resourceCost(),old.cooldownSeconds(),old.statusModifiers());
            var context=new com.inigmasgames.hytalerpg.execution.SkillExecutionContext(base.request(),root,instance,base.profile(),
                    base.compiledPlan(),snapshot,base.equipment(),base.target(),false);
            assertEquals("PASS",registry.admission(base.request().actorId(),base.profile().projectile().details().pattern().rootLaunches(base.compiledPlan())));
            var plans=service.buildBatch(context,base.request().actorId(),new Vec3(0,.5,0),Vec3.FORWARD,"fixture",6,cast+1);
            int declared=plans.getFirst().remainingContinuationBudgets().get("ROOT_LAUNCHES");
            assertEquals(plans.size(),declared,"the root ledger must reserve only the deterministic selected count");
            exercisedVariableCount|=plans.size()<base.profile().projectile().details().pattern().count();
            var instances=plans.stream().map(ProjectileInstance::new).toList();registry.registerAll(instances);instances.forEach(registry::remove);
            assertEquals(0,registry.size());assertEquals(0,registry.rootCount(),"completed variable Spark casts must not leak promises");
        }
        assertTrue(exercisedVariableCount);
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

    @Test void groundedTravelAllowsExactlyOneBlockUpOrDown() {
        assertTrue(GroundSparkTerrainPolicy.traversableHeight(1));
        assertTrue(GroundSparkTerrainPolicy.traversableHeight(-1));
        assertTrue(GroundSparkTerrainPolicy.barrier(2));
        assertTrue(GroundSparkTerrainPolicy.unsupportedDrop(-2));
        assertFalse(GroundSparkTerrainPolicy.traversableHeight(2));
        assertFalse(GroundSparkTerrainPolicy.traversableHeight(-2));
    }

    @Test void projectilePresentationIsAnUnscaledHorizontalModelQuadWithNoYellowFallback() throws Exception {
        var gson=new Gson();
        JsonObject projectile=gson.fromJson(new InputStreamReader(require("/Server/ProjectileConfigs/RPG/Projectile_Config_Hywind_Charged_Bolt.json"),StandardCharsets.UTF_8),JsonObject.class);
        assertEquals(6,projectile.get("LaunchForce").getAsDouble(),1e-12);
        assertEquals(6,projectile.getAsJsonObject("Physics").get("TerminalVelocityAir").getAsDouble(),1e-12);
        assertNull(SparkQaPassTest.class.getResource("/Server/Particles/Hywind/Hywind_Charged_Bolt.particlesystem"));
        assertNull(SparkQaPassTest.class.getResource("/Server/Particles/Hywind/Hywind_Charged_Bolt_Frame.particlespawner"));
        JsonObject model=gson.fromJson(new InputStreamReader(require("/Server/Models/Projectiles/Hywind_Charged_Bolt.json"),StandardCharsets.UTF_8),JsonObject.class);
        assertEquals("VFX/RPG/Spark/Spark_TriplePlane_R069.blockymodel",model.get("Model").getAsString());
        assertEquals("VFX/RPG/Spark/Spark_Strip_Vertical.png",model.get("Texture").getAsString());
        assertEquals(1,model.get("MinScale").getAsDouble(),1e-12);assertEquals(1,model.get("MaxScale").getAsDouble(),1e-12);
        assertFalse(model.has("Particles"));assertFalse(model.has("Light"),"the former yellow point-light fallback must not exist");
        for(String state:List.of("Idle","FlyIdle")){
            var animationState=model.getAsJsonObject("AnimationSets").getAsJsonObject(state).getAsJsonArray("Animations").get(0).getAsJsonObject();
            assertEquals("VFX/RPG/Spark/Spark_TriplePlane_FourFrame_R069.blockyanim",animationState.get("Animation").getAsString());
            assertTrue(animationState.get("Looping").getAsBoolean());
        }

        JsonObject blocky=gson.fromJson(new InputStreamReader(require("/Common/VFX/RPG/Spark/Spark_TriplePlane_R069.blockymodel"),StandardCharsets.UTF_8),JsonObject.class);
        var nodes=blocky.getAsJsonArray("nodes");assertEquals(3,nodes.size());var normals=new java.util.HashSet<String>();
        for(var value:nodes){var shape=value.getAsJsonObject().getAsJsonObject("shape");
            assertEquals("quad",shape.get("type").getAsString());normals.add(shape.getAsJsonObject("settings").get("normal").getAsString());
            assertEquals("fullbright",shape.get("shadingMode").getAsString());assertTrue(shape.get("doubleSided").getAsBoolean());
            var size=shape.getAsJsonObject("settings").getAsJsonObject("size");var stretch=shape.getAsJsonObject("stretch");
            assertEquals(80,size.get("x").getAsDouble());assertEquals(80,size.get("y").getAsDouble());
            assertEquals(.8,stretch.get("x").getAsDouble());assertEquals(.8,stretch.get("y").getAsDouble());assertEquals(.8,stretch.get("z").getAsDouble());
        }
        assertEquals(java.util.Set.of("+X","+Y","+Z"),normals);

        JsonObject animation=gson.fromJson(new InputStreamReader(require("/Common/VFX/RPG/Spark/Spark_TriplePlane_FourFrame_R069.blockyanim"),StandardCharsets.UTF_8),JsonObject.class);
        assertEquals(24,animation.get("duration").getAsInt());assertFalse(animation.get("holdLastKeyframe").getAsBoolean());
        var animated=animation.getAsJsonObject("nodeAnimations");assertEquals(3,animated.size());
        for(String node:List.of("SparkPlaneHorizontal","SparkPlaneVerticalX","SparkPlaneVerticalZ")){
            var uv=animated.getAsJsonObject(node).getAsJsonArray("shapeUvOffset");assertEquals(4,uv.size());
            for(int i=0;i<4;i++){var frame=uv.get(i).getAsJsonObject();assertEquals(i*6,frame.get("time").getAsInt());
                assertEquals(0,frame.getAsJsonObject("delta").get("x").getAsInt());assertEquals(i*-80,frame.getAsJsonObject("delta").get("y").getAsInt());}
        }

        var texture=javax.imageio.ImageIO.read(require("/Common/VFX/RPG/Spark/Spark_Strip_Vertical.png"));
        assertEquals(80,texture.getWidth());assertEquals(320,texture.getHeight());boolean transparent=false,blueWhite=false;
        for(int y=0;y<texture.getHeight();y++)for(int x=0;x<texture.getWidth();x++){int argb=texture.getRGB(x,y),alpha=argb>>>24;
            transparent|=alpha==0;int red=argb>>16&255,green=argb>>8&255,blue=argb&255;blueWhite|=alpha>200&&blue>180&&blue>=red&&blue>=green;}
        assertTrue(transparent&&blueWhite,"the packaged model texture must retain transparent and blue/white pixels");
        assertNull(SparkQaPassTest.class.getResource("/Common/VFX/RPG/Spark/chargedbolt.png"),
                "the horizontal atlas that corrupted unrelated HUD icon sampling must not be packaged");
        assertNull(SparkQaPassTest.class.getResource("/Common/VFX/RPG/Spark/Spark_Quad_FourFrame.blockyanim"),
                "the prior animation URI must not survive and be served from a stale client cache");
        assertNull(SparkQaPassTest.class.getResource("/Common/VFX/RPG/Spark/Spark_Quad.blockymodel"));
        assertNull(SparkQaPassTest.class.getResource("/Common/VFX/RPG/Spark/Spark_Quad_FourFrame_R068.blockyanim"));
        for(String icon:List.of("/Common/Icons/Items/RPG/SkillChargedbolt.png","/Common/Icons/Items/RPG/SkillStaticfield.png")){
            var image=javax.imageio.ImageIO.read(require(icon));assertEquals(128,image.getWidth(),icon);assertEquals(128,image.getHeight(),icon);
        }
        var presentation=Stage06AreaRuntimeTest.profile("charged_bolt").projectile().details().presentation();
        assertTrue(presentation.castParticle().isBlank());assertTrue(presentation.projectileParticle().isBlank());
        assertEquals("Laser_Impact",presentation.impactParticle());
    }

    @Test void productionAdapterOwnsGroundSamplingManualMotionWallRicochetAndAnimationSuppression() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSkillExecutionSystem.java"));
        for(String contract:List.of("direction=direction.horizontalNormalized()","SPARK_GROUND_ORIGIN_UNAVAILABLE",
                "advanceGroundSpark(carrier,deltaSeconds,store,buffer)","HytaleAreaQueries.projectileContact",
                "instance.remaining(\"RICOCHET\")<=0","SPARK_GROUND_SUPPORT_LOST","if(!isGroundSpark(context)) {",
                "if(isGroundSpark(context))physics.getVelocity().set(0,0,0)","GROUND_CRAWLER_OWNS_MOVEMENT",
                "buffer.tryRemoveComponent(projectileRef","if(isGroundSpark(carrier.context))return;",
                "resolveGroundSparkTerrainContact(carrier","presentSparkImpact(carrier,store,horizontal,\"SPARK_STEP_BARRIER_IMPACT\")",
                "presentSparkImpact(carrier,store,vec(position),\"SPARK_NATIVE_TERRAIN_IMPACT\")"))assertTrue(source.contains(contract),contract);
    }

    private static java.io.InputStream require(String path){return java.util.Objects.requireNonNull(SparkQaPassTest.class.getResourceAsStream(path),path);}
}
