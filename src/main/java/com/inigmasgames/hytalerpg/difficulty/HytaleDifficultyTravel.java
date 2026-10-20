package com.inigmasgames.hytalerpg.difficulty;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.vector.*;
import com.hypixel.hytale.server.core.universe.*;
import com.hypixel.hytale.server.core.universe.world.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.inigmasgames.hytalerpg.progress.*;
import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.execution.SkillExecutionService;
import java.util.*;
import java.util.concurrent.*;

/** All ECS reads and writes run on their owning world; disk work stays on the authority workers. */
public final class HytaleDifficultyTravel implements DifficultyTravel.Port {
    @FunctionalInterface public interface Cleanup{void run(Store<EntityStore> store,Ref<EntityStore> actor,UUID player);}
    private final HytaleDifficultyWorlds worlds;private final RpgLoadoutService players;
    private final RpgCombatKernel kernel;private final SkillExecutionService executions;private final Cleanup cleanup;
    private final GolemMilestones golems=GolemMilestones.load();
    public HytaleDifficultyTravel(HytaleDifficultyWorlds worlds,RpgLoadoutService players,RpgCombatKernel kernel,SkillExecutionService executions,Cleanup cleanup){
        this.worlds=worlds;this.players=players;this.kernel=kernel;this.executions=executions;this.cleanup=cleanup;}
    private record Actor(PlayerRef player,World world,Store<EntityStore> store,Ref<EntityStore> ref){}
    private <T> CompletableFuture<T> onPlayer(UUID id,java.util.function.Function<Actor,T> action){
        var result=new CompletableFuture<T>();var p=Universe.get().getPlayer(id);
        var world=p==null?null:Universe.get().getWorld(p.getWorldUuid());
        if(world==null)return CompletableFuture.failedFuture(new IllegalStateException("PLAYER_NOT_IN_WORLD"));
        try{world.execute(()->{try{
            var ref=world.getEntityRef(id);if(ref==null||!ref.isValid()||!world.getWorldConfig().getUuid().equals(p.getWorldUuid()))throw new IllegalStateException("PLAYER_WORLD_CHANGED");
            result.complete(action.apply(new Actor(p,world,world.getEntityStore().getStore(),ref)));
        }catch(Throwable e){result.completeExceptionally(e);}});}catch(Throwable e){result.completeExceptionally(e);}return result;
    }
    private void validate(Actor a,DifficultyId mode,boolean forced){
        UUID id=a.player().getUuid();if(!players.ready(id))throw new IllegalStateException("PLAYER_PERSISTENCE_NOT_READY");
        if(a.store().getComponent(a.ref(),DeathComponent.getComponentType())!=null)throw new IllegalStateException("CANNOT_TRAVEL_WHILE_DEAD");
        String gate=RespecGate.rejection(kernel.hostileCombat().secondsSinceHostile(id),executions.persistenceCommitPending(id));
        if(!gate.isEmpty())throw new IllegalStateException(gate.replace("RESPEC","TRAVEL"));
        if(!forced){String rejection=golems.rejection(players.getPresentationView(id).state().difficulty,mode);if(!rejection.isEmpty())throw new IllegalStateException(rejection);}
    }
    @Override public CompletionStage<UUID> admit(UUID player,DifficultyId mode,boolean forced){return onPlayer(player,a->{validate(a,mode,forced);return a.player().getWorldUuid();});}
    @Override public CompletionStage<UUID> location(UUID player){return onPlayer(player,a->a.player().getWorldUuid());}
    @Override public CompletionStage<DifficultyTravel.Destination> prepare(UUID player,DifficultyId mode){
        return worlds.prepare(mode).thenCompose(world->{var provider=world.getWorldConfig().getSpawnProvider();
            if(provider==null)return CompletableFuture.failedFuture(new IllegalStateException("NATIVE_CAMPAIGN_SPAWN_MISSING"));
            return provider.getSpawnPointAsync(world,player).thenCompose(transform->{
                var position=transform.getPosition();long chunk=com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock((int)Math.floor(position.x()),(int)Math.floor(position.z()));
                return world.getChunkAsync(chunk).thenApplyAsync(loaded->{
                    var safe=findSafe(world,position.x(),position.y(),position.z());
                    return new DifficultyTravel.Destination(world.getWorldConfig().getUuid(),world.getName(),mode,safe.x(),safe.y(),safe.z(),transform.getRotation().yaw());
                },world);
            });
        });
    }
    /** Search only around the native approved spawn. Never build a platform or overwrite existing blocks. */
    public static org.joml.Vector3d findSafe(World world,double x,double y,double z){
        for(int radius=0;radius<=3;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)for(int dy=4;dy>=-4;dy--){
            int bx=(int)Math.floor(x)+dx,by=(int)Math.floor(y)+dy,bz=(int)Math.floor(z)+dz;
            if(safe(world,bx,by,bz))return new org.joml.Vector3d(bx+.5,by,bz+.5);
        }throw new IllegalStateException("APPROVED_SPAWN_OBSTRUCTED");
    }
    static boolean safe(World world,int x,int y,int z){
        if(y<1||y>317)return false;
        var c=world.getChunkIfLoaded(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x,z));
        return c!=null&&c.getBlock(x,y,z)==0&&c.getBlock(x,y+1,z)==0&&c.getBlock(x,y+2,z)==0
                &&c.getFluidId(x,y,z)==0&&c.getFluidId(x,y+1,z)==0
                &&com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.getAssetMap().getAsset(c.getBlock(x,y-1,z)).getMaterial()==com.hypixel.hytale.protocol.BlockMaterial.Solid;
    }
    /** Operator placement verifies the actual native model bounds before NPCPlugin adds the holder. */
    public static void requireClear(World world,org.joml.Vector3dc at,com.hypixel.hytale.math.shape.Box bounds){
        if(bounds==null||bounds.width()>20||bounds.height()>20||bounds.depth()>20)throw new IllegalStateException("UNSUPPORTED_ENCOUNTER_BOUNDS");
        for(int x=(int)Math.floor(at.x()+bounds.min.x);x<(int)Math.ceil(at.x()+bounds.max.x);x++)
            for(int z=(int)Math.floor(at.z()+bounds.min.z);z<(int)Math.ceil(at.z()+bounds.max.z);z++){
                var chunk=world.getChunkIfLoaded(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x,z));
                if(chunk==null)throw new IllegalStateException("ENCOUNTER_SPACE_NOT_LOADED");
                for(int y=(int)Math.floor(at.y()+Math.max(0,bounds.min.y));y<(int)Math.ceil(at.y()+bounds.max.y);y++)
                    if(y<1||y>318||chunk.getBlock(x,y,z)!=0||chunk.getFluidId(x,y,z)!=0)throw new IllegalStateException("ENCOUNTER_SPACE_OBSTRUCTED");
            }
    }
    @Override public CompletionStage<Void> handoff(DifficultyTravel.Pending pending){
        // Recheck destination immediately before cleanup. The native transfer moves the same PlayerRef/holder.
        return worlds.prepare(pending.destination().mode()).thenCompose(destination->{
            if(!destination.getWorldConfig().getUuid().equals(pending.destination().world()))return CompletableFuture.failedFuture(new IllegalStateException("DESTINATION_ID_CHANGED"));
            return CompletableFuture.runAsync(()->{var d=pending.destination();if(!safe(destination,(int)Math.floor(d.x()),(int)Math.floor(d.y()),(int)Math.floor(d.z())))throw new IllegalStateException("DESTINATION_NOW_OBSTRUCTED");},destination)
                .thenCompose(v->onPlayer(pending.player(),a->{
                    validate(a,pending.destination().mode(),pending.forced());if(!a.player().getWorldUuid().equals(pending.source()))throw new IllegalStateException("SOURCE_WORLD_CHANGED");
                    cleanup.run(a.store(),a.ref(),pending.player());
                    return players.submitCooldowns(pending.player(),kernel.cooldowns().snapshot(pending.player()));
                })).thenCompose(saved->saved).thenCompose(v->onPlayer(pending.player(),a->{
                    validate(a,pending.destination().mode(),pending.forced());if(!a.player().getWorldUuid().equals(pending.source()))throw new IllegalStateException("SOURCE_WORLD_CHANGED");
                    var d=pending.destination();var transform=new Transform(d.x(),d.y(),d.z(),0,d.yaw(),0);
                    return Universe.transferPlayerAsync(a.player(),a.world(),CompletableFuture.completedFuture(destination),w->CompletableFuture.completedFuture(transform));
                })).thenCompose(nativeTransfer->nativeTransfer).thenApply(v->null);
        });
    }
}
