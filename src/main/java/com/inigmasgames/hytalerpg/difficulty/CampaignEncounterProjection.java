package com.inigmasgames.hytalerpg.difficulty;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

/** Server-only placement evidence. The frozen encounter journal owns persistence after spawn. */
public final class CampaignEncounterProjection implements Component<EntityStore> {
    private static ComponentType<EntityStore,CampaignEncounterProjection> type;
    public final UUID world, placement;
    public final String role;
    public CampaignEncounterProjection(){this(null,null,"");}
    public CampaignEncounterProjection(UUID world,UUID placement,String role){this.world=world;this.placement=placement;this.role=role;}
    public static void bind(ComponentType<EntityStore,CampaignEncounterProjection> value){type=value;}
    public static ComponentType<EntityStore,CampaignEncounterProjection> getComponentType(){return type;}
    @Override public CampaignEncounterProjection clone(){return new CampaignEncounterProjection(world,placement,role);}
}
