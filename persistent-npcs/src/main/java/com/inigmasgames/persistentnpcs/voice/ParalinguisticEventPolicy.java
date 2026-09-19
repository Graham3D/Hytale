package com.inigmasgames.persistentnpcs.voice;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Sparse contextual event selection with per-NPC global and per-event cooldowns. */
public final class ParalinguisticEventPolicy {
    private static final Duration GLOBAL_COOLDOWN = Duration.ofSeconds(45);
    private static final Duration EVENT_COOLDOWN = Duration.ofMinutes(2);
    private static final Duration EXPRESSIVE_EVENT_COOLDOWN = Duration.ofMinutes(4);
    private final Map<UUID, Instant> lastEventByNpc = new ConcurrentHashMap<>();
    private final Map<EventKey, Instant> lastSpecificEvent = new ConcurrentHashMap<>();

    public VocalState select(
            UUID npcId, VocalState state, String context, Instant now) {
        if (npcId == null || state == null) return state;
        Instant timestamp = now == null ? Instant.now() : now;
        ParalinguisticEvent candidate = candidate(state.emotion(), normalize(context));
        if (candidate == null || coolingDown(npcId, candidate, timestamp)) return state;
        lastEventByNpc.put(npcId, timestamp);
        lastSpecificEvent.put(new EventKey(npcId, candidate), timestamp);
        return state.withEvent(candidate);
    }

    private boolean coolingDown(UUID npcId, ParalinguisticEvent event, Instant now) {
        Instant lastAny = lastEventByNpc.get(npcId);
        if (lastAny != null && now.isBefore(lastAny.plus(GLOBAL_COOLDOWN))) return true;
        Instant lastSame = lastSpecificEvent.get(new EventKey(npcId, event));
        Duration cooldown = event == ParalinguisticEvent.LAUGH
                        || event == ParalinguisticEvent.SIGH
                ? EXPRESSIVE_EVENT_COOLDOWN : EVENT_COOLDOWN;
        return lastSame != null && now.isBefore(lastSame.plus(cooldown));
    }

    private static ParalinguisticEvent candidate(VocalEmotion emotion, String text) {
        if (contains(text, "cough", "smoke in the air", "forge smoke", "thick smoke")) {
            return ParalinguisticEvent.COUGH;
        }
        if (contains(text, "be quiet", "keep quiet", "quiet now", "hide", "sneak")) {
            return ParalinguisticEvent.SHUSH;
        }
        if (contains(text, "clear your throat", "speak up", "attention everyone")) {
            return ParalinguisticEvent.CLEAR_THROAT;
        }
        if (contains(text, "aching", "in pain", "badly hurt", "exhausted")) {
            return ParalinguisticEvent.GROAN;
        }
        if (emotion == VocalEmotion.SAD
                && contains(text, "tears", "crying", "grief", "mourning")) {
            return ParalinguisticEvent.SNIFF;
        }
        if ((emotion == VocalEmotion.SAD || emotion == VocalEmotion.UNEASY)
                && contains(text, "regret", "worried", "worry", "concerned", "fear losing")) {
            return ParalinguisticEvent.SIGH;
        }
        if (contains(text, "suddenly", "unexpected", "shocking", "startled")) {
            return ParalinguisticEvent.GASP;
        }
        if (emotion == VocalEmotion.EXCITED
                && contains(text, "hilarious", "best joke", "burst out laughing")) {
            return ParalinguisticEvent.LAUGH;
        }
        if (emotion == VocalEmotion.AMUSED
                && contains(text, "joke", "funny", "dry humor", "pulled it off")) {
            return ParalinguisticEvent.CHUCKLE;
        }
        return null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9' ]", " ").replaceAll("\\s+", " ").strip();
    }

    private static boolean contains(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private record EventKey(UUID npcId, ParalinguisticEvent event) { }
}
