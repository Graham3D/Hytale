package com.inigmasgames.persistentnpcs.cognition;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Small fail-closed lexical contract for factual claim classes that must never be
 * authorized by an unrelated evidence namespace.
 */
public final class NpcGroundingClaimValidator {
    private static final Pattern CURRENT_WORLD = Pattern.compile(
            "(?i)\\b(?:i can see|i see|there(?:'s| is| are)|right over there|nearby)\\b");
    private static final Pattern WITNESSED_WORLD_EVENT = Pattern.compile(
            "(?i)\\b(?:i|we)\\s+(?:just\\s+|recently\\s+)?"
                    + "(?:saw|spotted|watched|witnessed|came across|ran into)\\b");
    private static final Pattern CONCRETE_WORLD_REFERENCE = Pattern.compile(
            "(?i)\\b(?:(?:near|at|inside|outside|through|by)\\s+(?:the|this|that|an?)\\s+"
                    + "(?:old\\s+)?(?:mill|forge|forest|woods|river|lake|mountain|village|"
                    + "town|house|shop|cave|ruins?|tower|castle|temple|road|bridge|gears?)|"
                    + "(?:that|the|an?)\\s+(?:fox|wolf|bear|deer|dragon|creature|monster|bandit))\\b");
    private static final Pattern SHARED_HISTORY = Pattern.compile(
            "(?i)\\b(?:remember when we|last time we|when we (?:first |last )?|we used to)\\b");
    private static final Pattern COMPLETED_ACTION = Pattern.compile(
            "(?i)\\b(?:i|we)(?:'ve| have)?\\s+(?:already\\s+)?"
                    + "(?:finished|completed|delivered|placed|built|crafted|forged|repaired|fixed)\\b");
    private static final Pattern AUTOBIOGRAPHICAL_RELATIONSHIP = Pattern.compile(
            "(?i)\\b(?:i(?:'ve| have)?\\s+got|i\\s+have|my)\\s+"
                    + "(?:(?:a|an|the|some|any|several|many)\\s+)?(?:whole\\s+)?"
                    + "(?:friends?|family|parents?|siblings?|brothers?|sisters?|children?|"
                    + "crew|companions?|partners?|spouse|husband|wife|pets?|critters?)\\b");
    private static final Pattern INTERPERSONAL_KINSHIP = Pattern.compile(
            "(?i)\\b(?:(?:i am|i'm)(?:\\s+[\\p{L}'-]+,)?\\s+your|"
                    + "you are my|you're my)\\s+"
                    + "(?:grand(?:father|mother|parent|son|daughter)|grandpa|grandma|father|mother|parent|"
                    + "son|daughter|brother|sister|uncle|aunt|cousin|spouse|husband|wife)\\b");
    private static final Pattern AUTOBIOGRAPHICAL_POSSESSION = Pattern.compile(
            "(?i)\\b(?:i(?:'ve| have)?\\s+got|i\\s+(?:have|own|carry|keep))\\s+"
                    + "(?:(?:a|an|the|some|my)\\s+)?"
                    + "(?:house|home|shop|forge|weapon|sword|dagger|book|horse|cart|"
                    + "fox|wolf|pet|coin|coins|gold|tool|tools|gear|gears)\\b");
    private static final Pattern THIRD_PARTY_POSSESSION_OR_STATE = Pattern.compile(
            "(?i)\\b(?:your|his|her|their|[\\p{L}'-]+['’]s)\\s+"
                    + "(?:fox|wolf|pet|weapon|sword|dagger|gear|gears|house|shop|forge)\\b");
    private static final Pattern AUTOBIOGRAPHICAL_PAST_EVENT = Pattern.compile(
            "(?i)\\b(?:i|we)\\s+(?:once\\s+|formerly\\s+|previously\\s+|last\\s+"
                    + "(?:night|week|year)\\s+)?(?:met|visited|went|lived|worked|found|lost|"
                    + "fought|escaped|learned|grew up|used to)\\b");
    private static final Pattern HYPOTHETICAL = Pattern.compile(
            "(?i)\\b(?:if|unless|suppose|imagine|hypothetically|would|could|might|maybe)\\b");
    private static final Pattern SUBJECTIVE = Pattern.compile(
            "(?i)\\b(?:i\\s+(?:like|love|dislike|hate|want|wish|hope|prefer|feel|think|believe)|"
                    + "in my opinion|to me|i'd like|i would like)\\b");

    public List<ClaimAssessment> validate(String spokenText, List<String> evidenceRefs) {
        String text = spokenText == null ? "" : spokenText.strip();
        List<String> refs = List.copyOf(evidenceRefs == null ? List.of() : evidenceRefs);
        Set<GroundingEvidenceType> types = new LinkedHashSet<>();
        refs.stream().map(GroundingEvidenceType::fromReference).forEach(types::add);
        java.util.ArrayList<ClaimAssessment> results = new java.util.ArrayList<>();

        if (CURRENT_WORLD.matcher(text).find()) {
            assess(results, "CURRENT_WORLD", refs, types,
                    Set.of(GroundingEvidenceType.SEMANTIC_WORLD));
        }
        boolean witnessed = WITNESSED_WORLD_EVENT.matcher(text).find();
        boolean hypothetical = HYPOTHETICAL.matcher(text).find();
        if (witnessed || CONCRETE_WORLD_REFERENCE.matcher(text).find() && !hypothetical) {
            assess(results, "WITNESSED_OR_CONCRETE_WORLD_EVENT", refs, types,
                    Set.of(GroundingEvidenceType.MEMORY_OR_BELIEF,
                            GroundingEvidenceType.SEMANTIC_WORLD));
        }
        if (SHARED_HISTORY.matcher(text).find()) {
            assess(results, "SHARED_HISTORY", refs, types,
                    Set.of(GroundingEvidenceType.MEMORY_OR_BELIEF,
                            GroundingEvidenceType.RECENT_DELIVERED_CONVERSATION));
        }
        if (COMPLETED_ACTION.matcher(text).find()) {
            assess(results, "COMPLETED_ACTION", refs, types,
                    Set.of(GroundingEvidenceType.ACTION_RESULT));
        }
        if (AUTOBIOGRAPHICAL_RELATIONSHIP.matcher(text).find()) {
            boolean supported = refs.stream().anyMatch(value -> value != null
                            && (value.startsWith("RELATIONSHIP:stableTarget=")
                                    || value.startsWith("PROFILE:relationships")))
                    || refs.stream().filter(value -> value != null
                            && value.startsWith("RELATIONSHIP:")).count() >= 2;
            assessExact(results, "AUTOBIOGRAPHICAL_RELATIONSHIP", refs, types, supported,
                    "requires an authored third-party relationship reference");
        }
        if (INTERPERSONAL_KINSHIP.matcher(text).find()) {
            boolean supported = refs.stream().anyMatch(value -> value != null
                    && (value.startsWith("RELATIONSHIP:stableTarget=")
                            || value.startsWith("PROFILE:relationships")));
            assessExact(results, "INTERPERSONAL_KINSHIP", refs, types, supported,
                    "requires an authored relationship whose target and type are explicit");
        }
        if (AUTOBIOGRAPHICAL_POSSESSION.matcher(text).find()) {
            boolean supported = types.contains(GroundingEvidenceType.MEMORY_OR_BELIEF)
                    || refs.stream().anyMatch(value -> value != null
                            && value.startsWith("PROFILE:possessions"));
            assessExact(results, "AUTOBIOGRAPHICAL_POSSESSION", refs, types, supported,
                    "requires sourced memory/belief or an authored possession");
        }
        if (THIRD_PARTY_POSSESSION_OR_STATE.matcher(text).find()) {
            assess(results, "THIRD_PARTY_POSSESSION_OR_STATE", refs, types,
                    Set.of(GroundingEvidenceType.MEMORY_OR_BELIEF,
                            GroundingEvidenceType.SEMANTIC_WORLD));
        }
        if (AUTOBIOGRAPHICAL_PAST_EVENT.matcher(text).find()) {
            assess(results, "AUTOBIOGRAPHICAL_PAST_EVENT", refs, types,
                    Set.of(GroundingEvidenceType.MEMORY_OR_BELIEF));
        }
        if (results.isEmpty()) {
            String category = SUBJECTIVE.matcher(text).find() || hypothetical
                    ? "SAFE_SOCIAL_SUBJECTIVE" : "SAFE_SOCIAL";
            results.add(new ClaimAssessment(category, refs,
                    List.copyOf(types), true,
                    "subjective/social dialogue does not assert an authoritative world, memory, "
                            + "or executed-action fact"));
        }
        return List.copyOf(results);
    }

    private static void assess(List<ClaimAssessment> results, String category,
            List<String> refs, Set<GroundingEvidenceType> actual,
            Set<GroundingEvidenceType> required) {
        boolean supported = actual.stream().anyMatch(required::contains);
        String reason = supported
                ? "type-compatible evidence present"
                : "claim category " + category + " requires one of " + required
                        + " but cited evidence types were " + actual;
        results.add(new ClaimAssessment(category, refs, List.copyOf(actual),
                supported, reason));
    }

    private static void assessExact(List<ClaimAssessment> results, String category,
            List<String> refs, Set<GroundingEvidenceType> actual, boolean supported,
            String requirement) {
        results.add(new ClaimAssessment(category, refs, List.copyOf(actual), supported,
                supported ? "type-compatible authored evidence present"
                        : "claim category " + category + " " + requirement
                                + " but cited evidence was " + refs));
    }

    public record ClaimAssessment(String category, List<String> citedEvidenceRefs,
            List<GroundingEvidenceType> evidenceTypes, boolean valid, String reason) {
        public ClaimAssessment {
            citedEvidenceRefs = List.copyOf(citedEvidenceRefs == null
                    ? List.of() : citedEvidenceRefs);
            evidenceTypes = List.copyOf(evidenceTypes == null ? List.of() : evidenceTypes);
            reason = reason == null ? "" : reason.strip();
        }

        public String diagnostic() {
            return "category=" + category + "; evidenceRefs=" + citedEvidenceRefs
                    + "; evidenceTypes=" + evidenceTypes + "; validation="
                    + (valid ? "VALID" : "REJECTED") + "; reason=" + reason;
        }
    }
}
