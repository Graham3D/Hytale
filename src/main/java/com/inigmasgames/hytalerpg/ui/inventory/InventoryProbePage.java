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
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
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
import com.inigmasgames.hytalerpg.gear.HytaleGearLoot;
import com.inigmasgames.hytalerpg.progress.AttributeAllocationService;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleEquipmentAdapter;
import com.inigmasgames.hytalerpg.ui.HytaleResourceViewAdapter;
import com.inigmasgames.hytalerpg.ui.RpgUiProjectionService;
import com.inigmasgames.hytalerpg.ui.trace.UiInteractionTrace;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
    private final java.util.function.Supplier<HytaleGearLoot> gearTransfer;
    private final String session = UUID.randomUUID().toString();
    private final Map<String, View> items = new LinkedHashMap<>();
    private final Map<String, ItemStack> equippedTooltipStacks = new HashMap<>();
    private String hoveredTooltipKey;
    private int hoveredBagCell = -1;
    private SpatialLayout layout = new SpatialLayout(InventoryGridGeometry.COLUMNS, InventoryGridGeometry.ROWS);
    private SpatialLayout.Grab grab;
    private int nativeGrabCell = -1;
    private int pendingDropCell = -1, pendingDropQuantity = -1;
    private UUID selectedSpatialEntry;
    private String selectedGearSlot, selectedGearPayload;
    private short selectedNativeSlot = -1;
    private String nativeGridSelector;
    private String query = "", route = "Inventory";
    private AdvancedStatsViewModel advancedModel;
    private boolean advanced;
    private boolean dismissed;
    private boolean openSkillTreeOnDismiss;
    private boolean savePending, saveFailed;
    private UiInteractionTrace.Action traceAction;
    private String traceOutcome;
    private long toastSequence;

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
        this(player, projection, probe, allocation, skillTreeEntry, onDismiss,
                nativeWindow, nativeVirtualBag, () -> null);
    }
    public InventoryProbePage(PlayerRef player, RpgUiProjectionService projection,
                              NativeInventoryEntryProbe probe, AttributeAllocationService allocation,
                              Runnable skillTreeEntry, Runnable onDismiss,
                              ContainerWindow nativeWindow, SimpleItemContainer nativeVirtualBag,
                              java.util.function.Supplier<HytaleGearLoot> gearTransfer) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.player = player; this.projection = projection; this.probe = probe;
        this.allocation = allocation; this.skillTreeEntry = skillTreeEntry;
        this.onDismiss = onDismiss;
        this.nativeWindow = nativeWindow; this.nativeVirtualBag = nativeVirtualBag;
        this.gearTransfer = gearTransfer;
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
        var diagnostic = UiInteractionTrace.current();
        if (diagnostic != null) diagnostic.event(player.getUuid(), "PAGE_OPEN", "",
                Map.of("page", "inventory", "nativeWindow", nativeWindow != null));
        commands.append("RpgInventoryProbe.ui");
        commands.append("#ManagedTooltipHost", "RpgGearTooltip.ui");
        commands.append("#ComparisonTooltipHost", "RpgGearTooltip.ui");
        for (var name : ROUTES) events.addEventBinding(CustomUIEventBindingType.Activating, "#Route" + name, event("route", name), false);
        for (var name : List.of("Close", "Refresh", "Cancel", "Advanced", "Back"))
            events.addEventBinding(CustomUIEventBindingType.Activating, "#" + name, event(name.toLowerCase(Locale.ROOT), ""), false);
        for (var name : List.of("Sort", "Consolidate", "QuickEquip", "DropSelected"))
            events.addEventBinding(CustomUIEventBindingType.Activating, "#" + name,
                    event(name.toLowerCase(Locale.ROOT), ""), false);
        for (String slot : List.of("Weapon", "Offhand", "Head", "Chest", "Hands", "Legs"))
            events.addEventBinding(CustomUIEventBindingType.RightClicking, "#EquipDrop" + slot,
                    event("quickunequip", slot), false);
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
        hideManagedTooltip(commands);
        grab = null; nativeGrabCell = -1; selectedSpatialEntry = null; selectedNativeSlot = -1;
        selectedGearSlot = null; selectedGearPayload = null;
        items.clear(); equippedTooltipStacks.clear(); layout = new SpatialLayout(InventoryGridGeometry.COLUMNS, InventoryGridGeometry.ROWS);
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
                    || nativeVirtualBag.getCapacity() != NativeGearTargetGrid.CAPACITY)
                throw new IllegalStateException("Native workspace window has a different grid capacity");
            nativeGridSelector = bag.nativeGrid(commands, section);
            events.addEventBinding(CustomUIEventBindingType.RightClicking,
                    "#WorkspaceInput", event("quickequiphover", ""), false);
            writeNativeWorkspace(ref, store, commands);
            for (var binding : new CustomUIEventBindingType[]{CustomUIEventBindingType.SlotClicking,
                    CustomUIEventBindingType.SlotMouseDragExited,
                    CustomUIEventBindingType.SlotClickPressWhileDragging,
                    CustomUIEventBindingType.Dropped, CustomUIEventBindingType.DragCancelled})
                events.addEventBinding(binding, nativeGridSelector, event(binding.name(), ""), false);
            events.addEventBinding(CustomUIEventBindingType.SlotMouseEntered,
                    nativeGridSelector, event("bagHoverEnter", ""), false);
            events.addEventBinding(CustomUIEventBindingType.SlotMouseExited,
                    nativeGridSelector, event("bagHoverExit", ""), false);
            for (String slot : List.of("Weapon", "Offhand", "Head", "Chest", "Hands", "Legs",
                    "RingLeft", "RingRight")) {
                NativeGearTargetGrid.append(commands, slot, section);
                events.addEventBinding(CustomUIEventBindingType.Dropped,
                        "#GearTarget" + slot, event("gearDropped", slot), false);
                events.addEventBinding(CustomUIEventBindingType.SlotMouseEntered,
                        "#GearTarget" + slot, event("gearHoverEnter", slot), false);
                events.addEventBinding(CustomUIEventBindingType.SlotMouseExited,
                        "#GearTarget" + slot, event("gearHoverExit", slot), false);
                if (!slot.startsWith("Ring")) {
                    events.addEventBinding(CustomUIEventBindingType.SlotClicking,
                            "#GearTarget" + slot, event("gearSelected", slot), false);
                } else {
                    events.addEventBinding(CustomUIEventBindingType.SlotClicking,
                            "#GearTarget" + slot, event("ring", slot.equals("RingLeft") ? "left" : "right"), false);
                }
            }
            NativeOutsideDropTarget.append(commands, section);
            events.addEventBinding(CustomUIEventBindingType.Dropped,
                    NativeOutsideDropTarget.SELECTOR, event("outsideDropped", ""), false);
        }
        for (String slot : List.of("Weapon", "Offhand", "Head", "Chest", "Hands", "Legs",
                "RingLeft", "RingRight")) {
            commands.set("#GearSelected" + slot + ".Visible", false);
            commands.set("#GearInvalid" + slot + ".Visible", false);
        }
        toastSequence++;
        commands.set("#RequirementToast.Visible", false);
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
    static void renderAttributeAvailability(UICommandBuilder commands, int points, boolean allocationAvailable) {
        commands.set("#Points.Text", points + " points remaining");
        commands.set("#Points.Visible", true);
        boolean available = allocationAvailable && points > 0;
        for (int i = 0; i < RpgAttribute.values().length; i++) {
            commands.set("#AttributePlus" + i + ".Visible", available);
            commands.set("#AttributePlus" + i + ".Disabled", !available);
        }
    }

    private void renderCharacter(Ref<EntityStore> ref, Store<EntityStore> store,
                                 UICommandBuilder commands, UIEventBuilder events) {
        var stats = store.getComponent(ref, EntityStatMap.getComponentType());
        if (stats == null) return;
        var resourceSnapshot = new HytaleResourceViewAdapter().read(stats);
        var model = projection.character(player.getUuid(), player.getUsername(), resourceSnapshot);
        commands.set("#PlayerName.Text", model.displayName());
        String progress = model.xp().level() == 99 ? ""
                : "XP " + model.xp().xpIntoLevel() + " / " + model.xp().xpToNext();
        commands.set("#Level.Text", "Level " + model.xp().level());
        commands.set("#XpText.Text", progress);
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
                    model.attributeText(attributes[i]));
            if (allocation != null) events.addEventBinding(CustomUIEventBindingType.Activating, "#AttributePlus" + i,
                    event("allocate", attributes[i].name()).append("Revision", Long.toString(model.revision())), false);
        }
        renderAttributeAvailability(commands, model.unspentAttributePoints(), allocation != null);
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
        var equippedEffects = GearNativeItems.effects(ref, store);
        var nativeDefense = NativeArmorDefenseView.read(ref, store);
        advancedModel = AdvancedStatsViewModel.resolve(model, equippedEffects, nativeDefense, magicFind);
        advancedModel.trace(player.getUuid(), model, equippedEffects, nativeDefense, magicFind);
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
        var snapshot=GearNativeItems.effects(ref,store).snapshot();
        for(var channel:com.inigmasgames.hytalerpg.gear.GearCombatEffects.Channel.values()){
            if(channel==com.inigmasgames.hytalerpg.gear.GearCombatEffects.Channel.PHYSICAL)continue;
            String title=channel.name().charAt(0)+channel.name().substring(1).toLowerCase(java.util.Locale.ROOT);
            double nativeValue=defense.directPercent(com.inigmasgames.hytalerpg.gear.GearCombatEffects.nativeCause(channel)).orElse(0)/100.0;
            double total=com.inigmasgames.hytalerpg.gear.GearCombatEffects.resistance(snapshot,channel,nativeValue);
            commands.set("#Resist"+title+".Text",resistance(java.util.OptionalDouble.of(total*100)));
        }
    }
    private static String resistance(java.util.OptionalDouble percent) {
        return percent.isEmpty() || Math.abs(percent.getAsDouble()) < 0.0001
                ? "0%" : one(percent.getAsDouble()) + "%";
    }
    private void renderEquipment(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commands) {
        var tooltip=GearNativeItems.tooltipViewer(ref,store);
        showEquipment(commands, "Weapon", tooltip.apply(InventoryComponent.getItemInHand(store, ref)));
        var utility = store.getComponent(ref, InventoryComponent.Utility.getComponentType());
        showEquipment(commands, "Offhand", tooltip.apply(utility == null ? null : utility.getActiveItem()));
        var armor = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
        for (var slot : com.hypixel.hytale.protocol.ItemArmorSlot.values()) {
            ItemStack stack = armor == null || slot.getValue() >= armor.getInventory().getCapacity()
                    ? null : armor.getInventory().getItemStack((short) slot.getValue());
            showEquipment(commands, slot.name(), tooltip.apply(stack));
        }
        var player = store.getComponent(ref, PlayerRef.getComponentType());
        var spatial = store.getComponent(ref, SpatialBagComponent.getComponentType());
        boolean spatialOwner = player != null && spatial != null
                && spatial.mode(player.getUuid()) != SpatialBagComponent.OwnershipMode.NATIVE;
        RpgRingEquipment rings = player == null || spatial == null ? new RpgRingEquipment(null, null)
                : spatial.rings(player.getUuid());
        for (String side : List.of("Left", "Right")) {
            var ring = rings.get(side.toLowerCase(Locale.ROOT));
            commands.set("#RingEquip" + side + ".Visible",
                    spatialOwner && nativeWindow == null);
            commands.set("#EquipDropRing" + side + ".Visible",
                    spatialOwner && nativeWindow != null);
            commands.set("#RingItem" + side + ".Visible", ring != null);
            commands.set("#RingEmpty" + side + ".Visible", ring == null);
            commands.set("#RingEquip" + side + ".TooltipText", ring == null
                    ? "Drag a Copper Ring from the bag to equip."
                    : "Click to select, then click a free bag location.");
            if (ring != null) commands.set("#RingItem" + side + ".ItemId", ring.payload().getItemId());
            if (nativeWindow != null)
                NativeGearTargetGrid.write(commands, "Ring" + side, ring == null ? null : ring.payload());
            if (ring != null) equippedTooltipStacks.put("Ring" + side, ring.payload());
        }
    }
    private void writeNativeWorkspace(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commands) {
        var tooltip=GearNativeItems.tooltipViewer(ref,store);
        NativeWorkspaceGrid.write(commands,nativeGridSelector,layout,id->tooltip.apply(items.get(id).fingerprint));
    }
    private void showEquipment(UICommandBuilder commands, String slot, ItemStack stack) {
        boolean occupied = !ItemStack.isEmpty(stack);
        if (occupied) equippedTooltipStacks.put(slot, stack);
        commands.set("#Equip" + slot + ".Visible", occupied);
        commands.set("#Empty" + slot + ".Visible", !occupied);
        if (occupied) commands.set("#Equip" + slot + ".ItemId", stack.getItemId());
        if (nativeWindow != null) NativeGearTargetGrid.write(commands, slot, stack);
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
    private void hideManagedTooltip(UICommandBuilder commands) {
        hoveredTooltipKey = null;
        ManagedGearTooltip.hide(commands);
    }
    private void showManagedTooltip(Ref<EntityStore> ref, Store<EntityStore> store,
                                    UICommandBuilder commands, String key, ItemStack stack,
                                    ManagedGearTooltip.Rect slot) {
        if (ItemStack.isEmpty(stack)) {
            hideManagedTooltip(commands);
            return;
        }
        try {
            ManagedGearTooltip.Model model;
            if (GearNativeItems.managed(stack)) {
                var view = GearNativeItems.tooltipView(stack, ref, store, this::localizedText);
                if (view == null) { hideManagedTooltip(commands); return; }
                model = ManagedGearTooltip.model(view);
            } else model = nativeItemTooltip(stack);
            // The installed CustomUI contract supplies no held-Alt state. Keep the
            // paired renderer available, but never infer a modifier from hover alone.
            ManagedGearTooltip.show(commands, model, slot);
            hoveredTooltipKey = key;
        } catch (RuntimeException unavailable) {
            // A damaged/custody-pending or unresolved item must never disconnect a hovering player.
            hideManagedTooltip(commands);
        }
    }
    private ManagedGearTooltip.Model nativeItemTooltip(ItemStack stack) {
        Item item;
        try { item = Item.getAssetMap().getAsset(stack.getItemId()); }
        catch (RuntimeException unavailable) { item = null; }
        ItemQuality quality = null;
        try {
            if (stack.getQualityIndex() >= 0)
                quality = ItemQuality.getAssetMap().getAsset(stack.getQualityIndex());
        } catch (RuntimeException unavailable) { /* An unqualified item still has a tooltip. */ }
        String qualityId = quality == null ? "Common" : quality.getId();
        String label = quality == null ? "Common" : translated(quality.getLocalizationKey());
        if (label == null || label.isBlank()) label = qualityId;
        var presentation = com.inigmasgames.hytalerpg.gear.GearRarityPresentation.forQualityAsset(qualityId);
        if (presentation != null) label = presentation.label;
        String band = presentation != null
                ? (presentation == com.inigmasgames.hytalerpg.gear.GearRarityPresentation.NORMAL
                    || presentation == com.inigmasgames.hytalerpg.gear.GearRarityPresentation.SET
                    ? "Common" : presentation.label)
                : qualityId.equalsIgnoreCase("Legendary") ? "Legendary"
                : qualityId.equalsIgnoreCase("Epic") ? "Epic"
                : qualityId.equalsIgnoreCase("Rare") || qualityId.equalsIgnoreCase("Uncommon")
                || qualityId.equalsIgnoreCase("Magic") ? "Rare" : "Common";
        String description;
        try { description = NativeTooltipText.plain(localizedText(stack.getDisplayDescription()),
                this::nativeReferenceName, this::translated); }
        catch (RuntimeException unavailable) { description = ""; }
        var lines = description == null || description.isBlank() ? List.<String>of()
                : Arrays.stream(description.replace("\r", "").split("\n"))
                    .map(String::strip).filter(line -> !line.isBlank()).toList();
        String classification = item == null || item.getSubCategory() == null ? "" : item.getSubCategory();
        String name;
        try { name = localizedText(stack.getDisplayName()); }
        catch (RuntimeException unavailable) { name = null; }
        if (name == null || name.isBlank()) name = "Item";
        return ManagedGearTooltip.nativeModel(name, label, band, classification,
                lines, stack.getDurability(), stack.getMaxDurability());
    }
    private String nativeReferenceName(String itemId) {
        var asset = Item.getAssetMap().getAsset(itemId);
        if (asset != null) {
            String name = localizedText(asset.getTranslationMessage());
            if (name != null && !name.isBlank()) return name;
        }
        return translated("server.items." + itemId + ".name");
    }

    private String localizedText(Message message) {
        if (message == null) return null;
        var text = new StringBuilder();
        if (message.getRawText() != null) text.append(message.getRawText());
        else if (message.getMessageId() != null) {
            String translated = translated(message.getMessageId());
            if (translated != null) text.append(translated);
        }
        if (message.getChildren() != null) for (var child : message.getChildren()) {
            String part = localizedText(child);
            if (part != null) text.append(part);
        }
        return text.toString();
    }
    private static ManagedGearTooltip.Rect gearTooltipRect(String slot) {
        boolean left = switch (slot) {
            case "Weapon", "Head", "Chest", "Hands", "RingLeft" -> true;
            default -> false;
        };
        int x = left ? 371 : 747;
        int y = switch (slot) {
            case "Weapon", "Offhand" -> 72;
            case "Head", "Chest" -> 232;
            case "Hands", "Legs" -> 318;
            default -> 404;
        };
        return new ManagedGearTooltip.Rect(x, y, 74,
                slot.equals("Weapon") || slot.equals("Offhand") ? 148 : 74);
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
        // Deliberately never write Search.Value here or rebuild the page while typing.
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Data data) {
        var actionTrace = UiInteractionTrace.beginIfActive(player.getUuid(), traceComponent(data),
                traceElement(data), data.action, () -> traceInput(store, ref, data));
        traceAction = actionTrace;
        traceOutcome = null;
        UICommandBuilder traceCommands = null;
        try {
        if (dismissed || !session.equals(data.session) || data.action == null || data.value == null) return;
        if ("close".equals(data.action)) { grab = null; close(); return; }
        if (savePending || saveFailed) {
            if (data.action.endsWith("HoverExit")) {
                var clearing = new UICommandBuilder(); hideManagedTooltip(clearing);
                sendUpdate(clearing, new UIEventBuilder(), false);
            }
            traceOutcome = "SAVE_PENDING_OR_FAILED"; return;
        }
        if (probe != null && !probe.aliasActive(player)) { traceOutcome = "ALIAS_INACTIVE"; return; }
        var commands = new UICommandBuilder(); traceCommands = commands;
        var events = new UIEventBuilder();
        if (!data.action.endsWith("HoverEnter") && !data.action.endsWith("HoverExit"))
            hideManagedTooltip(commands);
        switch (data.action) {
            case "quickunequip" -> {
                if (nativeWindow == null || !"Inventory".equals(route) || nativeGrabCell >= 0 || !nativeVirtualEmpty()) break;
                var equipped = equippedTooltipStacks.get(data.value);
                if (ItemStack.isEmpty(equipped)) break;
                var size = CATALOG.size(GearNativeItems.nativeId(equipped.getItemId()));
                var target = layout.firstFit(size).orElse(null);
                if (target == null) break;
                selectGear(ref, store, data.value, commands);
                unequipAt(ref, store, target.y() * InventoryGridGeometry.COLUMNS + target.x(), commands, events);
            }
            case "quickequiphover" -> {
                if (nativeWindow == null || !"Inventory".equals(route) || !nativeVirtualEmpty()) break;
                var entry = hoveredBagCell < 0 ? null : layout.at(hoveredBagCell % InventoryGridGeometry.COLUMNS,
                        hoveredBagCell / InventoryGridGeometry.COLUMNS).orElse(null);
                var view = entry == null ? null : items.get(entry.id());
                String slot = view == null ? null : quickEquipSlot(view.fingerprint);
                if (slot == null) break; // Native stack quantity popup retains right-click for non-gear.
                clearGearSelection(commands);
                var quick = new Data();
                quick.action = "gearDropped";
                quick.sourceInventorySectionId = nativeWindow.getId();
                quick.sourceSlotId = NativeWorkspaceGrid.sourceCell(entry);
                quick.slotIndex = 0;
                quick.itemStackQuantity = 1;
                nativeGrabCell = quick.sourceSlotId;
                gearDrop(ref, store, quick, slot, commands, events);
            }
            case "bagHoverEnter" -> {
                if (nativeWindow == null || !"Inventory".equals(route) || nativeGrabCell >= 0) break;
                int cell = data.slotIndex;
                hoveredBagCell = cell;
                if (cell < 0 || cell >= InventoryGridGeometry.CELLS) { hideManagedTooltip(commands); break; }
                var entry = layout.at(cell % InventoryGridGeometry.COLUMNS,
                        cell / InventoryGridGeometry.COLUMNS).orElse(null);
                var view = entry == null ? null : items.get(entry.id());
                if (view == null) { hideManagedTooltip(commands); break; }
                showManagedTooltip(ref, store, commands, "bag:" + entry.id(), view.fingerprint,
                        ManagedGearTooltip.bagRect(entry));
            }
            case "bagHoverExit" -> {
                if (hoveredBagCell == data.slotIndex) hoveredBagCell = -1;
                var hovered = data.slotIndex < 0 || data.slotIndex >= InventoryGridGeometry.CELLS ? null
                        : layout.at(data.slotIndex % InventoryGridGeometry.COLUMNS,
                                data.slotIndex / InventoryGridGeometry.COLUMNS).orElse(null);
                if (hovered == null || Objects.equals(hoveredTooltipKey, "bag:" + hovered.id()))
                    hideManagedTooltip(commands);
            }
            case "close" -> { grab = null; close(); return; }
            case "SlotClicking" -> {
                if (nativeWindow == null || !"Inventory".equals(route)) return;
                if (selectedGearSlot != null) {
                    if (data.slotIndex >= 0 && data.slotIndex < InventoryGridGeometry.CELLS)
                        unequipAt(ref, store, data.slotIndex, commands, events);
                    break;
                }
                if (nativeGrabCell < 0 && NativeWorkspaceGrid.sourceCell(layout, data.slotIndex) >= 0) {
                    nativeGrabCell = data.slotIndex;
                    var selected = layout.at(data.slotIndex % InventoryGridGeometry.COLUMNS, data.slotIndex / InventoryGridGeometry.COLUMNS).orElseThrow();
                    try { selectedSpatialEntry = UUID.fromString(selected.id()); }
                    catch (IllegalArgumentException nativePreview) { selectedSpatialEntry = null; }
                    var view = items.get(selected.id());
                    selectedNativeSlot = view.slot;
                    commands.set("#ItemContext.Text", view.visibleName
                            + (view.quantity > 1 ? "  ×" + view.quantity : ""));
                    traceDomain("SELECT_ITEM", "SELECTED", Map.of("itemId", view.itemId,
                            "entryId", selected.id(), "nativeSlot", view.slot));
                }
            }
            case "SlotMouseDragExited", "SlotClickPressWhileDragging" -> {
                if (nativeWindow == null || !"Inventory".equals(route) || nativeGrabCell >= 0) return;
                int sourceCell = data.sourceSlotId >= 0 ? data.sourceSlotId : data.slotIndex;
                if (sourceCell < 0 || sourceCell >= InventoryGridGeometry.CELLS) return;
                var selected = layout.at(sourceCell % InventoryGridGeometry.COLUMNS,
                        sourceCell / InventoryGridGeometry.COLUMNS).orElse(null);
                if (selected == null) return;
                nativeGrabCell = sourceCell;
                traceDomain("DRAG_SOURCE", "CAPTURED", Map.of("sourceCell", sourceCell,
                        "entryId", selected.id(), "event", data.action));
            }
            case "DragCancelled" -> {
                nativeGrabCell = -1;
                clearInvalidGear(commands);
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
                traceDomain("DROP_ITEM", "DROP_REQUESTED", Map.of("sourceCell", source,
                        "sourceOwner", "inventory", "destinationOwner", "world"));
                submitOutsideDrop(ref, store, source, -1);
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
                traceDomain("ARMOR_VISIBILITY", "UPDATED", Map.of("hidden", hidden));
            }
            case "Dropped" -> {
                if (nativeWindow == null || !"Inventory".equals(route)) return;
                if (selectedGearSlot != null && data.slotIndex >= 0
                        && data.slotIndex < InventoryGridGeometry.CELLS) {
                    unequipAt(ref, store, data.slotIndex, commands, events);
                    break;
                }
                String gearSlot = NativeGearTargetGrid.slotAt(data.slotIndex);
                if (gearSlot != null) gearDrop(ref, store, data, gearSlot, commands, events);
                else nativeDrop(ref, store, data, commands, events);
            }
            case "outsideDropped" -> {
                if (nativeWindow == null || !"Inventory".equals(route)) return;
                outsideDrop(ref, store, data, commands);
            }
            case "gearDropped" -> {
                if (nativeWindow == null || !"Inventory".equals(route)) return;
                gearDrop(ref, store, data, data.value, commands, events);
            }
            case "gearHoverEnter" -> {
                if (nativeWindow == null || !"Inventory".equals(route)) return;
                String slot = data.value;
                if (nativeGrabCell < 0) {
                    showManagedTooltip(ref, store, commands, "gear:" + slot,
                            equippedTooltipStacks.get(slot), gearTooltipRect(slot));
                    break;
                }
                var selected = layout.at(nativeGrabCell % InventoryGridGeometry.COLUMNS,
                        nativeGrabCell / InventoryGridGeometry.COLUMNS).orElse(null);
                var view = selected == null ? null : items.get(selected.id());
                if (view != null && !slot.startsWith("Ring")) {
                    var transfer = gearTransfer.get();
                    String feedback = null;
                    if (transfer != null) try {
                        feedback = transfer.requirementFeedback(store, ref,
                                view.fingerprint, equipmentSlotName(slot));
                    } catch (RuntimeException unavailable) {
                        // A preview must never disconnect the player; commit still fails closed.
                    }
                    if (feedback != null)
                        commands.set("#GearInvalidIcon" + slot + ".ItemId", view.itemId);
                    commands.set("#GearInvalid" + slot + ".Visible", feedback != null);
                }
            }
            case "gearHoverExit" -> {
                commands.set("#GearInvalid" + data.value + ".Visible", false);
                if (Objects.equals(hoveredTooltipKey, "gear:" + data.value)) hideManagedTooltip(commands);
            }
            case "gearSelected" -> {
                if (nativeWindow == null || !"Inventory".equals(route) || nativeGrabCell >= 0
                        || !nativeVirtualEmpty()) return;
                selectGear(ref, store, data.value, commands);
                traceDomain("SELECT_EQUIPMENT", selectedGearSlot == null ? "EMPTY_SLOT" : "SELECTED",
                        Map.of("equipmentSlot", data.value));
            }
            case "sort" -> {
                var spatial = store.getComponent(ref, SpatialBagComponent.getComponentType());
                if (spatial == null || spatial.mode(player.getUuid()) == SpatialBagComponent.OwnershipMode.NATIVE) {
                    com.hypixel.hytale.server.core.inventory.InventoryUtils.sortStorage(ref, store);
                    snapshot(ref, store, commands, events);
                    traceDomain("SORT", "NATIVE_SORT_REQUESTED", Map.of("owner", "native.storage"));
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
                    traceDomain("COMBINE_STACKS", "ACCEPTED", Map.of("owner", "native.storage"));
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
                    traceDomain("SPLIT_STACK", "ACCEPTED", Map.of("quantity", quantity,
                            "owner", "native.storage"));
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
                clearGearSelection(commands);
                commands.set("#ItemContext.Text", "Selection cleared.");
                commands.set("#Status.Text", "");
            }
            case "search" -> { query = data.query == null ? "" : data.query.substring(0, Math.min(data.query.length(), 256)); search(commands);
                traceDomain("SEARCH", "FILTER_UPDATED", Map.of("queryLength", query.length())); }
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
                traceDomain("ADVANCED_STATS", advanced ? "OPENED" : "CLOSED", Map.of("visible", advanced));
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
                    else if (spatial.rings(player.getUuid()).get(data.value) != null) {
                        selectGear(ref, store, data.value.equals("left") ? "RingLeft" : "RingRight", commands);
                        break;
                    }
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
                    traceOutcome = "SKILLTREE_HANDOFF";
                    actionTrace.stage("PAGE_CHANGE", Map.of("from", "inventory", "to", "skilltree"));
                    close();
                    return;
                }
                if ("Map".equals(data.value)) {
                    var entity = store.getComponent(ref, Player.getComponentType());
                    if (entity == null) return;
                    // Release only this page's empty cursor alias before asking the
                    // client to show its built-in Map page. No custom map is created.
                    if (nativeWindow != null && entity.getWindowManager().getWindow(nativeWindow.getId()) == nativeWindow)
                        entity.getWindowManager().closeWindow(ref, nativeWindow.getId(), store);
                    grab = null; nativeGrabCell = -1;
                    entity.getPageManager().setPage(ref, store, Page.Map);
                    traceOutcome = "NATIVE_MAP_HANDOFF";
                    actionTrace.stage("PAGE_CHANGE", Map.of("from", "inventory", "to", "native.map"));
                    return;
                }
                if (grab != null) bag.select(commands, grab.id(), false);
                grab = null; nativeGrabCell = -1; route = data.value;
                traceDomain("ROUTE", "ROUTE_CHANGED", Map.of("route", route));
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
                        case "Bestiary", "Friends", "Party" -> "Coming soon.";
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
                        traceDomain("SELECT_ITEM", "EMPTY_CELL", Map.of("clickedCell", x + "," + y));
                    } else {
                        bag.select(commands, grab.id(), true);
                        var selected = layout.at(x, y).orElseThrow();
                        var view = items.get(grab.id());
                        commands.set("#ItemContext.Text", view.visibleName
                                + (view.quantity > 1 ? "  ×" + view.quantity : ""));
                        commands.set("#Status.Text", "Choose a destination.");
                        traceDomain("SELECT_ITEM", "SELECTED", Map.of("entryId", grab.id(),
                                "offsetX", grab.offsetX(), "offsetY", grab.offsetY()));
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
                        traceDomain("MOVE", result.receipt().outcome().name(), Map.of(
                                "operationId", result.receipt().operationId(), "entryId", entryId,
                                "expectedRevision", current.revision(), "bagRevisionAfter", result.bag().revision()));
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
                    traceDomain("PREVIEW_MOVE", reason, Map.of("nativeMutation", false,
                            "releaseCell", x + "," + y));
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
        } catch (RuntimeException failure) {
            traceOutcome = "HANDLER_EXCEPTION";
            actionTrace.stage("VALIDATION", Map.of("result", "HANDLER_EXCEPTION",
                    "exceptionType", failure.getClass().getSimpleName()));
            throw failure;
        } finally {
            if (actionTrace.active()) {
                try {
                    if (traceCommands != null) tracePatch(actionTrace, traceCommands);
                    actionTrace.complete(traceOutcome == null ? "HANDLED" : traceOutcome,
                            traceAfter(store, ref));
                } catch (RuntimeException diagnosticFailure) {
                    actionTrace.complete("TRACE_INCOMPLETE", Map.of("diagnosticFailure",
                            diagnosticFailure.getClass().getSimpleName()));
                }
            }
            traceAction = null;
            traceOutcome = null;
        }
    }

    private void commitSpatialAction(Ref<EntityStore> ref, Store<EntityStore> store,
                                     UICommandBuilder commands, UIEventBuilder events,
                                     SpatialBagComponent owner, SpatialBagAggregate before,
                                      SpatialBagAggregate.Result result, String success) {
        traceDomain("SPATIAL_ACTION", result.receipt().outcome().name(), Map.of(
                "operationId", result.receipt().operationId(),
                "expectedRevision", before.revision(), "bagRevisionAfter", result.bag().revision()));
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

    private void clearGearSelection(UICommandBuilder commands) {
        if (selectedGearSlot != null) commands.set("#GearSelected" + selectedGearSlot + ".Visible", false);
        selectedGearSlot = null;
        selectedGearPayload = null;
    }

    private void selectGear(Ref<EntityStore> ref, Store<EntityStore> store, String slot,
                            UICommandBuilder commands) {
        var spatial = store.getComponent(ref, SpatialBagComponent.getComponentType());
        if (spatial == null || spatial.mode(player.getUuid()) == SpatialBagComponent.OwnershipMode.NATIVE) return;
        String nativeSlot = switch (slot) {
            case "Weapon" -> "held";
            case "Offhand" -> "offhand";
            case "Head", "Chest", "Hands", "Legs" -> slot.toLowerCase(Locale.ROOT);
            case "RingLeft", "RingRight" -> null;
            default -> throw new IllegalArgumentException("Unknown gear UI slot: " + slot);
        };
        String payload;
        if (nativeSlot == null) {
            var ring = spatial.rings(player.getUuid()).get(slot.equals("RingLeft") ? "left" : "right");
            if (ring == null) return;
            payload = ring.payloadJson();
        } else {
            var transfer = gearTransfer.get();
            if (transfer == null) return;
            var stack = transfer.equipmentForUi(store, ref, nativeSlot);
            if (ItemStack.isEmpty(stack)) return;
            payload = ItemStack.CODEC.encode(stack, new com.hypixel.hytale.codec.ExtraInfo())
                    .asDocument().toJson();
        }
        clearGearSelection(commands);
        selectedGearSlot = slot;
        selectedGearPayload = payload;
        selectedSpatialEntry = null;
        selectedNativeSlot = -1;
        commands.set("#GearSelected" + slot + ".Visible", true);
    }

    private void unequipAt(Ref<EntityStore> ref, Store<EntityStore> store, int targetCell,
                           UICommandBuilder commands, UIEventBuilder events) {
        if (selectedGearSlot == null || targetCell < 0 || targetCell >= InventoryGridGeometry.CELLS
                || !nativeVirtualEmpty()) { traceDomain("UNEQUIP", "INVALID_TARGET_OR_CURSOR", Map.of("targetCell", targetCell)); return; }
        var spatial = store.getComponent(ref, SpatialBagComponent.getComponentType());
        if (spatial == null || spatial.mode(player.getUuid()) == SpatialBagComponent.OwnershipMode.NATIVE) {
            traceDomain("UNEQUIP", "SPATIAL_OWNER_REQUIRED", Map.of("targetCell", targetCell)); return;
        }
        int x = targetCell % InventoryGridGeometry.COLUMNS;
        int y = targetCell / InventoryGridGeometry.COLUMNS;
        String slot = selectedGearSlot;
        String expected = selectedGearPayload;
        if (slot.startsWith("Ring")) {
            try {
                var result = spatial.unequipRingAt(player.getUuid(),
                        slot.equals("RingLeft") ? "left" : "right", CATALOG, x, y, expected);
                traceDomain("UNEQUIP_RING", result.receipt().outcome().name(), Map.of(
                        "equipmentSlot", slot, "targetCell", targetCell,
                        "operationId", result.receipt().operationId()));
                if (!result.accepted()) return;
                snapshot(ref, store, commands, events);
                savePending = true;
                savePlacement(ref, store);
            } catch (RuntimeException changed) {
                traceDomain("UNEQUIP_RING", "STALE_OR_INVALID_ITEM", Map.of("equipmentSlot", slot));
                clearGearSelection(commands);
            }
            return;
        }
        var transfer = gearTransfer.get();
        if (transfer == null) { traceDomain("UNEQUIP", "TRANSFER_UNAVAILABLE", Map.of("equipmentSlot", slot)); return; }
        String nativeSlot = slot.equals("Weapon") ? "held" : slot.toLowerCase(Locale.ROOT);
        String correlation = traceAction == null ? "" : traceAction.correlation();
        traceDomain("UNEQUIP", "TRANSFER_REQUESTED", Map.of("equipmentSlot", nativeSlot,
                "targetCell", targetCell, "sourceOwner", "equipment", "destinationOwner", "spatial.bag"));
        savePending = true;
        clearGearSelection(commands);
        var world = store.getExternalData().getWorld();
        transfer.transferEquipment(store, ref, player, world, null, nativeSlot, false, x, y, expected,
                result -> world.execute(() -> {
                    var diagnostic = UiInteractionTrace.current();
                    if (diagnostic != null) diagnostic.event(player.getUuid(), "DOMAIN_OPERATION", correlation,
                            Map.of("operation", "UNEQUIP", "equipmentSlot", nativeSlot, "result", result));
                    if (dismissed || !ref.isValid()) return;
                    savePending = false;
                    var update = new UICommandBuilder();
                    var updateEvents = new UIEventBuilder();
                    if ("Equipment transfer saved and finalized.".equals(result))
                        snapshot(ref, store, update, updateEvents);
                    else saveFailed = result.contains("uncertain") || result.contains("Reconnect");
                    sendUpdate(update, updateEvents, false);
                }));
    }

    /** Select the canonical armor/weapon destination; the transfer owner enforces all requirements. */
    static String quickEquipSlot(ItemStack stack) {
        if (ItemStack.isEmpty(stack) || stack.getQuantity() != 1) return null;
        var asset = stack.getItem();
        if (asset == null) return null;
        if (asset.getArmor() != null) return asset.getArmor().getArmorSlot().name();
        if (asset.getUtility() != null && asset.getUtility().isCompatible()
                && GearNativeItems.nativeId(stack.getItemId()).contains("Shield")) return "Offhand";
        if (asset.getWeapon() != null) return "Weapon";
        return null;
    }

    private static String equipmentSlotName(String slot) {
        return switch (slot) {
            case "Weapon" -> "held";
            case "Offhand" -> "offhand";
            default -> slot.toLowerCase(Locale.ROOT);
        };
    }
    private void clearInvalidGear(UICommandBuilder commands) {
        for (String slot : List.of("Weapon", "Offhand", "Head", "Chest", "Hands", "Legs",
                "RingLeft", "RingRight"))
            commands.set("#GearInvalid" + slot + ".Visible", false);
    }
    private void toastPhase(UICommandBuilder commands, int phase) {
        commands.set("#RequirementToast.Visible", phase >= 0);
        commands.set("#ToastLow.Visible", phase == 0);
        commands.set("#ToastMedium.Visible", phase == 1);
        commands.set("#ToastFull.Visible", phase == 2);
    }
    private void scheduleToastPhase(Ref<EntityStore> ref, Store<EntityStore> store,
                                    long sequence, int phase, long delayMillis) {
        var world = store.getExternalData().getWorld();
        CompletableFuture.delayedExecutor(delayMillis, TimeUnit.MILLISECONDS).execute(() ->
                world.execute(() -> {
                    if (dismissed || !ref.isValid() || toastSequence != sequence) return;
                    var update = new UICommandBuilder();
                    toastPhase(update, phase);
                    sendUpdate(update, new UIEventBuilder(), false);
                }));
    }
    private void showRequirementToast(Ref<EntityStore> ref, Store<EntityStore> store,
                                      UICommandBuilder commands, String message) {
        long sequence = ++toastSequence;
        for (String phase : List.of("Low", "Medium", "Full"))
            commands.set("#Toast" + phase + "Text.Text", message);
        toastPhase(commands, 0);
        scheduleToastPhase(ref, store, sequence, 1, 90);
        scheduleToastPhase(ref, store, sequence, 2, 180);
        scheduleToastPhase(ref, store, sequence, 1, 1180);
        scheduleToastPhase(ref, store, sequence, 0, 1270);
        scheduleToastPhase(ref, store, sequence, -1, 1360);
    }

    private void gearDrop(Ref<EntityStore> ref, Store<EntityStore> store, Data data, String slot,
                          UICommandBuilder commands, UIEventBuilder events) {
        int grabbed = nativeGrabCell;
        nativeGrabCell = -1;
        clearInvalidGear(commands);
        int sourceCell = data.sourceSlotId;
        if (sourceCell < 0 && grabbed >= 0)
            sourceCell = NativeWorkspaceGrid.sourceCell(layout, grabbed);
        boolean sameAlias = data.sourceInventorySectionId == nativeWindow.getId()
                || data.sourceInventorySectionId == Integer.MIN_VALUE && grabbed >= 0;
        // The source grid reports the patched absolute target cell; a target
        // binding may report its local cell instead. Both identify the same
        // fixed target UI element, while the source and item remain verified.
        boolean targetCell = NativeGearTargetGrid.contains(slot, data.slotIndex)
                || "gearDropped".equals(data.action)
                    && data.slotIndex >= 0 && data.slotIndex < NativeGearTargetGrid.count(slot);
        if (!sameAlias || sourceCell < 0 || sourceCell >= InventoryGridGeometry.CELLS
                || !targetCell || !nativeVirtualEmpty()) {
            traceDomain("EQUIP", "INVALID_SOURCE_OR_TARGET", Map.of("equipmentSlot", slot));
            commands.set("#Status.Text", "Item changed. Reopen inventory and try again.");
            return;
        }
        var entity = store.getComponent(ref, Player.getComponentType());
        if (entity == null || entity.getWindowManager().getWindow(nativeWindow.getId()) != nativeWindow) {
            commands.set("#Status.Text", "Inventory changed. Reopen it and try again.");
            return;
        }
        if (grabbed < 0) grabbed = sourceCell;
        if (NativeWorkspaceGrid.sourceCell(layout, grabbed) != sourceCell) {
            commands.set("#Status.Text", "Item changed. Reopen inventory and try again.");
            return;
        }
        var selected = layout.at(grabbed % InventoryGridGeometry.COLUMNS,
                grabbed / InventoryGridGeometry.COLUMNS).orElse(null);
        var spatial = store.getComponent(ref, SpatialBagComponent.getComponentType());
        if (selected == null || spatial == null
                || spatial.mode(player.getUuid()) == SpatialBagComponent.OwnershipMode.NATIVE) {
            traceDomain("EQUIP", "SPATIAL_OWNER_REQUIRED", Map.of("equipmentSlot", slot));
            commands.set("#Status.Text", "Equipment drag requires the spatial bag.");
            return;
        }
        UUID entryId;
        try { entryId = UUID.fromString(selected.id()); }
        catch (IllegalArgumentException nativePreview) {
            commands.set("#Status.Text", "Equipment drag requires the spatial bag.");
            return;
        }
        var source = spatial.state(player.getUuid()).entry(entryId).orElse(null);
        if (source == null || source.position().x() != selected.position().x()
                || source.position().y() != selected.position().y()
                || source.payload().getQuantity() != 1
                || data.itemStackQuantity > 1) {
            traceDomain("EQUIP", "STALE_OR_INVALID_QUANTITY", Map.of("equipmentSlot", slot));
            commands.set("#Status.Text", "Select one current equipment item.");
            return;
        }
        if (slot.equals("RingLeft") || slot.equals("RingRight")) {
            String side = slot.equals("RingLeft") ? "left" : "right";
            if (!RpgRingEquipment.eligible(source.payload())
                    || spatial.rings(player.getUuid()).get(side) != null) {
                commands.set("#Status.Text", "That ring does not fit an empty ring slot.");
                return;
            }
            try {
                var result = spatial.equipRing(player.getUuid(), side, entryId,
                        spatial.state(player.getUuid()).revision(), CATALOG);
                traceDomain("EQUIP_RING", result.receipt().outcome().name(), Map.of(
                        "equipmentSlot", side, "entryId", entryId,
                        "operationId", result.receipt().operationId()));
                if (!result.accepted()) {
                    commands.set("#Status.Text", "Ring transfer needs a free bag cell.");
                    return;
                }
                snapshot(ref, store, commands, events);
                savePending = true;
                savePlacement(ref, store);
            } catch (RuntimeException failure) {
                commands.set("#Status.Text", "Ring transfer could not be completed.");
            }
            return;
        }
        var transfer = gearTransfer.get();
        if (transfer == null) {
            commands.set("#Status.Text", "Equipment transfer is unavailable.");
            return;
        }
        String destination = equipmentSlotName(slot);
        String requirement;
        try { requirement = transfer.requirementFeedback(store, ref, source.payload(), destination); }
        catch (RuntimeException unavailable) { requirement = null; }
        if (requirement != null) {
            traceDomain("EQUIP", "REQUIREMENT_REJECTED", Map.of("equipmentSlot", destination,
                    "entryId", entryId, "message", requirement));
            showRequirementToast(ref, store, commands, requirement);
            return;
        }
        String correlation = traceAction == null ? "" : traceAction.correlation();
        traceDomain("EQUIP", "TRANSFER_REQUESTED", Map.of("equipmentSlot", destination,
                "entryId", entryId, "sourceOwner", "spatial.bag", "destinationOwner", "equipment"));
        savePending = true;
        commands.set("#Status.Text", "Equipping...");
        var world = store.getExternalData().getWorld();
        transfer.transferEquipment(store, ref, player, world, entryId, destination, true,
                result -> world.execute(() -> {
                    var diagnostic = UiInteractionTrace.current();
                    if (diagnostic != null) diagnostic.event(player.getUuid(), "DOMAIN_OPERATION", correlation,
                            Map.of("operation", "EQUIP", "equipmentSlot", destination, "result", result));
                    if (dismissed || !ref.isValid()) return;
                    savePending = false;
                    var update = new UICommandBuilder();
                    var updateEvents = new UIEventBuilder();
                    if ("Equipment transfer saved and finalized.".equals(result)) {
                        snapshot(ref, store, update, updateEvents);
                    } else {
                        saveFailed = result.contains("uncertain") || result.contains("Reconnect");
                        update.set("#Status.Text", result);
                        int feedback = result.indexOf("Insufficient ");
                        if (feedback >= 0) showRequirementToast(ref, store, update,
                                result.substring(feedback));
                        else if (result.contains("Requires level "))
                            showRequirementToast(ref, store, update,
                                    result.substring(result.indexOf("Requires level ")));
                    }
                    sendUpdate(update, updateEvents, false);
                }));
    }

    private void submitOutsideDrop(Ref<EntityStore> ref, Store<EntityStore> store,
                                   int sourceCell, int quantity) {
        var owner = gearTransfer.get();
        if (owner != null) {
            // This page's native window is an empty cursor alias. Route the
            // validated CustomUI gesture to the custody owner directly; a
            // synthetic native DropItemEvent may be cancelled by native systems.
            owner.dropFromWorkspace(store, ref, player, this, nativeWindow.getId(),
                    (short) sourceCell, quantity);
            return;
        }
        traceDomain("DROP_ITEM", "OWNER_UNAVAILABLE", Map.of("sourceCell", sourceCell));
    }

    private void outsideDrop(Ref<EntityStore> ref, Store<EntityStore> store, Data data,
                             UICommandBuilder commands) {
        int grabbed = nativeGrabCell;
        nativeGrabCell = -1;
        clearInvalidGear(commands);
        boolean sameAlias = data.sourceInventorySectionId == nativeWindow.getId()
                || data.sourceInventorySectionId == Integer.MIN_VALUE && grabbed >= 0;
        int source = data.sourceSlotId >= 0 ? data.sourceSlotId : grabbed;
        if (!sameAlias || !NativeOutsideDropTarget.isTarget(data.slotIndex)
                || source < 0 || source >= InventoryGridGeometry.CELLS || !nativeVirtualEmpty()) {
            traceDomain("DROP_ITEM", "INVALID_SOURCE_OR_TARGET", Map.of(
                    "sourceSlot", source, "targetSlot", data.slotIndex));
            return;
        }
        int sourceCell = NativeWorkspaceGrid.sourceCell(layout, source);
        if (sourceCell < 0 || grabbed >= 0
                && NativeWorkspaceGrid.sourceCell(layout, grabbed) != sourceCell) {
            traceDomain("DROP_ITEM", "STALE_SOURCE", Map.of("sourceSlot", source));
            return;
        }
        var entity = store.getComponent(ref, Player.getComponentType());
        if (entity == null || entity.getWindowManager().getWindow(nativeWindow.getId()) != nativeWindow) {
            traceDomain("DROP_ITEM", "WINDOW_CHANGED", Map.of("windowId", nativeWindow.getId()));
            return;
        }
        traceDomain("DROP_ITEM", "DROP_REQUESTED", Map.of("sourceCell", sourceCell,
                "quantity", data.itemStackQuantity, "destinationOwner", "world"));
        submitOutsideDrop(ref, store, sourceCell, data.itemStackQuantity);
    }

    private void nativeDrop(Ref<EntityStore> ref, Store<EntityStore> store, Data data,
                            UICommandBuilder commands, UIEventBuilder events) {
        int grabbed = nativeGrabCell;
        nativeGrabCell = -1;
        clearInvalidGear(commands);
        int outsideSource = data.sourceSlotId;
        if (outsideSource < 0 && grabbed >= 0)
            outsideSource = NativeWorkspaceGrid.sourceCell(layout, grabbed);
        // Some client builds surface an outside-grid release as a CustomUI
        // Dropped event rather than a DropItemStack packet. Route both through
        // Hywind's custody owner; never treat a negative cell as a move.
        if (data.slotIndex < 0 && nativeWindow != null
                && (data.sourceInventorySectionId == nativeWindow.getId()
                    || (data.sourceInventorySectionId == Integer.MIN_VALUE && grabbed >= 0))
                && outsideSource >= 0 && outsideSource < InventoryGridGeometry.CELLS
                && (grabbed < 0 || outsideSource == NativeWorkspaceGrid.sourceCell(layout, grabbed))) {
            traceDomain("DROP_ITEM", "DROP_REQUESTED", Map.of("sourceCell", outsideSource,
                    "quantity", data.itemStackQuantity, "destinationOwner", "world"));
            submitOutsideDrop(ref, store, outsideSource, data.itemStackQuantity);
            return;
        }
        if (data.sourceInventorySectionId != nativeWindow.getId()
                || data.sourceSlotId < 0 || data.sourceSlotId >= InventoryGridGeometry.CELLS
                || data.slotIndex < 0 || data.slotIndex >= InventoryGridGeometry.CELLS
                || !nativeVirtualEmpty()) {
            traceDomain("MOVE", "INVALID_SOURCE_OR_TARGET", Map.of("sourceSlot", data.sourceSlotId,
                    "targetSlot", data.slotIndex));
            commands.set("#Status.Text", "Item changed. Reopen inventory and try again.");
            return;
        }
        var entity = store.getComponent(ref, Player.getComponentType());
        if (entity == null || entity.getWindowManager().getWindow(nativeWindow.getId()) != nativeWindow) {
            traceDomain("MOVE", "WINDOW_CHANGED", Map.of("windowId", nativeWindow.getId()));
            commands.set("#Status.Text", "Inventory changed. Reopen it and try again.");
            return;
        }
        // SlotClicking preserves the covered-cell offset. If this client did not
        // emit it, only a top-left source is accepted rather than guessing an offset.
        if (grabbed < 0) grabbed = data.sourceSlotId;
        if (NativeWorkspaceGrid.sourceCell(layout, grabbed) != data.sourceSlotId) {
            traceDomain("MOVE", "STALE_SOURCE", Map.of("sourceSlot", data.sourceSlotId));
            commands.set("#Status.Text", "Item changed. Reopen inventory and try again.");
            return;
        }
        var selected = layout.at(grabbed % InventoryGridGeometry.COLUMNS, grabbed / InventoryGridGeometry.COLUMNS).orElse(null);
        var spatial = store.getComponent(ref, SpatialBagComponent.getComponentType());
        if (selected == null) {
            traceDomain("MOVE", "NO_SELECTION", Map.of("sourceSlot", grabbed));
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
                traceDomain("PREVIEW_MOVE", unchanged ? preview == null ? "NO_SELECTION" : preview.outcome().name()
                        : "NATIVE_CHANGED", Map.of("nativeMutation", false));
                commands.set("#Status.Text", unchanged ? "That item does not fit there."
                        : "Native Storage changed. Refresh Inventory.");
                return;
            }
            for (var changed : preview.changed()) bag.move(commands, changed);
            traceDomain("PREVIEW_MOVE", preview.outcome().name(), Map.of("nativeMutation", false,
                    "entryId", selected.id()));
            writeNativeWorkspace(ref, store, commands);
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
            traceDomain("MOVE", "STALE_REVISION", Map.of("entryId", entryId));
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
        traceDomain(partial ? "PLACE_QUANTITY" : merge ? "MERGE_STACK" : "MOVE",
                result.receipt().outcome().name(), Map.of("entryId", entryId,
                        "operationId", result.receipt().operationId(),
                        "expectedRevision", current.revision(), "bagRevisionAfter", result.bag().revision(),
                        "quantity", data.itemStackQuantity));
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
        writeNativeWorkspace(ref, store, commands);
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
        // Release the matching native cursor window when the page closes.
        if (!dismissed && nativeWindow != null && ref.isValid()) {
            try {
                var entity = store.getComponent(ref, Player.getComponentType());
                if (entity != null && entity.getWindowManager().getWindow(nativeWindow.getId()) == nativeWindow)
                    entity.getWindowManager().closeWindow(ref, nativeWindow.getId(), store);
            } catch (RuntimeException closing) {
                // Disconnects can dismiss a page after its window channel closes.
            }
        }
        dismissed = true; grab = null; items.clear(); equippedTooltipStacks.clear(); hoveredTooltipKey = null;
        var diagnostic = UiInteractionTrace.current();
        if (diagnostic != null) diagnostic.event(player.getUuid(), "PAGE_CLOSE", "",
                Map.of("page", "inventory", "reason", openSkillTreeOnDismiss ? "SKILLS_HANDOFF" : "DISMISS"));
        record("DISMISSED", Map.of("session", session));
        onDismiss.run();
        if (openSkillTreeOnDismiss && skillTreeEntry != null)
            store.getExternalData().getWorld().execute(skillTreeEntry);
    }
    private void record(String event, Map<String, ?> details) {
        if (probe != null) probe.record(player, event, details);
    }
    private static String traceComponent(Data data) {
        if (data == null || data.action == null) return "inventory";
        return switch (data.action) {
            case "route", "close" -> "navigation";
            case "gearSelected", "gearDropped", "ring", "armorvisibility" -> "gear";
            default -> "inventory";
        };
    }
    private static String traceElement(Data data) {
        if (data == null || data.action == null) return "inventory.unknown";
        String value = data.value == null ? "" : data.value;
        return switch (data.action) {
            case "cell" -> "inventory.grid.cell." + value.replace(',', '.');
            case "outsideDropped" -> "inventory.grid.outside";
            case "SlotClicking", "Dropped", "DragCancelled" ->
                    data.slotIndex < 0 ? "inventory.grid.outside" :
                            "inventory.grid.cell." + (data.slotIndex % InventoryGridGeometry.COLUMNS) + "."
                                    + (data.slotIndex / InventoryGridGeometry.COLUMNS);
            case "gearDropped", "gearSelected" -> "gear." + value.toLowerCase(Locale.ROOT);
            case "ring" -> "gear.ring." + value;
            case "route" -> "nav." + value.toLowerCase(Locale.ROOT);
            case "advanced", "back", "advancedstat" -> "stats." + data.action;
            default -> "inventory." + data.action;
        };
    }
    private Map<String, ?> traceInput(Store<EntityStore> store, Ref<EntityStore> ref, Data data) {
        var values = new LinkedHashMap<String, Object>();
        values.put("handler", "InventoryProbePage.handleDataEvent");
        values.put("route", route);
        values.put("selectedEntry", selectedSpatialEntry == null ? "" : selectedSpatialEntry.toString());
        values.put("selectedGearSlot", selectedGearSlot == null ? "" : selectedGearSlot);
        values.put("sourceSection", data.sourceInventorySectionId);
        values.put("sourceSlot", data.sourceSlotId);
        values.put("targetSlot", data.slotIndex);
        values.put("quantity", data.itemStackQuantity);
        values.put("expectedRevision", data.revision);
        if ("search".equals(data.action)) values.put("searchLength", data.query == null ? 0 : data.query.length());
        var spatial = store.getComponent(ref, SpatialBagComponent.getComponentType());
        boolean owns = spatial != null && spatial.mode(player.getUuid()) != SpatialBagComponent.OwnershipMode.NATIVE;
        values.put("ownershipMode", owns ? "SPATIAL" : "NATIVE");
        if (owns) values.put("bagRevisionBefore", spatial.state(player.getUuid()).revision());
        int cell = switch (data.action) {
            case "SlotClicking", "Dropped", "bagHoverEnter", "bagHoverExit" -> data.slotIndex;
            case "gearDropped" -> data.sourceSlotId;
            default -> -1;
        };
        if ("cell".equals(data.action)) {
            String[] xy = data.value == null ? new String[0] : data.value.split(",", 3);
            if (xy.length == 2) try {
                cell = Integer.parseInt(xy[1]) * InventoryGridGeometry.COLUMNS + Integer.parseInt(xy[0]);
            } catch (NumberFormatException ignored) { cell = -1; }
        }
        if (cell >= 0 && cell < InventoryGridGeometry.CELLS) {
            int x = cell % InventoryGridGeometry.COLUMNS, y = cell / InventoryGridGeometry.COLUMNS;
            values.put("clickedCellX", x); values.put("clickedCellY", y);
            var entry = layout.at(x, y).orElse(null);
            if (entry != null) {
                values.put("entryId", entry.id());
                values.put("footprintWidth", entry.size().width());
                values.put("footprintHeight", entry.size().height());
                values.put("anchorX", entry.position().x()); values.put("anchorY", entry.position().y());
                values.put("offsetX", x - entry.position().x()); values.put("offsetY", y - entry.position().y());
                if (data.action.startsWith("bagHover")) {
                    var bounds = ManagedGearTooltip.bagRect(entry);
                    values.put("boundsSpace", "workspace-logical");
                    values.put("boundsX", bounds.x()); values.put("boundsY", bounds.y());
                    values.put("boundsWidth", bounds.width()); values.put("boundsHeight", bounds.height());
                }
                var view = items.get(entry.id());
                if (view != null) { values.put("itemId", view.itemId); values.put("stackQuantity", view.quantity); }
            }
        }
        return values;
    }
    private Map<String, ?> traceAfter(Store<EntityStore> store, Ref<EntityStore> ref) {
        var values = new LinkedHashMap<String, Object>();
        values.put("selectedEntryAfter", selectedSpatialEntry == null ? "" : selectedSpatialEntry.toString());
        values.put("selectedGearAfter", selectedGearSlot == null ? "" : selectedGearSlot);
        values.put("routeAfter", route);
        if (ref.isValid()) {
            var spatial = store.getComponent(ref, SpatialBagComponent.getComponentType());
            if (spatial != null && spatial.mode(player.getUuid()) != SpatialBagComponent.OwnershipMode.NATIVE)
                values.put("bagRevisionAfter", spatial.state(player.getUuid()).revision());
        }
        return values;
    }
    private static void tracePatch(UiInteractionTrace.Action action, UICommandBuilder commands) {
        var all = commands.getCommands();
        if (all.length == 0) return;
        String affected = Arrays.stream(all).map(command -> command.selector)
                .filter(Objects::nonNull).distinct().limit(16).collect(java.util.stream.Collectors.joining(","));
        action.stage("UI_PATCH", Map.of("commandCount", all.length, "affectedElements", affected));
    }
    private void traceDomain(String operation, String result, Map<String, ?> details) {
        var action = traceAction;
        if (action == null || !action.active()) return;
        var domain = new LinkedHashMap<String, Object>();
        domain.put("operation", operation);
        domain.put("result", result);
        if (details != null) domain.putAll(details);
        action.stage("DOMAIN_OPERATION", domain);
        action.stage("VALIDATION", Map.of("operation", operation, "result", result));
        traceOutcome = result;
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
