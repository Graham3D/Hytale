package com.inigmasgames.compat.immersivenpcs.v1;

import java.util.Set;

public record BridgeHello(
        int apiVersion,
        String clientId,
        String clientVersion,
        Set<BridgeCapability> offeredCapabilities) {

    public BridgeHello {
        clientId = clientId == null ? "" : clientId.strip();
        clientVersion = clientVersion == null ? "" : clientVersion.strip();
        offeredCapabilities = offeredCapabilities == null
                ? Set.of() : Set.copyOf(offeredCapabilities);
    }
}
