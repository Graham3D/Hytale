package com.inigmasgames.hytalerpg.difficulty;

import com.google.gson.Gson;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Small atomic, checksummed control records. Never stores native inventory or player resources. */
public final class DifficultyStorage<T> {
    private record Envelope(int schema,String sha256,String payload){}
    private final Path path;private final Class<T> type;private final Gson gson=new Gson();
    public DifficultyStorage(Path path,Class<T> type){this.path=path;this.type=type;}
    public synchronized Optional<T> read(){try{
        if(!Files.exists(path))return Optional.empty();
        var e=gson.fromJson(Files.readString(path),Envelope.class);
        if(e.schema()!=1||!hash(e.payload()).equals(e.sha256()))throw new IllegalStateException("DIFFICULTY_CONTROL_CHECKSUM");
        return Optional.of(gson.fromJson(e.payload(),type));
    }catch(Exception e){throw new IllegalStateException("DIFFICULTY_CONTROL_READ_FAILED:"+path,e);}}
    public synchronized void write(T value){try{
        String payload=gson.toJson(value);byte[] bytes=gson.toJson(new Envelope(1,hash(payload),payload)).getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(path.toAbsolutePath().getParent());var tmp=path.resolveSibling(path.getFileName()+".tmp");
        try(var channel=FileChannel.open(tmp,StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){
            var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
        }
        Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }catch(Exception e){throw new IllegalStateException("DIFFICULTY_CONTROL_SAVE_FAILED:"+path,e);}}
    private static String hash(String text)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
}
