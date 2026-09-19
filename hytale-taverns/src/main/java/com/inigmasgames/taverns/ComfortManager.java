package com.inigmasgames.taverns;

import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BlockMountType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.effect.ActiveEntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.joml.Vector3d;

/** Event-invalidated Comfort object registration, scoring, and per-player HUD lifecycle. */
final class ComfortManager {
    static final String EFFECT_ID = "Taverns_Comfort";
    static final String RELAXED_EFFECT_ID = "Taverns_Relaxed";
    private static final float PLAYER_CHECK_INTERVAL = 0.20f;
    private static final List<ComfortCategory> CONTRIBUTING_CATEGORY_ORDER = List.of(
            ComfortCategory.CONTAINERS,
            ComfortCategory.WARDROBES,
            ComfortCategory.TABLES,
            ComfortCategory.SEATING,
            ComfortCategory.DOORS,
            ComfortCategory.WINDOWS,
            ComfortCategory.LIGHTING,
            ComfortCategory.BEDS,
            ComfortCategory.SHELVES,
            ComfortCategory.SIGNS,
            ComfortCategory.DECO);

    private final TavernRepository repository;
    private final ComfortRegistry registry;
    private final Consumer<Throwable> error;
    private final Map<UUID, CacheEntry> cache = new HashMap<>();
    private final Set<UUID> dirtyTaverns = new HashSet<>();
    private final Map<UUID, Float> playerElapsed = new HashMap<>();
    private final Set<UUID> playersInside = new HashSet<>();
    private final Map<UUID, RelaxedSession> relaxedSessions = new HashMap<>();
    private final Set<UUID> reportedScanFailures = new HashSet<>();

    ComfortManager(
            TavernRepository repository,
            ComfortRegistry registry,
            Consumer<Throwable> error) {
        this.repository = repository;
        this.registry = registry;
        this.error = error;
    }

    void markDirtyAt(UUID worldId, int x, int y, int z) {
        repository.findPrimaryCoreContaining(worldId, x, y, z)
                .ifPresent(core -> dirtyTaverns.add(core.tavernId()));
    }

    void invalidateTavern(UUID tavernId) {
        cache.remove(tavernId);
        dirtyTaverns.remove(tavernId);
        reportedScanFailures.remove(tavernId);
    }

    void tick(
            float delta,
            PlayerRef playerRef,
            Player player,
            TransformComponent transform,
            EffectControllerComponent effects,
            Ref<EntityStore> ref,
            Store<EntityStore> store) {
        UUID playerId = playerRef.getUuid();
        float elapsed = playerElapsed.getOrDefault(playerId, 0.0f) + delta;
        if (elapsed < PLAYER_CHECK_INTERVAL) {
            playerElapsed.put(playerId, elapsed);
            return;
        }
        playerElapsed.put(playerId, 0.0f);
        float sampledDelta = elapsed;

        Vector3d position = transform.getPosition();
        int blockX = (int) Math.floor(position.x);
        int blockY = (int) Math.floor(position.y);
        int blockZ = (int) Math.floor(position.z);
        UUID worldId = store.getExternalData().getWorld().getWorldConfig().getUuid();
        Optional<CoreRecord> containing = repository.findPrimaryCoreContaining(
                worldId, blockX, blockY, blockZ);
        if (containing.isEmpty()) {
            leave(playerRef, player, effects, ref, store);
            return;
        }

        CoreRecord core = containing.get();
        TavernComfortSnapshot snapshot = snapshot(core, store.getExternalData().getWorld());
        TavernsHud hud = hud(playerRef, player);
        hud.showComfort(
                snapshot.score().totalComfort(),
                contributingSources(
                        snapshot.objects(),
                        snapshot.score(),
                        registry.thresholds()));
        boolean wasInside = playersInside.contains(playerId);
        updateRelaxedInside(
                playerId,
                sampledDelta,
                snapshot.score().relaxedMinutes(),
                isSeated(ref, store),
                wasInside,
                hud,
                effects,
                ref,
                store);
        playersInside.add(playerId);
        ensureComfortEffect(effects, ref, store);
    }

    void abandon(UUID playerId) {
        playerElapsed.remove(playerId);
        playersInside.remove(playerId);
        relaxedSessions.remove(playerId);
    }

    private TavernComfortSnapshot snapshot(CoreRecord core, World world) {
        CacheEntry current = cache.get(core.tavernId());
        boolean dirty = dirtyTaverns.remove(core.tavernId());
        if (!dirty && current != null && current.core().bounds().equals(core.bounds())) {
            return current.snapshot();
        }
        try {
            TavernComfortSnapshot scanned = scan(core, world);
            cache.put(core.tavernId(), new CacheEntry(core, scanned));
            reportedScanFailures.remove(core.tavernId());
            return scanned;
        } catch (RuntimeException exception) {
            if (reportedScanFailures.add(core.tavernId())) {
                error.accept(new IllegalStateException(
                        "Could not scan Comfort objects for Tavern " + core.tavernId(), exception));
            }
            if (current != null) {
                return current.snapshot();
            }
            return new TavernComfortSnapshot(
                    ComfortScore.calculate(
                            List.of(), 0, registry.thresholds()),
                    List.of());
        }
    }

    private TavernComfortSnapshot scan(CoreRecord core, World world) {
        List<RegisteredComfortObject> objects = new ArrayList<>();
        Cuboid bounds = core.bounds();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    BlockType block = BlockType.getAssetMap().getAsset(world.getBlock(x, y, z));
                    if (block == null || block == BlockType.EMPTY) {
                        continue;
                    }
                    Item item = block.getItem();
                    String assetId = item == null ? block.getId() : item.getId();
                    Optional<ComfortDefinition> found = registry.find(assetId);
                    if (found.isEmpty() && !assetId.equals(block.getId())) {
                        found = registry.find(block.getId());
                    }
                    if (found.isEmpty()) {
                        continue;
                    }
                    ComfortDefinition definition = found.get();
                    RegisteredComfortObject registered = new RegisteredComfortObject(
                            core.coreId(), core.worldId(), x, y, z,
                            definition.assetId(), definition.category(), definition.comfort(), true);
                    objects.add(registered);
                }
            }
        }
        // Exact finished-floor validation is still TBD in the design document.
        // Until it is specified, configured category minimums apply and optional
        // density scaling remains dormant rather than using cuboid volume.
        return new TavernComfortSnapshot(
                calculateScore(objects, 0, registry.thresholds()), objects);
    }

    static ComfortScore calculateScore(List<RegisteredComfortObject> objects) {
        return calculateScore(objects, 0, ComfortThreshold.designDefaults());
    }

    static ComfortScore calculateScore(
            List<RegisteredComfortObject> objects,
            int eligibleFloorArea,
            Map<ComfortCategory, ComfortThreshold> thresholds) {
        return ComfortScore.calculate(objects, eligibleFloorArea, thresholds);
    }

    private void leave(
            PlayerRef playerRef,
            Player player,
            EffectControllerComponent effects,
            Ref<EntityStore> ref,
            Store<EntityStore> store) {
        UUID playerId = playerRef.getUuid();
        boolean wasInside = playersInside.remove(playerId);
        TavernsHud hud = null;
        if (player.getHudManager().getCustomHud(TavernsHud.KEY) instanceof TavernsHud currentHud) {
            hud = currentHud;
            if (wasInside) {
                currentHud.hideComfort();
            }
            currentHud.hideRelaxing();
        }
        if (wasInside) {
            int effectIndex = EntityEffect.getAssetMap().getIndex(EFFECT_ID);
            if (effectIndex >= 0 && effects.hasEffect(effectIndex)) {
                effects.removeEffect(ref, effectIndex, store);
            }
        }
        updateRelaxedOutside(playerId, wasInside, effects, ref, store, hud);
    }

    private void updateRelaxedInside(
            UUID playerId,
            float delta,
            int relaxedMinutes,
            boolean seated,
            boolean wasInside,
            TavernsHud hud,
            EffectControllerComponent effects,
            Ref<EntityStore> ref,
            Store<EntityStore> store) {
        int effectIndex = EntityEffect.getAssetMap().getIndex(RELAXED_EFFECT_ID);
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(RELAXED_EFFECT_ID);
        if (effect == null || effectIndex < 0) {
            hud.hideRelaxing();
            hud.setRelaxedVisible(false);
            return;
        }

        RelaxedSession session = relaxedSessions.computeIfAbsent(
                playerId, ignored -> new RelaxedSession());
        ActiveEntityEffect active = effects.getActiveEffects().get(effectIndex);
        if (!wasInside && session.isRelaxed() && active == null) {
            session.expire();
        }
        if (active != null && !session.isRelaxed()) {
            if (active.isInfinite()) {
                session.recoverInfiniteEffect();
            } else {
                session.pauseEffect(active.getRemainingDuration());
            }
        }
        if (active != null && !active.isInfinite()) {
            session.pauseEffect(active.getRemainingDuration());
            effects.addInfiniteEffect(ref, effectIndex, effect, store);
        } else if (active == null && session.isRelaxed()) {
            effects.addInfiniteEffect(ref, effectIndex, effect, store);
        }

        RelaxedSession.RelaxingUpdate update =
                session.tickInside(delta, seated, relaxedMinutes);
        if (update.visible()) {
            hud.showRelaxing(update.progress());
        } else {
            hud.hideRelaxing();
        }
        if (update.completed()) {
            effects.addInfiniteEffect(ref, effectIndex, effect, store);
        }
        hud.setRelaxedVisible(effects.hasEffect(effectIndex));
    }

    private void updateRelaxedOutside(
            UUID playerId,
            boolean justLeft,
            EffectControllerComponent effects,
            Ref<EntityStore> ref,
            Store<EntityStore> store,
            TavernsHud hud) {
        int effectIndex = EntityEffect.getAssetMap().getIndex(RELAXED_EFFECT_ID);
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(RELAXED_EFFECT_ID);
        ActiveEntityEffect active = effectIndex < 0
                ? null
                : effects.getActiveEffects().get(effectIndex);
        if (hud != null) {
            hud.setRelaxedVisible(active != null);
        }
        RelaxedSession session = relaxedSessions.get(playerId);
        if (session == null || !session.isRelaxed()) {
            return;
        }
        if (effect == null || effectIndex < 0) {
            session.expire();
            if (hud != null) {
                hud.setRelaxedVisible(false);
            }
            return;
        }
        if (!justLeft) {
            if (active == null) {
                session.expire();
            }
            return;
        }
        if (active == null) {
            session.expire();
            return;
        }
        if (!active.isInfinite()) {
            return;
        }

        float duration = session.durationForLeaving();
        effects.removeEffect(ref, effectIndex, store);
        if (duration <= 0.0f
                || !effects.addEffect(
                        ref,
                        effectIndex,
                        effect,
                        duration,
                        OverlapBehavior.OVERWRITE,
                        store)) {
            session.expire();
        }
        if (hud != null) {
            hud.setRelaxedVisible(effects.hasEffect(effectIndex));
        }
    }

    private static boolean isSeated(
            Ref<EntityStore> ref,
            Store<EntityStore> store) {
        MountedComponent mounted = store.getComponent(
                ref, MountedComponent.getComponentType());
        return mounted != null
                && mounted.getMountedToBlock() != null
                && mounted.getBlockMountType() == BlockMountType.Seat;
    }

    private static TavernsHud hud(PlayerRef playerRef, Player player) {
        if (player.getHudManager().getCustomHud(TavernsHud.KEY) instanceof TavernsHud hud) {
            return hud;
        }
        TavernsHud hud = new TavernsHud(playerRef);
        player.getHudManager().addCustomHud(playerRef, hud);
        return hud;
    }

    private static void ensureComfortEffect(
            EffectControllerComponent effects,
            Ref<EntityStore> ref,
            Store<EntityStore> store) {
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(EFFECT_ID);
        int effectIndex = EntityEffect.getAssetMap().getIndex(EFFECT_ID);
        if (effect != null && effectIndex >= 0 && !effects.hasEffect(effectIndex)) {
            effects.addInfiniteEffect(ref, effectIndex, effect, store);
        }
    }

    static List<TavernsHud.ComfortSource> contributingSources(
            List<RegisteredComfortObject> objects) {
        Map<ComfortCategory, ComfortThreshold> thresholds =
                ComfortThreshold.designDefaults();
        ComfortScore score = calculateScore(objects, 0, thresholds);
        return contributingSources(objects, score, thresholds);
    }

    static List<TavernsHud.ComfortSource> contributingSources(
            List<RegisteredComfortObject> objects,
            ComfortScore score,
            Map<ComfortCategory, ComfortThreshold> thresholds) {
        Map<ComfortCategory, List<RegisteredComfortObject>> contributors =
                new HashMap<>();
        for (RegisteredComfortObject object :
                ComfortScore.contributors(objects, score, thresholds)) {
            contributors.computeIfAbsent(object.category(), ignored -> new ArrayList<>())
                    .add(object);
        }
        List<TavernsHud.ComfortSource> sources = new ArrayList<>();
        for (ComfortCategory category : CONTRIBUTING_CATEGORY_ORDER) {
            for (RegisteredComfortObject winner :
                    contributors.getOrDefault(category, List.of())) {
                sources.add(new TavernsHud.ComfortSource(
                        winner.assetId(), winner.comfort()));
            }
        }
        return List.copyOf(sources);
    }

    private record CacheEntry(CoreRecord core, TavernComfortSnapshot snapshot) {
    }
}
