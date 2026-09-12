package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.entityui.UIComponentList;
import com.hypixel.hytale.server.core.modules.entityui.asset.EntityUIComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Same tracker queue as native DamageSystems.EntityUIEvents; never submits fake damage.
 * Uses the recipient's existing CombatText component, without replacing its UI list. */
final class NativeHealingText {
    static String display(Store<EntityStore> store,Ref<EntityStore> actor,HealingTextAccumulator.Value value) {
        try {
            if(!store.getExternalData().getWorld().getWorldConfig().getUuid().equals(value.key().world()))return "WORLD_CHANGED";
            var target=store.getExternalData().getRefFromUUID(java.util.UUID.fromString(value.key().target()));
            if(target==null||!target.isValid()||actor==null||!actor.isValid())return "RECIPIENT_OR_VIEWER_INVALID";
            var ui=store.getComponent(target,UIComponentList.getComponentType());
            int nativeText=EntityUIComponent.getAssetMap().getIndex("CombatText");
            if(ui==null||nativeText<0||java.util.Arrays.stream(ui.getComponentIds()).noneMatch(i->i==nativeText))return "NATIVE_COMBAT_TEXT_NOT_ATTACHED";
            var viewer=store.getComponent(actor,EntityTrackerSystems.EntityViewer.getComponentType());
            if(viewer==null||!viewer.visible.contains(target))return "RECIPIENT_NOT_VISIBLE";
            viewer.queueUpdate(target,new CombatTextUpdate(0,HealingTextAccumulator.text(value.actualHealing()),new Color((byte)121,(byte)255,(byte)0)));
            return "NATIVE_COMBAT_TEXT_QUEUED";
        } catch(RuntimeException failure) {return "PRESENTATION_FAILED_"+failure.getClass().getSimpleName();}
    }
}
