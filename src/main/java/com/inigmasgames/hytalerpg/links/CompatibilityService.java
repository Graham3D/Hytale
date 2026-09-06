package com.inigmasgames.hytalerpg.links;

import com.inigmasgames.hytalerpg.domain.PassiveDefinition;
import com.inigmasgames.hytalerpg.domain.SkillDefinition;

import java.util.LinkedHashSet;
import java.util.Set;

/** One compatibility authority shared by commands, graph validation, compiler, and future UI adapters. */
public final class CompatibilityService {
    public CompatibilityResult assess(SkillDefinition skill, PassiveDefinition passive) {
        Set<String> actual = new LinkedHashSet<>(skill.linkCompatibilityTags());
        actual.addAll(skill.tags());

        if (!passive.requiredFamilies().isEmpty() && passive.requiredFamilies().stream().noneMatch(actual::contains)) {
            return CompatibilityResult.rejected(ValidationCode.WRONG_FAMILY,
                    passive.name() + " requires " + join(passive.requiredFamilies()) + "; " + skill.name()
                            + " is " + skill.family() + ".",
                    passive.requiredFamilies(), actual);
        }
        Set<String> missing = new LinkedHashSet<>(passive.requiredCapabilities());
        missing.removeAll(actual);
        boolean capabilityAlternatives = capabilityClauseUsesOr(passive.compatibilityExpression());
        boolean capabilitiesSatisfied = passive.requiredCapabilities().isEmpty()
                || (capabilityAlternatives
                    ? passive.requiredCapabilities().stream().anyMatch(actual::contains)
                    : missing.isEmpty());
        if (!capabilitiesSatisfied) {
            return CompatibilityResult.rejected(ValidationCode.MISSING_CAPABILITY,
                    passive.name() + " requires " + join(missing) + "; " + skill.name() + " does not expose it.",
                    missing, actual);
        }
        if (!passive.compatibleAnyPayloads().isEmpty()
                && passive.compatibleAnyPayloads().stream().noneMatch(actual::contains)) {
            return CompatibilityResult.rejected(ValidationCode.NO_SCALABLE_FIELD,
                    passive.name() + " requires one of " + join(passive.compatibleAnyPayloads())
                            + "; " + skill.name() + " has no matching payload.",
                    passive.compatibleAnyPayloads(), actual);
        }
        Set<String> excluded = new LinkedHashSet<>(passive.incompatibleTags());
        excluded.retainAll(actual);
        if (!excluded.isEmpty()) {
            return CompatibilityResult.rejected(ValidationCode.EXCLUDED_DELIVERY,
                    passive.name() + " excludes " + join(excluded) + ".", excluded, actual);
        }

        String gate = passive.compatibilityExpression().toLowerCase(java.util.Locale.ROOT);
        if ((gate.contains("finite mana/stamina cost") || gate.contains("finite upfront mana or stamina cost"))
                && !actual.contains("FINITE_RESOURCE_COST")) {
            return CompatibilityResult.rejected(ValidationCode.NO_SCALABLE_FIELD,
                    passive.name() + " requires a finite Mana or Stamina spend.", Set.of("FINITE_RESOURCE_COST"), actual);
        }
        if (gate.contains("finite spend/upkeep cost")
                && !(actual.contains("FINITE_RESOURCE_COST") || actual.contains("HAS_UPKEEP"))) {
            return CompatibilityResult.rejected(ValidationCode.NO_SCALABLE_FIELD,
                    passive.name() + " requires a finite spend or upkeep cost.", Set.of("FINITE_RESOURCE_COST", "HAS_UPKEEP"), actual);
        }
        if (gate.contains("resource mode: mana reservation or continuous mana drain")
                && !(actual.contains("MANA_RESERVATION") || actual.contains("HAS_UPKEEP"))) {
            return CompatibilityResult.rejected(ValidationCode.NO_SCALABLE_FIELD,
                    passive.name() + " requires Mana reservation or continuous Mana drain.", Set.of("MANA_RESERVATION", "HAS_UPKEEP"), actual);
        }

        // The source contract has gates whose prose is richer than its token clauses. These are stable,
        // shared rules rather than command-specific exceptions.
        String id = passive.id().value();
        if (id.equals("expanded_radius") && !actual.contains("HAS_RADIUS")) {
            return CompatibilityResult.rejected(ValidationCode.MISSING_CAPABILITY,
                    "Expanded Radius requires an effect radius; projectile collision radius does not qualify.",
                    Set.of("HAS_RADIUS"), actual);
        }
        if (id.equals("fork") && !(actual.contains("PROJECTILE") && actual.contains("CAN_FORK"))) {
            return CompatibilityResult.rejected(ValidationCode.WRONG_FAMILY,
                    "Fork requires Projectile compatibility; " + skill.name() + " is " + skill.family() + ".",
                    Set.of("PROJECTILE", "CAN_FORK"), actual);
        }
        return CompatibilityResult.accepted(actual);
    }

    private static String join(Set<String> values) { return String.join(" or ", values); }

    private static boolean capabilityClauseUsesOr(String expression) {
        if (expression == null) return false;
        String lower = expression.toLowerCase(java.util.Locale.ROOT);
        int start = lower.indexOf("capability:");
        if (start < 0) return false;
        int end = lower.indexOf(';', start);
        String clause = end < 0 ? lower.substring(start) : lower.substring(start, end);
        return clause.contains(" or ");
    }
}
