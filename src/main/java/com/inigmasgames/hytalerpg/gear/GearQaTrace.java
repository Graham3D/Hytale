package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.diagnostics.BoundedTraceWriter;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Opt-in, bounded evidence for the existing gear owners. Observation never changes rolls or custody. */
public final class GearQaTrace implements AutoCloseable {
    private static final DateTimeFormatter STAMP=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC);
    private static volatile GearQaTrace installed;
    private final Path directory;
    private final String channel;
    private final java.util.function.Consumer<Throwable> writerFailure;
    private java.util.function.Function<UUID,UUID> actorOwner=actor->actor;
    private final ConcurrentHashMap<UUID,GearEffectSnapshot> equipmentSnapshots=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID,Session> sessions=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID,Map<String,Double>> statSnapshots=new ConcurrentHashMap<>();
    private final ScheduledThreadPoolExecutor expiry=new ScheduledThreadPoolExecutor(1,task->{
        var thread=new Thread(task,"gear-qa-trace-expiry");thread.setDaemon(true);return thread;
    });
    public GearQaTrace(Path directory){this(directory,"Gear");}
    public GearQaTrace(Path directory,String channel){this(directory,channel,error->
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log("RPG_GEAR_TRACE_WRITE_FAILED %s",error));}
    GearQaTrace(Path directory,String channel,java.util.function.Consumer<Throwable> writerFailure){
        this.directory=directory;this.channel=channel;this.writerFailure=java.util.Objects.requireNonNull(writerFailure);
        expiry.setRemoveOnCancelPolicy(true);
    }
    public void actorOwner(java.util.function.Function<UUID,UUID> resolver){actorOwner=java.util.Objects.requireNonNull(resolver);}
    public static void install(GearQaTrace trace){installed=trace;}
    public static boolean active(UUID player){var trace=installed;return player!=null&&trace!=null&&trace.sessions.containsKey(player);}
    public static void record(UUID player,String event,Map<String,?> details){
        var trace=installed;if(trace==null||player==null)return;
        var session=trace.sessions.get(player);if(session!=null)session.write(event,details);
    }
    public String snapshot(UUID player,Map<String,?> details){
        var session=sessions.get(player);if(session==null)return channel+" trace is off; use on first.";
        session.write("SENTINEL_SNAPSHOT",details);return channel+" snapshot saved; "+session.path;
    }
    /** Copies existing owner receipts before skill-log filtering; never synthesizes a gameplay PASS. */
    public static void runtime(com.inigmasgames.hytalerpg.diagnostics.RpgTraceRecord receipt){
        var trace=installed;if(trace==null||trace.sessions.isEmpty()||receipt.playerUuid()==null)return;
        try{
            String event=receipt.eventType().name();
            if(!(event.startsWith("DAMAGE_")||event.startsWith("RESOURCE_")||event.startsWith("STATUS_")
                    ||event.startsWith("CRIT_")||event.startsWith("HEAL_")||event.startsWith("BARRIER_")
                    ||event.startsWith("AURA_")||event.startsWith("FINITE_SUPPORT_")||event.startsWith("SUMMON_")
                    ||event.equals("HIT_PROC_RESOLVED")||event.equals("TRIGGER_FIRED")||event.equals("MODIFIERS_APPLIED")
                    ||event.equals("BASE_POWER_RESOLVED")||event.equals("COOLDOWN_STARTED")||event.equals("SKILL_COMMITTED")))return;
            UUID owner=trace.actorOwner.apply(receipt.playerUuid());if(owner==null||!trace.sessions.containsKey(owner))return;
            var details=new LinkedHashMap<String,Object>(receipt.details());
            details.put("runtimeActor",receipt.playerUuid());details.put("correlationId",receipt.correlationId());
            var equipment=trace.equipmentSnapshots.get(owner);
            if(equipment!=null)details.put("equipmentRevision",equipment.revision());
            record(owner,"RUNTIME_"+event,details);
        }catch(RuntimeException ignored){/* Trace admission cannot interrupt a gameplay receipt. */}
    }
    public static void equipment(UUID owner,GearEffectSnapshot snapshot,java.util.List<GearInstance> candidates,
                                 java.util.Set<UUID> accepted){
        var trace=installed;if(!active(owner))return;
        var before=trace.equipmentSnapshots.put(owner,snapshot);
        if(snapshot.equals(before))return;
        var data=new LinkedHashMap<String,Object>();data.put("revision",snapshot.revision());
        data.put("before",before==null?"BASELINE":before.items().stream().map(Qa159Pack::source).toList());
        data.put("after",snapshot.items().stream().map(Qa159Pack::source).toList());
        data.put("rejectedItems",candidates.stream().filter(g->!accepted.contains(g.identity())).map(Qa159Pack::source).toList());
        var values=new java.util.TreeMap<String,Object>();
        for(var op:GearEffectSnapshot.Operator.values()){
            double old=before==null?0:before.total(op),now=snapshot.total(op);
            if(old!=0||now!=0)values.put(op.name(),Map.of("before",old,"after",now,"units","frozen affix units; resolved values in runtime/stat receipts"));
        }
        data.put("operatorValues",values);record(owner,"VALID_EQUIPMENT_SNAPSHOT",data);
    }
    public String on(UUID player){
        off(player);
        statSnapshots.remove(player);
        var path=directory.resolve(channel.toLowerCase(java.util.Locale.ROOT)+"-trace-"+STAMP.format(Instant.now())+"-"+UUID.randomUUID().toString().substring(0,8)+".jsonl");
        var session=new Session(player,path);sessions.put(player,session);
        session.write("SESSION_START",Map.of("limitEvents",3000,"limitMinutes",10,
                "buildRevision",com.inigmasgames.hytalerpg.phase00.BuildIdentity.REVISION,
                "gameVersion",com.inigmasgames.hytalerpg.phase00.BuildIdentity.HYTALE_VERSION,
                "affixContract","Master_Affix_v1.0"));
        expiry.schedule(()->{if(sessions.remove(player,session)){equipmentSnapshots.remove(player);statSnapshots.remove(player);session.close("TIME_LIMIT");}},10,TimeUnit.MINUTES);
        return channel+" trace ON; "+path;
    }
    public String off(UUID player){equipmentSnapshots.remove(player);statSnapshots.remove(player);var session=sessions.remove(player);if(session==null)return channel+" trace is off.";
        session.close("SESSION_END");return channel+" trace OFF; "+session.path;}
    public String mark(UUID player,String label){var session=sessions.get(player);if(session==null)return "Gear trace is off.";
        session.write("MARK",Map.of("label",label==null?"":label.substring(0,Math.min(64,label.length()))));return "Gear trace mark saved.";}
    public String status(UUID player){var session=sessions.get(player);return session==null?"Gear trace is off.":
        "Gear trace ON; events="+session.count.get()+"; "+session.path;}
    public void disconnect(UUID player){off(player);}
    public static void advanced(UUID player,Map<String,Double> authoritative,
                                Map<String,String> projected,Map<String,Double> gearContribution,
                                Map<String,String> expectedDisplay){
        var trace=installed;if(trace==null||!trace.sessions.containsKey(player))return;
        var before=trace.statSnapshots.put(player,Map.copyOf(authoritative));
        for(var entry:authoritative.entrySet()){
            var old=before==null?null:before.get(entry.getKey());
            if(old!=null&&Double.compare(old,entry.getValue())==0)continue;
            var details=new LinkedHashMap<String,Object>();details.put("statId",entry.getKey());
            details.put("valueBefore",old==null?"BASELINE":old);details.put("gearContribution",gearContribution.getOrDefault(entry.getKey(),0d));
            details.put("valueAfter",entry.getValue());
            String display=projected.getOrDefault(entry.getKey(),"MISSING");
            details.put("advancedStatsViewModel",display);
            details.put("result",expectedDisplay.getOrDefault(entry.getKey(),"MISSING").equals(display)?"PASS":"FAIL");
            record(player,"ADVANCED_STAT",details);
        }
    }
    @Override public void close(){if(installed==this)installed=null;for(var player:sessions.keySet())off(player);expiry.shutdownNow();}
    private final class Session {
        final UUID player;final Path path;final BoundedTraceWriter writer;final AtomicInteger count=new AtomicInteger();
        final long started=System.nanoTime();volatile boolean closed;
        Session(UUID player,Path path){this.player=player;this.path=path;
            writer=new BoundedTraceWriter(path,4L*1024*1024,1,writerFailure);}
        synchronized void write(String event,Map<String,?> details){
            if(closed)return;
            if(System.nanoTime()-started>TimeUnit.MINUTES.toNanos(10)||count.incrementAndGet()>3000){
                sessions.remove(player,this);equipmentSnapshots.remove(player);statSnapshots.remove(player);close("TRACE_LIMIT");return;
            }
            var row=new LinkedHashMap<String,Object>();row.put("timestamp",Instant.now().toString());
            row.put("sequence",count.get());row.put("elapsedMicros",(System.nanoTime()-started)/1000L);
            row.put("event",event);row.put("playerUuid",player.toString());
            details.forEach((key,value)->row.put(key,value));writer.submit(row);
        }
        synchronized void close(String reason){if(closed)return;closed=true;
            writer.submit(Map.of("timestamp",Instant.now().toString(),"event",reason,"recorded",count.get()));
            writer.close();}
    }
}
