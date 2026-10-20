package com.inigmasgames.hytalerpg.combat.status;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Explicit project control ranks; no role-name substring classification or enemy-level inference. */
public final class ControlProfileRegistry {
    public enum Rank { COMMON, ELITE, BOSS, PROTECTED }
    private final Rank defaultRank;
    private final Map<String, Rank> roles;
    public ControlProfileRegistry(Rank defaultRank, Map<String, Rank> roles) {
        this.defaultRank = java.util.Objects.requireNonNull(defaultRank); this.roles = Map.copyOf(roles);
    }
    public static ControlProfileRegistry loadCanonical() {
        try (var input = ControlProfileRegistry.class.getResourceAsStream("/rpg/balance/control-profiles-v1.json")) {
            if (input == null) throw new IllegalStateException("Missing control profile registry");
            var data = new Gson().fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), Data.class);
            if (data.schemaVersion != 1) throw new IllegalStateException("Unsupported control profile registry");
            return new ControlProfileRegistry(data.defaultRank, data.roles);
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot load control profile registry", error); }
    }
    public ControlProfile resolve(String role, boolean nativeProtected, boolean nativeBoss) {
        Rank rank = roles.getOrDefault(role == null ? "" : role, defaultRank);
        return new ControlProfile(nativeProtected || rank == Rank.PROTECTED,
                nativeBoss || rank == Rank.BOSS, false, rank == Rank.ELITE);
    }
    private record Data(int schemaVersion, Rank defaultRank, Map<String, Rank> roles) { }
}
