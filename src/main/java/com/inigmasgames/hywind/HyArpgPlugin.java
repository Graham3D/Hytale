package com.inigmasgames.hywind;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseMotionEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.event.events.player.RemovedPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketWatcher;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.inigmasgames.canvasui.CanvasUI;
import com.inigmasgames.canvasui.demo.CanvasCursorProbeCloseCommand;
import com.inigmasgames.canvasui.demo.CanvasCursorProbeCommand;
import com.inigmasgames.canvasui.demo.CanvasDemoCommand;
import com.inigmasgames.canvasui.demo.CanvasInputProbeCommand;
import com.inigmasgames.canvasui.runtime.CanvasService;
import com.inigmasgames.canvasui.runtime.cursor.CanvasInputGuard;
import com.inigmasgames.canvasui.runtime.cursor.CursorHudProbeService;
import com.inigmasgames.taverns.TavernsPlugin;
import com.inigmasgames.hytalerpg.commands.RpgCommand;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.diagnostics.RpgSkillTraceService;
import com.inigmasgames.hytalerpg.diagnostics.SkillTraceConfiguration;
import com.inigmasgames.hytalerpg.links.CompatibilityService;
import com.inigmasgames.hytalerpg.links.LinkCompiler;
import com.inigmasgames.hytalerpg.links.RpgLinkGraphService;
import com.inigmasgames.hytalerpg.progress.FileRpgPlayerStateRepository;
import com.inigmasgames.hytalerpg.progress.OwnershipEntitlementPolicy;
import com.inigmasgames.hytalerpg.progress.RpgLoadoutService;
import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.combat.diagnostics.CombatTrace;
import com.inigmasgames.hytalerpg.combat.hytale.DerivedStatEntityAdapter;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageLifecycleSystems;
import com.inigmasgames.hytalerpg.combat.hytale.HomeRestorationTickSystem;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import java.util.EnumMap;
import java.util.UUID;
import com.inigmasgames.hytalerpg.progress.AttributeAllocationService;
import com.inigmasgames.hytalerpg.input.HytaleAbilitySkillInputAdapter;
import com.inigmasgames.hytalerpg.input.NativeAbilityBridgeAudit;
import com.inigmasgames.hytalerpg.input.NativeAbilityProjectionService;
import com.inigmasgames.hytalerpg.input.NativeAbilityProjectionTickSystem;
import com.inigmasgames.hytalerpg.input.CommandOnlyRpgUiOpenInputAdapter;
import com.inigmasgames.hytalerpg.ui.RpgUiProjectionService;
import com.inigmasgames.hytalerpg.ui.hud.RpgHudCoordinator;
import com.inigmasgames.hytalerpg.ui.hud.RpgHudTickSystem;
import com.inigmasgames.hytalerpg.ui.trace.RpgUiTraceService;
import com.inigmasgames.hytalerpg.ui.skilltree.RpgSkillTreeMutationService;
import com.inigmasgames.hytalerpg.ui.skilltree.RpgSkillTreeProjectionService;
import com.inigmasgames.hytalerpg.ui.skilltree.StaticSkillTreeLayout;
import com.inigmasgames.hytalerpg.execution.SkillExecutionService;
import com.inigmasgames.hytalerpg.execution.SkillExecutorRegistry;
import com.inigmasgames.hytalerpg.execution.SkillInstanceLifecycle;
import com.inigmasgames.hytalerpg.execution.Stage04SkillProfiles;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleSkillExecutionSystem;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleBossBarTracker;
import com.inigmasgames.hytalerpg.execution.reaction.ReactionWindowService;
import com.inigmasgames.hytalerpg.vfx.HtDevLibVfxAdapter;
import com.inigmasgames.hytalerpg.vfx.LinkTreeVfxService;
import com.inigmasgames.hytalerpg.phase00.*;
import java.util.Map;
import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;

import javax.annotation.Nonnull;
import java.nio.file.Path;

/** Production bootstrap for merged HyARPG, Tavern, and CanvasUI gameplay. */
public final class HyArpgPlugin extends TavernsPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private PacketFilter inboundWatcher;
    private PacketFilter outboundWatcher;
    private com.inigmasgames.hytalerpg.ui.inventory.NativeInventoryEntryProbe inventoryEntryProbe;
    private com.inigmasgames.hytalerpg.ui.inventory.TabTraceProbe tabTraceProbe;
    private com.inigmasgames.hytalerpg.ui.inventory.NativeInventoryDropTraceProbe nativeDropTraceProbe;
    private com.inigmasgames.hytalerpg.execution.hytale.HealingPresentationProbe healingProbe;
    private RpgSkillTraceService skillTrace;
    private RpgLoadoutService loadouts;
    private RpgCombatKernel combatKernel;
    private RpgUiTraceService uiTrace;
    private com.inigmasgames.hytalerpg.ui.trace.UiInteractionTrace interactionTrace;
    private com.inigmasgames.hytalerpg.gear.GearQaTrace gearQaTrace;
    private com.inigmasgames.hytalerpg.gear.GearQaTrace sentinelQaTrace;
    private RpgHudCoordinator rpgHud;
    private HytaleAbilitySkillInputAdapter abilityInputs;
    private NativeAbilityProjectionService nativeAbilities;
    private HytaleSkillExecutionSystem skillExecutionSystem;
    private com.inigmasgames.hytalerpg.progress.FileEncounterStore encounterStore;
    private com.inigmasgames.hytalerpg.gear.HytaleGearLoot gearLootRuntime;
    private com.inigmasgames.hytalerpg.gear.NativeAffixDurabilityInstallation gearDurability;
    private com.inigmasgames.hytalerpg.execution.hytale.GearRecoveryBindings gearRecovery;
    private com.inigmasgames.hytalerpg.execution.hytale.GearSignatureBindings gearSignatures;
    private com.inigmasgames.hytalerpg.execution.hytale.GearStatusBindings gearStatuses;
    private com.inigmasgames.hytalerpg.execution.hytale.NativeItemAffixBindings itemAffixes;
    private final java.util.List<HytaleDamageLifecycleSystems.AppliedObserver> gearReceiptObservers=new java.util.ArrayList<>();
    private com.inigmasgames.hytalerpg.execution.hytale.HytalePlayerPersistenceReady persistenceReady;
    private com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards encounterRewards;
    private com.inigmasgames.hytalerpg.execution.hytale.PlayerHitRootOwner playerHitRoots;
    private com.inigmasgames.hytalerpg.combat.hytale.NativeSystemReplacement enemyArmor,enemyOutgoingEffects,enemySpawnGroups;
    private com.inigmasgames.hytalerpg.execution.hytale.PackboundNativeMutationBridge packboundNativeHook;
    private AutoCloseable enemyProjectileHook;
    private AutoCloseable enemyDamageReceiptHook;
    private com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyActions enemyActions;
    private com.inigmasgames.hytalerpg.enemies.EnemyReflectiveEffects enemyReflectiveEffects;
    private com.inigmasgames.hytalerpg.enemies.EnemyWorldAdmission enemyWorldAdmission;
    private com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyBirthOwner enemyBirthOwner;
    private com.inigmasgames.hytalerpg.execution.hytale.EnemyHealthBarPresentation enemyHealthBars;
    private com.inigmasgames.hytalerpg.difficulty.DifficultyRuntime difficultyRuntime;
    private java.util.concurrent.ExecutorService difficultyIo;
    private com.inigmasgames.hytalerpg.difficulty.HytaleDifficultyPortals difficultyPortals;
    private com.inigmasgames.hytalerpg.spawning.NativeWorldSpawnDensity worldSpawnDensity;
    private com.inigmasgames.hytalerpg.spawning.HywindWorldConfiguration worldConfiguration;
    private com.inigmasgames.hytalerpg.spawning.NativePopulationBalance populationBalance;
    private com.inigmasgames.hytalerpg.diagnostics.MonsterSpawnTrace monsterSpawnTrace;
    private com.inigmasgames.hywind.compat.RpgGameplayEventPublisher immersiveEvents =
            com.inigmasgames.hywind.compat.RpgGameplayEventPublisher.NO_OP;
    private CanvasService canvasService;
    private CursorHudProbeService cursorProbe;
    private boolean canvasSetup;
    private boolean rpgSetup;
    private boolean tavernsSetup;
    private enum StartupState { STARTING, RUNNING, STOPPING, STOPPED }
    private volatile StartupState startupState=StartupState.STARTING;

    /** Trusted server-plugin integration only; no command or packet can supply party membership. */
    public void configurePartyMembership(com.inigmasgames.hytalerpg.progress.PartyMembershipProvider provider){
        if(encounterRewards==null)throw new IllegalStateException("RPG_ENCOUNTER_SERVICE_NOT_READY");
        encounterRewards.configurePartyProvider(provider);
    }

    public HyArpgPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected Path tavernsDataDirectory() {
        return legacyDataDirectory("InigmasGames_Taverns");
    }

    private Path rpgDataDirectory() {
        return legacyDataDirectory("InigmasGames_HytaleRPGPhase00Audit");
    }

    private Path canvasDataDirectory() {
        return legacyDataDirectory("InigmasGames_CanvasUI");
    }

    private Path legacyDataDirectory(String directoryName) {
        Path parent = getDataDirectory().getParent();
        if (parent == null) throw new IllegalStateException("HYARPG_DATA_ROOT_PARENT_MISSING");
        return parent.resolve(directoryName).normalize();
    }

    @Override
    protected void setup() {
        try (var readyPathSpan = com.inigmasgames.hywind.readypath.ReadyPathProbe.span("BOOT_HYARPG_SETUP", null)) {
        if (com.inigmasgames.hywind.readypath.ReadyPathProbe.ENABLED) {
            getEventRegistry().registerGlobal(com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent.class,
                    event -> com.inigmasgames.hywind.readypath.ReadyPathProbe.connect(event.getUuid()));
            getEventRegistry().registerGlobal(com.hypixel.hytale.server.core.event.events.player.PlayerSetupDisconnectEvent.class,
                    event -> com.inigmasgames.hywind.readypath.ReadyPathProbe.disconnect(event.getUuid()));
            getEventRegistry().registerAsyncGlobal(com.hypixel.hytale.server.core.asset.common.events.SendCommonAssetsEvent.class,
                    future -> future.thenApply(event -> {
                        var auth = event.getPacketHandler().getAuth();
                        com.inigmasgames.hywind.readypath.ReadyPathProbe.requestedAssets(
                                auth == null ? null : auth.getUuid(), event.getRequestedAssets() == null ? -1 : event.getRequestedAssets().length);
                        return event;
                    }));
            getEventRegistry().registerGlobal(com.hypixel.hytale.server.core.event.events.BootEvent.class,
                    event -> com.inigmasgames.hywind.readypath.ReadyPathProbe.boot());
            getEventRegistry().registerGlobal(com.hypixel.hytale.server.core.universe.world.events.AllWorldsLoadedEvent.class,
                    event -> com.inigmasgames.hywind.readypath.ReadyPathProbe.mark("ALL_WORLDS_LOADED_OBSERVED", null));
        }
        try {
            tavernsSetup = true;
            super.setup();
            setupOptionalImmersiveBridge();
            com.hypixel.hytale.server.npc.NPCPlugin.get().registerCoreComponentType(
                    "HywindOpenBarterShop", com.inigmasgames.hytalerpg.ui.inventory.HywindOpenBarterShopAction.Builder::new);
            com.hypixel.hytale.server.npc.NPCPlugin.get().registerCoreComponentType(
                    com.inigmasgames.hytalerpg.execution.hytale.NativeNpcTiming.WALK_TYPE,
                    com.inigmasgames.hytalerpg.execution.hytale.NativeNpcTiming.WalkBuilder::new);
            com.hypixel.hytale.server.npc.NPCPlugin.get().registerCoreComponentType(
                    com.inigmasgames.hytalerpg.execution.hytale.NativeNpcTiming.ATTACK_TYPE,
                    com.inigmasgames.hytalerpg.execution.hytale.NativeNpcTiming.AttackBuilder::new);
            var triggerVolumes = com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin.get();
            if (triggerVolumes == null) throw new IllegalStateException("Required TriggerVolumes producer registry unavailable");
            triggerVolumes.registerEffectType(
                    com.inigmasgames.hytalerpg.ui.inventory.HywindSpatialGrantEffect.TYPE,
                    com.inigmasgames.hytalerpg.ui.inventory.HywindSpatialGrantEffect.class,
                    com.inigmasgames.hytalerpg.ui.inventory.HywindSpatialGrantEffect.CODEC);
            canvasSetup = true;
            setupCanvas();
            rpgSetup = true;
            setupRpg();
            LOGGER.atInfo().log("HYARPG_DATA_ROOTS bootstrap=%s gameplay=%s presentation=%s taverns=%s",
                    getDataDirectory(), rpgDataDirectory(), canvasDataDirectory(), tavernsDataDirectory());
            LOGGER.atInfo().log("HYARPG_SETUP version=%s revision=%s modules=GAMEPLAY,PRESENTATION,TAVERNS aiIntegration=OPTIONAL_BRIDGE_V1",
                    getManifest().getVersion(), BuildIdentity.REVISION);
        } catch (RuntimeException | Error failure) {
            startupState=StartupState.STOPPING;
            rollbackPartialSetup();
            throw failure;
        }

        }
    }

    private void setupCanvas() {
        try (var readyPathSpan = com.inigmasgames.hywind.readypath.ReadyPathProbe.span("BOOT_CANVAS_SETUP", null)) {
        canvasService = new CanvasService();
        cursorProbe = new CursorHudProbeService(canvasDataDirectory());
        getEntityStoreRegistry().registerSystem(new CanvasInputGuard.InteractionStartSystem(cursorProbe.inputGuard()));
        getEntityStoreRegistry().registerSystem(new CanvasInputGuard.ActiveSlotRequestSystem(cursorProbe.inputGuard()));
        getEntityStoreRegistry().registerSystem(new CanvasInputGuard.DropItemSystem(cursorProbe.inputGuard()));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.ui.inventory.InventoryAliasDropSystem(() -> gearLootRuntime));
        getEntityStoreRegistry().registerSystem(new CursorHudProbeService.DeathCleanupSystem(cursorProbe));
        CanvasUI.install(canvasService, cursorProbe);
        getEventRegistry().registerGlobal(PlayerMouseButtonEvent.class, event -> {
            cursorProbe.route(event);
            PlayerRef playerRef = event.getPlayerRefComponent();
            if (playerRef == null || !cursorProbe.active(playerRef.getUuid())) canvasService.route(event);
        });
        getEventRegistry().registerGlobal(PlayerMouseMotionEvent.class, event -> {
            cursorProbe.route(event);
            var entityRef = event.getPlayerRef();
            if (!entityRef.isValid()) return;
            PlayerRef playerRef = entityRef.getStore().getComponent(entityRef, PlayerRef.getComponentType());
            if (playerRef == null || !cursorProbe.active(playerRef.getUuid())) canvasService.route(event);
        });
        getEventRegistry().registerGlobal(PlayerInteractEvent.class, cursorProbe::route);
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            cursorProbe.close(event.getPlayerRef().getUuid(), "PLAYER_DISCONNECT");
            canvasService.close(event.getPlayerRef().getUuid(), "PLAYER_DISCONNECT");
        });
        getEventRegistry().registerGlobal(RemovedPlayerFromWorldEvent.class, event -> {
            PlayerRef playerRef = event.getHolder().getComponent(PlayerRef.getComponentType());
            if (playerRef != null) {
                cursorProbe.close(playerRef.getUuid(), "WORLD_TRANSITION");
                canvasService.close(playerRef.getUuid(), "WORLD_TRANSITION");
            }
        });
        Path demoLayouts = canvasDataDirectory().resolve("demo-layouts");
        getCommandRegistry().registerCommand(new CanvasDemoCommand(demoLayouts, false));
        getCommandRegistry().registerCommand(new CanvasDemoCommand(demoLayouts, true));
        getCommandRegistry().registerCommand(new CanvasInputProbeCommand());
        getCommandRegistry().registerCommand(new CanvasCursorProbeCommand("canvasui-cursor-probe",
                "Open the passive-HUD cursor-camera probe.", cursorProbe, CursorHudProbeService.Context.PASSIVE_HUD));
        getCommandRegistry().registerCommand(new CanvasCursorProbeCommand("canvasui-cursor-probe-nohud",
                "Open the cursor-camera probe without custom UI.", cursorProbe, CursorHudProbeService.Context.NO_HUD));
        getCommandRegistry().registerCommand(new CanvasCursorProbeCommand("canvasui-cursor-probe-page",
                "Open the cursor-camera probe with a CustomUI page control.", cursorProbe,
                CursorHudProbeService.Context.CUSTOM_PAGE_CONTROL));
        getCommandRegistry().registerCommand(new CanvasCursorProbeCommand("canvasui-cursor-drag-proof",
                "Open the calibrated passive-HUD two-node drag proof.", cursorProbe,
                CursorHudProbeService.Context.DRAG_PROOF));
        getCommandRegistry().registerCommand(new CanvasCursorProbeCloseCommand(cursorProbe));
        LOGGER.atInfo().log("CANVASUI_SETUP revision=%s version=%s hytale=%s inputBackend=%s capabilities=%s cursorBackend=%s cursorCapabilities=%s guardBoundary=INTERACTION_CHAIN_START cursorProbe=PHASE_A5_B_GATE owner=HYARPG",
                CanvasUI.REVISION, getManifest().getVersion(), CanvasUI.HYTALE_VERSION,
                canvasService.inputBackend().id(), canvasService.inputBackend().capabilities(),
                com.inigmasgames.canvasui.rendering.HytaleCursorHudInputBackend.INSTANCE.id(),
                com.inigmasgames.canvasui.rendering.HytaleCursorHudInputBackend.INSTANCE.capabilities());

        }
    }

    private void setupOptionalImmersiveBridge() {
        var identifier = new com.hypixel.hytale.common.plugin.PluginIdentifier(
                "InigmasGames", "ImmersiveNPCs");
        var candidate = com.hypixel.hytale.server.core.plugin.PluginManager.get()
                .getPlugin(identifier);
        if (candidate == null) {
            LOGGER.atInfo().log("HYARPG_IMMERSIVE_BRIDGE status=NO_OP reason=PLUGIN_NOT_INSTALLED");
            immersiveEvents = com.inigmasgames.hywind.compat.RpgGameplayEventPublisher.NO_OP;
            return;
        }
        try {
            immersiveEvents = com.inigmasgames.hywind.compat.ImmersiveNpcBridgeConnector.connect(
                    candidate,
                    getManifest().getVersion().toString(),
                    message -> LOGGER.atInfo().log("%s", message),
                    message -> LOGGER.atWarning().log("%s", message));
        } catch (LinkageError | RuntimeException incompatible) {
            immersiveEvents = com.inigmasgames.hywind.compat.RpgGameplayEventPublisher.NO_OP;
            LOGGER.atWarning().log(
                    "HYARPG_IMMERSIVE_BRIDGE status=DISABLED reason=CONTRACT_UNAVAILABLE providerVersion=%s error=%s",
                    candidate.getManifest().getVersion(), incompatible.getClass().getSimpleName());
        }
    }

    private Stage04SkillProfiles validatedRuntimeProfiles;

    private void setupRpg() {
        try (var readyPathSpan = com.inigmasgames.hywind.readypath.ReadyPathProbe.span("BOOT_RPG_SETUP", null)) {
        var saveRoot=com.inigmasgames.hytalerpg.spawning.HywindWorldConfiguration.resolveSaveRoot(rpgDataDirectory());
        worldConfiguration=new com.inigmasgames.hytalerpg.spawning.HywindWorldConfiguration(
                saveRoot,rpgDataDirectory().resolve("world-spawn-density.json"));
        LOGGER.atInfo().log("HYTALE_RPG_SETUP revision=%s version=%s hytale=%s stage=%s combatEnabled=true",
                BuildIdentity.REVISION, BuildIdentity.VERSION, BuildIdentity.HYTALE_VERSION,
                BuildIdentity.STAGE);
        RpgCatalog catalog = RpgCatalog.loadCanonical();
        var progressionProfiles=com.inigmasgames.hytalerpg.progress.ProgressionProfiles.load();
        LOGGER.atInfo().log("RPG_STAGE12_PROFILES revision=%s bands=%d difficulties=%d nativeBiomeBindings=%d awardHook=true connectedProof=false",
                BuildIdentity.REVISION,progressionProfiles.biomeBands().size(),progressionProfiles.difficulties().size(),
                progressionProfiles.biomeBands().stream().mapToInt(b->b.verifiedNativeBiomeIds().size()).sum());
        SkillTraceConfiguration configuration = SkillTraceConfiguration.load();
        skillTrace = new RpgSkillTraceService(rpgDataDirectory().resolve("logs").resolve("rpg").resolve("skill-trace.jsonl"), configuration);
        com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.configure(skillTrace);
        var repository = new FileRpgPlayerStateRepository(rpgDataDirectory().resolve("players"));
        var compatibility = new CompatibilityService();
        var graphService = new RpgLinkGraphService(catalog, compatibility);
        combatKernel = RpgCombatKernel.createProduction();
        var compiler = new LinkCompiler(catalog, graphService, compatibility, combatKernel.balance());
        loadouts = new RpgLoadoutService(catalog, repository, graphService, compiler,
                new OwnershipEntitlementPolicy(configuration.developmentEntitlements()), skillTrace);
        loadouts.configureEarnedRewards(new com.inigmasgames.hytalerpg.progress.FileEarnedRewardStore(rpgDataDirectory().resolve("earned-rewards")));
        LOGGER.atInfo().log("RPG_STAGE12_REWARD_STORE playerSchema=%d writeAhead=true immutableReceipts=true awardHook=true connectedProof=false",
                com.inigmasgames.hytalerpg.progress.RpgPlayerState.CURRENT_SCHEMA);
        encounterStore=com.inigmasgames.hytalerpg.progress.FileEncounterStore.durableV2(rpgDataDirectory().resolve("encounters"));
        LOGGER.atInfo().log("RPG_STAGE12_ENCOUNTER_STORE schema=1 frozenDeathPlans=true permanentExclusions=true pending=%d awardHook=true connectedProof=false",
                encounterStore.pendingCount());
        CombatTrace combatTrace = new CombatTrace(skillTrace);
        uiTrace = new RpgUiTraceService(rpgDataDirectory().resolve("logs").resolve("rpg").resolve("ui-trace.jsonl"),configuration);
        var uiProjection = new RpgUiProjectionService(catalog, loadouts, combatKernel.derivedStats(), combatKernel.cooldowns());
        var staticLayout = new StaticSkillTreeLayout();
        var skillTreeProjection = new RpgSkillTreeProjectionService(catalog, loadouts, staticLayout,
                configuration.developmentEntitlements());
        var skillTreeMutations = new RpgSkillTreeMutationService(loadouts, staticLayout);
        var allocation = new AttributeAllocationService(loadouts);
        var runtimeProfiles = Stage04SkillProfiles.loadCanonical(catalog);
        validatedRuntimeProfiles = runtimeProfiles;
        nativeAbilities = new NativeAbilityProjectionService(loadouts, runtimeProfiles, skillTrace, loadouts::ready);
        loadouts.addMutationListener(nativeAbilities::onLoadoutMutation);
        abilityInputs = new HytaleAbilitySkillInputAdapter(nativeAbilities::observeInput);
        abilityInputs.useNativeExecution();
        abilityInputs.configureDiagnostics(() -> skillTrace.level() == com.inigmasgames.hytalerpg.diagnostics.SkillTraceLevel.DETAILED,
                (actor, detail) -> skillTrace.trace(com.inigmasgames.hytalerpg.diagnostics.RpgTraceRecord.create(
                        actor, com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType.NATIVE_ABILITY_BOUNDARY,
                        "native-ability-boundary", detail)));
        getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
                .register(com.inigmasgames.hytalerpg.input.NativeSkillActivationInteraction.TYPE,
                        com.inigmasgames.hytalerpg.input.NativeSkillActivationInteraction.class,
                        com.inigmasgames.hytalerpg.input.NativeSkillActivationInteraction.codec(abilityInputs));
        getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
                .register(com.inigmasgames.hytalerpg.input.NativeHeldChannelInteraction.TYPE,
                        com.inigmasgames.hytalerpg.input.NativeHeldChannelInteraction.class,
                        com.inigmasgames.hytalerpg.input.NativeHeldChannelInteraction.codec(abilityInputs));
        getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
                .register(com.inigmasgames.hytalerpg.input.NativeFireballChargeInteraction.TYPE,
                        com.inigmasgames.hytalerpg.input.NativeFireballChargeInteraction.class,
                        com.inigmasgames.hytalerpg.input.NativeFireballChargeInteraction.codec(abilityInputs));
        var runeControl = new com.inigmasgames.hytalerpg.input.NativeRuneControl(
                rpgDataDirectory().resolve("diagnostics").resolve("native-rune-control"),
                configuration.developmentEntitlements(), skillTrace);
        nativeAbilities.configureControl(runeControl);
        abilityInputs.configureControl(runeControl::inputSuppressed, runeControl::observe);
        var reactions = new ReactionWindowService(System::nanoTime);
        var executions = new SkillExecutionService(loadouts, runtimeProfiles, combatKernel,
                SkillExecutorRegistry.runtime(), new SkillInstanceLifecycle(), skillTrace);
        loadouts.configureRespecGuard(actor->com.inigmasgames.hytalerpg.progress.RespecGate.rejection(combatKernel.hostileCombat().secondsSinceHostile(actor),executions.pendingCast(actor)));
        var vfx = new LinkTreeVfxService(new HtDevLibVfxAdapter(), Map.of());
        var bosses = new HytaleBossBarTracker();
        skillExecutionSystem = new HytaleSkillExecutionSystem(abilityInputs, executions, combatKernel,
                combatTrace, reactions, vfx, bosses);
        var supportSystem=skillExecutionSystem.configureSupport(loadouts);
        var nativeWeaponFire=new com.inigmasgames.hytalerpg.combat.hytale.NativeWeaponFireProducer(
                supportSystem::routeManagedWeaponFire,supportSystem::nativeWeaponOffensiveFactor);
        getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
                .register(com.inigmasgames.hytalerpg.combat.hytale.ManagedWeaponFireInteraction.TYPE,
                        com.inigmasgames.hytalerpg.combat.hytale.ManagedWeaponFireInteraction.class,
                        com.inigmasgames.hytalerpg.combat.hytale.ManagedWeaponFireInteraction.codec(nativeWeaponFire));
        var summonSystem=skillExecutionSystem.configureSummons(rpgDataDirectory().resolve("corpse-consumption"));
        com.inigmasgames.hytalerpg.gear.GearNativeItems.bindRecipientEffects(summonSystem::sentinelEffects);
        var sentinelItemAuras=new com.inigmasgames.hytalerpg.execution.hytale.NativeSentinelItemAuras(
                supportSystem,combatKernel,bosses,combatTrace);
        supportSystem.configureSentinelItemAuras(sentinelItemAuras);
        summonSystem.configureSentinelItemAuras(sentinelItemAuras);
        com.inigmasgames.hytalerpg.execution.hytale.SummonProjection.bind(getEntityStoreRegistry().registerComponent(
                com.inigmasgames.hytalerpg.execution.hytale.SummonProjection.class,
                com.inigmasgames.hytalerpg.execution.hytale.SummonProjection::new));
        com.inigmasgames.hytalerpg.execution.hytale.LightningSpireProjection.bind(getEntityStoreRegistry().registerComponent(
                com.inigmasgames.hytalerpg.execution.hytale.LightningSpireProjection.class,
                com.inigmasgames.hytalerpg.execution.hytale.LightningSpireProjection::new));
        getEntityStoreRegistry().registerSystem(summonSystem);
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem.Removal(summonSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem.Death(summonSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem.DamageGuard(summonSystem));
        com.inigmasgames.hytalerpg.execution.hytale.ConversionProjection.bind(getEntityStoreRegistry().registerComponent(
                com.inigmasgames.hytalerpg.execution.hytale.ConversionProjection.class,
                com.inigmasgames.hytalerpg.execution.hytale.ConversionProjection::new));
        var conversionSystem=skillExecutionSystem.configureConversions();
        encounterRewards=new com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards(encounterStore,loadouts,skillTrace,combatKernel);
        playerHitRoots=new com.inigmasgames.hytalerpg.execution.hytale.PlayerHitRootOwner(encounterRewards);
        skillExecutionSystem.nativeBasics().configureDurableRoots(playerHitRoots::issue);
        encounterRewards.configureActive(()->startupState==StartupState.RUNNING);
        difficultyRuntime=new com.inigmasgames.hytalerpg.difficulty.DifficultyRuntime(
                rpgDataDirectory().resolve("difficulty-worlds.json"),com.hypixel.hytale.server.core.universe.Universe.get().getWorldsPath());
        encounterRewards.configureDifficulty(difficultyRuntime.encounters());
        encounterRewards.configureGolems(difficultyRuntime.worlds());
        com.inigmasgames.hytalerpg.difficulty.DifficultyHealthProjection.bind(getEntityStoreRegistry().registerComponent(
                com.inigmasgames.hytalerpg.difficulty.DifficultyHealthProjection.class,"RpgDifficultyHealth",com.inigmasgames.hytalerpg.difficulty.DifficultyHealthProjection.CODEC));
        com.inigmasgames.hytalerpg.enemies.EnemyShieldProjection.bind(getEntityStoreRegistry().registerComponent(
                com.inigmasgames.hytalerpg.enemies.EnemyShieldProjection.class,"RpgEnemyShield",com.inigmasgames.hytalerpg.enemies.EnemyShieldProjection.CODEC));
        com.inigmasgames.hytalerpg.execution.hytale.EnemyStaging.bind(getEntityStoreRegistry().registerComponent(
                com.inigmasgames.hytalerpg.execution.hytale.EnemyStaging.class,"RpgEnemyStaging",com.inigmasgames.hytalerpg.execution.hytale.EnemyStaging.CODEC));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.EnemyStaging.Visibility());
        com.inigmasgames.hytalerpg.enemies.EnemyActorIdentity.bind(getEntityStoreRegistry().registerComponent(
                com.inigmasgames.hytalerpg.enemies.EnemyActorIdentity.class,"RpgEnemyActorIdentity",
                com.inigmasgames.hytalerpg.enemies.EnemyActorIdentity.CODEC));
        com.inigmasgames.hytalerpg.execution.hytale.QaTransientMarker.bind(getEntityStoreRegistry().registerComponent(
                com.inigmasgames.hytalerpg.execution.hytale.QaTransientMarker.class,
                com.inigmasgames.hytalerpg.execution.hytale.QaTransientMarker::new));
        com.inigmasgames.hytalerpg.enemies.EnemyProjectileReceipt.bind(getEntityStoreRegistry().registerComponent(
                com.inigmasgames.hytalerpg.enemies.EnemyProjectileReceipt.class,"RpgEnemyProjectileReceipt",
                com.inigmasgames.hytalerpg.enemies.EnemyProjectileReceipt.CODEC));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyActorRecovery());
        com.inigmasgames.hytalerpg.enemies.EnemyEngagementClock.bind(getEntityStoreRegistry().registerComponent(
                com.inigmasgames.hytalerpg.enemies.EnemyEngagementClock.class,"RpgEnemyEngagementClock",
                com.inigmasgames.hytalerpg.enemies.EnemyEngagementClock.CODEC));
        // Register the candidate same-player save format, but never attach it or import native items
        // until protected pickup and native equipment handoffs pass connected acceptance.
        com.inigmasgames.hytalerpg.ui.inventory.SpatialBagComponent.bind(getEntityStoreRegistry().registerComponent(
                com.inigmasgames.hytalerpg.ui.inventory.SpatialBagComponent.class,"RpgSpatialBag",
                com.inigmasgames.hytalerpg.ui.inventory.SpatialBagComponent.CODEC));
        com.inigmasgames.taverns.api.SpatialPlayerItemPolicy.bind((ref, accessor) -> {
            var player = accessor.getComponent(ref, PlayerRef.getComponentType());
            if (player == null) return false;
            var bag = accessor.getComponent(ref,
                    com.inigmasgames.hytalerpg.ui.inventory.SpatialBagComponent.getComponentType());
            return bag == null || bag.mode(player.getUuid())
                    == com.inigmasgames.hytalerpg.ui.inventory.SpatialBagComponent.OwnershipMode.NATIVE;
        });
        var difficultyCombat=new com.inigmasgames.hytalerpg.execution.hytale.HytaleDifficultyCombat(skillTrace);
        enemyHealthBars=difficultyCombat.healthBars();
        difficultyCombat.healthBars().configureBossBars(bosses);
        var enemyBindings=com.inigmasgames.hytalerpg.enemies.EnemyNativeBindings.load();
        com.inigmasgames.hytalerpg.execution.hytale.NativeNpcTiming.configure(
                new com.inigmasgames.hytalerpg.execution.hytale.NativeNpcTiming.Provider(){
                    @Override public double movement(com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> actor,
                            com.hypixel.hytale.server.npc.role.Role role,
                            com.hypixel.hytale.component.ComponentAccessor<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> accessor){
                        var store=actor.getStore();
                        if(!actor.isValid()||!store.isInThread())return 1;
                        var id=accessor.getComponent(actor,com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
                        var npc=accessor.getComponent(actor,com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType());
                        if(id==null||npc==null)return 1;
                        var world=store.getExternalData().getWorld().getWorldConfig().getUuid();
                        var state=difficultyCombat.enemyState(world,id.getUuid()).orElse(null);
                        return state!=null&&state.descriptor().nativeRoleId().equals(npc.getRoleName())
                                &&state.descriptor().entityId().equals(id.getUuid())?state.providers().movementMultiplier():1;
                    }
                    @Override public com.inigmasgames.hytalerpg.execution.hytale.NativeNpcTiming.Recovery recovery(
                            com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> actor,
                            com.hypixel.hytale.server.npc.role.Role role,String actualRoot,
                            com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store){
                        if(!actor.isValid()||actor.getStore()!=store||!store.isInThread()
                                ||store.getComponent(actor,com.inigmasgames.hytalerpg.execution.hytale.EnemyStaging.getComponentType())!=null)return null;
                        var id=store.getComponent(actor,com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
                        var npc=store.getComponent(actor,com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType());
                        if(id==null||npc==null)return null;
                        var world=store.getExternalData().getWorld().getWorldConfig().getUuid();
                        var state=difficultyCombat.enemyState(world,id.getUuid()).orElse(null);
                        if(state==null||!state.descriptor().entityId().equals(id.getUuid())
                                ||!state.descriptor().nativeRoleId().equals(npc.getRoleName()))return null;
                        var binding=enemyBindings.requireActorRole(state.descriptor());
                        var profile=binding.recoveryProfile(actualRoot).orElse(null);
                        if(profile==null)return null;
                        return new com.inigmasgames.hytalerpg.execution.hytale.NativeNpcTiming.Recovery(actualRoot,
                                state.descriptor().nativeBindingRevision(),
                                com.inigmasgames.hytalerpg.execution.hytale.NativeNpcTiming.protectedSeconds(profile),
                                state.providers().recoveryRateMultiplier());
                    }
                });
        encounterRewards.configureCombat(difficultyCombat);
        enemyWorldAdmission=new com.inigmasgames.hytalerpg.enemies.EnemyWorldAdmission(
                encounterRewards::recoverEnemyWorld,
                new com.inigmasgames.hytalerpg.enemies.EnemyPackCapacity(
                        ()->worldConfiguration.snapshot().enemyBalance().promotion()),true);
        encounterRewards.configureEnemyAdmission(enemyWorldAdmission);
        combatKernel.statuses().configureEncounterAdmission(difficultyCombat::statusImmune,
                difficultyCombat::allowsExternalMutation,difficultyCombat::slowImmune);
        com.inigmasgames.hytalerpg.execution.hytale.SupportNativeEffects.configureEncounterProtection(difficultyCombat::blocksIncoming);
        encounterRewards.configureUnlockNotification((id,mode)->{
            var player=com.hypixel.hytale.server.core.universe.Universe.get().getPlayer(id);
            if(player!=null)player.sendMessage(com.hypixel.hytale.server.core.Message.raw(mode+" unlocked — all required golems completed. Recommended level: "+mode.recommendedLevel()+"+."));
        });
        com.inigmasgames.hytalerpg.difficulty.CampaignEncounterProjection.bind(getEntityStoreRegistry().registerComponent(
                com.inigmasgames.hytalerpg.difficulty.CampaignEncounterProjection.class,com.inigmasgames.hytalerpg.difficulty.CampaignEncounterProjection::new));
        difficultyIo=new java.util.concurrent.ThreadPoolExecutor(1,2,30,java.util.concurrent.TimeUnit.SECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(128),r->Thread.ofPlatform().daemon().name("RPG-difficulty-io").unstarted(r),new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        var difficultyWorlds=new com.inigmasgames.hytalerpg.difficulty.HytaleDifficultyWorlds(difficultyRuntime.worlds(),difficultyIo);
        var travelPort=new com.inigmasgames.hytalerpg.difficulty.HytaleDifficultyTravel(difficultyWorlds,loadouts,combatKernel,executions,(store,ref,player)->{
            skillExecutionSystem.prepareWorldTransfer(store,ref,player);abilityInputs.clear(player);
            supportSystem.detach(store,ref,"DIFFICULTY_TRANSFER");
        });
        var difficultyTravel=new com.inigmasgames.hytalerpg.difficulty.DifficultyTravel(rpgDataDirectory().resolve("difficulty-transfers.json"),travelPort,difficultyIo);
        executions.configureTransferGuard(difficultyTravel::busy);
        var portalRegistry=new com.inigmasgames.hytalerpg.difficulty.DifficultyPortals(rpgDataDirectory().resolve("difficulty-portals.json"));
        difficultyPortals=new com.inigmasgames.hytalerpg.difficulty.HytaleDifficultyPortals(portalRegistry,difficultyTravel);
        getEntityStoreRegistry().registerSystem(difficultyPortals);
        getEventRegistry().registerGlobal(com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent.class,event->{
            var world=event.getWorld();
            var binding=difficultyRuntime.worldLoaded(world.getWorldConfig().getUuid(),world.getName());
            LOGGER.atInfo().log("RPG_DIFFICULTY_WORLD world=%s binding=%s connectedProof=false",world.getName(),binding);
            enemyWorldAdmission.begin(world.getWorldConfig().getUuid()).whenComplete((inventory,error)->{
                if(error!=null)LOGGER.atWarning().withCause(error).log("RPG_ENEMY_WORLD_RECOVERY_REJECTED world=%s",world.getName());
                else LOGGER.atInfo().log("RPG_ENEMY_WORLD_RECOVERED world=%s births=%d packs=%d anchors=%d freshAdmission=%s connectedProof=false",
                        world.getName(),inventory.births().size(),inventory.packs().size(),inventory.anchors().size(),
                        enemyWorldAdmission.admits(world.getWorldConfig().getUuid()));
            });
        });
        for(var world:com.hypixel.hytale.server.core.universe.Universe.get().getWorlds().values()){
            difficultyRuntime.worldLoaded(world.getWorldConfig().getUuid(),world.getName());
            enemyWorldAdmission.begin(world.getWorldConfig().getUuid()).whenComplete((inventory,error)->{
                if(error!=null)LOGGER.atWarning().withCause(error).log("RPG_ENEMY_WORLD_RECOVERY_REJECTED world=%s",world.getName());
            });
        }
        supportSystem.configureEncounterRewards(encounterRewards);
        conversionSystem.configureRewardExclusion(encounterRewards::invalidateConverted);
        conversionSystem.configureEncounterConversionProtection(difficultyCombat::blocksConversion);
        getEntityStoreRegistry().registerSystem(conversionSystem);
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleConversionSystem.Removal(conversionSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleConversionSystem.Death(conversionSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleConversionSystem.DamageGuard(conversionSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleCorpseSystem(summonSystem.corpses(),bosses));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleCorpseSystem.Removal(summonSystem.corpses()));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleStatusDeathSystem(skillExecutionSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleStatusDeathSystem.Removal(skillExecutionSystem));
        uiProjection.configureActiveRemaining(skillExecutionSystem::activeSkillRemaining);
        uiProjection.configureResourceReadiness(combatKernel.resources(),executions::attunementStacks);
        rpgHud = new RpgHudCoordinator(uiProjection, uiTrace);
        rpgHud.configureEnemyTargets(difficultyCombat::enemyDisplay);
        rpgHud.configureQaNativeHealthbar(difficultyCombat::qaNativeHealthbar);
        rpgHud.configureEnemyHealthBars(difficultyCombat.healthBars());
        // R217: native Nameplate + viewer-local EntityStat Healthbar own the live
        // monster presentation. Keep the projected HUD code as a diagnostic path.
        executions.configureRejectionNotice(rpgHud::showSkillFailure);
        rpgHud.configureFinisherPips(executions::finisherPips);
        rpgHud.configureOwnerPublication(encounterRewards::ownerPublished);
        var rpgCommand=new RpgCommand(catalog, loadouts, combatKernel, combatTrace,
                uiProjection, allocation, uiTrace, rpgHud, skillTreeProjection, skillTreeMutations, nativeAbilities,
                rpgDataDirectory().resolve("skill-tree-port-bindings"));
        interactionTrace = new com.inigmasgames.hytalerpg.ui.trace.UiInteractionTrace(
                rpgDataDirectory().resolve("evidence").resolve("ui-trace"));
        com.inigmasgames.hytalerpg.ui.trace.UiInteractionTrace.install(interactionTrace);
        rpgCommand.addSubCommand(new com.inigmasgames.hytalerpg.commands.RpgUiTraceCommand(interactionTrace));
        gearQaTrace = new com.inigmasgames.hytalerpg.gear.GearQaTrace(
                rpgDataDirectory().resolve("evidence").resolve("gear-trace"));
        com.inigmasgames.hytalerpg.gear.GearQaTrace.install(gearQaTrace);
        gearQaTrace.actorOwner(actor -> summonSystem.registry().findByEntity(actor).map(lease -> lease.owner()).orElse(actor));
        sentinelQaTrace = new com.inigmasgames.hytalerpg.gear.GearQaTrace(
                rpgDataDirectory().resolve("evidence").resolve("sentinel-trace"),"Sentinel");
        rpgCommand.addSubCommand(new com.inigmasgames.hytalerpg.commands.RpgSentinelTraceCommand(sentinelQaTrace,summonSystem));
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class,
                event -> sentinelQaTrace.disconnect(event.getPlayerRef().getUuid()));
        rpgCommand.addSubCommand(new com.inigmasgames.hytalerpg.commands.RpgGearTraceCommand(gearQaTrace));
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class,
                event -> gearQaTrace.disconnect(event.getPlayerRef().getUuid()));
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class,
                event -> interactionTrace.disconnect(event.getPlayerRef().getUuid()));
        CanvasUI.setInteractionObserver(new com.inigmasgames.canvasui.api.CanvasInteractionObserver() {
            @Override public boolean enabled(java.util.UUID player) {
                return com.inigmasgames.hytalerpg.ui.trace.UiInteractionTrace.active(player);
            }
            @Override public Action begin(java.util.UUID player, String canvasId, String elementId,
                                          String controlType, String event, double x, double y) {
                var details = new java.util.LinkedHashMap<String, Object>();
                details.put("canvasId", canvasId); details.put("controlType", controlType);
                if (Double.isFinite(x)) details.put("pointerX", x);
                if (Double.isFinite(y)) details.put("pointerY", y);
                var action = interactionTrace.begin(player, "canvasui", "canvas." + elementId, event, details);
                action.stage("HIT_TEST", java.util.Map.of("resolvedTarget", elementId,
                        "nodeType", controlType));
                return result -> action.complete(result, java.util.Map.of("canvasId", canvasId));
            }
        });
        {
            inventoryEntryProbe = new com.inigmasgames.hytalerpg.ui.inventory.NativeInventoryEntryProbe(
                    uiProjection, uiTrace, rpgDataDirectory(), rpgCommand.inventoryEntry());
            if (com.inigmasgames.hytalerpg.ui.inventory.NativeInventoryEntryProbe.enabled(rpgDataDirectory()))
            rpgCommand.configureInventoryProbe(inventoryEntryProbe);
            getEntityStoreRegistry().registerSystem(
                    new com.inigmasgames.hytalerpg.ui.inventory.NativeInventoryEntryProbe.DeathCleanupSystem(inventoryEntryProbe));
            getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> inventoryEntryProbe.detach(event.getPlayerRef().getUuid()));
            getEventRegistry().registerGlobal(RemovedPlayerFromWorldEvent.class, event -> {
                var leaving = event.getHolder().getComponent(PlayerRef.getComponentType());
                if (leaving != null) inventoryEntryProbe.detach(leaving.getUuid());
            });
        }
        tabTraceProbe = new com.inigmasgames.hytalerpg.ui.inventory.TabTraceProbe(
                rpgDataDirectory().resolve("logs").resolve("rpg"));
        rpgCommand.configureTabTrace(tabTraceProbe);
        getEntityStoreRegistry().registerSystem(tabTraceProbe.new Tick());
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class,
                event -> tabTraceProbe.detach(event.getPlayerRef().getUuid()));
        nativeDropTraceProbe = new com.inigmasgames.hytalerpg.ui.inventory.NativeInventoryDropTraceProbe(
                rpgDataDirectory().resolve("logs").resolve("rpg"), inventoryEntryProbe);
        rpgCommand.addSubCommand(new com.inigmasgames.hytalerpg.commands.RpgNativeDropTraceCommand(nativeDropTraceProbe));
        getEntityStoreRegistry().registerSystem(nativeDropTraceProbe.new PlayerRequestObserver());
        getEntityStoreRegistry().registerSystem(nativeDropTraceProbe.new DropObserver());
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class,
                event -> nativeDropTraceProbe.detach(event.getPlayerRef().getUuid()));
        rpgCommand.addSubCommand(new com.inigmasgames.hytalerpg.commands.RpgUnsummonCommand(summonSystem,conversionSystem));
        worldSpawnDensity = new com.inigmasgames.hytalerpg.spawning.NativeWorldSpawnDensity(worldConfiguration);
        rpgCommand.addSubCommand(new com.inigmasgames.hytalerpg.commands.RpgSpawnsCommand(worldSpawnDensity));
        monsterSpawnTrace=new com.inigmasgames.hytalerpg.diagnostics.MonsterSpawnTrace(
                rpgDataDirectory().resolve("logs").resolve("rpg").resolve("monster-spawn-trace"));
        rpgCommand.addSubCommand(new com.inigmasgames.hytalerpg.commands.RpgSpawnTraceCommand(monsterSpawnTrace,worldSpawnDensity));
        getChunkStoreRegistry().registerSystem(worldSpawnDensity.new Tick());
        populationBalance=new com.inigmasgames.hytalerpg.spawning.NativePopulationBalance(
                ()->worldConfiguration.snapshot().population());
        getChunkStoreRegistry().registerSystem(populationBalance.new Tick());
        rpgCommand.addSubCommand(new com.inigmasgames.hytalerpg.commands.RpgWorldConfigCommand(
                worldConfiguration,worldSpawnDensity,populationBalance));
        rpgCommand.addSubCommand(new com.inigmasgames.hytalerpg.commands.RpgManaguardCommand(supportSystem));
        rpgCommand.addSubCommand(new com.inigmasgames.hytalerpg.commands.RpgDifficultyCommand(difficultyRuntime,loadouts,difficultyWorlds,difficultyTravel,portalRegistry,difficultyIo,travelPort,difficultyCombat));
        var gearEquipment=new com.inigmasgames.hytalerpg.gear.HytaleGearEquipment(loadouts);
        gearEquipment.configureDefense((actor,accessor)->{
            var player=accessor.getComponent(actor,com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
            double broken=supportSystem.runtime().finite().winningStat(player.getWorldUuid(),player.getUuid(),0,
                    com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects.Stat.DEFENSE_BREAK,System.nanoTime()/1e9);
            return new com.inigmasgames.hytalerpg.combat.defense.DefenseView.Contributions(0,0,0,broken);
        });
        com.inigmasgames.hytalerpg.gear.GearNativeItems.bind(gearEquipment);
        skillTreeProjection.configureGearEffects(gearEquipment::publishedEffects);
        uiProjection.configureGearEffects(gearEquipment::publishedEffects);
        boolean gearQa=java.nio.file.Files.isRegularFile(rpgDataDirectory().resolve("gear-qa-enabled"));
        var spatialQaMarker=rpgDataDirectory().resolve("spatial-inventory-qa-enabled");
        if(java.nio.file.Files.isRegularFile(spatialQaMarker)){
            if(!gearQa)throw new IllegalStateException("Spatial QA needs the existing managed gear receipt owner");
            getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.ui.inventory.SpatialStockPickupGuard(spatialQaMarker));
            rpgCommand.addSubCommand(new com.inigmasgames.hytalerpg.commands.RpgSpatialQaCommand(spatialQaMarker,()->gearLootRuntime));
        }
        if(Boolean.getBoolean("rpg.gear.candidateEconomy")||gearQa){
            var isolated=System.getProperty("rpg.gear.auditRoot","");
            if(!gearQa&&(isolated.isBlank()||!java.nio.file.Path.of(isolated).toAbsolutePath().normalize().equals(java.nio.file.Path.of("").toAbsolutePath().normalize())
                    ||!isolated.contains("gear-stage-1-smoke")))throw new IllegalStateException("Candidate gear economy requires an isolated gear audit save or explicit gear-qa-enabled marker");
            var generator=new com.inigmasgames.hytalerpg.gear.GearDropGenerator(com.inigmasgames.hytalerpg.gear.GearCatalog.load(),new com.inigmasgames.hytalerpg.gear.GearBindings(),com.inigmasgames.hytalerpg.gear.GearAffixRuntime.ENABLED);
            var loot=new com.inigmasgames.hytalerpg.gear.GearLootService(encounterStore,generator);
            gearLootRuntime=new com.inigmasgames.hytalerpg.gear.HytaleGearLoot(loot,loadouts,gearEquipment,spatialQaMarker);
            rpgCommand.inventoryEntry().configureGearTransfer(() -> gearLootRuntime);
            summonSystem.configureIronSentinel(gearLootRuntime);
            gearLootRuntime.configureProjectionNotification(encounterRewards::gearProjected);
            encounterRewards.configureGearLoot(loot,gearEquipment);
            encounterStore.configureGearDelivery(plan->{var outcomes=loot.deliverPicks(plan);for(int pick=0;pick<outcomes.size();pick++){
                var outcome=outcomes.get(pick);
                gearLootRuntime.acceptDelivery(outcome);
                try{encounterRewards.lootDelivered(plan,outcome,pick==outcomes.size()-1);}
                catch(RuntimeException diagnostic){getLogger().atWarning().log("RPG_GEAR_REWARD_TRACE_FAILED event=%s error=%s",plan.spawn().eventId(),diagnostic.toString());}
            }
            });
            com.inigmasgames.hytalerpg.gear.GearNativeItems.bindAuthority(gearLootRuntime::usable);
            gearEquipment.configureLootTick(gearLootRuntime::tick);
            getEntityStoreRegistry().registerSystem(gearLootRuntime.new IronDropSystem());
            rpgCommand.addSubCommand(gearLootRuntime.command());
            rpgCommand.addSubCommand(new com.inigmasgames.hytalerpg.commands.RpgSentinelAffixesCommand(summonSystem));
            getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.gear.GearNativeDeathDrops(difficultyCombat));
            getLogger().atInfo().log("RPG_GEAR_QA_ENABLED affixes="+com.inigmasgames.hytalerpg.gear.GearAffixRuntime.ENABLED.size()+" connectedAcceptance=OPEN");
        }
        getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
                .register(com.inigmasgames.hytalerpg.gear.ManagedGearDamageInteraction.TYPE,
                        com.inigmasgames.hytalerpg.gear.ManagedGearDamageInteraction.class,
                        com.inigmasgames.hytalerpg.gear.ManagedGearDamageInteraction.CODEC);
        getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
                .register(com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyDamageInteraction.TYPE,
                        com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyDamageInteraction.class,
                        com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyDamageInteraction.CODEC);
        getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
                .register(com.inigmasgames.hytalerpg.gear.NativeTwinAssaultGate.TYPE,
                        com.inigmasgames.hytalerpg.gear.NativeTwinAssaultGate.class,
                        com.inigmasgames.hytalerpg.gear.NativeTwinAssaultGate.CODEC);
        getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
                .register(com.inigmasgames.hytalerpg.gear.ManagedCarrierDamageInteraction.TYPE,
                        com.inigmasgames.hytalerpg.gear.ManagedCarrierDamageInteraction.class,
                        com.inigmasgames.hytalerpg.gear.ManagedCarrierDamageInteraction.CODEC);
        getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
                .register(com.inigmasgames.hytalerpg.gear.ManagedCarrierProjectile.TYPE,
                        com.inigmasgames.hytalerpg.gear.ManagedCarrierProjectile.class,
                        com.inigmasgames.hytalerpg.gear.ManagedCarrierProjectile.CODEC);
        com.inigmasgames.hytalerpg.gear.ManagedCarrierProjectile.bind(getEntityStoreRegistry().registerComponent(
                com.inigmasgames.hytalerpg.gear.ManagedCarrierProjectile.Snapshot.class,
                com.inigmasgames.hytalerpg.gear.ManagedCarrierProjectile.Snapshot::new));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.gear.ManagedCarrierProjectile.Launch());
        // SystemDependency validates its target at registration, even for Order.BEFORE.
        // Both projectile Impact systems reference Use; register Use before either one.
        getEntityStoreRegistry().registerSystem(gearEquipment.new Use());
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.gear.ManagedCarrierProjectile.Impact());
        rpgCommand.addSubCommand(new com.inigmasgames.hytalerpg.commands.RpgGearCommand(gearEquipment));
        getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
                .register(com.inigmasgames.hytalerpg.gear.ManagedGearProjectile.TYPE,
                        com.inigmasgames.hytalerpg.gear.ManagedGearProjectile.class,
                        com.inigmasgames.hytalerpg.gear.ManagedGearProjectile.CODEC);
        com.inigmasgames.hytalerpg.gear.ManagedGearProjectile.bind(getEntityStoreRegistry().registerComponent(
                com.inigmasgames.hytalerpg.gear.ManagedGearProjectile.Snapshot.class,
                com.inigmasgames.hytalerpg.gear.ManagedGearProjectile.Snapshot::new));
        com.inigmasgames.hytalerpg.gear.NativeAffixProjectilePathSystem.bind(getEntityStoreRegistry().registerComponent(
                com.inigmasgames.hytalerpg.gear.NativeAffixProjectileTravel.Path.class,
                com.inigmasgames.hytalerpg.gear.NativeAffixProjectileTravel.Path::new));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.gear.NativeAffixProjectilePathSystem.BeforePhysics());
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.gear.NativeAffixProjectilePathSystem());
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.gear.ManagedGearProjectile.Launch(playerHitRoots));
        getEntityStoreRegistry().registerSystem(gearEquipment.new Tick());
        // Item-skill availability runs before skill execution, whose system type must
        // already exist when Hytale validates the dependency at registration.
        getEntityStoreRegistry().registerSystem(skillExecutionSystem);
        itemAffixes=new com.inigmasgames.hytalerpg.execution.hytale.NativeItemAffixBindings(
                loadouts,skillExecutionSystem,HytaleDamageLifecycleSystems.AppliedReceipt::acceptedDamage,
                HytaleDamageLifecycleSystems.AppliedReceipt::procCoefficient,
                skillExecutionSystem.sentinelChildPort(summonSystem));
        getEntityStoreRegistry().registerSystem(itemAffixes.availabilityTick());
        getEntityStoreRegistry().registerSystem(itemAffixes.blockObserver());
        getEntityStoreRegistry().registerSystem(itemAffixes.removal());
        summonSystem.configureSentinelItemBindings(itemAffixes);
        getEntityStoreRegistry().registerSystem(summonSystem.sentinelBlockCostObserver());
        getEntityStoreRegistry().registerSystem(summonSystem.sentinelBlockObserver());
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.gear.ManagedGearProjectile.Impact());
        getEntityStoreRegistry().registerSystem(gearEquipment.new BeforeArmor());
        rpgCommand.addSubCommand(new com.inigmasgames.hytalerpg.commands.RpgEnemiesCommand(
                difficultyRuntime,com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.canonical(),
                enemyBindings,enemyWorldAdmission,()->packboundNativeHook!=null&&enemyProjectileHook!=null&&enemyDamageReceiptHook!=null,difficultyCombat,
                supportSystem.runtime().finite()));
        rpgCommand.addSubCommand(new com.inigmasgames.hytalerpg.commands.RpgSpawnCommand(()->enemyBirthOwner));
        getCommandRegistry().registerCommand(rpgCommand);
        if(Boolean.getBoolean("rpg.healingPresentationProbe")||com.inigmasgames.hytalerpg.execution.hytale.HealingProbePolicy.liveTestBuild()){
            healingProbe=new com.inigmasgames.hytalerpg.execution.hytale.HealingPresentationProbe(skillTrace);
            getCommandRegistry().registerCommand(new com.inigmasgames.hytalerpg.commands.HealingProbeCommand(healingProbe));
            getEntityStoreRegistry().registerSystem(healingProbe.new Tick());
            getEntityStoreRegistry().registerSystem(healingProbe.new Observe());
            LOGGER.atInfo().log("RPG_HEAL_PROBE revision=R032-AP enabled=true permission=inigmasgames.rpg.healingprobe liveTest=%s disposableWorldRequired=%s connectedProof=false",
                    com.inigmasgames.hytalerpg.execution.hytale.HealingProbePolicy.liveTestBuild(),!com.inigmasgames.hytalerpg.execution.hytale.HealingProbePolicy.liveTestBuild());
        }
        getCommandRegistry().registerCommand(new com.inigmasgames.hytalerpg.commands.RpgTraceCommand(skillTrace));
        if(Boolean.getBoolean("rpg.projectileSpawnAudit"))getCommandRegistry().registerCommand(
                new com.inigmasgames.hytalerpg.execution.hytale.NativeProjectileSpawnAuditCommand(skillExecutionSystem));
        var productionPowers=com.inigmasgames.hytalerpg.combat.power.NativeItemPowerRegistry.loadProduction();
        getLogger().atInfo().log("RPG_STAGE13_N_POWER_REGISTRY entries=%s policy=NATIVE_UNCHARGED_OR_EXPLICIT_MAGIC_SHIELD_REFERENCE traceLevel=%s connectedProof=false",
                productionPowers.all().size(),skillTrace.level());
        getEventRegistry().register(LoadedAssetsEvent.class, RootInteraction.class,
                NativeAbilityBridgeAudit::onRootInteractionsLoaded);
        getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.Gather(combatTrace,combatKernel.statuses(),difficultyCombat.healthBars()));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.combat.hytale.NativeDamageLeafReceipts.Gather());
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.ManagedGearGather());
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.combat.hytale.HytaleConditionalDamage.GearGather(
                combatKernel.statuses(),skillExecutionSystem::activePeriodicKinds,(victim,stats)->
                    com.inigmasgames.hytalerpg.combat.hytale.NativeNormalHealthMaximum.value(stats)));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.gear.NativeAffixBlockCostSystem());
        com.inigmasgames.hytalerpg.gear.NativeAffixLightSystem.bind(getEntityStoreRegistry().registerComponent(
                com.inigmasgames.hytalerpg.gear.NativeAffixLightSystem.State.class,
                com.inigmasgames.hytalerpg.gear.NativeAffixLightSystem.State::new));
        if(System.getProperty("rpg.gear.lightMetresPerNativeUnit")!=null
                ||System.getProperty("rpg.gear.lightMaximumNativeRadius")!=null) {
            var calibration=com.inigmasgames.hytalerpg.gear.NativeAffixLightProjection.Calibration.fromSystemProperties();
            getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.gear.NativeAffixLightSystem(calibration));
        } else LOGGER.atInfo().log("GEAR_LIGHT_CALIBRATION_PENDING affix=WA-155 unit=metres productionRoll=GATED");
        gearDurability=new com.inigmasgames.hytalerpg.gear.NativeAffixDurabilityInstallation();
        getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.Filter(combatTrace));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.combat.hytale.NativeDamageLeafReceipts.Before());
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.combat.hytale.NativeDamageLeafReceipts.After());
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.NativeStatusPhysicalMiss(combatKernel.statuses()));
        getEntityStoreRegistry().registerSystem(supportSystem);
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleSupportSystem.Absorb(supportSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems.Shield(supportSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.GearResistanceFilter());
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems.HealthCap(supportSystem));
        // Registration order satisfies SystemDependency.validate; the declared
        // before/after edges still determine actual damage pipeline execution.
        getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.BeforeAbsorption());
        getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.BeforeApplication());
        gearRecovery=new com.inigmasgames.hytalerpg.execution.hytale.GearRecoveryBindings(
                combatKernel,supportSystem,summonSystem,bosses);
        gearSignatures=new com.inigmasgames.hytalerpg.execution.hytale.GearSignatureBindings(difficultyCombat,summonSystem);
        encounterRewards.configureSignatureKill(gearSignatures);
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.gear.NativeGearAttackAcceptance.Prechain());
        getEntityStoreRegistry().registerSystem(gearSignatures.dispatch());
        getEntityStoreRegistry().registerSystem(gearSignatures.defenseFilter());
        getEntityStoreRegistry().registerSystem(gearSignatures.rawBefore());
        getEntityStoreRegistry().registerSystem(gearSignatures.rawAfter());
        getEntityStoreRegistry().registerSystem(gearSignatures.new Removal());
        getEntityStoreRegistry().registerSystem(gearSignatures.new Death());
        gearReceiptObservers.add(gearSignatures.receipt());
        skillExecutionSystem.configureRecovery(gearRecovery.committedAttacks());
        encounterRewards.configureRecovery(gearRecovery.creditedDeaths());
        gearReceiptObservers.add(gearRecovery.observer());
        gearStatuses=new com.inigmasgames.hytalerpg.execution.hytale.GearStatusBindings(combatKernel.statuses(),bosses,skillExecutionSystem,
                (target,buffer)->com.inigmasgames.hytalerpg.gear.GearNativeItems.recipientEffects(target,buffer));
        skillExecutionSystem.configureStatusBindings(gearStatuses);
        getEntityStoreRegistry().registerSystem(gearStatuses.nativeAcceptance());
        gearReceiptObservers.add(gearStatuses.observer());
        gearReceiptObservers.add(itemAffixes.applicationObserver());
        getEntityStoreRegistry().registerSystem(gearStatuses.tickSystem());
        getEntityStoreRegistry().registerSystem(gearStatuses.removal());
        getEntityStoreRegistry().registerSystem(gearRecovery.tickSystem());
        getEntityStoreRegistry().registerSystem(gearRecovery.deathSystem());
        getEntityStoreRegistry().registerSystem(gearRecovery.removalSystem());
        getEventRegistry().registerGlobal(com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent.class,
                event->{if(!event.isCancelled()){
                    var worldId=event.getWorld().getWorldConfig().getUuid();
                    gearRecovery.cancelWorld(worldId);gearSignatures.clearWorld(worldId);
                    gearStatuses.clearWorld(worldId);itemAffixes.worldUnload(worldId);summonSystem.worldUnload(worldId);
                    difficultyCombat.healthBars().forgetWorld(worldId);
                }});
        getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.Application(combatTrace,
                (receipt,target,source,buffer)->{
                    for(var observer:gearReceiptObservers)observer.observed(receipt,target,source,buffer);
                }));
        getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.Inspect(
                combatTrace, combatKernel.hostileCombat(), difficultyCombat.healthBars(), immersiveEvents));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.NativeAffixFearBreak(combatKernel.statuses()));
        getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.ReactionObserver(skillExecutionSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.NativeBasicAttackObserver.Start(skillExecutionSystem.nativeBasics()));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.NativeBasicAttackObserver.Before(skillExecutionSystem.nativeBasics()));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.NativeBasicAttackObserver.After(skillExecutionSystem.nativeBasics()));
        com.inigmasgames.hytalerpg.execution.hytale.MonsterPeriodicProjection.bind(getEntityStoreRegistry().registerComponent(
                com.inigmasgames.hytalerpg.execution.hytale.MonsterPeriodicProjection.class,
                com.inigmasgames.hytalerpg.execution.hytale.MonsterPeriodicProjection::new));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.MonsterPeriodicProjection.Tick(skillExecutionSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.MonsterPeriodicProjection.Removal(skillExecutionSystem));
        LOGGER.atInfo().log("RPG_STAGE13_NATIVE_BASIC_HOOK start=INTERACTION_CHAIN_START before=POST_FILTER after=POST_APPLY scope=AUDITED_MELEE recovery=ROOT_HEALTH_LOSS finisher=ROOT_HEALTH_LOSS connectedProof=false");
        getEntityStoreRegistry().registerSystem(new HomeRestorationTickSystem(combatKernel.homeRestoration(),
                combatKernel.hostileCombat(), combatKernel.resources()));
        getEntityStoreRegistry().registerSystem(new RpgHudTickSystem(rpgHud));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.EnemyHealthBarPresentation.AnchorFollow(
                difficultyCombat.healthBars()));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.EnemyHealthBarPresentation.NamePackets(
                difficultyCombat.healthBars()));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.EnemyHealthBarPresentation.QaCanonicalQueueAudit(
                difficultyCombat.healthBars()));
        var enemyWeaponVisuals=new com.inigmasgames.hytalerpg.execution.hytale.HytaleEnemyWeaponVisuals();
        getEntityStoreRegistry().registerSystem(enemyWeaponVisuals);
        getEntityStoreRegistry().registerSystem(new NativeAbilityProjectionTickSystem(nativeAbilities));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleSupportSystem.Removal(supportSystem));
        com.inigmasgames.hytalerpg.execution.hytale.SupportEffectProjection.bind(getEntityStoreRegistry().registerComponent(
                com.inigmasgames.hytalerpg.execution.hytale.SupportEffectProjection.class,
                com.inigmasgames.hytalerpg.execution.hytale.SupportEffectProjection::new));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportNativeEffects.Projection(supportSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportNativeEffects.Retreat(supportSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportNativeEffects.NativeOutgoing(supportSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportNativeEffects.DirectDamageBreak(supportSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportNativeEffects.Removal(supportSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems.BeforeApply());
        var sharedReflection=new com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems.Reflect(supportSystem);
        getEntityStoreRegistry().registerSystem(sharedReflection);
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleRetaliationSystem(skillExecutionSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards.Tracking(encounterRewards));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemySpawnGroups.Capture());
        var enemyStagingRecovery=new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyStagingRecovery(
                encounterRewards,enemyWorldAdmission);
        getEntityStoreRegistry().registerSystem(enemyStagingRecovery);
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards.Inspect(encounterRewards));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards.PlayerInjuries(encounterRewards));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards.HealthObservation(encounterRewards));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards.Death(encounterRewards));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards.Delivery(encounterRewards));
        getEntityStoreRegistry().registerSystem(difficultyCombat.new Outgoing());
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyStatuses.Knockback(difficultyCombat));
        getEntityStoreRegistry().registerSystem(difficultyCombat.new IncomingProtection());
        getEntityStoreRegistry().registerSystem(difficultyCombat.new Resistance());
        enemyArmor=com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyArmor.install(
                com.hypixel.hytale.server.core.universe.world.storage.EntityStore.REGISTRY,
                new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyArmor(
                        (store,target)->difficultyCombat.physicalDefense(store,target,supportSystem.runtime().finite()),difficultyCombat::elementalDefense));
        var enemySourceEffects=new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyOutgoingEffects();
        enemyOutgoingEffects=enemySourceEffects.install(com.hypixel.hytale.server.core.universe.world.storage.EntityStore.REGISTRY);
        var enemyResources=new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyResources(combatKernel,bosses,combatTrace);
        var enemyStatuses=new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyStatuses(skillExecutionSystem,supportSystem,difficultyCombat,combatKernel,bosses,combatTrace);
        enemyActions=new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyActions(enemySourceEffects,enemyResources.andThen(enemyStatuses));
        enemyReflectiveEffects=new com.inigmasgames.hytalerpg.enemies.EnemyReflectiveEffects();
        var enemyReflectiveReaction=new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyReflectiveReaction(
                difficultyCombat,enemyWorldAdmission,enemyActions,bosses,enemyReflectiveEffects);
        sharedReflection.configureEnemyReaction(enemyReflectiveReaction);
        com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyDamageInteraction.configure(enemyActions::scope);
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyActions.Start(enemyActions));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyProjectileDamage(
                difficultyCombat,enemyActions,enemyBindings));
        var enemyBalance=com.inigmasgames.hytalerpg.enemies.EnemyBalance.canonical();
        try{
            packboundNativeHook=com.inigmasgames.hytalerpg.execution.hytale.PackboundNativeMutationBridge
                    .tryInstall(difficultyCombat).orElse(null);
        }catch(LinkageError missingPatch){
            packboundNativeHook=null;
            LOGGER.atWarning().withCause(missingPatch)
                    .log("RPG_PACKBOUND_NATIVE_PATCH_LINKAGE_FAILED promotion=false");
        }
        if(packboundNativeHook!=null)try{
            enemyProjectileHook=com.inigmasgames.hytale.patch.NativeProjectileReceiptHook.install(enemyActions::attachProjectile);
        }catch(LinkageError|RuntimeException missingHook){
            enemyProjectileHook=null;
            LOGGER.atWarning().withCause(missingHook).log("RPG_ENEMY_PROJECTILE_RECEIPT_HOOK_UNAVAILABLE promotion=false");
        }
        if(packboundNativeHook!=null)try{
            enemyDamageReceiptHook=com.inigmasgames.hytale.patch.NativeDamageReceiptHook.install(context->{
                String enemy=enemyActions.originalDamageReceipt(context);
                if(enemy!=null)return enemy;
                try{return skillExecutionSystem.nativeBasics().originalDamageReceipt(context);}
                catch(RuntimeException unavailable){return null;} // Receipt failure cannot alter an ordinary player hit.
            });
        }catch(LinkageError|RuntimeException missingHook){
            enemyDamageReceiptHook=null;
            LOGGER.atWarning().withCause(missingHook).log("RPG_ENEMY_DAMAGE_RECEIPT_HOOK_UNAVAILABLE promotion=false");
        }
        skillExecutionSystem.nativeBasics().configureNativeDamageReceipt(enemyDamageReceiptHook!=null);
        var enemyDecision=new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyBirthDecision(
                encounterRewards,enemyBindings,enemyBalance,worldConfiguration::snapshot);
        var enemyReservation=new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyBirthReservation(
                enemyDecision,enemyWorldAdmission,encounterRewards);
        var enemyAttachment=new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyBirthAttachment(
                encounterRewards,
                new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyStateAttachment(supportSystem.runtime().finite()),
                new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyActionAttachment(
                        encounterRewards,difficultyCombat,enemyActions,supportSystem.runtime().finite(),enemyBindings),enemyBalance,enemyBindings);
        var enemyPublication=new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyBirthPublication(
                encounterRewards,difficultyCombat,enemyWorldAdmission,
                com.inigmasgames.hytalerpg.enemies.EnemyVisualVariants.canonical(),enemyBindings,enemyWeaponVisuals);
        var enemyBirthOwner=new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyBirthOwner(
                enemyBindings,enemyWorldAdmission,encounterRewards,difficultyCombat,enemyActions,
                enemyReservation,enemyDecision,enemyAttachment,enemyPublication,packboundNativeHook!=null&&enemyProjectileHook!=null&&enemyDamageReceiptHook!=null);
        this.enemyBirthOwner=enemyBirthOwner;
        encounterRewards.configureQaEncounters(enemyBirthOwner.qaEncounters());
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyBirthOwner.Removal(enemyBirthOwner));
        var enemyWholeBirthRecovery=new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyWholeBirthRecovery(
                enemyWorldAdmission,encounterRewards,enemyBindings,enemyAttachment,enemyPublication,enemyBirthOwner);
        getEntityStoreRegistry().registerSystem(enemyWholeBirthRecovery);
        var enemyWorldRebind=new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyWorldRebind(
                enemyWorldAdmission,enemyBirthOwner,packboundNativeHook!=null,enemyDamageReceiptHook!=null);
        enemyStagingRecovery.onReconciled(enemyWorldRebind::begin);
        enemyWholeBirthRecovery.onPublished(enemyWorldRebind::begin);
        getEventRegistry().registerGlobal(com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent.class,
                event->enemyWorldRebind.begin(event.getWorld()));
        for(var existingWorld:com.hypixel.hytale.server.core.universe.Universe.get().getWorlds().values())
            enemyWorldRebind.begin(existingWorld);
        enemySpawnGroups=new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemySpawnGroups(
                com.hypixel.hytale.server.spawning.SpawningPlugin.get(),enemyBirthOwner)
                .install(com.hypixel.hytale.server.core.universe.world.storage.ChunkStore.REGISTRY);
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyEngagement(
                difficultyCombat,supportSystem.runtime().finite(),
                enemyBindings,enemyBalance));
        var enemyAura=new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyAura(
                difficultyCombat,supportSystem,enemyBindings,enemyBalance);
        difficultyCombat.configureEnemyPackObserver(enemyAura::reconcilePack);
        getEntityStoreRegistry().registerSystem(enemyAura);
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyAura.Death(enemyAura));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyAura.Removal(enemyAura));

        LOGGER.atInfo().log("RPG_STAGE12_NATIVE_REWARDS spawn=LEGACY_WORLD_SPAWN contribution=POST_APPLY_HEALTH_LOSS death=NATIVE_DEATH_COMPONENT deliveryBudget=8_per_second party=SOLO_ONLY connectedProof=false");
        LOGGER.atInfo().log("RPG_STAGE12_SUPPORT_CREDIT healing=POST_NATIVE_WRITE absorption=ACTUAL_CONSUMPTION partyProvider=%s mastery=true connectedProof=false",encounterRewards.partyAvailability());
        LOGGER.atInfo().log("RPG_STAGE12_MASTERY damage=INSPECT_BEFORE_DEATH control=NATIVE_STATE_CHANGE healing=HOSTILE_INJURY_ONLY rootDedup=DURABLE sustainedIntervalSeconds=5 movementAvoidance=UNAVAILABLE connectedProof=false");
        LOGGER.atInfo().log("RPG_STAGE12_ACQUISITION playerSchema=%d verifiedLearningBindings=%d pity=DURABLE spending=SAME_REWARD_AUTHORITY respec=TEN_SECONDS_AND_NO_PENDING_CAST import=IDS_AND_FIXED_LAYOUT_ONLY connectedProof=false",com.inigmasgames.hytalerpg.progress.RpgPlayerState.CURRENT_SCHEMA,encounterRewards.verifiedLearningBindings());
        com.inigmasgames.hytalerpg.execution.hytale.AreaStatusProjection.bind(getEntityStoreRegistry().registerComponent(
                com.inigmasgames.hytalerpg.execution.hytale.AreaStatusProjection.class,
                com.inigmasgames.hytalerpg.execution.hytale.AreaStatusProjection::new));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.AreaStatusProjectionSystem(combatKernel.statuses()));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.AreaNpcControlSystem(combatKernel.statuses()));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.StatusRemovalSystem(combatKernel.statuses()));
        LOGGER.atInfo().log("RPG_STAGE05_READY revision=%s skills=%d passives=%d pilots=%d projectiles=%d schema=%d balance=%s skillTrace=%s uiTrace=%s abilityInput=Ability2->skill01,Ability3->skill02 nativeAbility4=NATIVE_ABILITY4_UNAVAILABLE nativeAbility1=SIGNATURE_UNTOUCHED abilityHud=NATIVE_HYTALE_ONLY skillTreeHotkey=BLOCKED_PUBLIC_API uiOpen=%s entitlementMode=%s",
                BuildIdentity.REVISION, catalog.skills().size(), catalog.passives().size(),
                runtimeProfiles.all().size(), Stage04SkillProfiles.EXPECTED_STAGE05_PILOTS,
                com.inigmasgames.hytalerpg.progress.RpgPlayerState.CURRENT_SCHEMA,
                combatKernel.balance().profileId, skillTrace.path(), uiTrace.path(),
                new CommandOnlyRpgUiOpenInputAdapter().availability(),
                configuration.developmentEntitlements() ? "DEVELOPMENT" : "PRODUCTION");
        RpgDiagnosticsModule.initialize(rpgDataDirectory());
        inboundWatcher = PacketAdapters.registerInbound((PlayerPacketWatcher) (playerRef, packet) -> {
            abilityInputs.observe(playerRef, packet);
            RpgDiagnosticsModule.observeRaw(playerRef, packet);
        });
        outboundWatcher = PacketAdapters.registerOutbound((PlayerPacketWatcher) bosses::observe);
        getEventRegistry().registerGlobal(PlayerMouseButtonEvent.class, RpgDiagnosticsModule::onButton);
        getEventRegistry().registerGlobal(PlayerMouseMotionEvent.class, RpgDiagnosticsModule::onMotion);
        loadouts.enableNonblockingReads();
        encounterRewards.configureOwnerMaintenance(()->{executions.pollCancelledPersistence();executions.pollChannelCooldowns();combatKernel.cooldowns().pollMaintenance();supportSystem.runtime().pollMaintenance();});
        executions.configureDurableAbandon(context->{
            if(context.profile().support()!=null)supportSystem.runtime().abandonDurable(context);
            if(context.profile().conversion()!=null)conversionSystem.abandonDurable(context);
        });
        persistenceReady=new com.inigmasgames.hytalerpg.execution.hytale.HytalePlayerPersistenceReady(loadouts,(store,ref,playerRef,entity,statMap)->{
            if(gearLootRuntime!=null&&!gearLootRuntime.prepareInitialView().isDone())return false;
            if(gearLootRuntime!=null&&gearLootRuntime.prepareInitialView().isCompletedExceptionally())return false;
            if(!supportSystem.connectionReady(store,ref))return false;
            var view=loadouts.getPresentationView(playerRef.getUuid());
            var slots=store.getComponent(ref,com.hypixel.hytale.server.core.inventory.InventoryComponent.AbilitySlots.getComponentType());
            if(slots!=null)nativeAbilities.install(playerRef.getUuid(),slots);
            EnumMap<RpgAttribute,Integer> raw=new EnumMap<>(RpgAttribute.class);
            for(var attribute:RpgAttribute.values())raw.put(attribute,view.state().attributes.getOrDefault(attribute.name(),10));
            new DerivedStatEntityAdapter().apply(statMap,combatKernel.derivedStats().derive(raw));
            // Publish equipped validity, capacity and affixes in this owner task before the HUD.
            gearEquipment.project(ref,store);
            rpgHud.install(playerRef,entity,statMap);
            return true;
        });
        getEntityStoreRegistry().registerSystem(persistenceReady);
        getEventRegistry().registerAsync(com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent.class,
                incoming->incoming.thenCompose(event->{
                    if(event.getWorld()==null)return java.util.concurrent.CompletableFuture.completedFuture(event);
                    var hitRoot=playerHitRoots.open(event.getWorld().getWorldConfig().getUuid(),event.getPlayerRef().getUuid(),event.getPlayerRef())
                            .handle((ignored,error)->{if(error!=null)LOGGER.atWarning().withCause(error)
                                    .log("PLAYER_HIT_ROOT_RESERVATION_UNAVAILABLE player=%s",event.getPlayerRef().getUuid());
                                return (Void)null;}).toCompletableFuture();
                    var player=persistenceReady.preConnect(event.getPlayerRef(),event.getWorld().getWorldConfig().getUuid());
                    var custody=gearLootRuntime==null?java.util.concurrent.CompletableFuture.<Void>completedFuture(null):gearLootRuntime.prepareInitialView();
                    return java.util.concurrent.CompletableFuture.allOf(player,custody,hitRoot).orTimeout(30,java.util.concurrent.TimeUnit.SECONDS)
                            .whenComplete((ignored,error)->{if(error!=null){persistenceReady.detach(event.getPlayerRef());
                                playerHitRoots.detach(event.getPlayerRef().getUuid(),event.getPlayerRef());}})
                            .thenApply(ignored->event);
                }));
        if(rpgCommand.getSubCommand("readypath") instanceof com.inigmasgames.hytalerpg.commands.RpgReadyPathCommand diagnostic)
            diagnostic.configurePreparation(persistenceReady::preparationStatus);
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            var ref = event.getPlayerRef();
            var playerRef = ref.getStore().getComponent(ref,
                    com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
            if (playerRef != null) {
                // Reconnect/restart cannot erase a recent combat restriction; observe a fresh bounded quiet window.
                combatKernel.hostileCombat().markHostile(playerRef.getUuid());
                abilityInputs.clear(playerRef.getUuid());
                skillExecutionSystem.cancel(playerRef.getUuid(), "PLAYER_READY_RESET");
                supportSystem.beginReady(playerRef.getUuid());
                persistenceReady.begin(playerRef,playerRef.getWorldUuid());
                if(gearLootRuntime!=null)gearLootRuntime.recoverSpatialOnReady(
                        ref.getStore(),ref,playerRef,ref.getStore().getExternalData().getWorld());
                difficultyPortals.reconnect(playerRef.getUuid());
            }
        });
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            UUID player = event.getPlayerRef().getUuid();
            com.inigmasgames.hytalerpg.gear.ManagedGearDamageInteraction.forget(player);
            if(gearRecovery!=null)gearRecovery.cancel(player);
            if(gearSignatures!=null)gearSignatures.clearActor(player);
            if(itemAffixes!=null)itemAffixes.detach(player);
            com.inigmasgames.hytalerpg.gear.ManagedCarrierDamageInteraction.forget(player);
            var sentinelWorld=com.hypixel.hytale.server.core.universe.Universe.get().getWorld(event.getPlayerRef().getWorldUuid());
            if(sentinelWorld!=null)sentinelWorld.execute(()->summonSystem.dormancyForTransfer(sentinelWorld.getEntityStore().getStore(),player));
            difficultyPortals.disconnect(player);
            if(healingProbe!=null)healingProbe.detach(player);
            persistenceReady.detach(event.getPlayerRef());
            if(playerHitRoots!=null)playerHitRoots.detach(player,event.getPlayerRef());
            try { rpgHud.teardown(player, "PLAYER_DISCONNECT"); }
            catch (RuntimeException error) {
                LOGGER.atWarning().withCause(error).log("RPG HUD disconnect teardown failed player=%s", player);
            }
            nativeAbilities.detach(player, "PLAYER_DISCONNECT");
            abilityInputs.clear(player);
            bosses.clear(player);
            skillExecutionSystem.cancel(player, "PLAYER_DISCONNECT");
            try{combatKernel.cooldowns().detach(player);}
            catch(RuntimeException failure){LOGGER.atWarning().withCause(failure).log("RPG_COOLDOWN_DISCONNECT_SAVE_FAILED player=%s",player);}
        });
        getEventRegistry().registerGlobal(DrainPlayerFromWorldEvent.class, event -> {
            var playerRef = event.getHolder().getComponent(
                    com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
            if (playerRef != null) {
                com.inigmasgames.hytalerpg.gear.ManagedGearDamageInteraction.forget(playerRef.getUuid());
                if(gearRecovery!=null)gearRecovery.cancel(playerRef.getUuid());
                if(gearSignatures!=null)gearSignatures.clearActor(playerRef.getUuid());
                if(itemAffixes!=null)itemAffixes.detach(playerRef.getUuid());
                com.inigmasgames.hytalerpg.gear.ManagedCarrierDamageInteraction.forget(playerRef.getUuid());
                var sentinelWorld=com.hypixel.hytale.server.core.universe.Universe.get().getWorld(playerRef.getWorldUuid());
                if(sentinelWorld!=null)sentinelWorld.execute(()->summonSystem.dormancyForTransfer(sentinelWorld.getEntityStore().getStore(),playerRef.getUuid()));
                nativeAbilities.detach(playerRef.getUuid(), "WORLD_DRAIN");
                persistenceReady.drain(playerRef);
                if(playerHitRoots!=null)playerHitRoots.detach(playerRef.getUuid(),playerRef);
                if(healingProbe!=null)healingProbe.detach(playerRef.getUuid());
                abilityInputs.clear(playerRef.getUuid());
                bosses.clear(playerRef.getUuid());
                skillExecutionSystem.cancel(playerRef.getUuid(), "WORLD_DRAIN");
            }
        });
        for (var diagnostic : RpgDiagnosticsModule.commands()) {
            getCommandRegistry().registerCommand(diagnostic);
        }

        }
    }

    private void startRpg() {
        try (var readyPathSpan = com.inigmasgames.hywind.readypath.ReadyPathProbe.span("BOOT_RPG_NATIVE_VALIDATION", null)) {
        for(var golem:com.inigmasgames.hytalerpg.difficulty.GolemMilestones.load().golems())com.hypixel.hytale.server.npc.NPCPlugin.get().validateSpawnableRole(golem.roleId());
        for(String model:java.util.List.of("RPG_Difficulty_Portal","Invisible_Projectile"))
            if(com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset.getAssetMap().getAsset(model)==null)throw new IllegalStateException("DIFFICULTY_MODEL_MISSING:"+model);
        LOGGER.atInfo().log("RPG_DIFFICULTY_ASSETS golems=5 portalModels=2 result=PASS connectedProof=false");
        // Assets are resolved before plugin start, including in --bare smoke mode (no BootEvent).
        com.inigmasgames.hytalerpg.input.NativeRuneControl.auditAssets();
        com.inigmasgames.hytalerpg.execution.hytale.AreaStatusProjectionSystem.requireAssets();
        com.inigmasgames.hytalerpg.execution.hytale.SupportNativeEffects.requireAssets();
        com.inigmasgames.hytalerpg.execution.hytale.HytaleSupportSystem.requireMantleAssets();
        com.inigmasgames.hytalerpg.input.NativeSupportTetherAudit.requireAssets();
        var spireParticles=com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem.getAssetMap();
        if(spireParticles.getAsset("Undead_Digging")==null)
            throw new IllegalStateException("LIGHTNING_SPIRE_EMERGENCE_PARTICLE_UNRESOLVED");
        if(spireParticles.getAsset("Hywind_Lightning_Spire_Shockwave")==null)
            throw new IllegalStateException("LIGHTNING_SPIRE_SHOCKWAVE_PARTICLE_UNRESOLVED");
        var spireModels=com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset.getAssetMap();
        for(int frame=0;frame<8;frame++)if(spireModels.getAsset("Hywind_Lightning_Spire_Shock_Frame_"+frame)==null)
            throw new IllegalStateException("LIGHTNING_SPIRE_SHOCK_FRAME_UNRESOLVED:"+frame);
        LOGGER.atInfo().log("RPG_LIGHTNING_SPIRE_ASSETS emergenceParticle=Undead_Digging shockwaveParticle=Hywind_Lightning_Spire_Shockwave shockFrames=8 frameMilliseconds=100 result=PASS connectedProof=false");
        com.inigmasgames.hytalerpg.execution.hytale.NativeStrikeActionLock.requireAsset();
        var projectileAudit=com.inigmasgames.hytalerpg.execution.hytale.NativeProjectileAssetAudit.requireAssets(
                java.util.Objects.requireNonNull(validatedRuntimeProfiles, "RPG setup must precede asset validation"));
        LOGGER.atInfo().log("RPG_STAGE13_PROJECTILE_ASSETS result=PASS %s",new com.google.gson.Gson().toJson(projectileAudit));
        var movementAudit=com.inigmasgames.hytalerpg.execution.hytale.NativeMovementAssetAudit.requireAssets(
                java.util.Objects.requireNonNull(validatedRuntimeProfiles, "RPG setup must precede asset validation"));
        LOGGER.atInfo().log("RPG_STAGE13_MOVEMENT_ASSETS result=PASS %s",new com.google.gson.Gson().toJson(movementAudit));
        LOGGER.atInfo().log("RPG_STAGE13_NATIVE_BASIC_PATHS result=PASS %s",new com.google.gson.Gson().toJson(
                com.inigmasgames.hytalerpg.execution.hytale.NativeBasicAttackPaths.auditInstalledMelee()));
        LOGGER.atInfo().log("RPG_STAGE13_STRIKE_ASSETS ordinaryQueryLimit=64 fullHeight=2.5 finiteAnimationProfiles=7 actionLockAssets=2 result=PASS connectedProof=false");
        LOGGER.atInfo().log("RPG_MANAGED_FIRE_BINDING result=PASS contract=NORMALIZED_FIRE_V1 connectedProof=false %s",
                new com.google.gson.Gson().toJson(com.inigmasgames.hytalerpg.combat.hytale.NativeWeaponFireProducer.auditInstalledBinding()));
        com.inigmasgames.hytalerpg.execution.hytale.NativeHitProcAssets.requireAssets();
        LOGGER.atInfo().log("RPG_STAGE11_HIT_PROC_ASSETS bleedVisual=RPG_Bleed_Visual nativeDamage=false movementUnchanged=true result=PASS connectedProof=false");
        LOGGER.atInfo().log("RPG_STAGE11_STRIKE_ACTION_LOCK asset=RPG_Strike_Action_Lock disabledInteractions=6 movementUnchanged=true result=PASS connectedProof=false");
        LOGGER.atInfo().log("RPG_STAGE06_ASSETS revision=%s areaProfiles=%d requiredStatusAssets=10 nativeDamageChannels=2 result=PASS connectedProof=false",
                BuildIdentity.REVISION, Stage04SkillProfiles.EXPECTED_STAGE06_PROFILES);
        for(String cause:java.util.List.of("Wind","Lightning","RPG_Void","RPG_Nature","RPG_Necrotic"))
            if(com.hypixel.hytale.server.core.modules.entity.damage.DamageCause.getAssetMap().getAsset(cause)==null)
                throw new IllegalStateException("Missing Stage 08 native damage channel: "+cause);
        LOGGER.atInfo().log("RPG_STAGE08_ASSETS revision=%s connectionProfiles=%d nativeDamageChannels=5 result=PASS connectedProof=false",
                BuildIdentity.REVISION,Stage04SkillProfiles.EXPECTED_STAGE08_PROFILES);
        LOGGER.atInfo().log("RPG_STAGE09_READY revision=%s supportProfiles=%d playerSchema=%d regenAdapter=NATIVE_ENTRY_DECORATOR reservationProjection=STATIC_MAX allyPolicy=SELF_OR_NATIVE_FRIENDLY connectedProof=false",
                BuildIdentity.REVISION,Stage04SkillProfiles.EXPECTED_STAGE09_PROFILES,com.inigmasgames.hytalerpg.progress.RpgPlayerState.CURRENT_SCHEMA);
        com.hypixel.hytale.server.npc.NPCPlugin.get().validateSpawnableRole("RPG_Summon_Wolf");
        var encounterRegistry=com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry.load();
        LOGGER.atInfo().log("RPG_ENEMY_REGISTRY loaded=%d canonical=%d aliases=%d invalid=0 biomes=%d evidence=ASSET_AUDITED_CONNECTED_UNVERIFIED",
                encounterRegistry.roles().size()+encounterRegistry.aliases().size(),encounterRegistry.roles().size(),encounterRegistry.aliases().size(),encounterRegistry.biomes().size());
        com.hypixel.hytale.server.npc.NPCPlugin.get().validateSpawnableRole("RPG_Summon_Crawler");
        com.hypixel.hytale.server.npc.NPCPlugin.get().validateSpawnableRole("RPG_Summon_Broodling");
        LOGGER.atInfo().log("RPG_STAGE10_BATCH_ROLES count=3 result=PASS connectedProof=false");
        com.hypixel.hytale.server.npc.NPCPlugin.get().validateSpawnableRole("RPG_Summon_Decoy");
        LOGGER.atInfo().log("RPG_STAGE10_DECOY_ROLE appearance=Mannequin attacks=0 result=PASS connectedProof=false");
        com.hypixel.hytale.server.npc.NPCPlugin.get().validateSpawnableRole("RPG_Summon_Skeleton_Archer");
        if(com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig.getAssetMap().getAsset("Projectile_Config_RPG_Summon_Arrow")==null)
            throw new IllegalStateException("Missing RPG summon arrow continuation carrier");
        LOGGER.atInfo().log("RPG_STAGE10_SKELETON_SUMMON role=RPG_Summon_Skeleton_Archer fleeDistance=0 followLeash=20 projectile=Projectile_Config_RPG_Summon_Arrow result=PASS connectedProof=false");
        LOGGER.atInfo().log("RPG_STAGE10_ASSETS revision=%s summonProfiles=%d role=RPG_Summon_Wolf result=PASS connectedProof=false",
                BuildIdentity.REVISION,Stage04SkillProfiles.EXPECTED_STAGE10_PROFILES);

        }
    }

    private void shutdownRpg() {
        RuntimeException enemyTeardown=null;
        try{if(enemyHealthBars!=null)enemyHealthBars.shutdown();}
        catch(RuntimeException failure){enemyTeardown=enemyTeardownFailure(enemyTeardown,"ENEMY_PRESENTATION_TEARDOWN",failure);}
        finally{enemyHealthBars=null;}
        try{if(enemySpawnGroups!=null)enemySpawnGroups.close();}
        catch(RuntimeException failure){enemyTeardown=enemyTeardownFailure(enemyTeardown,"ENEMY_SPAWN_GROUPS_TEARDOWN",failure);}
        finally{enemySpawnGroups=null;}
        try{if(enemyProjectileHook!=null)enemyProjectileHook.close();}
        catch(Exception failure){enemyTeardown=enemyTeardownFailure(enemyTeardown,"ENEMY_PROJECTILE_HOOK_TEARDOWN",failure);}
        finally{enemyProjectileHook=null;}
        try{if(enemyDamageReceiptHook!=null)enemyDamageReceiptHook.close();}
        catch(Exception failure){enemyTeardown=enemyTeardownFailure(enemyTeardown,"ENEMY_DAMAGE_RECEIPT_HOOK_TEARDOWN",failure);}
        finally{enemyDamageReceiptHook=null;}
        try{if(enemyActions!=null)enemyActions.clear();}
        catch(RuntimeException failure){enemyTeardown=enemyTeardownFailure(enemyTeardown,"ENEMY_ACTION_TEARDOWN",failure);}
        finally{enemyActions=null;}
        try{com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyDamageInteraction.configure(call->null);}
        catch(RuntimeException failure){enemyTeardown=enemyTeardownFailure(enemyTeardown,"ENEMY_DAMAGE_SCOPE_TEARDOWN",failure);}
        try{if(packboundNativeHook!=null)packboundNativeHook.close();}
        catch(RuntimeException failure){enemyTeardown=enemyTeardownFailure(enemyTeardown,"ENEMY_PACKBOUND_HOOK_TEARDOWN",failure);}
        finally{packboundNativeHook=null;}
        try{com.inigmasgames.hytalerpg.execution.hytale.NativeNpcTiming.configure(
                new com.inigmasgames.hytalerpg.execution.hytale.NativeNpcTiming.Provider(){});}
        catch(RuntimeException failure){enemyTeardown=enemyTeardownFailure(enemyTeardown,"ENEMY_TIMING_TEARDOWN",failure);}
        try{com.inigmasgames.hytalerpg.execution.hytale.SupportNativeEffects.configureEncounterProtection((store,target)->false);}
        catch(RuntimeException failure){enemyTeardown=enemyTeardownFailure(enemyTeardown,"ENEMY_PROTECTION_TEARDOWN",failure);}
        try{if(enemyOutgoingEffects!=null)enemyOutgoingEffects.close();}
        catch(RuntimeException failure){enemyTeardown=enemyTeardownFailure(enemyTeardown,"ENEMY_OUTGOING_TEARDOWN",failure);}
        finally{enemyOutgoingEffects=null;}
        try{if(enemyArmor!=null)enemyArmor.close();}
        catch(RuntimeException failure){enemyTeardown=enemyTeardownFailure(enemyTeardown,"ENEMY_ARMOR_TEARDOWN",failure);}
        finally{enemyArmor=null;}
        enemyReflectiveEffects=null;
        gearReceiptObservers.clear();
        gearStatuses=null;itemAffixes=null;
        if(gearSignatures!=null){gearSignatures.close();gearSignatures=null;}
        if(gearDurability!=null){gearDurability.close();gearDurability=null;}
        startupState=StartupState.STOPPING;
        immersiveEvents.close();
        immersiveEvents = com.inigmasgames.hywind.compat.RpgGameplayEventPublisher.NO_OP;
        if (nativeDropTraceProbe != null) nativeDropTraceProbe.close();
        if (inventoryEntryProbe != null) inventoryEntryProbe.close();
        if (tabTraceProbe != null) tabTraceProbe.close();
        if(persistenceReady!=null)persistenceReady.close();
        if (worldSpawnDensity != null) { worldSpawnDensity.close(); worldSpawnDensity = null; }
        if (populationBalance != null) { populationBalance.close(); populationBalance = null; }
        worldConfiguration=null;
        if (monsterSpawnTrace != null) { monsterSpawnTrace.close(); monsterSpawnTrace = null; }
        com.inigmasgames.hytalerpg.gear.GearNativeItems.bind(null);
        com.inigmasgames.hytalerpg.gear.GearNativeItems.bindAuthority(com.inigmasgames.hytalerpg.gear.GearInstance::qaOnly);
        if(gearLootRuntime!=null){gearLootRuntime.close();gearLootRuntime=null;}
        if(difficultyIo!=null){difficultyIo.shutdown();difficultyIo=null;}
        if(healingProbe!=null){healingProbe.close();healingProbe=null;}
        if (inboundWatcher != null) {
            PacketAdapters.deregisterInbound(inboundWatcher);
            inboundWatcher = null;
        }
        if (outboundWatcher != null) {
            PacketAdapters.deregisterOutbound(outboundWatcher);
            outboundWatcher = null;
        }
        RpgDiagnosticsModule.close();
        CanvasUI.setInteractionObserver(null);
        if (interactionTrace != null) { interactionTrace.close(); interactionTrace = null; }
        if (gearQaTrace != null) { gearQaTrace.close(); gearQaTrace = null; }
        if (sentinelQaTrace != null) { sentinelQaTrace.close(); sentinelQaTrace = null; }
        if (nativeAbilities != null) { nativeAbilities.close(); nativeAbilities = null; }
        if (rpgHud != null) { rpgHud.close(); rpgHud = null; }
        skillExecutionSystem = null;
        abilityInputs = null;
        try { if (encounterRewards != null) { var closing=encounterRewards;encounterRewards=null;closing.close(); } }
        finally {
            try { if (encounterStore != null) { var closing=encounterStore;encounterStore=null;closing.close(); } }
            finally {
                try { if (loadouts != null) { var closing=loadouts;loadouts=null;closing.close(); } }
                finally {
                    try { if (uiTrace != null) { var closing=uiTrace;uiTrace=null;closing.close(); } }
                    finally { if (skillTrace != null) { var closing=skillTrace;skillTrace=null;closing.close(); } }
                }
            }
        }
        combatKernel = null;
        LOGGER.atInfo().log("HYTALE_RPG_SHUTDOWN revision=%s stage=%s", BuildIdentity.REVISION, BuildIdentity.STAGE);
        if(enemyTeardown!=null)throw enemyTeardown;
    }

    private static RuntimeException enemyTeardownFailure(RuntimeException first,String boundary,Exception failure){
        var wrapped=new IllegalStateException(boundary,failure);
        if(first==null)return wrapped;
        first.addSuppressed(wrapped);return first;
    }

    private void shutdownCanvas() {
        CursorHudProbeService installedCursor = cursorProbe;
        if (cursorProbe != null) {
            cursorProbe.close();
            cursorProbe = null;
        }
        if (canvasService != null) {
            canvasService.close();
            CanvasUI.uninstall(canvasService, installedCursor);
            canvasService = null;
        }
        LOGGER.atInfo().log("CANVASUI_SHUTDOWN revision=%s owner=HYARPG", CanvasUI.REVISION);
    }

    @Override
    protected void start() {
        try (var readyPathSpan = com.inigmasgames.hywind.readypath.ReadyPathProbe.span("BOOT_HYARPG_START", null)) {
        startupState=StartupState.STARTING;
        try {
            // Mandatory native feedback is validated before RPG world work starts.
            com.inigmasgames.hytalerpg.execution.hytale.NativeStrikeFeedback.requireAssets();
            super.start();
            startRpg();
            startupState=StartupState.RUNNING;
            LOGGER.atInfo().log("HYARPG_STARTED version=%s revision=%s", getManifest().getVersion(), BuildIdentity.REVISION);
        } catch (RuntimeException | Error failure) {
            startupState=StartupState.STOPPING;
            rollbackPartialSetup();
            throw failure;
        }

        }
    }

    @Override
    protected void shutdown() {
        startupState=StartupState.STOPPING;
        try {
            if (rpgSetup) shutdownRpg();
        } finally {
            rpgSetup = false;
            try {
                if (canvasSetup) shutdownCanvas();
            } finally {
                canvasSetup = false;
                if (tavernsSetup) super.shutdown();
                tavernsSetup = false;
            }
        }
        startupState=StartupState.STOPPED;
        LOGGER.atInfo().log("HYARPG_SHUTDOWN version=%s revision=%s", getManifest().getVersion(), BuildIdentity.REVISION);
    }

    private void rollbackPartialSetup() {
        startupState=StartupState.STOPPING;
        try {
            if (rpgSetup) shutdownRpg();
        } catch (RuntimeException cleanupFailure) {
            LOGGER.atSevere().withCause(cleanupFailure).log("HYARPG_PARTIAL_CLEANUP_FAILED module=GAMEPLAY");
        } finally {
            rpgSetup = false;
        }
        try {
            if (canvasSetup) shutdownCanvas();
        } catch (RuntimeException cleanupFailure) {
            LOGGER.atSevere().withCause(cleanupFailure).log("HYARPG_PARTIAL_CLEANUP_FAILED module=PRESENTATION");
        } finally {
            canvasSetup = false;
        }
        try {
            if (tavernsSetup) super.shutdown();
        } catch (RuntimeException cleanupFailure) {
            LOGGER.atSevere().withCause(cleanupFailure).log("HYARPG_PARTIAL_CLEANUP_FAILED module=TAVERNS");
        } finally {
            tavernsSetup = false;
        }
        startupState=StartupState.STOPPED;
    }
}
