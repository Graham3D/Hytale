package com.inigmasgames.hytalerpg.difficulty;

import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/** Persistent native campaign worlds. No disposable instances and no copies of character files. */
public final class HytaleDifficultyWorlds {
    private final WorldDifficultyRegistry registry;private final Executor io;
    private final Map<DifficultyId,CompletableFuture<World>> preparing=new EnumMap<>(DifficultyId.class);
    public HytaleDifficultyWorlds(WorldDifficultyRegistry registry,Executor io){this.registry=registry;this.io=io;}
    public synchronized CompletableFuture<World> prepare(DifficultyId mode){
        var cached=preparing.get(mode);if(cached!=null){
            if(!cached.isDone())return cached;
            if(!cached.isCompletedExceptionally()){var world=cached.getNow(null);if(world!=null&&Universe.get().getWorld(world.getWorldConfig().getUuid())==world)return cached;}
            preparing.remove(mode);
        }
        var future=CompletableFuture.supplyAsync(()->binding(mode),io).thenCompose(binding->{
            var u=Universe.get();var loaded=u.getWorld(binding.worldId());
            if(loaded!=null)return CompletableFuture.completedFuture(loaded);
            if(!u.isWorldLoadable(binding.worldName()))return CompletableFuture.failedFuture(new IllegalStateException("DIFFICULTY_WORLD_MISSING:"+binding.worldName()));
            return u.loadWorld(binding.worldName());
        }).thenApply(world->{var b=registry.require(world.getWorldConfig().getUuid());
            if(b.difficulty()!=mode||!b.enabled()||!b.worldName().equals(world.getName()))throw new IllegalStateException("DIFFICULTY_WORLD_IDENTITY_MISMATCH");return world;});
        preparing.put(mode,future);future.whenComplete((v,e)->{if(e!=null)synchronized(this){preparing.remove(mode,future);}});return future;
    }
    private WorldDifficultyRegistry.Binding binding(DifficultyId mode){
        String name=mode==DifficultyId.NORMAL?"default":"rpg_"+mode.name().toLowerCase(Locale.ROOT);
        var old=registry.bindings().stream().filter(b->b.worldName().equals(name)).findFirst();
        if(old.isPresent()){var b=old.get();if(b.difficulty()!=mode||!b.enabled()||b.kind()!=WorldDifficultyRegistry.Kind.CAMPAIGN)throw new IllegalStateException("CAMPAIGN_DESTINATION_UNAVAILABLE");return b;}
        if(mode==DifficultyId.NORMAL)throw new IllegalStateException("INITIAL_CAMPAIGN_DEFAULT_NOT_REGISTERED");
        var u=Universe.get();var path=u.getWorldsPath().resolve(name);
        if(Files.exists(path)||u.getWorld(name)!=null)throw new IllegalStateException("UNOWNED_DIFFICULTY_WORLD_PATH:"+name);
        var config=new WorldConfig();
        var initial=u.getDefaultWorld();if(initial==null)throw new IllegalStateException("INITIAL_CAMPAIGN_NOT_LOADED");
        config.setWorldGenProvider(initial.getWorldConfig().getWorldGenProvider());
        config.setSeed(initial.getWorldConfig().getSeed()^(mode==DifficultyId.NIGHTMARE?0x4E494748544CL:0x48454C4CL));
        config.setSavingConfig(true);config.setSavingPlayers(true);config.setDeleteOnRemove(false);config.setDeleteOnUniverseStart(false);
        var b=new WorldDifficultyRegistry.Binding(config.getUuid(),name,WorldDifficultyRegistry.Kind.CAMPAIGN,mode,
                "rpg.encounters."+mode.name().toLowerCase(Locale.ROOT)+".pending","server-character",true);
        // Register before AddWorldEvent. A partial provision fails closed on restart instead of regenerating a lost world.
        registry.register(b);
        u.makeWorld(name,path,config).join();return b;
    }
}
