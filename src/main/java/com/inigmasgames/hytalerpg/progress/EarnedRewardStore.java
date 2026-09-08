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
    boolean recover(UUID player,Authority authority);
}
