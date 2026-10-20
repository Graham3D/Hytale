package com.inigmasgames.hytalerpg.diagnostics;

public interface RpgSkillTracer extends AutoCloseable {
    void trace(RpgTraceRecord record);
    @Override default void close() {}
}
