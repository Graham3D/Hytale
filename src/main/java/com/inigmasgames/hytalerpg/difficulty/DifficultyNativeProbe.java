package com.inigmasgames.hytalerpg.difficulty;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleDifficultyCombat;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/** Real ECS probe, only on an explicitly named empty isolated save. Never connected acceptance. */
public final class DifficultyNativeProbe extends AbstractAsyncCommand {
    private final HytaleDifficultyTravel travel;private final HytaleDifficultyCombat combat;private final EncounterProfileResolver profiles;
    public DifficultyNativeProbe(HytaleDifficultyTravel travel,HytaleDifficultyCombat combat,EncounterProfileResolver profiles){
        super("probe","Run native difficulty checks on an explicitly isolated empty test server.");
        requirePermission(com.inigmasgames.hytalerpg.commands.RpgDifficultyCommand.AUTHOR_PERMISSION);this.travel=travel;this.combat=combat;this.profiles=profiles;
    }
    @Override protected CompletableFuture<Void> executeAsync(CommandContext command){
        String configured=System.getProperty("rpg.difficulty.probeRoot","");
        if(configured.isBlank())return CompletableFuture.failedFuture(new IllegalStateException("DIFFICULTY_PROBE_REQUIRES_ISOLATED_ROOT"));
        Path root=Path.of(configured).toAbsolutePath().normalize();
        if(Universe.get().getWorlds().values().stream().anyMatch(w->w.getPlayerCount()!=0||!w.getSavePath().toAbsolutePath().normalize().startsWith(root)))
            return CompletableFuture.failedFuture(new IllegalStateException("DIFFICULTY_PROBE_EMPTY_ISOLATED_WORLD_REQUIRED"));
        CompletableFuture<Void> sequence=CompletableFuture.completedFuture(null);
        for(var mode:DifficultyId.values())sequence=sequence.thenCompose(v->travel.prepare(new UUID(0,0),mode).thenCompose(destination->{
            var world=Universe.get().getWorld(destination.world());var ids=new ArrayList<UUID>();
            return CompletableFuture.runAsync(()->{
                var store=world.getEntityStore().getStore();
                for(var golem:GolemMilestones.load().golems()){
                    UUID placement=UUID.randomUUID();
                    var pair=NPCPlugin.get().spawnEntity(store,NPCPlugin.get().getIndex(golem.roleId()),new org.joml.Vector3d(destination.x(),destination.y(),destination.z()),
                            new com.hypixel.hytale.math.vector.Rotation3f(),null,
                            (entity,holder,s)->holder.addComponent(CampaignEncounterProjection.getComponentType(),new CampaignEncounterProjection(destination.world(),placement,golem.roleId())),null);
                    if(pair==null)throw new IllegalStateException("DIFFICULTY_PROBE_SPAWN_FAILED");
                    ids.add(store.getComponent(pair.first(),UUIDComponent.getComponentType()).getUuid());
                }
            },world).thenCompose(ignored->awaitProfiles(world,ids,100)).thenRunAsync(()->evaluate(command,world,ids,mode),world)
                    .whenCompleteAsync((ignored,error)->{var store=world.getEntityStore().getStore();for(UUID id:ids){var ref=world.getEntityRef(id);if(ref!=null&&ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);}},world);
        }).toCompletableFuture());
        for(var mode:DifficultyId.values())sequence=sequence.thenCompose(v->persistedProbe(command,mode,root));
        return sequence.whenComplete((ignored,error)->command.sendMessage(Message.raw(error==null?"RPG_DIFFICULTY_NATIVE_PROBE PASS worlds=3 connectedProof=false":"RPG_DIFFICULTY_NATIVE_PROBE FAIL "+HytaleDifficultyPortals.failure(error))));
    }
    private record SavedProbe(UUID world,UUID enemy,float wounded,com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry.Spawn source){}
    private CompletableFuture<Void> persistedProbe(CommandContext command,DifficultyId mode,Path root){
        Path path=root.resolve("difficulty-native-persistence-v2-"+mode+".json");var json=new com.google.gson.Gson();
        return travel.prepare(new UUID(0,0),mode).thenCompose(destination->CompletableFuture.supplyAsync(()->{
            try{return Files.exists(path)?json.fromJson(Files.readString(path),SavedProbe.class):null;}catch(java.io.IOException e){throw new CompletionException(e);}
        }).thenCompose(saved->{
            var world=Universe.get().getWorld(destination.world());
            if(saved!=null){
                if(!saved.world().equals(destination.world()))return CompletableFuture.failedFuture(new IllegalStateException("PROBE_WORLD_IDENTITY_CHANGED"));
                return awaitProfiles(world,List.of(saved.enemy()),100).thenRunAsync(()->{
                    var source=combat.snapshot(saved.world(),saved.enemy()).orElseThrow();if(!saved.source().equals(source))throw new IllegalStateException("PROBE_FROZEN_SOURCE_CHANGED_AFTER_RESTART");
                    var stats=world.getEntityStore().getStore().getComponent(world.getEntityRef(saved.enemy()),EntityStatMap.getComponentType());
                    check(saved.wounded(),health(stats),"savedNativeNpcWoundAfterRestart");check(source.combat().maxHealth(),stats.get(DefaultEntityStatTypes.getHealth()).getMax(),"savedNativeNpcMaximumAfterRestart");
                    command.sendMessage(Message.raw("RPG_DIFFICULTY_NATIVE_RESTART PASS mode="+mode+" world="+saved.world()+" enemy="+saved.enemy()+" health="+health(stats)+" frozenSource=true connectedProof=false"));
                },world);
            }
            return CompletableFuture.supplyAsync(()->{
                var store=world.getEntityStore().getStore();String role="Golem_Crystal_Flame";
                var pair=NPCPlugin.get().spawnEntity(store,NPCPlugin.get().getIndex(role),new org.joml.Vector3d(destination.x(),destination.y(),destination.z()),new com.hypixel.hytale.math.vector.Rotation3f(),null,
                        (entity,holder,s)->holder.addComponent(CampaignEncounterProjection.getComponentType(),new CampaignEncounterProjection(destination.world(),UUID.randomUUID(),role)),null);
                if(pair==null)throw new IllegalStateException("PERSISTENCE_PROBE_SPAWN_FAILED");return store.getComponent(pair.first(),UUIDComponent.getComponentType()).getUuid();
            },world).thenCompose(enemy->awaitProfiles(world,List.of(enemy),100).thenApplyAsync(v->{
                var store=world.getEntityStore().getStore();var ref=world.getEntityRef(enemy);var damage=new Damage(Damage.NULL_SOURCE,DamageCause.PHYSICAL,7);DamageSystems.executeDamage(ref,store,damage);
                var source=combat.snapshot(destination.world(),enemy).orElseThrow();float wounded=health(store.getComponent(ref,EntityStatMap.getComponentType()));check(source.combat().maxHealth()-7,wounded,"persistedProbeWound");
                return new SavedProbe(destination.world(),enemy,wounded,source);
            },world)).thenAcceptAsync(marker->{
                try{Files.writeString(path,json.toJson(marker));}catch(java.io.IOException e){throw new CompletionException(e);}
                command.sendMessage(Message.raw("RPG_DIFFICULTY_NATIVE_RESTART_PREPARED mode="+mode+" world="+marker.world()+" enemy="+marker.enemy()+" health="+marker.wounded()+" restartRequired=true"));
            });
        })).toCompletableFuture();
    }
    private CompletableFuture<Void> awaitProfiles(World world,List<UUID> ids,int remaining){
        return CompletableFuture.supplyAsync(()->ids.stream().allMatch(id->combat.snapshot(world.getWorldConfig().getUuid(),id).isPresent()),world).thenCompose(ready->{
            if(ready)return CompletableFuture.completedFuture(null);
            if(remaining==0)return CompletableFuture.failedFuture(new IllegalStateException("DIFFICULTY_NATIVE_PROJECTION_TIMEOUT"));
            return CompletableFuture.runAsync(()->{},CompletableFuture.delayedExecutor(100,TimeUnit.MILLISECONDS)).thenCompose(v->awaitProfiles(world,ids,remaining-1));
        });
    }
    private void evaluate(CommandContext command,World world,List<UUID> ids,DifficultyId mode){
        var store=world.getEntityStore().getStore();var worldId=world.getWorldConfig().getUuid();
        // Manual wild-role probes validate native stats only; they never forge natural-spawn reward provenance.
        var anchor=store.getComponent(world.getEntityRef(ids.getFirst()),com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
        for(var role:AuthoredEncounterCatalog.load().roles())if(role.campaignRegion().isBlank()){
            var pair=NPCPlugin.get().spawnEntity(store,NPCPlugin.get().getIndex(role.id()),anchor.getPosition(),anchor.getRotation(),null,null,null);
            if(pair==null)throw new IllegalStateException("WILD_STAT_PROBE_SPAWN_FAILED");var ref=pair.first();
            try{
                var stats=store.getComponent(ref,EntityStatMap.getComponentType());check(role.nativeHealth(),stats.get(DefaultEntityStatTypes.getHealth()).getMax(),"wildNativeBaseline/"+role.id());
                var id=store.getComponent(ref,UUIDComponent.getComponentType()).getUuid();
                var profile=profiles.resolveAuthored(worldId,id,role.id(),com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry.load().biomes().getFirst().key());
                HytaleDifficultyCombat.projectHealth(stats,profile,true);check(profile.maxHealth(),stats.get(DefaultEntityStatTypes.getHealth()).getMax(),"wildProjectedMaximum/"+role.id());
                command.sendMessage(Message.raw("RPG_DIFFICULTY_NATIVE_WILD_STATS PASS mode="+mode+" role="+role.id()+" native="+role.nativeHealth()+" max="+profile.maxHealth()+" rewardProvenanceForged=false"));
            }finally{if(ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);}
        }
        for(UUID id:ids){var spawn=combat.snapshot(worldId,id).orElseThrow();var ref=world.getEntityRef(id);var stats=store.getComponent(ref,EntityStatMap.getComponentType());
            check(spawn.combat().maxHealth(),stats.get(DefaultEntityStatTypes.getHealth()).getMax(),"maxHealth/"+spawn.roleId());
            var physical=new Damage(Damage.NULL_SOURCE,DamageCause.PHYSICAL,10);float before=health(stats);DamageSystems.executeDamage(ref,store,physical);
            check(10,before-health(stats),"nativePhysical/"+spawn.roleId());
            float wounded=health(stats);HytaleDifficultyCombat.projectHealth(stats,spawn.combat(),false);HytaleDifficultyCombat.projectHealth(stats,spawn.combat(),false);
            check(wounded,health(stats),"reapplyMustNotHeal");check(spawn.combat().maxHealth(),stats.get(DefaultEntityStatTypes.getHealth()).getMax(),"reapplyMustNotStack");
            var encoded=EntityStatMap.CODEC.encode(stats,new com.hypixel.hytale.codec.ExtraInfo());
            var restored=EntityStatMap.CODEC.decode(encoded,new com.hypixel.hytale.codec.ExtraInfo());restored.update();HytaleDifficultyCombat.projectHealth(restored,spawn.combat(),false);
            check(wounded,health(restored),"nativeStatCodecReloadMustNotHeal");
            command.sendMessage(Message.raw("RPG_DIFFICULTY_NATIVE_ROLE PASS mode="+mode+" role="+spawn.roleId()+" level="+spawn.level()+" max="+stats.get(DefaultEntityStatTypes.getHealth()).getMax()+" wounded="+wounded+" reloadNoHeal=true"));
        }
        var source=world.getEntityRef(ids.getFirst());var target=world.getEntityRef(ids.get(1));var targetStats=store.getComponent(target,EntityStatMap.getComponentType());
        double factor=combat.snapshot(worldId,ids.getFirst()).orElseThrow().combat().difficultyDamageFactor();
        var damage=new Damage(new Damage.EntitySource(source),DamageCause.PHYSICAL,10);float before=health(targetStats);DamageSystems.executeDamage(target,store,damage);
        // Installed ApplyDamage rounds its float amount with Math.round before subtracting Health.
        check(Math.round((float)(10*factor)),before-health(targetStats),"nativeOutgoing");
        // Same event cannot apply difficulty twice even if another observer re-enters that stage.
        double once=damage.getAmount();check(Math.round((float)(10*factor)),once,"nativeOutgoingAmount");
        var projectile=new Damage(new Damage.ProjectileSource(source,source),DamageCause.PROJECTILE,10);before=health(targetStats);DamageSystems.executeDamage(target,store,projectile);
        check(Math.round((float)(10*factor)),before-health(targetStats),"nativeProjectileOwner");
        for(UUID id:ids){var spawn=combat.snapshot(worldId,id).orElseThrow();var role=AuthoredEncounterCatalog.load().roles().stream().filter(r->r.id().equals(spawn.roleId())).findFirst().orElseThrow();
            for(var channel:role.affinities()){
                String cause=switch(channel){case FIRE->"Fire";case COLD->"Ice";case LIGHTNING->"Lightning";case WIND->"Wind";case NATURE->"RPG_Nature";default->throw new IllegalStateException("PROBE_CHANNEL_NOT_AUTHORED");};
                var ref=world.getEntityRef(id);var stats=store.getComponent(ref,EntityStatMap.getComponentType());var hit=new Damage(Damage.NULL_SOURCE,DamageCause.getAssetMap().getAsset(cause),20);
                before=health(stats);DamageSystems.executeDamage(ref,store,hit);double expected=spawn.combat().resistance().resolve(channel,20).amount();
                check(expected,before-health(stats),"element/"+channel);if(spawn.combat().resistance().immunities().contains(channel)&&!hit.isCancelled())throw new IllegalStateException("IMMUNITY_NOT_OWNED_BY_NATIVE_DAMAGE");
                command.sendMessage(Message.raw("RPG_DIFFICULTY_NATIVE_ELEMENT PASS mode="+mode+" role="+role.id()+" channel="+channel+" expected="+expected+" actual="+(before-health(stats))+" cancelled="+hit.isCancelled()));
            }
        }
        command.sendMessage(Message.raw("RPG_DIFFICULTY_NATIVE_WORLD PASS mode="+mode+" world="+worldId+" factor="+factor+" melee="+once+" projectile="+projectile.getAmount()+" nativeRounding=Math.round connectedProof=false"));
    }
    private static float health(EntityStatMap stats){return stats.get(DefaultEntityStatTypes.getHealth()).get();}
    /** Test isolation only: native regeneration is an independent Health writer, not a reload defect. */
    public static void freezeRegenerationInIsolatedProbe(Store<EntityStore> store,Ref<EntityStore> ref){
        String configured=System.getProperty("rpg.difficulty.probeRoot","");if(configured.isBlank())return;
        var world=store.getExternalData().getWorld();
        if(!world.getSavePath().toAbsolutePath().normalize().startsWith(Path.of(configured).toAbsolutePath().normalize())
                ||Universe.get().getWorlds().values().stream().anyMatch(w->w.getPlayerCount()!=0))return;
        var regen=store.getComponent(ref,com.hypixel.hytale.server.core.modules.entity.component.HealthRegenState.getComponentType());
        if(regen!=null)regen.setRegenEnabled(false);
    }
    private static void check(double expected,double actual,String detail){if(Math.abs(expected-actual)>.025)throw new IllegalStateException("NATIVE_DIFFICULTY_MISMATCH:"+detail+" expected="+expected+" actual="+actual);}
}
