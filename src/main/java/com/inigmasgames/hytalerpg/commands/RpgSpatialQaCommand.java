package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.ui.inventory.FootprintCatalog;
import com.inigmasgames.hytalerpg.ui.inventory.SpatialBagAggregate;
import com.inigmasgames.hytalerpg.ui.inventory.SpatialBagComponent;
import com.inigmasgames.hytalerpg.gear.HytaleGearLoot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import org.bson.BsonString;

/** Copied-save-only diagnostic issuance and attachment. Never migrates native items. */
public final class RpgSpatialQaCommand extends AbstractCommandCollection {
    private final Path marker;
    private final java.util.function.Supplier<HytaleGearLoot> gearRuntime;
    private final FootprintCatalog catalog = FootprintCatalog.loadDefault();
    public RpgSpatialQaCommand(Path marker,java.util.function.Supplier<HytaleGearLoot> gearRuntime) {
        super("spatialqa", "Copied-save spatial inventory acceptance fixtures.");
        this.marker = marker;
        this.gearRuntime=gearRuntime;
        requirePermission(RpgGearCommand.AUTHOR_PERMISSION);
        addSubCommand(new AbstractPlayerCommand("fault-next", "Arm one copied-save receipt interruption for reconnect recovery QA.") {
            final RequiredArg<String> boundaryArg=withRequiredArg("boundary",
                    "pickup/equipment/stock-after-prepare or -after-save",ArgTypes.STRING);
            @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> ref,PlayerRef player,World world){
                requireMarker();var runtime=gearRuntime.get();if(runtime==null)throw new IllegalStateException("Gear receipt owner unavailable");
                var boundary=boundaryArg.get(context).toLowerCase(java.util.Locale.ROOT);
                runtime.armCopiedTransferFault(boundary);
                context.sendMessage(Message.raw("Next "+boundary+" boundary will stop after durable work. Reconnect when prompted."));
            }
        });
        addSubCommand(new AbstractPlayerCommand("stocktake", "Admit the nearest protected stock drop to the QA bag.") {
            @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> ref,PlayerRef player,World world){
                requireMarker();var runtime=gearRuntime.get();if(runtime==null)throw new IllegalStateException("Gear receipt owner unavailable");
                runtime.takeNearbyStock(store,ref,player,world,message->context.sendMessage(Message.raw(message)));
            }
        });
        for(var migration:new String[]{"migration-preview","migration","export-preview","export"}){
            final boolean reverse=migration.startsWith("export");
            final boolean preview=migration.endsWith("preview");
            addSubCommand(new AbstractPlayerCommand(migration,"Copied-save native Storage migration preflight or commit.") {
                @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> ref,PlayerRef player,World world){
                    requireMarker();var runtime=gearRuntime.get();if(runtime==null)throw new IllegalStateException("Gear receipt owner unavailable");
                    runtime.copiedStorageMigration(store,ref,player,world,reverse,preview,
                            message->context.sendMessage(Message.raw(message)));
                }
            });
        }
        addSubCommand(new AbstractPlayerCommand("equip", "Move one private-bag entry to a native equipment slot.") {
            final RequiredArg<String> entryArg=withRequiredArg("entryId","Private bag entry UUID",ArgTypes.STRING);
            final RequiredArg<String> slotArg=withRequiredArg("slot","held, offhand, head, chest, hands or legs",ArgTypes.STRING);
            @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> ref,PlayerRef player,World world){
                requireMarker();var runtime=gearRuntime.get();if(runtime==null)throw new IllegalStateException("Gear receipt owner unavailable");
                runtime.transferEquipment(store,ref,player,world,UUID.fromString(entryArg.get(context)),slotArg.get(context),true,
                        message->context.sendMessage(Message.raw(message)));
            }
        });
        addSubCommand(new AbstractPlayerCommand("unequip", "Move a native equipment slot into the private bag.") {
            final RequiredArg<String> slotArg=withRequiredArg("slot","held, offhand, head, chest, hands or legs",ArgTypes.STRING);
            @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> ref,PlayerRef player,World world){
                requireMarker();var runtime=gearRuntime.get();if(runtime==null)throw new IllegalStateException("Gear receipt owner unavailable");
                runtime.transferEquipment(store,ref,player,world,null,slotArg.get(context),false,
                        message->context.sendMessage(Message.raw(message)));
            }
        });
        addSubCommand(new AbstractPlayerCommand("attach", "Attach an empty private bag without native migration.") {
            @Override protected void execute(CommandContext context, Store<EntityStore> store,
                                             Ref<EntityStore> ref, PlayerRef player, World world) {
                requireMarker();
                var existing = store.getComponent(ref, SpatialBagComponent.getComponentType());
                if (existing != null) {
                    context.sendMessage(Message.raw("Spatial QA bag already attached; mode " + existing.mode(player.getUuid())));
                    return;
                }
                var qaBag = new SpatialBagComponent(player.getUuid(), catalog);
                qaBag.activateQaProof(player.getUuid());
                store.addComponent(ref, SpatialBagComponent.getComponentType(), qaBag);
                var entity = store.getComponent(ref, Player.getComponentType());
                if (entity == null) throw new IllegalStateException("Player save owner unavailable");
                entity.saveConfig(world, entity.toHolder(), true).whenComplete((ignored, error) ->
                        context.sendMessage(Message.raw(error == null
                                ? "Empty QA bag attached and saved. Native inventory was not migrated."
                                : "QA bag save uncertain; reconnect before testing pickup.")));
            }
        });
        addSubCommand(new AbstractPlayerCommand("status", "Show bag and native ownership counts.") {
            @Override protected void execute(CommandContext context, Store<EntityStore> store,
                                             Ref<EntityStore> ref, PlayerRef player, World world) {
                requireMarker();
                var component = store.getComponent(ref, SpatialBagComponent.getComponentType());
                var nativeStorage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
                int nativeCount = 0;
                if (nativeStorage != null) for (short slot = 0; slot < nativeStorage.getInventory().getCapacity(); slot++)
                    if (!ItemStack.isEmpty(nativeStorage.getInventory().getItemStack(slot))) nativeCount++;
                if (component == null) {
                    context.sendMessage(Message.raw("Spatial QA bag absent; native Storage stacks " + nativeCount));
                    return;
                }
                var bag = component.state(player.getUuid());
                context.sendMessage(Message.raw("Spatial QA mode=" + component.mode(player.getUuid())
                        + " entries=" + bag.entries().size() + " revision=" + bag.revision()
                        + " receipts=" + bag.receipts().size() + " nativeStorageStacks=" + nativeCount));
                bag.entries().stream().limit(8).forEach(entry -> context.sendMessage(Message.raw(
                        entry.id() + " " + entry.payload().getItemId() + " " + entry.size().width() + "x"
                                + entry.size().height() + " at " + entry.position().x() + "," + entry.position().y())));
            }
        });
        addSubCommand(new AbstractPlayerCommand("fragment", "Issue 68 tagged QA rocks into an empty private bag.") {
            @Override protected void execute(CommandContext context, Store<EntityStore> store,
                                             Ref<EntityStore> ref, PlayerRef player, World world) {
                requireMarker();
                var component = store.getComponent(ref, SpatialBagComponent.getComponentType());
                if (component == null) throw new IllegalStateException("Attach a QA bag first");
                var before = component.state(player.getUuid());
                if (!before.entries().isEmpty()) throw new IllegalStateException("Fragment fixture requires an empty QA bag");
                UUID operation = UUID.randomUUID();
                var items = new ArrayList<SpatialBagAggregate.OfferedItem>();
                for (int index = 0; index < 68; index++) items.add(new SpatialBagAggregate.OfferedItem(
                        UUID.nameUUIDFromBytes((operation + ":" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        new ItemStack("Rock_Stone").withMetadata("HywindSpatialQaFixture", new BsonString(operation.toString()))));
                var result = before.offerAll(operation, before.revision(), items, catalog);
                if (!result.accepted()) throw new IllegalStateException("Fragment preflight: " + result.receipt().outcome());
                component.publish(player.getUuid(), before, result.bag());
                var entity = store.getComponent(ref, Player.getComponentType());
                if (entity == null) throw new IllegalStateException("Player save owner unavailable");
                entity.saveConfig(world, entity.toHolder(), true).whenComplete((ignored, error) ->
                        context.sendMessage(Message.raw(error == null
                                ? "68 QA rocks saved; four cells remain but no 2x4 rectangle fits."
                                : "Fragment save uncertain; reconnect before testing pickup.")));
            }
        });
    }
    private void requireMarker() {
        if (!Files.isRegularFile(marker)) throw new IllegalStateException("Spatial QA is only enabled in an isolated save");
    }
}
