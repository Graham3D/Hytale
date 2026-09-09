package com.inigmasgames.hytalerpg.progress;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.zip.CRC32C;
import static java.nio.file.StandardOpenOption.*;
import static com.inigmasgames.hytalerpg.progress.FileEncounterStore.DurabilityBoundary.*;

/** Prearmed, content-linked WAL. Only immutable values enter its preparation/checkpoint workers. */
final class EncounterJournalV2 implements EncounterLog {
    static final int HEADER=64, ACTIVATION=80, DATA=HEADER+ACTIVATION;
    static final long EXTENT=DATA+(long)EncounterJournal.CHECKPOINT_RECORDS*(EncounterJournal.MAX_RECORD+4);
    static final long MAGIC=0x52504757414c3032L, ACTIVATE=0x5250474143543032L;
    private static final UUID ZERO=new UUID(0,0);
    private record Segment(UUID id,Path path,FileChannel channel,UUID predecessor,long previous,long first){}
    private final Path directory;
    private final EncounterJournal.Checkpoints legacy;
    private final EncounterCheckpointBundle checkpoints;
    private final EncounterBarrierArbiter barriers;
    private final EncounterPersistenceTimings timings;
    private final Consumer<FileEncounterStore.DurabilityBoundary> fault;
    private final Consumer<FileEncounterStore.JournalBoundary> oldFault;
    private final ExecutorService preparation=Executors.newSingleThreadExecutor(r->Thread.ofPlatform().daemon().name("RPG-WAL-preparation").unstarted(r));
    private final Map<EncounterJournal.Key,EncounterJournal.Entry> cache=new LinkedHashMap<>(16,.75f,true);
    private final Set<EncounterJournal.Key> dirty=new HashSet<>();
    private final ConcurrentHashMap<EncounterJournal.Key,Integer> pinned=new ConcurrentHashMap<>();
    private final boolean preallocate;
    private Segment active;
    private CompletableFuture<Segment> prepared;
    private long sequence;
    private int records,reserved;
    private boolean admission,closed;
    private final List<Path> history=new ArrayList<>();

    EncounterJournalV2(Path root,EncounterJournal.Checkpoints legacy,EncounterBarrierArbiter barriers,
                       EncounterPersistenceTimings timings,Consumer<FileEncounterStore.DurabilityBoundary> fault,
                       Consumer<FileEncounterStore.JournalBoundary> oldFault,boolean existing,long legacyFloor,boolean preallocate)throws IOException {
        this.directory=root.resolve("journal-v2");this.legacy=legacy;this.barriers=barriers;this.timings=timings;this.fault=fault;this.oldFault=oldFault;this.preallocate=preallocate;
        Files.createDirectories(directory);checkpoints=new EncounterCheckpointBundle(root,barriers,timings,fault);
        try{
            if(existing){checkpoints.load();recover();}
            else{
                // No v2 contributions are admitted before the version-2 manifest is durable.
                // Leftovers from an interrupted bootstrap cannot contain acknowledged v2 work.
                discardUnpublishedBootstrap(legacyFloor);
                active=prepare();activate(active,ZERO,legacyFloor);barriers.force(active.channel(),EncounterBarrierArbiter.Kind.PREPARATION);
                active=new Segment(active.id(),active.path(),active.channel(),ZERO,legacyFloor,legacyFloor+1);
                sequence=legacyFloor;history.add(active.path());checkpoints.initialize(legacyFloor,active.id());
                cleanPrepared(Set.of(active.id()));
            }
            schedulePreparation();
        }catch(IOException|RuntimeException error){close();throw error;}
    }
    private void recover()throws IOException {
        var manifest=checkpoints.manifest();sequence=manifest.through();
        var segments=new HashMap<UUID,Segment>();var orphans=new ArrayList<Segment>();
        try(var paths=Files.newDirectoryStream(directory,"*.wal2")){
            for(var path:paths){
                if(segments.size()+orphans.size()>=68)throw corrupt("SEGMENT_BOUNDS");
                Segment segment=readSegment(path);if(segment.first()==0)orphans.add(segment);else if(segments.put(segment.id(),segment)!=null)throw corrupt("DUPLICATE_SEGMENT");
            }
        }
        Segment segment=segments.get(manifest.active());if(segment==null)throw corrupt("MISSING_ACTIVE_SEGMENT");
        if(segment.first()>manifest.through()+1)throw corrupt("MANIFEST_SEQUENCE_GAP");
        var visited=new HashSet<UUID>();
        while(true){
            if(!visited.add(segment.id()))throw corrupt("SEGMENT_CYCLE");
            long last=replay(segment,manifest.through());history.add(segment.path());
            if(last<manifest.through())throw corrupt("CHECKPOINT_AHEAD_OF_WAL");
            UUID predecessor=segment.id();var children=segments.values().stream().filter(s->s.predecessor().equals(predecessor)).toList();
            // Explicit loop below avoids relying on file enumeration order.
            if(children.size()>1)throw corrupt("DUPLICATE_ACTIVATION");
            if(children.isEmpty())break;
            Segment child=children.getFirst();if(child.previous()!=last||child.first()!=last+1)throw corrupt("LINK_SEQUENCE");segment=child;
        }
        for(var candidate:segments.values())if(!visited.contains(candidate.id())&&candidate.first()>manifest.through())throw corrupt("ORPHAN_ACTIVATION");
        active=new Segment(segment.id(),segment.path(),FileChannel.open(segment.path(),READ,WRITE),segment.predecessor(),segment.previous(),segment.first());
        active.channel().position(logicalEnd);records=(int)(sequence-segment.first()+1);
        // Do not interpret PREPARED existence as committed history. Clean only after all required
        // manifest/index/WAL validation succeeded, under the lifetime process writer lock.
        cleanPrepared(visited);
    }
    private long logicalEnd;
    private Segment readSegment(Path path)throws IOException {
        String name=path.getFileName().toString();UUID id;
        try{id=UUID.fromString(name.substring(0,name.length()-5));}catch(RuntimeException e){throw corrupt("SEGMENT_NAME");}
        try(var channel=FileChannel.open(path,READ)){
            if(channel.size()<DATA||channel.size()>EXTENT)throw corrupt("SEGMENT_BOUNDS");
            byte[] header=read(channel,HEADER);var data=ByteBuffer.wrap(header);
            if(data.getLong()!=MAGIC||data.getInt()!=2||!uuid(data).equals(id)||data.getLong()!=EXTENT)throw corrupt("HEADER");
            if(ByteBuffer.wrap(header,HEADER-8,8).getLong()!=checksum(Arrays.copyOf(header,HEADER-8)))throw corrupt("HEADER_CRC");
            byte[] activation=read(channel,ACTIVATION);if(zero(activation)){verifyZeroTail(channel);return new Segment(id,path,null,ZERO,0,0);}
            var fields=ByteBuffer.wrap(activation);
            if(fields.getLong()!=ACTIVATE||fields.getInt()!=2||!uuid(fields).equals(id))throw corrupt("ACTIVATION");
            UUID previous=uuid(fields);long last=fields.getLong(),first=fields.getLong();
            if(last<0||last==Long.MAX_VALUE||first!=last+1||ByteBuffer.wrap(activation,ACTIVATION-8,8).getLong()!=checksum(Arrays.copyOf(activation,ACTIVATION-8)))throw corrupt("ACTIVATION_CRC");
            return new Segment(id,path,null,previous,last,first);
        }
    }
    private long replay(Segment segment,long floor)throws IOException {
        long expected=segment.first();
        try(var channel=FileChannel.open(segment.path(),READ)){
            channel.position(DATA);logicalEnd=DATA;
            while(channel.position()<channel.size()){
                long offset=channel.position();int length=ByteBuffer.wrap(read(channel,4)).getInt();
                if(length==0){verifyZeroTail(channel);logicalEnd=offset;return expected-1;}
                if(length<16||length>EncounterJournal.MAX_RECORD)throw corrupt("FRAME_BOUNDS");
                byte[] bytes=read(channel,length);if(ByteBuffer.wrap(bytes,length-8,8).getLong()!=checksum(Arrays.copyOf(bytes,length-8)))throw corrupt("FRAME_CRC");
                var decoded=decode(bytes,expected++);
                if(decoded.value().sequence()>floor){cachePut(decoded.key(),decoded.value());dirty.add(decoded.key());sequence=decoded.value().sequence();}
                logicalEnd=channel.position();
                if(expected-segment.first()>EncounterJournal.CHECKPOINT_RECORDS)throw corrupt("SEGMENT_RECORD_BOUNDS");
            }
        }return expected-1;
    }
    private EncounterJournal.Frame decode(byte[] bytes,long expected)throws IOException {
        try(var in=new DataInputStream(new ByteArrayInputStream(bytes,0,bytes.length-8))){
            long next=in.readLong();if(next!=expected)throw corrupt("GLOBAL_SEQUENCE");
            var key=new EncounterJournal.Key(uuid(in),uuid(in));long previous=in.readLong();if(previous<0||previous>=next)throw corrupt("CONTEXT_SEQUENCE");
            var old=entry(key);if(old==null)throw corrupt("MISSING_CONTEXT");
            long combat=in.readLong(),progress=in.readLong(),observed=in.readLong();double lowest=in.readDouble();boolean excluded=in.readBoolean();
            var credits=new HashMap<UUID,EncounterContributions.Credit>();old.snapshot().credits().forEach(c->credits.put(c.player(),c));var touched=new HashSet<UUID>();
            int removed=in.readUnsignedShort();if(removed>EncounterContributions.MAX_CONTRIBUTORS)throw corrupt("DELTA_BOUNDS");
            for(int i=0;i<removed;i++){UUID id=uuid(in);if(!touched.add(id))throw corrupt("DELTA_DUPLICATE");credits.remove(id);}
            int added=in.readUnsignedShort();if(added>EncounterContributions.MAX_CONTRIBUTORS)throw corrupt("DELTA_BOUNDS");
            for(int i=0;i<added;i++){
                UUID id=uuid(in);int kind=in.readUnsignedByte();if(!touched.add(id)||kind>=EncounterContributions.Kind.values().length)throw corrupt("DELTA_KIND");
                credits.put(id,new EncounterContributions.Credit(id,EncounterContributions.Kind.values()[kind],in.readLong(),in.readDouble()));
            }
            if(in.available()!=0)throw corrupt("TRAILING_FIELDS");
            if(next<=old.sequence())return new EncounterJournal.Frame(key,old,bytes);
            if(previous!=old.sequence())throw corrupt("CONTEXT_SEQUENCE");
            var value=new EncounterContributions.Snapshot(old.snapshot().spawn(),credits.values().stream().sorted(Comparator.comparing(c->c.player().toString())).toList(),combat,progress,observed,lowest,excluded);
            FileEncounterStore.validateTransition(old.snapshot(),value);return new EncounterJournal.Frame(key,new EncounterJournal.Entry(next,value),bytes);
        }
    }
    public EncounterJournal.Entry entry(EncounterJournal.Key key){
        var value=cache.get(key);if(value!=null)return value;
        value=checkpoints.lookup(key);
        if(value==null){value=legacy.load(key);if(value!=null&&value.sequence()>checkpoints.manifest().legacyFloor())throw corrupt("LEGACY_CHECKPOINT_AHEAD_OF_MIGRATION");}
        if(value!=null)cachePut(key,value);return value;
    }
    private void cachePut(EncounterJournal.Key key,EncounterJournal.Entry value){
        if(!cache.containsKey(key)&&cache.size()>=EncounterContributions.MAX_ENCOUNTERS){
            var evict=cache.keySet().stream().filter(k->!dirty.contains(k)&&!pinned.containsKey(k)).findFirst().orElseThrow(()->corrupt("CACHE_CAPACITY"));cache.remove(evict);
        }cache.put(key,value);
    }
    public void reserve(int count)throws IOException {
        if(admission||count<0||count>64)throw new FileEncounterStore.CapacityRejected();reserved=count;admission=true;
    }
    public void release(){reserved=0;admission=false;}
    public void append(EncounterContributions.Snapshot value)throws IOException {if(!admission||reserved<1)throw new FileEncounterStore.CapacityRejected();appendGroup(List.of(value));reserved--;}
    public void appendGroup(List<EncounterContributions.Snapshot> values)throws IOException {
        if(values.isEmpty()||values.size()>64)throw new FileEncounterStore.CapacityRejected();
        boolean rotate=records+values.size()>EncounterJournal.CHECKPOINT_RECORDS;
        Segment successor=null;Map<EncounterJournal.Key,EncounterJournal.Entry> captured=null;long through=sequence;
        if(rotate){
            barriers.suspend();long start=System.nanoTime();
            try{legacy.reserveCheckpoint();successor=awaitPrepared();}finally{barriers.resume();timings.record(EncounterPersistenceTimings.Phase.CHECKPOINT_ROTATION,System.nanoTime()-start);}
            captured=capture();
        }
        long encodeStart=System.nanoTime();var frames=new ArrayList<EncounterJournal.Frame>();var provisional=new HashMap<EncounterJournal.Key,EncounterJournal.Entry>();long next=sequence;
        for(var value:values){var key=new EncounterJournal.Key(value.spawn().world(),value.spawn().enemy());next=Math.addExact(next,1);var frame=EncounterJournal.encode(value,provisional.containsKey(key)?provisional.get(key):entry(key),next);frames.add(frame);provisional.put(key,frame.value());}
        timings.record(EncounterPersistenceTimings.Phase.ENCODE_VALIDATE,System.nanoTime()-encodeStart);
        int first=0;
        while(first<frames.size()){
            int end=first,bytes=0;while(end<frames.size()&&bytes+frames.get(end).bytes().length<=EncounterGroupCommit.MAX_GROUP_BYTES)bytes+=frames.get(end++).bytes().length;
            if(end==first)throw corrupt("GROUP_BYTES");
            FileChannel channel=successor==null?active.channel():successor.channel();long start=System.nanoTime();
            if(successor!=null){activate(successor,active.id(),through);fault.accept(ACTIVATION_WRITTEN);}
            for(int i=first;i<end;i++){EncounterCheckpointBundle.write(channel,ByteBuffer.wrap(frames.get(i).bytes()));if(i==first)legacy.groupFault(FileEncounterStore.GroupBoundary.AFTER_FIRST_FRAME);}
            timings.record(EncounterPersistenceTimings.Phase.JOURNAL_APPEND,System.nanoTime()-start);
            oldFault.accept(FileEncounterStore.JournalBoundary.AFTER_APPEND);legacy.groupFault(FileEncounterStore.GroupBoundary.AFTER_GROUP_APPEND);legacy.groupFault(FileEncounterStore.GroupBoundary.BEFORE_FORCE);
            barriers.force(channel,EncounterBarrierArbiter.Kind.FOREGROUND);
            oldFault.accept(FileEncounterStore.JournalBoundary.AFTER_FORCE);legacy.groupFault(FileEncounterStore.GroupBoundary.AFTER_GROUP_FORCE);
            if(successor!=null){
                fault.accept(ACTIVATION_FORCED);fault.accept(BEFORE_ACTIVE_SWITCH);
                Segment old=active;active=new Segment(successor.id(),successor.path(),channel,old.id(),through,through+1);old.channel().close();history.add(active.path());
                dirty.clear();records=0;prepared=null;fault.accept(AFTER_ACTIVE_SWITCH);
                scheduleCheckpoint(captured,through,active.id());schedulePreparation();successor=null;
            }
            for(int i=first;i<end;i++){var frame=frames.get(i);cachePut(frame.key(),frame.value());dirty.add(frame.key());sequence=frame.value().sequence();records++;}
            timings.group(end-first,bytes);first=end;
        }
    }
    private Map<EncounterJournal.Key,EncounterJournal.Entry> capture(){
        var captured=new LinkedHashMap<EncounterJournal.Key,EncounterJournal.Entry>();
        dirty.stream().sorted(Comparator.comparing(EncounterCheckpointBundle::key)).forEach(k->captured.put(k,cache.get(k)));
        for(var key:captured.keySet())pinned.merge(key,1,Integer::sum);return Map.copyOf(captured);
    }
    public void checkpoint()throws IOException {
        if(admission)throw new FileEncounterStore.CapacityRejected();if(dirty.isEmpty())return;
        barriers.suspend();try{legacy.reserveCheckpoint();}finally{barriers.resume();}
        var captured=capture();dirty.clear();scheduleCheckpoint(captured,sequence,active.id());
    }
    private void scheduleCheckpoint(Map<EncounterJournal.Key,EncounterJournal.Entry> captured,long through,UUID successor){
        var retired=history.stream().filter(p->!p.getFileName().toString().equals(successor+".wal2")).toList();history.removeAll(retired);
        long scheduled=System.nanoTime();legacy.submit(()->{
            timings.record(EncounterPersistenceTimings.Phase.CHECKPOINT_QUEUE,System.nanoTime()-scheduled);legacy.groupFault(FileEncounterStore.GroupBoundary.CHECKPOINT_WORKER_STARTED);
            try{
                checkpoints.checkpoint(captured,through,successor);fault.accept(BEFORE_WAL_RETIRE);
                for(var path:retired){Files.deleteIfExists(path);fault.accept(DURING_WAL_RETIRE);}
                for(var key:captured.keySet())pinned.compute(key,(ignored,count)->count==1?null:count-1);
            }catch(IOException e){throw new IllegalStateException("ENCOUNTER_V2_CHECKPOINT_FAILED",e);}
        });
        timings.record(EncounterPersistenceTimings.Phase.CHECKPOINT_SCHEDULING,System.nanoTime()-scheduled);
    }
    private void schedulePreparation(){
        if(closed)return;prepared=CompletableFuture.supplyAsync(()->{try{return prepare();}catch(IOException e){throw new CompletionException(e);}},preparation);
    }
    private Segment awaitPrepared()throws IOException {
        long start=System.nanoTime();if(!prepared.isDone())timings.preparedMiss();
        try{return prepared.get(EncounterGroupCommit.DEADLINE_MILLIS,TimeUnit.MILLISECONDS);}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}
        catch(ExecutionException|TimeoutException e){throw new IOException("PREPARED_SUCCESSOR_UNAVAILABLE",e);}
        finally{timings.record(EncounterPersistenceTimings.Phase.PREPARED_WAIT,System.nanoTime()-start);}
    }
    private Segment prepare()throws IOException {
        long start=System.nanoTime();UUID id=UUID.randomUUID();Path temporary=directory.resolve(id+".preparing"),path=directory.resolve(id+".wal2");
        FileChannel channel=null;
        try{
            channel=FileChannel.open(temporary,CREATE_NEW,READ,WRITE);fault.accept(PREPARE_CREATED);
            if(preallocate){channel.position(EXTENT-1);EncounterCheckpointBundle.write(channel,ByteBuffer.wrap(new byte[1]));}
            channel.position(0);var header=ByteBuffer.allocate(HEADER);header.putLong(MAGIC).putInt(2);uuid(header,id);header.putLong(EXTENT);header.position(HEADER-8);header.putLong(checksum(Arrays.copyOf(header.array(),HEADER-8)));header.flip();
            EncounterCheckpointBundle.write(channel,header);EncounterCheckpointBundle.write(channel,ByteBuffer.wrap(new byte[ACTIVATION]));fault.accept(PREPARE_HEADER_WRITTEN);
            barriers.force(channel,EncounterBarrierArbiter.Kind.PREPARATION);fault.accept(PREPARE_FORCED);
            channel.close();channel=null;Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE);
            channel=FileChannel.open(path,READ,WRITE);channel.position(DATA);
            timings.record(EncounterPersistenceTimings.Phase.SUCCESSOR_PREPARATION,System.nanoTime()-start);return new Segment(id,path,channel,ZERO,0,0);
        }catch(IOException|RuntimeException e){if(channel!=null)channel.close();throw e;}
    }
    private void activate(Segment segment,UUID previous,long last)throws IOException {
        long start=System.nanoTime();var data=ByteBuffer.allocate(ACTIVATION);data.putLong(ACTIVATE).putInt(2);uuid(data,segment.id());uuid(data,previous);data.putLong(last).putLong(last+1);data.position(ACTIVATION-8);data.putLong(checksum(Arrays.copyOf(data.array(),ACTIVATION-8)));data.flip();
        segment.channel().position(HEADER);EncounterCheckpointBundle.write(segment.channel(),data);segment.channel().position(DATA);
        timings.record(EncounterPersistenceTimings.Phase.SUCCESSOR_ACTIVATION,System.nanoTime()-start);
    }
    private void cleanPrepared(Set<UUID> retained)throws IOException {
        try(var paths=Files.newDirectoryStream(directory)){
            for(var path:paths){String name=path.getFileName().toString();
                if(name.matches("[a-f0-9-]{36}\\.preparing")){Files.delete(path);continue;}
                if(name.endsWith(".wal2")){var segment=readSegment(path);if(segment.first()==0&&!retained.contains(segment.id()))Files.delete(path);}
            }
        }
    }
    private void discardUnpublishedBootstrap(long floor)throws IOException {
        int count=0;
        try(var paths=Files.newDirectoryStream(directory)){
            for(var path:paths){if(++count>68)throw corrupt("BOOTSTRAP_BOUNDS");String name=path.getFileName().toString();
                if(name.matches("[a-f0-9-]{36}\\.preparing")){Files.delete(path);continue;}
                if(!name.endsWith(".wal2"))throw corrupt("BOOTSTRAP_FILE");
                Segment candidate=readSegment(path);
                if(candidate.first()!=0&&(!candidate.predecessor().equals(ZERO)||candidate.previous()!=floor))throw corrupt("BOOTSTRAP_HISTORY");
                try(var channel=FileChannel.open(path,READ)){channel.position(DATA);verifyZeroTail(channel);}
                Files.delete(path);
            }
        }
    }
    public long sequence(){return sequence;}
    Map<String,Object> diagnostics(){return Map.of("format",2,"active",active.id().toString(),"activeFirst",active.first(),"durableSequence",sequence,"activeRecords",records,"preparedReady",prepared!=null&&prepared.isDone()&&!prepared.isCompletedExceptionally(),"extentBytes",EXTENT,"preallocated",preallocate,"checkpointGeneration",checkpoints.manifest().generation(),"checkpointThrough",checkpoints.manifest().through());}
    void awaitPreparation()throws IOException {barriers.suspend();try{awaitPrepared();}finally{barriers.resume();}}
    @Override public void close()throws IOException {
        closed=true;preparation.shutdown();
        try{
            if(!preparation.awaitTermination(5,TimeUnit.SECONDS)){preparation.shutdownNow();if(!preparation.awaitTermination(5,TimeUnit.SECONDS))throw new IOException("PREPARATION_SHUTDOWN_TIMEOUT");}
        }catch(InterruptedException e){preparation.shutdownNow();Thread.currentThread().interrupt();throw new IOException(e);}
        finally{if(active!=null&&active.channel()!=null)active.channel().close();if(prepared!=null&&prepared.isDone()&&!prepared.isCompletedExceptionally())prepared.join().channel().close();}
    }
    private static byte[] read(FileChannel channel,int length)throws IOException {var data=ByteBuffer.allocate(length);while(data.hasRemaining())if(channel.read(data)<0)throw corrupt("TORN_RECORD");return data.array();}
    private static void verifyZeroTail(FileChannel channel)throws IOException {while(channel.position()<channel.size())if(!zero(read(channel,(int)Math.min(8192,channel.size()-channel.position()))))throw corrupt("NONZERO_TORN_TAIL");}
    private static boolean zero(byte[] bytes){for(byte b:bytes)if(b!=0)return false;return true;}
    private static long checksum(byte[] bytes){var crc=new CRC32C();crc.update(bytes,0,bytes.length);return crc.getValue();}
    private static UUID uuid(ByteBuffer data){return new UUID(data.getLong(),data.getLong());}
    private static void uuid(ByteBuffer data,UUID id){data.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());}
    private static UUID uuid(DataInputStream data)throws IOException {return new UUID(data.readLong(),data.readLong());}
    private static IllegalStateException corrupt(String boundary){return new IllegalStateException("ENCOUNTER_V2_"+boundary);}
}
