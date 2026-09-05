package com.inigmasgames.persistentnpcs.stats;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.ItemArmorSlot;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemArmor;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Reconciles authoritative NPC equipment through Hytale's native stat-modifier
 * manager. No stat arithmetic is duplicated here; per-slot descriptors exist only
 * for bounded provenance and replacement/removal diagnostics.
 */
public final class NpcEquipmentStatSynchronizer {
    public record SourceModifier(short slot, String slotName, String itemId,
            int statIndex, int modifierPosition, String sourceIdentity,
            String nativeModifierKey, StaticModifier modifier) { }

    private record StatReading(String current, String maximum) { }

    private final Consumer<String> log;
    private final Map<UUID, Map<String, SourceModifier>> lastSources =
            new ConcurrentHashMap<>();

    public NpcEquipmentStatSynchronizer(Consumer<String> log) {
        this.log = log == null ? ignored -> { } : log;
    }

    public void synchronize(UUID stableId, Ref<EntityStore> ref,
            Store<EntityStore> store, String trigger) {
        if (stableId == null || ref == null || !ref.isValid() || store == null) return;
        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        InventoryComponent.Armor armor = store.getComponent(
                ref, InventoryComponent.Armor.getComponentType());
        if (stats == null || armor == null || armor.getInventory() == null) return;

        Map<String, SourceModifier> current = describe(armor.getInventory());
        Map<String, SourceModifier> previous = lastSources.getOrDefault(stableId, Map.of());
        var statIndexes = new java.util.TreeSet<Integer>();
        current.values().forEach(value -> statIndexes.add(value.statIndex()));
        previous.values().forEach(value -> statIndexes.add(value.statIndex()));
        Map<Integer, StatReading> before = readings(stats, statIndexes);
        String reason = trigger == null || trigger.isBlank() ? "UNSPECIFIED" : trigger;

        log.accept("NPC_EQUIPMENT_STATS_SYNC_BEGIN"
                + " npcStableId=" + stableId
                + " trigger=" + reason
                + " armorSources=" + current.size()
                + " nativePath=StatModifiersManager.recalculateEntityStatModifiers");
        try {
            // This is the installed 0.6.3 player/native equipment path. It applies
            // effects, every ItemArmor.getStatModifiers() entry, held-item modifiers,
            // utility modifiers, and native broken-item penalties to this same map.
            stats.getStatModifiersManager().recalculateEntityStatModifiers(ref, stats, store);
            Map<Integer, StatReading> after = readings(stats, statIndexes);

            previous.forEach((identity, source) -> {
                if (!current.containsKey(identity)) {
                    marker("NPC_EQUIPMENT_STAT_REMOVED", stableId, source,
                            before.get(source.statIndex()), after.get(source.statIndex()), reason);
                }
            });
            current.values().forEach(source -> marker("NPC_EQUIPMENT_STAT_APPLIED",
                    stableId, source, before.get(source.statIndex()),
                    after.get(source.statIndex()), reason));
            lastSources.put(stableId, current);
            log.accept("NPC_EQUIPMENT_STATS_SYNC_COMPLETE"
                    + " npcStableId=" + stableId
                    + " trigger=" + reason
                    + " armorSources=" + current.size()
                    + " removedSources=" + previous.keySet().stream()
                            .filter(key -> !current.containsKey(key)).count()
                    + " success=true");
        } catch (RuntimeException failure) {
            log.accept("NPC_EQUIPMENT_STATS_SYNC_COMPLETE"
                    + " npcStableId=" + stableId
                    + " trigger=" + reason
                    + " armorSources=" + current.size()
                    + " success=false reason=" + failure.getClass().getSimpleName());
            throw failure;
        }
    }

    /** Deterministic source inventory used by tests and bounded audit logging. */
    public static Map<String, SourceModifier> describe(ItemContainer armorContainer) {
        if (armorContainer == null) return Map.of();
        List<SourceModifier> values = new ArrayList<>();
        for (short slot = 0; slot < armorContainer.getCapacity(); slot++) {
            final short sourceSlot = slot;
            ItemStack stack = armorContainer.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) continue;
            ItemArmor armor = stack.getItem().getArmor();
            if (armor == null || armor.getStatModifiers() == null) continue;
            String slotName = slot < ItemArmorSlot.VALUES.length
                    ? ItemArmorSlot.VALUES[slot].name().toUpperCase(java.util.Locale.ROOT)
                    : "SLOT_" + slot;
            armor.getStatModifiers().int2ObjectEntrySet().forEach(entry -> {
                StaticModifier[] modifiers = entry.getValue();
                if (modifiers == null) return;
                for (int position = 0; position < modifiers.length; position++) {
                    StaticModifier modifier = modifiers[position];
                    if (modifier == null || modifier.getCalculationType() == null) continue;
                    String identity = "NPC_EQUIPMENT:" + slotName + ":stat_"
                            + entry.getIntKey() + ":modifier_" + position;
                    values.add(new SourceModifier(sourceSlot, slotName, stack.getItemId(),
                            entry.getIntKey(), position, identity,
                            modifier.getCalculationType().createKey("Armor"), modifier));
                }
            });
        }
        values.sort(Comparator.comparing(SourceModifier::sourceIdentity));
        Map<String, SourceModifier> result = new LinkedHashMap<>();
        values.forEach(value -> result.put(value.sourceIdentity(), value));
        return Map.copyOf(result);
    }

    private static Map<Integer, StatReading> readings(
            EntityStatMap stats, Iterable<Integer> indexes) {
        Map<Integer, StatReading> values = new LinkedHashMap<>();
        for (int index : indexes) {
            EntityStatValue value = stats.get(index);
            values.put(index, value == null ? new StatReading("UNAVAILABLE", "UNAVAILABLE")
                    : new StatReading(Float.toString(value.get()), Float.toString(value.getMax())));
        }
        return values;
    }

    private void marker(String marker, UUID stableId, SourceModifier source,
            StatReading before, StatReading after, String trigger) {
        StatReading safeBefore = before == null
                ? new StatReading("UNAVAILABLE", "UNAVAILABLE") : before;
        StatReading safeAfter = after == null
                ? new StatReading("UNAVAILABLE", "UNAVAILABLE") : after;
        log.accept(marker
                + " npcStableId=" + stableId
                + " slot=" + source.slotName()
                + " itemId=" + source.itemId()
                + " statIndex=" + source.statIndex()
                + " modifierKey=" + source.nativeModifierKey()
                + " sourceIdentity=" + source.sourceIdentity()
                + " beforeCurrent=" + safeBefore.current()
                + " beforeMax=" + safeBefore.maximum()
                + " afterCurrent=" + safeAfter.current()
                + " afterMax=" + safeAfter.maximum()
                + " trigger=" + trigger);
    }
}
