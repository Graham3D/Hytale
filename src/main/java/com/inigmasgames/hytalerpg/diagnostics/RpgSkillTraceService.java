package com.inigmasgames.hytalerpg.diagnostics;

import com.hypixel.hytale.logger.HytaleLogger;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Full accepted JSONL records; rate-limited console summary and explicit loss metrics. */
public final class RpgSkillTraceService implements RpgSkillTracer {
    private static final HytaleLogger LOGGER=HytaleLogger.forEnclosingClass();
    private final Path path;private final boolean enabled;private final BoundedTraceWriter writer;
    private final AtomicBoolean failureLogged=new AtomicBoolean();private final AtomicLong lastConsole=new AtomicLong(Long.MIN_VALUE);
    public RpgSkillTraceService(Path path,SkillTraceConfiguration configuration){
        this.path=path;enabled=configuration.enabled();
        writer=new BoundedTraceWriter(path,configuration.maxFileMb()*1024L*1024L,configuration.retainedFiles(),this::failure);
    }
    @Override public void trace(RpgTraceRecord record){
        if(!enabled)return;writer.submit(record);
        long now=System.nanoTime(),before=lastConsole.get();
        if((before==Long.MIN_VALUE||now-before>=1_000_000_000L)&&lastConsole.compareAndSet(before,now))
            LOGGER.atInfo().log("RPG_SKILL_TRACE revision=%s event=%s player=%s correlation=%s result=%s code=%s jsonl=%s metrics=%s consoleRateLimited=true",
                    record.rpgRevision(),record.eventType(),record.playerUuid(),record.correlationId(),
                    record.details().getOrDefault("validationResult","n/a"),record.details().getOrDefault("failureCode","n/a"),path,writer.metrics());
    }
    private void failure(Throwable error){if(failureLogged.compareAndSet(false,true))LOGGER.atWarning().withCause(error).log("RPG skill trace incomplete; inspect TRACE_GAP/metrics; gameplay active path=%s",path);}
    public BoundedTraceWriter.Metrics metrics(){return writer.metrics();}
    @Override public void close(){writer.close();}
    public Path path(){return path;}
}
