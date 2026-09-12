package com.inigmasgames.hytalerpg.execution.hytale;

import java.nio.file.Path;
import java.util.Locale;

/** Pure bounds/scope policy for the explicitly opt-in disposable-world diagnostic. */
public final class HealingProbePolicy {
    public static final double RUN_SECONDS=10, EFFECT_SECONDS=12, SAMPLE_SECONDS=1;
    public static final int MAX_WORLDS=4, MAX_TRANSITIONS=128;
    public enum Mode { WORLD, EMPTY, VISIBLE, RECIPIENT_ONCE, RECIPIENT_OVERWRITE, STAFF_ONCE, STAFF_OVERWRITE, BEAM }
    public static Mode mode(String value){return Mode.valueOf(value.toUpperCase(Locale.ROOT).replace('-','_'));}
    public static boolean allows(Path allowedRoot,Path worldPath,Path liveSave){
        if(allowedRoot==null||worldPath==null||liveSave==null)return false;
        var root=allowedRoot.toAbsolutePath().normalize();
        var world=worldPath.toAbsolutePath().normalize();var live=liveSave.toAbsolutePath().normalize();
        // Require a deliberately named disposable directory, never a broad parent of the real save.
        return root.getFileName()!=null&&root.getFileName().toString().startsWith("healing-probe-")
                &&!root.startsWith(live)&&!live.startsWith(root)&&world.startsWith(root);
    }
    private HealingProbePolicy(){}
}
