package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.FileEncounterStore;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.*;

class EnemyPackRecordTest {
    @TempDir Path directory;
    private final UUID world=UUID.randomUUID(),leader=UUID.randomUUID(),first=UUID.randomUUID(),last=UUID.randomUUID();
    private EnemyPackRecord reserved(){return new EnemyPackRecord(1,UUID.randomUUID(),world,UUID.randomUUID(),1,State.RESERVED,null,Vec3.ZERO,
            List.of(new Member(leader,leader,"Trork_Warrior",Role.LEADER),new Member(first,first,"Trork_Warrior",Role.MINION),
                    new Member(last,last,"Trork_Warrior",Role.MINION)),leader,Set.of(first,last),Map.of(),false,false,"native-plan/1",null);}
    private String receipt(UUID entity){return "enemy-death/"+world+"/"+entity;}
    @Test void finalGuardReleasesMonotonicallyAndDuplicateReceiptsDoNotAddDeaths(){
        var pack=reserved().staged().publish();assertTrue(pack.blocksExternalMutation(leader));assertFalse(pack.blocksExternalMutation(first));
        assertTrue(pack.blocksConversion(first));assertTrue(pack.blocksConversion(leader));
        pack=pack.terminalDefeat(first,receipt(first));assertEquals(1,pack.livingGuards());assertTrue(pack.blocksExternalMutation(leader));
        assertEquals(pack,pack.terminalDefeat(first,receipt(first)));
        var released=pack.terminalDefeat(last,receipt(last));assertEquals(State.RELEASED,released.state());
        assertTrue(released.packboundReleased());assertFalse(released.blocksExternalMutation(leader));assertFalse(released.blocksConversion(leader));
        assertTrue(released.suspend().resume().packboundReleased());
        assertEquals(State.DEFEATED,released.terminalDefeat(leader,receipt(leader)).state());
    }
    @Test void rebindOrUnloadDoesNotCountAsDeathAndAbortHasNoEconomicAdmission(){
        var guarded=reserved().staged().publish().terminalDefeat(first,receipt(first));
        var suspended=guarded.suspend();assertTrue(suspended.blocksExternalMutation(first));
        var restored=new Gson().fromJson(new Gson().toJson(suspended),EnemyPackRecord.class).resume();
        assertEquals(guarded,restored);assertTrue(restored.blocksExternalMutation(leader));
        var aborted=suspended.abort("LOADED_RECOVERY_TIMEOUT");assertFalse(aborted.packboundReleased());
        assertFalse(aborted.economicAdmission(leader));assertThrows(IllegalArgumentException.class,aborted::resume);
    }
    @Test void rejectsForeignGuardAndGuardedLeaderDefeat(){
        var pack=reserved().staged().publish();
        assertThrows(IllegalArgumentException.class,()->pack.terminalDefeat(UUID.randomUUID(),receipt(first)));
        assertThrows(IllegalArgumentException.class,()->pack.terminalDefeat(leader,receipt(leader)));
        assertThrows(IllegalArgumentException.class,()->pack.terminalDefeat(first,receipt(last)));
        assertFalse(pack.blocksExternalMutation(UUID.randomUUID()));
    }
    @Test void encounterStoreRetainsSealedRosterAndCannotAcceptSimulatedGuardDeaths(){
        var original=reserved();
        try(var store=new FileEncounterStore(directory)) {
            store.reserveEnemyPack(original);store.transitionEnemyPack(world,original.packId(),EnemyPackRecord::staged);
            store.transitionEnemyPack(world,original.packId(),EnemyPackRecord::publish);
            assertThrows(IllegalStateException.class,()->store.transitionEnemyPack(world,original.packId(),p->p.terminalDefeat(first,receipt(first))));
            assertEquals(2,store.reconcileEnemyPackDefeats(world,original.packId()).livingGuards());
        }
        try(var store=new FileEncounterStore(directory)) {
            var loaded=store.enemyPack(world,original.packId()).orElseThrow();assertEquals(State.GUARDED,loaded.state());
            assertEquals(loaded,store.reserveEnemyPack(original)); // Duplicate birth callback cannot reroll.
            store.transitionEnemyPack(world,original.packId(),EnemyPackRecord::suspend);
            store.transitionEnemyPack(world,original.packId(),p->p.abort("RECOVERY_TIMEOUT"));
            assertThrows(IllegalStateException.class,()->store.transitionEnemyPack(world,original.packId(),p->original.staged().publish()));
        }
    }
}
