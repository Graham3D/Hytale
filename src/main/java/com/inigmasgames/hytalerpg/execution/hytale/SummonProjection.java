package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

/** An RPG-created native entity, never a player-owned Rune, natural NPC or saved conversion. */
public final class SummonProjection implements Component<EntityStore> {
    private static ComponentType<EntityStore,SummonProjection> type;
    final UUID token;
    double nextQuery;
    boolean followingOwner;
    boolean combatEngaged;
    String followState="";
    public SummonProjection(){this(null);}
    public SummonProjection(UUID token){this.token=token;}
    public static ComponentType<EntityStore,SummonProjection> getComponentType(){return type;}
    public static void bind(ComponentType<EntityStore,SummonProjection> registered){type=registered;}
    @Override public SummonProjection clone(){var copy=new SummonProjection(token);copy.nextQuery=nextQuery;copy.followingOwner=followingOwner;
        copy.combatEngaged=combatEngaged;copy.followState=followState;return copy;}
}
