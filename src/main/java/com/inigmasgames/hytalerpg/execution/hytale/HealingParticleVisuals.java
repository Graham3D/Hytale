package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.model.config.*;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.*;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.connection.ConnectionWorldPort.TetherVisualSegment;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;
import java.util.function.BiConsumer;
import org.joml.Vector3d;

/** Presentation only. One persistent, unsaved model emitter per logical segment.
 * No native Beam ribbons, world particle bursts, stats, combat effects or target selection.
 * The installed particle protocol cannot bind an animated item node AND a destination.
 * Stream transforms therefore use authoritative spatial anchors, not a fabricated staff-tip pose. */
public final class HealingParticleVisuals {
    private final boolean effectsOnly;
    private final HealingChannelAudio audio=new HealingChannelAudio();
    public HealingParticleVisuals(){this(false);}
    HealingParticleVisuals(boolean effectsOnly){this.effectsOnly=effectsOnly;}
    public static final String ASSET_ID="Beam_Heal_Green2";
    public static final String MODEL_ID="RPG_Healing_Stream";
    public static final String RECIPIENT_EFFECT="RPG_Healing_Recipient";
    private static final Map<String,String> STAFF_EFFECTS=loadStaffEffects();
    public static String staffEffect(String itemId){return STAFF_EFFECTS.get(itemId);}
    private static Map<String,String> loadStaffEffects(){
        try(var input=HealingParticleVisuals.class.getResourceAsStream("/rpg/presentation/staff-heads-ag.json")){
            if(input==null)throw new IllegalStateException("HEAL_STAFF_MANIFEST_MISSING");
            var json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var result=new HashMap<String,String>();
            json.entrySet().forEach(e->result.put(e.getKey(),"RPG_Healing_Staff_"+e.getValue().getAsJsonObject().get("node").getAsString()));
            return Map.copyOf(result);
        }catch(java.io.IOException failure){throw new IllegalStateException("HEAL_STAFF_MANIFEST_READ",failure);}
    }
    public static final int MAX_ROOTS=512,MAX_SEGMENTS=6;
    private final Map<String,Root> roots=new HashMap<>();
    private final Map<String,Job> pending=new HashMap<>();
    private static final class Root {
        final UUID owner; final Store<EntityStore> store;
        final Map<String,Ref<EntityStore>> carriers=new HashMap<>();
        final Set<UUID> recipients=new HashSet<>();
        BiConsumer<String,Throwable> receipt; boolean updated; String staffEffect;
        Root(UUID owner,Store<EntityStore> store){this.owner=owner;this.store=store;}
    }
    private record Job(Store<EntityStore> store,SkillExecutionContext context,List<TetherVisualSegment> frame,BiConsumer<String,Throwable> receipt){}
    public synchronized void present(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,SkillExecutionContext c,
            List<TetherVisualSegment> frame,double now,BiConsumer<String,Throwable> receipt){
        if(frame==null||frame.isEmpty()||frame.size()>MAX_SEGMENTS||!Double.isFinite(now))throw new IllegalArgumentException("HEAL_PARTICLE_FRAME_INVALID");
        var key=c.skillInstanceId();
        if(!pending.containsKey(key)&&pending.size()>=MAX_ROOTS)throw new IllegalStateException("HEAL_PARTICLE_PENDING_CAPACITY");
        var job=new Job(store,c,List.copyOf(frame),receipt);
        var previous=pending.put(key,job);
        if(previous==null||previous.store!=store){try{mutate(store,buffer,()->drain(key,store));}catch(RuntimeException failure){pending.remove(key,job);throw failure;}}
    }
    private static void mutate(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Runnable work){
        if(buffer!=null&&buffer.getStore()==store)buffer.run(ignored->work.run());
        else if(store.isInThread()&&!store.isProcessing())work.run();
        else store.getExternalData().getWorld().execute(work);
    }
    private synchronized void drain(String key,Store<EntityStore> store){
        var job=pending.get(key);if(job==null||job.store!=store)return;pending.remove(key);
        HealingPresentationProbe.production("FRAME_REQUESTED",store,job.context.request().actorId(),key,Map.of(),Set.of(),null);
        try{apply(key,job);}catch(RuntimeException failure){
            HealingPresentationProbe.production("FRAME_FAILED",store,job.context.request().actorId(),key,Map.of(),Set.of(),null);
            try{remove(key,null);}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
            job.receipt.accept("HEAL_PARTICLE_FAILED",failure);
        }
    }
    private void apply(String key,Job job){
        var root=roots.get(key);boolean created=root==null;
        if(root!=null&&root.store!=job.store){remove(key,null);root=null;created=true;}
        if(root==null){if(roots.size()>=MAX_ROOTS)throw new IllegalStateException("HEAL_PARTICLE_ROOT_CAPACITY");
            root=new Root(job.context.request().actorId(),job.store);roots.put(key,root);}
        root.receipt=job.receipt;
        var ids=new HashSet<String>();var recipients=new HashSet<UUID>();
        for(var segment:job.frame){
            if(!ids.add(segment.id()))throw new IllegalArgumentException("HEAL_PARTICLE_DUPLICATE_SEGMENT");
            if(!effectsOnly){
                var ref=root.carriers.get(segment.id());
                if(ref==null){ref=spawn(job.store,segment.shape().start(),segment.shape().end());root.carriers.put(segment.id(),ref);}
                else update(job.store,ref,segment.shape().start(),segment.shape().end());
            }
            if(segment.recipient()!=null)recipients.add(UUID.fromString(segment.recipient()));
        }
        for(var id:new HashSet<>(root.carriers.keySet()))if(!ids.contains(id))destroy(root.store,root.carriers.remove(id));
        var old=new HashSet<>(root.recipients);root.recipients.clear();root.recipients.addAll(recipients);
        for(var id:old)if(!recipients.contains(id))releaseRecipient(root.store,id);
        for(var id:recipients){
            var ref=job.store.getExternalData().getRefFromUUID(id);if(ref==null||!ref.isValid())continue;
            var effects=job.store.getComponent(ref,EffectControllerComponent.getComponentType());
            if(effects==null)throw new IllegalStateException("HEAL_RECIPIENT_EFFECT_CONTROLLER_MISSING");
            // Cosmetic lease only; no Health check. Finite fallback cannot survive an abandoned channel forever.
            if(!effects.addEffect(ref,EntityEffect.getAssetMap().getAsset(RECIPIENT_EFFECT),.3f,OverlapBehavior.OVERWRITE,job.store))
                throw new IllegalStateException("HEAL_RECIPIENT_PARTICLE_REJECTED");
        }
        // Only an active Healing Beam frame owns this short cosmetic lease.
        // Item assets no longer attach Staff_Bronze while merely holding a staff.
        var hand=job.context.equipment()==null?null:job.context.equipment().mainHand();
        String next=hand==null?null:staffEffect(hand.itemId());
        var previous=root.staffEffect;root.staffEffect=next;
        if(previous!=null&&!previous.equals(next))releaseStaff(root.store,root.owner,previous);
        if(next!=null)renewStaff(root.store,root.owner,next);
        if(effectsOnly)try{audio.start(root.store,root.owner,key);}catch(RuntimeException failure){job.receipt.accept("HEAL_AUDIO_FAILED",failure);}
        HealingPresentationProbe.production("FRAME_READY",root.store,root.owner,key,root.carriers,root.recipients,root.staffEffect);
        if(created)job.receipt.accept("HEAL_PARTICLE_STARTED",null);
        else if(!root.updated){root.updated=true;job.receipt.accept("HEAL_PARTICLE_UPDATED",null);}
    }
    private static Ref<EntityStore> spawn(Store<EntityStore> store,Vec3 from,Vec3 to){
        return spawnCarrier(store,from,to,MODEL_ID);
    }
    /** Shared construction boundary; probe selects only its declared comparison model. */
    static Ref<EntityStore> spawnCarrier(Store<EntityStore> store,Vec3 from,Vec3 to,String modelId){
        var asset=ModelAsset.getAssetMap().getAsset(modelId);if(asset==null)throw new IllegalStateException("HEAL_PARTICLE_MODEL_MISSING");
        var model=Model.createStaticScaledModel(asset,1);var holder=EntityStore.REGISTRY.newHolder();
        holder.addComponent(UUIDComponent.getComponentType(),new UUIDComponent(UUID.randomUUID()));
        holder.addComponent(NetworkId.getComponentType(),new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.addComponent(TransformComponent.getComponentType(),new TransformComponent(vector(from),rotation(from,to)));
        holder.addComponent(ModelComponent.getComponentType(),new ModelComponent(model));
        holder.addComponent(BoundingBox.getComponentType(),new BoundingBox(model.getBoundingBox()));
        holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
        return store.addEntity(holder,AddReason.SPAWN);
    }
    static void update(Store<EntityStore> store,Ref<EntityStore> ref,Vec3 from,Vec3 to){
        if(!ref.isValid())throw new IllegalStateException("HEAL_PARTICLE_CARRIER_INVALID");
        var transform=store.getComponent(ref,TransformComponent.getComponentType());
        transform.setPosition(vector(from));transform.setRotation(rotation(from,to));
    }
    public static Rotation3f rotation(Vec3 from,Vec3 to){return NativeBeamTransform.rotation(from,to);}
    private static Vector3d vector(Vec3 v){return new Vector3d(v.x(),v.y(),v.z());}
    private static void destroy(Store<EntityStore> store,Ref<EntityStore> ref){if(ref!=null&&ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);}
    private void releaseRecipient(Store<EntityStore> store,UUID id){
        if(roots.values().stream().anyMatch(r->r.store==store&&r.recipients.contains(id)))return;
        var ref=store.getExternalData().getRefFromUUID(id);if(ref==null||!ref.isValid())return;
        var effects=store.getComponent(ref,EffectControllerComponent.getComponentType());
        if(effects!=null)effects.removeEffect(ref,EntityEffect.getAssetMap().getIndex(RECIPIENT_EFFECT),store);
    }
    private static void renewStaff(Store<EntityStore> store,UUID owner,String effect){
        var ref=store.getExternalData().getRefFromUUID(owner);if(ref==null||!ref.isValid())return;
        var effects=store.getComponent(ref,EffectControllerComponent.getComponentType());
        if(effects!=null&&!effects.addEffect(ref,EntityEffect.getAssetMap().getAsset(effect),.3f,OverlapBehavior.OVERWRITE,store))
            throw new IllegalStateException("HEAL_STAFF_PARTICLE_REJECTED");
    }
    private void releaseStaff(Store<EntityStore> store,UUID owner,String effect){
        if(roots.values().stream().anyMatch(r->r.store==store&&r.owner.equals(owner)&&effect.equals(r.staffEffect)))return;
        var ref=store.getExternalData().getRefFromUUID(owner);if(ref==null||!ref.isValid())return;
        var effects=store.getComponent(ref,EffectControllerComponent.getComponentType());
        if(effects!=null)effects.removeEffect(ref,EntityEffect.getAssetMap().getIndex(effect),store);
    }
    public synchronized void remove(SkillExecutionContext c,CommandBuffer<EntityStore> buffer){if(c!=null)remove(c.skillInstanceId(),buffer);}
    private void remove(String key,CommandBuffer<EntityStore> buffer){
        pending.remove(key);var root=roots.remove(key);if(root==null)return;
        mutate(root.store,buffer,()->{synchronized(this){
            if(effectsOnly)try{audio.stop(key);}catch(RuntimeException failure){if(root.receipt!=null)root.receipt.accept("HEAL_AUDIO_FAILED",failure);}
            for(var ref:root.carriers.values())destroy(root.store,ref);
            for(var id:root.recipients)releaseRecipient(root.store,id);
            if(root.staffEffect!=null)releaseStaff(root.store,root.owner,root.staffEffect);
            HealingPresentationProbe.production("REMOVED",root.store,root.owner,key,root.carriers,root.recipients,root.staffEffect);
            if(root.receipt!=null)root.receipt.accept("HEAL_PARTICLE_REMOVED",null);
        }});
    }
    public synchronized void cancel(UUID owner,CommandBuffer<EntityStore> buffer){
        pending.entrySet().removeIf(e->e.getValue().context.request().actorId().equals(owner));
        for(var key:roots.entrySet().stream().filter(e->e.getValue().owner.equals(owner)).map(Map.Entry::getKey).toList())remove(key,buffer);
    }
    synchronized int carrierCount(){return roots.values().stream().mapToInt(root->root.carriers.size()).sum();}
    /** Runs the retained legacy scheduler/model construction path only in the isolated native audit. */
    static void audit(Store<EntityStore> store,SkillExecutionContext c){
        if(!Boolean.getBoolean("rpg.projectileSpawnAudit"))throw new IllegalStateException("ISOLATED_AUDIT_DISABLED");
        var v=new HealingParticleVisuals();var failures=new ArrayList<Throwable>();var events=new ArrayList<String>();
        BiConsumer<String,Throwable> receipt=(event,error)->{events.add(event);if(error!=null)failures.add(error);};
        var frame=List.of(new TetherVisualSegment("audit",com.inigmasgames.hytalerpg.execution.connection.ConnectionShape.line(new Vec3(0,202,0),new Vec3(4,202,0),.2,1)));
        java.util.function.Consumer<java.util.function.Consumer<CommandBuffer<EntityStore>>> processing=work->{
            var once=new java.util.concurrent.atomic.AtomicBoolean();store.forEachChunk((java.util.function.BiConsumer<ArchetypeChunk<EntityStore>,CommandBuffer<EntityStore>>)(chunk,buffer)->{
                if(once.compareAndSet(false,true)){if(!store.isProcessing())throw new IllegalStateException("PARTICLE_AUDIT_NOT_PROCESSING");work.accept(buffer);}
            });if(!once.get())throw new IllegalStateException("PARTICLE_AUDIT_NO_ARCHETYPE");
        };
        Ref<EntityStore> recipientRef=null;
        try{
            processing.accept(b->{v.present(store,b,c,frame,0,receipt);if(!v.roots.isEmpty())throw new IllegalStateException("PARTICLE_EARLY_MUTATION");});
            if(!failures.isEmpty()||!events.contains("HEAL_PARTICLE_STARTED"))throw new IllegalStateException("PARTICLE_CREATE:"+failures);
            var ref=v.roots.get(c.skillInstanceId()).carriers.get("audit");var model=store.getComponent(ref,ModelComponent.getComponentType());
            if(model==null||!"Items/Projectiles/Projectile_default.png".equals(model.getModel().toPacket().texture)
                    ||!ASSET_ID.equals(model.getModel().getParticles()[0].getSystemId())||store.getComponent(ref,EntityStore.REGISTRY.getNonSerializedComponentType())==null)
                throw new IllegalStateException("PARTICLE_MODEL_CONTRACT");
            processing.accept(b->v.present(store,b,c,frame,.1,receipt));
            if(!failures.isEmpty()||v.roots.get(c.skillInstanceId()).carriers.get("audit")!=ref||store.getComponent(ref,ModelComponent.getComponentType())!=model)
                throw new IllegalStateException("PARTICLE_RECONSTRUCTED");
            processing.accept(b->v.remove(c,b));if(ref.isValid())throw new IllegalStateException("PARTICLE_ORPHAN");
            processing.accept(b->{v.present(store,b,c,frame,.2,receipt);v.remove(c,b);});
            if(!v.roots.isEmpty()||!v.pending.isEmpty())throw new IllegalStateException("PARTICLE_CANCEL_RESURRECTION");
            var id=UUID.randomUUID();var holder=EntityStore.REGISTRY.newHolder();
            holder.addComponent(UUIDComponent.getComponentType(),new UUIDComponent(id));
            holder.addComponent(TransformComponent.getComponentType(),new TransformComponent(new Vector3d(4,202,0),new Rotation3f()));
            holder.addComponent(EffectControllerComponent.getComponentType(),new EffectControllerComponent());
            holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
            recipientRef=store.addEntity(holder,AddReason.SPAWN);
            var effectController=store.getComponent(recipientRef,EffectControllerComponent.getComponentType());
            var targetFrame=List.of(new TetherVisualSegment("target",frame.getFirst().shape(),id.toString()));
            var second=new SkillExecutionContext(c.request(),c.rootCastId()+"-other",c.skillInstanceId()+"-other",c.profile(),c.compiledPlan(),c.snapshot(),c.equipment());
            processing.accept(b->{v.present(store,b,c,targetFrame,.3,receipt);v.present(store,b,second,targetFrame,.3,receipt);});
            int index=EntityEffect.getAssetMap().getIndex(RECIPIENT_EFFECT);
            if(!failures.isEmpty()||!effectController.hasEffect(index))throw new IllegalStateException("PARTICLE_RECIPIENT_ATTACH:"+failures);
            processing.accept(b->v.remove(c,b));
            if(!effectController.hasEffect(index))throw new IllegalStateException("PARTICLE_OTHER_ROOT_CLEARED");
            processing.accept(b->v.remove(second,b));
            if(effectController.hasEffect(index)||!v.roots.isEmpty())throw new IllegalStateException("PARTICLE_RECIPIENT_ORPHAN");
            var caster=store.getExternalData().getRefFromUUID(c.request().actorId());
            var casterEffects=new EffectControllerComponent();
            store.addComponent(caster,EffectControllerComponent.getComponentType(),casterEffects);
            var equipment=new com.inigmasgames.hytalerpg.execution.SkillExecutionPort.Equipment(
                    new com.inigmasgames.hytalerpg.execution.SkillExecutionPort.Item("Weapon_Staff_Bronze","STAFF",null),null);
            var staffContext=new SkillExecutionContext(c.request(),c.rootCastId(),c.skillInstanceId(),c.profile(),c.compiledPlan(),c.snapshot(),equipment);
            int staffIndex=EntityEffect.getAssetMap().getIndex(staffEffect("Weapon_Staff_Bronze"));
            if(casterEffects.hasEffect(staffIndex))throw new IllegalStateException("STAFF_EFFECT_BEFORE_CHANNEL");
            processing.accept(b->v.present(store,b,staffContext,targetFrame,.4,receipt));
            if(!failures.isEmpty()||!casterEffects.hasEffect(staffIndex))throw new IllegalStateException("STAFF_EFFECT_NOT_ATTACHED:"+failures);
            processing.accept(b->v.remove(staffContext,b));
            if(casterEffects.hasEffect(staffIndex))throw new IllegalStateException("STAFF_EFFECT_ORPHAN");
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log("RPG_HEAL_STAFF_NATIVE result=PASS channelOnly=true removed=true connectedProof=false");
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log("RPG_HEAL_PARTICLE_NATIVE_INTEGRATION result=PASS asset=Beam_Heal_Green2 create=true persistentModel=true update=true remove=true sameBufferCancel=true recipientAttached=true sharedRecipientCleanup=true connectedProof=false");
        }finally{v.cancel(c.request().actorId(),null);destroy(store,recipientRef);}
    }
}
