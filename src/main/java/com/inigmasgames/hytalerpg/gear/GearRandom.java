package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.progress.RewardIntent;
import java.util.*;

/** Versioned independent streams: MF cannot shift the opportunity/base/intrinsic draws. */
public final class GearRandom {
    public static final String VERSION="gear-event-sha256-split-v1";
    private final String seed;
    public GearRandom(String seed){if(seed==null||seed.isBlank())throw new IllegalArgumentException("Seed required");this.seed=seed;}
    public SplittableRandom stream(String purpose){return new SplittableRandom(Long.parseUnsignedLong(RewardIntent.digest(VERSION+"/"+seed+"/"+purpose).substring(0,16),16));}
    public <T> T weighted(Map<T,Double> weights,String purpose){
        double sum=weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if(!Double.isFinite(sum)||sum<=0||weights.values().stream().anyMatch(v->!Double.isFinite(v)||v<0))throw new IllegalArgumentException("Empty/invalid content weights");
        double draw=stream(purpose).nextDouble(sum);T last=null;
        for(var entry:weights.entrySet())if(entry.getValue()>0){last=entry.getKey();draw-=entry.getValue();if(draw<0)return last;}
        return Objects.requireNonNull(last);
    }
}
