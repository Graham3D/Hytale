package com.inigmasgames.persistentnpcs.voice;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Chatterbox-only sanitation. Never use this value for chat display or memory. */
public final class TtsTextNormalizer {
    private static final List<Contraction> CONTRACTIONS = List.of(
            new Contraction("you're", "you are"),
            new Contraction("what's", "what is"),
            new Contraction("I've", "I have"),
            new Contraction("can't", "cannot"),
            new Contraction("won't", "will not"),
            new Contraction("I'm", "I am"),
            new Contraction("we're", "we are"),
            new Contraction("they're", "they are"),
            new Contraction("that's", "that is"),
            new Contraction("it's", "it is"),
            new Contraction("don't", "do not"),
            new Contraction("doesn't", "does not"),
            new Contraction("didn't", "did not"),
            new Contraction("isn't", "is not"),
            new Contraction("aren't", "are not"));

    private TtsTextNormalizer() { }

    public static String normalize(String displayText) {
        // R027 keeps lexical wording immutable. Only punctuation/formatting safe for
        // pronunciation may differ from the displayed committed chunk.
        return normalize(displayText, false);
    }

    /** Adds server-selected performance metadata without modifying the lexical dialogue. */
    public static String performanceText(
            String displayText, VocalState state, boolean includeEvent) {
        String lexicalText = normalize(displayText);
        if (!includeEvent || state == null || state.paralinguisticEvent().isEmpty()) {
            return lexicalText;
        }
        return state.paralinguisticEvent().orElseThrow().tag()
                + (lexicalText.isBlank() ? "" : " " + lexicalText);
    }

    static String normalize(String displayText, boolean expandContractions) {
        String text = displayText == null ? "" : displayText;
        text = text.replace('\u2018', '\'').replace('\u2019', '\'')
                .replace('\u201C', '"').replace('\u201D', '"')
                .replace('\u00A0', ' ');
        // Chatterbox Turbo is more stable when a dash between clauses becomes a pause.
        text = text.replaceAll("\\s*[\\u2013\\u2014]\\s*", ". ");
        // Remove common model formatting without stripping ordinary spoken punctuation.
        text = text.replaceAll("(?s)```.*?```", " ")
                .replaceAll("[`*_~#]+", " ")
                .replaceAll("\\[([^]\\r\\n]+)]\\((?:https?://)?[^)]+\\)", "$1");
        if (expandContractions) {
            for (Contraction contraction : CONTRACTIONS) {
                text = expand(text, contraction.shortForm(), contraction.expanded());
            }
        }
        text = text.replaceAll("\\s+([,.!?;:])", "$1")
                .replaceAll("([.!?])(?:\\s*[.!?])+", "$1")
                .replaceAll("\\s+", " ").strip();
        return sentenceCase(text);
    }

    private static String expand(String value, String shortForm, String expanded) {
        Pattern pattern = Pattern.compile("(?i)\\b" + Pattern.quote(shortForm) + "\\b");
        Matcher matcher = pattern.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = expanded;
            if (!matcher.group().isEmpty() && Character.isUpperCase(matcher.group().charAt(0))) {
                replacement = Character.toUpperCase(replacement.charAt(0)) + replacement.substring(1);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String sentenceCase(String value) {
        Matcher matcher = Pattern.compile("(^|[.!?]\\s+)([a-z])").matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(
                    matcher.group(1) + matcher.group(2).toUpperCase()));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private record Contraction(String shortForm, String expanded) { }
}
