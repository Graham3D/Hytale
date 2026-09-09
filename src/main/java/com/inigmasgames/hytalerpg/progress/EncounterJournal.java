package com.inigmasgames.hytalerpg.progress;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.zip.CRC32C;
import static java.nio.file.StandardOpenOption.*;

/** Store-monitor confined. Only immutable encounter values cross this boundary; no ECS references. */
final class EncounterJournal implements AutoCloseable {
    static final int MAX_RECORD=16384, CHECKPOINT_RECORDS=1024;
    private static final long MAGIC=0x52504757414c3031L;
    record Key(UUID world,UUID enemy) {}
    record Entry(long sequence,EncounterContributions.Snapshot snapshot) {}
    interface Checkpoints {
        Entry load(Key key);
        long floor();
        void save(Key key,Entry value);
        void floor(long value);
        void submit(Runnable publication);
        void reserveCheckpoint();
        void groupFault(FileEncounterStore.GroupBoundary boundary);
    }
    private final Path directory;
    private final Checkpoints checkpoints;
    private final EncounterPersistenceTimings timings;
    private final Consumer<FileEncounterStore.JournalBoundary> fault;
    private final LinkedHashMap<Key,Entry> cache=new LinkedHashMap<>(16,.75f,true);
    private final Set<Key> dirty=new HashSet<>();
    private FileChannel channel;
    private long sequence;
    private volatile long floor;
    private final java.util.concurrent.ConcurrentHashMap<Key,Integer> pinned=new java.util.concurrent.ConcurrentHashMap<>();
    private int records,reserved;
    private boolean admission;
    private boolean replaying=true;

    EncounterJournal(Path directory,Checkpoints checkpoints,EncounterPersistenceTimings timings,
                     Consumer<FileEncounterStore.JournalBoundary> fault)throws IOException {
        this.directory=directory;this.checkpoints=checkpoints;this.timings=timings;this.fault=fault;
        Files.createDirectories(directory);floor=checkpoints.floor();sequence=floor;
        var segments=segments();
        if(segments.isEmpty()) {
            // A committed floor always publishes its successor segment first.
            if(floor!=0||Files.exists(directory.resolve("initialized")))throw corrupt("MISSING_JOURNAL");
            try {
                channel=newSegment(1);
                try(var marker=FileChannel.open(directory.resolve("initialized"),CREATE_NEW,WRITE)){marker.force(true);}
            }catch(RuntimeException|IOException error){close();throw error;}
        } else {
            try {
                for(Path segment:segments) {
                    long start=start(segment);
                    if(start<=floor)continue; // Retired segments can survive interrupted cleanup.
                    if(start!=sequence+1)throw corrupt("SEGMENT_SEQUENCE");
                    replay(segment,start);
                }
                Path latest=segments.getLast();
                if(start(latest)<=floor)throw corrupt("MISSING_SUCCESSOR");
                for(var entry:cache.values())if(entry.sequence()>sequence)throw corrupt("CHECKPOINT_AHEAD_OF_JOURNAL");
                channel=FileChannel.open(latest,WRITE);channel.position(channel.size());
            } catch(RuntimeException|IOException error){close();throw error;}
        }
        replaying=false;
    }
    private List<Path> segments()throws IOException {
        var result=new ArrayList<Path>();
        try(var entries=Files.newDirectoryStream(directory,"*.wal")){
            for(Path path:entries){if(result.size()>=64)throw corrupt("SEGMENT_BOUNDS");start(path);result.add(path);}
        }
        result.sort(Comparator.comparing(p->p.getFileName().toString()));return result;
    }
    private static long start(Path path){
        String name=path.getFileName().toString();
        if(!name.matches("[0-9]{20}\\.wal"))throw corrupt("SEGMENT_NAME");
        long value=Long.parseLong(name.substring(0,20));if(value<1)throw corrupt("SEGMENT_SEQUENCE");return value;
    }
    private FileChannel newSegment(long first)throws IOException {
        Path target=directory.resolve(String.format(Locale.ROOT,"%020d.wal",first));
        Path temporary=target.resolveSibling(target.getFileName()+"."+UUID.randomUUID()+".tmp");
        var header=ByteBuffer.allocate(24).putLong(MAGIC).putLong(first);
        header.putLong(checksum(Arrays.copyOf(header.array(),16))).flip();
        long started=System.nanoTime(),forced=0;
        try(var out=FileChannel.open(temporary,CREATE_NEW,WRITE)){
            writeAll(out,header);long forceStart=System.nanoTime();
            try{out.force(true);}finally{forced=System.nanoTime()-forceStart;timings.record(EncounterPersistenceTimings.Phase.CHECKPOINT_FORCE,forced);}
        }
        Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE);
        var out=FileChannel.open(target,WRITE);out.position(out.size());
        timings.record(EncounterPersistenceTimings.Phase.CHECKPOINT_WRITE,System.nanoTime()-started-forced);return out;
    }
    private void replay(Path path,long first)throws IOException {
        try(var in=FileChannel.open(path,READ)) {
            if(in.size()>((long)CHECKPOINT_RECORDS+EncounterContributions.MAX_SUPPORT_ENCOUNTERS)*MAX_RECORD+24)throw corrupt("SEGMENT_BOUNDS");
            var header=ByteBuffer.allocate(24);readAll(in,header);header.flip();
            if(header.getLong()!=MAGIC||header.getLong()!=first||header.getLong()!=checksum(Arrays.copyOf(header.array(),16)))throw corrupt("HEADER");
            while(in.position()<in.size()) {
                var size=ByteBuffer.allocate(4);readAll(in,size);size.flip();int length=size.getInt();
                if(length<16||length>MAX_RECORD)throw corrupt("RECORD_BOUNDS");
                var data=ByteBuffer.allocate(length);readAll(in,data);
                byte[] bytes=data.array();long crc=ByteBuffer.wrap(bytes,length-8,8).getLong();
                if(crc!=checksum(Arrays.copyOf(bytes,length-8)))throw corrupt("CHECKSUM");
                try(var fields=new DataInputStream(new ByteArrayInputStream(bytes,0,length-8))) {
                    long next=fields.readLong();if(next!=sequence+1)throw corrupt("RECORD_SEQUENCE");
                    var key=new Key(uuid(fields),uuid(fields));long previous=fields.readLong();if(previous<0||previous>=next)throw corrupt("CONTEXT_SEQUENCE");
                    Entry old=entry(key);if(old==null)throw corrupt("MISSING_CONTEXT");
                    long firstCombat=fields.readLong(),progress=fields.readLong(),observed=fields.readLong();
                    double lowest=fields.readDouble();boolean disqualified=fields.readBoolean();
                    int removed=fields.readUnsignedShort();if(removed>EncounterContributions.MAX_CONTRIBUTORS)throw corrupt("DELTA_BOUNDS");
                    var credits=new HashMap<UUID,EncounterContributions.Credit>();for(var c:old.snapshot().credits())credits.put(c.player(),c);
                    var touched=new HashSet<UUID>();
                    for(int i=0;i<removed;i++){var id=uuid(fields);if(!touched.add(id))throw corrupt("DUPLICATE_DELTA");credits.remove(id);}
                    int added=fields.readUnsignedShort();if(added>EncounterContributions.MAX_CONTRIBUTORS)throw corrupt("DELTA_BOUNDS");
                    for(int i=0;i<added;i++){
                        var id=uuid(fields);int kind=fields.readUnsignedByte();if(kind>=EncounterContributions.Kind.values().length||!touched.add(id))throw corrupt("DELTA_KIND");
                        credits.put(id,new EncounterContributions.Credit(id,EncounterContributions.Kind.values()[kind],fields.readLong(),fields.readDouble()));
                    }
                    if(fields.available()!=0)throw corrupt("TRAILING_FIELDS");
                    if(next>old.sequence()) {
                        if(previous!=old.sequence())throw corrupt("CONTEXT_SEQUENCE");
                        var value=new EncounterContributions.Snapshot(old.snapshot().spawn(),sorted(credits.values()),firstCombat,progress,observed,lowest,disqualified);
                        FileEncounterStore.validateTransition(old.snapshot(),value);
                        cache.put(key,new Entry(next,value));dirty.add(key);
                    }
                    sequence=next;records++;
                }
            }
        }
    }
    Entry entry(Key key){
        Entry value=cache.get(key);if(value!=null)return value;
        if(cache.size()>=EncounterContributions.MAX_ENCOUNTERS) {
            var evict=cache.keySet().stream().filter(k->!dirty.contains(k)&&!pinned.containsKey(k)).findFirst().orElseThrow(()->corrupt("CACHE_CAPACITY"));cache.remove(evict);
        }
        value=checkpoints.load(key);
        if(value!=null){if(!replaying&&value.sequence()>sequence)throw corrupt("CHECKPOINT_AHEAD_OF_JOURNAL");cache.put(key,value);}return value;
    }
    void reserve(int count)throws IOException {
        if(count<0||count>EncounterContributions.MAX_SUPPORT_ENCOUNTERS)throw new FileEncounterStore.CapacityRejected();
        if(admission)throw new FileEncounterStore.CapacityRejected();
        if(records+count>CHECKPOINT_RECORDS)checkpoint();
        reserved=count;admission=true;
    }
    void release(){reserved=0;admission=false;}
    void append(EncounterContributions.Snapshot value)throws IOException {
        if(!admission||reserved<=0)throw new FileEncounterStore.CapacityRejected();
        appendGroup(List.of(value));reserved--;
    }
    private record Frame(Key key,Entry value,byte[] bytes){}
    /** One owner assigns every predecessor before any bytes are written. Never expose this provisional map. */
    void appendGroup(List<EncounterContributions.Snapshot> values)throws IOException {
        if(values.isEmpty()||values.size()>EncounterGroupCommit.MAX_GROUP_RECORDS)throw new FileEncounterStore.CapacityRejected();
        if(records+values.size()>CHECKPOINT_RECORDS)checkpoint();
        var provisional=new HashMap<Key,Entry>();var frames=new ArrayList<Frame>();long next=sequence;
        for(var value:values){
            var key=new Key(value.spawn().world(),value.spawn().enemy());var old=provisional.containsKey(key)?provisional.get(key):entry(key);
            var frame=encode(value,old,Math.addExact(next,1));next++;frames.add(frame);provisional.put(key,frame.value());
        }
        int first=0;
        while(first<frames.size()){
            int end=first,bytes=0;
            while(end<frames.size()&&bytes+frames.get(end).bytes().length<=EncounterGroupCommit.MAX_GROUP_BYTES){bytes+=frames.get(end++).bytes().length;}
            if(end==first)throw corrupt("GROUP_BYTE_BOUNDS");
            var buffer=ByteBuffer.allocate(bytes);for(int i=first;i<end;i++)buffer.put(frames.get(i).bytes());buffer.flip();
            long started=System.nanoTime();
            try{
                // Two bounded writes permit a real-process crash exactly after the first complete frame.
                int limit=buffer.limit();buffer.limit(frames.get(first).bytes().length);writeAll(channel,buffer);
                checkpoints.groupFault(FileEncounterStore.GroupBoundary.AFTER_FIRST_FRAME);
                buffer.limit(limit);writeAll(channel,buffer);
            }finally{timings.record(EncounterPersistenceTimings.Phase.JOURNAL_APPEND,System.nanoTime()-started);}
            fault.accept(FileEncounterStore.JournalBoundary.AFTER_APPEND);
            checkpoints.groupFault(FileEncounterStore.GroupBoundary.AFTER_GROUP_APPEND);
            checkpoints.groupFault(FileEncounterStore.GroupBoundary.BEFORE_FORCE);
            started=System.nanoTime();try{channel.force(true);}finally{timings.record(EncounterPersistenceTimings.Phase.JOURNAL_FORCE,System.nanoTime()-started);}
            fault.accept(FileEncounterStore.JournalBoundary.AFTER_FORCE);
            checkpoints.groupFault(FileEncounterStore.GroupBoundary.AFTER_GROUP_FORCE);
            for(int i=first;i<end;i++){var frame=frames.get(i);cache.put(frame.key(),frame.value());dirty.add(frame.key());sequence=frame.value().sequence();records++;}
            timings.group(end-first,bytes);first=end;
        }
    }
    private Frame encode(EncounterContributions.Snapshot value,Entry old,long next)throws IOException {
        var key=new Key(value.spawn().world(),value.spawn().enemy());
        if(old==null)throw new IllegalStateException("UNREGISTERED_ENCOUNTER");
        FileEncounterStore.validateTransition(old.snapshot(),value);
        var previous=new HashMap<UUID,EncounterContributions.Credit>();for(var c:old.snapshot().credits())previous.put(c.player(),c);
        var changed=new ArrayList<EncounterContributions.Credit>();
        for(var c:value.credits())if(!c.equals(previous.remove(c.player())))changed.add(c);
        var bytes=new ByteArrayOutputStream(160);
        try(var out=new DataOutputStream(bytes)) {
            out.writeLong(next);uuid(out,key.world());uuid(out,key.enemy());out.writeLong(old.sequence());
            out.writeLong(value.firstCombat());out.writeLong(value.progressAt());out.writeLong(value.lastObserved());out.writeDouble(value.lowestHealthFraction());out.writeBoolean(value.disqualified());
            out.writeShort(previous.size());for(var id:previous.keySet().stream().sorted().toList())uuid(out,id);
            out.writeShort(changed.size());for(var c:sorted(changed)){uuid(out,c.player());out.writeByte(c.kind().ordinal());out.writeLong(c.observedAtMillis());out.writeDouble(c.actualAmount());}
        }
        byte[] payload=bytes.toByteArray();if(payload.length+8>MAX_RECORD)throw corrupt("RECORD_BOUNDS");
        var frame=ByteBuffer.allocate(4+payload.length+8).putInt(payload.length+8).put(payload).putLong(checksum(payload));frame.flip();
        return new Frame(key,new Entry(next,value),frame.array());
    }
    void checkpoint()throws IOException {
        if(admission)throw new FileEncounterStore.CapacityRejected();
        if(sequence==floor||dirty.isEmpty()&&channel.size()==24)return;
        checkpoints.reserveCheckpoint(); // Backpressure BEFORE creating another sealed segment.
        long rotationStart=System.nanoTime();
        long through=sequence;var captured=new LinkedHashMap<Key,Entry>();
        for(var key:dirty.stream().sorted(Comparator.comparing(k->k.world()+"/"+k.enemy())).toList())captured.put(key,cache.get(key));
        var retired=segments().stream().filter(path->start(path)<=through).toList();
        // Recovery may already have the empty successor from an interrupted rotation.
        if(channel.size()>24){channel.close();channel=newSegment(sequence+1);}
        fault.accept(FileEncounterStore.JournalBoundary.AFTER_ROTATION);
        timings.record(EncounterPersistenceTimings.Phase.CHECKPOINT_ROTATION,System.nanoTime()-rotationStart);
        for(var key:captured.keySet())pinned.merge(key,1,Integer::sum);
        long scheduled=System.nanoTime();
        checkpoints.submit(()->{
            timings.record(EncounterPersistenceTimings.Phase.CHECKPOINT_QUEUE,System.nanoTime()-scheduled);
            checkpoints.groupFault(FileEncounterStore.GroupBoundary.CHECKPOINT_WORKER_STARTED);
            for(var entry:captured.entrySet())checkpoints.save(entry.getKey(),entry.getValue());
            fault.accept(FileEncounterStore.JournalBoundary.AFTER_CHECKPOINTS);
            checkpoints.floor(through);floor=through;fault.accept(FileEncounterStore.JournalBoundary.AFTER_FLOOR);
            // Newer commits live in the durable successor. Never retire beyond this immutable capture.
            try{for(Path segment:retired)Files.deleteIfExists(segment);}catch(IOException error){throw new IllegalStateException("CHECKPOINT_RETIRE_FAILED",error);}
            for(var key:captured.keySet())pinned.compute(key,(ignored,count)->count==1?null:count-1);
        });
        timings.record(EncounterPersistenceTimings.Phase.CHECKPOINT_SCHEDULING,System.nanoTime()-scheduled);
        dirty.clear();records=0;
    }
    long sequence(){return sequence;}
    private static List<EncounterContributions.Credit> sorted(Collection<EncounterContributions.Credit> values){return values.stream().sorted(Comparator.comparing(c->c.player().toString())).toList();}
    private static long checksum(byte[] value){var crc=new CRC32C();crc.update(value,0,value.length);return crc.getValue();}
    private static UUID uuid(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
    private static void uuid(DataOutputStream out,UUID value)throws IOException{out.writeLong(value.getMostSignificantBits());out.writeLong(value.getLeastSignificantBits());}
    private static void writeAll(FileChannel channel,ByteBuffer data)throws IOException{while(data.hasRemaining())channel.write(data);}
    private static void readAll(FileChannel channel,ByteBuffer data)throws IOException{while(data.hasRemaining())if(channel.read(data)<0)throw corrupt("TORN_RECORD");}
    private static IllegalStateException corrupt(String detail){return new IllegalStateException("ENCOUNTER_JOURNAL_"+detail);}
    @Override public void close()throws IOException{if(channel!=null)channel.close();}
}
