package com.inigmasgames.persistentnpcs.stats;

import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import java.util.Map;
import java.util.UUID;

/** Dormant extension seam. No contributors, formulas or custom assets are installed by S1. */
public interface NpcStatModifierContributor {
    String sourceId();
    /** Deterministic asset-ID -> stable namespaced key -> native modifier. Never writes current values. */
    Map<String, Map<String, Modifier>> modifiersFor(Context context);
    record Context(UUID stableNpcId, NpcStatState savedState) { }
}
