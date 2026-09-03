package com.inigmasgames.persistentnpcs.conversation.contract;

import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import java.util.ArrayList;
import java.util.List;

/** Deterministic final prompt ceiling enforcement after section-level context routing. */
public final class ContractMessagePruner {
    private static final String MARKER = "\n[ORBIS_CONTEXT_PRUNED_TO_ROUTE_BUDGET]\n";
    private ContractMessagePruner() { }

    public static LlmRequest prune(LlmRequest request, ContextProfile profile) {
        int ceilingCharacters = Math.max(256, profile.promptTokenCeiling() * 4 - 96);
        List<ChatMessage> messages = request.canonicalMessages();
        int total = messages.stream().mapToInt(value -> value.content() == null
                ? 0 : value.content().length()).sum();
        if (total <= ceilingCharacters) return request;
        ArrayList<ChatMessage> result = new ArrayList<>(messages);
        for (int index = 0; index < result.size(); index++) {
            ChatMessage message = result.get(index);
            if (!"system".equalsIgnoreCase(message.role())) continue;
            String sectioned = sectionAware(message.content(), profile, ceilingCharacters);
            if (!sectioned.equals(message.content())) {
                result.set(index, new ChatMessage(message.role(), sectioned));
            }
        }
        total = result.stream().mapToInt(value -> value.content() == null
                ? 0 : value.content().length()).sum();
        if (total <= ceilingCharacters) return copy(request, result);
        int excess = total - ceilingCharacters;
        for (int index = 0; index < result.size() && excess > 0; index++) {
            ChatMessage message = result.get(index);
            if (!"system".equalsIgnoreCase(message.role())) continue;
            String value = message.content() == null ? "" : message.content();
            int target = Math.max(240, value.length() - excess - MARKER.length());
            if (target >= value.length()) continue;
            int head = Math.max(120, target * 2 / 3);
            int tail = Math.max(80, target - head);
            String compact = value.substring(0, Math.min(head, value.length())) + MARKER
                    + value.substring(Math.max(head, value.length() - tail));
            excess -= Math.max(0, value.length() - compact.length());
            result.set(index, new ChatMessage(message.role(), compact));
        }
        if (excess > 0) {
            // Preserve the latest user request verbatim; trim oldest non-system history first.
            for (int index = 0; index < result.size() - 1 && excess > 0; index++) {
                ChatMessage message = result.get(index);
                if ("system".equalsIgnoreCase(message.role())) continue;
                String value = message.content() == null ? "" : message.content();
                int remove = Math.min(excess, Math.max(0, value.length() - 48));
                result.set(index, new ChatMessage(message.role(),
                        "[older turn pruned] " + value.substring(remove)));
                excess -= remove;
            }
        }
        return copy(request, result);
    }

    private static LlmRequest copy(LlmRequest request, List<ChatMessage> messages) {
        return new LlmRequest(request.conversationId(), request.npcId(), request.playerId(),
                messages, request.tools(), request.responseFormat(), request.temperatureOverride(),
                request.maxTokensOverride(), request.providerRequestId(),
                request.executionPolicy(), request.turnExecutionPlan());
    }

    private static String sectionAware(String source, ContextProfile profile, int ceiling) {
        if (source == null || source.length() <= ceiling) return source == null ? "" : source;
        String[] markers = {
            "CURRENT PLAYER MESSAGE:",
            "CURRENT_WORLD_STATE (authoritative Hytale perception only):",
            "CONTENT EXISTENCE VALIDATION:",
            "PLAYER-PROVIDED CLAIM (not authoritative unless confirmed above):",
            "AVAILABLE/RELEVANT ITEMS (bounded, not the full registry):",
            "RECENT CONVERSATION (same player + NPC + session, oldest to newest;",
            "RECENT INVALIDATED/FAILED INTENTS (session-only; do not repeat):",
            "VALIDATED_ACTIVE_TASK (server-confirmed current execution only):",
            "VALIDATED_QUEST (server-confirmed ACTIVE quest only):",
            "VALIDATED_SHARED_PLAN (persistent purpose/participants/time/status):",
            "MEMORY (strictly filtered; a remembered claim is not current perception):",
            "RELATIONSHIP (deterministic state):",
            "PROFILE/BACKSTORY (authored character information, not current events):",
            "ROLE/CAPABILITIES (background and action eligibility only; not a default topic):",
            "PROPOSED_PLAN:", "FICTIONAL_STORY:", "OPTIONAL DIRECTOR CONTEXT:"
        };
        ArrayList<Integer> starts = new ArrayList<>();
        ArrayList<String> found = new ArrayList<>();
        for (String marker : markers) {
            int start = source.indexOf(marker);
            if (start >= 0) { starts.add(start); found.add(marker); }
        }
        if (starts.isEmpty()) return source;
        // Markers are declared in prompt order; keep a bounded policy preamble.
        StringBuilder result = new StringBuilder(source.substring(0,
                Math.min(starts.getFirst(), 800)).strip()).append(MARKER);
        for (int index = 0; index < found.size(); index++) {
            String marker = found.get(index);
            if (!allowed(marker, profile)) {
                result.append("\n\n").append(marker)
                        .append(marker.startsWith("OPTIONAL DIRECTOR")
                                ? "\nNone. No Director framing is injected into this dialogue turn."
                                : "\nOmitted by the immutable route context contract.");
                continue;
            }
            int start = starts.get(index);
            int end = index + 1 < starts.size() ? starts.get(index + 1) : source.length();
            int cap = sectionCharacterCap(marker, profile);
            String section = source.substring(start, end).strip();
            if (section.length() > cap) section = section.substring(0, cap).strip()
                    + "\n[section pruned]";
            result.append("\n\n").append(section);
        }
        String compact = result.toString();
        return compact.length() <= ceiling ? compact : compact.substring(0, ceiling);
    }

    private static boolean allowed(String marker, ContextProfile profile) {
        if (marker.startsWith("CURRENT PLAYER") || marker.startsWith("CONTENT EXISTENCE")
                || marker.startsWith("PLAYER-PROVIDED")) return true;
        if (marker.startsWith("CURRENT_WORLD")) return profile.allowedSections().contains("SEMANTIC_WORLD");
        if (marker.startsWith("AVAILABLE")) return profile.allowedSections().contains("ACTIONS");
        if (marker.startsWith("RECENT CONVERSATION") || marker.startsWith("RECENT INVALIDATED"))
            return profile.allowedSections().contains("RECENT_CONVERSATION");
        if (marker.startsWith("VALIDATED_ACTIVE_TASK")) return profile.allowedSections().contains("TASKS");
        if (marker.startsWith("VALIDATED_QUEST")) return profile.allowedSections().contains("OBLIGATIONS");
        if (marker.startsWith("VALIDATED_SHARED_PLAN")) return profile.allowedSections().contains("SHARED_PLANS");
        if (marker.startsWith("MEMORY")) return profile.allowedSections().contains("MEMORIES");
        if (marker.startsWith("RELATIONSHIP")) return profile.allowedSections().contains("PLAYER_RELATIONSHIP")
                || profile.allowedSections().contains("RELATIONSHIPS");
        if (marker.startsWith("PROFILE")) return profile.allowedSections().contains("PROFILE");
        if (marker.startsWith("ROLE")) return profile.allowedSections().contains("ACTIONS");
        return profile.allowedSections().contains("GOALS") || profile.allowedSections().contains("TASKS");
    }

    private static int sectionCharacterCap(String marker, ContextProfile profile) {
        String key = marker.startsWith("CURRENT_WORLD") ? "SEMANTIC_WORLD"
                : marker.startsWith("RECENT") ? "RECENT_CONVERSATION"
                : marker.startsWith("MEMORY") ? "MEMORIES"
                : marker.startsWith("RELATIONSHIP") ? "RELATIONSHIPS"
                : marker.startsWith("PROFILE") ? "PROFILE"
                : marker.startsWith("VALIDATED_ACTIVE_TASK") ? "TASKS"
                : marker.startsWith("VALIDATED_SHARED_PLAN") ? "SHARED_PLANS"
                : marker.startsWith("AVAILABLE") || marker.startsWith("ROLE") ? "ACTIONS"
                : "";
        int tokens = profile.sectionTokenCeilings().getOrDefault(key, 140);
        return Math.max(160, tokens * 4);
    }
}
