package com.inigmasgames.hytalerpg.progress;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Immutable write-ahead intent. Its hash binds player, exact event payload and before checkpoint. */
public record RewardIntent(UUID player,EarnedReward reward,RewardCheckpoint before,String hash) {
    private static final Gson JSON=new Gson();
    public RewardIntent {
        Objects.requireNonNull(player);Objects.requireNonNull(reward);Objects.requireNonNull(before);
        if(!calculate(player,reward,before).equals(hash))throw new IllegalArgumentException("REWARD_INTENT_HASH_MISMATCH");
        before.advance(reward,hash); // Validate arithmetic and legacy consistency BEFORE persisting intent.
    }
    public static RewardIntent create(UUID player,EarnedReward reward,RewardCheckpoint before){
        return new RewardIntent(player,reward,before,calculate(player,reward,before));
    }
    public RewardCheckpoint after(){return before.advance(reward,hash);}
    public static String digest(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    private static String calculate(UUID player,EarnedReward reward,RewardCheckpoint before){
        return digest("rpg-earned-v1\n"+player+"\n"+JSON.toJson(reward)+"\n"+JSON.toJson(before));
    }
}
