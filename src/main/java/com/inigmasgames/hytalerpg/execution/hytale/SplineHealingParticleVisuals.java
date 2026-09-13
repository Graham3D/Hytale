package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.connection.ConnectionWorldPort.TetherVisualSegment;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;
import java.util.function.BiConsumer;

/** Native particle anchors, NOT native Beams. All placement is driven by the RPG visual path.
 * Stock child sprites/animation are retained, with zero autonomous velocity and full anchor following.
 * A persistent sample pool forms the body; finite travelling pulse anchors retire at the endpoint. */
public final class SplineHealingParticleVisuals {
    public static final String BLIPS="RPG_Heal_Path_Blips",PULSE="RPG_Heal_Path_Pulse";
    public static final int MAX_SEGMENTS=6,MAX_ROOTS=512,MAX_ANCHORS=2048;
    private final Map<String,Root> roots=new HashMap<>();
    private final Map<String,Job> pending=new HashMap<>();
    private int anchors;
    private record Job(Store<EntityStore> store,SkillExecutionContext context,List<TetherVisualSegment> frame,double now,BiConsumer<String,Throwable> receipt){}
    private static final class Root {
        final UUID owner;final Store<EntityStore> store;final Map<String,Segment> segments=new HashMap<>();
        BiConsumer<String,Throwable> receipt;boolean updated;
        Root(UUID owner,Store<EntityStore> store){this.owner=owner;this.store=store;}
    }
    private static final class Segment {
        final String recipient;final ElasticBeamTether motion=new ElasticBeamTether();
        final List<Ref<EntityStore>> blips=new ArrayList<>();
        final Ref<EntityStore>[] pulses=new Ref[HealingParticlePath.PULSES];
        final double[] arcs=new double[HealingParticlePath.PULSES];
        double last=Double.NaN;
        Segment(String recipient){this.recipient=recipient;}
    }
    public synchronized int rootCount(){return roots.size();}
    public synchronized int anchorCount(){return anchors;}
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
        try{apply(key,job);}catch(RuntimeException failure){try{remove(key,null);}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}job.receipt.accept("PARTICLE_PATH_FAILED",failure);}
    }
    private void apply(String key,Job job){
        var root=roots.get(key);boolean created=root==null;
        if(root!=null&&root.store!=job.store){remove(key,null);root=null;created=true;}
        if(root==null){if(roots.size()>=MAX_ROOTS)throw new IllegalStateException("HEAL_PATH_ROOT_CAPACITY");root=new Root(job.context.request().actorId(),job.store);roots.put(key,root);}
        root.receipt=job.receipt;var ids=new HashSet<String>();
        for(var requested:job.frame){
            if(!ids.add(requested.id()))throw new IllegalArgumentException("HEAL_PATH_DUPLICATE_ID");
            var segment=root.segments.get(requested.id());
            if(segment!=null&&!Objects.equals(segment.recipient,requested.recipient())){destroy(root.store,segment);root.segments.remove(requested.id());segment=null;}
            if(segment==null){segment=new Segment(requested.recipient());root.segments.put(requested.id(),segment);}
            update(root.store,segment,requested.shape().start(),requested.shape().end(),job.now);
        }
        for(var id:new HashSet<>(root.segments.keySet()))if(!ids.contains(id))destroy(root.store,root.segments.remove(id));
        // Existing optional observer only: normal tracing remains event-driven, not one event per sample/tick.
        var refs=new HashMap<String,Ref<EntityStore>>();
        root.segments.forEach((id,s)->{if(!s.blips.isEmpty())refs.put(id+"/blip",s.blips.getFirst());if(s.pulses[0]!=null)refs.put(id+"/pulse",s.pulses[0]);});
        HealingPresentationProbe.production("PARTICLE_PATH_FRAME_READY",root.store,root.owner,key,refs,Set.of(),null);
        if(created)job.receipt.accept("PARTICLE_PATH_STARTED",null);else if(!root.updated){root.updated=true;job.receipt.accept("PARTICLE_PATH_UPDATED",null);}
    }
    private void update(Store<EntityStore> store,Segment s,Vec3 start,Vec3 end,double now){
        var path=new HealingParticlePath(s.motion.update(start,end,now));int count=path.blips();
        while(s.blips.size()>count)destroy(store,s.blips.removeLast());
        for(int i=0;i<count;i++){
            Vec3 position=path.at(path.length()*i/(count-1));
            if(i==s.blips.size())s.blips.add(spawn(store,position,BLIPS));else place(store,s.blips.get(i),position);
        }
        boolean reset=Double.isNaN(s.last)||now<s.last||now-s.last>.25;
        double dt=reset?0:now-s.last;s.last=now;
        for(int i=0;i<HealingParticlePath.PULSES;i++){
            if(count==0){destroy(store,s.pulses[i]);s.pulses[i]=null;continue;}
            if(reset)s.arcs[i]=path.length()*i/HealingParticlePath.PULSES;
            else s.arcs[i]+=HealingParticlePath.PULSE_SPEED*dt;
            if(s.arcs[i]>=path.length()){
                // Retire at target instead of client-interpolating the same entity backward along the beam.
                s.arcs[i]%=path.length();destroy(store,s.pulses[i]);s.pulses[i]=null;
            }
            var position=path.at(s.arcs[i]);
            if(s.pulses[i]==null)s.pulses[i]=spawn(store,position,PULSE);else place(store,s.pulses[i],position);
        }
    }
    private Ref<EntityStore> spawn(Store<EntityStore> store,Vec3 position,String model){
        if(anchors>=MAX_ANCHORS)throw new IllegalStateException("HEAL_PATH_VISUAL_CAPACITY");
        var ref=HealingParticleVisuals.spawnCarrier(store,position,position.add(Vec3.FORWARD),model);anchors++;return ref;
    }
    private static void place(Store<EntityStore> store,Ref<EntityStore> ref,Vec3 position){
        if(ref==null||!ref.isValid())throw new IllegalStateException("HEAL_PATH_ANCHOR_INVALID");
        var transform=store.getComponent(ref,TransformComponent.getComponentType());if(transform==null)throw new IllegalStateException("HEAL_PATH_TRANSFORM_MISSING");
        transform.setPosition(new org.joml.Vector3d(position.x(),position.y(),position.z()));
    }
    private void destroy(Store<EntityStore> store,Ref<EntityStore> ref){if(ref!=null){if(ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);anchors--;}}
    private void destroy(Store<EntityStore> store,Segment s){for(var ref:s.blips)destroy(store,ref);s.blips.clear();for(int i=0;i<s.pulses.length;i++){destroy(store,s.pulses[i]);s.pulses[i]=null;}}
    public synchronized void remove(SkillExecutionContext context,CommandBuffer<EntityStore> buffer){if(context!=null)remove(context.skillInstanceId(),buffer);}
    private void remove(String key,CommandBuffer<EntityStore> buffer){
        pending.remove(key);var root=roots.remove(key);if(root==null)return;
        mutate(root.store,buffer,()->{synchronized(this){for(var s:root.segments.values())destroy(root.store,s);root.segments.clear();if(root.receipt!=null)root.receipt.accept("PARTICLE_PATH_REMOVED",null);}});
    }
    public synchronized void cancel(UUID owner,CommandBuffer<EntityStore> buffer){
        pending.entrySet().removeIf(e->e.getValue().context.request().actorId().equals(owner));
        for(var key:roots.entrySet().stream().filter(e->e.getValue().owner.equals(owner)).map(Map.Entry::getKey).toList())remove(key,buffer);
    }
    /** Isolated real native model/particle construction and ECS ordering, not client rendering proof. */
    static void audit(Store<EntityStore> store,SkillExecutionContext context){
        if(!Boolean.getBoolean("rpg.projectileSpawnAudit"))throw new IllegalStateException("ISOLATED_AUDIT_DISABLED");
        var v=new SplineHealingParticleVisuals();var errors=new ArrayList<Throwable>();var events=new ArrayList<String>();
        BiConsumer<String,Throwable> receipt=(event,error)->{events.add(event);if(error!=null)errors.add(error);};
        java.util.function.Consumer<java.util.function.Consumer<CommandBuffer<EntityStore>>> processing=work->{
            var once=new java.util.concurrent.atomic.AtomicBoolean();store.forEachChunk((java.util.function.BiConsumer<ArchetypeChunk<EntityStore>,CommandBuffer<EntityStore>>)(chunk,buffer)->{if(once.compareAndSet(false,true)){if(!store.isProcessing())throw new IllegalStateException("PARTICLE_PATH_NOT_PROCESSING");work.accept(buffer);}});if(!once.get())throw new IllegalStateException("PARTICLE_PATH_NO_CHUNK");
        };
        java.util.function.Function<Double,List<TetherVisualSegment>> frame=length->List.of(new TetherVisualSegment("primary",com.inigmasgames.hytalerpg.execution.connection.ConnectionShape.line(new Vec3(0,202,0),new Vec3(length,202,0),.2,1)));
        try{
            processing.accept(buffer->{v.present(store,buffer,context,frame.apply(18d),0,receipt);if(v.anchors!=0)throw new IllegalStateException("PARTICLE_PATH_EARLY_MUTATION");});
            if(!errors.isEmpty()||v.anchors!=94)throw new IllegalStateException("PARTICLE_PATH_CREATE:"+errors+" anchors="+v.anchors);
            var s=v.roots.get(context.skillInstanceId()).segments.get("primary");var refs=List.copyOf(s.blips);
            var firstModel=store.getComponent(refs.getFirst(),ModelComponent.getComponentType());
            for(var ref:refs){
                if(store.getComponent(ref,EntityStore.REGISTRY.getNonSerializedComponentType())==null||store.getComponent(ref,com.hypixel.hytale.builtin.beam.BeamComponent.getComponentType())!=null)throw new IllegalStateException("PARTICLE_PATH_NATIVE_CONTRACT");
                var p=store.getComponent(ref,ModelComponent.getComponentType()).getModel().toPacket().particles[0];
                if(!BLIPS.equals(p.systemId)||p.scale!=1||p.detachedFromModel||!p.clearParticlesOnRemove)throw new IllegalStateException("PARTICLE_PATH_MODEL_CONTRACT");
            }
            processing.accept(buffer->v.present(store,buffer,context,frame.apply(18d),.1,receipt));
            if(!errors.isEmpty()||!s.blips.equals(refs)||store.getComponent(refs.getFirst(),ModelComponent.getComponentType())!=firstModel||Math.abs(s.arcs[0]-.6)>1e-9)throw new IllegalStateException("PARTICLE_PATH_UPDATE");
            for(double length:new double[]{2,6,12,18,25.2}){
                processing.accept(buffer->v.present(store,buffer,context,frame.apply(length),10+length,receipt));
                var last=store.getComponent(s.blips.getLast(),TransformComponent.getComponentType()).getPosition();
                if(Math.abs(last.x-length)>1e-8||v.anchors>HealingParticlePath.MAX_BLIPS+3)throw new IllegalStateException("PARTICLE_PATH_ENDPOINT");
            }
            processing.accept(buffer->v.remove(context,buffer));if(v.anchors!=0||v.rootCount()!=0||refs.stream().anyMatch(Ref::isValid))throw new IllegalStateException("PARTICLE_PATH_ORPHAN");
            processing.accept(buffer->v.present(store,buffer,context,frame.apply(2d),60,receipt));
            var finite=v.roots.get(context.skillInstanceId()).segments.get("primary");var pulse=finite.pulses[0];var body=List.copyOf(finite.blips);
            for(int step=1;step<=4;step++){final double now=60+step*.1;processing.accept(buffer->v.present(store,buffer,context,frame.apply(2d),now,receipt));}
            if(pulse.isValid()||!finite.blips.equals(body)||finite.pulses[0]==pulse||Math.abs(finite.arcs[0]-.4)>1e-8)throw new IllegalStateException("PARTICLE_PATH_PULSE_TERMINATION");
            var branches=new ArrayList<TetherVisualSegment>();for(int i=0;i<6;i++)branches.add(new TetherVisualSegment("branch-"+i,frame.apply(2d).getFirst().shape(),"recipient-"+i));
            processing.accept(buffer->v.present(store,buffer,context,branches,61,receipt));
            if(!errors.isEmpty()||v.anchors!=84)throw new IllegalStateException("PARTICLE_PATH_BRANCH_BUDGET");
            var root=v.roots.get(context.skillInstanceId());var firstBranch=root.segments.get("branch-0");var untouched=root.segments.get("branch-1");
            var oldBranchRefs=List.copyOf(firstBranch.blips);
            branches.set(0,new TetherVisualSegment("branch-0",branches.getFirst().shape(),"different-recipient"));
            processing.accept(buffer->v.present(store,buffer,context,branches,61.05,receipt));
            if(root.segments.get("branch-0")==firstBranch||root.segments.get("branch-1")!=untouched||oldBranchRefs.stream().anyMatch(Ref::isValid))throw new IllegalStateException("PARTICLE_PATH_BRANCH_IDENTITY");
            processing.accept(buffer->v.remove(context,buffer));if(v.anchors!=0||v.rootCount()!=0)throw new IllegalStateException("PARTICLE_PATH_BRANCH_ORPHAN");
            processing.accept(buffer->{v.present(store,buffer,context,frame.apply(2d),50,receipt);v.remove(context,buffer);});
            if(v.anchors!=0||!v.pending.isEmpty())throw new IllegalStateException("PARTICLE_PATH_CANCEL_RESURRECTION");
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log("RPG_HEAL_PARTICLE_PATH_NATIVE revision=R032-AM result=PASS stockChildren=true noNativeBeam=true persistentBlips=true forwardPulses=true endpointDistances=2,6,12,18,25.2 nonSerialized=true sameBufferCancel=true cleanup=true pulseRetirement=true branchIdentity=true connectedProof=false");
        }finally{v.cancel(context.request().actorId(),null);}
    }
}
