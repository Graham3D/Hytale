package com.inigmasgames.hytalerpg.domain;

import java.util.UUID;

/** Correlation identities reserved now for the later combat runtime. */
public final class SkillExecutionIds {
    private SkillExecutionIds() {}
    public record RootCastId(UUID value) { public static RootCastId create() { return new RootCastId(UUID.randomUUID()); } }
    public record SkillInstanceId(UUID value) { public static SkillInstanceId create() { return new SkillInstanceId(UUID.randomUUID()); } }
    public record Identity(RootCastId rootCastId, SkillInstanceId skillInstanceId, int generation) {
        public Identity {
            if (generation < 0) throw new IllegalArgumentException("generation must be nonnegative");
        }
        public static Identity root() { return new Identity(RootCastId.create(), SkillInstanceId.create(), 0); }
        public Identity child() { return new Identity(rootCastId, SkillInstanceId.create(), generation + 1); }
    }
}
