package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.inigmasgames.hytalerpg.enemies.EnemyActorIdentity;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** One hitbox-centred layout for the native monster presentation rows. */
final class MonsterPresentationLayout {
    // World-space row positions all start from the owning actor's actual hitbox top.
    static final double monsterPresentationBaseMargin = 0.12;
    static final double monsterPresentationAffixGap = 0.30;
    // The native affix Nameplate renders above its zero-volume anchor; glyphs render at nameY.
    // Reserve that native lift inside this shared row spacing so the glyph name clears the affixes.
    static final double monsterPresentationNameGap = 1.00;
    // Server/Entity/UI/Healthbar.json is a shared native client-pixel offset.
    static final float NATIVE_HEALTHBAR_HITBOX_OFFSET_Y = -48f;

    record Rows(double centerX, double centerZ, double visualTopY,
                double healthbarY, double affixY, double nameY) {
        Vector3d affixAnchorPosition() {
            return new Vector3d(centerX, affixY, centerZ);
        }
        Vector3d nameAnchorPosition() {
            return new Vector3d(centerX, nameY, centerZ);
        }
    }

    static Rows resolve(Vector3dc position, Box hitbox) {
        return resolve(position,hitbox,1.0);
    }

    static Rows resolve(Vector3dc position, Box hitbox, ModelComponent model, EnemyActorIdentity identity) {
        double multiplier=1.0;
        if(model!=null&&model.getModel()!=null&&identity!=null&&identity.nativeVisualScale()>0)
            multiplier=model.getModel().getScale()/identity.nativeVisualScale();
        return resolve(position,hitbox,multiplier);
    }

    static Rows resolve(Vector3dc position, Box hitbox, double visualMultiplier) {
        if(!Double.isFinite(visualMultiplier)||visualMultiplier<=0)
            throw new IllegalArgumentException("ENEMY_PRESENTATION_VISUAL_SCALE");
        double top = position.y() + hitbox.max.y*visualMultiplier;
        double bar = top + monsterPresentationBaseMargin;
        double affix = bar + monsterPresentationAffixGap;
        return new Rows(position.x() + hitbox.middleX(), position.z() + hitbox.middleZ(),
                top, bar, affix, affix + monsterPresentationNameGap);
    }

    static double desiredHealthbarOffsetFromHitboxCenter(Vector3dc position, Box hitbox) {
        var rows=resolve(position,hitbox);
        return rows.healthbarY()-(position.y()+hitbox.middleY());
    }

    private MonsterPresentationLayout() { }
}
