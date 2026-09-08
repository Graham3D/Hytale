package com.inigmasgames.hytalerpg.execution.summon;

import java.util.UUID;

/** A durable consumption decision must precede gameplay dispatch. Never prune using corpse lifetime. */
public interface CorpseConsumptionStore {
    boolean consumed(UUID world, UUID corpse);
    boolean consume(CorpseLedger.Claim claim);
}
