package com.inigmasgames.hytalerpg.ui.hud;

/** Presentation only; one replaceable notice per HUD, with no scheduled tasks or gameplay writes. */
public final class SkillFailureNotice {
    public record View(String text, int phase) { }
    private String text = "";
    private long started;
    public synchronized void show(String code, long now) {
        String next = message(code);
        if (next.equals(text) && now - started < 1_360_000_000L) return;
        text = next;
        started = now;
    }
    public synchronized View view(long now) {
        long age = now - started;
        int phase = text.isEmpty() || age < 0 || age >= 1_360_000_000L ? -1
                : age < 90_000_000L || age >= 1_270_000_000L ? 0
                : age < 180_000_000L || age >= 1_180_000_000L ? 1 : 2;
        return new View(phase < 0 ? "" : text, phase);
    }
    public static String message(String code) {
        return switch (code) {
            case "NO_AIMED_GROUND_ITEM" -> "Aim at dropped Hywind gear within 6 blocks";
            case "INVALID_MAIN_HAND" -> "This skill requires a different main-hand weapon";
            case "INVALID_OFF_HAND" -> "This skill requires a different off-hand item";
            case "INSUFFICIENT_RESOURCE", "AURA_INITIAL_UPKEEP_UNAFFORDABLE", "RESOURCE_RESERVATION_FAILED" -> "Insufficient resources to cast";
            case "COOLDOWN_ACTIVE" -> "Skill is on cooldown";
            case "SILENCED" -> "Cannot cast while silenced";
            case "EMPTY_SLOT" -> "No skill equipped in this slot";
            case "PENDING_PRIMARY_FOR_SLOT", "INCOMPATIBLE_ACTIVE_STATE", "IRON_SENTINEL_CAST_IN_PROGRESS" -> "Another skill cast is still in progress";
            case "IRON_SENTINEL_WORLD_ITEM_GONE" -> "The dropped source item is no longer available";
            case "IRON_SENTINEL_TARGET_RANGE_OR_LOS" -> "Source item is out of range or obstructed";
            case "ACTOR_NOT_USABLE" -> "Cannot cast in your current state";
            default -> "Cannot cast: " + code;
        };
    }
}
