package com.inigmasgames.persistentnpcs.profile;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.persistentnpcs.ui.NativeNpcInventoryController;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Read-only, generation-bound snapshot of live NPC statistics. */
public final class NpcStatsSnapshotService {
    public record StatValue(float current, float minimum, float maximum) { }
    public record DefenseSnapshot(double value, String authority) { }
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
        if (stats == null) throw new IllegalStateException("ENTITY_STAT_MAP_MISSING");

        double baseDefense = 0.0;
        for (short slot = 0; slot < Math.min((short) 4, authority.armor().getCapacity()); slot++) {
            ItemStack stack = authority.armor().getItemStack(slot);
            if (!ItemStack.isEmpty(stack) && stack.getItem() != null
                    && stack.getItem().getArmor() != null) {
                baseDefense += stack.getItem().getArmor().getBaseDamageResistance();
            }
        }
        NpcStatsSnapshot snapshot = new NpcStatsSnapshot(
                authority.profile().stableId(), authority.npcEntityId(), Instant.now(),
                stat(stats, "Health"), stat(stats, "Stamina"), stat(stats, "Mana"),
                Optional.of(new DefenseSnapshot(baseDefense,
                        "AUTHORITATIVE_ARMOR_BASE_DAMAGE_RESISTANCE")),
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
                + " defenseBase=" + baseDefense);
        return snapshot;
    }

    private static Optional<StatValue> stat(EntityStatMap map, String id) {
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
