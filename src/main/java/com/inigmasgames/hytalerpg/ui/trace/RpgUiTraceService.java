package com.inigmasgames.hytalerpg.ui.trace;

import com.inigmasgames.hytalerpg.phase00.BuildIdentity;
import com.inigmasgames.hytalerpg.diagnostics.BoundedTraceWriter;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Bounded asynchronous UI diagnostics; JSONL retains accepted events, console is rate-limited. */
public final class RpgUiTraceService implements AutoCloseable {
    private static final Logger LOGGER=Logger.getLogger(RpgUiTraceService.class.getName());
    private final Path path;private final BoundedTraceWriter writer;
    private final AtomicBoolean failureLogged=new AtomicBoolean();
    private final AtomicLong lastConsole=new AtomicLong(Long.MIN_VALUE);
    public RpgUiTraceService(Path path){this.path=path;writer=new BoundedTraceWriter(path,4L*1024*1024,4,this::logFailureOnce);}
    public void trace(UUID player,String event,String correlationId,Map<String,?> details){
        var enriched=new LinkedHashMap<String,Object>();enriched.put("page",inferredPage(event));
        enriched.put("component",event.toLowerCase(java.util.Locale.ROOT));enriched.putAll(details);
        writer.submit(new Record(Instant.now().toString(),BuildIdentity.REVISION,BuildIdentity.VERSION,
                BuildIdentity.HYTALE_VERSION,player,event,correlationId,enriched));
        long now=System.nanoTime(),prior=lastConsole.get();
        if(!"HUD_REFRESHED".equals(event)&&(prior==Long.MIN_VALUE||now-prior>=50_000_000L)&&lastConsole.compareAndSet(prior,now))
            LOGGER.log(Level.INFO,"RPG_UI_TRACE revision="+BuildIdentity.REVISION+" event="+brief(event)
                    +" player="+player+" correlation="+brief(correlationId)+" result="+brief(enriched.get("result"))
                    +" jsonl="+path+" consoleRateLimited=true");
    }
    private static String brief(Object value){var text=String.valueOf(value);return text.substring(0,Math.min(256,text.length()));}
    private static String inferredPage(String event){
        if(event.startsWith("SKILLTREE"))return "skilltree";
        if(event.startsWith("CHARACTER")||event.startsWith("ATTRIBUTE"))return "character";
        if(event.startsWith("HUD")||event.startsWith("RESOURCE_HUD")||event.startsWith("ABILITY_")
                ||event.startsWith("SKILLBAR")||event.startsWith("XP_")||event.startsWith("LEVEL_UP"))return "hud";
        return "command";
    }
    private void logFailureOnce(Throwable error){
        if(failureLogged.compareAndSet(false,true))
            LOGGER.log(Level.WARNING,"RPG UI trace incomplete; inspect TRACE_GAP/metrics; gameplay active path="+path,error);
    }
    public BoundedTraceWriter.Metrics metrics(){return writer.metrics();}
    public Path path(){return path;}
    @Override public void close(){writer.close();}
    private record Record(String timestamp,String rpgRevision,String buildVersion,String hytaleBuild,
                          UUID playerUuid,String eventType,String correlationId,Map<String,Object> details){
        private Record{details=Map.copyOf(details);}
    }
}
