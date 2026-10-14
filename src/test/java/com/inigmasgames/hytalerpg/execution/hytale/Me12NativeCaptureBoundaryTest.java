package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.enemies.*;
import com.inigmasgames.hytalerpg.progress.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Captured-owner boundary regressions; not a connected publication certificate. */
class Me12NativeCaptureBoundaryTest {
    @Test void revalidationIgnoresOnlyObservationTimeAndPreservesEverySourceField(){
        var actor=Me12PersistenceTest.fixture(false).root().plan().actors().getFirst();
        var source=Me12PersistenceTest.spawn(actor,false);
        var frozen=new EnemyNativeGroupPreparation.MemberSource(source,"Native name");
        var later=new EnemyRewardRegistry.Spawn(source.world(),source.enemy(),source.roleId(),source.combatIdentity(),source.biomeKey(),source.level(),source.rank(),source.rarity(),source.registryProfile(),source.spawnedAtMillis()+1000,source.milestone(),source.combat(),source.enemyRewards());
        assertTrue(NativeEnemyBirthDecision.sameSources(List.of(frozen),List.of(new EnemyNativeGroupPreparation.MemberSource(later,"Native name"))));
        assertFalse(NativeEnemyBirthDecision.sameSources(List.of(frozen),List.of(new EnemyNativeGroupPreparation.MemberSource(later,"Changed name"))));
        var changed=new EnemyRewardRegistry.Spawn(source.world(),source.enemy(),source.roleId(),"changed_source_identity",source.biomeKey(),source.level(),source.rank(),source.rarity(),source.registryProfile(),source.spawnedAtMillis(),source.milestone(),source.combat(),source.enemyRewards());
        assertFalse(NativeEnemyBirthDecision.sameSources(List.of(frozen),List.of(new EnemyNativeGroupPreparation.MemberSource(changed,"Native name"))));
    }
    @Test void untrustedEvidenceAndGlobalEnabledRemainRejected(){
        var fixture=Me12PersistenceTest.fixture(false);var c=fixture.compiled().context();
        var untrusted=new Me12Planner.Context(c.nativeSnapshotDigest(),c.sourceReceipt(),c.generation(),c.difficulty(),Me12Planner.Evidence.UNCERTIFIED_PRODUCTION,c.leader(),c.minions(),c.hordeSlots(),c.effectiveWeights());
        assertThrows(IllegalStateException.class,()->Me12Planner.compile(untrusted,fixture.policies(),new Me12Planner.Budget(n->{})));
        assertThrows(IllegalArgumentException.class,Me12FeatureState.ENABLED::validateStage2);
        for(var state:Me12FeatureState.values())assertFalse(state.allowsNewV2Births());
    }
    @Test void firesteelIncomingContractDoesNotGrantAnOutgoingActionOrCoverOtherRoles(){
        var bindings=EnemyNativeBindings.load();var role=bindings.role("Golem_Firesteel").orElseThrow();
        assertTrue(EnemyIncomingOnlyAttachment.certified(role));assertTrue(role.actions().isEmpty());
        assertFalse(role.capabilities().contains(EnemyAffixRegistry.Capability.DIRECT_HIT));
        assertFalse(EnemyIncomingOnlyAttachment.certified(bindings.role("Skeleton_Scout").orElseThrow()));
        var json=new com.google.gson.Gson().toJsonTree(role).getAsJsonObject();json.addProperty("sourceAssetSha256","0".repeat(64));
        assertFalse(EnemyIncomingOnlyAttachment.certified(new com.google.gson.Gson().fromJson(json,EnemyNativeBindings.Role.class)));
    }
}
