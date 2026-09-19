package com.inigmasgames.hytalerpg;

import com.google.gson.JsonParser;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.projectile.*;
import com.inigmasgames.hytalerpg.execution.summon.*;
import com.inigmasgames.hytalerpg.progress.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Local structure/authority proof. Native animation and ranged behavior still require connected QA. */
class SummonSkeletonArchersTest {
    static class H extends Stage10SummonTest.Harness {
        final int bonus;
        H(int bonus){super("summon_skeleton_archers");this.bonus=bonus;}
        @Override public int itemGrantedSkillLevels(String skill,Equipment equipment){return bonus;}
        @Override public Validation familyPrerequisites(Stage04SkillProfile p,com.inigmasgames.hytalerpg.domain.CompiledSkillPlan plan,int level){
            String code=summons.admission(actor,plan.summonModifiers().count(p.summon().baseCount(level)),p.summon().decoy());
            return code.equals("PASS")?Validation.pass():Validation.reject(code);
        }
    }

    @Test void canonicalEffectiveLevelCountContinuesAboveMasteryCap(){
        assertEquals(1,EffectiveSkillLevel.summonSkeletonArchers(1));
        assertEquals(1,EffectiveSkillLevel.summonSkeletonArchers(2));
        assertEquals(2,EffectiveSkillLevel.summonSkeletonArchers(3));
        assertEquals(10,EffectiveSkillLevel.summonSkeletonArchers(19));
        assertEquals(10,EffectiveSkillLevel.summonSkeletonArchers(20));
        assertEquals(11,EffectiveSkillLevel.summonSkeletonArchers(21));
        assertEquals(21,EffectiveSkillLevel.resolve(0,20));
    }

    @Test void levelTwentyCommitsExactlyTenWithSharedOwnerAndRootPastStaleEightCap(){
        var h=new H(19);var result=h.cast();assertTrue(result.committed(),result.toString());
        assertEquals(20,h.context.effectiveSkillLevel());assertEquals(10,h.leases.size());
        assertEquals(10,h.summons.size());assertEquals("PASS",h.summons.admission(h.actor,10));
        for(var lease:h.leases){assertEquals(h.actor,lease.owner());assertSame(h.context,lease.context());
            assertEquals(h.context.rootCastId(),lease.context().rootCastId());assertEquals("RPG_Summon_Skeleton_Archer",lease.roleId());assertTrue(lease.nativeRanged());}
    }

    @Test void swarmAndEmpowermentRemainGenericTypedModifiers(){
        var swarm=new H(19);swarm.link("swarm",PassiveSlot.PASSIVE01);assertTrue(swarm.cast().committed());
        assertEquals(11,swarm.leases.size());swarm.leases.forEach(l->{assertEquals(45,l.maximumHealth());assertEquals(.35*.75,l.coefficient(),1e-9);});
        var empowered=new H(19);empowered.link("minion_empowerment",PassiveSlot.PASSIVE01);assertTrue(empowered.cast().committed());
        assertEquals(10,empowered.leases.size());empowered.leases.forEach(l->{assertEquals(78,l.maximumHealth());assertEquals(.35*1.3,l.coefficient(),1e-9);assertEquals(15,l.expires());});
    }

    @Test void canonicalArrowSnapshotsResolvedMagicPowerAndDealsThirtyFivePercentPhysical(){
        assertEquals(14,SummonArrowDamage.basePhysicalDamage(40),1e-9);
        var h=new H(0);assertTrue(h.cast().committed());var lease=h.leases.getFirst();
        assertEquals(com.inigmasgames.hytalerpg.combat.power.BasePowerSource.MAGIC_WEAPON,h.context.snapshot().basePowerSource());
        assertEquals(20.6,lease.snapshottedMagicPower(),1e-9);
        assertEquals(.35,lease.baseArrowCoefficient(),1e-9);assertEquals(.35,lease.coefficient(),1e-9);
        assertEquals(7.21,lease.baseArrowPhysicalDamage(),1e-9);assertEquals(1,lease.passiveMagnitudeFactor(),1e-9);
    }

    @Test void countGrowthNeverChangesPerArcherCoefficientOrCapturedPower(){
        var one=new H(0);one.cast();var ten=new H(19);ten.cast();
        assertEquals(1,one.leases.size());assertEquals(10,ten.leases.size());
        ten.leases.forEach(lease->{assertEquals(one.leases.getFirst().snapshottedMagicPower(),lease.snapshottedMagicPower(),1e-9);
            assertEquals(SummonArrowDamage.BASE_COEFFICIENT,lease.baseArrowCoefficient(),1e-9);});
    }

    @Test void missingOrInvalidMagicPowerCannotBecomeZeroOrOneDamageFallback(){
        assertThrows(IllegalArgumentException.class,()->SummonArrowDamage.basePhysicalDamage(0));
        assertThrows(IllegalArgumentException.class,()->SummonArrowDamage.basePhysicalDamage(Double.NaN));
        assertThrows(IllegalArgumentException.class,()->SummonArrowDamage.passiveMagnitudeFactor(0));
    }

    @Test void deathPactIsOwnedOncePerEligibleArcher(){
        var h=new H(19);h.link("death_pact",PassiveSlot.PASSIVE01);h.cast();
        for(var lease:h.leases){assertTrue(h.summons.activate(lease,UUID.randomUUID(),0));
            assertTrue(h.summons.end(lease.token(),SummonRegistry.EndReason.ENEMY_KILL).orElseThrow().deathPact());
            assertTrue(h.summons.end(lease.token(),SummonRegistry.EndReason.ENEMY_KILL).isEmpty());}
        assertEquals(0,h.summons.size());
    }

    @Test void formationIsSeparatedAndCapacityRejectsRatherThanTruncates(){
        var points=SummonFormation.points(new Vec3(8,2,-4),10);assertEquals(10,points.size());
        for(int i=0;i<points.size();i++)for(int j=0;j<i;j++)assertTrue(points.get(i).subtract(points.get(j)).length()>=1);
        assertEquals("SUMMON_INVALID_COUNT",new SummonRegistry().admission(UUID.randomUUID(),SummonRegistry.OWNER_LIMIT+1));
    }

    @Test void sameSkillRecastReplacesTheWholePriorBatchWithoutADeathPactTerminal(){
        var h=new H(0);assertTrue(h.cast().committed());var old=h.leases.getFirst();var entity=UUID.randomUUID();
        assertTrue(h.summons.activate(old,entity,0));
        var next=Stage10SummonTest.copy(h.context,h.actor,"replacement-root");
        var replacement=h.summons.replaceAndReserve(next,.1,null);
        assertEquals(List.of(old),replacement.replaced());assertEquals(1,replacement.reserved().size());
        assertFalse(h.summons.owns(entity));assertEquals(1,h.summons.size());
        assertEquals("PASS",h.summons.admissionReplacing(h.actor,next.target().worldId(),next.profile().skillId(),1,false));
    }

    @Test void arcForkAndChainCompileForSummonArrowsWithoutASummonSpecificContinuationEngine(){
        for(String passive:List.of("arc","fork","chain")){
            var h=new H(0);h.link(passive,PassiveSlot.PASSIVE01);assertTrue(h.cast().committed(),passive);
            assertTrue(h.context.compiledPlan().passiveOrder().contains(new PassiveId(passive)),passive);
        }
        var h=new H(0);h.link("arc",PassiveSlot.PASSIVE01);h.link("fork",PassiveSlot.PASSIVE02);h.link("chain",PassiveSlot.PASSIVE03);h.cast();
        assertTrue(h.context.compiledPlan().passiveOrder().containsAll(List.of(
                new PassiveId("arc"),new PassiveId("fork"),new PassiveId("chain"))));
        assertFalse(Files.exists(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/summon/SummonArrowContinuations.java")));
    }

    @Test void chainAndForkDeriveFromTheSameSnapshotWithoutSuppressingParentPayload(){
        var plain=new H(0);plain.cast();var plainLease=plain.leases.getFirst();
        var chain=new H(0);chain.link("chain",PassiveSlot.PASSIVE01);chain.cast();
        var fork=new H(0);fork.link("fork",PassiveSlot.PASSIVE01);fork.cast();
        assertEquals(plainLease.snapshottedMagicPower(),chain.leases.getFirst().snapshottedMagicPower(),1e-9);
        assertEquals(plainLease.snapshottedMagicPower(),fork.leases.getFirst().snapshottedMagicPower(),1e-9);
        assertEquals(.35,plainLease.coefficient(),1e-9);assertEquals(.35,chain.leases.getFirst().coefficient(),1e-9);
        assertEquals(.35,fork.leases.getFirst().coefficient(),1e-9);
        assertEquals(.70,ProjectileContinuationBalance.hitFactor(chain.context.compiledPlan()),1e-9);
        assertEquals(.65,ProjectileContinuationBalance.FORK_CHILD_FACTOR,1e-9);
    }

    @Test void visibleForkAndChainChildrenRetainTheSummonOffensiveSnapshot(){
        var forkHarness=new H(0);forkHarness.link("fork",PassiveSlot.PASSIVE01);forkHarness.cast();
        var forkRegistry=new ProjectileLifecycleRegistry();var forkParent=projectileParent(forkHarness,"fork-parent",Map.of("FORK",1,"CHAIN",0,"IS_LAUNCH",1,"ROOT_LAUNCHES",1));
        forkRegistry.register(forkParent);var forkDecision=new ProjectileContinuation(forkRegistry).afterEnemy(forkParent,new Vec3(1,1,0),Vec3.ZERO,List.of(),2);
        assertEquals(ProjectileContinuation.Action.FORK,forkDecision.action());assertEquals(2,forkDecision.children().size());
        forkDecision.children().forEach(child->{assertEquals(forkHarness.context.snapshot().basePower(),child.plan().snapshot().basePower(),1e-9);
            assertEquals(forkHarness.context.snapshot().derivedStats(),child.plan().snapshot().derivedStats());
            assertEquals(forkHarness.context.snapshot().modifiers().factor()*.65,child.plan().snapshot().modifiers().factor(),1e-9);});

        var chainHarness=new H(0);chainHarness.link("chain",PassiveSlot.PASSIVE01);chainHarness.cast();
        var chainRegistry=new ProjectileLifecycleRegistry();var chainParent=projectileParent(chainHarness,"chain-parent",Map.of("FORK",0,"CHAIN",1,"IS_LAUNCH",1,"ROOT_LAUNCHES",1));
        chainRegistry.register(chainParent);var chainDecision=new ProjectileContinuation(chainRegistry).afterEnemy(chainParent,new Vec3(1,1,0),Vec3.ZERO,
                List.of(new ProjectileContinuation.Candidate(UUID.randomUUID().toString(),new Vec3(3,1,0),true)),2);
        assertEquals(ProjectileContinuation.Action.CHAIN,chainDecision.action());var chainChild=chainDecision.children().getFirst();
        assertSame(chainHarness.context.snapshot(),chainChild.plan().snapshot());
        assertEquals(chainHarness.context.snapshot().basePower(),chainChild.plan().snapshot().basePower(),1e-9);
    }

    @Test void replacementPolicyAndFollowRadiusAreBoundedToSkeletonArchers() throws Exception {
        var h=new H(0);assertTrue(com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem.OWNER_IDLE_RADIUS>=6
                &&com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem.OWNER_IDLE_RADIUS<=9);
        assertTrue(com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem.OWNER_FOLLOW_STOP_RADIUS
                <com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem.OWNER_IDLE_RADIUS);
        assertTrue(com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem.replacesBatch(h.profiles.require("summon_skeleton_archers")));
        assertFalse(com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem.replacesBatch(h.profiles.require("wolf_summon")));
        String nativeAdapter=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSummonSystem.java"));
        assertTrue(nativeAdapter.contains("saveLeashInformation(ownerTransform.getPosition()"));
        assertTrue(nativeAdapter.contains("state.setState(ref,\"ReturnHome\",null,store)"));
        assertTrue(nativeAdapter.contains("state.setState(ref,\"Idle\",null,store)"));
        assertFalse(nativeAdapter.contains("setPosition(ownerTransform"));
        assertTrue(nativeAdapter.contains("quarantineAttack(store,buffer,lease,failure)"));
        assertTrue(nativeAdapter.contains("NATIVE_ARROW_CONVERSION"));
        assertTrue(nativeAdapter.contains("\"FOLLOW_OWNER\""));assertTrue(nativeAdapter.contains("\"IDLE_NEAR_OWNER\""));
        String role=Files.readString(Path.of("src/main/resources/Server/NPC/Roles/RPG/RPG_Summon_Skeleton_Archer.json"));
        assertTrue(role.contains("\"Reference\": \"Template_Intelligent\""));
        assertFalse(role.contains("CombatFleeIfTooCloseDistance"),"Template_Intelligent's audited zero/default disables flee");
        assertTrue(role.contains("\"LeashDistance\": 20"));
    }

    @Test void publicAcquisitionIsUnassignedAndNativeRoleIsNotReusedAsSource(){
        var definition=Stage01BTestSupport.bundle().catalog().skill(new SkillId("summon_skeleton_archers")).orElseThrow();
        assertTrue(definition.sourceAcquisition().signatureEnemyId().startsWith("UNASSIGNED"));
        assertEquals("UNASSIGNED",definition.sourceAcquisition().validationState());
        assertFalse(definition.sourceAcquisition().signatureEnemyId().contains("Skeleton_Archer"));
        assertEquals(0,new LearningSources(Stage01BTestSupport.bundle().catalog(),List.of()).verifiedBindings());
    }

    @Test void nativeSpawnAssetsAndProductionPathRetainStockSpawnLifecycle() throws Exception {
        Path root=Path.of(System.getenv("APPDATA"),"Hytale","install");
        Path assets=root.resolve("pre-release/package/game/latest/Assets.zip");
        if(!Files.exists(assets))assets=root.resolve("release/package/game/latest/Assets.zip");
        assertTrue(Files.exists(assets));
        try(var zip=new ZipFile(assets.toFile())){
            assertNotNull(zip.getEntry("Server/NPC/Roles/Undead/Skeleton/Skeleton/Skeleton_Archer.json"));
            var model=JsonParser.parseReader(new InputStreamReader(zip.getInputStream(zip.getEntry("Server/Models/Undead/Skeleton.json")),StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(model.getAsJsonObject("AnimationSets").has("Spawn"));
        }
        assertTrue(Files.exists(Path.of("src/main/resources/Server/NPC/Roles/RPG/RPG_Summon_Skeleton_Archer.json")));
        assertTrue(Files.exists(Path.of("src/main/resources/Server/ProjectileConfigs/RPG/Projectile_Config_RPG_Summon_Arrow.json")));
        String source=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSummonSystem.java"));
        assertTrue(source.contains("spawnNPCWithSpaceValidation"));
        assertTrue(source.contains("NewSpawnComponent.getComponentType()"));
        assertFalse(source.contains("playAnimation("));
        assertTrue(source.contains("setDeathItemsDropped"));assertTrue(source.contains("NonSerialized.get()"));
    }

    @Test void nativeArrowContinuationUsesSharedTrackedCarrierAndDeferredWorldThreadQueue() throws Exception {
        String execution=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSkillExecutionSystem.java"));
        String summon=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSummonSystem.java"));
        assertTrue(execution.contains("projectileService.onProjectileSpawn(plan)"));
        assertTrue(execution.contains("continuations.afterEnemy(parent"));
        assertTrue(execution.contains("spawnProjectileCarrier(proxy,port.actor,child,buffer,lineage)"));
        assertFalse(execution.contains("SummonArrowContinuations.branches(context.compiledPlan())"));
        assertTrue(summon.contains("ConcurrentLinkedQueue<PendingArrowHit>"));
        assertTrue(summon.contains("drainArrowHits(store,buffer)"));
        assertTrue(summon.indexOf("damage.setCancelled(true)")<summon.indexOf("enqueueArrowHit(lease,targetId,ordinal"));
        assertEquals(1,count(execution,"var result=port.damage(context,candidate,attack,"));
        assertTrue(execution.indexOf("var result=port.damage(context,candidate,attack,")<execution.indexOf("applySummonArrowContinuations(port,context,candidate"));
        assertTrue(execution.contains("SUMMON_ARROW_DAMAGE"));
        assertTrue(execution.contains("primary.bounds().centre().add(new Vec3(0,.08,0))"));
        assertFalse(summon.contains("summons.attack.apply(actual,null"));
        var projectile=JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/Server/ProjectileConfigs/RPG/Projectile_Config_RPG_Summon_Arrow.json"))).getAsJsonObject();
        assertEquals("Arrow_Iron",projectile.get("Model").getAsString());
        assertEquals(0,projectile.getAsJsonObject("Physics").get("Gravity").getAsInt());
        assertEquals(0,projectile.getAsJsonObject("Interactions").size());
    }
    private static int count(String value,String token){int total=0,at=0;while((at=value.indexOf(token,at))>=0){total++;at+=token.length();}return total;}
    private static ProjectileInstance projectileParent(H h,String id,Map<String,Integer> budgets){
        return new ProjectileInstance(new ProjectileExecutionPlan(h.context.rootCastId(),h.context.skillInstanceId(),id,h.actor,
                h.context.profile().skillId(),h.context.compiledPlan().planHash(),h.context.snapshot(),0,budgets,47,16,1,
                "Projectile_Config_RPG_Summon_Arrow",new Vec3(0,1,0),new Vec3(30,0,0),.075,24,.8));
    }
}
