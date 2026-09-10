package com.inigmasgames.hytalerpg.ui.skilltree;

/** Presentation-only mapping; native Item.Icon and CustomUI receive the same unmodified artwork. */
public final class RpgSkillIcons {
    private RpgSkillIcons() {}

    public static String forSkill(String id) {
        if (id == null) return RpgSkillTreeProjectionService.PLACEHOLDER_ICON;
        return switch (id) {
            case "fire_bolt" -> "Icons/RPG/SkillFirebolt.png";
            case "quick_slash" -> "Icons/RPG/SkillQuickslash.png";
            default -> RpgSkillTreeProjectionService.PLACEHOLDER_ICON;
        };
    }
}
