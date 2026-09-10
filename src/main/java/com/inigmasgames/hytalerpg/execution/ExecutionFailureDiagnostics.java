package com.inigmasgames.hytalerpg.execution;

import java.util.*;

/** Bounded code locations, never exception messages, locals, filenames, or user data. */
public final class ExecutionFailureDiagnostics {
    private ExecutionFailureDiagnostics() { }
    public static Map<String,Object> describe(String stage,RuntimeException failure) {
        String boundary=Set.of("EXECUTOR_DISPATCH","STRIKE_REPEAT").contains(stage)?stage:"UNKNOWN";
        var frames=new ArrayList<String>();
        for(var frame:failure.getStackTrace()){
            if(frames.size()==8)break;
            frames.add(safe(frame.getClassName())+"#"+safe(frame.getMethodName())+":"+frame.getLineNumber());
        }
        return Map.of("stage",boundary,"errorClass",safe(failure.getClass().getName()),
                "codeFrames",List.copyOf(frames),"paidRootRetained",true);
    }
    private static String safe(String value){
        String clean=value.replaceAll("[^A-Za-z0-9_.$]","_");return clean.substring(0,Math.min(clean.length(),160));
    }
}
