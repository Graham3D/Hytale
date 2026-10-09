package com.inigmasgames.hytalerpg.execution.hytale;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Guards the no-IO/native-only path that a declined natural promotion must use. */
class NativeEnemyPreRootRollbackTest {
    private static String source(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale",file));
    }
    @Test void preRootReleaseCannotReenterEncounterAttachment() throws Exception {
        var rewards=source("HytaleEncounterRewards.java");
        var start=rewards.indexOf("public void releasePreRootNativeGroup(");
        var end=rewards.indexOf("public void restoreStagedNativeGroup(",start);
        assertTrue(start>=0&&end>start);
        var release=rewards.substring(start,end);
        assertTrue(release.contains("EnemyStaging.releaseGroup(store,members)"));
        assertFalse(release.contains("added("));
        assertFalse(release.contains("attachObserved("));
        assertFalse(release.contains("runtime."));
        assertTrue(rewards.contains("releasingPreRoot.contains(new ExclusionKey(world(store),id(store,ref)))"));
    }
    @Test void declinedAndRecoveredPreRootGroupsUseNativeReleaseButCommittedCompensationKeepsItsOwner() throws Exception {
        var decision=source("NativeEnemyBirthDecision.java");
        var start=decision.indexOf("private void restore(Store<EntityStore> store,NativeEnemySpawnGroups.Group original,");
        var end=decision.indexOf("private Optional<List<EnemyNativeGroupPreparation.MemberSource>> classify(",start);
        assertTrue(start>=0&&end>start);
        var restore=decision.substring(start,end);
        assertTrue(restore.contains("NativeEnemyFlockExtension.discard(store,original,additional)"));
        assertTrue(restore.contains("rewards.releasePreRootNativeGroup("));
        assertFalse(restore.contains("restoreStagedNativeGroup("));
        var recovery=source("NativeEnemyStagingRecovery.java");
        assertTrue(recovery.contains("if(birth==null)rewards.releasePreRootNativeGroup(current,List.of(state))"));
        assertTrue(recovery.contains("else rewards.restoreStagedNativeGroup(current,List.of(state))"));
        var rewards=source("HytaleEncounterRewards.java");
        assertTrue(rewards.contains("runtime.compensateEnemyBirth(root)"));
        assertTrue(rewards.contains("restoreStagedNativeGroup(current,group.members()"));
    }
    @Test void actualCommittedEliteStillUsesDurableRootAndWorldThreadHandoffDoesNotThrow() throws Exception {
        var reservation=source("NativeEnemyBirthReservation.java");
        assertTrue(reservation.contains("write=rewards.reserveEnemyBirthRoot(selected.root())"));
        var owner=source("NativeEnemyBirthOwner.java");
        var start=owner.indexOf("@Override public void captured(Store<EntityStore> store,NativeEnemySpawnGroups.Group group,SpawnJobData nativeJob)");
        var end=owner.indexOf("private record QaSpawnSite",start);
        assertTrue(start>=0&&end>start);
        var handoff=owner.substring(start,end);
        assertTrue(handoff.contains("processBirth(store,group,reservation.begin(store,group,nativeJob),null)"));
        assertFalse(handoff.contains("throw rejected"));
    }
}
