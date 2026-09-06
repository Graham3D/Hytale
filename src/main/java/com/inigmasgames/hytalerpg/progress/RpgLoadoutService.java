package com.inigmasgames.hytalerpg.progress;

import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.diagnostics.RpgSkillTracer;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceRecord;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.domain.EdgeId;
import com.inigmasgames.hytalerpg.domain.LinkEdge;
import com.inigmasgames.hytalerpg.domain.LinkNodeId;
import com.inigmasgames.hytalerpg.domain.PassiveId;
import com.inigmasgames.hytalerpg.domain.PassiveSlot;
import com.inigmasgames.hytalerpg.domain.SkillId;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.links.CompatibilityResult;
import com.inigmasgames.hytalerpg.links.CompilationResult;
import com.inigmasgames.hytalerpg.links.GraphValidationResult;
import com.inigmasgames.hytalerpg.links.LinkCompiler;
import com.inigmasgames.hytalerpg.links.RpgLinkGraphService;
import com.inigmasgames.hytalerpg.links.ValidationCode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;

/** Single transaction boundary for all loadout and gameplay graph mutations. */
public final class RpgLoadoutService implements RpgLoadoutOperations {
    private final RpgCatalog catalog;
    private final RpgPlayerStateRepository repository;
    private final RpgLinkGraphService graphService;
    private final LinkCompiler compiler;
    private final EntitlementPolicy entitlements;
    private final RpgSkillTracer tracer;
    private final Map<UUID, Holder> states = new ConcurrentHashMap<>();

    public RpgLoadoutService(RpgCatalog catalog, RpgPlayerStateRepository repository,
                             RpgLinkGraphService graphService, LinkCompiler compiler,
                             EntitlementPolicy entitlements, RpgSkillTracer tracer) {
        this.catalog = catalog; this.repository = repository; this.graphService = graphService;
        this.compiler = compiler; this.entitlements = entitlements; this.tracer = tracer;
    }

    @Override public MutationResult equipSkill(UUID player, SkillSlot slot, SkillId skill) {
        String correlation = reference();
        trace(player, RpgTraceEventType.EQUIP_SKILL_REQUEST, correlation,
                details("skillSlot", slot.externalId(), "skillId", skill.value()));
        if (catalog.skill(skill).isEmpty()) return fail(player, correlation, RpgTraceEventType.COMPILE_FAILURE,
                ValidationCode.UNKNOWN_SKILL, "Unknown Skill: " + skill.value(), revision(player));
        Holder holder = holder(player);
        synchronized (holder) {
            EntitlementPolicy.EntitlementVerdict entitlement = entitlements.skill(holder.state, skill);
            if (!entitlement.allowed()) return fail(player, correlation, RpgTraceEventType.COMPILE_FAILURE,
                    ValidationCode.SOURCE_UNAVAILABLE, entitlement.reason(), holder.state.revision);
            MutationResult result = mutate(holder, player, correlation, candidate -> candidate.skill(slot, skill));
            if (result.success()) trace(player, RpgTraceEventType.EQUIP_SKILL_COMMITTED, correlation,
                    details("skillSlot", slot.externalId(), "skillId", skill.value(), "RPG revision", result.revision(),
                            "entitlementMode", entitlement.reason(), "validationResult", "PASS"));
            return result.success() ? withMessage(result, "Equipped " + catalog.skill(skill).orElseThrow().name() + " in " + slot.externalId() + ".\nCompile: PASS.") : result;
        }
    }

    @Override public MutationResult unequipSkill(UUID player, SkillSlot slot) {
        String correlation = reference();
        trace(player, RpgTraceEventType.UNEQUIP_SKILL_REQUEST, correlation, details("skillSlot", slot.externalId()));
        Holder holder = holder(player);
        synchronized (holder) {
            MutationResult result = mutate(holder, player, correlation, candidate -> {
                removeRoutesToSkill(candidate, slot);
                candidate.skill(slot, null);
            });
            if (result.success()) trace(player, RpgTraceEventType.UNEQUIP_SKILL_COMMITTED, correlation,
                    details("skillSlot", slot.externalId(), "validationResult", "PASS"));
            return result;
        }
    }

    @Override public MutationResult equipPassive(UUID player, PassiveSlot slot, PassiveId passive) {
        String correlation = reference();
        trace(player, RpgTraceEventType.EQUIP_PASSIVE_REQUEST, correlation,
                details("passiveSlot", slot.externalId(), "passiveId", passive.value()));
        if (catalog.passive(passive).isEmpty()) return fail(player, correlation, RpgTraceEventType.COMPILE_FAILURE,
                ValidationCode.UNKNOWN_PASSIVE, "Unknown Passive: " + passive.value(), revision(player));
        Holder holder = holder(player);
        synchronized (holder) {
            EntitlementPolicy.EntitlementVerdict entitlement = entitlements.passive(holder.state, passive);
            if (!entitlement.allowed()) return fail(player, correlation, RpgTraceEventType.COMPILE_FAILURE,
                    ValidationCode.NO_OWNED_COPY, entitlement.reason(), holder.state.revision);
            MutationResult result = mutate(holder, player, correlation, candidate -> candidate.passive(slot, passive));
            if (result.success()) trace(player, RpgTraceEventType.EQUIP_PASSIVE_COMMITTED, correlation,
                    details("passiveSlot", slot.externalId(), "passiveId", passive.value(), "RPG revision", result.revision(),
                            "entitlementMode", entitlement.reason(), "validationResult", "PASS"));
            return result.success() ? withMessage(result, "Equipped " + catalog.passive(passive).orElseThrow().name() + " in " + slot.externalId() + ".\nCompile: PASS.") : result;
        }
    }

    @Override public MutationResult unequipPassive(UUID player, PassiveSlot slot) {
        String correlation = reference();
        trace(player, RpgTraceEventType.UNEQUIP_PASSIVE_REQUEST, correlation, details("passiveSlot", slot.externalId()));
        Holder holder = holder(player);
        synchronized (holder) {
            MutationResult result = mutate(holder, player, correlation, candidate -> {
                candidate.linkEdges(graphService.candidateUnlinkSource(candidate, LinkNodeId.valueOf(slot.name())));
                candidate.passive(slot, null);
            });
            if (result.success()) trace(player, RpgTraceEventType.UNEQUIP_PASSIVE_COMMITTED, correlation,
                    details("passiveSlot", slot.externalId(), "validationResult", "PASS"));
            return result;
        }
    }

    @Override public MutationResult link(UUID player, LinkNodeId source, LinkNodeId target) {
        String correlation = reference();
        Holder holder = holder(player);
        synchronized (holder) {
            Map<String, Object> context = linkDetails(holder.state, source, target);
            trace(player, RpgTraceEventType.LINK_REQUEST, correlation, context);
            MutationResult result = mutate(holder, player, correlation,
                    candidate -> candidate.linkEdges(graphService.candidateLink(candidate, source, target)));
            if (result.success()) {
                context.put("validationResult", "PASS"); context.put("RPG revision", result.revision());
                trace(player, RpgTraceEventType.LINK_ACCEPTED, correlation, context);
                return withMessage(result, "Linked " + displayNode(holder.state, source) + " -> "
                        + displayNode(holder.state, target) + ".\nCompile: PASS.");
            }
            context.put("validationResult", "FAIL"); context.put("failureCode", result.code().name());
            context.put("failureMessage", result.message());
            context.put("compatibilityReason", result.message());
            trace(player, RpgTraceEventType.LINK_REJECTED, correlation, context);
            return new MutationResult(false, result.code(), "Cannot link " + displayNode(holder.state, source)
                    + " to " + displayNode(holder.state, target) + ".\n" + result.message(), correlation,
                    result.revision(), Map.of());
        }
    }

    @Override public MutationResult unlink(UUID player, EdgeId edge) {
        String correlation = reference(); Holder holder = holder(player);
        synchronized (holder) {
            MutationResult result = mutate(holder, player, correlation,
                    candidate -> candidate.linkEdges(graphService.candidateUnlinkEdge(candidate, edge.value())));
            if (result.success()) trace(player, RpgTraceEventType.UNLINK, correlation,
                    details("edgeId", edge.value(), "validationResult", "PASS", "RPG revision", result.revision()));
            return result;
        }
    }

    @Override public MutationResult unlinkSource(UUID player, LinkNodeId source) {
        String correlation = reference(); Holder holder = holder(player);
        synchronized (holder) {
            MutationResult result = mutate(holder, player, correlation,
                    candidate -> candidate.linkEdges(graphService.candidateUnlinkSource(candidate, source)));
            if (result.success()) trace(player, RpgTraceEventType.UNLINK, correlation,
                    details("sourceNodeId", source.externalId(), "validationResult", "PASS", "RPG revision", result.revision()));
            return result.success() ? withMessage(result, "Unlinked " + source.externalId() + ".\nCompile: PASS.") : result;
        }
    }

    @Override public CompilationResult compile(UUID player) {
        Holder holder = holder(player);
        synchronized (holder) { return compileTraced(player, holder.state, reference()); }
    }

    @Override public RpgLoadoutView getLoadout(UUID player) {
        Holder holder = holder(player);
        synchronized (holder) {
            CompilationResult compiled = compileTraced(player, holder.state, reference());
            GraphValidationResult graph = graphService.validate(holder.state);
            List<String> warnings = new ArrayList<>(holder.state.degradedReasons);
            if (!compiled.success()) warnings.add(compiled.code() + ": " + compiled.message());
            return new RpgLoadoutView(holder.state, compiled.plans(), graph.valid() ? graph.routes() : Map.of(), warnings);
        }
    }

    @Override public Map<LinkNodeId, CompatibilityResult> getCompatibleTargets(UUID player, LinkNodeId source) {
        Holder holder = holder(player);
        synchronized (holder) {
            Map<LinkNodeId, CompatibilityResult> result = new EnumMap<>(LinkNodeId.class);
            if (source.kind() == LinkNodeId.NodeKind.PASSIVE) {
                for (SkillSlot slot : SkillSlot.values()) {
                    LinkNodeId target = LinkNodeId.valueOf(slot.name());
                    result.put(target, graphService.getCompatibility(holder.state, source.passiveSlot(), slot));
                }
            }
            for (LinkNodeId target : LinkNodeId.values()) {
                if (target.kind() == LinkNodeId.NodeKind.PASSIVE || target == source || result.containsKey(target)) continue;
                RpgPlayerState candidate = holder.state.copy();
                try {
                    candidate.linkEdges(graphService.candidateLink(candidate, source, target));
                    GraphValidationResult validation = graphService.validate(candidate);
                    if (validation.valid()) result.put(target, CompatibilityResult.accepted(Set.of(target.kind().name())));
                    else result.put(target, CompatibilityResult.rejected(validation.firstIssue().code(),
                            validation.firstIssue().message(), Set.of(), Set.of(target.kind().name())));
                } catch (RuntimeException error) {
                    result.put(target, CompatibilityResult.rejected(ValidationCode.INVALID_REQUEST,
                            error.getMessage(), Set.of(), Set.of()));
                }
            }
            return Map.copyOf(result);
        }
    }

    @Override public MutationResult setDevelopmentAttribute(UUID player, RpgAttribute attribute, int rawValue) {
        if (rawValue < 0) throw new IllegalArgumentException("Raw attribute cannot be negative");
        String correlation = reference(); Holder holder = holder(player);
        synchronized (holder) {
            trace(player, RpgTraceEventType.ATTRIBUTE_SNAPSHOT, correlation,
                    details("operation", "DEV_SET_REQUEST", "attribute", attribute.name(), "raw", rawValue));
            MutationResult result = mutate(holder, player, correlation,
                    candidate -> candidate.attributes.put(attribute.name(), rawValue));
            if (result.success()) trace(player, RpgTraceEventType.ATTRIBUTE_SNAPSHOT, correlation,
                    details("operation", "DEV_SET_COMMITTED", "attribute", attribute.name(), "raw", rawValue,
                            "RPG revision", result.revision(), "validationResult", "PASS"));
            return result.success() ? withMessage(result, attribute + " raw value set to " + rawValue + ".") : result;
        }
    }

    @Override public MutationResult resetDevelopmentAttributes(UUID player) {
        String correlation = reference(); Holder holder = holder(player);
        synchronized (holder) {
            MutationResult result = mutate(holder, player, correlation, candidate -> {
                for (RpgAttribute attribute : RpgAttribute.values()) candidate.attributes.put(attribute.name(), 10);
            });
            if (result.success()) trace(player, RpgTraceEventType.ATTRIBUTE_SNAPSHOT, correlation,
                    details("operation", "DEV_RESET_COMMITTED", "raw", holder.state.attributes,
                            "RPG revision", result.revision(), "validationResult", "PASS"));
            return result.success() ? withMessage(result, "Development attributes reset to 10.") : result;
        }
    }

    private MutationResult mutate(Holder holder, UUID player, String correlation, Consumer<RpgPlayerState> mutation) {
        RpgPlayerState candidate = holder.state.copy();
        try { mutation.accept(candidate); }
        catch (RuntimeException error) {
            return fail(player, correlation, RpgTraceEventType.COMPILE_FAILURE, ValidationCode.INVALID_REQUEST,
                    error.getMessage(), holder.state.revision);
        }
        candidate.revision = holder.state.revision + 1;
        CompilationResult compiled = compileTraced(player, candidate, correlation);
        if (!compiled.success()) return MutationResult.failure(compiled.code(), compiled.message() + "\nTrace: " + correlation,
                correlation, holder.state.revision);
        try {
            repository.save(candidate);
            trace(player, RpgTraceEventType.SAVE, correlation,
                    details("RPG revision", candidate.revision, "schemaVersion", candidate.schemaVersion,
                            "validationResult", "PASS"));
        } catch (RuntimeException error) {
            return fail(player, correlation, RpgTraceEventType.COMPILE_FAILURE, ValidationCode.PERSISTENCE_FAILURE,
                    "RPG state was not changed because persistence failed: " + error.getMessage(), holder.state.revision);
        }
        holder.state = candidate;
        return new MutationResult(true, ValidationCode.ACCEPTED, "Compile: PASS.", correlation,
                candidate.revision, compiled.plans());
    }

    private CompilationResult compileTraced(UUID player, RpgPlayerState state, String correlation) {
        trace(player, RpgTraceEventType.COMPILE_BEGIN, correlation,
                details("RPG revision", state.revision, "schemaVersion", state.schemaVersion));
        CompilationResult result = compiler.compile(state);
        if (!result.success()) {
            trace(player, RpgTraceEventType.COMPILE_FAILURE, correlation,
                    details("RPG revision", state.revision, "validationResult", "FAIL",
                            "failureCode", result.code().name(), "failureMessage", result.message()));
            return result;
        }
        for (CompiledSkillPlan plan : result.plans().values()) {
            for (String stage : List.of("BASE_SKILL", "FAMILY_CONVERSION", "TARGETING", "GEOMETRY", "MULTIPLICITY",
                    "CONTINUATION_BUDGETS", "RESOURCE_COOLDOWN", "DAMAGE_HEALING", "VFX_SOUND")) {
                trace(player, RpgTraceEventType.COMPILE_STAGE, correlation,
                        planDetails(plan, stage, "PASS"));
            }
        }
        Map<String, Object> details = details("RPG revision", state.revision, "validationResult", "PASS",
                "compiledSkillCount", result.plans().size());
        details.put("plans", result.plans().values().stream().map(plan -> planDetails(plan, "FINAL", "PASS")).toList());
        trace(player, RpgTraceEventType.COMPILE_SUCCESS, correlation, details);
        return result;
    }

    private Holder holder(UUID player) {
        return states.computeIfAbsent(player, ignored -> load(player));
    }

    private Holder load(UUID player) {
        RpgPlayerStateRepository.LoadResult loaded = repository.load(player);
        RpgPlayerState state = loaded.state();
        List<String> loadWarnings = new ArrayList<>(loaded.warnings());
        for (String id : state.equippedSkills) if (id != null && catalog.skill(new SkillId(id)).isEmpty())
            loadWarnings.add("UNKNOWN_SKILL:" + id);
        for (String id : state.equippedPassives) if (id != null && catalog.passive(new PassiveId(id)).isEmpty())
            loadWarnings.add("UNKNOWN_PASSIVE:" + id);
        GraphValidationResult graph = graphService.validate(state);
        boolean recoveredGraph = !graph.valid();
        if (recoveredGraph) {
            loadWarnings.add("GRAPH_RECOVERED:" + graph.firstIssue().code() + ':' + graph.firstIssue().message());
            state.degradedReasons.addAll(loadWarnings);
            state.linkEdges(List.of());
            state.revision++;
            repository.save(state); // Atomic save retains the invalid graph as the .bak recovery source.
            trace(player, RpgTraceEventType.SAVE, reference(), details("RPG revision", state.revision,
                    "validationResult", "RECOVERED", "failureCode", graph.firstIssue().code().name()));
        } else if (loaded.migrated()) {
            repository.save(state);
        }
        if (loaded.migrated()) trace(player, RpgTraceEventType.MIGRATION, reference(),
                details("sourceSchemaVersion", loaded.sourceSchema(), "schemaVersion", state.schemaVersion,
                        "validationResult", "PASS"));
        if (!recoveredGraph) state.degradedReasons.addAll(loadWarnings.stream()
                .filter(warning -> warning.startsWith("UNKNOWN_")).toList());
        trace(player, RpgTraceEventType.LOAD, reference(), details("RPG revision", state.revision,
                "schemaVersion", state.schemaVersion, "warnings", loadWarnings, "validationResult", "PASS"));
        return new Holder(state);
    }

    private void removeRoutesToSkill(RpgPlayerState state, SkillSlot slot) {
        LinkNodeId targetSkill = LinkNodeId.valueOf(slot.name());
        List<LinkEdge> edges = state.linkEdges();
        Map<LinkNodeId, LinkNodeId> outgoing = new EnumMap<>(LinkNodeId.class);
        edges.forEach(edge -> outgoing.put(edge.sourceNodeId(), edge.targetNodeId()));
        Set<LinkNodeId> removeSources = new java.util.HashSet<>();
        for (LinkNodeId source : outgoing.keySet()) {
            LinkNodeId current = source;
            Set<LinkNodeId> seen = new java.util.HashSet<>();
            while (outgoing.containsKey(current) && seen.add(current)) {
                current = outgoing.get(current);
                if (current == targetSkill) { removeSources.addAll(seen); break; }
            }
        }
        state.linkEdges(edges.stream().filter(edge -> !removeSources.contains(edge.sourceNodeId())).toList());
    }

    private Map<String, Object> linkDetails(RpgPlayerState state, LinkNodeId source, LinkNodeId target) {
        Map<String, Object> result = details("sourceNodeId", source.externalId(), "targetNodeId", target.externalId());
        if (source.kind() == LinkNodeId.NodeKind.PASSIVE) state.passive(source.passiveSlot()).ifPresent(id -> {
            result.put("passiveSlot", source.externalId()); result.put("passiveId", id.value());
        });
        if (target.kind() == LinkNodeId.NodeKind.SKILL) state.skill(target.skillSlot()).ifPresent(id -> {
            result.put("skillSlot", target.externalId()); result.put("skillId", id.value());
            catalog.skill(id).ifPresent(skill -> result.put("baseSkillTags", skill.linkCompatibilityTags()));
        });
        if (source.kind() == LinkNodeId.NodeKind.PASSIVE && target.kind() == LinkNodeId.NodeKind.SKILL) {
            CompatibilityResult compatibility = graphService.getCompatibility(state, source.passiveSlot(), target.skillSlot());
            result.put("compatibilityVerdict", compatibility.accepted() ? "ACCEPTED" : "REJECTED");
            result.put("compatibilityReason", compatibility.message());
            result.put("required", compatibility.required());
            result.put("actual", compatibility.actual());
        }
        return result;
    }

    private Map<String, Object> planDetails(CompiledSkillPlan plan, String stage, String result) {
        return details("skillSlot", plan.skillSlot().externalId(), "skillId", plan.skillId().value(),
                "passiveIds", plan.passiveOrder().stream().map(PassiveId::value).toList(),
                "graphRoute", plan.graphRoutes().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        entry -> entry.getKey().value(),
                        entry -> entry.getValue().stream().map(LinkNodeId::externalId).toList(),
                        (left, right) -> left, LinkedHashMap::new)), "compilerStage", stage,
                "modifierOrder", plan.passiveOrder().stream().map(PassiveId::value).toList(),
                "compiledTags", plan.finalTags(), "compiledFamily", plan.finalFamily(),
                "compiledGeometry", plan.geometryModifiers(), "compiledMultiplicity", plan.multiplicity(),
                "compiledContinuation", plan.continuation(), "compiledResourceCost", plan.resourceCooldownModifiers(),
                "compiledCooldown", plan.resourceCooldownModifiers(), "compiledPowerModifiers", plan.powerModifiers(),
                "kernelModifiers", plan.kernelModifiers(),
                "triggerHooks", plan.triggerHooks(), "VfxRecipeId", plan.vfxRecipeId(),
                "SoundRecipeId", plan.soundRecipeId(), "recursionSpawnBudgets", plan.safetyBudgets(),
                "validationResult", result);
    }

    private String displayNode(RpgPlayerState state, LinkNodeId node) {
        return switch (node.kind()) {
            case SKILL -> node.externalId() + state.skill(node.skillSlot()).flatMap(catalog::skill)
                    .map(skill -> " (" + skill.name() + ')').orElse("");
            case PASSIVE -> node.externalId() + state.passive(node.passiveSlot()).flatMap(catalog::passive)
                    .map(passive -> " (" + passive.name() + ')').orElse("");
            case JOINT -> node.externalId();
        };
    }

    private MutationResult fail(UUID player, String correlation, RpgTraceEventType event, ValidationCode code,
                                String message, long revision) {
        trace(player, event, correlation, details("validationResult", "FAIL", "failureCode", code.name(), "failureMessage", message));
        return MutationResult.failure(code, message + "\nTrace: " + correlation, correlation, revision);
    }

    private long revision(UUID player) { return holder(player).state.revision; }

    private void trace(UUID player, RpgTraceEventType type, String correlation, Map<String, ?> details) {
        try { tracer.trace(RpgTraceRecord.create(player, type, correlation, details)); }
        catch (Throwable ignored) { /* Diagnostics are deliberately non-authoritative. */ }
    }

    private static Map<String, Object> details(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) result.put(String.valueOf(values[index]), values[index + 1]);
        return result;
    }

    private static MutationResult withMessage(MutationResult result, String message) {
        return new MutationResult(result.success(), result.code(), message, result.traceReference(), result.revision(), result.compiledPlans());
    }

    private static String reference() { return UUID.randomUUID().toString().substring(0, 12); }

    private static final class Holder { private RpgPlayerState state; private Holder(RpgPlayerState state) { this.state = state; } }
}
