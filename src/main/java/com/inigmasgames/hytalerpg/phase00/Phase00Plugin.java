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
import com.inigmasgames.hytalerpg.input.CommandOnlyRpgUiOpenInputAdapter;
import com.inigmasgames.hytalerpg.ui.RpgUiProjectionService;
import com.inigmasgames.hytalerpg.ui.hud.RpgHudCoordinator;
import com.inigmasgames.hytalerpg.ui.hud.RpgHudTickSystem;
import com.inigmasgames.hytalerpg.ui.trace.RpgUiTraceService;
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
        var allocation = new AttributeAllocationService(loadouts);
        abilityInputs = new HytaleAbilitySkillInputAdapter();
        var runtimeProfiles = Stage04SkillProfiles.loadCanonical(catalog);
        var reactions = new ReactionWindowService(System::nanoTime);
        var executions = new SkillExecutionService(loadouts, runtimeProfiles, combatKernel,
                SkillExecutorRegistry.runtime(), new SkillInstanceLifecycle(), skillTrace);
        var vfx = new LinkTreeVfxService(new HtDevLibVfxAdapter(), Map.of());
        var bosses = new HytaleBossBarTracker();
        skillExecutionSystem = new HytaleSkillExecutionSystem(abilityInputs, executions, combatKernel,
                combatTrace, reactions, vfx, bosses);
        rpgHud = new RpgHudCoordinator(uiProjection, uiTrace);
        getCommandRegistry().registerCommand(new RpgCommand(catalog, loadouts, combatKernel, combatTrace,
                uiProjection, allocation, uiTrace, rpgHud));
        getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.Gather(combatTrace));
        getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.Filter(combatTrace));
        getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.Application(combatTrace));
        getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.Inspect(combatTrace, combatKernel.hostileCombat()));
        getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.ReactionObserver(skillExecutionSystem));
        getEntityStoreRegistry().registerSystem(new HomeRestorationTickSystem(combatKernel.homeRestoration(),
                combatKernel.hostileCombat(), combatKernel.resources()));
        getEntityStoreRegistry().registerSystem(new RpgHudTickSystem(rpgHud));
        getEntityStoreRegistry().registerSystem(skillExecutionSystem);
        LOGGER.atInfo().log("RPG_STAGE05_READY revision=%s skills=%d passives=%d pilots=%d projectiles=%d schema=%d balance=%s skillTrace=%s uiTrace=%s abilityInput=Ability1..Ability4 uiOpen=%s entitlementMode=%s",
                BuildIdentity.REVISION, catalog.skills().size(), catalog.passives().size(),
                runtimeProfiles.all().size(), Stage04SkillProfiles.EXPECTED_STAGE05_PILOTS,
                com.inigmasgames.hytalerpg.progress.RpgPlayerState.CURRENT_SCHEMA,
                combatKernel.balance().profileId, skillTrace.path(), uiTrace.path(),
                new CommandOnlyRpgUiOpenInputAdapter().availability(),
                configuration.developmentEntitlements() ? "DEVELOPMENT" : "PRODUCTION");
        MouseProbeService.initialize(getDataDirectory());
        inboundWatcher = PacketAdapters.registerInbound((PlayerPacketWatcher) (playerRef, packet) -> {
            AbilityInputObserver.observe(playerRef, packet);
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
                EntityStatMap statMap = ref.getStore().getComponent(ref, EntityStatMap.getComponentType());
                if (statMap != null) {
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
            abilityInputs.clear(player);
            bosses.clear(player);
            skillExecutionSystem.cancel(player, "PLAYER_DISCONNECT");
            combatKernel.cooldowns().clear(player);
        });
        getEventRegistry().registerGlobal(DrainPlayerFromWorldEvent.class, event -> {
            var playerRef = event.getHolder().getComponent(
                    com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
            if (playerRef != null) {
                abilityInputs.clear(playerRef.getUuid());
                bosses.clear(playerRef.getUuid());
                skillExecutionSystem.cancel(playerRef.getUuid(), "WORLD_DRAIN");
            }
        });
        getCommandRegistry().registerCommand(new CharacterProbeCommand());
        getCommandRegistry().registerCommand(new LinkCanvasProbeCommand());
        getCommandRegistry().registerCommand(new MouseProbeCommand());
        getCommandRegistry().registerCommand(new HudShowProbeCommand());
        getCommandRegistry().registerCommand(new HudClearProbeCommand());
        getCommandRegistry().registerCommand(new StatsProbeCommand());
        getCommandRegistry().registerCommand(new CapabilitiesProbeCommand());
        getCommandRegistry().registerCommand(new AbilityInputsProbeCommand());
        getCommandRegistry().registerCommand(new HtDevLibProbeCommand());
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
        if (rpgHud != null) { rpgHud.close(); rpgHud = null; }
        skillExecutionSystem = null;
        abilityInputs = null;
        if (uiTrace != null) { uiTrace.close(); uiTrace = null; }
        if (skillTrace != null) { skillTrace.close(); skillTrace = null; }
        combatKernel = null;
        LOGGER.atInfo().log("HYTALE_RPG_SHUTDOWN revision=%s stage=%s", BuildIdentity.REVISION, BuildIdentity.STAGE);
    }
}
