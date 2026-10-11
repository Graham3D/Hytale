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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** A forced parent intent admits at most one independently debited recipient barrier.
 * Parent payment must be proven again from the loaded native caster Mana record
 * before a recipient's surviving child is reattached. */
public final class CycloneSharedAegisLedger {
    private static final Gson JSON=new Gson();
    private static final int SCHEMA=1,MAX_CHILDREN=32,MAX_ARCHIVE_FILES=100_000,MAX_RECEIPTS=100_000;
    public record Grant(int schema,UUID caster,UUID recipient,UUID parentGeneration,
            UUID childGeneration,double capacity,UUID trustGeneration){
        public Grant {
            Objects.requireNonNull(caster);Objects.requireNonNull(recipient);
            Objects.requireNonNull(parentGeneration);Objects.requireNonNull(childGeneration);
            if(schema!=SCHEMA||caster.equals(recipient)||!Double.isFinite(capacity)||capacity<=0||capacity>Float.MAX_VALUE)
                throw new IllegalArgumentException("CYCLONE_SHARED_GRANT_INVALID");
        }
    }
    public record Lease(UUID recipient,UUID session){}
    public record View(UUID generation,long revision,double remaining,double capacity){}
    public record Debit(double absorbed,float nativeRemainder,boolean duplicate){}
    @FunctionalInterface public interface PaymentProof {boolean confirms(UUID caster,UUID generation);}
    private record Frame(int schema,String kind,Grant grant,long revision,double remaining,String receipt){}
    private static final class Child {
        Grant grant;long revision;double remaining;boolean poisoned,authorized;
        final HashSet<String> receipts=new HashSet<>();
        Child(Grant grant){this.grant=grant;remaining=grant.capacity();}
    }
    private static final class Recipient {
        UUID session;boolean loaded;final ArrayList<Child> children=new ArrayList<>();
    }
    private final Path root;
    private final ConcurrentHashMap<UUID,Recipient> recipients=new ConcurrentHashMap<>();
    public record Metrics(long forcedWrites,long forcedWriteNanos,long debitLockWaitNanos){}
    private final java.util.concurrent.atomic.LongAdder forcedWrites=new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder forcedWriteNanos=new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder debitLockWaitNanos=new java.util.concurrent.atomic.LongAdder();
    public Metrics metrics(){return new Metrics(forcedWrites.sum(),forcedWriteNanos.sum(),debitLockWaitNanos.sum());}
    public CycloneSharedAegisLedger(Path barrierDirectory){root=barrierDirectory.resolve("shared-aegis").toAbsolutePath().normalize();}
    private Path intent(Grant grant){return root.resolve("intent").resolve(grant.parentGeneration()+".grant");}
    public boolean hasUnspentGrant(UUID parentGeneration){
        Path file=root.resolve("intent").resolve(parentGeneration+".grant");
        if(!Files.exists(file))return false;
        try{
            Intent state=readIntentState(file);
            if(state.closed())return false;
            Grant grant=state.grant();
            Path childFile=child(grant);
            return Files.exists(childFile)&&readChild(childFile,grant.recipient()).remaining>0;
        }catch(IOException|RuntimeException uncertain){return true;}
    }
    private Path children(UUID recipient){return root.resolve("children").resolve(recipient.toString());}
    private Path child(Grant grant){return children(grant.recipient()).resolve(grant.parentGeneration()+".journal");}
    private static String digest(String text){
        try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static byte[] line(Frame frame){
        String body=JSON.toJson(frame);return (body+"|"+digest(body)+"\n").getBytes(StandardCharsets.UTF_8);
    }
    private static Frame parse(String line){
        int separator=line.lastIndexOf('|');
        if(separator<0||!digest(line.substring(0,separator)).equals(line.substring(separator+1)))
            throw new IllegalStateException("CYCLONE_SHARED_CHECKSUM_INVALID");
        Frame frame=JSON.fromJson(line.substring(0,separator),Frame.class);
        if(frame==null||frame.schema()!=SCHEMA)throw new IllegalStateException("CYCLONE_SHARED_SCHEMA_INVALID");
        return frame;
    }
    private static Grant checked(Grant decoded){
        return new Grant(decoded.schema(),decoded.caster(),decoded.recipient(),
                decoded.parentGeneration(),decoded.childGeneration(),decoded.capacity(),decoded.trustGeneration());
    }
    private void force(Path file,Frame frame,boolean create) throws IOException {
        long started=System.nanoTime();
        var options=create?new StandardOpenOption[]{StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE}
                :new StandardOpenOption[]{StandardOpenOption.WRITE,StandardOpenOption.APPEND};
        try(FileChannel channel=FileChannel.open(file,options)){
            ByteBuffer bytes=ByteBuffer.wrap(line(frame));while(bytes.hasRemaining())channel.write(bytes);
            channel.force(true);
            forcedWrites.increment();
        }finally{
            forcedWriteNanos.add(System.nanoTime()-started);
        }
    }
    private record Intent(Grant grant,boolean closed){}
    private Intent readIntentState(Path file) throws IOException {
        String content=Files.readString(file,StandardCharsets.UTF_8);
        if(!content.endsWith("\n"))
            throw new IllegalStateException("CYCLONE_SHARED_INTENT_TORN");
        String[] lines=content.split("\n");
        if(lines.length<1||lines.length>2)throw new IllegalStateException("CYCLONE_SHARED_INTENT_LENGTH");
        Frame frame=parse(lines[0]);
        if(!"INTENT".equals(frame.kind())||frame.grant()==null||frame.revision()!=0||frame.remaining()!=0||frame.receipt()!=null)
            throw new IllegalStateException("CYCLONE_SHARED_INTENT_INVALID");
        Grant grant=checked(frame.grant());
        if(lines.length==2){
            Frame closed=parse(lines[1]);
            if(!"CLOSED".equals(closed.kind())||closed.grant()!=null||closed.revision()!=1
                    ||closed.remaining()!=0||closed.receipt()!=null)
                throw new IllegalStateException("CYCLONE_SHARED_INTENT_CLOSE_INVALID");
        }
        return new Intent(grant,lines.length==2);
    }
    private Grant readIntent(Path file) throws IOException {return readIntentState(file).grant();}
    private Child readChild(Path file,UUID recipient) throws IOException {
        if(Files.size(file)>20_000_000)throw new IllegalStateException("CYCLONE_SHARED_CHILD_OVERSIZE");
        String content=Files.readString(file,StandardCharsets.UTF_8);
        if(!content.endsWith("\n"))throw new IllegalStateException("CYCLONE_SHARED_CHILD_TORN");
        Child result=null;
        for(String raw:content.split("\n")){
            Frame frame=parse(raw);
            if("INIT".equals(frame.kind())&&result==null&&frame.grant()!=null&&frame.revision()==0&&frame.receipt()==null){
                Grant grant=checked(frame.grant());
                if(!recipient.equals(grant.recipient())||!file.equals(child(grant))||frame.remaining()!=grant.capacity())
                    throw new IllegalStateException("CYCLONE_SHARED_CHILD_IDENTITY_INVALID");
                result=new Child(grant);
            }else if(result!=null&&frame.grant()==null&&frame.revision()==result.revision+1){
                if("DEBIT".equals(frame.kind())&&frame.receipt()!=null&&!frame.receipt().isBlank()
                        &&frame.receipt().length()<=64&&frame.remaining()>=0&&frame.remaining()<result.remaining
                        &&result.receipts.add(frame.receipt())&&result.receipts.size()<=MAX_RECEIPTS){
                    result.remaining=frame.remaining();result.revision=frame.revision();
                }else if("FORFEIT".equals(frame.kind())&&frame.receipt()==null&&frame.remaining()==0){
                    result.remaining=0;result.revision=frame.revision();
                }else throw new IllegalStateException("CYCLONE_SHARED_CHILD_TRANSITION_INVALID");
            }else throw new IllegalStateException("CYCLONE_SHARED_CHILD_RECORD_INVALID");
        }
        if(result==null||!result.grant.equals(readIntent(intent(result.grant)))
                ||readIntentState(intent(result.grant)).closed()&&result.remaining>0)
            throw new IllegalStateException("CYCLONE_SHARED_PARENT_INTENT_MISSING");
        return result;
    }
    /** Worker-only native payment preload inventory. No child is authorized here. */
    public List<Grant> childGrants(UUID recipient){
        Path directory=children(recipient);
        if(!Files.isDirectory(directory))return List.of();
        try(var files=Files.list(directory)){
            List<Path> paths=files.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".journal"))
                    .sorted().toList();
            if(paths.size()>MAX_ARCHIVE_FILES)throw new IllegalStateException("CYCLONE_SHARED_RECIPIENT_BUDGET");
            var grants=new ArrayList<Grant>();
            for(Path file:paths)try{
                var child=readChild(file,recipient);if(child.remaining>0)grants.add(child.grant);
            }catch(IOException|RuntimeException corrupt){reportQuarantine(recipient,file,corrupt);}
            if(grants.size()>MAX_CHILDREN)throw new IllegalStateException("CYCLONE_SHARED_ACTIVE_BUDGET");
            return List.copyOf(grants);
        }catch(IOException failure){throw new IllegalStateException("CYCLONE_SHARED_INVENTORY_FAILED",failure);}
    }
    /** Called on ReadyPath's worker. Missing native payment proof durably forfeits
     * only that child; no caster or recipient Mana is charged again. */
    public void preload(UUID recipient,PaymentProof proof){preload(recipient,proof,grant->true);}
    public void preload(UUID recipient,PaymentProof proof,java.util.function.Predicate<Grant> eligible){
        Objects.requireNonNull(proof);
        Recipient holder=recipients.computeIfAbsent(recipient,id->new Recipient());
        synchronized(holder){
            if(holder.session!=null)return;
            Path directory=children(recipient);
            holder.children.clear();
            if(Files.isDirectory(directory))try(var files=Files.list(directory)){
                List<Path> paths=files.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".journal"))
                        .sorted().toList();
                if(paths.size()>MAX_ARCHIVE_FILES)throw new IllegalStateException("CYCLONE_SHARED_RECIPIENT_BUDGET");
                for(Path file:paths)try{
                    Child child=readChild(file,recipient);
                    if(child.remaining<=0){closeDepleted(child);continue;}
                    if(holder.children.size()>=MAX_CHILDREN)throw new IllegalStateException("CYCLONE_SHARED_ACTIVE_BUDGET");
                    if(child.remaining>0){
                        boolean confirmed;
                        try{confirmed=proof.confirms(child.grant.caster(),child.grant.parentGeneration());}
                        catch(RuntimeException unavailable){confirmed=false;}
                        boolean admissible=false;
                        try{admissible=confirmed&&eligible.test(child.grant);}catch(RuntimeException ignored){/* fail closed */}
                        if(admissible)child.authorized=true;
                        else try{forfeit(child);}catch(RuntimeException failed){child.poisoned=true;}
                    }
                    if(child.remaining>0)holder.children.add(child);
                }catch(IOException|RuntimeException corrupt){reportQuarantine(recipient,file,corrupt);}
            }catch(IOException failure){throw new IllegalStateException("CYCLONE_SHARED_PRELOAD_FAILED",failure);}
            holder.loaded=true;
        }
    }
    private static void reportQuarantine(UUID recipient,Path file,Exception failure){
        java.util.logging.Logger.getLogger(CycloneSharedAegisLedger.class.getName()).log(
                java.util.logging.Level.WARNING,
                "RPG_CYCLONE_SHARED_RECORD_QUARANTINED recipient="+recipient+" file="+file.getFileName(),failure);
    }
    public Lease attachPrepared(UUID recipient){
        Recipient holder=recipients.get(recipient);
        if(holder==null||!holder.loaded)throw new IllegalStateException("CYCLONE_SHARED_PRELOAD_REQUIRED");
        synchronized(holder){holder.session=UUID.randomUUID();return new Lease(recipient,holder.session);}
    }
    public void detach(Lease lease){
        Recipient holder=recipients.get(lease.recipient());
        if(holder!=null)synchronized(holder){if(lease.session().equals(holder.session)){
            holder.session=null;
            holder.children.removeIf(child->child.remaining<=0);
        }}
    }
    private Recipient check(Lease lease){
        Recipient holder=recipients.get(lease.recipient());
        if(holder==null||!lease.session().equals(holder.session))throw new IllegalStateException("CYCLONE_SHARED_STALE_SESSION");
        return holder;
    }
    /** The parent is ACTIVE with a confirmed in-memory native debit and marker.
     * A deterministic parent intent is forced before child admission. */
    public boolean grant(CycloneBarrierLedger.State parent,UUID recipient,Lease recipientLease){
        return grant(parent,recipient,recipientLease,null);
    }
    public boolean grant(CycloneBarrierLedger.State parent,UUID recipient,Lease recipientLease,UUID trustGeneration){
        Objects.requireNonNull(parent);Objects.requireNonNull(recipient);
        if(parent.phase()!=CycloneBarrierLedger.Phase.ACTIVE||parent.manaReceipt()==null
                ||parent.cooldownReceipt()==null||parent.player().equals(recipient))
            throw new IllegalStateException("CYCLONE_SHARED_PARENT_NOT_PAID");
        Grant grant=new Grant(SCHEMA,parent.player(),recipient,parent.generation(),
                UUID.nameUUIDFromBytes((parent.generation()+"/SHARED_AEGIS").getBytes(StandardCharsets.UTF_8)),
                parent.capacity()*.5,trustGeneration);
        Recipient holder=check(recipientLease);
        synchronized(holder){
            check(recipientLease);
            if(grant.capacity()<=0)return false;
            try{
                Files.createDirectories(Objects.requireNonNull(intent(grant).getParent()));
                Files.createDirectories(children(recipient));
                try{force(intent(grant),new Frame(SCHEMA,"INTENT",grant,0,0,null),true);}
                catch(java.nio.file.FileAlreadyExistsException existing){
                    Intent prior=readIntentState(intent(grant));
                    if(prior.closed()||!grant.equals(prior.grant()))return false;
                }
                Path childFile=child(grant);
                Child child;
                if(Files.exists(childFile)){
                    child=readChild(childFile,recipient);
                    if(!grant.equals(child.grant))return false;
                }else{
                    if(holder.children.stream().filter(c->c.remaining>0).count()>=MAX_CHILDREN)return false;
                    force(childFile,new Frame(SCHEMA,"INIT",grant,0,grant.capacity(),null),true);
                    child=new Child(grant);
                }
                child.authorized=true;
                if(holder.children.stream().noneMatch(c->c.grant.parentGeneration().equals(grant.parentGeneration())))
                    holder.children.add(child);
                return child.remaining>0;
            }catch(IOException failure){throw new IllegalStateException("CYCLONE_SHARED_GRANT_FAILED",failure);}
        }
    }
    /** Receipt and remaining HP are forced before the caller may reduce native Damage. */
    public Debit debit(Lease lease,String receipt,float damage){return debit(lease,receipt,damage,grant->true);}
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value="FE_FLOATING_POINT_EQUALITY",
            justification="Exact equality rejects a debit too small to change stored double Barrier HP.")
    public Debit debit(Lease lease,String receipt,float damage,java.util.function.Predicate<Grant> eligible){
        if(receipt==null||receipt.isBlank()||receipt.length()>64||!Float.isFinite(damage)||damage<0)
            throw new IllegalArgumentException("CYCLONE_SHARED_HIT_INVALID");
        long waitingSince=System.nanoTime();
        Recipient holder=check(lease);
        synchronized(holder){
            debitLockWaitNanos.add(System.nanoTime()-waitingSince);
            check(lease);
            if(holder.children.stream().anyMatch(c->c.receipts.contains(receipt)))return new Debit(0,damage,true);
            double remaining=damage,absorbed=0;
            for(Child child:holder.children.stream().sorted(Comparator.comparing(c->c.grant.parentGeneration().toString())).toList()){
                if(remaining<=0||child.remaining<=0||!child.authorized||child.poisoned)continue;
                boolean admitted;
                try{admitted=eligible.test(child.grant);}catch(RuntimeException uncertain){admitted=false;}
                if(!admitted){
                    try{forfeit(child);}catch(RuntimeException failed){child.poisoned=true;}
                    continue;
                }
                double used=Math.min(remaining,child.remaining);
                if(child.remaining-used==child.remaining)continue;
                try{
                    if(child.receipts.size()>=MAX_RECEIPTS){forfeit(child);continue;}
                    double next=child.remaining-used;
                    force(child(child.grant),new Frame(SCHEMA,"DEBIT",null,child.revision+1,next,receipt),false);
                    child.receipts.add(receipt);child.remaining=next;child.revision++;
                    absorbed+=used;remaining-=used;
                }catch(IOException|RuntimeException failure){child.poisoned=true;}
            }
            return new Debit(absorbed,absorbed>0?CycloneBarrierLedger.nativeRemainder(remaining):damage,false);
        }
    }
    private void forfeit(Child child){
        if(child.remaining<=0)return;
        try{force(child(child.grant),new Frame(SCHEMA,"FORFEIT",null,child.revision+1,0,null),false);
            child.remaining=0;child.revision++;child.authorized=false;
            closeDepleted(child);
        }catch(IOException failure){child.poisoned=true;throw new IllegalStateException("CYCLONE_SHARED_FORFEIT_FAILED",failure);}
    }
    private void closeDepleted(Child child){
        try{
            Path parent=intent(child.grant);
            if(!readIntentState(parent).closed())
                force(parent,new Frame(SCHEMA,"CLOSED",null,1,0,null),false);
            Files.deleteIfExists(child(child.grant));
        }catch(IOException|RuntimeException failure){
            // The forced DEBIT/FORFEIT already settled protection. Keep the
            // child journal for replay if parent compaction did not complete.
            reportQuarantine(child.grant.recipient(),intent(child.grant),failure);
        }
    }
    public void clearForDeath(Lease lease){
        Recipient holder=check(lease);synchronized(holder){check(lease);
            RuntimeException first=null;
            for(Child child:holder.children)if(child.remaining>0)try{forfeit(child);}
            catch(RuntimeException failure){if(first==null)first=failure;else first.addSuppressed(failure);}
            if(first!=null)throw first;
        }
    }
    /** A caster death ends all of that caster's independently stored children,
     * including offline recipients. The intent inventory is durable authority. */
    public void clearCasterForDeath(UUID caster){
        for(Recipient holder:recipients.values())synchronized(holder){
            for(Child child:holder.children)if(caster.equals(child.grant.caster())&&child.remaining>0)
                forfeit(child);
        }
        Path directory=root.resolve("intent");
        if(!Files.isDirectory(directory))return;
        try(var files=Files.list(directory)){
            for(Path path:files.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".grant")).toList()){
                Grant grant;
                try{
                    Intent state=readIntentState(path);
                    if(state.closed())continue;
                    grant=state.grant();
                }
                catch(IOException|RuntimeException corrupt){
                    reportQuarantine(caster,path,corrupt);continue;
                }
                if(!caster.equals(grant.caster()))continue;
                Recipient holder=recipients.computeIfAbsent(grant.recipient(),id->new Recipient());
                synchronized(holder){
                    Child child=holder.children.stream()
                            .filter(c->c.grant.parentGeneration().equals(grant.parentGeneration()))
                            .findFirst().orElse(null);
                    if(child==null){
                        if(!Files.exists(child(grant)))continue;
                        child=readChild(child(grant),grant.recipient());
                        holder.children.add(child);
                    }
                    if(child.remaining>0)forfeit(child);
                }
            }
        }catch(IOException failure){throw new IllegalStateException("CYCLONE_SHARED_CASTER_DEATH_FAILED",failure);}
    }
    public View view(Lease lease){return view(lease,grant->true);}
    public View view(Lease lease,java.util.function.Predicate<Grant> eligible){
        Recipient holder=check(lease);synchronized(holder){check(lease);
            var active=holder.children.stream().filter(c->c.authorized&&!c.poisoned&&c.remaining>0&&eligible.test(c.grant)).toList();
            if(active.isEmpty())return null;
            double capacity=active.stream().mapToDouble(c->c.grant.capacity()).sum();
            double remaining=active.stream().mapToDouble(c->c.remaining).sum();
            long revision=active.stream().mapToLong(c->c.revision).sum();
            UUID generation=UUID.nameUUIDFromBytes(active.stream()
                    .map(c->c.grant.childGeneration().toString()).sorted().reduce("",String::concat).getBytes(StandardCharsets.UTF_8));
            return new View(generation,revision,remaining,capacity);
        }
    }
}
