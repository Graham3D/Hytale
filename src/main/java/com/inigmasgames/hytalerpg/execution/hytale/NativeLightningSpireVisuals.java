package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.execution.lightning.LightningSpireRuntime;
import com.inigmasgames.hytalerpg.execution.lightning.LightningSpireEntityLifecycle;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;
import org.joml.Vector3d;

/** Native model carriers for the damageable Spire, charge gauge and bounded hit/wave flashes. */
final class NativeLightningSpireVisuals {
    static final double MODEL_HEIGHT=3.6,TOP_OFFSET=3.45;
    static final double TOTEM_SPAWN_CUE_SECONDS=.9;
    static final String EMERGENCE_AUDIO="RPG_Lightning_Spire_Emergence_Audio",IDLE_AUDIO="RPG_Lightning_Spire_Idle_Audio";
    static final String TOTEM_SPAWN="SFX_Deployable_Totem_Slowing_Spawn",TOTEM_DESPAWN="SFX_Deployable_Totem_Slowing_Despawn";
    static final String METAL_HIT="SFX_Metal_Break",WAVE_SOUND="SFX_Portal_Neutral_Open";
    private record Carrier(Ref<EntityStore> ref,Store<EntityStore> store,Vec3 ground,double deployedAt,
                           EffectControllerComponent effects,LightningSpireEntityLifecycle lifecycle,double[] sway,boolean[] cues){}
    record HealthProbe(LightningSpireEntityLifecycle.State state,boolean refValid,boolean statMapPresent,
                       boolean healthPresent,double currentHealth,double minimumHealth,boolean healthyEntityObserved){
        boolean destroyed(){return state==LightningSpireEntityLifecycle.State.DESTROYED;}
    }
    private final Map<String,Carrier> carriers=new HashMap<>();
    private final Map<String,Ref<EntityStore>> gauges=new HashMap<>();

    Ref<EntityStore> deploy(LightningSpireRuntime.View view,Vec3 ground,double now,CommandBuffer<EntityStore> buffer){
        if(buffer==null)throw new IllegalStateException("LIGHTNING_SPIRE_BUFFER_MISSING");
        var store=buffer.getStore();var asset=require("Hywind_Lightning_Spire");var model=Model.createStaticScaledModel(asset,1);
        var holder=EntityStore.REGISTRY.newHolder();UUID id=UUID.randomUUID();
        holder.addComponent(UUIDComponent.getComponentType(),new UUIDComponent(id));
        holder.addComponent(NetworkId.getComponentType(),new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.addComponent(TransformComponent.getComponentType(),transform(ground.add(new Vec3(0,-MODEL_HEIGHT,0)),0,0));
        holder.addComponent(ModelComponent.getComponentType(),new ModelComponent(model));
        holder.addComponent(BoundingBox.getComponentType(),new BoundingBox(model.getBoundingBox()));
        holder.addComponent(LightningSpireProjection.getComponentType(),new LightningSpireProjection(view.instance(),view.owner()));
        var effects=new EffectControllerComponent();holder.addComponent(EffectControllerComponent.getComponentType(),effects);
        var stats=new EntityStatMap();stats.update();int health=DefaultEntityStatTypes.getHealth();var hp=stats.get(health);
        if(hp==null)throw new IllegalStateException("LIGHTNING_SPIRE_NATIVE_HEALTH_MISSING");
        stats.putModifier(health,"RPG_LIGHTNING_SPIRE_MAX",new StaticModifier(Modifier.ModifierTarget.MAX,
                StaticModifier.CalculationType.ADDITIVE,(float)(view.maximumHealth()-hp.getMax())));
        stats.update();stats.setStatValue(health,(float)view.maximumHealth());holder.addComponent(EntityStatMap.getComponentType(),stats);
        holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
        Ref<EntityStore> ref=buffer.addEntity(holder,AddReason.SPAWN);
        carriers.put(view.instance(),new Carrier(ref,store,ground,now,effects,new LightningSpireEntityLifecycle(),new double[4],new boolean[3]));
        return ref;
    }

    void tick(LightningSpireRuntime.View view,double now,double delta,CommandBuffer<EntityStore> buffer){
        var c=carriers.get(view.instance());if(c==null||!c.ref.isValid())return;
        var transform=buffer.getComponent(c.ref,TransformComponent.getComponentType());if(transform==null)return;
        double age=Math.max(0,now-c.deployedAt);double progress=Math.clamp(age/LightningSpireRuntime.EMERGENCE_SECONDS,0,1);
        if(!c.cues[0]){applyEffect(c.ref,c.effects,c.store,EMERGENCE_AUDIO,(float)LightningSpireRuntime.EMERGENCE_SECONDS);c.cues[0]=true;}
        if(age>=TOTEM_SPAWN_CUE_SECONDS&&!c.cues[1]){sound(TOTEM_SPAWN,c.ground,c.store);c.cues[1]=true;}
        if(progress>=1&&!c.cues[2]){applyEffect(c.ref,c.effects,c.store,IDLE_AUDIO,(float)Math.max(.1,view.readyRemaining()));c.cues[2]=true;}
        double eased=1-Math.pow(1-progress,3);double y=c.ground.y()-MODEL_HEIGHT*(1-eased);
        // Critically damped presentation-only spring. Collision/authoritative position never moves.
        double angle=c.sway[0],velocity=c.sway[1];velocity+=(-34*angle-11*velocity)*Math.min(delta,.1);angle+=velocity*Math.min(delta,.1);
        angle=Math.clamp(angle,Math.toRadians(-7),Math.toRadians(7));c.sway[0]=angle;c.sway[1]=velocity;
        // The carrier pivots at its grounded model origin. A small damped emergence wobble therefore
        // leaves the base planted while the narrow upper prism visibly bears most of the motion.
        double riseWobble=progress<1?Math.sin(age*15)*Math.toRadians(3.25)*(1-.45*progress):0;
        transform.setPosition(new Vector3d(c.ground.x(),y,c.ground.z()));
        transform.setRotation(new Rotation3f((float)(angle*c.sway[2]+riseWobble*.62),0,
                (float)(angle*c.sway[3]+riseWobble*.78)));
    }

    void friendlyHit(String instance,Vec3 attacker,int percent,double now,CommandBuffer<EntityStore> buffer){
        var c=carriers.get(instance);if(c==null||!c.ref.isValid())return;
        Vec3 away=c.ground.subtract(attacker);double length=Math.sqrt(away.x()*away.x()+away.z()*away.z());
        if(length<1e-6){away=new Vec3(1,0,0);length=1;}c.sway[2]=away.z()/length;c.sway[3]=-away.x()/length;
        c.sway[1]=Math.toRadians(24);sound(METAL_HIT,c.ground.add(new Vec3(0,1.4,0)),c.store);
        spawn("Hywind_Lightning_Spire_Shock",c.ground.add(new Vec3(0,TOP_OFFSET,0)),.42f,buffer,true);
        setGauge(instance,Math.clamp(percent,0,100),buffer);
    }
    void wave(String instance,CommandBuffer<EntityStore> buffer){var c=carriers.get(instance);if(c!=null){sound(WAVE_SOUND,c.ground,c.store);spawn("Hywind_Lightning_Spire_Shockwave",c.ground.add(new Vec3(0,.03,0)),.50f,buffer,true);setGauge(instance,0,buffer);}}
    Ref<EntityStore> ref(String instance){var c=carriers.get(instance);return c==null?null:c.ref;}
    Optional<String> instance(Ref<EntityStore> ref,Store<EntityStore> store){var p=ref==null?null:store.getComponent(ref,LightningSpireProjection.getComponentType());return p==null?Optional.empty():Optional.of(p.instance());}
    HealthProbe health(String instance,LightningSpireRuntime.Phase phase,CommandBuffer<EntityStore> buffer){
        var c=carriers.get(instance);if(c==null)return new HealthProbe(LightningSpireEntityLifecycle.State.DESTROYED,false,false,false,-1,-1,false);
        boolean valid=c.ref.isValid();var stats=valid?buffer.getComponent(c.ref,EntityStatMap.getComponentType()):null;
        var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());double current=hp==null?-1:hp.get(),minimum=hp==null?-1:hp.getMin();
        var state=c.lifecycle.observe(phase,valid,hp!=null,current,minimum);
        return new HealthProbe(state,valid,stats!=null,hp!=null,current,minimum,c.lifecycle.healthyEntityObserved());
    }
    void end(String instance,boolean explosion,CommandBuffer<EntityStore> buffer){var c=carriers.remove(instance);if(c==null)return;
        sound(TOTEM_DESPAWN,c.ground.add(new Vec3(0,1,0)),c.store);
        if(explosion&&buffer!=null)try{com.hypixel.hytale.server.core.universe.world.ParticleUtil.spawnParticleEffect("Explosion_Medium",new Vector3d(c.ground.x(),c.ground.y()+1,c.ground.z()),buffer.getStore());}catch(RuntimeException ignored){}
        remove(c.ref,c.store,buffer);var gauge=gauges.remove(instance);if(gauge!=null)remove(gauge,c.store,buffer);}
    void cancel(UUID owner,CommandBuffer<EntityStore> buffer){carriers.entrySet().stream().filter(e->{var p=e.getValue().store.getComponent(e.getValue().ref,LightningSpireProjection.getComponentType());return p!=null&&p.owner().equals(owner);}).map(Map.Entry::getKey).toList().forEach(id->end(id,false,buffer));}

    private static void spawn(String id,Vec3 p,float seconds,CommandBuffer<EntityStore> buffer,boolean animate){var store=buffer.getStore();var model=Model.createStaticScaledModel(require(id),1);var h=EntityStore.REGISTRY.newHolder();
        h.addComponent(UUIDComponent.getComponentType(),new UUIDComponent(UUID.randomUUID()));h.addComponent(NetworkId.getComponentType(),new NetworkId(store.getExternalData().takeNextNetworkId()));
        h.addComponent(TransformComponent.getComponentType(),transform(p,0,0));h.addComponent(ModelComponent.getComponentType(),new ModelComponent(model));h.addComponent(BoundingBox.getComponentType(),new BoundingBox(model.getBoundingBox()));
        h.addComponent(ActiveAnimationComponent.getComponentType(),new ActiveAnimationComponent());h.addComponent(DespawnComponent.getComponentType(),DespawnComponent.despawnInSeconds(store.getResource(TimeResource.getResourceType()),seconds));h.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
        var ref=buffer.addEntity(h,AddReason.SPAWN);if(animate)AnimationUtils.playAnimation(ref,AnimationSlot.Movement,"Idle",true,buffer);}
    private void setGauge(String instance,int percent,CommandBuffer<EntityStore> buffer){var c=carriers.get(instance);if(c==null)return;percent=Math.clamp((percent/10)*10,0,100);
        var model=Model.createStaticScaledModel(require("Hywind_Lightning_Spire_Gauge_"+percent),1);var existing=gauges.get(instance);
        if(existing!=null&&existing.isValid()){buffer.replaceComponent(existing,ModelComponent.getComponentType(),new ModelComponent(model));buffer.replaceComponent(existing,BoundingBox.getComponentType(),new BoundingBox(model.getBoundingBox()));return;}
        var h=EntityStore.REGISTRY.newHolder();var store=buffer.getStore();h.addComponent(UUIDComponent.getComponentType(),new UUIDComponent(UUID.randomUUID()));h.addComponent(NetworkId.getComponentType(),new NetworkId(store.getExternalData().takeNextNetworkId()));
        h.addComponent(TransformComponent.getComponentType(),transform(c.ground.add(new Vec3(0,TOP_OFFSET+1,0)),0,0));h.addComponent(ModelComponent.getComponentType(),new ModelComponent(model));h.addComponent(BoundingBox.getComponentType(),new BoundingBox(model.getBoundingBox()));h.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());gauges.put(instance,buffer.addEntity(h,AddReason.SPAWN));}
    private static ModelAsset require(String id){var asset=ModelAsset.getAssetMap().getAsset(id);if(asset==null)throw new IllegalStateException("LIGHTNING_SPIRE_MODEL_UNRESOLVED:"+id);return asset;}
    private static void applyEffect(Ref<EntityStore> ref,EffectControllerComponent controller,Store<EntityStore> store,String id,float seconds){
        var effect=EntityEffect.getAssetMap().getAsset(id);if(effect==null)throw new IllegalStateException("LIGHTNING_SPIRE_EFFECT_UNRESOLVED:"+id);
        if(!controller.addEffect(ref,effect,seconds,com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior.OVERWRITE,store))
            throw new IllegalStateException("LIGHTNING_SPIRE_EFFECT_REJECTED:"+id);
    }
    private static void sound(String id,Vec3 point,Store<EntityStore> store){int index=SoundEvent.getAssetMap().getIndex(id);
        if(index<0)throw new IllegalStateException("LIGHTNING_SPIRE_SOUND_UNRESOLVED:"+id);
        SoundUtil.playSoundEvent3d(index,SoundCategory.SFX,point.x(),point.y(),point.z(),store);
    }
    private static TransformComponent transform(Vec3 p,double pitch,double roll){return new TransformComponent(new Vector3d(p.x(),p.y(),p.z()),new Rotation3f((float)pitch,0,(float)roll));}
    private static void remove(Ref<EntityStore> ref,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){if(ref==null||!ref.isValid())return;if(buffer!=null&&buffer.getStore()==store)buffer.tryRemoveEntity(ref,RemoveReason.REMOVE);else store.getExternalData().getWorld().execute(()->{if(ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);});}
}
