package com.inigmasgames.hytalerpg.diagnostics.hro;

import com.inigmasgames.hytalerpg.diagnostics.SharedTraceBudget;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/** One process session, one worker, fixed queue; callers never wait for disk, locks or finalization. */
public final class HroRecorder implements AutoCloseable {
    public static final long DURATION_NANOS=600_000_000_000L;
    static final int CAPACITY=512, RESERVATION=16*1024;
    private static final Set<String> CRITICAL=Set.of("IDENTITY","MARK","PAYMENT","DEBIT","NATIVE_RESULT","OUTCOME",
            "SAVED","LOADED","PLAN_ACCEPTED","ELITE_PUBLISHED","BIRTH_COMPENSATED","BIRTH_DECLINED");
    private static final Set<String> FACTS=Set.of("result","reason","failureCode","validationResult","skillSlot","logicalSlot",
            "skillId","nativeItemId","nativeAction","nativePrimaryIndex","operation","ownershipMode","componentPresent",
            "expectedSpatial","bagRevisionBefore","bagRevisionAfter","RPG revision","revision","loadoutRevision","rawNativeSlot",
            "resolvedNativeSlot","quantityBefore","quantityAfter","singleCustodian","settled","identityVerified","closedOnce",
            "hudRestored","duplicateClose","page","session","schema","itemId","entryId","receiptId","quantity","equipmentSlot","preloadRegistration",
            "wholeWorldTickMs","wholeWorldTickPaired","rpgWallMs","nativeTick",
            "equipmentRevision","accepted","affixId","value","applied","units","bucket","rootId","selected","roll","chance",
            "healthBefore","healthAfter","postAbsorption","reconciliation","attempted","origin","cause","targetId",
            "before","after","absorbed","remaining","requested","damageRemaining","generation","durable","duplicate",
            "requestedReflection","reflectionFactor","manaPaid","capacity","provenance","encounter","rarity","published",
            "durableSubject","fingerprint","phase","activation","manaReceipt","cooldownReceipt","label","critical","coefficient",
            "manaSpent","intended","uncertain","proof");
    private record Request(UUID owner,UUID world) { }
    private final Path root;
    private final AtomicReference<Request> request=new AtomicReference<>();
    private final Set<String> providers=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Thread worker;
    private volatile boolean closing;
    private volatile Session session;
    private volatile String last="OFF";
    private final java.util.function.LongSupplier clock;
    private final java.util.function.Supplier<Map<String,Object>> identity;
    @FunctionalInterface interface Fault { void beforeWrite() throws java.io.IOException; }
    private final Fault fault;
    private static final class Session {
        final UUID owner,salt;final String id;final Path directory;final long start;
        final ReentrantLock lock=new ReentrantLock();final ArrayDeque<HroEvent> queue=new ArrayDeque<>(CAPACITY);
        final AtomicLong drops=new AtomicLong(),sequence=new AtomicLong(),admitted=new AtomicLong(),nanos=new AtomicLong();
        final AtomicLongArray timing=new AtomicLongArray(32);
        volatile String stop; long maximumQueue;
        volatile UUID world;
        final AtomicLong loadoutRevision=new AtomicLong(Long.MIN_VALUE);
        final AtomicReference<String> equipmentRevision=new AtomicReference<>();
        final AtomicLong packets=new AtomicLong();
        Session(UUID owner,String id,Path directory,long now){this.owner=owner;this.id=id;this.salt=UUID.randomUUID();this.directory=directory;start=now;}
    }
    public HroRecorder(Path root) { this(root,System::nanoTime,HroIdentity::capture,()->{}); }
    HroRecorder(Path root,java.util.function.LongSupplier clock,java.util.function.Supplier<Map<String,Object>> identity,Fault fault) {
        this.root=root.toAbsolutePath().normalize();this.clock=clock;this.identity=identity;this.fault=fault;
        for(Path part:this.root)if(part.toString().equalsIgnoreCase("Saves"))throw new IllegalArgumentException("HRO_REQUIRES_PATH_OUTSIDE_SAVES");
        worker=Thread.ofPlatform().daemon().name("hywind-hro").unstarted(this::run);worker.start();
    }
    public void registered(String contract){if(HroContracts.IDS.contains(contract))providers.add(contract);}
    public boolean active(UUID owner){Session current=session;return current!=null&&current.stop==null&&current.owner.equals(owner);}
    public boolean recording(){Session s=session;return s!=null&&s.stop==null;}
    public void loss(UUID owner){Session s=session;if(s!=null&&s.owner.equals(owner))s.drops.incrementAndGet();}
    public UUID ownerInWorld(UUID world){Session s=session;return s!=null&&s.stop==null&&Objects.equals(s.world,world)?s.owner:null;}
    public String token(UUID owner){Session s=session;return s!=null&&s.stop==null&&s.owner.equals(owner)?s.id:null;}
    public String packet(UUID owner){Session s=session;if(s==null||s.stop!=null||!s.owner.equals(owner))return null;
        long n=s.packets.incrementAndGet();if(n>2048){s.drops.incrementAndGet();s.stop="PACKET_OBSERVER_BUDGET";return null;}return s.id+":"+n;}
    public boolean firstEquipment(UUID owner,String revision){Session s=session;if(s==null||s.stop!=null||!s.owner.equals(owner))return false;
        String prior=s.equipmentRevision.get();return !Objects.equals(prior,revision)&&s.equipmentRevision.compareAndSet(prior,revision);}
    public String start(UUID owner,UUID world) {
        if(closing)return "HRO unavailable: shutting down";
        if(session!=null || !request.compareAndSet(null,new Request(Objects.requireNonNull(owner),world)))return status();
        last="STARTING";LockSupport.unpark(worker);return "HRO STARTING; scope=invoking player; maximum=10 minutes";
    }
    public String stop(){Session current=session;if(current!=null)current.stop="STOPPED";
        else if(request.getAndSet(null)!=null)last="HRO CANCELLED_START";return status();}
    public String status(){Session current=session;return current==null?last:"HRO "+(current.stop==null?"RECORDING":"FINALIZING")+
            "; run="+current.id+"; events="+current.admitted.get()+"; dropped="+current.drops.get()+"; folder="+current.directory;}
    public void disconnect(UUID owner){Session current=session;if(current!=null&&current.owner.equals(owner))current.stop="DISCONNECTED";}
    public boolean firstLoadout(UUID owner,long revision) {
        Session current=session;if(current==null||current.stop!=null||!current.owner.equals(owner))return false;
        long previous=current.loadoutRevision.get();
        return previous!=revision&&current.loadoutRevision.compareAndSet(previous,revision);
    }
    public void worldSample(Map<String,?> facts) {
        Session current=session;if(current==null||current.stop!=null||!Objects.equals(current.world,facts.get("world")))return;
        Object tick=facts.get("nativeTick");
        observe(current.owner,"PERFORMANCE","NativeRpgTickMetrics","WORLD_TICK",null,current.world,null,
                tick instanceof Number n?n.longValue():null,null,facts);
    }
    /** Called after the enabled check. Copies at most 20 allowlisted bounded scalar facts. */
    public void observe(UUID owner,String contract,String provider,String boundary,String operation,UUID world,Long connection,
                        Long tick,Long revision,Map<String,?> values) {
        Session current=session;if(current==null||current.stop!=null||!current.owner.equals(owner))return;
        long began=clock.getAsLong();
        if(began-current.start>=DURATION_NANOS){current.stop="EXPIRED";return;}
        if(!SharedTraceBudget.acquire(RESERVATION)){current.drops.incrementAndGet();current.stop="SHARED_QUEUE_BUDGET";return;}
        boolean retained=false;
        try {
            if(!current.lock.tryLock()){current.drops.incrementAndGet();return;}
            try {
                if(current.stop!=null)return;
                if(world!=null)current.world=world;
                if(current.queue.size()>=CAPACITY){current.drops.incrementAndGet();current.stop="QUEUE_BUDGET";return;}
                // Thirty-two slots remain available for final outcomes/control receipts under routine pressure.
                if(current.queue.size()>=CAPACITY-32&&!critical(boundary)){current.drops.incrementAndGet();return;}
                Map<String,String> facts=new LinkedHashMap<>(); int visited=0;
                for(var entry:values.entrySet()) {
                    if(++visited>48 || facts.size()>=20){current.drops.incrementAndGet();break;}
                    if(!FACTS.contains(entry.getKey()))continue;
                    Object value=entry.getValue();
                    if(value instanceof String s){if(s.length()>192)current.drops.incrementAndGet();facts.put(entry.getKey(),clip(s));}
                    else if(value instanceof Number || value instanceof Boolean || value instanceof Enum<?>)facts.put(entry.getKey(),clip(value.toString()));
                    else if(value instanceof UUID id)facts.put(entry.getKey(),alias(current,id));
                }
                long sequence=current.sequence.incrementAndGet();
                if(connection==null)facts.put("connectionEvidenceGap","PROVIDER_HAS_NO_CONNECTION_GENERATION");
                if(world==null)facts.put("worldEvidenceGap","PROVIDER_HAS_NO_WORLD_ID");
                if(revision==null)facts.put("revisionEvidenceGap","PROVIDER_HAS_NO_STATE_REVISION");
                if(operation!=null&&operation.length()>192)current.drops.incrementAndGet();
                current.queue.addLast(new HroEvent(1,current.id,sequence,System.currentTimeMillis(),began-current.start,
                        clip(contract),clip(provider),clip(boundary),operation==null||operation.isBlank()?"unjoined-"+sequence:clip(operation),
                        world==null?null:alias(current,world),connection,tick,revision,facts));
                retained=true;current.admitted.incrementAndGet();current.maximumQueue=Math.max(current.maximumQueue,current.queue.size());
            }finally{current.lock.unlock();}
        }catch(RuntimeException diagnosticFailure){current.drops.incrementAndGet();}
        finally {
            if(!retained)SharedTraceBudget.release(RESERVATION);
            long elapsed=Math.max(0,clock.getAsLong()-began);current.nanos.addAndGet(elapsed);
            current.timing.incrementAndGet(Math.min(31,64-Long.numberOfLeadingZeros(elapsed)));
        }
    }
    private static boolean critical(String boundary){return CRITICAL.contains(boundary);}
    private static String clip(String value){return value.substring(0,Math.min(192,value.length()));}
    private static String alias(Session current,UUID value){return new UUID(value.getMostSignificantBits()^current.salt.getMostSignificantBits(),
            value.getLeastSignificantBits()^current.salt.getLeastSignificantBits()).toString();}
    private void run() {
        while(!closing || session!=null) {
            Request next=request.get();
            if(session==null&&next!=null) {
                // Keep request occupied during initialization so repeated starts cannot enqueue another run.
                String id=UUID.randomUUID().toString();Session current=new Session(next.owner,id,root.resolve(id),clock.getAsLong());
                current.world=next.world;
                session=current;
                if(request.get()!=next)current.stop="CANCELLED_START";
                try {validateRoot();capture(current,next.world);}catch(Exception failure){last="HRO PARTIAL; folder="+current.directory+
                        "; reason="+failure.getClass().getSimpleName()+":"+clip(String.valueOf(failure.getMessage()));}
                finally {
                    current.stop="FINALIZED";
                    current.lock.lock();try{int queued=current.queue.size();current.queue.clear();SharedTraceBudget.release((long)queued*RESERVATION);}finally{current.lock.unlock();}
                    request.set(null);session=null;
                }
            }else LockSupport.park(this);
        }
    }
    private void validateRoot()throws java.io.IOException {
        Path ancestor=root;
        while(ancestor!=null&&!Files.exists(ancestor))ancestor=ancestor.getParent();
        if(ancestor==null)throw new java.io.IOException("NO_OUTPUT_ANCESTOR");
        for(Path part:ancestor.toRealPath())if(part.toString().equalsIgnoreCase("Saves"))throw new java.io.IOException("OUTPUT_RESOLVES_INTO_SAVES");
        Files.createDirectories(root);
        try(var children=Files.list(root)) {
            if(children.filter(Files::isDirectory).limit(9).count()>=8)throw new java.io.IOException("RETENTION_FULL_PRESERVE_EXISTING_RUNS");
        }
    }
    private void capture(Session current,UUID world)throws java.io.IOException {
        Map<String,Object> origin=identity.get();Map<String,Object> manifest=new LinkedHashMap<>();
        manifest.put("schemaVersion",1);manifest.put("runId",current.id);manifest.put("state","RECORDING");
        manifest.put("captureBuild",origin);manifest.put("processId",ProcessHandle.current().pid());
        manifest.put("bootId",HroRuntime.BOOT_ID);manifest.put("startedUtcMillis",System.currentTimeMillis());
        manifest.put("scope","invoking-player");manifest.put("preStartEvidence","NOT_OBSERVED");
        manifest.put("sampling","ALL_OPERATIONS_DETERMINISTIC_1_OF_1; no partial-lifecycle sampling");
        manifest.put("criticalReservedSlots",32);manifest.put("maxPacketObserversPerRun",2048);
        manifest.put("monotonicClockOrigin","CAPTURE_START_SYSTEM_NANOTIME");
        manifest.put("maxRetainedHeapBytes",64L*1024*1024);manifest.put("maxQueuedBytes",SharedTraceBudget.LIMIT);
        manifest.put("maxUncompressedBytes",HroBundle.MAX_EVENTS_BYTES);manifest.put("maxPhysicalBytes",512L*1024*1024);
        try(HroBundle bundle=new HroBundle(current.directory,manifest,Set.copyOf(providers))) {
            observe(current.owner,"BOOT.ARTIFACT","HroIdentity","IDENTITY", "boot",world,null,null,null,
                    Map.of("identityVerified",false,"reason","RUNTIME_ASSET_ARCHIVE_IDENTITY_UNOBSERVABLE",
                            "preloadRegistration",Boolean.TRUE.equals(origin.get("preloadRegistration"))));
            long flushed=clock.getAsLong();
            while(true) {
                if(closing&&current.stop==null)current.stop="SHUTDOWN";
                if(clock.getAsLong()-current.start>=DURATION_NANOS&&current.stop==null)current.stop="EXPIRED";
                HroEvent event;
                current.lock.lock();try{event=current.queue.pollFirst();}finally{current.lock.unlock();}
                if(event!=null) {
                    try {fault.beforeWrite();bundle.event(event);}
                    catch(java.io.IOException failure){current.drops.incrementAndGet();current.stop="WRITE_FAILED";throw failure;}
                    finally{SharedTraceBudget.release(RESERVATION);}
                }else if(current.stop!=null)break;
                else LockSupport.parkNanos(2_000_000L);
                if(clock.getAsLong()-flushed>1_000_000_000L){bundle.flush();flushed=clock.getAsLong();
                    if(Files.getFileStore(root).getUsableSpace()<64L*1024*1024)current.stop="DISK_HEADROOM";}
            }
            Map<String,Object> performance=new LinkedHashMap<>();performance.put("observations",current.admitted.get());
            performance.put("producerNanos",current.nanos.get());performance.put("maxQueuedReservationsBytes",current.maximumQueue*RESERVATION);
            List<Long> histogram=new ArrayList<>(32);for(int i=0;i<32;i++)histogram.add(current.timing.get(i));
            performance.put("producerNanosLog2Histogram",histogram);performance.put("worldTickQualification","UNVERIFIED_NO_MATCHED_NATIVE_RUN");
            performance.put("clientFrameQualification","UNOBSERVABLE");
            bundle.finish(current.drops.get(),current.stop,performance);
            last="HRO COMPLETE; run="+current.id+"; folder="+current.directory+"; termination="+current.stop;
        }
    }
    @Override public void close(){closing=true;Session current=session;if(current!=null)current.stop="SHUTDOWN";LockSupport.unpark(worker);}
}
