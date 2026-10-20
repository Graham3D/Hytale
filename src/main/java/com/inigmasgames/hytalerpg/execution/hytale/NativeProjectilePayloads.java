package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.inigmasgames.hytalerpg.execution.Stage04SkillProfile;

/** A projectile is a transport, not an element. Never inherit native carrier gameplay interactions. */
public final class NativeProjectilePayloads {
    private NativeProjectilePayloads() {}
    public static String causeId(String element) {
        return switch (element) {
            case "PHYSICAL" -> "Physical";
            case "FIRE" -> "Fire";
            case "COLD" -> "Ice";
            case "ARCANE" -> "RPG_Arcane";
            case "VOID" -> "RPG_Void";
            case "NECROTIC" -> "RPG_Necrotic";
            default -> throw new IllegalArgumentException("UNKNOWN_PROJECTILE_ELEMENT:" + element);
        };
    }
    public static DamageCause cause(Stage04SkillProfile.Projectile payload) {
        return DamageCause.getAssetMap().getAsset(causeId(payload.details().element()));
    }
}
