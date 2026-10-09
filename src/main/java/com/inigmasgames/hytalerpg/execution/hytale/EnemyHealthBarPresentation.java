package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.dependency.SystemGroupDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.UIComponentsUpdate;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.entityui.UIComponentList;
import com.hypixel.hytale.server.core.modules.entityui.asset.EntityUIComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Vector3d;

/** Transient, viewer-local admission of Hytale's entity-attached Healthbar. */
public final class EnemyHealthBarPresentation {
    private static final long VISIBLE_NANOS = 3_000_000_000L;
    private static final long FOLLOWUP_NANOS = 100_000_000L;
    private record Enemy(UUID world, UUID id) { }
    private record Baseline(int[] nativeIds, int[] hiddenIds) { }
    private record Window(UUID world, long until, long followupAt, boolean followupQueued) { }
    private record Anchor(Ref<EntityStore> ref, String text) { }
    private final Map<Enemy, Baseline> baselines = new ConcurrentHashMap<>();
    private final java.util.Set<Enemy> qaNative = ConcurrentHashMap.newKeySet();
    private final Map<Enemy,Long> qaCanonicalQueuePending = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Window>> viewers = new ConcurrentHashMap<>();
    private final Map<Enemy, Anchor> nameAnchors = new ConcurrentHashMap<>();
    private final Map<Enemy, Anchor> affixAnchors = new ConcurrentHashMap<>();
    private final Map<Enemy, PromotedNameGlyphs.Name> coloredNames = new ConcurrentHashMap<>();
    private final PromotedNameBillboards coloredPackets = new PromotedNameBillboards();
    private final java.util.Set<String> coloredNameFailures = ConcurrentHashMap.newKeySet();
    private final Map<Enemy, String> traceOutcomes = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean missingAssetLogged = new java.util.concurrent.atomic.AtomicBoolean();
    private HytaleBossBarTracker bosses;
    @FunctionalInterface public interface TargetDamageObserver {
        void accept(UUID world, UUID enemy, UUID player);
    }
    private TargetDamageObserver targetHealthChanged;
    public void configureBossBars(HytaleBossBarTracker tracker) { bosses = java.util.Objects.requireNonNull(tracker); }
    /** Legacy projected-target diagnostic callback; not wired in the live build. */
    public void configureTargetHealthChanged(TargetDamageObserver observer) {
        targetHealthChanged=java.util.Objects.requireNonNull(observer);
    }

    /** Persistent affix anchor and packet-only promoted name share one hitbox layout. */
    public boolean presentPromotedAnchors(Store<EntityStore> store, Ref<EntityStore> enemy, UUID id,
                                          com.inigmasgames.hytalerpg.enemies.EnemyDisplayDto display) {
        var key = new Enemy(store.getExternalData().getWorld().getWorldConfig().getUuid(), id);
        String affixText = MonsterAffixLabel.row(display);
        if (affixText.isBlank()) { removeAnchors(store, key); return false; }
        var position = store.getComponent(enemy, TransformComponent.getComponentType());
        var box = store.getComponent(enemy, BoundingBox.getComponentType());
        var asset = ModelAsset.getAssetMap().getAsset("Invisible_Projectile");
        if (position == null || box == null || asset == null) {
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                    "RPG_ENEMY_PRESENTATION_ANCHOR_UNAVAILABLE enemy=%s transform=%s hitbox=%s model=%s",
                    id, position != null, box != null, asset != null);
            return false;
        }
        var rows = MonsterPresentationLayout.resolve(position.getPosition(), box.getBoundingBox(),
                store.getComponent(enemy,ModelComponent.getComponentType()),
                store.getComponent(enemy,com.inigmasgames.hytalerpg.enemies.EnemyActorIdentity.getComponentType()));
        var priorAffix=affixAnchors.get(key);
        if(priorAffix!=null&&priorAffix.ref().isValid())
            updateAnchor(store,affixAnchors,key,priorAffix,affixText);
        else {
            removeAnchor(store,affixAnchors.remove(key));
            affixAnchors.put(key,new Anchor(createAnchor(store,asset,rows.affixAnchorPosition(),affixText),affixText));
        }
        boolean promoted=PromotedNameGlyphs.rarityAsset(display.rarityLabel())!=null
                &&!"Minion".equals(display.packRoleLabel());
        if(promoted){
            removeAnchor(store,nameAnchors.remove(key));
            var old=coloredNames.get(key);
            if(old!=null&&old.valid()&&old.text().equals(display.name())&&old.rarity().equals(display.rarityLabel()))return true;
            try {
                var next=PromotedNameGlyphs.prepare(display.name(),display.rarityLabel());
                coloredNames.put(key,next);
                if(old!=null)coloredPackets.removeOwner(store,id);
                com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log(
                        "RPG_ENEMY_COLORED_NAME_PREPARED enemy=%s rarity=%s glyphs=%s nameY=%s",
                        id,display.rarityLabel(),next.glyphs().size(),rows.nameY());
                return true;
            } catch(RuntimeException failure) {
                coloredNames.remove(key);
                coloredPackets.removeOwner(store,id);
                String reason=failure.getClass().getSimpleName()+":"+failure.getMessage();
                if(coloredNameFailures.add(reason))
                    com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                            "RPG_ENEMY_COLORED_NAME_FAILED reason=%s nativeNamePreserved=true",reason);
                return false;
            }
        }
        coloredNames.remove(key);
        coloredPackets.removeOwner(store,id);
        var priorName=nameAnchors.get(key);
        if(priorName!=null&&priorName.ref().isValid())updateAnchor(store,nameAnchors,key,priorName,display.name());
        else {
            removeAnchor(store,nameAnchors.remove(key));
            nameAnchors.put(key,new Anchor(createAnchor(store,asset,rows.nameAnchorPosition(),display.name()),display.name()));
        }
        return true;
    }

    private static Ref<EntityStore> createAnchor(Store<EntityStore> store,ModelAsset asset,
                                                  Vector3d position,String text) {
        var holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(UUIDComponent.getComponentType(), new UUIDComponent(UUID.randomUUID()));
        holder.addComponent(NetworkId.getComponentType(),
                new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.addComponent(TransformComponent.getComponentType(),
                new TransformComponent(position, new Rotation3f()));
        holder.addComponent(ModelComponent.getComponentType(),
                new ModelComponent(Model.createStaticScaledModel(asset, 1)));
        holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(new Box(Box.ZERO)));
        holder.addComponent(Nameplate.getComponentType(), new Nameplate(text));
        holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
        return store.addEntity(holder, com.hypixel.hytale.component.AddReason.SPAWN);
    }

    private static void updateAnchor(Store<EntityStore> store,Map<Enemy,Anchor> owner,
                                     Enemy key,Anchor old,String text) {
        if(old.text().equals(text))return;
        var plate=store.getComponent(old.ref(),Nameplate.getComponentType());
        if(plate!=null)plate.setText(text);
        owner.replace(key,old,new Anchor(old.ref(),text));
    }

    private void removeAnchors(Store<EntityStore> store, Enemy key) {
        removeAnchor(store,nameAnchors.remove(key));
        removeAnchor(store,affixAnchors.remove(key));
        coloredNames.remove(key);
        coloredPackets.removeOwner(store,key.id());
    }

    public void removePromotedAnchors(Store<EntityStore> store,UUID id) {
        removeAnchors(store,new Enemy(store.getExternalData().getWorld().getWorldConfig().getUuid(),id));
    }

    private static void removeAnchor(Store<EntityStore> store,Anchor anchor) {
        if (anchor != null && anchor.ref().isValid())
            store.removeEntity(anchor.ref(), com.hypixel.hytale.component.RemoveReason.REMOVE);
    }

    /** Suppress only the native Healthbar on the shared actor; other UI components stay native. */
    public void hideByDefault(Store<EntityStore> store, Ref<EntityStore> enemy, UUID id) {
        int healthbar = EntityUIComponent.getAssetMap().getIndex("Healthbar");
        if (healthbar < 0) {
            if(missingAssetLogged.compareAndSet(false,true))
                com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                        "RPG_ENEMY_HEALTHBAR outcome=NATIVE_ASSET_MISSING asset=Healthbar");
            return;
        }
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

    /** QA actors use Hytale's shared entity UI list and the actor's real Health stat. */
    public void attachQaNative(Store<EntityStore> store, Ref<EntityStore> enemy, UUID id) {
        if (!store.isInThread() || enemy == null || !enemy.isValid())
            throw new IllegalStateException("QA_NATIVE_HEALTHBAR_WRITER_REQUIRED");
        var stats = store.getComponent(enemy, EntityStatMap.getComponentType());
        if (stats == null || stats.get(DefaultEntityStatTypes.getHealth()) == null)
            throw new IllegalStateException("QA_NATIVE_HEALTHBAR_HEALTH_MISSING");
        var asset = EntityUIComponent.getAssetMap().getAsset("Healthbar");
        if (asset == null || asset.toPacket().type != com.hypixel.hytale.protocol.EntityUIType.EntityStat
                || asset.toPacket().entityStatIndex != DefaultEntityStatTypes.getHealth())
            throw new IllegalStateException("QA_NATIVE_HEALTHBAR_ASSET_MISMATCH");
        var key = new Enemy(store.getExternalData().getWorld().getWorldConfig().getUuid(), id);
        hideByDefault(store, enemy, id);
        var current = store.getComponent(enemy, UIComponentList.getComponentType());
        if (current == null) throw new IllegalStateException("QA_NATIVE_HEALTHBAR_LIST_MISSING");
        int healthbar = EntityUIComponent.getAssetMap().getIndex("Healthbar");
        int[] ids = current.getComponentIds();
        int[] shown = withHealthbar(ids, healthbar);
        if (shown.length != ids.length) {
            store.putComponent(enemy, UIComponentList.getComponentType(), list(shown));
        }
        if(qaNative.add(key)){
            qaCanonicalQueuePending.put(key,System.nanoTime());
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log(
                    "RPG_ENEMY_QA_NATIVE_HEALTHBAR_ATTACHED enemy=%s component=%s canonicalIds=%s",
                    id,healthbar,Arrays.toString(store.getComponent(enemy,UIComponentList.getComponentType()).getComponentIds()));
        }
    }

    public boolean qaNativeEnabled(UUID world, UUID enemy) { return qaNative.contains(new Enemy(world, enemy)); }

    /** Read-only connected QA evidence after all birth publication writes have completed. */
    public void verifyQaNative(Store<EntityStore> store, Ref<EntityStore> enemy, UUID id) {
        if (enemy == null || !enemy.isValid()) return;
        var ui=store.getComponent(enemy,UIComponentList.getComponentType());
        var stats=store.getComponent(enemy,EntityStatMap.getComponentType());
        var health=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
        int bar=EntityUIComponent.getAssetMap().getIndex("Healthbar");
        boolean present=ui!=null&&Arrays.stream(ui.getComponentIds()).anyMatch(value->value==bar);
        boolean enabled=store.getExternalData().getWorld().getGameplayConfig().getCombatConfig().isDisplayHealthBars();
        var asset=EntityUIComponent.getAssetMap().getAsset("Healthbar");
        com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log(
                "RPG_ENEMY_QA_PRESENTATION_FINAL enemy=%s mode=%s gameplayNativeBars=%s canonicalIds=%s listContainsNativeBar=%s healthStatIndex=%s healthRegistered=%s health=%s max=%s nativeHitboxOffset=%s affixAnchor=%s",
                id,qaNativeEnabled(store.getExternalData().getWorld().getWorldConfig().getUuid(),id)?"SHARED_LIST_QA_CONTROL":"VIEWER_LOCAL_NATIVE",
                enabled,ui==null?"missing":Arrays.toString(ui.getComponentIds()),present,
                asset==null?"missing":asset.toPacket().entityStatIndex,health!=null,
                health==null?"missing":health.get(),health==null?"missing":health.getMax(),
                asset==null?"missing":asset.toPacket().hitboxOffset,
                (nameAnchors.containsKey(new Enemy(store.getExternalData().getWorld().getWorldConfig().getUuid(),id))
                        ||coloredNames.containsKey(new Enemy(store.getExternalData().getWorld().getWorldConfig().getUuid(),id)))
                        && affixAnchors.containsKey(new Enemy(store.getExternalData().getWorld().getWorldConfig().getUuid(),id)));
    }

    /** Remove only the QA-added Healthbar; keep other owners' UI components. */
    public void removeQaNative(Store<EntityStore> store, Ref<EntityStore> enemy, UUID id) {
        if (enemy == null || !enemy.isValid()) return;
        var key = new Enemy(store.getExternalData().getWorld().getWorldConfig().getUuid(), id);
        if (!qaNative.remove(key)) return;
        qaCanonicalQueuePending.remove(key);
        removeHealthbar(store, enemy);
    }

    /** Detach already captured QA ownership before its asynchronous world callback. */
    public void restoreDetachedQaNative(Store<EntityStore> store, Ref<EntityStore> enemy) {
        if (enemy != null && enemy.isValid()) removeHealthbar(store, enemy);
    }

    private static void removeHealthbar(Store<EntityStore> store, Ref<EntityStore> enemy) {
        var current = store.getComponent(enemy, UIComponentList.getComponentType());
        if (current == null) return;
        int healthbar = EntityUIComponent.getAssetMap().getIndex("Healthbar");
        int[] ids = current.getComponentIds();
        int[] without = withoutHealthbar(ids, healthbar);
        if (without.length != ids.length)
            store.putComponent(enemy, UIComponentList.getComponentType(), list(without));
    }

    /** Called only after native ApplyDamage, with its measured positive Health loss. */
    public void reveal(Store<EntityStore> store, CommandBuffer<EntityStore> buffer,
                       Ref<EntityStore> source, Ref<EntityStore> enemy, double actualLoss) {
        if (actualLoss <= 0 || enemy == null || !enemy.isValid()) return;
        var id = store.getComponent(enemy, UUIDComponent.getComponentType());
        if(id==null)return;
        var world = store.getExternalData().getWorld().getWorldConfig().getUuid();
        var key=new Enemy(world,id.getUuid());
        if(qaNative.contains(key)){
            var stats=store.getComponent(enemy,EntityStatMap.getComponentType());
            var health=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
            var ui=store.getComponent(enemy,UIComponentList.getComponentType());
            int bar=EntityUIComponent.getAssetMap().getIndex("Healthbar");
            boolean present=ui!=null&&Arrays.stream(ui.getComponentIds()).anyMatch(value->value==bar);
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log(
                    "RPG_ENEMY_QA_NATIVE_HEALTHBAR_DAMAGE enemy=%s actualLoss=%s health=%s max=%s listContainsHealthbar=%s",
                    id.getUuid(),actualLoss,health==null?"missing":health.get(),health==null?"missing":health.getMax(),present);
            return; // Canonical shared-list control: no viewer-local packet or expiry.
        }
        if(targetHealthChanged!=null){
            var player=source==null||!source.isValid()?null:
                    store.getComponent(source,PlayerRef.getComponentType());
            targetHealthChanged.accept(world,id.getUuid(),player==null?null:player.getUuid());
            return; // The configured target billboard supersedes transient native world bars.
        }
        if(source==null||!source.isValid()){trace(key,"PLAYER_SOURCE_MISSING");return;}
        var player = store.getComponent(source, PlayerRef.getComponentType());
        if(player==null){trace(key,"SOURCE_NOT_PLAYER");return;}
        if(store.getComponent(enemy, DeathComponent.getComponentType()) != null)return;
        var baseline = baselines.get(key);
        if (baseline == null && eligibleUnboundHostile(store, enemy, world)) {
            // Some ordinary native hostiles have no authored difficulty profile.
            // The damage event is inside ECS processing; mutate the shared UI
            // list only from the command buffer's later writable phase.
            java.util.Objects.requireNonNull(buffer).run(later -> {
                if(!enemy.isValid() || !source.isValid())return;
                try{
                    hideByDefault(later,enemy,id.getUuid());
                    reveal(later,null,source,enemy,actualLoss);
                }catch(RuntimeException presentationFailure){
                    baselines.remove(key);
                    com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                            "RPG_ENEMY_HEALTHBAR outcome=DEFERRED_PRESENTATION_FAILED reason=%s",
                            String.valueOf(presentationFailure));
                }
            });
            return;
        }
        if (baseline == null) {trace(key,"BASELINE_MISSING");return;}
        var stats = store.getComponent(enemy, EntityStatMap.getComponentType());
        var health = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
        if (health == null || health.get() <= 0 || health.getMax() <= 0) {trace(key,"HEALTH_UNAVAILABLE");return;}
        var viewer = store.getComponent(source, EntityTrackerSystems.EntityViewer.getComponentType());
        if (viewer == null || !viewer.visible.contains(enemy)) {trace(key,"VIEWER_NOT_TRACKING");return;}
        viewer.queueUpdate(enemy, new UIComponentsUpdate(baseline.nativeIds.clone()));
        trace(key,"REVEAL_QUEUED");
        long now=System.nanoTime();
        viewers.computeIfAbsent(player.getUuid(), ignored -> new ConcurrentHashMap<>())
                .put(id.getUuid(), new Window(world, now + VISIBLE_NANOS, now + FOLLOWUP_NANOS, false));
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
            var baseline = baselines.get(new Enemy(world, item.getKey()));
            if (now < window.until) {
                if (!window.followupQueued && now >= window.followupAt && baseline != null) {
                    // A native UI-list update in the damage tick can supersede the
                    // initial viewer packet. Requeue on the next bounded HUD poll.
                    viewer.queueUpdate(target, new UIComponentsUpdate(baseline.nativeIds.clone()));
                    active.replace(item.getKey(), window,
                            new Window(world, window.until, window.followupAt, true));
                    trace(new Enemy(world,item.getKey()),"REVEAL_REQUEUED");
                }
                continue;
            }
            if (baseline != null) viewer.queueUpdate(target, new UIComponentsUpdate(baseline.hiddenIds.clone()));
            active.remove(item.getKey(), window);
        }
        if (active.isEmpty()) viewers.remove(player.getUuid(), active);
    }

    public void hideDead(Store<EntityStore> store, Ref<EntityStore> enemy, CommandBuffer<EntityStore> buffer) {
        var id = store.getComponent(enemy, UUIDComponent.getComponentType());
        if (id == null) return;
        var world = store.getExternalData().getWorld().getWorldConfig().getUuid();
        var key = new Enemy(world,id.getUuid());
        buffer.run(later -> removeAnchors(later,key));
        if (qaNativeEnabled(world, id.getUuid())) {
            UUID nativeId = id.getUuid();
            buffer.run(later -> removeQaNative(later, enemy, nativeId));
        }
        var baseline = baselines.get(key);
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
        var key=new Enemy(world,enemy);
        var name=nameAnchors.remove(key);
        var affix=affixAnchors.remove(key);
        var colored=coloredNames.remove(key);
        if(name!=null||affix!=null||colored!=null){
            var nativeWorld=Universe.get().getWorld(world);
            if(nativeWorld!=null)try{nativeWorld.execute(()->{
                if(nameAnchors.containsKey(key)||affixAnchors.containsKey(key)||coloredNames.containsKey(key))return;
                var store=nativeWorld.getEntityStore().getStore();
                removeAnchor(store,name);
                removeAnchor(store,affix);
                coloredPackets.removeOwner(store,enemy);
            });}catch(RuntimeException closing){/* World unload owns unsaved carriers. */}
        }
        qaNative.remove(new Enemy(world, enemy));
        qaCanonicalQueuePending.remove(new Enemy(world, enemy));
        baselines.remove(new Enemy(world, enemy));
        traceOutcomes.remove(new Enemy(world,enemy));
        for (var active : viewers.values()) active.remove(enemy);
    }

    public void forgetViewer(UUID player) { viewers.remove(player); coloredPackets.forgetViewer(player); }

    public void shutdown() {
        var owned=new java.util.HashSet<Enemy>(nameAnchors.keySet());
        owned.addAll(affixAnchors.keySet());
        owned.addAll(coloredNames.keySet());
        for(var key:owned)forget(key.world(),key.id());
        coloredPackets.shutdown();
        viewers.clear();
    }

    /** The world owns nonserialized carriers on unload; discard stale Java references. */
    public void forgetWorld(UUID world) {
        nameAnchors.keySet().removeIf(key -> key.world().equals(world));
        affixAnchors.keySet().removeIf(key -> key.world().equals(world));
        coloredNames.keySet().removeIf(key -> key.world().equals(world));
        coloredPackets.forgetWorld(world);
        baselines.keySet().removeIf(key -> key.world().equals(world));
        qaNative.removeIf(key -> key.world().equals(world));
        qaCanonicalQueuePending.keySet().removeIf(key -> key.world().equals(world));
        for(var active:viewers.values())active.entrySet().removeIf(entry -> entry.getValue().world().equals(world));
    }

    /** Native per-tick transform replication keeps the zero-size Nameplate carrier with its actor. */
    public static final class AnchorFollow extends EntityTickingSystem<EntityStore> {
        private final EnemyHealthBarPresentation owner;
        public AnchorFollow(EnemyHealthBarPresentation owner){this.owner=java.util.Objects.requireNonNull(owner);}
        @Override public Query<EntityStore> getQuery(){return Query.and(
                UUIDComponent.getComponentType(), TransformComponent.getComponentType(),
                BoundingBox.getComponentType(),
                com.inigmasgames.hytalerpg.enemies.EnemyActorIdentity.getComponentType());}
        @Override public void tick(float delta,int index,com.hypixel.hytale.component.ArchetypeChunk<EntityStore> chunk,
                                   Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
            try(var span=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,
                    com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.HUD)){
            var actor=chunk.getReferenceTo(index);
            var id=chunk.getComponent(index,UUIDComponent.getComponentType());
            var key=new Enemy(store.getExternalData().getWorld().getWorldConfig().getUuid(),id.getUuid());
            var name=owner.nameAnchors.get(key);
            var affix=owner.affixAnchors.get(key);
            var colored=owner.coloredNames.get(key);
            if((name==null&&colored==null)||affix==null)return;
            if((name!=null&&!name.ref().isValid())||(colored!=null&&!colored.valid())||!affix.ref().isValid()
                    ||store.getComponent(actor,DeathComponent.getComponentType())!=null){
                boolean alive=store.getComponent(actor,DeathComponent.getComponentType())==null;
                if(alive){
                    var plate=buffer.getComponent(actor,Nameplate.getComponentType());
                    if(plate!=null&&plate.getText().isBlank())plate.setText(colored!=null?colored.text():name.text());
                }
                if(name!=null&&owner.nameAnchors.remove(key,name)&&name.ref().isValid())
                    buffer.tryRemoveEntity(name.ref(),com.hypixel.hytale.component.RemoveReason.REMOVE);
                if(owner.affixAnchors.remove(key,affix)&&affix.ref().isValid())
                    buffer.tryRemoveEntity(affix.ref(),com.hypixel.hytale.component.RemoveReason.REMOVE);
                if(colored!=null&&owner.coloredNames.remove(key,colored))
                    buffer.run(later -> owner.coloredPackets.removeOwner(later,key.id()));
                return;
            }
            var transform=chunk.getComponent(index,TransformComponent.getComponentType());
            var box=chunk.getComponent(index,BoundingBox.getComponentType());
            var rows=MonsterPresentationLayout.resolve(transform.getPosition(),box.getBoundingBox(),
                    chunk.getComponent(index,ModelComponent.getComponentType()),
                    chunk.getComponent(index,com.inigmasgames.hytalerpg.enemies.EnemyActorIdentity.getComponentType()));
            if(name!=null)follow(buffer,name.ref(),rows.nameAnchorPosition());
            follow(buffer,affix.ref(),rows.affixAnchorPosition());
            }
        }
        private static void follow(CommandBuffer<EntityStore> buffer,Ref<EntityStore> anchor,Vector3d next){
            var follower=buffer.getComponent(anchor,TransformComponent.getComponentType());
            if(follower!=null&&follower.getPosition().distanceSquared(next)>1e-8)follower.setPosition(next);
        }
    }

    /** Runs after the native actor spawn packet so packet-only name children have a known parent. */
    public static final class NamePackets extends TickingSystem<EntityStore> {
        private final EnemyHealthBarPresentation owner;
        public NamePackets(EnemyHealthBarPresentation owner) { this.owner=java.util.Objects.requireNonNull(owner); }
        @Override public Set<Dependency<EntityStore>> getDependencies() { return Set.of(
                new SystemDependency<>(Order.AFTER, EntityTrackerSystems.SendPackets.class)); }
        @Override public void tick(float delta, int index, Store<EntityStore> store) {
            try(var span=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,
                    com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.HUD)){
            if(owner.coloredNames.isEmpty())return;
            UUID world=store.getExternalData().getWorld().getWorldConfig().getUuid();
            for(var entry:owner.coloredNames.entrySet()) {
                var key=entry.getKey();
                if(!key.world().equals(world))continue;
                var actor=store.getExternalData().getRefFromUUID(key.id());
                if(actor==null||!actor.isValid()||store.getComponent(actor,DeathComponent.getComponentType())!=null){
                    owner.coloredPackets.removeOwner(store,key.id());
                    continue;
                }
                var transform=store.getComponent(actor,TransformComponent.getComponentType());
                var box=store.getComponent(actor,BoundingBox.getComponentType());
                if(transform==null||box==null)continue;
                try {
                    owner.coloredPackets.sync(store,key.id(),actor,entry.getValue(),
                            MonsterPresentationLayout.resolve(transform.getPosition(),box.getBoundingBox(),
                                    store.getComponent(actor,ModelComponent.getComponentType()),
                                    store.getComponent(actor,com.inigmasgames.hytalerpg.enemies.EnemyActorIdentity.getComponentType())));
                } catch(RuntimeException failure) {
                    String reason=failure.getClass().getSimpleName()+":"+failure.getMessage();
                    if(owner.coloredNameFailures.add(reason))
                        com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                                "RPG_ENEMY_COLORED_NAME_TICK_FAILED reason=%s",reason);
                }
            }
            }
        }
    }

    /** Read-only QA witness after native UIComponentSystems.Update, before SendPackets. */
    public static final class QaCanonicalQueueAudit extends TickingSystem<EntityStore> {
        private static final long TIMEOUT_NANOS=2_000_000_000L;
        private final EnemyHealthBarPresentation owner;
        public QaCanonicalQueueAudit(EnemyHealthBarPresentation owner){this.owner=java.util.Objects.requireNonNull(owner);}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
                new SystemGroupDependency<>(Order.AFTER,EntityTrackerSystems.QUEUE_UPDATE_GROUP),
                new SystemDependency<>(Order.BEFORE,EntityTrackerSystems.SendPackets.class));}
        @Override public void tick(float delta,int index,Store<EntityStore> store){
            try(var span=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,
                    com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.HUD)){
            if(owner.qaCanonicalQueuePending.isEmpty())return;
            var world=store.getExternalData().getWorld().getWorldConfig().getUuid();
            long now=System.nanoTime();
            for(var entry:owner.qaCanonicalQueuePending.entrySet()){
                var key=entry.getKey();if(!key.world().equals(world))continue;
                var actor=store.getExternalData().getRefFromUUID(key.id());
                if(actor==null||!actor.isValid()){owner.qaCanonicalQueuePending.remove(key,entry.getValue());continue;}
                var ui=store.getComponent(actor,UIComponentList.getComponentType());
                if(ui==null)continue;
                int[] canonical=ui.getComponentIds();int tracking=0;boolean queued=false;
                for(var player:store.getExternalData().getWorld().getPlayerRefs()){
                    var playerRef=player.getReference();if(playerRef==null||!playerRef.isValid())continue;
                    var viewer=store.getComponent(playerRef,EntityTrackerSystems.EntityViewer.getComponentType());
                    if(viewer==null)continue;
                    if(viewer.visible.contains(actor))tracking++;
                    var update=viewer.updates.get(actor);if(update==null)continue;
                    for(var packet:update.toUpdatesArray())
                        if(packet instanceof UIComponentsUpdate nativeUi&&Arrays.equals(nativeUi.components,canonical))queued=true;
                }
                if(!queued&&now-entry.getValue()<TIMEOUT_NANOS)continue;
                if(owner.qaCanonicalQueuePending.remove(key,entry.getValue()))
                    com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log(
                            "RPG_ENEMY_QA_CANONICAL_UI_UPDATE enemy=%s nativeQueueObserved=%s trackingViewers=%s canonicalIds=%s phase=AFTER_UI_UPDATE_BEFORE_SEND",
                            key.id(),queued,tracking,Arrays.toString(canonical));
            }
            }
        }
    }

    static int[] withHealthbar(int[] ids, int healthbar) {
        if (Arrays.stream(ids).anyMatch(value -> value == healthbar)) return ids.clone();
        int[] result = Arrays.copyOf(ids, ids.length + 1);
        result[ids.length] = healthbar;
        return result;
    }
    static int[] withoutHealthbar(int[] ids, int healthbar) {
        return Arrays.stream(ids).filter(value -> value != healthbar).toArray();
    }

    private boolean eligibleUnboundHostile(Store<EntityStore> store, Ref<EntityStore> enemy, UUID world) {
        if (HytaleEncounterRewards.excluded(store, enemy)) return false;
        var support = store.getComponent(enemy, WorldSupport.getComponentType());
        if (support == null || support.getDefaultPlayerAttitude() != Attitude.HOSTILE) return false;
        var network = store.getComponent(enemy, NetworkId.getComponentType());
        return bosses == null || network == null || !bosses.isBoss(world, network.getId());
    }

    private void trace(Enemy enemy,String outcome){
        if(outcome.equals(traceOutcomes.put(enemy,outcome)))return;
        com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log(
                "RPG_ENEMY_HEALTHBAR outcome=%s world=%s enemy=%s",outcome,enemy.world(),enemy.id());
    }

    private static int[] allIds() {
        var assets = EntityUIComponent.getAssetMap();
        // Match UIComponentList.update()'s native default order exactly.
        return java.util.stream.IntStream.range(0, assets.getNextIndex()).toArray();
    }
    private static UIComponentList list(int[] ids) {
        var assets = EntityUIComponent.getAssetMap();
        return new UIComponentList(Arrays.stream(ids).mapToObj(assets::getAsset)
                .filter(java.util.Objects::nonNull).map(EntityUIComponent::getId).toArray(String[]::new));
    }
}
