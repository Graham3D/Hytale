package com.inigmasgames.persistentnpcs.conversation;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Keeps one response coherent and suppresses immediately repeated utterances/questions. */
public final class DialogueNaturalnessFilter {
    private static final Set<String> QUESTION_STOP_WORDS = Set.of(
            "a", "an", "and", "are", "did", "do", "does", "from", "have", "how",
            "i", "is", "it", "me", "of", "or", "that", "the", "this", "to", "was",
            "were", "what", "when", "where", "which", "who", "why", "you", "your");

    private DialogueNaturalnessFilter() { }

    public static String filterResponse(String dialogue, List<String> recentNpcUtterances) {
        return filterResponse(dialogue, recentNpcUtterances, false);
    }

    /** Material player information is decided before style/repetition filtering. */
    public static String filterResponse(String dialogue, List<String> recentNpcUtterances,
            boolean materialPlayerUpdate) {
        return materialPlayerUpdate ? clean(dialogue)
                : filter(dialogue, recentNpcUtterances, true);
    }

    public static String filterChunk(String dialogue, List<String> priorUtterances) {
        return filter(dialogue, priorUtterances, false);
    }

    public static boolean nearDuplicate(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.isBlank() || b.isBlank()) return false;
        if (a.equals(b)) return true;
        int shorter = Math.min(a.length(), b.length());
        int longer = Math.max(a.length(), b.length());
        if (shorter >= 18 && (a.contains(b) || b.contains(a))
                && (double) shorter / longer >= 0.72) {
            return true;
        }
        Set<String> aTerms = terms(a, isQuestion(left));
        Set<String> bTerms = terms(b, isQuestion(right));
        if (aTerms.isEmpty() || bTerms.isEmpty()) return false;
        Set<String> intersection = new HashSet<>(aTerms);
        intersection.retainAll(bTerms);
        Set<String> union = new HashSet<>(aTerms);
        union.addAll(bTerms);
        double similarity = (double) intersection.size() / union.size();
        double threshold = isQuestion(left) && isQuestion(right) ? 0.58 : 0.74;
        return similarity >= threshold && intersection.size() >= 2;
    }

    private static String filter(
            String dialogue, List<String> recentNpcUtterances, boolean fallbackAllowed) {
        String paragraph = clean(dialogue);
        if (paragraph.isBlank()) return "";
        List<String> recent = recentNpcUtterances == null ? List.of()
                : recentNpcUtterances.stream().filter(value -> value != null && !value.isBlank())
                        .toList();
        boolean recentQuestion = !recent.isEmpty()
                && sentences(recent.get(recent.size() - 1)).stream()
                        .anyMatch(DialogueNaturalnessFilter::isQuestion);
        boolean acceptedQuestion = false;
        List<String> accepted = new ArrayList<>();
        for (String sentence : sentences(paragraph)) {
            if (sentence.isBlank()) continue;
            boolean question = isQuestion(sentence);
            if (question && (acceptedQuestion || recentQuestion)) continue;
            boolean duplicate = java.util.stream.Stream.concat(
                            recent.stream().flatMap(value -> sentences(value).stream()),
                            accepted.stream())
                    .anyMatch(previous -> nearDuplicate(sentence, previous));
            if (duplicate) continue;
            accepted.add(sentence);
            acceptedQuestion |= question;
        }
        String result = String.join(" ", accepted).replaceAll("\\s+", " ").strip();
        if (!result.isBlank() || !fallbackAllowed) return result;
        // Never synthesize generic assistant-style dialogue. The caller either preserves the
        // semantically material response or uses the selected intent's grounded fallback.
        return "";
    }

    private static List<String> sentences(String value) {
        String text = clean(value);
        if (text.isBlank()) return List.of();
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.ROOT);
        iterator.setText(text);
        List<String> sentences = new ArrayList<>();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE;
                start = end, end = iterator.next()) {
            String sentence = text.substring(start, end).strip();
            if (!sentence.isBlank()) sentences.add(sentence);
        }
        return sentences.isEmpty() ? List.of(text) : sentences;
    }

    private static Set<String> terms(String value, boolean question) {
        Set<String> result = new HashSet<>();
        for (String term : normalize(value).split(" ")) {
            if (term.length() < 3 || question && QUESTION_STOP_WORDS.contains(term)) continue;
            result.add(term.endsWith("s") && term.length() > 4
                    ? term.substring(0, term.length() - 1) : term);
        }
        return result;
    }

    private static boolean isQuestion(String value) {
        return value != null && value.strip().endsWith("?");
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.replace('\r', ' ').replace('\n', ' ')
                .replaceAll("\\s+", " ").strip();
    }

    private static String normalize(String value) {
        return clean(value).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9' ]", " ").replaceAll("\\s+", " ").strip();
    }
}
