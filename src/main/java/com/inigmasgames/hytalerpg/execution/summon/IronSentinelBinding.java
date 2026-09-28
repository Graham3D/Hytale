package com.inigmasgames.hytalerpg.execution.summon;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.gear.GearInstance;
import java.util.Objects;
import java.util.UUID;

/** Durable logical companion identity and frozen source, separate from a native NPC instance. */
public record IronSentinelBinding(int schemaVersion,UUID instanceId,UUID ownerId,String sourceEvent,
                                  GearInstance boundItem,State state,double currentHealth,UUID worldId,
                                  Vec3 position,long createdAt,long revision,int effectiveLevel,
                                  double powerFactor,double nativeInterval) {
    public enum State { PREPARED, DORMANT, RESTORING, ACTIVE, DEAD, ABORTED }
    /** Read-compatible with the initial custody record; old rows use rank-one defaults. */
    public IronSentinelBinding(int schemaVersion,UUID instanceId,UUID ownerId,String sourceEvent,
                               GearInstance boundItem,State state,double currentHealth,UUID worldId,
                               Vec3 position,long createdAt,long revision){
        this(schemaVersion,instanceId,ownerId,sourceEvent,boundItem,state,currentHealth,worldId,position,
                createdAt,revision,1,1,2);
    }
    public IronSentinelBinding {
        if((schemaVersion!=1&&schemaVersion!=2)||instanceId==null||ownerId==null||sourceEvent==null||sourceEvent.isBlank()
                ||boundItem==null||state==null||!Double.isFinite(currentHealth)||currentHealth<0
                ||worldId==null||position==null||createdAt<0||revision<0
                ||schemaVersion==2&&(effectiveLevel<1||effectiveLevel>1001||!Double.isFinite(powerFactor)
                ||powerFactor<=0||!Double.isFinite(nativeInterval)||nativeInterval<=0))
            throw new IllegalArgumentException("Invalid Iron Sentinel binding");
    }
    public int restoredLevel(){return effectiveLevel>0?effectiveLevel:1;}
    public double restoredPowerFactor(){return powerFactor>0&&Double.isFinite(powerFactor)?powerFactor:1;}
    public double restoredInterval(){return nativeInterval>0&&Double.isFinite(nativeInterval)?nativeInterval:2;}
    public IronSentinelBinding withState(State next,double health,UUID world,Vec3 at){
        if((state==State.DEAD||state==State.ABORTED)&&next!=state)throw new IllegalStateException("Terminal Sentinel cannot revive");
        return new IronSentinelBinding(2,instanceId,ownerId,sourceEvent,boundItem,Objects.requireNonNull(next),
                health,Objects.requireNonNull(world),Objects.requireNonNull(at),createdAt,Math.addExact(revision,1),
                restoredLevel(),restoredPowerFactor(),restoredInterval());
    }
}
