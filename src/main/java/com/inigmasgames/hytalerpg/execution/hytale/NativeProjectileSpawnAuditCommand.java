package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.command.system.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.*;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.inigmasgames.hytalerpg.combat.*;
import com.inigmasgames.hytalerpg.combat.power.*;
import com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets;
import com.inigmasgames.hytalerpg.combat.resource.ResourceCost;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.links.*;
import com.inigmasgames.hytalerpg.progress.RpgPlayerState;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.joml.Vector3d;

/** Explicitly opt-in, empty isolated-server-only native regression. Never registered in normal play. */
public final class NativeProjectileSpawnAuditCommand extends AbstractCommand {
    private final HytaleSkillExecutionSystem system;
    public NativeProjectileSpawnAuditCommand(HytaleSkillExecutionSystem system){super("rpg-native-spawn-audit","Isolated native projectile construction regression");this.system=system;}
    @Override protected CompletableFuture<Void> execute(CommandContext command){
        var world=Universe.get().getDefaultWorld();var result=new CompletableFuture<Void>();
        var expected=java.nio.file.Path.of(System.getProperty("rpg.projectileSpawnAuditRoot","UNSET")).toAbsolutePath().normalize();
        if(!Boolean.getBoolean("rpg.projectileSpawnAudit")||world==null||!world.getSavePath().toAbsolutePath().normalize().startsWith(expected)
                ||Universe.get().getWorlds().values().stream().anyMatch(w->w.getPlayerCount()!=0))return CompletableFuture.failedFuture(new IllegalStateException("ISOLATED_EMPTY_WORLD_REQUIRED"));
        world.getChunkAsync(0L).whenComplete((auditChunk,loadFailure)->{
            if(loadFailure!=null){result.completeExceptionally(loadFailure);return;}
            world.execute(()->{
            auditChunk.addKeepLoaded();
            var store=world.getEntityStore().getStore();Ref<EntityStore> actor=null;var spawned=new AtomicReference<Ref<EntityStore>>();var deferred=new AtomicBoolean();
            try{
                UUID owner=UUID.randomUUID();var holder=EntityStore.REGISTRY.newHolder();
                holder.addComponent(UUIDComponent.getComponentType(),new UUIDComponent(owner));
                holder.addComponent(TransformComponent.getComponentType(),new TransformComponent(new Vector3d(0,200,0),new com.hypixel.hytale.math.vector.Rotation3f()));
                holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());actor=store.addEntity(holder,AddReason.SPAWN);
                var context=context(owner);var source=ProjectileConfig.getAssetMap().getAsset("Projectile_Config_RPG_Fire_Bolt");
                NativeBlizzardVisuals.audit(store,auditChunk,context);
                NativeHealingBeamVisuals.audit(store,context);
                HealingParticleVisuals.audit(store,context);
                HealingPresentationProbe.auditChannel(store,context);
                NativeManaReplicationAudit.audit(store);
                if(Boolean.getBoolean("rpg.healingPresentationProbe"))HealingPresentationProbe.audit(store);
                var nativeActor=actor;var ran=new AtomicBoolean();
                store.forEachChunk((java.util.function.BiConsumer<ArchetypeChunk<EntityStore>,CommandBuffer<EntityStore>>)(chunk,buffer)->{
                    if(!ran.compareAndSet(false,true))return;
                    try{
                        var unexpected=ProjectileModule.get().spawnProjectile(nativeActor,buffer,source,new Vector3d(0,200,0),new Vector3d(0,0,1));
                        buffer.tryRemoveEntity(unexpected,RemoveReason.REMOVE);throw new IllegalStateException("OLD_BOUNDARY_DID_NOT_REPRODUCE");
                    }catch(IllegalArgumentException expectedFailure){
                        if(!"Specified map is empty".equals(expectedFailure.getMessage()))throw expectedFailure;
                        command.sendMessage(Message.raw("RPG_NATIVE_SPAWN_CONTROL expectedFailure=Specified_map_is_empty mapClass="+source.getInteractions().getClass().getName()+" stage=Interactions_constructor"));
                    }
                    spawned.set(system.auditProjectileCarrier(context,nativeActor,buffer));
                    if(spawned.get().isValid())throw new IllegalStateException("EXPECTED_NATIVE_INSERTION_TO_BE_QUEUED");
                    system.advancePendingAuditProjectile(owner,buffer);
                });
                var ref=Objects.requireNonNull(spawned.get(),"NATIVE_CARRIER_NOT_CREATED");
                if(!ref.isValid())throw new IllegalStateException("NATIVE_CARRIER_NOT_COMMITTED");
                var physics=Objects.requireNonNull(store.getComponent(ref,StandardPhysicsProvider.getComponentType()));
                var velocity=Objects.requireNonNull(store.getComponent(ref,com.hypixel.hytale.server.core.modules.physics.component.Velocity.getComponentType()));
                if(Math.abs(physics.getVelocity().length()-24)>1e-6||!store.getComponent(ref,Interactions.getComponentType()).isEmpty())throw new IllegalStateException("NATIVE_CARRIER_CONTRACT_CHANGED");
                var rolledBack=new AtomicReference<Ref<EntityStore>>();var rollbackRan=new AtomicBoolean();
                store.forEachChunk((java.util.function.BiConsumer<ArchetypeChunk<EntityStore>,CommandBuffer<EntityStore>>)(chunk,buffer)->{
                    if(!rollbackRan.compareAndSet(false,true))return;
                    var pending=system.auditProjectileCarrier(context(owner),nativeActor,buffer);rolledBack.set(pending);
                    if(pending.isValid())throw new IllegalStateException("ROLLBACK_FIXTURE_NOT_PENDING");
                    system.cleanupAuditProjectile(pending);buffer.tryRemoveEntity(pending,RemoveReason.REMOVE);
                });
                if(rolledBack.get()==null||rolledBack.get().isValid())throw new IllegalStateException("PENDING_NATIVE_ROLLBACK_FAILED");
                // A grounded/resting carrier must still expire by RPG's real monotonic lifetime.
                physics.setState(StandardPhysicsProvider.STATE.RESTING);
                CompletableFuture.delayedExecutor(1300,TimeUnit.MILLISECONDS).execute(()->world.execute(()->{
                    try{
                        var once=new AtomicBoolean();
                        store.forEachChunk((java.util.function.BiConsumer<ArchetypeChunk<EntityStore>,CommandBuffer<EntityStore>>)(chunk,buffer)->{
                            if(once.compareAndSet(false,true))system.advancePendingAuditProjectile(owner,buffer);
                        });
                        if(!once.get())throw new IllegalStateException("EXPIRY_OWNER_ADVANCE_NOT_EXECUTED");
                        if(ref.isValid())throw new IllegalStateException("RESTING_PROJECTILE_DID_NOT_EXPIRE");
                        command.sendMessage(Message.raw("RPG_NATIVE_SPAWN_INTEGRATION result=PASS config=Projectile_Config_RPG_Fire_Bolt nativeRefValid=true physicsVelocity=24 interactionRoots=0 pendingRollback=true productionCarrier=true connectedProof=false sameTickAdvance=true restingExpiry=true"));
                        result.complete(null);
                    }catch(Throwable failure){com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atSevere().withCause(failure).log("RPG_NATIVE_SPAWN_INTEGRATION result=FAIL");result.completeExceptionally(failure);}
                    finally{system.cleanupAuditProjectile(ref);if(ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);if(nativeActor.isValid())store.removeEntity(nativeActor,RemoveReason.REMOVE);auditChunk.removeKeepLoaded();}
                }));
                deferred.set(true);
            }catch(Throwable failure){com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atSevere().withCause(failure).log("RPG_NATIVE_SPAWN_INTEGRATION result=FAIL");result.completeExceptionally(failure);}
            finally{if(!deferred.get()){if(spawned.get()!=null){system.cleanupAuditProjectile(spawned.get());if(spawned.get().isValid())store.removeEntity(spawned.get(),RemoveReason.REMOVE);}if(actor!=null&&actor.isValid())store.removeEntity(actor,RemoveReason.REMOVE);auditChunk.removeKeepLoaded();}}
        });});return result;
    }
    private static SkillExecutionContext context(UUID owner){
        var catalog=RpgCatalog.loadCanonical();var compatibility=new CompatibilityService();var state=RpgPlayerState.create(owner);
        state.learnedSkills.add("fire_bolt");state.skill(SkillSlot.SKILL01,new SkillId("fire_bolt"));
        var plan=new LinkCompiler(catalog,new RpgLinkGraphService(catalog,compatibility),compatibility).compile(state).plans().get(SkillSlot.SKILL01);
        var profile=Stage04SkillProfiles.loadCanonical(catalog).require("fire_bolt");var kernel=RpgCombatKernel.createProduction();
        var item=HytaleEquipmentAdapter.describe("Weapon_Staff_Mithril",Map.of("Family",new String[]{"Staff"},"Type",new String[]{"Weapon"}));
        String root="isolated-native-spawn-"+UUID.randomUUID();var power=kernel.basePower().resolve(new BasePowerResolver.Request(BasePowerSource.MAGIC_WEAPON,item.power(),null));
        var snapshot=kernel.snapshots().capture(root,root,owner,kernel.derivedStats().derive(Map.of()),power,plan,.95,ModifierBuckets.NONE,ResourceCost.NONE,0,Map.of());
        return new SkillExecutionContext(new SkillExecutionRequest(owner,SkillSlot.SKILL01,"ISOLATED_CONSTRUCTION_ONLY",0,root,Vec3.ZERO),root,root,profile,plan,snapshot,new SkillExecutionPort.Equipment(item,null));
    }
}
