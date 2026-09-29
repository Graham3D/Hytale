package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.combat.hytale.DerivedStatEntityAdapter;
import com.inigmasgames.hytalerpg.gear.GearNativeItems;
import com.inigmasgames.hytalerpg.progress.AttributeAllocationService;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleEquipmentAdapter;
import com.inigmasgames.hytalerpg.ui.HytaleResourceViewAdapter;
import com.inigmasgames.hytalerpg.ui.RpgUiProjectionService;
import java.util.*;

/** Unified workspace: native-mode placement is a preview; spatial-mode placement commits to its owner. */
public final class InventoryProbePage extends InteractiveCustomUIPage<InventoryProbePage.Data> {
    public static final List<String> ROUTES = List.of("Inventory", "Skills", "Quests", "Bestiary", "Map", "Friends", "Party");
    private static final FootprintCatalog CATALOG = FootprintCatalog.loadDefault();
    private final InventoryProbeBag bag = new InventoryProbeBag();
    private record View(short slot, String itemId, String visibleName, int quantity, ItemStack fingerprint) { }
    private final PlayerRef player;
    private final RpgUiProjectionService projection;
    private final NativeInventoryEntryProbe probe;
    private final AttributeAllocationService allocation;
    private final Runnable skillTreeEntry;
    private final Runnable onDismiss;
    private final ContainerWindow nativeWindow;
    private final SimpleItemContainer nativeVirtualBag;
    private final String session = UUID.randomUUID().toString();
    private final Map<String, View> items = new LinkedHashMap<>();
    private SpatialLayout layout = new SpatialLayout(InventoryGridGeometry.COLUMNS, InventoryGridGeometry.ROWS);
    private SpatialLayout.Grab grab;
    private int nativeGrabCell = -1;
    private int pendingDropCell = -1, pendingDropQuantity = -1;
    private UUID selectedSpatialEntry;
    private short selectedNativeSlot = -1;
    private String nativeGridSelector;
    private String query = "", route = "Inventory";
    private AdvancedStatsViewModel advancedModel;
    private boolean advanced;
    private boolean dismissed;
    private boolean openSkillTreeOnDismiss;
    private boolean savePending, saveFailed;

    public InventoryProbePage(PlayerRef player, RpgUiProjectionService projection, NativeInventoryEntryProbe probe) {
        this(player, projection, probe, null, null, () -> {});
    }
    public InventoryProbePage(PlayerRef player, RpgUiProjectionService projection,
                              NativeInventoryEntryProbe probe, AttributeAllocationService allocation,
                              Runnable skillTreeEntry, Runnable onDismiss) {
        this(player, projection, probe, allocation, skillTreeEntry, onDismiss, null, null);
    }
    public InventoryProbePage(PlayerRef player, RpgUiProjectionService projection,
                              NativeInventoryEntryProbe probe, AttributeAllocationService allocation,
                              Runnable skillTreeEntry, Runnable onDismiss,
                              ContainerWindow nativeWindow, SimpleItemContainer nativeVirtualBag) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.player = player; this.projection = projection; this.probe = probe;
        this.allocation = allocation; this.skillTreeEntry = skillTreeEntry;
        this.onDismiss = onDismiss;
        this.nativeWindow = nativeWindow; this.nativeVirtualBag = nativeVirtualBag;
    }
    public static void preload() { CATALOG.bindingCount(); }
    public boolean isWorkspaceEntry() { return allocation != null; }
    /** Resolve only this page's empty native cursor alias, never a real inventory section. */
    public UUID spatialEntryForDrop(int section, short slot) {
        if (dismissed || nativeWindow == null || nativeVirtualBag == null
                || section != nativeWindow.getId() || slot < 0 || slot >= InventoryGridGeometry.CELLS
                || !nativeVirtualEmpty()) return null;
        var entry = layout.at(slot % InventoryGridGeometry.COLUMNS, slot / InventoryGridGeometry.COLUMNS).orElse(null);
        if (entry == null) return null;
        try { return UUID.fromString(entry.id()); }
        catch (IllegalArgumentException nativePreview) { return null; }
    }
    public record NativeStorageDrop(short slot, ItemStack fingerprint) { }
    public NativeStorageDrop nativeStorageForDrop(int section, short visualSlot) {
        if (!ownsSpatialAlias(section) || visualSlot < 0 || visualSlot >= InventoryGridGeometry.CELLS
                || !nativeVirtualEmpty()) return null;
        var entry = layout.at(visualSlot % InventoryGridGeometry.COLUMNS,
                visualSlot / InventoryGridGeometry.COLUMNS).orElse(null);
        var view = entry == null ? null : items.get(entry.id());
        return view == null || view.slot < 0 ? null : new NativeStorageDrop(view.slot, view.fingerprint);
    }
    public boolean ownsSpatialAlias(int section) {
        return !dismissed && nativeWindow != null && nativeWindow.getId() == section;
    }
    public int consumeDropQuantity(short sourceCell) {
        int quantity=pendingDropCell==sourceCell?pendingDropQuantity:-1;
        pendingDropCell=-1;pendingDropQuantity=-1;
        return quantity;
    }
    public void refreshAfterSpatialDrop(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (dismissed || !ref.isValid()) return;
        savePending = false;
        var commands = new UICommandBuilder(); var events = new UIEventBuilder();
        snapshot(ref, store, commands, events);
        sendUpdate(commands, events, false);
    }
    public void beginSpatialDrop() { savePending = true; }
    public void failSpatialDrop(String message) {
        if (dismissed) return;
        savePending = false; saveFailed = true;
        var update = new UICommandBuilder();
        update.set("#Status.Text", message);
        sendUpdate(update, new UIEventBuilder(), false);
    }
    private EventData event(String action, String value) {
        return new EventData().append("Action", action).append("Value", value).append("Session", session);
    }
    @Override public void build(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
        commands.append("RpgInventoryProbe.ui");
        for (var name : ROUTES) events.addEventBinding(CustomUIEventBindingType.Activating, "#Route" + name, event("route", name), false);
        for (var name : List.of("Close", "Clear", "Refresh", "Cancel", "Advanced", "Back"))
            events.addEventBinding(CustomUIEventBindingType.Activating, "#" + name, event(name.toLowerCase(Locale.ROOT), ""), false);
        for (var name : List.of("Sort", "Consolidate", "QuickEquip", "DropSelected"))
            events.addEventBinding(CustomUIEventBindingType.Activating, "#" + name,
                    event(name.toLowerCase(Locale.ROOT), ""), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RingEquipLeft", event("ring", "left"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RingEquipRight", event("ring", "right"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ArmorVisibility",
                event("armorvisibility", ""), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#Split",
                event("split", "").append("@Quantity", "#SplitQuantity.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#Search",
                event("search", "").append("@Query", "#Search.Value"), false);
        for (var type : List.of(CustomUIEventBindingType.FocusGained, CustomUIEventBindingType.FocusLost))
            events.addEventBinding(type, "#Search", event("focus", type.name()), false);
        snapshot(ref, store, commands, events);
    }
    private void snapshot(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commands, UIEventBuilder events) {
        grab = null; nativeGrabCell = -1; selectedSpatialEntry = null; selectedNativeSlot = -1;
        items.clear(); layout = new SpatialLayout(InventoryGridGeometry.COLUMNS, InventoryGridGeometry.ROWS);
        bag.reset(commands);
        int omitted = 0;
        var spatial = store.getComponent(ref, SpatialBagComponent.getComponentType());
        boolean spatialOwner = spatial != null && spatial.mode(player.getUuid()) != SpatialBagComponent.OwnershipMode.NATIVE;
        if (spatialOwner) {
            for (var entry : spatial.state(player.getUuid()).entries()) {
                var stack = entry.payload();
                String id = entry.id().toString();
                if (!layout.add(id, entry.size(), entry.position())) throw new IllegalStateException("Invalid committed bag placement");
                items.put(id, new View((short)-1, stack.getItemId(), visibleName(stack.getDisplayName()),
                        stack.getQuantity(), stack));
            }
        } else {
        var storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        if (storage != null) {
            var nativeBag = storage.getInventory();
            for (short slot = 0; slot < nativeBag.getCapacity(); slot++) {
                var stack = nativeBag.getItemStack(slot);
                if (ItemStack.isEmpty(stack)) continue;
                var size = CATALOG.size(GearNativeItems.nativeId(stack.getItemId()));
                var position = size == null ? Optional.<SpatialLayout.Position>empty() : layout.firstFit(size);
                if (position.isEmpty()) { omitted++; continue; }
                String id = "Slot" + slot;
                layout.add(id, size, position.get());
                items.put(id, new View(slot, stack.getItemId(), visibleName(stack.getDisplayName()), stack.getQuantity(),
                        stack.withMetadata(stack.getMetadata() == null ? null : stack.getMetadata().clone())));
            }
        }
        }
        if (nativeWindow != null) {
            int section = nativeWindow.getId();
            if (section < 1 || section > 1024 || nativeVirtualBag == null
                    || nativeVirtualBag.getCapacity() != InventoryGridGeometry.CELLS)
                throw new IllegalStateException("Native workspace window has a different grid capacity");
            nativeGridSelector = bag.nativeGrid(commands, section);
            NativeWorkspaceGrid.write(commands, nativeGridSelector, layout, id -> items.get(id).fingerprint);
            for (var binding : new CustomUIEventBindingType[]{CustomUIEventBindingType.SlotClicking,
                    CustomUIEventBindingType.Dropped, CustomUIEventBindingType.DragCancelled})
                events.addEventBinding(binding, nativeGridSelector, event(binding.name(), ""), false);
        }
        // Opaque visual cells mask the native grid's repeated per-cell icons while
        // remaining hit-test transparent, preserving its native cursor behavior.
        bag.cells(commands);
        // Whole-footprint frame; square native art retains its aspect ratio inside the rectangle.
        for (var e : layout.entries()) {
            bag.item(commands, e, items.get(e.id()).fingerprint);
        }
        // A uniform hit plane after artwork: every covered cell resolves to the same entry.
        if (nativeWindow == null) for (int y = 0; y < InventoryGridGeometry.ROWS; y++) for (int x = 0; x < InventoryGridGeometry.COLUMNS; x++) {
            String selector = bag.hit(commands, x, y);
            events.addEventBinding(CustomUIEventBindingType.Activating, selector, event("cell", x + "," + y), false);
        }
        renderCharacter(ref, store, commands, events);
        commands.set("#Coverage.Text", spatialOwner
                ? items.size() + " committed spatial stacks; bag revision " + spatial.state(player.getUuid()).revision()
                : items.size() + " mapped stacks; " + omitted + " omitted (unmapped or no fit). Catalog v"
                    + CATALOG.revision() + "; native items unchanged.");
        commands.set("#OwnershipNotice.Text", spatialOwner
                ? "CHARACTER & INVENTORY  |  Copied-save spatial proof  |  Native Storage migration under test"
                : "CHARACTER & INVENTORY  |  Development entry  |  Native Storage remains authoritative");
        commands.set("#SpatialActions.Visible", true);
        commands.set("#QuickEquip.Visible", !spatialOwner);
        commands.set("#DropSelected.Visible", !spatialOwner);
        commands.set("#Status.Text", "");
        commands.set("#ItemContext.Text", "");
        commands.set("#Cancel.Visible", nativeWindow == null);
        search(commands);
        record("SNAPSHOT", Map.of("mapped", items.size(), "omitted", omitted, "nativeMutation", false));
    }
    private void renderCharacter(Ref<EntityStore> ref, Store<EntityStore> store,
                                 UICommandBuilder commands, UIEventBuilder events) {
        var stats = store.getComponent(ref, EntityStatMap.getComponentType());
        if (stats == null) return;
        var resourceSnapshot = new HytaleResourceViewAdapter().read(stats);
        var model = projection.character(player.getUuid(), player.getUsername(), resourceSnapshot);
        commands.set("#PlayerName.Text", model.displayName());
        String progress = model.xp().level() == 99 ? "MAX LEVEL"
                : "XP " + model.xp().xpIntoLevel() + " / " + model.xp().xpToNext();
        commands.set("#Level.Text", "Level " + model.xp().level() + "    " + progress);
        double fraction = model.xp().level() == 99 ? 1 : model.xp().progress();
        int fill = (int)Math.round(300 * Math.max(0, Math.min(1, fraction)));
        var xpAnchor = new com.hypixel.hytale.server.core.ui.Anchor();
        xpAnchor.setLeft(com.hypixel.hytale.server.core.ui.Value.of(0));
        xpAnchor.setTop(com.hypixel.hytale.server.core.ui.Value.of(0));
        xpAnchor.setWidth(com.hypixel.hytale.server.core.ui.Value.of(Math.max(1, fill)));
        xpAnchor.setHeight(com.hypixel.hytale.server.core.ui.Value.of(10));
        commands.setObject("#XpFill.Anchor", xpAnchor);
        commands.set("#XpFill.Visible", fill > 0);
        String[] names = {"Strength", "Dexterity", "Intelligence", "Wisdom", "Luck"};
        var attributes = new RpgAttribute[]{RpgAttribute.STR, RpgAttribute.DEX, RpgAttribute.INT, RpgAttribute.WIS, RpgAttribute.LUCK};
        for (int i = 0; i < names.length; i++) {
            commands.set("#Attribute" + i + ".Text", names[i]);
            commands.set("#AttributeValue" + i + ".Text",
                    clean(model.derivedStats().effective(attributes[i])));
            commands.set("#AttributePlus" + i + ".Disabled", allocation == null || model.unspentAttributePoints() <= 0);
            if (allocation != null) events.addEventBinding(CustomUIEventBindingType.Activating, "#AttributePlus" + i,
                    event("allocate", attributes[i].name()).append("Revision", Long.toString(model.revision())), false);
        }
        commands.set("#Points.Text", "Attribute Points: " + model.unspentAttributePoints());
        commands.set("#Points.Visible", model.unspentAttributePoints() > 0);
        double usableMana = projection.usableMana(player.getUuid(), resourceSnapshot);
        commands.set("#ResourceHealth.Text", resource(model.health()));
        commands.set("#ResourceMana.Text", clean(model.mana().current()) + " / " + clean(usableMana)
                + (usableMana < model.mana().maximum() ? "  (" + clean(model.mana().maximum()) + " total)" : ""));
        commands.set("#ResourceStamina.Text", resource(model.stamina()));
        var d = model.derivedStats();
        commands.set("#Critical.Text", percent(d.criticalChance()));
        commands.set("#Cooldown.Text", percent(d.cooldownRecovery()));
        var magicFind = GearNativeItems.magicFindBreakdown(ref, store);
        commands.set("#MagicFind.Text", percent(magicFind.total()));
        renderCombatSources(ref, store, commands);
        advancedModel = AdvancedStatsViewModel.resolve(model, GearNativeItems.effects(ref, store),
                NativeArmorDefenseView.read(ref, store), magicFind);
        renderAdvanced(commands, events);
        commands.set("#AdvancedStats.Visible", advanced);
        workspacePosition(commands);
        renderEquipment(ref, store, commands);
        var settings = store.getComponent(ref,
                com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings.getComponentType());
        boolean armorHidden = settings != null && settings.hideHelmet() && settings.hideCuirass()
                && settings.hideGauntlets() && settings.hidePants();
        commands.set("#ArmorVisibleIcon.Visible", !armorHidden);
        commands.set("#ArmorHiddenIcon.Visible", armorHidden);
    }
    private void workspacePosition(UICommandBuilder commands) {
        var anchor = new com.hypixel.hytale.server.core.ui.Anchor();
        if (advanced) {
            anchor.setLeft(com.hypixel.hytale.server.core.ui.Value.of(16));
            anchor.setTop(com.hypixel.hytale.server.core.ui.Value.of(16));
            anchor.setBottom(com.hypixel.hytale.server.core.ui.Value.of(16));
            anchor.setWidth(com.hypixel.hytale.server.core.ui.Value.of(1240));
        } else {
            anchor.setFull(com.hypixel.hytale.server.core.ui.Value.of(16));
            anchor.setMaxWidth(com.hypixel.hytale.server.core.ui.Value.of(1240));
        }
        commands.setObject("#Workspace.Anchor", anchor);
    }
    private void renderAdvanced(UICommandBuilder commands, UIEventBuilder events) {
        commands.clear("#AdvancedRows");
        String category = "";
        int child = 0;
        for (var row : advancedModel.rows()) {
            if (!category.equals(row.descriptor().category())) {
                category = row.descriptor().category();
                commands.append("#AdvancedRows", "RpgAdvancedStatSection.ui");
                commands.set("#AdvancedRows[" + child++ + "].Text", category);
            }
            commands.append("#AdvancedRows", "RpgAdvancedStatRow.ui");
            String selector = "#AdvancedRows[" + child++ + "]";
            commands.set(selector + " #Name.Text", row.descriptor().label());
            commands.set(selector + " #Value.Text", row.value());
            commands.set(selector + " #Open.TooltipText", row.sources());
            events.addEventBinding(CustomUIEventBindingType.Activating, selector + " #Open",
                    event("advancedstat", row.descriptor().id()), false);
        }
        commands.set("#AdvancedDetail.Text", "");
    }
    private void renderCombatSources(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commands) {
        var equipment = new HytaleEquipmentAdapter().read(ref, store);
        var main = equipment.mainHand();
        var power = main == null ? null : main.power();
        commands.set("#WeaponDamage.Text", power != null && power.physicalMinimum() != null
                ? clean(power.physicalMinimum()) + "–" + clean(power.physicalMaximum()) : "—");
        var equipped = projection.equippedSkills(player.getUuid());
        commands.set("#EquippedSpells.Text", equipped.isEmpty() ? "" :
                "Equipped: " + equipped.stream().map(RpgUiProjectionService.EquippedSkill::name)
                        .collect(java.util.stream.Collectors.joining(", ")));
        var defense = NativeArmorDefenseView.read(ref, store);
        var physical = defense.directPercent("Physical");
        // The installed native DamageCause names for frost and poison map to the
        // project's Water and Earth channels. Missing direct modifiers are not 0%.
        String[][] channels = {
                {"Wind", "Wind"}, {"Water", "Ice"}, {"Fire", "Fire"},
                {"Earth", "Poison"}, {"Lightning", "Lightning"}, {"Void", "RPG_Void"}
        };
        for (var channel : channels) {
            var value = defense.directPercent(channel[1]);
            commands.set("#Resist" + channel[0] + ".Text", resistance(value));
        }
    }
    private static String resistance(java.util.OptionalDouble percent) {
        return percent.isEmpty() || Math.abs(percent.getAsDouble()) < 0.0001
                ? "0%" : one(percent.getAsDouble()) + "%";
    }
    private static void renderEquipment(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commands) {
        showEquipment(commands, "Weapon", InventoryComponent.getItemInHand(store, ref));
        var utility = store.getComponent(ref, InventoryComponent.Utility.getComponentType());
        showEquipment(commands, "Offhand", utility == null ? null : utility.getActiveItem());
        var armor = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
        for (var slot : com.hypixel.hytale.protocol.ItemArmorSlot.values()) {
            ItemStack stack = armor == null || slot.getValue() >= armor.getInventory().getCapacity()
                    ? null : armor.getInventory().getItemStack((short) slot.getValue());
            showEquipment(commands, slot.name(), stack);
        }
        var player = store.getComponent(ref, PlayerRef.getComponentType());
        var spatial = store.getComponent(ref, SpatialBagComponent.getComponentType());
        boolean spatialOwner = player != null && spatial != null
                && spatial.mode(player.getUuid()) != SpatialBagComponent.OwnershipMode.NATIVE;
        RpgRingEquipment rings = player == null || spatial == null ? new RpgRingEquipment(null, null)
                : spatial.rings(player.getUuid());
        for (String side : List.of("Left", "Right")) {
            var ring = rings.get(side.toLowerCase(Locale.ROOT));
            commands.set("#RingEquip" + side + ".Visible", spatialOwner);
            commands.set("#RingItem" + side + ".Visible", ring != null);
            commands.set("#RingEmpty" + side + ".Visible", ring == null);
            commands.set("#RingEquip" + side + ".TooltipText", ring == null
                    ? "Select a Copper Ring in the bag, then click to equip."
                    : "Click to return this ring to the bag.");
            if (ring != null) commands.set("#RingItem" + side + ".ItemId", ring.payload().getItemId());
        }
    }
    private static void showEquipment(UICommandBuilder commands, String slot, ItemStack stack) {
        boolean occupied = !ItemStack.isEmpty(stack);
        commands.set("#Equip" + slot + ".Visible", occupied);
        if (!"Weapon".equals(slot)) commands.set("#Empty" + slot + ".Visible", !occupied);
        if (occupied) commands.set("#Equip" + slot + ".ItemId", stack.getItemId());
    }
    private static String one(double value) { return String.format(Locale.ROOT, "%.1f", value); }
    private static String clean(double value) {
        return Math.abs(value - Math.rint(value)) < 0.0001
                ? Long.toString(Math.round(value)) : one(value);
    }
    private static String percent(double value) { return one(value * 100.0) + "%"; }
    private static String resource(com.inigmasgames.hytalerpg.ui.model.NativeResourceView value) {
        return clean(value.current()) + " / " + clean(value.maximum());
    }
    private String visibleName(Message message) {
        // Native formatting is kept out of comparison. Never substitute an ItemId for a missing translation.
        String raw = message.getRawText();
        if (raw != null) return raw;
        String translated = message.getMessageId() == null ? null : I18nModule.get().getMessage(player.getLanguage(), message.getMessageId());
        return translated == null ? "Name unavailable" : translated;
    }
    private void search(UICommandBuilder commands) {
        int matches = 0;
        for (var e : items.entrySet()) {
            boolean match = VisibleItemSearch.matches(e.getValue().visibleName, query);
            if (match) matches++;
            bag.dim(commands, e.getKey(), !match);
        }
        commands.set("#MatchCount.Visible", !query.isBlank());
        commands.set("#MatchCount.Text", matches + " found");
        commands.set("#Clear.Visible", !query.isBlank());
        // Deliberately never write Search.Value here or rebuild the page while typing.
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Data data) {
        if (dismissed || !session.equals(data.session) || data.action == null || data.value == null) return;
        if ("close".equals(data.action)) { grab = null; close(); return; }
        if (savePending || saveFailed) return;
        if (probe != null && !probe.aliasActive(player)) return;
        var commands = new UICommandBuilder(); var events = new UIEventBuilder();
        switch (data.action) {
            case "close" -> { grab = null; close(); return; }
            case "SlotClicking" -> {
                if (nativeWindow == null || !"Inventory".equals(route)) return;
                if (nativeGrabCell < 0 && NativeWorkspaceGrid.sourceCell(layout, data.slotIndex) >= 0) {
                    nativeGrabCell = data.slotIndex;
                    var selected = layout.at(data.slotIndex % InventoryGridGeometry.COLUMNS, data.slotIndex / InventoryGridGeometry.COLUMNS).orElseThrow();
                    try { selectedSpatialEntry = UUID.fromString(selected.id()); }
                    catch (IllegalArgumentException nativePreview) { selectedSpatialEntry = null; }
                    var view = items.get(selected.id());
                    selectedNativeSlot = view.slot;
                    commands.set("#ItemContext.Text", view.visibleName
                            + (view.quantity > 1 ? "  ×" + view.quantity : ""));
                }
            }
            case "DragCancelled" -> {
                nativeGrabCell = -1;
                commands.set("#Status.Text", "");
            }
            case "dropselected" -> {
                if (nativeWindow == null || !"Inventory".equals(route)) return;
                int source=-1;
                if (selectedSpatialEntry != null) {
                    var entry=layout.entries().stream().filter(e->e.id().equals(selectedSpatialEntry.toString()))
                            .findFirst().orElse(null);
                    if(entry!=null)source=NativeWorkspaceGrid.sourceCell(entry);
                } else if (selectedNativeSlot >= 0) {
                    var entry=layout.entries().stream().filter(e->items.get(e.id()).slot==selectedNativeSlot)
                            .findFirst().orElse(null);
                    if(entry!=null)source=NativeWorkspaceGrid.sourceCell(entry);
                }
                if(source<0){commands.set("#Status.Text", "Select an item to drop.");break;}
                store.invoke(ref,new com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent.PlayerRequest(
                        nativeWindow.getId(),(short)source));
                return;
            }
            case "armorvisibility" -> {
                var settings = store.getComponent(ref,
                        com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings.getComponentType());
                if (settings == null)
                    settings = com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings.defaults();
                boolean hidden = !(settings.hideHelmet() && settings.hideCuirass()
                        && settings.hideGauntlets() && settings.hidePants());
                store.putComponent(ref,
                        com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings.getComponentType(),
                        new com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings(
                                settings.showEntityMarkers(), settings.armorItemsPreferredPickupLocation(),
                                settings.weaponAndToolItemsPreferredPickupLocation(),
                                settings.usableItemsItemsPreferredPickupLocation(),
                                settings.solidBlockItemsPreferredPickupLocation(),
                                settings.miscItemsPreferredPickupLocation(), settings.creativeSettings(),
                                hidden, hidden, hidden, hidden, settings.voiceSettings()));
                commands.set("#ArmorVisibleIcon.Visible", !hidden);
                commands.set("#ArmorHiddenIcon.Visible", hidden);
            }
            case "Dropped" -> {
                if (nativeWindow == null || !"Inventory".equals(route)) return;
                nativeDrop(ref, store, data, commands, events);
            }
            case "sort" -> {
                var spatial = store.getComponent(ref, SpatialBagComponent.getComponentType());
                if (spatial == null || spatial.mode(player.getUuid()) == SpatialBagComponent.OwnershipMode.NATIVE) {
                    com.hypixel.hytale.server.core.inventory.InventoryUtils.sortStorage(ref, store);
                    snapshot(ref, store, commands, events);
                } else {
                    var current = spatial.state(player.getUuid());
                    commitSpatialAction(ref, store, commands, events, spatial, current,
                            current.sort(UUID.randomUUID(), current.revision()), "Inventory auto-packed.");
                }
            }
            case "quickequip" -> {
                var spatial = store.getComponent(ref, SpatialBagComponent.getComponentType());
                if (spatial != null && spatial.mode(player.getUuid()) != SpatialBagComponent.OwnershipMode.NATIVE) {
                    commands.set("#Status.Text", "Spatial gear transfer uses protected custody; quick equip is unavailable here.");
                    break;
                }
                var storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
                var selected = items.get("Slot" + selectedNativeSlot);
                if (storage == null || selected == null || selectedNativeSlot < 0
                        || selectedNativeSlot >= storage.getInventory().getCapacity()
                        || !selected.fingerprint.equals(storage.getInventory().getItemStack(selectedNativeSlot))) {
                    commands.set("#Status.Text", "Click an item in the grid first, then try quick equip.");
                    break;
                }
                var settings = store.getComponent(ref,
                        com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings.getComponentType());
                if (settings == null)
                    settings = com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings.defaults();
                com.hypixel.hytale.server.core.inventory.InventoryUtils.smartMoveItem(ref,
                        InventoryComponent.STORAGE_SECTION_ID, selectedNativeSlot,
                        selected.fingerprint.getQuantity(), com.hypixel.hytale.protocol.SmartMoveType.EquipOrMergeStack,
                        settings, store);
                snapshot(ref, store, commands, events);
            }
            case "consolidate" -> {
                var spatial = store.getComponent(ref, SpatialBagComponent.getComponentType());
                if (spatial == null || spatial.mode(player.getUuid()) == SpatialBagComponent.OwnershipMode.NATIVE) {
                    var nativeStorage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
                    if (nativeStorage == null) return;
                    var container = nativeStorage.getInventory();
                    for (short slot = 0; slot < container.getCapacity(); slot++)
                        if (!ItemStack.isEmpty(container.getItemStack(slot)))
                            container.combineItemStacksIntoSlot(container, slot);
                    snapshot(ref, store, commands, events);
                    commands.set("#Status.Text", "Compatible native stacks combined.");
                    break;
                }
                var current = spatial.state(player.getUuid());
                commitSpatialAction(ref, store, commands, events, spatial, current,
                        current.consolidate(UUID.randomUUID(), current.revision()), "Compatible stacks combined.");
            }
            case "split" -> {
                var spatial = store.getComponent(ref, SpatialBagComponent.getComponentType());
                int quantity;
                try { quantity = Integer.parseInt(data.quantity.trim()); }
                catch (RuntimeException invalid) { commands.set("#Status.Text", "Enter a whole quantity to split."); break; }
                if (spatial == null || spatial.mode(player.getUuid()) == SpatialBagComponent.OwnershipMode.NATIVE) {
                    var nativeStorage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
                    if (nativeStorage == null || selectedNativeSlot < 0) {
                        commands.set("#Status.Text", "Click a stack in the grid first."); break;
                    }
                    var container = nativeStorage.getInventory();
                    if (selectedNativeSlot >= container.getCapacity()) return;
                    var stack = container.getItemStack(selectedNativeSlot);
                    if (ItemStack.isEmpty(stack) || quantity < 1 || quantity >= stack.getQuantity()) {
                        commands.set("#Status.Text", "Split quantity must be smaller than the stack."); break;
                    }
                    short empty = -1;
                    for (short slot = 0; slot < container.getCapacity(); slot++)
                        if (ItemStack.isEmpty(container.getItemStack(slot))) { empty = slot; break; }
                    if (empty < 0 || !container.moveItemStackFromSlotToSlot(
                            selectedNativeSlot, quantity, container, empty).succeeded()) {
                        commands.set("#Status.Text", "No free native slot for that split."); break;
                    }
                    snapshot(ref, store, commands, events);
                    commands.set("#Status.Text", "Stack split; drag the new stack to place or drop it.");
                    break;
                }
                if (selectedSpatialEntry == null) {
                    commands.set("#Status.Text", "Click a stack in the grid first."); break;
                }
                var current = spatial.state(player.getUuid());
                commitSpatialAction(ref, store, commands, events, spatial, current,
                        current.split(UUID.randomUUID(), current.revision(), selectedSpatialEntry, quantity),
                        "Stack split; drag the new stack to place or drop it.");
            }
            case "cancel" -> {
                if (grab != null) bag.select(commands, grab.id(), false);
                grab = null; nativeGrabCell = -1;
                commands.set("#ItemContext.Text", "Selection cleared.");
                commands.set("#Status.Text", "");
            }
            case "search" -> { query = data.query == null ? "" : data.query.substring(0, Math.min(data.query.length(), 256)); search(commands); }
            case "clear" -> {
                // The native preview holds its rotation on the client. Avoid a needless UI patch
                // when the query is already empty; such patches can reset that local rotation.
                if (query.isEmpty()) return;
                query = ""; commands.set("#Search.Value", ""); search(commands);
            }
            case "focus" -> { record("TEXT_FOCUS", Map.of("event", data.value)); return; }
            case "refresh" -> {
                if (nativeGrabCell >= 0) {
                    commands.set("#Status.Text", "Place your item before refreshing.");
                    break;
                }
                snapshot(ref, store, commands, events);
                if ("Quests".equals(route)) commands.set("#RouteDetail.Text", renderQuests(commands));
            }
            case "advanced", "back" -> {
                advanced = "advanced".equals(data.action);
                commands.set("#AdvancedStats.Visible", advanced);
                workspacePosition(commands);
            }
            case "advancedstat" -> {
                if (advanced && advancedModel != null)
                    commands.set("#AdvancedDetail.Text", advancedModel.sources(data.value));
            }
            case "ring" -> {
                if (nativeWindow == null || !nativeVirtualEmpty()) {
                    commands.set("#Status.Text", "Finish the current inventory move before using a ring slot.");
                    break;
                }
                var spatial = store.getComponent(ref, SpatialBagComponent.getComponentType());
                if (spatial == null || spatial.mode(player.getUuid()) == SpatialBagComponent.OwnershipMode.NATIVE) {
                    commands.set("#Status.Text", "Ring equipment requires spatial inventory.");
                    break;
                }
                try {
                    var current = spatial.state(player.getUuid());
                    SpatialBagAggregate.Result result;
                    if (selectedSpatialEntry != null
                            && current.entry(selectedSpatialEntry).map(e -> RpgRingEquipment.eligible(e.payload())).orElse(false))
                        result = spatial.equipRing(player.getUuid(), data.value, selectedSpatialEntry,
                                current.revision(), CATALOG);
                    else if (spatial.rings(player.getUuid()).get(data.value) != null)
                        result = spatial.unequipRing(player.getUuid(), data.value, CATALOG);
                    else {
                        commands.set("#Status.Text", "Select a Copper Ring in the bag first.");
                        break;
                    }
                    if (!result.accepted()) {
                        commands.set("#Status.Text", "Ring transfer needs a free bag cell.");
                        break;
                    }
                    snapshot(ref, store, commands, events);
                    savePending = true;
                    savePlacement(ref, store);
                } catch (RuntimeException failure) {
                    commands.set("#Status.Text", "Ring transfer could not be completed.");
                }
            }
            case "allocate" -> {
                if (allocation == null) return;
                long revision;
                RpgAttribute attribute;
                try { revision = Long.parseLong(data.revision); attribute = RpgAttribute.parse(data.value); }
                catch (RuntimeException error) { return; }
                var result = allocation.allocate(player.getUuid(), attribute, revision, UUID.randomUUID().toString());
                if (result.success()) {
                    var stats = store.getComponent(ref, EntityStatMap.getComponentType());
                    if (stats != null) {
                        var model = projection.character(player.getUuid(), player.getUsername(), new HytaleResourceViewAdapter().read(stats));
                        new DerivedStatEntityAdapter().apply(stats, model.derivedStats());
                    }
                }
                renderCharacter(ref, store, commands, events);
                commands.set("#Status.Text", result.success() ? "" : "Could not add that attribute point.");
            }
            case "route" -> {
                if (!ROUTES.contains(data.value)) return;
                if (nativeGrabCell >= 0) {
                    commands.set("#Status.Text", "Place your item before changing tabs.");
                    break;
                }
                if ("Skills".equals(data.value)) {
                    if (skillTreeEntry == null) {
                        commands.set("#Status.Text", "Skills are unavailable right now.");
                        break;
                    }
                    grab = null;
                    openSkillTreeOnDismiss = true;
                    close();
                    return;
                }
                if (grab != null) bag.select(commands, grab.id(), false);
                grab = null; nativeGrabCell = -1; route = data.value;
                for (var name : ROUTES) if (!"Skills".equals(name))
                    commands.set("#Active" + name + ".Visible", name.equals(route));
                commands.set("#InventoryContent.Visible", "Inventory".equals(route));
                commands.set("#RouteContent.Visible", !"Inventory".equals(route));
                commands.set("#QuestsOverview.Visible", "Quests".equals(route));
                if ("Inventory".equals(route)) {
                    commands.set("#ItemContext.Text", "");
                    commands.set("#Status.Text", "");
                } else {
                    commands.set("#RouteMessage.Text", route.toUpperCase(Locale.ROOT));
                    commands.set("#RouteDetail.Text", switch (route) {
                        case "Quests" -> renderQuests(commands);
                        case "Bestiary", "Map", "Friends", "Party" -> "Coming soon.";
                        default -> "";
                    });
                }
            }
            case "cell" -> {
                if (!"Inventory".equals(route)) return;
                String[] coordinates = data.value.split(",");
                if (coordinates.length != 2) return;
                int x, y;
                try { x = Integer.parseInt(coordinates[0]); y = Integer.parseInt(coordinates[1]); }
                catch (NumberFormatException e) { return; }
                if (x < 0 || x >= InventoryGridGeometry.COLUMNS || y < 0 || y >= InventoryGridGeometry.ROWS) return;
                if (grab == null) {
                    grab = layout.grab(x, y).orElse(null);
                    if (grab == null) {
                        commands.set("#ItemContext.Text", "Empty cell.");
                        commands.set("#Status.Text", "");
                    } else {
                        bag.select(commands, grab.id(), true);
                        var selected = layout.at(x, y).orElseThrow();
                        var view = items.get(grab.id());
                        commands.set("#ItemContext.Text", view.visibleName
                                + (view.quantity > 1 ? "  ×" + view.quantity : ""));
                        commands.set("#Status.Text", "Choose a destination.");
                    }
                } else {
                    bag.select(commands, grab.id(), false);
                    commands.set("#ItemContext.Text", "Selection released.");
                    var view = items.get(grab.id());
                    var spatial = store.getComponent(ref, SpatialBagComponent.getComponentType());
                    if (spatial != null && spatial.mode(player.getUuid()) != SpatialBagComponent.OwnershipMode.NATIVE) {
                        var current = spatial.state(player.getUuid());
                        var entryId = UUID.fromString(grab.id());
                        var source = current.entry(entryId).orElse(null);
                        if (source == null) { grab = null; commands.set("#Status.Text", "Item changed. Reopen inventory."); break; }
                        var result = current.move(UUID.randomUUID(), current.revision(), entryId,
                                source.position().x() + grab.offsetX(), source.position().y() + grab.offsetY(), x, y);
                        if (result.accepted()) {
                            spatial.publish(player.getUuid(), current, result.bag());
                            for (var changed : result.bag().entries()) {
                                var previous = current.entry(changed.id()).orElse(null);
                                if (previous != null && !previous.position().equals(changed.position()))
                                    bag.move(commands, new SpatialLayout.Entry(changed.id().toString(), changed.size(), changed.position()));
                            }
                            savePending = true;
                            commands.set("#Status.Text", "");
                            var entity = store.getComponent(ref, Player.getComponentType());
                            if (entity == null) { saveFailed = true; savePending = false; break; }
                            var world = store.getExternalData().getWorld();
                            entity.saveConfig(world, entity.toHolder(), true).whenComplete((ignored, error) -> world.execute(() -> {
                                if (dismissed) return;
                                savePending = false;
                                saveFailed = error != null;
                                var update = new UICommandBuilder();
                                update.set("#Status.Text", error == null ? "" :
                                        "Could not save inventory. Reconnect before moving more items.");
                                sendUpdate(update, new UIEventBuilder(), false);
                            }));
                        } else commands.set("#Status.Text", "That item does not fit there.");
                        grab = null;
                        break;
                    }
                    var storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
                    // The whole layout must still refer to the same native payloads before moving metadata.
                    boolean unchanged = storage != null && items.values().stream().allMatch(v ->
                            v.slot < storage.getInventory().getCapacity() && v.fingerprint.equals(storage.getInventory().getItemStack(v.slot)));
                    var result = unchanged ? layout.moveOrSwapSnapped(grab, x, y) : null;
                    if (result != null && result.accepted())
                        for (var changed : result.changed()) bag.move(commands, changed);
                    String reason = !unchanged ? "NATIVE_CHANGED" : result.outcome().name();
                    commands.set("#Status.Text", switch (reason) {
                        case "MOVED", "SWAPPED" -> "";
                        case "OUT_OF_BOUNDS", "AMBIGUOUS", "NO_FIT" -> "That item does not fit there.";
                        case "STALE" -> "Item changed. Select it again.";
                        default -> "Item changed. Reopen inventory.";
                    });
                    record("PLACEMENT", Map.of("accepted", result != null && result.accepted(),
                            "outcome", reason, "nativeMutation", false, "grabOffset", grab.offsetX() + "," + grab.offsetY(),
                            "releaseCell", x + "," + y)); grab = null;
                }
            }
            default -> { return; }
        }
        sendUpdate(commands, events, false);
    }

    private void commitSpatialAction(Ref<EntityStore> ref, Store<EntityStore> store,
                                     UICommandBuilder commands, UIEventBuilder events,
                                     SpatialBagComponent owner, SpatialBagAggregate before,
                                     SpatialBagAggregate.Result result, String success) {
        if (!result.accepted()) {
            commands.set("#Status.Text", result.receipt().outcome() == SpatialBagAggregate.Outcome.NO_FIT
                    ? ""
                    : "Inventory changed: " + result.receipt().outcome());
            return;
        }
        owner.publish(player.getUuid(), before, result.bag());
        snapshot(ref, store, commands, events);
        savePending = true;
        commands.set("#Status.Text", success);
        savePlacement(ref, store);
    }

    private void nativeDrop(Ref<EntityStore> ref, Store<EntityStore> store, Data data,
                            UICommandBuilder commands, UIEventBuilder events) {
        int grabbed = nativeGrabCell;
        nativeGrabCell = -1;
        int outsideSource = data.sourceSlotId;
        if (outsideSource < 0 && grabbed >= 0)
            outsideSource = NativeWorkspaceGrid.sourceCell(layout, grabbed);
        // Some client builds surface an outside-grid release as a CustomUI
        // Dropped event rather than a DropItemStack packet. Route both through
        // the same server ECS drop owner; never treat a negative cell as a move.
        if (data.slotIndex < 0 && nativeWindow != null
                && (data.sourceInventorySectionId == nativeWindow.getId()
                    || (data.sourceInventorySectionId == Integer.MIN_VALUE && grabbed >= 0))
                && outsideSource >= 0 && outsideSource < InventoryGridGeometry.CELLS
                && (grabbed < 0 || outsideSource == NativeWorkspaceGrid.sourceCell(layout, grabbed))) {
            pendingDropCell=outsideSource;
            pendingDropQuantity=data.itemStackQuantity;
            store.invoke(ref, new com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent.PlayerRequest(
                    nativeWindow.getId(), (short) outsideSource));
            return;
        }
        if (data.sourceInventorySectionId != nativeWindow.getId()
                || data.sourceSlotId < 0 || data.sourceSlotId >= InventoryGridGeometry.CELLS
                || data.slotIndex < 0 || data.slotIndex >= InventoryGridGeometry.CELLS
                || !nativeVirtualEmpty()) {
            commands.set("#Status.Text", "Item changed. Reopen inventory and try again.");
            return;
        }
        var entity = store.getComponent(ref, Player.getComponentType());
        if (entity == null || entity.getWindowManager().getWindow(nativeWindow.getId()) != nativeWindow) {
            commands.set("#Status.Text", "Inventory changed. Reopen it and try again.");
            return;
        }
        // SlotClicking preserves the covered-cell offset. If this client did not
        // emit it, only a top-left source is accepted rather than guessing an offset.
        if (grabbed < 0) grabbed = data.sourceSlotId;
        if (NativeWorkspaceGrid.sourceCell(layout, grabbed) != data.sourceSlotId) {
            commands.set("#Status.Text", "Item changed. Reopen inventory and try again.");
            return;
        }
        var selected = layout.at(grabbed % InventoryGridGeometry.COLUMNS, grabbed / InventoryGridGeometry.COLUMNS).orElse(null);
        var spatial = store.getComponent(ref, SpatialBagComponent.getComponentType());
        if (selected == null) {
            commands.set("#Status.Text", "Inventory changed. Reopen it and try again.");
            return;
        }
        if (spatial == null || spatial.mode(player.getUuid()) == SpatialBagComponent.OwnershipMode.NATIVE) {
            var storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
            boolean unchanged = storage != null && items.values().stream().allMatch(view ->
                    view.slot >= 0 && view.slot < storage.getInventory().getCapacity()
                            && view.fingerprint.equals(storage.getInventory().getItemStack(view.slot)));
            var nativeGrab = unchanged ? layout.grab(grabbed % InventoryGridGeometry.COLUMNS, grabbed / InventoryGridGeometry.COLUMNS).orElse(null) : null;
            var preview = nativeGrab == null ? null : layout.moveOrSwapSnapped(nativeGrab,
                    data.slotIndex % InventoryGridGeometry.COLUMNS, data.slotIndex / InventoryGridGeometry.COLUMNS);
            if (preview == null || !preview.accepted()) {
                commands.set("#Status.Text", unchanged ? "That item does not fit there."
                        : "Native Storage changed. Refresh Inventory.");
                return;
            }
            for (var changed : preview.changed()) bag.move(commands, changed);
            NativeWorkspaceGrid.write(commands, nativeGridSelector, layout, id -> items.get(id).fingerprint);
            commands.set("#Status.Text", "");
            return;
        }
        var current = spatial.state(player.getUuid());
        UUID entryId;
        try { entryId = UUID.fromString(selected.id()); }
        catch (IllegalArgumentException invalid) { return; }
        var source = current.entry(entryId).orElse(null);
        if (source == null || source.position().x() != selected.position().x()
                || source.position().y() != selected.position().y()) {
            commands.set("#Status.Text", "Item changed. Reopen inventory and try again.");
            return;
        }
        var target = layout.at(data.slotIndex % InventoryGridGeometry.COLUMNS, data.slotIndex / InventoryGridGeometry.COLUMNS).orElse(null);
        boolean partial = data.itemStackQuantity > 0
                && data.itemStackQuantity < source.payload().getQuantity();
        boolean merge = target != null && !target.id().equals(selected.id())
                && ItemStack.isStackableWith(source.payload(), items.get(target.id()).fingerprint);
        int targetX = data.slotIndex % InventoryGridGeometry.COLUMNS;
        int targetY = data.slotIndex / InventoryGridGeometry.COLUMNS;
        var result = partial
                ? current.placeQuantity(UUID.randomUUID(), current.revision(), entryId,
                        data.itemStackQuantity,
                        merge ? targetX : targetX - (grabbed % InventoryGridGeometry.COLUMNS - source.position().x()),
                        merge ? targetY : targetY - (grabbed / InventoryGridGeometry.COLUMNS - source.position().y()))
                : merge
                ? current.merge(UUID.randomUUID(), current.revision(), entryId, UUID.fromString(target.id()))
                : current.move(UUID.randomUUID(), current.revision(), entryId,
                        grabbed % InventoryGridGeometry.COLUMNS, grabbed / InventoryGridGeometry.COLUMNS, data.slotIndex % InventoryGridGeometry.COLUMNS, data.slotIndex / InventoryGridGeometry.COLUMNS);
        if (!result.accepted()) {
            commands.set("#Status.Text", "That item does not fit there.");
            return;
        }
        spatial.publish(player.getUuid(), current, result.bag());
        if (merge || partial) {
            snapshot(ref, store, commands, events);
            savePending = true;
            commands.set("#Status.Text", "");
            savePlacement(ref, store);
            return;
        }
        layout = new SpatialLayout(InventoryGridGeometry.COLUMNS, InventoryGridGeometry.ROWS);
        for (var changed : result.bag().entries()) {
            String id = changed.id().toString();
            if (!layout.add(id, changed.size(), changed.position()))
                throw new IllegalStateException("Committed native cursor placement overlaps");
            var previous = current.entry(changed.id()).orElse(null);
            if (previous != null && !previous.position().equals(changed.position()))
                bag.move(commands, new SpatialLayout.Entry(id, changed.size(), changed.position()));
        }
        NativeWorkspaceGrid.write(commands, nativeGridSelector, layout, id -> items.get(id).fingerprint);
        commands.set("#ItemContext.Text", "");
        savePending = true;
        commands.set("#Status.Text", "");
        savePlacement(ref, store);
    }

    private void savePlacement(Ref<EntityStore> ref, Store<EntityStore> store) {
        var entity = store.getComponent(ref, Player.getComponentType());
        if (entity == null) { savePending = false; saveFailed = true; return; }
        var world = store.getExternalData().getWorld();
        entity.saveConfig(world, entity.toHolder(), true).whenComplete((ignored, error) -> world.execute(() -> {
            if (dismissed) return;
            savePending = false;
            saveFailed = error != null;
            var update = new UICommandBuilder();
            update.set("#Status.Text", error == null ? "" :
                    "Could not save inventory. Reconnect before moving more items.");
            sendUpdate(update, new UIEventBuilder(), false);
        }));
    }

    private boolean nativeVirtualEmpty() {
        for (short slot = 0; slot < nativeVirtualBag.getCapacity(); slot++)
            if (!ItemStack.isEmpty(nativeVirtualBag.getItemStack(slot))) return false;
        return true;
    }
    private String renderQuests(UICommandBuilder commands) {
        commands.clear("#QuestRows");
        com.hypixel.hytale.builtin.adventure.objectives.ObjectivePlugin provider;
        try { provider = com.hypixel.hytale.builtin.adventure.objectives.ObjectivePlugin.get(); }
        catch (RuntimeException unavailable) { provider = null; }
        if (provider == null || provider.getObjectiveDataStore() == null)
            return "Native objective provider unavailable. No quest state is inferred.";
        var active = provider.getObjectiveDataStore().getObjectiveCollection().stream()
                .filter(objective -> !objective.isCompleted() && objective.getPlayerUUIDs().contains(player.getUuid()))
                .sorted(Comparator.comparing(com.hypixel.hytale.builtin.adventure.objectives.Objective::getObjectiveId))
                .limit(64).toList();
        for (int i = 0; i < active.size(); i++) {
            var objective = active.get(i);
            commands.append("#QuestRows", "RpgInventoryQuestRow.ui");
            String row = "#QuestRows[" + i + "]";
            var asset = objective.getObjectiveAsset();
            String title = asset == null ? null : translated(asset.getObjectiveTitleKey());
            if (title == null || title.isBlank()) title = objective.getObjectiveId();
            String description = objective.getCurrentDescription();
            commands.set(row + " #Title.Text", title);
            commands.set(row + " #Detail.Text", description == null || description.isBlank()
                    ? "Current objective details unavailable" : description);
        }
        return active.isEmpty() ? "No active objectives for this character."
                : active.size() + " active objective" + (active.size() == 1 ? "" : "s") + " from the native provider.";
    }
    private String translated(String key) {
        if (key == null || key.isBlank()) return null;
        try { return I18nModule.get().getMessage(player.getLanguage(), key); }
        catch (RuntimeException unavailable) { return null; }
    }
    @Override public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
        dismissed = true; grab = null; items.clear();
        record("DISMISSED", Map.of("session", session));
        onDismiss.run();
        if (openSkillTreeOnDismiss && skillTreeEntry != null)
            store.getExternalData().getWorld().execute(skillTreeEntry);
    }
    private void record(String event, Map<String, ?> details) {
        if (probe != null) probe.record(player, event, details);
    }
    public static final class Data {
        static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d,v)->d.action=v, d->d.action).add()
                .append(new KeyedCodec<>("Value", Codec.STRING), (d,v)->d.value=v, d->d.value).add()
                .append(new KeyedCodec<>("Session", Codec.STRING), (d,v)->d.session=v, d->d.session).add()
                .append(new KeyedCodec<>("@Query", Codec.STRING), (d,v)->d.query=v, d->d.query).add()
                .append(new KeyedCodec<>("@Quantity", Codec.STRING), (d,v)->d.quantity=v, d->d.quantity).add()
                .append(new KeyedCodec<>("Revision", Codec.STRING), (d,v)->d.revision=v, d->d.revision).add()
                .append(new KeyedCodec<>("SlotIndex", Codec.INTEGER), (d,v)->d.slotIndex=v, d->d.slotIndex).add()
                .append(new KeyedCodec<>("SourceSlotId", Codec.INTEGER), (d,v)->d.sourceSlotId=v, d->d.sourceSlotId).add()
                .append(new KeyedCodec<>("SourceInventorySectionId", Codec.INTEGER),
                        (d,v)->d.sourceInventorySectionId=v, d->d.sourceInventorySectionId).add()
                .append(new KeyedCodec<>("ItemStackQuantity", Codec.INTEGER),
                        (d,v)->d.itemStackQuantity=v, d->d.itemStackQuantity).add().build();
        private String action="", value="", session="", query="", quantity="", revision="";
        private int slotIndex=-1, sourceSlotId=-1, sourceInventorySectionId=Integer.MIN_VALUE,
                itemStackQuantity=-1;
    }
}
