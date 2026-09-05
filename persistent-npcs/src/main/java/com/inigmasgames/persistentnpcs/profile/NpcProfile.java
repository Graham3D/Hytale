package com.inigmasgames.persistentnpcs.profile;

import java.util.List;
import java.util.UUID;

public record NpcProfile(
        UUID id,
        String name,
        String role,
        String personality,
        String biography,
        String purpose,
        String home,
        String workplace,
        List<String> likes,
        List<String> dislikes,
        List<String> roleIds,
        List<String> capabilities,
        int defaultDisposition,
        Integer schemaVersion,
        String selfIdentity,
        String ageCategory,
        String speakingStyle,
        List<String> knowledgeDomains,
        List<NpcScheduleEntry> defaultSchedule,
        String appearancePreset,
        UUID stableId,
        String speciesArchetype,
        List<String> personalityTraits,
        List<String> values,
        List<String> fears,
        List<String> goals,
        String voicePreset,
        String voiceEffectPreset,
        String modelTier,
        Double riskTolerance,
        Double sociability,
        Double curiosity,
        Double trustDisposition,
        List<AuthoredNpcRelationship> relationships,
        String summary,
        String creatorNotes) {

    /** Backward-compatible constructor for Profile Schema v1 callers. */
    public NpcProfile(
            UUID id, String name, String role, String personality, String biography,
            String purpose, String home, String workplace, List<String> likes,
            List<String> dislikes, List<String> roleIds, List<String> capabilities,
            int defaultDisposition, Integer schemaVersion, String selfIdentity,
            String ageCategory, String speakingStyle, List<String> knowledgeDomains,
            List<NpcScheduleEntry> defaultSchedule, String appearancePreset, UUID stableId,
            String speciesArchetype, List<String> personalityTraits, List<String> values,
            List<String> fears, List<String> goals, String voicePreset,
            String voiceEffectPreset, String modelTier, Double riskTolerance,
            Double sociability, Double curiosity, Double trustDisposition,
            List<AuthoredNpcRelationship> relationships) {
        this(id, name, role, personality, biography, purpose, home, workplace, likes,
                dislikes, roleIds, capabilities, defaultDisposition, schemaVersion,
                selfIdentity, ageCategory, speakingStyle, knowledgeDomains, defaultSchedule,
                appearancePreset, stableId, speciesArchetype, personalityTraits, values, fears,
                goals, voicePreset, voiceEffectPreset, modelTier, riskTolerance, sociability,
                curiosity, trustDisposition, relationships, "", "");
    }

    /** Backward-compatible constructor for pre-R031 profile callers. */
    public NpcProfile(
            UUID id, String name, String role, String personality, String biography,
            String purpose, String home, String workplace, List<String> likes,
            List<String> dislikes, List<String> roleIds, List<String> capabilities,
            int defaultDisposition, Integer schemaVersion, String selfIdentity,
            String ageCategory, String speakingStyle, List<String> knowledgeDomains,
            List<NpcScheduleEntry> defaultSchedule, String appearancePreset, UUID stableId,
            String speciesArchetype, List<String> personalityTraits, List<String> values,
            List<String> fears, List<String> goals, String voicePreset,
            String voiceEffectPreset, String modelTier, Double riskTolerance,
            Double sociability, Double curiosity, Double trustDisposition) {
        this(id, name, role, personality, biography, purpose, home, workplace, likes,
                dislikes, roleIds, capabilities, defaultDisposition, schemaVersion,
                selfIdentity, ageCategory, speakingStyle, knowledgeDomains, defaultSchedule,
                appearancePreset, stableId, speciesArchetype, personalityTraits, values, fears,
                goals, voicePreset, voiceEffectPreset, modelTier, riskTolerance, sociability,
                curiosity, trustDisposition, List.of(), "", "");
    }

    public NpcProfile(
            UUID id,
            String name,
            String role,
            String personality,
            String biography,
            String purpose,
            String home,
            String workplace,
            List<String> likes,
            List<String> dislikes,
            List<String> roleIds,
            List<String> capabilities,
            int defaultDisposition) {
        this(id, name, role, personality, biography, purpose, home, workplace,
                likes, dislikes, roleIds, capabilities, defaultDisposition,
                1, name, "ADULT", "Natural and concise", List.of(), List.of(), "",
                id, "HUMAN", List.of(personality), List.of(), List.of(), List.of(),
                "", "none", "GENERIC", 0.35, 0.55, 0.65, 0.40);
    }

    public NpcProfile(
            UUID id,
            String name,
            String role,
            String personality,
            String biography,
            String purpose,
            String home,
            String workplace,
            List<String> likes,
            List<String> dislikes,
            int defaultDisposition) {
        this(id, name, role, personality, biography, purpose, home, workplace,
                likes, dislikes, List.of(), List.of(), defaultDisposition,
                1, name, "ADULT", "Natural and concise", List.of(), List.of(), "",
                id, "HUMAN", List.of(personality), List.of(), List.of(), List.of(),
                "", "none", "GENERIC", 0.35, 0.55, 0.65, 0.40);
    }

    /** Backward-compatible constructor for existing authored Profile Schema v1 files. */
    public NpcProfile(
            UUID id,
            String name,
            String role,
            String personality,
            String biography,
            String purpose,
            String home,
            String workplace,
            List<String> likes,
            List<String> dislikes,
            List<String> roleIds,
            List<String> capabilities,
            int defaultDisposition,
            Integer schemaVersion,
            String selfIdentity,
            String ageCategory,
            String speakingStyle,
            List<String> knowledgeDomains,
            List<NpcScheduleEntry> defaultSchedule,
            String appearancePreset) {
        this(id, name, role, personality, biography, purpose, home, workplace,
                likes, dislikes, roleIds, capabilities, defaultDisposition, schemaVersion,
                selfIdentity, ageCategory, speakingStyle, knowledgeDomains, defaultSchedule,
                appearancePreset, id, "HUMAN", List.of(personality), List.of(), List.of(),
                List.of(), "", "none", "GENERIC", 0.35, 0.55, 0.65, 0.40);
    }

    /** Backward-compatible constructor for pre-R012 Profile Schema v1 callers. */
    public NpcProfile(
            UUID id, String name, String role, String personality, String biography,
            String purpose, String home, String workplace, List<String> likes,
            List<String> dislikes, List<String> roleIds, List<String> capabilities,
            int defaultDisposition, Integer schemaVersion, String selfIdentity,
            String ageCategory, String speakingStyle, List<String> knowledgeDomains,
            List<NpcScheduleEntry> defaultSchedule, String appearancePreset, UUID stableId,
            String speciesArchetype, List<String> personalityTraits, List<String> values,
            List<String> fears, List<String> goals, String voicePreset, String modelTier) {
        this(id, name, role, personality, biography, purpose, home, workplace, likes,
                dislikes, roleIds, capabilities, defaultDisposition, schemaVersion,
                selfIdentity, ageCategory, speakingStyle, knowledgeDomains, defaultSchedule,
                appearancePreset, stableId, speciesArchetype, personalityTraits, values, fears,
                goals, voicePreset, "none", modelTier, 0.35, 0.55, 0.65, 0.40);
    }

    /** Backward-compatible constructor for pre-R013 callers with cognition fields. */
    public NpcProfile(
            UUID id, String name, String role, String personality, String biography,
            String purpose, String home, String workplace, List<String> likes,
            List<String> dislikes, List<String> roleIds, List<String> capabilities,
            int defaultDisposition, Integer schemaVersion, String selfIdentity,
            String ageCategory, String speakingStyle, List<String> knowledgeDomains,
            List<NpcScheduleEntry> defaultSchedule, String appearancePreset, UUID stableId,
            String speciesArchetype, List<String> personalityTraits, List<String> values,
            List<String> fears, List<String> goals, String voicePreset, String modelTier,
            Double riskTolerance, Double sociability, Double curiosity,
            Double trustDisposition) {
        this(id, name, role, personality, biography, purpose, home, workplace, likes,
                dislikes, roleIds, capabilities, defaultDisposition, schemaVersion,
                selfIdentity, ageCategory, speakingStyle, knowledgeDomains, defaultSchedule,
                appearancePreset, stableId, speciesArchetype, personalityTraits, values, fears,
                goals, voicePreset, "none", modelTier, riskTolerance, sociability, curiosity,
                trustDisposition);
    }

    public NpcProfile validated() {
        require(name, "name");
        UUID resolvedId = stableId == null ? id : stableId;
        if (resolvedId == null) {
            // Authored create profiles may omit identity. A deterministic value is
            // assigned once and persisted by ProfileRepository, so retries/restarts
            // cannot create a second persistent NPC identity.
            resolvedId = UUID.nameUUIDFromBytes(("ImmersiveNPCs:profile:"
                    + name.strip().toLowerCase(java.util.Locale.ROOT))
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        require(role, "role");
        require(personality, "personality");
        require(biography, "biography");
        require(purpose, "purpose");
        if (defaultDisposition < -100 || defaultDisposition > 100) {
            throw new IllegalArgumentException("defaultDisposition must be between -100 and 100");
        }
        return new NpcProfile(resolvedId, name, role, personality, biography, purpose,
                home == null ? "" : home,
                workplace == null ? "" : workplace,
                likes == null ? List.of() : List.copyOf(likes),
                dislikes == null ? List.of() : List.copyOf(dislikes),
                roleIds == null ? List.of() : roleIds.stream()
                        .map(NpcProfile::normalize).distinct().toList(),
                capabilities == null ? List.of() : capabilities.stream()
                        .map(NpcProfile::normalize).distinct().toList(),
                defaultDisposition,
                schemaVersion == null ? 1 : schemaVersion,
                selfIdentity == null || selfIdentity.isBlank() ? name : selfIdentity.strip(),
                ageCategory == null || ageCategory.isBlank() ? "ADULT" : normalize(ageCategory),
                speakingStyle == null ? "" : speakingStyle.strip(),
                knowledgeDomains == null ? List.of() : knowledgeDomains.stream()
                        .map(String::strip).filter(value -> !value.isBlank()).distinct().toList(),
                defaultSchedule == null ? List.of() : defaultSchedule.stream()
                        .map(NpcScheduleEntry::normalized).toList(),
                appearancePreset == null ? "" : appearancePreset.strip(),
                resolvedId,
                speciesArchetype == null || speciesArchetype.isBlank()
                        ? "HUMAN" : normalize(speciesArchetype),
                clean(personalityTraits == null || personalityTraits.isEmpty()
                        ? List.of(personality) : personalityTraits),
                clean(values), clean(fears), clean(goals),
                voicePreset == null ? "" : voicePreset.strip(),
                voiceEffectPreset == null || voiceEffectPreset.isBlank()
                        ? "none" : voiceEffectPreset.strip().toLowerCase(java.util.Locale.ROOT),
                modelTier == null || modelTier.isBlank() ? "GENERIC" : normalize(modelTier),
                bounded(riskTolerance, 0.35), bounded(sociability, 0.55),
                bounded(curiosity, 0.65), bounded(trustDisposition, 0.40),
                relationships == null ? List.of() : relationships.stream()
                        .filter(java.util.Objects::nonNull)
                        .map(AuthoredNpcRelationship::normalized)
                        .filter(AuthoredNpcRelationship::identifiesTarget).toList(),
                summary == null ? "" : summary.strip(),
                creatorNotes == null ? "" : creatorNotes.strip());
    }

    public boolean hasCapability(String capability) {
        return capabilities.contains(normalize(capability));
    }

    public boolean hasRole(String roleId) {
        return roleIds.contains(normalize(roleId));
    }

    public NpcProfile withRelationships(List<AuthoredNpcRelationship> authored) {
        return new NpcProfile(id, name, role, personality, biography, purpose, home,
                workplace, likes, dislikes, roleIds, capabilities, defaultDisposition,
                schemaVersion, selfIdentity, ageCategory, speakingStyle, knowledgeDomains,
                defaultSchedule, appearancePreset, stableId, speciesArchetype,
                personalityTraits, values, fears, goals, voicePreset, voiceEffectPreset,
                modelTier, riskTolerance, sociability, curiosity, trustDisposition,
                authored, summary, creatorNotes).validated();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toUpperCase(java.util.Locale.ROOT);
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("NPC profile " + field + " is required");
        }
    }

    private static List<String> clean(List<String> values) {
        return values == null ? List.of() : values.stream().filter(java.util.Objects::nonNull)
                .map(String::strip).filter(value -> !value.isBlank()).distinct().toList();
    }

    private static double bounded(Double value, double fallback) {
        double resolved = value == null || !Double.isFinite(value) ? fallback : value;
        return Math.max(0.0, Math.min(1.0, resolved));
    }
}
