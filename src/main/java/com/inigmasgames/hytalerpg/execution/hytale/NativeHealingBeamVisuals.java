package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.builtin.beam.AttachedBeam;
import com.hypixel.hytale.builtin.beam.BeamComponent;
import com.hypixel.hytale.builtin.beam.asset.Beam;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
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
    public static final String ASSET_ID="RPG_Healing";
    public static final int MAX_LOGICAL_SEGMENTS=6;
    private static final int MAX_ROOTS=512;
    private static final float SCALE=.16f;
    private final Map<String,RootVisual> roots=new HashMap<>();

    public synchronized void present(Store<EntityStore> store,SkillExecutionContext context,
                                     List<ConnectionWorldPort.TetherVisualSegment> frame,double now){
        if(store==null||context==null||frame==null||frame.isEmpty()||frame.size()>MAX_LOGICAL_SEGMENTS||!Double.isFinite(now))
            throw new IllegalArgumentException("Invalid native healing beam frame");
        String key=context.skillInstanceId();var root=roots.get(key);
        if(root==null){
            if(roots.size()>=MAX_ROOTS)throw new IllegalStateException("HEALING_BEAM_VISUAL_ROOT_CAPACITY");
            root=new RootVisual(context.request().actorId(),store);roots.put(key,root);
        }else if(root.store!=store){remove(key);root=new RootVisual(context.request().actorId(),store);roots.put(key,root);}
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
    }

    public synchronized void remove(SkillExecutionContext context){if(context!=null)remove(context.skillInstanceId());}
    public synchronized void cancel(UUID owner){
        var keys=roots.entrySet().stream().filter(e->e.getValue().owner.equals(owner)).map(Map.Entry::getKey).toList();
        for(var key:keys)remove(key);
    }
    public synchronized int rootCount(){return roots.size();}
    private void remove(String key){var root=roots.remove(key);if(root!=null)root.removeAll();}

    private static final class RootVisual {
        final UUID owner;final Store<EntityStore> store;final Map<String,Segment> segments=new HashMap<>();
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
            return BeamComponent.spawn(store,vector(from),AttachedBeam.toPosition(beamIndex,SCALE,SCALE,null,vector(to)));
        }
        private static void update(Store<EntityStore> store,Ref<EntityStore> ref,int beamIndex,Vec3 from,Vec3 to){
            if(ref==null||!ref.isValid())throw new IllegalStateException("HEALING_BEAM_VISUAL_ENTITY_INVALID");
            var transform=store.getComponent(ref,TransformComponent.getComponentType());
            var beams=store.getComponent(ref,BeamComponent.getComponentType());
            if(transform==null||beams==null)throw new IllegalStateException("HEALING_BEAM_VISUAL_COMPONENT_MISSING");
            transform.setPosition(vector(from));
            beams.set(List.of(AttachedBeam.toPosition(beamIndex,SCALE,SCALE,null,vector(to))));
        }
    }
    private static Vector3d vector(Vec3 value){return new Vector3d(value.x(),value.y(),value.z());}
}
