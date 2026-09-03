package com.inigmasgames.persistentnpcs.epistemic;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/** Small E4 typed predicate catalog; unknown legacy predicates remain inspectable. */
public final class BeliefPredicateRegistry {
    private static final Map<String, Definition> VALUES = Map.ofEntries(
            stable("NAME"), stable("SPECIES"), stable("ROLE"), stable("FAMILY_RELATION"),
            stable("TRUSTS"), stable("LIKES"), stable("FEARS"), stable("OWES"),
            stable("PROMISED_TO"), volatileValue("IS_AT", Duration.ofMinutes(5)),
            volatileValue("HOLDS", Duration.ofSeconds(30)), stable("OWNS"),
            volatileValue("EQUIPPED", Duration.ofMinutes(2)),
            volatileValue("VISIBLE", Duration.ofSeconds(15)),
            volatileValue("NEAR", Duration.ofSeconds(30)), stable("CONDITION"),
            stable("HAS_PROPERTY"), stable("WITNESSED"), stable("WAS_TOLD"),
            stable("ACTION_OCCURRED"), stable("TRANSACTION_OCCURRED"),
            stable("BELIEVES_ACTOR_KNOWS"), stable("BELIEVES_ACTOR_WANTS"),
            stable("BELIEVES_ACTOR_FEELS"), stable("BELIEVES_ACTOR_PREFERS"),
            stable("BELIEVES_ACTOR_INTENDS"),
            stable("SECRET_METADATA"),
            volatileValue("CURRENT_TASK", Duration.ofHours(1)), stable("CURRENT_GOAL"),
            volatileValue("INTENDS", Duration.ofHours(1)),
            volatileValue("EMOTIONAL_STATE", Duration.ofMinutes(10)));

    private BeliefPredicateRegistry() { }
    public static String canonical(String value) {
        return value == null ? "" : value.strip().replaceAll("[\\s-]+", "_")
                .replaceAll("_+", "_").toUpperCase(Locale.ROOT);
    }
    public static Definition definition(String predicate) {
        String key = canonical(predicate);
        return VALUES.getOrDefault(key, new Definition(key, Stability.STABLE, null));
    }
    public static boolean registered(String predicate) {
        return VALUES.containsKey(canonical(predicate));
    }
    private static Map.Entry<String, Definition> stable(String key) {
        return Map.entry(key, new Definition(key, Stability.STABLE, null));
    }
    private static Map.Entry<String, Definition> volatileValue(String key, Duration ttl) {
        return Map.entry(key, new Definition(key, Stability.VOLATILE, ttl));
    }
    public enum Stability { STABLE, VOLATILE }
    public record Definition(String predicate, Stability stability, Duration defaultTtl) { }
}
