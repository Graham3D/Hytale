package com.inigmasgames.persistentnpcs.llm;

public record ChatMessage(
        String role,
        String content,
        java.util.List<LlmToolCall> tool_calls,
        String tool_call_id) {

    public ChatMessage(String role, String content) {
        this(role, content, null, null);
    }
}
