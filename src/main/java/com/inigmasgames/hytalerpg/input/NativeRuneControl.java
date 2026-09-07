package com.inigmasgames.hytalerpg.input;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.AbilitySlot;
import com.hypixel.hytale.protocol.AbilityCostType;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.inigmasgames.hytalerpg.diagnostics.*;
import org.bson.BsonDocument;
import org.bson.BsonString;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Development-only native control. Container mutations run only on the player's world thread. */
public final class NativeRuneControl {
    public static final String RUNE = "Rune_Fireball";
    public static final String ROOT = "Root_Ability_Fireball";
    private static final short[] PRIMARY = {0, 3};
    private static final long DURATION = 120_000_000_000L;
    private static final long QUIET_PERIOD = 15_000_000_000L;
    private final Path journals;
    private final boolean enabled;
    private final RpgSkillTracer tracer;
    private final Map<UUID, Capture> active = new ConcurrentHashMap<>();
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final Set<UUID> attemptedRecovery = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> quietUntil = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastSummary = new ConcurrentHashMap<>();

    /** Actual server-loaded shipped asset audit, separate from the minimal unit inventory fixture. */
    public static void auditAssets() {
        var item = com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(RUNE);
        var root = RootInteraction.getAssetMap().getAsset(ROOT);
        var ability = item == null ? null : item.getAbility();
        var operations = new ArrayList<String>();
        if (root != null) for (int i = 0; i < root.getOperationMax(); i++)
            operations.add(root.getOperation(i).getClass().getSimpleName());
        boolean pass = ability != null && ROOT.equals(ability.getCastRootId())
                && ability.getSlot() == AbilitySlot.Primary && ability.getCost() == 25
                && ability.getCooldownS() == 12 && ability.getCostType() == AbilityCostType.Mana
                && !operations.isEmpty();
        com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(
                "RPG_NATIVE_RUNE_CONTROL_AUDIT revision=%s item=%s root=%s itemPack=%s operations=%s result=%s connectedProof=false",
                com.inigmasgames.hytalerpg.phase00.BuildIdentity.REVISION, RUNE, ROOT,
                com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAssetPack(RUNE),
                operations, pass ? "PASS" : "FAIL");
    }

    public NativeRuneControl(Path journals, boolean enabled, RpgSkillTracer tracer) {
        this.journals = journals; this.enabled = enabled; this.tracer = tracer;
        try {
            Files.createDirectories(journals);
            try (var files = Files.list(journals)) {
                files.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p ->
                        pending.add(UUID.fromString(p.getFileName().toString().replace(".json", ""))));
            }
        } catch (IOException error) { throw new IllegalStateException("Cannot load native-control recovery journal", error); }
    }

    public boolean inputSuppressed(UUID player) {
        return pending.contains(player) || System.nanoTime() < quietUntil.getOrDefault(player, 0L);
    }

    public String start(UUID player, ItemContainer container) {
        if (!enabled) return "Native Rune control is development-only and disabled.";
        if (inputSuppressed(player)) return "Control/recovery or 15-second quiet period active. Use rune-control status/stop.";
        if (container.getCapacity() != 6) return "Control refused: expected six native AbilitySlots.";
        for (short i = 0; i < 6; i++) {
            ItemStack item = container.getItemStack(i);
            if (i == 0 || i == 3) {
                if (ItemStack.isEmpty(item) || !NativeAbilityProjectionService.isOwnedItem(item.getItemId()))
                    return "Control refused: equip RPG skills in both primary slots first; foreign runes are never overwritten.";
            } else if (!ItemStack.isEmpty(item)) return "Control refused: remove native support runes for an unmodified baseline.";
        }
        ItemStack rune = new ItemStack(RUNE);
        if (!rune.isValid()) return "Control refused: shipped Rune_Fireball asset missing.";
        var ability = rune.getItem().getAbility();
        RootInteraction root = RootInteraction.getAssetMap().getAsset(ROOT);
        if (ability == null || ability.getSlot() != AbilitySlot.Primary || !ROOT.equals(ability.getCastRootId())
                || ability.getCost() != 25 || ability.getCooldownS() != 12 || ability.getCostType() != AbilityCostType.Mana
                || root == null || root.getOperationMax() == 0)
            return "Control refused: installed shipped Rune does not match audited 0.7.0-pre.1 baseline.";

        Capture capture = new Capture();
        BsonDocument journal = new BsonDocument("trial", new BsonString(capture.id));
        for (short i : PRIMARY) journal.put(Short.toString(i), ItemStack.CODEC.encode(container.getItemStack(i), new ExtraInfo()));
        try {
            // Durable before any native slot mutation. Never overwrite an unfinished recovery journal.
            byte[] bytes = journal.toJson().getBytes(StandardCharsets.UTF_8);
            try (FileChannel file = FileChannel.open(path(player), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) file.write(buffer);
                file.force(true);
            }
        } catch (IOException error) {
            if (Files.exists(path(player))) pending.add(player);
            return "Control refused: cannot durably snapshot slots: " + error.getMessage();
        }
        pending.add(player); // Suppress before native mutation; drain also checks this gate.
        active.put(player, capture);
        try {
            for (short i : PRIMARY) {
                if (!container.setItemStackForSlot(i, new ItemStack(RUNE)).succeeded()
                        || !new ItemStack(RUNE).equals(container.getItemStack(i)))
                    throw new IllegalStateException("Native slot write rejected at " + i);
            }
        } catch (RuntimeException error) {
            return "Control setup failed: " + error.getMessage() + ". " + stop(player, container, "SETUP_FAILED");
        }
        emit(player, RpgTraceEventType.NATIVE_RUNE_CONTROL_START, capture.id, Map.of(
                "item", RUNE, "root", ROOT, "nativeCost", ability.getCost(), "nativeCostType", "Mana",
                "nativeCooldownSeconds", ability.getCooldownS(), "rootOperations", root.getOperationMax(),
                "rootRemote", root.needsRemoteSync(), "primaryIndices", List.of(0, 3),
                "rpgExecutionSuppressed", true, "durationSeconds", 120));
        return "Native control " + capture.id + ": shipped Fireball in Ability2 AND Ability3 for 120 seconds.\n"
                + "Aim at empty ground away from people/buildings. Need 25 Mana per cast. Press E once; wait 13 seconds and for 25 Mana; press R once.\n"
                + "Do not edit loadout/inventory during test. Note whether a VANILLA fireball appears. Then /rpg dev rune-control stop. RPG casting is suppressed.";
    }

    /** Called before projection on its world tick. Recovery never runs from a network callback. */
    public boolean pauseProjection(UUID player, ItemContainer container) {
        Capture capture = active.get(player);
        if (capture != null && System.nanoTime() - capture.started >= DURATION) stop(player, container, "TIMEOUT");
        else if (capture == null && pending.contains(player) && attemptedRecovery.add(player))
            stop(player, container, "INTERRUPTED_SESSION_RECOVERY");
        return pending.contains(player);
    }

    public void onJoin(UUID player) { attemptedRecovery.remove(player); }

    /** Teardown may be off-world-thread. Preserve journal; restore on next ready/world tick instead. */
    public void onDetach(UUID player) {
        Capture capture = active.remove(player);
        if (capture != null) summarize(player, capture, "DETACHED_RECOVERY_REQUIRED");
    }

    public String stop(UUID player, ItemContainer container, String reason) {
        Capture capture = active.remove(player);
        if (capture != null) summarize(player, capture, reason);
        if (!pending.contains(player)) return status(player);
        String trial = capture == null ? "recovery" : capture.id;
        try {
            BsonDocument journal = BsonDocument.parse(Files.readString(path(player)));
            trial = journal.getString("trial").getValue();
            for (short i : PRIMARY) {
                ItemStack original = ItemStack.CODEC.decode(journal.getDocument(Short.toString(i)), new ExtraInfo());
                if (ItemStack.isEmpty(original) || !NativeAbilityProjectionService.isOwnedItem(original.getItemId()))
                    throw new IllegalStateException("Invalid original snapshot at " + i);
                ItemStack current = container.getItemStack(i);
                if (original.equals(current)) continue; // Handles partial setup/restoration and repeated ready events.
                if (!new ItemStack(RUNE).equals(current))
                    throw new IllegalStateException("Slot " + i + " changed externally; preserved, journal retained");
                if (!container.setItemStackForSlot(i, original).succeeded() || !original.equals(container.getItemStack(i)))
                    throw new IllegalStateException("Native restoration rejected at slot " + i);
            }
            Files.delete(path(player));
            quietUntil.put(player, System.nanoTime() + QUIET_PERIOD);
            pending.remove(player);
            attemptedRecovery.remove(player);
            emit(player, RpgTraceEventType.NATIVE_RUNE_CONTROL_RESTORE, trial, Map.of("result", "RESTORED", "reason", reason));
            return "Original RPG native slots restored. RPG input quiet for 15 seconds.\n" + status(player);
        } catch (IOException | RuntimeException error) {
            emit(player, RpgTraceEventType.NATIVE_RUNE_CONTROL_RESTORE, trial,
                    Map.of("result", "BLOCKED", "reason", reason, "error", String.valueOf(error.getMessage())));
            return "Restoration BLOCKED; RPG input remains suppressed. Journal retained. " + error.getMessage();
        }
    }

    public void observe(UUID player, Packet packet) {
        Capture capture = active.get(player);
        if (capture != null) capture.observe(player, packet);
    }

    public String status(UUID player) {
        Capture capture = active.get(player);
        if (capture != null) return summarize(player, capture, "STATUS");
        return "Native control active=false recoveryPending=" + pending.contains(player)
                + " rpgInputSuppressed=" + inputSuppressed(player) + "\n"
                + lastSummary.getOrDefault(player, "No control capture in this process.");
    }

    private String summarize(UUID player, Capture capture, String reason) {
        Map<String, Object> details = capture.summary();
        details.put("reason", reason);
        emit(player, RpgTraceEventType.NATIVE_RUNE_CONTROL_SUMMARY, capture.id, details);
        String result = "Trial " + capture.id + ": " + details + "\nVisual vanilla cast result must be supplied by the tester; counters alone do not prove casting.";
        lastSummary.put(player, result);
        return result;
    }

    private Path path(UUID player) { return journals.resolve(player + ".json"); }

    private void emit(UUID player, RpgTraceEventType event, String id, Map<String, ?> details) {
        tracer.trace(RpgTraceRecord.create(player, event, id, details));
    }

    private final class Capture {
        private final String id = UUID.randomUUID().toString();
        private final long started = System.nanoTime();
        private final Map<String, Long> packetTypes = new TreeMap<>();
        private final Map<String, Long> chainTypes = new TreeMap<>();
        private long syncPackets, initialAbilities, abilityUpdates, detailsRecorded;

        synchronized void observe(UUID player, Packet packet) {
            packetTypes.merge(packet.getClass().getSimpleName(), 1L, Long::sum);
            if (!(packet instanceof SyncInteractionChains chains)) return;
            syncPackets++;
            if (chains.updates != null) for (SyncInteractionChain chain : chains.updates) chain(player, chain, 0);
        }

        private void chain(UUID player, SyncInteractionChain chain, int depth) {
            if (chain == null || depth > 32) return;
            String type = String.valueOf(chain.interactionType);
            chainTypes.merge(type, 1L, Long::sum);
            if (HytaleAbilitySkillInputAdapter.slot(chain.interactionType) != null) {
                abilityUpdates++;
                if (chain.initial) initialAbilities++;
            }
            if (detailsRecorded++ < 64) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("action", type); details.put("initial", chain.initial);
                details.put("chainId", chain.chainId); details.put("state", String.valueOf(chain.state));
                details.put("overrideRootInteraction", chain.overrideRootInteraction);
                details.put("equipSlot", chain.equipSlot); details.put("forkDepth", depth);
                details.put("operationBaseIndex", chain.operationBaseIndex);
                details.put("itemInHandId", String.valueOf(chain.itemInHandId));
                details.put("rpgExecutionSuppressed", true);
                emit(player, RpgTraceEventType.NATIVE_RUNE_CONTROL_PACKET, id, details);
            }
            if (chain.newForks != null) for (SyncInteractionChain fork : chain.newForks) chain(player, fork, depth + 1);
        }

        synchronized Map<String, Object> summary() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("packetTypes", new TreeMap<>(packetTypes)); out.put("chainTypes", new TreeMap<>(chainTypes));
            out.put("syncPackets", syncPackets); out.put("ability2or3Updates", abilityUpdates);
            out.put("initialAbility2or3Updates", initialAbilities);
            out.put("detailLimit", 64); out.put("chainDetailsSeen", detailsRecorded);
            out.put("elapsedSeconds", (System.nanoTime() - started) / 1_000_000_000L);
            return out;
        }
    }
}
