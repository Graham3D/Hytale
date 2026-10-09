package com.inigmasgames.hytalerpg.combat.hytale;

import com.inigmasgames.hytalerpg.combat.resource.GearRecoveryRuntime;
import com.inigmasgames.hytalerpg.gear.GearEffectSnapshot;
import java.util.Objects;
import java.util.UUID;

/** Callback for the durable encounter owner's accepted, credited death reward. */
public final class GearCreditedDeathRecovery {
    public record Credit(UUID recipient,String world,String rewardEventId,
                         GearEffectSnapshot validEquipmentAtDeath,double normalHealthMaximumAtDeath) {
        public Credit {
            Objects.requireNonNull(recipient);Objects.requireNonNull(world);
            Objects.requireNonNull(rewardEventId);Objects.requireNonNull(validEquipmentAtDeath);
            if(world.isBlank()||rewardEventId.isBlank()||!Double.isFinite(normalHealthMaximumAtDeath)
                    ||normalHealthMaximumAtDeath<=0)throw new IllegalArgumentException("INVALID_DEATH_RECOVERY_CREDIT");
        }
    }
    private final GearRecoveryRuntime recovery;
    public GearCreditedDeathRecovery(GearRecoveryRuntime recovery){this.recovery=Objects.requireNonNull(recovery);}
    /** Call only for a real player share from finishDeath, after its durable dedup accepted the reward. */
    public void accepted(Credit credit){
        recovery.onKill(credit.recipient(),credit.world(),credit.rewardEventId(),true,
                credit.validEquipmentAtDeath(),credit.normalHealthMaximumAtDeath(),System.nanoTime()/1e9);
    }
}
