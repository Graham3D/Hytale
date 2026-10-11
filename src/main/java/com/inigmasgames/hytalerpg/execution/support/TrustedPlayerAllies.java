package com.inigmasgames.hytalerpg.execution.support;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Small server-owned mutual consent authority for Shared Aegis player recipients.
 * Identity is always the authenticated player's UUID. A revoked pair gets a new
 * generation if consent is later renewed, so old grants cannot revive. */
public final class TrustedPlayerAllies {
    private static final Gson JSON=new Gson();
    private static final int SCHEMA=1,MAX_RECORDS=100_000,MAX_PEERS=64;
    private record Directed(UUID from,UUID to){}
    private record Pair(UUID first,UUID second){
        Pair {if(first.toString().compareTo(second.toString())>0){UUID old=first;first=second;second=old;}}
    }
    private record Record(int schema,long revision,String action,UUID actor,UUID other,UUID generation){}
    private final Path file;
    private final Set<Directed> pending=new HashSet<>();
    private final Map<Pair,UUID> active=new HashMap<>();
    private volatile Map<Pair,UUID> published=Map.of();
    private volatile long revision;
    private volatile boolean poisoned;
    public TrustedPlayerAllies(Path pluginDirectory){
        file=Objects.requireNonNull(pluginDirectory).toAbsolutePath().normalize().resolve("trusted-player-allies.journal");
        try{replay();}catch(IOException|RuntimeException failure){poisoned=true;}
    }
    private static String digest(String body){
        try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(body.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private void replay() throws IOException {
        if(!Files.exists(file))return;
        if(Files.size(file)>20_000_000)throw new IllegalStateException("TRUST_JOURNAL_OVERSIZE");
        String content=Files.readString(file,StandardCharsets.UTF_8);
        if(!content.endsWith("\n"))throw new IllegalStateException("TRUST_JOURNAL_TORN");
        for(String raw:content.split("\n")){
            int cut=raw.lastIndexOf('|');
            if(cut<0||!digest(raw.substring(0,cut)).equals(raw.substring(cut+1)))
                throw new IllegalStateException("TRUST_JOURNAL_CHECKSUM_INVALID");
            Record record=JSON.fromJson(raw.substring(0,cut),Record.class);
            if(record==null||record.schema()!=SCHEMA||record.revision()!=revision+1
                    ||record.actor()==null||record.other()==null||record.actor().equals(record.other()))
                throw new IllegalStateException("TRUST_JOURNAL_RECORD_INVALID");
            apply(record);revision=record.revision();
        }
        published=Map.copyOf(active);
    }
    private void apply(Record record){
        var directed=new Directed(record.actor(),record.other());
        var pair=new Pair(record.actor(),record.other());
        switch(record.action()){
            case "REQUEST" -> {if(record.generation()!=null||active.containsKey(pair)||!pending.add(directed))
                    throw new IllegalStateException("TRUST_REQUEST_REPLAY_INVALID");}
            case "ACCEPT" -> {if(record.generation()==null||!pending.remove(new Directed(record.other(),record.actor()))
                    ||active.containsKey(pair))throw new IllegalStateException("TRUST_ACCEPT_REPLAY_INVALID");
                pending.remove(directed);active.put(pair,record.generation());}
            case "REVOKE" -> {if(record.generation()!=null)throw new IllegalStateException("TRUST_REVOKE_RECORD_INVALID");
                pending.remove(directed);pending.remove(new Directed(record.other(),record.actor()));active.remove(pair);}
            default -> throw new IllegalStateException("TRUST_ACTION_INVALID");
        }
    }
    private void append(Record record){
        if(poisoned)throw new IllegalStateException("TRUST_AUTHORITY_UNAVAILABLE");
        if(revision>=MAX_RECORDS)throw new IllegalStateException("TRUST_JOURNAL_BUDGET");
        String body=JSON.toJson(record);
        byte[] bytes=(body+"|"+digest(body)+"\n").getBytes(StandardCharsets.UTF_8);
        try{
            Files.createDirectories(Objects.requireNonNull(file.getParent()));
            try(FileChannel channel=FileChannel.open(file,StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.APPEND)){
                ByteBuffer data=ByteBuffer.wrap(bytes);while(data.hasRemaining())channel.write(data);
                channel.force(true);
            }
            apply(record);revision=record.revision();published=Map.copyOf(active);
        }catch(IOException|RuntimeException failure){poisoned=true;throw new IllegalStateException("TRUST_JOURNAL_WRITE_FAILED",failure);}
    }
    private int peers(UUID actor){
        return (int)active.keySet().stream().filter(p->p.first().equals(actor)||p.second().equals(actor)).count()
                +(int)pending.stream().filter(p->p.from().equals(actor)||p.to().equals(actor)).count();
    }
    public synchronized boolean request(UUID actor,UUID other){
        validate(actor,other);
        if(active.containsKey(new Pair(actor,other))||pending.contains(new Directed(actor,other)))return false;
        if(peers(actor)>=MAX_PEERS||peers(other)>=MAX_PEERS)throw new IllegalStateException("TRUST_PEER_BUDGET");
        append(new Record(SCHEMA,revision+1,"REQUEST",actor,other,null));return true;
    }
    public synchronized boolean accept(UUID actor,UUID requester){
        validate(actor,requester);
        if(active.containsKey(new Pair(actor,requester)))return false;
        if(!pending.contains(new Directed(requester,actor)))throw new IllegalStateException("TRUST_REQUEST_MISSING");
        append(new Record(SCHEMA,revision+1,"ACCEPT",actor,requester,UUID.randomUUID()));return true;
    }
    public synchronized boolean revoke(UUID actor,UUID other){
        validate(actor,other);
        if(!active.containsKey(new Pair(actor,other))&&!pending.contains(new Directed(actor,other))
                &&!pending.contains(new Directed(other,actor)))return false;
        append(new Record(SCHEMA,revision+1,"REVOKE",actor,other,null));return true;
    }
    public UUID generation(UUID actor,UUID other){
        if(actor==null||other==null||actor.equals(other)||poisoned)return null;
        return published.get(new Pair(actor,other));
    }
    public boolean trusted(UUID actor,UUID other){return generation(actor,other)!=null;}
    public synchronized Set<UUID> pendingFor(UUID actor){
        if(poisoned)return Set.of();
        var ids=new HashSet<UUID>();for(var request:pending)if(request.to().equals(actor))ids.add(request.from());
        return Set.copyOf(ids);
    }
    public Set<UUID> trustedFor(UUID actor){
        if(actor==null||poisoned)return Set.of();
        var ids=new HashSet<UUID>();
        for(var pair:published.keySet()){
            if(pair.first().equals(actor))ids.add(pair.second());
            else if(pair.second().equals(actor))ids.add(pair.first());
        }
        return Set.copyOf(ids);
    }
    private static void validate(UUID actor,UUID other){
        if(actor==null||other==null||actor.equals(other))throw new IllegalArgumentException("TRUST_IDENTITIES_INVALID");
    }
}
