package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Objects;
import java.util.UUID;

/** Unsaved QA provenance; the native actor is also marked NonSerialized before insertion. */
public final class QaTransientMarker implements Component<EntityStore> {
    private static ComponentType<EntityStore,QaTransientMarker> type;
    private final UUID world;
    private final UUID encounter;
    private final UUID actor;
    public QaTransientMarker(){this.world=null;this.encounter=null;this.actor=null;}
    public QaTransientMarker(UUID world,UUID encounter,UUID actor){
        this.world=Objects.requireNonNull(world);this.encounter=Objects.requireNonNull(encounter);
        this.actor=Objects.requireNonNull(actor);
    }
    public UUID world(){return world;}
    public UUID encounter(){return encounter;}
    public UUID actor(){return actor;}
    public static void bind(ComponentType<EntityStore,QaTransientMarker> value){type=Objects.requireNonNull(value);}
    public static ComponentType<EntityStore,QaTransientMarker> getComponentType(){return type;}
    @Override public QaTransientMarker clone(){return new QaTransientMarker(world,encounter,actor);}
}
