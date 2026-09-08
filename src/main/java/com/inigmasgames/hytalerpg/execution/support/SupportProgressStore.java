package com.inigmasgames.hytalerpg.execution.support;
import com.inigmasgames.hytalerpg.progress.SupportProgress;
import java.util.UUID;

/** The player repository remains the single durable owner. Save must reject stale revisions atomically. */
public interface SupportProgressStore {
    SupportProgress read(UUID actor);
    SupportProgress save(UUID actor,SupportProgress next);
}
