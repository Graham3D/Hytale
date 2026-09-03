package com.inigmasgames.persistentnpcs.action;

public record NpcActionResult(boolean success, String code, String eventDescription) {
    public static NpcActionResult success(String eventDescription) {
        return new NpcActionResult(true, "OK", eventDescription);
    }

    public static NpcActionResult failure(String code, String eventDescription) {
        return new NpcActionResult(false, code, eventDescription);
    }
}
