package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.builtin.beam.AttachedBeam;
import com.hypixel.hytale.builtin.beam.BeamComponent;
import com.hypixel.hytale.builtin.beam.asset.Beam;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.connection.ConnectionWorldPort;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.joml.Vector3d;

/** Persistent native-beam owner. Every spawned entity is non-serialized and removed with its root. */
public final class NativeHealingBeamVisuals {
    /** AE connected evidence proved ECS creation/update/removal. Use the dedicated magical trail now. */
    public static final String ASSET_ID="RPG_Healing";
    /** Native endpoint width multiplier, not a claimed world-space metre measurement. */
    public static final float WIDTH_SCALE=.025f;
    public static final int MAX_LOGICAL_SEGMENTS=6;
    private static final int MAX_ROOTS=512;
    private final Map<String,RootVisual> roots=new HashMap<>();
    private final Map<String,FrameJob> pending=new HashMap<>();

    /** Capture immutable presentation data while processing. Native mutation runs at
     * command-buffer consumption, after the Store processing guard is released. */
    public synchronized void present(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,SkillExecutionContext context,
            List<ConnectionWorldPort.TetherVisualSegment> frame,double now,java.util.function.BiConsumer<String,Throwable> result){
        String key=context.skillInstanceId();
        if(frame==null||frame.isEmpty()||frame.size()>MAX_LOGICAL_SEGMENTS||!Double.isFinite(now))
            throw new IllegalArgumentException("Invalid native healing beam frame");
        var prior=pending.get(key);
        if(prior!=null&&prior.store==store){prior.frame=List.copyOf(frame);prior.now=now;return;}
        if(pending.size()>=MAX_ROOTS)throw new IllegalStateException("HEALING_BEAM_PENDING_CAPACITY");
        var job=new FrameJob(store,context,List.copyOf(frame),now);pending.put(key,job);
        try{mutate(store,buffer,()->drain(key,job,result));}
        catch(RuntimeException failure){pending.remove(key,job);throw failure;}
    }
    private synchronized void drain(String key,FrameJob job,java.util.function.BiConsumer<String,Throwable> result){
        if(pending.get(key)!=job)return; // Release/world change invalidates work before it can create an orphan.
        pending.remove(key);
        try{
            boolean created=present(job.store,job.context,job.frame,job.now);
            var root=roots.get(key);
            root.receipt=result;
            if(created)result.accept("NATIVE_BEAM_STARTED",null);
            else if(!root.updateReported){root.updateReported=true;result.accept("NATIVE_BEAM_UPDATED",null);}
        }catch(RuntimeException failure){result.accept("NATIVE_BEAM_FAILED",failure);}
    }
    private static void mutate(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Runnable mutation){
        if(buffer!=null&&buffer.getStore()==store){buffer.run(ignored->mutation.run());return;}
        if(store.isInThread()&&!store.isProcessing())mutation.run();
        else store.getExternalData().getWorld().execute(mutation);
    }
    private static final class FrameJob {
        final Store<EntityStore> store;final SkillExecutionContext context;
        List<ConnectionWorldPort.TetherVisualSegment> frame;double now;
        FrameJob(Store<EntityStore> store,SkillExecutionContext context,List<ConnectionWorldPort.TetherVisualSegment> frame,double now){
            this.store=store;this.context=context;this.frame=frame;this.now=now;
        }
    }

    /** @return true when this call allocated a new persistent native visual root. */
    private synchronized boolean present(Store<EntityStore> store,SkillExecutionContext context,
                                     List<ConnectionWorldPort.TetherVisualSegment> frame,double now){
        if(store==null||context==null||frame==null||frame.isEmpty()||frame.size()>MAX_LOGICAL_SEGMENTS||!Double.isFinite(now))
            throw new IllegalArgumentException("Invalid native healing beam frame");
        String key=context.skillInstanceId();var root=roots.get(key);boolean created=false;
        if(root==null){
            if(roots.size()>=MAX_ROOTS)throw new IllegalStateException("HEALING_BEAM_VISUAL_ROOT_CAPACITY");
            root=new RootVisual(context.request().actorId(),store);roots.put(key,root);created=true;
        }else if(root.store!=store){remove(key);root=new RootVisual(context.request().actorId(),store);roots.put(key,root);created=true;}
        var live=new HashSet<String>();
        try{
            for(var requested:frame){
                if(!live.add(requested.id()))throw new IllegalArgumentException("Duplicate healing beam visual id");
                var shape=requested.shape();var segment=root.segments.computeIfAbsent(requested.id(),ignored->new Segment());
                segment.update(store,shape.start(),shape.end(),now);
            }
            var stale=new ArrayList<String>();
            for(var id:root.segments.keySet())if(!live.contains(id))stale.add(id);
            for(var id:stale)root.remove(id);
        }catch(RuntimeException failure){remove(key);throw failure;}
        return created;
    }

    public synchronized void remove(SkillExecutionContext context,CommandBuffer<EntityStore> buffer){
        if(context!=null)remove(context.skillInstanceId(),buffer);
    }
    public synchronized void cancel(UUID owner,CommandBuffer<EntityStore> buffer){
        pending.entrySet().removeIf(e->e.getValue().context.request().actorId().equals(owner));
        var keys=roots.entrySet().stream().filter(e->e.getValue().owner.equals(owner)).map(Map.Entry::getKey).toList();
        for(var key:keys)remove(key,buffer);
    }
    public synchronized int rootCount(){return roots.size();}
    private void remove(String key){remove(key,null);}
    private void remove(String key,CommandBuffer<EntityStore> buffer){
        pending.remove(key);var root=roots.remove(key);
        if(root!=null)mutate(root.store,buffer,()->{
            try{root.removeAll();if(root.receipt!=null)root.receipt.accept("NATIVE_BEAM_REMOVED",null);}
            catch(RuntimeException failure){if(root.receipt!=null)root.receipt.accept("NATIVE_BEAM_REMOVE_FAILED",failure);else throw failure;}
        });
    }

    private static final class RootVisual {
        final UUID owner;final Store<EntityStore> store;final Map<String,Segment> segments=new HashMap<>();
        boolean updateReported;
        java.util.function.BiConsumer<String,Throwable> receipt;
        RootVisual(UUID owner,Store<EntityStore> store){this.owner=owner;this.store=store;}
        void remove(String id){var segment=segments.remove(id);if(segment!=null)segment.remove(store);}
        void removeAll(){for(var segment:segments.values())segment.remove(store);segments.clear();}
    }

    private static final class Segment {
        final ElasticBeamTether motion=new ElasticBeamTether();final List<Ref<EntityStore>> pieces=new ArrayList<>(ElasticBeamTether.PIECES);
        void update(Store<EntityStore> store,Vec3 start,Vec3 end,double now){
            int beamIndex=Beam.getAssetMap().getIndex(ASSET_ID);
            if(beamIndex<0)throw new IllegalStateException("HEALING_BEAM_ASSET_UNRESOLVED");
            var points=motion.update(start,end,now);
            if(pieces.isEmpty())for(int i=0;i<ElasticBeamTether.PIECES;i++)pieces.add(spawn(store,beamIndex,points.get(i),points.get(i+1)));
            else for(int i=0;i<ElasticBeamTether.PIECES;i++)update(store,pieces.get(i),beamIndex,points.get(i),points.get(i+1));
        }
        void remove(Store<EntityStore> store){for(var ref:pieces)if(ref!=null&&ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);pieces.clear();}
        private static Ref<EntityStore> spawn(Store<EntityStore> store,int beamIndex,Vec3 from,Vec3 to){
            return BeamComponent.spawn(store,vector(from),attachment(beamIndex,to));
        }
        private static void update(Store<EntityStore> store,Ref<EntityStore> ref,int beamIndex,Vec3 from,Vec3 to){
            if(ref==null||!ref.isValid())throw new IllegalStateException("HEALING_BEAM_VISUAL_ENTITY_INVALID");
            var transform=store.getComponent(ref,TransformComponent.getComponentType());
            var beams=store.getComponent(ref,BeamComponent.getComponentType());
            if(transform==null||beams==null)throw new IllegalStateException("HEALING_BEAM_VISUAL_COMPONENT_MISSING");
            transform.setPosition(vector(from));
            beams.set(List.of(attachment(beamIndex,to)));
        }
    }
    public static AttachedBeam attachment(int beamIndex,Vec3 target){
        return AttachedBeam.toPosition(beamIndex,WIDTH_SCALE,WIDTH_SCALE,null,vector(target));
    }
    private static Vector3d vector(Vec3 value){return new Vector3d(value.x(),value.y(),value.z());}

    /** Actual processing-phase regression, registered only in the isolated empty-world audit. */
    static void audit(Store<EntityStore> store,SkillExecutionContext context){
        if(!Boolean.getBoolean("rpg.projectileSpawnAudit"))throw new IllegalStateException("ISOLATED_AUDIT_DISABLED");
        var visuals=new NativeHealingBeamVisuals();var failures=new ArrayList<Throwable>();var events=new ArrayList<String>();
        java.util.function.BiConsumer<String,Throwable> receipt=(event,error)->{events.add(event);if(error!=null)failures.add(error);};
        java.util.function.BiFunction<Vec3,Vec3,List<ConnectionWorldPort.TetherVisualSegment>> frame=(a,b)->List.of(
            new ConnectionWorldPort.TetherVisualSegment("primary",com.inigmasgames.hytalerpg.execution.connection.ConnectionShape.line(a,b,.2,1)));
        java.util.function.Consumer<java.util.function.Consumer<CommandBuffer<EntityStore>>> processing=action->{
            var once=new java.util.concurrent.atomic.AtomicBoolean();
            store.forEachChunk((java.util.function.BiConsumer<com.hypixel.hytale.component.ArchetypeChunk<EntityStore>,CommandBuffer<EntityStore>>)(chunk,buffer)->{
                if(once.compareAndSet(false,true)){if(!store.isProcessing())throw new IllegalStateException("BEAM_AUDIT_NOT_PROCESSING");action.accept(buffer);}
            });
            if(!once.get())throw new IllegalStateException("BEAM_AUDIT_NO_ARCHETYPE");
        };
        try{
            processing.accept(buffer->{
                try{var ref=BeamComponent.spawn(store,new Vector3d(0,202,0),AttachedBeam.toPosition(Beam.getAssetMap().getIndex(ASSET_ID),.25f,.25f,null,new Vector3d(4,202,0)));
                    buffer.tryRemoveEntity(ref,RemoveReason.REMOVE);throw new IllegalStateException("OLD_BEAM_GUARD_NOT_REPRODUCED");
                }catch(IllegalStateException expected){if(!expected.getMessage().startsWith("Store is currently processing"))throw expected;}
                visuals.present(store,buffer,context,frame.apply(new Vec3(0,202,0),new Vec3(4,202,0)),0,receipt);
                if(visuals.rootCount()!=0)throw new IllegalStateException("BEAM_CREATED_DURING_PROCESSING");
            });
            if(!failures.isEmpty()||!events.equals(List.of("NATIVE_BEAM_STARTED")))throw new IllegalStateException("BEAM_CREATE_FAILED:"+failures);
            var root=visuals.roots.get(context.skillInstanceId());var refs=List.copyOf(root.segments.get("primary").pieces);
            for(var ref:refs)if(!ref.isValid()||store.getComponent(ref,BeamComponent.getComponentType())==null
                    ||store.getComponent(ref,EntityStore.REGISTRY.getNonSerializedComponentType())==null)throw new IllegalStateException("BEAM_NOT_INSERTED");
            for(var ref:refs){
                var beam=store.getComponent(ref,BeamComponent.getComponentType()).getBeams().getFirst();
                if(beam.beamIndex()!=Beam.getAssetMap().getIndex(ASSET_ID)||beam.sourceScale()!=WIDTH_SCALE||beam.targetScale()!=WIDTH_SCALE)
                    throw new IllegalStateException("BEAM_APPEARANCE_CONTRACT");
            }
            var transform=store.getComponent(refs.getFirst(),TransformComponent.getComponentType());
            processing.accept(buffer->visuals.present(store,buffer,context,frame.apply(new Vec3(1,203,0),new Vec3(5,203,0)),.1,receipt));
            if(!failures.isEmpty()||!events.contains("NATIVE_BEAM_UPDATED")||!refs.equals(root.segments.get("primary").pieces)
                    ||store.getComponent(refs.getFirst(),TransformComponent.getComponentType())!=transform
                    ||transform.getPosition().distance(new Vector3d(1,203,0))>1e-9)throw new IllegalStateException("BEAM_UPDATE_FAILED");
            processing.accept(buffer->visuals.remove(context,buffer));
            if(visuals.rootCount()!=0||refs.stream().anyMatch(Ref::isValid)||!events.contains("NATIVE_BEAM_REMOVED"))throw new IllegalStateException("BEAM_REMOVE_FAILED");
            int receipts=events.size();
            processing.accept(buffer->{visuals.present(store,buffer,context,frame.apply(new Vec3(0,202,0),new Vec3(4,202,0)),.2,receipt);visuals.remove(context,buffer);});
            if(visuals.rootCount()!=0||!visuals.pending.isEmpty()||events.size()!=receipts)throw new IllegalStateException("BEAM_CANCELLED_FRAME_RECREATED");
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log("RPG_BEAM_NATIVE_INTEGRATION result=PASS asset=%s widthScale=%s oldProcessingGuard=true create=true update=true remove=true sameBufferCancel=true persistentRefs=true connectedProof=false",ASSET_ID,WIDTH_SCALE);
        }finally{visuals.cancel(context.request().actorId(),null);}
    }
}
