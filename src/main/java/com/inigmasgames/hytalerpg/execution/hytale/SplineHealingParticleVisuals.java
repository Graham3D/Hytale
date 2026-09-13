package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.connection.ConnectionWorldPort.TetherVisualSegment;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;
import java.util.function.BiConsumer;

/** Production world-particle tether. No visual entity or model attachments.
 * Deferred frames are coalesced and invalidated by the existing channel cleanup boundary. */
public final class SplineHealingParticleVisuals {
    public static final String BLIPS="RPG_Heal_World_Blips",PULSE="RPG_Heal_World_Pulse";
    // Former anchor ceiling now bounds the total active sample slots, not ECS objects.
    public static final int MAX_SEGMENTS=6,MAX_ROOTS=512,MAX_ANCHORS=2048;
    public static final double VIEW_DISTANCE=30;
    private final Map<String,Root> roots=new HashMap<>();
    private final Map<String,Job> pending=new HashMap<>();
    private int slots;
    private final java.util.function.Consumer<Segment> auditSink;
    public SplineHealingParticleVisuals(){this(null);}
    private SplineHealingParticleVisuals(java.util.function.Consumer<Segment> auditSink){this.auditSink=auditSink;}
    private record Job(Store<EntityStore> store,SkillExecutionContext context,List<TetherVisualSegment> frame,double now,BiConsumer<String,Throwable> receipt){}
    private static final class Root {
        final UUID owner;final Store<EntityStore> store;final Map<String,Segment> segments=new HashMap<>();
        BiConsumer<String,Throwable> receipt;boolean updated;
        Root(UUID owner,Store<EntityStore> store){this.owner=owner;this.store=store;}
    }
    private static final class Segment {
        final String recipient;final HealingWorldParticleFrame frame=new HealingWorldParticleFrame();
        final SpawnParticleSystem[] packets=new SpawnParticleSystem[67];
        int reserved;
        Segment(String recipient){this.recipient=recipient;}
        SpawnParticleSystem packet(int i){
            var existing=packets[i];if(existing!=null)return existing;
            var p=frame.sample(i);
            // Same constructor/scale/color convention as the connected WORLD control.
            return packets[i]=new SpawnParticleSystem(i<frame.bodyCount()?BLIPS:PULSE,
                    new Position(p.x(),p.y(),p.z()),new Direction(0,0,0),1,null,HealingWorldParticleFrame.LIFETIME);
        }
    }
    public synchronized int rootCount(){return roots.size();}
    public synchronized int anchorCount(){return 0;}
    public synchronized int sampleSlots(){return slots;}
    public synchronized void present(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,SkillExecutionContext context,
            List<TetherVisualSegment> frame,double now,BiConsumer<String,Throwable> receipt){
        if(frame==null||frame.isEmpty()||frame.size()>MAX_SEGMENTS||!Double.isFinite(now))throw new IllegalArgumentException("HEAL_PATH_FRAME");
        String key=context.skillInstanceId();
        if(!pending.containsKey(key)&&pending.size()>=MAX_ROOTS)throw new IllegalStateException("HEAL_PATH_PENDING_CAPACITY");
        var job=new Job(store,context,List.copyOf(frame),now,receipt);var prior=pending.put(key,job);
        if(prior==null||prior.store!=store)try{mutate(store,buffer,()->drain(key,store));}catch(RuntimeException e){pending.remove(key,job);throw e;}
    }
    private static void mutate(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Runnable action){
        if(buffer!=null&&buffer.getStore()==store)buffer.run(ignored->action.run());
        else if(store.isInThread()&&!store.isProcessing())action.run();else store.getExternalData().getWorld().execute(action);
    }
    private synchronized void drain(String key,Store<EntityStore> store){
        var job=pending.get(key);if(job==null||job.store!=store)return;pending.remove(key);
        try{apply(key,job);}catch(RuntimeException failure){remove(key);job.receipt.accept("PARTICLE_PATH_FAILED",failure);}
    }
    private void apply(String key,Job job){
        var root=roots.get(key);boolean created=root==null;
        if(root!=null&&root.store!=job.store){remove(key);root=null;created=true;}
        if(root==null){if(roots.size()>=MAX_ROOTS)throw new IllegalStateException("HEAL_PATH_ROOT_CAPACITY");root=new Root(job.context.request().actorId(),job.store);roots.put(key,root);}
        root.receipt=job.receipt;var ids=new HashSet<String>();
        for(var requested:job.frame)if(!ids.add(requested.id()))throw new IllegalArgumentException("HEAL_PATH_DUPLICATE_ID");
        for(var id:new HashSet<>(root.segments.keySet()))if(!ids.contains(id))discard(root.segments.remove(id));
        for(var requested:job.frame){
            var segment=root.segments.get(requested.id());
            if(segment!=null&&!Objects.equals(segment.recipient,requested.recipient())){
                var previous=segment;discard(previous);segment=new Segment(requested.recipient());
                segment.frame.inheritCadence(previous.frame);root.segments.put(requested.id(),segment);
            }
            if(segment==null){segment=new Segment(requested.recipient());root.segments.put(requested.id(),segment);}
            if(!segment.frame.update(requested.shape().start(),requested.shape().end(),job.now))continue;
            int count=segment.frame.count();
            if(slots-segment.reserved+count>MAX_ANCHORS)throw new IllegalStateException("HEAL_PATH_VISUAL_CAPACITY");
            slots+=count-segment.reserved;segment.reserved=count;
            Arrays.fill(segment.packets,null);
            if(auditSink!=null)auditSink.accept(segment);
            send(root.store,segment);
        }
        if(created)job.receipt.accept("PARTICLE_PATH_STARTED",null);else if(!root.updated){root.updated=true;job.receipt.accept("PARTICLE_PATH_UPDATED",null);}
    }
    public static boolean withinView(Vec3 viewer,Vec3 sample,double nativeViewBlocks){
        double distance=Math.min(VIEW_DISTANCE,Math.max(0,nativeViewBlocks));
        return distance>0&&viewer.distanceSquared(sample)<=distance*distance;
    }
    private static void send(Store<EntityStore> store,Segment segment){
        // Native same-world player list plus reference/store identity and current ECS position.
        // Cull each sample, not just the source, so distant observers receive no tether traffic.
        for(var viewer:store.getExternalData().getWorld().getPlayerRefs()){
            var ref=viewer.getReference();
            if(ref==null||!ref.isValid()||ref.getStore()!=store)continue;
            var transform=store.getComponent(ref,TransformComponent.getComponentType());
            var player=store.getComponent(ref,Player.getComponentType());
            if(transform==null||player==null)continue;
            var p=transform.getPosition();var position=new Vec3(p.x,p.y,p.z);
            double distance=player.getViewRadius()*32d;
            for(int i=0;i<segment.frame.count();i++)if(withinView(position,segment.frame.sample(i),distance))
                viewer.getPacketHandler().write(segment.packet(i));
        }
    }
    private void discard(Segment s){slots-=s.reserved;s.reserved=0;}
    public synchronized void remove(SkillExecutionContext context,CommandBuffer<EntityStore> buffer){if(context!=null)remove(context.skillInstanceId());}
    private void remove(String key){
        pending.remove(key);var root=roots.remove(key);if(root==null)return;
        for(var s:root.segments.values())discard(s);root.segments.clear();
        if(root.receipt!=null)root.receipt.accept("PARTICLE_PATH_REMOVED",null);
        // No packet cancel handle and no persistent entity: last finite particles age out in .18s.
    }
    public synchronized void cancel(UUID owner,CommandBuffer<EntityStore> buffer){
        pending.entrySet().removeIf(e->e.getValue().context.request().actorId().equals(owner));
        for(var key:roots.entrySet().stream().filter(e->e.getValue().owner.equals(owner)).map(Map.Entry::getKey).toList())remove(key);
    }
    /** Real native packet construction/asset resolution and lifecycle, not client rendering proof. */
    static void audit(Store<EntityStore> store,SkillExecutionContext context){
        if(!Boolean.getBoolean("rpg.projectileSpawnAudit"))throw new IllegalStateException("ISOLATED_AUDIT_DISABLED");
        var packets=new ArrayList<SpawnParticleSystem>();
        int entityCount=store.getEntityCount();
        var v=new SplineHealingParticleVisuals(s->{for(int i=0;i<s.frame.count();i++)packets.add(s.packet(i));});
        var errors=new ArrayList<Throwable>();
        BiConsumer<String,Throwable> receipt=(event,error)->{if(error!=null)errors.add(error);};
        java.util.function.Consumer<java.util.function.Consumer<CommandBuffer<EntityStore>>> processing=work->{
            var once=new java.util.concurrent.atomic.AtomicBoolean();store.forEachChunk((java.util.function.BiConsumer<ArchetypeChunk<EntityStore>,CommandBuffer<EntityStore>>)(chunk,buffer)->{if(once.compareAndSet(false,true)){if(!store.isProcessing())throw new IllegalStateException("PARTICLE_PATH_NOT_PROCESSING");work.accept(buffer);}});if(!once.get())throw new IllegalStateException("PARTICLE_PATH_NO_CHUNK");
        };
        java.util.function.Function<Double,List<TetherVisualSegment>> frame=length->List.of(new TetherVisualSegment("primary",com.inigmasgames.hytalerpg.execution.connection.ConnectionShape.line(new Vec3(0,202,0),new Vec3(length,202,0),.2,1)));
        try{
            processing.accept(buffer->{v.present(store,buffer,context,frame.apply(18d),0,receipt);if(!packets.isEmpty())throw new IllegalStateException("PACKET_EARLY_SUBMIT");});
            if(!errors.isEmpty()||packets.size()!=60||v.anchorCount()!=0||v.sampleSlots()!=60)throw new IllegalStateException("PACKET_CREATE:"+errors);
            for(var p:packets)if(p.maxDuration!=HealingWorldParticleFrame.LIFETIME||p.scale!=1||p.color!=null||p.computeSize()<=0||p.rotation.yaw!=0)
                throw new IllegalStateException("PACKET_CONSTRUCTION");
            packets.clear();
            processing.accept(buffer->v.present(store,buffer,context,frame.apply(18d),.05,receipt));
            if(!packets.isEmpty())throw new IllegalStateException("PACKET_CADENCE");
            processing.accept(buffer->v.present(store,buffer,context,frame.apply(18d),.1,receipt));
            if(packets.size()!=60||Math.abs(packets.get(57).position.x-.6)>1e-8)throw new IllegalStateException("PACKET_FORWARD_FLOW");
            packets.clear();
            var branches=new ArrayList<TetherVisualSegment>();for(int i=0;i<6;i++)branches.add(new TetherVisualSegment("branch-"+i,frame.apply(100d).getFirst().shape(),"recipient-"+i));
            processing.accept(buffer->v.present(store,buffer,context,branches,1,receipt));
            if(!errors.isEmpty()||packets.size()!=402||v.sampleSlots()!=402||v.anchorCount()!=0)throw new IllegalStateException("PACKET_BRANCH_BOUND");
            for(int i=0;i<6;i++)if(packets.get(i*67).position.x!=0||packets.get(i*67+63).position.x!=100)throw new IllegalStateException("PACKET_ENDPOINT");
            var root=v.roots.get(context.skillInstanceId());var untouched=root.segments.get("branch-1");var replaced=root.segments.get("branch-0");
            packets.clear();branches.set(0,new TetherVisualSegment("branch-0",branches.getFirst().shape(),"new-recipient"));
            processing.accept(buffer->v.present(store,buffer,context,branches,1.01,receipt));
            if(!packets.isEmpty()||root.segments.get("branch-1")!=untouched||root.segments.get("branch-0")==replaced)throw new IllegalStateException("PACKET_BRANCH_IDENTITY");
            processing.accept(buffer->v.present(store,buffer,context,branches,1.11,receipt));
            if(packets.size()!=402)throw new IllegalStateException("PACKET_BRANCH_RESUME");
            processing.accept(buffer->v.remove(context,buffer));
            if(v.rootCount()!=0||v.sampleSlots()!=0||!v.pending.isEmpty())throw new IllegalStateException("PACKET_CLEANUP");
            packets.clear();
            processing.accept(buffer->{v.present(store,buffer,context,frame.apply(2d),5,receipt);v.remove(context,buffer);});
            if(!packets.isEmpty()||v.rootCount()!=0)throw new IllegalStateException("PACKET_CANCEL_RESURRECTION");
            if(!errors.isEmpty())throw new IllegalStateException("PACKET_FAILURE:"+errors);
            if(store.getEntityCount()!=entityCount)throw new IllegalStateException("PACKET_VISUAL_ENTITY_LEAK");
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log("RPG_HEAL_WORLD_PARTICLE_NATIVE revision=R032-AO result=PASS packetConstruction=true cadence=true endpoints=true branches=true noEntities=true sameBufferCancel=true cleanup=true connectedProof=false");
        }finally{v.cancel(context.request().actorId(),null);}
    }
}
