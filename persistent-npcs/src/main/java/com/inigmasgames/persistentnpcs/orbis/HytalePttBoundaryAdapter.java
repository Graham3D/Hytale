package com.inigmasgames.persistentnpcs.orbis;

/**
 * Stable Update 6 does not publish the physical PTT key-up transition to server
 * plugins. This edge policy maps the end of the PTT-generated packet run to release.
 * It is intentionally separate from capture/turn ownership and does not inspect audio.
 */
public record HytalePttBoundaryAdapter(long packetRunReleaseMillis) {
    public HytalePttBoundaryAdapter {
        packetRunReleaseMillis = Math.max(80, packetRunReleaseMillis);
    }
}
