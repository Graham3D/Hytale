package com.inigmasgames.persistentnpcs.config;

public record ModelTierConfig(
        String endpoint,
        String model,
        String apiKey,
        String reasoningEffort) {

    public boolean configured() {
        return model != null && !model.isBlank();
    }
}
