package com.inigmasgames.persistentnpcs.stats;

public record NpcStatRecord(double current, double baseInitial, double baseMin, double baseMax,
        double lastKnownEffectiveMin, double lastKnownEffectiveMax, String source) {
    public NpcStatRecord {
        for (double value : new double[] {current, baseInitial, baseMin, baseMax,
                lastKnownEffectiveMin, lastKnownEffectiveMax})
            if (!Double.isFinite(value) || Math.abs(value) > Float.MAX_VALUE)
                throw new IllegalArgumentException("Non-finite or out-of-native-range NPC stat");
        if (baseMin > baseInitial || baseInitial > baseMax || lastKnownEffectiveMin > lastKnownEffectiveMax)
            throw new IllegalArgumentException("Inverted NPC stat bounds");
        if (source == null || !source.matches("[A-Z0-9_:.-]{1,200}"))
            throw new IllegalArgumentException("Invalid NPC stat provenance");
    }
    public NpcStatRecord observed(NpcStatSample value) {
        return new NpcStatRecord(value.current(), baseInitial, baseMin, baseMax,
                value.minimum(), value.maximum(), source);
    }
}
