package com.inigmasgames.hytalerpg.diagnostics.hro;

import com.inigmasgames.hytalerpg.diagnostics.SharedTraceBudget;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.lang.management.*;

/** One process session, one worker, fixed queue; callers never wait for disk, locks or finalization. */
public final class HroRecorder implements AutoCloseable {
    public static final long DURATION_NANOS=600_000_000_000L;
    public static final long RECONNECT_GRACE_NANOS=30_000_000_000L;
    static final int CAPACITY=512, RESERVATION=16*1024;
    private static final int LOSS_DETAIL_LIMIT=128;
    private static final Set<String> CRITICAL=Set.of("IDENTITY","MARK","PAYMENT","DEBIT","NATIVE_RESULT","OUTCOME",
            "SAVED","LOADED","PLAN_ACCEPTED","ELITE_PUBLISHED","BIRTH_COMPENSATED","BIRTH_DECLINED");
    private static final Set<String> FACTS=Set.of("result","reason","failureCode","validationResult","skillSlot","logicalSlot",
            "skillId","nativeItemId","nativeAction","nativePrimaryIndex","operation","ownershipMode","componentPresent",
            "expectedSpatial","bagRevisionBefore","bagRevisionAfter","RPG revision","revision","loadoutRevision","rawNativeSlot",
            "resolvedNativeSlot","quantityBefore","quantityAfter","singleCustodian","settled","identityVerified","closedOnce",
            "hudRestored","duplicateClose","page","session","schema","itemId","entryId","receiptId","quantity","equipmentSlot","preloadRegistration",
            "wholeWorldTickMs","wholeWorldTickPaired","rpgWallMs","nativeTick","nonRpgRemainderMs","rejectedWorlds",
            "equipmentRevision","accepted","affixId","value","applied","units","bucket","rootId","selected","roll","chance",
            "healthBefore","healthAfter","postAbsorption","reconciliation","attempted","origin","cause","targetId",
            "before","after","absorbed","remaining","requested","damageRemaining","generation","durable","duplicate",
            "requestedReflection","reflectionFactor","manaPaid","capacity","provenance","encounter","rarity","published",
            "durableSubject","fingerprint","phase","activation","manaReceipt","cooldownReceipt","label","critical","coefficient",
            "manaSpent","intended","uncertain","proof");
    private record Request(UUID owner,UUID world,boolean armed) { }
    private record ScopeState(UUID world,String worldAlias,long connection,int worldSegment,long disconnectAt) { }
    private final Path root;
    private final AtomicReference<Request> request=new AtomicReference<>();
    private final Set<String> providers=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Thread worker;
    private volatile boolean closing;
    private volatile Session session;
    private volatile String last="OFF";
    private final String installation;
    private final AtomicReference<HroArm.Action> armAction=new AtomicReference<>();
    private final java.util.function.LongSupplier clock;
    private final java.util.function.Supplier<Map<String,Object>> identity;
    @FunctionalInterface interface Fault { void beforeWrite() throws java.io.IOException; }
    private final Fault fault;
    private static final class Session {
        final UUID owner,salt;final String id;final Path directory;final long start;
        final ReentrantLock lock=new ReentrantLock();final ArrayDeque<HroEvent> queue=new ArrayDeque<>(CAPACITY);
        final AtomicLong drops=new AtomicLong(),sequence=new AtomicLong(),admitted=new AtomicLong(),nanos=new AtomicLong(),timingSamples=new AtomicLong();
        final AtomicLongArray timing=new AtomicLongArray(32);
        final AtomicReferenceArray<HroContracts.Loss> lossDetails=new AtomicReferenceArray<>(LOSS_DETAIL_LIMIT);
        final AtomicLong lossDetailSlots=new AtomicLong();
        final AtomicLongArray lossOverflowByContract=new AtomicLongArray(HroContracts.IDS.size()+1);
        final AtomicLong producerAtOrAboveBudget=new AtomicLong();
        final AtomicLongArray producerOverBudgetByContract=new AtomicLongArray(HroContracts.IDS.size()+1);
        volatile String stop; long maximumQueue,writerNanos;final AtomicLong contention=new AtomicLong();
        final AtomicReference<ScopeState> scope;
        final AtomicLongArray tickTiming=new AtomicLongArray(32);
        final AtomicReference<TickWindow> tickWindow=new AtomicReference<>();
        long firstCaptureNanos;
        final long initialGcCount,initialGcMillis,initialUsedBytes;
        final AtomicLong loadoutRevision=new AtomicLong(Long.MIN_VALUE);
        final AtomicReference<String> equipmentRevision=new AtomicReference<>();
        final AtomicLong packets=new AtomicLong();
        final AtomicLong disconnections=new AtomicLong(),reconnections=new AtomicLong(),tickMetricGaps=new AtomicLong(),observationsWithoutTick=new AtomicLong();
        Session(UUID owner,UUID world,String id,Path directory,long now){this.owner=owner;this.id=id;this.salt=UUID.randomUUID();this.directory=directory;start=now;
            scope=new AtomicReference<>(new ScopeState(world,world==null?null:alias(this,world),Long.MIN_VALUE,1,-1));
            initialGcCount=gcCount();initialGcMillis=gcMillis();initialUsedBytes=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();}
    }
    private static final class TickWindow {
        final UUID world;final long tick;final AtomicLong nanos=new AtomicLong();
        TickWindow(UUID world,long tick){this.world=world;this.tick=tick;}
    }
    public HroRecorder(Path root) { this(root,System::nanoTime,HroIdentity::capture,()->{},root.toString()); }
    HroRecorder(Path root,java.util.function.LongSupplier clock,java.util.function.Supplier<Map<String,Object>> identity,Fault fault) {
        this(root,clock,identity,fault,root.toString());
    }
    HroRecorder(Path root,java.util.function.LongSupplier clock,java.util.function.Supplier<Map<String,Object>> identity,Fault fault,String installation) {
        this.root=root.toAbsolutePath().normalize();this.clock=clock;this.identity=identity;this.fault=fault;this.installation=HroArm.installation(installation);
        for(Path part:this.root)if(part.toString().equalsIgnoreCase("Saves"))throw new IllegalArgumentException("HRO_REQUIRES_PATH_OUTSIDE_SAVES");
        worker=Thread.ofPlatform().daemon().name("hywind-hro").unstarted(this::run);worker.start();
    }
    public void registered(String contract){if(HroContracts.IDS.contains(contract))providers.add(contract);}
    public boolean active(UUID owner){Session current=session;return current!=null&&current.stop==null&&current.scope.get().disconnectAt()<0&&current.owner.equals(owner);}
    public boolean recording(){Session s=session;return s!=null&&s.stop==null;}
    public void loss(UUID owner){loss(owner,null,null,"UNATTRIBUTED_PRODUCER_GAP");}
    public void loss(UUID owner,String contract,String operation,String reason){Session s=session;
        if(s!=null&&s.stop==null&&s.owner.equals(owner))drop(s,contract,operation,reason);}
    private static void drop(Session s,String contract,String operation,String reason){
        // Count first: an in-flight loss without published detail remains conservatively unscoped.
        s.drops.incrementAndGet();
        int slot=(int)Math.min(Integer.MAX_VALUE,s.lossDetailSlots.getAndIncrement());
        if(slot<LOSS_DETAIL_LIMIT)s.lossDetails.set(slot,new HroContracts.Loss(contract==null?"UNATTRIBUTED":contract,
                operation==null?"":clip(operation),reason,1));
        else {int contractIndex=HroContracts.IDS.indexOf(contract);s.lossOverflowByContract.incrementAndGet(contractIndex<0?
                HroContracts.IDS.size():contractIndex);}
    }
    private static List<HroContracts.Loss> lossSites(Session s){
        var sites=new ArrayList<HroContracts.Loss>();
        for(int i=0;i<Math.min(LOSS_DETAIL_LIMIT,s.lossDetailSlots.get());i++){
            var site=s.lossDetails.get(i);if(site!=null)sites.add(site);
        }
        for(int i=0;i<s.lossOverflowByContract.length();i++){
            long count=s.lossOverflowByContract.get(i);
            if(count>0)sites.add(new HroContracts.Loss(i==HroContracts.IDS.size()?"UNATTRIBUTED":HroContracts.IDS.get(i),
                    "","LOSS_DETAIL_CAP",count));
        }
        return List.copyOf(sites);
    }
    public UUID ownerInWorld(UUID world){Session s=session;if(s==null||s.stop!=null)return null;
        ScopeState scope=s.scope.get();return scope.disconnectAt()<0&&Objects.equals(scope.world(),world)?s.owner:null;}
    public String token(UUID owner){Session s=session;return s!=null&&s.stop==null&&s.scope.get().disconnectAt()<0&&s.owner.equals(owner)?s.id:null;}
    public String packet(UUID owner){Session s=session;if(s==null||s.stop!=null||s.scope.get().disconnectAt()>=0||!s.owner.equals(owner))return null;
        long n=s.packets.incrementAndGet();if(n>2048){drop(s,null,null,"PACKET_OBSERVER_BUDGET");s.stop="PACKET_OBSERVER_BUDGET";return null;}return s.id+":"+n;}
    public boolean firstEquipment(UUID owner,String revision){Session s=session;if(s==null||s.stop!=null||s.scope.get().disconnectAt()>=0||!s.owner.equals(owner))return false;
        String prior=s.equipmentRevision.get();return !Objects.equals(prior,revision)&&s.equipmentRevision.compareAndSet(prior,revision);}
    public String start(UUID owner,UUID world) {
        if(closing)return "HRO unavailable: shutting down";
        if(session!=null || !request.compareAndSet(null,new Request(Objects.requireNonNull(owner),world,false)))return status();
        last="STARTING";LockSupport.unpark(worker);return "HRO STARTING; scope=invoking player; maximum=10 minutes";
    }
    public String armNext(UUID owner,UUID world){
        if(closing||recording()||request.get()!=null)return status();
        armAction.set(new HroArm.Action(Objects.requireNonNull(owner),world,false));
        last="HRO ARMING_NEXT_BOOT";LockSupport.unpark(worker);return last;
    }
    public String cancelArm(){armAction.set(new HroArm.Action(null,null,true));last="HRO CANCELLING_ARM";LockSupport.unpark(worker);return last;}
    public String stop(){Session current=session;if(current!=null)current.stop="STOPPED";
        else if(request.getAndSet(null)!=null)last="HRO CANCELLED_START";return status();}
    public String status(){Session current=session;return current==null?last:"HRO "+(current.stop!=null?"FINALIZING":current.scope.get().disconnectAt()>=0?"WAITING_RECONNECT":"RECORDING")+
            "; run="+current.id+"; events="+current.admitted.get()+"; dropped="+current.drops.get()+"; folder="+current.directory;}
    public void disconnect(UUID owner,long generation){Session current=session;
        if(current==null||!current.owner.equals(owner)||current.stop!=null)return;
        while(true){ScopeState old=current.scope.get();if(generation!=old.connection()||old.disconnectAt()>=0)return;
            ScopeState next=new ScopeState(old.world(),old.worldAlias(),old.connection(),old.worldSegment(),clock.getAsLong());
            if(current.scope.compareAndSet(old,next)){current.disconnections.incrementAndGet();LockSupport.unpark(worker);return;}}
    }
    /** Only the native owner connection lifecycle may advance a generation or world segment. */
    public void lifecycle(UUID owner,UUID world,long generation,String boundary){
        Session current=session;if(current==null||current.stop!=null||!current.owner.equals(owner)||generation<0)return;
        boolean reconnected=false,resetRevision=false;
        while(true){ScopeState old=current.scope.get();
            if(old.disconnectAt()>=0&&(generation<=old.connection()||clock.getAsLong()-old.disconnectAt()>RECONNECT_GRACE_NANOS))return;
            if(generation<old.connection())return;
            reconnected=old.disconnectAt()>=0&&generation>old.connection();
            UUID nextWorld=world==null?old.world():world;
            int nextSegment=old.world()!=null&&world!=null&&!old.world().equals(world)?old.worldSegment()+1:old.worldSegment();
            ScopeState next=new ScopeState(nextWorld,Objects.equals(nextWorld,old.world())?old.worldAlias():
                    nextWorld==null?null:alias(current,nextWorld),generation,nextSegment,generation>old.connection()?-1:old.disconnectAt());
            if(current.scope.compareAndSet(old,next)){resetRevision=generation>old.connection()||nextSegment>old.worldSegment();break;}
        }
        if(reconnected)current.reconnections.incrementAndGet();
        if(resetRevision){current.loadoutRevision.set(Long.MIN_VALUE);current.equipmentRevision.set(null);}
        observe(owner,"PLAYER.ADMISSION","HytalePlayerPersistenceReady",boundary,"admission-"+generation,world,generation,null,null,
                reconnected?Map.of("reason","RECONNECTED_AFTER_DISCONNECT"):Map.of());
    }
    public String mark(UUID owner,UUID world,String label){Session current=session;
        if(current==null||!active(owner))return "HRO is not recording this player";
        ScopeState scope=current.scope.get();
        if(world!=null&&!Objects.equals(scope.world(),world))return "HRO marker world has no lifecycle handoff";
        String safe=label==null||label.isBlank()?"USER_CHECKPOINT":label.replaceAll("[^A-Za-z0-9_.-]","_");
        if(safe.length()>64)safe=safe.substring(0,64);
        observe(owner,"MARK","RpgTraceCommand","MARK","mark-"+UUID.randomUUID(),world,
                scope.connection()==Long.MIN_VALUE?null:scope.connection(),null,null,Map.of("label",safe));
        return "HRO checkpoint requested: "+safe+"; check dropped evidence in status";
    }
    public boolean firstLoadout(UUID owner,long revision) {
        Session current=session;if(current==null||current.stop!=null||current.scope.get().disconnectAt()>=0||!current.owner.equals(owner))return false;
        long previous=current.loadoutRevision.get();
        return previous!=revision&&current.loadoutRevision.compareAndSet(previous,revision);
    }
    public void worldSample(Map<String,?> facts) {
        Session current=session;if(current==null||current.stop!=null)return;
        ScopeState scope=current.scope.get();if(scope.disconnectAt()>=0||!Objects.equals(scope.world(),facts.get("world")))return;
        Object tick=facts.get("nativeTick");
        observe(current.owner,"PERFORMANCE","NativeRpgTickMetrics","WORLD_TICK",null,scope.world(),null,
                tick instanceof Number n?n.longValue():null,null,facts);
    }
    /** Called after the enabled check. Copies at most 20 allowlisted bounded scalar facts. */
    public void observe(UUID owner,String contract,String provider,String boundary,String operation,UUID world,Long connection,
                        Long tick,Long revision,Map<String,?> values) {
        Session current=session;if(current==null||current.stop!=null||!current.owner.equals(owner))return;
        long began=clock.getAsLong();
        if(began-current.start>=DURATION_NANOS){current.stop="EXPIRED";return;}
        if(!SharedTraceBudget.acquire(RESERVATION)){drop(current,contract,operation,"SHARED_QUEUE_BUDGET");current.stop="SHARED_QUEUE_BUDGET";return;}
        boolean retained=false;
        try {
            if(!current.lock.tryLock()){drop(current,contract,operation,"QUEUE_LOCK_CONTENTION");current.contention.incrementAndGet();return;}
            try {
                if(current.stop!=null)return;
                ScopeState scope=current.scope.get();
                if(scope.disconnectAt()>=0)return;
                if(connection!=null&&scope.connection()!=Long.MIN_VALUE&&connection.longValue()!=scope.connection()){
                    drop(current,contract,operation,"STALE_CONNECTION");return;}
                if(world!=null&&scope.world()!=null&&!scope.world().equals(world)){drop(current,contract,operation,"STALE_WORLD");return;}
                if(current.queue.size()>=CAPACITY){drop(current,contract,operation,"QUEUE_BUDGET");current.stop="QUEUE_BUDGET";return;}
                // Thirty-two slots remain available for final outcomes/control receipts under routine pressure.
                if(current.queue.size()>=CAPACITY-32&&!critical(boundary)){drop(current,contract,operation,"ROUTINE_QUEUE_PRESSURE");return;}
                Map<String,String> facts=new LinkedHashMap<>(); int visited=0;
                for(var entry:values.entrySet()) {
                    if(++visited>48 || facts.size()>=20){drop(current,contract,operation,"FACT_LIMIT");break;}
                    if(!FACTS.contains(entry.getKey()))continue;
                    Object value=entry.getValue();
                    if(value instanceof String s){if(s.length()>192)drop(current,contract,operation,"FACT_TRUNCATED");facts.put(entry.getKey(),clip(s));}
                    else if(value instanceof Number || value instanceof Boolean || value instanceof Enum<?>)facts.put(entry.getKey(),clip(value.toString()));
                    else if(value instanceof UUID id)facts.put(entry.getKey(),alias(current,id));
                }
                if(current.scope.get()!=scope){drop(current,contract,operation,"SCOPE_CHANGED_DURING_COPY");return;}
                long sequence=current.sequence.incrementAndGet();
                if(connection==null)facts.put("connectionEvidenceGap","PROVIDER_HAS_NO_CONNECTION_GENERATION");
                if(world==null)facts.put("worldEvidenceGap","PROVIDER_HAS_NO_WORLD_ID");
                if(revision==null)facts.put("revisionEvidenceGap","PROVIDER_HAS_NO_STATE_REVISION");
                if(operation!=null&&operation.length()>192)drop(current,contract,operation,"OPERATION_ID_TRUNCATED");
                current.queue.addLast(new HroEvent(1,current.id,sequence,System.currentTimeMillis(),began-current.start,
                        clip(contract),clip(provider),clip(boundary),operation==null||operation.isBlank()?"unjoined-"+sequence:clip(operation),
                        world==null?null:(scope.worldAlias()==null?alias(current,world):scope.worldAlias())+
                                ":segment-"+scope.worldSegment(),connection,tick,revision,facts));
                retained=true;current.admitted.incrementAndGet();current.maximumQueue=Math.max(current.maximumQueue,current.queue.size());
            }finally{current.lock.unlock();}
        }catch(RuntimeException diagnosticFailure){drop(current,contract,operation,"PRODUCER_EXCEPTION");}
        finally {
            if(!retained)SharedTraceBudget.release(RESERVATION);
            long tickCost=Math.max(0,clock.getAsLong()-began);
            if(tick!=null)recordTick(current,world,tick,tickCost);else current.observationsWithoutTick.incrementAndGet();
            long elapsed=Math.max(0,clock.getAsLong()-began);current.nanos.addAndGet(elapsed);
            current.timing.incrementAndGet(Math.min(31,64-Long.numberOfLeadingZeros(elapsed)));
            if(elapsed>=50_000){current.producerAtOrAboveBudget.incrementAndGet();
                int contractIndex=HroContracts.IDS.indexOf(contract);
                current.producerOverBudgetByContract.incrementAndGet(contractIndex<0?HroContracts.IDS.size():contractIndex);}
            current.timingSamples.incrementAndGet();
        }
    }
    private static boolean critical(String boundary){return CRITICAL.contains(boundary);}
    private static void recordTick(Session session,UUID world,long tick,long elapsed){
        TickWindow window=session.tickWindow.get();
        if(window!=null&&window.tick==tick&&Objects.equals(window.world,world)){window.nanos.addAndGet(elapsed);return;}
        var next=new TickWindow(world,tick);next.nanos.set(elapsed);
        if(session.tickWindow.compareAndSet(window,next)){
            if(window!=null){long finished=window.nanos.get();session.tickTiming.incrementAndGet(Math.min(31,64-Long.numberOfLeadingZeros(finished)));}
        }else session.tickMetricGaps.incrementAndGet();
    }
    private static String clip(String value){return value.substring(0,Math.min(192,value.length()));}
    private static String alias(Session current,UUID value){return new UUID(value.getMostSignificantBits()^current.salt.getMostSignificantBits(),
            value.getLeastSignificantBits()^current.salt.getLeastSignificantBits()).toString();}
    private void run() {
        try {HroArm.Armed armed=HroArm.consume(root,installation,System.currentTimeMillis());
            if(armed!=null){request.compareAndSet(null,new Request(armed.owner(),armed.world(),true));last="HRO BOOT_CAPTURE_STARTING";}}
        catch(Exception failure){last="HRO ARM_PARTIAL; reason="+failure.getClass().getSimpleName();}
        while(!closing || session!=null) {
            HroArm.Action action=armAction.getAndSet(null);
            if(action!=null)try {if(action.cancel()){HroArm.cancel(root);last="HRO ARM_CANCELLED";}
                else {HroArm.write(root,installation,action.owner(),action.world(),System.currentTimeMillis());last="HRO ARMED_NEXT_BOOT";}}
                catch(Exception failure){last="HRO ARM_PARTIAL; reason="+failure.getClass().getSimpleName();}
            Request next=request.get();
            if(session==null&&next!=null) {
                // Keep request occupied during initialization so repeated starts cannot enqueue another run.
                String id=UUID.randomUUID().toString();Session current=new Session(next.owner,next.world,id,root.resolve(id),clock.getAsLong());
                session=current;
                if(request.get()!=next)current.stop="CANCELLED_START";
                try {validateRoot();capture(current,next.world,next.armed);}catch(Exception failure){last="HRO PARTIAL; folder="+current.directory+
                        "; reason="+failure.getClass().getSimpleName()+":"+clip(String.valueOf(failure.getMessage()));}
                finally {
                    current.stop="FINALIZED";
                    current.lock.lock();try{int queued=current.queue.size();current.queue.clear();SharedTraceBudget.release((long)queued*RESERVATION);}finally{current.lock.unlock();}
                    request.set(null);session=null;
                }
            }else LockSupport.parkNanos(this,2_000_000_000L);
        }
    }
    private void validateRoot()throws java.io.IOException {
        Path ancestor=root;
        while(ancestor!=null&&!Files.exists(ancestor))ancestor=ancestor.getParent();
        if(ancestor==null)throw new java.io.IOException("NO_OUTPUT_ANCESTOR");
        for(Path part:ancestor.toRealPath())if(part.toString().equalsIgnoreCase("Saves"))throw new java.io.IOException("OUTPUT_RESOLVES_INTO_SAVES");
        Files.createDirectories(root);
        try(var children=Files.list(root)) {
            if(children.filter(Files::isDirectory).filter(path->{try{UUID.fromString(path.getFileName().toString());return true;}
                catch(IllegalArgumentException ignored){return false;}}).limit(9).count()>=8)
                throw new java.io.IOException("RETENTION_FULL_PRESERVE_EXISTING_RUNS");
        }
    }
    private void capture(Session current,UUID world,boolean armed)throws java.io.IOException {
        Map<String,Object> origin=identity.get();Map<String,Object> manifest=new LinkedHashMap<>();
        manifest.put("schemaVersion",1);manifest.put("runId",current.id);manifest.put("state","RECORDING");
        manifest.put("captureBuild",origin);manifest.put("processId",ProcessHandle.current().pid());
        manifest.put("bootId",HroRuntime.BOOT_ID);manifest.put("startedUtcMillis",System.currentTimeMillis());
        manifest.put("scope","invoking-player");manifest.put("preStartEvidence",armed?"BOOTSTRAP_UNOBSERVED_BEFORE_PLUGIN_SETUP":"NOT_OBSERVED");
        manifest.put("sampling","ALL_OPERATIONS_DETERMINISTIC_1_OF_1; no partial-lifecycle sampling");
        manifest.put("criticalReservedSlots",32);manifest.put("maxPacketObserversPerRun",2048);
        manifest.put("monotonicClockOrigin","CAPTURE_START_SYSTEM_NANOTIME");
        manifest.put("maxRetainedHeapBytes",64L*1024*1024);manifest.put("maxQueuedBytes",SharedTraceBudget.LIMIT);
        manifest.put("maxUncompressedBytes",HroBundle.MAX_EVENTS_BYTES);manifest.put("maxPhysicalBytes",512L*1024*1024);
        try(HroBundle bundle=new HroBundle(current.directory,manifest,providers)) {
            current.firstCaptureNanos=Math.max(0,clock.getAsLong()-current.start);
            observe(current.owner,"BOOT.ARTIFACT","HroIdentity","IDENTITY", "boot",world,null,null,null,
                    Map.of("identityVerified",false,"reason","RUNTIME_ASSET_ARCHIVE_IDENTITY_UNOBSERVABLE",
                            "preloadRegistration",Boolean.TRUE.equals(origin.get("preloadRegistration"))));
            long flushed=clock.getAsLong();
            while(true) {
                if(closing&&current.stop==null)current.stop="SHUTDOWN";
                if(clock.getAsLong()-current.start>=DURATION_NANOS&&current.stop==null)current.stop="EXPIRED";
                ScopeState observedScope=current.scope.get();
                if(observedScope.disconnectAt()>=0&&clock.getAsLong()-observedScope.disconnectAt()>=RECONNECT_GRACE_NANOS&&current.stop==null)
                    current.stop="RECONNECT_GRACE_EXPIRED";
                HroEvent event;
                current.lock.lock();try{event=current.queue.pollFirst();}finally{current.lock.unlock();}
                if(event!=null) {
                    try {long writing=clock.getAsLong();fault.beforeWrite();bundle.event(event);current.writerNanos+=Math.max(0,clock.getAsLong()-writing);}
                    catch(java.io.IOException failure){drop(current,event.contract(),event.operationId(),"WRITE_FAILED");current.stop="WRITE_FAILED";throw failure;}
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
            performance.put("producerTimingSamples",current.timingSamples.get());
            performance.put("producerP95NanosUpperBound",percentile(current.timing,current.timingSamples.get(),95));
            long producerP99=percentile(current.timing,current.timingSamples.get(),99);
            performance.put("producerP99NanosUpperBound",producerP99);
            performance.put("producerBudgetNanos",50_000);
            long overBudget=current.producerAtOrAboveBudget.get(),producerSamples=current.timingSamples.get();
            performance.put("producerAtOrAboveBudgetSamples",overBudget);
            Map<String,Long> overByContract=new LinkedHashMap<>();
            for(int i=0;i<HroContracts.IDS.size();i++)if(current.producerOverBudgetByContract.get(i)>0)
                overByContract.put(HroContracts.IDS.get(i),current.producerOverBudgetByContract.get(i));
            if(current.producerOverBudgetByContract.get(HroContracts.IDS.size())>0)
                overByContract.put("OTHER",current.producerOverBudgetByContract.get(HroContracts.IDS.size()));
            performance.put("producerAtOrAboveBudgetByContract",overByContract);
            performance.put("producerQualificationMethod","EXACT_50US_THRESHOLD_COUNT; log2 p99 remains a conservative upper bound");
            performance.put("producerQualification",producerSamples==0?"UNVERIFIED_NO_SAMPLES":
                    producerP99WithinBudget(producerSamples,overBudget)?"OBSERVED_WITHIN_BUDGET":"OBSERVED_BUDGET_EXCEEDED");
            TickWindow finalTick=current.tickWindow.getAndSet(null);
            if(finalTick!=null)current.tickTiming.incrementAndGet(Math.min(31,64-Long.numberOfLeadingZeros(finalTick.nanos.get())));
            List<Long> ticks=new ArrayList<>(32);long tickCount=0;for(int i=0;i<32;i++){long n=current.tickTiming.get(i);ticks.add(n);tickCount+=n;}
            performance.put("ownerTickNanosLog2Histogram",ticks);performance.put("ownerTickSamples",tickCount);
            performance.put("observationsWithoutNativeTick",current.observationsWithoutTick.get());
            long tickP99=percentile(current.tickTiming,tickCount,99);
            performance.put("ownerTickP99NanosUpperBound",tickP99);performance.put("ownerTickBudgetNanos",500_000);
            performance.put("ownerTickQualification",tickP99<0?"UNVERIFIED_NO_TICK_CONTEXT":
                    current.tickMetricGaps.get()>0?"INCOMPLETE_TICK_METRICS":tickP99<=500_000?"OBSERVED_WITHIN_BUDGET":"OBSERVED_BUDGET_EXCEEDED");
            performance.put("ownerTickMetricGaps",current.tickMetricGaps.get());
            performance.put("writerNanos",current.writerNanos);performance.put("writerBytes",bundle.eventBytes());
            performance.put("writerBytesPerSecond",current.writerNanos==0?0:(double)bundle.eventBytes()*1_000_000_000d/current.writerNanos);
            performance.put("firstCaptureInitializationNanos",current.firstCaptureNanos);performance.put("contentionDrops",current.contention.get());
            performance.put("heapUsedStartBytes",current.initialUsedBytes);performance.put("heapUsedEndBytes",ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
            performance.put("gcCountDelta",Math.max(0,gcCount()-current.initialGcCount));performance.put("gcMillisDelta",Math.max(0,gcMillis()-current.initialGcMillis));
            performance.put("nativeWorldTiming",bundle.nativeTiming());
            performance.put("governor",current.stop);performance.put("clientFrameQualification","CLIENT_PERFORMANCE_UNVERIFIED");
            performance.put("governorTransitions",List.of(Map.of("state","RECORDING","monotonicNanos",0),
                    Map.of("state","STOPPED_OR_SUSPENDED","reason",current.stop,"monotonicNanos",Math.max(0,clock.getAsLong()-current.start))));
            performance.put("diskUsableBytesAtFinish",diskUsable());
            performance.put("observedDisconnects",current.disconnections.get());performance.put("correlatedReconnects",current.reconnections.get());
            // Read details before the count so a concurrently published scope cannot outrun it.
            List<HroContracts.Loss> sites=lossSites(current);
            bundle.finish(current.drops.get(),current.stop,performance,sites);
            String analysis=HroAutoAnalysis.finish(root,current.directory,clock);
            last="HRO "+(current.drops.get()==0&&("STOPPED".equals(current.stop)||"EXPIRED".equals(current.stop))&&analysis.equals("COMPLETE")?"COMPLETE":"PARTIAL")+
                    "; run="+current.id+"; dropped="+current.drops.get()+"; folder="+current.directory+"; termination="+current.stop+"; analysis="+analysis;
        }
    }
    private static long percentile(AtomicLongArray histogram,long count,int percent){if(count<=0)return -1;long target=(count*percent+99)/100,sum=0;
        for(int i=0;i<histogram.length();i++){sum+=histogram.get(i);if(sum>=target)return 1L<<Math.min(30,i);}return -1;}
    static boolean producerP99WithinBudget(long samples,long atOrAboveBudget){
        if(samples<=0||atOrAboveBudget<0||atOrAboveBudget>samples)return false;
        return atOrAboveBudget<=samples-(samples*99+99)/100;
    }
    private static long gcCount(){long n=0;for(var bean:ManagementFactory.getGarbageCollectorMXBeans())if(bean.getCollectionCount()>0)n+=bean.getCollectionCount();return n;}
    private static long gcMillis(){long n=0;for(var bean:ManagementFactory.getGarbageCollectorMXBeans())if(bean.getCollectionTime()>0)n+=bean.getCollectionTime();return n;}
    private long diskUsable(){try{return Files.getFileStore(root).getUsableSpace();}catch(java.io.IOException|SecurityException unavailable){return -1;}}
    @Override public void close(){closing=true;Session current=session;if(current!=null)current.stop="SHUTDOWN";LockSupport.unpark(worker);}
}
