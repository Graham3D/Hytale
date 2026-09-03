package com.inigmasgames.persistentnpcs.memory;

import java.util.Locale;
import java.util.Set;

/** Deterministic, testable appraisal. The language model never assigns the final score. */
public final class MemoryImportanceEvaluator {
    private static final Set<String> POSITIVE = Set.of("love", "loved", "joy", "joyful",
            "happy", "proud", "rescued", "saved", "celebrated", "wedding", "birth",
            "friend", "trusted", "forgave", "victory", "homecoming");
    private static final Set<String> NEGATIVE = Set.of("death", "died", "killed", "loss",
            "lost", "grief", "betrayed", "betrayal", "trauma", "terrified", "fear",
            "hurt", "failed", "destroyed", "abandoned", "regret");
    private static final Set<String> RELATIONSHIP = Set.of("family", "mother", "father",
            "daughter", "son", "sister", "brother", "friend", "trusted", "trust",
            "betrayed", "betrayal", "forgave", "promise", "married", "love");
    private static final Set<String> GOAL = Set.of("goal", "quest", "mission", "dream",
            "purpose", "completed", "failed", "achievement", "worked", "crafted",
            "found", "delivered", "rescued");
    private static final Set<String> DANGER = Set.of("danger", "attack", "attacked", "combat",
            "fire", "explosion", "dying", "death", "killed", "monster", "threat",
            "ambush", "wounded", "hurt", "escaped", "survived");
    private static final Set<String> NOVEL = Set.of("first", "never", "discovered",
            "unexpected", "unusual", "unique", "new", "unknown", "rare", "once");
    private static final Set<String> CONSEQUENCE = Set.of("forever", "life", "changed",
            "destroyed", "saved", "lost", "death", "died", "home", "exiled",
            "became", "ended", "began", "permanent");
    private static final Set<String> CORE_VALUE = Set.of("integrity", "honor", "honour",
            "truth", "lie", "lied", "loyal", "loyalty", "betrayal", "betrayed",
            "family", "promise", "duty", "justice", "mercy", "courage");

    public MemoryAppraisal evaluate(MemoryRecord record) {
        String text = normalize(record.summary() + " " + record.npcPerspective()
                + " " + record.source());
        Set<String> terms = new java.util.HashSet<>(java.util.Arrays.asList(text.split(" ")));
        double positive = fraction(terms, POSITIVE);
        double negative = fraction(terms, NEGATIVE);
        double valence = clampSigned(positive - negative);
        double emotional = clamp(Math.max(positive, negative)
                + emphasis(text) + typeEmotionalFloor(record.type()));
        double relationship = clamp(fraction(terms, RELATIONSHIP)
                + (record.type() == MemoryType.RELATIONSHIP ? 0.45 : 0.0)
                + (record.type() == MemoryType.COMMITMENT ? 0.20 : 0.0));
        double goal = clamp(fraction(terms, GOAL)
                + (record.type() == MemoryType.TASK ? 0.35 : 0.0)
                + (record.type() == MemoryType.ACTION_RESULT ? 0.15 : 0.0));
        double danger = clamp(fraction(terms, DANGER));
        double novelty = clamp(fraction(terms, NOVEL)
                + (record.type() == MemoryType.WORLD_EVENT ? 0.20 : 0.0));
        double consequences = clamp(fraction(terms, CONSEQUENCE));
        double coreValue = clamp(fraction(terms, CORE_VALUE));
        double supplied = clamp(record.importance());
        double importance = Math.max(supplied, clamp(supplied * 0.28 + emotional * 0.18
                + relationship * 0.15 + goal * 0.10 + danger * 0.13
                + novelty * 0.06 + consequences * 0.06 + coreValue * 0.04));
        if (extreme(emotional, relationship, danger, consequences, coreValue)) {
            importance = Math.max(importance, 0.88);
        }
        if (record.type() == MemoryType.COMMITMENT) importance = Math.max(importance, 0.62);
        if (record.type() == MemoryType.PLAYER_FACT) importance = Math.max(importance, 0.38);
        return new MemoryAppraisal(importance, tier(importance), valence, emotional,
                relationship, goal, danger, novelty, consequences, coreValue);
    }

    public MemoryDurability tier(double importance) {
        if (importance >= 0.82) return MemoryDurability.LANDMARK;
        if (importance >= 0.60) return MemoryDurability.IMPORTANT;
        if (importance >= 0.30) return MemoryDurability.NORMAL;
        return MemoryDurability.TRANSIENT;
    }

    private static boolean extreme(double emotional, double relationship, double danger,
            double consequences, double coreValue) {
        return emotional >= 0.72 && (relationship >= 0.45 || danger >= 0.55
                || consequences >= 0.45 || coreValue >= 0.45)
                || danger >= 0.72 && consequences >= 0.40;
    }

    private static double typeEmotionalFloor(MemoryType type) {
        return switch (type == null ? MemoryType.CONVERSATION : type) {
            case RELATIONSHIP -> 0.12;
            case COMMITMENT, WORLD_EVENT -> 0.08;
            default -> 0.0;
        };
    }

    private static double fraction(Set<String> terms, Set<String> signals) {
        long hits = signals.stream().filter(terms::contains).count();
        return clamp(hits / 3.0);
    }

    private static double emphasis(String text) {
        return text.contains("extremely") || text.contains("terrifying")
                || text.contains("life changing") ? 0.30 : 0.0;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").strip();
    }

    private static double clamp(double value) { return Math.max(0.0, Math.min(1.0, value)); }

    private static double clampSigned(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }

    public record MemoryAppraisal(double importance, MemoryDurability durability,
            double emotionalValence, double emotionalIntensity,
            double relationshipImpact, double goalImpact, double dangerImpact,
            double novelty, double consequenceImpact, double coreValueRelevance) { }
}
