package com.inigmasgames.persistentnpcs.epistemic;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded, deterministic E5 alias/referent/time expansion. No provider or persistence I/O. */
public record E5QueryExpansion(List<String> terms, String temporalMode,
        Instant validFrom, Instant validUntil, boolean currentOnly, boolean historical,
        String resolvedReferent, String currentTopic, List<String> diagnostics) {
    private static final Pattern DAYS_AGO = Pattern.compile("(?i)\\b(\\d{1,3})\\s+days? ago\\b");

    public E5QueryExpansion {
        terms = List.copyOf(terms == null ? List.of() : terms);
        temporalMode = clean(temporalMode);
        resolvedReferent = clean(resolvedReferent);
        currentTopic = clean(currentTopic);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }

    public static E5QueryExpansion expand(String raw, DialogueFrame frame,
            ConversationWorkspace.Snapshot workspace, Instant now) {
        Instant at = now == null ? Instant.now() : now;
        String query = raw == null ? "" : raw.replaceAll("\\s+", " ").strip();
        String lower = query.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String value : query.split("[^\\p{L}\\p{N}]+")) {
            String term = alias(value.toLowerCase(Locale.ROOT));
            if (term.length() >= 3 && !STOP.contains(term)) terms.add(term);
        }
        String referent = frame == null ? "" : frame.objectKey();
        String topic = workspace == null ? "" : workspace.currentTopic();
        if (referent.isBlank() && workspace != null && lower.matches(".*\\b(it|that|this)\\b.*")) {
            referent = workspace.currentObject();
        }
        if (!referent.isBlank()) {
            for (String value : referent.split("[^\\p{L}\\p{N}]+")) {
                String term = alias(value.toLowerCase(Locale.ROOT));
                if (term.length() >= 3) terms.add(term);
            }
        }
        if (!topic.isBlank() && !topic.equals("PAST_EVENT")) {
            for (String value : topic.split("[^\\p{L}\\p{N}]+")) {
                String term = alias(value.toLowerCase(Locale.ROOT));
                if (term.length() >= 3) terms.add(term);
            }
        }

        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.ofInstant(at, zone);
        Instant from = null, until = null;
        boolean current = lower.matches(".*\\b(current|currently|now|latest|still)\\b.*");
        boolean historical = lower.matches(".*\\b(earlier|before|previous|previously|old|last time|was|were)\\b.*");
        String mode = current ? "CURRENT" : historical ? "HISTORICAL" : "UNBOUNDED";
        if (lower.contains("last time")) mode = "LAST_TIME";
        if (lower.contains("today")) {
            from = today.atStartOfDay(zone).toInstant();
            until = today.plusDays(1).atStartOfDay(zone).toInstant();
            mode = "TODAY"; historical = true;
        } else if (lower.contains("yesterday")) {
            from = today.minusDays(1).atStartOfDay(zone).toInstant();
            until = today.atStartOfDay(zone).toInstant();
            mode = "YESTERDAY"; historical = true;
        } else {
            Matcher days = DAYS_AGO.matcher(lower);
            if (days.find()) {
                long count = Math.min(365, Long.parseLong(days.group(1)));
                LocalDate day = today.minusDays(count);
                from = day.atStartOfDay(zone).toInstant();
                until = day.plusDays(1).atStartOfDay(zone).toInstant();
                mode = count + "_DAYS_AGO"; historical = true;
            }
        }
        ArrayList<String> diagnostics = new ArrayList<>();
        diagnostics.add("TEMPORAL=" + mode);
        if (!referent.isBlank()) diagnostics.add("REFERENT=" + referent);
        if (!topic.isBlank()) diagnostics.add("CURRENT_TOPIC=" + topic);
        diagnostics.add("EXPANDED_TERMS=" + terms);
        return new E5QueryExpansion(List.copyOf(terms), mode, from, until, current,
                historical, referent, topic, diagnostics);
    }

    public boolean matches(Instant candidate) {
        if (candidate == null) return validFrom == null && validUntil == null;
        return (validFrom == null || !candidate.isBefore(validFrom))
                && (validUntil == null || candidate.isBefore(validUntil));
    }

    public boolean timeConstraintIsLoose() {
        return validFrom == null && validUntil == null;
    }

    public double temporalScore(Instant candidate, Instant now) {
        if (validFrom != null || validUntil != null) return matches(candidate) ? 1.0 : 0.0;
        if (candidate == null) return .35;
        double ageDays = Math.max(0, ChronoUnit.SECONDS.between(candidate,
                now == null ? Instant.now() : now) / 86_400d);
        if (temporalMode.equals("LAST_TIME")) return Math.exp(-ageDays / 30d);
        if (currentOnly) return Math.exp(-ageDays * 2.5);
        if (historical) return Math.min(1, .55 + Math.min(30, ageDays) / 60d);
        return Math.max(.25, Math.exp(-ageDays / 90d));
    }

    private static String alias(String value) {
        return switch (value) {
            case "hid", "hide", "hidden", "stashed", "stash", "buried", "bury" -> "conceal";
            case "put", "placed", "left", "stored" -> "place";
            case "holding", "held", "holds" -> "hold";
            case "remember", "remembered", "recall", "recalled" -> "memory";
            case "rock", "stone" -> "rock";
            default -> value;
        };
    }

    private static String clean(String value) { return value == null ? "" : value.strip(); }
    private static final Set<String> STOP = Set.of("about", "again", "and", "did", "does", "have",
            "here", "what", "when", "where", "which", "with", "would", "your", "you",
            "said", "tell", "told", "that", "this", "there", "today", "yesterday",
            "earlier", "before", "current", "currently", "latest", "time");
}
