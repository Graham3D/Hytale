package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.*;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.interaction.event.InteractionChainStartEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.inigmasgames.hytalerpg.combat.hytale.NativeDamageLeafInteraction;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter;
import com.inigmasgames.hytalerpg.enemies.*;
import com.inigmasgames.hytale.patch.NativeProjectileReceiptHook;
import com.inigmasgames.hytale.patch.NativeDamageReceiptHook;
import com.inigmasgames.hytalerpg.combat.hytale.NativeDamageLeafInteraction;
import java.util.*;
import java.util.function.*;

/** Native start-event/root lifetime owner. Only published, explicitly certified actors may attach. */
public final class NativeEnemyActions {
    /** Native references captured at leaf delivery, consumed only after its complete Health receipt. */
    public record Delivery(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> actor,
            Ref<EntityStore> victim,EnemyDescriptor descriptor,EnemyAppliedHit hit,BooleanSupplier current,
            EnemyStatusEffects.Source statusSource,String frozenSeed,boolean nativeImpulse,com.inigmasgames.hytalerpg.execution.math.Vec3 forward){}
    public record SourceState(EnemyAffixSnapshot providers,double difficultyFactor,double nativeSupportFactor,double directWeakening){
        public SourceState{
            Objects.requireNonNull(providers);
            if(!Double.isFinite(difficultyFactor)||difficultyFactor<=0||!Double.isFinite(nativeSupportFactor)||nativeSupportFactor<=0
                    ||!Double.isFinite(directWeakening)||directWeakening<0||directWeakening>1)
                throw new IllegalArgumentException("ENEMY_ACTION_SOURCE_STATE");
        }
    }
    private static final class Actor {
        final Store<EntityStore> store;final Ref<EntityStore> ref;final EnemyDescriptor descriptor;
        final List<NativeEnemyAction.Binding> bindings;final Supplier<SourceState> source;
        final Supplier<String> nextReplayRoot;final String seed;final Consumer<EnemyAppliedHit> accepted;final Consumer<Throwable> rejected;
        final List<NativeEnemyAction> roots=new ArrayList<>();volatile boolean attached=true,quarantined;
        boolean sparRejectedLogged,missingSnapshotLogged;
        final Set<ProjectileReceiptKey> projectileReceipts=new HashSet<>();
        Actor(Store<EntityStore> store,Ref<EntityStore> ref,EnemyDescriptor descriptor,List<NativeEnemyAction.Binding> bindings,
                Supplier<SourceState> source,Supplier<String> nextReplayRoot,String seed,Consumer<EnemyAppliedHit> accepted,Consumer<Throwable> rejected){
            this.store=store;this.ref=ref;this.descriptor=descriptor;this.bindings=List.copyOf(bindings);this.source=source;
            this.nextReplayRoot=nextReplayRoot;this.seed=seed;this.accepted=accepted;this.rejected=rejected;
        }
    }
    private record ProjectileReceiptKey(UUID projectile,String root,String strike,UUID victim){}
    private final Map<Ref<EntityStore>,Actor> actors=new IdentityHashMap<>();
    private final NativeEnemyOutgoingEffects outgoing;
    private Consumer<Delivery> reactions;
    public NativeEnemyActions(NativeEnemyOutgoingEffects outgoing,Consumer<Delivery> reactions){
        this.outgoing=Objects.requireNonNull(outgoing);this.reactions=Objects.requireNonNull(reactions);
    }
    /** Composition-time extension over the same completed original-hit receipt. */
    public synchronized void addReaction(Consumer<Delivery> reaction){
        if(!actors.isEmpty())throw new IllegalStateException("ENEMY_ACTION_REACTION_ALREADY_ACTIVE");
        reactions=reactions.andThen(Objects.requireNonNull(reaction));
    }
    /** Register before publication. The replay owner allocates/persists each accepted logical root identity. */
    public synchronized AutoCloseable attach(Store<EntityStore> store,Ref<EntityStore> ref,EnemyDescriptor descriptor,
            List<NativeEnemyAction.Binding> bindings,Supplier<SourceState> source,Supplier<String> nextReplayRoot,
            String frozenSeed,Consumer<EnemyAppliedHit> accepted,Consumer<Throwable> rejected){
        if(!store.isInThread()||ref==null||!ref.isValid()||ref.getStore()!=store)throw new IllegalStateException("ENEMY_ACTION_ATTACH_THREAD_OR_ENTITY");
        var uuid=store.getComponent(ref,UUIDComponent.getComponentType());var npc=store.getComponent(ref,NPCEntity.getComponentType());
        if(uuid==null||!uuid.getUuid().equals(descriptor.entityId())||npc==null||!npc.getRoleName().equals(descriptor.nativeRoleId())
                ||!store.getExternalData().getWorld().getWorldConfig().getUuid().equals(descriptor.worldId()))
            throw new IllegalStateException("ENEMY_ACTION_ATTACH_DESCRIPTOR");
        if(bindings.isEmpty()||bindings.size()>64||actors.size()>=4096||actors.containsKey(ref))throw new IllegalStateException("ENEMY_ACTION_ATTACH_BOUNDS_OR_DUPLICATE");
        var keys=new HashMap<String,List<NativeEnemyAction.Binding>>();
        for(var binding:bindings){
            if(!binding.revision().equals(descriptor.nativeBindingRevision()))
                throw new IllegalArgumentException("ENEMY_ACTION_ATTACH_BINDINGS");
            String key=binding.type().name()+"/"+binding.root().getId();
            var priors=keys.computeIfAbsent(key,ignored->new ArrayList<>());
            for(var prior:priors){
                // A native combat evaluator can select one of several variable maps for
                // the same original root. Each variant must own entirely distinct leaves.
                if(prior.root()!=binding.root()||!prior.projectileStrikes().isEmpty()
                        ||!binding.projectileStrikes().isEmpty()||prior.strikes().isEmpty()
                        ||binding.strikes().isEmpty()||prior.strikes().stream().anyMatch(left->
                        binding.strikes().stream().anyMatch(right->left.leaf()==right.leaf())))
                    throw new IllegalArgumentException("ENEMY_ACTION_ATTACH_BINDINGS");
            }
            priors.add(binding);
        }
        if(EnemyStatusEffects.requiresSource(descriptor)&&bindings.stream().anyMatch(binding->
                binding.strikes().stream().anyMatch(strike->strike.statusSource()==null)
                ||binding.projectileStrikes().stream().anyMatch(strike->strike.statusSource()==null)))
            throw new IllegalArgumentException("ENEMY_ACTION_STATUS_SOURCE_NOT_CERTIFIED");
        Objects.requireNonNull(source);Objects.requireNonNull(nextReplayRoot);Objects.requireNonNull(accepted);Objects.requireNonNull(rejected);
        if(frozenSeed==null||frozenSeed.isBlank())throw new IllegalArgumentException("ENEMY_ACTION_SEED");
        var actor=new Actor(store,ref,descriptor,bindings,source,nextReplayRoot,frozenSeed,accepted,rejected);actors.put(ref,actor);
        return ()->{synchronized(this){if(!actor.store.isInThread())throw new IllegalStateException("ENEMY_ACTION_DETACH_THREAD");
            if(actors.get(ref)==actor)actors.remove(ref);actor.attached=false;actor.roots.clear();actor.projectileReceipts.clear();}};
    }
    public synchronized void clear(){for(var actor:actors.values()){
        actor.attached=false;actor.roots.clear();actor.projectileReceipts.clear();}actors.clear();}
    /** Keep routed leaves owned, but refuse every new/continuing action after an uncertain world result. */
    public synchronized void quarantineWorld(UUID world){
        for(var actor:actors.values())if(actor.descriptor.worldId().equals(world)
                &&actor.descriptor.spawnOrigin()!=EnemyRewardContext.Origin.QA)actor.quarantined=true;
    }
    /** The native damage seam runs inside this exact accepted leaf's current invocation. */
    public String originalDamageReceipt(NativeDamageReceiptHook.Context context){
        var invocation=NativeDamageLeafInteraction.currentInvocation();
        if(invocation==null||!(invocation.scope() instanceof EnemyNativeStrikeScope accepted))return null;
        var active=invocation.context();var chain=active.getChain();
        if(chain==null||chain.getRootInteraction()==null||chain.getInitialRootInteraction()==null
                ||context.chainId()!=chain.getChainId()
                ||!context.forkPath().equals(NativeEnemyAction.forkPath(chain.getForkedChainId()))
                ||!context.initialRoot().equals(chain.getInitialRootInteraction().getId())
                ||!context.operationRoot().equals(chain.getRootInteraction().getId())
                ||context.operationIndex()!=chain.getOperationIndex()
                ||context.operationCounter()!=active.getOperationCounter())
            throw new IllegalStateException("ENEMY_ORIGINAL_RECEIPT_NATIVE_CHAIN_CHANGED");
        return accepted.originalReceipt(context);
    }
    private synchronized void start(Ref<EntityStore> ref,Store<EntityStore> store,InteractionChainStartEvent event){
        // The native chain starts with finalState=Finished until its first tick. Capture at
        // the start event; waiting for NotFinished loses the immutable acceptance snapshot.
        var actor=actors.get(ref);if(actor==null||!acceptsRootStart(event))return;
        try{
            requireCurrent(actor,store);
            var candidates=actor.bindings.stream().filter(b->b.type()==event.getType()
                    &&b.root()==event.getChain().getInitialRootInteraction()).toList();
            NativeEnemyAction.Binding binding=candidates.isEmpty()?null:candidates.getFirst();
            if(candidates.size()>1){
                binding=null;
                for(var candidate:candidates)try{
                    candidate.validate(event.getChain().getContext());
                    if(binding!=null)throw new IllegalStateException("ENEMY_ACTION_VARIANT_AMBIGUOUS");
                    binding=candidate;
                }catch(IllegalStateException mismatched){
                    if("ENEMY_ACTION_VARIANT_AMBIGUOUS".equals(mismatched.getMessage()))throw mismatched;
                }
                if(binding==null)throw new IllegalStateException("ENEMY_ACTION_VARIANT_UNCERTIFIED");
            }
            if(binding==null){
                // Native Trork sparring uses this generic melee root with DamageFriendlies=true.
                // Its routed damage leaf has no accepted ME strike snapshot. Cancel the
                // uncertified spar chain before it can hit another member of the QA pack.
                if(uncertifiedTrorkSpar(actor.descriptor.nativeRoleId(),event.getRootInteractionId())){
                    event.setCancelled(true);
                    if(!actor.sparRejectedLogged){
                        actor.sparRejectedLogged=true;
                        com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log(
                                "RPG_ENEMY_NATIVE_SPAR_CANCELLED actor=%s role=%s root=%s",
                                actor.descriptor.entityId(),actor.descriptor.nativeRoleId(),event.getRootInteractionId());
                    }
                    return;
                }
                if(actor.bindings.stream().anyMatch(b->b.type()==event.getType()&&b.root().getId().equals(event.getRootInteractionId())))
                    throw new IllegalStateException("ENEMY_ACTION_NATIVE_ASSET_REBOUND");
                return; // Explicitly certified subset; unrelated native support actions retain their native path.
            }
            // The native manager, rather than a momentary Finished state on a parent
            // with Selector hit forks, owns the lifetime of an accepted action root.
            var nativeRoots=event.getChain().getContext().getInteractionManager().getChains();
            actor.roots.removeIf(r->nativeRoots.get(r.root().getChainId())!=r.root());
            if(actor.roots.stream().anyMatch(r->r.root()==event.getChain()))return;
            if(actor.roots.size()>=32)throw new IllegalStateException("ENEMY_ACTION_ACTIVE_ROOT_BOUNDS");
            var state=actor.source.get();var rootId=actor.nextReplayRoot.get();
            actor.roots.add(NativeEnemyAction.capture(store,ref,event.getChain(),binding,actor.descriptor,state.providers(),rootId,actor.seed,
                    state.difficultyFactor(),state.nativeSupportFactor(),state.directWeakening(),outgoing,()->actor.attached&&ref.isValid()));
        }catch(RuntimeException failure){event.setCancelled(true);actor.rejected.accept(failure);}
    }
    static boolean acceptsRootStart(InteractionChainStartEvent event){
        return !event.isCancelled()&&event.getChain().getForkedChainId()==null;
    }
    static boolean uncertifiedTrorkSpar(String role,String root){
        return "Trork_Warrior".equals(role)&&"Root_NPC_Attack_Melee".equals(root);
    }
    public synchronized NativeEnemyDamageInteraction.EnemyScope scope(NativeDamageLeafInteraction.Invocation call){
        var actor=actors.get(call.context().getEntity());if(actor==null)return null;
        // Hytale simulates the same native leaf before its authoritative tick. The
        // simulation must retain native operation flow, but has no accepted hit receipt.
        // The snapshot guard below remains mandatory for the real server invocation.
        if(call.context().getEntry()!=null&&call.context().getEntry().isUseSimulationState())return null;
        requireCurrent(actor,call.context().getCommandBuffer().getStore());
        NativeEnemyAction matched=null;
        for(var root:actor.roots)if(root.owns(call)){
            if(matched!=null)throw new IllegalStateException("ENEMY_ACTION_AMBIGUOUS_ACTIVE_ROOT");matched=root;
        }
        if(matched==null){
            // A routed leaf on a certified actor must never silently fall back to unsnapshotted native RNG.
            if(actor.bindings.stream().flatMap(b->b.strikes().stream()).anyMatch(s->s.leaf()==call.leaf())){
                if(!actor.missingSnapshotLogged){
                    actor.missingSnapshotLogged=true;
                    var chain=call.context().getChain();
                    com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                            "RPG_ENEMY_ACTION_SNAPSHOT_MISSING actor=%s role=%s chain=%s initial=%s type=%s activeRoots=%s chainId=%s forked=%s acceptedChainIds=%s ownerMatches=%s operation=%s operationMatchesLeaf=%s",
                            actor.descriptor.entityId(),actor.descriptor.nativeRoleId(),
                            chain==null||chain.getRootInteraction()==null?"null":chain.getRootInteraction().getId(),
                            chain==null||chain.getInitialRootInteraction()==null?"null":chain.getInitialRootInteraction().getId(),
                            chain==null?"null":chain.getType(),actor.roots.size(),
                            chain==null?"null":chain.getChainId(),chain!=null&&chain.getForkedChainId()!=null,
                            actor.roots.stream().map(r->Integer.toString(r.root().getChainId())).toList(),
                            call.context().getOwningEntity()==actor.ref,
                            chain==null||call.context().getEntry()==null?"null":call.context().getOperationCounter(),
                            chain!=null&&call.context().getEntry()!=null&&call.context().getOperationCounter()>=0
                                    &&call.context().getOperationCounter()<chain.getRootInteraction().getOperationMax()
                                    &&chain.getRootInteraction().getOperation(call.context().getOperationCounter())==call.leaf());
                }
                throw new IllegalStateException("ENEMY_ACTION_ACCEPTANCE_SNAPSHOT_MISSING");
            }
            return null;
        }
        var target=call.context().getTargetEntity();var buffer=call.context().getCommandBuffer();
        var statusSource=matched.statusSource((NativeEnemyDamageInteraction)call.leaf());
        boolean nativeImpulse=((NativeEnemyDamageInteraction)call.leaf()).ownsNativeImpulse();var forward=matched.forward();
        return matched.scope(call,hit->{
            reactions.accept(new Delivery(actor.store,buffer,actor.ref,target,actor.descriptor,hit,
                    ()->actor.attached&&actor.ref.isValid()&&actor.store==buffer.getStore(),statusSource,actor.seed,nativeImpulse,forward));
            actor.accepted.accept(hit);
        },actor.rejected);
    }
    /** Called by the version-pinned native pre-queue seam with the original holder. */
    public synchronized void attachProjectile(NativeProjectileReceiptHook.Context context,Holder<EntityStore> holder){
        Objects.requireNonNull(context);Objects.requireNonNull(holder);
        if(context.executor()==null)return; // No ME shooter; native launch remains untouched.
        Actor actor=null;
        for(var candidate:actors.values())if(candidate.descriptor.worldId().equals(context.world())
                &&candidate.descriptor.entityId().equals(context.executor())){
            if(actor!=null)throw new IllegalStateException("ENEMY_PROJECTILE_AMBIGUOUS_SOURCE");actor=candidate;
        }
        if(actor==null)return;
        if(actor.bindings.stream().noneMatch(binding->!binding.projectileStrikes().isEmpty()
                &&binding.root().getId().equals(context.initialRoot())))return;
        try{
            requireCurrent(actor,actor.store);
            if(context.owner()!=null&&!context.owner().equals(context.executor()))
                throw new IllegalStateException("ENEMY_PROJECTILE_PROXY_SOURCE_UNCERTIFIED");
            NativeEnemyAction.ProjectileLaunch accepted=null;
            for(var root:actor.roots){
                var match=root.projectileLaunch(context);
                if(match.isPresent()){
                    if(accepted!=null)throw new IllegalStateException("ENEMY_PROJECTILE_AMBIGUOUS_ROOT");
                    accepted=match.get();
                }
            }
            if(accepted==null)throw new IllegalStateException("ENEMY_PROJECTILE_ACCEPTED_ROOT_MISSING");
            var component=EnemyProjectileReceipt.getComponentType();
            var nativeProjectile=holder.getComponent(ProjectileComponent.getComponentType());
            var nativeId=holder.getComponent(UUIDComponent.getComponentType());
            // Native shoot() writes launch velocity to the projectile's physics provider.
            // The holder's Velocity component is initialized separately and is still zero here.
            var physics=nativeProjectile.getSimplePhysicsProvider();
            if(component==null||nativeProjectile==null||nativeProjectile.getProjectile()==null||nativeId==null
                    ||!nativeId.getUuid().equals(context.projectile())
                    ||!Objects.equals(nativeProjectile.getProjectileAssetName(),accepted.nativeProjectileAsset())
                    ||nativeProjectile.getProjectile().getDamage()!=accepted.nativeBaseDamage()
                    ||holder.getComponent(component)!=null||physics==null)
                throw new IllegalStateException("ENEMY_PROJECTILE_ORIGINAL_HOLDER_UNCERTIFIED");
            var transform=holder.getComponent(com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
            var direction=launchHorizontal(physics.getVelocity(),transform==null?null:transform.getRotation());
            var state=new EnemyProjectileReceipt.State(context.projectile(),context.executor(),
                    accepted.nativeProjectileAsset(),accepted.nativeBaseDamage(),accepted.offense(),
                    direction.x(),direction.z());
            state.require(context.projectile(),actor.descriptor.entityId(),actor.descriptor.worldId(),
                    actor.descriptor.encounterGeneration(),actor.descriptor.nativeBindingRevision(),accepted.nativeProjectileAsset());
            holder.addComponent(component,new EnemyProjectileReceipt(state));
        }catch(RuntimeException unsupported){
            quarantineWorld(actor.descriptor.worldId());
            throw unsupported; // Prevent native queueing of an uncertified ME projectile.
        }
    }
    /** Read the vector written by native shoot(), never the holder's pre-physics Velocity component. */
    static com.inigmasgames.hytalerpg.execution.math.Vec3 launchHorizontal(org.joml.Vector3dc velocity,
            com.hypixel.hytale.math.vector.Rotation3f rotation){
        if(velocity==null||!Double.isFinite(velocity.x())||!Double.isFinite(velocity.y())
                ||!Double.isFinite(velocity.z()))
            throw new IllegalStateException("ENEMY_PROJECTILE_LAUNCH_DIRECTION_MISSING");
        double x=velocity.x(),z=velocity.z(),horizontal=Math.hypot(x,z);
        if(horizontal<1e-9){
            // A vertical native shot still has an authored yaw. Preserve its horizontal
            // receipt direction without changing native flight or inferring from the shooter.
            if(rotation==null)throw new IllegalStateException("ENEMY_PROJECTILE_LAUNCH_DIRECTION_MISSING");
            var facing=com.hypixel.hytale.server.core.modules.physics.util.PhysicsMath.vectorFromAngles(
                    0,rotation.yaw(),new org.joml.Vector3d());
            x=facing.x();z=facing.z();horizontal=Math.hypot(x,z);
        }
        if(!Double.isFinite(horizontal)||horizontal<1e-9)
            throw new IllegalStateException("ENEMY_PROJECTILE_LAUNCH_DIRECTION_MISSING");
        return new com.inigmasgames.hytalerpg.execution.math.Vec3(x/horizontal,0,z/horizontal);
    }
    /** Existing post-Apply native Health observer calls this once for the original arrow packet. */
    public synchronized void deliveredProjectile(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,
            Ref<EntityStore> source,Ref<EntityStore> victim,EnemyProjectileReceipt.State receipt,
            HytaleDamageAdapter.NativeResult result){
        Actor actor=null;
        for(var candidate:actors.values())if(candidate.store==store
                &&candidate.descriptor.entityId().equals(receipt.sourceEntity())){
            if(actor!=null)throw new IllegalStateException("ENEMY_PROJECTILE_AMBIGUOUS_DELIVERY_SOURCE");
            actor=candidate;
        }
        if(actor==null)throw new IllegalStateException("ENEMY_PROJECTILE_DELIVERY_SOURCE_DETACHED");
        requireCurrent(actor,store);
        if(!source.isValid()||source.getStore()!=store||source.getIndex()!=actor.ref.getIndex())
            throw new IllegalStateException("ENEMY_PROJECTILE_DELIVERY_NATIVE_SOURCE_CHANGED");
        var offense=receipt.offense();var identity=offense.identity();
        if(!actor.descriptor.entityId().equals(receipt.sourceEntity())
                ||!actor.descriptor.logicalActorId().equals(identity.actorId())
                ||!actor.descriptor.worldId().equals(identity.worldId())
                ||actor.descriptor.encounterGeneration()!=offense.generation())
            throw new IllegalStateException("ENEMY_PROJECTILE_DELIVERY_ACTOR_CHANGED");
        var target=buffer.getComponent(victim,UUIDComponent.getComponentType());
        if(target==null)throw new IllegalStateException("ENEMY_PROJECTILE_DELIVERY_TARGET_MISSING");
        var binding=actor.bindings.stream().filter(b->b.root().getId().equals(identity.executionId())).findFirst()
                .orElseThrow(()->new IllegalStateException("ENEMY_PROJECTILE_DELIVERY_ROOT_CHANGED"));
        var strike=binding.projectileStrikes().stream().filter(s->s.authoredId().equals(identity.authoredTickId())
                &&s.leaf().getProjectileId().equals(receipt.nativeProjectileAsset())).findFirst()
                .orElseThrow(()->new IllegalStateException("ENEMY_PROJECTILE_DELIVERY_STRIKE_CHANGED"));
        var hit=EnemyAppliedHit.completed(offense,target.getUuid(),Map.of("Projectile",result));
        if(hit.actualHealthLoss()<=0)return;
        // A root can launch more than one original projectile. Its hit receipt is scoped to
        // the saved native holder, so separate arrows never consume each other's delivery.
        var key=new ProjectileReceiptKey(receipt.projectile(),identity.rootId(),identity.authoredTickId(),target.getUuid());
        if(actor.projectileReceipts.contains(key))return;
        if(actor.projectileReceipts.size()>=4096)throw new IllegalStateException("ENEMY_PROJECTILE_RECEIPT_BOUNDS");
        actor.projectileReceipts.add(key);
        var acceptedActor=actor;
        reactions.accept(new Delivery(store,buffer,source,victim,actor.descriptor,hit,
                ()->acceptedActor.attached&&source.isValid()&&acceptedActor.store==buffer.getStore(),
                strike.statusSource(),actor.seed,false,
                new com.inigmasgames.hytalerpg.execution.math.Vec3(receipt.forwardX(),0,receipt.forwardZ())));
        actor.accepted.accept(hit);
    }
    private static void requireCurrent(Actor actor,Store<EntityStore> store){
        if(!actor.attached||actor.quarantined||actor.store!=store||!store.isInThread()||!actor.ref.isValid()
                ||store.getComponent(actor.ref,EnemyStaging.getComponentType())!=null)
            throw new IllegalStateException("ENEMY_ACTION_STALE_OR_STAGED_BINDING");
    }
    public static final class Start extends EntityEventSystem<EntityStore,InteractionChainStartEvent>{
        private final NativeEnemyActions owner;
        public Start(NativeEnemyActions owner){super(InteractionChainStartEvent.class);this.owner=owner;}
        @Override public Query<EntityStore> getQuery(){return NPCEntity.getComponentType();}
        @Override public void handle(int i,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,InteractionChainStartEvent event){
            try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.EXECUTION)){
            owner.start(chunk.getReferenceTo(i),store,event);
        
            }}
    }
}
