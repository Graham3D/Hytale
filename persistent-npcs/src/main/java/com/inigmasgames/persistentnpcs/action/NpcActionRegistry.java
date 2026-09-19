package com.inigmasgames.persistentnpcs.action;

import com.inigmasgames.persistentnpcs.llm.LlmToolDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.Set;

public final class NpcActionRegistry {
    private final Map<String, NpcActionDefinition> definitions = new LinkedHashMap<>();
    private final java.util.concurrent.atomic.LongAdder successfulExecutions =
            new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder failedExecutions =
            new java.util.concurrent.atomic.LongAdder();

    public synchronized void register(NpcActionDefinition definition) {
        String id = normalize(definition.id());
        if (id.isBlank() || definitions.putIfAbsent(id, definition) != null) {
            throw new IllegalArgumentException("Duplicate or blank NPC action: " + id);
        }
    }

    public synchronized List<LlmToolDefinition> toolsFor(NpcActionContext context) {
        return definitions.values().stream()
                .filter(definition -> definition.isEligible(context))
                .map(definition -> new LlmToolDefinition(definition.id(),
                        definition.descriptionForLlm(), definition.parameterSchema()))
                .toList();
    }

    /** Keeps small local models reliable by exposing only request-relevant tools. */
    public synchronized List<LlmToolDefinition> toolsFor(
            NpcActionContext context, String playerMessage) {
        String message = playerMessage == null ? ""
                : playerMessage.toLowerCase(Locale.ROOT);
        Set<String> relevant = relevantIds(message);
        if (relevant.isEmpty()) {
            return List.of();
        }
        return definitions.values().stream()
                .filter(definition -> relevant.contains(normalize(definition.id())))
                .filter(definition -> definition.isEligible(context))
                .map(definition -> new LlmToolDefinition(definition.id(),
                        definition.descriptionForLlm(), definition.parameterSchema()))
                .toList();
    }

    public CompletableFuture<NpcActionResult> execute(
            NpcActionRequest untrustedRequest, NpcActionContext context) {
        NpcActionRequest request = untrustedRequest.normalized();
        NpcActionResult validation = validate(request, context);
        if (!validation.success()) return CompletableFuture.completedFuture(validation);
        NpcActionDefinition definition;
        synchronized (this) {
            definition = definitions.get(request.id());
        }
        return definition.executor().execute(request, context)
                .exceptionally(failure -> NpcActionResult.failure("EXECUTION_ERROR",
                        "Action " + request.id() + " failed: " + compact(failure.getMessage())))
                .thenApply(result -> {
                    (result.success() ? successfulExecutions : failedExecutions).increment();
                    return result;
                });
    }

    /** Fresh, non-mutating validation immediately before an action is committed/executed. */
    public NpcActionResult validate(NpcActionRequest untrustedRequest, NpcActionContext context) {
        NpcActionRequest request = untrustedRequest.normalized();
        if (request.actorStableId() != null
                && !request.actorStableId().equals(context.profile().id())) {
            return NpcActionResult.failure("ACTOR_MISMATCH",
                    "The action actor does not match the responding NPC.");
        }
        NpcActionDefinition definition;
        synchronized (this) {
            definition = definitions.get(request.id());
        }
        if (definition == null) return NpcActionResult.failure(
                "UNKNOWN_ACTION", "The server rejected unknown action " + request.id() + ".");
        if (!definition.isEligible(context)) return NpcActionResult.failure(
                "NOT_ELIGIBLE", "The NPC lacks the role/capability for " + request.id() + ".");
        return definition.validator().validate(request, context);
    }

    public synchronized List<String> ids() {
        return List.copyOf(definitions.keySet());
    }

    public long successfulExecutionCount() {
        return successfulExecutions.sum();
    }

    public long failedExecutionCount() {
        return failedExecutions.sum();
    }

    private static String normalize(String id) {
        return id == null ? "" : id.strip().replaceAll("[\\s-]+", "_")
                .replaceAll("_+", "_").toUpperCase(Locale.ROOT);
    }

    private static Set<String> relevantIds(String message) {
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        boolean stop = contains(message, "stop following", "wait here", "stay here",
                "hold position", "you can stop");
        if (!stop && contains(message, "follow", "come with", "come along",
                "stay with me", "travel with", "escort")) {
            ids.add("FOLLOW_PLAYER");
        }
        if (stop) {
            ids.add("STOP_FOLLOWING");
        } else if (contains(message, "wait for", "wait until")) {
            ids.add("WAIT");
        }
        if (contains(message, "go to", "walk to", "move to", "patrol", "wander")) {
            ids.add("GO_TO");
            ids.add("PATROL");
            ids.add("WANDER");
        }
        if (contains(message, "flee", "run away", "get away")) {
            ids.add("FLEE");
        }
        if (contains(message, "bring", "fetch", "pick up", "collect")) {
            ids.add("BRING_ITEM");
            ids.add("PICK_UP_ITEM");
        }
        if (contains(message, "give", "hand me", "take this", "accept this")) {
            ids.add("GIVE_ITEM");
            ids.add("TAKE_ITEM");
        }
        if (contains(message, "drop", "put down")) {
            ids.add("DROP_ITEM");
        }
        if (contains(message, "inspect", "examine", "what am i holding", "what is this")) {
            ids.add("INSPECT_ITEM");
        }
        if (contains(message, "equip", "wear", "put on")) {
            ids.add("EQUIP_ITEM");
        }
        if (contains(message, "unequip", "take off", "remove armor")) {
            ids.add("UNEQUIP_ITEM");
        }
        if (contains(message, "craft", "make", "forge", "cook", "process", "repair")) {
            ids.add("CRAFT_ITEM");
            ids.add("COOK_ITEM");
            ids.add("PROCESS_ITEM");
        }
        if (contains(message, "meet", "tonight", "tomorrow", "at ")) {
            ids.add("SCHEDULE_MEETING");
            ids.add("SCHEDULE_TASK");
        }
        if (contains(message, "want to go", "go together", "come with me",
                "accompany me", "go on a date", "meet at", "meet me at",
                "can we go", "take me to", "show me your", "lead me to")) {
            ids.add("CREATE_SHARED_PLAN");
        }
        if (contains(message, "take me to", "lead me to", "guide me to",
                "bring me to", "show me where")) {
            ids.add("GUIDE_PLAYER_TO_NPC");
        }
        if (contains(message, "owe", "debt", "obligation", "tab")) {
            ids.add("CREATE_OBLIGATION");
            ids.add("ADD_TO_OBLIGATION");
            ids.add("FORGIVE_OBLIGATION");
        }
        if (contains(message, "trust", "relationship", "forgive", "afraid", "hostile")) {
            ids.add("ADJUST_RELATIONSHIP");
        }
        return Set.copyOf(ids);
    }

    private static boolean contains(String message, String... needles) {
        return java.util.Arrays.stream(needles).anyMatch(message::contains);
    }

    private static String compact(String value) {
        String text = value == null ? "unknown error" : value.replaceAll("\\s+", " ").trim();
        return text.length() <= 240 ? text : text.substring(0, 240) + "...";
    }
}
