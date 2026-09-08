package com.inigmasgames.hytalerpg.phase00;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseMotionEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketWatcher;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
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
import java.util.Map;
import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;

import javax.annotation.Nonnull;

/** RPG plugin entrypoint for the retained staged runtime and diagnostics. */
public final class Phase00Plugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private PacketFilter inboundWatcher;
    private PacketFilter outboundWatcher;
    private RpgSkillTraceService skillTrace;
    private RpgLoadoutService loadouts;
    private RpgCombatKernel combatKernel;
    private RpgUiTraceService uiTrace;
    private RpgHudCoordinator rpgHud;
    private HytaleAbilitySkillInputAdapter abilityInputs;
    private NativeAbilityProjectionService nativeAbilities;
    private HytaleSkillExecutionSystem skillExecutionSystem;

    public Phase00Plugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("HYTALE_RPG_SETUP revision=%s version=%s hytale=%s stage=%s combatEnabled=true",
                BuildIdentity.REVISION, BuildIdentity.VERSION, BuildIdentity.HYTALE_VERSION,
                BuildIdentity.STAGE);
        RpgCatalog catalog = RpgCatalog.loadCanonical();
        SkillTraceConfiguration configuration = SkillTraceConfiguration.load();
        skillTrace = new RpgSkillTraceService(getDataDirectory().resolve("logs").resolve("rpg").resolve("skill-trace.jsonl"), configuration);
        var repository = new FileRpgPlayerStateRepository(getDataDirectory().resolve("players"));
        var compatibility = new CompatibilityService();
        var graphService = new RpgLinkGraphService(catalog, compatibility);
        combatKernel = RpgCombatKernel.createProduction();
        var compiler = new LinkCompiler(catalog, graphService, compatibility, combatKernel.balance());
        loadouts = new RpgLoadoutService(catalog, repository, graphService, compiler,
                new OwnershipEntitlementPolicy(configuration.developmentEntitlements()), skillTrace);
        CombatTrace combatTrace = new CombatTrace(skillTrace);
        uiTrace = new RpgUiTraceService(getDataDirectory().resolve("logs").resolve("rpg").resolve("ui-trace.jsonl"));
        var uiProjection = new RpgUiProjectionService(catalog, loadouts, combatKernel.derivedStats(), combatKernel.cooldowns());
        var staticLayout = new StaticSkillTreeLayout();
        var skillTreeProjection = new RpgSkillTreeProjectionService(catalog, loadouts, staticLayout,
                configuration.developmentEntitlements());
        var skillTreeMutations = new RpgSkillTreeMutationService(loadouts, staticLayout);
        var allocation = new AttributeAllocationService(loadouts);
        var runtimeProfiles = Stage04SkillProfiles.loadCanonical(catalog);
        nativeAbilities = new NativeAbilityProjectionService(loadouts, runtimeProfiles, skillTrace);
        loadouts.addMutationListener(nativeAbilities::onLoadoutMutation);
        abilityInputs = new HytaleAbilitySkillInputAdapter(nativeAbilities::observeInput);
        abilityInputs.useNativeExecution();
        getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
                .register(com.inigmasgames.hytalerpg.input.NativeSkillActivationInteraction.TYPE,
                        com.inigmasgames.hytalerpg.input.NativeSkillActivationInteraction.class,
                        com.inigmasgames.hytalerpg.input.NativeSkillActivationInteraction.codec(abilityInputs));
        var runeControl = new com.inigmasgames.hytalerpg.input.NativeRuneControl(
                getDataDirectory().resolve("diagnostics").resolve("native-rune-control"),
                configuration.developmentEntitlements(), skillTrace);
        nativeAbilities.configureControl(runeControl);
        abilityInputs.configureControl(runeControl::inputSuppressed, runeControl::observe);
        var reactions = new ReactionWindowService(System::nanoTime);
        var executions = new SkillExecutionService(loadouts, runtimeProfiles, combatKernel,
                SkillExecutorRegistry.runtime(), new SkillInstanceLifecycle(), skillTrace);
        var vfx = new LinkTreeVfxService(new HtDevLibVfxAdapter(), Map.of());
        var bosses = new HytaleBossBarTracker();
        skillExecutionSystem = new HytaleSkillExecutionSystem(abilityInputs, executions, combatKernel,
                combatTrace, reactions, vfx, bosses);
        var supportSystem=skillExecutionSystem.configureSupport(loadouts);
        var summonSystem=skillExecutionSystem.configureSummons(getDataDirectory().resolve("corpse-consumption"));
        com.inigmasgames.hytalerpg.execution.hytale.SummonProjection.bind(getEntityStoreRegistry().registerComponent(
                com.inigmasgames.hytalerpg.execution.hytale.SummonProjection.class,
                com.inigmasgames.hytalerpg.execution.hytale.SummonProjection::new));
        getEntityStoreRegistry().registerSystem(summonSystem);
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem.Removal(summonSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem.Death(summonSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem.DamageGuard(summonSystem));
        com.inigmasgames.hytalerpg.execution.hytale.ConversionProjection.bind(getEntityStoreRegistry().registerComponent(
                com.inigmasgames.hytalerpg.execution.hytale.ConversionProjection.class,
                com.inigmasgames.hytalerpg.execution.hytale.ConversionProjection::new));
        var conversionSystem=skillExecutionSystem.configureConversions();
        getEntityStoreRegistry().registerSystem(conversionSystem);
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleConversionSystem.Removal(conversionSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleConversionSystem.Death(conversionSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleConversionSystem.DamageGuard(conversionSystem));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleCorpseSystem(summonSystem.corpses(),bosses));
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleCorpseSystem.Removal(summonSystem.corpses()));
        rpgHud = new RpgHudCoordinator(uiProjection, uiTrace);
        var rpgCommand=new RpgCommand(catalog, loadouts, combatKernel, combatTrace,
                uiProjection, allocation, uiTrace, rpgHud, skillTreeProjection, skillTreeMutations, nativeAbilities);
        rpgCommand.addSubCommand(new com.inigmasgames.hytalerpg.commands.RpgManaguardCommand(supportSystem));
        getCommandRegistry().registerCommand(rpgCommand);
        getEventRegistry().register(LoadedAssetsEvent.class, RootInteraction.class,
                NativeAbilityBridgeAudit::onRootInteractionsLoaded);
        getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.Gather(combatTrace,combatKernel.statuses()));
        getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.Filter(combatTrace));
        getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.Application(combatTrace));
        getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.Inspect(combatTrace, combatKernel.hostileCombat()));
        getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.ReactionObserver(skillExecutionSystem));
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
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems.BeforeApply());
        getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems.Reflect(supportSystem));
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
        MouseProbeService.initialize(getDataDirectory());
        inboundWatcher = PacketAdapters.registerInbound((PlayerPacketWatcher) (playerRef, packet) -> {
            abilityInputs.observe(playerRef, packet);
            MouseProbeService.observeRaw(playerRef, packet);
        });
        outboundWatcher = PacketAdapters.registerOutbound((PlayerPacketWatcher) bosses::observe);
        getEventRegistry().registerGlobal(PlayerMouseButtonEvent.class, MouseProbeService::onButton);
        getEventRegistry().registerGlobal(PlayerMouseMotionEvent.class, MouseProbeService::onMotion);
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            var ref = event.getPlayerRef();
            var playerRef = ref.getStore().getComponent(ref,
                    com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
            if (playerRef != null) {
                abilityInputs.clear(playerRef.getUuid());
                skillExecutionSystem.cancel(playerRef.getUuid(), "PLAYER_READY_RESET");
                var view = loadouts.getLoadout(playerRef.getUuid());
                var abilitySlots = ref.getStore().getComponent(ref,
                        com.hypixel.hytale.server.core.inventory.InventoryComponent.AbilitySlots.getComponentType());
                if (abilitySlots != null) nativeAbilities.install(playerRef.getUuid(), abilitySlots);
                else LOGGER.atWarning().log("RPG native AbilitySlots unavailable player=%s", playerRef.getUuid());
                EntityStatMap statMap = ref.getStore().getComponent(ref, EntityStatMap.getComponentType());
                if (statMap != null) {
                    supportSystem.ready(ref.getStore(),ref);
                    EnumMap<RpgAttribute, Integer> raw = new EnumMap<>(RpgAttribute.class);
                    for (RpgAttribute attribute : RpgAttribute.values())
                        raw.put(attribute, view.state().attributes.getOrDefault(attribute.name(), 10));
                    new DerivedStatEntityAdapter().apply(statMap, combatKernel.derivedStats().derive(raw));
                    try {
                        rpgHud.install(playerRef, event.getPlayer(), statMap);
                        LOGGER.atInfo().log("RPG_HUD_INSTALLED revision=%s player=%s readyId=%d",
                                BuildIdentity.REVISION, playerRef.getUuid(), event.getReadyId());
                    } catch (RuntimeException error) {
                        LOGGER.atWarning().withCause(error).log("RPG HUD install failed after native visibility rollback player=%s",
                                playerRef.getUuid());
                    }
                }
            }
        });
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            UUID player = event.getPlayerRef().getUuid();
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
                abilityInputs.clear(playerRef.getUuid());
                bosses.clear(playerRef.getUuid());
                skillExecutionSystem.cancel(playerRef.getUuid(), "WORLD_DRAIN");
            }
        });
        getCommandRegistry().registerCommand(new CharacterProbeCommand());
        getCommandRegistry().registerCommand(new LinkCanvasProbeCommand());
        getCommandRegistry().registerCommand(new MouseProbeCommand());
        getCommandRegistry().registerCommand(new StatsProbeCommand());
        getCommandRegistry().registerCommand(new CapabilitiesProbeCommand());
        getCommandRegistry().registerCommand(new HtDevLibProbeCommand());
    }

    @Override
    protected void start() {
        // Assets are resolved before plugin start, including in --bare smoke mode (no BootEvent).
        com.inigmasgames.hytalerpg.input.NativeRuneControl.auditAssets();
        com.inigmasgames.hytalerpg.execution.hytale.AreaStatusProjectionSystem.requireAssets();
        com.inigmasgames.hytalerpg.execution.hytale.SupportNativeEffects.requireAssets();
        com.inigmasgames.hytalerpg.execution.hytale.NativeStrikeActionLock.requireAsset();
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
        com.hypixel.hytale.server.npc.NPCPlugin.get().validateSpawnableRole("RPG_Summon_Crawler");
        com.hypixel.hytale.server.npc.NPCPlugin.get().validateSpawnableRole("RPG_Summon_Broodling");
        LOGGER.atInfo().log("RPG_STAGE10_BATCH_ROLES count=3 result=PASS connectedProof=false");
        com.hypixel.hytale.server.npc.NPCPlugin.get().validateSpawnableRole("RPG_Summon_Decoy");
        LOGGER.atInfo().log("RPG_STAGE10_DECOY_ROLE appearance=Mannequin attacks=0 result=PASS connectedProof=false");
        LOGGER.atInfo().log("RPG_STAGE10_ASSETS revision=%s summonProfiles=%d role=RPG_Summon_Wolf result=PASS connectedProof=false",
                BuildIdentity.REVISION,Stage04SkillProfiles.EXPECTED_STAGE10_PROFILES);
    }

    @Override
    protected void shutdown() {
        if (inboundWatcher != null) {
            PacketAdapters.deregisterInbound(inboundWatcher);
            inboundWatcher = null;
        }
        if (outboundWatcher != null) {
            PacketAdapters.deregisterOutbound(outboundWatcher);
            outboundWatcher = null;
        }
        MouseProbeService.clear();
        if (nativeAbilities != null) { nativeAbilities.close(); nativeAbilities = null; }
        if (rpgHud != null) { rpgHud.close(); rpgHud = null; }
        skillExecutionSystem = null;
        abilityInputs = null;
        if (uiTrace != null) { uiTrace.close(); uiTrace = null; }
        if (skillTrace != null) { skillTrace.close(); skillTrace = null; }
        combatKernel = null;
        LOGGER.atInfo().log("HYTALE_RPG_SHUTDOWN revision=%s stage=%s", BuildIdentity.REVISION, BuildIdentity.STAGE);
    }
}
