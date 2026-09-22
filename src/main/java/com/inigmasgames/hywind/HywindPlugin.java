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

/** Single production bootstrap for Hywind gameplay, presentation and characters. */
public final class HywindPlugin extends TavernsPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private PacketFilter inboundWatcher;
    private PacketFilter outboundWatcher;
    private com.inigmasgames.hytalerpg.execution.hytale.HealingPresentationProbe healingProbe;
    private RpgSkillTraceService skillTrace;
    private RpgLoadoutService loadouts;
    private RpgCombatKernel combatKernel;
    private RpgUiTraceService uiTrace;
    private RpgHudCoordinator rpgHud;
    private HytaleAbilitySkillInputAdapter abilityInputs;
    private NativeAbilityProjectionService nativeAbilities;
    private HytaleSkillExecutionSystem skillExecutionSystem;
    private com.inigmasgames.hytalerpg.progress.FileEncounterStore encounterStore;
    private com.inigmasgames.hytalerpg.execution.hytale.HytalePlayerPersistenceReady persistenceReady;
    private com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards encounterRewards;
    private CanvasService canvasService;
    private CursorHudProbeService cursorProbe;
    private boolean charactersSetup;
    private boolean canvasSetup;
    private boolean rpgSetup;

    /** Trusted server-plugin integration only; no command or packet can supply party membership. */
    public void configurePartyMembership(com.inigmasgames.hytalerpg.progress.PartyMembershipProvider provider){
        if(encounterRewards==null)throw new IllegalStateException("RPG_ENCOUNTER_SERVICE_NOT_READY");
        encounterRewards.configurePartyProvider(provider);
    }

    public HywindPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected Path charactersDataDirectory() {
        return legacyDataDirectory("ImmersiveNPCs");
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
        if (parent == null) throw new IllegalStateException("HYWIND_DATA_ROOT_PARENT_MISSING");
        return parent.resolve(directoryName).normalize();
    }

    @Override
    protected void setup() {
        try {
            charactersSetup = true;
            super.setup();
            canvasSetup = true;
            setupCanvas();
            rpgSetup = true;
            setupRpg();
            LOGGER.atInfo().log("HYWIND_DATA_ROOTS bootstrap=%s gameplay=%s presentation=%s characters=%s taverns=%s",
                    getDataDirectory(), rpgDataDirectory(), canvasDataDirectory(), charactersDataDirectory(),
                    tavernsDataDirectory());
            LOGGER.atInfo().log("HYWIND_SETUP version=%s revision=%s modules=GAMEPLAY,PRESENTATION,CHARACTERS,INTELLIGENCE,TAVERNS",
                    getManifest().getVersion(), BuildIdentity.REVISION);
        } catch (RuntimeException | Error failure) {
            rollbackPartialSetup();
            throw failure;
        }
    }

    private void setupCanvas() {
        canvasService = new CanvasService();
        cursorProbe = new CursorHudProbeService(canvasDataDirectory());
        getEntityStoreRegistry().registerSystem(new CanvasInputGuard.InteractionStartSystem(cursorProbe.inputGuard()));
        getEntityStoreRegistry().registerSystem(new CanvasInputGuard.ActiveSlotRequestSystem(cursorProbe.inputGuard()));
        getEntityStoreRegistry().registerSystem(new CanvasInputGuard.DropItemSystem(cursorProbe.inputGuard()));
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
        LOGGER.atInfo().log("CANVASUI_SETUP revision=%s version=%s hytale=%s inputBackend=%s capabilities=%s cursorBackend=%s cursorCapabilities=%s guardBoundary=INTERACTION_CHAIN_START cursorProbe=PHASE_A5_B_GATE owner=HYWIND",
                CanvasUI.REVISION, getManifest().getVersion(), CanvasUI.HYTALE_VERSION,
                canvasService.inputBackend().id(), canvasService.inputBackend().capabilities(),
                com.inigmasgames.canvasui.rendering.HytaleCursorHudInputBackend.INSTANCE.id(),
                com.inigmasgames.canvasui.rendering.HytaleCursorHudInputBackend.INSTANCE.capabilities());
    }

    private void setupRpg() {
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
        nativeAbilities = new NativeAbilityProjectionService(loadouts, runtimeProfiles, skillTrace, loadouts::ready);
        loadouts.addMutationListener(nativeAbilities::onLoadoutMutation);
        abilityInputs = new HytaleAbilitySkillInputAdapter(nativeAbilities::observeInput);
        abilityInputs.useNativeExecution();
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
        supportSystem.configureEncounterRewards(encounterRewards);
        conversionSystem.configureRewardExclusion(encounterRewards::invalidateConverted);
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
        rpgHud.configureFinisherPips(executions::finisherPips);
        rpgHud.configureOwnerPublication(encounterRewards::ownerPublished);
        var rpgCommand=new RpgCommand(catalog, loadouts, combatKernel, combatTrace,
                uiProjection, allocation, uiTrace, rpgHud, skillTreeProjection, skillTreeMutations, nativeAbilities,
                rpgDataDirectory().resolve("skill-tree-port-bindings"));
        rpgCommand.addSubCommand(new com.inigmasgames.hytalerpg.commands.RpgManaguardCommand(supportSystem));
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
        getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.Gather(combatTrace,combatKernel.statuses()));
        getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.Filter(combatTrace));
        getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.Application(combatTrace));
        getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.Inspect(combatTrace, combatKernel.hostileCombat()));
        getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.ReactionObserver(skillExecutionSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.NativeBasicAttackObserver.Start(skillExecutionSystem.nativeBasics()));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.NativeBasicAttackObserver.Before(skillExecutionSystem.nativeBasics()));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.NativeBasicAttackObserver.After(skillExecutionSystem.nativeBasics()));
        LOGGER.atInfo().log("RPG_STAGE13_NATIVE_BASIC_HOOK start=INTERACTION_CHAIN_START before=POST_FILTER after=POST_APPLY scope=AUDITED_MELEE recovery=ROOT_HEALTH_LOSS finisher=ROOT_HEALTH_LOSS connectedProof=false");
        getEntityStoreRegistry().registerSystem(new HomeRestorationTickSystem(combatKernel.homeRestoration(),
                combatKernel.hostileCombat(), combatKernel.resources()));
        getEntityStoreRegistry().registerSystem(new RpgHudTickSystem(rpgHud));
        getEntityStoreRegistry().registerSystem(new NativeAbilityProjectionTickSystem(nativeAbilities));
        getEntityStoreRegistry().registerSystem(skillExecutionSystem);
        getEntityStoreRegistry().registerSystem(supportSystem);
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleSupportSystem.Absorb(supportSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleSupportSystem.Removal(supportSystem));
        com.inigmasgames.hytalerpg.execution.hytale.SupportEffectProjection.bind(getEntityStoreRegistry().registerComponent(
                com.inigmasgames.hytalerpg.execution.hytale.SupportEffectProjection.class,
                com.inigmasgames.hytalerpg.execution.hytale.SupportEffectProjection::new));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportNativeEffects.Projection(supportSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportNativeEffects.Retreat(supportSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportNativeEffects.NativeOutgoing(supportSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportNativeEffects.DirectDamageBreak(supportSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportNativeEffects.Removal(supportSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems.Shield(supportSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems.HealthCap(supportSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems.BeforeApply());
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems.Reflect(supportSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleRetaliationSystem(skillExecutionSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards.Tracking(encounterRewards));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards.Inspect(encounterRewards));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards.PlayerInjuries(encounterRewards));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards.HealthObservation(encounterRewards));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards.Death(encounterRewards));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards.Delivery(encounterRewards));
        LOGGER.atInfo().log("RPG_STAGE12_NATIVE_REWARDS spawn=LEGACY_WORLD_SPAWN contribution=POST_APPLY_HEALTH_LOSS death=NATIVE_DEATH_COMPONENT deliveryBudget=8_per_second party=SOLO_ONLY connectedProof=false");
        LOGGER.atInfo().log("RPG_STAGE12_SUPPORT_CREDIT healing=POST_NATIVE_WRITE absorption=ACTUAL_CONSUMPTION partyProvider=%s mastery=true connectedProof=false",encounterRewards.partyAvailability());
        LOGGER.atInfo().log("RPG_STAGE12_MASTERY damage=INSPECT_BEFORE_DEATH control=NATIVE_STATE_CHANGE healing=HOSTILE_INJURY_ONLY rootDedup=DURABLE sustainedIntervalSeconds=5 movementAvoidance=UNAVAILABLE connectedProof=false");
        LOGGER.atInfo().log("RPG_STAGE12_ACQUISITION playerSchema=9 verifiedLearningBindings=%d pity=DURABLE spending=SAME_REWARD_AUTHORITY respec=TEN_SECONDS_AND_NO_PENDING_CAST import=IDS_AND_FIXED_LAYOUT_ONLY connectedProof=false",encounterRewards.verifiedLearningBindings());
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
            if(!supportSystem.connectionReady(store,ref))return false;
            var view=loadouts.getPresentationView(playerRef.getUuid());
            var slots=store.getComponent(ref,com.hypixel.hytale.server.core.inventory.InventoryComponent.AbilitySlots.getComponentType());
            if(slots!=null)nativeAbilities.install(playerRef.getUuid(),slots);
            EnumMap<RpgAttribute,Integer> raw=new EnumMap<>(RpgAttribute.class);
            for(var attribute:RpgAttribute.values())raw.put(attribute,view.state().attributes.getOrDefault(attribute.name(),10));
            new DerivedStatEntityAdapter().apply(statMap,combatKernel.derivedStats().derive(raw));
            rpgHud.install(playerRef,entity,statMap);
            return true;
        });
        getEntityStoreRegistry().registerSystem(persistenceReady);
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
                persistenceReady.begin(playerRef.getUuid(),playerRef.getWorldUuid());
            }
        });
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            UUID player = event.getPlayerRef().getUuid();
            if(healingProbe!=null)healingProbe.detach(player);
            persistenceReady.detach(player);
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
                nativeAbilities.detach(playerRef.getUuid(), "WORLD_DRAIN");
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

    private void startRpg() {
        // Assets are resolved before plugin start, including in --bare smoke mode (no BootEvent).
        com.inigmasgames.hytalerpg.input.NativeRuneControl.auditAssets();
        com.inigmasgames.hytalerpg.execution.hytale.AreaStatusProjectionSystem.requireAssets();
        com.inigmasgames.hytalerpg.execution.hytale.SupportNativeEffects.requireAssets();
        com.inigmasgames.hytalerpg.execution.hytale.HytaleSupportSystem.requireMantleAssets();
        com.inigmasgames.hytalerpg.input.NativeSupportTetherAudit.requireAssets();
        if(com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem.getAssetMap()
                .getAsset("Hywind_Lightning_Spire_Emergence")==null)
            throw new IllegalStateException("LIGHTNING_SPIRE_EMERGENCE_PARTICLE_UNRESOLVED");
        LOGGER.atInfo().log("RPG_LIGHTNING_SPIRE_ASSETS emergenceParticle=Hywind_Lightning_Spire_Emergence result=PASS connectedProof=false");
        com.inigmasgames.hytalerpg.execution.hytale.NativeStrikeActionLock.requireAsset();
        com.inigmasgames.hytalerpg.execution.hytale.NativeStrikeFeedback.requireAssets();
        var projectileAudit=com.inigmasgames.hytalerpg.execution.hytale.NativeProjectileAssetAudit.requireAssets(
                Stage04SkillProfiles.loadCanonical(com.inigmasgames.hytalerpg.content.RpgCatalog.loadCanonical()));
        LOGGER.atInfo().log("RPG_STAGE13_PROJECTILE_ASSETS result=PASS %s",new com.google.gson.Gson().toJson(projectileAudit));
        var movementAudit=com.inigmasgames.hytalerpg.execution.hytale.NativeMovementAssetAudit.requireAssets(
                Stage04SkillProfiles.loadCanonical(com.inigmasgames.hytalerpg.content.RpgCatalog.loadCanonical()));
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
        for(var role:encounterRegistry.roles())com.hypixel.hytale.server.npc.NPCPlugin.get().validateSpawnableRole(role.roleId());
        LOGGER.atInfo().log("RPG_STAGE12_ENCOUNTER_REGISTRY roles=%d biomes=%d rankAuthority=RPG_PROFILE awardHook=true connectedProof=false",
                encounterRegistry.roles().size(),encounterRegistry.biomes().size());
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

    private void shutdownRpg() {
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
        LOGGER.atInfo().log("CANVASUI_SHUTDOWN revision=%s owner=HYWIND", CanvasUI.REVISION);
    }

    @Override
    protected void start() {
        try {
            super.start();
            startRpg();
            LOGGER.atInfo().log("HYWIND_STARTED version=%s revision=%s", getManifest().getVersion(), BuildIdentity.REVISION);
        } catch (RuntimeException | Error failure) {
            rollbackPartialSetup();
            throw failure;
        }
    }

    @Override
    protected void shutdown() {
        try {
            if (rpgSetup) shutdownRpg();
        } finally {
            rpgSetup = false;
            try {
                if (canvasSetup) shutdownCanvas();
            } finally {
                canvasSetup = false;
                if (charactersSetup) super.shutdown();
                charactersSetup = false;
            }
        }
        LOGGER.atInfo().log("HYWIND_SHUTDOWN version=%s revision=%s", getManifest().getVersion(), BuildIdentity.REVISION);
    }

    private void rollbackPartialSetup() {
        try {
            if (rpgSetup) shutdownRpg();
        } catch (RuntimeException cleanupFailure) {
            LOGGER.atSevere().withCause(cleanupFailure).log("HYWIND_PARTIAL_CLEANUP_FAILED module=GAMEPLAY");
        } finally {
            rpgSetup = false;
        }
        try {
            if (canvasSetup) shutdownCanvas();
        } catch (RuntimeException cleanupFailure) {
            LOGGER.atSevere().withCause(cleanupFailure).log("HYWIND_PARTIAL_CLEANUP_FAILED module=PRESENTATION");
        } finally {
            canvasSetup = false;
        }
        try {
            if (charactersSetup) super.shutdown();
        } catch (RuntimeException cleanupFailure) {
            LOGGER.atSevere().withCause(cleanupFailure).log("HYWIND_PARTIAL_CLEANUP_FAILED module=CHARACTERS,TAVERNS");
        } finally {
            charactersSetup = false;
        }
    }
}
