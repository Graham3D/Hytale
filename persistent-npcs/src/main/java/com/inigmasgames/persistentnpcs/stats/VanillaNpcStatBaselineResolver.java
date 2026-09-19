package com.inigmasgames.persistentnpcs.stats;

import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;

/** Installed definitions plus the SAME role selected by the spawn adapter. No parallel defaults. */
public final class VanillaNpcStatBaselineResolver {
    public record RolePolicy(String roleId, Double maxHealth, Boolean invulnerable) { }
    private final Function<String, EntityStatType> definitions;
    private final Consumer<String> log;
    public VanillaNpcStatBaselineResolver(Consumer<String> log) {
        this(id -> {
            int index = VanillaNpcStats.index(id);
            return index < 0 ? null : EntityStatType.getAssetMap().getAsset(index);
        }, log);
    }
    public VanillaNpcStatBaselineResolver(Function<String, EntityStatType> definitions, Consumer<String> log) {
        this.definitions = definitions;
        this.log = log;
    }
    public Map<String, NpcStatRecord> resolve(NpcProfile profile, RolePolicy role) {
        var result = new TreeMap<String, NpcStatRecord>();
        for (String id : VanillaNpcStats.IDS) {
            try {
                EntityStatType asset = definitions.apply(id);
                if (asset == null || asset.isUnknown() || !id.equals(asset.getId()))
                    throw new IllegalStateException("Missing installed stat asset");
                double min = asset.getMin(), max = asset.getMax(), initial = asset.getInitialValue();
                if (!Double.isFinite(initial) || !Double.isFinite(min) || !Double.isFinite(max) || min > max)
                    throw new IllegalStateException("Invalid installed stat bounds");
                String source = "ENTITY_STAT_ASSET";
                if (id.equals("Health") && role.maxHealth() != null) {
                    max = role.maxHealth();
                    if (!Double.isFinite(max) || max < min) throw new IllegalStateException("Invalid native role MaxHealth");
                    source = "ROLE_AND_ENTITY_STAT_ASSET";
                }
                initial = Math.clamp(initial, min, max);
                result.put(id, new NpcStatRecord(initial, initial, min, max, min, max, source));
                log.accept("NPC_STATS_BASELINE_RESOLVED npc=" + profile.name() + " stableId=" + profile.stableId()
                        + " role=" + role.roleId() + " stat=" + id + " initial=" + initial + " min=" + min
                        + " max=" + max + " reset=" + asset.getResetBehavior());
            } catch (RuntimeException failure) {
                log.accept("NPC_STATS_MISSING_ASSET npc=" + profile.name() + " stat=" + id + " reason=" + failure);
            }
        }
        return Map.copyOf(result);
    }
}
