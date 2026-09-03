package com.inigmasgames.persistentnpcs.autonomy;

import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** E8 authored semantic plans. Skills contain action IDs and evidence predicates, never code. */
public final class NpcSkillLibrary {
    public enum FailurePolicy { REPLAN, ABORT, WAIT }

    public record Step(String actionId, Map<String, String> parameters,
            Set<String> requiredEvidence, FailurePolicy failurePolicy) {
        public Step {
            actionId = normalize(actionId);
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
            requiredEvidence = requiredEvidence == null ? Set.of()
                    : requiredEvidence.stream().map(NpcSkillLibrary::normalize)
                            .collect(java.util.stream.Collectors.toUnmodifiableSet());
            failurePolicy = failurePolicy == null ? FailurePolicy.ABORT : failurePolicy;
            String payload = actionId + parameters;
            String unsafe = payload.toLowerCase(Locale.ROOT);
            if (actionId.isBlank() || payload.contains("{") && unsafe.contains("class ")
                    || unsafe.contains("runtime.getruntime") || unsafe.contains("processbuilder")
                    || unsafe.contains("import ") || unsafe.contains("package ")) {
                throw new IllegalArgumentException("skills accept semantic actions, not code");
            }
        }
    }

    public record Skill(UUID skillId, String name, Set<String> roles,
            Set<String> capabilities, Set<String> preconditions, List<Step> steps,
            Set<String> effects, List<String> failureModes, String provenance) {
        public Skill {
            name = name == null ? "" : name.strip();
            if (name.isBlank() || steps == null || steps.isEmpty())
                throw new IllegalArgumentException("named skill with steps required");
            skillId = skillId == null ? stableId(name) : skillId;
            roles = normalized(roles); capabilities = normalized(capabilities);
            preconditions = normalized(preconditions); effects = normalized(effects);
            steps = List.copyOf(steps);
            failureModes = failureModes == null ? List.of() : List.copyOf(failureModes);
            provenance = provenance == null || provenance.isBlank() ? "AUTHORED:E8" : provenance;
        }
    }

    public record Metrics(long selected, long completed, long failed) { }

    private final Map<UUID, Skill> skills = new LinkedHashMap<>();
    private final Map<UUID, long[]> metrics = new HashMap<>();

    public NpcSkillLibrary() { authoredDefaults().forEach(this::register); }

    public synchronized void register(Skill skill) {
        if (skills.putIfAbsent(skill.skillId(), skill) != null)
            throw new IllegalArgumentException("duplicate skill " + skill.skillId());
    }

    public synchronized List<Skill> eligible(NpcProfile profile, Set<String> evidence,
            boolean higherObligation) {
        if (profile == null || higherObligation) return List.of();
        Set<String> known = normalized(evidence);
        return skills.values().stream()
                .filter(s -> s.roles().isEmpty() || s.roles().stream().anyMatch(profile::hasRole))
                .filter(s -> s.capabilities().stream().allMatch(profile::hasCapability))
                .filter(s -> known.containsAll(s.preconditions())).toList();
    }

    public synchronized Optional<Skill> byName(String name) {
        return skills.values().stream().filter(s -> s.name().equalsIgnoreCase(name)).findFirst();
    }

    public synchronized void selected(UUID id) { metrics.computeIfAbsent(id, k -> new long[3])[0]++; }
    public synchronized void outcome(UUID id, boolean success) {
        metrics.computeIfAbsent(id, k -> new long[3])[success ? 1 : 2]++;
    }
    public synchronized Metrics metrics(UUID id) {
        long[] m = metrics.getOrDefault(id, new long[3]); return new Metrics(m[0], m[1], m[2]);
    }
    public synchronized List<Skill> all() { return List.copyOf(skills.values()); }

    private static List<Skill> authoredDefaults() {
        return List.of(
            skill("INSPECT_UNUSUAL_ITEM", Set.of(), Set.of("INSPECT_ITEM"),
                    Set.of("ITEM_PERCEIVED"), step("INSPECT_ITEM", "itemId", "$itemId")),
            skill("HUNT_GAME", Set.of("HUNTER"), Set.of("GO_TO", "PICK_UP_ITEM"),
                    Set.of("PREY_LOCATED"), step("GO_TO", "target", "$preyLocation")),
            skill("SELL_EQUIPMENT", Set.of("HUNTER", "MERCHANT"),
                    Set.of("GIVE_ITEM"), Set.of("ITEM_OWNED", "MERCHANT_KNOWN"),
                    step("GO_TO", "target", "$merchantLocation"),
                    step("TRANSACTION_ITEM", "itemId", "$itemId")),
            skill("MERCHANT_APPRAISAL", Set.of("MERCHANT"), Set.of("INSPECT_ITEM"),
                    Set.of("ITEM_PRESENTED"), step("INSPECT_ITEM", "itemId", "$itemId")),
            skill("BUY_TOOL_OR_FOOD", Set.of(), Set.of("TAKE_ITEM"),
                    Set.of("MERCHANT_KNOWN", "PURCHASE_NEEDED"),
                    step("GO_TO", "target", "$merchantLocation"),
                    step("TRANSACTION_ITEM", "itemId", "$itemId")),
            skill("RETURN_HOME", Set.of(), Set.of("GO_TO"), Set.of("HOME_KNOWN"),
                    step("GO_TO", "target", "$home")),
            skill("DELIVER_ITEM", Set.of(), Set.of("GIVE_ITEM"),
                    Set.of("ITEM_OWNED", "RECIPIENT_KNOWN"),
                    step("GO_TO", "target", "$recipientLocation"),
                    step("GIVE_ITEM", "itemId", "$itemId")),
            skill("INVESTIGATE_ROUTE", Set.of(), Set.of("GO_TO", "INSPECT_ITEM"),
                    Set.of("ROUTE_KNOWN"), step("GO_TO", "target", "$routeLocation")));
    }

    private static Skill skill(String name, Set<String> roles, Set<String> capabilities,
            Set<String> preconditions, Step... steps) {
        return new Skill(stableId(name), name, roles, capabilities, preconditions,
                List.of(steps), Set.of(), List.of("NOT_ELIGIBLE", "STALE_EVIDENCE",
                "UNREACHABLE", "RESOURCE_PRESSURE"), "AUTHORED:E8");
    }
    private static Step step(String action, String key, String value) {
        return new Step(action, Map.of(key, value), Set.of(), FailurePolicy.REPLAN);
    }
    private static UUID stableId(String name) {
        return UUID.nameUUIDFromBytes(("IMMERSIVE_NPC_SKILL:" + normalize(name))
                .getBytes(StandardCharsets.UTF_8));
    }
    private static Set<String> normalized(Collection<String> values) {
        if (values == null) return Set.of();
        return values.stream().filter(Objects::nonNull).map(NpcSkillLibrary::normalize)
                .filter(v -> !v.isBlank()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    private static String normalize(String value) {
        return value == null ? "" : value.strip().replaceAll("[\\s-]+", "_")
                .toUpperCase(Locale.ROOT);
    }
}
