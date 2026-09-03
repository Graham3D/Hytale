package com.inigmasgames.persistentnpcs.action;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.inigmasgames.persistentnpcs.hytale.NpcRuntimeRegistry;
import com.inigmasgames.persistentnpcs.home.NpcHomeAnchor;
import com.inigmasgames.persistentnpcs.home.NpcHomeBehaviorController;
import com.inigmasgames.persistentnpcs.event.NpcEventBus;
import com.inigmasgames.persistentnpcs.event.NpcEventType;
import com.inigmasgames.persistentnpcs.event.NpcFrameworkEvent;
import com.inigmasgames.persistentnpcs.economy.ObligationRecord;
import com.inigmasgames.persistentnpcs.economy.ObligationStore;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import com.inigmasgames.persistentnpcs.task.NpcTask;
import com.inigmasgames.persistentnpcs.task.NpcTaskScheduler;
import com.inigmasgames.persistentnpcs.task.NpcTaskState;
import com.inigmasgames.persistentnpcs.task.NpcTaskStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.joml.Vector3d;
import java.util.function.Consumer;
import com.inigmasgames.persistentnpcs.hytale.GroundPositionResolver;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;

/** Deterministic server-thread execution for the registered action surface. */
public final class HytaleNpcActionService implements NpcActionExecutor {
    private static final double INTERACTION_DISTANCE_SQUARED = 4.5 * 4.5;
    private final NpcRuntimeRegistry runtimes;
    private final NpcTaskStore tasks;
    private final NpcEventBus events;
    private final RelationshipStore relationships;
    private final ObligationStore obligations;
    private final Consumer<String> diagnostics;
    private final NpcHomeBehaviorController homeBehavior;

    public HytaleNpcActionService(NpcRuntimeRegistry runtimes, NpcTaskStore tasks) {
        this(runtimes, tasks, new NpcEventBus(), null, null, ignored -> { }, null);
    }

    public HytaleNpcActionService(
            NpcRuntimeRegistry runtimes, NpcTaskStore tasks, NpcEventBus events) {
        this(runtimes, tasks, events, null, null, ignored -> { }, null);
    }

    public HytaleNpcActionService(
            NpcRuntimeRegistry runtimes,
            NpcTaskStore tasks,
            NpcEventBus events,
            RelationshipStore relationships,
            ObligationStore obligations) {
        this(runtimes, tasks, events, relationships, obligations, ignored -> { }, null);
    }

    public HytaleNpcActionService(
            NpcRuntimeRegistry runtimes,
            NpcTaskStore tasks,
            NpcEventBus events,
            RelationshipStore relationships,
            ObligationStore obligations,
            Consumer<String> diagnostics) {
        this(runtimes, tasks, events, relationships, obligations, diagnostics, null);
    }

    public HytaleNpcActionService(
            NpcRuntimeRegistry runtimes,
            NpcTaskStore tasks,
            NpcEventBus events,
            RelationshipStore relationships,
            ObligationStore obligations,
            Consumer<String> diagnostics,
            NpcHomeBehaviorController homeBehavior) {
        this.runtimes = runtimes;
        this.tasks = tasks;
        this.events = events;
        this.relationships = relationships;
        this.obligations = obligations;
        this.diagnostics = diagnostics;
        this.homeBehavior = homeBehavior;
    }

    public void registerDefaults(NpcActionRegistry registry) {
        register(registry, "FOLLOW_PLAYER", "Begin following the focused player.",
                objectSchema(), Set.of("FOLLOW_PLAYER"), Set.of());
        register(registry, "STOP_FOLLOWING", "Stop following any player.",
                schema(property("waitHere", "boolean", false)),
                Set.of("STOP_FOLLOWING"), Set.of());
        register(registry, "GO_TO", "Navigate to exact server-world coordinates.",
                coordinatesSchema(), Set.of("GO_TO"), Set.of());
        register(registry, "PATROL", "Patrol repeatedly between the current position and server-world coordinates.",
                coordinatesSchema(), Set.of("PATROL"), Set.of());
        register(registry, "WANDER", "Move to a deterministic nearby point within a bounded radius.",
                schema(property("radius", "number", false)), Set.of("WANDER"), Set.of());
        register(registry, "FLEE", "Move directly away from the focused player.",
                schema(property("distance", "number", false)), Set.of("FLEE"), Set.of());
        register(registry, "CANCEL_TASK", "Cancel the NPC's active task of a named type.",
                schema(property("taskType", "string", true)), Set.of("CANCEL_TASK"), Set.of());
        register(registry, "WAIT", "Stop and wait at the current position.",
                schema(property("durationSeconds", "number", false)),
                Set.of("WAIT"), Set.of());
        register(registry, "PICK_UP_ITEM", "Pick up a perceived dropped item entity.",
                schema(property("itemEntityId", "string", false),
                        property("itemId", "string", false)),
                Set.of("PICK_UP_ITEM"), Set.of());
        register(registry, "TAKE_ITEM",
                "Take one or more items from the focused player's exact held ItemStack.",
                schema(property("quantity", "integer", false)),
                Set.of("TAKE_ITEM"), Set.of());
        register(registry, "GIVE_ITEM", "Give an item from NPC inventory to the focused player.",
                schema(property("itemId", "string", true),
                        property("quantity", "integer", false)),
                Set.of("GIVE_ITEM"), Set.of());
        register(registry, "DROP_ITEM", "Drop an item from NPC inventory into the world.",
                schema(property("itemId", "string", true),
                        property("quantity", "integer", false)),
                Set.of("DROP_ITEM"), Set.of());
        register(registry, "INSPECT_ITEM",
                "Inspect the focused player's held item or an NPC inventory item.",
                schema(property("itemId", "string", false)),
                Set.of("INSPECT_ITEM"), Set.of());
        register(registry, "BRING_ITEM",
                "Fetch the nearest matching perceived dropped item and physically deliver it to the focused player.",
                schema(property("itemId", "string", true)), Set.of("BRING_ITEM"), Set.of());
        register(registry, "EQUIP_ITEM", "Move a compatible owned item into NPC armor equipment.",
                schema(property("itemId", "string", true)), Set.of("EQUIP_ITEM"), Set.of());
        register(registry, "UNEQUIP_ITEM", "Move a matching equipped item back into NPC storage.",
                schema(property("itemId", "string", true)), Set.of("UNEQUIP_ITEM"), Set.of());
        register(registry, "SCHEDULE_MEETING",
                "Schedule a future meeting at exact coordinates and 24-hour game time.",
                schema(property("hour", "integer", true),
                        property("minute", "integer", false),
                        property("x", "number", false),
                        property("y", "number", false),
                        property("z", "number", false),
                        property("purpose", "string", false)),
                Set.of("SCHEDULE_MEETING"), Set.of());
        register(registry, "SCHEDULE_TASK",
                "Schedule one supported future task in Hytale game time without keeping the LLM running.",
                schema(property("taskType", "string", true),
                        property("hour", "integer", true),
                        property("minute", "integer", false),
                        property("x", "number", false), property("y", "number", false),
                        property("z", "number", false), property("purpose", "string", false)),
                Set.of("SCHEDULE_TASK"), Set.of());
        register(registry, "CRAFT_ITEM",
                "Craft a real item using a Hytale recipe, required station, and NPC ingredients.",
                schema(property("requestedItem", "string", true)),
                Set.of("CRAFT_ITEM"), Set.of("BLACKSMITH"));
        register(registry, "COOK_ITEM",
                "Cook a real recipe at its required Hytale station and deliver the output.",
                schema(property("requestedItem", "string", true)),
                Set.of("COOK_ITEM"), Set.of("COOK"));
        register(registry, "PROCESS_ITEM",
                "Process a real Hytale recipe at its required station and deliver the output.",
                schema(property("requestedItem", "string", true)),
                Set.of("PROCESS_ITEM"), Set.of());
        register(registry, "ADJUST_RELATIONSHIP",
                "Apply a small deterministic social-state change after a meaningful event.",
                schema(property("trust", "integer", false),
                        property("affection", "integer", false),
                        property("respect", "integer", false),
                        property("fear", "integer", false),
                        property("hostility", "integer", false),
                        property("obligation", "integer", false)),
                Set.of("ADJUST_RELATIONSHIP"), Set.of());
        register(registry, "CREATE_OBLIGATION",
                "Record a debt or non-currency obligation between this NPC and the focused player.",
                schema(property("amount", "integer", true), property("unit", "string", true),
                        property("reason", "string", true),
                        property("direction", "string", true),
                        property("recurrenceGameDays", "integer", false)),
                Set.of("CREATE_OBLIGATION"), Set.of());
        register(registry, "ADD_TO_OBLIGATION",
                "Increase the single active obligation between this NPC and the focused player.",
                schema(property("amount", "integer", true)),
                Set.of("ADD_TO_OBLIGATION"), Set.of());
        register(registry, "FORGIVE_OBLIGATION",
                "Settle the single active obligation without transferring fabricated currency.",
                objectSchema(), Set.of("FORGIVE_OBLIGATION"), Set.of());
    }

    private void register(
            NpcActionRegistry registry,
            String id,
            String description,
            JsonObject schema,
            Set<String> capabilities,
            Set<String> roles) {
        registry.register(new NpcActionDefinition(id, description, schema,
                capabilities, roles,
                context -> context.perception().npcEntityId() != null,
                (request, context) -> NpcActionResult.success("Validated " + id + "."),
                this, id + " result"));
    }

    @Override
    public CompletableFuture<NpcActionResult> execute(
            NpcActionRequest request, NpcActionContext context) {
        NpcRuntimeRegistry.RuntimeNpc runtime = runtimes.forProfile(
                context.profile().id()).orElse(null);
        if (runtime == null) {
            return CompletableFuture.completedFuture(NpcActionResult.failure(
                    "NPC_UNLOADED", "The NPC entity is not currently loaded."));
        }
        World world = Universe.get().getWorld(runtime.worldId());
        if (world == null || !world.isAlive()) {
            return CompletableFuture.completedFuture(NpcActionResult.failure(
                    "WORLD_UNAVAILABLE", "The NPC world is not available."));
        }
        CompletableFuture<NpcActionResult> future = new CompletableFuture<>();
        world.execute(() -> {
            try {
                future.complete(executeOnWorld(request, context, runtime, world));
            } catch (Throwable failure) {
                future.completeExceptionally(failure);
            }
        });
        return future;
    }

    private NpcActionResult executeOnWorld(
            NpcActionRequest request,
            NpcActionContext context,
            NpcRuntimeRegistry.RuntimeNpc runtime,
            World world) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        Ref<EntityStore> npcRef = world.getEntityRef(runtime.entityId());
        Ref<EntityStore> playerRef = world.getEntityRef(context.session().playerId());
        if (npcRef == null || !npcRef.isValid()) {
            return NpcActionResult.failure("NPC_STALE", "The NPC entity no longer exists.");
        }
        NpcActionResult result = switch (request.id()) {
            case "FOLLOW_PLAYER" -> follow(context, runtime, npcRef, playerRef, store, world);
            case "STOP_FOLLOWING" -> stopFollowing(
                    request, context, runtime, npcRef, store);
            case "GO_TO" -> goTo(request, context, runtime, npcRef, store);
            case "PATROL" -> patrol(request, context, runtime, npcRef, store);
            case "WANDER" -> wander(request, context, runtime, npcRef, playerRef, store);
            case "FLEE" -> flee(request, context, runtime, npcRef, playerRef, store);
            case "CANCEL_TASK" -> cancelTask(request, context, runtime, npcRef, store);
            case "WAIT" -> waitHere(request, context, runtime, npcRef, store);
            case "TAKE_ITEM" -> takeItem(request, npcRef, playerRef, store);
            case "GIVE_ITEM" -> giveItem(request, npcRef, playerRef, store);
            case "DROP_ITEM" -> dropItem(request, npcRef, store);
            case "PICK_UP_ITEM" -> pickUpItem(request, npcRef, store, world);
            case "INSPECT_ITEM" -> inspectItem(request, npcRef, playerRef, store);
            case "BRING_ITEM" -> bringItem(request, context, runtime, npcRef, store, world);
            case "EQUIP_ITEM" -> equipItem(request, npcRef, store);
            case "UNEQUIP_ITEM" -> unequipItem(request, npcRef, store);
            case "SCHEDULE_MEETING" -> scheduleMeeting(
                    request, context, runtime, npcRef, store);
            case "SCHEDULE_TASK" -> scheduleTask(
                    request, context, runtime, npcRef, store);
            case "CRAFT_ITEM", "COOK_ITEM", "PROCESS_ITEM" ->
                    craftItem(request, context, runtime, npcRef, store, world);
            case "ADJUST_RELATIONSHIP" -> adjustRelationship(request, context);
            case "CREATE_OBLIGATION" -> createObligation(request, context);
            case "ADD_TO_OBLIGATION" -> changeObligation(request, context, false);
            case "FORGIVE_OBLIGATION" -> changeObligation(request, context, true);
            default -> NpcActionResult.failure(
                    "UNKNOWN_ACTION", "Unknown action rejected: " + request.id());
        };
        if (result.success()) {
            emitActionEvent(request, context, result);
        }
        if ("FOLLOW_PLAYER".equals(request.id())) {
            diagnostics.accept("Follow trace session=" + context.session().sessionId()
                    + " validation=" + (result.success() ? "PASSED" : result.code())
                    + " taskCreated=" + result.success()
                    + " navigationStarted=" + result.success()
                    + " failureReason=" + (result.success() ? "NONE"
                            : result.eventDescription()));
        }
        return result;
    }

    private void emitActionEvent(
            NpcActionRequest request, NpcActionContext context, NpcActionResult result) {
        NpcEventType type = switch (request.id()) {
            case "TAKE_ITEM" -> NpcEventType.ITEM_TAKEN;
            case "GIVE_ITEM" -> NpcEventType.ITEM_GIVEN;
            case "EQUIP_ITEM", "UNEQUIP_ITEM" -> NpcEventType.ITEM_EQUIPPED;
            default -> null;
        };
        if (type == null) {
            return;
        }
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("actionId", request.id());
        facts.put("result", result.eventDescription());
        String itemId = string(request.parameters(), "itemId");
        int quantity = integer(request.parameters(), "quantity", 1);
        if ("TAKE_ITEM".equals(request.id())
                && context.perception().focusedPlayerHeldItem() != null) {
            itemId = context.perception().focusedPlayerHeldItem().itemId();
            quantity = Math.min(Math.max(1, quantity),
                    context.perception().focusedPlayerHeldItem().quantity());
        }
        facts.put("itemId", itemId);
        facts.put("quantity", Integer.toString(quantity));
        events.emit(new NpcFrameworkEvent(UUID.randomUUID(), type, context.profile().id(),
                context.profile().id(), context.session().playerId(), Instant.now(), facts));
    }

    private NpcActionResult follow(
            NpcActionContext context,
            NpcRuntimeRegistry.RuntimeNpc runtime,
            Ref<EntityStore> npcRef,
            Ref<EntityStore> playerRef,
            Store<EntityStore> store,
            World world) {
        if (!near(npcRef, playerRef, store)) {
            return NpcActionResult.failure("PLAYER_NOT_NEARBY",
                    "The focused player is no longer within interaction distance.");
        }
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        TransformComponent target = store.getComponent(playerRef, TransformComponent.getComponentType());
        PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (npc == null || target == null || player == null) {
            return NpcActionResult.failure("ENTITY_STATE_MISSING", "Navigation state is missing.");
        }
        Vector3d trailing = NpcTaskScheduler.trailingPosition(target.getPosition(),
                new Vector3d(player.getTransform().getDirection()));
        trailing = GroundPositionResolver.resolve(world, trailing).orElse(trailing);
        tasks.cancelMovementTasks(context.profile().id(),
                "Superseded by explicit follow request.");
        Map<String, String> data = new LinkedHashMap<>();
        data.put("movementState", "FOLLOWING_PLAYER");
        data.put("source", "PLAYER_ACTION");
        tasks.put(task(context, runtime, "FOLLOW_PLAYER", trailing,
                null, "Follow the requester", NpcTaskState.ACTIVE).withData(data));
        if (homeBehavior != null) {
            homeBehavior.beginFollowing(context.profile().id());
        }
        npc.getPathManager().setTransientPath(null);
        npc.setLeashPoint(trailing);
        MovementStatesComponent states = store.getComponent(
                npcRef, MovementStatesComponent.getComponentType());
        boolean grounded = GroundPositionResolver.isGrounded(world,
                store.getComponent(npcRef, TransformComponent.getComponentType()).getPosition());
        diagnostics.accept("Follow trace session=" + context.session().sessionId()
                + " followAuthorized=true actionRequested=true validation=PASSED"
                + " taskCreated=true navigationStarted=true targetPosition="
                + "%.1f,%.1f,%.1f".formatted(trailing.x, trailing.y, trailing.z)
                + " npcGrounded=" + grounded + " distanceToTarget="
                + "%.2f".formatted(store.getComponent(npcRef,
                        TransformComponent.getComponentType()).getPosition().distance(trailing))
                + " movementState=" + (states == null ? "UNKNOWN"
                        : states.getMovementStates().onGround ? "GROUNDED" : "AIRBORNE")
                + " failureReason=NONE");
        return NpcActionResult.success("Mara began following player "
                + context.session().playerId() + ".");
    }

    private NpcActionResult stopFollowing(
            NpcActionRequest request,
            NpcActionContext context,
            NpcRuntimeRegistry.RuntimeNpc runtime,
            Ref<EntityStore> npcRef,
            Store<EntityStore> store) {
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        TransformComponent transform = store.getComponent(npcRef, TransformComponent.getComponentType());
        if (npc == null || transform == null) {
            return NpcActionResult.failure("ENTITY_STATE_MISSING", "Navigation state is missing.");
        }
        boolean waitHere = request.parameters().has("waitHere")
                ? request.parameters().get("waitHere").getAsBoolean()
                : waitHereIntent(context.playerMessage());
        tasks.cancelMovementTasks(context.profile().id(), "Stopped by player request.");
        npc.getPathManager().setTransientPath(null);
        if (homeBehavior == null) {
            npc.setLeashPoint(new Vector3d(transform.getPosition()));
            return NpcActionResult.success("Mara stopped following.");
        }
        NpcHomeAnchor anchor = homeBehavior.stopFollowing(context.profile().id(),
                runtime.worldId(), transform.getPosition(), waitHere, Instant.now());
        npc.setLeashPoint(waitHere ? new Vector3d(transform.getPosition()) : anchor.anchor());
        return NpcActionResult.success(waitHere
                ? "Mara stopped following and established this position as her anchor."
                : "Mara stopped following and is returning to her established home anchor.");
    }

    private NpcActionResult goTo(
            NpcActionRequest request,
            NpcActionContext context,
            NpcRuntimeRegistry.RuntimeNpc runtime,
            Ref<EntityStore> npcRef,
            Store<EntityStore> store) {
        Vector3d target = coordinates(request.parameters());
        if (target == null || target.distanceSquared(context.perception().x(),
                context.perception().y(), context.perception().z()) > 192 * 192) {
            return NpcActionResult.failure("INVALID_DESTINATION",
                    "GO_TO requires finite x/y/z within 192 meters.");
        }
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null) {
            return NpcActionResult.failure("NAVIGATION_UNAVAILABLE", "NPC navigation is missing.");
        }
        tasks.put(task(context, runtime, "GO_TO", target, null,
                "Travel to requested coordinates", NpcTaskState.TRAVELING));
        npc.getPathManager().setTransientPath(null);
        npc.setLeashPoint(target);
        return NpcActionResult.success("Mara is navigating to %.1f, %.1f, %.1f."
                .formatted(target.x, target.y, target.z));
    }

    private NpcActionResult patrol(
            NpcActionRequest request,
            NpcActionContext context,
            NpcRuntimeRegistry.RuntimeNpc runtime,
            Ref<EntityStore> npcRef,
            Store<EntityStore> store) {
        Vector3d target = coordinates(request.parameters());
        TransformComponent transform = store.getComponent(
                npcRef, TransformComponent.getComponentType());
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (target == null || transform == null || npc == null
                || target.distanceSquared(transform.getPosition()) > 192 * 192) {
            return NpcActionResult.failure("INVALID_PATROL",
                    "PATROL requires a valid destination within 192 meters.");
        }
        Map<String, String> data = new LinkedHashMap<>();
        data.put("ax", Double.toString(transform.getPosition().x));
        data.put("ay", Double.toString(transform.getPosition().y));
        data.put("az", Double.toString(transform.getPosition().z));
        data.put("bx", Double.toString(target.x));
        data.put("by", Double.toString(target.y));
        data.put("bz", Double.toString(target.z));
        data.put("leg", "B");
        NpcTask task = task(context, runtime, "PATROL", target, null,
                "Patrol between two authoritative points", NpcTaskState.TRAVELING)
                .withData(data);
        tasks.cancelType(context.profile().id(), "PATROL", "Superseded.");
        tasks.put(task);
        npc.setLeashPoint(target);
        return NpcActionResult.success("Patrol started between the current position and "
                + "%.1f, %.1f, %.1f.".formatted(target.x, target.y, target.z));
    }

    private NpcActionResult wander(
            NpcActionRequest request,
            NpcActionContext context,
            NpcRuntimeRegistry.RuntimeNpc runtime,
            Ref<EntityStore> npcRef,
            Ref<EntityStore> playerRef,
            Store<EntityStore> store) {
        TransformComponent transform = store.getComponent(
                npcRef, TransformComponent.getComponentType());
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (transform == null || npc == null) {
            return NpcActionResult.failure("NAVIGATION_UNAVAILABLE", "NPC navigation is missing.");
        }
        double radius = Math.max(2.0, Math.min(16.0,
                number(request.parameters(), "radius", 6.0)));
        long seed = context.profile().id().getMostSignificantBits()
                ^ Instant.now().getEpochSecond() / 30;
        double angle = Math.floorMod(seed, 6283) / 1000.0;
        Vector3d target = new Vector3d(transform.getPosition()).add(
                Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
        tasks.put(task(context, runtime, "WANDER", target, null,
                "Bounded wander", NpcTaskState.TRAVELING));
        npc.setLeashPoint(target);
        return NpcActionResult.success("NPC began a bounded wander within %.1fm.".formatted(radius));
    }

    private NpcActionResult flee(
            NpcActionRequest request,
            NpcActionContext context,
            NpcRuntimeRegistry.RuntimeNpc runtime,
            Ref<EntityStore> npcRef,
            Ref<EntityStore> playerRef,
            Store<EntityStore> store) {
        TransformComponent npcTransform = store.getComponent(
                npcRef, TransformComponent.getComponentType());
        TransformComponent threat = playerRef == null ? null : store.getComponent(
                playerRef, TransformComponent.getComponentType());
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npcTransform == null || threat == null || npc == null) {
            return NpcActionResult.failure("THREAT_UNAVAILABLE",
                    "The focused player is unavailable as a flee source.");
        }
        double distance = Math.max(6.0, Math.min(32.0,
                number(request.parameters(), "distance", 12.0)));
        Vector3d away = new Vector3d(npcTransform.getPosition()).sub(threat.getPosition());
        away.y = 0;
        if (away.lengthSquared() < 0.01) {
            away.set(1, 0, 0);
        }
        Vector3d target = new Vector3d(npcTransform.getPosition())
                .add(away.normalize().mul(distance));
        tasks.put(task(context, runtime, "FLEE", target, null,
                "Flee from focused player", NpcTaskState.TRAVELING));
        npc.setLeashPoint(target);
        return NpcActionResult.success("NPC is fleeing %.1fm away from the focused player."
                .formatted(distance));
    }

    private NpcActionResult cancelTask(
            NpcActionRequest request,
            NpcActionContext context,
            NpcRuntimeRegistry.RuntimeNpc runtime,
            Ref<EntityStore> npcRef,
            Store<EntityStore> store) {
        String type = string(request.parameters(), "taskType");
        if (type.isBlank()) {
            return NpcActionResult.failure("TASK_TYPE_REQUIRED", "taskType is required.");
        }
        tasks.cancelType(context.profile().id(), type, "Cancelled by request.");
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        TransformComponent transform = store.getComponent(npcRef, TransformComponent.getComponentType());
        if (npc != null && transform != null) {
            npc.getPathManager().setTransientPath(null);
            npc.setLeashPoint(new Vector3d(transform.getPosition()));
        }
        return NpcActionResult.success("Cancelled active " + type + " tasks.");
    }

    private NpcActionResult waitHere(
            NpcActionRequest request,
            NpcActionContext context,
            NpcRuntimeRegistry.RuntimeNpc runtime,
            Ref<EntityStore> npcRef,
            Store<EntityStore> store) {
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        TransformComponent transform = store.getComponent(npcRef, TransformComponent.getComponentType());
        if (npc == null || transform == null) {
            return NpcActionResult.failure("ENTITY_STATE_MISSING", "NPC position is missing.");
        }
        npc.getPathManager().setTransientPath(null);
        npc.setLeashPoint(new Vector3d(transform.getPosition()));
        tasks.put(task(context, runtime, "WAIT", transform.getPosition(), null,
                "Wait here", NpcTaskState.WAITING));
        return NpcActionResult.success("Mara is waiting at her current position.");
    }

    private static NpcActionResult takeItem(
            NpcActionRequest request,
            Ref<EntityStore> npcRef,
            Ref<EntityStore> playerRef,
            Store<EntityStore> store) {
        if (!near(npcRef, playerRef, store)) {
            return NpcActionResult.failure("PLAYER_NOT_NEARBY",
                    "The player moved out of item-transfer range.");
        }
        InventoryComponent.Hotbar hotbar = store.getComponent(
                playerRef, InventoryComponent.Hotbar.getComponentType());
        short activeSlot = hotbar == null ? -1 : hotbar.getActiveSlot();
        ItemStack held = hotbar == null || activeSlot < 0
                || activeSlot >= hotbar.getInventory().getCapacity()
                ? null : hotbar.getInventory().getItemStack(activeSlot);
        if (hotbar == null || ItemStack.isEmpty(held)) {
            return NpcActionResult.failure("HELD_ITEM_STALE",
                    "The player is no longer holding an item.");
        }
        int quantity = Math.max(1, Math.min(integer(request.parameters(), "quantity", 1),
                held.getQuantity()));
        CombinedItemContainer npcInventory = InventoryComponent.getCombined(
                store, npcRef, InventoryComponent.STORAGE_HOTBAR_BACKPACK);
        ItemStack transfer = held.withQuantity(quantity);
        if (!npcInventory.canAddItemStack(transfer)) {
            return NpcActionResult.failure("NPC_INVENTORY_FULL",
                    "Mara has no capacity for " + transfer + ".");
        }
        MoveTransaction<ItemStackTransaction> moved = hotbar.getInventory()
                .moveItemStackFromSlot(activeSlot, quantity,
                        npcInventory, true, true);
        if (!moved.succeeded()) {
            return NpcActionResult.failure("TRANSFER_REJECTED",
                    "The authoritative inventory move was rejected.");
        }
        return NpcActionResult.success("Transferred " + transfer.getItemId() + " x"
                + quantity + " from the player's held slot to Mara.");
    }

    private static NpcActionResult giveItem(
            NpcActionRequest request,
            Ref<EntityStore> npcRef,
            Ref<EntityStore> playerRef,
            Store<EntityStore> store) {
        if (!near(npcRef, playerRef, store)) {
            return NpcActionResult.failure("PLAYER_NOT_NEARBY",
                    "The player moved out of item-transfer range.");
        }
        String requested = string(request.parameters(), "itemId");
        int quantity = Math.max(1, integer(request.parameters(), "quantity", 1));
        CombinedItemContainer source = InventoryComponent.getCombined(
                store, npcRef, InventoryComponent.STORAGE_HOTBAR_BACKPACK);
        CombinedItemContainer destination = InventoryComponent.getCombined(
                store, playerRef, InventoryComponent.STORAGE_HOTBAR_BACKPACK);
        short slot = findSlot(source, requested);
        if (slot < 0) {
            return NpcActionResult.failure("ITEM_NOT_OWNED",
                    "Mara does not have " + requested + ".");
        }
        ItemStack stack = source.getItemStack(slot);
        int movedQuantity = Math.min(quantity, stack.getQuantity());
        if (!destination.canAddItemStack(stack.withQuantity(movedQuantity))) {
            return NpcActionResult.failure("PLAYER_INVENTORY_FULL",
                    "The player's inventory has no capacity.");
        }
        if (!source.moveItemStackFromSlot(slot, movedQuantity, destination, true, true)
                .succeeded()) {
            return NpcActionResult.failure("TRANSFER_REJECTED",
                    "The authoritative inventory move was rejected.");
        }
        return NpcActionResult.success("Mara gave " + stack.getItemId() + " x"
                + movedQuantity + " to the player.");
    }

    private static NpcActionResult dropItem(
            NpcActionRequest request,
            Ref<EntityStore> npcRef,
            Store<EntityStore> store) {
        CombinedItemContainer inventory = InventoryComponent.getCombined(
                store, npcRef, InventoryComponent.STORAGE_HOTBAR_BACKPACK);
        String requested = string(request.parameters(), "itemId");
        short slot = findSlot(inventory, requested);
        if (slot < 0) {
            return NpcActionResult.failure("ITEM_NOT_OWNED",
                    "Mara does not have " + requested + ".");
        }
        ItemStack existing = inventory.getItemStack(slot);
        int quantity = Math.min(existing.getQuantity(),
                Math.max(1, integer(request.parameters(), "quantity", 1)));
        ItemStack dropped = existing.withQuantity(quantity);
        if (!inventory.removeItemStackFromSlot(slot, quantity).succeeded()) {
            return NpcActionResult.failure("REMOVE_REJECTED",
                    "The item could not be removed from Mara's inventory.");
        }
        Ref<EntityStore> drop = ItemUtils.dropItem(npcRef, dropped, store);
        if (drop == null) {
            inventory.addItemStack(dropped);
            return NpcActionResult.failure("DROP_REJECTED",
                    "Hytale rejected the item drop; the item was restored.");
        }
        return NpcActionResult.success("Mara dropped " + dropped.getItemId()
                + " x" + quantity + ".");
    }

    private static NpcActionResult pickUpItem(
            NpcActionRequest request,
            Ref<EntityStore> npcRef,
            Store<EntityStore> store,
            World world) {
        Ref<EntityStore> itemRef = null;
        String entityText = string(request.parameters(), "itemEntityId");
        if (!entityText.isBlank()) {
            try {
                itemRef = world.getEntityRef(UUID.fromString(entityText));
            } catch (IllegalArgumentException ignored) {
                return NpcActionResult.failure("INVALID_ENTITY_ID",
                        "itemEntityId is not a UUID.");
            }
        }
        if (itemRef == null || !itemRef.isValid()) {
            itemRef = findNearestDroppedItem(world, npcRef,
                    string(request.parameters(), "itemId"), INTERACTION_DISTANCE_SQUARED);
        }
        if (itemRef == null || !itemRef.isValid()) {
            return NpcActionResult.failure("ITEM_NOT_PERCEIVED",
                    "No matching dropped item is currently perceived within pickup range.");
        }
        ItemComponent item = store.getComponent(itemRef, ItemComponent.getComponentType());
        if (item == null || ItemStack.isEmpty(item.getItemStack()) || !near(npcRef, itemRef, store)) {
            return NpcActionResult.failure("ITEM_ENTITY_STALE",
                    "The dropped item is unavailable or out of range.");
        }
        CombinedItemContainer inventory = InventoryComponent.getCombined(
                store, npcRef, InventoryComponent.STORAGE_HOTBAR_BACKPACK);
        ItemStack stack = item.getItemStack();
        if (!inventory.canAddItemStack(stack)) {
            return NpcActionResult.failure("NPC_INVENTORY_FULL",
                    "Mara has no capacity for the dropped item.");
        }
        if (!inventory.addItemStack(stack).succeeded()) {
            return NpcActionResult.failure("PICKUP_REJECTED", "Inventory addition was rejected.");
        }
        store.removeEntity(itemRef, RemoveReason.REMOVE);
        return NpcActionResult.success("Mara picked up " + stack.getItemId()
                + " x" + stack.getQuantity() + ".");
    }

    private static NpcActionResult inspectItem(
            NpcActionRequest request,
            Ref<EntityStore> npcRef,
            Ref<EntityStore> playerRef,
            Store<EntityStore> store) {
        String requested = string(request.parameters(), "itemId");
        ItemStack stack = null;
        if (playerRef != null && playerRef.isValid() && near(npcRef, playerRef, store)) {
            stack = InventoryComponent.getItemInHand(store, playerRef);
        }
        if (ItemStack.isEmpty(stack) && !requested.isBlank()) {
            CombinedItemContainer inventory = InventoryComponent.getCombined(
                    store, npcRef, InventoryComponent.STORAGE_HOTBAR_BACKPACK);
            short slot = findSlot(inventory, requested);
            stack = slot < 0 ? null : inventory.getItemStack(slot);
        }
        if (ItemStack.isEmpty(stack)) {
            return NpcActionResult.failure("NO_ITEM_TO_INSPECT",
                    "No matching real ItemStack is available.");
        }
        return NpcActionResult.success("Inspected real ItemStack: id=" + stack.getItemId()
                + ", quantity=" + stack.getQuantity()
                + ", durability=" + stack.getDurability() + "/" + stack.getMaxDurability()
                + ", metadata=" + (stack.getMetadata() == null
                        ? "{}" : stack.getMetadata().toJson()) + ".");
    }

    private NpcActionResult bringItem(
            NpcActionRequest request,
            NpcActionContext context,
            NpcRuntimeRegistry.RuntimeNpc runtime,
            Ref<EntityStore> npcRef,
            Store<EntityStore> store,
            World world) {
        String requested = string(request.parameters(), "itemId");
        if (requested.isBlank()) {
            return NpcActionResult.failure("ITEM_REQUIRED", "itemId is required.");
        }
        Ref<EntityStore> itemRef = findNearestDroppedItem(
                world, npcRef, requested, 12.0 * 12.0);
        TransformComponent itemTransform = itemRef == null ? null : store.getComponent(
                itemRef, TransformComponent.getComponentType());
        if (itemRef == null || itemTransform == null) {
            return NpcActionResult.failure("ITEM_NOT_PERCEIVED",
                    "No matching dropped item is currently perceived within 12 meters.");
        }
        Map<String, String> data = new LinkedHashMap<>();
        data.put("phase", "PICKUP");
        data.put("itemEntityId", itemRef.getStore().getComponent(
                itemRef, com.hypixel.hytale.server.core.entity.UUIDComponent
                        .getComponentType()).getUuid().toString());
        data.put("itemId", requested);
        NpcTask task = task(context, runtime, "BRING_ITEM", itemTransform.getPosition(),
                null, "Fetch and deliver " + requested, NpcTaskState.TRAVELING)
                .withData(data);
        tasks.put(task);
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc != null) {
            npc.setLeashPoint(new Vector3d(itemTransform.getPosition()));
        }
        return NpcActionResult.success("Fetch-and-deliver task accepted for " + requested + ".");
    }

    private static NpcActionResult equipItem(
            NpcActionRequest request, Ref<EntityStore> npcRef, Store<EntityStore> store) {
        InventoryComponent.Armor armor = store.getComponent(
                npcRef, InventoryComponent.Armor.getComponentType());
        CombinedItemContainer storage = InventoryComponent.getCombined(
                store, npcRef, InventoryComponent.STORAGE_HOTBAR_BACKPACK);
        String requested = string(request.parameters(), "itemId");
        short sourceSlot = findSlot(storage, requested);
        if (armor == null) {
            return NpcActionResult.failure("EQUIPMENT_UNAVAILABLE",
                    "This NPC archetype has no armor inventory component.");
        }
        if (sourceSlot < 0) {
            return NpcActionResult.failure("ITEM_NOT_OWNED",
                    "The NPC does not own a matching item.");
        }
        ItemStack stack = storage.getItemStack(sourceSlot);
        if (!armor.getInventory().canAddItemStack(stack)
                || !storage.moveItemStackFromSlot(sourceSlot, stack.getQuantity(),
                        armor.getInventory(), true, true).succeeded()) {
            return NpcActionResult.failure("EQUIP_REJECTED",
                    "Hytale equipment filters rejected that item.");
        }
        armor.setOutdatedEquipment(true);
        return NpcActionResult.success("Equipped " + stack.getItemId() + ".");
    }

    private static NpcActionResult unequipItem(
            NpcActionRequest request, Ref<EntityStore> npcRef, Store<EntityStore> store) {
        InventoryComponent.Armor armor = store.getComponent(
                npcRef, InventoryComponent.Armor.getComponentType());
        CombinedItemContainer storage = InventoryComponent.getCombined(
                store, npcRef, InventoryComponent.STORAGE_HOTBAR_BACKPACK);
        if (armor == null) {
            return NpcActionResult.failure("EQUIPMENT_UNAVAILABLE",
                    "This NPC archetype has no armor inventory component.");
        }
        ItemContainer equipped = armor.getInventory();
        short slot = findSlot(equipped, string(request.parameters(), "itemId"));
        if (slot < 0) {
            return NpcActionResult.failure("ITEM_NOT_EQUIPPED",
                    "No matching equipped item exists.");
        }
        ItemStack stack = equipped.getItemStack(slot);
        if (!storage.canAddItemStack(stack)
                || !equipped.moveItemStackFromSlot(slot, stack.getQuantity(), storage,
                        true, true).succeeded()) {
            return NpcActionResult.failure("UNEQUIP_REJECTED",
                    "Hytale rejected the equipment transfer.");
        }
        armor.setOutdatedEquipment(true);
        return NpcActionResult.success("Unequipped " + stack.getItemId() + ".");
    }

    private NpcActionResult scheduleMeeting(
            NpcActionRequest request,
            NpcActionContext context,
            NpcRuntimeRegistry.RuntimeNpc runtime,
            Ref<EntityStore> npcRef,
            Store<EntityStore> store) {
        int hour = integer(request.parameters(), "hour", -1);
        int minute = integer(request.parameters(), "minute", 0);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return NpcActionResult.failure("INVALID_TIME",
                    "Meeting time must use hour 0-23 and minute 0-59.");
        }
        TransformComponent npcTransform = store.getComponent(
                npcRef, TransformComponent.getComponentType());
        Vector3d target = coordinates(request.parameters());
        if (target == null) {
            target = new Vector3d(context.perception().x(),
                    context.perception().y(), context.perception().z());
        }
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        if (time == null) {
            return NpcActionResult.failure("GAME_TIME_UNAVAILABLE",
                    "Hytale WorldTimeResource is unavailable.");
        }
        LocalDateTime scheduled = time.getGameDateTime()
                .withHour(hour).withMinute(minute).withSecond(0).withNano(0);
        if (!scheduled.isAfter(time.getGameDateTime())) {
            scheduled = scheduled.plusDays(1);
        }
        Instant scheduledInstant = scheduled.toInstant(ZoneOffset.UTC);
        String purpose = string(request.parameters(), "purpose");
        NpcTask task = task(context, runtime, "SCHEDULE_MEETING", target,
                scheduledInstant, purpose.isBlank() ? "Meet the requester" : purpose,
                NpcTaskState.PLANNED);
        tasks.put(task);
        return NpcActionResult.success("Meeting task " + task.taskId()
                + " scheduled for game time " + scheduled + " at "
                + "%.1f, %.1f, %.1f.".formatted(target.x, target.y, target.z));
    }

    private NpcActionResult scheduleTask(
            NpcActionRequest request,
            NpcActionContext context,
            NpcRuntimeRegistry.RuntimeNpc runtime,
            Ref<EntityStore> npcRef,
            Store<EntityStore> store) {
        String type = string(request.parameters(), "taskType").toUpperCase(Locale.ROOT);
        Set<String> supported = Set.of("GO_TO_LOCATION", "ESCORT", "SEARCH_WITH_PLAYER",
                "PATROL", "WAIT_UNTIL", "WORK_SHIFT", "RETURN_HOME");
        if (!supported.contains(type)) {
            return NpcActionResult.failure("SCHEDULE_TYPE_UNSUPPORTED",
                    "Supported future task types are " + supported + ".");
        }
        int hour = integer(request.parameters(), "hour", -1);
        int minute = integer(request.parameters(), "minute", 0);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return NpcActionResult.failure("INVALID_TIME",
                    "Future task time must use hour 0-23 and minute 0-59.");
        }
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        if (time == null) {
            return NpcActionResult.failure("GAME_TIME_UNAVAILABLE",
                    "Hytale WorldTimeResource is unavailable.");
        }
        LocalDateTime scheduled = time.getGameDateTime()
                .withHour(hour).withMinute(minute).withSecond(0).withNano(0);
        if (!scheduled.isAfter(time.getGameDateTime())) {
            scheduled = scheduled.plusDays(1);
        }
        Vector3d target = coordinates(request.parameters());
        if (!"WAIT_UNTIL".equals(type) && target == null
                && !("ESCORT".equals(type) || "SEARCH_WITH_PLAYER".equals(type))) {
            return NpcActionResult.failure("DESTINATION_REQUIRED",
                    type + " requires authoritative x/y/z coordinates.");
        }
        if (target == null) {
            target = new Vector3d(context.perception().x(), context.perception().y(),
                    context.perception().z());
        }
        String purpose = string(request.parameters(), "purpose");
        NpcTask task = task(context, runtime, type, target,
                scheduled.toInstant(ZoneOffset.UTC),
                purpose.isBlank() ? "Scheduled " + type : purpose, NpcTaskState.PLANNED);
        tasks.put(task);
        return NpcActionResult.success("Persisted " + type + " task for game time "
                + scheduled + ". The LLM request is complete and will not remain running.");
    }

    private NpcActionResult craftItem(
            NpcActionRequest request,
            NpcActionContext context,
            NpcRuntimeRegistry.RuntimeNpc runtime,
            Ref<EntityStore> npcRef,
            Store<EntityStore> store,
            World world) {
        String requested = string(request.parameters(), "requestedItem");
        if (requested.isBlank()) {
            return NpcActionResult.failure("ITEM_REQUIRED", "requestedItem is required.");
        }
        CraftingRecipe recipe = resolveRecipe(requested);
        if (recipe == null) {
            return NpcActionResult.failure("RECIPE_NOT_FOUND",
                    "No loaded Hytale recipe resolves to " + requested + ".");
        }
        TransformComponent transform = store.getComponent(
                npcRef, TransformComponent.getComponentType());
        Station station = transform == null ? null
                : findStation(world, transform.getPosition(), recipe);
        if (station == null) {
            return NpcActionResult.failure("STATION_NOT_FOUND",
                    "No loaded required crafting station is within 8 meters.");
        }
        CombinedItemContainer inventory = InventoryComponent.getCombined(
                store, npcRef, InventoryComponent.STORAGE_HOTBAR_BACKPACK);
        List<MaterialQuantity> inputs = CraftingManager.getInputMaterials(recipe, 1);
        List<ItemStack> outputs = CraftingManager.getOutputItemStacks(recipe, 1);
        if (!inventory.canRemoveMaterials(inputs)) {
            return NpcActionResult.failure("INGREDIENTS_MISSING",
                    "Mara lacks one or more real recipe ingredients: " + inputs + ".");
        }
        if (!inventory.canAddItemStacks(outputs)) {
            return NpcActionResult.failure("NPC_INVENTORY_FULL",
                    "Mara lacks capacity for recipe outputs.");
        }
        Map<String, String> data = new LinkedHashMap<>();
        data.put("phase", "CRAFT");
        data.put("recipeId", recipe.getId());
        data.put("stationId", station.blockId());
        data.put("requestedItem", requested);
        NpcTask task = task(context, runtime, "CRAFT_FOR_PLAYER", station.position(),
                null, "Craft and deliver " + requested, NpcTaskState.TRAVELING)
                .withData(data);
        tasks.put(task);
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc != null) {
            npc.getPathManager().setTransientPath(null);
            npc.setLeashPoint(new Vector3d(station.position()));
        }
        return NpcActionResult.success("Crafting task accepted using Hytale recipe "
                + recipe.getId() + " at " + station.blockId() + ".");
    }

    /** Continues physical composite tasks on the Hytale world thread. */
    public NpcTask resumeTask(
            NpcTask task,
            Ref<EntityStore> npcRef,
            Store<EntityStore> store,
            World world) {
        return switch (task.type().toUpperCase(Locale.ROOT)) {
            case "CRAFT_FOR_PLAYER" -> resumeCraft(task, npcRef, store);
            case "BRING_ITEM" -> resumeBring(task, npcRef, store, world);
            default -> task.withState(NpcTaskState.FAILED,
                    "No deterministic continuation exists for " + task.type() + ".");
        };
    }

    private NpcActionResult adjustRelationship(
            NpcActionRequest request, NpcActionContext context) {
        if (relationships == null) {
            return NpcActionResult.failure("RELATIONSHIP_STORE_UNAVAILABLE",
                    "Relationship persistence is unavailable.");
        }
        int trust = boundedDelta(request.parameters(), "trust");
        int affection = boundedDelta(request.parameters(), "affection");
        int respect = boundedDelta(request.parameters(), "respect");
        int fear = boundedDelta(request.parameters(), "fear");
        int hostility = boundedDelta(request.parameters(), "hostility");
        int obligation = boundedDelta(request.parameters(), "obligation");
        var updated = relationships.adjust(context.profile().id(),
                context.session().playerId(), context.profile().defaultDisposition(),
                trust, affection, respect, fear, hostility, obligation, Instant.now());
        return NpcActionResult.success("Relationship updated deterministically: "
                + updated.naturalSummary("the focused player"));
    }

    private NpcActionResult createObligation(
            NpcActionRequest request, NpcActionContext context) {
        if (obligations == null) {
            return NpcActionResult.failure("OBLIGATION_STORE_UNAVAILABLE",
                    "Obligation persistence is unavailable.");
        }
        int amount = integer(request.parameters(), "amount", 0);
        String unit = string(request.parameters(), "unit");
        String reason = string(request.parameters(), "reason");
        String direction = string(request.parameters(), "direction")
                .toUpperCase(Locale.ROOT);
        if (amount <= 0 || amount > 1_000_000 || unit.isBlank() || reason.isBlank()) {
            return NpcActionResult.failure("INVALID_OBLIGATION",
                    "amount must be 1-1000000 and unit/reason are required.");
        }
        UUID creditor;
        UUID debtor;
        if ("NPC_OWES_PLAYER".equals(direction)) {
            creditor = context.session().playerId();
            debtor = context.profile().id();
        } else if ("PLAYER_OWES_NPC".equals(direction)) {
            creditor = context.profile().id();
            debtor = context.session().playerId();
        } else {
            return NpcActionResult.failure("INVALID_DIRECTION",
                    "direction must be NPC_OWES_PLAYER or PLAYER_OWES_NPC.");
        }
        int recurringDays = integer(request.parameters(), "recurrenceGameDays", 0);
        ObligationRecord record = obligations.create(new ObligationRecord(
                UUID.randomUUID(), creditor, debtor, amount, unit, reason,
                recurringDays > 0, recurringDays > 0 ? recurringDays : null,
                Instant.now(), false));
        return NpcActionResult.success("Recorded obligation amount=" + record.amount()
                + " unit=" + record.unit() + " reason=" + record.reason()
                + ". No currency was fabricated or transferred.");
    }

    private NpcActionResult changeObligation(
            NpcActionRequest request, NpcActionContext context, boolean settle) {
        if (obligations == null) {
            return NpcActionResult.failure("OBLIGATION_STORE_UNAVAILABLE",
                    "Obligation persistence is unavailable.");
        }
        List<ObligationRecord> active = obligations.activeBetween(
                context.profile().id(), context.session().playerId());
        if (active.size() != 1) {
            return NpcActionResult.failure("OBLIGATION_AMBIGUOUS",
                    "Exactly one active obligation is required; found " + active.size() + ".");
        }
        ObligationRecord updated = settle
                ? obligations.settle(active.getFirst().obligationId())
                : obligations.add(active.getFirst().obligationId(),
                        Math.max(1, integer(request.parameters(), "amount", 0)));
        return NpcActionResult.success(settle
                ? "The active obligation was forgiven and marked settled."
                : "The active obligation is now " + updated.amount() + " " + updated.unit() + ".");
    }

    private static int boundedDelta(JsonObject parameters, String key) {
        return Math.max(-15, Math.min(15, integer(parameters, key, 0)));
    }

    private static NpcTask resumeCraft(
            NpcTask task, Ref<EntityStore> npcRef, Store<EntityStore> store) {
        String phase = task.data().getOrDefault("phase", "CRAFT");
        if ("DELIVER".equals(phase)) {
            Ref<EntityStore> playerRef = store.getExternalData()
                    .getRefFromUUID(task.requesterPlayerId());
            JsonObject parameters = new JsonObject();
            parameters.addProperty("itemId", task.data().get("outputItemId"));
            parameters.addProperty("quantity", Integer.parseInt(
                    task.data().getOrDefault("outputQuantity", "1")));
            NpcActionResult delivery = giveItem(
                    new NpcActionRequest("GIVE_ITEM", parameters, null), npcRef, playerRef, store);
            return task.withState(delivery.success()
                            ? NpcTaskState.COMPLETED : NpcTaskState.FAILED,
                    delivery.eventDescription());
        }
        CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(
                task.data().get("recipeId"));
        if (recipe == null) {
            return task.withState(NpcTaskState.FAILED,
                    "The persisted Hytale recipe no longer exists.");
        }
        CombinedItemContainer inventory = InventoryComponent.getCombined(
                store, npcRef, InventoryComponent.STORAGE_HOTBAR_BACKPACK);
        List<MaterialQuantity> inputs = CraftingManager.getInputMaterials(recipe, 1);
        List<ItemStack> outputs = CraftingManager.getOutputItemStacks(recipe, 1);
        if (!inventory.canRemoveMaterials(inputs)) {
            return task.withState(NpcTaskState.FAILED,
                    "Required recipe ingredients are no longer present: " + inputs + ".");
        }
        if (!inventory.canAddItemStacks(outputs)) {
            return task.withState(NpcTaskState.FAILED,
                    "NPC inventory cannot accept recipe outputs.");
        }
        if (!inventory.removeMaterials(inputs).succeeded()) {
            return task.withState(NpcTaskState.FAILED,
                    "Hytale rejected ingredient consumption.");
        }
        if (!inventory.addItemStacks(outputs).succeeded()) {
            return task.withState(NpcTaskState.FAILED,
                    "Hytale rejected recipe outputs after preflight; manual recovery is required.");
        }
        ItemStack primary = outputs.getFirst();
        Map<String, String> data = new LinkedHashMap<>(task.data());
        data.put("phase", "DELIVER");
        data.put("outputItemId", primary.getItemId());
        data.put("outputQuantity", Integer.toString(primary.getQuantity()));
        return task.withData(data).withState(NpcTaskState.TRAVELING,
                "Crafted " + primary.getItemId() + "; returning to requester.");
    }

    private static NpcTask resumeBring(
            NpcTask task,
            Ref<EntityStore> npcRef,
            Store<EntityStore> store,
            World world) {
        String phase = task.data().getOrDefault("phase", "PICKUP");
        if ("DELIVER".equals(phase)) {
            Ref<EntityStore> playerRef = store.getExternalData()
                    .getRefFromUUID(task.requesterPlayerId());
            JsonObject parameters = new JsonObject();
            parameters.addProperty("itemId", task.data().get("actualItemId"));
            parameters.addProperty("quantity", Integer.parseInt(
                    task.data().getOrDefault("quantity", "1")));
            NpcActionResult delivery = giveItem(
                    new NpcActionRequest("GIVE_ITEM", parameters, null), npcRef, playerRef, store);
            return task.withState(delivery.success()
                            ? NpcTaskState.COMPLETED : NpcTaskState.FAILED,
                    delivery.eventDescription());
        }
        UUID itemId;
        try {
            itemId = UUID.fromString(task.data().get("itemEntityId"));
        } catch (RuntimeException invalid) {
            return task.withState(NpcTaskState.FAILED,
                    "Persisted dropped-item identity is invalid.");
        }
        Ref<EntityStore> itemRef = world.getEntityRef(itemId);
        ItemComponent item = itemRef == null ? null
                : store.getComponent(itemRef, ItemComponent.getComponentType());
        if (item == null || ItemStack.isEmpty(item.getItemStack())
                || !near(npcRef, itemRef, store)) {
            return task.withState(NpcTaskState.FAILED,
                    "The dropped item moved or no longer exists.");
        }
        ItemStack stack = item.getItemStack();
        CombinedItemContainer inventory = InventoryComponent.getCombined(
                store, npcRef, InventoryComponent.STORAGE_HOTBAR_BACKPACK);
        if (!inventory.canAddItemStack(stack)
                || !inventory.addItemStack(stack).succeeded()) {
            return task.withState(NpcTaskState.FAILED,
                    "NPC inventory rejected the dropped item.");
        }
        store.removeEntity(itemRef, RemoveReason.REMOVE);
        Map<String, String> data = new LinkedHashMap<>(task.data());
        data.put("phase", "DELIVER");
        data.put("actualItemId", stack.getItemId());
        data.put("quantity", Integer.toString(stack.getQuantity()));
        return task.withData(data).withState(NpcTaskState.TRAVELING,
                "Picked up " + stack.getItemId() + "; returning to requester.");
    }

    private static CraftingRecipe resolveRecipe(String requested) {
        String needle = normalizeItem(requested);
        return CraftingRecipe.getAssetMap().getAssetMap().values().stream()
                .filter(recipe -> CraftingManager.getOutputItemStacks(recipe).stream()
                        .anyMatch(stack -> normalizeItem(stack.getItemId()).contains(needle)))
                .min(Comparator.comparing(recipe -> recipe.getId().length()))
                .orElse(null);
    }

    private static Station findStation(
            World world, Vector3d origin, CraftingRecipe recipe) {
        List<String> requiredIds = java.util.Arrays.stream(recipe.getBenchRequirement())
                .map(requirement -> requirement.id)
                .filter(value -> value != null && !value.isBlank())
                .toList();
        if (requiredIds.isEmpty()) {
            return new Station("field_crafting", new Vector3d(origin), 0);
        }
        Station nearest = null;
        int ox = (int) Math.floor(origin.x);
        int oy = (int) Math.floor(origin.y);
        int oz = (int) Math.floor(origin.z);
        for (int x = ox - 8; x <= ox + 8; x++) {
            for (int z = oz - 8; z <= oz + 8; z++) {
                WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
                if (chunk == null) {
                    continue;
                }
                for (int y = oy - 3; y <= oy + 3; y++) {
                    BlockType block = chunk.getBlockType(x, y, z);
                    if (block == null || block.getBench() == null
                            || !requiredIds.contains(block.getBench().getId())) {
                        continue;
                    }
                    Vector3d position = new Vector3d(x + 0.5, y, z + 0.5);
                    double distance = position.distance(origin);
                    if (nearest == null || distance < nearest.distance()) {
                        nearest = new Station(block.getId(), position, distance);
                    }
                }
            }
        }
        return nearest;
    }

    private static boolean near(
            Ref<EntityStore> left,
            Ref<EntityStore> right,
            Store<EntityStore> store) {
        if (left == null || right == null || !left.isValid() || !right.isValid()) {
            return false;
        }
        TransformComponent a = store.getComponent(left, TransformComponent.getComponentType());
        TransformComponent b = store.getComponent(right, TransformComponent.getComponentType());
        return a != null && b != null
                && a.getPosition().distanceSquared(b.getPosition())
                <= INTERACTION_DISTANCE_SQUARED;
    }

    private static Ref<EntityStore> findNearestDroppedItem(
            World world,
            Ref<EntityStore> npcRef,
            String requested,
            double maxDistanceSquared) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        TransformComponent origin = store.getComponent(
                npcRef, TransformComponent.getComponentType());
        if (origin == null) {
            return null;
        }
        String needle = normalizeItem(requested);
        java.util.concurrent.atomic.AtomicReference<Ref<EntityStore>> best =
                new java.util.concurrent.atomic.AtomicReference<>();
        double[] bestDistance = { maxDistanceSquared };
        Query<EntityStore> query = Archetype.of(
                TransformComponent.getComponentType(), ItemComponent.getComponentType());
        store.forEachChunk(query, (chunk, commandBuffer) -> {
            for (int index = 0; index < chunk.size(); index++) {
                ItemComponent item = chunk.getComponent(index, ItemComponent.getComponentType());
                TransformComponent transform = chunk.getComponent(
                        index, TransformComponent.getComponentType());
                if (item == null || transform == null || ItemStack.isEmpty(item.getItemStack())
                        || (!needle.isBlank() && !normalizeItem(
                                item.getItemStack().getItemId()).contains(needle))) {
                    continue;
                }
                double distance = origin.getPosition().distanceSquared(transform.getPosition());
                if (distance <= bestDistance[0]) {
                    bestDistance[0] = distance;
                    best.set(chunk.getReferenceTo(index));
                }
            }
        });
        return best.get();
    }

    private static short findSlot(ItemContainer inventory, String requested) {
        String needle = normalizeItem(requested);
        for (short slot = 0; slot < inventory.getCapacity(); slot++) {
            ItemStack stack = inventory.getItemStack(slot);
            if (!ItemStack.isEmpty(stack)
                    && normalizeItem(stack.getItemId()).contains(needle)) {
                return slot;
            }
        }
        return -1;
    }

    private static NpcTask task(
            NpcActionContext context,
            NpcRuntimeRegistry.RuntimeNpc runtime,
            String type,
            Vector3d target,
            Instant scheduled,
            String purpose,
            NpcTaskState state) {
        return new NpcTask(UUID.randomUUID(), context.profile().id(),
                context.session().playerId(), type, runtime.worldId(),
                target == null ? null : target.x,
                target == null ? null : target.y,
                target == null ? null : target.z,
                scheduled, purpose, state, Instant.now(), null);
    }

    private static Vector3d coordinates(JsonObject parameters) {
        double x = number(parameters, "x", Double.NaN);
        double y = number(parameters, "y", Double.NaN);
        double z = number(parameters, "z", Double.NaN);
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                ? new Vector3d(x, y, z) : null;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        try {
            return value == null || value.isJsonNull() ? fallback : value.getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double number(JsonObject object, String key, double fallback) {
        JsonElement value = object.get(key);
        try {
            return value == null || value.isJsonNull() ? fallback : value.getAsDouble();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        try {
            return value == null || value.isJsonNull() ? "" : value.getAsString().strip();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static String normalizeItem(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
    }

    private static boolean waitHereIntent(String message) {
        String value = message == null ? "" : message.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9' ]", " ").replaceAll("\\s+", " ").strip();
        return value.contains("wait here") || value.contains("stay here")
                || value.contains("hold position");
    }

    private static JsonObject objectSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    private static JsonObject coordinatesSchema() {
        return schema(property("x", "number", true),
                property("y", "number", true), property("z", "number", true));
    }

    private static JsonObject schema(Property... properties) {
        JsonObject schema = objectSchema();
        JsonObject values = schema.getAsJsonObject("properties");
        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        for (Property property : properties) {
            JsonObject definition = new JsonObject();
            definition.addProperty("type", property.type());
            values.add(property.name(), definition);
            if (property.required()) {
                required.add(property.name());
            }
        }
        if (!required.isEmpty()) {
            schema.add("required", required);
        }
        return schema;
    }

    private static Property property(String name, String type, boolean required) {
        return new Property(name, type, required);
    }

    private record Property(String name, String type, boolean required) {
    }

    private record Station(String blockId, Vector3d position, double distance) {
    }
}
