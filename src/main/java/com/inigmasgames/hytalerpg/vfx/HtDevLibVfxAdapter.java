package com.inigmasgames.hytalerpg.vfx;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import org.hytaledevlib.lib.ParticleHelper;

/** Optional HTDevLib boundary kept behind LinkTreeVfxService. */
public final class HtDevLibVfxAdapter implements LinkTreeVfxService.Adapter {
    @Override public boolean emit(World world, Player actor, String nativeEffectId) {
        ParticleHelper.spawnParticleAtEntity(world, nativeEffectId, actor);
        return true;
    }
}
