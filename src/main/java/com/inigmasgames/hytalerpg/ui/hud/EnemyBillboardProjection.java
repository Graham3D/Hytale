package com.inigmasgames.hytalerpg.ui.hud;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

/** Viewer-local projection fallback for the textured target card. Native EntityUI supports only stat/combat-text components. */
final class EnemyBillboardProjection {
    // UI uses centered virtual pixels. Fixed world width keeps apparent size proportional to inverse depth.
    private static final double FOCAL_PIXELS = 640;
    private static final double BAR_WORLD_WIDTH = 1.4;
    record Frame(int horizontal, int vertical, int frameWidth, int frameHeight, int panelWidth, int panelHeight, int fontSize) { }

    static Frame project(Store<EntityStore> store, Ref<EntityStore> viewer, Ref<EntityStore> enemy) {
        if (viewer == null || !viewer.isValid() || enemy == null || !enemy.isValid()) return null;
        var position = store.getComponent(enemy, TransformComponent.getComponentType());
        var bounds = store.getComponent(enemy, BoundingBox.getComponentType());
        if (position == null || bounds == null) return null;
        // Use the same eye pose and head rotation as Hytale's target ray.
        var look=TargetUtil.getLook(viewer,store);
        var rotation=look.getRotation();
        if(rotation==null||!rotation.isFinite())return null;
        double top = bounds.getBoundingBox().max.y();
        if (!Double.isFinite(top) || top <= 0) return null;
        var camera = new Vector3d(look.getPosition());
        var overhead = new Vector3d(position.getPosition()).add(0, top + 0.25, 0);
        // Hytale's Transform.getDirection (also used by TargetUtil) faces -Z at zero yaw.
        // +Z puts every looked-at actor behind this projection and hides the entire card.
        return project(camera, overhead,
                look.getDirection(),
                rotation.transform(new Vector3d(1, 0, 0)),
                rotation.transform(new Vector3d(0, 1, 0)));
    }

    static Frame project(Vector3d camera, Vector3d overhead, Vector3d forward, Vector3d right, Vector3d up) {
        var offset = new Vector3d(overhead).sub(camera);
        double depth = offset.dot(forward);
        if (!Double.isFinite(depth) || depth <= 0.5 || depth > 24) return null;
        double x = offset.dot(right) * FOCAL_PIXELS / depth;
        double y = offset.dot(up) * FOCAL_PIXELS / depth;
        if (!Double.isFinite(x) || !Double.isFinite(y) || Math.abs(x) > 700 || Math.abs(y) > 500) return null;
        int frameWidth = Math.max(48, Math.min(176, (int) Math.round(BAR_WORLD_WIDTH * FOCAL_PIXELS / depth)));
        int frameHeight = Math.max(9, (int) Math.round(frameWidth * 24.0 / 128.0));
        int font = Math.max(8, Math.min(17, (int) Math.round(frameWidth * 0.095)));
        int panelWidth = Math.max(96, Math.min(330, (int) Math.round(frameWidth * 1.8)));
        int panelHeight = font * 3 + frameHeight + 12;
        // Vertical is measured from viewport center; put the bottom of the stack above the head.
        return new Frame((int) Math.round(x), (int) Math.round(y + panelHeight / 2.0),
                frameWidth, frameHeight, panelWidth, panelHeight, font);
    }

    private EnemyBillboardProjection() { }
}
