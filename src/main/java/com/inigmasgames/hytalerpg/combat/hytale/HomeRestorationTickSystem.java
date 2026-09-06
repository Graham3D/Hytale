package com.inigmasgames.hytalerpg.combat.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.resource.HomeRestorationService;
import com.inigmasgames.hytalerpg.combat.resource.HostileCombatTracker;
import com.inigmasgames.hytalerpg.combat.resource.RpgResourceService;

/** Actual native-world Home loop, driven by WildernessTracker and Hytale EntityStatMap. */
public final class HomeRestorationTickSystem extends EntityTickingSystem<EntityStore> {
    private final HomeRestorationService home;
    private final HostileCombatTracker combat;
    private final RpgResourceService resources;
    private final HytaleHomeRegionAdapter region = new HytaleHomeRegionAdapter();
    public HomeRestorationTickSystem(HomeRestorationService home, HostileCombatTracker combat,
                                     RpgResourceService resources) {
        this.home = home; this.combat = combat; this.resources = resources;
    }
    @Override public Query<EntityStore> getQuery() {
        return Query.and(PlayerRef.getComponentType(), EntityStatMap.getComponentType());
    }
    @Override public void tick(float deltaSeconds, int index, ArchetypeChunk<EntityStore> chunk,
                               Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
        EntityStatMap stats = chunk.getComponent(index, EntityStatMap.getComponentType());
        if (player == null || stats == null) return;
        boolean isHome = region.isHome(store.getExternalData().getWorld(), player);
        home.observe(player.getUuid(), isHome, combat.secondsSinceHostile(player.getUuid()), deltaSeconds,
                resources, new EntityStatResourcePort(stats));
    }
}
