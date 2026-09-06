package com.inigmasgames.hytalerpg.combat.damage;

import java.util.List;

/** Increased/Reduced share one additive bucket; More/Less remain separate multiplicative buckets. */
public record ModifierBuckets(List<Double> increased, List<Double> reduced, List<Double> more, List<Double> less) {
    public static final ModifierBuckets NONE = new ModifierBuckets(List.of(), List.of(), List.of(), List.of());
    public ModifierBuckets {
        increased = validated(increased, "increased"); reduced = validated(reduced, "reduced");
        more = validated(more, "more"); less = validated(less, "less");
        if (less.stream().anyMatch(value -> value > 1.0)) throw new IllegalArgumentException("Less values cannot exceed 1.0");
    }
    public double factor() {
        double additive = Math.max(0.0, 1.0 + sum(increased) - sum(reduced));
        double result = additive;
        for (double value : more) result *= value;
        for (double value : less) result *= 1.0 - value;
        return result;
    }
    private static List<Double> validated(List<Double> values, String name) {
        List<Double> copy = List.copyOf(values == null ? List.of() : values);
        if (copy.stream().anyMatch(value -> value == null || !Double.isFinite(value) || value < 0.0))
            throw new IllegalArgumentException(name + " values must be finite and non-negative");
        return copy;
    }
    private static double sum(List<Double> values) { return values.stream().mapToDouble(Double::doubleValue).sum(); }
}
