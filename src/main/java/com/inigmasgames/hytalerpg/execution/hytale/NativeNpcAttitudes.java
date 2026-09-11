package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.server.npc.role.support.WorldSupport;

/** Call on the owning world thread before reading native NPC attitudes. */
final class NativeNpcAttitudes {
    private NativeNpcAttitudes(){ }
    static WorldSupport prepared(WorldSupport support){
        // 0.7.0-pre.1 getAttitude dereferences its optional cache unconditionally.
        // Social/passive roles need not have requested it through an AI sensor.
        // Native initialization is idempotent: it preserves cached/overridden attitudes.
        support.requireAttitudeCache();
        return support;
    }
}
