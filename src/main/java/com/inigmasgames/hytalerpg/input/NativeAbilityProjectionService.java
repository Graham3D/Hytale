package com.inigmasgames.hytalerpg.input;

import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.inigmasgames.hytalerpg.diagnostics.RpgSkillTracer;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceRecord;
import com.inigmasgames.hytalerpg.domain.SkillId;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.execution.Stage04SkillProfiles;
import com.inigmasgames.hytalerpg.progress.RpgLoadoutOperations;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Projects the first two logical RPG skills into Hytale's two native Primary rune cells.
 *
 * <p>Ownership is deliberately conservative: RPG items may replace or clear only other RPG projection
 * items. A player-owned native rune is never overwritten, moved, or deleted; it produces a visible
 * diagnostic conflict instead. The projected ItemAbility assets are trigger-only (zero native cost and
 * cooldown), so all gameplay authority remains in {@code SkillExecutionService}.</p>
 */
public final class NativeAbilityProjectionService implements AutoCloseable {
    private static final long RECONCILE_NANOS = 250_000_000L;
    public static final short ABILITY2_PRIMARY_INDEX = 0;
    public static final short ABILITY3_PRIMARY_INDEX = InventoryComponent.ABILITIES_LINE_WIDTH;
    public static final String OWNED_ITEM_PREFIX = "RPG_Ability_";
    public static final String ABILITY4_UNAVAILABLE = "NATIVE_ABILITY4_UNAVAILABLE";

    private final RpgLoadoutOperations loadouts;
    private final Stage04SkillProfiles executable;
    private final RpgSkillTracer tracer;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private NativeRuneControl runeControl;

    public void configureControl(NativeRuneControl control) { runeControl = control; }

    public String runeControl(UUID player, String action) {
        Session session = sessions.get(player);
        if (runeControl == null || session == null) return "No active native ability session.";
        return switch (action) {
            case "start" -> runeControl.start(player, session.slots.getInventory());
            case "stop" -> runeControl.stop(player, session.slots.getInventory(), "COMMAND_STOP");
            case "status" -> runeControl.status(player);
            default -> "Use /rpg dev rune-control start|status|stop";
        };
    }

    public NativeAbilityProjectionService(RpgLoadoutOperations loadouts, Stage04SkillProfiles executable,
                                          RpgSkillTracer tracer) {
        this.loadouts = loadouts;
        this.executable = executable;
        this.tracer = tracer;
    }

    public void install(UUID player, InventoryComponent.AbilitySlots slots) {
        if (runeControl != null) runeControl.onJoin(player);
        sessions.put(player, new Session(slots));
        reconcile(player, slots, "PLAYER_READY");
    }

    /** Immediate mutation hook; the ticking system remains the repair path for native-container changes. */
    public void onLoadoutMutation(UUID player) {
        Session session = sessions.get(player);
        if (session != null) reconcile(player, session.slots, "LOADOUT_MUTATION");
    }

    public void tick(UUID player, InventoryComponent.AbilitySlots slots) {
        Session session = sessions.computeIfAbsent(player, ignored -> new Session(slots));
        if (session.slots != slots) {
            session = new Session(slots);
            sessions.put(player, session);
        }
        long now = System.nanoTime();
        if (now - session.lastReconcileNanos < RECONCILE_NANOS) return;
        session.lastReconcileNanos = now;
        reconcile(player, slots, "RECONCILE_TICK");
    }

    public void observeInput(HytaleAbilitySkillInputAdapter.Observation observation) {
        boolean unavailable = "Ability4".equals(observation.action());
        trace(observation.player(), unavailable ? RpgTraceEventType.NATIVE_ABILITY4_UNAVAILABLE
                        : RpgTraceEventType.NATIVE_ABILITY_INPUT_OBSERVED,
                observation.correlationId(), Map.of(
                        "action", observation.action(),
                        "chainId", observation.chainId(),
                        "mapped", observation.slot() != null,
                        "logicalSlot", observation.slot() == null ? "" : observation.slot().externalId(),
                        "result", observation.result()));
    }

    public String status(UUID player, boolean nativeAbilitiesVisible) {
        Session session = sessions.get(player);
        if (session == null) return "Native ability projection: NO_ACTIVE_WORLD_SESSION\nHudComponent.Abilities visible="
                + nativeAbilitiesVisible;
        reconcile(player, session.slots, "STATUS_COMMAND");
        ItemContainer container = session.slots.getInventory();
        StringBuilder out = new StringBuilder("Native ability projection (safe ownership: never overwrite native runes):")
                .append("\nHudComponent.Abilities visible=").append(nativeAbilitiesVisible)
                .append("\nAbilitySlots present=true container=").append(container.getClass().getSimpleName())
                .append('@').append(Integer.toHexString(System.identityHashCode(container)))
                .append(" capacity=").append(container.getCapacity())
                .append(" lines=").append(InventoryComponent.ABILITIES_LINES)
                .append(" lineWidth=").append(InventoryComponent.ABILITIES_LINE_WIDTH)
                .append(" primaryIndices=0,3");
        for (SkillSlot slot : SkillSlot.values()) {
            SlotStatus value = session.status.get(slot);
            out.append('\n').append(slot.externalId()).append(" -> ")
                    .append(value == null ? "UNKNOWN" : value.result());
            if (value != null && !value.skillId().isBlank()) out.append(" skill=").append(value.skillId());
            if (value != null && !value.nativeItemId().isBlank()) out.append(" nativeItem=").append(value.nativeItemId());
        }
        return out.toString();
    }

    public void detach(UUID player, String reason) {
        if (runeControl != null) runeControl.onDetach(player);
        Session session = sessions.remove(player);
        if (session == null) return;
        clearOwned(player, session.slots, reason);
    }

    @Override public void close() {
        for (UUID player : Set.copyOf(sessions.keySet())) detach(player, "PLUGIN_SHUTDOWN");
    }

    private void reconcile(UUID player, InventoryComponent.AbilitySlots slots, String reason) {
        if (runeControl != null && runeControl.pauseProjection(player, slots.getInventory())) return;
        Session session = sessions.computeIfAbsent(player, ignored -> new Session(slots));
        ItemContainer container = slots.getInventory();
        if (container.getCapacity() < InventoryComponent.DEFAULT_ABILITIES_CAPACITY) {
            updateStatus(player, session, SkillSlot.SKILL01, new SlotStatus("", "", "NATIVE_CONTAINER_TOO_SMALL"), reason);
            updateStatus(player, session, SkillSlot.SKILL02, new SlotStatus("", "", "NATIVE_CONTAINER_TOO_SMALL"), reason);
            SkillId skill03 = loadouts.getPresentationView(player).state().skill(SkillSlot.SKILL03).orElse(null);
            updateAbility4(player, session, skill03, reason);
            return;
        }
        var state = loadouts.getPresentationView(player).state();
        project(player, session, container, SkillSlot.SKILL01, ABILITY2_PRIMARY_INDEX,
                state.skill(SkillSlot.SKILL01).orElse(null), reason);
        project(player, session, container, SkillSlot.SKILL02, ABILITY3_PRIMARY_INDEX,
                state.skill(SkillSlot.SKILL02).orElse(null), reason);
        updateAbility4(player, session, state.skill(SkillSlot.SKILL03).orElse(null), reason);
    }

    private void project(UUID player, Session session, ItemContainer container, SkillSlot logicalSlot,
                         short nativeIndex, SkillId skill, String reason) {
        ItemStack current = container.getItemStack(nativeIndex);
        String currentId = ItemStack.isEmpty(current) ? "" : current.getItemId();
        String skillId = skill == null ? "" : skill.value();
        if (skill == null) {
            if (isOwnedItem(currentId)) {
                boolean succeeded = container.setItemStackForSlot(nativeIndex, ItemStack.EMPTY).succeeded();
                updateStatus(player, session, logicalSlot,
                        new SlotStatus("", succeeded ? "" : currentId,
                                succeeded ? "CLEARED" : "NATIVE_CONTAINER_WRITE_REJECTED"), reason);
            } else {
                updateStatus(player, session, logicalSlot,
                        new SlotStatus("", currentId, currentId.isBlank() ? "EMPTY" : "NATIVE_SLOT_PRESERVED"), reason);
            }
            return;
        }
        if (!executable.supports(skillId)) {
            if (isOwnedItem(currentId)) container.setItemStackForSlot(nativeIndex, ItemStack.EMPTY);
            updateStatus(player, session, logicalSlot,
                    new SlotStatus(skillId, "", "EXECUTOR_NOT_IMPLEMENTED"), reason);
            return;
        }
        String desiredId = itemIdFor(skillId);
        if (!currentId.isBlank() && !isOwnedItem(currentId)) {
            updateStatus(player, session, logicalSlot,
                    new SlotStatus(skillId, currentId, "NATIVE_SLOT_OCCUPIED"), reason);
            return;
        }
        if (desiredId.equals(currentId)) {
            updateStatus(player, session, logicalSlot, new SlotStatus(skillId, desiredId, "PROJECTED"), reason);
            return;
        }
        ItemStack desired = new ItemStack(desiredId);
        if (!desired.isValid()) {
            updateStatus(player, session, logicalSlot,
                    new SlotStatus(skillId, desiredId, "NATIVE_ITEM_ASSET_MISSING"), reason);
            return;
        }
        boolean succeeded = container.setItemStackForSlot(nativeIndex, desired).succeeded();
        updateStatus(player, session, logicalSlot,
                new SlotStatus(skillId, succeeded ? desiredId : currentId,
                        succeeded ? "PROJECTED" : "NATIVE_CONTAINER_WRITE_REJECTED"), reason);
    }

    private void updateAbility4(UUID player, Session session, SkillId skill, String reason) {
        String skillId = skill == null ? "" : skill.value();
        updateStatus(player, session, SkillSlot.SKILL03,
                new SlotStatus(skillId, "", skillId.isBlank() ? "EMPTY" : ABILITY4_UNAVAILABLE), reason);
    }

    private void updateStatus(UUID player, Session session, SkillSlot slot, SlotStatus next, String reason) {
        SlotStatus previous = session.status.put(slot, next);
        if (next.equals(previous)) return;
        RpgTraceEventType event = switch (next.result()) {
            case "PROJECTED" -> RpgTraceEventType.NATIVE_ABILITY_SLOT_PROJECTED;
            case "CLEARED", "EMPTY", "NATIVE_SLOT_PRESERVED" -> RpgTraceEventType.NATIVE_ABILITY_SLOT_CLEARED;
            case ABILITY4_UNAVAILABLE -> RpgTraceEventType.NATIVE_ABILITY4_UNAVAILABLE;
            default -> RpgTraceEventType.NATIVE_ABILITY_SLOT_CONFLICT;
        };
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("logicalSlot", slot.externalId());
        details.put("nativeAction", nativeAction(slot));
        details.put("nativePrimaryIndex", primaryIndex(slot));
        details.put("skillId", next.skillId());
        details.put("nativeItemId", next.nativeItemId());
        details.put("result", next.result());
        details.put("reason", reason);
        details.put("nativeCostAuthority", "RPG_ONLY");
        trace(player, event, reference(), details);
    }

    private void clearOwned(UUID player, InventoryComponent.AbilitySlots slots, String reason) {
        ItemContainer container = slots.getInventory();
        for (short index : new short[]{ABILITY2_PRIMARY_INDEX, ABILITY3_PRIMARY_INDEX}) {
            ItemStack current = container.getItemStack(index);
            if (!ItemStack.isEmpty(current) && isOwnedItem(current.getItemId())) {
                boolean succeeded = container.setItemStackForSlot(index, ItemStack.EMPTY).succeeded();
                trace(player, succeeded ? RpgTraceEventType.NATIVE_ABILITY_SLOT_CLEARED
                                : RpgTraceEventType.NATIVE_ABILITY_SLOT_CONFLICT,
                        reference(), Map.of("nativePrimaryIndex", index, "nativeItemId", current.getItemId(),
                                "result", succeeded ? "CLEARED" : "NATIVE_CONTAINER_WRITE_REJECTED",
                                "reason", reason));
            }
        }
    }

    private void trace(UUID player, RpgTraceEventType event, String correlation, Map<String, ?> details) {
        try { tracer.trace(RpgTraceRecord.create(player, event, correlation, details)); }
        catch (Throwable ignored) { /* Diagnostics never own gameplay. */ }
    }

    public static String itemIdFor(String skillId) {
        StringBuilder id = new StringBuilder(OWNED_ITEM_PREFIX);
        String[] parts = skillId.split("_");
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isBlank()) continue;
            id.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            if (index + 1 < parts.length) id.append('_');
        }
        return id.toString();
    }

    public static boolean isOwnedItem(String itemId) {
        return itemId != null && itemId.startsWith(OWNED_ITEM_PREFIX);
    }

    public static short primaryIndex(SkillSlot slot) {
        return switch (slot) {
            case SKILL01 -> ABILITY2_PRIMARY_INDEX;
            case SKILL02 -> ABILITY3_PRIMARY_INDEX;
            case SKILL03 -> -1;
        };
    }

    public static String nativeAction(SkillSlot slot) {
        return switch (slot) {
            case SKILL01 -> "Ability2";
            case SKILL02 -> "Ability3";
            case SKILL03 -> "UNAVAILABLE";
        };
    }

    private static String reference() { return UUID.randomUUID().toString().substring(0, 12); }
    private record SlotStatus(String skillId, String nativeItemId, String result) { }
    private static final class Session {
        private final InventoryComponent.AbilitySlots slots;
        private final Map<SkillSlot, SlotStatus> status = new EnumMap<>(SkillSlot.class);
        private volatile long lastReconcileNanos;
        private Session(InventoryComponent.AbilitySlots slots) { this.slots = slots; }
    }
}
