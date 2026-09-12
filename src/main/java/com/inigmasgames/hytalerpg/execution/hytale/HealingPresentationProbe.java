package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.tracker.*;
import com.hypixel.hytale.server.core.modules.entitystats.*;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.io.adapter.*;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates;
import com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem;
import com.hypixel.hytale.builtin.beam.*;
import com.hypixel.hytale.builtin.beam.asset.Beam;
import com.inigmasgames.hytalerpg.diagnostics.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.joml.Vector3d;

/** Gate A only. No combat/resource writes or production renderer switch.
 * AJ live effects are explicitly client-only controls; AK CHANNEL observes native production, never sends effects.
 * Snapshot native viewer queues non-destructively and observe outbound packets before transport.
 * OUTBOUND_OBSERVED is deliberately not PACKET_SENT or CLIENT_RENDERED. */
public final class HealingPresentationProbe implements AutoCloseable {
    private static final Set<HealingPresentationProbe> observers=ConcurrentHashMap.newKeySet();
    private final RpgSkillTracer trace;
    private final ConcurrentMap<UUID,Run> runs=new ConcurrentHashMap<>();
    private final Path allowedRoot;
    private final boolean liveTest;
    private final PacketFilter watcher;
    private volatile boolean closed;
    private static final class Run {
        final UUID owner,world,generation=UUID.randomUUID();
        final Store<EntityStore> store;final Ref<EntityStore> actor;final HealingProbePolicy.Mode mode;
        final long started=System.nanoTime();final String requestedTarget;
        final Set<String> receipts=ConcurrentHashMap.newKeySet();
        final Map<Integer,String> watched=new ConcurrentHashMap<>();
        final Map<Integer,Set<Integer>> expectedEffects=new ConcurrentHashMap<>();
        final AtomicLong packets=new AtomicLong(),componentUpdates=new AtomicLong();
        final AtomicLong suppressedTransitions=new AtomicLong();
        final Set<String> channels=new HashSet<>();
        Ref<EntityStore> target,carrier;String effect;Vec3 endpoint;boolean ownedTarget,created,queued,stopping;
        double nextSample,nextFrame;long stopped;int effectNetworkId=-1;
        Run(UUID owner,UUID world,Store<EntityStore> store,Ref<EntityStore> actor,HealingProbePolicy.Mode mode,String target){
            this.owner=owner;this.world=world;this.store=store;this.actor=actor;this.mode=mode;requestedTarget=target;
        }
    }
    public HealingPresentationProbe(RpgSkillTracer trace){
        this(trace,HealingProbePolicy.liveTestBuild());
    }
    HealingPresentationProbe(RpgSkillTracer trace,boolean liveTest){
        if(!liveTest&&!Boolean.getBoolean("rpg.healingPresentationProbe"))throw new IllegalStateException("HEAL_PROBE_DISABLED");
        this.liveTest=liveTest;
        this.trace=trace;allowedRoot=Path.of(System.getProperty("rpg.healingPresentationProbeRoot","UNCONFIGURED"));
        watcher=PacketAdapters.registerOutbound((PlayerPacketWatcher)this::outbound);
        observers.add(this);
    }
    public synchronized void start(Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,String mode,String target)throws java.io.IOException{
        var world=store.getExternalData().getWorld();
        if(closed)throw new IllegalStateException("HEAL_PROBE_CLOSED");
        if(!liveTest){
        var live=Path.of(System.getenv("APPDATA"),"Hytale/data/pre-release/Saves/RPG").toRealPath();
        if(!HealingProbePolicy.allows(allowedRoot.toRealPath(),world.getSavePath().toRealPath(),live))
            throw new IllegalStateException("HEAL_PROBE_DISPOSABLE_WORLD_REQUIRED");
        }
        var selected=HealingProbePolicy.mode(mode);
        if(liveTest)HealingProbePolicy.validateLiveTarget(selected,target);else HealingProbePolicy.validateTarget(selected,target);
        if(runs.size()>=HealingProbePolicy.MAX_WORLDS)throw new IllegalStateException("HEAL_PROBE_CAPACITY");
        var run=new Run(player.getUuid(),world.getWorldConfig().getUuid(),store,actor,selected,target);
        if(runs.putIfAbsent(run.world,run)!=null)throw new IllegalStateException("HEAL_PROBE_ALREADY_ACTIVE_WAIT_OR_STOP");
        receipt(run,"REQUESTED","root",Map.of("durationSeconds",HealingProbePolicy.duration(selected),"targetSelection",target));
        schedule(run);
    }
    public void stop(Store<EntityStore> store,UUID owner){
        var run=runs.get(store.getExternalData().getWorld().getWorldConfig().getUuid());
        if(run!=null&&run.owner.equals(owner))mutate(run,()->cleanup(run,"OWNER_STOP"));
    }
    public void detach(UUID owner){
        for(var r:runs.values())if(r.owner.equals(owner))mutate(r,()->{cleanup(r,"OWNER_DETACH");runs.remove(r.world,r);});
    }
    private void mutate(Run r,Runnable work){
        Runnable measured=()->{try(var span=NativeRpgTickMetrics.enter(r.store,NativeRpgTickMetrics.Phase.HUD)){work.run();}};
        if(r.store.isInThread()&&!r.store.isProcessing())measured.run();
        else r.store.getExternalData().getWorld().execute(measured);
    }
    private void schedule(Run r){
        if(r.queued||r.stopping)return;r.queued=true;
        mutate(r,()->{r.queued=false;
            if(closed||runs.get(r.world)!=r||r.stopping)return;
            try{
                if(!r.actor.isValid()||r.store.getComponent(r.actor,PlayerRef.getComponentType())==null){cleanup(r,"OWNER_GONE");return;}
                if(!r.created){create(r);r.created=true;notifyOwner(r,r.mode==HealingProbePolicy.Mode.CHANNEL
                    ?"R032-AK ARMED for 30s. Cast Healing Beam normally now; this observer casts nothing and does not waive Mana costs."
                    :"STARTED "+r.mode+" for 10s; observe now.");}
                if(r.target!=null&&!r.target.isValid()){cleanup(r,"TARGET_GONE");return;}
                for(var ref:r.target==null?List.of(r.actor):List.of(r.actor,r.target)){
                    var stats=r.store.getComponent(ref,EntityStatMap.getComponentType());
                    if(stats!=null&&stats.get(DefaultEntityStatTypes.getHealth())!=null&&stats.get(DefaultEntityStatTypes.getHealth()).get()<=0){cleanup(r,"ENDPOINT_DEAD");return;}
                }
                if(seconds(r)>=HealingProbePolicy.duration(r.mode)){cleanup(r,"FINITE_DEADLINE");return;}
                if(seconds(r)>=r.nextFrame){r.nextFrame=seconds(r)+.05;update(r);}
            }catch(RuntimeException failure){
                receipt(r,"FAILED","root",Map.of("error",failure.getClass().getSimpleName(),"reason",safe(failure.getMessage())));
                notifyOwner(r,"FAILED "+r.mode+": "+safe(failure.getMessage())+". This is not a rendering result.");
                cleanup(r,"FAILURE");
            }
        });
    }
    private void create(Run r){
        if(r.mode==HealingProbePolicy.Mode.CHANNEL){
            receipt(r,"CHANNEL_OBSERVER_ARMED","root",Map.of("readOnly",true,"productionRendererUnchanged",true));
            receipt(r,"SYSTEM_ORDER","root",systemOrder());return;
        }
        var a=anchor(r,r.actor);
        var head=r.store.getComponent(r.actor,HeadRotation.getComponentType());
        var direction=head==null?new Vector3d(0,0,-1):head.getDirection();
        r.endpoint=a.add(new Vec3(direction.x,direction.y,direction.z).multiply(6));
        if(HealingProbePolicy.needsRecipient(r.mode)){
        if(liveTest&&r.requestedTarget.equals("native"))throw new IllegalArgumentException("LIVE_PROBE_NPC_SPAWN_FORBIDDEN");
        if(liveTest&&r.requestedTarget.equals("self"))r.target=r.actor;
        else if(r.requestedTarget.equals("native")){
            if(a.subtract(new Vec3(8.5,65.35,2.5)).length()>16)throw new IllegalArgumentException("RETURN_TO_FIXTURE_NEAR_8_64_8");
            r.target=spawnRecipient(r.store);r.ownedTarget=true;
        }else r.target=r.store.getExternalData().getRefFromUUID(UUID.fromString(r.requestedTarget));
        if(r.target==null||!r.target.isValid())throw new IllegalStateException("TARGET_REF_UNRESOLVED");
        if(r.target.equals(r.actor)&&!liveTest)throw new IllegalArgumentException("PROBE_TARGET_MUST_NOT_BE_CASTER");
        var stats=r.store.getComponent(r.target,EntityStatMap.getComponentType());
        if(stats==null||stats.get(DefaultEntityStatTypes.getHealth())==null||stats.get(DefaultEntityStatTypes.getHealth()).get()<=0)
            throw new IllegalArgumentException("PROBE_TARGET_MUST_BE_LIVING");
        receipt(r,"TARGET_RESOLVED","recipient",entity(r,r.target));
        }else receipt(r,"TARGET_NOT_REQUIRED","root",Map.of("npcSpawnAttempted",false,"endpoint","FIXED_WORLD_POINT_ALONG_INITIAL_AIM"));
        var b=endpoint(r);
        switch(r.mode){
            case WORLD -> {
                var rot=HealingParticleVisuals.rotation(a,b);
                var packet=new SpawnParticleSystem("Beam_Heal_Green2",new Position(a.x(),a.y(),a.z()),
                        new Direction(rot.yaw(),rot.pitch(),rot.roll()),1,null,(float)HealingProbePolicy.RUN_SECONDS);
                // Exact stock system, finite maxDuration. No broad CancelParticleSystems that might erase somebody else's emitter.
                for(var viewer:r.store.getExternalData().getWorld().getPlayerRefs())viewer.getPacketHandler().write(packet);
                receipt(r,"WORLD_PACKET_SUBMITTED","core",Map.of("asset","Beam_Heal_Green2","maxDuration",packet.maxDuration));
            }
            case EMPTY, VISIBLE -> {
                String model=r.mode==HealingProbePolicy.Mode.EMPTY?HealingParticleVisuals.MODEL_ID:"RPG_Probe_Healing_Visible";
                r.carrier=HealingParticleVisuals.spawnCarrier(r.store,a,b,model);
                watch(r,r.carrier,"core");receipt(r,"ENTITY_CREATED","core",entity(r,r.carrier));
            }
            case BEAM -> {
                // Explicit geometry-only control: existing AF material, never selected by production.
                r.carrier=BeamComponent.spawn(r.store,vector(a),NativeHealingBeamVisuals.attachment(Beam.getAssetMap().getIndex(NativeHealingBeamVisuals.ASSET_ID),b));
                watch(r,r.carrier,"core");receipt(r,"ENTITY_CREATED","core",entity(r,r.carrier));
            }
            case RECIPIENT_ONCE,RECIPIENT_OVERWRITE -> {
                r.effect="RPG_Probe_Healing_Recipient";watch(r,r.target,"recipient");attach(r,r.target);
            }
            case STAFF_ONCE,STAFF_OVERWRITE -> {
                var player=r.store.getComponent(r.actor,Player.getComponentType());
                var hand=player.getInventory().getItemInHand();
                String nativeEffect=hand==null?null:HealingParticleVisuals.staffEffect(hand.getItemId());
                if(nativeEffect==null)throw new IllegalArgumentException("AUDITED_STAFF_REQUIRED");
                r.effect=nativeEffect.replace("RPG_Healing_Staff_","RPG_Probe_Healing_Staff_");
                watch(r,r.actor,"staff");attach(r,r.actor);
            }
            case CHANNEL -> throw new IllegalStateException("CHANNEL_MUST_NOT_CREATE_VISUALS");
        }
        receipt(r,"SYSTEM_ORDER","root",systemOrder());
    }
    private void attach(Run r,Ref<EntityStore> ref){
        if(liveTest){
            r.effectNetworkId=Objects.requireNonNull(r.store.getComponent(ref,NetworkId.getComponentType()),"PROBE_NETWORK_ID_REQUIRED").getId();
            sendTransientEffect(r,false);
            receipt(r,"CLIENT_EFFECT_SUBMITTED",r.mode.name().startsWith("STAFF")?"staff":"recipient",
                    Map.of("effect",r.effect,"backend","CLIENT_ONLY_NO_NATIVE_CONTROLLER_WRITE","duration",overwrite(r)?.3:HealingProbePolicy.EFFECT_SECONDS));
            return;
        }
        var controller=r.store.getComponent(ref,EffectControllerComponent.getComponentType());
        if(controller==null)throw new IllegalStateException("EFFECT_CONTROLLER_MISSING");
        float duration=overwrite(r)?.3f:(float)HealingProbePolicy.EFFECT_SECONDS;
        if(!controller.addEffect(ref,EntityEffect.getAssetMap().getAsset(r.effect),duration,OverlapBehavior.OVERWRITE,r.store))
            throw new IllegalStateException("EFFECT_ATTACH_REJECTED");
        receipt(r,"EFFECT_ATTACHED",r.mode.name().startsWith("STAFF")?"staff":"recipient",
                Map.of("effect",r.effect,"duration",duration,"strategy",overwrite(r)?"AH_OVERWRITE_EACH_FRAME":"ONE_FINITE_APPLICATION"));
    }
    private static boolean overwrite(Run r){return r.mode==HealingProbePolicy.Mode.RECIPIENT_OVERWRITE||r.mode==HealingProbePolicy.Mode.STAFF_OVERWRITE;}
    private void update(Run r){
        if(r.mode==HealingProbePolicy.Mode.CHANNEL)return;
        var a=anchor(r,r.actor);var b=endpoint(r);
        if(r.carrier!=null){
            if(r.mode==HealingProbePolicy.Mode.BEAM){
                r.store.getComponent(r.carrier,TransformComponent.getComponentType()).setPosition(vector(a));
                r.store.getComponent(r.carrier,BeamComponent.getComponentType()).set(List.of(NativeHealingBeamVisuals.attachment(Beam.getAssetMap().getIndex(NativeHealingBeamVisuals.ASSET_ID),b)));
            }else HealingParticleVisuals.update(r.store,r.carrier,a,b);
        }
        if(overwrite(r))attach(r,r.mode==HealingProbePolicy.Mode.STAFF_OVERWRITE?r.actor:r.target);
        if(seconds(r)>=r.nextSample){
            int sample=(int)r.nextSample;r.nextSample=seconds(r)+HealingProbePolicy.SAMPLE_SECONDS;
            receipt(r,"FRAME_"+sample,"root",Map.of("source",a,"target",b,"distance",b.subtract(a).length(),"carrierCount",r.carrier==null?0:1));
        }
    }
    private void cleanup(Run r,String reason){
        if(r.stopping)return;r.stopping=true;r.stopped=System.nanoTime();
        release(r,"effect",()->{if(r.effect!=null){
            if(liveTest){sendTransientEffect(r,true);return;}
            var ref=r.mode.name().startsWith("STAFF")?r.actor:r.target;
            if(ref!=null&&ref.isValid()){
                var c=r.store.getComponent(ref,EffectControllerComponent.getComponentType());
                if(c!=null)c.removeEffect(ref,EntityEffect.getAssetMap().getIndex(r.effect),r.store);
            }
        }});
        release(r,"core",()->{if(r.carrier!=null&&r.carrier.isValid())r.store.removeEntity(r.carrier,RemoveReason.REMOVE);});
        release(r,"ownedNpc",()->{if(r.ownedTarget&&r.target!=null&&r.target.isValid())r.store.removeEntity(r.target,RemoveReason.REMOVE);});
        receipt(r,"CLEANUP","root",Map.of("reason",reason,"worldParticleTail",r.mode==HealingProbePolicy.Mode.WORLD?"NATIVE_FINITE_DURATION_NO_INSTANCE_CANCEL_HANDLE":"NONE"));
        notifyOwner(r,"ENDED "+r.mode+" ("+reason+"). Wait 2s for trace summary.");
    }
    /** Explicit live diagnostic packets, not native controller execution evidence. No saved entity component is modified. */
    static EntityUpdates transientEffectPacket(int networkId,int effectId,float duration,boolean remove){
        if(networkId<0||effectId<0||!Float.isFinite(duration)||duration<0||duration>HealingProbePolicy.EFFECT_SECONDS)
            throw new IllegalArgumentException("PROBE_PACKET_BOUNDS");
        var effects=new EntityEffectsUpdate();
        effects.entityEffectUpdates=new EntityEffectUpdate[]{new EntityEffectUpdate(remove?EffectOp.Remove:EffectOp.Add,effectId,duration,false,false,null)};
        return new EntityUpdates(null,new EntityUpdate[]{new EntityUpdate(networkId,null,new ComponentUpdate[]{effects})});
    }
    private void sendTransientEffect(Run r,boolean remove){
        if(r.effectNetworkId<0||!r.actor.isValid())return;
        var viewer=r.store.getComponent(r.actor,PlayerRef.getComponentType());if(viewer==null)return;
        int index=EntityEffect.getAssetMap().getIndex(r.effect);
        if(!r.effect.startsWith("RPG_Probe_Healing_"))throw new IllegalStateException("NON_PROBE_EFFECT_FORBIDDEN");
        viewer.getPacketHandler().write(transientEffectPacket(r.effectNetworkId,index,remove?0:overwrite(r)?.3f:(float)HealingProbePolicy.EFFECT_SECONDS,remove));
    }
    private static Vec3 endpoint(Run r){return r.target==null?r.endpoint:anchor(r,r.target);}
    private static void notifyOwner(Run r,String text){
        try{
        if(!r.actor.isValid())return;
        var player=r.store.getComponent(r.actor,PlayerRef.getComponentType());
        if(player!=null)player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Healing probe: "+text));
        }catch(RuntimeException ignored){/* Diagnostic chat must not prevent cleanup when transport closes. */}
    }
    /** Fresh launcher-owned flat fixture only. Never edits terrain or searches random terrain at player Y. */
    static Ref<EntityStore> spawnRecipient(Store<EntityStore> store){
        var world=store.getExternalData().getWorld();
        if(!(world.getWorldConfig().getWorldGenProvider() instanceof com.hypixel.hytale.server.core.universe.world.worldgen.provider.FlatWorldGenProvider))
            throw new IllegalStateException("FRESH_FLAT_PROBE_WORLD_REQUIRED");
        var chunk=world.getChunkIfLoaded(0L);
        if(chunk==null)throw new IllegalStateException("FIXTURE_CHUNK_NOT_LOADED_RETURN_TO_SPAWN");
        int ground=com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.getAssetMap().getIndex("Soil_Grass");
        for(int x=6;x<=10;x++)for(int z=0;z<=4;z++){
            if(chunk.getBlock(x,63,z)!=ground)throw new IllegalStateException("FIXTURE_FLOOR_CHANGED");
            for(int y=64;y<70;y++)if(chunk.getBlock(x,y,z)!=0)throw new IllegalStateException("FIXTURE_CLEARANCE_CHANGED");
        }
        var target=new java.util.concurrent.atomic.AtomicReference<Ref<EntityStore>>();
        try{
            var result=com.hypixel.hytale.server.npc.NPCPlugin.get().spawnNPCWithColumnProbe(
                store,"RPG_Summon_Decoy",null,world,8,2,64,new Rotation3f(),(npc,ref,s)->{
                    target.set(ref);s.addComponent(ref,EntityStore.REGISTRY.getNonSerializedComponentType(),NonSerialized.get());
                    npc.getRole().setDeathItemsDropped();
                });
            if(result!=com.hypixel.hytale.server.spawning.SpawnTestResult.TEST_OK||target.get()==null||!target.get().isValid())
                throw new IllegalStateException("FIXTURE_NATIVE_COLUMN_SPAWN_"+result);
            return target.get();
        }catch(RuntimeException error){
            if(target.get()!=null&&target.get().isValid())store.removeEntity(target.get(),RemoveReason.REMOVE);
            throw error;
        }
    }
    private void release(Run r,String layer,Runnable action){
        try{action.run();receipt(r,"RELEASE_COMPLETED",layer,Map.of());}
        catch(RuntimeException error){receipt(r,"RELEASE_FAILED",layer,Map.of("error",error.getClass().getSimpleName(),"reason",safe(error.getMessage()),"fallback","UNSAVED_CARRIER_OR_FINITE_EFFECT"));}
    }
    private void watch(Run r,Ref<EntityStore> ref,String layer){
        var id=r.store.getComponent(ref,NetworkId.getComponentType());
        if(id==null)throw new IllegalStateException("NETWORK_ID_MISSING_"+layer);
        if(!r.watched.containsKey(id.getId())&&r.watched.size()>=64)throw new IllegalStateException("PROBE_WATCH_CAPACITY");
        r.watched.put(id.getId(),layer);
    }
    private Map<String,Object> entity(Run r,Ref<EntityStore> ref){
        var fields=new LinkedHashMap<String,Object>();var uuid=r.store.getComponent(ref,UUIDComponent.getComponentType());var net=r.store.getComponent(ref,NetworkId.getComponentType());
        fields.put("entityUuid",uuid==null?"UNAVAILABLE":uuid.getUuid());fields.put("networkId",net==null?-1:net.getId());
        var m=r.store.getComponent(ref,ModelComponent.getComponentType());
        if(m!=null){var packet=m.getModel().toPacket();fields.put("model",packet.path);fields.put("texture",String.valueOf(packet.texture));
            fields.put("modelParticles",packet.particles==null?0:packet.particles.length);}
        var box=r.store.getComponent(ref,BoundingBox.getComponentType());fields.put("bounds",String.valueOf(box==null?null:box.getBoundingBox()));
        return fields;
    }
    private void receipt(Run r,String stage,String layer,Map<String,?> fields){
        String key=stage+"/"+layer;
        synchronized(r.receipts){
            if(r.receipts.contains(key))return;
            if(r.receipts.size()>=HealingProbePolicy.MAX_TRANSITIONS&&!stage.equals("SUMMARY")){r.suppressedTransitions.incrementAndGet();return;}
            r.receipts.add(key);
        }
        var d=new LinkedHashMap<String,Object>(fields);d.put("probe",true);d.put("cohort","AK");d.put("root",r.generation);d.put("generation",r.generation);
        d.put("liveTest",liveTest);d.put("effectBackend",r.mode==HealingProbePolicy.Mode.CHANNEL?"OBSERVE_NATIVE_PRODUCTION_NO_WRITES":liveTest?"CLIENT_ONLY_NO_NATIVE_CONTROLLER_WRITE":"NATIVE_EFFECT_CONTROLLER");
        d.put("world",r.world);d.put("mode",r.mode);d.put("stage",stage);d.put("layer",layer);d.put("segment",r.generation+"/primary");d.put("connectedRendered",false);
        trace.trace(RpgTraceRecord.create(r.owner,RpgTraceEventType.HEAL_PRESENTATION,r.generation.toString(),d));
    }
    private void outbound(PlayerRef player,Packet packet){
        var worldId=player.getWorldUuid();if(worldId==null)return; // login/drain can have no world yet
        var r=runs.get(worldId);if(r==null)return;
        try{observeOutbound(r,player,packet);}
        catch(RuntimeException error){observerFailure(r,"outbound",error);}
    }
    private void observerFailure(Run r,String layer,RuntimeException error){
        // A diagnostic serialization/read failure must not abort Hytale's actual packet write or world tick.
        try{receipt(r,"OBSERVER_FAILED",layer,Map.of("error",error.getClass().getSimpleName(),"reason",safe(error.getMessage())));}
        catch(RuntimeException ignored){/* native transport continues even if the trace sink is unavailable */}
    }
    private void observeOutbound(Run r,PlayerRef player,Packet packet){
        if(packet instanceof SpawnParticleSystem p&&r.mode==HealingProbePolicy.Mode.WORLD&&"Beam_Heal_Green2".equals(p.particleSystemId))
            receipt(r,"OUTBOUND_OBSERVED","world/"+player.getUuid(),Map.of("system",p.particleSystemId,"maxDuration",p.maxDuration,"attribution","SYSTEM_AND_ACTIVE_WINDOW_NOT_INSTANCE_ACK"));
        if(!(packet instanceof EntityUpdates update))return;
        if(update.updates!=null)for(var entity:update.updates){
            String layer=r.watched.get(entity.networkId);if(layer==null)continue;
            r.packets.incrementAndGet();
            if(entity.updates!=null)for(var component:entity.updates){
                r.componentUpdates.incrementAndGet();
                receipt(r,"OUTBOUND_OBSERVED",layer+"/"+player.getUuid()+"/"+component.getClass().getSimpleName(),packetFields(component,entity.networkId));
                effectTransitions(r,"OUTBOUND",layer+"/"+player.getUuid(),entity.networkId,component);
            }
        }
        if(update.removed!=null)for(int id:update.removed)if(r.watched.containsKey(id))
            receipt(r,"OUTBOUND_REMOVAL_OBSERVED",r.watched.get(id)+"/"+player.getUuid(),Map.of("networkId",id));
    }
    /** Non-consuming diagnostic hook. Executes synchronously in the presenter's safe world phase.
     * No queued ECS work, no captured Ref beyond the read, no effect writes or gameplay callbacks.
     * A failed observer is contained and cannot change the presenter's control flow. */
    static void production(String stage,Store<EntityStore> store,UUID owner,String channel,
            Map<String,Ref<EntityStore>> carriers,Set<UUID> recipients,String staffEffect){
        for(var probe:observers){
            Run r=null;
            try{
                r=probe.runs.get(store.getExternalData().getWorld().getWorldConfig().getUuid());
                if(r==null||r.store!=store||!acceptsChannel(r.mode,r.owner,owner,r.stopping,probe.closed))continue;
                if(!r.channels.contains(channel)&&r.channels.size()>=8){probe.receipt(r,"CHANNEL_LIMIT","root",Map.of("maxChannels",8));continue;}
                r.channels.add(channel);
                probe.receipt(r,"PRODUCTION_"+stage,channel,Map.of("productionSkillInstanceId",channel,
                    "carriers",carriers.size(),"recipients",recipients.size(),"processing",store.isProcessing()));
                if(!stage.equals("FRAME_READY"))continue;
                for(var entry:carriers.entrySet())probe.productionLayer(r,channel+"/core/"+entry.getKey(),entry.getValue(),null);
                for(var id:recipients)probe.productionLayer(r,channel+"/recipient/"+id,store.getExternalData().getRefFromUUID(id),HealingParticleVisuals.RECIPIENT_EFFECT);
                if(staffEffect!=null)probe.productionLayer(r,channel+"/staff",store.getExternalData().getRefFromUUID(owner),staffEffect);
            }catch(RuntimeException failure){if(r!=null)probe.observerFailure(r,"production",failure);}
        }
    }
    static boolean acceptsChannel(HealingProbePolicy.Mode mode,UUID observer,UUID caster,boolean stopping,boolean closed){
        return mode==HealingProbePolicy.Mode.CHANNEL&&observer.equals(caster)&&!stopping&&!closed;
    }
    private void productionLayer(Run r,String layer,Ref<EntityStore> ref,String effect){
        if(ref==null||!ref.isValid()){receipt(r,"PRODUCTION_REF_UNRESOLVED",layer,Map.of());return;}
        watch(r,ref,layer);
        // Read once after create and once on a later frame; do not reserialize models every tick.
        String stage=!r.receipts.contains("PRODUCTION_LAYER_STATE/"+layer)?"PRODUCTION_LAYER_STATE":"PRODUCTION_LAYER_RECHECK";
        if(r.receipts.contains(stage+"/"+layer))return;
        var fields=new LinkedHashMap<String,Object>(entity(r,ref));
        var transform=r.store.getComponent(ref,TransformComponent.getComponentType());
        fields.put("position",String.valueOf(transform==null?null:transform.getPosition()));
        fields.put("nonSerialized",r.store.getComponent(ref,EntityStore.REGISTRY.getNonSerializedComponentType())!=null);
        if(effect!=null){
            var controller=r.store.getComponent(ref,EffectControllerComponent.getComponentType());
            int index=EntityEffect.getAssetMap().getIndex(effect);
            int networkId=r.store.getComponent(ref,NetworkId.getComponentType()).getId();
            r.expectedEffects.computeIfAbsent(networkId,ignored->ConcurrentHashMap.newKeySet()).add(index);
            fields.put("effect",effect);fields.put("effectIndex",index);fields.put("controllerPresent",controller!=null);
            fields.put("nativeHasEffect",controller!=null&&controller.hasEffect(index));
        }
        receipt(r,stage,layer,fields);
    }
    private void effectTransitions(Run r,String boundary,String layer,int networkId,ComponentUpdate component){
        var expected=r.expectedEffects.get(networkId);
        if(expected==null||!(component instanceof EntityEffectsUpdate effects)||effects.entityEffectUpdates==null)return;
        for(var effect:effects.entityEffectUpdates)if(expected.contains(effect.id))
            receipt(r,"NATIVE_EFFECT_"+effect.type+"_"+boundary,layer+"/"+effect.id,
                Map.of("networkId",networkId,"effectIndex",effect.id,"operation",effect.type,"remaining",effect.remainingTime,"infinite",effect.infinite));
    }
    static Map<String,Object> packetFields(ComponentUpdate component,int networkId){
        var fields=new LinkedHashMap<String,Object>();fields.put("networkId",networkId);fields.put("component",component.getClass().getSimpleName());
        if(component instanceof ModelUpdate m&&m.model!=null){fields.put("model",String.valueOf(m.model.path));fields.put("texture",String.valueOf(m.model.texture));
            fields.put("particles",m.model.particles==null?List.of():Arrays.stream(m.model.particles).map(p->Map.of("system",p.systemId,"part",p.targetEntityPart,"node",String.valueOf(p.targetNodeName),"clearOnRemove",p.clearParticlesOnRemove)).toList());}
        if(component instanceof EntityEffectsUpdate effects)fields.put("effects",effects.entityEffectUpdates==null?List.of():Arrays.stream(effects.entityEffectUpdates).map(e->Map.of("index",e.id,"op",e.type,"infinite",e.infinite,"remaining",e.remainingTime)).toList());
        return fields;
    }
    private void observe(Store<EntityStore> store){
        var r=runs.get(store.getExternalData().getWorld().getWorldConfig().getUuid());if(r==null)return;
        try{observeViewers(r,store);}catch(RuntimeException error){observerFailure(r,"viewer",error);}
    }
    private void observeViewers(Run r,Store<EntityStore> store){
        for(var player:store.getExternalData().getWorld().getPlayerRefs()){
            var ref=player.getReference();if(ref==null||!ref.isValid())continue;
            var viewer=store.getComponent(ref,EntityTrackerSystems.EntityViewer.getComponentType());if(viewer==null)continue;
            for(var entity:viewer.visible){if(!entity.isValid())continue;
                var net=store.getComponent(entity,NetworkId.getComponentType());String layer=net==null?null:r.watched.get(net.getId());
                if(layer!=null)receipt(r,"VIEWER_TRACKED",layer+"/"+player.getUuid(),Map.of("networkId",net.getId()));
            }
            for(var entry:viewer.updates.entrySet()){
                var entity=entry.getKey();if(!entity.isValid())continue;var net=store.getComponent(entity,NetworkId.getComponentType());
                String layer=net==null?null:r.watched.get(net.getId());if(layer==null)continue;
                for(var packet:entry.getValue().toUpdatesArray()){
                    receipt(r,"UPDATE_QUEUED",layer+"/"+player.getUuid()+"/"+packet.getClass().getSimpleName(),packetFields(packet,net.getId()));
                    effectTransitions(r,"QUEUED",layer+"/"+player.getUuid(),net.getId(),packet);
                }
            }
        }
    }
    public final class Tick extends TickingSystem<EntityStore>{
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemGroupDependency<>(Order.BEFORE,EntityTrackerSystems.QUEUE_UPDATE_GROUP));}
        @Override public void tick(float dt,int index,Store<EntityStore> store){
            try(var span=NativeRpgTickMetrics.enter(store,NativeRpgTickMetrics.Phase.HUD)){
            var r=runs.get(store.getExternalData().getWorld().getWorldConfig().getUuid());if(r==null)return;
            if(r.stopping){if(System.nanoTime()-r.stopped>2_000_000_000L){
                receipt(r,"SUMMARY","root",Map.of("outboundEntityUpdates",r.packets.get(),"outboundComponents",r.componentUpdates.get(),
                    "suppressedDiagnosticTransitions",r.suppressedTransitions.get(),"productionChannels",r.channels.size(),"packetSent","UNVERIFIED","clientRendered","UNVERIFIED"));
                runs.remove(r.world,r);
            }}else schedule(r);
            }
        }
    }
    public final class Observe extends TickingSystem<EntityStore>{
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
            new SystemGroupDependency<>(Order.AFTER,EntityTrackerSystems.QUEUE_UPDATE_GROUP),new SystemDependency<>(Order.BEFORE,EntityTrackerSystems.SendPackets.class));}
        @Override public void tick(float dt,int index,Store<EntityStore> store){
            try(var span=NativeRpgTickMetrics.enter(store,NativeRpgTickMetrics.Phase.HUD)){observe(store);}
        }
    }
    private static Map<String,Object> systemOrder(){
        var map=new LinkedHashMap<String,Object>();var data=EntityStore.REGISTRY.getData();
        for(int i=0;i<data.getSystemSize();i++){var system=data.getSystem(i);var name=system.getClass().getName();
            if(name.contains("EntityTrackerSystems")&&(name.contains("Model")||name.contains("Effect")||name.contains("SendPackets"))||system instanceof HytaleSkillExecutionSystem||name.contains("HealingPresentationProbe"))map.put(system.getClass().getSimpleName(),i);}
        return map;
    }
    /** Isolated server construction/queue-reader audit, never a connected rendering assertion. */
    static void auditChannel(Store<EntityStore> store,com.inigmasgames.hytalerpg.execution.SkillExecutionContext context){
        if(!Boolean.getBoolean("rpg.projectileSpawnAudit"))throw new IllegalStateException("ISOLATED_AUDIT_DISABLED");
        var owner=context.request().actorId();var actor=store.getExternalData().getRefFromUUID(owner);
        var target=HealingParticleVisuals.spawnCarrier(store,new Vec3(4,202,0),new Vec3(8,202,0),"RPG_Probe_Healing_Visible");
        var controller=new EffectControllerComponent();store.addComponent(target,EffectControllerComponent.getComponentType(),controller);
        var targetId=store.getComponent(target,UUIDComponent.getComponentType()).getUuid();
        var records=new ArrayList<RpgTraceRecord>();var visual=new HealingParticleVisuals();
        var failures=new ArrayList<Throwable>();
        try(var probe=new HealingPresentationProbe(records::add,true)){
            var run=new Run(owner,store.getExternalData().getWorld().getWorldConfig().getUuid(),store,actor,HealingProbePolicy.Mode.CHANNEL,"none");
            probe.runs.put(run.world,run);probe.create(run);
            if(run.target!=null||run.carrier!=null||run.effect!=null||run.ownedTarget)throw new IllegalStateException("CHANNEL_OBSERVER_CREATED_PRESENTATION");
            var frame=List.of(new com.inigmasgames.hytalerpg.execution.connection.ConnectionWorldPort.TetherVisualSegment("observed",
                com.inigmasgames.hytalerpg.execution.connection.ConnectionShape.line(new Vec3(0,202,0),new Vec3(4,202,0),.2,1),targetId.toString()));
            // Preserve the audit actor equipment as authored; this test independently exercises recipient/core.
            var c=new com.inigmasgames.hytalerpg.execution.SkillExecutionContext(context.request(),context.rootCastId()+"-observer",context.skillInstanceId()+"-observer",
                context.profile(),context.compiledPlan(),context.snapshot(),null);
            java.util.function.BiConsumer<String,Throwable> receipt=(event,error)->{if(error!=null)failures.add(error);};
            try{
                visual.present(store,null,c,frame,0,receipt);visual.present(store,null,c,frame,.05,receipt);
                if(!failures.isEmpty())throw new IllegalStateException("CHANNEL_AUDIT_PRODUCTION_FAILED:"+failures);
                for(String expected:List.of("PRODUCTION_FRAME_REQUESTED/"+c.skillInstanceId(),"PRODUCTION_FRAME_READY/"+c.skillInstanceId(),
                    "PRODUCTION_LAYER_STATE/"+c.skillInstanceId()+"/core/observed","PRODUCTION_LAYER_RECHECK/"+c.skillInstanceId()+"/core/observed",
                    "PRODUCTION_LAYER_STATE/"+c.skillInstanceId()+"/recipient/"+targetId))
                    if(!run.receipts.contains(expected))throw new IllegalStateException("CHANNEL_AUDIT_MISSING:"+expected);
                var effectsBefore=controller.getAllActiveEntityEffects().length;
                production("FRAME_READY",store,owner,c.skillInstanceId(),Map.of(),Set.of(targetId),null);
                if(controller.getAllActiveEntityEffects().length!=effectsBefore||!controller.hasEffect(EntityEffect.getAssetMap().getIndex(HealingParticleVisuals.RECIPIENT_EFFECT)))
                    throw new IllegalStateException("CHANNEL_OBSERVER_CHANGED_NATIVE_EFFECT");
                visual.remove(c,null);
                if(!run.receipts.contains("PRODUCTION_REMOVED/"+c.skillInstanceId())||controller.hasEffect(EntityEffect.getAssetMap().getIndex(HealingParticleVisuals.RECIPIENT_EFFECT)))
                    throw new IllegalStateException("CHANNEL_PRODUCTION_CLEANUP_FAILED");
                // Stop the diagnostic while the production presenter is active: it must not clear the real effect.
                visual.present(store,null,c,frame,.1,receipt);probe.cleanup(run,"AUDIT_STOP");
                if(!controller.hasEffect(EntityEffect.getAssetMap().getIndex(HealingParticleVisuals.RECIPIENT_EFFECT)))throw new IllegalStateException("OBSERVER_STOP_CHANGED_PRODUCTION");
                if(run.receipts.stream().anyMatch(s->s.startsWith("OBSERVER_FAILED")||s.startsWith("RELEASE_FAILED")))throw new IllegalStateException("CHANNEL_OBSERVER_FAILURE");
                if(records.stream().noneMatch(r->Boolean.TRUE.equals(r.details().get("nativeHasEffect"))))throw new IllegalStateException("NATIVE_EFFECT_OBSERVATION_MISSING");
            }finally{visual.remove(c,null);}
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log("RPG_HEAL_CHANNEL_OBSERVER result=PASS productionHooks=true nativeEffectsUnchanged=true observerStopDoesNotStopChannel=true nativeCleanup=true connectedProof=false");
        }finally{if(target.isValid())store.removeEntity(target,RemoveReason.REMOVE);}
    }
    static void audit(Store<EntityStore> store){
        if(!Boolean.getBoolean("rpg.projectileSpawnAudit")||!Boolean.getBoolean("rpg.healingPresentationProbe"))
            throw new IllegalStateException("PROBE_AUDIT_NOT_ENABLED");
        var refs=new ArrayList<Ref<EntityStore>>();
        try{
            var recipient=spawnRecipient(store);refs.add(recipient);
            var health=store.getComponent(recipient,EntityStatMap.getComponentType()).get(DefaultEntityStatTypes.getHealth());
            if(health==null||health.get()<=0)throw new IllegalStateException("FIXTURE_NATIVE_HEALTH_MISSING");
            var effects=store.getComponent(recipient,EffectControllerComponent.getComponentType());
            for(float duration:new float[]{12,.3f}){
                if(!effects.addEffect(recipient,EntityEffect.getAssetMap().getAsset("RPG_Probe_Healing_Recipient"),duration,OverlapBehavior.OVERWRITE,store))
                    throw new IllegalStateException("FIXTURE_RECIPIENT_EFFECT_FAILED");
                effects.removeEffect(recipient,EntityEffect.getAssetMap().getIndex("RPG_Probe_Healing_Recipient"),store);
            }
            store.removeEntity(recipient,RemoveReason.REMOVE);
            if(recipient.isValid())throw new IllegalStateException("FIXTURE_RECIPIENT_CLEANUP_FAILED");
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log("RPG_HEAL_PROBE_FIXTURE result=PASS nativeColumnSpawn=true health=true recipientOnce=true recipientOverwrite=true cleanup=true connectedProof=false");
            var actorHolder=EntityStore.REGISTRY.newHolder();
            var owner=UUID.randomUUID();
            actorHolder.addComponent(UUIDComponent.getComponentType(),new UUIDComponent(owner));
            actorHolder.addComponent(TransformComponent.getComponentType(),new TransformComponent(new Vector3d(8.5,64,8.5),new Rotation3f()));
            actorHolder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
            var actor=store.addEntity(actorHolder,AddReason.SPAWN);refs.add(actor);
            try(var probe=new HealingPresentationProbe(ignored->{},false)){
                for(var mode:List.of(HealingProbePolicy.Mode.WORLD,HealingProbePolicy.Mode.EMPTY,HealingProbePolicy.Mode.VISIBLE,HealingProbePolicy.Mode.BEAM,
                        HealingProbePolicy.Mode.RECIPIENT_ONCE,HealingProbePolicy.Mode.RECIPIENT_OVERWRITE)){
                    var run=new Run(owner,store.getExternalData().getWorld().getWorldConfig().getUuid(),store,actor,mode,
                            HealingProbePolicy.needsRecipient(mode)?"native":"none");
                    try{
                        // Execute the same create/update/cleanup methods as the connected command, not just its model factory.
                        probe.create(run);probe.update(run);
                        if(!HealingProbePolicy.needsRecipient(mode)&&(run.target!=null||run.ownedTarget))throw new IllegalStateException("STANDALONE_CREATED_NPC");
                    }finally{probe.cleanup(run,"ISOLATED_AUDIT");}
                    if(run.carrier!=null&&run.carrier.isValid()||run.ownedTarget&&run.target.isValid())throw new IllegalStateException("PROBE_MODE_ORPHAN");
                    if(run.receipts.stream().anyMatch(s->s.startsWith("RELEASE_FAILED")))throw new IllegalStateException("PROBE_MODE_CLEANUP_FAILURE");
                }
            }
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log("RPG_HEAL_PROBE_MODE_PATHS result=PASS standalone=4 recipient=2 sameCreateUpdateCleanup=true noStandaloneNpc=true connectedProof=false");
            for(String id:List.of(HealingParticleVisuals.MODEL_ID,"RPG_Probe_Healing_Visible")){
                var ref=HealingParticleVisuals.spawnCarrier(store,new Vec3(0,203,0),new Vec3(6,203,0),id);refs.add(ref);
                var model=store.getComponent(ref,ModelComponent.getComponentType());
                var packet=model.getModel().toPacket();
                if(packet.particles.length!=1||!"Beam_Heal_Green2".equals(packet.particles[0].systemId)||packet.texture==null)
                    throw new IllegalStateException("PROBE_MODEL_PARTICLE_CONTRACT");
                var update=new ModelUpdate();update.model=packet;
                var queue=new EntityTrackerSystems.EntityUpdate();queue.queueUpdate(update);
                var before=queue.toUpdatesArray();packetFields(before[0],1);
                if(!Arrays.equals(before,queue.toUpdatesArray()))throw new IllegalStateException("PROBE_CONSUMED_QUEUE");
                var controller=new EffectControllerComponent();store.addComponent(ref,EffectControllerComponent.getComponentType(),controller);
                var visible=new EntityTrackerSystems.Visible();store.addComponent(ref,EntityTrackerSystems.Visible.getComponentType(),visible);
                var viewer=new EntityTrackerSystems.EntityViewer(128,null);viewer.visible.add(ref);visible.visibleTo.put(ref,viewer);
                var sender=new EntityTrackerSystems.EffectControllerSystem(EntityTrackerSystems.Visible.getComponentType(),EffectControllerComponent.getComponentType());
                for(String effectId:List.of("RPG_Probe_Healing_Recipient","RPG_Probe_Healing_Staff_Block5","RPG_Probe_Healing_Staff_Knob","RPG_Probe_Healing_Staff_Origin_Projectile","RPG_Probe_Healing_Staff_TopPommel")){
                    var asset=Objects.requireNonNull(EntityEffect.getAssetMap().getAsset(effectId));
                    int index=EntityEffect.getAssetMap().getIndex(effectId);
                    for(float duration:new float[]{12,.3f,.3f}){
                        if(!controller.addEffect(ref,asset,duration,OverlapBehavior.OVERWRITE,store)||!controller.hasEffect(index))
                            throw new IllegalStateException("PROBE_EFFECT_ATTACH_FAILED");
                        // Native owner, not diagnostic code, consumes its pending changes and produces the viewer queue.
                        store.forEachChunk((java.util.function.BiConsumer<ArchetypeChunk<EntityStore>,CommandBuffer<EntityStore>>)(chunk,buffer)->{
                            for(int i=0;i<chunk.size();i++)if(chunk.getReferenceTo(i).equals(ref))sender.tick(.05f,i,chunk,store,buffer);
                        });
                        var queued=Objects.requireNonNull(viewer.updates.get(ref),"PROBE_NATIVE_EFFECT_QUEUE_MISSING");
                        var copy=queued.toUpdatesArray();
                        if(Arrays.stream(copy).noneMatch(p->p instanceof EntityEffectsUpdate))throw new IllegalStateException("PROBE_EFFECT_PACKET_MISSING");
                        for(var p:copy)packetFields(p,1);
                        if(!Arrays.equals(copy,queued.toUpdatesArray()))throw new IllegalStateException("PROBE_OBSERVER_CONSUMED_NATIVE_QUEUE");
                    }
                    controller.removeEffect(ref,index,store);if(controller.hasEffect(index))throw new IllegalStateException("PROBE_EFFECT_CLEANUP_FAILED");
                }
            }
            var order=systemOrder();
            int observer=((Number)Objects.requireNonNull(order.get("Observe"))).intValue();
            if(observer<=((Number)order.get("EntityModel")).intValue()||observer<=((Number)order.get("EffectControllerSystem")).intValue()||observer>=((Number)order.get("SendPackets")).intValue())
                throw new IllegalStateException("PROBE_OBSERVER_ORDER:"+order);
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log("RPG_HEAL_PROBE_NATIVE result=PASS models=2 effects=5 onceAndOverwrite=true nativeEffectQueue=true nonConsumingRead=true order=%s connectedProof=false",order);
        }finally{for(var ref:refs)if(ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);}
    }
    private static Vec3 anchor(Run r,Ref<EntityStore> ref){var p=r.store.getComponent(ref,TransformComponent.getComponentType()).getPosition();return new Vec3(p.x,p.y+1.35,p.z);}
    private static Vector3d vector(Vec3 p){return new Vector3d(p.x(),p.y(),p.z());}
    private static double seconds(Run r){return (System.nanoTime()-r.started)/1e9;}
    private static String safe(String value){return value==null?"UNSPECIFIED":value.replaceAll("[\\r\\n\\t]"," ").substring(0,Math.min(160,value.length()));}
    @Override public void close(){closed=true;observers.remove(this);PacketAdapters.deregisterOutbound(watcher);for(var r:runs.values())mutate(r,()->cleanup(r,"SHUTDOWN"));}
}
