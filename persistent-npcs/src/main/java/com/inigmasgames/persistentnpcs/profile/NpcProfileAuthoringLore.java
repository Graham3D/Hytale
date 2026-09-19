package com.inigmasgames.persistentnpcs.profile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Builds a small approved-canon packet for creative Profile Editor generation. */
public final class NpcProfileAuthoringLore {
    static final int MAX_ENTRIES = 4;
    static final int MAX_PACKET_CHARACTERS = 2_400;

    public record LoreEntry(UUID stableNpcId, String name, String relevance,
            String authoredCanon) { }

    public record WorldLorePacket(String targetLocation, List<LoreEntry> entries) {
        public WorldLorePacket {
            targetLocation = clean(targetLocation, 200);
            entries = List.copyOf(entries == null ? List.of() : entries);
        }

        public String promptText() {
            StringBuilder text = new StringBuilder();
            if (!targetLocation.isBlank()) {
                text.append("Authored location: ").append(targetLocation).append('\n');
            }
            for (LoreEntry entry : entries) {
                text.append("- ").append(entry.name()).append(" [")
                        .append(entry.stableNpcId()).append("] relevance=")
                        .append(entry.relevance()).append(": ")
                        .append(entry.authoredCanon()).append('\n');
                if (text.length() >= MAX_PACKET_CHARACTERS) break;
            }
            return clean(text.toString(), MAX_PACKET_CHARACTERS);
        }
    }

    private record Candidate(NpcProfile profile, int score, String relevance) { }

    private NpcProfileAuthoringLore() { }

    public static Optional<WorldLorePacket> relevantTo(NpcProfileDraft draft,
            Collection<NpcProfile> profiles) {
        if (draft == null || profiles == null || profiles.isEmpty()) return Optional.empty();
        String home = normalized(draft.value(NpcProfileDraft.Field.HOME));
        Set<String> roleTokens = tokens(draft.value(NpcProfileDraft.Field.ROLE));
        Set<UUID> relationshipTargets = relationshipTargets(draft.stableNpcId(), profiles);
        List<Candidate> candidates = new ArrayList<>();
        for (NpcProfile profile : profiles) {
            if (profile == null || profile.stableId().equals(draft.stableNpcId())) continue;
            int score = 0;
            List<String> reasons = new ArrayList<>();
            if (!home.isBlank() && home.equals(normalized(profile.home()))) {
                score += 8;
                reasons.add("same authored location");
            }
            Set<String> sharedRole = new LinkedHashSet<>(roleTokens);
            sharedRole.retainAll(tokens(profile.role()));
            if (!sharedRole.isEmpty()) {
                score += Math.min(4, sharedRole.size() * 2);
                reasons.add("shared role context");
            }
            if (relationshipTargets.contains(profile.stableId())) {
                score += 12;
                reasons.add("authored relationship");
            }
            if (score > 0) candidates.add(new Candidate(profile, score,
                    String.join(", ", reasons)));
        }
        candidates.sort(Comparator.comparingInt(Candidate::score).reversed()
                .thenComparing(candidate -> candidate.profile().name(),
                        String.CASE_INSENSITIVE_ORDER));
        List<LoreEntry> entries = candidates.stream().limit(MAX_ENTRIES)
                .map(candidate -> new LoreEntry(candidate.profile().stableId(),
                        candidate.profile().name(), candidate.relevance(),
                        authoredExcerpt(candidate.profile())))
                .toList();
        if (entries.isEmpty()) return Optional.empty();
        return Optional.of(new WorldLorePacket(draft.value(NpcProfileDraft.Field.HOME), entries));
    }

    private static Set<UUID> relationshipTargets(UUID target, Collection<NpcProfile> profiles) {
        Set<UUID> ids = new LinkedHashSet<>();
        profiles.stream().filter(profile -> profile.stableId().equals(target)).findFirst()
                .ifPresent(profile -> profile.relationships().forEach(relationship -> {
                    try {
                        if (relationship.targetId() != null
                                && !relationship.targetId().isBlank()) {
                            ids.add(UUID.fromString(relationship.targetId()));
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Invalid legacy relationship IDs are not approved lore references.
                    }
                    if (relationship.targetName() != null
                            && !relationship.targetName().isBlank()) {
                        profiles.stream().filter(candidate -> candidate.name().equalsIgnoreCase(
                                relationship.targetName())).findFirst()
                                .ifPresent(candidate -> ids.add(candidate.stableId()));
                    }
                }));
        return Set.copyOf(ids);
    }

    private static String authoredExcerpt(NpcProfile profile) {
        StringBuilder text = new StringBuilder("role=").append(clean(profile.role(), 160));
        append(text, "home", profile.home(), 180);
        append(text, "workplace", profile.workplace(), 180);
        append(text, "summary", profile.summary(), 260);
        append(text, "background", profile.biography(), 360);
        return clean(text.toString(), 850);
    }

    private static void append(StringBuilder text, String label, String value, int maximum) {
        String clean = clean(value, maximum);
        if (!clean.isBlank()) text.append("; ").append(label).append('=').append(clean);
    }

    private static Set<String> tokens(String value) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : normalized(value).split("[^a-z0-9]+")) {
            if (token.length() >= 4) tokens.add(token);
        }
        return Set.copyOf(tokens);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").strip()
                .toLowerCase(Locale.ROOT);
    }

    private static String clean(String value, int maximum) {
        String clean = value == null ? "" : value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "")
                .replaceAll("[ \\t]+", " ").strip();
        return clean.length() <= maximum ? clean : clean.substring(0, maximum).strip();
    }
}
