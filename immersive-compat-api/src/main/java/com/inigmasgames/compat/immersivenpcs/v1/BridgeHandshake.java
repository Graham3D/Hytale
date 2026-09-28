package com.inigmasgames.compat.immersivenpcs.v1;

import java.util.Set;

public record BridgeHandshake(
        boolean enabled,
        int apiVersion,
        String providerId,
        String providerVersion,
        Set<BridgeCapability> acceptedCapabilities,
        String reason) {

    public BridgeHandshake {
        providerId = providerId == null ? "" : providerId.strip();
        providerVersion = providerVersion == null ? "" : providerVersion.strip();
        acceptedCapabilities = acceptedCapabilities == null
                ? Set.of() : Set.copyOf(acceptedCapabilities);
        reason = reason == null ? "" : reason.strip();
    }
}
