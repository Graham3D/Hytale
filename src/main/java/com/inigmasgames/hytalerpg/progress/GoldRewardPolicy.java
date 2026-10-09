package com.inigmasgames.hytalerpg.progress;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Initial authorable Gold pot; no implicit rank or party multiplier. */
public record GoldRewardPolicy(long ordinaryBaseGold) {
    public GoldRewardPolicy {
        if (ordinaryBaseGold < 1 || ordinaryBaseGold > 1_000_000)
            throw new IllegalArgumentException("INVALID_ORDINARY_GOLD_POT");
    }
    public static GoldRewardPolicy loadCanonical() {
        try (var stream = GoldRewardPolicy.class.getResourceAsStream("/rpg/gear/gold-reward-v1.json")) {
            if (stream == null) throw new IllegalStateException("MISSING_GOLD_REWARD_POLICY");
            var parsed=new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), GoldRewardPolicy.class);
            if(parsed==null)throw new IllegalStateException("EMPTY_GOLD_REWARD_POLICY");
            return new GoldRewardPolicy(parsed.ordinaryBaseGold());
        } catch (java.io.IOException error) { throw new IllegalStateException("GOLD_REWARD_POLICY_UNREADABLE", error); }
    }
}
