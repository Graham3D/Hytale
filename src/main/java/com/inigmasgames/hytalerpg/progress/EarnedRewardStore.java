package com.inigmasgames.hytalerpg.progress;

import java.util.UUID;

/** Must be called under the player's existing state-authority lock. */
public interface EarnedRewardStore {
    interface Authority {
        RewardCheckpoint current();
        void commit(RewardIntent intent);
    }
    enum Outcome { COMMITTED, DUPLICATE }
    record Result(Outcome outcome,long sequence,String receiptHash,boolean recoveredPending){}
    Result award(UUID player,EarnedReward reward,Authority authority);
    default Result awardChecked(UUID player,EarnedReward reward,java.util.function.Consumer<RewardCheckpoint> precondition,Authority authority){
        throw new UnsupportedOperationException("DURABLE_CHECKED_REWARDS_UNAVAILABLE");
    }
    /** Server-owned event: resolve state-dependent price/pity/roll exactly once, after permanent dedup. */
    default Result awardGenerated(UUID player,String eventId,java.util.function.Function<RewardCheckpoint,EarnedReward> factory,Authority authority){
        throw new UnsupportedOperationException("DURABLE_GENERATED_REWARDS_UNAVAILABLE");
    }
    boolean recover(UUID player,Authority authority);
}
