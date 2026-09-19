package com.inigmasgames.persistentnpcs.hytale;

/** Detects the Update 6 NPC ABI before any world-tick code can link against it. */
public record RuntimeApiCompatibility(boolean update6NpcApi, String detail) {
    private static final String REQUIRED_MESSAGE =
            "Immersive AI requires Hytale Update 6 pre-release. The running game is "
            + "using the older release API. Select Pre-release in the Hytale launcher, "
            + "restart Hytale, and try again.";

    public static RuntimeApiCompatibility detect() {
        try {
            ClassLoader loader = RuntimeApiCompatibility.class.getClassLoader();
            Class<?> ref = Class.forName("com.hypixel.hytale.component.Ref", false, loader);
            Class<?> accessor = Class.forName(
                    "com.hypixel.hytale.component.ComponentAccessor", false, loader);
            Class<?> positionCache = Class.forName(
                    "com.hypixel.hytale.server.npc.role.support.PositionCache", false, loader);
            positionCache.getMethod("get", ref, accessor);
            Class<?> role = Class.forName(
                    "com.hypixel.hytale.server.npc.role.Role", false, loader);
            role.getMethod("setMarkedTarget", ref, accessor, String.class, ref);
            return new RuntimeApiCompatibility(true, "Update 6 NPC API detected");
        } catch (ReflectiveOperationException | LinkageError failure) {
            return new RuntimeApiCompatibility(false,
                    failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    public static RuntimeApiCompatibility supportedForTests() {
        return new RuntimeApiCompatibility(true, "test runtime");
    }

    public String blockerMessage() {
        return update6NpcApi ? "" : REQUIRED_MESSAGE + " Diagnostic: " + detail;
    }
}
