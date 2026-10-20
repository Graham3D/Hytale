package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.progress.*;
import com.inigmasgames.hytalerpg.execution.support.ShieldEscrow;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Child JVM only; abrupt halt intentionally bypasses close/shutdown, never touches a live save. */
public final class ShieldHandoffCrashProcess {
    public static final UUID PLAYER=new UUID(13,1),WORLD=new UUID(13,2),ENEMY=new UUID(13,3);
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]);String boundary=args[1];
        if(boundary.startsWith("ENCOUNTER_")){
            var store=FileEncounterStore.durableV2(root.resolve("encounters"));
            var runtime=new PersistentEncounterRuntime(store,(id,reward)->{});
            var spawn=EnemyRewardRegistry.load().classify(WORLD,ENEMY,"Wolf_Black","Default/Zone1_Tier1/Forest_Birch",EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,100).orElseThrow();
            runtime.attachNative(WORLD,ENEMY,"Wolf_Black",Optional.of(spawn)).toCompletableFuture().get(5,TimeUnit.SECONDS);
            runtime.submitDamage(WORLD,ENEMY,PLAYER,100,90,100,true,101).durable().toCompletableFuture().get(5,TimeUnit.SECONDS);
            var prepared=runtime.prepareDeathNative(WORLD,ENEMY,Vec3.ZERO,102).toCompletableFuture().get(5,TimeUnit.SECONDS).orElseThrow();
            if(boundary.equals("ENCOUNTER_PLAN_DURABLE"))runtime.finishDeath(prepared,List.of(new EncounterContributions.Participant(PLAYER,WORLD,Vec3.ZERO,1,true,null)));
            Runtime.getRuntime().halt(73);
        }
        var repo=new FileRpgPlayerStateRepository(root.resolve("players"));var state=RpgPlayerState.create(PLAYER);
        var actual=SupportProgress.INITIAL.guard(SupportProgress.INITIAL.managuard().validateCapacity(50,1));state.support=actual;repo.save(state);
        var escrow=new ShieldEscrow();var debit=escrow.prepare(actual);
        if(boundary.equals("BEFORE_DEBIT"))Runtime.getRuntime().halt(73);
        state.support=debit.persisted().nextRevision();repo.save(state);
        if(boundary.equals("DEBIT_BEFORE_PUBLICATION"))Runtime.getRuntime().halt(73);
        escrow.submitted(debit,CompletableFuture.completedStage(state.support));escrow.poll();
        actual=actual.guard(escrow.absorb(actual.managuard(),20,50,false).ledger());
        if(boundary.equals("OWNER_HIT"))Runtime.getRuntime().halt(73);
        actual=actual.guard(escrow.absorb(actual.managuard(),10,50,true).ledger());
        if(boundary.equals("CLEAN_SETTLEMENT")){
            escrow.revoke();state.support=new SupportProgress(state.support.revision()+1,actual.lastAuraEpoch(),actual.managuard(),actual.toggleLocks());repo.save(state);
        }
        Runtime.getRuntime().halt(73);
    }
}
