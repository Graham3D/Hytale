package com.inigmasgames.hytalerpg.ui.hud;

import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
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

/** Owns production HUD lifecycle, diff refresh, and exact native visibility restoration. */
public final class RpgHudCoordinator {
    private static final long POLL_NANOS = 250_000_000L;
    private static final long RATE_TRACE_NANOS = 5_000_000_000L;
    private final RpgUiProjectionService projection;
    private final HytaleResourceViewAdapter resources = new HytaleResourceViewAdapter();
    private final RpgUiTraceService trace;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, XpView> xpFixtures = new ConcurrentHashMap<>();

    public RpgHudCoordinator(RpgUiProjectionService projection, RpgUiTraceService trace) {
        this.projection = projection; this.trace = trace;
    }

    public void install(PlayerRef playerRef, Player player, EntityStatMap stats) {
        UUID id = playerRef.getUuid();
        teardown(id, "REINSTALL");
        HudManager manager = player.getHudManager();
        HudVisibilityLease lease = HudVisibilityLease.hideRpgResourceDuplicates(new ManagerPort(manager, playerRef));
        trace.trace(id, "HUD_VISIBILITY_SNAPSHOT", ref(), Map.of("visible", lease.snapshot().toString(),
                "hidden", "Mana,Health,Stamina"));
        try {
            RpgHudViewModel model = projection.hud(id, resources.read(stats), xpFixtures.get(id));
            RpgHud hud = new RpgHud(playerRef, model);
            manager.addCustomHud(playerRef, hud);
            sessions.put(id, new Session(playerRef, manager, lease, hud, model, System.nanoTime()));
            trace.trace(id, "HUD_OPENED", ref(), Map.of("revision", model.revision(), "skillSlots", 4,
                    "resourceOrder", "Mana|Health|Stamina"));
            trace.trace(id, "SKILLBAR_REFRESH", ref(), Map.of(
                    "slots", model.skills().stream().map(slot -> slot.skillId() + ':' + slot.state()).toList(),
                    "initial", true));
            trace.trace(id, "XP_PROJECTED", ref(), Map.of("level", model.xp().level(),
                    "progress", model.xp().progress(), "pips", model.xp().pipFill(), "initial", true));
            if (model.showLevelUpNotice()) trace.trace(id, "LEVEL_UP_INDICATOR_SHOWN", ref(),
                    Map.of("pendingLevelUpPoints", model.pendingLevelUpPoints(), "initial", true));
        } catch (RuntimeException error) {
            lease.restore();
            throw error;
        }
    }

    public void tick(PlayerRef playerRef, EntityStatMap stats) {
        Session session = sessions.get(playerRef.getUuid());
        if (session == null) return;
        long now = System.nanoTime();
        if (now - session.lastPollNanos < POLL_NANOS) return;
        session.lastPollNanos = now;
        session.polls++;
        try {
            RpgHudViewModel next = projection.hud(playerRef.getUuid(), resources.read(stats), xpFixtures.get(playerRef.getUuid()));
            if (!next.equals(session.model)) {
                boolean skillsChanged = !next.skills().equals(session.model.skills());
                boolean xpChanged = !next.xp().equals(session.model.xp());
                boolean noticeChanged = next.showLevelUpNotice() != session.model.showLevelUpNotice();
                session.hud.refresh(next);
                session.model = next;
                session.updates++;
                if (skillsChanged) trace.trace(playerRef.getUuid(), "SKILLBAR_REFRESH", ref(),
                        Map.of("slots", next.skills().stream().map(slot -> slot.skillId() + ':' + slot.state()).toList()));
                if (xpChanged) trace.trace(playerRef.getUuid(), "XP_PROJECTED", ref(),
                        Map.of("level", next.xp().level(), "progress", next.xp().progress(), "pips", next.xp().pipFill()));
                if (noticeChanged) trace.trace(playerRef.getUuid(), next.showLevelUpNotice()
                        ? "LEVEL_UP_INDICATOR_SHOWN" : "LEVEL_UP_INDICATOR_HIDDEN", ref(),
                        Map.of("pendingLevelUpPoints", next.pendingLevelUpPoints()));
            }
            if (now - session.rateWindowNanos >= RATE_TRACE_NANOS) {
                double seconds = (now - session.rateWindowNanos) / 1_000_000_000.0;
                trace.trace(playerRef.getUuid(), "HUD_REFRESHED", ref(), Map.of(
                        "pollRateHz", session.polls / seconds, "updateRateHz", session.updates / seconds,
                        "polls", session.polls, "updates", session.updates));
                session.rateWindowNanos = now; session.polls = 0; session.updates = 0;
            }
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
        try {
            if (session.manager.getCustomHud(RpgHud.KEY) != null)
                session.manager.removeCustomHud(session.playerRef, RpgHud.KEY);
        } catch (RuntimeException error) { failure = error; }
        try {
            session.lease.restore();
            trace.trace(player, "HUD_VISIBILITY_RESTORED", ref(), Map.of("visible", session.lease.snapshot().toString()));
        } finally {
            trace.trace(player, "HUD_TEARDOWN", ref(), Map.of("reason", reason));
        }
        if (failure != null) throw failure;
    }

    public void close() {
        for (UUID player : Set.copyOf(sessions.keySet())) {
            try { teardown(player, "PLUGIN_SHUTDOWN"); } catch (RuntimeException ignored) { }
        }
    }

    private static String ref() { return UUID.randomUUID().toString().substring(0, 12); }

    private static final class ManagerPort implements HudVisibilityLease.Port {
        private final HudManager manager; private final PlayerRef player;
        private ManagerPort(HudManager manager, PlayerRef player) { this.manager = manager; this.player = player; }
        @Override public Set<HudComponent> visible() { return Set.copyOf(manager.getVisibleHudComponents()); }
        @Override public void setVisible(Set<HudComponent> components) { manager.setVisibleHudComponents(player, components); }
    }

    private static final class Session {
        private final PlayerRef playerRef; private final HudManager manager; private final HudVisibilityLease lease;
        private final RpgHud hud; private RpgHudViewModel model; private long lastPollNanos;
        private long rateWindowNanos; private long polls; private long updates;
        private Session(PlayerRef playerRef, HudManager manager, HudVisibilityLease lease, RpgHud hud,
                        RpgHudViewModel model, long now) {
            this.playerRef = playerRef; this.manager = manager; this.lease = lease; this.hud = hud;
            this.model = model; this.lastPollNanos = now; this.rateWindowNanos = now;
        }
    }
}
