package com.inigmasgames.hytalerpg.phase00;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseMotionEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
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

import javax.annotation.Nonnull;

/** RPG plugin entrypoint. Stage 00 probes remain available while Stage 01B adds no combat behavior. */
public final class Phase00Plugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private PacketFilter inboundWatcher;
    private RpgSkillTraceService skillTrace;
    private RpgLoadoutService loadouts;
    private RpgCombatKernel combatKernel;

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
        getCommandRegistry().registerCommand(new RpgCommand(catalog, loadouts, combatKernel, combatTrace));
        getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.Gather(combatTrace));
        getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.Filter(combatTrace));
        getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.Application(combatTrace));
        getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.Inspect(combatTrace, combatKernel.hostileCombat()));
        getEntityStoreRegistry().registerSystem(new HomeRestorationTickSystem(combatKernel.homeRestoration(),
                combatKernel.hostileCombat(), combatKernel.resources()));
        LOGGER.atInfo().log("RPG_STAGE02_READY revision=%s skills=%d passives=%d schema=%d balance=%s trace=%s entitlementMode=%s",
                BuildIdentity.REVISION, catalog.skills().size(), catalog.passives().size(),
                com.inigmasgames.hytalerpg.progress.RpgPlayerState.CURRENT_SCHEMA,
                combatKernel.balance().profileId, skillTrace.path(),
                configuration.developmentEntitlements() ? "DEVELOPMENT" : "PRODUCTION");
        MouseProbeService.initialize(getDataDirectory());
        inboundWatcher = PacketAdapters.registerInbound((PlayerPacketWatcher) (playerRef, packet) -> {
            AbilityInputObserver.observe(playerRef, packet);
            MouseProbeService.observeRaw(playerRef, packet);
        });
        getEventRegistry().registerGlobal(PlayerMouseButtonEvent.class, MouseProbeService::onButton);
        getEventRegistry().registerGlobal(PlayerMouseMotionEvent.class, MouseProbeService::onMotion);
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            var ref = event.getPlayerRef();
            var playerRef = ref.getStore().getComponent(ref,
                    com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
            if (playerRef != null && event.getPlayer().getHudManager().getCustomHud(RevisionHud.KEY) == null) {
                event.getPlayer().getHudManager().addCustomHud(playerRef, new RevisionHud(playerRef));
                LOGGER.atInfo().log("PHASE00_REVISION_HUD revision=%s player=%s readyId=%d",
                        BuildIdentity.REVISION, playerRef.getUuid(), event.getReadyId());
            }
            if (playerRef != null) {
                var view = loadouts.getLoadout(playerRef.getUuid());
                EntityStatMap statMap = ref.getStore().getComponent(ref, EntityStatMap.getComponentType());
                if (statMap != null) {
                    EnumMap<RpgAttribute, Integer> raw = new EnumMap<>(RpgAttribute.class);
                    for (RpgAttribute attribute : RpgAttribute.values())
                        raw.put(attribute, view.state().attributes.getOrDefault(attribute.name(), 10));
                    new DerivedStatEntityAdapter().apply(statMap, combatKernel.derivedStats().derive(raw));
                }
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
        MouseProbeService.clear();
        if (skillTrace != null) { skillTrace.close(); skillTrace = null; }
        combatKernel = null;
        LOGGER.atInfo().log("HYTALE_RPG_SHUTDOWN revision=%s stage=%s", BuildIdentity.REVISION, BuildIdentity.STAGE);
    }
}
