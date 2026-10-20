package com.inigmasgames.hytalerpg.vfx;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.Optional;

/** Per-carrier bounded visual sampling. No hit tests, damage or catch-up emission. */
public final class ProjectileReadability {
    public static final double CAST_SECONDS=.10, TRAIL_SECONDS=.20, IMPACT_SECONDS=.12, EXPIRY_SECONDS=.15;
    public static final long SAMPLE_NANOS=100_000_000L; // 10 Hz, below the master's 30 Hz maximum.
    public record Segment(Vec3 from,Vec3 to){}
    private Vec3 previous;
    private long next;
    private boolean failed;
    public ProjectileReadability(Vec3 origin,long now){previous=origin;next=now+SAMPLE_NANOS;}
    public Optional<Segment> sample(Vec3 position,long now){
        if(now<next)return Optional.empty();
        var result=new Segment(previous,position);previous=position;next=now+SAMPLE_NANOS;
        return result.from().distanceSquared(result.to())>1e-12?Optional.of(result):Optional.empty();
    }
    public boolean firstFailure(){if(failed)return false;failed=true;return true;}
    /** Coalesces only cosmetics. Combat hit/proc ledgers and resource authority never read this state. */
    public static final class Group {
        private final java.util.Set<String> casts=new java.util.HashSet<>();
        private final java.util.Map<String,Long> impacts=new java.util.HashMap<>();
        public synchronized boolean cast(String release){return casts.size()<16&&casts.add(release);}
        public synchronized boolean impact(String victim,long now){
            impacts.values().removeIf(until->until<=now);
            if(impacts.containsKey(victim)||impacts.size()>=64)return false;
            impacts.put(victim,now+50_000_000);return true;
        }
    }
}
