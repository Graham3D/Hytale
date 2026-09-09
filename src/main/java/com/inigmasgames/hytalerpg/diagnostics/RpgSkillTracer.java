package com.inigmasgames.hytalerpg.diagnostics;

public interface RpgSkillTracer extends AutoCloseable {
    void trace(RpgTraceRecord record);
    default boolean wantsCompileStages(){return true;}
    @Override default void close() {}
}
