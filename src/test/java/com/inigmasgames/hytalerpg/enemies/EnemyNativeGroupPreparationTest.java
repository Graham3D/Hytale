package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.difficulty.*;
import com.inigmasgames.hytalerpg.execution.hytale.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyNativeGroupPreparationTest {
    private final EnemyNativeBindings manifest=EnemyNativeBindings.load();
    private final EnemyNativeBindings.Role enabled=manifest.role("Larva_Void").orElseThrow();
    private final EnemyNativeBindings.Role disabled=new EnemyNativeBindings.Role(enabled.canonicalRoleId(),
            enabled.nativeRoleIds(),enabled.profiles(),enabled.capabilities(),enabled.modelAssetId(),enabled.textures(),
            enabled.controlProfileId(),enabled.sourceValidationId(),enabled.nativeLootSourceId(),enabled.sourceAssetPath(),
            enabled.sourceAssetSha256(),enabled.nativeImmunityChannels(),enabled.nativeStatusSource(),enabled.nativeStunStaggerImmune(),
            enabled.nativeSlowImmune(),enabled.maximumBaseEquipmentSlots(),enabled.distanceDisplacementDelivery(),
            false,enabled.actions(),enabled.evidenceFiles());
    private final EnemyBalance balance=EnemyBalance.canonical();
    private final UUID world=UUID.randomUUID(),encounter=UUID.randomUUID(),first=UUID.randomUUID(),second=UUID.randomUUID();
    private final NativeEnemySpawnGroups.Job job=new NativeEnemySpawnGroups.Job(world,31,4,"Larva_Void",3,5,2);
    private NativeEnemySpawnGroups.Group group(boolean failed,int expected){
        var actual=new NativeEnemySpawnGroups.Job(world,job.nativeJobId(),job.roleIndex(),job.nativeRole(),job.environment(),job.spawnConfiguration(),expected);
        var reservation=new NativeEnemySpawnGroups.Reservation(world,encounter,1);
        var members=List.of(new NativeEnemySpawnGroups.Member(first,"Larva_Void",new EnemyStaging.State(world,encounter,first,1,7)),
                new NativeEnemySpawnGroups.Member(second,"Larva_Void",new EnemyStaging.State(world,encounter,second,1,7)));
        return new NativeEnemySpawnGroups.Group(actual,reservation,members,failed);
    }
    private EnemyNativeGroupPreparation.MemberSource source(UUID id){
        var profile=AuthoredEncounterCatalog.load().profiles().stream().filter(p->p.roleId().equals("Larva_Void")
                &&p.difficulty()==DifficultyId.NORMAL).findFirst().orElseThrow();
        var combat=new EncounterProfileResolver.Resolved(world,id,profile.difficulty(),profile.id(),profile.worldProfileId(),
                profile.roleId(),profile.biomeKey(),profile.combatLevel(),profile.roleHealth(),profile.roleAttackBasis(),
                1,1,profile.resistance(),profile.evidence());
        var spawn=new EnemyRewardRegistry.Spawn(world,id,"Larva_Void","larva_void",profile.biomeKey(),profile.combatLevel(),
                ProgressionMath.Rank.COMMON,ProgressionMath.Rarity.ORDINARY,profile.id(),1000,null,combat);
        return new EnemyNativeGroupPreparation.MemberSource(spawn,"Larva Void");
    }
    private List<EnemyNativeGroupPreparation.MemberSource> sources(){return List.of(source(first),source(second));}
    @Test void exactNativeGroupProducesOneRepeatablePlannerRequest(){
        var one=EnemyNativeGroupPreparation.prepare(group(false,2),sources(),id->Optional.of(enabled),Vec3.ZERO,balance,manifest.revision(),true).orElseThrow();
        var again=EnemyNativeGroupPreparation.prepare(group(false,2),sources(),id->Optional.of(enabled),Vec3.ZERO,balance,manifest.revision(),true).orElseThrow();
        assertEquals(one,again);assertEquals(List.of(first,second),one.originals().stream().map(c->c.nativeBaseline().entityId()).toList());
        assertEquals("Empty",one.originals().getFirst().nativeBaseline().lootSourceId());
        assertEquals(0,one.additionalNativeCapacity());assertTrue(one.additionalSlots().isEmpty());
    }
    @Test void disabledRoleAndIncompleteOrMisorderedGroupsReturnToNativeFallback(){
        assertTrue(EnemyNativeGroupPreparation.prepare(group(false,2),sources(),id->Optional.of(disabled),Vec3.ZERO,balance,manifest.revision(),true).isEmpty());
        assertTrue(EnemyNativeGroupPreparation.prepare(group(true,2),sources(),id->Optional.of(enabled),Vec3.ZERO,balance,manifest.revision(),true).isEmpty());
        assertTrue(EnemyNativeGroupPreparation.prepare(group(false,3),sources(),id->Optional.of(enabled),Vec3.ZERO,balance,manifest.revision(),true).isEmpty());
        assertTrue(EnemyNativeGroupPreparation.prepare(group(false,2),List.of(source(second),source(first)),id->Optional.of(enabled),
                Vec3.ZERO,balance,manifest.revision(),true).isEmpty());
        var captured=group(false,2);
        var foreignJob=new NativeEnemySpawnGroups.Job(world,job.nativeJobId(),job.roleIndex(),"Trork_Warrior",
                job.environment(),job.spawnConfiguration(),job.expectedMembers());
        assertTrue(EnemyNativeGroupPreparation.prepare(new NativeEnemySpawnGroups.Group(foreignJob,captured.reservation(),
                captured.members(),false),sources(),id->Optional.of(enabled),Vec3.ZERO,balance,manifest.revision(),true).isEmpty());
    }
    @Test void additionalNativeMembersKeepTheOriginalDrawSeedAndExactFlockIdentity(){
        var originalGroup=group(false,2);
        var original=EnemyNativeGroupPreparation.prepare(originalGroup,sources(),id->Optional.of(enabled),
                Vec3.ZERO,balance,manifest.revision(),true).orElseThrow();
        var third=UUID.randomUUID();var fourth=UUID.randomUUID();
        var extraJob=new NativeEnemySpawnGroups.Job(world,job.nativeJobId(),job.roleIndex(),job.nativeRole(),
                job.environment(),job.spawnConfiguration(),2);
        var extras=new NativeEnemySpawnGroups.Group(extraJob,originalGroup.reservation(),List.of(
                new NativeEnemySpawnGroups.Member(third,"Larva_Void",new EnemyStaging.State(world,encounter,third,1,7)),
                new NativeEnemySpawnGroups.Member(fourth,"Larva_Void",new EnemyStaging.State(world,encounter,fourth,1,7))),false);
        var joined=EnemyNativeGroupPreparation.completeAdditional(originalGroup,original,extras,List.of(source(third),source(fourth)),
                id->Optional.of(enabled),balance,manifest.revision()).orElseThrow();
        assertEquals(original.seed(),joined.seed());assertEquals(original.packId(),joined.packId());
        assertEquals(original.originals(),joined.originals());assertEquals(2,joined.additionalNativeCapacity());
        assertEquals(List.of(third,fourth),joined.additionalSlots().stream().map(c->c.nativeBaseline().entityId()).toList());
        assertTrue(EnemyNativeGroupPreparation.completeAdditional(originalGroup,original,extras,List.of(source(fourth),source(third)),
                id->Optional.of(enabled),balance,manifest.revision()).isEmpty());
        var foreign=new NativeEnemySpawnGroups.Group(extraJob,
                new NativeEnemySpawnGroups.Reservation(world,encounter,2),extras.members(),false);
        assertTrue(EnemyNativeGroupPreparation.completeAdditional(originalGroup,original,foreign,List.of(source(third),source(fourth)),
                id->Optional.of(enabled),balance,manifest.revision()).isEmpty());
    }
    @Test void certifiedMixedSkeletonFlockRetainsEachConcreteRoleAndOneJobIdentity(){
        var leaderRole="Skeleton_Frost_Fighter";var memberRole="Skeleton_Frost_Soldier";
        var nativeJob=new NativeEnemySpawnGroups.Job(world,47,8,leaderRole,3,5,2);
        var group=new NativeEnemySpawnGroups.Group(nativeJob,new NativeEnemySpawnGroups.Reservation(world,encounter,1),
                List.of(new NativeEnemySpawnGroups.Member(first,leaderRole,new EnemyStaging.State(world,encounter,first,1,7)),
                        new NativeEnemySpawnGroups.Member(second,memberRole,new EnemyStaging.State(world,encounter,second,1,7))),false);
        var request=EnemyNativeGroupPreparation.prepare(group,List.of(sourceRole(first,leaderRole),sourceRole(second,memberRole)),
                manifest::role,Vec3.ZERO,balance,manifest.revision(),true).orElseThrow();
        assertEquals(List.of(leaderRole,memberRole),request.originals().stream()
                .map(candidate->candidate.nativeBaseline().nativeRoleId()).toList());
        assertTrue(EnemyNativeGroupPreparation.prepare(group,List.of(sourceRole(first,leaderRole),sourceRole(second,leaderRole)),
                manifest::role,Vec3.ZERO,balance,manifest.revision(),true).isEmpty());
    }
    @Test void nativeSelectedMixedAdditionsChangeTheFrozenPlanAndUncertifiedSelectionsFailClosed(){
        String leader="Skeleton_Frost_Fighter",soldier="Skeleton_Frost_Soldier";
        var selectedJob=new NativeEnemySpawnGroups.Job(world,47,8,leader,3,5,2);
        var reservation=new NativeEnemySpawnGroups.Reservation(world,encounter,1);
        var original=new NativeEnemySpawnGroups.Group(selectedJob,reservation,List.of(
                new NativeEnemySpawnGroups.Member(first,leader,new EnemyStaging.State(world,encounter,first,1,7)),
                new NativeEnemySpawnGroups.Member(second,soldier,new EnemyStaging.State(world,encounter,second,1,7))),false);
        var request=EnemyNativeGroupPreparation.prepare(original,List.of(sourceRole(first,leader),sourceRole(second,soldier)),
                manifest::role,Vec3.ZERO,balance,manifest.revision(),true).orElseThrow();
        var third=UUID.randomUUID();var extensionJob=new NativeEnemySpawnGroups.Job(world,47,8,leader,3,5,1);
        var soldierPick=new NativeEnemySpawnGroups.Group(extensionJob,reservation,List.of(
                new NativeEnemySpawnGroups.Member(third,soldier,new EnemyStaging.State(world,encounter,third,1,7))),false);
        var fighterPick=new NativeEnemySpawnGroups.Group(extensionJob,reservation,List.of(
                new NativeEnemySpawnGroups.Member(third,leader,new EnemyStaging.State(world,encounter,third,1,7))),false);
        var soldierPlan=EnemyNativeGroupPreparation.completeAdditional(original,request,soldierPick,
                List.of(sourceRole(third,soldier)),manifest::role,balance,manifest.revision()).orElseThrow();
        var fighterPlan=EnemyNativeGroupPreparation.completeAdditional(original,request,fighterPick,
                List.of(sourceRole(third,leader)),manifest::role,balance,manifest.revision()).orElseThrow();
        assertEquals(request.seed(),soldierPlan.seed());assertEquals(request.seed(),fighterPlan.seed());
        assertEquals(soldier,soldierPlan.additionalSlots().getFirst().nativeBaseline().nativeRoleId());
        assertEquals(leader,fighterPlan.additionalSlots().getFirst().nativeBaseline().nativeRoleId());
        assertNotEquals(soldierPlan.additionalSlots(),fighterPlan.additionalSlots());
        assertTrue(EnemyNativeGroupPreparation.completeAdditional(original,request,soldierPick,
                List.of(sourceRole(third,leader)),manifest::role,balance,manifest.revision()).isEmpty());
        assertTrue(EnemyNativeGroupPreparation.completeAdditional(original,request,soldierPick,
                List.of(sourceRole(third,soldier)),id->id.equals(soldier)?Optional.empty():manifest.role(id),
                balance,manifest.revision()).isEmpty());
        var stale=new NativeEnemySpawnGroups.Group(extensionJob,new NativeEnemySpawnGroups.Reservation(world,encounter,2),
                soldierPick.members(),false);
        assertTrue(EnemyNativeGroupPreparation.completeAdditional(original,request,stale,
                List.of(sourceRole(third,soldier)),manifest::role,balance,manifest.revision()).isEmpty());
    }
    @Test void capturedReservationCannotSubstituteAnotherNativeEntityOrRole(){
        var original=group(false,2);
        var request=EnemyNativeGroupPreparation.prepare(original,sources(),manifest::role,Vec3.ZERO,
                balance,manifest.revision(),true).orElseThrow();
        var template=Me12PlannerTest.context(DifficultyId.NORMAL);
        var context=new Me12Planner.Context(template.nativeSnapshotDigest(),request.nativePlanReceipt(),
                original.reservation().generation(),template.difficulty(),Me12Planner.Evidence.NATIVE_OWNER_CAPTURE,
                template.leader(),template.minions(),template.hordeSlots(),template.effectiveWeights());
        var third=UUID.randomUUID();var extraJob=new NativeEnemySpawnGroups.Job(world,job.nativeJobId(),
                job.roleIndex(),job.nativeRole(),job.environment(),job.spawnConfiguration(),1);
        var reserved=new NativeEnemySpawnGroups.Group(extraJob,original.reservation(),List.of(
                new NativeEnemySpawnGroups.Member(third,"Larva_Void",new EnemyStaging.State(world,encounter,third,1,8))),false);
        var valid=new NativeEnemyBirthDecision.V2Capture(request,context,Me12Policies.canonical(),enabled,sources(),
                reserved,List.of(source(third)));
        assertSame(reserved,valid.reserved());
        assertThrows(IllegalArgumentException.class,()->new NativeEnemyBirthDecision.V2Capture(request,context,
                Me12Policies.canonical(),enabled,sources(),reserved,List.of(source(UUID.randomUUID()))));
        assertThrows(IllegalArgumentException.class,()->new NativeEnemyBirthDecision.V2Capture(request,context,
                Me12Policies.canonical(),enabled,sources(),reserved,List.of()));
        var foreign=new NativeEnemySpawnGroups.Group(extraJob,new NativeEnemySpawnGroups.Reservation(world,encounter,2),
                reserved.members(),false);
        assertThrows(IllegalArgumentException.class,()->new NativeEnemyBirthDecision.V2Capture(request,context,
                Me12Policies.canonical(),enabled,sources(),foreign,List.of(source(third))));
    }
    private EnemyNativeGroupPreparation.MemberSource sourceRole(UUID id,String roleId){
        var profile=AuthoredEncounterCatalog.load().profiles().stream().filter(p->p.roleId().equals(roleId)
                &&p.difficulty()==DifficultyId.NORMAL).findFirst().orElseThrow();
        var combat=new EncounterProfileResolver.Resolved(world,id,profile.difficulty(),profile.id(),profile.worldProfileId(),
                profile.roleId(),profile.biomeKey(),profile.combatLevel(),profile.roleHealth(),profile.roleAttackBasis(),
                1,1,profile.resistance(),profile.evidence());
        var spawn=new EnemyRewardRegistry.Spawn(world,id,roleId,roleId.toLowerCase(Locale.ROOT),profile.biomeKey(),
                profile.combatLevel(),ProgressionMath.Rank.COMMON,ProgressionMath.Rarity.ORDINARY,profile.id(),1000,null,combat);
        return new EnemyNativeGroupPreparation.MemberSource(spawn,roleId.replace('_',' '));
    }
}
