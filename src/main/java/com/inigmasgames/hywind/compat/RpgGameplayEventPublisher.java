package com.inigmasgames.hywind.compat;

import java.util.UUID;

/** Internal producer seam; it remains a no-op when ImmersiveNPCs is absent. */
@FunctionalInterface
public interface RpgGameplayEventPublisher extends AutoCloseable {
    RpgGameplayEventPublisher NO_OP = observation -> { };

    void entityDamaged(DamageObservation observation);

    @Override
    default void close() { }

    record DamageObservation(UUID actorId, UUID targetId, String skillId,
                             String correlationId, double amount, double healthAfter) { }
}
