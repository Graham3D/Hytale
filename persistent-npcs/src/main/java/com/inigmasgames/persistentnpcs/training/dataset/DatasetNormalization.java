package com.inigmasgames.persistentnpcs.training.dataset;

import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.training.curation.DistillationExample;
import com.inigmasgames.persistentnpcs.training.registry.CanonicalJson;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Version-pinned D5 normalization. Block-1 registry hashing remains unchanged. */
public final class DatasetNormalization {
    public static final String VERSION = "nfc-lf-trailing-ws-v1";
    public static final String FUZZY_ALGORITHM = "token-bigram-jaccard-v1";
    public static final double FUZZY_DUPLICATE_THRESHOLD = 0.94;
    public static final double FUZZY_REVIEW_THRESHOLD = 0.78;
    private DatasetNormalization() { }

    public static String canonicalText(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value,
                Normalizer.Form.NFC).replace("\r\n", "\n").replace('\r', '\n');
        return Arrays.stream(normalized.split("\\n", -1))
                .map(line -> line.replaceFirst("[\\t ]+$", ""))
                .collect(Collectors.joining("\n"));
    }

    public static String exactInput(DistillationExample example) {
        return example.productionInput().messages().stream()
                .map(message -> canonicalText(message.role()) + "\u0000"
                        + canonicalText(message.content()))
                .collect(Collectors.joining("\u0001"));
    }

    public static String normalizedFingerprint(String value) {
        String normalized = canonicalText(value).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").replaceAll("\\s+", " ").strip();
        return CanonicalJson.sha256(normalized);
    }

    public static String normalizedText(String value) {
        return canonicalText(value).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").replaceAll("\\s+", " ").strip();
    }

    public static String entityNormalizedText(String value, Set<String> entityValues) {
        String normalized = normalizedText(value);
        if (entityValues == null) return normalized;
        var ordered = entityValues.stream().filter(v -> v != null && !v.isBlank())
                .map(DatasetNormalization::normalizedText)
                .sorted(java.util.Comparator.comparingInt(String::length).reversed())
                .toList();
        for (String entity : ordered) {
            normalized = normalized.replaceAll("(?<![\\p{L}\\p{N}])"
                    + java.util.regex.Pattern.quote(entity)
                    + "(?![\\p{L}\\p{N}])", "<entity>");
        }
        return normalized;
    }

    public static String entityNormalizedFingerprint(String value, Set<String> entities) {
        return CanonicalJson.sha256(entityNormalizedText(value, entities));
    }

    public static double fuzzySimilarity(String left, String right) {
        Set<String> a = tokenBigrams(entitySafe(left));
        Set<String> b = tokenBigrams(entitySafe(right));
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        Set<String> union = new HashSet<>(a); union.addAll(b);
        Set<String> intersection = new HashSet<>(a); intersection.retainAll(b);
        return union.isEmpty() ? 0.0 : intersection.size() / (double) union.size();
    }

    public static int approximateTokens(String value) {
        String normalized = normalizedText(value);
        return normalized.isBlank() ? 0 : normalized.split(" ").length;
    }

    private static Set<String> tokenBigrams(String normalized) {
        String[] tokens = normalized.isBlank() ? new String[0] : normalized.split(" ");
        Set<String> values = new TreeSet<>();
        for (String token : tokens) values.add("u:" + token);
        for (int i = 0; i + 1 < tokens.length; i++) {
            values.add("b:" + tokens[i] + "_" + tokens[i + 1]);
        }
        return values;
    }
    private static String entitySafe(String value) { return normalizedText(value); }
}
