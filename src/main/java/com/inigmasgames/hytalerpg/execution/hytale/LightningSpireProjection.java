package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

/** Identifies the one damageable, unsaved native entity owned by a Spire deployment. */
public final class LightningSpireProjection implements Component<EntityStore> {
    private static ComponentType<EntityStore,LightningSpireProjection> type;
    private final String instance; private final UUID owner;
    public LightningSpireProjection(){this("",new UUID(0,0));}
    public LightningSpireProjection(String instance,UUID owner){this.instance=instance;this.owner=owner;}
    public String instance(){return instance;} public UUID owner(){return owner;}
    public static ComponentType<EntityStore,LightningSpireProjection> getComponentType(){return type;}
    public static void bind(ComponentType<EntityStore,LightningSpireProjection> value){type=value;}
    @Override public LightningSpireProjection clone(){return new LightningSpireProjection(instance,owner);}
}
