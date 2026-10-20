package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.require;

/** Durable placement/cycle state. Reload reads a reservation; it never allocates another cycle. */
public record SuperUniqueAnchor(int schemaVersion,UUID anchorId,UUID worldId,DifficultyId difficulty,Vec3 position,
        String templateId,int templateRevision,String placementValidationId,SuperUniqueTemplates.Respawn respawn,
        long revision,long cycle,State state,UUID encounterId,long nextEligibleMillis,String terminalReceipt) {
    public enum State { IDLE, RESERVED, LIVE, COOLDOWN, EXHAUSTED, REMOVED }
    public SuperUniqueAnchor {
        Objects.requireNonNull(anchorId);Objects.requireNonNull(worldId);Objects.requireNonNull(difficulty);Objects.requireNonNull(position);
        Objects.requireNonNull(respawn);Objects.requireNonNull(state);
        require(schemaVersion==1&&revision>=1&&cycle>=0&&templateRevision>=1&&nextEligibleMillis>=0,"ANCHOR_VERSION_OR_CLOCK");
        require(templateId!=null&&templateId.matches("[a-z][a-z0-9_]{1,63}")&&placementValidationId!=null&&!placementValidationId.isBlank(),"ANCHOR_SOURCE_ID");
        require((state==State.RESERVED||state==State.LIVE)==(encounterId!=null),"ANCHOR_OCCUPANCY_MISMATCH");
        require(state!=State.COOLDOWN||respawn.mode()==SuperUniqueTemplates.RespawnMode.REUSABLE,"ONE_SHOT_CANNOT_COOLDOWN");
        require(state!=State.EXHAUSTED||respawn.mode()==SuperUniqueTemplates.RespawnMode.ONE_SHOT,"REUSABLE_CANNOT_EXHAUST");
        require(state!=State.IDLE||cycle==0,"ANCHOR_CYCLE_RESET");
        require(state!=State.COOLDOWN&&state!=State.EXHAUSTED||terminalReceipt!=null&&!terminalReceipt.isBlank(),"ANCHOR_TERMINAL_RECEIPT_REQUIRED");
    }
    public boolean due(long serverMillis){require(serverMillis>=0,"INVALID_ANCHOR_CLOCK");return state==State.IDLE||state==State.COOLDOWN&&serverMillis>=nextEligibleMillis;}
    public SuperUniqueAnchor reserve(long serverMillis){
        if(state==State.RESERVED)return this;
        require(due(serverMillis),"ANCHOR_NOT_DUE_OR_OCCUPIED");
        long next=Math.addExact(cycle,1);
        UUID encounter=UUID.nameUUIDFromBytes(("master-enemies/anchor/"+worldId+"/"+anchorId+"/cycle/"+next).getBytes(StandardCharsets.UTF_8));
        return copy(next,State.RESERVED,encounter,0,terminalReceipt);
    }
    public SuperUniqueAnchor published(UUID encounter){
        require(Objects.equals(encounterId,encounter),"FOREIGN_ANCHOR_ENCOUNTER");
        if(state==State.LIVE)return this;require(state==State.RESERVED,"ANCHOR_NOT_RESERVED");return copy(cycle,State.LIVE,encounterId,0,terminalReceipt);
    }
    /** Receipts are verified by FileEncounterStore against the sealed pack, not trusted from a command. */
    public SuperUniqueAnchor terminal(UUID encounter,String receipt,long serverMillis){
        require(receipt!=null&&!receipt.isBlank()&&serverMillis>=0,"INVALID_ANCHOR_TERMINAL");
        if(receipt.equals(terminalReceipt)&&(state==State.COOLDOWN||state==State.EXHAUSTED))return this;
        require((state==State.RESERVED||state==State.LIVE)&&Objects.equals(encounterId,encounter),"ANCHOR_TERMINAL_IDENTITY");
        boolean reusable=respawn.mode()==SuperUniqueTemplates.RespawnMode.REUSABLE;
        return copy(cycle,reusable?State.COOLDOWN:State.EXHAUSTED,null,reusable?Math.addExact(serverMillis,respawn.delaySeconds()*1000L):0,receipt);
    }
    public SuperUniqueAnchor removed(){return state==State.REMOVED?this:copy(cycle,State.REMOVED,null,0,terminalReceipt);}
    private SuperUniqueAnchor copy(long cycle,State state,UUID encounter,long due,String receipt){return new SuperUniqueAnchor(schemaVersion,anchorId,worldId,difficulty,position,
            templateId,templateRevision,placementValidationId,respawn,Math.addExact(revision,1),cycle,state,encounter,due,receipt);}
}
