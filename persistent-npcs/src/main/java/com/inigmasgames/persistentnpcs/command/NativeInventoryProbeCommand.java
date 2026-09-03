package com.inigmasgames.persistentnpcs.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.window.OpenWindow;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.WindowManager;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.persistentnpcs.ui.NativeInventoryProbePage;
import com.inigmasgames.persistentnpcs.ui.CustomInventoryBridgeProbePage;
import com.inigmasgames.persistentnpcs.ui.CustomGridDifferentialProbePage;
import com.inigmasgames.persistentnpcs.ui.NativeInventoryControlWindow;
import com.inigmasgames.persistentnpcs.ui.NativeNpcInventoryProbeWindow;
import com.inigmasgames.persistentnpcs.ui.NativeNpcInventoryController;
import com.inigmasgames.persistentnpcs.hytale.HytaleNpcAdapter;
import com.inigmasgames.persistentnpcs.profile.NpcInventoryRepository;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import java.util.List;
import java.util.function.Consumer;

/** Plugin-owned connected-validation command; production NPC Profile is untouched. */
public final class NativeInventoryProbeCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> variantArg;
    private final NpcProfileRegistry profiles;
    private final HytaleNpcAdapter npcAdapter;
    private final NpcInventoryRepository npcInventories;
    private final Consumer<String> diagnostics;

    public NativeInventoryProbeCommand(Consumer<String> diagnostics) {
        this(null, null, null, diagnostics);
    }

    public NativeInventoryProbeCommand(
            NpcProfileRegistry profiles,
            HytaleNpcAdapter npcAdapter,
            NpcInventoryRepository npcInventories,
            Consumer<String> diagnostics) {
        super("nativeinventoryprobe",
                "Open the isolated native ItemGrid/ContainerWindow validation probe");
        this.profiles = profiles;
        this.npcAdapter = npcAdapter;
        this.npcInventories = npcInventories;
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
        this.variantArg = withRequiredArg("variant",
                "Native inventory probe: 1 through 8, 10 [mode], 11, or npc <name>",
                ArgTypes.GREEDY_STRING);
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store,
            Ref<EntityStore> playerEntityRef, PlayerRef playerRef, World world) {
        NativeInventoryProbePage page = null;
        try {
            String request = context.get(variantArg);
            Player player = store.getComponent(playerEntityRef, Player.getComponentType());
            if (player == null) throw new IllegalStateException("Player managers unavailable.");
            InventoryComponent.Storage storage = store.getComponent(
                    playerEntityRef, InventoryComponent.Storage.getComponentType());
            if (storage == null || storage.getInventory() == null) {
                throw new IllegalStateException("Player Storage inventory unavailable.");
            }

            if (isNpcRequest(request)) {
                openLiveNpcControl(context, request, player, playerEntityRef, playerRef,
                        store, storage.getInventory());
                return;
            }

            if (isDifferentialRequest(request)) {
                openCustomGridDifferential(context, request, player, playerEntityRef,
                        playerRef, store, storage.getInventory());
                return;
            }

            if (isBridgeRequest(request)) {
                openCustomInventoryBridge(context, player, playerEntityRef,
                        playerRef, store, storage.getInventory());
                return;
            }

            int variant = parseVariant(request);

            SimpleItemContainer npcInventory = new SimpleItemContainer((short) 40);
            if (variant == 8) {
                openNativeControl(context, player, playerEntityRef, playerRef, store,
                        npcInventory, storage.getInventory());
                return;
            }
            ContainerWindow npcWindow = new ContainerWindow(npcInventory);
            page = new NativeInventoryProbePage(
                    playerRef, variant, npcWindow, npcInventory,
                    storage.getInventory(), diagnostics);
            WindowManager windows = player.getWindowManager();
            diagnostics.accept("NATIVE_INVENTORY_PROBE_BEGIN variant=" + variant
                    + " viewer=" + playerRef.getUuid()
                    + " npcCapacity=" + npcInventory.getCapacity()
                    + " playerStorageCapacity=" + storage.getInventory().getCapacity()
                    + " playerStorageSectionId=" + InventoryComponent.STORAGE_SECTION_ID
                    + " transferAuthority=InventoryUtils.moveItem customHandler=false");

            switch (variant) {
                case 1 -> openControl(player, playerEntityRef, store, page, npcWindow);
                case 2 -> openRegisteredThenPage(player, playerRef, playerEntityRef,
                        store, page, windows, npcWindow);
                case 3 -> openLiteralAtConstruction(player, playerEntityRef,
                        store, page, npcWindow);
                case 4 -> openMaterializedNativeGrid(player, playerEntityRef,
                        store, page, npcWindow);
                case 5 -> openIndexedNativeGrid(player, playerEntityRef,
                        store, page, npcWindow);
                case 6 -> openWindowThenIndexedPage(player, playerRef,
                        playerEntityRef, store, page, windows, npcWindow);
                case 7 -> openNativeItemStacksGrid(player, playerEntityRef,
                        store, page, npcWindow);
                default -> throw new IllegalStateException("Unsupported variant " + variant);
            }
            context.sendMessage(Message.raw("Native inventory Probe " + variant
                    + " opened. LEFT is an ephemeral 40-slot container; RIGHT is your"
                    + " Storage. Close the page to discard the test container."));
        } catch (RuntimeException failure) {
            if (page != null) page.onDismiss(playerEntityRef, store);
            String message = failure.getMessage() == null
                    ? failure.getClass().getSimpleName() : failure.getMessage();
            diagnostics.accept("NATIVE_INVENTORY_PROBE_FAILED reason=" + message);
            context.sendMessage(Message.raw("Native inventory probe failed: " + message));
        }
    }

    /** Option 2: isolated server-authoritative bridge over the exact Probe 5 grids. */
    private void openCustomInventoryBridge(CommandContext context, Player player,
            Ref<EntityStore> ref, PlayerRef playerRef, Store<EntityStore> store,
            com.hypixel.hytale.server.core.inventory.container.ItemContainer playerStorage) {
        SimpleItemContainer npcInventory = new SimpleItemContainer((short) 40);
        ContainerWindow npcWindow = new ContainerWindow(npcInventory);
        CustomInventoryBridgeProbePage page = new CustomInventoryBridgeProbePage(
                playerRef, npcWindow, npcInventory, playerStorage, diagnostics);
        diagnostics.accept("CUSTOM_BRIDGE_PROBE11_BEGIN"
                + " viewerUuid=" + playerRef.getUuid()
                + " baseline=PROBE_5"
                + " pageApi=PageManager.openCustomPageWithWindows"
                + " container=EPHEMERAL_SIMPLE_ITEM_CONTAINER_40"
                + " nativeWindow=ContainerWindow"
                + " mutationAuthority=InventoryUtils.moveItem"
                + " productionNpcProfileModified=false");
        try {
            if (!player.getPageManager().openCustomPageWithWindows(
                    ref, store, page, npcWindow)) {
                throw new IllegalStateException(
                        "openCustomPageWithWindows rejected Probe 11.");
            }
        } catch (RuntimeException failure) {
            page.onDismiss(ref, store);
            throw failure;
        }
        diagnostics.accept("CUSTOM_BRIDGE_PROBE11_OPENED"
                + " viewerUuid=" + playerRef.getUuid()
                + " npcWindowId=" + npcWindow.getId()
                + " wireOrder=CUSTOM_PAGE_THEN_OPEN_WINDOW"
                + " operationGate=LEFT_CLICK_FULL_STACK_TO_EMPTY_SLOT_ONLY"
                + " reconciliationDifferential=A_ATOMIC_BOTH_SLOTS_REFRESH");
        context.sendMessage(Message.raw("Custom inventory bridge Probe 11 opened."
                + " Test only a left-click full-stack move into an empty slot,"
                + " in either direction. Remaining probe items are recovered on close."));
    }

    /** Exact Probe 5 construction plus observation-only differential telemetry. */
    private void openCustomGridDifferential(CommandContext context, String request,
            Player player, Ref<EntityStore> ref, PlayerRef playerRef,
            Store<EntityStore> store,
            com.hypixel.hytale.server.core.inventory.container.ItemContainer playerStorage) {
        String[] parts = request == null ? new String[0] : request.strip().split("\\s+", 2);
        String modeToken = parts.length < 2 ? "baseline" : parts[1];
        CustomGridDifferentialProbePage.Mode mode =
                CustomGridDifferentialProbePage.Mode.parse(modeToken);
        SimpleItemContainer npcInventory = new SimpleItemContainer((short) 40);
        ContainerWindow npcWindow = new ContainerWindow(npcInventory);
        CustomGridDifferentialProbePage page = new CustomGridDifferentialProbePage(
                playerRef, mode, npcWindow, npcInventory, playerStorage, diagnostics);
        diagnostics.accept("CUSTOM_GRID_PROBE10_BEGIN"
                + " mode=" + mode.commandToken()
                + " viewerUuid=" + playerRef.getUuid()
                + " baseline=PROBE_5"
                + " pageApi=PageManager.openCustomPageWithWindows"
                + " customTransferHandler=false"
                + " packetWatcher=PASSIVE_INBOUND");
        try {
            if (!player.getPageManager().openCustomPageWithWindows(
                    ref, store, page, npcWindow)) {
                throw new IllegalStateException(
                        "openCustomPageWithWindows rejected Probe 10.");
            }
        } catch (RuntimeException failure) {
            page.onDismiss(ref, store);
            throw failure;
        }
        diagnostics.accept("CUSTOM_GRID_PROBE10_OPENED"
                + " mode=" + mode.commandToken()
                + " npcWindowId=" + npcWindow.getId()
                + " wireOrder=CUSTOM_PAGE_THEN_OPEN_WINDOW"
                + " observationOnly=true");
        context.sendMessage(Message.raw("Custom grid differential Probe 10 ("
                + mode.commandToken() + ") opened from the exact Probe 5 baseline."
                + " Remaining test-container items are recovered on close."));
    }

    /** Exact native control over the target NPC's authoritative ECS Storage. */
    private void openLiveNpcControl(
            CommandContext context,
            String request,
            Player player,
            Ref<EntityStore> viewerRef,
            PlayerRef playerRef,
            Store<EntityStore> store,
            com.hypixel.hytale.server.core.inventory.container.ItemContainer playerStorage) {
        if (profiles == null || npcAdapter == null || npcInventories == null) {
            throw new IllegalStateException("Live NPC inventory probe dependencies unavailable.");
        }
        String requestedName = request.strip().substring(3).strip();
        if (requestedName.isBlank()) {
            throw new IllegalArgumentException("Usage: /nativeinventoryprobe npc <name>");
        }
        String sanitized = ProfileRepository.sanitizeProfileName(requestedName);
        NpcProfile profile = profiles.requireName(sanitized);
        NativeNpcInventoryController.open("PROBE_COMMAND", profile, npcAdapter,
                npcInventories, player, viewerRef, playerRef, store,
                playerStorage, diagnostics);
    }

    /** Exact current-build InventorySeeCommand/chest architecture. */
    private void openNativeControl(CommandContext context, Player player,
            Ref<EntityStore> ref, PlayerRef playerRef, Store<EntityStore> store,
            SimpleItemContainer npcInventory,
            com.hypixel.hytale.server.core.inventory.container.ItemContainer playerStorage) {
        NativeInventoryControlWindow window = new NativeInventoryControlWindow(
                npcInventory, playerStorage, diagnostics);
        diagnostics.accept("NATIVE_INVENTORY_CONTROL_BEGIN variant=8"
                + " viewerUuid=" + playerRef.getUuid()
                + " viewerEntityRef=" + ref
                + " page=Bench"
                + " pageApi=PageManager.setPageWithWindows"
                + " playerSurface=NATIVE_BENCH_INVENTORY"
                + " targetWindow=ContainerWindow"
                + " targetContainer=SimpleItemContainer"
                + " targetCapacity=" + npcInventory.getCapacity()
                + " customPage=false customItemGrid=false customTransferHandler=false");
        boolean opened = player.getPageManager().setPageWithWindows(
                ref, store, Page.Bench, true, window);
        if (!opened) {
            throw new IllegalStateException("Native Page.Bench control rejected the window.");
        }
        diagnostics.accept("NATIVE_INVENTORY_CONTROL_OPENED variant=8"
                + " npcWindowId=" + window.getId()
                + " wireOrder=SET_PAGE_THEN_OPEN_WINDOW"
                + " implementationClonedFrom=InventorySeeCommand");
        context.sendMessage(Message.raw("Native inventory Probe 8 opened with Hytale's"
                + " native Bench/ContainerWindow page. Test both directions and close"
                + " the page normally; remaining test-container items are recovered."));
    }

    private void openControl(Player player, Ref<EntityStore> ref, Store<EntityStore> store,
            NativeInventoryProbePage page, ContainerWindow npcWindow) {
        diagnostics.accept("NATIVE_INVENTORY_PROBE_ORDER variant=1 step=CALL_OPEN_CUSTOM_PAGE_WITH_WINDOWS");
        if (!player.getPageManager().openCustomPageWithWindows(ref, store, page, npcWindow)) {
            throw new IllegalStateException("openCustomPageWithWindows rejected the probe.");
        }
        diagnostics.accept("NATIVE_INVENTORY_PROBE_ORDER variant=1 step=RETURNED"
                + " serverWindowRegisteredBeforeBuild=true"
                + " wireOrder=CUSTOM_PAGE_THEN_OPEN_WINDOW npcWindowId=" + npcWindow.getId());
        page.rebindAfterOpen();
    }

    private void openRegisteredThenPage(Player player, PlayerRef playerRef,
            Ref<EntityStore> ref, Store<EntityStore> store, NativeInventoryProbePage page,
            WindowManager windows, ContainerWindow npcWindow) {
        List<OpenWindow> packets = allocate(windows, ref, store, npcWindow, 2);
        diagnostics.accept("NATIVE_INVENTORY_PROBE_ORDER variant=2 step=WINDOW_REGISTERED"
                + " npcWindowId=" + npcWindow.getId());
        player.getPageManager().openCustomPage(ref, store, page);
        diagnostics.accept("NATIVE_INVENTORY_PROBE_ORDER variant=2 step=CUSTOM_PAGE_PACKET_SENT"
                + " initialNpcSectionId=" + npcWindow.getId());
        sendOpenWindowPackets(playerRef, packets, 2);
    }

    private void openLiteralAtConstruction(Player player, Ref<EntityStore> ref,
            Store<EntityStore> store, NativeInventoryProbePage page,
            ContainerWindow npcWindow) {
        diagnostics.accept("NATIVE_INVENTORY_PROBE_ORDER variant=3"
                + " step=CALL_OPEN_CUSTOM_PAGE_WITH_WINDOWS"
                + " gridBinding=LITERAL_AT_ELEMENT_CREATION");
        if (!player.getPageManager().openCustomPageWithWindows(ref, store, page, npcWindow)) {
            throw new IllegalStateException("openCustomPageWithWindows rejected the probe.");
        }
        diagnostics.accept("NATIVE_INVENTORY_PROBE_ORDER variant=3 step=RETURNED"
                + " serverWindowRegisteredBeforeBuild=true"
                + " wireOrder=CUSTOM_PAGE_THEN_OPEN_WINDOW"
                + " initialNpcSectionId=" + npcWindow.getId());
    }

    private void openMaterializedNativeGrid(Player player, Ref<EntityStore> ref,
            Store<EntityStore> store, NativeInventoryProbePage page,
            ContainerWindow npcWindow) {
        diagnostics.accept("NATIVE_INVENTORY_PROBE_ORDER variant=4"
                + " step=CALL_OPEN_CUSTOM_PAGE_WITH_WINDOWS"
                + " gridBinding=LITERAL_SECTION_WITH_INITIAL_SLOTS");
        if (!player.getPageManager().openCustomPageWithWindows(ref, store, page, npcWindow)) {
            throw new IllegalStateException("openCustomPageWithWindows rejected the probe.");
        }
        diagnostics.accept("NATIVE_INVENTORY_PROBE_ORDER variant=4 step=RETURNED"
                + " serverWindowRegisteredBeforeBuild=true"
                + " wireOrder=CUSTOM_PAGE_THEN_OPEN_WINDOW"
                + " initialNpcSectionId=" + npcWindow.getId());
    }

    private void openIndexedNativeGrid(Player player, Ref<EntityStore> ref,
            Store<EntityStore> store, NativeInventoryProbePage page,
            ContainerWindow npcWindow) {
        diagnostics.accept("NATIVE_INVENTORY_PROBE_ORDER variant=5"
                + " step=CALL_OPEN_CUSTOM_PAGE_WITH_WINDOWS"
                + " gridBinding=LITERAL_SECTION_WITH_INDEXED_ACTIVATABLE_SLOTS");
        if (!player.getPageManager().openCustomPageWithWindows(ref, store, page, npcWindow)) {
            throw new IllegalStateException("openCustomPageWithWindows rejected the probe.");
        }
        diagnostics.accept("NATIVE_INVENTORY_PROBE_ORDER variant=5 step=RETURNED"
                + " serverWindowRegisteredBeforeBuild=true"
                + " wireOrder=CUSTOM_PAGE_THEN_OPEN_WINDOW"
                + " nativeSlotIdentity=InventorySlotIndex"
                + " initialNpcSectionId=" + npcWindow.getId());
    }

    private void openWindowThenIndexedPage(Player player, PlayerRef playerRef,
            Ref<EntityStore> ref, Store<EntityStore> store,
            NativeInventoryProbePage page, WindowManager windows,
            ContainerWindow npcWindow) {
        diagnostics.accept("NATIVE_INVENTORY_PROBE_ORDER variant=6"
                + " step=ALLOCATE_REGISTER_CONTAINER_WINDOW");
        List<OpenWindow> packets = allocate(windows, ref, store, npcWindow, 6);
        sendOpenWindowPackets(playerRef, packets, 6);
        diagnostics.accept("NATIVE_INVENTORY_PROBE_ORDER variant=6"
                + " step=OPEN_WINDOW_ON_WIRE_BEFORE_CUSTOM_PAGE"
                + " npcWindowId=" + npcWindow.getId());
        player.getPageManager().openCustomPage(ref, store, page);
        diagnostics.accept("NATIVE_INVENTORY_PROBE_ORDER variant=6 step=RETURNED"
                + " serverWindowRegisteredBeforeBuild=true"
                + " wireOrder=OPEN_WINDOW_THEN_CUSTOM_PAGE"
                + " nativeSlotIdentity=InventorySlotIndex"
                + " initialNpcSectionId=" + npcWindow.getId());
    }

    private void openNativeItemStacksGrid(Player player, Ref<EntityStore> ref,
            Store<EntityStore> store, NativeInventoryProbePage page,
            ContainerWindow npcWindow) {
        diagnostics.accept("NATIVE_INVENTORY_PROBE_ORDER variant=7"
                + " step=CALL_OPEN_CUSTOM_PAGE_WITH_WINDOWS"
                + " gridBinding=LITERAL_SECTION_WITH_NATIVE_ITEM_STACKS"
                + " emptyEncoding=BSON_NULL slotIdentity=ARRAY_INDEX");
        if (!player.getPageManager().openCustomPageWithWindows(ref, store, page, npcWindow)) {
            throw new IllegalStateException("openCustomPageWithWindows rejected the probe.");
        }
        diagnostics.accept("NATIVE_INVENTORY_PROBE_ORDER variant=7 step=RETURNED"
                + " serverWindowRegisteredBeforeBuild=true"
                + " wireOrder=CUSTOM_PAGE_THEN_OPEN_WINDOW"
                + " initialNpcSectionId=" + npcWindow.getId());
    }

    private List<OpenWindow> allocate(WindowManager windows, Ref<EntityStore> ref,
            Store<EntityStore> store, ContainerWindow npcWindow, int variant) {
        List<OpenWindow> packets = windows.openWindows(ref, store, npcWindow);
        if (packets == null || packets.size() != 1 || npcWindow.getId() < 0) {
            throw new IllegalStateException("WindowManager could not allocate/register ContainerWindow.");
        }
        diagnostics.accept("NATIVE_INVENTORY_PROBE_ALLOCATED variant=" + variant
                + " allocator=WindowManager.openWindows npcWindowId=" + npcWindow.getId()
                + " packetCount=" + packets.size() + " manuallyInventedId=false");
        return packets;
    }

    private void sendOpenWindowPackets(PlayerRef playerRef, List<OpenWindow> packets,
            int variant) {
        for (OpenWindow packet : packets) {
            playerRef.getPacketHandler().write(packet);
            diagnostics.accept("NATIVE_INVENTORY_PROBE_ORDER variant=" + variant
                    + " step=OPEN_WINDOW_PACKET_SENT packetWindowId=" + packet.id
                    + " hasInventory=" + (packet.inventory != null));
        }
    }

    private static int parseVariant(String raw) {
        try {
            int variant = Integer.parseInt(raw == null ? "" : raw.trim());
            if (variant >= 1 && variant <= 8) return variant;
        } catch (NumberFormatException ignored) {
            // Uniform user-facing error below.
        }
        throw new IllegalArgumentException(
                "Variant must be between 1 and 8, Probe 10 with a valid mode,"
                        + " Probe 11, or npc <name>.");
    }

    private static boolean isDifferentialRequest(String raw) {
        if (raw == null) return false;
        String value = raw.strip();
        return value.equals("10") || value.startsWith("10 ");
    }

    private static boolean isBridgeRequest(String raw) {
        return raw != null && raw.strip().equals("11");
    }

    private static boolean isNpcRequest(String raw) {
        if (raw == null) return false;
        String value = raw.strip();
        return value.equalsIgnoreCase("npc")
                || value.regionMatches(true, 0, "npc ", 0, 4);
    }
}
