package com.inigmasgames.hytalerpg.links;

import com.inigmasgames.hytalerpg.domain.PassiveDefinition;
import com.inigmasgames.hytalerpg.domain.SkillDefinition;

import java.util.LinkedHashSet;
import java.util.Set;

/** One compatibility authority shared by commands, graph validation, compiler, and future UI adapters. */
public final class CompatibilityService {
    /** Component-introduction seam: do not grant radius to the original projectile carrier. */
    public CompatibilityResult assess(SkillDefinition skill,PassiveDefinition passive,java.util.List<PassiveDefinition> selected) {
        boolean orbit=selected.stream().anyMatch(p->p.id().value().equals("orbit"))&&com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.orbit(skill.id().value());
        if(orbit&&(passive.id().value().equals("terror")||passive.id().value().equals("hemorrhage")&&skill.tags().contains("PHYSICAL")))
            return CompatibilityResult.accepted(Set.of("CONVERTED_ORBIT_DIRECT_HIT","DAMAGE"));
        if(orbit&&Set.of("piercing","fork","chain","return","ricochet","homing","accelerant","ballistics","shrapnel","splinterburst","long_reach","rapid_pulse").contains(passive.id().value()))return CompatibilityResult.rejected(ValidationCode.EXCLUDED_DELIVERY,
                "Orbit removes projectile flight/continuation and pulse cadence; this modifier has no retained eligible component.",Set.of("REMOVED_BY_ORBIT"),skill.linkCompatibilityTags());
        if(orbit&&Set.of("expanded_radius","concentration","lingering","aftermath","vacuum","repulsion").contains(passive.id().value()))return new CompatibilityResult(true,ValidationCode.ACCEPTED,
                "Compatible with the converted finite Orbit component",Set.of("CONVERTED_ORBIT_COMPONENT"),Set.of("ORBIT","HAS_RADIUS","HAS_AREA_GEOMETRY","PERSISTENT_FINITE_EFFECT"));
        var base=assess(skill,passive);
        if(base.accepted())return base;
        boolean shockwave=selected.stream().anyMatch(p->p.id().value().equals("shockwave")&&assess(skill,p).accepted());
        boolean cleave=selected.stream().anyMatch(p->p.id().value().equals("cleaving_edge")&&assess(skill,p).accepted());
        boolean shrapnel=selected.stream().anyMatch(p->p.id().value().equals("shrapnel")&&assess(skill,p).accepted());
        if(Set.of("vacuum","repulsion").contains(passive.id().value())&&(shockwave||cleave||shrapnel))return new CompatibilityResult(true,ValidationCode.ACCEPTED,
                "Position control applies only to introduced hostile area components, not the original carrier",Set.of("HAS_AREA_GEOMETRY","CAN_AFFECT_ENEMY_POSITION"),Set.of("COMPONENT_SECONDARY_AREA"));
        if(passive.id().value().equals("concentration")&&(shockwave||cleave))return new CompatibilityResult(true,ValidationCode.ACCEPTED,
                "Concentration applies only to the introduced secondary area component, not the original single-target strike",Set.of("HAS_AREA_GEOMETRY"),Set.of("COMPONENT_STRIKE_SECONDARY","AREA","DAMAGE","HAS_AREA_GEOMETRY"));
        if(!passive.id().value().equals("expanded_radius"))return base;
        if(shockwave)return new CompatibilityResult(true,ValidationCode.ACCEPTED,"Expanded Radius applies only to the Shockwave Burst, not the original strike",
                Set.of("HAS_RADIUS"),Set.of("COMPONENT_SHOCKWAVE","BURST","AREA","DAMAGE","HAS_RADIUS"));
        if(!shrapnel)return base;
        return new CompatibilityResult(true,ValidationCode.ACCEPTED,"Compatible with Shrapnel secondary radius only",Set.of("HAS_RADIUS"),
                Set.of("COMPONENT_SHRAPNEL","AREA","BURST","DAMAGE","HAS_RADIUS"));
    }
    public CompatibilityResult assess(SkillDefinition skill, PassiveDefinition passive) {
        Set<String> actual = new LinkedHashSet<>(skill.linkCompatibilityTags());
        actual.addAll(skill.tags());
        if(passive.id().value().equals("retaliation")&&com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.retaliation(skill.id().value()).filter(v->!v).isPresent())
            return CompatibilityResult.rejected(ValidationCode.EXCLUDED_DELIVERY,"Retaliation requires a discrete activation without movement, reaction, channel, Aura, corpse or conversion consumption.",Set.of("RETALIATION_DISCRETE_COMPONENT"),actual);
        if(Set.of("critical_trigger","kill_trigger").contains(passive.id().value())&&
                com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.conditionalRepeat(skill.id().value(),passive.id().value().equals("critical_trigger")).filter(v->!v).isPresent())
            return CompatibilityResult.rejected(ValidationCode.EXCLUDED_DELIVERY,"Conditional repeats require a discrete damaging primary; no movement, reaction, channel, support, summon or consumer replay.",Set.of("REPEATABLE_PRIMARY_COMPONENT"),actual);
        if(passive.id().value().equals("proliferation")){
            var burn=com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.dotPayload(skill.id().value(),"BURN");
            var poison=com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.dotPayload(skill.id().value(),"POISON");
            var chill=com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.chillPayload(skill.id().value());
            if(burn.isPresent()&&!burn.get()&&!poison.orElse(false)&&!chill.orElse(false))return CompatibilityResult.rejected(ValidationCode.NO_SCALABLE_FIELD,
                    "Proliferation requires a source-owned Burn, Poison or Chill application, not elemental damage alone.",Set.of("TRANSFERABLE_STATUS_COMPONENT"),actual);
        }
        if(passive.id().value().equals("hemorrhage")&&!actual.contains("PHYSICAL"))return CompatibilityResult.rejected(ValidationCode.NO_SCALABLE_FIELD,
                "Hemorrhage requires direct Physical damage; elemental damage alone does not qualify.",Set.of("PHYSICAL_DAMAGE_COMPONENT"),actual);
        if(passive.id().value().equals("shatter")&&(!actual.contains("COLD")||!actual.contains("DAMAGE")||!actual.contains("CAN_KILL")))return CompatibilityResult.rejected(ValidationCode.NO_SCALABLE_FIELD,
                "Shatter requires a killing Cold damage component, not a Slow, heal or non-Cold hit.",Set.of("COLD_KILL_COMPONENT"),actual);
        if(Set.of("hemorrhage","terror").contains(passive.id().value())){
            var direct=com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.directDamage(skill.id().value());
            if(direct.filter(v->!v).isPresent())return CompatibilityResult.rejected(ValidationCode.NO_SCALABLE_FIELD,
                    passive.name()+" requires a direct damaging component, not DoT, pulse-only damage, support or a summoned actor.",Set.of("DIRECT_DAMAGE_COMPONENT"),actual);
            if(direct.orElse(false)){actual.add("DIRECT_HIT");actual.add("DAMAGE");}
        }
        if(passive.id().value().equals("orbit")){
            if(!com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.orbit(skill.id().value()))return CompatibilityResult.rejected(ValidationCode.EXCLUDED_DELIVERY,
                    "Orbit requires an implemented projectile or finite Orb payload",Set.of("ORBIT_CONVERSION_COMPONENT"),actual);
            actual.add("ORBIT_CONVERTIBLE");actual.add("ORB");
        }
        if(passive.id().value().equals("aftermath")){
            if(!com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.aftermath(skill.id().value()))return CompatibilityResult.rejected(ValidationCode.EXCLUDED_DELIVERY,
                    "Aftermath requires a finite persistent area/Orb/Orbit, not a channel, Aura, collision wall, travelling carrier or recipient buff.",Set.of("EXPIRING_PERSISTENT_AREA_COMPONENT"),actual);
            actual.add("PERSISTENT_FINITE_EFFECT");actual.add("HAS_AREA_GEOMETRY");
        }
        if(passive.id().value().equals("cascade")){
            if(!com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.cascade(skill.id().value()))return CompatibilityResult.rejected(ValidationCode.EXCLUDED_DELIVERY,
                    "Cascade requires a ground-targeted radial area, not a caster burst, trap, corpse, collision wall or Aura.",Set.of("GROUND_TARGETED_RADIAL_AREA"),actual);
            actual.add("AREA_OF_EFFECT");actual.add("GROUND_TARGETED");
        }
        if(Set.of("vacuum","repulsion").contains(passive.id().value())){
            var position=com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.enemyPosition(skill.id().value());
            if(position.filter(v->!v).isPresent())return CompatibilityResult.rejected(ValidationCode.NO_SCALABLE_FIELD,
                    passive.name()+" requires a resolving hostile area or a hostile pulsing Aura, not a heal, target-lock or projectile carrier.",Set.of("HOSTILE_RESOLVING_AREA"),actual);
            if(position.orElse(false)){actual.add("HAS_AREA_GEOMETRY");actual.add("CAN_AFFECT_ENEMY_POSITION");}
        }
        if(Set.of("cleaving_edge","phantom_reach").contains(passive.id().value())&&
                (skill.id().value().equals("ground_slam")||com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.frontalStrike(skill.id().value()).filter(v->!v).isPresent()))
            return CompatibilityResult.rejected(ValidationCode.EXCLUDED_DELIVERY,passive.name()+" requires a frontal damaging Strike, not a radial, movement or reaction-only component.",Set.of("FRONTAL_STRIKE_COMPONENT"),actual);
        if(Set.of("multistrike","ruthless","shockwave").contains(passive.id().value())&&
                com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.discreteStrike(skill.id().value(),passive.id().value().equals("multistrike")).filter(v->!v).isPresent())
            return CompatibilityResult.rejected(ValidationCode.EXCLUDED_DELIVERY,passive.name()+" requires an independent discrete damaging Strike; Multistrike excludes an already authored sequence.",Set.of("DISCRETE_STRIKE_COMPONENT"),actual);
        if(passive.id().value().equals("multistrike")&&Set.of("dagger_flurry","whirlwind").contains(skill.id().value()))
            return CompatibilityResult.rejected(ValidationCode.EXCLUDED_DELIVERY,"Multistrike cannot repeat an authored multi-hit sequence.",Set.of("AUTHORED_MULTI_HIT"),actual);
        if(passive.id().value().equals("leeching")&&!Set.of("MANA","STAMINA").contains(skill.resourceType()))
            return CompatibilityResult.rejected(ValidationCode.NO_SCALABLE_FIELD,"Leeching requires a declared Mana or Stamina resource.",Set.of("DECLARED_SPEND_RESOURCE"),actual);

        if(Set.of("lifeblood","attunement").contains(passive.id().value())&&
                com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.finiteUpfront(skill.id().value()).filter(v->!v).isPresent())
            return CompatibilityResult.rejected(ValidationCode.NO_SCALABLE_FIELD,passive.name()+" requires a finite upfront Mana/Stamina spend, not upkeep or reservation.",Set.of("FINITE_UPFRONT_COMPONENT"),actual);

        if(passive.id().value().equals("deep_freeze")&&com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.chillPayload(skill.id().value()).filter(v->!v).isPresent())
            return CompatibilityResult.rejected(ValidationCode.NO_SCALABLE_FIELD,"Deep Freeze requires an authored Chill application.",Set.of("CHILL_APPLICATION_COMPONENT"),actual);

        if(Set.of("combustion","virulence","concentrated_venom").contains(passive.id().value())){
            String status=passive.id().value().equals("combustion")?"BURN":"POISON";
            var payload=com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.dotPayload(skill.id().value(),status);
            if(payload.filter(v->!v).isPresent())return CompatibilityResult.rejected(ValidationCode.NO_SCALABLE_FIELD,
                    passive.name()+" requires an implemented "+status+" application, not elemental damage alone.",Set.of(status+"_APPLICATION_COMPONENT"),actual);
            if(payload.orElse(false)){actual.add("APPLIES_"+status);if(status.equals("POISON"))actual.add("STACKABLE_POISON");}
        }

        if(passive.id().value().equals("rapid_pulse")){
            if(!com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.periodicPulse(skill.id().value()))
                return CompatibilityResult.rejected(ValidationCode.NO_SCALABLE_FIELD,"Rapid Pulse requires an authored pulse, not contact sampling, flight or a one-off attack sequence.",Set.of("PERIODIC_PULSE_COMPONENT"),actual);
            actual.add("PERIODIC_PULSE"); // Assessment only, no new capability on unrelated components.
        }

        if(passive.id().value().equals("mobile_domain")&&!com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.mobileZone(skill.id().value()))
            return CompatibilityResult.rejected(ValidationCode.EXCLUDED_DELIVERY,
                    "Mobile Domain requires a finite ground zone without fixed warnings, collision, corpse or trap ownership.",
                    Set.of("MOBILE_FINITE_ZONE_COMPONENT"),actual);

        if(passive.id().value().equals("impact_force")){
            var impact=com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.impact(skill.id().value());
            if(impact.filter(v->!v).isPresent())return CompatibilityResult.rejected(ValidationCode.NO_SCALABLE_FIELD,"Impact Force requires authored knockback or Stagger, not any crowd control or pull.",Set.of("IMPACT_COMPONENT"),actual);
            if(impact.orElse(false))actual.add("APPLIES_KNOCKBACK"); // Local OR gate only; not a global capability introduction.
        }
        if(Set.of("widening","focused_channel").contains(passive.id().value())){
            var width=com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.resolvingWidth(skill.id().value());
            if(width.filter(v->!v).isPresent())return CompatibilityResult.rejected(ValidationCode.NO_SCALABLE_FIELD,"Width modifiers require resolving Beam/Line geometry, not target-lock width.",Set.of("RESOLVING_WIDTH_COMPONENT"),actual);
            if(width.orElse(false))actual.add("HAS_WIDTH");
        }

        if(passive.id().value().equals("reversal")&&com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.reactionWindow(skill.id().value()).filter(value->!value).isPresent())
            return CompatibilityResult.rejected(ValidationCode.NO_SCALABLE_FIELD,"Reversal requires an authored reaction window.",Set.of("REACTION_WINDOW_COMPONENT"),actual);
        if(passive.id().value().equals("momentum")){
            boolean travels=(actual.contains("MOVEMENT")||actual.contains("DAMAGING_CHARGE"))&&actual.contains("DAMAGE");
            if(!com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.damagingMovement(skill.id().value()).orElse(travels))
                return CompatibilityResult.rejected(ValidationCode.NO_SCALABLE_FIELD,"Momentum requires validated travel followed by a damage component.",Set.of("DAMAGING_MOVEMENT_COMPONENT"),actual);
            // The source says Movement OR DamagingCharge; imported clauses incorrectly require both.
            // This local assessment never writes capability tags into the original skill or plan.
            actual.add("MOVEMENT");actual.add("DAMAGING_CHARGE");
        }

        // Imported summary tags are not proof of a distinct spend/range component.
        // These canonical radius-only fields must never gain reach semantics globally.
        if(passive.id().value().equals("long_reach")&&Set.of("whirlwind","ground_slam","frost_nova",
                "orbiting_shadow_blades","battle_cry","pack_howl","thorns_aura","emanatism",
                "chilling_aura","pedanticism","reaping_storm").contains(skill.id().value()))
            return CompatibilityResult.rejected(ValidationCode.NO_SCALABLE_FIELD,"Long Reach cannot scale a radius-only component.",Set.of("DECLARED_REACH_NOT_RADIUS"),actual);
        if(Set.of("efficiency","overcharge").contains(passive.id().value())&&actual.contains("MANA_RESERVATION")&&!actual.contains("HAS_UPKEEP"))
            return CompatibilityResult.rejected(ValidationCode.NO_SCALABLE_FIELD,passive.name()+" cannot modify a pure Mana reservation.",Set.of("FINITE_SPEND_OR_UPKEEP"),actual);
        if(passive.id().value().equals("lingering")&&com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.finiteDuration(skill.id().value()).filter(value->!value).isPresent())
            return CompatibilityResult.rejected(ValidationCode.NO_SCALABLE_FIELD,"Lingering requires a finite effect component, not flight, channel maximum, attack sequence, warning or crowd-control duration.",Set.of("FINITE_EFFECT_DURATION_COMPONENT"),actual);
        if(passive.id().value().equals("concentration")){
            var area=com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.affectedArea(skill.id().value());
            if(area.filter(value->!value).isPresent())return CompatibilityResult.rejected(ValidationCode.NO_SCALABLE_FIELD,
                    "Concentration requires an affected area, not a projectile collision or target-lock width.",Set.of("AFFECTED_AREA_COMPONENT"),actual);
            // Local assessment only: this set is not written into the skill or compiled global tags.
            if(area.orElse(false))actual.add("HAS_AREA_GEOMETRY");
        }

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
