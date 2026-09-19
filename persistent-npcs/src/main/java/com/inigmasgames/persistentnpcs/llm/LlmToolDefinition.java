package com.inigmasgames.persistentnpcs.llm;

import com.google.gson.JsonObject;

public record LlmToolDefinition(String type, Function function) {
    public LlmToolDefinition(String name, String description, JsonObject parameters) {
        this("function", new Function(name, description, parameters));
    }

    public record Function(String name, String description, JsonObject parameters) {
    }
}
