package com.inigmasgames.persistentnpcs.voice;

import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.ConversationSessionManager;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Bounded deterministic repair for STT output where the intended token is constrained by
 * authored identities or the active conversation. This deliberately is not a general-purpose
 * spell checker: ambiguous ordinary words remain exactly as Moonshine produced them.
 */
public final class SttSemanticCorrector {
    private final NpcProfileRegistry profiles;
    private final ConversationSessionManager sessions;

    public SttSemanticCorrector(NpcProfileRegistry profiles,
            ConversationSessionManager sessions) {
        this.profiles = profiles;
        this.sessions = sessions;
    }

    public Correction correct(UUID playerId, String transcript) {
        String raw = clean(transcript);
        if (raw.isBlank()) return Correction.unchanged(raw, "EMPTY_TRANSCRIPT");
        List<NameCandidate> candidates = nameCandidates(raw);
        if (!candidates.isEmpty()) {
            candidates.sort(Comparator.comparingInt(NameCandidate::distance)
                    .thenComparing(NameCandidate::name));
            NameCandidate best = candidates.getFirst();
            boolean unique = candidates.size() == 1
                    || candidates.get(1).distance() > best.distance();
            if (unique) {
                String corrected = replaceTokens(raw, best.start(), best.tokenCount(), best.name());
                double confidence = best.distance() == 0 ? 1.0
                        : best.distance() == 1 ? .97 : best.distance() == 2 ? .93 : .90;
                return new Correction(raw, corrected, true, confidence,
                        "KNOWN_NPC_NAME_NEAR_MATCH:" + best.observed() + "->" + best.name());
            }
        }
        Correction contextual = correctConstrainedRecentPhrase(playerId, raw);
        return contextual == null ? Correction.unchanged(raw, "NO_UNIQUE_CONSTRAINED_MATCH")
                : contextual;
    }

    private List<NameCandidate> nameCandidates(String raw) {
        String[] tokens = normalizedTokens(raw);
        ArrayList<NameCandidate> found = new ArrayList<>();
        for (NpcProfile profile : profiles.profiles()) {
            String name = clean(profile.name());
            String[] target = normalizedTokens(name);
            if (target.length == 0 || tokens.length < target.length) continue;
            // A proper name is eligible only in a syntactically constrained address slot:
            // leading vocative, "hey Name", or recipient/subject cue. A later object mention
            // must not become a second response owner.
            Set<Integer> starts = new LinkedHashSet<>();
            starts.add(0);
            if (tokens.length > target.length && tokens[0].equals("hey")) starts.add(1);
            for (int i = 1; i + target.length <= tokens.length; i++) {
                String prior = tokens[i - 1];
                if (Set.of("tell", "ask", "find", "about", "to").contains(prior)) starts.add(i);
            }
            for (int start : starts) {
                boolean exactBaseSpan = start + target.length <= tokens.length
                        && String.join("", java.util.Arrays.copyOfRange(
                                tokens, start, start + target.length))
                                .equals(name.toLowerCase(Locale.ROOT).replace(" ", ""));
                int maximumSpan = target.length == 1 ? 2 : target.length;
                for (int span = target.length; span <= maximumSpan; span++) {
                    // Once an authored name already matches exactly, never consume the next
                    // ordinary word in an attempt to manufacture a longer phonetic match.
                    if (exactBaseSpan && span > target.length) continue;
                    if (start + span > tokens.length) continue;
                    String observed = String.join(" ", java.util.Arrays.copyOfRange(
                            tokens, start, start + span));
                    String observedPhonetic = observed.replace(" ", "");
                    String targetPhonetic = name.toLowerCase(Locale.ROOT).replace(" ", "");
                    int distance = PlayerUtteranceAudienceService.levenshtein(
                            observedPhonetic, targetPhonetic);
                    int maximum = name.length() >= 7 ? 3 : 2;
                    if (distance <= maximum && !observedPhonetic.equals(targetPhonetic)) {
                        found.add(new NameCandidate(profile.name(), observed, start,
                                span, distance));
                    }
                }
            }
        }
        return found;
    }

    private Correction correctConstrainedRecentPhrase(UUID playerId, String raw) {
        if (sessions == null || playerId == null) return null;
        ConversationSession session = sessions.active(playerId, Instant.now()).orElse(null);
        if (session == null) return null;
        String[] observed = normalizedTokens(raw);
        if (observed.length < 2) return null;
        ArrayList<PhraseCandidate> candidates = new ArrayList<>();
        for (ConversationSession.ConversationTurn turn : session.recentTurns(4)) {
            collectPhraseCandidates(observed, normalizedTokens(turn.playerMessage()), candidates);
            collectPhraseCandidates(observed, normalizedTokens(turn.npcReply()), candidates);
        }
        candidates.sort(Comparator.comparingInt(PhraseCandidate::distance));
        if (candidates.isEmpty() || candidates.size() > 1
                && candidates.get(1).distance() == candidates.getFirst().distance()) return null;
        PhraseCandidate best = candidates.getFirst();
        String corrected = replaceTokens(raw, best.start(), 2, best.replacement());
        return new Correction(raw, corrected, true, .91,
                "ACTIVE_CONTEXT_TWO_TOKEN_NEAR_MATCH:" + best.observed()
                        + "->" + best.replacement());
    }

    private static void collectPhraseCandidates(String[] observed, String[] context,
            List<PhraseCandidate> out) {
        for (int i = 0; i + 1 < observed.length; i++) {
            for (int j = 0; j + 1 < context.length; j++) {
                // Context repair is deliberately one-way: a distinctive first token anchors
                // the phrase and only its following token may be repaired. Short function-word
                // anchors caused broad rewrites such as "what is" -> "that is" and
                // "use a" -> "just a".
                if (!observed[i].equals(context[j]) || observed[i].length() < 4
                        || CONTEXT_STOPWORDS.contains(observed[i])) continue;
                String changed = observed[i + 1];
                String target = context[j + 1];
                if (changed.length() < 3 || target.length() < 3) continue;
                int distance = PlayerUtteranceAudienceService.levenshtein(changed, target);
                if (distance <= 2) out.add(new PhraseCandidate(i,
                        observed[i] + " " + observed[i + 1],
                        context[j] + " " + context[j + 1], distance));
            }
        }
    }

    private static final Set<String> CONTEXT_STOPWORDS = Set.of(
            "that", "this", "what", "when", "where", "which", "with", "from",
            "have", "your", "their", "there", "were", "will", "would", "could");

    private static String replaceTokens(String raw, int start, int count, String replacement) {
        String[] words = raw.split("\\s+");
        if (start < 0 || start + count > words.length) return raw;
        ArrayList<String> result = new ArrayList<>();
        for (int i = 0; i < start; i++) result.add(words[i]);
        String suffix = words[start + count - 1].replaceAll(".*?([^\\p{L}\\p{N}']+)$", "$1");
        if (suffix.equals(words[start + count - 1])) suffix = "";
        result.add(replacement + suffix);
        for (int i = start + count; i < words.length; i++) result.add(words[i]);
        return String.join(" ", result).replaceAll("\\s+([,.!?])", "$1").strip();
    }

    private static String[] normalizedTokens(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}' ]", " ").replaceAll("\\s+", " ").strip();
        return normalized.isBlank() ? new String[0] : normalized.split(" ");
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").strip();
    }

    public record Correction(String rawTranscript, String correctedTranscript,
            boolean applied, double confidence, String reason) {
        public Correction {
            rawTranscript = clean(rawTranscript);
            correctedTranscript = clean(correctedTranscript);
            confidence = Math.max(0, Math.min(1, confidence));
            reason = reason == null ? "" : reason;
        }
        static Correction unchanged(String raw, String reason) {
            return new Correction(raw, raw, false, 1.0, reason);
        }
    }

    private record NameCandidate(String name, String observed, int start,
            int tokenCount, int distance) { }
    private record PhraseCandidate(int start, String observed, String replacement,
            int distance) { }
}
