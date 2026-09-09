package com.inigmasgames.hytalerpg.execution.hytale;

import java.util.*;

/** Failure-only, bounded metadata. Preserve the original exception type and never log arbitrary exception messages. */
public final class ProjectileSpawnDiagnostics {
    public enum Stage { CONFIG_LOOKUP, NATIVE_ALLOCATION, PHYSICS_COMPONENTS, VELOCITY_ASSIGNMENT, CONTINUATION_SETUP, IMPACT_CALLBACK, SPAWN_EVENT, PRESENTATION }
    private static final class Boundary extends RuntimeException {
        final Stage stage;Boundary(Stage stage){super(stage.name(),null,false,false);this.stage=stage;}
    }
    public static void mark(RuntimeException error,Stage stage){
        for(var e:error.getSuppressed())if(e instanceof Boundary)return;
        error.addSuppressed(new Boundary(stage));
    }
    public static Map<String,Object> describe(RuntimeException error,Map<String,?> original){
        var result=new LinkedHashMap<String,Object>(original);String stage="PRE_CARRIER_BATCH";
        for(var e:error.getSuppressed())if(e instanceof Boundary b){stage=b.stage.name();break;}
        result.put("spawnStage",stage);
        result.put("spawnFailureCode",error instanceof IllegalArgumentException&&"Specified map is empty".equals(error.getMessage())?"NATIVE_EMPTY_ENUM_MAP":"SPAWN_"+stage+"_FAILED");
        result.put("spawnError",error.getClass().getSimpleName().substring(0,Math.min(80,error.getClass().getSimpleName().length())));
        for(var frame:error.getStackTrace())if(frame.getClassName().startsWith("com.hypixel.hytale.")||frame.getClassName().equals("java.util.EnumMap")){
            String origin=frame.getClassName()+"."+frame.getMethodName()+":"+frame.getLineNumber();
            result.put("spawnFailureOrigin",origin.substring(0,Math.min(192,origin.length())));break;
        }
        return Map.copyOf(result);
    }
    private ProjectileSpawnDiagnostics(){}
}
