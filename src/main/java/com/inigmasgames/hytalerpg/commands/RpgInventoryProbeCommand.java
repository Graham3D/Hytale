package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.ui.inventory.NativeInventoryEntryProbe;
import com.inigmasgames.hytalerpg.ui.inventory.FootprintCatalog;
import com.inigmasgames.hytalerpg.ui.inventory.SpatialMigrationPreflight;
import com.inigmasgames.hytalerpg.ui.inventory.NativeSpatialAlias;
import com.inigmasgames.hytalerpg.ui.inventory.NativeSpatialDragProbePage;
import com.inigmasgames.hytalerpg.gear.GearNativeItems;
import com.inigmasgames.canvasui.CanvasUI;
import java.util.ArrayList;

/** Operator-only command, registered only for saves with the operator probe marker. */
public final class RpgInventoryProbeCommand extends AbstractPlayerCommand {
    private final NativeInventoryEntryProbe probe;
    private final RequiredArg<String> mode = withRequiredArg("mode", "observe | redirect | open | drag | native | preflight | off", ArgTypes.STRING);
    public RpgInventoryProbeCommand(NativeInventoryEntryProbe probe) {
        super("inventoryprobe", "Inventory capability and read-only migration probe.");
        this.probe = probe;
    }
    @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                     PlayerRef player, World world) {
        String requested = mode.get(context);
        switch (requested) {
            case "observe" -> probe.arm(player, NativeInventoryEntryProbe.Mode.OBSERVE);
            case "redirect" -> probe.arm(player, NativeInventoryEntryProbe.Mode.REDIRECT);
            case "open" -> {
                if (!probe.aliasActive(player)) probe.arm(player, NativeInventoryEntryProbe.Mode.OBSERVE);
                probe.open(player, ref, store, "COMMAND_DIAGNOSTIC");
            }
            case "drag" -> {
                var storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
                var entity = store.getComponent(ref, Player.getComponentType());
                if (storage == null || entity == null) {
                    context.sendMessage(Message.raw("Native Storage or player component unavailable.")); return;
                }
                String itemId = null;
                var catalog = FootprintCatalog.loadDefault();
                var nativeBag = storage.getInventory();
                for (short slot = 0; slot < nativeBag.getCapacity(); slot++) {
                    var stack = nativeBag.getItemStack(slot);
                    if (ItemStack.isEmpty(stack)) continue;
                    var size = catalog.size(GearNativeItems.nativeId(stack.getItemId()));
                    if (size != null && size.width() == 2 && size.height() == 4) {
                        itemId = stack.getItemId(); break;
                    }
                }
                if (itemId == null) {
                    context.sendMessage(Message.raw("No mapped 2x4 item is currently in native Storage.")); return;
                }
                var result = CanvasUI.openInventoryDragProof(entity, player, world, store, ref, itemId);
                context.sendMessage(Message.raw(result.message() + " This is a separate read-only drag surface; native items stay unchanged."));
                return;
            }
            case "native" -> {
                var storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
                var entity = store.getComponent(ref, Player.getComponentType());
                if (storage == null || entity == null) {
                    context.sendMessage(Message.raw("Native Storage or player component unavailable.")); return;
                }
                if (entity.getPageManager().getCustomPage() != null) {
                    context.sendMessage(Message.raw("Close the current custom page before opening the native drag test.")); return;
                }
                var catalog = FootprintCatalog.loadDefault();
                var nativeBag = storage.getInventory();
                short bowSlot = -1;
                ItemStack bow = null;
                for (short slot = 0; slot < nativeBag.getCapacity(); slot++) {
                    var stack = nativeBag.getItemStack(slot);
                    if (ItemStack.isEmpty(stack)) continue;
                    var size = catalog.size(GearNativeItems.nativeId(stack.getItemId()));
                    if (size != null && size.width() == 2 && size.height() == 4) {
                        bowSlot = slot; bow = stack; break;
                    }
                }
                if (bow == null) {
                    context.sendMessage(Message.raw("No mapped 2x4 item is currently in native Storage.")); return;
                }
                SimpleItemContainer virtualBag = new SimpleItemContainer((short) NativeSpatialAlias.CELLS);
                virtualBag.setGlobalFilter(FilterType.DENY_ALL);
                ContainerWindow window = new ContainerWindow(virtualBag);
                var page = new NativeSpatialDragProbePage(player, probe, window, virtualBag, bowSlot, bow);
                try {
                    if (!entity.getPageManager().openCustomPageWithWindows(ref, store, page, window)) {
                        page.onDismiss(ref, store);
                        probe.record(player, "NATIVE_ALIAS_OPEN_FAILED", java.util.Map.of("reason", "PAGE_REJECTED"));
                        context.sendMessage(Message.raw("Native ItemGrid drag test could not open.")); return;
                    }
                } catch (RuntimeException error) {
                    page.onDismiss(ref, store);
                    probe.record(player, "NATIVE_ALIAS_OPEN_FAILED", java.util.Map.of("reason",
                            error.getClass().getSimpleName(), "detail", String.valueOf(error.getMessage())));
                    context.sendMessage(Message.raw("Native ItemGrid drag test rejected: "
                            + error.getClass().getSimpleName() + ". Check the server log."));
                    return;
                }
                context.sendMessage(Message.raw("Native ItemGrid alias test opened. Try click-click and hold-drag"
                        + " from each bow cell. The 72-slot test container is empty and denies all mutations;"
                        + " your real bow remains in native Storage."));
                return;
            }
            case "off" -> probe.detach(player.getUuid());
            case "preflight" -> {
                var storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
                if (storage == null) { context.sendMessage(Message.raw("No native Storage component found.")); return; }
                var inputs = new ArrayList<SpatialMigrationPreflight.Input>();
                var nativeBag = storage.getInventory();
                for (short slot = 0; slot < nativeBag.getCapacity(); slot++) {
                    var stack = nativeBag.getItemStack(slot);
                    if (!ItemStack.isEmpty(stack)) inputs.add(new SpatialMigrationPreflight.Input(slot,
                            GearNativeItems.nativeId(stack.getItemId())));
                }
                var result = SpatialMigrationPreflight.plan(18, 4, FootprintCatalog.loadDefault(), inputs);
                long unknown = result.overflow().stream().filter(v -> v.reason() == SpatialMigrationPreflight.Reason.UNMAPPED).count();
                context.sendMessage(Message.raw("Spatial dry-run: " + result.placed().size() + " placed, "
                        + unknown + " unmapped, " + (result.overflow().size() - unknown)
                        + " without a rectangle. Native Storage unchanged."));
                if (unknown > 0) context.sendMessage(Message.raw("Unmapped base IDs: " + result.overflow().stream()
                        .filter(v -> v.reason() == SpatialMigrationPreflight.Reason.UNMAPPED)
                        .limit(8).map(SpatialMigrationPreflight.Overflow::baseItemId).toList()));
                return;
            }
            default -> { context.sendMessage(Message.raw("Use observe, redirect, open, drag, native, preflight, or off.")); return; }
        }
        context.sendMessage(Message.raw(switch (requested) {
            case "redirect" -> "Inventory redirect armed. Close chat and press your Inventory key to check the entry path.";
            case "open" -> "Inventory diagnostic opened. Spatial pickup and native-entry acceptance remain pending.";
            case "observe" -> "Inventory entry observation armed. Close chat and press your Inventory key.";
            default -> "Inventory probe disabled for this session.";
        }));
    }
}
