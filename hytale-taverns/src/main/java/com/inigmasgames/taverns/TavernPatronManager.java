package com.inigmasgames.taverns;

import com.hypixel.hytale.builtin.mounts.BlockMountAPI;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.modules.physics.util.PhysicsMath;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.Pair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Server-authoritative lifecycle for first-generation Tavern patrons. */
final class TavernPatronManager {
    static final String PATRON_ROLE_ID = "Tavern_Patron";
    static final float ORDER_SECONDS = 60.0f;
    static final float EATING_SECONDS = 60.0f;
    static final float EXIT_DESPAWN_SECONDS = 20.0f;
    static final float SPAWN_INTERVAL_SECONDS = 15.0f;
    static final int MAX_PATRONS_PER_TAVERN = 4;

    private static final float MIN_REST_DURATION_SECONDS = 3.0f;
    private static final float MAX_REST_DURATION_SECONDS = 5.0f;
    private static final float MIN_EATING_DURATION_SECONDS = 4.0f;
    private static final float MAX_EATING_DURATION_SECONDS = 8.0f;
    private static final double GREETING_DISTANCE_SQUARED = 4.5 * 4.5;
    private static final float GREETING_SECONDS = 2.0f;
    private static final float ORDER_LEAD_IN_SECONDS = 1.25f;
    private static final float GREETING_COOLDOWN_SECONDS = 20.0f;
    private static final float MEAL_ACKNOWLEDGEMENT_SECONDS = 2.0f;
    private static final float DEPARTURE_DIALOGUE_SECONDS = 4.0f;
    private static final float BITE_EQUIPMENT_LEAD_SECONDS = 0.20f;
    private static final String GREETING_TARGET_SLOT = "GreetingTarget";
    private static final String[] GREETINGS = {
            "Hello!",
            "Yo!",
            "Take my order, now!",
            "Hehe, hiya!",
            "...",
            "Hmm, what to order...",
            "Man, I'm starving!",
            "Server...!"
    };
    private static final String[] ORDER_FOLLOW_UPS = {
            "I'll have...",
            "This looks good!",
            "May I have...",
            "Please make me a...",
            "Make me this, now!!!"
    };
    private static final String[] CORRECT_MEAL_LINES = {
            "Oh heck yeah!",
            "Thank you so much!",
            "This looks amazing!",
            "Itadakimasu",
            "Thank you!",
            "...",
            "This better be good.",
            "Exciting...",
            "May I have some paper napkins?",
            "Ketchup please?"
    };
    private static final String[] FAILED_ORDER_LINES = {
            "Faster service next time.",
            "Server, I'd like to speak to your manager!",
            "I've had better experiences at home, and that's not saying much...",
            "What a trash experience.",
            "What a dump.",
            "The food here is about as appetizing as the mole on my moms right cheek.",
            "What an abhor ant experience!",
            "I'm not coming back.",
            "Barf!",
            "Wait, gratuity included?!"
    };
    private static final String[] SATISFIED_MEAL_LINES = {
            "Compliments to the chef!",
            "I was actually taking notes on how this establishment made such a great dish!",
            "I've already told my friends about this place and it's amazing!",
            "OOooohhhhggga boooga!",
            "Better than my mom's home cooking!",
            "5-star experience!",
            "This place always delivers!",
            "This was great, do you do take out?",
            "Mom?",
            "Father has summoned me forth to mow ye old grass, I must depart now. Excellent food!"
    };

    private static final double ARRIVAL_DISTANCE_SQUARED = 1.75 * 1.75;
    private static final double STAGE_DISTANCE_SQUARED = 0.8 * 0.8;
    private static final double EXIT_DISTANCE_SQUARED = 1.5 * 1.5;
    private static final float APPROACH_TIMEOUT_SECONDS = 35.0f;
    private static final float STUCK_REPATH_SECONDS = 3.0f;
    private static final float ROUTE_RETRY_SECONDS = 7.0f;
    private static final String SIT_ANIMATION = "Sit";
    private static final int APPEARANCE_ATTEMPTS = 8;
    private static final String[] HUMAN_SKIN_TONES = {
            "06", "05", "11", "04", "15", "02"
    };
    private static final String PARTICLE_STUNNED = "Taverns_Stunned";
    private static final String PARTICLE_ANGRY = "Angry";
    private static final String PARTICLE_HEARTS = "Hearts";

    private final TavernRepository repository;
    private final TableServingManager servingManager;
    private final PreparedFoodRegistry preparedFoods;
    private final ComponentType<EntityStore, TavernPatronComponent> patronType;
    private final Consumer<String> info;
    private final Consumer<Throwable> error;
    private final TavernDoorOperator doorOperator;
    private final PatronParticleController particles;
    private final Random random = new Random();
    private final Map<Ref<EntityStore>, PatronSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, List<Entrance>> entrances = new ConcurrentHashMap<>();
    private final Map<UUID, Float> spawnCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastWorldTicks = new ConcurrentHashMap<>();
    private final Set<UUID> pendingTaverns = ConcurrentHashMap.newKeySet();
    private final Set<SeatKey> reservedSeats = ConcurrentHashMap.newKeySet();
    private final Set<TableServingManager.TableKey> reservedTables = ConcurrentHashMap.newKeySet();
    private final Set<UUID> forcedClosures = ConcurrentHashMap.newKeySet();
    private volatile List<String> foodItemIds = List.of();

    TavernPatronManager(
            TavernRepository repository,
            TableServingManager servingManager,
            PreparedFoodRegistry preparedFoods,
            ComponentType<EntityStore, TavernPatronComponent> patronType,
            Consumer<String> info,
            Consumer<Throwable> error) {
        this.repository = repository;
        this.servingManager = servingManager;
        this.preparedFoods = preparedFoods;
        this.patronType = patronType;
        this.info = info;
        this.error = error;
        this.doorOperator = new TavernDoorOperator(error);
        this.particles = new PatronParticleController(info, error);
    }

    synchronized int resolveFoodItems() {
        foodItemIds = preparedFoods.definitions().stream()
                .filter(definition -> isLoadedItem(definition.baseFoodId()))
                .filter(definition -> isLoadedItem(definition.preparedFoodId()))
                .map(PreparedFoodRegistry.Definition::baseFoodId)
                .toList();
        info.accept("Registered " + foodItemIds.size()
                + " Tavern Prepared food(s) for patron orders.");
        return foodItemIds.size();
    }

    private static boolean isLoadedItem(String itemId) {
        Item item = Item.getAssetMap().getAsset(itemId);
        return item != null && item != Item.UNKNOWN;
    }

    synchronized void initializeCore(CoreRecord core, World world) {
        List<Entrance> detected = detectEntrances(core, world);
        if (detected.isEmpty()) {
            entrances.remove(core.tavernId());
        } else {
            entrances.put(core.tavernId(), detected);
        }
    }

    synchronized void markLayoutDirty(UUID worldId, int x, int y, int z) {
        repository.findPrimaryCoreContaining(worldId, x, y, z)
                .ifPresent(core -> entrances.remove(core.tavernId()));
    }

    synchronized void tickWorld(
            float delta,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer) {
        World world = store.getExternalData().getWorld();
        UUID worldId = world.getWorldConfig().getUuid();
        Long previousTick = lastWorldTicks.put(worldId, world.getTick());
        if (previousTick != null && previousTick == world.getTick()) {
            return;
        }

        for (PatronSession session : new ArrayList<>(sessions.values())) {
            if (session.worldId.equals(worldId)) {
                tickSession(delta, session, world, commandBuffer);
            }
        }
        forcedClosures.removeIf(tavernId -> activePatronCount(tavernId) == 0);

        for (TavernRecord tavern : repository.allTaverns()) {
            if (!tavern.worldId().equals(worldId) || tavern.status() != TavernStatus.OPEN) {
                continue;
            }
            float cooldown = spawnCooldowns.getOrDefault(tavern.tavernId(), 0.0f) - delta;
            spawnCooldowns.put(tavern.tavernId(), cooldown);
            if (cooldown > 0.0f
                    || pendingTaverns.contains(tavern.tavernId())
                    || activePatronCount(tavern.tavernId()) >= MAX_PATRONS_PER_TAVERN) {
                continue;
            }
            Optional<CoreRecord> foundCore = repository.findPrimaryCore(tavern.tavernId());
            if (foundCore.isEmpty()) {
                continue;
            }
            CoreRecord core = foundCore.get();
            List<Entrance> availableEntrances = entrances.computeIfAbsent(
                    tavern.tavernId(), ignored -> detectEntrances(core, world));
            if (availableEntrances.isEmpty()) {
                spawnCooldowns.put(tavern.tavernId(), SPAWN_INTERVAL_SECONDS);
                continue;
            }
            Optional<Reservation> reservation = findReservation(core, world);
            if (reservation.isEmpty()) {
                spawnCooldowns.put(tavern.tavernId(), SPAWN_INTERVAL_SECONDS);
                continue;
            }
            scheduleSpawn(
                    tavern, availableEntrances, reservation.get(), world, commandBuffer);
            spawnCooldowns.put(tavern.tavernId(), SPAWN_INTERVAL_SECONDS);
        }
    }

    synchronized void onPatronEntityAdded(
            Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer) {
        if (!sessions.containsKey(ref)) {
            commandBuffer.tryRemoveEntity(ref, RemoveReason.REMOVE);
        }
    }

    synchronized void onPatronEntityRemoved(Ref<EntityStore> ref) {
        PatronSession session = sessions.remove(ref);
        if (session != null && !session.removed) {
            session.removed = true;
            releaseReservation(session);
        }
    }

    synchronized void ownerDisconnected(UUID ownerId) {
        repository.findByOwner(ownerId).ifPresent(tavern -> {
            if (tavern.status() == TavernStatus.OPEN) {
                repository.updateTavern(tavern.withStatus(TavernStatus.CLOSED));
                forcedClosures.add(tavern.tavernId());
                spawnCooldowns.remove(tavern.tavernId());
                pendingTaverns.remove(tavern.tavernId());
            }
        });
    }

    synchronized void shutdown() {
        for (PatronSession session : new ArrayList<>(sessions.values())) {
            if (!completeSession(session)) {
                continue;
            }
            Ref<EntityStore> patronRef = session.patronRef;
            World world = patronRef.isValid()
                    ? patronRef.getStore().getExternalData().getWorld()
                    : null;
            if (world != null) {
                world.execute(() -> {
                    if (patronRef.isValid()) {
                        patronRef.getStore().removeEntity(patronRef, RemoveReason.REMOVE);
                    }
                });
            }
        }
        sessions.clear();
        pendingTaverns.clear();
        forcedClosures.clear();
    }

    private void scheduleSpawn(
            TavernRecord tavern,
            List<Entrance> availableEntrances,
            Reservation reservation,
            World world,
            CommandBuffer<EntityStore> commandBuffer) {
        Entrance entrance = availableEntrances.get(0);
        SeatKey seatKey = new SeatKey(tavern.worldId(), reservation.chair());
        pendingTaverns.add(tavern.tavernId());
        reservedSeats.add(seatKey);
        reservedTables.add(reservation.table());
        commandBuffer.run(store -> {
            synchronized (TavernPatronManager.this) {
                Ref<EntityStore> spawnedRef = null;
                PatronSession spawnedSession = null;
                try {
                    if (repository.findById(tavern.tavernId())
                            .map(TavernRecord::status)
                            .orElse(TavernStatus.CLOSED) != TavernStatus.OPEN) {
                        releaseReservation(seatKey, reservation.table());
                        return;
                    }
                    Pair<Ref<EntityStore>, INonPlayerCharacter> spawned = NPCPlugin.get().spawnNPC(
                            store,
                            PATRON_ROLE_ID,
                            null,
                            entrance.spawn(),
                            Rotation3f.lookAt(new Vector3d(entrance.door()).sub(entrance.spawn())));
                    if (spawned == null || spawned.first() == null
                            || !(spawned.second() instanceof NPCEntity npc)) {
                        releaseReservation(seatKey, reservation.table());
                        return;
                    }
                    Ref<EntityStore> patronRef = spawned.first();
                    spawnedRef = patronRef;
                    PatronSession session = new PatronSession(
                            patronRef,
                            tavern.tavernId(),
                            tavern.worldId(),
                            entrance,
                            List.copyOf(availableEntrances),
                            reservation,
                            seatKey);
                    spawnedSession = session;
                    sessions.put(patronRef, session);
                    store.putComponent(patronRef, patronType,
                            new TavernPatronComponent(tavern.tavernId()));
                    applyRandomHumanAppearance(patronRef, store);
                    store.putComponent(patronRef, Nameplate.getComponentType(),
                            new Nameplate("Tavern Patron"));
                    setTravelTarget(session, npc, TravelStage.EXTERIOR_APPROACH);
                } catch (RuntimeException exception) {
                    if (spawnedSession == null || !completeSession(spawnedSession)) {
                        releaseReservation(seatKey, reservation.table());
                    }
                    if (spawnedRef != null && spawnedRef.isValid()) {
                        store.removeEntity(spawnedRef, RemoveReason.REMOVE);
                    }
                    error.accept(exception);
                } finally {
                    pendingTaverns.remove(tavern.tavernId());
                }
            }
        });
    }

    private void applyRandomHumanAppearance(
            Ref<EntityStore> patronRef,
            Store<EntityStore> store) {
        CosmeticsModule cosmetics = CosmeticsModule.get();
        for (int attempt = 0; attempt < APPEARANCE_ATTEMPTS; attempt++) {
            PlayerSkin skin = cosmetics.generateRandomSkin(random);
            String skinTone = HUMAN_SKIN_TONES[
                    random.nextInt(HUMAN_SKIN_TONES.length)];
            String hairColor = randomHairColor();
            String body = partId(skin.bodyCharacteristic);
            if (!"Default".equals(body) && !"Muscular".equals(body)) {
                body = random.nextBoolean() ? "Default" : "Muscular";
            }
            skin.bodyCharacteristic = body + "." + skinTone;
            skin.ears = "Default";
            skin.haircut = withGradient(skin.haircut, hairColor);
            skin.eyebrows = withGradient(
                    skin.eyebrows == null ? "Medium" : skin.eyebrows,
                    hairColor);
            skin.facialHair = withGradient(skin.facialHair, hairColor);
            skin.cape = null;
            skin.headAccessory = null;
            skin.skinFeature = null;
            try {
                // Validation must happen before changing any tracked component.
                // A null ModelComponent is fatal to Hytale's entity tracker.
                cosmetics.validateSkin(skin);
                Model model = cosmetics.createModel(skin);
                if (model == null) {
                    continue;
                }
                store.putComponent(patronRef, PlayerSkinComponent.getComponentType(),
                        new PlayerSkinComponent(skin));
                store.putComponent(patronRef, ModelComponent.getComponentType(),
                        new ModelComponent(model));
                store.putComponent(patronRef, BoundingBox.getComponentType(),
                        new BoundingBox(new Box(model.getBoundingBox())));
                return;
            } catch (CosmeticsModule.InvalidSkinException exception) {
                // Try a different vanilla combination. The NPC keeps its valid
                // base Player appearance until a complete model is ready.
            }
        }
        info.accept("Kept the base human appearance for a Tavern patron because "
                + "no randomized cosmetic combination passed validation.");
    }

    private String randomHairColor() {
        List<String> colors = new ArrayList<>(CosmeticsModule.get()
                .getRegistry()
                .getGradientSets()
                .get("Hair")
                .getGradients()
                .keySet());
        return colors.get(random.nextInt(colors.size()));
    }

    private static String partId(String selection) {
        if (selection == null || selection.isBlank()) {
            return "Default";
        }
        int separator = selection.indexOf('.');
        return separator < 0 ? selection : selection.substring(0, separator);
    }

    static String withGradient(String selection, String gradient) {
        if (selection == null || selection.isBlank()) {
            return null;
        }
        String[] parts = selection.split("\\.");
        if (parts.length == 1) {
            return selection + "." + gradient;
        }
        // Cosmetic keys are Id.Gradient[.Variant]. R043 replaced the last
        // segment, turning e.g. Dreadlocks.BrownSemiDark.Dreadlocks02 into
        // Dreadlocks.BrownSemiDark.Red, which is not a registered haircut.
        parts[1] = gradient;
        return String.join(".", parts);
    }

    private void tickSession(
            float delta,
            PatronSession session,
            World world,
            CommandBuffer<EntityStore> commandBuffer) {
        if (session.removed) {
            return;
        }
        if (!session.patronRef.isValid()) {
            forceCleanup(session, commandBuffer);
            return;
        }
        NPCEntity npc = commandBuffer.getComponent(
                session.patronRef, NPCEntity.getComponentType());
        TransformComponent transform = commandBuffer.getComponent(
                session.patronRef, TransformComponent.getComponentType());
        if (npc == null || transform == null) {
            forceCleanup(session, commandBuffer);
            return;
        }

        if (forcedClosures.contains(session.tavernId)) {
            forceCleanup(session, commandBuffer);
            return;
        }

        TavernStatus status = repository.findById(session.tavernId)
                .map(TavernRecord::status)
                .orElse(TavernStatus.CLOSED);
        if (status != TavernStatus.OPEN && session.phase != Phase.LEAVING) {
            beginLeaving(session, npc, transform, commandBuffer, false);
        }

        if (session.phase != Phase.LEAVING
                && !isReservationValid(session, world)) {
            particles.spawnEmotion(
                    session.patronRef, PARTICLE_ANGRY, world, commandBuffer);
            beginLeaving(session, npc, transform, commandBuffer, true);
        }

        if (session.phase == Phase.EATING) {
            Optional<TableServingManager.ServingSnapshot> serving =
                    servingManager.servingAt(session.reservation.table());
            if (serving.isEmpty()
                    || !PreparedMeal.matchesBase(
                            serving.get().foodStack(),
                            session.requestedItemId,
                            preparedFoods)) {
                particles.spawnEmotion(
                        session.patronRef, PARTICLE_ANGRY, world, commandBuffer);
                beginLeaving(session, npc, transform, commandBuffer, true);
            }
        }

        switch (session.phase) {
            case ENTERING -> tickEntering(
                    delta, session, npc, transform, world, commandBuffer);
            case ORDERING -> tickOrdering(
                    delta, session, npc, transform, world, commandBuffer);
            case EATING -> tickEating(
                    delta, session, npc, transform, world, commandBuffer);
            case LEAVING -> tickLeaving(
                    delta, session, npc, transform, world, commandBuffer);
        }
    }

    private void tickEntering(
            float delta,
            PatronSession session,
            NPCEntity npc,
            TransformComponent transform,
            World world,
            CommandBuffer<EntityStore> commandBuffer) {
        session.phaseRemaining -= delta;
        tickRouteProgress(delta, session, npc, transform);
        if (session.travelStage == TravelStage.EXTERIOR_APPROACH
                && reached(transform, session.entrance.outside())) {
            if (doorOperator.openIfClosed(
                    world,
                    commandBuffer,
                    session.patronRef,
                    session.entrance.doorBlock())) {
                setTravelTarget(session, npc, TravelStage.CROSSING_IN);
            }
            return;
        }
        if (session.travelStage == TravelStage.CROSSING_IN) {
            if (transform.getPosition().distanceSquared(session.entrance.door())
                    <= 2.5 * 2.5) {
                doorOperator.openIfClosed(
                        world,
                        commandBuffer,
                        session.patronRef,
                        session.entrance.doorBlock());
            }
            if (reached(transform, session.entrance.inside())) {
                setTravelTarget(session, npc, TravelStage.TO_SEAT);
            }
            return;
        }
        if (session.travelStage == TravelStage.TO_SEAT
                && transform.getPosition().distanceSquared(session.reservation.approach())
                        <= ARRIVAL_DISTANCE_SQUARED) {
            BlockMountAPI.BlockMountResult result = BlockMountAPI.mountOnBlock(
                    session.patronRef,
                    commandBuffer,
                    session.reservation.chair(),
                    transform.getPosition());
            if (result instanceof BlockMountAPI.Mounted) {
                stopNavigation(npc, transform);
                npc.playAnimation(
                        session.patronRef,
                        AnimationSlot.Status,
                        SIT_ANIMATION,
                        commandBuffer);
                beginOrdering(session, commandBuffer);
                return;
            }
        }
        if (session.phaseRemaining <= 0.0f) {
            beginLeaving(session, npc, transform, commandBuffer, false);
        }
    }

    private void beginOrdering(
            PatronSession session,
            CommandBuffer<EntityStore> commandBuffer) {
        if (foodItemIds.isEmpty()) {
            NPCEntity npc = commandBuffer.getComponent(
                    session.patronRef, NPCEntity.getComponentType());
            TransformComponent transform = commandBuffer.getComponent(
                    session.patronRef, TransformComponent.getComponentType());
            if (npc != null && transform != null) {
                beginLeaving(session, npc, transform, commandBuffer, false);
            }
            return;
        }
        session.phase = Phase.ORDERING;
        session.phaseRemaining = ORDER_SECONDS;
        session.requestedItemId = foodItemIds.get(random.nextInt(foodItemIds.size()));
        session.lastCountdownSecond = -1;
        session.lastSeenFoodRef = null;
        session.orderParticlePulseRemaining = 0.0f;
        session.greetingPhase = GreetingPhase.WAITING;
        session.greetingRemaining = 0.0f;
        session.greetingCooldownRemaining = 0.0f;
        setNameplate(session.patronRef, "Tavern Patron", commandBuffer);
    }

    private void tickOrdering(
            float delta,
            PatronSession session,
            NPCEntity npc,
            TransformComponent transform,
            World world,
            CommandBuffer<EntityStore> commandBuffer) {
        if (session.mealAcknowledgementActive) {
            session.mealAcknowledgementRemaining = Math.max(
                    0.0f, session.mealAcknowledgementRemaining - delta);
            if (session.mealAcknowledgementRemaining <= 0.0f) {
                session.mealAcknowledgementActive = false;
                beginEating(session, commandBuffer);
            }
            return;
        }
        if (!tickGreeting(delta, session, npc, transform, world, commandBuffer)) {
            return;
        }
        session.phaseRemaining = Math.max(0.0f, session.phaseRemaining - delta);
        updateOrderDisplay(session, commandBuffer);
        session.orderParticlePulseRemaining -= delta;
        if (session.orderParticlePulseRemaining <= 0.0f) {
            particles.pulseOrder(
                    session.patronRef,
                    session.requestedItemId,
                    world,
                    commandBuffer);
            session.orderParticlePulseRemaining =
                    PatronParticleController.ORDER_PULSE_SECONDS;
        }

        Optional<TableServingManager.ServingSnapshot> serving =
                servingManager.servingAt(session.reservation.table());
        if (serving.isPresent()) {
            TableServingManager.ServingSnapshot snapshot = serving.get();
            Optional<PreparedMeal.Details> prepared =
                    PreparedMeal.inspect(snapshot.foodStack(), preparedFoods);
            if (prepared.isPresent()
                    && session.requestedItemId.equals(prepared.get().baseFoodId())) {
                if (prepared.get().isFresh(System.currentTimeMillis())) {
                    beginMealAcknowledgement(session, npc, commandBuffer);
                    return;
                } else {
                    // Correct but expired meals stay physically present and are
                    // rejected without completing the order or replaying Stunned.
                    session.lastSeenFoodRef = snapshot.foodRef();
                }
            } else if (!snapshot.foodRef().equals(session.lastSeenFoodRef)) {
                session.lastSeenFoodRef = snapshot.foodRef();
                particles.spawnEmotion(
                        session.patronRef, PARTICLE_STUNNED, world, commandBuffer);
            }
        } else {
            session.lastSeenFoodRef = null;
        }

        if (session.phaseRemaining <= 0.0f) {
            particles.spawnEmotion(
                    session.patronRef, PARTICLE_ANGRY, world, commandBuffer);
            beginLeaving(session, npc, transform, commandBuffer, true);
            showDepartureDialogue(
                    session,
                    FAILED_ORDER_LINES[random.nextInt(FAILED_ORDER_LINES.length)],
                    "Unhappy Patron",
                    commandBuffer);
        }
    }

    private void beginMealAcknowledgement(
            PatronSession session,
            NPCEntity npc,
            CommandBuffer<EntityStore> commandBuffer) {
        clearGreetingTarget(session, npc, commandBuffer);
        servingManager.lockForPatron(session.reservation.table());
        session.mealAcknowledgementActive = true;
        session.mealAcknowledgementRemaining = MEAL_ACKNOWLEDGEMENT_SECONDS;
        setNameplate(
                session.patronRef,
                CORRECT_MEAL_LINES[random.nextInt(CORRECT_MEAL_LINES.length)],
                commandBuffer);
    }

    private boolean tickGreeting(
            float delta,
            PatronSession session,
            NPCEntity npc,
            TransformComponent transform,
            World world,
            CommandBuffer<EntityStore> commandBuffer) {
        session.greetingCooldownRemaining = Math.max(
                0.0f, session.greetingCooldownRemaining - delta);
        if (session.greetingPhase == GreetingPhase.COMPLETE) {
            return true;
        }
        if (session.greetingPhase != GreetingPhase.WAITING) {
            applyGreetingHeadTracking(session, npc, transform, commandBuffer);
        }
        if (session.greetingPhase == GreetingPhase.WAITING) {
            Ref<EntityStore> player = nearestConversationPlayer(
                    transform.getPosition(), world, commandBuffer);
            if (player == null || session.greetingCooldownRemaining > 0.0f) {
                return false;
            }
            session.greetingPhase = GreetingPhase.GREETING;
            session.greetingRemaining = GREETING_SECONDS;
            session.greetingCooldownRemaining = GREETING_COOLDOWN_SECONDS;
            session.greetingTarget = player;
            if (npc.getRole() != null) {
                npc.getRole().setMarkedTarget(GREETING_TARGET_SLOT, player);
                Ref<EntityStore> boundTarget = npc.getRole().getMarkedEntitySupport()
                        .getMarkedEntityRef(GREETING_TARGET_SLOT);
                if (boundTarget != null && boundTarget.isValid()) {
                    session.greetingTarget = boundTarget;
                }
            }
            applyGreetingHeadTracking(session, npc, transform, commandBuffer);
            setNameplate(
                    session.patronRef,
                    GREETINGS[random.nextInt(GREETINGS.length)],
                    commandBuffer);
            return false;
        }

        session.greetingRemaining = Math.max(0.0f, session.greetingRemaining - delta);
        if (session.greetingRemaining > 0.0f) {
            return false;
        }
        if (session.greetingPhase == GreetingPhase.GREETING) {
            session.greetingPhase = GreetingPhase.ORDER_LEAD_IN;
            session.greetingRemaining = ORDER_LEAD_IN_SECONDS;
            setNameplate(
                    session.patronRef,
                    ORDER_FOLLOW_UPS[random.nextInt(ORDER_FOLLOW_UPS.length)],
                    commandBuffer);
            return false;
        }

        session.greetingPhase = GreetingPhase.COMPLETE;
        clearGreetingTarget(session, npc, commandBuffer);
        session.lastCountdownSecond = -1;
        session.orderParticlePulseRemaining = 0.0f;
        updateOrderDisplay(session, commandBuffer);
        return true;
    }

    private static Ref<EntityStore> nearestConversationPlayer(
            Vector3d patronPosition,
            World world,
            CommandBuffer<EntityStore> commandBuffer) {
        Ref<EntityStore> nearest = null;
        double nearestDistance = GREETING_DISTANCE_SQUARED;
        for (PlayerRef player : world.getPlayerRefs()) {
            if (player == null || !player.isValid()) {
                continue;
            }
            Ref<EntityStore> playerRef = player.getReference();
            if (playerRef == null || !playerRef.isValid()) {
                continue;
            }
            TransformComponent playerTransform = commandBuffer.getComponent(
                    playerRef, TransformComponent.getComponentType());
            if (playerTransform == null) {
                continue;
            }
            double distance = patronPosition.distanceSquared(playerTransform.getPosition());
            if (distance <= nearestDistance) {
                nearestDistance = distance;
                nearest = playerRef;
            }
        }
        return nearest;
    }

    private static void clearGreetingTarget(
            PatronSession session,
            NPCEntity npc,
            CommandBuffer<EntityStore> commandBuffer) {
        if (npc != null && npc.getRole() != null) {
            npc.getRole().setMarkedTarget(GREETING_TARGET_SLOT, null);
            if (npc.getRole().getHeadSteering() != null) {
                npc.getRole().getHeadSteering().clear();
            }
        }
        restoreHeadForward(session, commandBuffer);
        session.greetingTarget = null;
    }

    private static void restoreHeadForward(
            PatronSession session,
            CommandBuffer<EntityStore> commandBuffer) {
        if (!session.patronRef.isValid()) {
            return;
        }
        TransformComponent transform = commandBuffer.getComponent(
                session.patronRef, TransformComponent.getComponentType());
        HeadRotation headRotation = commandBuffer.getComponent(
                session.patronRef, HeadRotation.getComponentType());
        if (transform != null && headRotation != null) {
            headRotation.setRotation(transform.getRotation());
        }
    }

    private static void applyGreetingHeadTracking(
            PatronSession session,
            NPCEntity npc,
            TransformComponent patronTransform,
            CommandBuffer<EntityStore> commandBuffer) {
        if (npc == null || npc.getRole() == null || session.greetingTarget == null
                || !session.greetingTarget.isValid()
                || npc.getRole().getHeadSteering() == null) {
            return;
        }
        npc.getRole().setMarkedTarget(GREETING_TARGET_SLOT, session.greetingTarget);
        Ref<EntityStore> boundTarget = npc.getRole().getMarkedEntitySupport()
                .getMarkedEntityRef(GREETING_TARGET_SLOT);
        if (boundTarget == null || !boundTarget.isValid()) {
            clearGreetingTarget(session, npc, commandBuffer);
            return;
        }
        session.greetingTarget = boundTarget;
        TransformComponent targetTransform = commandBuffer.getComponent(
                boundTarget, TransformComponent.getComponentType());
        ModelComponent patronModel = commandBuffer.getComponent(
                session.patronRef, ModelComponent.getComponentType());
        ModelComponent targetModel = commandBuffer.getComponent(
                boundTarget, ModelComponent.getComponentType());
        HeadRotation headRotation = commandBuffer.getComponent(
                session.patronRef, HeadRotation.getComponentType());
        if (targetTransform == null || patronModel == null || headRotation == null
                || patronModel.getModel() == null
                || targetTransform.getPosition().distanceSquared(
                        patronTransform.getPosition()) > GREETING_DISTANCE_SQUARED) {
            clearGreetingTarget(session, npc, commandBuffer);
            return;
        }
        Vector3d patronPosition = patronTransform.getPosition();
        Vector3d targetPosition = targetTransform.getPosition();
        double deltaX = targetPosition.x - patronPosition.x;
        double deltaY = targetPosition.y
                + (targetModel == null || targetModel.getModel() == null
                        ? 0.0 : targetModel.getModel().getEyeHeight())
                - patronPosition.y - patronModel.getModel().getEyeHeight();
        double deltaZ = targetPosition.z - patronPosition.z;
        float targetYaw = PhysicsMath.normalizeTurnAngle(
                PhysicsMath.headingFromDirection(deltaX, deltaZ));
        float targetPitch = PhysicsMath.pitchFromDirection(deltaX, deltaY, deltaZ);
        npc.getRole().getHeadSteering()
                .clearTranslation()
                .setYaw(targetYaw)
                .setPitch(targetPitch)
                .setRelativeTurnSpeed(1.0);
        headRotation.getRotation().setYaw(targetYaw);
        headRotation.getRotation().setPitch(targetPitch);
    }

    private void beginEating(
            PatronSession session,
            CommandBuffer<EntityStore> commandBuffer) {
        Optional<TableServingManager.ServingSnapshot> serving =
                servingManager.servingAt(session.reservation.table());
        if (serving.isEmpty()) {
            return;
        }
        session.phase = Phase.EATING;
        session.phaseRemaining = EATING_SECONDS;
        clearGreetingTarget(session, commandBuffer.getComponent(
                session.patronRef, NPCEntity.getComponentType()), commandBuffer);
        stopEatingAnimation(session, commandBuffer);
        session.nextAnimationSeconds = randomRestDuration();
        session.mealConsumed = false;
        session.mealVisualState = MealVisualState.RESTING;
        session.mealFoodHidden = false;
        servingManager.lockForPatron(session.reservation.table());
        servingManager.restoreFoodVisual(session.reservation.table(), commandBuffer);
        setNameplate(session.patronRef, "Tavern Patron", commandBuffer);
    }

    private void tickEating(
            float delta,
            PatronSession session,
            NPCEntity npc,
            TransformComponent transform,
            World world,
            CommandBuffer<EntityStore> commandBuffer) {
        session.phaseRemaining = Math.max(0.0f, session.phaseRemaining - delta);
        if (session.phaseRemaining <= 0.0f) {
            session.mealConsumed = servingManager.consumeForPatron(
                    session.reservation.table(),
                    session.requestedItemId,
                    commandBuffer);
            if (session.mealConsumed) {
                session.mealFoodHidden = false;
            }
            stopEatingPresentation(session, commandBuffer);
            particles.spawnEmotion(
                    session.patronRef, PARTICLE_HEARTS, world, commandBuffer);
            beginLeaving(session, npc, transform, commandBuffer, false);
            showDepartureDialogue(
                    session,
                    SATISFIED_MEAL_LINES[random.nextInt(SATISFIED_MEAL_LINES.length)],
                    "Tavern Patron",
                    commandBuffer);
            return;
        }

        if (session.mealVisualState == MealVisualState.EATING) {
            if (!session.biteAnimationStarted) {
                session.biteEquipmentLeadRemaining -= delta;
                if (session.biteEquipmentLeadRemaining <= 0.0f) {
                    npc.playAnimation(
                            session.patronRef,
                            AnimationSlot.Action,
                            "Consume",
                            commandBuffer);
                    session.biteAnimationStarted = true;
                    session.biteAnimationRemaining = randomEatingDuration();
                }
            } else {
                session.biteAnimationRemaining -= delta;
                if (session.biteAnimationRemaining <= 0.0f) {
                    finishBite(session, commandBuffer);
                    session.nextAnimationSeconds = randomRestDuration();
                }
            }
        } else {
            session.nextAnimationSeconds -= delta;
        }
        if (session.mealVisualState == MealVisualState.RESTING
                && session.nextAnimationSeconds <= 0.0f) {
            startBite(session, npc, commandBuffer);
        }
    }

    private void startBite(
            PatronSession session,
            NPCEntity npc,
            CommandBuffer<EntityStore> commandBuffer) {
        Optional<TableServingManager.ServingSnapshot> serving =
                servingManager.servingAt(session.reservation.table());
        if (serving.isEmpty()) {
            session.nextAnimationSeconds = randomRestDuration();
            return;
        }
        equipMealInHand(session, serving.get().foodStack(), commandBuffer);
        session.mealFoodHidden = servingManager.hideFoodVisual(
                session.reservation.table(), commandBuffer);
        if (!session.mealFoodHidden) {
            clearMealFromHand(session, commandBuffer);
            session.nextAnimationSeconds = randomRestDuration();
            return;
        }
        session.mealVisualState = MealVisualState.EATING;
        session.biteAnimationStarted = false;
        session.biteEquipmentLeadRemaining = BITE_EQUIPMENT_LEAD_SECONDS;
        session.biteAnimationRemaining = 0.0f;
    }

    private void finishBite(
            PatronSession session,
            CommandBuffer<EntityStore> commandBuffer) {
        stopEatingAnimation(session, commandBuffer);
        clearMealFromHand(session, commandBuffer);
        if (session.mealFoodHidden) {
            servingManager.restoreFoodVisual(
                    session.reservation.table(), commandBuffer);
        }
        session.mealFoodHidden = false;
        session.mealVisualState = MealVisualState.RESTING;
        session.biteAnimationStarted = false;
        session.biteEquipmentLeadRemaining = 0.0f;
    }

    private void beginLeaving(
            PatronSession session,
            NPCEntity npc,
            TransformComponent transform,
            CommandBuffer<EntityStore> commandBuffer,
            boolean failedOrder) {
        boolean interruptedMeal = session.phase == Phase.EATING && !session.mealConsumed;
        clearGreetingTarget(session, npc, commandBuffer);
        stopEatingPresentation(session, commandBuffer);
        if (interruptedMeal && session.mealFoodHidden) {
            servingManager.restoreFoodVisual(
                    session.reservation.table(), commandBuffer);
        }
        session.mealFoodHidden = false;
        servingManager.unlockForPatron(session.reservation.table());
        commandBuffer.tryRemoveComponent(
                session.patronRef, MountedComponent.getComponentType());
        // Tavern's explicit Sit occupies Status, while block mounting can leave
        // a client-side seated pose in Movement. Clear both before pathing so
        // normal locomotion becomes the only surviving presentation.
        stopTrackedAnimation(session.patronRef, AnimationSlot.Status, commandBuffer);
        stopTrackedAnimation(session.patronRef, AnimationSlot.Movement, commandBuffer);
        session.phase = Phase.LEAVING;
        session.phaseRemaining = EXIT_DESPAWN_SECONDS;
        setNameplate(session.patronRef, failedOrder ? "Unhappy Patron" : "Tavern Patron",
                commandBuffer);
        setTravelTarget(session, npc, TravelStage.EXIT_INSIDE);
    }

    private boolean isReservationValid(PatronSession session, World world) {
        TableServingManager.TableKey table = session.reservation.table();
        BlockType tableBlock = loadedBlockType(
                world, table.x(), table.y(), table.z());
        Vector3i chair = session.reservation.chair();
        BlockType chairBlock = loadedBlockType(
                world, chair.x(), chair.y(), chair.z());
        return tableBlock != null
                && servingManager.isRegisteredTable(tableBlock)
                && chairBlock != null
                && chairBlock.getSeats() != null
                && servingManager.isRegisteredSeat(chairBlock);
    }

    private void tickLeaving(
            float delta,
            PatronSession session,
            NPCEntity npc,
            TransformComponent transform,
            World world,
            CommandBuffer<EntityStore> commandBuffer) {
        tickDepartureDialogue(delta, session, commandBuffer);
        session.phaseRemaining -= delta;
        tickRouteProgress(delta, session, npc, transform);
        if (session.travelStage == TravelStage.EXIT_INSIDE
                && reached(transform, session.entrance.inside())) {
            if (doorOperator.openIfClosed(
                    world, commandBuffer, session.patronRef,
                    session.entrance.doorBlock())) {
                setTravelTarget(session, npc, TravelStage.EXIT_OUTSIDE);
            }
        } else if (session.travelStage == TravelStage.EXIT_OUTSIDE) {
            if (transform.getPosition().distanceSquared(session.entrance.door())
                    <= 2.5 * 2.5) {
                doorOperator.openIfClosed(
                        world, commandBuffer, session.patronRef,
                        session.entrance.doorBlock());
            }
            if (reached(transform, session.entrance.outside())) {
                setTravelTarget(session, npc, TravelStage.EXIT_SPAWN);
            }
        }
        if ((session.travelStage == TravelStage.EXIT_SPAWN
                    && transform.getPosition().distanceSquared(session.entrance.spawn())
                            <= EXIT_DISTANCE_SQUARED)
                || session.phaseRemaining <= 0.0f) {
            despawn(session, commandBuffer);
        }
    }

    private static void showDepartureDialogue(
            PatronSession session,
            String text,
            String fallback,
            CommandBuffer<EntityStore> commandBuffer) {
        session.departureDialogueRemaining = DEPARTURE_DIALOGUE_SECONDS;
        session.departureFallbackNameplate = fallback;
        setNameplate(session.patronRef, text, commandBuffer);
    }

    private static void tickDepartureDialogue(
            float delta,
            PatronSession session,
            CommandBuffer<EntityStore> commandBuffer) {
        if (session.departureDialogueRemaining <= 0.0f) {
            return;
        }
        session.departureDialogueRemaining = Math.max(
                0.0f, session.departureDialogueRemaining - delta);
        if (session.departureDialogueRemaining <= 0.0f
                && session.departureFallbackNameplate != null) {
            setNameplate(
                    session.patronRef, session.departureFallbackNameplate, commandBuffer);
            session.departureFallbackNameplate = null;
        }
    }

    private void despawn(
            PatronSession session,
            CommandBuffer<EntityStore> commandBuffer) {
        if (!completeSession(session)) {
            return;
        }
        if (session.patronRef.isValid()) {
            commandBuffer.tryRemoveEntity(session.patronRef, RemoveReason.REMOVE);
        }
    }

    private void forceCleanup(
            PatronSession session,
            CommandBuffer<EntityStore> commandBuffer) {
        if (session.removed) {
            return;
        }
        boolean interruptedMeal = session.phase == Phase.EATING && !session.mealConsumed;
        NPCEntity npc = session.patronRef.isValid()
                ? commandBuffer.getComponent(session.patronRef, NPCEntity.getComponentType())
                : null;
        clearGreetingTarget(session, npc, commandBuffer);
        stopEatingPresentation(session, commandBuffer);
        if (interruptedMeal && session.mealFoodHidden) {
            servingManager.restoreFoodVisual(
                    session.reservation.table(), commandBuffer);
            session.mealFoodHidden = false;
        }
        servingManager.unlockForPatron(session.reservation.table());
        if (session.patronRef.isValid()) {
            commandBuffer.tryRemoveComponent(
                    session.patronRef, MountedComponent.getComponentType());
        }
        despawn(session, commandBuffer);
    }

    private static void equipMealInHand(
            PatronSession session,
            ItemStack meal,
            CommandBuffer<EntityStore> commandBuffer) {
        InventoryComponent.Hotbar hotbar = commandBuffer.getComponent(
                session.patronRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) {
            SimpleItemContainer container = new SimpleItemContainer((short) 1);
            container.setItemStackForSlot((short) 0, meal.withQuantity(1));
            hotbar = new InventoryComponent.Hotbar(container, (byte) 0);
            commandBuffer.putComponent(
                    session.patronRef,
                    InventoryComponent.Hotbar.getComponentType(),
                    hotbar);
            session.createdMealHotbar = true;
        } else {
            session.previousHotbarSlot = hotbar.getActiveSlot();
            session.previousMealSlotItem = hotbar.getInventory().getCapacity() > 0
                    ? hotbar.getInventory().getItemStack((short) 0)
                    : ItemStack.EMPTY;
            hotbar.ensureCapacity((short) 1, List.of());
            hotbar.getInventory().setItemStackForSlot(
                    (short) 0, meal.withQuantity(1));
            hotbar.setActiveSlot((byte) 0, session.patronRef, commandBuffer);
        }
        hotbar.setOutdatedEquipment(true);
        session.mealEquipped = true;
    }

    private static void clearMealFromHand(
            PatronSession session,
            CommandBuffer<EntityStore> commandBuffer) {
        if (!session.mealEquipped) {
            return;
        }
        if (!session.patronRef.isValid()) {
            session.mealEquipped = false;
            session.createdMealHotbar = false;
            session.previousMealSlotItem = null;
            return;
        }
        InventoryComponent.Hotbar hotbar = commandBuffer.getComponent(
                session.patronRef, InventoryComponent.Hotbar.getComponentType());
        if (session.createdMealHotbar) {
            commandBuffer.tryRemoveComponent(
                    session.patronRef, InventoryComponent.Hotbar.getComponentType());
        } else if (hotbar != null) {
            hotbar.getInventory().setItemStackForSlot(
                    (short) 0,
                    session.previousMealSlotItem == null
                            ? ItemStack.EMPTY : session.previousMealSlotItem);
            hotbar.setActiveSlot(
                    session.previousHotbarSlot, session.patronRef, commandBuffer);
            hotbar.setOutdatedEquipment(true);
        }
        session.mealEquipped = false;
        session.createdMealHotbar = false;
        session.previousMealSlotItem = null;
    }

    private static void stopEatingAnimation(
            PatronSession session,
            CommandBuffer<EntityStore> commandBuffer) {
        stopTrackedAnimation(session.patronRef, AnimationSlot.Action, commandBuffer);
        session.biteAnimationRemaining = 0.0f;
        session.biteAnimationStarted = false;
        session.biteEquipmentLeadRemaining = 0.0f;
    }

    private static void stopTrackedAnimation(
            Ref<EntityStore> patronRef,
            AnimationSlot slot,
            CommandBuffer<EntityStore> commandBuffer) {
        if (!patronRef.isValid()) {
            return;
        }
        ActiveAnimationComponent active = commandBuffer.getComponent(
                patronRef, ActiveAnimationComponent.getComponentType());
        if (active != null) {
            active.setPlayingAnimation(slot, null);
        }
        // AnimationUtils.stopAnimation uses the packet cache. Sending a null
        // animation through playAnimation is the engine's no-cache equivalent
        // and cannot be skipped by NPCEntity's non-Action duplicate guard.
        AnimationUtils.playAnimation(patronRef, slot, null, commandBuffer);
    }

    private static void stopEatingPresentation(
            PatronSession session,
            CommandBuffer<EntityStore> commandBuffer) {
        stopEatingAnimation(session, commandBuffer);
        clearMealFromHand(session, commandBuffer);
        session.nextAnimationSeconds = 0.0f;
        session.mealVisualState = MealVisualState.RESTING;
    }

    private void updateOrderDisplay(
            PatronSession session,
            CommandBuffer<EntityStore> commandBuffer) {
        int second = (int) Math.ceil(session.phaseRemaining);
        if (second == session.lastCountdownSecond) {
            return;
        }
        session.lastCountdownSecond = second;
        setNameplate(
                session.patronRef,
                formatTimer(second),
                commandBuffer);
    }

    private Optional<Reservation> findReservation(CoreRecord core, World world) {
        List<Vector3i> chairs = new ArrayList<>();
        List<TableServingManager.TableKey> tables = new ArrayList<>();
        Cuboid bounds = core.bounds();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    BlockType block = loadedBlockType(world, x, y, z);
                    if (block == null || block == BlockType.EMPTY) {
                        continue;
                    }
                    if (servingManager.isRegisteredTable(block)) {
                        TableServingManager.TableKey key = new TableServingManager.TableKey(
                                core.worldId(), x, y, z);
                        if (!reservedTables.contains(key)
                                && servingManager.servingAt(key).isEmpty()) {
                            tables.add(key);
                        }
                    }
                    if (block.getSeats() != null && servingManager.isRegisteredSeat(block)) {
                        Vector3i chair = new Vector3i(x, y, z);
                        if (!reservedSeats.contains(new SeatKey(core.worldId(), chair))) {
                            chairs.add(chair);
                        }
                    }
                }
            }
        }

        Reservation best = null;
        double bestDistance = Double.MAX_VALUE;
        for (TableServingManager.TableKey table : tables) {
            Vector3d tableCenter = new Vector3d(
                    table.x() + 0.5, table.y() + 1.0, table.z() + 0.5);
            for (Vector3i chair : chairs) {
                Vector3d approach = new Vector3d(
                        chair.x() + 0.5, chair.y(), chair.z() + 0.5);
                double distance = approach.distanceSquared(tableCenter);
                if (distance <= 3.75 * 3.75 && distance < bestDistance) {
                    bestDistance = distance;
                    best = new Reservation(table, new Vector3i(chair), approach);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private List<Entrance> detectEntrances(CoreRecord core, World world) {
        List<DoorCandidate> candidates = new ArrayList<>();
        Cuboid bounds = core.bounds();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    BlockType block = loadedBlockType(world, x, y, z);
                    if (block == null || !block.isDoor() || isHorizontalDoor(block)) {
                        continue;
                    }
                    Vector3i door = new Vector3i(x, y, z);
                    candidates.add(new DoorCandidate(
                            door, new Vector3i(-1, 0, 0), x - bounds.minX()));
                    candidates.add(new DoorCandidate(
                            door, new Vector3i(1, 0, 0), bounds.maxX() - x));
                    candidates.add(new DoorCandidate(
                            door, new Vector3i(0, 0, -1), z - bounds.minZ()));
                    candidates.add(new DoorCandidate(
                            door, new Vector3i(0, 0, 1), bounds.maxZ() - z));
                }
            }
        }
        candidates.sort(Comparator.comparingInt(DoorCandidate::boundaryDistance));
        List<Entrance> detected = new ArrayList<>();
        for (DoorCandidate candidate : candidates) {
            Vector3i outsideColumn = new Vector3i(candidate.door())
                    .add(new Vector3i(candidate.outward())
                            .mul(candidate.boundaryDistance() + 2));
            Vector3i insideColumn = new Vector3i(candidate.door())
                    .sub(candidate.outward());
            Vector3i outsideApproachColumn = new Vector3i(candidate.door())
                    .add(candidate.outward());
            Optional<Vector3d> spawn = findStandingPosition(
                    world, outsideColumn.x(), candidate.door().y(), outsideColumn.z());
            Optional<Vector3d> outside = findStandingPosition(
                    world,
                    outsideApproachColumn.x(),
                    candidate.door().y(),
                    outsideApproachColumn.z());
            Optional<Vector3d> inside = findStandingPosition(
                    world, insideColumn.x(), candidate.door().y(), insideColumn.z());
            if (spawn.isEmpty() || outside.isEmpty() || inside.isEmpty()) {
                continue;
            }
            detected.add(new Entrance(
                    new Vector3d(spawn.get()),
                    new Vector3d(outside.get()),
                    new Vector3d(candidate.door().x() + 0.5,
                            candidate.door().y(), candidate.door().z() + 0.5),
                    new Vector3d(inside.get()),
                    new Vector3i(candidate.door())));
        }
        return detected;
    }

    private static boolean isHorizontalDoor(BlockType block) {
        Item item = block.getItem();
        String id = item == null ? block.getId() : item.getId();
        return id != null && id.toLowerCase(java.util.Locale.ROOT).contains("trapdoor");
    }

    private static Optional<Vector3d> findStandingPosition(
            World world, int x, int preferredY, int z) {
        for (int offset : new int[] {0, 1, -1, 2, -2}) {
            int y = preferredY + offset;
            BlockType feet = loadedBlockType(world, x, y, z);
            BlockType head = loadedBlockType(world, x, y + 1, z);
            BlockType floor = loadedBlockType(world, x, y - 1, z);
            if (isPassable(feet) && isPassable(head)
                    && floor != null && floor.getMaterial() == BlockMaterial.Solid) {
                return Optional.of(new Vector3d(x + 0.5, y, z + 0.5));
            }
        }
        return Optional.empty();
    }

    /**
     * Patron systems run while the EntityStore is processing. Never call
     * World#getBlockType here: it synchronously loads a missing chunk and mutates
     * the ChunkStore, which Hytale explicitly forbids during an ECS tick.
     */
    static BlockType loadedBlockType(World world, int x, int y, int z) {
        WorldChunk chunk = world.getChunkIfLoaded(
                ChunkUtil.indexChunkFromBlock(x, z));
        return chunk == null ? null : chunk.getBlockType(x, y, z);
    }

    private static boolean isPassable(BlockType block) {
        return block == null || block == BlockType.EMPTY
                || block.getMaterial() == BlockMaterial.Empty || block.isDoor();
    }

    private static boolean reached(TransformComponent transform, Vector3d target) {
        return transform.getPosition().distanceSquared(target) <= STAGE_DISTANCE_SQUARED;
    }

    private static void setTravelTarget(
            PatronSession session,
            NPCEntity npc,
            TravelStage stage) {
        session.travelStage = stage;
        session.lastProgressPosition = null;
        session.stuckSeconds = 0.0f;
        npc.getPathManager().setTransientPath(null);
        npc.setLeashPoint(new Vector3d(targetFor(session, stage)));
    }

    private static void stopNavigation(
            NPCEntity npc,
            TransformComponent transform) {
        npc.getPathManager().setTransientPath(null);
        npc.setLeashPoint(new Vector3d(transform.getPosition()));
    }

    private static Vector3d targetFor(PatronSession session, TravelStage stage) {
        return switch (stage) {
            case EXTERIOR_APPROACH, EXIT_OUTSIDE -> session.entrance.outside();
            case CROSSING_IN, EXIT_INSIDE -> session.entrance.inside();
            case TO_SEAT -> session.reservation.approach();
            case EXIT_SPAWN -> session.entrance.spawn();
        };
    }

    private void tickRouteProgress(
            float delta,
            PatronSession session,
            NPCEntity npc,
            TransformComponent transform) {
        Vector3d position = transform.getPosition();
        if (session.lastProgressPosition == null
                || position.distanceSquared(session.lastProgressPosition) > 0.04 * 0.04) {
            session.lastProgressPosition = new Vector3d(position);
            session.stuckSeconds = 0.0f;
            return;
        }
        session.stuckSeconds += delta;
        if (session.stuckSeconds < STUCK_REPATH_SECONDS) {
            return;
        }
        session.stuckSeconds = 0.0f;
        session.routeRetrySeconds += STUCK_REPATH_SECONDS;
        npc.setLeashPoint(new Vector3d(targetFor(session, session.travelStage)));
        if (session.phase == Phase.ENTERING
                && session.routeRetrySeconds >= ROUTE_RETRY_SECONDS
                && session.availableEntrances.size() > 1) {
            session.routeRetrySeconds = 0.0f;
            session.entranceIndex =
                    (session.entranceIndex + 1) % session.availableEntrances.size();
            session.entrance = session.availableEntrances.get(session.entranceIndex);
            setTravelTarget(session, npc, TravelStage.EXTERIOR_APPROACH);
        }
    }

    private static void setNameplate(
            Ref<EntityStore> patronRef,
            String text,
            CommandBuffer<EntityStore> commandBuffer) {
        Nameplate nameplate = commandBuffer.getComponent(
                patronRef, Nameplate.getComponentType());
        if (nameplate == null) {
            commandBuffer.putComponent(
                    patronRef, Nameplate.getComponentType(), new Nameplate(text));
        } else {
            nameplate.setText(text);
        }
    }

    static String formatTimer(int totalSeconds) {
        int clamped = Math.max(0, totalSeconds);
        return String.format("%d:%02d", clamped / 60, clamped % 60);
    }

    private float randomRestDuration() {
        return MIN_REST_DURATION_SECONDS
                + random.nextFloat() * (MAX_REST_DURATION_SECONDS - MIN_REST_DURATION_SECONDS);
    }

    private float randomEatingDuration() {
        return MIN_EATING_DURATION_SECONDS
                + random.nextFloat()
                        * (MAX_EATING_DURATION_SECONDS - MIN_EATING_DURATION_SECONDS);
    }

    private int activePatronCount(UUID tavernId) {
        int count = 0;
        for (PatronSession session : sessions.values()) {
            if (session.tavernId.equals(tavernId)) {
                count++;
            }
        }
        return count;
    }

    private void releaseReservation(PatronSession session) {
        servingManager.unlockForPatron(session.reservation.table());
        releaseReservation(session.seatKey, session.reservation.table());
    }

    private boolean completeSession(PatronSession session) {
        if (session.removed) {
            return false;
        }
        session.removed = true;
        sessions.remove(session.patronRef, session);
        releaseReservation(session);
        return true;
    }

    private void releaseReservation(
            SeatKey seatKey,
            TableServingManager.TableKey tableKey) {
        reservedSeats.remove(seatKey);
        reservedTables.remove(tableKey);
    }

    enum Phase { ENTERING, ORDERING, EATING, LEAVING }

    enum GreetingPhase { WAITING, GREETING, ORDER_LEAD_IN, COMPLETE }

    enum MealVisualState { RESTING, EATING }

    enum TravelStage {
        EXTERIOR_APPROACH,
        CROSSING_IN,
        TO_SEAT,
        EXIT_INSIDE,
        EXIT_OUTSIDE,
        EXIT_SPAWN
    }

    record Entrance(
            Vector3d spawn,
            Vector3d outside,
            Vector3d door,
            Vector3d inside,
            Vector3i doorBlock) { }

    record Reservation(
            TableServingManager.TableKey table,
            Vector3i chair,
            Vector3d approach) { }

    private record DoorCandidate(
            Vector3i door,
            Vector3i outward,
            int boundaryDistance) { }

    private record SeatKey(UUID worldId, int x, int y, int z) {
        private SeatKey(UUID worldId, Vector3i position) {
            this(worldId, position.x(), position.y(), position.z());
        }
    }

    private static final class PatronSession {
        private final Ref<EntityStore> patronRef;
        private final UUID tavernId;
        private final UUID worldId;
        private Entrance entrance;
        private final List<Entrance> availableEntrances;
        private final Reservation reservation;
        private final SeatKey seatKey;
        private Phase phase = Phase.ENTERING;
        private float phaseRemaining = APPROACH_TIMEOUT_SECONDS;
        private String requestedItemId;
        private Ref<EntityStore> lastSeenFoodRef;
        private int lastCountdownSecond = -1;
        private float orderParticlePulseRemaining;
        private GreetingPhase greetingPhase = GreetingPhase.WAITING;
        private float greetingRemaining;
        private float greetingCooldownRemaining;
        private Ref<EntityStore> greetingTarget;
        private boolean mealAcknowledgementActive;
        private float mealAcknowledgementRemaining;
        private float nextAnimationSeconds;
        private float biteAnimationRemaining;
        private float biteEquipmentLeadRemaining;
        private boolean biteAnimationStarted;
        private ItemStack previousMealSlotItem;
        private byte previousHotbarSlot = -1;
        private boolean createdMealHotbar;
        private boolean mealEquipped;
        private MealVisualState mealVisualState = MealVisualState.RESTING;
        private boolean mealFoodHidden;
        private boolean mealConsumed;
        private float departureDialogueRemaining;
        private String departureFallbackNameplate;
        private TravelStage travelStage = TravelStage.EXTERIOR_APPROACH;
        private Vector3d lastProgressPosition;
        private float stuckSeconds;
        private float routeRetrySeconds;
        private int entranceIndex;
        private boolean removed;

        private PatronSession(
                Ref<EntityStore> patronRef,
                UUID tavernId,
                UUID worldId,
                Entrance entrance,
                List<Entrance> availableEntrances,
                Reservation reservation,
                SeatKey seatKey) {
            this.patronRef = patronRef;
            this.tavernId = tavernId;
            this.worldId = worldId;
            this.entrance = entrance;
            this.availableEntrances = availableEntrances;
            this.reservation = reservation;
            this.seatKey = seatKey;
        }
    }
}
