package com.inigmasgames.hytalerpg.execution.hytale;

import java.nio.file.Path;
import java.util.Locale;

/** Pure bounds/scope policy for the explicitly opt-in disposable-world diagnostic. */
public final class HealingProbePolicy {
    public static final double RUN_SECONDS=10, EFFECT_SECONDS=12, SAMPLE_SECONDS=1;
    public static final int MAX_WORLDS=4, MAX_TRANSITIONS=128;
    public static boolean liveTestBuild(){
        try(var stream=HealingProbePolicy.class.getResourceAsStream("/healing-probe-live-test.txt")){
            return stream!=null&&new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim().equals("R032-AJ-LIVE");
        }catch(java.io.IOException error){throw new IllegalStateException("LIVE_PROBE_MARKER_UNREADABLE",error);}
    }
    public static void validateLiveTarget(Mode mode,String target){
        if(needsRecipient(mode)){
            if(target.equals("self"))return;
            if(target.equals("native"))throw new IllegalArgumentException("LIVE_PROBE_USE_SELF_OR_EXISTING_UUID_NO_NPC_SPAWN");
        }
        validateTarget(mode,target);
    }
    public enum Mode { WORLD, EMPTY, VISIBLE, RECIPIENT_ONCE, RECIPIENT_OVERWRITE, STAFF_ONCE, STAFF_OVERWRITE, BEAM }
    public static boolean needsRecipient(Mode mode){return mode==Mode.RECIPIENT_ONCE||mode==Mode.RECIPIENT_OVERWRITE;}
    public static void validateTarget(Mode mode,String target){
        if(target.equals("native"))return; // Legacy syntax remains valid; no NPC for standalone modes.
        if(target.equals("none")&&!needsRecipient(mode))return;
        if(!needsRecipient(mode))throw new IllegalArgumentException("STANDALONE_USE_NONE");
        java.util.UUID.fromString(target);
    }
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
