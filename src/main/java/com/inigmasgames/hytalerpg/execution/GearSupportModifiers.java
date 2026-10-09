package com.inigmasgames.hytalerpg.execution;

import com.inigmasgames.hytalerpg.execution.connection.ConnectionProfile;
import com.inigmasgames.hytalerpg.gear.GearEffectSnapshot;

public final class GearSupportModifiers {
    private GearSupportModifiers() { }
    public static double primaryReach(ConnectionProfile profile,GearEffectSnapshot gear) {
        return profile.range()*(profile.kind()==ConnectionProfile.Kind.TETHER
                ||profile.kind()==ConnectionProfile.Kind.DRAIN||profile.friendlyTether()
                ?1+gear.percent(GearEffectSnapshot.Operator.TETHER_REACH):1);
    }
    /** Apply the paid ramp to the payload bucket itself, including a bucket previously clamped to zero. */
    public static SkillExecutionContext channelPayload(SkillExecutionContext context,double paidSeconds) {
        if(!Double.isFinite(paidSeconds)||paidSeconds<0)throw new IllegalArgumentException("Invalid paid channel time");
        double increase=context.gearSnapshot().percent(GearEffectSnapshot.Operator.CHANNEL_RAMP)*Math.min(3,paidSeconds);
        return increase==0?context:context.withSnapshot(context.snapshot().withModifiers(
                context.snapshot().modifiers().withIncreased(increase)));
    }
}
