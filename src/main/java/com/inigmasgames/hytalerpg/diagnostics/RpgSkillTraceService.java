package com.inigmasgames.hytalerpg.diagnostics;

import com.hypixel.hytale.logger.HytaleLogger;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Full accepted JSONL records; rate-limited console summary and explicit loss metrics. */
public final class RpgSkillTraceService implements RpgSkillTracer {
    private static final HytaleLogger LOGGER=HytaleLogger.forEnclosingClass();
    private final Path path;private final boolean enabled;private final BoundedTraceWriter writer;
    private final SkillTraceRouter router;
    private static final int MAX_SESSIONS=16, MAX_SESSION_EVENTS=2048;
    private final ConcurrentHashMap<UUID,Capture> captures=new ConcurrentHashMap<>();
    private final Map<UUID,String> lastCaptures=java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>() {
                @Override protected boolean removeEldestEntry(Map.Entry<UUID,String> eldest){return size()>256;}
            });
    private final ScheduledExecutorService expiry=Executors.newSingleThreadScheduledExecutor(task->{
        var thread=new Thread(task,"skill-trace-expiry");thread.setDaemon(true);return thread;});
    private final AtomicBoolean failureLogged=new AtomicBoolean();private final AtomicLong lastConsole=new AtomicLong(Long.MIN_VALUE);
    public RpgSkillTraceService(Path path,SkillTraceConfiguration configuration){
        this.path=path;enabled=configuration.enabled();
        writer=new BoundedTraceWriter(path,configuration.maxFileMb()*1024L*1024L,configuration.retainedFiles(),this::failure,
                TraceArchiveManager.Compression.valueOf(configuration.archiveCompression()),configuration.archiveVerify());
        router=new SkillTraceRouter(SkillTraceLevel.parse(configuration.level()),this::write);
        if(enabled)router.setLevel(router.level());
    }
    @Override public void trace(RpgTraceRecord record){
        com.inigmasgames.hytalerpg.diagnostics.hro.HroRuntime.skill(record);
        com.inigmasgames.hytalerpg.gear.GearQaTrace.runtime(record);
        if(!enabled)return;
        router.trace(record); // Existing bounded global telemetry remains independent of personal QA captures.
        if(record.playerUuid()!=null){var capture=captures.get(record.playerUuid());if(capture!=null)capture.accept(record);}
        else if(record.eventType()==RpgTraceEventType.NATIVE_RPG_TICK_SAMPLE){
            Object world=record.details().get("world");
            for(Capture capture:captures.values())if(capture.level==SkillTraceLevel.PERFORMANCE
                    &&capture.world.toString().equals(String.valueOf(world)))capture.accept(record);
        }
    }
    private void write(RpgTraceRecord record){
        writer.submit(record);
        long now=System.nanoTime(),before=lastConsole.get();
        if((before==Long.MIN_VALUE||now-before>=1_000_000_000L)&&lastConsole.compareAndSet(before,now))
            LOGGER.atInfo().log("RPG_SKILL_TRACE revision=%s event=%s player=%s correlation=%s result=%s code=%s jsonl=%s metrics=%s consoleRateLimited=true",
                    record.rpgRevision(),record.eventType(),record.playerUuid(),record.correlationId(),
                    record.details().getOrDefault("validationResult","n/a"),record.details().getOrDefault("failureCode","n/a"),path,writer.metrics());
    }
    private void failure(Throwable error){if(failureLogged.compareAndSet(false,true))LOGGER.atWarning().withCause(error).log("RPG skill trace incomplete; inspect TRACE_GAP/metrics; gameplay active path=%s",path);}
    public BoundedTraceWriter.Metrics metrics(){return writer.metrics();}
    public TraceArchiveManager.Metrics archiveMetrics(){return writer.archiveMetrics();}
    public SkillTraceLevel level(){return router.level();}
    public boolean available(){return enabled;}
    public synchronized String start(UUID player,UUID world,SkillTraceLevel level){
        if(!enabled)return "Skill trace unavailable: disabled by configuration.";
        var prior=captures.get(player);if(prior!=null)return "Skill trace already active; "+status(player);
        if(captures.size()>=MAX_SESSIONS)return "Skill trace capacity reached (16 personal captures).";
        var capture=new Capture(player,world,level);
        prior=captures.putIfAbsent(player,capture);
        if(prior!=null){capture.close();return "Skill trace already active; "+status(player);}
        capture.start();
        expiry.schedule(()->stopIfCurrent(player,capture,"TIME_LIMIT"),60,TimeUnit.SECONDS);
        return "Skill trace started; "+status(player);
    }
    public String stop(UUID player){
        var capture=captures.get(player);
        if(capture==null)return status(player);
        stopIfCurrent(player,capture,"OPERATOR_STOP");return status(player);
    }
    public String status(UUID player){
        var capture=captures.get(player);
        return capture==null?"personal capture off; "+lastCaptures.getOrDefault(player,"no prior capture")
                +"; backgroundGlobal="+enabled+"; backgroundLevel="+router.level()
                :"personal capture ON; session="+capture.id+"; level="+capture.level+"; world="+capture.world
                +"; events="+capture.count.get()+"/"+MAX_SESSION_EVENTS+"; file="+capture.file
                +"; writer="+capture.writer.metrics()+"; backgroundGlobal="+enabled;
    }
    public void disconnect(UUID player){var capture=captures.get(player);if(capture!=null)stopIfCurrent(player,capture,"DISCONNECT");}
    private void stopIfCurrent(UUID player,Capture capture,String reason){
        if(!captures.remove(player,capture))return;
        capture.close();lastCaptures.put(player,"completed="+reason+"; session="+capture.id
                +"; events="+capture.count.get()+"; file="+capture.file+"; writer="+capture.writer.metrics());
    }
    private final class Capture {
        final UUID id=UUID.randomUUID(),player,world;
        final SkillTraceLevel level;
        final Path file;
        final BoundedTraceWriter writer;
        final SkillTraceRouter local;
        final AtomicInteger count=new AtomicInteger();
        final AtomicBoolean closed=new AtomicBoolean();
        Capture(UUID player,UUID world,SkillTraceLevel level){
            this.player=player;this.world=world;this.level=level;
            file=path.resolveSibling("skill-trace-"+id+".jsonl");
            writer=new BoundedTraceWriter(file,8L*1024*1024,1,RpgSkillTraceService.this::failure);
            local=new SkillTraceRouter(level,this::write);
        }
        void start(){write(RpgTraceRecord.create(player,RpgTraceEventType.TRACE_LEVEL_CHANGED,id.toString(),
                Map.of("traceType","SKILL","sessionId",id.toString(),"level",level.name(),
                        "scope","PERSONAL_PLAYER","worldAtStart",world.toString(),"limitEvents",MAX_SESSION_EVENTS)));}
        void accept(RpgTraceRecord record){if(!closed.get())local.trace(record);}
        void write(RpgTraceRecord record){
            int current=count.incrementAndGet();
            if(current>MAX_SESSION_EVENTS){
                if(current==MAX_SESSION_EVENTS+1){writer.submit(Map.of("eventType","TRACE_GAP","traceType","SKILL",
                        "sessionId",id.toString(),"reason","EVENT_LIMIT"));
                    expiry.execute(()->stopIfCurrent(player,this,"EVENT_LIMIT"));}
                return;
            }
            writer.submit(Map.of("traceType","SKILL","sessionId",id.toString(),
                    "event",record.eventType().name(),"actor",player.toString(),
                    "correlationId",String.valueOf(record.correlationId()),"record",record));
        }
        void close(){if(closed.compareAndSet(false,true)){local.flush();writer.close();}}
    }
    public void setLevel(SkillTraceLevel level){if(!enabled)throw new IllegalStateException("SKILL_TRACE_DISABLED");router.setLevel(level);}
    @Override public boolean wantsCompileStages(){
        if(enabled&&router.level()==SkillTraceLevel.DETAILED)return true;
        for(Capture capture:captures.values())if(capture.level==SkillTraceLevel.DETAILED)return true;
        return false;
    }
    @Override public void close(){
        for(var entry:captures.entrySet())stopIfCurrent(entry.getKey(),entry.getValue(),"SHUTDOWN");
        expiry.shutdownNow();router.flush();writer.close();
    }
    public Path path(){return path;}
}
