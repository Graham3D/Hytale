package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.spawning.world.component.SpawnJobData;
import com.inigmasgames.hytalerpg.enemies.*;
import com.inigmasgames.hytalerpg.diagnostics.MonsterSpawnTrace;
import java.util.*;
import java.util.concurrent.*;

/** Capacity and immutable-root handoff for one fully captured native group. */
public final class NativeEnemyBirthReservation {
    public record Sealed(NativeEnemyBirthDecision.Selected selected,EnemyPackCapacity.Reservation capacity){
        public Sealed{Objects.requireNonNull(selected);Objects.requireNonNull(capacity);
            if(!capacity.equals(EnemyPackCapacity.Reservation.of(selected.root().plan().pack())))
                throw new IllegalArgumentException("ENEMY_SEALED_CAPACITY_IDENTITY");}
    }
    private final NativeEnemyBirthDecision decisions;
    private final EnemyWorldAdmission admission;
    private final HytaleEncounterRewards rewards;
    public NativeEnemyBirthReservation(NativeEnemyBirthDecision decisions,EnemyWorldAdmission admission,
            HytaleEncounterRewards rewards){
        this.decisions=Objects.requireNonNull(decisions);this.admission=Objects.requireNonNull(admission);
        this.rewards=Objects.requireNonNull(rewards);
    }
    /** Async completion returns to the same world thread. An uncertain write leaves actors staged and closes admission. */
    public CompletionStage<Optional<Sealed>> begin(Store<EntityStore> store,NativeEnemySpawnGroups.Group original,
            SpawnJobData nativeJob){
        if(!store.isInThread()||!admission.admits(original.job().world()))
            throw new IllegalStateException("ENEMY_BIRTH_WORLD_NOT_ADMITTED");
        Optional<NativeEnemyBirthDecision.Selected> selected;
        try{selected=decisions.select(store,original,nativeJob);}
        catch(RuntimeException|Error uncertain){admission.failClosed(original.job().world());throw uncertain;}
        if(selected.isEmpty())return CompletableFuture.completedStage(Optional.empty());
        return seal(store,selected.get(),false);
    }
    public CompletionStage<Optional<Sealed>> beginQa(Store<EntityStore> store,NativeEnemySpawnGroups.Group group,
            EnemyQaSpawnRequest qa){
        throw new IllegalStateException("ENEMY_QA_TRANSIENT_OWNER_REQUIRED");
    }
    private CompletionStage<Optional<Sealed>> seal(Store<EntityStore> store,NativeEnemyBirthDecision.Selected selected,
            boolean qa){
        var birth=selected.root().plan();
        var capacity=EnemyPackCapacity.Reservation.of(birth.pack());
        EnemyPackCapacity.Admission reservation;
        try{reservation=selected.preLease()==null?admission.reserve(capacity):
                admission.pending(selected.preLease())
                        ?new EnemyPackCapacity.Admission(EnemyPackCapacity.Gate.RESERVED,selected.preLease())
                        :throwMissingLease();}
        catch(RuntimeException uncertain){admission.failClosed(birth.world());throw uncertain;}
        MonsterSpawnTrace.event("PACK_RESERVATION",selected.original().job().world(),selected.original().job().environment(),
                selected.original().job().nativeRole(),"job="+selected.original().job().nativeJobId()
                        +" encounter="+birth.encounter()+" gate="+reservation.gate()+" qa="+qa);
        if(reservation.gate()!=EnemyPackCapacity.Gate.RESERVED){
            try{if(!qa)decisions.restore(store,selected);}
            catch(RuntimeException failedRestore){admission.failClosed(birth.world());throw failedRestore;}
            return CompletableFuture.completedStage(Optional.empty());
        }
        CompletionStage<EnemyBirthPlan> write;
        try{write=rewards.reserveEnemyBirthRoot(selected.root());}
        catch(RuntimeException synchronousRejection){
            // No writer task was accepted, so the original native group is still recoverable in place.
            try{if(!qa)decisions.restore(store,selected);}
            catch(RuntimeException rollback){synchronousRejection.addSuppressed(rollback);admission.failClosed(birth.world());}
            finally{admission.releaseUnsealed(capacity);}
            throw synchronousRejection;
        }
        var result=new CompletableFuture<Optional<Sealed>>();
        var nativeWorld=store.getExternalData().getWorld();
        write.whenComplete((written,error)->{
            if(error!=null||!birth.equals(written)){
                admission.failClosed(birth.world());
                result.completeExceptionally(error!=null?error:new IllegalStateException("ENEMY_BIRTH_ROOT_WRITE_MISMATCH"));
                return;
            }
            try{nativeWorld.execute(()->{
                try{
                    var current=nativeWorld.getEntityStore().getStore();
                    if(!current.isInThread()||!admission.admits(birth.world()))
                        throw new IllegalStateException("ENEMY_BIRTH_ROOT_WORLD_CHANGED");
                    for(var actor:birth.actors()){
                        var ref=current.getExternalData().getRefFromUUID(actor.entityId());
                        var marker=ref==null||!ref.isValid()?null:current.getComponent(ref,EnemyStaging.getComponentType());
                        if(marker==null||!marker.state().world().equals(birth.world())
                                ||!marker.state().encounter().equals(birth.encounter())
                                ||marker.state().generation()!=birth.generation()
                                ||!marker.state().entity().equals(actor.entityId()))
                            throw new IllegalStateException("ENEMY_BIRTH_SEALED_ROSTER_CHANGED");
                    }
                    result.complete(Optional.of(new Sealed(selected,capacity)));
                }catch(RuntimeException failure){admission.failClosed(birth.world());result.completeExceptionally(failure);}
            });}catch(RuntimeException queueFailure){admission.failClosed(birth.world());result.completeExceptionally(queueFailure);}
        });
        return result.minimalCompletionStage();
    }
    private static EnemyPackCapacity.Admission throwMissingLease(){
        throw new IllegalStateException("ENEMY_BIRTH_PRELEASE_LOST");
    }
}
