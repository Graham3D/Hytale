package com.inigmasgames.hytalerpg.progress;

import com.google.gson.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import static java.nio.file.StandardOpenOption.*;

/** Immutable copy-on-write radix index, packed with snapshots into forced epoch bundles.
 * Unchanged index branches reference earlier bundles: historical encounters do not impose a
 * new global context cap or require rewriting the entire history at each checkpoint.
 * Bundle garbage collection is intentionally not part of the durability protocol. */
final class EncounterCheckpointBundle {
    static final int FORMAT=2, LEAF_ENTRIES=32, CHUNK_BYTES=16*1024*1024, MAX_CHUNKS=64;
    static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    record Ref(UUID bundle,int index,String hash) {
        Ref {Objects.requireNonNull(bundle);if(index<0||hash==null||!hash.matches("[a-f0-9]{64}"))throw bad("REF");}
    }
    record Node(int depth,List<EncounterJournal.Entry> entries,Map<String,Ref> children) {
        Node {if(depth<0||depth>64||entries==null||children==null||!entries.isEmpty()&&!children.isEmpty()
                ||entries.size()>LEAF_ENTRIES||children.size()>16)throw bad("NODE_BOUNDS");entries=List.copyOf(entries);children=Map.copyOf(children);}
    }
    record Manifest(int format,long generation,long through,long legacyFloor,UUID active,Ref root,Map<String,String> bundles) {
        Manifest {if(format!=FORMAT||generation<0||through<legacyFloor||legacyFloor<0||active==null||bundles==null||bundles.size()>MAX_CHUNKS)throw bad("MANIFEST");bundles=Map.copyOf(bundles);}
    }
    private final Path directory,manifestPath;
    private final EncounterBarrierArbiter barriers;
    private final EncounterPersistenceTimings timings;
    private final Consumer<FileEncounterStore.DurabilityBoundary> fault;
    private final LinkedHashMap<UUID,JsonArray> cache=new LinkedHashMap<>(4,.75f,true);
    private volatile Manifest manifest;
    EncounterCheckpointBundle(Path directory,EncounterBarrierArbiter barriers,EncounterPersistenceTimings timings,
                              Consumer<FileEncounterStore.DurabilityBoundary> fault)throws IOException {
        this.directory=directory.resolve("bundles-v2");manifestPath=directory.resolve("checkpoint-floor.json");
        this.barriers=barriers;this.timings=timings;this.fault=fault;Files.createDirectories(this.directory);
    }
    Manifest manifest(){return manifest;}
    void load()throws IOException {
        manifest=readEnvelope(manifestPath,Manifest.class,FileEncounterStore.MAX_FILE_BYTES);
        for(var entry:manifest.bundles().entrySet()){
            UUID id=UUID.fromString(entry.getKey());byte[] data=bounded(path(id),CHUNK_BYTES);
            if(!digest(data).equals(entry.getValue()))throw bad("BUNDLE_CHECKSUM");nodes(id);
        }
        validateTree(manifest.root(),0,"");
    }
    void initialize(long floor,UUID active)throws IOException {
        publish(new Manifest(FORMAT,0,floor,floor,active,null,Map.of()));
    }
    EncounterJournal.Entry lookup(EncounterJournal.Key key){
        Ref ref=manifest.root();String hash=key(key);int depth=0;
        while(ref!=null){Node node=read(ref);if(node.depth()!=depth)throw bad("INDEX_DEPTH");
            if(node.children().isEmpty())return node.entries().stream().filter(e->key(e).equals(hash)).findFirst().orElse(null);
            if(depth>=64)throw bad("INDEX_DEPTH");ref=node.children().get(hash.substring(depth,++depth));
        }return null;
    }
    /** Called only by the single checkpoint worker with immutable captured values. */
    void checkpoint(Map<EncounterJournal.Key,EncounterJournal.Entry> values,long through,UUID active)throws IOException {
        if(values.size()>EncounterJournal.CHECKPOINT_RECORDS||through<manifest.through())throw bad("EPOCH_BOUNDS");
        fault.accept(FileEncounterStore.DurabilityBoundary.BUNDLE_SERIALIZATION);
        long start=System.nanoTime();var builder=new Builder();
        Ref root=builder.update(manifest.root(),values.values().stream().sorted(Comparator.comparing(EncounterCheckpointBundle::key)).toList(),0);
        timings.record(EncounterPersistenceTimings.Phase.CHECKPOINT_SERIALIZATION,System.nanoTime()-start);
        builder.flush();
        fault.accept(FileEncounterStore.DurabilityBoundary.BUNDLES_DURABLE);
        publish(new Manifest(FORMAT,Math.addExact(manifest.generation(),1),through,manifest.legacyFloor(),active,root,builder.hashes));
    }
    private final class Builder {
        UUID id=UUID.randomUUID();JsonArray pending=new JsonArray();int bytes=64;
        final Map<String,String> hashes=new LinkedHashMap<>();
        Ref update(Ref prior,List<EncounterJournal.Entry> changes,int depth)throws IOException {
            if(changes.isEmpty())return prior;
            if(depth>64)throw bad("HASH_COLLISION");
            Node old=prior==null?new Node(depth,List.of(),Map.of()):read(prior);
            if(old.depth()!=depth)throw bad("INDEX_DEPTH");
            if(old.children().isEmpty()){
                var merged=new TreeMap<String,EncounterJournal.Entry>();for(var e:old.entries())merged.put(key(e),e);
                for(var e:changes){var previous=merged.put(key(e),e);if(previous!=null){if(e.sequence()<=previous.sequence())throw bad("ENTRY_SEQUENCE");FileEncounterStore.validateTransition(previous.snapshot(),e.snapshot());}}
                if(merged.size()<=LEAF_ENTRIES)return add(new Node(depth,List.copyOf(merged.values()),Map.of()));
                return split(Map.of(),List.copyOf(merged.values()),depth);
            }
            return split(old.children(),changes,depth);
        }
        Ref split(Map<String,Ref> prior,List<EncounterJournal.Entry> changes,int depth)throws IOException {
            if(depth>=64)throw bad("HASH_COLLISION");
            var groups=new TreeMap<String,List<EncounterJournal.Entry>>();
            for(var e:changes)groups.computeIfAbsent(key(e).substring(depth,depth+1),ignored->new ArrayList<>()).add(e);
            var children=new TreeMap<>(prior);
            for(var group:groups.entrySet())children.put(group.getKey(),update(prior.get(group.getKey()),group.getValue(),depth+1));
            return add(new Node(depth,List.of(),children));
        }
        Ref add(Node node)throws IOException {
            var json=JSON.toJsonTree(node);byte[] encoded=JSON.toJson(json).getBytes(StandardCharsets.UTF_8);
            if(encoded.length>CHUNK_BYTES/2)throw bad("INDEX_PAGE_BOUNDS");
            if(bytes+encoded.length+1>CHUNK_BYTES-256)flush();
            Ref ref=new Ref(id,pending.size(),digest(encoded));pending.add(json);bytes+=encoded.length+1;return ref;
        }
        void flush()throws IOException {
            if(pending.isEmpty())return;if(hashes.size()>=MAX_CHUNKS)throw bad("CHUNK_BOUNDS");
            var object=new JsonObject();object.addProperty("format",FORMAT);object.add("nodes",pending);
            byte[] encoded=JSON.toJson(object).getBytes(StandardCharsets.UTF_8);
            if(encoded.length>CHUNK_BYTES)throw bad("CHUNK_BOUNDS");
            Path target=path(id);long start=System.nanoTime();
            try(var out=FileChannel.open(target,CREATE_NEW,WRITE)){
                int half=encoded.length/2;write(out,ByteBuffer.wrap(encoded,0,half));fault.accept(FileEncounterStore.DurabilityBoundary.BUNDLE_PARTIAL_WRITE);
                write(out,ByteBuffer.wrap(encoded,half,encoded.length-half));fault.accept(FileEncounterStore.DurabilityBoundary.BUNDLE_BEFORE_FORCE);
                timings.record(EncounterPersistenceTimings.Phase.CHECKPOINT_WRITE,System.nanoTime()-start);
                barriers.force(out,EncounterBarrierArbiter.Kind.BUNDLE);
            }
            fault.accept(FileEncounterStore.DurabilityBoundary.BUNDLE_AFTER_FORCE);
            hashes.put(id.toString(),digest(encoded));timings.bundle(encoded.length);
            id=UUID.randomUUID();pending=new JsonArray();bytes=64;
        }
    }
    private void publish(Manifest next)throws IOException {
        Path temporary=manifestPath.resolveSibling("checkpoint-v2-"+UUID.randomUUID()+".tmp");
        byte[] encoded=envelope(next);long start=System.nanoTime();
        try(var out=FileChannel.open(temporary,CREATE_NEW,WRITE)){
            int half=encoded.length/2;write(out,ByteBuffer.wrap(encoded,0,half));fault.accept(FileEncounterStore.DurabilityBoundary.MANIFEST_PARTIAL_WRITE);
            write(out,ByteBuffer.wrap(encoded,half,encoded.length-half));fault.accept(FileEncounterStore.DurabilityBoundary.MANIFEST_BEFORE_FORCE);
            timings.record(EncounterPersistenceTimings.Phase.CHECKPOINT_WRITE,System.nanoTime()-start);
            barriers.force(out,EncounterBarrierArbiter.Kind.MANIFEST);
        }
        fault.accept(FileEncounterStore.DurabilityBoundary.MANIFEST_AFTER_FORCE);
        Files.move(temporary,manifestPath,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        manifest=next;fault.accept(FileEncounterStore.DurabilityBoundary.MANIFEST_PUBLISHED);
    }
    private void validateTree(Ref ref,int depth,String prefix){
        if(ref==null)return;Node node=read(ref);if(node.depth()!=depth)throw bad("INDEX_DEPTH");
        var keys=new HashSet<String>();
        for(var entry:node.entries())if(entry.sequence()<1||entry.sequence()>manifest.through()||!key(entry).startsWith(prefix)||!keys.add(key(entry)))throw bad("INDEX_ENTRY");
        for(var child:node.children().entrySet()){
            if(depth>=64||!child.getKey().matches("[a-f0-9]"))throw bad("INDEX_BRANCH");validateTree(child.getValue(),depth+1,prefix+child.getKey());
        }
    }
    private Node read(Ref ref){
        try{var nodes=nodes(ref.bundle());if(ref.index()>=nodes.size())throw bad("INDEX_OFFSET");var json=nodes.get(ref.index());
            if(!RewardIntent.digest(JSON.toJson(json)).equals(ref.hash()))throw bad("ENTRY_CHECKSUM");
            Node node=JSON.fromJson(json,Node.class);if(!JSON.toJsonTree(node).equals(json))throw bad("ENTRY_SHAPE");return node;
        }catch(IOException e){throw new IllegalStateException("ENCOUNTER_BUNDLE_UNREADABLE",e);}
    }
    private synchronized JsonArray nodes(UUID id)throws IOException {
        var found=cache.get(id);if(found!=null)return found;
        var object=JsonParser.parseString(new String(bounded(path(id),CHUNK_BYTES),StandardCharsets.UTF_8)).getAsJsonObject();
        if(object.size()!=2||object.get("format").getAsInt()!=FORMAT)throw bad("FORMAT");var nodes=object.getAsJsonArray("nodes");
        if(nodes.isEmpty()||nodes.size()>EncounterJournal.CHECKPOINT_RECORDS*65)throw bad("INDEX_BOUNDS");
        if(cache.size()>=4)cache.remove(cache.keySet().iterator().next());cache.put(id,nodes);return nodes;
    }
    private Path path(UUID id){return directory.resolve(id+".bundle");}
    static String key(EncounterJournal.Key key){return RewardIntent.digest(key.world()+"/"+key.enemy());}
    private static String key(EncounterJournal.Entry entry){return key(new EncounterJournal.Key(entry.snapshot().spawn().world(),entry.snapshot().spawn().enemy()));}
    static byte[] envelope(Object value){var object=new JsonObject();object.addProperty("schema",FORMAT);var payload=JSON.toJsonTree(value);object.add("payload",payload);object.addProperty("checksum",RewardIntent.digest(JSON.toJson(payload)));return JSON.toJson(object).getBytes(StandardCharsets.UTF_8);}
    static <T> T readEnvelope(Path path,Class<T> type,int bound)throws IOException {
        var json=JsonParser.parseString(new String(bounded(path,bound),StandardCharsets.UTF_8)).getAsJsonObject();
        if(json.size()!=3||json.get("schema").getAsInt()!=FORMAT||!RewardIntent.digest(JSON.toJson(json.get("payload"))).equals(json.get("checksum").getAsString()))throw bad("MANIFEST_CHECKSUM");
        T value=JSON.fromJson(json.get("payload"),type);if(value==null||!JSON.toJsonTree(value).equals(json.get("payload")))throw bad("MANIFEST_SHAPE");return value;
    }
    static byte[] bounded(Path path,int max)throws IOException {long size=Files.size(path);if(size<1||size>max)throw bad("FILE_BOUNDS");return Files.readAllBytes(path);}
    static String digest(byte[] bytes){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    static void write(FileChannel channel,ByteBuffer buffer)throws IOException {while(buffer.hasRemaining())channel.write(buffer);}
    static IllegalStateException bad(String detail){return new IllegalStateException("ENCOUNTER_BUNDLE_"+detail);}
}
