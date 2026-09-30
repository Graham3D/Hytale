package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.packets.window.WindowType;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.inigmasgames.hytalerpg.ui.RpgUiProjectionService;
import com.inigmasgames.hytalerpg.progress.AttributeAllocationService;
import com.inigmasgames.hytalerpg.ui.skilltree.SkillTreeEntryAdapter;
import com.inigmasgames.hytalerpg.gear.HytaleGearLoot;

/** Temporary development entry; a future Page.Inventory bridge can replace only this adapter. */
public final class CommandInventoryEntryAdapter implements InventoryEntryAdapter {
    private final RpgUiProjectionService projection;
    private final AttributeAllocationService allocation;
    private final SkillTreeEntryAdapter skillTree;
    private java.util.function.Consumer<java.util.UUID> dismissListener = ignored -> {};
    private java.util.function.Supplier<HytaleGearLoot> gearTransfer = () -> null;

    public CommandInventoryEntryAdapter(RpgUiProjectionService projection, AttributeAllocationService allocation,
                                        SkillTreeEntryAdapter skillTree) {
        this.projection = projection;
        this.allocation = allocation;
        this.skillTree = skillTree;
    }

    @Override public boolean open(PlayerRef player, Ref<EntityStore> ref, Store<EntityStore> store) {
        return open(player, ref, store, () -> {});
    }

    public void onDismiss(java.util.function.Consumer<java.util.UUID> listener) {
        dismissListener = java.util.Objects.requireNonNull(listener);
    }

    public void configureGearTransfer(java.util.function.Supplier<HytaleGearLoot> transfer) {
        gearTransfer = java.util.Objects.requireNonNull(transfer);
    }

    public boolean open(PlayerRef player, Ref<EntityStore> ref, Store<EntityStore> store, Runnable entryDismiss) {
        var entity = store.getComponent(ref, Player.getComponentType());
        if (entity == null) return false;
        if (entity.getPageManager().getCustomPage() instanceof InventoryProbePage page && page.isWorkspaceEntry()) return true;
        if (entity.getWindowManager().getWindows().stream().filter(java.util.Objects::nonNull)
                .anyMatch(window -> window.getType() != WindowType.PocketCrafting)) {
            player.sendMessage(Message.raw("Close the active container or station before opening Inventory. Spatial transfers through that window are not yet enabled."));
            return false;
        }
        // Always provide Hytale's native cursor over a denied presentation alias.
        // In native mode real Storage still owns the payload; in spatial mode the
        // private aggregate owns it. Neither path writes to this empty container.
        SimpleItemContainer virtualBag = new SimpleItemContainer((short) NativeGearTargetGrid.CAPACITY);
        virtualBag.setGlobalFilter(FilterType.DENY_ALL);
        ContainerWindow window = new ContainerWindow(virtualBag);
        var page = new InventoryProbePage(player, projection, null, allocation,
                () -> {
                    if (!ref.isValid()) return;
                    try {
                        var result = skillTree.open(player, ref, store, store.getExternalData().getWorld());
                        player.sendMessage(Message.raw(result.message()));
                        if (!result.opened() && ref.isValid()) open(player, ref, store, entryDismiss);
                    } catch (RuntimeException failure) {
                        player.sendMessage(Message.raw("Skill Tree could not open; Inventory restored."));
                        if (ref.isValid()) open(player, ref, store, entryDismiss);
                    }
                },
                () -> { dismissListener.accept(player.getUuid()); entryDismiss.run(); }, window, virtualBag,
                gearTransfer);
        return entity.getPageManager().openCustomPageWithWindows(ref, store, page, window);
    }
}
