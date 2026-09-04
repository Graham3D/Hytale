package com.inigmasgames.persistentnpcs.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.InventoryUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Shared server-authoritative Custom UI inventory bridge. Custom UI events are
 * untrusted intent; Hytale's InventoryUtils and registered ItemContainers remain
 * the only mutation authority.
 */
public final class CustomInventoryTransactionBridge {
    /** Returns {@code null} when the captured external authority is still valid. */
    @FunctionalInterface
    public interface AuthorityValidator {
        String invalidReason(Ref<EntityStore> viewerRef, Store<EntityStore> store);
    }

    public enum ResultType {
        COMMITTED,
        REJECTED,
        NO_OP,
        STALE,
        INVALID,
        DUPLICATE
    }

    /** Immutable, server-bounded intent. Client item fields remain diagnostic only. */
    public record InventoryMoveIntent(
            UUID sessionId,
            long pageGeneration,
            int sourceSectionId,
            int sourceSlotId,
            int targetSectionId,
            int targetSlotId,
            int requestedQuantity,
            int mouseButton,
            long eventSequence,
            String clientItemIdDiagnostic,
            int clientQuantityDiagnostic) { }

    public record BridgeResult(
            long operationId,
            ResultType type,
            String reason,
            String sourceBefore,
            String targetBefore,
            String sourceAfter,
            String targetAfter) { }

    private static final long DUPLICATE_WINDOW_NANOS = 2_000_000_000L;

    private final UUID sessionId;
    private final long pageGeneration;
    private final PlayerRef viewer;
    private final CustomUIPage expectedPage;
    private final ContainerWindow npcWindow;
    private final ItemContainer npcInventory;
    private final ItemContainer playerStorage;
    private final AuthorityValidator authorityValidator;
    private final Consumer<String> diagnostics;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final AtomicLong operationSequence = new AtomicLong();
    private final AtomicLong committedOperations = new AtomicLong();
    private final AtomicLong rejectedOperations = new AtomicLong();
    private final AtomicLong staleOperations = new AtomicLong();
    private final AtomicLong duplicateOperations = new AtomicLong();
    private final AtomicLong invariantViolations = new AtomicLong();
    private final Map<String, Long> recentFingerprints = new HashMap<>();

    public CustomInventoryTransactionBridge(UUID sessionId, long pageGeneration,
            PlayerRef viewer, CustomUIPage expectedPage, ContainerWindow npcWindow,
            ItemContainer npcInventory, ItemContainer playerStorage,
            Consumer<String> diagnostics) {
        this(sessionId, pageGeneration, viewer, expectedPage, npcWindow,
                npcInventory, playerStorage, (ignoredRef, ignoredStore) -> null,
                diagnostics);
    }

    public CustomInventoryTransactionBridge(UUID sessionId, long pageGeneration,
            PlayerRef viewer, CustomUIPage expectedPage, ContainerWindow npcWindow,
            ItemContainer npcInventory, ItemContainer playerStorage,
            AuthorityValidator authorityValidator, Consumer<String> diagnostics) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.pageGeneration = pageGeneration;
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.expectedPage = Objects.requireNonNull(expectedPage, "expectedPage");
        this.npcWindow = Objects.requireNonNull(npcWindow, "npcWindow");
        this.npcInventory = Objects.requireNonNull(npcInventory, "npcInventory");
        this.playerStorage = Objects.requireNonNull(playerStorage, "playerStorage");
        this.authorityValidator = Objects.requireNonNull(
                authorityValidator, "authorityValidator");
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public long pageGeneration() {
        return pageGeneration;
    }

    /** Execute immediately on the world thread, or marshal there before validation. */
    public void submit(Ref<EntityStore> ref, Store<EntityStore> store,
            InventoryMoveIntent intent, Consumer<BridgeResult> completion) {
        long operationId = operationSequence.incrementAndGet();
        diagnostics.accept(marker("CUSTOM_BRIDGE_INTENT", operationId, intent)
                + " player=" + viewer.getUuid()
                + " clientItemIdDiagnostic=" + quoted(intent.clientItemIdDiagnostic())
                + " clientQuantityDiagnostic=" + intent.clientQuantityDiagnostic()
                + " submittedOnWorldThread=" + store.isInThread());
        Runnable task = () -> completion.accept(execute(operationId, ref, store, intent));
        if (store.isInThread()) {
            task.run();
        } else {
            diagnostics.accept("CUSTOM_BRIDGE_THREAD_MARSHAL"
                    + " timestamp=" + Instant.now()
                    + " BridgeOperationId=" + operationId
                    + " executionContext=World.execute");
            store.getExternalData().getWorld().execute(task);
        }
    }

    /** Invalidate before page/window teardown so late callbacks fail closed. */
    public void close() {
        boolean wasActive = active.getAndSet(false);
        diagnostics.accept("CUSTOM_BRIDGE_SESSION_CLOSE"
                + " timestamp=" + Instant.now()
                + " player=" + viewer.getUuid()
                + " sessionId=" + sessionId
                + " pageGeneration=" + pageGeneration
                + " wasActive=" + wasActive
                + " observedOperationCount=" + operationSequence.get()
                + " committedOperationCount=" + committedOperations.get()
                + " rejectedOperationCount=" + rejectedOperations.get()
                + " staleOperationCount=" + staleOperations.get()
                + " duplicateOperationCount=" + duplicateOperations.get()
                + " invariantViolationCount=" + invariantViolations.get());
    }

    private BridgeResult execute(long operationId, Ref<EntityStore> ref,
            Store<EntityStore> store, InventoryMoveIntent intent) {
        if (!active.get()) {
            return reject(operationId, intent, ResultType.INVALID, "SESSION_CLOSED",
                    null, null);
        }
        if (!sessionId.equals(intent.sessionId())
                || pageGeneration != intent.pageGeneration()) {
            return reject(operationId, intent, ResultType.INVALID,
                    "SESSION_GENERATION_MISMATCH", null, null);
        }
        if (!ref.isValid() || !Objects.equals(viewer.getReference(), ref)) {
            return reject(operationId, intent, ResultType.INVALID,
                    "PLAYER_REFERENCE_MISMATCH", null, null);
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || player.getPageManager().getCustomPage() != expectedPage) {
            return reject(operationId, intent, ResultType.INVALID,
                    "CUSTOM_PAGE_NOT_ACTIVE", null, null);
        }
        String authorityFailure = authorityValidator.invalidReason(ref, store);
        if (authorityFailure != null) {
            return reject(operationId, intent, ResultType.INVALID,
                    "AUTHORITY_INVALID_" + authorityFailure, null, null);
        }
        int npcSection = npcWindow.getId();
        if (npcSection < 0
                || player.getWindowManager().getWindow(npcSection) != npcWindow) {
            return reject(operationId, intent, ResultType.INVALID,
                    "CONTAINER_WINDOW_NOT_ACTIVE", null, null);
        }
        if (!allowedSection(intent.sourceSectionId(), npcSection)
                || !allowedSection(intent.targetSectionId(), npcSection)) {
            return reject(operationId, intent, ResultType.INVALID,
                    "SECTION_NOT_ALLOWED", null, null);
        }
        ItemContainer source = InventoryUtils.getSectionById(
                ref, intent.sourceSectionId(), store);
        ItemContainer target = InventoryUtils.getSectionById(
                ref, intent.targetSectionId(), store);
        if (!expectedIdentity(source, intent.sourceSectionId(), npcSection)
                || !expectedIdentity(target, intent.targetSectionId(), npcSection)) {
            return reject(operationId, intent, ResultType.INVALID,
                    "SECTION_OBJECT_IDENTITY_MISMATCH", null, null);
        }
        if (!validSlot(intent.sourceSlotId(), source)
                || !validSlot(intent.targetSlotId(), target)) {
            return reject(operationId, intent, ResultType.INVALID,
                    "SLOT_OUT_OF_RANGE", null, null);
        }
        if (source == target && intent.sourceSlotId() == intent.targetSlotId()) {
            return reject(operationId, intent, ResultType.NO_OP,
                    "SOURCE_EQUALS_DESTINATION", null, null);
        }
        if (intent.mouseButton() != 1 && intent.mouseButton() != 2) {
            return reject(operationId, intent, ResultType.INVALID,
                    "OPERATION_NOT_ENABLED_MOUSE_BUTTON", null, null);
        }

        ItemStack sourceBefore = source.getItemStack((short) intent.sourceSlotId());
        ItemStack targetBefore = target.getItemStack((short) intent.targetSlotId());
        if (ItemStack.isEmpty(sourceBefore)) {
            return reject(operationId, intent, ResultType.STALE,
                    "AUTHORITATIVE_SOURCE_EMPTY", sourceBefore, targetBefore);
        }
        if (intent.requestedQuantity() <= 0
                || intent.requestedQuantity() > sourceBefore.getQuantity()) {
            return reject(operationId, intent, ResultType.STALE,
                    "AUTHORITATIVE_QUANTITY_BOUNDS", sourceBefore, targetBefore);
        }
        boolean targetOccupied = !ItemStack.isEmpty(targetBefore);
        boolean compatibleMerge = targetOccupied
                && sourceBefore.isStackableWith(targetBefore);
        boolean swap = targetOccupied && !compatibleMerge;
        if (swap && intent.requestedQuantity() != sourceBefore.getQuantity()) {
            return reject(operationId, intent, ResultType.INVALID,
                    "PARTIAL_STACK_CANNOT_SWAP_OCCUPIED_DESTINATION",
                    sourceBefore, targetBefore);
        }

        String fingerprint = fingerprint(intent, sourceBefore, targetBefore);
        long now = System.nanoTime();
        recentFingerprints.entrySet().removeIf(entry ->
                now - entry.getValue() > DUPLICATE_WINDOW_NANOS);
        Long prior = recentFingerprints.putIfAbsent(fingerprint, now);
        if (prior != null && now - prior <= DUPLICATE_WINDOW_NANOS) {
            diagnostics.accept(marker("CUSTOM_BRIDGE_DUPLICATE_SUPPRESSED",
                    operationId, intent) + " fingerprint=" + quoted(fingerprint));
            duplicateOperations.incrementAndGet();
            return new BridgeResult(operationId, ResultType.DUPLICATE,
                    "DUPLICATE_RELEASE", stack(sourceBefore), stack(targetBefore),
                    stack(sourceBefore), stack(targetBefore));
        }

        diagnostics.accept(marker("CUSTOM_BRIDGE_VALIDATED", operationId, intent)
                + " player=" + viewer.getUuid()
                + " sourceIdentity=" + identity(source, npcSection)
                + " targetIdentity=" + identity(target, npcSection)
                + " authoritativeSourceBefore=" + stack(sourceBefore)
                + " authoritativeTargetBefore=" + stack(targetBefore)
                + " operation=" + (swap ? "SWAP" : compatibleMerge ? "MERGE" : "MOVE")
                + " mutationThreadVerified=" + store.isInThread());
        boolean nativeSucceeded;
        String nativeApi;
        try {
            if (swap) {
                nativeApi = "ItemContainer.swapItems";
                nativeSucceeded = source.swapItems(
                        (short) intent.sourceSlotId(), target,
                        (short) intent.targetSlotId(), (short) 1).succeeded();
            } else {
                nativeApi = "ItemContainer.moveItemStackFromSlotToSlot";
                nativeSucceeded = source.moveItemStackFromSlotToSlot(
                        (short) intent.sourceSlotId(), intent.requestedQuantity(),
                        target, (short) intent.targetSlotId()).succeeded();
            }
        } catch (RuntimeException failure) {
            diagnostics.accept(marker("CUSTOM_BRIDGE_NATIVE_EXCEPTION", operationId, intent)
                    + " operation=" + (swap ? "SWAP" : compatibleMerge ? "MERGE" : "MOVE")
                    + " exception=" + quoted(failure.toString()));
            return reject(operationId, intent, ResultType.REJECTED,
                    "NATIVE_TRANSACTION_EXCEPTION", sourceBefore, targetBefore);
        }
        diagnostics.accept(marker("CUSTOM_BRIDGE_NATIVE_MOVE", operationId, intent)
                + " api=" + nativeApi
                + " nativeTransactionSucceeded=" + nativeSucceeded
                + " manualStackMutation=false");

        ItemStack sourceAfter = source.getItemStack((short) intent.sourceSlotId());
        ItemStack targetAfter = target.getItemStack((short) intent.targetSlotId());
        boolean committed = nativeSucceeded && (swap
                ? validSwap(sourceBefore, targetBefore, sourceAfter, targetAfter)
                : validMove(sourceBefore, targetBefore, sourceAfter, targetAfter,
                        intent.requestedQuantity()));
        ResultType type = committed ? ResultType.COMMITTED : ResultType.REJECTED;
        String reason = committed
                ? "AUTHORITATIVE_" + (swap ? "SWAP" : compatibleMerge ? "MERGE" : "MOVE")
                        + "_COMMITTED"
                : "NATIVE_TRANSACTION_REJECTED_OR_POSTSTATE_MISMATCH";
        if (committed) {
            committedOperations.incrementAndGet();
        } else {
            rejectedOperations.incrementAndGet();
            if (nativeSucceeded) invariantViolations.incrementAndGet();
        }
        diagnostics.accept(marker("CUSTOM_BRIDGE_NATIVE_RESULT", operationId, intent)
                + " nativeResult=" + type
                + " reason=" + reason
                + " authoritativeSourceBefore=" + stack(sourceBefore)
                + " authoritativeTargetBefore=" + stack(targetBefore)
                + " authoritativeSourceAfter=" + stack(sourceAfter)
                + " authoritativeTargetAfter=" + stack(targetAfter));
        return new BridgeResult(operationId, type, reason,
                stack(sourceBefore), stack(targetBefore),
                stack(sourceAfter), stack(targetAfter));
    }

    private BridgeResult reject(long operationId, InventoryMoveIntent intent,
            ResultType type, String reason, ItemStack source, ItemStack target) {
        if (type == ResultType.STALE) staleOperations.incrementAndGet();
        else if (type != ResultType.NO_OP && type != ResultType.DUPLICATE) {
            rejectedOperations.incrementAndGet();
        }
        diagnostics.accept(marker("CUSTOM_BRIDGE_REJECTED", operationId, intent)
                + " result=" + type
                + " reason=" + reason
                + " authoritativeSource=" + stack(source)
                + " authoritativeTarget=" + stack(target));
        return new BridgeResult(operationId, type, reason,
                stack(source), stack(target), stack(source), stack(target));
    }

    private boolean expectedIdentity(ItemContainer value, int section, int npcSection) {
        if (section == InventoryComponent.STORAGE_SECTION_ID) {
            return value == playerStorage;
        }
        return section == npcSection && value == npcInventory;
    }

    private String identity(ItemContainer value, int npcSection) {
        if (value == playerStorage) return "PLAYER_STORAGE_-2";
        if (value == npcInventory) return "NPC_WINDOW_" + npcSection;
        return value == null ? "null" : value.getClass().getName()
                + '@' + Integer.toHexString(System.identityHashCode(value));
    }

    private static boolean allowedSection(int section, int npcSection) {
        return section == InventoryComponent.STORAGE_SECTION_ID
                || section == npcSection;
    }

    private static boolean validSlot(int slot, ItemContainer container) {
        return slot >= 0 && slot < container.getCapacity();
    }

    private static String fingerprint(InventoryMoveIntent intent,
            ItemStack sourceBefore, ItemStack targetBefore) {
        return intent.pageGeneration() + ":" + intent.sourceSectionId() + ':'
                + intent.sourceSlotId() + ':' + intent.targetSectionId() + ':'
                + intent.targetSlotId() + ':' + intent.requestedQuantity() + ':'
                + intent.mouseButton() + ':' + stack(sourceBefore) + ':'
                + stack(targetBefore);
    }

    private static boolean validMove(ItemStack sourceBefore, ItemStack targetBefore,
            ItemStack sourceAfter, ItemStack targetAfter, int requestedQuantity) {
        int sourceAfterQuantity = compatibleQuantity(sourceBefore, sourceAfter);
        if (sourceAfterQuantity < 0 || sourceAfterQuantity > sourceBefore.getQuantity()) {
            return false;
        }
        int moved = sourceBefore.getQuantity() - sourceAfterQuantity;
        if (moved <= 0 || moved > requestedQuantity) return false;

        if (ItemStack.isEmpty(targetBefore)) {
            return !ItemStack.isEmpty(targetAfter)
                    && sourceBefore.isStackableWith(targetAfter)
                    && targetAfter.getQuantity() == moved;
        }
        return sourceBefore.isStackableWith(targetBefore)
                && !ItemStack.isEmpty(targetAfter)
                && sourceBefore.isStackableWith(targetAfter)
                && targetAfter.getQuantity() - targetBefore.getQuantity() == moved;
    }

    private static boolean validSwap(ItemStack sourceBefore, ItemStack targetBefore,
            ItemStack sourceAfter, ItemStack targetAfter) {
        return Objects.equals(sourceBefore, targetAfter)
                && Objects.equals(targetBefore, sourceAfter);
    }

    private static int compatibleQuantity(ItemStack expected, ItemStack actual) {
        if (ItemStack.isEmpty(actual)) return 0;
        return expected.isStackableWith(actual) ? actual.getQuantity() : -1;
    }

    private static String marker(String marker, long operationId,
            InventoryMoveIntent intent) {
        return marker
                + " timestamp=" + Instant.now()
                + " BridgeOperationId=" + operationId
                + " sessionId=" + intent.sessionId()
                + " pageGeneration=" + intent.pageGeneration()
                + " eventSequence=" + intent.eventSequence()
                + " sourceSection=" + intent.sourceSectionId()
                + " sourceSlot=" + intent.sourceSlotId()
                + " destinationSection=" + intent.targetSectionId()
                + " destinationSlot=" + intent.targetSlotId()
                + " requestedQuantity=" + intent.requestedQuantity()
                + " mouseButton=" + intent.mouseButton();
    }

    private static String stack(ItemStack value) {
        return ItemStack.isEmpty(value) ? "EMPTY"
                : value.getItemId() + 'x' + value.getQuantity();
    }

    private static String quoted(String value) {
        if (value == null) return "null";
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
