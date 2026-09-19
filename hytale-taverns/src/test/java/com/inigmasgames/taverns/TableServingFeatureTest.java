package com.inigmasgames.taverns;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.joml.Vector3d;
import org.joml.Vector3i;

public final class TableServingFeatureTest {
    private TableServingFeatureTest() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("TableServing: eligibility");
        actualConsumableFlagControlsEligibility();
        System.out.println("TableServing: marker");
        servingMarkerPreservesItsTableSlot();
        System.out.println("TableServing: scoped input");
        inputPathIsTargetScopedAndCancelsVanillaChain();
        System.out.println("TableServing: close-range dual input");
        tableActionsAreCloseRangeUseAndSecondaryInteractions();
        System.out.println("TableServing: footprint range");
        interactionRangeUsesTableFootprintDistance();
        System.out.println("TableServing: replicated props");
        servingUsesReplicatedPropsAndAPlate();
        System.out.println("TableServing: native consume");
        entityUseRunsTheNativeConsumableRoot();
        System.out.println("TableServing: pickup/consume");
        pickupAndConsumptionLeaveThePlateTracked();
        System.out.println("TableServing: slot reuse");
        emptyPlateSlotCanBeServedAgain();
        System.out.println("TableServing: sounds");
        servingActionsUseVanillaPickupSounds();
        System.out.println("TableServing: table break");
        tableBreakReleasesNativeDrops();
        System.out.println("TableServingFeatureTest passed");
    }

    private static void actualConsumableFlagControlsEligibility() {
        assert TableServingManager.isConsumable(new TestItem(true));
        assert !TableServingManager.isConsumable(new TestItem(false));
        assert !TableServingManager.isConsumable((Item) null);
    }

    private static void servingMarkerPreservesItsTableSlot() {
        TableServingComponent original = new TableServingComponent(14, 27, -9);
        TableServingComponent copied = (TableServingComponent) original.clone();
        assert copied.tableX() == 14;
        assert copied.tableY() == 27;
        assert copied.tableZ() == -9;

        UUID world = UUID.randomUUID();
        assert new TableServingManager.TableKey(world, 14, 27, -9)
                .equals(new TableServingManager.TableKey(world, 14, 27, -9));
    }

    private static void inputPathIsTargetScopedAndCancelsVanillaChain() throws IOException {
        String plugin = source("TavernsPlugin.java");
        String manager = source("TableServingManager.java");
        assert manager.contains("InteractionType.Secondary");
        assert plugin.contains("update.interactionType != InteractionType.Use");
        assert plugin.contains("manager.pickupFoodFromTable(");
        assert plugin.contains("new CancelInteractionChain");
        assert plugin.contains("InteractionSystems.TickInteractionManagerSystem.class");
        assert manager.contains("findPrimaryCoreContaining");
        assert manager.contains("ComfortCategory.TABLES");
        assert manager.contains("item.isConsumable()");
        assert manager.contains("removeItemStackFromSlot(heldSlot, 1)");
        assert manager.contains("new TableKey(worldId, position.x(), position.y(), position.z())");
        assert manager.contains(
                "pickupFoodFromState(playerRef, aimedKey, servings.get(aimedKey)");
    }

    private static void tableActionsAreCloseRangeUseAndSecondaryInteractions()
            throws IOException {
        String plugin = source("TavernsPlugin.java");
        String manager = source("TableServingManager.java");
        assert plugin.contains("update.interactionType != InteractionType.Use")
                && plugin.contains("update.interactionType != InteractionType.Secondary");
        assert plugin.contains("cancelTableAction(queue, packetHandler, update)");
        assert plugin.contains("candidate.interactionType == InteractionType.Use")
                && plugin.contains("== InteractionType.Secondary");
        assert plugin.contains("TransformComponent.getComponentType()");
        assert plugin.contains("TableServingManager.isWithinInteractionRange(");
        assert manager.contains("MAX_TABLE_HORIZONTAL_RANGE = 1.10");
        assert manager.contains("MAX_TABLE_VERTICAL_RANGE = 1.75");
        assert manager.contains("resolveInteractionTablePosition");
        assert manager.contains("offsetY >= -1");
        assert manager.contains("playerTransform.getPosition()");
    }

    private static void interactionRangeUsesTableFootprintDistance() {
        Vector3i table = new Vector3i(10, 20, 30);
        assert TableServingManager.isWithinInteractionRange(
                new Vector3d(12.05, 20.0, 30.5), table);
        assert !TableServingManager.isWithinInteractionRange(
                new Vector3d(12.11, 20.0, 30.5), table);
        assert !TableServingManager.isWithinInteractionRange(
                new Vector3d(10.5, 21.76, 30.5), table);
    }

    private static void servingUsesReplicatedPropsAndAPlate() throws IOException {
        String manager = source("TableServingManager.java");
        assert manager.contains("PropComponent.get()");
        assert manager.contains("new ModelComponent(model)");
        assert manager.contains("new PersistentModel(model.toReference())");
        assert manager.contains("PLATE_ITEM_ID = \"Deco_Plate\"");
        assert manager.contains("CLEAN_PLATE_ITEM_ID = \"Tavern_Clean_Plate\"");
        assert manager.contains("DIRTY_PLATE_ITEM_ID = \"Dirty_Plate\"");
        assert manager.contains("TableServingComponent.Part.PLATE");
        assert manager.contains("TableServingComponent.Part.FOOD");
        assert manager.contains("TABLE_PROFILES.getOrDefault");
        assert manager.contains("DEFAULT_PLATE_LIFT = -0.010");
        assert manager.contains("DEFAULT_FOOD_LIFT = 0.018");
        assert manager.contains("repositionServing(key, ref, current.part(), commandBuffer)");
    }

    private static void entityUseRunsTheNativeConsumableRoot() throws IOException {
        String manager = source("TableServingManager.java");
        String interaction = source("TableServingUseInteraction.java");
        assert manager.contains("Interactable.INSTANCE");
        assert manager.contains("InteractionType.Use, SERVING_ROOT_ID");
        assert manager.contains("InteractionType.Secondary, SERVING_ROOT_ID");
        assert manager.contains("installEmptyHandSecondary()");
        assert manager.contains("emptyHand.getInteractions().put(");
        assert manager.contains("EMPTY_HAND_SECONDARY_ROOT_ID");
        assert manager.contains("restoreEmptyHandSecondary()");
        assert manager.contains("InteractionContext.forProxyEntity");
        assert manager.contains("foodStack.getItem().getInteractions().get(InteractionType.Secondary)");
        assert manager.contains("context.fork(");
        assert manager.contains("nativeChain.setOnCompletion");
        assert interaction.contains("installed.useServing(type, context)");
    }

    private static void pickupAndConsumptionLeaveThePlateTracked() throws IOException {
        String manager = source("TableServingManager.java");
        assert manager.contains("type == InteractionType.Use");
        assert manager.contains("Pickup is exclusively the entity's F/Use interaction");
        assert manager.contains("spawnMissingPlate(key, state.food");
        assert manager.contains("removeFood(foodRef, serving, commandBuffer)");
        assert manager.contains("state.food = null");
        assert !manager.contains("state.plate = null;\n                state.food = null");
        assert manager.contains("pickupPlate");
    }

    private static void emptyPlateSlotCanBeServedAgain() throws IOException {
        String manager = source("TableServingManager.java");
        assert manager.contains("synchronized PlacementResult place(");
        assert manager.contains("Ref<EntityStore> plateRef = reserved.plate");
        assert manager.contains("if (plateRef == null || !plateRef.isValid())");
        assert manager.contains("reserved.food = foodRef");
    }

    private static void servingActionsUseVanillaPickupSounds() throws IOException {
        String manager = source("TableServingManager.java");
        assert manager.contains("PLACE_SOUND_EVENT = \"SFX_Player_Drop_Item\"");
        assert manager.contains("PICKUP_SOUND_EVENT = \"SFX_Player_Pickup_Item\"");
        assert manager.contains("SoundUtil.playSoundEvent3d(");
    }

    private static void tableBreakReleasesNativeDrops() throws IOException {
        String manager = source("TableServingManager.java");
        String plugin = source("TavernsPlugin.java");
        assert manager.contains("int releaseAt(");
        assert manager.contains("ItemComponent.generateItemDrops(");
        assert manager.contains("commandBuffer.addEntity(drop, AddReason.SPAWN)");
        assert manager.contains("servings.remove(key)");
        assert manager.contains("Breaking a serviced Table intentionally destroys every food item");
        assert manager.contains("List.of(new ItemStack(returnedPlateId, 1))");
        assert manager.contains("DIRTY_PLATE_ITEM_ID.equals(statePlateId)");
        assert manager.contains("PreparedMeal.inspect(state.foodStack, preparedFoods)");
        assert manager.contains("FillerBlockUtil.unpackX(filler)");
        assert plugin.contains("new TableServingBrokenSystem(");
        assert plugin.contains("manager.releaseAt(");
    }

    private static String source(String name) throws IOException {
        return Files.readString(Path.of(
                "src", "main", "java", "com", "inigmasgames", "taverns", name));
    }

    private static final class TestItem extends Item {
        private TestItem(boolean consumable) {
            super("Taverns_Test_Item");
            this.consumable = consumable;
        }
    }
}
