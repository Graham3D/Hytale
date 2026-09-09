package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.progress.*;
import java.nio.file.Path;
import java.util.*;

/** Test-only child process. Deliberately bypasses close/shutdown hooks at an exact persisted boundary. */
public final class EncounterJournalCrashProcess {
    public static void main(String[] args){
        var boundary=FileEncounterStore.JournalBoundary.valueOf(args[1]);
        var store=new FileEncounterStore(Path.of(args[0]),ignored->{},at->{if(at==boundary)Runtime.getRuntime().halt(73);});
        var world=new UUID(1,1);var enemy=new UUID(2,2);var actor=new UUID(3,3);
        var spawn=EnemyRewardRegistry.load().classify(world,enemy,"Wolf_Black","Default/Zone1_Tier1/Forest_Birch",EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,100).orElseThrow();
        var runtime=new PersistentEncounterRuntime(store,(id,reward)->{throw new AssertionError("No death");});
        if(!runtime.attach(world,enemy,"Wolf_Black",Optional.of(spawn))||!runtime.damage(world,enemy,actor,100,90,100,true,101))throw new AssertionError("Not accepted");
        store.checkpoint();throw new AssertionError("Boundary not reached");
    }
}
