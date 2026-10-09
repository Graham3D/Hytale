package com.inigmasgames.hytalerpg.diagnostics;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/** Bounded, read-only observation of native jobs and the existing Elite birth handoff. */
public final class MonsterSpawnTrace implements AutoCloseable {
    public static final int DURATION_SECONDS=120, MAX_DETAILS=10_000, MAX_PRIORITY_DETAILS=2_000;
    private static volatile MonsterSpawnTrace installed;
    private final Path directory;
    private final Function<UUID,Integer> activeLeases;
    private final ScheduledExecutorService expiry=Executors.newSingleThreadScheduledExecutor(
            task->Thread.ofPlatform().daemon().name("RPG-monster-spawn-trace").unstarted(task));
    private final Gson gson=new Gson();
    private java.util.function.Supplier<Map<String,Object>> configuration=Map::of;
    public void configureManifest(java.util.function.Supplier<Map<String,Object>> source){configuration=source;}
    private Session session;
    private String lastFile="none",lastError="none";
    private long lastEvents,lastDetails;
    private Map<String,Long> lastCounts=Map.of();
    private boolean closed;

    private static Map<String,Object> buildManifest(){
        var result=new LinkedHashMap<String,Object>();
        try(var input=MonsterSpawnTrace.class.getResourceAsStream("/rpg-build.properties")){
            var properties=new java.util.Properties();if(input!=null)properties.load(input);
            properties.forEach((k,v)->result.put(k.toString(),v));
        }catch(java.io.IOException e){result.put("buildManifest","UNAVAILABLE");}
        result.put("biomeManifest",com.inigmasgames.hytalerpg.difficulty.CampaignBiomes.current().revision());
        result.put("nativeProjection","population-projection-0.7.0-pre.5.1-r244");
        return Map.copyOf(result);
    }
    private static final class Session {
        final Map<String,Object> manifest=new LinkedHashMap<>(buildManifest());
        final long started=System.currentTimeMillis(), deadline=started+DURATION_SECONDS*1000L;
        final Map<String,Long> counts=new TreeMap<>();
        final List<Map<String,Object>> details=new ArrayList<>();
        final List<Map<String,Object>> priorityDetails=new ArrayList<>();
        final Map<String,Long> priorityCounts=new TreeMap<>();
        final Set<String> jobs=new HashSet<>();
        final Map<UUID,Long> lastPopulationAt=new HashMap<>();
        final List<Bucket> buckets=new ArrayList<>();
        final Map<UUID,Long> completedJobs=new HashMap<>();
        final Map<UUID,Long> completedBudget=new HashMap<>();
        long total;
        Session(){for(int i=0;i<DURATION_SECONDS/10;i++)buckets.add(new Bucket(i));}
    }
    private static final class Bucket {
        final int index;
        final Map<String,Long> counts=new TreeMap<>(), additions=new TreeMap<>(), removals=new TreeMap<>();
        final Map<String,Object> population=new TreeMap<>();
        Bucket(int index){this.index=index;}
        Map<String,Object> output(){
            var result=new LinkedHashMap<String,Object>();
            result.put("type","bucket");result.put("fromSecond",index*10);result.put("toSecond",(index+1)*10);
            result.put("counts",counts);result.put("additionsByCategory",additions);
            result.put("removalsByReason",removals);result.put("populationByWorld",population);
            return result;
        }
    }
    public record Status(boolean active,long secondsRemaining,long events,long detailedEvents,
            String lastFile,String lastError,Map<String,Long> counts) {}
    public MonsterSpawnTrace(Path directory){this(directory,world->null);}
    public MonsterSpawnTrace(Path directory,Function<UUID,Integer> activeLeases){
        this.directory=Objects.requireNonNull(directory);this.activeLeases=Objects.requireNonNull(activeLeases);
    }
    public synchronized Status start(){
        if(closed)throw new IllegalStateException("Monster spawn trace is stopped");
        if(session!=null)throw new IllegalStateException("Monster spawn trace is already running");
        session=new Session();session.manifest.putAll(configuration.get());installed=this;
        var current=session;
        expiry.schedule(()->{synchronized(this){if(session==current)finish();}},DURATION_SECONDS,TimeUnit.SECONDS);
        return status();
    }
    public synchronized Status stop(){if(session!=null)finish();return status();}
    public synchronized Status status(){
        var current=session;
        if(current==null)return new Status(false,0,lastEvents,lastDetails,lastFile,lastError,lastCounts);
        return new Status(true,Math.max(0,(current.deadline-System.currentTimeMillis()+999)/1000),
                current.total,current.details.size()+current.priorityDetails.size(),lastFile,lastError,Map.copyOf(current.counts));
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
        try{
            var bucket=bucket(current,now);
            var sample=new LinkedHashMap<String,Object>();
            for(var key:List.of("actual","expected","activeJobs","nativeCap")){
                var value=field(detail,key+"=");if(value!=null)sample.put(key,value);
            }
            for(var key:List.of("completedJobs","completedJobBudget")){
                var value=field(detail,key+"=");
                if(value==null)continue;
                long currentValue=Long.parseLong(value);
                var previous=(key.equals("completedJobs")?current.completedJobs:current.completedBudget).put(world,currentValue);
                if(previous!=null&&currentValue>=previous)bucket.counts.merge(key+"Delta",currentValue-previous,Long::sum);
                sample.put(key,currentValue);
            }
            var leases=activeLeases.apply(world);if(leases!=null)sample.put("activePackLeases",leases);
            bucket.population.put(world.toString(),sample);
        }catch(RuntimeException ignored){/* A diagnostic sample cannot affect spawning. */}
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
        long now=System.currentTimeMillis();if(now>=current.deadline)return;
        // Diagnostics must never throw into Hytale's spawn or world thread.
        try{
            String category=clean(stage,64),nativeRole=clean(role,96),message=clean(detail,1536);
            String key=category+"|"+world+"|"+environment+"|"+nativeRole;
            if(current.counts.size()>=50_000&&!current.counts.containsKey(key))key="OTHER|COUNTER_LIMIT";
            current.counts.merge(key,amount,Long::sum);current.total+=amount;
            var bucket=bucket(current,now);
            if(category.equals("NATIVE_JOB_CREATED")||category.startsWith("NATIVE_REJECTION_")
                    ||category.equals("NATIVE_FLUID_JOB_DRY_COLUMN")||category.equals("NATIVE_CLASSIFY_REJECT")
                    ||category.equals("ELITE_PUBLISHED")||category.equals("ELITE_FALLBACK")
                    ||category.startsWith("PACK_LEASE_")||category.startsWith("NATIVE_HABITAT")||category.equals("HOSTILE_NAME")||category.equals("PLAYER_LOCAL"))bucket.counts.merge(category,amount,Long::sum);
            if(category.equals("HOSTILE_NAME"))bucket.counts.merge("HOSTILE_NAME_"+Objects.toString(field(message,"outcome="),"UNKNOWN"),amount,Long::sum);
            if(category.equals("NATIVE_HABITAT"))bucket.counts.merge("HABITAT_"+Objects.toString(field(message,"outcome="),"UNKNOWN"),amount,Long::sum);
            if(category.equals("NPC_ADDED_NATIVE_JOB")||category.equals("NPC_ADDED_OTHER"))
                bucket.additions.merge(populationCategory(nativeRole),amount,Long::sum);
            if(category.equals("NPC_REMOVED"))
                bucket.removals.merge(Objects.toString(field(message,"reason="),"unknown"),amount,Long::sum);
            var row=Map.<String,Object>of(
                    "time",Instant.now().toString(),"stage",category,"world",world==null?"unknown":world.toString(),
                    "environment",environment,"role",nativeRole,"count",amount,"detail",message);
            if(priority(category,message)){
                String reason=field(message,"subreason=");
                if(reason==null)reason=field(message,"reason=");
                String priorityKey=category+"|"+(reason==null?"unspecified":reason);
                if(current.priorityCounts.size()>=512&&!current.priorityCounts.containsKey(priorityKey))priorityKey="OTHER|PRIORITY_LIMIT";
                current.priorityCounts.merge(priorityKey,amount,Long::sum);
                if(current.priorityDetails.size()<MAX_PRIORITY_DETAILS)current.priorityDetails.add(row);
            }else if(current.details.size()<MAX_DETAILS)current.details.add(row);
        }catch(RuntimeException ignored){/* Trace cannot affect native admission. */}
    }
    private static Bucket bucket(Session session,long now){
        int index=(int)Math.min(session.buckets.size()-1,Math.max(0,(now-session.started)/10_000L));
        return session.buckets.get(index);
    }
    private static final com.inigmasgames.hytalerpg.spawning.NativePopulationRoles POPULATION_ROLES=
            com.inigmasgames.hytalerpg.spawning.NativePopulationRoles.load();
    private static String populationCategory(String role){return POPULATION_ROLES.category(role).name();}
    private static boolean priority(String stage,String detail){
        return stage.equals("ELITE_PUBLISHED")||stage.equals("PLAYER_LOCAL")||stage.equals("PACK_RESERVATION")||stage.startsWith("PACK_LEASE_")
                ||stage.equals("NATIVE_CLASSIFY_REJECT")
                ||stage.equals("PACK_REACTIVATED")||stage.equals("PACK_GRANDFATHERED_OVER_CAP")
                ||stage.equals("PACK_NEW_BIRTH_DENIED")||stage.equals("NATIVE_EXTENSION_REJECTED")
                ||stage.equals("ELITE_PLAN_RESULT")||stage.equals("ELITE_FALLBACK_EXCEPTION")
                ||stage.equals("ELITE_FALLBACK")&&!detail.contains("reason=RARITY_NORMAL")
                ||stage.equals("ORIGINAL_GROUP_RESTORED");
    }
    private static String field(String detail,String marker){
        int start=detail.indexOf(marker);if(start<0)return null;
        start+=marker.length();int end=detail.indexOf(' ',start);
        return detail.substring(start,end<0?detail.length():end);
    }
    private static String clean(String text,int max){
        if(text==null)return "unknown";
        String safe=text.replaceAll("[\\p{Cntrl}]"," ");
        return safe.length()<=max?safe:safe.substring(0,max);
    }
    private void finish(){
        var current=session;if(current==null)return;
        session=null;if(installed==this)installed=null;
        lastEvents=current.total;lastDetails=current.details.size()+current.priorityDetails.size();lastCounts=Map.copyOf(current.counts);
        Path target=directory.resolve("monster-spawn-"+current.started+".jsonl");
        try{
            Files.createDirectories(directory);
            Path temp=target.resolveSibling(target.getFileName()+".tmp");
            try(var writer=Files.newBufferedWriter(temp,StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){
                writer.write(gson.toJson(Map.of("type","summary","started",Instant.ofEpochMilli(current.started).toString(),
                        "ended",Instant.now().toString(),"events",current.total,"detailedEvents",lastDetails,
                        "detailLimit",MAX_DETAILS,"priorityDetailLimit",MAX_PRIORITY_DETAILS,
                        "priorityCounts",current.priorityCounts,"counts",current.counts,"manifest",current.manifest)));writer.newLine();
                for(var bucket:current.buckets){writer.write(gson.toJson(bucket.output()));writer.newLine();}
                for(var event:current.details){writer.write(gson.toJson(event));writer.newLine();}
                for(var event:current.priorityDetails){writer.write(gson.toJson(event));writer.newLine();}
            }
            Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            lastFile=target.toString();lastError="none";
        }catch(IOException error){lastError=error.toString();}
    }
    @Override public synchronized void close(){if(!closed){closed=true;finish();expiry.shutdownNow();}}
}
