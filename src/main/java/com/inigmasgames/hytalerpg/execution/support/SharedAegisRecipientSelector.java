package com.inigmasgames.hytalerpg.execution.support;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Deterministic final admission after native membership and line-of-sight checks. */
public final class SharedAegisRecipientSelector {
    public static final double RANGE_METERS=8;
    private SharedAegisRecipientSelector(){}
    public record Candidate<T>(T target,UUID id,double distanceSquared,boolean clearLineOfSight){
        public Candidate {
            Objects.requireNonNull(target);Objects.requireNonNull(id);
            if(!Double.isFinite(distanceSquared)||distanceSquared<0)
                throw new IllegalArgumentException("SHARED_AEGIS_DISTANCE_INVALID");
        }
    }
    public static <T> List<T> ordered(List<Candidate<T>> candidates){
        return candidates.stream()
                .filter(c->c.clearLineOfSight()&&c.distanceSquared()<=RANGE_METERS*RANGE_METERS+1e-9)
                .sorted(Comparator.<Candidate<T>>comparingDouble(Candidate::distanceSquared)
                        .thenComparing(c->c.id().toString()))
                .map(Candidate::target).toList();
    }
}
