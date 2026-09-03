package com.inigmasgames.persistentnpcs.llm;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.conversation.contract.TurnExecutionPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record LlmRequest(
        UUID conversationId,
        UUID npcId,
        UUID playerId,
        List<ChatMessage> messages,
        List<LlmToolDefinition> tools,
        JsonObject responseFormat,
        Double temperatureOverride,
        Integer maxTokensOverride,
        UUID providerRequestId,
        LlmExecutionPolicy executionPolicy,
        TurnExecutionPlan turnExecutionPlan) {

    public LlmRequest {
        messages = List.copyOf(messages == null ? List.of() : messages);
        tools = List.copyOf(tools == null ? List.of() : tools);
        responseFormat = responseFormat == null ? null : responseFormat.deepCopy();
        providerRequestId = providerRequestId == null ? conversationId : providerRequestId;
        executionPolicy = executionPolicy == null
                ? LlmExecutionPolicy.unspecified() : executionPolicy;
    }

    public LlmRequest(UUID conversationId, UUID npcId, UUID playerId,
            List<ChatMessage> messages, List<LlmToolDefinition> tools,
            JsonObject responseFormat, Double temperatureOverride,
            Integer maxTokensOverride, UUID providerRequestId,
            LlmExecutionPolicy executionPolicy) {
        this(conversationId, npcId, playerId, messages, tools, responseFormat,
                temperatureOverride, maxTokensOverride, providerRequestId,
                executionPolicy, null);
    }

    public LlmRequest(UUID conversationId, UUID npcId, UUID playerId,
            List<ChatMessage> messages, List<LlmToolDefinition> tools,
            JsonObject responseFormat, Double temperatureOverride,
            Integer maxTokensOverride, UUID providerRequestId) {
        this(conversationId, npcId, playerId, messages, tools, responseFormat,
                temperatureOverride, maxTokensOverride, providerRequestId,
                LlmExecutionPolicy.unspecified(), null);
    }

    public LlmRequest(UUID conversationId, UUID npcId, UUID playerId,
            List<ChatMessage> messages, List<LlmToolDefinition> tools,
            JsonObject responseFormat, Double temperatureOverride,
            Integer maxTokensOverride) {
        this(conversationId, npcId, playerId, messages, tools, responseFormat,
                temperatureOverride, maxTokensOverride, conversationId);
    }

    public LlmRequest(
            UUID conversationId,
            UUID npcId,
            UUID playerId,
            List<ChatMessage> messages,
            List<LlmToolDefinition> tools) {
        this(conversationId, npcId, playerId, messages, tools, null, null, null);
    }

    public LlmRequest(
            UUID conversationId,
            UUID npcId,
            UUID playerId,
            List<ChatMessage> messages) {
        this(conversationId, npcId, playerId, messages, List.of());
    }

    /** Adds an OpenAI-compatible structured-output contract for this request only. */
    public LlmRequest constrained(JsonObject format, double temperature) {
        // NPC_DECISION includes IDs and action metadata, so it needs more room than the
        // configured lexical-dialogue limit. Keep the bound small enough that a malformed
        // local generation cannot monopolize the same GPU that is rendering Hytale.
        return constrained(format, temperature, 256);
    }

    public LlmRequest constrained(JsonObject format, double temperature, int maxTokens) {
        return new LlmRequest(conversationId, npcId, playerId, messages, List.of(), format,
                Math.max(0.0, Math.min(2.0, temperature)),
                Math.max(32, Math.min(1024, maxTokens)), providerRequestId, executionPolicy,
                turnExecutionPlan);
    }

    /**
     * Adds authoritative system guidance without creating a second system role.
     *
     * <p>Some OpenAI-compatible templates, including the installed Qwen 3.5 Ollama
     * template, require exactly one system message at the beginning of the request.
     * Prompt layers must therefore be composed into that message instead of inserted
     * as additional system turns.</p>
     */
    public LlmRequest withSystemInstruction(String instruction) {
        List<ChatMessage> combined = new ArrayList<>(messages);
        if (instruction != null && !instruction.isBlank()) {
            combined.add(new ChatMessage("system", instruction.strip()));
        }
        return new LlmRequest(conversationId, npcId, playerId,
                canonicalSystemMessages(combined), tools, responseFormat,
                temperatureOverride, maxTokensOverride, providerRequestId, executionPolicy,
                turnExecutionPlan);
    }

    /** Replaces inherited system context while preserving the current user/history messages. */
    public LlmRequest withSystemReplacement(String instruction) {
        List<ChatMessage> replaced = new ArrayList<>();
        if (instruction != null && !instruction.isBlank()) {
            replaced.add(new ChatMessage("system", instruction.strip()));
        }
        for (ChatMessage message : messages) {
            if (message != null && !"system".equalsIgnoreCase(message.role())) {
                replaced.add(message);
            }
        }
        return new LlmRequest(conversationId, npcId, playerId, replaced, tools,
                responseFormat, temperatureOverride, maxTokensOverride, providerRequestId,
                executionPolicy, turnExecutionPlan);
    }

    public LlmRequest withProviderRequestId(UUID requestId) {
        return new LlmRequest(conversationId, npcId, playerId, messages, tools,
                responseFormat, temperatureOverride, maxTokensOverride, requestId,
                executionPolicy, turnExecutionPlan);
    }

    public LlmRequest withExecutionPolicy(LlmExecutionPolicy policy) {
        return new LlmRequest(conversationId, npcId, playerId, messages, tools,
                responseFormat, temperatureOverride, maxTokensOverride, providerRequestId,
                policy, turnExecutionPlan);
    }

    public LlmRequest withGenerationParameters(double temperature, int maximumTokens) {
        return new LlmRequest(conversationId, npcId, playerId, messages, tools,
                responseFormat, Math.max(0.0, Math.min(2.0, temperature)),
                Math.max(32, Math.min(1024, maximumTokens)), providerRequestId,
                executionPolicy, turnExecutionPlan);
    }

    public LlmRequest withTurnExecutionPlan(TurnExecutionPlan plan) {
        return new LlmRequest(conversationId, npcId, playerId, messages, tools,
                responseFormat, temperatureOverride, maxTokensOverride, providerRequestId,
                executionPolicy, java.util.Objects.requireNonNull(plan, "plan"));
    }

    /** Returns wire-safe messages with one leading system message at most. */
    public List<ChatMessage> canonicalMessages() {
        return canonicalSystemMessages(messages);
    }

    static List<ChatMessage> canonicalSystemMessages(List<ChatMessage> source) {
        StringBuilder system = new StringBuilder();
        List<ChatMessage> ordinary = new ArrayList<>();
        for (ChatMessage message : source == null ? List.<ChatMessage>of() : source) {
            if (message != null && "system".equalsIgnoreCase(message.role())) {
                if (message.content() != null && !message.content().isBlank()) {
                    if (!system.isEmpty()) system.append("\n\n");
                    system.append(message.content().strip());
                }
            } else if (message != null) {
                ordinary.add(message);
            }
        }
        if (!system.isEmpty()) {
            ordinary.add(0, new ChatMessage("system", system.toString()));
        }
        return List.copyOf(ordinary);
    }
}
