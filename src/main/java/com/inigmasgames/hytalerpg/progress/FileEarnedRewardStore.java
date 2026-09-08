package com.inigmasgames.hytalerpg.progress;

import com.google.gson.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import static java.nio.file.StandardOpenOption.*;

/**
 * One pending intent/player, O(1) disk lookups and bounded memory. Receipts are immutable,
 * sharded by event hash and never evicted. A coordinated player+receipt backup is mandatory.
 * Forced file content plus required atomic replacement supports process-crash recovery;
 * filesystem/whole-volume power-loss durability is not claimed.
 */
public final class FileEarnedRewardStore implements EarnedRewardStore {
    public enum Boundary { AFTER_INTENT, AFTER_PLAYER, AFTER_RECEIPT, AFTER_HEAD, AFTER_CLEANUP }
    private record Head(long sequence,String hash){
        static final Head INITIAL=new Head(0,"");
        Head {new RewardLedger(sequence,hash,0);}
        boolean matches(RewardLedger ledger){return sequence==ledger.sequence()&&hash.equals(ledger.lastReceiptHash());}
        static Head of(RewardLedger ledger){return new Head(ledger.sequence(),ledger.lastReceiptHash());}
    }
    private final Path directory;
    private final Consumer<Boundary> fault;
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    private static final long MAX_FILE_BYTES=65536;
    public FileEarnedRewardStore(Path directory){this(directory,ignored->{});}
    /** Test-only fault injection: production uses the one-argument constructor. */
    public FileEarnedRewardStore(Path directory,Consumer<Boundary> fault){
        this.directory=directory.toAbsolutePath().normalize();this.fault=Objects.requireNonNull(fault);
    }
    @Override public Result award(UUID player,EarnedReward reward,Authority authority){
        Objects.requireNonNull(reward);return locked(player,folder->{
            boolean recovered=recoverLocked(player,folder,authority);
            Path receipt=receipt(folder,reward.eventId());
            if(Files.exists(receipt)){
                RewardIntent previous=read(receipt,RewardIntent.class);validatePlayer(previous,player);
                if(!previous.reward().equals(reward))throw new IllegalStateException("REWARD_EVENT_PAYLOAD_CONFLICT");
                Head head=head(folder);
                if(previous.after().ledger().sequence()>head.sequence())throw new IllegalStateException("REWARD_RECEIPT_AHEAD_OF_HEAD");
                return new Result(Outcome.DUPLICATE,previous.after().ledger().sequence(),previous.hash(),recovered);
            }
            RewardIntent intent=RewardIntent.create(player,reward,authority.current());
            write(folder.resolve("pending.json"),intent,false);
            fault.accept(Boundary.AFTER_INTENT);
            finish(folder,intent,authority);
            return new Result(Outcome.COMMITTED,intent.after().ledger().sequence(),intent.hash(),recovered);
        });
    }
    @Override public boolean recover(UUID player,Authority authority){return locked(player,folder->recoverLocked(player,folder,authority));}
    private boolean recoverLocked(UUID player,Path folder,Authority authority){
        Head head=head(folder);Path pending=folder.resolve("pending.json");
        if(!Files.exists(pending)){
            if(!head.matches(authority.current().ledger()))throw new IllegalStateException("REWARD_PLAYER_HEAD_MISMATCH: restore coordinated backup, not one side");
            return false;
        }
        RewardIntent intent=read(pending,RewardIntent.class);validatePlayer(intent,player);
        if(!head.matches(intent.before().ledger())&&!head.matches(intent.after().ledger()))
            throw new IllegalStateException("REWARD_PENDING_HEAD_MISMATCH");
        if(head.matches(intent.after().ledger())&&!authority.current().equals(intent.after()))
            throw new IllegalStateException("REWARD_COMMITTED_PLAYER_ROLLBACK");
        finish(folder,intent,authority);return true;
    }
    private void finish(Path folder,RewardIntent intent,Authority authority){
        RewardCheckpoint current=authority.current(),after=intent.after();
        if(current.equals(intent.before()))authority.commit(intent);
        else if(!current.equals(after))throw new IllegalStateException("REWARD_PENDING_PLAYER_MISMATCH");
        if(!authority.current().equals(after))throw new IllegalStateException("REWARD_AUTHORITY_DID_NOT_COMMIT");
        fault.accept(Boundary.AFTER_PLAYER);
        Path receipt=receipt(folder,intent.reward().eventId());
        if(Files.exists(receipt)){
            if(!read(receipt,RewardIntent.class).equals(intent))throw new IllegalStateException("REWARD_RECEIPT_CONFLICT");
        }else write(receipt,intent,false);
        fault.accept(Boundary.AFTER_RECEIPT);
        write(folder.resolve("head.json"),Head.of(after.ledger()),true);
        fault.accept(Boundary.AFTER_HEAD);
        try{Files.delete(folder.resolve("pending.json"));}catch(IOException error){throw failure("REWARD_PENDING_CLEANUP_FAILED",error);}
        fault.accept(Boundary.AFTER_CLEANUP);
    }
    private Head head(Path folder){Path path=folder.resolve("head.json");return Files.exists(path)?read(path,Head.class):Head.INITIAL;}
    private static Path receipt(Path folder,String eventId){String key=RewardIntent.digest(eventId);return folder.resolve("receipts").resolve(key.substring(0,2)).resolve(key+".json");}
    private static void validatePlayer(RewardIntent intent,UUID player){if(!intent.player().equals(player))throw new IllegalStateException("REWARD_PLAYER_ID_MISMATCH");}
    private <T> T locked(UUID player,Function<Path,T> operation){
        Objects.requireNonNull(player);Path folder=directory.resolve(player.toString());
        try{
            Files.createDirectories(folder);
            try(var channel=FileChannel.open(folder.resolve("writer.lock"),CREATE,WRITE);var lock=channel.tryLock()){
                if(lock==null)throw new IllegalStateException("REWARD_WRITER_BUSY");
                return operation.apply(folder);
            }
        }catch(IOException|java.nio.channels.OverlappingFileLockException error){throw failure("REWARD_STORE_UNAVAILABLE",error);}
    }
    private static <T> T read(Path path,Class<T> type){
        try{
            if(!Files.isRegularFile(path)||Files.size(path)>MAX_FILE_BYTES)throw new IllegalStateException("REWARD_FILE_BOUNDS");
            var envelope=JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();
            if(envelope.size()!=3||envelope.get("schema").getAsInt()!=1)throw new IllegalStateException("REWARD_FILE_SCHEMA");
            JsonElement payload=envelope.get("payload");
            if(!RewardIntent.digest(JSON.toJson(payload)).equals(envelope.get("checksum").getAsString()))throw new IllegalStateException("REWARD_FILE_CHECKSUM");
            T value=JSON.fromJson(payload,type);
            if(value==null||!JSON.toJsonTree(value).equals(payload))throw new IllegalStateException("REWARD_FILE_SHAPE");
            return value;
        }catch(IOException|RuntimeException error){throw failure("REWARD_FILE_UNREADABLE: "+path,error);}
    }
    private static void write(Path path,Object value,boolean replace){
        Path temporary=path.resolveSibling(path.getFileName()+"."+UUID.randomUUID()+".tmp");
        try{
            Files.createDirectories(path.getParent());
            if(!replace&&Files.exists(path))throw new IllegalStateException("REWARD_IMMUTABLE_FILE_EXISTS");
            JsonElement payload=JSON.toJsonTree(value);JsonObject envelope=new JsonObject();
            envelope.addProperty("schema",1);envelope.addProperty("checksum",RewardIntent.digest(JSON.toJson(payload)));envelope.add("payload",payload);
            byte[] bytes=JSON.toJson(envelope).getBytes(StandardCharsets.UTF_8);
            if(bytes.length>MAX_FILE_BYTES)throw new IllegalStateException("REWARD_FILE_BOUNDS");
            try(var channel=FileChannel.open(temporary,CREATE_NEW,WRITE)){
                var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
            }
            // No non-atomic fallback: a refused award is safer than a partially replaced authority.
            if(replace)Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            else Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE);
        }catch(IOException error){throw failure("REWARD_WRITE_FAILED",error);}
        finally{try{Files.deleteIfExists(temporary);}catch(IOException ignored){/* Unique orphan temp is never replayed. */}}
    }
    private static IllegalStateException failure(String message,Exception cause){return new IllegalStateException(message,cause);}
}
