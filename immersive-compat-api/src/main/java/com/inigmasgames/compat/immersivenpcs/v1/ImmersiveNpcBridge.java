package com.inigmasgames.compat.immersivenpcs.v1;

/**
 * Version 1 optional compatibility surface. Implemented by ImmersiveNPCs and
 * discovered through Hytale's PluginManager by compatible gameplay producers.
 */
public interface ImmersiveNpcBridge {
    int API_VERSION = 1;

    BridgeHandshake handshake(BridgeHello hello);

    void publish(GameplayEvent event);
}
