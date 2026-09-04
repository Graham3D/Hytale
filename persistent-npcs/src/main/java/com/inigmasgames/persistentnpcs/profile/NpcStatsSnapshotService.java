package com.inigmasgames.persistentnpcs.profile;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.persistentnpcs.ui.NativeNpcInventoryController;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Read-only, generation-bound snapshot: live ECS vitals and independently authoritative armor. */
public final class NpcStatsSnapshotService {
    public record StatValue(float current, float minimum, float maximum) { }
    public record Protection(double flat, double percent, String inheritedParent, boolean bypassed) { }
    public record DefenseSnapshot(Map<String, Protection> types) {
        public DefenseSnapshot { types = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(types)); }
        public String summary() {
            if (types.isEmpty()) return "No armor";
            String type = types.containsKey("Physical") ? "Physical" : types.keySet().iterator().next();
            var value = types.get(type);
            if (value.bypassed()) return type + ": bypass";
            return (value.percent() != 0 ? NpcConfiguredVitals.number(value.percent() * 100) + "%"
                    : NpcConfiguredVitals.number(value.flat()) + " flat") + " " + type;
        }
        public String details() {
            StringBuilder text = new StringBuilder("Equipped armor resistance (before effects/broken-item penalties):");
            types.forEach((type, value) -> text.append("\n").append(type).append(": ")
                    .append(NpcConfiguredVitals.number(value.flat())).append(" flat + ")
                    .append(NpcConfiguredVitals.number(value.percent() * 100)).append("%")
                    .append(value.inheritedParent() == null ? "" : "; then parent " + value.inheritedParent())
                    .append(value.bypassed() ? "; damage bypasses resistance" : ""));
            if (types.isEmpty()) text.append("\nNo typed armor resistance.");
            return text.append("\nPer type: max(0, damage - flat) × max(0, 1 - percent); inherited types apply afterward. Types are not summed together.").toString();
        }
    }
    public record NpcStatsSnapshot(
            UUID npcStableId,
            UUID npcEntityUuid,
            Instant capturedAt,
            Optional<StatValue> health,
            Optional<StatValue> stamina,
            Optional<StatValue> mana,
            Optional<DefenseSnapshot> defense,
            Map<String, Double> optionalResistances,
            long equipmentRevision,
            UUID sessionId,
            long pageGeneration) { }

    public NpcStatsSnapshot capture(Store<EntityStore> store,
            NativeNpcInventoryController.LiveStorageAuthority authority,
            UUID sessionId, long pageGeneration, long equipmentRevision,
            Consumer<String> diagnostics) {
        if (authority == null) {
            throw new IllegalStateException("LIVE_NPC_UNAVAILABLE");
        }
        String invalid = authority.invalidReason(null, store);
        if (invalid != null) throw new IllegalStateException(invalid);
        EntityStatMap stats = store.getComponent(
                authority.npcRef(), EntityStatMap.getComponentType());
        NpcStatsSnapshot snapshot = new NpcStatsSnapshot(
                authority.profile().stableId(), authority.npcEntityId(), Instant.now(),
                stat(stats, "Health"), stat(stats, "Stamina"), stat(stats, "Mana"),
                Optional.of(armorProtection(authority.armor())),
                Map.of(), equipmentRevision, sessionId, pageGeneration);
        if (diagnostics != null) diagnostics.accept("NPC_STATS_SNAPSHOT"
                + " npc=" + authority.profile().name()
                + " stableId=" + snapshot.npcStableId()
                + " entityUuid=" + snapshot.npcEntityUuid()
                + " capturedAt=" + snapshot.capturedAt()
                + " equipmentRevision=" + snapshot.equipmentRevision()
                + " sessionId=" + snapshot.sessionId()
                + " pageGeneration=" + snapshot.pageGeneration()
                + " health=" + shown(snapshot.health())
                + " stamina=" + shown(snapshot.stamina())
                + " mana=" + shown(snapshot.mana())
                + " armorProtection=" + snapshot.defense());
        return snapshot;
    }

    /** Unspawned NPCs have no authoritative persisted vitals. Never invent them. */
    public NpcStatsSnapshot captureEquipmentOnly(UUID npcStableId, ItemContainer armor,
            UUID sessionId, long pageGeneration, long equipmentRevision) {
        return new NpcStatsSnapshot(npcStableId, null, Instant.now(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(armorProtection(armor)),
                Map.of(), equipmentRevision, sessionId, pageGeneration);
    }

    public static DefenseSnapshot armorProtection(ItemContainer armor) {
        var types = new java.util.TreeMap<String, Protection>();
        // Installed SDK: world is accessed only when penalties are enabled; null effects
        // explicitly means equipment-only. Native aggregation includes base in each typed flat value.
        DamageSystems.ArmorDamageReduction.getResistanceModifiers(null, armor, false, null)
                .forEach((cause, modifiers) -> types.put(cause.getId(), new Protection(
                        modifiers.flatModifier, modifiers.multiplierModifier,
                        modifiers.inheritedParentId == null ? null : modifiers.inheritedParentId.getId(),
                        cause.doesBypassResistances())));
        return new DefenseSnapshot(types);
    }

    private static Optional<StatValue> stat(EntityStatMap map, String id) {
        if (map == null) return Optional.empty();
        try {
            EntityStatValue value = map.get(id);
            return value == null ? Optional.empty()
                    : Optional.of(new StatValue(value.get(), value.getMin(), value.getMax()));
        } catch (RuntimeException unavailable) {
            return Optional.empty();
        }
    }

    private static String shown(Optional<StatValue> value) {
        return value.map(stat -> stat.current() + "/" + stat.maximum()).orElse("UNAVAILABLE");
    }
}
