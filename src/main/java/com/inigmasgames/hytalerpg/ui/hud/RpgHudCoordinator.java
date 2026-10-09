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
    private java.util.function.BiFunction<UUID,UUID,java.util.Optional<com.inigmasgames.hytalerpg.enemies.EnemyDisplayDto>> enemySource=(world,entity)->java.util.Optional.empty();
    private java.util.function.BiPredicate<UUID,UUID> qaNativeHealthbar=(world,entity)->false;
    private com.inigmasgames.hytalerpg.execution.hytale.EnemyHealthBarPresentation enemyHealthBars;
    public void configureEnemyHealthBars(com.inigmasgames.hytalerpg.execution.hytale.EnemyHealthBarPresentation presentation){enemyHealthBars=java.util.Objects.requireNonNull(presentation);}
    public void configureEnemyTargets(java.util.function.BiFunction<UUID,UUID,java.util.Optional<com.inigmasgames.hytalerpg.enemies.EnemyDisplayDto>> source){enemySource=java.util.Objects.requireNonNull(source);}
    public void configureQaNativeHealthbar(java.util.function.BiPredicate<UUID,UUID> source){qaNativeHealthbar=java.util.Objects.requireNonNull(source);}
    /** Protection release bypasses the routine 4 Hz poll for viewers already tracking this actor. */
    public void enemyDisplayChanged(UUID world,UUID entity){
        var nativeWorld=com.hypixel.hytale.server.core.universe.Universe.get().getWorld(world);if(nativeWorld==null)return;
        try{nativeWorld.execute(()->{
            var store=nativeWorld.getEntityStore().getStore();
            var target=store.getExternalData().getRefFromUUID(entity);
            for(var session:sessions.values())if(world.equals(session.playerRef.getWorldUuid())&&world.equals(session.enemyWorld)
                    &&entity.equals(session.enemyEntity)&&session.ownsActiveHuds()){
                try{refreshEnemyTarget(session,store,session.playerRef.getReference(),target,world,entity);}
                catch(RuntimeException failure){enemyPresentationFailed(session,failure);}
            }
        });}catch(RuntimeException worldClosing){/* Presentation must not block durable protection release or teardown. */}
    }
    /** After an applied hit, refresh only viewers already targeting this actor. */
    public void enemyHealthChanged(UUID world,UUID entity,UUID player){
        if(player!=null){
            var session=sessions.get(player);
            if(session!=null&&world.equals(session.playerRef.getWorldUuid())){
                session.recentDamagedEntity=entity;
                session.recentDamagedUntil=System.nanoTime()+3_000_000_000L;
            }
        }
        enemyDisplayChanged(world,entity);
    }
    public void tickEnemyTarget(PlayerRef player,com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store,
            com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> viewer){
        // The live monster presentation is Hytale's entity-attached Nameplate
        // and viewer-local EntityStat UI. This tick only expires native bar
        // admissions; the projected CustomUI stack below is retained for
        // diagnostics and is deliberately not polled in connected play.
        var session=sessions.get(player.getUuid());if(session==null||!session.ownsActiveHuds())return;
        long now=System.nanoTime();if(now-session.lastEnemyPollNanos<250_000_000L)return;session.lastEnemyPollNanos=now;
        if(enemyHealthBars!=null)enemyHealthBars.tick(player,store,viewer);
    }
    private void tickProjectedEnemyTarget(PlayerRef player,com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store,
            com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> viewer){
        var session=sessions.get(player.getUuid());if(session==null||!session.ownsActiveHuds())return;
        long now=System.nanoTime();if(now-session.lastEnemyPollNanos<250_000_000L)return;session.lastEnemyPollNanos=now;
        try{
        var world=store.getExternalData().getWorld().getWorldConfig().getUuid();
        var target=com.hypixel.hytale.server.core.util.TargetUtil.getTargetEntity(viewer,24f,store);
        var id=target==null||!target.isValid()?null:store.getComponent(target,com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
        var dead=target!=null&&target.isValid()&&store.getComponent(target,com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent.getComponentType())!=null;
        if(id==null||dead||enemySource.apply(world,id.getUuid()).isEmpty()){
            target=null;id=null;
            if(session.recentDamagedEntity!=null&&now<session.recentDamagedUntil){
                var recent=store.getExternalData().getRefFromUUID(session.recentDamagedEntity);
                if(recent!=null&&recent.isValid()
                        &&store.getComponent(recent,com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent.getComponentType())==null
                        &&enemySource.apply(world,session.recentDamagedEntity).isPresent()){
                    target=recent;
                    id=store.getComponent(recent,com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
                }
            }
        }
        session.enemyWorld=world;session.enemyEntity=id==null?null:id.getUuid();
        refreshEnemyTarget(session,store,viewer,target,world,session.enemyEntity);
        }catch(RuntimeException failure){
            enemyPresentationFailed(session,failure);
        }
    }
    private void refreshEnemyTarget(Session session,
            com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store,
            com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> viewer,
            com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> target,
            UUID world,UUID entity){
        if(entity==null||target==null||!target.isValid()
                ||store.getComponent(target,com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent.getComponentType())!=null){
            session.hud.refreshEnemyTarget(null,null,0,0);return;
        }
        var display=enemySource.apply(world,entity).orElse(null);
        if(display==null){session.hud.refreshEnemyTarget(null,null,0,0);return;}
        var stats=store.getComponent(target,EntityStatMap.getComponentType());
        var health=stats==null?null:stats.get(com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes.getHealth());
        var frame=EnemyBillboardProjection.project(store,viewer,target);
        session.hud.refreshEnemyTarget(display,frame,health==null?0:health.get(),health==null?0:health.getMax());
    }
    private void enemyPresentationFailed(Session session,RuntimeException failure){
        session.enemyEntity=null;
        trace.trace(session.playerRef.getUuid(),"ENEMY_TARGET_REFRESH_FAILED",ref(),Map.of("reason",String.valueOf(failure.getMessage())));
        try{teardown(session.playerRef.getUuid(),"ENEMY_TARGET_REFRESH_FAILURE");}catch(RuntimeException ignored){}
    }
    private java.util.function.ToIntFunction<UUID> finisherPips=ignored->0;
    private java.util.function.Consumer<UUID> ownerPublished=ignored->{};
    private java.util.function.Function<UUID,java.util.Optional<com.inigmasgames.hytalerpg.execution.summon.IronSentinelBinding>> sentinelSource=ignored->java.util.Optional.empty();
    public void configureSentinelAffixes(java.util.function.Function<UUID,java.util.Optional<com.inigmasgames.hytalerpg.execution.summon.IronSentinelBinding>> source){sentinelSource=java.util.Objects.requireNonNull(source);}
    public boolean toggleSentinelAffixes(UUID player){
        var session=sessions.get(player);
        if(session==null)throw new IllegalStateException("RPG HUD is not ready");
        session.sentinelAffixesOn=!session.sentinelAffixesOn;
        if(session.ownsActiveHuds())session.hud.refreshSentinelAffixes(session.sentinelAffixesOn?sentinelLines(player):null);
        return session.sentinelAffixesOn;
    }
    private SentinelAffixPresentation.Lines sentinelLines(UUID player){
        return SentinelAffixPresentation.of(sentinelSource.apply(player).map(
                com.inigmasgames.hytalerpg.execution.summon.IronSentinelBinding::boundItem).orElse(null));
    }
    public void configureOwnerPublication(java.util.function.Consumer<UUID> observer){ownerPublished=java.util.Objects.requireNonNull(observer);}
    public void configureFinisherPips(java.util.function.ToIntFunction<UUID> reader){this.finisherPips=java.util.Objects.requireNonNull(reader);}
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    public void showSkillFailure(UUID actor, String code) {
        var session = sessions.get(actor);
        if (session != null) session.skillFailure.show(code, System.nanoTime());
    }
    private final Map<UUID, XpView> xpFixtures = new ConcurrentHashMap<>();

    public RpgHudCoordinator(RpgUiProjectionService projection, RpgUiTraceService trace) {
        this.projection = projection; this.trace = trace;
    }

    public void install(PlayerRef playerRef, Player player, EntityStatMap stats) {
        try (var readyPathSpan = com.inigmasgames.hywind.readypath.ReadyPathProbe.span("RPG_HUD_INSTALL_SERVER_ONLY", playerRef.getUuid())) {
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
    }

    public void tick(PlayerRef playerRef, EntityStatMap stats, boolean emptyHand) {
        tick(playerRef, stats, emptyHand, !emptyHand);
    }
    public void tick(PlayerRef playerRef, EntityStatMap stats, boolean emptyHand, boolean nativeWeaponReady) {
        Session session = sessions.get(playerRef.getUuid());
        if (session == null) return;
        long now = System.nanoTime();
        if (now - session.lastPollNanos < POLL_NANOS) return;
        session.lastPollNanos = now;
        // CanvasUI takes an exclusive modal lease over CustomUI HUD documents while its graph editor is open.
        // Updating a removed HUD object sends selectors into the active editor document and the client correctly
        // disconnects on the missing element. Preserve the latest authoritative model in the coordinator and resume
        // projection after CanvasUI restores these exact HUD instances.
        if (!session.ownsActiveHuds()) return;
        try {
            RpgHudViewModel previous = session.model;
            RpgHudViewModel next = projection.hud(playerRef.getUuid(), resources.read(stats), xpFixtures.get(playerRef.getUuid()));
            session.hud.refreshSkillFailure(session.skillFailure.view(now), !nativeWeaponReady
                    && next.skills().stream().anyMatch(slot -> slot.skillId() != null && !slot.skillId().isBlank()));
            session.combo.refresh(next,finisherPips.applyAsInt(playerRef.getUuid()));
            if(session.sentinelAffixesOn)session.hud.refreshSentinelAffixes(sentinelLines(playerRef.getUuid()));
            ownerPublished.accept(playerRef.getUuid());
            if (next.equals(previous) && session.emptyHand == emptyHand) return;
            boolean xpChanged = !next.xp().equals(previous.xp());
            boolean noticeChanged = next.showLevelUpNotice() != previous.showLevelUpNotice();
            session.hud.refresh(next,emptyHand);
            session.model = next;
            session.emptyHand = emptyHand;
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
        if(enemyHealthBars!=null)enemyHealthBars.forgetViewer(player);
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
        private final SkillFailureNotice skillFailure = new SkillFailureNotice();
        private final PlayerRef playerRef; private final HudManager manager;
        private final RpgHud hud; private RpgHudViewModel model; private long lastPollNanos; private boolean emptyHand;
        private final FinisherHud combo;
        private boolean sentinelAffixesOn;
        private UUID enemyWorld,enemyEntity,recentDamagedEntity;
        private long lastEnemyPollNanos,recentDamagedUntil;
        private Session(PlayerRef playerRef, HudManager manager, RpgHud hud,FinisherHud combo,
                        RpgHudViewModel model, long now) {
            this.combo=combo;
            this.playerRef = playerRef; this.manager = manager; this.hud = hud;
            this.model = model; this.lastPollNanos = now;
        }
        private boolean ownsActiveHuds() {
            return manager.getCustomHud(RpgHud.KEY) == hud
                    && manager.getCustomHud(FinisherHud.KEY) == combo;
        }
    }
}
