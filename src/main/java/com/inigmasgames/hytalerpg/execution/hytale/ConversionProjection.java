package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.execution.summon.ConversionRegistry;

/** Unsaved control metadata. The natural entity itself is NOT made NonSerialized or replaced. */
public final class ConversionProjection implements Component<EntityStore>{
    private static ComponentType<EntityStore,ConversionProjection> type;
    final ConversionRegistry.Lease lease;
    double nextQuery;
    boolean restored;
    public ConversionProjection(){this(null);}
    public ConversionProjection(ConversionRegistry.Lease lease){this.lease=lease;}
    public static ComponentType<EntityStore,ConversionProjection> getComponentType(){return type;}
    public static void bind(ComponentType<EntityStore,ConversionProjection> value){type=value;}
    @Override public ConversionProjection clone(){var copy=new ConversionProjection(lease);copy.nextQuery=nextQuery;copy.restored=restored;return copy;}
}
