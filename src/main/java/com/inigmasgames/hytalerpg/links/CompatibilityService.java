package com.inigmasgames.hytalerpg.links;

import com.inigmasgames.hytalerpg.domain.PassiveDefinition;
import com.inigmasgames.hytalerpg.domain.SkillDefinition;

import java.util.LinkedHashSet;
import java.util.Set;

/** One compatibility authority shared by commands, graph validation, compiler, and future UI adapters. */
public final class CompatibilityService {
    /** Component-introduction seam: do not grant radius to the original projectile carrier. */
    public CompatibilityResult assess(SkillDefinition skill,PassiveDefinition passive,java.util.List<PassiveDefinition> selected) {
        var base=assess(skill,passive);
        if(base.accepted()||!passive.id().value().equals("expanded_radius"))return base;
        boolean shrapnel=selected.stream().anyMatch(p->p.id().value().equals("shrapnel")&&assess(skill,p).accepted());
        if(!shrapnel)return base;
        return new CompatibilityResult(true,ValidationCode.ACCEPTED,"Compatible with Shrapnel secondary radius only",Set.of("HAS_RADIUS"),
                Set.of("COMPONENT_SHRAPNEL","AREA","BURST","DAMAGE","HAS_RADIUS"));
    }
    public CompatibilityResult assess(SkillDefinition skill, PassiveDefinition passive) {
        Set<String> actual = new LinkedHashSet<>(skill.linkCompatibilityTags());
        actual.addAll(skill.tags());

        // Imported summary tags are not proof of a distinct spend/range component.
        // These canonical radius-only fields must never gain reach semantics globally.
        if(passive.id().value().equals("long_reach")&&Set.of("whirlwind","ground_slam","frost_nova",
                "orbiting_shadow_blades","battle_cry","pack_howl","thorns_aura","emanatism",
                "chilling_aura","pedanticism","reaping_storm").contains(skill.id().value()))
            return CompatibilityResult.rejected(ValidationCode.NO_SCALABLE_FIELD,"Long Reach cannot scale a radius-only component.",Set.of("DECLARED_REACH_NOT_RADIUS"),actual);
        if(passive.id().value().equals("efficiency")&&actual.contains("MANA_RESERVATION")&&!actual.contains("HAS_UPKEEP"))
            return CompatibilityResult.rejected(ValidationCode.NO_SCALABLE_FIELD,"Efficiency cannot reduce a pure Mana reservation.",Set.of("FINITE_SPEND_OR_UPKEEP"),actual);

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
                && !actual.contains("FINITE_RESOURCE_COST")
                && !(gate.contains("or continuous mana upkeep")&&actual.contains("HAS_UPKEEP"))) {
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
        if(Set.of("echo","retaliation","critical_trigger","kill_trigger").contains(id)&&
                (actual.contains("CORPSE_REQUIRED")||actual.contains("CORPSE")||actual.contains("CONSUME")||actual.contains("CONVERSION")))
            return CompatibilityResult.rejected(ValidationCode.EXCLUDED_DELIVERY,
                    "Generic repeats/triggers cannot select or consume a corpse, owned summon or native conversion target.",Set.of("CONSUMER_OR_CONVERSION"),actual);
        if(id.equals("swarm")&&(!actual.contains("TEMPORARY_COMBAT_SUMMON")||actual.contains("CORPSE_REQUIRED")||actual.contains("REVIVE")||actual.contains("DECOY")||actual.contains("CONVERSION")))
            return CompatibilityResult.rejected(ValidationCode.EXCLUDED_DELIVERY,
                    "Swarm requires a static temporary combat summon and cannot duplicate a corpse, decoy or conversion.",Set.of("TEMPORARY_COMBAT_SUMMON"),actual);
        if(id.equals("shared_aegis")&&!actual.contains("CAN_SHARE"))return CompatibilityResult.rejected(ValidationCode.MISSING_CAPABILITY,
                "Shared Aegis requires a shareable self-target absorb payload, not native block or collision.",Set.of("CAN_SHARE"),actual);
        if(id.equals("selflessness")&&!actual.contains("HAS_RADIUS"))return CompatibilityResult.rejected(ValidationCode.MISSING_CAPABILITY,
                "Selflessness requires a beneficial ally-radius Aura, not a self-only shield.",Set.of("HAS_RADIUS"),actual);
        if(id.equals("ballistics")&&actual.contains("BALLISTIC_GRAVITY"))
            return CompatibilityResult.rejected(ValidationCode.UNSUPPORTED_BUILD,"Ballistics requires an implemented retargeted gravity solution",
                    Set.of("RETARGETED_BALLISTIC_SOLUTION"),actual);
        if (id.equals("echo")) {
            Set<String> repeatExcluded = new LinkedHashSet<>(Set.of("CORPSE", "CORPSE_REQUIRED", "COLLISION_WALL", "TRAP"));
            repeatExcluded.retainAll(actual);
            if (!repeatExcluded.isEmpty()) return CompatibilityResult.rejected(ValidationCode.EXCLUDED_DELIVERY,
                    "Echo excludes corpse consumers, collision-wall creators and deployed traps.", repeatExcluded, actual);
        }
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
