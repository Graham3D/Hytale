package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.UIComponentsUpdate;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.entityui.UIComponentList;
import com.hypixel.hytale.server.core.modules.entityui.asset.EntityUIComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Transient, viewer-local projection of Hytale's native Healthbar component. */
public final class EnemyHealthBarPresentation {
    private static final long VISIBLE_NANOS = 3_000_000_000L;
    private record Enemy(UUID world, UUID id) { }
    private record Baseline(int[] nativeIds, int[] hiddenIds) { }
    private record Window(UUID world, long until) { }
    private final Map<Enemy, Baseline> baselines = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Window>> viewers = new ConcurrentHashMap<>();

    /** Suppress only the native Healthbar on the shared actor; other UI components stay native. */
    public void hideByDefault(Store<EntityStore> store, Ref<EntityStore> enemy, UUID id) {
        int healthbar = EntityUIComponent.getAssetMap().getIndex("Healthbar");
        if (healthbar < 0) return;
        var world = store.getExternalData().getWorld().getWorldConfig().getUuid();
        var key = new Enemy(world, id);
        if (baselines.containsKey(key)) return;
        var original = store.getComponent(enemy, UIComponentList.getComponentType());
        int[] originalIds = original == null ? allIds() : original.getComponentIds().clone();
        boolean hadHealthbar = Arrays.stream(originalIds).anyMatch(value -> value == healthbar);
        int[] hidden = Arrays.stream(originalIds).filter(value -> value != healthbar).toArray();
        int[] shown = hadHealthbar ? originalIds : Arrays.copyOf(hidden, hidden.length + 1);
        if (!hadHealthbar) shown[shown.length - 1] = healthbar;
        if (baselines.putIfAbsent(key, new Baseline(shown, hidden)) != null) return;
        if (original == null || hadHealthbar) store.putComponent(enemy, UIComponentList.getComponentType(), list(hidden));
    }

    /** Called only after native ApplyDamage, with its measured positive Health loss. */
    public void reveal(Store<EntityStore> store, Ref<EntityStore> source, Ref<EntityStore> enemy, double actualLoss) {
        if (actualLoss <= 0 || source == null || enemy == null || !source.isValid() || !enemy.isValid()) return;
        var player = store.getComponent(source, PlayerRef.getComponentType());
        var id = store.getComponent(enemy, UUIDComponent.getComponentType());
        if (player == null || id == null || store.getComponent(enemy, DeathComponent.getComponentType()) != null) return;
        var world = store.getExternalData().getWorld().getWorldConfig().getUuid();
        var baseline = baselines.get(new Enemy(world, id.getUuid()));
        if (baseline == null) return;
        var stats = store.getComponent(enemy, EntityStatMap.getComponentType());
        var health = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
        if (health == null || health.get() <= 0 || health.getMax() <= 0) return;
        var viewer = store.getComponent(source, EntityTrackerSystems.EntityViewer.getComponentType());
        if (viewer == null || !viewer.visible.contains(enemy)) return;
        viewer.queueUpdate(enemy, new UIComponentsUpdate(baseline.nativeIds.clone()));
        viewers.computeIfAbsent(player.getUuid(), ignored -> new ConcurrentHashMap<>())
                .put(id.getUuid(), new Window(world, System.nanoTime() + VISIBLE_NANOS));
    }

    /** Reuses the existing four-Hz player presentation tick; work scales with revealed bars. */
    public void tick(PlayerRef player, Store<EntityStore> store, Ref<EntityStore> actor) {
        var active = viewers.get(player.getUuid());
        if (active == null || active.isEmpty()) return;
        var viewer = store.getComponent(actor, EntityTrackerSystems.EntityViewer.getComponentType());
        var world = store.getExternalData().getWorld().getWorldConfig().getUuid();
        long now = System.nanoTime();
        for (var item : active.entrySet()) {
            var window = item.getValue();
            var target = window.world.equals(world) ? store.getExternalData().getRefFromUUID(item.getKey()) : null;
            if (target == null || !target.isValid() || viewer == null || !viewer.visible.contains(target)
                    || store.getComponent(target, DeathComponent.getComponentType()) != null) {
                active.remove(item.getKey(), window);
                continue; // Native entity removal/visibility already clears its client projection.
            }
            if (now < window.until) continue;
            var baseline = baselines.get(new Enemy(world, item.getKey()));
            if (baseline != null) viewer.queueUpdate(target, new UIComponentsUpdate(baseline.hiddenIds.clone()));
            active.remove(item.getKey(), window);
        }
        if (active.isEmpty()) viewers.remove(player.getUuid(), active);
    }

    public void hideDead(Store<EntityStore> store, Ref<EntityStore> enemy) {
        var id = store.getComponent(enemy, UUIDComponent.getComponentType());
        if (id == null) return;
        var world = store.getExternalData().getWorld().getWorldConfig().getUuid();
        var baseline = baselines.get(new Enemy(world, id.getUuid()));
        if (baseline == null) return;
        for (var entry : viewers.entrySet()) {
            if (entry.getValue().remove(id.getUuid()) == null) continue;
            var actor = store.getExternalData().getRefFromUUID(entry.getKey());
            var viewer = actor == null || !actor.isValid() ? null : store.getComponent(actor, EntityTrackerSystems.EntityViewer.getComponentType());
            if (viewer != null && viewer.visible.contains(enemy))
                viewer.queueUpdate(enemy, new UIComponentsUpdate(baseline.hiddenIds.clone()));
        }
    }

    public void forget(UUID world, UUID enemy) {
        baselines.remove(new Enemy(world, enemy));
        for (var active : viewers.values()) active.remove(enemy);
    }

    public void forgetViewer(UUID player) { viewers.remove(player); }

    private static int[] allIds() {
        var assets = EntityUIComponent.getAssetMap();
        return assets.getAssetMap().keySet().stream().mapToInt(assets::getIndex).filter(id -> id >= 0).toArray();
    }
    private static UIComponentList list(int[] ids) {
        var assets = EntityUIComponent.getAssetMap();
        return new UIComponentList(Arrays.stream(ids).mapToObj(assets::getAsset)
                .filter(java.util.Objects::nonNull).map(EntityUIComponent::getId).toArray(String[]::new));
    }
}
