package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.gear.GearNativeItems;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;

import java.util.Map;
import java.util.UUID;

/** Client-native cursor experiment. The window is empty and denies every inventory mutation. */
public final class NativeSpatialDragProbePage extends InteractiveCustomUIPage<NativeSpatialDragProbePage.Data> {
    private final PlayerRef player;
    private final NativeInventoryEntryProbe probe;
    private final ContainerWindow window;
    private final SimpleItemContainer virtualBag;
    private final short nativeSlot;
    private final ItemStack fingerprint;
    private final ItemStack visualStack;
    private final String session = UUID.randomUUID().toString();
    private int column;
    private int grabColumn;
    private boolean armed;
    private boolean dismissed;

    public NativeSpatialDragProbePage(PlayerRef player, NativeInventoryEntryProbe probe,
                                      ContainerWindow window, SimpleItemContainer virtualBag,
                                      short nativeSlot, ItemStack stack) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.player = player;
        this.probe = probe;
        this.window = window;
        this.virtualBag = virtualBag;
        this.nativeSlot = nativeSlot;
        this.fingerprint = stack.withMetadata(stack.getMetadata() == null ? null : stack.getMetadata().clone());
        this.visualStack = projectionStack(stack);
    }

    /** UI slots are visual aliases; custom managed metadata is not client ItemGrid metadata. */
    static ItemStack projectionStack(ItemStack nativeStack) {
        return new ItemStack(GearNativeItems.nativeId(nativeStack.getItemId()), 1);
    }

    @Override public void build(Ref<EntityStore> ref, UICommandBuilder commands,
                                UIEventBuilder events, Store<EntityStore> store) {
        int section = window.getId();
        if (section < 1 || section > 1024)
            throw new IllegalStateException("Native alias probe section outside packaged 1..1024 bank: " + section);
        if (virtualBag.getCapacity() != NativeSpatialAlias.CELLS)
            throw new IllegalStateException("Native alias probe backing capacity changed.");
        commands.append("RpgInventoryNativeAlias.ui");
        commands.append("#NativeGridHost", "InventoryNativeAlias/Section" + section + ".ui");
        commands.set("#BowIcon.ItemId", visualStack.getItemId());
        writeProjection(commands);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#Close",
                new EventData().append("Action", "close").append("Session", session), false);
        for (var binding : new CustomUIEventBindingType[]{CustomUIEventBindingType.SlotClicking,
                CustomUIEventBindingType.Dropped, CustomUIEventBindingType.DragCancelled}) {
            events.addEventBinding(binding, "#NativeAliasGrid",
                    new EventData().append("Action", binding.name()).append("Session", session), false);
        }
        probe.record(player, "NATIVE_ALIAS_OPEN", Map.of("section", section,
                "nativeSlot", (int) nativeSlot, "visualCells", NativeSpatialAlias.CELLS,
                "virtualEmpty", true, "nativeMutation", false,
                "visualItemId", visualStack.getItemId()));
    }

    private void writeProjection(UICommandBuilder commands) {
        ItemGridSlot[] slots = new ItemGridSlot[NativeSpatialAlias.CELLS];
        for (int visual = 0; visual < slots.length; visual++) {
            slots[visual] = NativeSpatialAlias.covered(visual, column)
                    ? new ItemGridSlot(visualStack) : new ItemGridSlot();
            slots[visual].setActivatable(true);
        }
        int before = commands.getCommands().length;
        commands.set("#NativeAliasGrid.Slots", slots);
        var encodedCommands = commands.getCommands();
        if (encodedCommands.length != before + 1)
            throw new IllegalStateException("Unable to locate native alias slot command.");
        var command = encodedCommands[encodedCommands.length - 1];
        BsonDocument data = BsonDocument.parse(command.data);
        BsonArray encoded = data.getArray("0");
        if (encoded.size() != NativeSpatialAlias.CELLS)
            throw new IllegalStateException("Native alias slot encoding changed.");
        for (int visual = 0; visual < encoded.size(); visual++) {
            BsonDocument slot = encoded.get(visual).asDocument();
            // Eight visual hit cells share one empty virtual source slot. The real bow stays in native Storage.
            slot.put("InventorySlotIndex", new BsonInt32(
                    NativeSpatialAlias.covered(visual, column) ? 0 : visual));
            slot.put("IsActivatable", BsonBoolean.TRUE);
        }
        command.data = data.toJson();
        commands.setObject("#BowVisual.Anchor", bowAnchor());
    }

    private Anchor bowAnchor() {
        Anchor anchor = new Anchor();
        anchor.setLeft(Value.of(column * 46));
        anchor.setTop(Value.of(0));
        anchor.setWidth(Value.of(92));
        anchor.setHeight(Value.of(184));
        return anchor;
    }

    @Override public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Data data) {
        if (dismissed || data == null || !session.equals(data.session)) return;
        if ("close".equals(data.action)) { close(); return; }
        var nativeStorage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        if (nativeStorage == null || nativeSlot >= nativeStorage.getInventory().getCapacity()
                || !fingerprint.equals(nativeStorage.getInventory().getItemStack(nativeSlot))) {
            probe.record(player, "NATIVE_ALIAS_STALE", Map.of("nativeSlot", (int) nativeSlot));
            close();
            return;
        }
        if ("SlotClicking".equals(data.action)) {
            int sourceOffset = NativeSpatialAlias.sourceColumn(data.slotIndex, column);
            if (!armed && sourceOffset >= 0) {
                grabColumn = sourceOffset;
                armed = true;
                probe.record(player, "NATIVE_ALIAS_GRAB", Map.of("visualSlot", data.slotIndex,
                        "grabColumn", grabColumn, "nativeMutation", false));
            }
            return;
        }
        if ("DragCancelled".equals(data.action)) {
            armed = false;
            probe.record(player, "NATIVE_ALIAS_CANCEL", Map.of("nativeMutation", false));
            return;
        }
        if (!"Dropped".equals(data.action)) return;
        int previous = column;
        int next = data.sourceInventorySectionId == window.getId() && data.sourceSlotId == 0
                ? NativeSpatialAlias.destinationColumn(data.slotIndex, grabColumn) : -1;
        armed = false;
        UICommandBuilder commands = new UICommandBuilder();
        if (next >= 0) {
            column = next;
            writeProjection(commands);
            commands.set("#NativeAliasStatus.Text", "Native cursor drop at column " + column
                    + "; real bow remains in native Storage slot " + nativeSlot + ".");
        } else {
            commands.set("#NativeAliasStatus.Text", "Native drop was not an accepted bow-to-grid gesture; real bow unchanged.");
        }
        sendUpdate(commands, false);
        probe.record(player, "NATIVE_ALIAS_DROP", Map.of("accepted", next >= 0,
                "fromColumn", previous, "toColumn", column, "sourceSection", data.sourceInventorySectionId,
                "sourceSlot", data.sourceSlotId, "targetVisual", data.slotIndex,
                "virtualEmpty", virtualEmpty(), "nativeMutation", false));
        if (!virtualEmpty()) close();
    }

    private boolean virtualEmpty() {
        for (short slot = 0; slot < virtualBag.getCapacity(); slot++)
            if (!ItemStack.isEmpty(virtualBag.getItemStack(slot))) return false;
        return true;
    }

    @Override public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
        dismissed = true;
        probe.record(player, "NATIVE_ALIAS_DISMISSED", Map.of("nativeMutation", false,
                "virtualEmpty", virtualEmpty()));
    }

    public static final class Data {
        static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d,v)->d.action=v, d->d.action).add()
                .append(new KeyedCodec<>("Session", Codec.STRING), (d,v)->d.session=v, d->d.session).add()
                .append(new KeyedCodec<>("SlotIndex", Codec.INTEGER), (d,v)->d.slotIndex=v, d->d.slotIndex).add()
                .append(new KeyedCodec<>("SourceSlotId", Codec.INTEGER), (d,v)->d.sourceSlotId=v, d->d.sourceSlotId).add()
                .append(new KeyedCodec<>("SourceInventorySectionId", Codec.INTEGER),
                        (d,v)->d.sourceInventorySectionId=v, d->d.sourceInventorySectionId).add()
                .build();
        private String action = "", session = "";
        private int slotIndex = -1, sourceSlotId = -1, sourceInventorySectionId = Integer.MIN_VALUE;
    }
}
