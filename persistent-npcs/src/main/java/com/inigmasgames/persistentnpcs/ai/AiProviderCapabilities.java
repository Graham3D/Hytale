package com.inigmasgames.persistentnpcs.ai;

import java.util.Set;

public record AiProviderCapabilities(
        boolean streaming,
        boolean cancellation,
        boolean healthChecks,
        Set<String> formats) {

    public AiProviderCapabilities {
        formats = formats == null ? Set.of() : Set.copyOf(formats);
    }
}
