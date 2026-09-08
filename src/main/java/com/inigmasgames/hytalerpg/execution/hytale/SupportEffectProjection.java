package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

/** Unsaved native recipient marker. Temporary target selection is never a durable NPC rebind. */
public final class SupportEffectProjection implements Component<EntityStore> {
    private static ComponentType<EntityStore,SupportEffectProjection> type;
    UUID tauntOwner;
    String lastTauntObservation="";
    double elapsed;
    String presentationAsset="";
    boolean presentationFailureLogged;
    public static ComponentType<EntityStore,SupportEffectProjection> getComponentType(){return type;}
    public static void bind(ComponentType<EntityStore,SupportEffectProjection> registered){type=registered;}
    @Override public SupportEffectProjection clone(){return new SupportEffectProjection();}
}
