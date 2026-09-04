package com.inigmasgames.persistentnpcs;

import com.hypixel.hytale.protocol.ItemArmorSlot;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemArmor;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.InventoryUtils;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.inigmasgames.persistentnpcs.profile.NpcEquipmentCompatibilityResolver;
import com.inigmasgames.persistentnpcs.profile.NpcEquipmentMovePolicy;
import com.inigmasgames.persistentnpcs.ui.CustomInventoryBridgeUi;
import com.inigmasgames.persistentnpcs.ui.CustomInventoryTransactionBridge.Endpoint;
import com.inigmasgames.persistentnpcs.ui.CustomInventoryTransactionBridge.SectionRole;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.bson.BsonArray;
import org.bson.BsonDocument;

/** Deterministic A3 gate: authoritative gear, loadout, visibility, and live stats. */
public final class R132NpcAuthoringStudioA3GearStatsTest {
    private R132NpcAuthoringStudioA3GearStatsTest() { }

    public static void main(String[] args) throws Exception {
        System.out.println("R132 stage=installed-contracts");
        installedUpdate6ContractsExist();
        System.out.println("R132 stage=compatibility");
        compatibilityIsTypedAndFailClosed();
        System.out.println("R132 stage=movement-graph");
        movementGraphIsExplicitAndAtomic();
        System.out.println("R132 stage=authority-persistence");
        authoritativeContainersAndPersistenceArePreserved();
        System.out.println("R132 stage=commit-stats");
        commitApplicationAndStatsAreCoalesced();
        System.out.println("R132 stage=ui");
        uiUsesExactNativeEquipmentEndpoints();
        System.out.println("R132 stage=incompatible-wire");
        incompatiblePresentationIsEncoded();
        System.out.println("R132 NPC Authoring Studio A3 gear/stats gate passed.");
    }

    private static void installedUpdate6ContractsExist() throws Exception {
        ItemArmor.class.getMethod("getArmorSlot");
        ItemArmor.class.getMethod("getBaseDamageResistance");
        Item.class.getMethod("getWeapon");
        Item.class.getMethod("getArmor");
        Item.class.getMethod("getUtility");
        Item.class.getMethod("getData");
        EntityStatMap.class.getMethod("get", String.class);
        assert ItemArmorSlot.VALUES.length == 4;
        assert Arrays.stream(InventoryUtils.class.getMethods())
                .anyMatch(method -> method.getName().equals("createEquipmentUpdate"));
    }

    private static void compatibilityIsTypedAndFailClosed() throws Exception {
        String resolver = source("src/main/java/com/inigmasgames/persistentnpcs/profile/"
                + "NpcEquipmentCompatibilityResolver.java");
        assert resolver.contains("ItemArmor.armorSlot=") && resolver.contains("getArmorSlot()")
                : "Armor compatibility must use authoritative ItemArmor.armorSlot";
        assert resolver.contains("getRawTags()") && resolver.contains("Tags.Family=Arrow")
                : "Loadout categories must use installed asset metadata";
        assert resolver.contains("COMPATIBLE, INCOMPATIBLE, UNKNOWN, REQUIRES_REVIEW")
                : "Every compatibility result must remain typed";
        assert resolver.contains("OFFHAND_CATEGORY_UNSUPPORTED")
                : "Unknown Update 6 offhand categories must fail closed";
        assert !resolver.toLowerCase().contains("startswith(\"weapon_arrow_\")")
                : "Filename heuristics are not authoritative compatibility evidence";

        var checker = new NpcEquipmentCompatibilityResolver();
        assert checker.validateArmor(ItemStack.EMPTY, (short) 0).compatible();
        assert checker.validatePrimaryWeapon(ItemStack.EMPTY).compatible();
    }

    private static void movementGraphIsExplicitAndAtomic() throws Exception {
        String policySource = source("src/main/java/com/inigmasgames/persistentnpcs/profile/"
                + "NpcEquipmentMovePolicy.java");
        String bridge = source("src/main/java/com/inigmasgames/persistentnpcs/ui/"
                + "CustomInventoryTransactionBridge.java");
        assert policySource.contains("DIRECT_EQUIPMENT_TO_EQUIPMENT_NOT_ENABLED");
        assert policySource.contains("sourceStorage && targetGear")
                && policySource.contains("sourceGear != null && targetStorage");
        assert bridge.contains("movePolicy.invalidReason")
                && bridge.indexOf("movePolicy.invalidReason") < bridge.indexOf("source.swapItems(")
                : "Semantic validation must precede the native mutation";
        assert bridge.contains("moveItemStackFromSlotToSlot")
                : "The native SDK must remain the only mutation authority";
        assert bridge.contains("PARTIAL_STACK_CANNOT_SWAP_OCCUPIED_DESTINATION");
        assert !bridge.contains("removeAllItemStacks()") && !bridge.contains("dropAllItemStacks()");

        NpcEquipmentMovePolicy policy = new NpcEquipmentMovePolicy();
        SimpleItemContainer left = new SimpleItemContainer((short) 4);
        SimpleItemContainer right = new SimpleItemContainer((short) 4);
        Endpoint storage = new Endpoint(SectionRole.NPC_STORAGE, 1, 0, left);
        Endpoint armor = new Endpoint(SectionRole.NPC_ARMOR, 2, 0, right);
        Endpoint loadout = new Endpoint(SectionRole.NPC_HOTBAR, 3, 0, right);
        assert policy.invalidReason(storage, ItemStack.EMPTY, armor, ItemStack.EMPTY, false) == null;
        assert policy.invalidReason(armor, ItemStack.EMPTY, loadout, ItemStack.EMPTY, false)
                .equals("DIRECT_EQUIPMENT_TO_EQUIPMENT_NOT_ENABLED");
    }

    private static void authoritativeContainersAndPersistenceArePreserved() throws Exception {
        String repository = source("src/main/java/com/inigmasgames/persistentnpcs/profile/"
                + "NpcInventoryRepository.java");
        String authority = source("src/main/java/com/inigmasgames/persistentnpcs/ui/"
                + "NativeNpcInventoryController.java");
        assert repository.contains("openWithLiveInventory")
                && repository.contains("liveArmor") && repository.contains("liveHotbar")
                && repository.contains("liveUtility") && repository.contains("liveStorage");
        assert repository.contains("PRIMARY_SLOT -> hotbar.getItemStack((short) 0)");
        assert repository.contains("OFFHAND_SLOT -> utility.getItemStack((short) 0)");
        assert repository.contains("AMMUNITION_SLOT -> hotbar.getItemStack((short) 1)");
        assert repository.contains("addRuntimeSlot(loadout, hotbar, (short) 1, Session.AMMUNITION_SLOT)")
                : "A3 uses physical preferred-ammo stack model A";
        assert repository.contains("infiniteAmmunition && ammunitionPolicyRelevant()")
                || repository.contains("&& ammunitionPolicyRelevant()");
        assert repository.contains("INFINITE_AMMUNITION_CONFIG")
                || source("src/main/java/com/inigmasgames/persistentnpcs/profile/"
                        + "NpcEquipmentRules.java").contains("INFINITE_AMMUNITION_CONFIG");
        assert authority.contains("NPC_ECS_ARMOR_IDENTITY_MISMATCH")
                && authority.contains("NPC_ECS_HOTBAR_IDENTITY_MISMATCH")
                && authority.contains("NPC_ECS_UTILITY_IDENTITY_MISMATCH");
        assert authority.contains("setOutdatedEquipment(true)")
                && authority.contains("InventoryUtils.createEquipmentUpdate");
        assert authority.contains("hotbarComponent.setActiveSlot((byte) 0, npcRef, store)")
                && authority.contains("utilityComponent.setActiveSlot((byte) 0, npcRef, store)")
                : "World-visible primary/offhand authority must use the authored active cells";
    }

    private static void commitApplicationAndStatsAreCoalesced() throws Exception {
        String page = source("src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java");
        String stats = source("src/main/java/com/inigmasgames/persistentnpcs/profile/"
                + "NpcStatsSnapshotService.java");
        int nativeCommit = source("src/main/java/com/inigmasgames/persistentnpcs/ui/"
                + "CustomInventoryTransactionBridge.java").indexOf("source.swapItems(");
        assert nativeCommit >= 0;
        assert page.contains("inventory.flush()")
                && page.contains("applyEquipmentAndStats(store, \"GEAR_TRANSACTION\")")
                && page.contains("setNpcProfileUi(commands)");
        assert page.contains("itemStateRolledBack=false")
                : "Preview failure must never roll back committed item state";
        assert page.contains("equipmentChanged") && page.contains("uiRefresh=COALESCED");
        assert stats.contains("npcStableId") && stats.contains("npcEntityUuid")
                && stats.contains("capturedAt") && stats.contains("equipmentRevision")
                && stats.contains("sessionId") && stats.contains("pageGeneration");
        assert stats.contains("EntityStatMap") && stats.contains("getResistanceModifiers")
                : "Stats must come from live ECS state and native typed armor aggregation";
        assert page.contains("\"—\"") && !page.contains(" + \" base\"")
                : "Unavailable stats must not be shown as fake zero values";
        assert page.contains("scheduleAtFixedRate") && page.contains("STALE_IDENTITY_OR_GENERATION");
    }

    private static void uiUsesExactNativeEquipmentEndpoints() throws Exception {
        String ui = source("src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcProfile.ui");
        String page = source("src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java");
        for (String grid : new String[] {"ArmorGrid", "PrimaryWeaponGrid",
                "OffhandGrid", "AmmunitionGrid"}) {
            assert ui.contains("ItemGrid #" + grid);
            assert page.contains("#" + grid + ".InventorySectionId");
            assert page.contains("#" + grid + ".Slots");
        }
        assert page.contains("NpcAuthoringPermissions.GEAR")
                : "Gear mutations require their own permission envelope";
        assert page.contains("#InfiniteAmmoCheckBox.Disabled")
                && page.contains("infiniteAmmunitionFeatureEnabled");
        assert page.contains("prefix + \".Visible\", equipped")
                : "Visibility controls must be occupied-slot only";
    }

    private static void incompatiblePresentationIsEncoded() {
        SimpleItemContainer container = new SimpleItemContainer((short) 2);
        UICommandBuilder commands = new UICommandBuilder();
        CustomInventoryBridgeUi.setNativeSlots(commands, "#Grid.Slots",
                container, 0, 2, slot -> slot == 1);
        BsonArray slots = BsonDocument.parse(commands.getCommands()[0].data).getArray("0");
        assert !slots.get(0).asDocument().getBoolean("IsItemIncompatible").getValue();
        assert slots.get(1).asDocument().getBoolean("IsItemIncompatible").getValue();
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
