package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.ComponentUpdate;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.EntityUpdate;
import com.hypixel.hytale.protocol.HitboxCollisionUpdate;
import com.hypixel.hytale.protocol.IntangibleUpdate;
import com.hypixel.hytale.protocol.ModelTransform;
import com.hypixel.hytale.protocol.ModelUpdate;
import com.hypixel.hytale.protocol.MountController;
import com.hypixel.hytale.protocol.MountedUpdate;
import com.hypixel.hytale.protocol.NewSpawnUpdate;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.TransformUpdate;
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.receiver.IPacketReceiver;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.joml.Vector3d;
import org.joml.Vector3f;

/** Per-viewer native entity packets. No glyph is inserted into the server entity store. */
final class PromotedNameBillboards {
    private record Owner(UUID world, UUID actor) { }
    private record Viewer(IPacketReceiver receiver, int ownerNetwork, int root, int[] glyphs, String text,
                          String rarity, float yaw, float ownerYaw, int bucket, float mountY) {
        int[] ids() {
            var result = new int[glyphs.length + 1];
            result[0] = root;
            System.arraycopy(glyphs, 0, result, 1, glyphs.length);
            return result;
        }
    }

    private final Map<Owner, Map<UUID, Viewer>> active = new HashMap<>();

    synchronized void sync(Store<EntityStore> store, UUID actorId, Ref<EntityStore> actor,
              PromotedNameGlyphs.Name name, MonsterPresentationLayout.Rows rows) {
        var world = store.getExternalData().getWorld();
        var key = new Owner(world.getWorldConfig().getUuid(), actorId);
        var ownerTransform = store.getComponent(actor, TransformComponent.getComponentType());
        var ownerNetwork = store.getComponent(actor, NetworkId.getComponentType());
        if (ownerTransform == null || ownerNetwork == null) {
            removeOwner(store, actorId);
            return;
        }
        var ownerPos = ownerTransform.getPosition();
        float ownerYaw = ownerTransform.getRotation().yaw();
        float mountY = (float) (rows.nameY() - ownerPos.y);
        var renders = active.computeIfAbsent(key, ignored -> new HashMap<>());
        Set<UUID> eligible = new HashSet<>();
        for (var player : world.getPlayerRefs()) {
            var playerActor = player.getReference();
            if (playerActor == null || !playerActor.isValid()) continue;
            var tracker = store.getComponent(playerActor, EntityTrackerSystems.EntityViewer.getComponentType());
            var playerTransform = store.getComponent(playerActor, TransformComponent.getComponentType());
            if (tracker == null || tracker.packetReceiver == null || playerTransform == null
                    || !tracker.visible.contains(actor) || !tracker.sent.containsKey(actor)) continue;
            var playerId = player.getUuid();
            eligible.add(playerId);
            var viewerPos = playerTransform.getPosition();
            double distance = ownerPos.distance(viewerPos);
            float facing = PromotedNameGlyphs.frontFacingYaw(
                    (float) Math.atan2(viewerPos.x - ownerPos.x, viewerPos.z - ownerPos.z));
            var prior = renders.get(playerId);
            int bucket = distanceBucket(distance, prior == null ? -1 : prior.bucket());
            if (prior != null && (!prior.text().equals(name.text()) || !prior.rarity().equals(name.rarity())
                    || prior.receiver() != tracker.packetReceiver || prior.glyphs().length != name.glyphs().size())) {
                despawn(prior);
                renders.remove(playerId);
                prior = null;
            }
            if (prior == null) {
                try {
                    renders.put(playerId, spawn(store, tracker.packetReceiver, ownerNetwork.getId(),
                            ownerPos, rows, name, facing, ownerYaw, bucket, mountY));
                    log("ATTACHED", actorId, playerId, distance);
                } catch (RuntimeException failure) {
                    com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                            "RPG_ENEMY_COLORED_NAME_PACKET_FAILED actor=%s viewer=%s reason=%s",
                            actorId, playerId, failure.toString());
                    throw new IllegalStateException("PROMOTED_NAME_PACKET_DELIVERY_FAILED",failure);
                }
                continue;
            }
            boolean yawChanged = angularDifference(facing, prior.yaw()) > 0.02f
                    || angularDifference(ownerYaw, prior.ownerYaw()) > 0.02f;
            boolean bucketChanged = prior.bucket() != bucket;
            boolean heightChanged = Math.abs(prior.mountY() - mountY) > 0.02f;
            if (!yawChanged && !bucketChanged && !heightChanged) continue;
            // Parent/child mounts are stable across ordinary turns; only transform orientation is refreshed.
            update(prior, ownerPos, rows, name, facing, bucket, mountY, bucketChanged, heightChanged);
            renders.put(playerId, new Viewer(prior.receiver(), prior.ownerNetwork(), prior.root(), prior.glyphs(), prior.text(),
                    prior.rarity(), facing, ownerYaw, bucket, mountY));
        }
        for (var iterator = renders.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            if (eligible.contains(entry.getKey())) continue;
            despawn(entry.getValue());
            log("TRACKING_ENDED", actorId, entry.getKey(), distanceToViewer(store, entry.getKey(), ownerPos));
            iterator.remove();
        }
        if (renders.isEmpty()) active.remove(key);
    }

    synchronized void removeOwner(Store<EntityStore> store, UUID actorId) {
        var key = new Owner(store.getExternalData().getWorld().getWorldConfig().getUuid(), actorId);
        var renders = active.remove(key);
        if (renders == null) return;
        renders.values().forEach(PromotedNameBillboards::despawn);
    }

    synchronized void forgetViewer(UUID viewerId) {
        for (var renders : active.values()) renders.remove(viewerId); // The disconnected client cleared its world.
        active.values().removeIf(Map::isEmpty);
    }

    synchronized void forgetWorld(UUID worldId) {
        for(var entry : active.entrySet()) if(entry.getKey().world().equals(worldId))
            entry.getValue().values().forEach(PromotedNameBillboards::despawn);
        active.keySet().removeIf(key -> key.world().equals(worldId));
    }

    synchronized void shutdown() {
        for(var renders : active.values()) renders.values().forEach(PromotedNameBillboards::despawn);
        active.clear();
    }

    private static Viewer spawn(Store<EntityStore> store, IPacketReceiver receiver, int ownerNetwork,
                                Vector3d ownerPos, MonsterPresentationLayout.Rows rows,
                                PromotedNameGlyphs.Name name, float yaw, float ownerYaw,
                                int bucket, float mountY) {
        int root = store.getExternalData().takeNextNetworkId();
        var glyphIds = new int[name.glyphs().size()];
        for (int i = 0; i < glyphIds.length; i++) glyphIds[i] = store.getExternalData().takeNextNetworkId();
        receiver.writeNoCache(spawnPacket(ownerNetwork, root, glyphIds, ownerPos, rows, name, yaw, bucket, mountY));
        return new Viewer(receiver, ownerNetwork, root, glyphIds, name.text(), name.rarity(), yaw, ownerYaw, bucket, mountY);
    }

    static EntityUpdates spawnPacket(int ownerNetwork, int root, int[] glyphIds,
                                     Vector3d ownerPos, MonsterPresentationLayout.Rows rows,
                                     PromotedNameGlyphs.Name name, float yaw, int bucket, float mountY) {
        if (glyphIds.length != name.glyphs().size()) throw new IllegalArgumentException("PROMOTED_NAME_GLYPH_ID_COUNT");
        var updates = new ArrayList<EntityUpdate>(glyphIds.length + 1);
        var center = rows.nameAnchorPosition();
        updates.add(new EntityUpdate(root, null, new ComponentUpdate[]{
                new NewSpawnUpdate(), new IntangibleUpdate(), new HitboxCollisionUpdate(0),
                new TransformUpdate(transform(center, yaw)),
                new MountedUpdate(ownerNetwork, new Vector3f((float) (center.x - ownerPos.x), mountY,
                        (float) (center.z - ownerPos.z)), MountController.Minecart, null)}));
        for (int index = 0; index < glyphIds.length; index++) {
            var glyph = name.glyphs().get(index);
            int id = glyphIds[index];
            var point = glyphPosition(center, yaw, glyph.horizontal(), bucket);
            updates.add(new EntityUpdate(id, null, new ComponentUpdate[]{
                    new NewSpawnUpdate(), new IntangibleUpdate(), new HitboxCollisionUpdate(0),
                    new TransformUpdate(transform(point, yaw)),
                    new MountedUpdate(root, new Vector3f((float) -glyph.horizontal() * bucketScale(bucket), 0, 0),
                            MountController.Minecart, null),
                    new ModelUpdate(glyph.model(), PromotedNameGlyphs.PROMOTED_NAME_SCALE * bucketScale(bucket))}));
        }
        return new EntityUpdates(null, updates.toArray(EntityUpdate[]::new));
    }

    private static void update(Viewer prior, Vector3d ownerPos, MonsterPresentationLayout.Rows rows,
                               PromotedNameGlyphs.Name name, float yaw, int bucket, float mountY,
                               boolean bucketChanged, boolean heightChanged) {
        prior.receiver().writeNoCache(refreshPacket(prior.ownerNetwork(), prior.root(), prior.glyphs(),
                ownerPos, rows, name, yaw, bucket, mountY, bucketChanged, heightChanged));
    }

    static EntityUpdates refreshPacket(int ownerNetwork, int root, int[] glyphIds,
                                       Vector3d ownerPos, MonsterPresentationLayout.Rows rows,
                                       PromotedNameGlyphs.Name name, float yaw, int bucket, float mountY,
                                       boolean bucketChanged, boolean heightChanged) {
        if (glyphIds.length != name.glyphs().size()) throw new IllegalArgumentException("PROMOTED_NAME_GLYPH_ID_COUNT");
        var center = rows.nameAnchorPosition();
        var updates = new ArrayList<EntityUpdate>(glyphIds.length + 1);
        var rootUpdates = new ArrayList<ComponentUpdate>();
        rootUpdates.add(new TransformUpdate(transform(center, yaw)));
        if (heightChanged) rootUpdates.add(new MountedUpdate(ownerNetwork, new Vector3f(
                (float) (center.x - ownerPos.x), mountY, (float) (center.z - ownerPos.z)),
                MountController.Minecart, null));
        updates.add(new EntityUpdate(root, null, rootUpdates.toArray(ComponentUpdate[]::new)));
        for (int i = 0; i < glyphIds.length; i++) {
            var glyph = name.glyphs().get(i);
            var components = new ArrayList<ComponentUpdate>();
            components.add(new TransformUpdate(transform(glyphPosition(center, yaw, glyph.horizontal(), bucket), yaw)));
            if (bucketChanged) {
                components.add(new MountedUpdate(root, new Vector3f(
                        (float) -glyph.horizontal() * bucketScale(bucket), 0, 0), MountController.Minecart, null));
                components.add(new ModelUpdate(glyph.model(), PromotedNameGlyphs.PROMOTED_NAME_SCALE * bucketScale(bucket)));
            }
            updates.add(new EntityUpdate(glyphIds[i], null, components.toArray(ComponentUpdate[]::new)));
        }
        return new EntityUpdates(null, updates.toArray(EntityUpdate[]::new));
    }

    private static ModelTransform transform(Vector3d point, float yaw) {
        var facing = new Direction(yaw, 0, 0);
        return new ModelTransform(new Position(point.x, point.y, point.z), facing, facing);
    }

    static Vector3d glyphPosition(Vector3d center, float facing, double horizontal, int bucket) {
        // The readable face uses a PI yaw flip. Its local X is opposite viewer-right.
        double offset = horizontal * bucketScale(bucket);
        return new Vector3d(center.x - Math.cos(facing) * offset, center.y,
                center.z + Math.sin(facing) * offset);
    }

    static int distanceBucket(double distance, int prior) {
        if (prior == 0 && distance < 12.75) return 0;
        if (prior == 1 && distance >= 11.25 && distance < 24.75) return 1;
        if (prior == 2 && distance >= 23.25) return 2;
        return distance < 12 ? 0 : distance < 24 ? 1 : 2;
    }
    static float bucketScale(int bucket) { return bucket == 0 ? 1f : bucket == 1 ? 1.25f : 1.5f; }
    private static float angularDifference(float a, float b) {
        return Math.abs((float) Math.atan2(Math.sin(a - b), Math.cos(a - b)));
    }
    private static void despawn(Viewer state) {
        try { state.receiver().writeNoCache(new EntityUpdates(state.ids(), null)); }
        catch (RuntimeException disconnected) { /* The viewer's world is already gone. */ }
    }
    private static double distanceToViewer(Store<EntityStore> store, UUID viewerId, Vector3d ownerPos) {
        for (var player : store.getExternalData().getWorld().getPlayerRefs()) {
            if (!player.getUuid().equals(viewerId)) continue;
            var ref = player.getReference();
            var transform = ref != null && ref.isValid()
                    ? store.getComponent(ref, TransformComponent.getComponentType()) : null;
            return transform == null ? Double.NaN : ownerPos.distance(transform.getPosition());
        }
        return Double.NaN;
    }
    private static void log(String event, UUID owner, UUID viewer, double distance) {
        com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log(
                "RPG_ENEMY_COLORED_NAME outcome=%s actor=%s viewer=%s distance=%.2f",
                event, owner, viewer, distance);
    }
}
