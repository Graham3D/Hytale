package com.inigmasgames.hytalerpg.execution.support;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * R261-A gated authority. A native hit may reduce its Damage only after the matching
 * per-player journal record has been written and forced. No player save or native
 * entity reference is held here. A torn final record was never authorized to absorb.
 */
public final class CycloneBarrierLedger {
    public static final int SCHEMA=2, MAX_RECORDS=100_000, MAX_SESSIONS=4096;
    private static final long SEGMENT_BYTES=16_000_000;
    private static final Gson JSON=new Gson();
    public enum Phase { NONE, PENDING, ACTIVE, DEPLETED, RECOVERY_PENDING }
    public record State(int schema,UUID player,String skill,UUID generation,UUID activation,
                        long revision,Phase phase,double capacity,double remaining,
                        String manaReceipt,String cooldownReceipt,double manaPaid) {
        public State {
            Objects.requireNonNull(player);Objects.requireNonNull(skill);Objects.requireNonNull(phase);
            if(schema!=SCHEMA||!"spirit_shield".equals(skill)||revision<0
                    ||!Double.isFinite(capacity)||!Double.isFinite(remaining)||capacity<0||remaining<0||remaining>capacity
                    ||phase==Phase.NONE&&(generation!=null||activation!=null||capacity!=0||remaining!=0)
                    ||phase!=Phase.NONE&&(generation==null||activation==null)
                    ||phase==Phase.ACTIVE&&(capacity<=0||remaining<=0||manaReceipt==null||cooldownReceipt==null)
                    ||phase==Phase.DEPLETED&&remaining!=0
                    ||!Double.isFinite(manaPaid)||manaPaid<0
                    ||manaReceipt==null&&manaPaid!=0||manaReceipt!=null&&manaPaid<=0
                    ||manaReceipt!=null&&(manaReceipt.isBlank()||manaReceipt.length()>160)
                    ||cooldownReceipt!=null&&(cooldownReceipt.isBlank()||cooldownReceipt.length()>160))
                throw new IllegalArgumentException("INVALID_CYCLONE_BARRIER_STATE");
        }
        static State initial(UUID player){return new State(SCHEMA,player,"spirit_shield",null,null,0,Phase.NONE,0,0,null,null,0);}
    }
    public record Lease(UUID player,UUID session) {}
    public record Debit(double requested,double absorbed,double remaining,double damageRemaining,
                        UUID generation,String receipt,long revision,boolean duplicate,boolean durable) {}
    /** Conservative narrowing for Hytale's float-valued pending Damage field. */
    public static float nativeRemainder(double damage){
        if(!Double.isFinite(damage)||damage<0||damage>Float.MAX_VALUE)
            throw new IllegalArgumentException("INVALID_NATIVE_DAMAGE_REMAINDER");
        float narrowed=(float)damage;
        return narrowed<damage?Math.nextUp(narrowed):narrowed;
    }
    public record Metrics(long writes,long totalNanos,long maximumNanos,long failedWrites,long debitLockWaitNanos) {}
    private static final class Holder {
        UUID session;State state;boolean uncertain;int slot;boolean loaded,paymentRecoveryRequired;
        final HashSet<String> receipts=new HashSet<>();
        final HashSet<UUID> activations=new HashSet<>();
        Holder(State state){this.state=state;}
    }
    private final Path root;
    private final long segmentBytes;
    private final int maxRecords;
    private final ConcurrentHashMap<UUID,Holder> holders=new ConcurrentHashMap<>();
    private record Prepared(State state,HashSet<String> receipts,HashSet<UUID> activations,int slot,
                            boolean paymentRecoveryRequired){}
    private final ConcurrentHashMap<UUID,Prepared> preloaded=new ConcurrentHashMap<>();
    private final LongAdder writes=new LongAdder(),totalNanos=new LongAdder(),failedWrites=new LongAdder();
    private final LongAdder debitLockWaitNanos=new LongAdder();
    private volatile long maximumNanos;
    public CycloneBarrierLedger(Path root){this(root,SEGMENT_BYTES,MAX_RECORDS);}
    /** Small thresholds are used by deterministic rotation tests; production uses 16 MB. */
    public CycloneBarrierLedger(Path root,long segmentBytes){this(root,segmentBytes,MAX_RECORDS);}
    /** Package-private bound for deterministic ceiling tests; production always uses MAX_RECORDS. */
    CycloneBarrierLedger(Path root,long segmentBytes,int maxRecords){
        this.root=Objects.requireNonNull(root).toAbsolutePath().normalize();
        if(segmentBytes<4096||segmentBytes>SEGMENT_BYTES)throw new IllegalArgumentException("CYCLONE_SEGMENT_LIMIT");
        if(maxRecords<1||maxRecords>MAX_RECORDS)throw new IllegalArgumentException("CYCLONE_RECORD_LIMIT");
        this.segmentBytes=segmentBytes;
        this.maxRecords=maxRecords;
    }
    public Metrics metrics(){return new Metrics(writes.sum(),totalNanos.sum(),maximumNanos,
            failedWrites.sum(),debitLockWaitNanos.sum());}
    /** Run from ReadyPath's worker before the native player installation callback. */
    public void preload(UUID player){
        Objects.requireNonNull(player);
        Holder live=holders.get(player);
        if(live!=null){synchronized(live){
            if(live.uncertain){live.receipts.clear();live.activations.clear();int[] slot={0};
                live.state=replay(player,live.receipts,live.activations,slot);live.slot=slot[0];
                live.paymentRecoveryRequired=live.state.phase()==Phase.ACTIVE;
                live.session=null;live.uncertain=false;}
            if(live.session==null&&(live.state.phase()==Phase.PENDING||live.state.phase()==Phase.RECOVERY_PENDING))
                recoverPending(live,player);
            return; // A live holder has a newer serialized view.
        }}
        if(preloaded.size()>=256&&!preloaded.containsKey(player))throw new IllegalStateException("CYCLONE_PRELOAD_BUDGET");
        var receipts=new HashSet<String>();var activations=new HashSet<UUID>();
        int[] slot={0};var candidate=new Holder(replay(player,receipts,activations,slot));candidate.slot=slot[0];
        candidate.paymentRecoveryRequired=candidate.state.phase()==Phase.ACTIVE;
        candidate.receipts.addAll(receipts);
        candidate.activations.addAll(activations);
        if(candidate.state.phase()==Phase.PENDING||candidate.state.phase()==Phase.RECOVERY_PENDING)
            recoverPending(candidate,player);
        preloaded.put(player,new Prepared(candidate.state,new HashSet<>(candidate.receipts),
                new HashSet<>(candidate.activations),candidate.slot,candidate.paymentRecoveryRequired));
    }
    /** Native ready uses only a completed preload or an already-live holder; no disk read. */
    public Lease attachPrepared(UUID player){
        var prepared=preloaded.remove(player);
        Holder existing=holders.get(player);
        if(prepared==null&&existing==null)throw new IllegalStateException("CYCLONE_PRELOAD_REQUIRED");
        if(existing==null&&holders.size()>=MAX_SESSIONS)throw new IllegalStateException("CYCLONE_SESSION_BUDGET");
        Holder holder=holders.computeIfAbsent(player,id->{
            var value=new Holder(prepared.state());value.receipts.addAll(prepared.receipts());
            value.activations.addAll(prepared.activations());value.slot=prepared.slot();
            value.loaded=true;value.paymentRecoveryRequired=prepared.paymentRecoveryRequired();return value;
        });
        synchronized(holder){if(holder.uncertain)throw new IllegalStateException("CYCLONE_RECOVERY_PRELOAD_REQUIRED");
            if(holder.state.phase()==Phase.PENDING||holder.state.phase()==Phase.RECOVERY_PENDING)
                throw new IllegalStateException("CYCLONE_PENDING_PRELOAD_RECOVERY_REQUIRED");
            if(holder.session!=null&&holder.state.phase()==Phase.ACTIVE)holder.paymentRecoveryRequired=true;
            holder.session=UUID.randomUUID();
            return new Lease(player,holder.session);
        }
    }
    private void recoverPending(Holder holder,UUID player){
        append(holder,new State(SCHEMA,player,"spirit_shield",holder.state.generation(),holder.state.activation(),
                holder.state.revision()+1,Phase.DEPLETED,holder.state.capacity(),0,
                holder.state.manaReceipt(),holder.state.cooldownReceipt(),holder.state.manaPaid()),null);
    }

    /** Missing R260 sidecar is an empty barrier, never an invented active barrier. */
    public Lease attach(UUID player) {
        Objects.requireNonNull(player);
        if(holders.size()>=MAX_SESSIONS&&!holders.containsKey(player))throw new IllegalStateException("CYCLONE_SESSION_BUDGET");
        Holder holder=holders.computeIfAbsent(player,id->new Holder(State.initial(id)));
        synchronized(holder){
            if(!holder.loaded||holder.uncertain){
                holder.receipts.clear();holder.activations.clear();
                int[] slot={0};holder.state=replay(player,holder.receipts,holder.activations,slot);holder.slot=slot[0];
                holder.paymentRecoveryRequired=holder.state.phase()==Phase.ACTIVE;
                holder.loaded=true;holder.uncertain=false;
            }
            if(holder.session!=null&&holder.state.phase()==Phase.ACTIVE)holder.paymentRecoveryRequired=true;
            holder.session=UUID.randomUUID();
            // A pending payment cannot prove that native Mana actually left the player.
            if(holder.state.phase()==Phase.PENDING||holder.state.phase()==Phase.RECOVERY_PENDING)
                recoverPending(holder,player);
            return new Lease(player,holder.session);
        }
    }
    public State confirmMana(Lease lease,UUID activation,String receipt,double paid){
        if(receipt==null||receipt.isBlank()||receipt.length()>160)throw new IllegalArgumentException("INVALID_MANA_RECEIPT");
        if(!Double.isFinite(paid)||paid<=0)throw new IllegalArgumentException("INVALID_MANA_PAYMENT");
        Holder h=holder(lease.player());synchronized(h){check(h,lease);
            if(!activation.equals(h.state.activation())||h.state.phase()!=Phase.PENDING)
                throw new IllegalStateException("CYCLONE_MANA_STAGE_INVALID");
            if(h.state.manaReceipt()!=null){
                if(h.state.manaReceipt().equals(receipt)&&h.state.manaPaid()==paid)return h.state;
                throw new IllegalStateException("CYCLONE_MANA_RECEIPT_CONFLICT");
            }
            var old=h.state;
            var next=new State(SCHEMA,lease.player(),"spirit_shield",old.generation(),activation,
                    old.revision()+1,Phase.PENDING,old.capacity(),0,receipt,null,paid);
            append(h,next,null);return next;
        }
    }
    public State confirmCooldown(Lease lease,UUID activation,String receipt){
        if(receipt==null||receipt.isBlank()||receipt.length()>160)throw new IllegalArgumentException("INVALID_COOLDOWN_RECEIPT");
        Holder h=holder(lease.player());synchronized(h){check(h,lease);
            if(!activation.equals(h.state.activation())||h.state.phase()!=Phase.PENDING||h.state.manaReceipt()==null)
                throw new IllegalStateException("CYCLONE_COOLDOWN_STAGE_INVALID");
            if(h.state.cooldownReceipt()!=null){
                if(h.state.cooldownReceipt().equals(receipt))return h.state;
                throw new IllegalStateException("CYCLONE_COOLDOWN_RECEIPT_CONFLICT");
            }
            var old=h.state;
            var next=new State(SCHEMA,lease.player(),"spirit_shield",old.generation(),activation,
                    old.revision()+1,Phase.PENDING,old.capacity(),0,old.manaReceipt(),receipt,old.manaPaid());
            append(h,next,null);return next;
        }
    }
    public void detach(Lease lease){
        Holder holder=holder(lease.player());
        synchronized(holder){if(Objects.equals(holder.session,lease.session())){
            holder.session=null;
            if(holder.state.phase()==Phase.ACTIVE)holder.paymentRecoveryRequired=true;
        }}
    }
    public State state(Lease lease){Holder h=holder(lease.player());synchronized(h){check(h,lease);return h.state;}}
    public boolean paymentRecoveryRequired(Lease lease){
        Holder h=holder(lease.player());synchronized(h){check(h,lease);return h.paymentRecoveryRequired;}
    }
    /** The caller must have read a matching marker from the loaded native Mana stat.
     * That marker is serialized beside the debited value, after ACTIVE was forced. */
    public State reconcileNativePayment(Lease lease,UUID generation){
        Holder h=holder(lease.player());synchronized(h){check(h,lease);
            if(h.state.phase()!=Phase.ACTIVE||!Objects.equals(h.state.generation(),generation)
                    ||h.state.manaReceipt()==null||h.state.cooldownReceipt()==null)
                throw new IllegalStateException("CYCLONE_PAYMENT_RECONCILIATION_MISMATCH");
            h.paymentRecoveryRequired=false;
            return h.state;
        }
    }
    /** If the loaded native Mana record lacks a matching post-payment marker,
     * forfeit protection without charging or refunding Mana. The forced DEPLETED
     * record must complete before the new connection becomes ready. */
    public State invalidateUncertainPayment(Lease lease){
        Holder h=holder(lease.player());synchronized(h){check(h,lease);
            if(!h.paymentRecoveryRequired)return h.state;
            if(h.state.phase()!=Phase.ACTIVE){h.paymentRecoveryRequired=false;return h.state;}
            return clearForDeath(lease);
        }
    }
    public boolean activationSeen(Lease lease,UUID activation){
        Holder h=holder(lease.player());synchronized(h){check(h,lease);return h.activations.contains(activation);}
    }
    public boolean receiptSeen(Lease lease,String receipt){
        Holder h=holder(lease.player());synchronized(h){check(h,lease);return h.receipts.contains(receipt);}
    }
    public State begin(Lease lease,UUID activation,UUID generation,double capacity){
        if(activation==null||generation==null||!Double.isFinite(capacity)||capacity<=0||capacity>Float.MAX_VALUE)
            throw new IllegalArgumentException("INVALID_CYCLONE_ACTIVATION");
        Holder h=holder(lease.player());synchronized(h){check(h,lease);
            if(h.state.activation()!=null&&h.state.activation().equals(activation))return h.state;
            if(h.activations.contains(activation))throw new IllegalStateException("CYCLONE_ACTIVATION_REPLAY");
            if(h.activations.size()>=maxRecords)throw new IllegalStateException("CYCLONE_ACTIVATION_BUDGET");
            if(h.state.phase()!=Phase.NONE&&h.state.phase()!=Phase.DEPLETED)
                throw new IllegalStateException("CYCLONE_BARRIER_ALREADY_ACTIVE_OR_PENDING");
            var next=new State(SCHEMA,lease.player(),"spirit_shield",generation,activation,h.state.revision()+1,
                    Phase.PENDING,capacity,0,null,null,0);
            append(h,next,null);h.receipts.clear();h.activations.add(activation);return next;
        }
    }
    /** Only the coordinator may publish this after both independent authorities confirmed. */
    State activate(Lease lease,UUID activation){
        Holder h=holder(lease.player());synchronized(h){check(h,lease);
            if(!activation.equals(h.state.activation()))throw new IllegalStateException("CYCLONE_ACTIVATION_MISMATCH");
            if(h.state.phase()==Phase.ACTIVE)return h.state;
            if(h.state.phase()!=Phase.PENDING||h.state.manaReceipt()==null||h.state.cooldownReceipt()==null)
                throw new IllegalStateException("CYCLONE_ACTIVATION_NOT_PAID");
            var next=new State(SCHEMA,lease.player(),"spirit_shield",h.state.generation(),activation,
                    h.state.revision()+1,Phase.ACTIVE,h.state.capacity(),h.state.capacity(),
                    h.state.manaReceipt(),h.state.cooldownReceipt(),h.state.manaPaid());
            append(h,next,null);return next;
        }
    }
    public State abort(Lease lease,UUID activation,boolean compensationUncertain){
        Holder h=holder(lease.player());synchronized(h){check(h,lease);
            if(!activation.equals(h.state.activation()))throw new IllegalStateException("CYCLONE_ACTIVATION_MISMATCH");
            if(h.state.phase()!=Phase.PENDING&&h.state.phase()!=Phase.RECOVERY_PENDING)return h.state;
            var next=new State(SCHEMA,lease.player(),"spirit_shield",h.state.generation(),activation,
                    h.state.revision()+1,compensationUncertain?Phase.RECOVERY_PENDING:Phase.DEPLETED,
                    h.state.capacity(),0,h.state.manaReceipt(),h.state.cooldownReceipt(),h.state.manaPaid());
            append(h,next,null);return next;
        }
    }
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value="FE_FLOATING_POINT_EQUALITY",
            justification="Exact equality detects a debit too small to change the stored double; that hit must pass through unchanged.")
    public Debit debit(Lease lease,UUID generation,String receipt,double damage){
        if(receipt==null||receipt.isBlank()||receipt.length()>64||!Double.isFinite(damage)||damage<0)
            throw new IllegalArgumentException("INVALID_CYCLONE_DAMAGE_RECEIPT");
        long waitingSince=System.nanoTime();
        Holder h=holder(lease.player());synchronized(h){
            debitLockWaitNanos.add(System.nanoTime()-waitingSince);
            check(h,lease);
            State old=h.state;
            if(old.phase()!=Phase.ACTIVE||!Objects.equals(generation,old.generation()))
                return new Debit(damage,0,old.remaining(),damage,old.generation(),receipt,old.revision(),false,true);
            if(h.receipts.contains(receipt))
                return new Debit(damage,0,old.remaining(),damage,old.generation(),receipt,old.revision(),true,true);
            if(h.paymentRecoveryRequired)
                return new Debit(damage,0,old.remaining(),damage,old.generation(),receipt,old.revision(),false,false);
            double used=Math.min(damage,old.remaining());
            if(used<=0||old.remaining()-used==old.remaining())
                return new Debit(damage,0,old.remaining(),damage,old.generation(),receipt,old.revision(),false,true);
            if(h.receipts.size()>=maxRecords){
                // Bounded journal lifetime: forfeit the remaining pool instead of silently
                // absorbing without a receipt or permanently blocking recast.
                clearForDeath(lease);
                return new Debit(damage,0,0,damage,old.generation(),receipt,h.state.revision(),false,true);
            }
            double remaining=Math.max(0,old.remaining()-used);
            var next=new State(SCHEMA,lease.player(),"spirit_shield",old.generation(),old.activation(),
                    old.revision()+1,remaining==0?Phase.DEPLETED:Phase.ACTIVE,
                    old.capacity(),remaining,old.manaReceipt(),old.cooldownReceipt(),old.manaPaid());
            append(h,next,receipt);h.receipts.add(receipt);
            return new Debit(damage,used,remaining,Math.max(0,damage-used),old.generation(),receipt,next.revision(),false,true);
        }
    }
    public State clearForDeath(Lease lease){
        Holder h=holder(lease.player());synchronized(h){check(h,lease);State old=h.state;
            if(old.phase()!=Phase.ACTIVE)return old;
            var next=new State(SCHEMA,lease.player(),"spirit_shield",old.generation(),old.activation(),
                    old.revision()+1,Phase.DEPLETED,old.capacity(),0,old.manaReceipt(),old.cooldownReceipt(),old.manaPaid());
            append(h,next,null);h.paymentRecoveryRequired=false;return next;
        }
    }
    private Holder holder(UUID player){Holder h=holders.get(player);if(h==null)throw new IllegalStateException("CYCLONE_NOT_ATTACHED");return h;}
    private static void check(Holder h,Lease lease){
        if(h.uncertain||!Objects.equals(h.session,lease.session()))throw new IllegalStateException("CYCLONE_SESSION_STALE_OR_UNCERTAIN");
    }
    private Path journal(UUID player,int slot){return root.resolve(player+(slot==0?".journal":".journal.alt"));}
    private void append(Holder holder,State next,String receipt){
        long started=System.nanoTime();
        try{
            if(next.revision()!=holder.state.revision()+1)throw new IllegalStateException("CYCLONE_REVISION_GAP");
            Files.createDirectories(root);
            byte[] bytes=(line(next,receipt)+"\n").getBytes(StandardCharsets.UTF_8);
            Path path=journal(next.player(),holder.slot);
            if(Files.exists(path)&&Files.size(path)+bytes.length>segmentBytes){
                int alternate=1-holder.slot;Path checkpoint=journal(next.player(),alternate);
                byte[] snapshot=(checkpointLine(holder)+"\n").getBytes(StandardCharsets.UTF_8);
                if(snapshot.length+bytes.length>segmentBytes)throw new IllegalStateException("CYCLONE_CHECKPOINT_BUDGET");
                try(var channel=FileChannel.open(checkpoint,StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){
                    var buffer=ByteBuffer.wrap(snapshot);while(buffer.hasRemaining())channel.write(buffer);
                    channel.force(true);
                }
                // The old slot is kept until the new one is complete. An interrupted
                // checkpoint can only damage the inactive, older slot.
                holder.slot=alternate;path=checkpoint;
            }
            try(var channel=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.APPEND)){
                var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);
                channel.force(true);
            }
            holder.state=next;writes.increment();
            long elapsed=System.nanoTime()-started;totalNanos.add(elapsed);maximumNanos=Math.max(maximumNanos,elapsed);
        }catch(IOException|RuntimeException failure){failedWrites.increment();holder.uncertain=true;
            throw new IllegalStateException("CYCLONE_DURABLE_WRITE_FAILED",failure);}
    }
    private record Recovered(State state,HashSet<String> receipts,HashSet<UUID> activations){}
    private State replay(UUID player,HashSet<String> receipts,HashSet<UUID> activations,int[] slot){
        Recovered first=replaySlot(player,0),second=replaySlot(player,1);
        if(first==null&&second==null)return State.initial(player);
        if(first!=null&&second!=null&&first.state.revision()==second.state.revision()
                &&(!first.state.equals(second.state)||!first.receipts.equals(second.receipts)
                    ||!first.activations.equals(second.activations)))
            throw new IllegalStateException("CYCLONE_CHECKPOINT_REVISION_CONFLICT");
        slot[0]=second!=null&&(first==null||second.state.revision()>=first.state.revision())?1:0;
        Recovered chosen=slot[0]==1?second:first;
        receipts.addAll(chosen.receipts);activations.addAll(chosen.activations);return chosen.state;
    }
    private Recovered replaySlot(UUID player,int slot){
        Path path=journal(player,slot);State state=State.initial(player);
        if(!Files.exists(path))return null;
        var receipts=new HashSet<String>();var activations=new HashSet<UUID>();
        try{
            byte[] bytes=Files.readAllBytes(path);
            if(bytes.length>segmentBytes)throw new IllegalStateException("CYCLONE_JOURNAL_BUDGET");
            int end=bytes.length;
            while(end>0&&bytes[end-1]!='\n')end--;
            if(end!=bytes.length)try(var channel=FileChannel.open(path,StandardOpenOption.WRITE)){
                channel.truncate(end);channel.force(true);
            }
            int start=0;boolean first=true;
            for(int i=0;i<end;i++)if(bytes[i]=='\n'){
                Parsed parsed=parse(new String(bytes,start,i-start,StandardCharsets.UTF_8));
                State next=parsed.state();
                if(first&&parsed.checkpoint()){
                    if(!player.equals(next.player()))throw new IllegalStateException("CYCLONE_CHECKPOINT_PLAYER");
                    receipts.addAll(Arrays.asList(parsed.receipts()));
                    activations.addAll(Arrays.asList(parsed.activations()));
                    if(receipts.size()!=parsed.receipts().length||activations.size()!=parsed.activations().length
                            ||activations.size()>maxRecords||receipts.size()>maxRecords
                            ||next.phase()!=Phase.NONE&&!activations.contains(next.activation()))
                        throw new IllegalStateException("CYCLONE_CHECKPOINT_SET_INVALID");
                    state=next;start=i+1;first=false;continue;
                }
                if(parsed.checkpoint())throw new IllegalStateException("CYCLONE_CHECKPOINT_POSITION");
                if(!player.equals(next.player())||next.revision()!=state.revision()+1)
                    throw new IllegalStateException("CYCLONE_JOURNAL_SEQUENCE");
                boolean newGeneration=!Objects.equals(next.generation(),state.generation());
                if(!validTransition(state,next,parsed.receipt(),newGeneration))
                    throw new IllegalStateException("CYCLONE_JOURNAL_TRANSITION");
                if(newGeneration){
                    if(!activations.add(next.activation()))throw new IllegalStateException("CYCLONE_ACTIVATION_REPLAY");
                    if(activations.size()>maxRecords)throw new IllegalStateException("CYCLONE_ACTIVATION_BUDGET");
                    receipts.clear();
                }
                if(parsed.receipt()!=null&&!receipts.add(parsed.receipt()))
                    throw new IllegalStateException("CYCLONE_DUPLICATE_DURABLE_RECEIPT");
                if(receipts.size()>maxRecords)throw new IllegalStateException("CYCLONE_DEBIT_BUDGET");
                state=next;start=i+1;first=false;
            }
            return end==0?null:new Recovered(state,receipts,activations);
        }catch(IOException|RuntimeException error){throw new IllegalStateException("CYCLONE_JOURNAL_RECOVERY_FAILED",error);}
    }
    private static boolean validTransition(State old,State next,String receipt,boolean newGeneration){
        if(newGeneration)return receipt==null&&(old.phase()==Phase.NONE||old.phase()==Phase.DEPLETED)
                &&next.phase()==Phase.PENDING&&next.remaining()==0&&next.capacity()>0
                &&next.manaReceipt()==null&&next.cooldownReceipt()==null&&next.manaPaid()==0;
        if(!Objects.equals(old.activation(),next.activation())||old.capacity()!=next.capacity()
                ||old.manaPaid()!=next.manaPaid()
                    &&!(old.phase()==Phase.PENDING&&old.manaReceipt()==null&&next.manaReceipt()!=null
                        &&old.manaPaid()==0&&next.manaPaid()>0)
                ||!Objects.equals(old.manaReceipt(),next.manaReceipt())
                    &&!(old.phase()==Phase.PENDING&&old.manaReceipt()==null&&next.manaReceipt()!=null)
                ||!Objects.equals(old.cooldownReceipt(),next.cooldownReceipt())
                    &&!(old.phase()==Phase.PENDING&&old.cooldownReceipt()==null&&next.cooldownReceipt()!=null))return false;
        return switch(old.phase()){
            case NONE,DEPLETED->false;
            case PENDING->receipt==null&&next.remaining()==0&&(next.phase()==Phase.PENDING
                        &&(old.manaReceipt()==null&&next.manaReceipt()!=null&&next.cooldownReceipt()==null
                            ||old.manaReceipt()!=null&&old.cooldownReceipt()==null
                                &&next.manaReceipt().equals(old.manaReceipt())&&next.cooldownReceipt()!=null)
                    ||next.phase()==Phase.DEPLETED||next.phase()==Phase.RECOVERY_PENDING)
                    ||receipt==null&&next.phase()==Phase.ACTIVE&&old.manaReceipt()!=null
                        &&old.cooldownReceipt()!=null&&next.remaining()==next.capacity();
            case ACTIVE->receipt!=null&&(next.phase()==Phase.ACTIVE||next.phase()==Phase.DEPLETED)
                        &&next.remaining()<old.remaining()
                    ||receipt==null&&next.phase()==Phase.DEPLETED&&next.remaining()==0;
            case RECOVERY_PENDING->receipt==null&&next.phase()==Phase.DEPLETED&&next.remaining()==0;
        };
    }
    private static String line(State value,String receipt){
        String payload=JSON.toJson(value);var envelope=new JsonObject();
        envelope.addProperty("checksum",sha(payload+":"+receipt));
        envelope.add("state",JsonParser.parseString(payload));
        if(receipt!=null)envelope.addProperty("receipt",receipt);
        return JSON.toJson(envelope);
    }
    private static String checkpointLine(Holder holder){
        var payload=new JsonObject();
        payload.add("state",JSON.toJsonTree(holder.state));
        String[] receipts=holder.receipts.toArray(String[]::new);Arrays.sort(receipts);
        UUID[] activations=holder.activations.toArray(UUID[]::new);Arrays.sort(activations);
        payload.add("receipts",JSON.toJsonTree(receipts));
        payload.add("activations",JSON.toJsonTree(activations));
        var envelope=new JsonObject();envelope.add("checkpoint",payload);
        envelope.addProperty("checksum",sha(JSON.toJson(payload)));
        return JSON.toJson(envelope);
    }
    private record Parsed(State state,String receipt,boolean checkpoint,String[] receipts,UUID[] activations){}
    private static Parsed parse(String line){
        var envelope=JsonParser.parseString(line).getAsJsonObject();
        if(envelope.has("checkpoint")){
            var payload=envelope.getAsJsonObject("checkpoint");
            if(!sha(JSON.toJson(payload)).equals(envelope.get("checksum").getAsString()))
                throw new IllegalStateException("CYCLONE_CHECKPOINT_CHECKSUM");
            var state=validated(JSON.fromJson(payload.get("state"),State.class));
            String[] receipts=JSON.fromJson(payload.get("receipts"),String[].class);
            UUID[] activations=JSON.fromJson(payload.get("activations"),UUID[].class);
            if(receipts==null||activations==null)throw new IllegalStateException("CYCLONE_CHECKPOINT_SETS_MISSING");
            for(String receipt:receipts)if(receipt==null||receipt.isBlank()||receipt.length()>64)
                throw new IllegalStateException("CYCLONE_CHECKPOINT_RECEIPT_INVALID");
            for(UUID activation:activations)if(activation==null)
                throw new IllegalStateException("CYCLONE_CHECKPOINT_ACTIVATION_INVALID");
            return new Parsed(state,null,true,receipts,activations);
        }
        String payload=JSON.toJson(envelope.get("state"));
        String receipt=envelope.has("receipt")?envelope.get("receipt").getAsString():null;
        if(receipt!=null&&(receipt.isBlank()||receipt.length()>64))
            throw new IllegalStateException("CYCLONE_DAMAGE_RECEIPT_INVALID");
        if(!sha(payload+":"+receipt).equals(envelope.get("checksum").getAsString()))
            throw new IllegalStateException("CYCLONE_JOURNAL_CHECKSUM");
        return new Parsed(validated(JSON.fromJson(payload,State.class)),receipt,false,null,null);
    }
    private static State validated(State raw){
        return new State(raw.schema(),raw.player(),raw.skill(),raw.generation(),raw.activation(),
                raw.revision(),raw.phase(),raw.capacity(),raw.remaining(),raw.manaReceipt(),raw.cooldownReceipt(),raw.manaPaid());
    }
    private static String sha(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
}
