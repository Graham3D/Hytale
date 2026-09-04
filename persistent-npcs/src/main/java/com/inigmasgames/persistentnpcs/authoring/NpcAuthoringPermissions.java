package com.inigmasgames.persistentnpcs.authoring;

import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import java.util.List;

/** Explicit permission surface for every NPC Authoring Studio domain. */
public final class NpcAuthoringPermissions {
    public static final String OPEN = "inigmasgames.immersivenpcs.authoring.open";
    public static final String PROFILE = "inigmasgames.immersivenpcs.authoring.profile";
    public static final String GENERATE = "inigmasgames.immersivenpcs.authoring.generate";
    public static final String APPEARANCE = "inigmasgames.immersivenpcs.authoring.appearance";
    public static final String VOICE = "inigmasgames.immersivenpcs.authoring.voice";
    public static final String INVENTORY = "inigmasgames.immersivenpcs.authoring.inventory";
    public static final String GEAR = "inigmasgames.immersivenpcs.authoring.gear";
    public static final String ADVANCED = "inigmasgames.immersivenpcs.authoring.advanced";

    private static final List<String> ALL = List.of(OPEN, PROFILE, GENERATE,
            APPEARANCE, VOICE, INVENTORY, GEAR, ADVANCED);

    private NpcAuthoringPermissions() { }

    public static void registerAll() {
        for (String permission : ALL) PermissionsModule.registerPermission(permission);
    }
}
