package com.inigmasgames.hytalerpg.diagnostics;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Bounded, read-only observation of native jobs and the existing Elite birth handoff. */
public final class MonsterSpawnTrace implements AutoCloseable {
    public static final int DURATION_SECONDS=120, MAX_DETAILS=10_000;
    private static volatile MonsterSpawnTrace installed;
    private final Path directory;
    private final ScheduledExecutorService expiry=Executors.newSingleThreadScheduledExecutor(
            task->Thread.ofPlatform().daemon().name("RPG-monster-spawn-trace").unstarted(task));
    private final Gson gson=new Gson();
    private Session session;
    private String lastFile="none",lastError="none";
    private long lastEvents,lastDetails;
    private Map<String,Long> lastCounts=Map.of();
    private boolean closed;

    private static final class Session {
        final long started=System.currentTimeMillis(), deadline=started+DURATION_SECONDS*1000L;
        final Map<String,Long> counts=new TreeMap<>();
        final List<Map<String,Object>> details=new ArrayList<>();
        final Set<String> jobs=new HashSet<>();
        final Map<UUID,Long> lastPopulationAt=new HashMap<>();
        long total;
    }
    public record Status(boolean active,long secondsRemaining,long events,long detailedEvents,
            String lastFile,String lastError,Map<String,Long> counts) {}
    public MonsterSpawnTrace(Path directory){this.directory=Objects.requireNonNull(directory);}
    public synchronized Status start(){
        if(closed)throw new IllegalStateException("Monster spawn trace is stopped");
        if(session!=null)throw new IllegalStateException("Monster spawn trace is already running");
        session=new Session();installed=this;
        var current=session;
        expiry.schedule(()->{synchronized(this){if(session==current)finish();}},DURATION_SECONDS,TimeUnit.SECONDS);
        return status();
    }
    public synchronized Status stop(){if(session!=null)finish();return status();}
    public synchronized Status status(){
        var current=session;
        if(current==null)return new Status(false,0,lastEvents,lastDetails,lastFile,lastError,lastCounts);
        return new Status(true,Math.max(0,(current.deadline-System.currentTimeMillis()+999)/1000),
                current.total,current.details.size(),lastFile,lastError,Map.copyOf(current.counts));
    }
    public static boolean enabled(){return installed!=null;}
    public static void event(String stage,UUID world,int environment,String role,String detail){
        count(stage,world,environment,role,1,detail);
    }
    public static void count(String stage,UUID world,int environment,String role,long amount,String detail){
        var target=installed;if(target!=null&&amount>0)target.record(stage,world,environment,role,amount,detail);
    }
    public static void job(UUID world,int environment,String role,int nativeJob,String detail){
        var target=installed;if(target!=null)target.recordJob(world,environment,role,nativeJob,detail);
    }
    public static void population(UUID world,String detail){
        var target=installed;if(target!=null)target.recordPopulation(world,detail);
    }
    private synchronized void recordPopulation(UUID world,String detail){
        var current=session;if(current==null)return;
        long now=System.currentTimeMillis();
        if(now-current.lastPopulationAt.getOrDefault(world,0L)<1000)return;
        current.lastPopulationAt.put(world,now);
        record("NATIVE_POPULATION_SAMPLE",world,-1,"all",1,detail);
    }
    private synchronized void recordJob(UUID world,int environment,String role,int nativeJob,String detail){
        var current=session;if(current==null)return;
        String key=world+"/"+nativeJob;
        if(current.jobs.size()>=50_000||!current.jobs.add(key))return;
        record("NATIVE_JOB_CREATED",world,environment,role,1,"job="+nativeJob+" "+detail);
    }
    private synchronized void record(String stage,UUID world,int environment,String role,long amount,String detail){
        var current=session;if(current==null)return;
        // The scheduled diagnostic thread owns serialization. A native world tick must never
        // spend time writing the capped detail buffer when the window expires.
        if(System.currentTimeMillis()>=current.deadline)return;
        // Diagnostics must never throw into Hytale's spawn or world thread.
        try{
            String category=clean(stage,64),nativeRole=clean(role,96),message=clean(detail,512);
            String key=category+"|"+world+"|"+environment+"|"+nativeRole;
            if(current.counts.size()>=50_000&&!current.counts.containsKey(key))key="OTHER|COUNTER_LIMIT";
            current.counts.merge(key,amount,Long::sum);current.total+=amount;
            if(current.details.size()<MAX_DETAILS)current.details.add(Map.of(
                    "time",Instant.now().toString(),"stage",category,"world",world==null?"unknown":world.toString(),
                    "environment",environment,"role",nativeRole,"count",amount,"detail",message));
        }catch(RuntimeException ignored){/* Trace cannot affect native admission. */}
    }
    private static String clean(String text,int max){
        if(text==null)return "unknown";
        String safe=text.replaceAll("[\\p{Cntrl}]"," ");
        return safe.length()<=max?safe:safe.substring(0,max);
    }
    private void finish(){
        var current=session;if(current==null)return;
        session=null;if(installed==this)installed=null;
        lastEvents=current.total;lastDetails=current.details.size();lastCounts=Map.copyOf(current.counts);
        Path target=directory.resolve("monster-spawn-"+current.started+".jsonl");
        try{
            Files.createDirectories(directory);
            Path temp=target.resolveSibling(target.getFileName()+".tmp");
            try(var writer=Files.newBufferedWriter(temp,StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){
                writer.write(gson.toJson(Map.of("type","summary","started",Instant.ofEpochMilli(current.started).toString(),
                        "ended",Instant.now().toString(),"events",current.total,"detailedEvents",current.details.size(),
                        "detailLimit",MAX_DETAILS,"counts",current.counts)));writer.newLine();
                for(var event:current.details){writer.write(gson.toJson(event));writer.newLine();}
            }
            Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            lastFile=target.toString();lastError="none";
        }catch(IOException error){lastError=error.toString();}
    }
    @Override public synchronized void close(){if(!closed){closed=true;finish();expiry.shutdownNow();}}
}
