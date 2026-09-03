package com.inigmasgames.taverns;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interaction.CancelInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.io.handlers.game.GamePacketHandler;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.system.TransformSystems;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.item.ItemPhysicsComponent;
import com.hypixel.hytale.server.core.modules.interaction.system.InteractionSystems;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.systems.SteeringSystem;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3i;

public final class TavernsPlugin extends JavaPlugin {
    public static final String CORE_ITEM_ID = CoreDefinitions.TAVERN.itemId();
    public static final String TAVERN_SERVICE_ITEM_ID = "Furniture_Tavern_Service";
    public static final String REVISION = "R056";

    private TavernRepository repository;
    private CoreModeManager coreMode;
    private ComfortRegistry comfortRegistry;
    private ComfortManager comfortManager;
    private ComfortTooltipInstaller comfortTooltipInstaller;
    private TableServingManager tableServingManager;
    private TavernPatronManager patronManager;
    private PreparedFoodRegistry preparedFoods;
    private PreparedCraftingManager preparedCraftingManager;

    public TavernsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        repository = new TavernRepository(
                getDataDirectory(),
                message -> getLogger().at(Level.INFO).log("%s", message),
                throwable -> getLogger().at(Level.SEVERE).withCause(throwable).log("Taverns persistence failure"));
        repository.load();
        CoreValidator validator = new CoreValidator(repository);
        coreMode = new CoreModeManager(repository, validator);
        comfortRegistry = ComfortRegistry.load(
                getDataDirectory(),
                message -> getLogger().at(Level.INFO).log("%s", message),
                throwable -> getLogger().at(Level.SEVERE).withCause(throwable)
                        .log("Taverns Comfort failure"));
        comfortTooltipInstaller = new ComfortTooltipInstaller();
        comfortManager = new ComfortManager(
                repository,
                comfortRegistry,
                throwable -> getLogger().at(Level.SEVERE).withCause(throwable)
                         .log("Taverns Comfort failure"));
        preparedFoods = PreparedFoodRegistry.loadDefault();
        preparedCraftingManager = new PreparedCraftingManager(repository, preparedFoods);
        ComponentType<EntityStore, TableServingComponent> tableServingType =
                getEntityStoreRegistry().registerComponent(
                        TableServingComponent.class,
                        "InigmasGames:TavernTableServing",
                        TableServingComponent.CODEC);
        tableServingManager = new TableServingManager(
                repository, comfortRegistry, preparedFoods, tableServingType);
        ComponentType<EntityStore, TavernPatronComponent> patronType =
                getEntityStoreRegistry().registerComponent(
                        TavernPatronComponent.class,
                        "InigmasGames:TavernPatron",
                        TavernPatronComponent.CODEC);
        patronManager = new TavernPatronManager(
                repository,
                tableServingManager,
                preparedFoods,
                patronType,
                message -> getLogger().at(Level.INFO).log("%s", message),
                throwable -> getLogger().at(Level.SEVERE).withCause(throwable)
                        .log("Taverns patron failure"));
        Interaction.CODEC.register(
                TableServingUseInteraction.TYPE_ID,
                TableServingUseInteraction.class,
                TableServingUseInteraction.CODEC);
        TableServingUseInteraction.install(tableServingManager);
        registerItemLoadEvent();

        getEntityStoreRegistry().registerSystem(new CorePlacedSystem(
                repository, validator, patronManager));
        getEntityStoreRegistry().registerSystem(new CoreUsedSystem(repository, coreMode));
        getEntityStoreRegistry().registerSystem(new TavernServiceUsedSystem(repository));
        getEntityStoreRegistry().registerSystem(new PreparedCraftPreSystem(
                preparedCraftingManager));
        getEntityStoreRegistry().registerSystem(new PreparedCraftPostSystem(
                preparedCraftingManager));
        getEntityStoreRegistry().registerSystem(new CoreBrokenSystem(repository, coreMode));
        getEntityStoreRegistry().registerSystem(new ComfortPlacedSystem(
                comfortManager, patronManager));
        getEntityStoreRegistry().registerSystem(new ComfortBrokenSystem(
                comfortManager, patronManager));
        getEntityStoreRegistry().registerSystem(new CoreUseInputSystem(coreMode));
        getEntityStoreRegistry().registerSystem(new TableServingTrackingSystem(
                tableServingManager, tableServingType));
        getEntityStoreRegistry().registerSystem(new TableServingBrokenSystem(
                tableServingManager));
        getEntityStoreRegistry().registerSystem(new TableServingInputSystem(
                tableServingManager,
                throwable -> getLogger().at(Level.SEVERE).withCause(throwable)
                        .log("Taverns tabletop placement failure")));
        getEntityStoreRegistry().registerSystem(new TavernPatronTrackingSystem(
                patronManager, patronType));
        getEntityStoreRegistry().registerSystem(new TavernPatronTickSystem(patronManager));
        getEntityStoreRegistry().registerSystem(new TavernPatronHeartbeatSystem(
                patronManager, patronType));
        getEntityStoreRegistry().registerSystem(new CoreModeTickSystem(coreMode));
        getEntityStoreRegistry().registerSystem(new ComfortTickSystem(comfortManager));
        getEntityStoreRegistry().registerSystem(new ServiceAnnouncementTickSystem());
        getEntityStoreRegistry().registerSystem(new RelaxedRegenerationSystem(
                throwable -> getLogger().at(Level.SEVERE).withCause(throwable)
                        .log("Taverns Relaxed regeneration failure")));
        getEventRegistry().registerGlobal(PlayerMouseButtonEvent.class, coreMode::handleMouseButton);
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            PlayerRef playerRef = event.getPlayerRef();
            patronManager.ownerDisconnected(playerRef.getUuid());
            coreMode.abandon(playerRef.getUuid());
            comfortManager.abandon(playerRef.getUuid());
        });
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            Ref<EntityStore> ref = event.getPlayerRef();
            PlayerRef playerRef = ref.getStore().getComponent(
                    ref, PlayerRef.getComponentType());
            if (playerRef != null) {
                event.getPlayer().getHudManager().addCustomHud(
                        playerRef, new TavernsHud(playerRef));
            }
        });
    }

    @Override
    protected void start() {
        coreMode.start();
        // LoadedAssetsEvent may be emitted before the final vanilla Item map is
        // populated. Resolve once more at plugin start, after asset loading.
        patronManager.resolveFoodItems();
        getLogger().at(Level.INFO).log(
                "Taverns revision %s started with persistence schema %s and generic Core support.",
                REVISION, TavernRepository.CURRENT_SCHEMA_VERSION);
    }

    @Override
    protected void shutdown() {
        if (coreMode != null) {
            coreMode.shutdown();
        }
        if (comfortTooltipInstaller != null) {
            comfortTooltipInstaller.restore();
        }
        if (tableServingManager != null) {
            tableServingManager.restoreEmptyHandSecondary();
            TableServingUseInteraction.uninstall(tableServingManager);
        }
        if (patronManager != null) {
            patronManager.shutdown();
        }
    }

    private void resolveComfortItems() {
        try {
            tableServingManager.installEmptyHandSecondary();
            comfortRegistry.resolveLoadedItems(
                    message -> getLogger().at(Level.INFO).log("%s", message));
            patronManager.resolveFoodItems();
            int tooltipCount = comfortTooltipInstaller.install(comfortRegistry);
            getLogger().at(Level.INFO).log(
                    "Attached Comfort values to %s native item tooltip(s).", tooltipCount);
        } catch (RuntimeException exception) {
            getLogger().at(Level.SEVERE).withCause(exception)
                    .log("Could not resolve Comfort data after Item assets loaded");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerItemLoadEvent() {
        getEventRegistry().register(
                (Class) LoadedAssetsEvent.class,
                Item.class,
                (java.util.function.Consumer<LoadedAssetsEvent<String, Item, ?>>)
                        event -> resolveComfortItems());
    }

    private static UUID worldId(Store<EntityStore> store) {
        return store.getExternalData().getWorld().getWorldConfig().getUuid();
    }

    private static void tell(PlayerRef playerRef, String text) {
        playerRef.sendMessage(Message.raw(text));
    }

    private static boolean isCoreBlock(World world, Vector3i position, String itemId) {
        BlockType blockType = BlockType.getAssetMap().getAsset(world.getBlock(position.x(), position.y(), position.z()));
        return blockType != null && itemId.equals(blockType.getId());
    }

    private static Optional<TavernRecord> ownerOf(TavernRepository repository, CoreRecord core) {
        return repository.findById(core.tavernId());
    }

    static Optional<TavernStatus> toggledServiceStatus(TavernStatus current) {
        return switch (current) {
            case CLOSED -> Optional.of(TavernStatus.OPEN);
            case OPEN -> Optional.of(TavernStatus.CLOSED);
            default -> Optional.empty();
        };
    }

    private static final class PreparedCraftPreSystem
            extends EntityEventSystem<EntityStore, CraftRecipeEvent.Pre> {
        private final PreparedCraftingManager manager;
        private final Query<EntityStore> query = Archetype.of(
                PlayerRef.getComponentType(), Player.getComponentType());

        private PreparedCraftPreSystem(PreparedCraftingManager manager) {
            super(CraftRecipeEvent.Pre.class);
            this.manager = manager;
        }

        @Override
        public void handle(
                int index,
                @Nonnull ArchetypeChunk<EntityStore> chunk,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer,
                @Nonnull CraftRecipeEvent.Pre event) {
            PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
            Player player = chunk.getComponent(index, Player.getComponentType());
            if (playerRef == null || player == null) {
                event.setCancelled(true);
                return;
            }
            manager.validate(event, playerRef, player, store.getExternalData().getWorld());
        }

        @Override public Query<EntityStore> getQuery() { return query; }
    }

    private static final class PreparedCraftPostSystem
            extends EntityEventSystem<EntityStore, CraftRecipeEvent.Post> {
        private final PreparedCraftingManager manager;
        private final Query<EntityStore> query = Archetype.of(
                PlayerRef.getComponentType(), Player.getComponentType());

        private PreparedCraftPostSystem(PreparedCraftingManager manager) {
            super(CraftRecipeEvent.Post.class);
            this.manager = manager;
        }

        @Override
        public void handle(
                int index,
                @Nonnull ArchetypeChunk<EntityStore> chunk,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer,
                @Nonnull CraftRecipeEvent.Post event) {
            PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
            if (playerRef == null) {
                event.setCancelled(true);
                return;
            }
            manager.complete(
                    event, chunk.getReferenceTo(index), playerRef, commandBuffer);
        }

        @Override public Query<EntityStore> getQuery() { return query; }
    }

    private static final class CorePlacedSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
        private final TavernRepository repository;
        private final CoreValidator validator;
        private final TavernPatronManager patronManager;
        private final Query<EntityStore> query = Archetype.of(PlayerRef.getComponentType());

        private CorePlacedSystem(
                TavernRepository repository,
                CoreValidator validator,
                TavernPatronManager patronManager) {
            super(PlaceBlockEvent.class);
            this.repository = repository;
            this.validator = validator;
            this.patronManager = patronManager;
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                           @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                           @Nonnull PlaceBlockEvent event) {
            ItemStack item = event.getItemInHand();
            Optional<CoreDefinition> foundDefinition = item == null
                    ? Optional.empty()
                    : CoreDefinitions.byItemId(item.getItemId());
            if (foundDefinition.isEmpty()) {
                return;
            }
            CoreDefinition definition = foundDefinition.get();
            PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
            if (playerRef == null) {
                event.setCancelled(true);
                return;
            }
            Vector3i position = new Vector3i(event.getTargetBlock());
            UUID currentWorldId = worldId(store);
            if (!definition.type().isPrimary()) {
                placeSpecializedCore(event, playerRef, store, definition, position, currentWorldId);
                return;
            }
            if (repository.findByOwner(playerRef.getUuid()).isPresent()) {
                event.setCancelled(true);
                tell(playerRef, "You already own a Tavern Core. The current design allows one Tavern per player.");
                return;
            }

            UUID tavernId = UUID.randomUUID();
            TavernRecord tavern = new TavernRecord(
                    tavernId, currentWorldId, playerRef.getUuid(), TavernStatus.CLOSED);
            CoreRecord core = CoreRecord.create(
                    UUID.randomUUID(), tavernId, definition, currentWorldId,
                    position.x(), position.y(), position.z());
            Optional<String> invalidReason = validator.validate(core);
            if (invalidReason.isPresent()) {
                event.setCancelled(true);
                tell(playerRef, "That Core cannot be placed: " + invalidReason.get());
                return;
            }

            repository.create(tavern, core);
            patronManager.initializeCore(core, store.getExternalData().getWorld());
            tell(playerRef, "Tavern established: "
                    + definition.startingWidth() + " x " + definition.startingDepth() + " x "
                    + definition.startingHeight() + " (" + definition.startingVolume()
                    + " blocks). Interact with the Core to edit it.");

            World world = store.getExternalData().getWorld();
            world.execute(() -> {
                if (!isCoreBlock(world, position, definition.itemId())) {
                    repository.removeTavern(tavern.tavernId());
                }
            });
        }

        private void placeSpecializedCore(
                PlaceBlockEvent event,
                PlayerRef playerRef,
                Store<EntityStore> store,
                CoreDefinition definition,
                Vector3i position,
                UUID currentWorldId) {
            Optional<CoreRecord> foundParent = repository.findPrimaryCoreContaining(
                    currentWorldId, position.x(), position.y(), position.z());
            if (foundParent.isEmpty()) {
                event.setCancelled(true);
                tell(playerRef, "A " + coreDisplayName(definition.type())
                        + " Core must be placed inside an existing Tavern Core volume.");
                return;
            }
            CoreRecord parent = foundParent.get();
            Optional<TavernRecord> foundTavern = ownerOf(repository, parent);
            if (foundTavern.isEmpty() || !foundTavern.get().ownerId().equals(playerRef.getUuid())) {
                event.setCancelled(true);
                tell(playerRef, "Only this Tavern's owner can place specialized Cores inside it.");
                return;
            }

            CoreRecord core = CoreRecord.create(
                    UUID.randomUUID(), parent.tavernId(), definition, currentWorldId,
                    position.x(), position.y(), position.z());
            Optional<String> invalidReason = validator.validate(core);
            if (invalidReason.isPresent()) {
                event.setCancelled(true);
                tell(playerRef, "That Core cannot be placed: " + invalidReason.get());
                return;
            }

            repository.addCore(core);
            tell(playerRef, coreDisplayName(definition.type()) + " Core established: "
                    + definition.startingWidth() + " x " + definition.startingDepth() + " x "
                    + definition.startingHeight() + " (" + definition.startingVolume()
                    + " blocks). Interact with the Core to edit it.");

            World world = store.getExternalData().getWorld();
            world.execute(() -> {
                if (!isCoreBlock(world, position, definition.itemId())) {
                    repository.removeCore(core.coreId());
                }
            });
        }

        @Override public Query<EntityStore> getQuery() { return query; }
    }

    private static final class CoreUsedSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Post> {
        private final TavernRepository repository;
        private final CoreModeManager coreMode;
        private final Query<EntityStore> query = Archetype.of(PlayerRef.getComponentType(), Player.getComponentType());

        private CoreUsedSystem(TavernRepository repository, CoreModeManager coreMode) {
            super(UseBlockEvent.Post.class);
            this.repository = repository;
            this.coreMode = coreMode;
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                           @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                           @Nonnull UseBlockEvent.Post event) {
            if (CoreDefinitions.byItemId(event.getBlockType().getId()).isEmpty()) {
                return;
            }
            PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }
            Vector3i position = event.getTargetBlock();
            Optional<CoreRecord> found = repository.findCoreByPosition(
                    worldId(store), position.x(), position.y(), position.z());
            if (found.isEmpty()) {
                tell(playerRef, "This Core is missing its persistent record; break and replace it to repair it.");
                return;
            }
            CoreRecord core = found.get();
            Optional<TavernRecord> tavern = ownerOf(repository, core);
            if (tavern.isEmpty()) {
                tell(playerRef, "This Core references a missing Tavern and requires administrative repair.");
                return;
            }
            if (!tavern.get().ownerId().equals(playerRef.getUuid())) {
                tell(playerRef, "Only this Tavern's owner can configure its Core.");
                return;
            }
            coreMode.toggle(playerRef, chunk.getReferenceTo(index), store, core);
        }

        @Override public Query<EntityStore> getQuery() { return query; }
    }

    private static String coreDisplayName(CoreType type) {
        String name = type.name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static final class TavernServiceUsedSystem
            extends EntityEventSystem<EntityStore, UseBlockEvent.Post> {
        private final TavernRepository repository;
        private final Query<EntityStore> query = Archetype.of(
                PlayerRef.getComponentType(), Player.getComponentType());

        private TavernServiceUsedSystem(TavernRepository repository) {
            super(UseBlockEvent.Post.class);
            this.repository = repository;
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> commandBuffer,
                           @Nonnull UseBlockEvent.Post event) {
            if (!TAVERN_SERVICE_ITEM_ID.equals(event.getBlockType().getId())) {
                return;
            }
            PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
            Player player = chunk.getComponent(index, Player.getComponentType());
            if (playerRef == null || player == null) {
                return;
            }

            Vector3i position = event.getTargetBlock();
            Optional<CoreRecord> foundCore = repository.findPrimaryCoreContaining(
                    worldId(store), position.x(), position.y(), position.z());
            if (foundCore.isEmpty()) {
                tell(playerRef, "Tavern Service must be placed inside a Tavern volume.");
                return;
            }
            Optional<TavernRecord> foundTavern = ownerOf(repository, foundCore.get());
            if (foundTavern.isEmpty()) {
                tell(playerRef, "This Tavern Service references a missing Tavern and requires administrative repair.");
                return;
            }
            TavernRecord tavern = foundTavern.get();
            if (!tavern.ownerId().equals(playerRef.getUuid())) {
                tell(playerRef, "Only this Tavern's owner can operate Tavern Service.");
                return;
            }

            Optional<TavernStatus> nextStatus = toggledServiceStatus(tavern.status());
            if (nextStatus.isEmpty()) {
                tell(playerRef, "Tavern Service is unavailable while this Tavern requires attention.");
                return;
            }
            boolean opening = nextStatus.get() == TavernStatus.OPEN;
            repository.updateTavern(tavern.withStatus(nextStatus.get()));
            String announcement = opening
                    ? "The Tavern is Open for Service"
                    : "The Tavern is Closed for Service";
            if (player.getHudManager().getCustomHud(TavernsHud.KEY) instanceof TavernsHud hud) {
                hud.showServiceAnnouncement(announcement);
            } else {
                tell(playerRef, announcement);
            }
        }

        @Override public Query<EntityStore> getQuery() { return query; }
    }

    private static final class CoreBrokenSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        private final TavernRepository repository;
        private final CoreModeManager coreMode;
        private final Query<EntityStore> query = Archetype.of(PlayerRef.getComponentType());

        private CoreBrokenSystem(TavernRepository repository, CoreModeManager coreMode) {
            super(BreakBlockEvent.class);
            this.repository = repository;
            this.coreMode = coreMode;
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                           @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                           @Nonnull BreakBlockEvent event) {
            Optional<CoreDefinition> foundDefinition = CoreDefinitions.byItemId(event.getBlockType().getId());
            if (foundDefinition.isEmpty()) {
                return;
            }
            PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
            if (playerRef == null) {
                event.setCancelled(true);
                return;
            }
            Vector3i position = new Vector3i(event.getTargetBlock());
            Optional<CoreRecord> found = repository.findCoreByPosition(
                    worldId(store), position.x(), position.y(), position.z());
            if (found.isEmpty()) {
                return;
            }
            CoreRecord core = found.get();
            Optional<TavernRecord> tavern = ownerOf(repository, core);
            if (tavern.isEmpty() || !tavern.get().ownerId().equals(playerRef.getUuid())) {
                event.setCancelled(true);
                tell(playerRef, "Only this Tavern's owner can break its Core.");
                return;
            }
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            if (coreMode.isActive(playerRef.getUuid())) {
                coreMode.exit(playerRef, ref, store, false);
            }
            World world = store.getExternalData().getWorld();
            String itemId = foundDefinition.get().itemId();
            world.execute(() -> {
                if (!isCoreBlock(world, position, itemId)) {
                    if (core.type().isPrimary()) {
                        repository.removeTavern(core.tavernId());
                        tell(playerRef, "Tavern removed. The Core was returned as a whole item, not crystal shards.");
                    } else {
                        repository.removeCore(core.coreId());
                        tell(playerRef, "Core removed from its Tavern.");
                    }
                }
            });
        }

        @Override public Query<EntityStore> getQuery() { return query; }
    }

    private static final class CoreModeTickSystem extends EntityTickingSystem<EntityStore> {
        private final CoreModeManager coreMode;
        private final Query<EntityStore> query = Archetype.of(PlayerRef.getComponentType(), Player.getComponentType());

        private CoreModeTickSystem(CoreModeManager coreMode) {
            this.coreMode = coreMode;
        }

        @Override
        public void tick(float delta, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                         @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
            if (playerRef == null || !coreMode.isActive(playerRef.getUuid())) {
                return;
            }
            coreMode.validateCurrentSelection(playerRef, chunk.getReferenceTo(index), store);
        }

        @Override public Query<EntityStore> getQuery() { return query; }
    }

    private static final class ComfortPlacedSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
        private final ComfortManager comfortManager;
        private final TavernPatronManager patronManager;
        private final Query<EntityStore> query = Archetype.of(PlayerRef.getComponentType());

        private ComfortPlacedSystem(
                ComfortManager comfortManager,
                TavernPatronManager patronManager) {
            super(PlaceBlockEvent.class);
            this.comfortManager = comfortManager;
            this.patronManager = patronManager;
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> commandBuffer,
                           @Nonnull PlaceBlockEvent event) {
            Vector3i position = new Vector3i(event.getTargetBlock());
            World world = store.getExternalData().getWorld();
            UUID currentWorldId = worldId(store);
            patronManager.markLayoutDirty(
                    currentWorldId, position.x(), position.y(), position.z());
            world.execute(() -> comfortManager.markDirtyAt(
                    currentWorldId, position.x(), position.y(), position.z()));
        }

        @Override public Query<EntityStore> getQuery() { return query; }
    }

    private static final class ComfortBrokenSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        private final ComfortManager comfortManager;
        private final TavernPatronManager patronManager;
        private final Query<EntityStore> query = Archetype.of(PlayerRef.getComponentType());

        private ComfortBrokenSystem(
                ComfortManager comfortManager,
                TavernPatronManager patronManager) {
            super(BreakBlockEvent.class);
            this.comfortManager = comfortManager;
            this.patronManager = patronManager;
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> commandBuffer,
                           @Nonnull BreakBlockEvent event) {
            Vector3i position = new Vector3i(event.getTargetBlock());
            World world = store.getExternalData().getWorld();
            UUID currentWorldId = worldId(store);
            patronManager.markLayoutDirty(
                    currentWorldId, position.x(), position.y(), position.z());
            world.execute(() -> comfortManager.markDirtyAt(
                    currentWorldId, position.x(), position.y(), position.z()));
        }

        @Override public Query<EntityStore> getQuery() { return query; }
    }

    private static final class ComfortTickSystem extends EntityTickingSystem<EntityStore> {
        private final ComfortManager comfortManager;
        private final Query<EntityStore> query = Archetype.of(
                PlayerRef.getComponentType(),
                Player.getComponentType(),
                TransformComponent.getComponentType(),
                EffectControllerComponent.getComponentType());

        private ComfortTickSystem(ComfortManager comfortManager) {
            this.comfortManager = comfortManager;
        }

        @Override
        public void tick(float delta, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                         @Nonnull Store<EntityStore> store,
                         @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
            Player player = chunk.getComponent(index, Player.getComponentType());
            TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
            EffectControllerComponent effects = chunk.getComponent(
                    index, EffectControllerComponent.getComponentType());
            if (playerRef == null || player == null || transform == null || effects == null) {
                return;
            }
            comfortManager.tick(
                    delta, playerRef, player, transform, effects,
                    chunk.getReferenceTo(index), store);
        }

        @Override public Query<EntityStore> getQuery() { return query; }
    }

    /** Releases a Table's stable serving props as native item drops before block removal. */
    private static final class TableServingBrokenSystem
            extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        private final TableServingManager manager;
        private final Query<EntityStore> query = Archetype.of(PlayerRef.getComponentType());

        private TableServingBrokenSystem(TableServingManager manager) {
            super(BreakBlockEvent.class);
            this.manager = manager;
        }

        @Override
        public void handle(
                int index,
                @Nonnull ArchetypeChunk<EntityStore> chunk,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer,
                @Nonnull BreakBlockEvent event) {
            manager.releaseAt(
                    worldId(store),
                    new Vector3i(event.getTargetBlock()),
                    store.getExternalData().getWorld(),
                    commandBuffer);
        }

        @Override
        public Query<EntityStore> getQuery() {
            return query;
        }
    }

    private static final class ServiceAnnouncementTickSystem
            extends EntityTickingSystem<EntityStore> {
        private final Query<EntityStore> query = Archetype.of(Player.getComponentType());

        @Override
        public void tick(float delta, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                         @Nonnull Store<EntityStore> store,
                         @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            Player player = chunk.getComponent(index, Player.getComponentType());
            if (player != null
                    && player.getHudManager().getCustomHud(TavernsHud.KEY) instanceof TavernsHud hud) {
                hud.tickServiceAnnouncement(delta);
            }
        }

        @Override public Query<EntityStore> getQuery() { return query; }
    }
    /**
     * Handles F/Use before Hytale validates the client-only Selection Tool presentation
     * against the player's unchanged authoritative hotbar item.
     */
    private static final class CoreUseInputSystem extends EntityTickingSystem<EntityStore> {
        private final CoreModeManager coreMode;
        private final Query<EntityStore> query = Archetype.of(PlayerRef.getComponentType());
        private final Set<Dependency<EntityStore>> dependencies = Set.of(
                new SystemDependency<>(Order.BEFORE,
                        InteractionSystems.TickInteractionManagerSystem.class));

        private CoreUseInputSystem(CoreModeManager coreMode) {
            this.coreMode = coreMode;
        }

        @Override
        public void tick(float delta, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                         @Nonnull Store<EntityStore> store,
                         @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
            if (playerRef == null || !coreMode.isActive(playerRef.getUuid())
                    || !(playerRef.getPacketHandler() instanceof GamePacketHandler packetHandler)) {
                return;
            }

            Deque<SyncInteractionChain> queue = packetHandler.getInteractionPacketQueue();
            SyncInteractionChain exitInput = null;
            for (SyncInteractionChain update : queue) {
                if (update.initial && update.interactionType == InteractionType.Use
                        && update.data != null && update.data.blockPosition != null
                        && coreMode.isActiveCoreTarget(playerRef.getUuid(),
                                update.data.blockPosition.x,
                                update.data.blockPosition.y,
                                update.data.blockPosition.z)) {
                    exitInput = update;
                    break;
                }
            }
            if (exitInput == null) {
                return;
            }

            int chainId = exitInput.chainId;
            queue.removeIf(update -> update.chainId == chainId);
            packetHandler.writeNoCache(new CancelInteractionChain(chainId, exitInput.forkedId));
            coreMode.exit(playerRef, chunk.getReferenceTo(index), store, true);
        }

        @Override public Query<EntityStore> getQuery() { return query; }

        @Override
        public SystemGroup<EntityStore> getGroup() {
            return DamageModule.get().getGatherDamageGroup();
        }

        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return dependencies;
        }
    }

    /** Rebuilds one-slot occupancy from durable tabletop display entities on chunk load. */
    private static final class TableServingTrackingSystem extends RefSystem<EntityStore> {
        private final TableServingManager manager;
        private final ComponentType<EntityStore, TableServingComponent> servingType;
        private final Query<EntityStore> query;

        private TableServingTrackingSystem(
                TableServingManager manager,
                ComponentType<EntityStore, TableServingComponent> servingType) {
            this.manager = manager;
            this.servingType = servingType;
            this.query = Archetype.of(servingType);
        }

        @Override
        public void onEntityAdded(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull AddReason reason,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            TableServingComponent serving = commandBuffer.getComponent(ref, servingType);
            if (serving != null) {
                manager.track(worldId(store), ref, serving, commandBuffer);
            }
        }

        @Override
        public void onEntityRemove(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull RemoveReason reason,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            TableServingComponent serving = commandBuffer.getComponent(ref, servingType);
            if (serving != null) {
                manager.untrack(worldId(store), ref, serving);
            }
        }

        @Override
        @Nonnull
        public Query<EntityStore> getQuery() {
            return query;
        }
    }

    /** Removes stale persisted patrons and releases runtime reservations on despawn. */
    private static final class TavernPatronTrackingSystem extends RefSystem<EntityStore> {
        private final TavernPatronManager manager;
        private final Query<EntityStore> query;

        private TavernPatronTrackingSystem(
                TavernPatronManager manager,
                ComponentType<EntityStore, TavernPatronComponent> patronType) {
            this.manager = manager;
            this.query = Archetype.of(patronType);
        }

        @Override
        public void onEntityAdded(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull AddReason reason,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            manager.onPatronEntityAdded(ref, commandBuffer);
        }

        @Override
        public void onEntityRemove(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull RemoveReason reason,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            manager.onPatronEntityRemoved(ref);
        }

        @Override
        @Nonnull
        public Query<EntityStore> getQuery() {
            return query;
        }
    }

    /** Advances each world's patron scheduler and patron state machines once per tick. */
    private static final class TavernPatronTickSystem
            extends EntityTickingSystem<EntityStore> {
        private final TavernPatronManager manager;
        private final Query<EntityStore> query = Archetype.of(PlayerRef.getComponentType());
        private final Set<Dependency<EntityStore>> dependencies = Set.of(
                new SystemDependency<>(Order.AFTER, SteeringSystem.class),
                new SystemDependency<>(Order.BEFORE,
                        TransformSystems.EntityTrackerUpdate.class));

        private TavernPatronTickSystem(TavernPatronManager manager) {
            this.manager = manager;
        }

        @Override
        public void tick(
                float delta,
                int index,
                @Nonnull ArchetypeChunk<EntityStore> chunk,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            manager.tickWorld(delta, store, commandBuffer);
        }

        @Override
        public Query<EntityStore> getQuery() {
            return query;
        }

        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return dependencies;
        }
    }

    /** Keeps active patron timers/path state advancing if the last player disconnects. */
    private static final class TavernPatronHeartbeatSystem
            extends EntityTickingSystem<EntityStore> {
        private final TavernPatronManager manager;
        private final Query<EntityStore> query;

        private TavernPatronHeartbeatSystem(
                TavernPatronManager manager,
                ComponentType<EntityStore, TavernPatronComponent> patronType) {
            this.manager = manager;
            this.query = Archetype.of(patronType);
        }

        @Override
        public void tick(
                float delta,
                int index,
                @Nonnull ArchetypeChunk<EntityStore> chunk,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            manager.tickWorld(delta, store, commandBuffer);
        }

        @Override
        public Query<EntityStore> getQuery() {
            return query;
        }
    }

    /** Handles close-range F/Use and Secondary placement at Tavern Tables. */
    private static final class TableServingInputSystem extends EntityTickingSystem<EntityStore> {
        private final TableServingManager manager;
        private final Consumer<Throwable> error;
        private final Query<EntityStore> query = Archetype.of(
                PlayerRef.getComponentType(),
                InventoryComponent.Hotbar.getComponentType(),
                TransformComponent.getComponentType());
        private final Set<Dependency<EntityStore>> dependencies = Set.of(
                new SystemDependency<>(Order.BEFORE,
                        InteractionSystems.TickInteractionManagerSystem.class));

        private TableServingInputSystem(
                TableServingManager manager,
                Consumer<Throwable> error) {
            this.manager = manager;
            this.error = error;
        }

        @Override
        public void tick(float delta, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                         @Nonnull Store<EntityStore> store,
                         @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
            InventoryComponent.Hotbar hotbar = chunk.getComponent(
                    index, InventoryComponent.Hotbar.getComponentType());
            TransformComponent playerTransform = chunk.getComponent(
                    index, TransformComponent.getComponentType());
            if (playerRef == null || hotbar == null || playerTransform == null
                    || !(playerRef.getPacketHandler() instanceof GamePacketHandler packetHandler)) {
                return;
            }

            Deque<SyncInteractionChain> queue = packetHandler.getInteractionPacketQueue();
            World world = store.getExternalData().getWorld();
            UUID currentWorldId = worldId(store);
            Ref<EntityStore> entityRef = chunk.getReferenceTo(index);
            Vector3d playerPosition = playerTransform.getPosition();

            // A Table target wins before vanilla interaction chains tick. F/Use
            // and Secondary can both place; only F/Use performs Table pickup.
            for (SyncInteractionChain update : queue) {
                if (!update.initial
                        || update.interactionType != InteractionType.Use
                                && update.interactionType != InteractionType.Secondary
                        || update.data == null
                        || update.data.blockPosition == null) {
                    continue;
                }
                Vector3i position = new Vector3i(
                        update.data.blockPosition.x,
                        update.data.blockPosition.y,
                        update.data.blockPosition.z);
                Optional<Vector3i> resolvedPosition =
                        manager.resolveInteractionTablePosition(world, position);
                if (resolvedPosition.isEmpty()
                        || !TableServingManager.isWithinInteractionRange(
                                playerPosition, resolvedPosition.get())) {
                    continue;
                }

                short heldSlot = -1;
                ItemStack heldStack = null;
                if (update.activeHotbarSlot >= 0
                        && update.activeHotbarSlot < hotbar.getInventory().getCapacity()) {
                    heldSlot = (short) update.activeHotbarSlot;
                    heldStack = hotbar.getInventory().getItemStack(heldSlot);
                    if (heldStack != null && update.itemInHandId != null
                            && !update.itemInHandId.equals(heldStack.getItemId())) {
                        heldStack = null;
                    }
                }

                try {
                    Optional<TableServingManager.TableTarget> placementTarget =
                            manager.eligibleTarget(
                                    world, currentWorldId, resolvedPosition.get(), heldStack);
                    if (placementTarget.isPresent()) {
                        cancelTableAction(queue, packetHandler, update);
                        TableServingManager.PlacementResult result = manager.place(
                                placementTarget.get(),
                                hotbar.getInventory(),
                                heldSlot,
                                heldStack,
                                world,
                                commandBuffer);
                        if (result == TableServingManager.PlacementResult.OCCUPIED
                                && update.interactionType == InteractionType.Use) {
                            manager.pickupFoodFromTable(
                                    entityRef,
                                    currentWorldId,
                                    resolvedPosition.get(),
                                    world,
                                    commandBuffer);
                        }
                        return;
                    }
                    if (update.interactionType == InteractionType.Use
                            && manager.pickupFoodFromTable(
                            entityRef,
                            currentWorldId,
                            resolvedPosition.get(),
                            world,
                            commandBuffer)) {
                        int chainId = update.chainId;
                        queue.removeIf(candidate -> candidate.chainId == chainId);
                        packetHandler.writeNoCache(new CancelInteractionChain(
                                chainId, update.forkedId));
                        return;
                    }
                } catch (RuntimeException exception) {
                    error.accept(exception);
                    return;
                }
            }
        }

        private static void cancelTableAction(
                Deque<SyncInteractionChain> queue,
                GamePacketHandler packetHandler,
                SyncInteractionChain handled) {
            List<SyncInteractionChain> related = queue.stream()
                    .filter(candidate -> candidate.initial
                            && candidate.data != null
                            && candidate.data.blockPosition != null
                            && candidate.activeHotbarSlot == handled.activeHotbarSlot
                            && candidate.data.blockPosition.x
                                    == handled.data.blockPosition.x
                            && candidate.data.blockPosition.y
                                    == handled.data.blockPosition.y
                            && candidate.data.blockPosition.z
                                    == handled.data.blockPosition.z
                            && (candidate.interactionType == InteractionType.Use
                                    || candidate.interactionType
                                            == InteractionType.Secondary))
                    .toList();
            for (SyncInteractionChain candidate : related) {
                queue.remove(candidate);
                packetHandler.writeNoCache(new CancelInteractionChain(
                        candidate.chainId, candidate.forkedId));
            }
        }

        @Override
        public Query<EntityStore> getQuery() {
            return query;
        }

        @Override
        public SystemGroup<EntityStore> getGroup() {
            return DamageModule.get().getGatherDamageGroup();
        }

        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return dependencies;
        }
    }

}
