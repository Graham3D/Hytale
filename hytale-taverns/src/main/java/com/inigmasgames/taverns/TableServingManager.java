package com.inigmasgames.taverns;

import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.builtin.mounts.BlockMountComponent;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import com.hypixel.hytale.protocol.BlockMountType;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.ModelTrail;
import com.hypixel.hytale.protocol.Phobia;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.CustomModelTexture;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.model.config.DetailBox;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelParticle;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemPhysicsComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventItemMerging;
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.physics.component.PhysicsValues;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Owns one durable plate visual + optional Consumable serving slot per Tavern Table. */
final class TableServingManager {
    static final String PLATE_ITEM_ID = "Deco_Plate";
    static final String CLEAN_PLATE_ITEM_ID = "Tavern_Clean_Plate";
    static final String DIRTY_PLATE_ITEM_ID = "Dirty_Plate";
    static final String SERVING_ROOT_ID = "Taverns_Table_Serving_Secondary";
    static final String EMPTY_HAND_SECONDARY_ROOT_ID =
            "Taverns_Table_Serving_Empty_Secondary";
    private static final String PLACE_SOUND_EVENT = "SFX_Player_Drop_Item";
    private static final String PICKUP_SOUND_EVENT = "SFX_Player_Pickup_Item";
    static final double MAX_TABLE_HORIZONTAL_RANGE = 1.10;
    static final double MAX_TABLE_VERTICAL_RANGE = 1.75;

    private static final double DEFAULT_PLATE_LIFT = -0.010;
    private static final double DEFAULT_FOOD_LIFT = 0.018;
    private static final Box DEFAULT_MODEL_BOX =
            new Box(-0.34, 0.0, -0.34, 0.34, 0.42, 0.34);

    /* Profiles are local to the Table before block rotation. */
    private static final PlacementProfile DEFAULT_PROFILE =
            new PlacementProfile(0.0, 0.0, 0.0, DEFAULT_PLATE_LIFT, DEFAULT_FOOD_LIFT);
    private static final Map<String, PlacementProfile> TABLE_PROFILES = Map.of();

    private final TavernRepository repository;
    private final ComfortRegistry comfortRegistry;
    private final PreparedFoodRegistry preparedFoods;
    private final ComponentType<EntityStore, TableServingComponent> servingType;
    private final Map<TableKey, ServingState> servings = new ConcurrentHashMap<>();
    private final Set<Ref<EntityStore>> activeUses = ConcurrentHashMap.newKeySet();
    private final Set<Ref<EntityStore>> releasedUses = ConcurrentHashMap.newKeySet();
    private final Set<TableKey> patronLockedSlots = ConcurrentHashMap.newKeySet();
    private Item patchedEmptyHandItem;
    private String previousEmptyHandSecondary;

    TableServingManager(
            TavernRepository repository,
            ComfortRegistry comfortRegistry,
            PreparedFoodRegistry preparedFoods,
            ComponentType<EntityStore, TableServingComponent> servingType) {
        this.repository = repository;
        this.comfortRegistry = comfortRegistry;
        this.preparedFoods = preparedFoods;
        this.servingType = servingType;
    }

    /**
     * Adds a reversible empty-hand Secondary probe. Vanilla Empty only exposes Use,
     * so a right-click otherwise never runs UseEntity and cannot reach a serving.
     */
    synchronized boolean installEmptyHandSecondary() {
        Item emptyHand = Item.getAssetMap().getAsset("Empty");
        if (emptyHand == null || emptyHand == Item.UNKNOWN
                || emptyHand.getInteractions() == null) {
            return false;
        }
        if (emptyHand == patchedEmptyHandItem
                && EMPTY_HAND_SECONDARY_ROOT_ID.equals(
                        emptyHand.getInteractions().get(InteractionType.Secondary))) {
            return true;
        }
        patchedEmptyHandItem = emptyHand;
        previousEmptyHandSecondary =
                emptyHand.getInteractions().put(
                        InteractionType.Secondary, EMPTY_HAND_SECONDARY_ROOT_ID);
        emptyHand.invalidatePacketCache();
        return true;
    }

    synchronized void restoreEmptyHandSecondary() {
        if (patchedEmptyHandItem == null || patchedEmptyHandItem.getInteractions() == null) {
            return;
        }
        if (previousEmptyHandSecondary == null) {
            patchedEmptyHandItem.getInteractions().remove(InteractionType.Secondary);
        } else {
            patchedEmptyHandItem.getInteractions().put(
                    InteractionType.Secondary, previousEmptyHandSecondary);
        }
        patchedEmptyHandItem.invalidatePacketCache();
        patchedEmptyHandItem = null;
        previousEmptyHandSecondary = null;
    }

    Optional<TableTarget> eligibleTarget(
            World world,
            UUID worldId,
            Vector3i position,
            ItemStack heldStack) {
        if (!isConsumable(heldStack)) {
            return Optional.empty();
        }
        BlockType blockType = BlockType.getAssetMap().getAsset(
                world.getBlock(position.x(), position.y(), position.z()));
        if (blockType == null || blockType == BlockType.EMPTY || !isRegisteredTable(blockType)) {
            return Optional.empty();
        }
        if (repository.findPrimaryCoreContaining(
                worldId, position.x(), position.y(), position.z()).isEmpty()) {
            return Optional.empty();
        }
        // A multi-block Table exposes four independently targetable filler
        // positions. Keep those positions as service-slot identities; resolving
        // them to the physical anchor here would collapse the Table to one slot.
        return Optional.of(new TableTarget(
                new TableKey(worldId, position.x(), position.y(), position.z()),
                blockType));
    }

    /** Accepts the Table itself or the cell directly above its tabletop. */
    Optional<Vector3i> resolveInteractionTablePosition(World world, Vector3i position) {
        for (int offsetY = 0; offsetY >= -1; offsetY--) {
            Vector3i candidate = new Vector3i(
                    position.x(), position.y() + offsetY, position.z());
            BlockType blockType = BlockType.getAssetMap().getAsset(
                    world.getBlock(candidate.x(), candidate.y(), candidate.z()));
            if (blockType != null && blockType != BlockType.EMPTY
                    && isRegisteredTable(blockType)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    static boolean isWithinInteractionRange(Vector3d playerPosition, Vector3i tablePosition) {
        double deltaX = distanceOutsideInterval(
                playerPosition.x, tablePosition.x(), tablePosition.x() + 1.0);
        double deltaZ = distanceOutsideInterval(
                playerPosition.z, tablePosition.z(), tablePosition.z() + 1.0);
        double horizontalDistanceSquared = deltaX * deltaX + deltaZ * deltaZ;
        return horizontalDistanceSquared
                <= MAX_TABLE_HORIZONTAL_RANGE * MAX_TABLE_HORIZONTAL_RANGE
                && Math.abs(playerPosition.y - tablePosition.y())
                <= MAX_TABLE_VERTICAL_RANGE;
    }

    private static double distanceOutsideInterval(double value, double minimum, double maximum) {
        if (value < minimum) {
            return minimum - value;
        }
        return value > maximum ? value - maximum : 0.0;
    }

    synchronized PlacementResult place(
            TableTarget target,
            ItemContainer heldContainer,
            short heldSlot,
            ItemStack heldStack,
            World world,
            CommandBuffer<EntityStore> commandBuffer) {
        TableKey key = target.key();
        ServingState reserved = servings.get(key);
        boolean createdState = reserved == null;
        if (createdState) {
            reserved = new ServingState();
            servings.put(key, reserved);
        } else {
            if (reserved.food != null && reserved.food.isValid()) {
                return PlacementResult.OCCUPIED;
            }
            reserved.food = null;
            reserved.foodStack = null;
            if (reserved.plate != null && !reserved.plate.isValid()) {
                reserved.plate = null;
            }
        }
        if (reserved.food != null) {
            return PlacementResult.OCCUPIED;
        }

        ItemStack servingStack = heldStack.withQuantity(1);
        ItemStackSlotTransaction removal = heldContainer.removeItemStackFromSlot(heldSlot, 1);
        if (!removal.succeeded()) {
            if (createdState) {
                servings.remove(key, reserved);
            }
            return PlacementResult.INVENTORY_REJECTED;
        }

        Ref<EntityStore> plateRef = reserved.plate;
        boolean spawnedPlate = false;
        Ref<EntityStore> foodRef = null;
        try {
            ServingPose pose = servingPose(world, target);
            if (plateRef == null || !plateRef.isValid()) {
                ItemStack plateStack = plateFor(servingStack);
                plateRef = commandBuffer.addEntity(
                        servingHolder(key, TableServingComponent.Part.PLATE, plateStack,
                                pose.platePosition(), pose.rotation()),
                        AddReason.SPAWN);
                spawnedPlate = true;
            }
            foodRef = commandBuffer.addEntity(
                    servingHolder(key, TableServingComponent.Part.FOOD, servingStack,
                            pose.foodPosition(), pose.rotation()),
                    AddReason.SPAWN);
            reserved.plate = plateRef;
            reserved.food = foodRef;
            reserved.foodStack = servingStack;
            playServingSound(PLACE_SOUND_EVENT, key, commandBuffer);
            return PlacementResult.PLACED;
        } catch (RuntimeException exception) {
            if (foodRef != null) {
                commandBuffer.tryRemoveEntity(foodRef, RemoveReason.REMOVE);
            }
            if (spawnedPlate && plateRef != null) {
                commandBuffer.tryRemoveEntity(plateRef, RemoveReason.REMOVE);
                reserved.plate = null;
            }
            reserved.food = null;
            reserved.foodStack = null;
            if (createdState && reserved.plate == null) {
                servings.remove(key, reserved);
            }
            heldContainer.addItemStackToSlot(heldSlot, servingStack);
            throw exception;
        }
    }

    /** Called by the target entity's native Secondary interaction root. */
    boolean useServing(InteractionType type, InteractionContext context) {
        Ref<EntityStore> targetRef = context.getTargetEntity();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        Ref<EntityStore> playerRef = context.getEntity();
        if (targetRef == null || !targetRef.isValid()
                || commandBuffer == null || playerRef == null || !playerRef.isValid()) {
            return false;
        }
        TableServingComponent serving = commandBuffer.getComponent(targetRef, servingType);
        TransformComponent playerTransform = commandBuffer.getComponent(
                playerRef, TransformComponent.getComponentType());
        TableKey servingKey = serving == null
                ? null
                : key(worldId(commandBuffer.getStore()), serving);
        if (serving == null || serving.itemStack() == null
                || playerTransform == null
                || !isWithinInteractionRange(
                        playerTransform.getPosition(),
                        new Vector3i(servingKey.x(), servingKey.y(), servingKey.z()))
                || !activeUses.add(targetRef)) {
            return false;
        }

        if (serving.part() == TableServingComponent.Part.FOOD
                && patronLockedSlots.contains(servingKey)) {
            activeUses.remove(targetRef);
            return false;
        }

        if (serving.part() == TableServingComponent.Part.PLATE) {
            if (type != InteractionType.Use) {
                activeUses.remove(targetRef);
                return false;
            }
            return pickupPlate(playerRef, targetRef, serving, commandBuffer);
        }
        if (type == InteractionType.Use) {
            return pickupFood(playerRef, targetRef, serving, commandBuffer);
        }
        if (type != InteractionType.Secondary) {
            activeUses.remove(targetRef);
            return false;
        }
        if (!isSeatedAtValidChair(playerRef, servingKey, commandBuffer)) {
            activeUses.remove(targetRef);
            return false;
        }
        return startNativeConsumption(playerRef, targetRef, serving, context);
    }

    /**
     * Handles F/Use when the Table surface wins the client raycast over its small
     * food prop. The aimed filler slot is preferred, then another occupied slot
     * on the same physical Table is used as a forgiving fallback.
     */
    boolean pickupFoodFromTable(
            Ref<EntityStore> playerRef,
            UUID worldId,
            Vector3i tablePosition,
            World world,
            CommandBuffer<EntityStore> commandBuffer) {
        BlockType blockType = BlockType.getAssetMap().getAsset(
                world.getBlock(tablePosition.x(), tablePosition.y(), tablePosition.z()));
        if (blockType == null || blockType == BlockType.EMPTY || !isRegisteredTable(blockType)) {
            return false;
        }

        TableKey aimedKey = new TableKey(
                worldId, tablePosition.x(), tablePosition.y(), tablePosition.z());
        if (pickupFoodFromState(playerRef, aimedKey, servings.get(aimedKey), commandBuffer)) {
            return true;
        }

        Vector3i anchor = tableAnchor(world, tablePosition);
        for (Map.Entry<TableKey, ServingState> entry : servings.entrySet()) {
            TableKey key = entry.getKey();
            if (key.equals(aimedKey) || !key.worldId().equals(worldId)) {
                continue;
            }
            Vector3i candidateAnchor = tableAnchor(
                    world, new Vector3i(key.x(), key.y(), key.z()));
            if (candidateAnchor.equals(anchor)
                    && pickupFoodFromState(playerRef, key, entry.getValue(), commandBuffer)) {
                return true;
            }
        }
        return false;
    }

    private boolean pickupFoodFromState(
            Ref<EntityStore> playerRef,
            TableKey key,
            ServingState state,
            CommandBuffer<EntityStore> commandBuffer) {
        if (patronLockedSlots.contains(key)
                || state == null || state.food == null || !state.food.isValid()
                || !activeUses.add(state.food)) {
            return false;
        }
        TableServingComponent serving = commandBuffer.getComponent(state.food, servingType);
        if (serving == null || serving.part() != TableServingComponent.Part.FOOD
                || serving.itemStack() == null) {
            activeUses.remove(state.food);
            return false;
        }
        return pickupFood(playerRef, state.food, serving, commandBuffer);
    }

    synchronized Optional<ServingSnapshot> servingAt(TableKey key) {
        ServingState state = servings.get(key);
        if (state == null || state.food == null || !state.food.isValid()
                || state.foodStack == null || state.foodStack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ServingSnapshot(
                state.food,
                state.foodStack,
                state.plate != null && state.plate.isValid()));
    }

    synchronized boolean consumeForPatron(
            TableKey key,
            String expectedItemId,
            CommandBuffer<EntityStore> commandBuffer) {
        ServingState state = servings.get(key);
        if (state == null || state.food == null || !state.food.isValid()
                || state.foodStack == null
                || !PreparedMeal.matchesBase(
                        state.foodStack, expectedItemId, preparedFoods)) {
            return false;
        }
        TableServingComponent serving = commandBuffer.getComponent(state.food, servingType);
        if (serving == null || serving.part() != TableServingComponent.Part.FOOD) {
            return false;
        }
        if (state.plate == null || !state.plate.isValid()) {
            spawnMissingPlate(key, state.food, commandBuffer);
        }
        if (state.plate == null || !state.plate.isValid()) {
            return false;
        }
        replacePlate(key, DIRTY_PLATE_ITEM_ID, commandBuffer);
        removeFood(state.food, serving, commandBuffer);
        return true;
    }

    synchronized boolean hideFoodVisual(
            TableKey key,
            CommandBuffer<EntityStore> commandBuffer) {
        ServingState state = servings.get(key);
        if (state == null || state.food == null || !state.food.isValid()) {
            return false;
        }
        TransformComponent transform = commandBuffer.getComponent(
                state.food, TransformComponent.getComponentType());
        if (transform == null) {
            return false;
        }
        transform.setPosition(new Vector3d(key.x() + 0.5, key.y() - 64.0, key.z() + 0.5));
        transform.markChunkDirty(commandBuffer);
        return true;
    }

    synchronized boolean moveFoodVisual(
            TableKey key,
            Vector3d position,
            Rotation3f rotation,
            CommandBuffer<EntityStore> commandBuffer) {
        ServingState state = servings.get(key);
        if (state == null || state.food == null || !state.food.isValid()) {
            return false;
        }
        TransformComponent transform = commandBuffer.getComponent(
                state.food, TransformComponent.getComponentType());
        if (transform == null) {
            return false;
        }
        transform.setPosition(position);
        transform.setRotation(rotation);
        transform.markChunkDirty(commandBuffer);
        return true;
    }

    synchronized boolean restoreFoodVisual(
            TableKey key,
            CommandBuffer<EntityStore> commandBuffer) {
        ServingState state = servings.get(key);
        if (state == null || state.food == null || !state.food.isValid()) {
            return false;
        }
        repositionServing(
                key,
                state.food,
                TableServingComponent.Part.FOOD,
                commandBuffer);
        return true;
    }

    void lockForPatron(TableKey key) {
        patronLockedSlots.add(key);
    }

    void unlockForPatron(TableKey key) {
        patronLockedSlots.remove(key);
    }

    void track(
            UUID worldId,
            Ref<EntityStore> ref,
            TableServingComponent serving,
            CommandBuffer<EntityStore> commandBuffer) {
        TableServingComponent current = serving;
        if (serving.isLegacyDroppedItem()) {
            ItemComponent legacy = commandBuffer.getComponent(ref, ItemComponent.getComponentType());
            ItemStack legacyStack = legacy == null ? null : legacy.getItemStack();
            if (!isConsumable(legacyStack)) {
                commandBuffer.tryRemoveEntity(ref, RemoveReason.REMOVE);
                return;
            }
            current = new TableServingComponent(
                    serving.tableX(), serving.tableY(), serving.tableZ(),
                    TableServingComponent.Part.FOOD, legacyStack.withQuantity(1));
            commandBuffer.replaceComponent(ref, servingType, current);
            stripLegacyItemComponents(ref, commandBuffer);
        }

        TableKey key = key(worldId, current);
        ensurePropComponents(ref, current, commandBuffer);
        repositionServing(key, ref, current.part(), commandBuffer);
        ServingState state = servings.computeIfAbsent(key, ignored -> new ServingState());
        if (current.part() == TableServingComponent.Part.PLATE) {
            state.plate = ref;
        } else {
            state.food = ref;
            state.foodStack = current.itemStack();
            if (state.plate == null || !state.plate.isValid()) {
                spawnMissingPlate(key, ref, commandBuffer);
            }
        }
    }

    void untrack(UUID worldId, Ref<EntityStore> ref, TableServingComponent serving) {
        activeUses.remove(ref);
        TableKey key = key(worldId, serving);
        servings.computeIfPresent(key, (ignored, state) -> {
            if (ref.equals(state.food)) {
                state.food = null;
                state.foodStack = null;
            }
            if (ref.equals(state.plate)) {
                state.plate = null;
            }
            return state.food == null && state.plate == null ? null : state;
        });
    }

    /** Converts a Table's stable serving props back into ordinary world item drops. */
    int releaseAt(
            UUID worldId,
            Vector3i tablePosition,
            World world,
            CommandBuffer<EntityStore> commandBuffer) {
        Vector3i anchor = tableAnchor(world, tablePosition);
        List<TableKey> attachedKeys = servings.keySet().stream()
                .filter(key -> key.worldId().equals(worldId))
                .filter(key -> tableAnchor(
                        world, new Vector3i(key.x(), key.y(), key.z())).equals(anchor))
                .toList();
        if (attachedKeys.isEmpty()) {
            return 0;
        }

        boolean hadServing = false;
        boolean hadFood = false;
        String returnedPlateId = null;
        for (TableKey key : attachedKeys) {
            ServingState state = servings.remove(key);
            if (state == null) {
                continue;
            }
            hadServing = true;
            hadFood |= state.foodStack != null
                    || state.food != null && state.food.isValid();
            String statePlateId = plateItemId(state.plate, commandBuffer);
            if (DIRTY_PLATE_ITEM_ID.equals(statePlateId)) {
                returnedPlateId = DIRTY_PLATE_ITEM_ID;
            } else if (returnedPlateId == null && state.foodStack != null
                    && PreparedMeal.inspect(state.foodStack, preparedFoods).isPresent()) {
                returnedPlateId = CLEAN_PLATE_ITEM_ID;
            } else if (returnedPlateId == null && statePlateId != null) {
                returnedPlateId = statePlateId;
            }
            // Breaking a serviced Table intentionally destroys every food item.
            // Only one Plate is returned for the physical Table, even if an older
            // build allowed several filler-block service keys on that Table.
            removeReleasedProp(state.food, commandBuffer);
            removeReleasedProp(state.plate, commandBuffer);
        }
        if (!hadServing || !hadFood && returnedPlateId == null) {
            return 0;
        }
        if (returnedPlateId == null) {
            returnedPlateId = PLATE_ITEM_ID;
        }

        Vector3d dropPosition = new Vector3d(
                anchor.x() + 0.5,
                anchor.y() + 1.05,
                anchor.z() + 0.5);
        Holder<EntityStore>[] drops = ItemComponent.generateItemDrops(
                commandBuffer,
                List.of(new ItemStack(returnedPlateId, 1)),
                dropPosition,
                new Rotation3f());
        for (Holder<EntityStore> drop : drops) {
            commandBuffer.addEntity(drop, AddReason.SPAWN);
        }
        return drops.length;
    }

    private boolean startNativeConsumption(
            Ref<EntityStore> playerRef,
            Ref<EntityStore> foodRef,
            TableServingComponent serving,
            InteractionContext context) {
        ItemStack foodStack = serving.itemStack();
        String rootId = foodStack.getItem().getInteractions().get(InteractionType.Secondary);
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        InteractionChain parent = context.getChain();
        InventoryComponent.Hotbar servingHotbar = commandBuffer == null
                ? null
                : commandBuffer.getComponent(
                        foodRef, InventoryComponent.Hotbar.getComponentType());
        if (rootId == null || commandBuffer == null || parent == null || servingHotbar == null) {
            activeUses.remove(foodRef);
            return false;
        }

        ItemContainer servingContainer = servingHotbar.getInventory();
        if (!sameStack(servingContainer.getItemStack((short) 0), foodStack)) {
            ItemStackSlotTransaction sync = servingContainer.setItemStackForSlot((short) 0, foodStack);
            if (!sync.succeeded()) {
                activeUses.remove(foodRef);
                return false;
            }
        }

        InteractionContext proxy = InteractionContext.forProxyEntity(
                context.getInteractionManager(), foodRef, playerRef, commandBuffer);
        proxy.setInteractionVarsGetter(ignored -> foodStack.getItem().getInteractionVars());
        proxy.getMetaStore().putMetaObject(
                com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.TARGET_ENTITY,
                foodRef);
        InteractionChain nativeChain = context.fork(
                InteractionType.Secondary,
                proxy,
                RootInteraction.getRootInteractionOrUnknown(rootId),
                false);
        if (nativeChain == null) {
            activeUses.remove(foodRef);
            return false;
        }
        nativeChain.setOnCompletion(() -> finishNativeConsumption(
                playerRef, foodRef, serving, servingContainer, proxy));
        return true;
    }

    private void finishNativeConsumption(
            Ref<EntityStore> playerRef,
            Ref<EntityStore> foodRef,
            TableServingComponent serving,
            ItemContainer servingContainer,
            InteractionContext context) {
        try {
            if (releasedUses.remove(foodRef)) {
                return;
            }
            CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
            if (commandBuffer == null) {
                return;
            }
            ItemStack foodStack = serving.itemStack();
            ItemStack chainResult = servingContainer.getItemStack((short) 0);
            boolean consumed = !sameStack(chainResult, foodStack);

            if (consumed) {
                if (chainResult != null && !chainResult.isEmpty()) {
                    CombinedItemContainer inventory = InventoryComponent.getCombined(
                            commandBuffer, playerRef,
                            InventoryComponent.STORAGE_HOTBAR_BACKPACK);
                    inventory.addItemStack(chainResult);
                }
                TableKey key = key(worldId(commandBuffer.getStore()), serving);
                if (PreparedMeal.inspect(foodStack, preparedFoods).isPresent()) {
                    replacePlate(key, DIRTY_PLATE_ITEM_ID, commandBuffer);
                }
                removeFood(foodRef, serving, commandBuffer);
                return;
            }

            // A release below the vanilla Charging threshold leaves the serving
            // untouched. Pickup is exclusively the entity's F/Use interaction.
        } finally {
            activeUses.remove(foodRef);
        }
    }

    private boolean pickupFood(
            Ref<EntityStore> playerRef,
            Ref<EntityStore> foodRef,
            TableServingComponent serving,
            CommandBuffer<EntityStore> commandBuffer) {
        ItemStack foodStack = serving.itemStack();
        CombinedItemContainer inventory = InventoryComponent.getCombined(
                commandBuffer, playerRef, InventoryComponent.STORAGE_HOTBAR_BACKPACK);
        if (!inventory.canAddItemStack(foodStack)) {
            activeUses.remove(foodRef);
            return false;
        }
        ItemStackTransaction added = inventory.addItemStack(foodStack);
        if (!added.succeeded()) {
            activeUses.remove(foodRef);
            return false;
        }
        TableKey key = key(worldId(commandBuffer.getStore()), serving);
        if (PreparedMeal.inspect(foodStack, preparedFoods).isPresent()) {
            removePlate(key, commandBuffer);
        }
        removeFood(foodRef, serving, commandBuffer);
        playServingSound(PICKUP_SOUND_EVENT, key, commandBuffer);
        activeUses.remove(foodRef);
        return true;
    }

    private boolean pickupPlate(
            Ref<EntityStore> playerRef,
            Ref<EntityStore> plateRef,
            TableServingComponent serving,
            CommandBuffer<EntityStore> commandBuffer) {
        TableKey key = key(worldId(commandBuffer.getStore()), serving);
        ServingState state = servings.get(key);
        if (state != null && state.food != null && state.food.isValid()) {
            activeUses.remove(plateRef);
            return false;
        }
        CombinedItemContainer inventory = InventoryComponent.getCombined(
                commandBuffer, playerRef, InventoryComponent.STORAGE_HOTBAR_BACKPACK);
        ItemStack plate = serving.itemStack();
        if (!inventory.canAddItemStack(plate) || !inventory.addItemStack(plate).succeeded()) {
            activeUses.remove(plateRef);
            return false;
        }
        commandBuffer.tryRemoveEntity(plateRef, RemoveReason.REMOVE);
        playServingSound(PICKUP_SOUND_EVENT, key, commandBuffer);
        activeUses.remove(plateRef);
        return true;
    }

    private static void playServingSound(
            String soundEventId,
            TableKey key,
            CommandBuffer<EntityStore> commandBuffer) {
        int soundEventIndex = SoundEvent.getAssetMap().getIndex(soundEventId);
        if (soundEventIndex == SoundEvent.EMPTY_ID) {
            return;
        }
        SoundUtil.playSoundEvent3d(
                soundEventIndex,
                SoundCategory.SFX,
                key.x() + 0.5,
                key.y() + 1.0,
                key.z() + 0.5,
                commandBuffer);
    }

    private void removeFood(
            Ref<EntityStore> foodRef,
            TableServingComponent serving,
            CommandBuffer<EntityStore> commandBuffer) {
        TableKey key = key(worldId(commandBuffer.getStore()), serving);
        servings.computeIfPresent(key, (ignored, state) -> {
            if (foodRef.equals(state.food)) {
                state.food = null;
                state.foodStack = null;
            }
            return state.food == null && state.plate == null ? null : state;
        });
        commandBuffer.tryRemoveEntity(foodRef, RemoveReason.REMOVE);
    }

    private Holder<EntityStore> servingHolder(
            TableKey key,
            TableServingComponent.Part part,
            ItemStack itemStack,
            Vector3d position,
            Rotation3f rotation) {
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        Model model = modelFor(itemStack);
        holder.addComponent(TransformComponent.getComponentType(),
                new TransformComponent(position, rotation));
        holder.ensureComponent(UUIDComponent.getComponentType());
        holder.addComponent(PropComponent.getComponentType(), PropComponent.get());
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        if (model.getModelAssetId() != null) {
            holder.addComponent(PersistentModel.getComponentType(),
                    new PersistentModel(model.toReference()));
        }
        holder.addComponent(BoundingBox.getComponentType(),
                new BoundingBox(new Box(model.getBoundingBox())));
        holder.addComponent(Intangible.getComponentType(), Intangible.INSTANCE);
        holder.addComponent(Interactable.getComponentType(), Interactable.INSTANCE);
        holder.addComponent(Interactions.getComponentType(),
                servingInteractions());
        holder.addComponent(InventoryComponent.Hotbar.getComponentType(),
                servingHotbar(itemStack));
        holder.addComponent(servingType, new TableServingComponent(
                key.x(), key.y(), key.z(), part, itemStack));
        return holder;
    }

    private void ensurePropComponents(
            Ref<EntityStore> ref,
            TableServingComponent serving,
            CommandBuffer<EntityStore> commandBuffer) {
        ItemStack stack = serving.itemStack();
        if (stack == null || stack.isEmpty() || !stack.isValid()) {
            commandBuffer.tryRemoveEntity(ref, RemoveReason.REMOVE);
            return;
        }
        Model model = modelFor(stack);
        commandBuffer.putComponent(ref, PropComponent.getComponentType(), PropComponent.get());
        commandBuffer.putComponent(ref, ModelComponent.getComponentType(), new ModelComponent(model));
        commandBuffer.putComponent(ref, BoundingBox.getComponentType(),
                new BoundingBox(new Box(model.getBoundingBox())));
        commandBuffer.putComponent(ref, Intangible.getComponentType(), Intangible.INSTANCE);
        commandBuffer.putComponent(ref, Interactable.getComponentType(), Interactable.INSTANCE);
        commandBuffer.putComponent(ref, Interactions.getComponentType(),
                servingInteractions());
        commandBuffer.putComponent(ref, InventoryComponent.Hotbar.getComponentType(),
                servingHotbar(stack));
        if (model.getModelAssetId() != null) {
            commandBuffer.putComponent(ref, PersistentModel.getComponentType(),
                    new PersistentModel(model.toReference()));
        }
    }

    private void spawnMissingPlate(
            TableKey key,
            Ref<EntityStore> foodRef,
            CommandBuffer<EntityStore> commandBuffer) {
        World world = commandBuffer.getExternalData().getWorld();
        BlockType table = loadedBlockType(world, key.x(), key.y(), key.z());
        if (table == null || table == BlockType.EMPTY) {
            return;
        }
        ServingPose pose = servingPose(world, new TableTarget(key, table));
        TransformComponent foodTransform = commandBuffer.getComponent(
                foodRef, TransformComponent.getComponentType());
        if (foodTransform != null) {
            foodTransform.setPosition(pose.foodPosition());
            foodTransform.setRotation(pose.rotation());
            foodTransform.markChunkDirty(commandBuffer);
        }
        ServingState state = servings.get(key);
        ItemStack plate = plateFor(state == null ? null : state.foodStack);
        Ref<EntityStore> plateRef = commandBuffer.addEntity(
                servingHolder(key, TableServingComponent.Part.PLATE, plate,
                        pose.platePosition(), pose.rotation()),
                AddReason.SPAWN);
        servings.computeIfAbsent(key, ignored -> new ServingState()).plate = plateRef;
    }

    private ItemStack plateFor(ItemStack foodStack) {
        return new ItemStack(
                PreparedMeal.inspect(foodStack, preparedFoods).isPresent()
                        ? CLEAN_PLATE_ITEM_ID
                        : PLATE_ITEM_ID,
                1);
    }

    private void replacePlate(
            TableKey key,
            String plateItemId,
            CommandBuffer<EntityStore> commandBuffer) {
        ServingState state = servings.get(key);
        if (state == null) {
            return;
        }
        removePlate(key, commandBuffer);
        World world = commandBuffer.getExternalData().getWorld();
        BlockType table = loadedBlockType(world, key.x(), key.y(), key.z());
        if (table == null || table == BlockType.EMPTY) {
            return;
        }
        ServingPose pose = servingPose(world, new TableTarget(key, table));
        state.plate = commandBuffer.addEntity(
                servingHolder(
                        key,
                        TableServingComponent.Part.PLATE,
                        new ItemStack(plateItemId, 1),
                        pose.platePosition(),
                        pose.rotation()),
                AddReason.SPAWN);
    }

    private void removePlate(TableKey key, CommandBuffer<EntityStore> commandBuffer) {
        ServingState state = servings.get(key);
        if (state == null || state.plate == null) {
            return;
        }
        Ref<EntityStore> plate = state.plate;
        state.plate = null;
        activeUses.remove(plate);
        if (plate.isValid()) {
            commandBuffer.tryRemoveEntity(plate, RemoveReason.REMOVE);
        }
    }

    private String plateItemId(
            Ref<EntityStore> plateRef,
            CommandBuffer<EntityStore> commandBuffer) {
        if (plateRef == null || !plateRef.isValid()) {
            return null;
        }
        TableServingComponent plate = commandBuffer.getComponent(plateRef, servingType);
        return plate == null || plate.itemStack() == null
                ? null
                : plate.itemStack().getItemId();
    }

    private void repositionServing(
            TableKey key,
            Ref<EntityStore> ref,
            TableServingComponent.Part part,
            CommandBuffer<EntityStore> commandBuffer) {
        World world = commandBuffer.getExternalData().getWorld();
        BlockType table = loadedBlockType(world, key.x(), key.y(), key.z());
        if (table == null || table == BlockType.EMPTY || !isRegisteredTable(table)) {
            return;
        }
        ServingPose pose = servingPose(world, new TableTarget(key, table));
        TransformComponent transform = commandBuffer.getComponent(
                ref, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }
        transform.setPosition(part == TableServingComponent.Part.PLATE
                ? pose.platePosition() : pose.foodPosition());
        transform.setRotation(pose.rotation());
        transform.markChunkDirty(commandBuffer);
    }

    /** Non-loading lookup safe to use while an EntityStore system is processing. */
    private static BlockType loadedBlockType(World world, int x, int y, int z) {
        WorldChunk chunk = world.getChunkIfLoaded(
                ChunkUtil.indexChunkFromBlock(x, z));
        return chunk == null ? null : chunk.getBlockType(x, y, z);
    }

    private static void stripLegacyItemComponents(
            Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer) {
        commandBuffer.tryRemoveComponent(ref, ItemComponent.getComponentType());
        commandBuffer.tryRemoveComponent(ref, ItemPhysicsComponent.getComponentType());
        commandBuffer.tryRemoveComponent(ref, Velocity.getComponentType());
        commandBuffer.tryRemoveComponent(ref, PhysicsValues.getComponentType());
        commandBuffer.tryRemoveComponent(ref, DespawnComponent.getComponentType());
        commandBuffer.tryRemoveComponent(ref, PreventPickup.getComponentType());
        commandBuffer.tryRemoveComponent(ref, PreventItemMerging.getComponentType());
    }

    Model modelFor(ItemStack stack) {
        Item item = stack.getItem();
        String itemModel = item.getModel();
        if (itemModel != null) {
            ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(itemModel);
            if (modelAsset != null) {
                return Model.createStaticScaledModel(modelAsset, item.getScale());
            }
        }

        BlockType block = item.getBlockId() == null
                ? null
                : BlockType.getAssetMap().getAsset(item.getBlockId());
        String modelPath = block != null ? block.getCustomModel() : itemModel;
        String texturePath = firstTexture(block, item.getTexture());
        float scale = block != null && block.getCustomModelScale() > 0.0f
                ? block.getCustomModelScale()
                : Math.max(0.01f, item.getScale());
        Box box = visualBox(block, scale);
        if (modelPath == null || texturePath == null) {
            return Model.createUnitScaleModel(ModelAsset.DEBUG, box);
        }
        return new Model(
                null, scale, Map.of(), new ModelAttachment[0], box,
                modelPath, texturePath, null, null,
                (float) box.max.y, 0.0f, 0.0f, 0.0f,
                Map.of(), null, null,
                new ModelParticle[0], new ModelTrail[0], null,
                Map.<String, DetailBox[]>of(), Phobia.None, null);
    }

    private static InventoryComponent.Hotbar servingHotbar(ItemStack stack) {
        SimpleItemContainer container = new SimpleItemContainer((short) 1);
        container.setItemStackForSlot((short) 0, stack);
        return new InventoryComponent.Hotbar(container, (byte) 0);
    }

    private static Interactions servingInteractions() {
        // Empty-hand entity use dispatches InteractionType.Use. Secondary remains
        // registered for held-item chains that explicitly execute UseEntity.
        return new Interactions(Map.of(
                InteractionType.Use, SERVING_ROOT_ID,
                InteractionType.Secondary, SERVING_ROOT_ID));
    }

    private void removeReleasedProp(
            Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer) {
        if (ref == null) {
            return;
        }
        if (activeUses.remove(ref)) {
            releasedUses.add(ref);
        }
        if (ref.isValid()) {
            commandBuffer.tryRemoveEntity(ref, RemoveReason.REMOVE);
        }
    }

    private static String firstTexture(BlockType block, String fallback) {
        if (block != null) {
            CustomModelTexture[] textures = block.getCustomModelTexture();
            if (textures != null && textures.length > 0 && textures[0] != null) {
                return textures[0].getTexture();
            }
        }
        return fallback;
    }

    private static Box visualBox(BlockType block, float scale) {
        if (block == null) {
            return new Box(DEFAULT_MODEL_BOX);
        }
        BlockBoundingBoxes boxes = BlockBoundingBoxes.getAssetMap().getAsset(
                block.getHitboxTypeIndex());
        if (boxes == null) {
            return new Box(DEFAULT_MODEL_BOX);
        }
        Box source = boxes.get(0).getBoundingBox();
        if (source == null || !source.hasVolume()) {
            return new Box(DEFAULT_MODEL_BOX);
        }
        Box centered = new Box(
                source.min.x - 0.5, Math.max(0.0, source.min.y), source.min.z - 0.5,
                source.max.x - 0.5, Math.max(0.08, source.max.y), source.max.z - 0.5);
        return centered.scale(scale);
    }

    private boolean isSeatedAtValidChair(
            Ref<EntityStore> playerRef,
            TableKey table,
            CommandBuffer<EntityStore> commandBuffer) {
        MountedComponent mounted = commandBuffer.getComponent(
                playerRef, MountedComponent.getComponentType());
        Ref<ChunkStore> mountedBlock = mounted == null ? null : mounted.getMountedToBlock();
        if (mountedBlock == null || !mountedBlock.isValid()
                || mounted.getBlockMountType() != BlockMountType.Seat) {
            return false;
        }
        BlockMountComponent blockMount = mountedBlock.getStore().getComponent(
                mountedBlock, BlockMountComponent.getComponentType());
        Vector3i chairPosition = blockMount == null ? null : blockMount.getBlockPos();
        if (chairPosition == null) {
            return false;
        }
        World world = commandBuffer.getExternalData().getWorld();
        BlockType chair = BlockType.getAssetMap().getAsset(world.getBlock(
                chairPosition.x(), chairPosition.y(), chairPosition.z()));
        if (chair == null || !isRegisteredCategory(chair, ComfortCategory.SEATING)) {
            return false;
        }
        Optional<CoreRecord> tableCore = repository.findPrimaryCoreContaining(
                table.worldId(), table.x(), table.y(), table.z());
        Optional<CoreRecord> chairCore = repository.findPrimaryCoreContaining(
                table.worldId(), chairPosition.x(), chairPosition.y(), chairPosition.z());
        return tableCore.isPresent() && chairCore.isPresent()
                && tableCore.get().coreId().equals(chairCore.get().coreId());
    }

    boolean isRegisteredTable(BlockType blockType) {
        return isRegisteredCategory(blockType, ComfortCategory.TABLES);
    }

    boolean isRegisteredSeat(BlockType blockType) {
        return isRegisteredCategory(blockType, ComfortCategory.SEATING);
    }

    private boolean isRegisteredCategory(BlockType blockType, ComfortCategory category) {
        Item blockItem = blockType.getItem();
        String assetId = blockItem == null ? blockType.getId() : blockItem.getId();
        Optional<ComfortDefinition> found = comfortRegistry.find(assetId);
        if (found.isEmpty() && !assetId.equals(blockType.getId())) {
            found = comfortRegistry.find(blockType.getId());
        }
        return found.isPresent() && found.get().category() == category;
    }

    static boolean isConsumable(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isValid()
                && stack.getQuantity() > 0 && isConsumable(stack.getItem());
    }

    static boolean isConsumable(Item item) {
        return item != null && item.isConsumable();
    }

    static ServingPose servingPose(World world, TableTarget target) {
        TableKey key = target.key();
        long chunkIndex = ChunkUtil.indexChunkFromBlock(key.x(), key.z());
        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
        RotationTuple blockRotation = chunk == null
                ? RotationTuple.NONE
                : chunk.getBlockChunk().getSectionAtBlockY(key.y()).getRotation(
                        ChunkUtil.localCoordinate(key.x()), key.y(),
                        ChunkUtil.localCoordinate(key.z()));
        Rotation3f displayRotation = new Rotation3f();
        blockRotation.applyRotationTo(displayRotation);

        double surfaceHeight = 1.0;
        BlockBoundingBoxes hitboxes = BlockBoundingBoxes.getAssetMap().getAsset(
                target.blockType().getHitboxTypeIndex());
        if (hitboxes != null) {
            Box bounds = hitboxes.get(blockRotation.index()).getBoundingBox();
            if (bounds != null && Double.isFinite(bounds.max.y) && bounds.max.y > 0.0) {
                surfaceHeight = bounds.max.y;
            }
        }

        Item tableItem = target.blockType().getItem();
        String tableAssetId = tableItem == null ? target.blockType().getId() : tableItem.getId();
        PlacementProfile profile = TABLE_PROFILES.getOrDefault(tableAssetId, DEFAULT_PROFILE);
        Vector3d localPlate = new Vector3d(
                profile.localX(),
                surfaceHeight + profile.surfaceAdjustment() + profile.plateLift(),
                profile.localZ());
        Vector3d localFood = new Vector3d(
                profile.localX(),
                surfaceHeight + profile.surfaceAdjustment() + profile.foodLift(),
                profile.localZ());
        blockRotation.applyRotationTo(localPlate);
        blockRotation.applyRotationTo(localFood);
        return new ServingPose(
                new Vector3d(key.x() + 0.5 + localPlate.x,
                        key.y() + localPlate.y, key.z() + 0.5 + localPlate.z),
                new Vector3d(key.x() + 0.5 + localFood.x,
                        key.y() + localFood.y, key.z() + 0.5 + localFood.z),
                displayRotation);
    }

    private static boolean sameStack(ItemStack left, ItemStack right) {
        return left == right || left != null && left.equals(right);
    }

    private static TableKey key(UUID worldId, TableServingComponent serving) {
        return new TableKey(worldId, serving.tableX(), serving.tableY(), serving.tableZ());
    }

    private static UUID worldId(Store<EntityStore> store) {
        return store.getExternalData().getWorld().getWorldConfig().getUuid();
    }

    private static Vector3i tableAnchor(World world, Vector3i position) {
        WorldChunk chunk = world.getChunkIfLoaded(
                ChunkUtil.indexChunkFromBlock(position.x(), position.z()));
        if (chunk == null) {
            return new Vector3i(position);
        }
        int filler = chunk.getBlockChunk()
                .getSectionAtBlockY(position.y())
                .getFiller(
                        ChunkUtil.localCoordinate(position.x()),
                        position.y(),
                        ChunkUtil.localCoordinate(position.z()));
        if (filler == FillerBlockUtil.NO_FILLER) {
            return new Vector3i(position);
        }
        return new Vector3i(
                position.x() - FillerBlockUtil.unpackX(filler),
                position.y() - FillerBlockUtil.unpackY(filler),
                position.z() - FillerBlockUtil.unpackZ(filler));
    }

    enum PlacementResult { PLACED, OCCUPIED, INVENTORY_REJECTED }

    record TableKey(UUID worldId, int x, int y, int z) { }

    record TableTarget(TableKey key, BlockType blockType) { }

    record ServingSnapshot(
            Ref<EntityStore> foodRef,
            ItemStack foodStack,
            boolean platePresent) { }

    record ServingPose(Vector3d platePosition, Vector3d foodPosition, Rotation3f rotation) { }

    record PlacementProfile(
            double localX,
            double localZ,
            double surfaceAdjustment,
            double plateLift,
            double foodLift) { }

    private static final class ServingState {
        private Ref<EntityStore> plate;
        private Ref<EntityStore> food;
        private ItemStack foodStack;
    }
}
