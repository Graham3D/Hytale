package com.inigmasgames.hytalerpg.execution.support;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Frozen Reflective Ward behavior and write-before-effect secondary claims.
 * A missing or damaged journal forfeits reflection and the parent barrier on recovery.
 * Claims are never replayed into native damage after a crash. */
public final class CycloneReflectionJournal {
    private static final Gson JSON=new Gson();
    private static final int SCHEMA=1, MAX_CLAIMS=16, MAX_BYTES=16_384;
    public record Behavior(int schema,UUID player,UUID generation,UUID activation,
            String rootCastId,String skillInstanceId,String correlationId,double coefficient,double capacity){
        public Behavior {
            Objects.requireNonNull(player);Objects.requireNonNull(generation);Objects.requireNonNull(activation);
            validId(rootCastId);validId(skillInstanceId);validId(correlationId);
            if(schema!=SCHEMA||coefficient!=.2||!Double.isFinite(capacity)||capacity<=0)
                throw new IllegalArgumentException("CYCLONE_REFLECTION_BEHAVIOR_INVALID");
        }
        public boolean matches(CycloneBarrierLedger.State state){
            return state!=null&&player.equals(state.player())&&generation.equals(state.generation())
                    &&activation.equals(state.activation())&&capacity==state.capacity();
        }
    }
    private record Frame(int schema,String kind,Behavior behavior,String receipt) {}
    private static final class Holder {
        Behavior behavior;final HashSet<String> claims=new HashSet<>();boolean loaded,poisoned;
    }
    private final Path root;
    private final ConcurrentHashMap<UUID,Holder> holders=new ConcurrentHashMap<>();
    public record Metrics(long forcedClaims,long forceNanos,long claimLockWaitNanos){}
    private final java.util.concurrent.atomic.LongAdder forcedClaims=new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder forceNanos=new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder claimLockWaitNanos=new java.util.concurrent.atomic.LongAdder();
    public Metrics metrics(){return new Metrics(forcedClaims.sum(),forceNanos.sum(),claimLockWaitNanos.sum());}
    public CycloneReflectionJournal(Path barrierDirectory){root=barrierDirectory.resolve("reflective-ward").toAbsolutePath().normalize();}
    private static void validId(String id){
        if(id==null||id.isBlank()||id.length()>160)throw new IllegalArgumentException("CYCLONE_REFLECTION_ID_INVALID");
    }
    private Path path(UUID player){return root.resolve(player+".journal");}
    private static String checksum(String content){
        try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(content.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static byte[] line(Frame frame){
        String json=JSON.toJson(frame);return (json+"|"+checksum(json)+"\n").getBytes(StandardCharsets.UTF_8);
    }
    private static void write(FileChannel channel,byte[] bytes) throws IOException {
        var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);
        channel.force(true);
    }
    /** Called on ReadyPath's preload worker, never from the native damage callback. */
    public void preload(UUID player){
        Holder holder=holders.computeIfAbsent(player,id->new Holder());
        synchronized(holder){
            if(holder.loaded)return;
            try{read(player,holder);}catch(RuntimeException|IOException invalid){holder.poisoned=true;}
            holder.loaded=true;
        }
    }
    private void read(UUID player,Holder holder) throws IOException {
        Path file=path(player);if(!Files.exists(file))return;
        if(Files.size(file)>MAX_BYTES)throw new IllegalStateException("CYCLONE_REFLECTION_SIZE_INVALID");
        String content=Files.readString(file,StandardCharsets.UTF_8);
        if(!content.endsWith("\n"))throw new IllegalStateException("CYCLONE_REFLECTION_TORN");
        for(String raw:content.split("\n")){
            int cut=raw.lastIndexOf('|');
            if(cut<0||!checksum(raw.substring(0,cut)).equals(raw.substring(cut+1)))
                throw new IllegalStateException("CYCLONE_REFLECTION_CHECKSUM_INVALID");
            Frame frame=JSON.fromJson(raw.substring(0,cut),Frame.class);
            if(frame==null||frame.schema()!=SCHEMA)throw new IllegalStateException("CYCLONE_REFLECTION_SCHEMA_INVALID");
            if("INIT".equals(frame.kind())&&holder.behavior==null&&frame.receipt()==null){
                var decoded=Objects.requireNonNull(frame.behavior());
                holder.behavior=new Behavior(decoded.schema(),decoded.player(),decoded.generation(),
                        decoded.activation(),decoded.rootCastId(),decoded.skillInstanceId(),
                        decoded.correlationId(),decoded.coefficient(),decoded.capacity());
                if(!player.equals(holder.behavior.player()))throw new IllegalStateException("CYCLONE_REFLECTION_OWNER_INVALID");
            }else if("CLAIM".equals(frame.kind())&&holder.behavior!=null&&frame.behavior()==null){
                validId(frame.receipt());
                if(!holder.claims.add(frame.receipt())||holder.claims.size()>MAX_CLAIMS)
                    throw new IllegalStateException("CYCLONE_REFLECTION_CLAIM_INVALID");
            }else throw new IllegalStateException("CYCLONE_REFLECTION_RECORD_INVALID");
        }
        if(holder.behavior==null)throw new IllegalStateException("CYCLONE_REFLECTION_INIT_MISSING");
    }
    /** Prepare before the parent is forced ACTIVE. Failed writes cannot publish a paid barrier. */
    public void install(Behavior behavior){
        Holder holder=holders.computeIfAbsent(behavior.player(),id->new Holder());
        synchronized(holder){
            if(holder.behavior!=null&&holder.behavior.generation().equals(behavior.generation())){
                if(!holder.poisoned&&holder.behavior.equals(behavior))return;
                throw new IllegalStateException("CYCLONE_REFLECTION_GENERATION_CONFLICT");
            }
            try{
                Files.createDirectories(root);
                Path temporary=root.resolve(behavior.player()+"."+UUID.randomUUID()+".tmp");
                try{
                    try(FileChannel channel=FileChannel.open(temporary,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
                        write(channel,line(new Frame(SCHEMA,"INIT",behavior,null)));
                    }
                    Files.move(temporary,path(behavior.player()),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                }finally{Files.deleteIfExists(temporary);}
                holder.behavior=behavior;holder.claims.clear();holder.loaded=true;holder.poisoned=false;
            }catch(IOException failure){holder.poisoned=true;throw new IllegalStateException("CYCLONE_REFLECTION_INSTALL_FAILED",failure);}
        }
    }
    /** Returns only the exact frozen behavior for the paid parent generation. */
    public Behavior load(CycloneBarrierLedger.State state){
        Holder holder=holders.get(state.player());if(holder==null)return null;
        synchronized(holder){return holder.loaded&&!holder.poisoned&&holder.behavior!=null
                &&holder.behavior.matches(state)?holder.behavior:null;}
    }
    /** The receipt is forced before native reflected damage. A claimed but unapplied hit is forfeited. */
    public boolean claim(Behavior behavior,String receipt){
        validId(receipt);
        Holder holder=holders.get(behavior.player());if(holder==null)return false;
        long waitingSince=System.nanoTime();
        synchronized(holder){
            claimLockWaitNanos.add(System.nanoTime()-waitingSince);
            if(holder.poisoned||!behavior.equals(holder.behavior)||holder.claims.contains(receipt)
                    ||holder.claims.size()>=MAX_CLAIMS)return false;
            long started=System.nanoTime();
            try(FileChannel channel=FileChannel.open(path(behavior.player()),StandardOpenOption.WRITE,StandardOpenOption.APPEND)){
                if(channel.size()+line(new Frame(SCHEMA,"CLAIM",null,receipt)).length>MAX_BYTES)
                    throw new IllegalStateException("CYCLONE_REFLECTION_BUDGET");
                write(channel,line(new Frame(SCHEMA,"CLAIM",null,receipt)));
                forceNanos.add(System.nanoTime()-started);forcedClaims.increment();
                holder.claims.add(receipt);return true;
            }catch(IOException|RuntimeException failure){holder.poisoned=true;
                throw new IllegalStateException("CYCLONE_REFLECTION_CLAIM_FAILED",failure);}
        }
    }
}
