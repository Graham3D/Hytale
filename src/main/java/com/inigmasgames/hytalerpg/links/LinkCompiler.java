package com.inigmasgames.hytalerpg.links;

import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.domain.LinkNodeId;
import com.inigmasgames.hytalerpg.domain.PassiveDefinition;
import com.inigmasgames.hytalerpg.domain.PassiveId;
import com.inigmasgames.hytalerpg.domain.PassiveSlot;
import com.inigmasgames.hytalerpg.domain.SkillDefinition;
import com.inigmasgames.hytalerpg.domain.SkillId;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.progress.RpgPlayerState;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure, deterministic Link compiler. It creates plans but executes no combat behavior. */
public final class LinkCompiler {
    private final RpgCatalog catalog;
    private final RpgLinkGraphService graphService;
    private final CompatibilityService compatibility;
    private final CombatBalanceProfile balance;

    public LinkCompiler(RpgCatalog catalog, RpgLinkGraphService graphService, CompatibilityService compatibility) {
        this(catalog, graphService, compatibility, CombatBalanceProfile.loadCanonical());
    }

    public LinkCompiler(RpgCatalog catalog, RpgLinkGraphService graphService, CompatibilityService compatibility,
                        CombatBalanceProfile balance) {
        this.catalog = catalog;
        this.graphService = graphService;
        this.compatibility = compatibility;
        this.balance = balance;
    }

    public CompilationResult compile(RpgPlayerState state) {
        GraphValidationResult graph = graphService.validate(state);
        if (!graph.valid()) return CompilationResult.failure(graph.firstIssue().code(), graph.firstIssue().message());

        Map<SkillSlot, CompiledSkillPlan> plans = new EnumMap<>(SkillSlot.class);
        for (SkillSlot slot : SkillSlot.values()) {
            var skillId = state.skill(slot);
            if (skillId.isEmpty()) continue;
            var definition = catalog.skill(skillId.get());
            if (definition.isEmpty()) {
                plans.put(slot, degradedPlan(slot, skillId.get(), "Definition is missing from the current catalog"));
                continue;
            }
            List<PassiveBinding> bindings = bindingsFor(state, graph, slot);
            CompilationResult validation = validateBindings(definition.get(), bindings);
            if (!validation.success()) return validation;
            plans.put(slot, compileOne(slot, definition.get(), bindings));
        }
        return CompilationResult.success(plans);
    }

    private CompilationResult validateBindings(SkillDefinition skill, List<PassiveBinding> bindings) {
        Map<String, PassiveDefinition> groups = new LinkedHashMap<>();
        for (PassiveBinding binding : bindings) {
            CompatibilityResult result = compatibility.assess(skill, binding.definition(),bindings.stream().map(PassiveBinding::definition).toList());
            if (!result.accepted()) return CompilationResult.failure(result.code(), result.message());
            String group = binding.definition().stackingGroup();
            if(Set.of("widening","focused_channel").contains(binding.definition().id().value()))group="BEAM_WIDTH_MODE";
            if(Set.of("vacuum","repulsion").contains(binding.definition().id().value()))group="AREA_POSITION_MODE";
            if (group != null && !group.isBlank()) {
                PassiveDefinition prior = groups.putIfAbsent(group, binding.definition());
                if (prior != null && !prior.id().equals(binding.definition().id())) {
                    return CompilationResult.failure(ValidationCode.CONFLICTING_MODIFIER,
                            prior.name() + " conflicts with " + binding.definition().name() + " in " + group + '.');
                }
            }
        }
        boolean orbit = bindings.stream().anyMatch(binding -> binding.definition().id().value().equals("orbit"));
        var selected=bindings.stream().map(b->b.definition().id().value()).collect(java.util.stream.Collectors.toSet());
        if(orbit&&(selected.contains("volley")?3:com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.baseOrbitCount(skill.id().value()))*(selected.contains("barrage")?3:selected.contains("echo")?2:1)>3)return CompilationResult.failure(ValidationCode.CONFLICTING_MODIFIER,"Orbit caps one root at three orbs; this multiplicity/release combination exceeds that cap.");
        if(selected.contains("lifeblood")&&(selected.contains("leeching")||selected.contains("retaliation")))
            return CompilationResult.failure(ValidationCode.CONFLICTING_MODIFIER,"Lifeblood excludes Leeching and nonmanual Retaliation Health spending.");
        if (orbit && bindings.stream().anyMatch(binding -> Set.of("piercing", "fork", "chain", "ricochet", "return")
                .contains(binding.definition().id().value()))) {
            return CompilationResult.failure(ValidationCode.CONFLICTING_MODIFIER,
                    "Orbit removes Projectile continuation capabilities and conflicts with Piercing/Fork/Chain/Ricochet/Return.");
        }
        return CompilationResult.success(Map.of());
    }

    private CompiledSkillPlan compileOne(SkillSlot slot, SkillDefinition skill, List<PassiveBinding> bindings) {
        bindings.sort(Comparator.comparingInt((PassiveBinding binding) -> binding.definition().priority())
                .thenComparing(binding -> binding.definition().id()));
        Set<String> finalTags = new LinkedHashSet<>(skill.linkCompatibilityTags());
        finalTags.addAll(skill.tags());
        String family = skill.family();
        List<String> targeting = new ArrayList<>();
        List<String> geometry = new ArrayList<>();
        List<String> multiplicity = new ArrayList<>();
        List<String> continuation = new ArrayList<>();
        List<String> resource = new ArrayList<>();
        List<String> power = new ArrayList<>();
        List<String> triggers = new ArrayList<>();
        Map<PassiveId, List<LinkNodeId>> routes = new LinkedHashMap<>();
        int spawnCost = 0;
        double scalablePayloadIncreased = 0.0;
        double resourceCostMultiplier = 1.0;
        double cooldownRecoveryBonus = 0.0;

        for (PassiveBinding binding : bindings) {
            PassiveDefinition passive = binding.definition();
            routes.put(passive.id(), binding.route());
            finalTags.removeAll(passive.removedTags());
            finalTags.addAll(passive.addedTags());
            if (!passive.familyConversion().isBlank()) {
                family = passive.familyConversion();
                finalTags.add(family);
                if (passive.id().value().equals("orbit")) {
                    finalTags.removeAll(Set.of("PROJECTILE", "CAN_PIERCE", "CAN_FORK", "CAN_CHAIN", "CAN_RICOCHET", "CAN_RETURN"));
                    finalTags.addAll(Set.of("ORB","HAS_RADIUS","HAS_AREA_GEOMETRY","PERSISTENT_FINITE_EFFECT","CAN_AFFECT_ENEMY_POSITION"));
                }
            }
            String operation = passive.name() + ": " + String.join("; ", passive.modifierOps());
            int priority = passive.priority();
            if (priority < 200) targeting.add("FAMILY_CONVERSION=" + family + " via " + passive.name());
            else if (priority < 300) targeting.add(operation);
            else if (priority < 400) geometry.add(operation);
            else if (priority < 500) multiplicity.add(operation);
            else if (priority < 600) continuation.add(continuationOperation(passive));
            else if (priority < 700) resource.add(operation);
            else power.add(operation);
            if (!passive.triggerHook().isBlank()) triggers.add(passive.triggerHook() + ':' + passive.id().value());
            spawnCost += passive.spawnBudgetCost();
            switch (passive.id().value()) {
                case "potency" -> scalablePayloadIncreased += balance.potencyIncreased;
                case "ballistics" -> scalablePayloadIncreased += .30;
                case "efficiency" -> resourceCostMultiplier *= 0.85;
                case "overcharge" -> {scalablePayloadIncreased+=.25;resourceCostMultiplier*=1.20;}
                case "concentration" -> {
                    if(compatibility.assess(skill,passive).accepted()||bindings.stream().anyMatch(b->b.definition().id().value().equals("orbit")))scalablePayloadIncreased+=.30;
                    else power.add("CONCENTRATION_SCOPE=SECONDARY_ONLY");
                }
                case "lingering" -> resourceCostMultiplier*=1.15;
                case "reversal" -> scalablePayloadIncreased+=.25;
                case "focused_channel" -> scalablePayloadIncreased+=.30;
                case "lifeblood" -> scalablePayloadIncreased+=.10;
                default -> { }
            }
            if (passive.id().value().equals("expanded_radius")) {
                String prefix="";
                if(!compatibility.assess(skill,passive).accepted()){
                    if(bindings.stream().anyMatch(b->b.definition().id().value().equals("shockwave")))prefix="SHOCKWAVE_";
                    else if(bindings.stream().anyMatch(b->b.definition().id().value().equals("shrapnel")))prefix="SHRAPNEL_";
                }
                geometry.add(prefix+"RADIUS_MULTIPLIER=1.25");
                power.add(prefix+"MAGNITUDE_MULTIPLIER=0.90");
            }
            if(Set.of("vacuum","repulsion").contains(passive.id().value())&&!compatibility.assess(skill,passive).accepted()&&bindings.stream().noneMatch(b->b.definition().id().value().equals("orbit")))geometry.add("POSITION_SCOPE=SECONDARY_ONLY");
        }
        continuation.sort(Comparator.comparingInt(LinkCompiler::continuationRank).thenComparing(String::compareTo));
        List<PassiveId> order = bindings.stream().map(binding -> binding.definition().id()).toList();
        String canonical = slot + "|" + skill.id().value() + "|" + family + "|" + sorted(finalTags)
                + "|" + order + "|" + targeting + "|" + geometry + "|" + multiplicity + "|" + continuation
                + "|" + resource + "|" + power + "|" + triggers + "|" + spawnCost
                + "|" + scalablePayloadIncreased + "|" + resourceCostMultiplier + "|" + cooldownRecoveryBonus
                + "|" + CompiledSkillPlan.ExecutionModifiers.from(order) + "|" + CompiledSkillPlan.ProjectileModifiers.from(order)
                + "|" + com.inigmasgames.hytalerpg.domain.SupportModifiers.from(order)
                + "|" + com.inigmasgames.hytalerpg.domain.SummonModifiers.from(order)
                + "|" + com.inigmasgames.hytalerpg.domain.FoundationModifiers.from(order)
                + "|" + com.inigmasgames.hytalerpg.domain.HitConditionModifiers.from(order)
                + "|" + com.inigmasgames.hytalerpg.domain.GeometryModifiers.from(order)
                + "|" + com.inigmasgames.hytalerpg.domain.ZoneModifiers.from(order)
                + "|" + com.inigmasgames.hytalerpg.domain.PulseModifiers.from(order)
                + "|" + com.inigmasgames.hytalerpg.domain.DotModifiers.from(order)
                + "|" + com.inigmasgames.hytalerpg.domain.ControlModifiers.from(order)
                + "|" + com.inigmasgames.hytalerpg.domain.ResourceModifiers.from(order)
                + "|" + com.inigmasgames.hytalerpg.domain.StrikeModifiers.from(order)
                + "|" + com.inigmasgames.hytalerpg.domain.PositionModifiers.from(order)
                + "|planSchema=" + CompiledSkillPlan.CURRENT_SCHEMA;
        var kernelModifiers = new CompiledSkillPlan.KernelModifiers(scalablePayloadIncreased,
                resourceCostMultiplier, cooldownRecoveryBonus);
        return new CompiledSkillPlan(CompiledSkillPlan.CURRENT_SCHEMA, slot, skill.id(), hash(canonical), family,
                finalTags, order, routes, targeting, geometry, multiplicity, continuation, resource, power, kernelModifiers, triggers,
                skill.vfxRecipeId(), skill.soundRecipeId(), CompiledSkillPlan.SafetyBudgets.baseline(spawnCost), false, List.of());
    }

    private List<PassiveBinding> bindingsFor(RpgPlayerState state, GraphValidationResult graph, SkillSlot skillSlot) {
        List<PassiveBinding> result = new ArrayList<>();
        for (var entry : graph.routes().entrySet()) {
            if (entry.getValue().getLast().skillSlot() != skillSlot) continue;
            var passiveId = state.passive(entry.getKey());
            if (passiveId.isEmpty()) continue;
            catalog.passive(passiveId.get()).ifPresent(definition -> result.add(new PassiveBinding(entry.getKey(), definition, entry.getValue())));
        }
        return result;
    }

    private static String continuationOperation(PassiveDefinition passive) {
        if (passive.id().value().equals("fork")) {
            return "FORK(children=" + passive.childProjectileCount() + ",angles=-20/+20,depth=1)";
        }
        return passive.name().toUpperCase().replace(' ', '_') + ":" + String.join(";", passive.modifierOps());
    }

    private static int continuationRank(String operation) {
        if (operation.startsWith("SPLIT")) return 10;
        if (operation.startsWith("PIERC")) return 20;
        if (operation.startsWith("FORK")) return 30;
        if (operation.startsWith("CHAIN")) return 40;
        if (operation.startsWith("RICOCHET")) return 45;
        if (operation.startsWith("RETURN")) return 50;
        return 100;
    }

    private static String sorted(Set<String> values) { return values.stream().sorted().toList().toString(); }

    private static String hash(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    private static CompiledSkillPlan degradedPlan(SkillSlot slot, SkillId id, String reason) {
        return new CompiledSkillPlan(CompiledSkillPlan.CURRENT_SCHEMA, slot, id, hash(slot + "|" + id.value() + "|DEGRADED"),
                "UNKNOWN", Set.of("DEGRADED"), List.of(), Map.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), CompiledSkillPlan.KernelModifiers.NONE, List.of(), "", "",
                CompiledSkillPlan.SafetyBudgets.baseline(0), true, List.of(reason));
    }

    private record PassiveBinding(PassiveSlot slot, PassiveDefinition definition, List<LinkNodeId> route) {}
}
