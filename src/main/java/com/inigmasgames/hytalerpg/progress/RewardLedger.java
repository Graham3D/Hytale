package com.inigmasgames.hytalerpg.progress;

/** Small player-owned checkpoint. Permanent event receipts live in the durable reward store. */
public record RewardLedger(long sequence,String lastReceiptHash,long insight) {
    public static final RewardLedger INITIAL=new RewardLedger(0,"",0);
    public RewardLedger {
        if(sequence<0||insight<0||lastReceiptHash==null||
                (sequence==0?!lastReceiptHash.isEmpty():!lastReceiptHash.matches("[0-9a-f]{64}")))
            throw new IllegalArgumentException("INVALID_REWARD_CHECKPOINT");
    }
}
