package com.inigmasgames.hytalerpg.spawning;

/** One authoritative operator value shared by /rpg spawns and world-config reload. */
public interface SpawnDensitySetting {
    double multiplier();
    void set(double value);
}
