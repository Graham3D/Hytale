package com.inigmasgames.hytalerpg.combat.hytale;

import com.hypixel.hytale.builtin.adventure.wilderness.resource.WildernessTracker;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

/** Uses Hytale's ownership-aware WildernessTracker; never infers Home from nearby blocks. */
public final class HytaleHomeRegionAdapter {
    public boolean isHome(World world, PlayerRef player) {
        WildernessTracker tracker = WildernessTracker.getTracker(world);
        return tracker != null && tracker.isEnabled() && tracker.isHome(player.getTransform().getPosition());
    }
}
