package com.inigmasgames.hytalerpg.ui.hud;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.inigmasgames.hytalerpg.ui.CharacterXpProjectionService;
import com.inigmasgames.hytalerpg.ui.HytaleResourceViewAdapter;
import com.inigmasgames.hytalerpg.ui.RpgUiProjectionService;
import com.inigmasgames.hytalerpg.ui.model.RpgHudViewModel;
import com.inigmasgames.hytalerpg.ui.model.XpView;
import com.inigmasgames.hytalerpg.ui.trace.RpgUiTraceService;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Owns the presentation-only HUD lifecycle and traces meaningful state transitions. */
public final class RpgHudCoordinator {
    private static final long POLL_NANOS = 100_000_000L;
    private final RpgUiProjectionService projection;
    private final HytaleResourceViewAdapter resources = new HytaleResourceViewAdapter();
    private final RpgUiTraceService trace;
    private java.util.function.ToIntFunction<UUID> finisherPips=ignored->0;
    private java.util.function.Consumer<UUID> ownerPublished=ignored->{};
    public void configureOwnerPublication(java.util.function.Consumer<UUID> observer){ownerPublished=java.util.Objects.requireNonNull(observer);}
    public void configureFinisherPips(java.util.function.ToIntFunction<UUID> reader){this.finisherPips=java.util.Objects.requireNonNull(reader);}
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, XpView> xpFixtures = new ConcurrentHashMap<>();

    public RpgHudCoordinator(RpgUiProjectionService projection, RpgUiTraceService trace) {
        this.projection = projection; this.trace = trace;
    }

    public void install(PlayerRef playerRef, Player player, EntityStatMap stats) {
        UUID id = playerRef.getUuid();
        teardown(id, "REINSTALL");
        HudManager manager = player.getHudManager();
        RpgHudViewModel model = projection.hud(id, resources.read(stats), xpFixtures.get(id));
        RpgHud hud = new RpgHud(playerRef, model);
        manager.addCustomHud(playerRef, hud);
        var combo=new FinisherHud(playerRef,model,finisherPips.applyAsInt(id));
        try{manager.addCustomHud(playerRef,combo);}
        catch(RuntimeException failure){manager.removeCustomHud(playerRef,RpgHud.KEY);throw failure;}
        sessions.put(id, new Session(playerRef, manager, hud, combo, model, System.nanoTime()));
        trace.trace(id, "HUD_LAYOUT_READY", ref(), Map.of(
                "resourcePresentation", "VANILLA_HYTALE", "rpgResourceControls", 0,
                "nativeResourceVisibilityMutation", false,
                "xpLayerOrder", "ExperienceBackground|ExperienceBar|ExperienceFrame",
                "xpAnchor", "Centered Bottom:138 Width:702", "xpUsableWidth", RpgHud.XP_FILL_WIDTH,
                "nativeAbilitiesVisible", true, "nativeSignature", "PRESERVED",
                "rpgAbilityControls", 2, "abilityPresentation", "NATIVE_WITH_READ_ONLY_COOLDOWN_SWEEPS"));
        traceXp(id, model, true);
        if (model.showLevelUpNotice()) trace.trace(id, "LEVEL_UP_INDICATOR_SHOWN", ref(),
                Map.of("pendingLevelUpPoints", model.pendingLevelUpPoints(), "initial", true));
    }

    public void tick(PlayerRef playerRef, EntityStatMap stats) {
        Session session = sessions.get(playerRef.getUuid());
        if (session == null) return;
        long now = System.nanoTime();
        if (now - session.lastPollNanos < POLL_NANOS) return;
        session.lastPollNanos = now;
        try {
            RpgHudViewModel previous = session.model;
            RpgHudViewModel next = projection.hud(playerRef.getUuid(), resources.read(stats), xpFixtures.get(playerRef.getUuid()));
            session.combo.refresh(next,finisherPips.applyAsInt(playerRef.getUuid()));
            ownerPublished.accept(playerRef.getUuid());
            if (next.equals(previous)) return;
            boolean xpChanged = !next.xp().equals(previous.xp());
            boolean noticeChanged = next.showLevelUpNotice() != previous.showLevelUpNotice();
            session.hud.refresh(next);
            session.model = next;
            traceCooldownTransitions(playerRef.getUuid(),previous,next);
            if (xpChanged) traceXp(playerRef.getUuid(), next, false);
            if (noticeChanged) trace.trace(playerRef.getUuid(), next.showLevelUpNotice()
                            ? "LEVEL_UP_INDICATOR_SHOWN" : "LEVEL_UP_INDICATOR_HIDDEN", ref(),
                    Map.of("pendingLevelUpPoints", next.pendingLevelUpPoints()));
        } catch (RuntimeException error) {
            trace.trace(playerRef.getUuid(), "HUD_REFRESH_FAILED", ref(), Map.of(
                    "error", error.getClass().getSimpleName(), "message", String.valueOf(error.getMessage())));
            try { teardown(playerRef.getUuid(), "REFRESH_FAILURE"); } catch (RuntimeException ignored) { }
        }
    }

    public void setXpFixture(UUID player, Double percent) {
        if (percent == null) xpFixtures.remove(player);
        else xpFixtures.put(player, new CharacterXpProjectionService().fixturePercent(percent));
    }

    public void teardown(UUID player, String reason) {
        Session session = sessions.remove(player);
        xpFixtures.remove(player);
        if (session == null) return;
        RuntimeException failure = null;
        try { if(session.manager.getCustomHud(FinisherHud.KEY)!=null)session.manager.removeCustomHud(session.playerRef,FinisherHud.KEY); }
        catch(RuntimeException error){failure=error;}
        try {
            if (session.manager.getCustomHud(RpgHud.KEY) != null)
                session.manager.removeCustomHud(session.playerRef, RpgHud.KEY);
        } catch (RuntimeException error) { failure = error; }
        trace.trace(player, "HUD_TEARDOWN", ref(), Map.of("reason", reason));
        if (failure != null) throw failure;
    }

    public void close() {
        for (UUID player : Set.copyOf(sessions.keySet())) {
            try { teardown(player, "PLUGIN_SHUTDOWN"); } catch (RuntimeException ignored) { }
        }
    }

    private void traceXp(UUID player, RpgHudViewModel model, boolean initial) {
        trace.trace(player, "XP_HUD_REFRESH", ref(), Map.of(
                "level", model.xp().level(), "progress", model.xp().progress(),
                "fillWidth", RpgHud.xpFillWidth(model.xp().progress()),
                "fullWidth", RpgHud.XP_FILL_WIDTH, "leftAnchored", true, "initial", initial));
    }

    /** Event-driven only: proves start/ready transitions without tracing every HUD poll. */
    private void traceCooldownTransitions(UUID player,RpgHudViewModel previous,RpgHudViewModel next) {
        for(int i=0;i<Math.min(2,next.skills().size());i++){
            var before=previous.skills().get(i);var after=next.skills().get(i);
            if(before.state()==after.state()&&before.skillId().equals(after.skillId())&&before.unavailableReason().equals(after.unavailableReason()))continue;
            trace.trace(player,"COOLDOWN_HUD_STATE",ref(),Map.of("slot",i+1,"skillId",after.skillId(),
                    "state",after.state().name(),"remaining",after.cooldownRemainingSeconds(),
                    "duration",after.cooldownDurationSeconds(),"radialValue",CooldownSweep.progress(
                            after.cooldownRemainingSeconds(),after.cooldownDurationSeconds()),"countdown",CooldownSweep.countdown(after.cooldownRemainingSeconds()),"resourceFeedback",after.unavailableReason()));
        }
    }

    private static String ref() { return UUID.randomUUID().toString().substring(0, 12); }

    private static final class Session {
        private final PlayerRef playerRef; private final HudManager manager;
        private final RpgHud hud; private RpgHudViewModel model; private long lastPollNanos;
        private final FinisherHud combo;
        private Session(PlayerRef playerRef, HudManager manager, RpgHud hud,FinisherHud combo,
                        RpgHudViewModel model, long now) {
            this.combo=combo;
            this.playerRef = playerRef; this.manager = manager; this.hud = hud;
            this.model = model; this.lastPollNanos = now;
        }
    }
}
