package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.connection.ConnectionWorldPort.TetherVisualSegment;
import java.util.*;
import java.util.function.BiConsumer;

/** Production presentation owner. Core and existing native cosmetic leases fail/clean up independently.
 * No gameplay callbacks, effects selection, target discovery, payment or healing decisions. */
public final class HealingTetherPresentation {
    public static final String REVISION="R032-AO";
    private final SplineHealingParticleVisuals core=new SplineHealingParticleVisuals();
    private final HealingParticleVisuals effects=new HealingParticleVisuals(true);
    public void present(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,SkillExecutionContext context,
            List<TetherVisualSegment> frame,double now,BiConsumer<String,Throwable> result){
        layer(()->core.present(store,buffer,context,frame,now,result),"PARTICLE_PATH_FAILED",result);
        layer(()->effects.present(store,buffer,context,frame,now,(event,error)->result.accept(event.replace("HEAL_PARTICLE_","HEAL_ATTACHMENTS_"),error)),
            "HEAL_ATTACHMENTS_FAILED",result);
    }
    public void remove(SkillExecutionContext context,CommandBuffer<EntityStore> buffer){
        try{core.remove(context,buffer);}finally{effects.remove(context,buffer);}
    }
    public void cancel(UUID owner,CommandBuffer<EntityStore> buffer){
        try{core.cancel(owner,buffer);}finally{effects.cancel(owner,buffer);}
    }
    private static void layer(Runnable action,String failure,BiConsumer<String,Throwable> result){
        try{action.run();}catch(RuntimeException error){result.accept(failure,error);}
    }
    static void audit(Store<EntityStore> store,SkillExecutionContext context){
        if(!Boolean.getBoolean("rpg.projectileSpawnAudit"))throw new IllegalStateException("ISOLATED_AUDIT_DISABLED");
        var owner=new HealingTetherPresentation();var events=new ArrayList<String>();var failures=new ArrayList<Throwable>();
        BiConsumer<String,Throwable> receipt=(event,error)->{events.add(event);if(error!=null)failures.add(error);};
        var a=new com.inigmasgames.hytalerpg.execution.math.Vec3(0,202,0);
        var b=new com.inigmasgames.hytalerpg.execution.math.Vec3(18,202,0);
        var frame=List.of(new TetherVisualSegment("primary",com.inigmasgames.hytalerpg.execution.connection.ConnectionShape.line(a,b,.2,1)));
        java.util.function.Consumer<java.util.function.Consumer<CommandBuffer<EntityStore>>> processing=work->{
            var once=new java.util.concurrent.atomic.AtomicBoolean();
            store.forEachChunk((java.util.function.BiConsumer<ArchetypeChunk<EntityStore>,CommandBuffer<EntityStore>>)(chunk,buffer)->{
                if(once.compareAndSet(false,true)){if(!store.isProcessing())throw new IllegalStateException("TETHER_AUDIT_NOT_PROCESSING");work.accept(buffer);}
            });if(!once.get())throw new IllegalStateException("TETHER_AUDIT_NO_ARCHETYPE");
        };
        try{
            processing.accept(buffer->owner.present(store,buffer,context,frame,0,receipt));
            if(!failures.isEmpty()||owner.core.rootCount()!=1||owner.effects.carrierCount()!=0
                    ||!events.contains("PARTICLE_PATH_STARTED")||!events.contains("HEAL_ATTACHMENTS_STARTED"))
                throw new IllegalStateException("PRODUCTION_TETHER_CREATE:"+failures);
            processing.accept(buffer->owner.present(store,buffer,context,frame,.1,receipt));
            if(!failures.isEmpty()||!events.contains("PARTICLE_PATH_UPDATED"))throw new IllegalStateException("PRODUCTION_TETHER_UPDATE");
            processing.accept(buffer->owner.remove(context,buffer));
            if(owner.core.rootCount()!=0||owner.effects.carrierCount()!=0||!events.contains("PARTICLE_PATH_REMOVED"))throw new IllegalStateException("PRODUCTION_TETHER_REMOVE");
            var isolated=new ArrayList<String>();
            layer(()->{throw new IllegalStateException("AUDIT_CORE_FAILURE");},"PARTICLE_PATH_FAILED",(event,error)->isolated.add(event));
            layer(()->isolated.add("EFFECT_LAYER_RAN"),"HEAL_ATTACHMENTS_FAILED",(event,error)->isolated.add(event));
            if(!isolated.equals(List.of("PARTICLE_PATH_FAILED","EFFECT_LAYER_RAN")))throw new IllegalStateException("PRODUCTION_LAYER_ISOLATION");
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log("RPG_HEAL_TETHER_NATIVE revision=R032-AO result=PASS productionOwner=true autonomousBeamCarriers=0 createUpdateRemove=true independentLayers=true connectedProof=false");
        }finally{owner.cancel(context.request().actorId(),null);}
    }
}
