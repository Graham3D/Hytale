package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entitystats.*;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.diagnostics.CombatTrace;
import com.inigmasgames.hytalerpg.combat.hytale.*;
import com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.area.AreaGeometry;
import com.inigmasgames.hytalerpg.execution.connection.ConnectionShape;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.support.*;
import com.inigmasgames.hytalerpg.progress.*;
import com.inigmasgames.hytalerpg.vfx.LinkTreeVfxService;
import java.util.*;

/** Native stat/ally adapters and player-owned Aura tick. No packet interception or HUD ownership. */
public final class HytaleSupportSystem extends EntityTickingSystem<EntityStore> {
    private final RpgLoadoutService loadouts;
    private final RpgCombatKernel kernel;
    private final CombatTrace trace;
    private final LinkTreeVfxService vfx;
    private final SupportRuntime runtime;
    private final HytaleBossBarTracker bosses;
    private final Set<UUID> cooldownSaveWarnings=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<String> mantlePresentationRoots=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<UUID> initialized=java.util.concurrent.ConcurrentHashMap.newKeySet(),initializing=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<UUID> completedCasterDeathSweeps=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingCasterDeathSweeps=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<UUID> casterDeathSweepWarnings=java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** R261-A integration seam: production configures this false until R261-B owns activation. */
    private boolean durableCycloneEnabled;
    private CycloneBarrierLedger durableCyclone;
    private CycloneReflectionJournal cycloneReflections;
    private CycloneSharedAegisLedger cycloneShared;
    private TrustedPlayerAllies trustedAllies;
    private CycloneActivationTransaction cycloneActivations;
    private record CycloneConnection(PlayerRef player,CycloneBarrierLedger.Lease lease){}
    private final Map<UUID,CycloneConnection> cycloneConnections=new java.util.concurrent.ConcurrentHashMap<>();
    private record LivePayment(PlayerRef player,Set<UUID> generations){}
    private final Map<UUID,LivePayment> liveSharedPayments=new java.util.concurrent.ConcurrentHashMap<>();
    private record SharedConnection(PlayerRef player,CycloneSharedAegisLedger.Lease lease){}
    private final Map<UUID,SharedConnection> sharedConnections=new java.util.concurrent.ConcurrentHashMap<>();
    private record NpcSharedConnection(Ref<EntityStore> entity,CycloneSharedAegisLedger.Lease lease){}
    private final Map<UUID,NpcSharedConnection> npcSharedConnections=new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID,java.util.concurrent.CompletableFuture<Void>> npcSharedPreloads=new java.util.concurrent.ConcurrentHashMap<>();
    private record CycloneLive(PlayerRef player,UUID generation,SkillExecutionContext context,
            CycloneReflectionJournal.Behavior behavior,java.util.concurrent.atomic.AtomicBoolean presented,
            java.util.concurrent.atomic.AtomicBoolean reflectionFailureReported){}
    private final Map<UUID,CycloneLive> cycloneLive=new java.util.concurrent.ConcurrentHashMap<>();
    public void configureDurableCyclone(java.nio.file.Path directory,boolean enabled){
        durableCyclone=new CycloneBarrierLedger(directory);
        cycloneReflections=new CycloneReflectionJournal(directory);
        cycloneShared=new CycloneSharedAegisLedger(directory);
        cycloneActivations=new CycloneActivationTransaction(durableCyclone);
        durableCycloneEnabled=enabled;
    }
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value="EI_EXPOSE_REP2",
            justification="The registered support and command handlers intentionally share one server trust authority.")
    public void configureTrustedPlayerAllies(TrustedPlayerAllies allies){trustedAllies=Objects.requireNonNull(allies);}
    public void preloadDurableCyclone(UUID player){
        if(durableCycloneEnabled){durableCyclone.preload(player);cycloneReflections.preload(player);}
    }
    /** ReadyPath composes native caster-save reads asynchronously. The child is
     * attached only after loaded native Mana evidence has been checked. */
    public java.util.concurrent.CompletionStage<Void> preloadSharedAegis(UUID recipient){
        return preloadSharedAegis(recipient,this::sharedPlayerGrantEligible);
    }
    private java.util.concurrent.CompletionStage<Void> preloadSharedAegis(UUID recipient,
            java.util.function.Predicate<CycloneSharedAegisLedger.Grant> eligible){
        if(!durableCycloneEnabled)return java.util.concurrent.CompletableFuture.completedStage(null);
        var grants=cycloneShared.childGrants(recipient);
        if(grants.isEmpty()){
            cycloneShared.preload(recipient,(caster,generation)->false,eligible);
            return java.util.concurrent.CompletableFuture.completedStage(null);
        }
        var proofs=new java.util.HashMap<UUID,java.util.concurrent.CompletableFuture<Boolean>>();
        for(var grant:grants){
            proofs.computeIfAbsent(grant.parentGeneration(),generation->{
                if(pendingCasterDeathSweeps.contains(grant.caster()))
                    return java.util.concurrent.CompletableFuture.completedFuture(false);
                var current=liveSharedPayments.get(grant.caster());
                if(current!=null&&current.generations().contains(generation))
                    return java.util.concurrent.CompletableFuture.completedFuture(true);
                return
                    com.hypixel.hytale.server.core.universe.Universe.get().getPlayerStorage().load(grant.caster())
                            .orTimeout(10,java.util.concurrent.TimeUnit.SECONDS)
                            .thenApply(holder->{
                                var stats=holder==null?null:holder.getComponent(EntityStatMap.getComponentType());
                                return CycloneNativePaymentMarker.kind(stats,generation)!=CycloneNativePaymentMarker.Kind.NONE;
                            }).exceptionally(failure->false);});
        }
        return java.util.concurrent.CompletableFuture.allOf(proofs.values().toArray(java.util.concurrent.CompletableFuture[]::new))
                .thenRunAsync(()->cycloneShared.preload(recipient,(caster,generation)->
                                !pendingCasterDeathSweeps.contains(caster)
                                        &&Boolean.TRUE.equals(proofs.get(generation).getNow(false)),
                        eligible));
    }
    private java.util.concurrent.CompletableFuture<Void> preloadNpcSharedAegis(UUID recipient){
        return java.util.concurrent.CompletableFuture.supplyAsync(
                        ()->preloadSharedAegis(recipient,this::sharedNpcGrantEligible))
                .thenCompose(stage->stage)
                .toCompletableFuture();
    }
    boolean durableCycloneEnabled(){return durableCycloneEnabled;}
    public com.inigmasgames.hytalerpg.ui.model.BarrierView barrierView(UUID player){
        if(!durableCycloneEnabled||durableCyclone==null)return com.inigmasgames.hytalerpg.ui.model.BarrierView.NONE;
        var binding=cycloneConnections.get(player);var shared=sharedConnections.get(player);
        try{
            var state=binding==null?null:durableCyclone.state(binding.lease());
            var child=shared==null?null:cycloneShared.view(shared.lease(),this::sharedPlayerGrantEligible);
            boolean own=state!=null&&state.phase()==CycloneBarrierLedger.Phase.ACTIVE;
            if(!own&&child==null)return state==null?com.inigmasgames.hytalerpg.ui.model.BarrierView.NONE
                    :new com.inigmasgames.hytalerpg.ui.model.BarrierView(state.revision(),null,0,0);
            UUID generation=own&&child==null?state.generation():!own?child.generation()
                    :UUID.nameUUIDFromBytes((state.generation()+"/"+child.generation()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new com.inigmasgames.hytalerpg.ui.model.BarrierView((own?state.revision():0)+(child==null?0:child.revision()),
                    generation,(own?state.remaining():0)+(child==null?0:child.remaining()),
                    (own?state.capacity():0)+(child==null?0:child.capacity()));
        }catch(RuntimeException unavailable){return com.inigmasgames.hytalerpg.ui.model.BarrierView.NONE;}
    }
    private boolean sharedPlayerGrantEligible(CycloneSharedAegisLedger.Grant grant){
        return !pendingCasterDeathSweeps.contains(grant.caster())
                &&grant.trustGeneration()!=null&&trustedAllies!=null
                &&grant.trustGeneration().equals(trustedAllies.generation(grant.caster(),grant.recipient()));
    }
    private boolean sharedNpcGrantEligible(CycloneSharedAegisLedger.Grant grant){
        return !pendingCasterDeathSweeps.contains(grant.caster())&&grant.trustGeneration()==null;
    }
    boolean retainSharedPaymentMarker(UUID generation){return cycloneShared.hasUnspentGrant(generation);}
    void cyclonePaymentMarked(PlayerRef player,UUID generation){
        liveSharedPayments.compute(player.getUuid(),(id,prior)->{
            var generations=new HashSet<UUID>();
            if(prior!=null&&prior.player()==player)generations.addAll(prior.generations());
            generations.add(generation);
            return new LivePayment(player,Set.copyOf(generations));
        });
    }
    private boolean sharedEligible(Store<EntityStore> store,Ref<EntityStore> actor,Ref<EntityStore> candidate){
        if(candidate.equals(actor)||!alive(store,candidate)
                ||store.getComponent(candidate,Invulnerable.getComponentType())!=null)return false;
        var actorId=store.getComponent(actor,UUIDComponent.getComponentType());
        var recipientId=store.getComponent(candidate,UUIDComponent.getComponentType());
        if(actorId==null||recipientId==null||!kernel.statuses().allowsExternalMutation(actorId.getUuid(),recipientId.getUuid()))return false;
        var player=store.getComponent(candidate,PlayerRef.getComponentType());
        if(player!=null){
            var binding=sharedConnections.get(recipientId.getUuid());
            return binding!=null&&binding.player()==player&&trustedAllies!=null
                    &&trustedAllies.generation(actorId.getUuid(),recipientId.getUuid())!=null;
        }
        var binding=npcSharedConnections.get(recipientId.getUuid());
        return binding!=null&&binding.entity().equals(candidate)&&eligibleAlly(store,actor,candidate);
    }
    private List<Ref<EntityStore>> sharedCandidates(Store<EntityStore> store,Ref<EntityStore> actor){
        var query=Query.and(UUIDComponent.getComponentType(),EntityStatMap.getComponentType(),
                TransformComponent.getComponentType(),BoundingBox.getComponentType());
        if(store.getEntityCountFor(query)>4096)throw new IllegalStateException("SHARED_AEGIS_SCAN_BUDGET");
        var found=new ArrayList<SharedAegisRecipientSelector.Candidate<Ref<EntityStore>>>();
        var origin=position(store,actor).add(new Vec3(0,1.35,0));
        boolean overflow=store.forEachChunk(query,(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++){
                var candidate=chunk.getReferenceTo(i);
                if(!sharedEligible(store,actor,candidate))continue;
                var bounds=bounds(store,candidate);
                double distance=ConnectionShape.pointDistanceSquared(origin,bounds);
                if(distance>SharedAegisRecipientSelector.RANGE_METERS*SharedAegisRecipientSelector.RANGE_METERS+1e-9)continue;
                if(found.size()>=64)return true;
                var id=store.getComponent(candidate,UUIDComponent.getComponentType()).getUuid();
                found.add(new SharedAegisRecipientSelector.Candidate<>(candidate,id,distance,
                        HytaleAreaQueries.clear(store,origin,bounds.centre())));
            }
            return false;
        });
        if(overflow)throw new IllegalStateException("SHARED_AEGIS_TARGET_BUDGET");
        return SharedAegisRecipientSelector.ordered(found);
    }
    void cycloneGrantShared(Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef caster,
            SkillExecutionContext context,UUID generation){
        if(!context.compiledPlan().supportModifiers().sharedAegis())return;
        var state=durableCyclone.state(cycloneAuthority(caster).lease());
        if(state.phase()!=CycloneBarrierLedger.Phase.ACTIVE||!generation.equals(state.generation()))return;
        for(var recipient:sharedCandidates(store,actor)){
            UUID recipientId=store.getComponent(recipient,UUIDComponent.getComponentType()).getUuid();
            var player=store.getComponent(recipient,PlayerRef.getComponentType());
            CycloneSharedAegisLedger.Lease lease;
            UUID trustGeneration=null;
            if(player!=null){
                var binding=sharedConnections.get(recipientId);
                if(binding==null||binding.player()!=player)continue;
                trustGeneration=trustedAllies.generation(caster.getUuid(),recipientId);
                if(trustGeneration==null)continue;
                lease=binding.lease();
            }else{
                var binding=npcSharedConnections.get(recipientId);
                if(binding==null||!binding.entity().equals(recipient))continue;
                lease=binding.lease();
            }
            try{if(cycloneShared.grant(state,recipientId,lease,trustGeneration))return;}
            catch(RuntimeException failure){
                com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(failure)
                        .log("RPG_CYCLONE_SHARED_GRANT_FAILED caster=%s recipient=%s",caster.getUuid(),recipientId);
                return;
            }
            return; // A recipient was selected; never silently choose a farther ally.
        }
    }
    SkillExecutionPort.CycloneAuthority cycloneAuthority(PlayerRef player){
        if(!durableCycloneEnabled||cycloneActivations==null)
            throw new IllegalStateException("CYCLONE_AUTHORITY_DISABLED");
        var binding=cycloneConnections.get(player.getUuid());
        if(binding==null||binding.player()!=player)
            throw new IllegalStateException("CYCLONE_SESSION_NOT_READY");
        return new SkillExecutionPort.CycloneAuthority(cycloneActivations,binding.lease());
    }
    void cyclonePreparePublication(PlayerRef player,SkillExecutionContext context,UUID generation){
        if(!context.compiledPlan().supportModifiers().reflectiveWard())return;
        var state=durableCyclone.state(cycloneAuthority(player).lease());
        if(state.phase()!=CycloneBarrierLedger.Phase.PENDING||!generation.equals(state.generation()))
            throw new IllegalStateException("CYCLONE_REFLECTION_PARENT_PENDING_REQUIRED");
        cycloneReflections.install(new CycloneReflectionJournal.Behavior(1,player.getUuid(),generation,
                state.activation(),context.rootCastId(),context.skillInstanceId(),
                context.request().correlationId(),.2,state.capacity()));
    }
    void cyclonePublished(PlayerRef player,SkillExecutionContext context,UUID generation){
        var authority=cycloneAuthority(player);
        var state=durableCyclone.state(authority.lease());
        if(state.phase()!=CycloneBarrierLedger.Phase.ACTIVE||!generation.equals(state.generation()))
            throw new IllegalStateException("CYCLONE_PUBLICATION_MISMATCH");
        var behavior=context.compiledPlan().supportModifiers().reflectiveWard()?cycloneReflections.load(state):null;
        if(context.compiledPlan().supportModifiers().reflectiveWard()&&behavior==null)
            throw new IllegalStateException("CYCLONE_REFLECTION_FROZEN_STATE_MISSING");
        cycloneLive.compute(player.getUuid(),(id,existing)->
                existing!=null&&existing.player()==player&&existing.generation().equals(generation)
                        ?existing:new CycloneLive(player,generation,context,behavior,
                                new java.util.concurrent.atomic.AtomicBoolean(),
                                new java.util.concurrent.atomic.AtomicBoolean()));
    }
    SkillExecutionContext durableCycloneContext(PlayerRef player,UUID generation){
        var live=cycloneLive.get(player.getUuid());
        return live!=null&&live.player()==player&&live.generation().equals(generation)?live.context():null;
    }
    CycloneReflectionJournal.Behavior durableReflectionBehavior(PlayerRef player,UUID generation){
        var live=cycloneLive.get(player.getUuid());
        return live!=null&&live.player()==player&&live.generation().equals(generation)?live.behavior():null;
    }
    boolean claimDurableReflection(PlayerRef player,UUID generation,String receipt){
        var behavior=durableReflectionBehavior(player,generation);
        return behavior!=null&&cycloneReflections.claim(behavior,receipt);
    }
    void reportDurableReflectionFailure(PlayerRef player,UUID generation,String receipt,RuntimeException failure){
        var live=cycloneLive.get(player.getUuid());
        if(live!=null&&live.player()==player&&live.generation().equals(generation)
                &&live.reflectionFailureReported().compareAndSet(false,true))
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(failure)
                    .log("RPG_CYCLONE_DURABLE_REFLECTION_FAILED player=%s receipt=%s",
                            player.getUuid(),receipt);
    }
    void traceDurableCyclone(PlayerRef player,CycloneBarrierLedger.Debit debit){
        var context=durableCycloneContext(player,debit.generation());
        var behavior=durableReflectionBehavior(player,debit.generation());
        if(context==null&&behavior==null)return;
        trace.emit(player.getUuid(),RpgTraceEventType.BARRIER_ABSORBED,
                context!=null?new CombatTrace.Context(context.rootCastId(),context.skillInstanceId(),context.request().correlationId())
                        :new CombatTrace.Context(behavior.rootCastId(),behavior.skillInstanceId(),behavior.correlationId()),
                Map.of("absorbed",debit.absorbed(),"remaining",debit.remaining(),"damageRemaining",debit.damageRemaining(),
                        "receipt",debit.receipt(),"durable",debit.durable(),"duplicate",debit.duplicate()));
    }
    void traceDurableReflection(PlayerRef player,CycloneBarrierLedger.Debit debit,double requested,
            HytaleDamageAdapter.NativeResult result,String channel){
        var context=durableCycloneContext(player,debit.generation());
        var behavior=durableReflectionBehavior(player,debit.generation());
        if(context==null&&behavior==null)return;
        trace.emit(player.getUuid(),RpgTraceEventType.DAMAGE_REFLECTED,
                context!=null?new CombatTrace.Context(context.rootCastId(),context.skillInstanceId(),context.request().correlationId())
                        :new CombatTrace.Context(behavior.rootCastId(),behavior.skillInstanceId(),behavior.correlationId()),
                Map.of("actuallyAbsorbed",debit.absorbed(),"requestedReflection",requested,
                        "originalChannel",channel,"cancelled",result.cancelled(),"noProc",true,"noCrit",true));
    }
    public SkillExecutionResult executeDurableCyclone(Store<EntityStore> store,Ref<EntityStore> actor,SkillExecutionContext context){
        var live=cycloneLive.get(context.request().actorId());
        if(live==null||live.context()!=context||!live.presented().compareAndSet(false,true))
            return SkillExecutionResult.committed("CYCLONE_BARRIER_ACTIVE",1,0);
        try{CycloneArmorPresentation.applyDurable(store,actor);}
        catch(RuntimeException presentationFailure){
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(presentationFailure)
                    .log("RPG_CYCLONE_DURABLE_VISUAL_FAILED actor=%s",context.request().actorId());
        }
        trace.emit(context.request().actorId(),RpgTraceEventType.BARRIER_CREATED,
                new CombatTrace.Context(context.rootCastId(),context.skillInstanceId(),context.request().correlationId()),
                Map.of("capacity",durableCyclone.state(cycloneAuthority(store.getComponent(actor,PlayerRef.getComponentType())).lease()).capacity(),
                        "authority","CYCLONE_DURABLE_LEDGER","durationSeconds",0));
        return SkillExecutionResult.committed("CYCLONE_BARRIER_ACTIVE",1,0);
    }
    CycloneDamageStage.Resolution durableCycloneDebit(PlayerRef player,String receipt,float damage){
        if(!durableCycloneEnabled||durableCyclone==null)return null;
        var binding=cycloneConnections.get(player.getUuid());
        if(binding==null||binding.player()!=player)return null;
        var lease=binding.lease();
        try{
            var state=durableCyclone.state(lease);
            if(state.phase()!=CycloneBarrierLedger.Phase.ACTIVE)return null;
            return CycloneDamageStage.absorb(durableCyclone,lease,state.generation(),receipt,damage);
        }
        catch(RuntimeException failure){
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(failure)
                    .log("RPG_CYCLONE_DURABLE_DEBIT_REJECTED player=%s",player.getUuid());
            return null; // Keep all unconfirmed damage in the native event.
        }
    }
    CycloneSharedAegisLedger.Debit durableSharedDebit(PlayerRef player,String receipt,float damage){
        if(!durableCycloneEnabled||damage<=0)return null;
        var binding=sharedConnections.get(player.getUuid());
        if(binding==null||binding.player()!=player)return null;
        try{return cycloneShared.debit(binding.lease(),receipt,damage,this::sharedPlayerGrantEligible);}
        catch(RuntimeException failure){
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(failure)
                    .log("RPG_CYCLONE_SHARED_DEBIT_REJECTED recipient=%s",player.getUuid());
            return null;
        }
    }
    CycloneSharedAegisLedger.Debit durableNpcSharedDebit(Store<EntityStore> store,Ref<EntityStore> recipient,UUID id,
            String receipt,float damage){
        if(!durableCycloneEnabled||damage<=0)return null;
        var binding=npcSharedConnections.get(id);
        if(binding==null||!binding.entity().equals(recipient))return null;
        try{return cycloneShared.debit(binding.lease(),receipt,damage,grant->{
            if(!sharedNpcGrantEligible(grant))return false;
            var caster=store.getExternalData().getRefFromUUID(grant.caster());
            return caster==null||!caster.isValid()||eligibleAlly(store,caster,recipient);
        });}
        catch(RuntimeException failure){
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(failure)
                    .log("RPG_CYCLONE_SHARED_NPC_DEBIT_REJECTED recipient=%s",id);
            return null;
        }
    }
    private void bindNpcShared(Ref<EntityStore> ref,UUID id){
        if(!durableCycloneEnabled)return;
        var existing=npcSharedConnections.get(id);
        if(existing!=null&&existing.entity().equals(ref))return;
        if(existing!=null&&npcSharedConnections.remove(id,existing))cycloneShared.detach(existing.lease());
        var future=npcSharedPreloads.computeIfAbsent(id,this::preloadNpcSharedAegis);
        if(!future.isDone()||future.isCompletedExceptionally())return;
        try{npcSharedConnections.put(id,new NpcSharedConnection(ref,cycloneShared.attachPrepared(id)));}
        catch(RuntimeException unavailable){/* No unconfirmed recipient protection is published. */}
    }
    private void unbindNpcShared(Ref<EntityStore> ref,UUID id,boolean death){
        var binding=npcSharedConnections.get(id);
        if(binding==null||!binding.entity().equals(ref)||!npcSharedConnections.remove(id,binding))return;
        try{if(death)cycloneShared.clearForDeath(binding.lease());}
        catch(RuntimeException failure){
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(failure)
                    .log("RPG_CYCLONE_SHARED_NPC_DEATH_CLEAR_FAILED recipient=%s",id);
        }
        finally{cycloneShared.detach(binding.lease());}
    }
    /** NPC recipients use the same child journal; native caster proof is loaded
     * asynchronously and the world thread only attaches a completed lease. */
    public static final class NpcSharedBindings extends EntityTickingSystem<EntityStore>{
        private final HytaleSupportSystem owner;
        @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value="EI_EXPOSE_REP2",
                justification="The native ECS system intentionally shares the registered support authority.")
        public NpcSharedBindings(HytaleSupportSystem owner){this.owner=owner;}
        @Override public Query<EntityStore> getQuery(){return Query.and(
                com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType(),
                UUIDComponent.getComponentType(),EntityStatMap.getComponentType());}
        @Override public void tick(float delta,int index,ArchetypeChunk<EntityStore> chunk,
                Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
            var ref=chunk.getReferenceTo(index);
            if(alive(store,ref))owner.bindNpcShared(ref,chunk.getComponent(index,UUIDComponent.getComponentType()).getUuid());
        }
    }
    public static final class NpcSharedRemoval extends com.hypixel.hytale.component.system.RefSystem<EntityStore>{
        private final HytaleSupportSystem owner;
        @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value="EI_EXPOSE_REP2",
                justification="The native ECS system intentionally shares the registered support authority.")
        public NpcSharedRemoval(HytaleSupportSystem owner){this.owner=owner;}
        @Override public Query<EntityStore> getQuery(){return Query.and(
                com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType(),UUIDComponent.getComponentType());}
        @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,
                Store<EntityStore> store,CommandBuffer<EntityStore> buffer){}
        @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,
                Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
            var identity=store.getComponent(ref,UUIDComponent.getComponentType());
            if(identity!=null)owner.unbindNpcShared(ref,identity.getUuid(),false);
        }
    }
    public static final class NpcSharedDeath extends com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems.OnDeathSystem{
        private final HytaleSupportSystem owner;
        @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value="EI_EXPOSE_REP2",
                justification="The native ECS system intentionally shares the registered support authority.")
        public NpcSharedDeath(HytaleSupportSystem owner){this.owner=owner;}
        @Override public Query<EntityStore> getQuery(){return Query.and(
                com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType(),UUIDComponent.getComponentType());}
        @Override public void onComponentAdded(Ref<EntityStore> ref,DeathComponent death,
                Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
            var identity=store.getComponent(ref,UUIDComponent.getComponentType());
            if(identity!=null)owner.unbindNpcShared(ref,identity.getUuid(),true);
        }
    }
    boolean durableOwnReceiptSeen(PlayerRef player,String receipt){
        var binding=cycloneConnections.get(player.getUuid());
        if(binding==null||binding.player()!=player)return false;
        try{return durableCyclone.receiptSeen(binding.lease(),receipt);}
        catch(RuntimeException unavailable){return true;}
    }
    public void beginReady(UUID actor){initialized.remove(actor);initializing.remove(actor);}
    public boolean sessionReady(UUID actor){return initialized.contains(actor)&&loadouts.ready(actor);}
    public boolean connectionReady(Store<EntityStore> store,Ref<EntityStore> actor){
        var id=store.getComponent(actor,PlayerRef.getComponentType()).getUuid();
        if(!loadouts.ready(id))return com.inigmasgames.hytalerpg.diagnostics.hro.HroRuntime.waiting(id,"LOADOUT_NOT_READY");
        if(initialized.contains(id))return true;
        if(durableCycloneEnabled)try{CycloneArmorPresentation.remove(store,actor);}
        catch(RuntimeException ignored){/* A stale cosmetic effect cannot authorize protection. */}
        if(initializing.add(id))runtime.detach(id,"PLAYER_READY_RESET",port(store,actor));
        if(!runtime.settled(id)||!kernel.cooldowns().persistenceReady(id))return com.inigmasgames.hytalerpg.diagnostics.hro.HroRuntime.waiting(id,"SUPPORT_OR_COOLDOWN_NOT_SETTLED");
        kernel.cooldowns().restore(id);
        kernel.reservations().removeAll(id,new EntityStatResourcePort(store.getComponent(actor,EntityStatMap.getComponentType())));
        if(durableCycloneEnabled){
            var player=store.getComponent(actor,PlayerRef.getComponentType());
            var existing=cycloneConnections.get(id);
            if(existing==null||existing.player()!=player){
                try{cycloneConnections.put(id,new CycloneConnection(player,durableCyclone.attachPrepared(id)));}
                catch(IllegalStateException pending){return com.inigmasgames.hytalerpg.diagnostics.hro.HroRuntime.waiting(id,"CYCLONE_PREPARED_LEASE_UNAVAILABLE");}
            }
            try{
                var binding=cycloneConnections.get(id);
                if(binding!=null&&binding.player()==player&&durableCyclone.paymentRecoveryRequired(binding.lease())){
                    var stats=store.getComponent(actor,EntityStatMap.getComponentType());
                    var pending=durableCyclone.state(binding.lease());
                    var recovered=CycloneNativePaymentMarker.recover(durableCyclone,binding.lease(),stats,cycloneReflections);
                    if(recovered.phase()==CycloneBarrierLedger.Phase.ACTIVE){
                        var behavior=CycloneNativePaymentMarker.kind(stats,pending.generation())
                                ==CycloneNativePaymentMarker.Kind.REFLECTIVE?cycloneReflections.load(recovered):null;
                        if(behavior!=null)cycloneLive.put(id,new CycloneLive(player,recovered.generation(),null,
                                behavior,new java.util.concurrent.atomic.AtomicBoolean(true),
                                new java.util.concurrent.atomic.AtomicBoolean()));
                        try{CycloneArmorPresentation.applyDurable(store,actor);}
                        catch(RuntimeException presentationFailure){
                            com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning()
                                    .withCause(presentationFailure)
                                    .log("RPG_CYCLONE_RECOVERED_VISUAL_FAILED player=%s",id);
                        }
                    }
                }
            }catch(RuntimeException recoveryFailure){
                com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(recoveryFailure)
                        .log("RPG_CYCLONE_PAYMENT_RECOVERY_FAILED player=%s",id);
                return com.inigmasgames.hytalerpg.diagnostics.hro.HroRuntime.waiting(id,"CYCLONE_RECOVERY_FAILED");
            }
            var existingShared=sharedConnections.get(id);
            if(existingShared==null||existingShared.player()!=player)try{
                sharedConnections.put(id,new SharedConnection(player,cycloneShared.attachPrepared(id)));
            }catch(RuntimeException unavailable){return com.inigmasgames.hytalerpg.diagnostics.hro.HroRuntime.waiting(id,"SHARED_PREPARED_LEASE_UNAVAILABLE");}
        }
        initialized.add(id);return true;
    }
    @FunctionalInterface public interface AuraPayload {void apply(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> actor,SkillExecutionContext context,List<UUID> targets,int tick,boolean chill);}
    @FunctionalInterface public interface ItemAuraContext {
        SkillExecutionContext create(Store<EntityStore> store,Ref<EntityStore> actor,UUID item,String skill,
                                     com.inigmasgames.hytalerpg.gear.GearEffectSnapshot source,CommandBuffer<EntityStore> buffer);
    }
    private final AuraPayload auraPayload;
    private final ItemAuraContext itemAuraContext;
    private NativeSentinelItemAuras sentinelItemAuras;
    public void configureSentinelItemAuras(NativeSentinelItemAuras auras){
        sentinelItemAuras=java.util.Objects.requireNonNull(auras);
    }
    public double manaRegenerationIncreased(UUID recipient,double now){
        return sentinelItemAuras==null?runtime.manaRegenerationIncreased(recipient,now):
                sentinelItemAuras.manaRegenerationIncreased(recipient,now);
    }
    public double cooldownRecoveryIncreased(UUID recipient,double now){
        return sentinelItemAuras==null?runtime.cooldownRecoveryIncreased(recipient,now):
                sentinelItemAuras.cooldownRecoveryIncreased(recipient,now);
    }
    public java.util.Optional<com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects.Effect> thorns(UUID world,UUID recipient,double now){
        return sentinelItemAuras==null?runtime.thorns(world,recipient,now):sentinelItemAuras.thorns(world,recipient,now);
    }
    public HytaleSupportSystem(RpgLoadoutService loadouts,RpgCombatKernel kernel,OwnedFieldBudget fields,CombatTrace trace,LinkTreeVfxService vfx,HytaleBossBarTracker bosses,AuraPayload auraPayload){
        this(loadouts,kernel,fields,trace,vfx,bosses,auraPayload,null);
    }
    public HytaleSupportSystem(RpgLoadoutService loadouts,RpgCombatKernel kernel,OwnedFieldBudget fields,CombatTrace trace,LinkTreeVfxService vfx,HytaleBossBarTracker bosses,AuraPayload auraPayload,ItemAuraContext itemAuraContext){
        this.auraPayload=auraPayload;
        this.itemAuraContext=itemAuraContext;
        this.bosses=bosses;
        this.loadouts=loadouts;this.kernel=kernel;this.trace=trace;this.vfx=vfx;
        kernel.cooldowns().bindPersistence(new com.inigmasgames.hytalerpg.combat.cooldown.RpgCooldownService.AsyncPersistence(){
            public Map<String,com.inigmasgames.hytalerpg.combat.cooldown.SavedCooldown> load(UUID actor){return loadouts.getPresentationView(actor).state().cooldowns;}
            public void save(UUID actor,Map<String,com.inigmasgames.hytalerpg.combat.cooldown.SavedCooldown> values){throw new IllegalStateException("NATIVE_SYNCHRONOUS_COOLDOWN_SAVE_FORBIDDEN");}
            public java.util.concurrent.CompletionStage<Void> submit(UUID actor,Map<String,com.inigmasgames.hytalerpg.combat.cooldown.SavedCooldown> values){return loadouts.submitCooldowns(actor,values);}
        });
        runtime=new SupportRuntime(kernel.reservations(),fields,new SupportProgressStore.Async(){
            public SupportProgress read(UUID actor){return loadouts.getPresentationView(actor).state().support;}
            public SupportProgress save(UUID actor,SupportProgress next){throw new IllegalStateException("NATIVE_SYNCHRONOUS_SUPPORT_SAVE_FORBIDDEN");}
            public java.util.concurrent.CompletionStage<SupportProgress> submit(UUID actor,SupportProgress next){return loadouts.submitSupport(actor,next);}
            public java.util.concurrent.CompletionStage<SupportProgress> settle(UUID actor,java.util.concurrent.CompletionStage<SupportProgress> prior,SupportProgress actual){return loadouts.settleSupport(actor,prior,actual);}
        });
    }
    public SupportRuntime runtime(){return runtime;}
    /** Cursed/Armor Breaker reuse the shared negative-stat package and the one status/Fortune roll. */
    public String applyMonsterStat(Store<EntityStore> store,Ref<EntityStore> actor,Ref<EntityStore> target,
            com.inigmasgames.hytalerpg.combat.damage.MonsterAffixSource source,
            FiniteSupportEffects.StatEffect effect,com.inigmasgames.hytalerpg.combat.status.StatusApplication application,
            com.inigmasgames.hytalerpg.combat.damage.CriticalRoller random,
            java.util.function.BooleanSupplier bindingCurrent,java.util.function.BooleanSupplier claimOpportunity){
        if(!store.isInThread())throw new IllegalStateException("MONSTER_STAT_WRONG_WORLD_THREAD");
        var statSource=effect.key().source();
        String status=switch(source.affixId()){
            case "ME-014"->"WEAKEN";case "ME-025"->"ARMOR_BREAK";
            default->throw new IllegalArgumentException("MONSTER_NEGATIVE_STAT_AFFIX");
        };
        if(!statSource.world().equals(source.worldId())||!statSource.actor().equals(source.logicalActorId())
                ||statSource.generation()!=source.encounterGeneration()||!statSource.definitionId().equals(source.affixId())
                ||!application.source().equals(source.nativeActorId())||!application.status().equals(status)||!application.hostile()||application.scripted())
            throw new IllegalArgumentException("MONSTER_STAT_SOURCE_MISMATCH");
        java.util.function.BooleanSupplier mayMutate=()->{
            if(!bindingCurrent.getAsBoolean()||actor==null||target==null||actor.getStore()!=store||target.getStore()!=store
                    ||!alive(store,actor)||!alive(store,target)||!source.worldId().equals(SupportNativeEffects.world(store)))return false;
            var actorId=store.getComponent(actor,UUIDComponent.getComponentType());var targetId=store.getComponent(target,UUIDComponent.getComponentType());
            return actorId!=null&&targetId!=null&&actorId.getUuid().equals(source.nativeActorId())&&targetId.getUuid().equals(application.target())
                    &&store.getComponent(actor,com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType())!=null
                    &&HytaleAreaQueries.hostile(store,actor,target)&&!SupportNativeEffects.control(store,target,bosses).protectedEntity()
                    &&kernel.statuses().allowsExternalMutation(application.source(),application.target());
        };
        if(!mayMutate.getAsBoolean())return "MONSTER_STAT_BINDING_REJECTED";
        double now=System.nanoTime()/1e9;runtime.finite().requireStatAdmission(effect,now);
        var admitted=kernel.statuses().admit(application,SupportNativeEffects.control(store,target,bosses),true,random,claimOpportunity);
        if(admitted!=com.inigmasgames.hytalerpg.combat.status.StatusService.Admission.ACCEPTED)return admitted.name();
        if(!runtime.finite().applyStat(effect,now,mayMutate))return "PROTECTED";
        trace.emit(source.nativeActorId(),RpgTraceEventType.STATUS_APPLIED,new CombatTrace.Context(source.rootId(),"",source.strikeId()),
                Map.of("sourceKind","MONSTER_AFFIX","affixId",source.affixId(),"logicalActorId",source.logicalActorId(),"generation",source.encounterGeneration(),
                        "targetId",application.target(),"status",status,"fraction",effect.fraction(),"durationSeconds",effect.ends()-effect.starts()));
        return "APPLIED";
    }
    public double nativeWeaponOffensiveFactor(com.hypixel.hytale.server.core.entity.InteractionContext nativeContext){
        var player=nativeContext.getCommandBuffer().getComponent(nativeContext.getOwningEntity(),PlayerRef.getComponentType());
        return runtime.finite().nativeDirectOutgoingFactor(player.getWorldUuid(),player.getUuid(),0,System.nanoTime()/1e9);
    }
    /** Echo runs after the native leaf and its queued damage, never inside its calculator. */
    public void routeManagedWeaponFire(com.hypixel.hytale.server.core.entity.InteractionContext nativeContext,
            com.inigmasgames.hytalerpg.combat.damage.WeaponFireDecision decision){
        var buffer=nativeContext.getCommandBuffer();var store=buffer.getStore();var actor=nativeContext.getOwningEntity();
        buffer.run(safeStore->{if(actor.isValid())routeWeaponFire(safeStore,null,actor,decision);});
    }
    public void routeWeaponFire(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> actor,
            com.inigmasgames.hytalerpg.combat.damage.WeaponFireDecision decision){
        try{routeWeaponFireEcho(store,buffer,actor,decision);}
        catch(RuntimeException failure){
            var id=decision.execution().identity();
            trace.emit(id.actorId(),RpgTraceEventType.WEAPON_FIRE_SOURCE_ROUTED,new CombatTrace.Context(id.rootId(),id.executionId(),id.authoredTickId()),
                    Map.of("route","ECHO_FAILED_DIRECT_PRESERVED","reason",safeMantleReason(failure),"error",failure.getClass().getSimpleName()));
        }
    }
    private void routeWeaponFireEcho(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> actor,
            com.inigmasgames.hytalerpg.combat.damage.WeaponFireDecision decision){
        if(decision.resolved())return;
        var id=decision.execution().identity();var context=runtime.activeContext(id.actorId(),"mantle_of_flame");
        var port=port(store,actor,buffer);
        boolean enabled=context!=null&&port.valid(context).equals("PASS");
        double radius=enabled?SupportRuntime.radius(context):0;
        var result=decision.decideProportional(enabled,()->{
            var recipients=new ArrayList<com.inigmasgames.hytalerpg.combat.damage.WeaponFireDecision.Recipient>();
            for(var target:hostileRefs(store,actor,radius)){
                var targetId=store.getComponent(target,UUIDComponent.getComponentType()).getUuid();
                double k=runtime.finite().victimModifiers(id.worldId(),id.actorId(),targetId,context.snapshot().modifiers(),System.nanoTime()/1e9).factor();
                double amount=decision.execution().sourceFire()*.25*k;
                if(amount>0)recipients.add(new com.inigmasgames.hytalerpg.combat.damage.WeaponFireDecision.Recipient(targetId,amount));
            }
            return List.copyOf(recipients);
        },enabled?context.compiledPlan().kernelModifiers().resourceCostMultiplier():1,kernel.resources(),port.resources(),
                ()->runtime.terminateAura(id.actorId(),"mantle_of_flame","INSUFFICIENT_TRIGGER_MANA",port));
        String routing=switch(result.status()){
            case PASS_THROUGH->"DIRECT";
            case INSUFFICIENT_MANA->"DIRECT_AFTER_MANA_FAILURE";
            case ECHO_COMMITTED->result.recipients().isEmpty()?"ECHO_EMPTY_DIRECT_PRESERVED":"ECHO_COMMITTED";
        };
        trace.emit(id.actorId(),RpgTraceEventType.WEAPON_FIRE_SOURCE_ROUTED,new CombatTrace.Context(id.rootId(),id.executionId(),id.authoredTickId()),
                Map.of("contract","NORMALIZED_FIRE_V1","item",decision.execution().itemId(),"route",routing,"sourceFire",result.sourceFire(),
                        "recipients",result.recipients().size(),"manaCost",result.manaCost(),"sourceEvaluations",1,"directDamagePreserved",true));
        var pulse=decision.claimPulse();
        if(pulse.isEmpty())return;
        // Immutable recipients/context only. The callback-scoped native CommandBuffer is NOT retained.
        java.util.function.Consumer<Store<EntityStore>> dispatch=safeStore->{
            if(!actor.isValid()||!alive(safeStore,actor)||!port(safeStore,actor).valid(context).equals("PASS")
                    ||runtime.activeContext(id.actorId(),"mantle_of_flame")!=context)return;
            int affected=0;double actual=0;
            for(var recipient:pulse){
                var target=safeStore.getExternalData().getRefFromUUID(recipient.targetId());
                if(target==null||!target.isValid()||!alive(safeStore,target)||!HytaleAreaQueries.hostile(safeStore,target,actor)
                        ||SupportNativeEffects.control(safeStore,target,bosses).protectedEntity()||!auraInRange(safeStore,actor,target,radius))continue;
                var metadata=new HytaleDamageMetadata(id.actorId(),context.rootCastId(),context.skillInstanceId(),context.request().correlationId(),
                        recipient.generatedFire(),0,"mantle:"+id.rootId()+":"+id.authoredTickId(),false,HytaleDamageMetadata.Origin.TRIGGERED);
                var hit=new HytaleDamageAdapter().applyResolved(target,safeStore,actor,DamageCause.getAssetMap().getAsset("Fire"),metadata,recipient.generatedFire(),null,context);
                double loss=Math.max(0,hit.healthBefore()-hit.healthAfter());
                if(!hit.cancelled()&&loss>0){affected++;actual+=loss;
                    try{MantlePresentation.impact(safeStore,target);MantlePresentation.apply(safeStore,target,"RPG_Mantle_Flash",.12f);
                        trace.emit(id.actorId(),RpgTraceEventType.MANTLE_IMPACT_APPLIED,
                                new CombatTrace.Context(context.rootCastId(),context.skillInstanceId(),context.request().correlationId()),
                                Map.of("weaponRoot",id.rootId(),"execution",id.authoredTickId(),"target",recipient.targetId(),
                                        "actualHealthLost",loss,"system","Impact_Fire","owner","RPG_Mantle_Impact","instances",1));}
                    catch(RuntimeException failure){mantleVisualFailure(context,failure);}
                }
            }
            if(affected>0)try{MantlePresentation.apply(safeStore,actor,"RPG_Mantle_Pulse",.4f);}catch(RuntimeException failure){mantleVisualFailure(context,failure);}
            trace.emit(id.actorId(),RpgTraceEventType.MANTLE_PULSE_RESOLVED,new CombatTrace.Context(context.rootCastId(),context.skillInstanceId(),context.request().correlationId()),
                    Map.of("weaponRoot",id.rootId(),"execution",id.authoredTickId(),"committedRecipients",pulse.size(),"affected",affected,"actualDamage",actual,"lateInvalidationRefund",false));
        };
        if(buffer!=null)buffer.run(dispatch);else store.getExternalData().getWorld().execute(()->dispatch.accept(store));
    }
    private void mantleVisualFailure(SkillExecutionContext context,RuntimeException failure){
        trace.emit(context.request().actorId(),RpgTraceEventType.MANTLE_PRESENTATION_FAILURE,
                new CombatTrace.Context(context.rootCastId(),context.skillInstanceId(),context.request().correlationId()),
                Map.of("boundary",failure.getClass().getSimpleName(),"reason",safeMantleReason(failure)));
    }
    private void mantleOwner(SkillExecutionContext context,boolean active){
        trace.emit(context.request().actorId(),RpgTraceEventType.MANTLE_PRESENTATION_OWNER,
                new CombatTrace.Context(context.rootCastId(),context.skillInstanceId(),context.request().correlationId()),
                Map.of("mode","NATIVE_CAMERA_VARIANTS","effectOwner","RPG_Mantle_Aura","activeOwners",active?1:0,
                        "worldVariants",active?1:0,"firstPersonVariants",active?1:0,"visualScale",.5,
                        "cameraTransitionOwner","CLIENT_NATIVE_NOT_SERVER_OBSERVED","cleanup",!active));
    }
    private static String safeMantleReason(RuntimeException failure){
        String reason=String.valueOf(failure.getMessage()).replaceAll("[\\p{Cntrl}]"," ");
        return reason.substring(0,Math.min(160,reason.length()));
    }
    public static void requireMantleAssets(){MantlePresentation.requireAssets();}
    private HytaleEncounterRewards encounterRewards;
    public void configureEncounterRewards(HytaleEncounterRewards rewards){encounterRewards=java.util.Objects.requireNonNull(rewards);}
    void invalidateHealthCredit(UUID world,UUID actor){if(encounterRewards!=null)encounterRewards.invalidateHealthCredit(world,actor);}
    void forgetProgressionPlayer(UUID actor){if(encounterRewards!=null)encounterRewards.forgetPlayer(actor);}
    void observeControl(Store<EntityStore> store,Ref<EntityStore> target,SkillExecutionContext context,Runnable operation){
        var before=HytaleAreaStatuses.observed(store,target);operation.run();
        if(HytaleAreaStatuses.observed(store,target).improvedFrom(before))controlResolved(store,target,context,false,"NATIVE_EFFECT_STATE_CHANGED");
    }
    void controlResolved(Store<EntityStore> store,Ref<EntityStore> target,SkillExecutionContext context,boolean taunt,String evidence){
        if(encounterRewards!=null)encounterRewards.controlResolved(store,target,context,taunt,evidence);
    }
    public void absorptionResolved(Store<EntityStore> store,Ref<EntityStore> recipient,Damage damage,FiniteSupportEffects.Absorption absorption){
        if(encounterRewards!=null&&absorption!=null&&absorption.effect().context()!=null)
            encounterRewards.absorptionResolved(store,recipient,damage,absorption);
    }
    /** Shared post-write hook for direct heals and drain healing; no second Health writer or mastery grant. */
    public void healingResolved(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> target,
                                SkillExecutionContext context,double requested,double before,double after,double maximum){
        if(encounterRewards!=null)encounterRewards.healingResolved(store,target,context,before,after);
        if(!context.compiledPlan().supportModifiers().overflow())return;
        var id=store.getComponent(target,UUIDComponent.getComponentType());if(id==null)return;
        try{
            var created=runtime.finite().healingResolved(context,id.getUuid(),requested,before,after,maximum,System.nanoTime()/1e9);
            if(created.isEmpty())return;
            if(buffer!=null)buffer.ensureComponent(target,SupportEffectProjection.getComponentType());
            else store.ensureComponent(target,SupportEffectProjection.getComponentType());
            traceFinite(created.get(),RpgTraceEventType.BARRIER_CREATED,Map.of("actualOverheal",Math.max(0,requested-(maximum-before)),
                    "actualHealing",Math.max(0,after-before),"barrierRemaining",created.get().shieldRemaining(),"cap",maximum*.2,
                    "durationSeconds",6,"triggerRootCastId",context.rootCastId(),"masteryCredit",false));
        }catch(RuntimeException failure){
            trace.emit(context.request().actorId(),RpgTraceEventType.NATIVE_SUPPORT_REJECTED,
                    new CombatTrace.Context(context.rootCastId(),context.skillInstanceId(),context.request().correlationId()),
                    Map.of("boundary","OVERFLOW_POST_HEAL_"+failure.getClass().getSimpleName(),"detail",String.valueOf(failure.getMessage()),"healRolledBack",false));
        }
    }
    /** Generic native bridge for the status owner's post-removal cleanse event. */
    public void cleanseResolved(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> target,
                                com.inigmasgames.hytalerpg.combat.status.StatusService.CleanseReceipt receipt){
        Objects.requireNonNull(receipt);
        if(target==null||!target.isValid())throw new IllegalArgumentException("CLEANSE_TARGET_UNAVAILABLE");
        var id=store.getComponent(target,UUIDComponent.getComponentType());
        var stats=store.getComponent(target,EntityStatMap.getComponentType());
        var health=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
        if(id==null||health==null)throw new IllegalStateException("CLEANSE_RECIPIENT_HEALTH_UNAVAILABLE");
        if(!receipt.source().recipient().equals(id.getUuid())||
                !receipt.source().world().equals(SupportNativeEffects.world(store)))
            throw new IllegalArgumentException("CLEANSE_RECEIPT_TARGET_MISMATCH");
        var created=SupportCleanse.consume(runtime.finite(),receipt,NativeNormalHealthMaximum.value(health));
        if(created.isEmpty())return;
        if(buffer!=null)buffer.ensureComponent(target,SupportEffectProjection.getComponentType());
        else store.ensureComponent(target,SupportEffectProjection.getComponentType());
        traceFinite(created.get(),RpgTraceEventType.BARRIER_CREATED,Map.of("trigger","SUCCESSFUL_CLEANSE",
                "removedStatuses",receipt.removed().size(),"capacity",created.get().magnitude(),"durationSeconds",3,"pairLockSeconds",10));
    }
    /** Admits queued self recovery directly to native Health, without skill-heal side effects. */
    public double creditSelfRecovery(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,
                                     Ref<EntityStore> actor,double requested,double normalMaximum){
        if(store==null||actor==null||!actor.isValid()||!alive(store,actor)
                ||!Double.isFinite(requested)||requested<=0||!Double.isFinite(normalMaximum)||normalMaximum<=0)return 0;
        var stats=buffer==null?store.getComponent(actor,EntityStatMap.getComponentType())
                :buffer.getComponent(actor,EntityStatMap.getComponentType());
        var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
        if(hp==null||!Double.isFinite(hp.getMax()))return 0;
        double before=hp.get();
        double ceiling=Math.min(hp.getMax(),normalMaximum);
        if(before>=ceiling)return 0;
        stats.setStatValue(DefaultEntityStatTypes.getHealth(),(float)Math.min(ceiling,before+requested));
        return Math.max(0,Math.min(requested,stats.get(DefaultEntityStatTypes.getHealth()).get()-before));
    }
    /** Recipient gear changes the request sent to the existing Health writer exactly once. */
    static double receivedHealing(double requested,com.inigmasgames.hytalerpg.gear.GearEffectSnapshot recipient){
        return new com.inigmasgames.hytalerpg.combat.healing.HealingCalculationService().direct(
                requested,1,1,1,recipient.percent(com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.Operator.HEALING_RECEIVED));
    }
    RpgCombatKernel kernel(){return kernel;}
    /** ME membership examines only the sealed roster and reuses native ally/range/LOS checks and finite stat leases. */
    public Set<UUID> projectEnemyAura(Store<EntityStore> store,com.inigmasgames.hytalerpg.enemies.EnemyDescriptor descriptor,
            com.inigmasgames.hytalerpg.enemies.EnemyPackRecord pack,Map<UUID,Ref<EntityStore>> bindings,double now,
            java.util.function.BooleanSupplier bindingCurrent){
        if(!store.isInThread())throw new IllegalStateException("ENEMY_AURA_WRONG_WORLD_THREAD");
        if(pack.birthRoster().size()>8||!pack.worldId().equals(SupportNativeEffects.world(store))
                ||!descriptor.worldId().equals(pack.worldId())||!Objects.equals(descriptor.packId(),pack.packId())
                ||descriptor.encounterGeneration()!=pack.generation()||!Objects.equals(descriptor.logicalActorId(),pack.leaderId()))
            throw new IllegalArgumentException("ENEMY_AURA_PACK_BINDING");
        var affix=descriptor.own(com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.Operator.AURA_ENCHANTED).orElseThrow();
        var source=new FiniteSupportEffects.StatSource(descriptor.worldId(),descriptor.logicalActorId(),descriptor.encounterGeneration(),
                FiniteSupportEffects.SourceKind.MONSTER_AFFIX,"ME-023");
        var leader=bindings.get(descriptor.logicalActorId());
        java.util.function.BooleanSupplier providing=()->bindingCurrent.getAsBoolean()&&leader!=null&&leader.isValid()&&leader.getStore()==store
                &&alive(store,leader)&&!pack.blocksExternalMutation(descriptor.logicalActorId());
        if(!providing.getAsBoolean()){runtime.finite().replaceAuraStats(source,List.of(),now,()->false);return Set.of();}
        var sourceId=store.getComponent(leader,UUIDComponent.getComponentType());
        if(sourceId==null||!sourceId.getUuid().equals(descriptor.entityId()))throw new IllegalStateException("ENEMY_AURA_NATIVE_IDENTITY_MISMATCH");
        var leases=new ArrayList<FiniteSupportEffects.StatEffect>();var members=new TreeSet<UUID>();
        for(var member:pack.birthRoster()){
            var ref=bindings.get(member.logicalActorId());
            if(ref==null||!ref.isValid()||ref.getStore()!=store||pack.blocksExternalMutation(member.logicalActorId())
                    ||store.getComponent(ref,com.hypixel.hytale.server.core.modules.entity.component.BoundingBox.getComponentType())==null
                    ||!eligibleAlly(store,leader,ref)||!inRange(store,leader,ref,affix.value("radiusMeters")))continue;
            members.add(member.logicalActorId());
            var values=new EnumMap<FiniteSupportEffects.Stat,Double>(FiniteSupportEffects.Stat.class);
            switch(affix.selector()){
                case MIGHT->values.put(FiniteSupportEffects.Stat.AURA_PHYSICAL_INCREASE,affix.value("mightPhysicalIncrease"));
                case WARD->values.put(FiniteSupportEffects.Stat.AURA_RESISTANCE_ADD,affix.value("wardElementalResistanceAdd"));
                case HASTE->{values.put(FiniteSupportEffects.Stat.AURA_MOVEMENT_INCREASE,affix.value("hasteMovementIncrease"));
                    values.put(FiniteSupportEffects.Stat.AURA_RECOVERY_INCREASE,affix.value("hasteRecoveryRateIncrease"));}
            }
            values.forEach((stat,value)->leases.add(new FiniteSupportEffects.StatEffect(new FiniteSupportEffects.StatKey(source,member.logicalActorId(),pack.generation(),stat),
                    value,now,now+affix.value("providerExpiryMs")/1000.)));
        }
        return runtime.finite().replaceAuraStats(source,leases,now,providing)?Collections.unmodifiableSet(members):Set.of();
    }
    HytaleBossBarTracker bosses(){return bosses;}
    void traceFinite(FiniteSupportEffects.Effect effect,RpgTraceEventType event,Map<String,?> details){
        var values=new HashMap<String,Object>(details);values.put("skillId",effect.key().skill());values.put("target",effect.key().target().toString());
        if(effect.kind()==SupportProfile.Kind.CLEANSE_BARRIER){
            values.put("admittedGearRevision",effect.admittedGear().revision());
            values.put("affixSourceItems",effect.admittedGear().sources(com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.Operator.CLEANSE_RESPITE)
                    .stream().map(source->source.itemId().toString()).toList());
        }
        trace.emit(effect.key().owner(),event,new CombatTrace.Context(effect.rootCastId(),effect.skillInstanceId(),effect.correlationId()),values);
    }
    void traceIntrinsic(FiniteSupportEffects.IntrinsicAbsorption absorption,com.hypixel.hytale.server.core.modules.entity.damage.Damage damage){
        var shield=absorption.after();var key=shield.key();String identity=key.target()+"/"+key.generation()+"/"+key.definitionId();
        trace.emit(key.target(),RpgTraceEventType.BARRIER_ABSORBED,new CombatTrace.Context(identity,identity,identity),
                Map.of("effectSourceKind","MONSTER_AFFIX","affixId",key.definitionId(),"world",key.world(),"target",key.target(),
                        "generation",key.generation(),"absorbed",absorption.amount(),"shieldRemaining",shield.remaining(),
                        "shieldConsumed",shield.consumed(),"nativeAmountAfterShield",damage.getAmount()));
    }
    @Override public Query<EntityStore> getQuery(){return Query.and(PlayerRef.getComponentType(),EntityStatMap.getComponentType(),TransformComponent.getComponentType());}
    @Override public Set<Dependency<EntityStore>> getDependencies(){
        return Set.of(new SystemDependency<>(Order.AFTER,EntityStatsSystems.Recalculate.class),
                new SystemDependency<>(Order.BEFORE,EntityStatsModule.PlayerRegenerateStatsSystem.class),
                new SystemDependency<>(Order.BEFORE,HytaleSkillExecutionSystem.class));
    }
    @Override public void tick(float delta,int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.SUPPORT)){
        var ref=chunk.getReferenceTo(index);var player=chunk.getComponent(index,PlayerRef.getComponentType());
        runtime.pollMaintenance();
        if(!sessionReady(player.getUuid()))return;
        var stats=chunk.getComponent(index,EntityStatMap.getComponentType());
        if(durableCycloneEnabled){
            if(!alive(store,ref))clearDurableCycloneOnDeath(player);
            else completedCasterDeathSweeps.remove(player.getUuid());
            retryCasterDeathSweep(player.getUuid());
        }
        var liveGear=com.inigmasgames.hytalerpg.gear.GearNativeItems.effects(ref,store).snapshot();
        syncItemAuras(store,ref,player,buffer,liveGear);
        runtime.finite().reprojectShieldCapacity(player.getUuid(),liveGear,System.nanoTime()/1e9);
        NativeManaRegenerationAdapter.install(stats,()->manaRegenerationIncreased(player.getUuid(),System.nanoTime()/1e9)
                +liveGear.percent(com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.Operator.MANA_REGEN));
        NativeStaminaRegenerationAdapter.install(stats,()->liveGear
                .percent(com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.Operator.STAMINA_REGEN));
        runtime.tick(player.getUuid(),System.nanoTime()/1e9,alive(store,ref),new Port(store,ref,buffer));
        // Depletion removes the finite shield inside the damage callback. Retire its cosmetic
        // entity effect on the next owner tick; native Health protection already ended at impact.
        if(durableCycloneEnabled){
            var binding=cycloneConnections.get(player.getUuid());
            if(binding!=null&&binding.player()==player){
                try{
                    var state=durableCyclone.state(binding.lease());
                    if(state.phase()!=CycloneBarrierLedger.Phase.ACTIVE){
                        cycloneLive.remove(player.getUuid());
                        CycloneArmorPresentation.remove(store,ref);
                    }
                }catch(RuntimeException unavailable){
                    cycloneLive.remove(player.getUuid());
                    try{CycloneArmorPresentation.remove(store,ref);}catch(RuntimeException ignored){/* cosmetic */}
                }
            }
        }else if(runtime.finite().forTarget(SupportNativeEffects.world(store),player.getUuid(),System.nanoTime()/1e9)
                .stream().noneMatch(effect->effect.key().owner().equals(player.getUuid())
                        &&effect.key().skill().equals("spirit_shield")
                        &&effect.kind()==SupportProfile.Kind.SHIELD))
            try{CycloneArmorPresentation.remove(store,ref);}catch(RuntimeException ignored){/* cosmetic */}
        kernel.cooldowns().setAuraRate(player.getUuid(),cooldownRecoveryIncreased(player.getUuid(),System.nanoTime()/1e9)
                +kernel.statuses().cooldownRecoveryRate(player.getUuid()),1,.25);
        try{if(kernel.cooldowns().checkpoint(player.getUuid()))cooldownSaveWarnings.remove(player.getUuid());}
        catch(RuntimeException failure){if(cooldownSaveWarnings.add(player.getUuid()))
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(failure).log("RPG_COOLDOWN_CHECKPOINT_FAILED player=%s",player.getUuid());}

        }
    }
    /** Equipment transition and sustain owner; invalid sources are withdrawn before the next projection tick. */
    public void syncItemAuras(Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,
                              CommandBuffer<EntityStore> buffer,
                              com.inigmasgames.hytalerpg.gear.GearEffectSnapshot snapshot){
        if(itemAuraContext==null)return;
        var desired=new HashSet<SupportRuntime.ItemAuraSource>();
        for(var source:snapshot.sources(com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.Operator.ITEM_AURA)){
            String skill=switch(source.affixId()){
                case "WA-148"->"emanatism";case "WA-149"->"thorns_aura";case "WA-150"->"pedanticism";
                default->null;};
            if(skill!=null)desired.add(new SupportRuntime.ItemAuraSource(source.itemId(),skill));
        }
        var port=new Port(store,actor,buffer);
        for(var active:runtime.itemAuras(player.getUuid()))if(!desired.contains(active))
            runtime.endItemAura(player.getUuid(),active.item(),active.skill(),"SOURCE_REMOVED",port);
        double now=System.nanoTime()/1e9;
        for(var wanted:desired)if(!runtime.itemAuraActive(player.getUuid(),wanted.item(),wanted.skill())){
            try{var context=itemAuraContext.create(store,actor,wanted.item(),wanted.skill(),snapshot.forItem(wanted.item()),buffer);
                runtime.activateItemAura(context,wanted.item(),false,now,port);
            }catch(RuntimeException failure){
                com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(failure)
                        .log("RPG_ITEM_AURA_ACTIVATION_FAILED actor=%s item=%s skill=%s",player.getUuid(),wanted.item(),wanted.skill());
            }
        }
    }
    /** Public binding for the Sentinel owner: call with its exact bound item and a D08 world port. */
    public String activateSentinelItemAura(SkillExecutionContext context,UUID boundItem,double now,SupportWorldPort port){
        return runtime.activateItemAura(context,boundItem,true,now,port);
    }
    public void endSentinelItemAura(UUID sentinel,UUID boundItem,String skill,String reason,SupportWorldPort port){
        runtime.endItemAura(sentinel,boundItem,skill,reason,port);
    }
    public void tickSentinelItemAuras(UUID sentinel,double now,boolean alive,SupportWorldPort port){
        runtime.tick(sentinel,now,alive,port);
    }
    public void detachSentinelItemAuras(UUID sentinel,String reason,SupportWorldPort port){
        runtime.detach(sentinel,reason,port);
    }
    public void ready(Store<EntityStore> store,Ref<EntityStore> actor){
        var player=store.getComponent(actor,PlayerRef.getComponentType());
        kernel.cooldowns().restore(player.getUuid());
        runtime.detach(player.getUuid(),"PLAYER_READY_RESET",port(store,actor));
        // A persisted native max modifier is capacity, not proof that a paid Aura may resume after reconnect.
        kernel.reservations().removeAll(player.getUuid(),new EntityStatResourcePort(store.getComponent(actor,EntityStatMap.getComponentType())));
    }
    public void detach(Store<EntityStore> store,Ref<EntityStore> actor,String reason){
        var player=store.getComponent(actor,PlayerRef.getComponentType());
        if(player!=null)cycloneLive.computeIfPresent(player.getUuid(),
                (id,live)->live.player()==player?null:live);
        if(durableCycloneEnabled&&player!=null){
            liveSharedPayments.computeIfPresent(player.getUuid(),(id,proof)->proof.player()==player?null:proof);
            var binding=cycloneConnections.get(player.getUuid());
            if(binding!=null&&binding.player()==player&&cycloneConnections.remove(player.getUuid(),binding))
                durableCyclone.detach(binding.lease());
            var shared=sharedConnections.get(player.getUuid());
            if(shared!=null&&shared.player()==player&&sharedConnections.remove(player.getUuid(),shared))
                cycloneShared.detach(shared.lease());
        }
        try{CycloneArmorPresentation.remove(store,actor);}catch(RuntimeException ignored){/* cosmetic */}
        if(player!=null){beginReady(player.getUuid());cooldownSaveWarnings.remove(player.getUuid());runtime.detach(player.getUuid(),reason,port(store,actor));}
    }
    void clearDurableCycloneOnDeath(PlayerRef player){
        liveSharedPayments.computeIfPresent(player.getUuid(),(id,proof)->proof.player()==player?null:proof);
        if(!completedCasterDeathSweeps.contains(player.getUuid()))pendingCasterDeathSweeps.add(player.getUuid());
        var binding=cycloneConnections.get(player.getUuid());
        if(binding!=null&&binding.player()==player)try{durableCyclone.clearForDeath(binding.lease());}
        catch(RuntimeException failure){
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(failure)
                    .log("RPG_CYCLONE_DURABLE_DEATH_CLEAR_FAILED player=%s",player.getUuid());
        }
        cycloneLive.computeIfPresent(player.getUuid(),(id,live)->live.player()==player?null:live);
        var shared=sharedConnections.get(player.getUuid());
        if(shared!=null&&shared.player()==player)try{cycloneShared.clearForDeath(shared.lease());}
        catch(RuntimeException failure){
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(failure)
                    .log("RPG_CYCLONE_SHARED_DEATH_CLEAR_FAILED recipient=%s",player.getUuid());
        }
        retryCasterDeathSweep(player.getUuid());
    }
    private void retryCasterDeathSweep(UUID caster){
        if(!pendingCasterDeathSweeps.contains(caster))return;
        try{
            cycloneShared.clearCasterForDeath(caster);
            pendingCasterDeathSweeps.remove(caster);
            completedCasterDeathSweeps.add(caster);
            casterDeathSweepWarnings.remove(caster);
        }catch(RuntimeException failure){
            if(casterDeathSweepWarnings.add(caster))
                com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(failure)
                        .log("RPG_CYCLONE_SHARED_CASTER_DEATH_CLEAR_FAILED caster=%s",caster);
        }
    }
    /** Native death callback is earlier than the next support tick; tick remains a fallback. */
    public static final class CycloneDeath extends com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems.OnDeathSystem {
        private final HytaleSupportSystem owner;
        @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value="EI_EXPOSE_REP2",
                justification="The native death system intentionally shares the registered support authority.")
        public CycloneDeath(HytaleSupportSystem owner){this.owner=owner;}
        @Override public Query<EntityStore> getQuery(){return PlayerRef.getComponentType();}
        @Override public void onComponentAdded(Ref<EntityStore> ref,
                com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent death,
                Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
            if(owner.durableCycloneEnabled){
                var player=store.getComponent(ref,PlayerRef.getComponentType());
                if(player!=null){
                    owner.clearDurableCycloneOnDeath(player);
                    buffer.run(s->{try{CycloneArmorPresentation.remove(s,ref);}
                        catch(RuntimeException ignored){/* Durable death record remains authoritative. */}});
                }
            }
        }
    }
    public SkillExecutionPort.Validation preflight(Store<EntityStore> store,Ref<EntityStore> actor,Stage04SkillProfile profile,CompiledSkillPlan plan){
        var id=store.getComponent(actor,PlayerRef.getComponentType()).getUuid();
        try{
            if(durableCycloneEnabled&&profile.skillId().equals("spirit_shield")){
                var authority=cycloneAuthority(store.getComponent(actor,PlayerRef.getComponentType()));
                var state=durableCyclone.state(authority.lease());
                if(state.phase()!=CycloneBarrierLedger.Phase.NONE&&state.phase()!=CycloneBarrierLedger.Phase.DEPLETED)
                    return SkillExecutionPort.Validation.reject("CYCLONE_BARRIER_ALREADY_ACTIVE_OR_PENDING");
            }
            NativeManaReservationProjection.maximumMultiplier(store.getComponent(actor,EntityStatMap.getComponentType()).get(DefaultEntityStatTypes.getMana()));
            String result=runtime.preflight(id,profile.skillId(),profile.support(),plan.supportModifiers(),port(store,actor));
            if(!result.equals("PASS"))return SkillExecutionPort.Validation.reject(result);
            if(profile.support().allyTarget())selectHealTarget(store,actor,profile.support().range());
            if(profile.support().hostileTarget())SupportNativeEffects.requireTarget(store,actor,selectHostileTarget(store,actor,profile.support().range()),profile.support(),bosses);
            if(profile.support().recipientBurst()){
                SupportNativeEffects.requireAssets();
                for(var ref:allyRefs(store,actor,profile.support().radius()*plan.executionModifiers().radiusFactor(),true))SupportNativeEffects.requireRallyRecipient(store,ref);
            }
            if(profile.support().allyAura())allyRefs(store,actor,profile.support().radius()*plan.executionModifiers().radiusFactor()*plan.supportModifiers().radiusFactor(),true);
            if(profile.support().hostileAura())hostileRefs(store,actor,profile.support().radius()*plan.executionModifiers().radiusFactor()*plan.supportModifiers().radiusFactor());
            if(plan.supportModifiers().sharedAegis())sharedCandidates(store,actor);
            return SkillExecutionPort.Validation.pass();
        }catch(RuntimeException error){return SkillExecutionPort.Validation.reject("SUPPORT_PREFLIGHT_"+error.getMessage());}
    }
    public CommittedTarget capture(Store<EntityStore> store,Ref<EntityStore> actor,Stage04SkillProfile profile){
        var owner=store.getComponent(actor,PlayerRef.getComponentType());
        var chosen=profile.support().allyTarget()?selectHealTarget(store,actor,profile.support().range()):
                profile.support().hostileTarget()?selectHostileTarget(store,actor,profile.support().range()):actor;
        var id=store.getComponent(chosen,UUIDComponent.getComponentType());
        UUID target=chosen.equals(actor)?owner.getUuid():id.getUuid();
        return new CommittedTarget(owner.getWorldUuid(),position(store,actor),position(store,chosen),direction(store,actor),target);
    }
    public SkillExecutionResult execute(Store<EntityStore> store,Ref<EntityStore> actor,SkillExecutionContext context,CommandBuffer<EntityStore> buffer){
        return runtime.execute(context,System.nanoTime()/1e9,new Port(store,actor,buffer));
    }
    public SkillExecutionPort.Validation validateRelease(Store<EntityStore> store,Ref<EntityStore> actor,SkillExecutionContext context){
        String valid=port(store,actor).valid(context);
        if(!valid.equals("PASS"))return SkillExecutionPort.Validation.reject(valid);
        if(context.profile().support().aura())return SkillExecutionPort.Validation.reject("AURA_CANNOT_SCHEDULE_REPEAT");
        if(context.profile().support().recipientBurst())return SkillExecutionPort.Validation.pass();
        var owner=store.getComponent(actor,PlayerRef.getComponentType());
        var target=context.target().entityId();
        if(!kernel.statuses().allowsExternalMutation(owner.getUuid(),target))return SkillExecutionPort.Validation.reject("ENCOUNTER_PROTECTED");
        var ref=owner.getUuid().equals(target)?actor:target==null?null:store.getExternalData().getRefFromUUID(target);
        if(context.profile().support().hostileTarget()){
            try{SupportNativeEffects.requireTarget(store,actor,ref,context.profile().support(),bosses);return SkillExecutionPort.Validation.pass();}
            catch(RuntimeException error){return SkillExecutionPort.Validation.reject("COMMITTED_SUPPORT_TARGET_"+error.getMessage());}
        }
        return ref!=null&&eligibleAlly(store,actor,ref)&&inRange(store,actor,ref,context.profile().support().range())
                ?SkillExecutionPort.Validation.pass():SkillExecutionPort.Validation.reject("COMMITTED_HEAL_TARGET_INVALID");
    }
    public SupportWorldPort port(Store<EntityStore> store,Ref<EntityStore> actor){return new Port(store,actor);}
    public SupportWorldPort port(Store<EntityStore> store,Ref<EntityStore> actor,CommandBuffer<EntityStore> buffer){return new Port(store,actor,buffer);}
    private final class Port implements SupportWorldPort {
        final Store<EntityStore> store;final Ref<EntityStore> actor;final PlayerRef player;final CommandBuffer<EntityStore> buffer;
        Port(Store<EntityStore> store,Ref<EntityStore> actor){this(store,actor,null);}
        Port(Store<EntityStore> store,Ref<EntityStore> actor,CommandBuffer<EntityStore> buffer){this.store=store;this.actor=actor;this.buffer=buffer;player=store.getComponent(actor,PlayerRef.getComponentType());}
        public NativeResourcePort resources(){return new EntityStatResourcePort(store.getComponent(actor,EntityStatMap.getComponentType()));}
        public String valid(SkillExecutionContext context){
            if(!sessionReady(player.getUuid()))return "PLAYER_PERSISTENCE_NOT_READY";
            if(!alive(store,actor))return "OWNER_DEAD";
            if(context.target()==null||!context.target().worldId().equals(player.getWorldUuid()))return "WORLD_CHANGED";
            if(context.request().action().equals("ITEM_AURA")&&context.request().actorId().equals(player.getUuid())){
                if(context.gearSnapshot().items().size()!=1)return "ITEM_AURA_SOURCE_INVALID";
                var item=context.gearSnapshot().items().getFirst().identity();
                return com.inigmasgames.hytalerpg.gear.GearNativeItems.effects(actor,store).snapshot().forItem(item).empty()
                        ?"ITEM_AURA_SOURCE_REMOVED":"PASS";
            }
            if(context.request().action().equals("ITEM_TRIGGER")&&context.profile().skillId().equals("minor_heal")
                    &&context.request().actorId().equals(player.getUuid())
                    &&context.target().entityId()!=null&&context.target().entityId().equals(player.getUuid())
                    &&!context.gearSnapshot().empty())return "PASS";
            var view=loadouts.getPresentationView(player.getUuid());var plan=view.plans().get(context.request().slot());
            if(plan==null||!plan.planHash().equals(context.compiledPlan().planHash()))return "LOADOUT_CHANGED";
            if(!context.profile().allowedMainHandKinds().isEmpty()){
                var current=new HytaleEquipmentAdapter().read(actor,store).mainHand();var prior=context.equipment().mainHand();
                if(current==null||prior==null||!current.itemId().equals(prior.itemId()))return "EQUIPMENT_CHANGED";
            }
            return "PASS";
        }
        public List<UUID> allies(SkillExecutionContext context,double radius){
            var list=allyRefs(store,actor,radius,true);var result=new ArrayList<UUID>();
            for(var ref:list){
                var id=ref.equals(actor)?player.getUuid():store.getComponent(ref,UUIDComponent.getComponentType()).getUuid();
                if(!kernel.statuses().allowsExternalMutation(context.request().actorId(),id))continue;
                var stats=store.getComponent(ref,EntityStatMap.getComponentType());
                var liveGear=store.getComponent(ref,PlayerRef.getComponentType())==null
                        ?com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.EMPTY
                        :com.inigmasgames.hytalerpg.gear.GearNativeItems.effects(ref,store).snapshot();
                NativeManaRegenerationAdapter.install(stats,()->manaRegenerationIncreased(id,System.nanoTime()/1e9)
                        +liveGear.percent(com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.Operator.MANA_REGEN));
                NativeStaminaRegenerationAdapter.install(stats,()->liveGear
                        .percent(com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.Operator.STAMINA_REGEN));
                result.add(id);
            }
            return result;
        }
        public List<UUID> enemies(SkillExecutionContext context,double radius){return hostileRefs(store,actor,radius).stream()
                .map(ref->store.getComponent(ref,UUIDComponent.getComponentType()).getUuid())
                .filter(id->kernel.statuses().allowsExternalMutation(context.request().actorId(),id)).toList();}
        private com.inigmasgames.hytalerpg.combat.resource.ResourceCost itemAuraCost(SkillExecutionContext context){
            if(context.profile().resourceCost()<=0)return com.inigmasgames.hytalerpg.combat.resource.ResourceCost.NONE;
            return kernel.resources().evaluateActivation(new com.inigmasgames.hytalerpg.combat.resource.ResourceCost(
                    com.inigmasgames.hytalerpg.combat.resource.ResourceType.MANA,context.profile().resourceCost()),
                    context.compiledPlan(),0,com.inigmasgames.hytalerpg.execution.GearResourceModifiers.factor(
                            context.gearSnapshot(),com.inigmasgames.hytalerpg.combat.resource.ResourceType.MANA,false,false));
        }
        public double itemAuraActivationCost(SkillExecutionContext context){return itemAuraCost(context).amount();}
        public double itemAuraInitialUpkeepCost(SkillExecutionContext context){
            if(context.profile().support().upkeepPerSecond()<=0)return 0;
            return kernel.resources().evaluateUpkeep(new com.inigmasgames.hytalerpg.combat.resource.ResourceCost(
                    com.inigmasgames.hytalerpg.combat.resource.ResourceType.MANA,
                    context.profile().support().upkeepPerSecond()*.25),context.compiledPlan().kernelModifiers(),
                    com.inigmasgames.hytalerpg.execution.GearResourceModifiers.factor(context.gearSnapshot(),
                            com.inigmasgames.hytalerpg.combat.resource.ResourceType.MANA,false,false)).amount();
        }
        public boolean payItemAuraActivation(SkillExecutionContext context){
            var cost=itemAuraCost(context);if(cost.amount()<=0)return true;
            var resources=resources();if(!kernel.resources().canAfford(player.getUuid(),cost,resources))return false;
            var token=kernel.resources().reserveCost(player.getUuid(),cost,resources);
            try{kernel.resources().commitCost(token,resources);return true;}
            finally{kernel.resources().finish(token);}
        }
        public boolean upkeep(SkillExecutionContext context,double seconds,int quantum){
            var cost=kernel.resources().evaluateUpkeep(new com.inigmasgames.hytalerpg.combat.resource.ResourceCost(
                    com.inigmasgames.hytalerpg.combat.resource.ResourceType.MANA,context.profile().support().upkeepPerSecond()*seconds*context.compiledPlan().supportModifiers().commitmentFactor()),context.compiledPlan().kernelModifiers(),
                    com.inigmasgames.hytalerpg.execution.GearResourceModifiers.factor(context.gearSnapshot(),com.inigmasgames.hytalerpg.combat.resource.ResourceType.MANA,false,false));
            var resources=resources();double before=resources.current(com.inigmasgames.hytalerpg.combat.resource.ResourceType.MANA);
            if(!kernel.resources().canAfford(player.getUuid(),cost,resources))return false;
            var token=kernel.resources().reserveCost(player.getUuid(),cost,resources);
            try{
                kernel.resources().commitCost(token,resources);double after=resources.current(com.inigmasgames.hytalerpg.combat.resource.ResourceType.MANA);
                boolean observed=Math.abs(before-after-cost.amount())<=1e-4;
                trace(context,"AURA_UPKEEP",Map.of("quantum",quantum,"seconds",seconds,"before",before,"after",after,"cost",cost.amount(),"nativeWriteObserved",observed));
                return observed;
            }finally{kernel.resources().finish(token);}
        }
        public void auraPulse(SkillExecutionContext context,List<UUID> targets,int tick,boolean chill){auraPayload.apply(store,buffer,actor,context,targets,tick,chill);}
        public void auraMembership(SkillExecutionContext context,List<UUID> allies,List<UUID> enemies){
            if(context.profile().support().kind()!=SupportProfile.Kind.COOLDOWN_AURA)return;
            var affected=new HashSet<>(allies);var previous=cooldownRecipients.put(context.skillInstanceId(),Set.copyOf(allies));
            if(previous!=null)affected.addAll(previous);
            for(var id:affected)kernel.cooldowns().setAuraRate(id,cooldownRecoveryIncreased(id,System.nanoTime()/1e9)
                    +kernel.statuses().cooldownRecoveryRate(id),1,.25);
            // Native Cooldown.getCooldown() returns maximum, not remaining work. No exported remaining/charge progress getter exists.
            // Setting maximum or restarting native cooldowns would violate the elapsed-work contract.
            if(warnedCooldownRoots.add(context.skillInstanceId()))trace(context,"AURA_CAPABILITY_BLOCKED",Map.of(
                    "component","enemy.explicitNativeCooldown","reason","NATIVE_COOLDOWN_REMAINING_WORK_NOT_EXPOSED",
                    "allyRpgRecoveryImplemented",true,"enemyCount",enemies.size(),"animationModified",false));
        }
        public void auraEnded(SkillExecutionContext context){
            if(context.profile().support().kind()==SupportProfile.Kind.MANTLE_OF_FLAME){
                java.util.function.Consumer<Store<EntityStore>> remove=s->{boolean tracked=mantlePresentationRoots.remove(context.skillInstanceId());
                    try{MantlePresentation.remove(s,actor);if(tracked)mantleOwner(context,false);
                    }catch(RuntimeException failure){mantleVisualFailure(context,failure);}};
                if(buffer!=null)buffer.run(remove);else store.getExternalData().getWorld().execute(()->remove.accept(store));
            }
            warnedCooldownRoots.remove(context.skillInstanceId());var previous=cooldownRecipients.remove(context.skillInstanceId());
            if(previous!=null)for(var id:previous)kernel.cooldowns().setAuraRate(id,cooldownRecoveryIncreased(id,System.nanoTime()/1e9)
                    +kernel.statuses().cooldownRecoveryRate(id),1,.25);
        }
        public double heal(SkillExecutionContext context,UUID target,double requested){
            if(!valid(context).equals("PASS"))throw new IllegalStateException("HEAL_OWNER_INVALID");
            if(!kernel.statuses().allowsExternalMutation(context.request().actorId(),target))return 0;
            var ref=target.equals(player.getUuid())?actor:store.getExternalData().getRefFromUUID(target);
            if(ref==null||!eligibleAlly(store,actor,ref)||!inRange(store,actor,ref,context.profile().support().range()))
                throw new IllegalStateException("HEAL_TARGET_INVALID");
            var stats=store.getComponent(ref,EntityStatMap.getComponentType());int index=DefaultEntityStatTypes.getHealth();
            requested=receivedHealing(requested,com.inigmasgames.hytalerpg.gear.GearNativeItems.recipientEffects(ref,store));
            double before=stats.get(index).get();stats.setStatValue(index,(float)Math.min(stats.get(index).getMax(),before+requested));
            double after=stats.get(index).get();
            healingResolved(store,buffer,ref,context,requested,before,after,stats.get(index).getMax());
            trace(context,"HEAL_APPLIED",Map.of("target",target.toString(),"requested",requested,"healthBefore",before,"healthAfter",after,"actualHealing",Math.max(0,after-before)));
            return Math.max(0,after-before);
        }
        public Health health(UUID target){
            var ref=target.equals(player.getUuid())?actor:store.getExternalData().getRefFromUUID(target);
            if(!alive(store,ref))throw new IllegalStateException("HEAL_TARGET_INVALID");
            var hp=store.getComponent(ref,EntityStatMap.getComponentType()).get(DefaultEntityStatTypes.getHealth());return new Health(hp.get(),hp.getMax());
        }
        public UUID nearestAlly(SkillExecutionContext context){
            var origin=position(store,actor).add(new Vec3(0,1.35,0));
            return allyRefs(store,actor,8).stream().filter(ref->!ref.equals(actor))
                    .filter(ref->kernel.statuses().allowsExternalMutation(context.request().actorId(),store.getComponent(ref,UUIDComponent.getComponentType()).getUuid()))
                    .min(Comparator.<Ref<EntityStore>>comparingDouble(ref->ConnectionShape.pointDistanceSquared(origin,bounds(store,ref)))
                            .thenComparing(ref->store.getComponent(ref,UUIDComponent.getComponentType()).getUuid().toString()))
                    .map(ref->store.getComponent(ref,UUIDComponent.getComponentType()).getUuid()).orElse(null);
        }
        public void sharedRecipient(SkillExecutionContext context,UUID target){
            if(target==null)return;var ref=store.getExternalData().getRefFromUUID(target);
            if(buffer!=null)buffer.ensureComponent(ref,SupportEffectProjection.getComponentType());else store.ensureComponent(ref,SupportEffectProjection.getComponentType());
        }
        public void finiteEffect(SkillExecutionContext context,FiniteSupportEffects effects,double now){
            if(!valid(context).equals("PASS"))throw new IllegalStateException("SUPPORT_OWNER_INVALID");
            var p=context.profile().support();
            var refs=p.recipientBurst()?allyRefs(store,actor,p.radius()*context.compiledPlan().executionModifiers().radiusFactor(),true):
                    List.of(context.target().entityId().equals(player.getUuid())?actor:store.getExternalData().getRefFromUUID(context.target().entityId()));
            refs=refs.stream().filter(ref->ref!=null&&ref.isValid()&&kernel.statuses().allowsExternalMutation(context.request().actorId(),
                    ref.equals(actor)?player.getUuid():store.getComponent(ref,UUIDComponent.getComponentType()).getUuid())).toList();
            if(refs.isEmpty())return;
            double seconds=FiniteSupportEffects.durationSeconds(context);
            if(p.hostileTarget())SupportNativeEffects.requireTarget(store,actor,refs.getFirst(),p,bosses);
            if(p.recipientBurst())for(var ref:refs)SupportNativeEffects.requireRallyRecipient(store,ref);
            if(p.allyTarget()&&(!eligibleAlly(store,actor,refs.getFirst())||!inRange(store,actor,refs.getFirst(),p.range())))
                throw new IllegalStateException("SHIELD_ALLY_TARGET_INVALID");
            var ids=refs.stream().map(ref->ref.equals(actor)?player.getUuid():store.getComponent(ref,UUIDComponent.getComponentType()).getUuid()).toList();
            effects.requireAdmission(context,ids,seconds,now);
            if(p.kind()==SupportProfile.Kind.FEAR){
                var control=SupportNativeEffects.control(store,refs.getFirst(),bosses);
                var result=kernel.statuses().applyHostile(context.request().actorId(),context.target().entityId(),com.inigmasgames.hytalerpg.combat.status.RpgStatusType.FEAR,control,seconds);
                if(result.detail().equals("FORTUNE_STATUS_AVOIDED")||result.detail().equals("STATUS_CHANCE_FAILED")){
                    trace(context,"HOSTILE_STATUS_REJECTED",Map.of("target",context.target().entityId(),"kind",p.kind(),"reason",result.detail()));return;
                }
                if(result.outcome()==com.inigmasgames.hytalerpg.combat.status.StatusService.Outcome.REJECTED)throw new IllegalStateException(result.detail());
                seconds=result.remainingSeconds();
            }
            if(p.hostileTarget() && p.kind()!=SupportProfile.Kind.FEAR){
                var admission=kernel.statuses().admit(new com.inigmasgames.hytalerpg.combat.status.StatusApplication(
                        context.request().actorId(),context.target().entityId(),p.kind().name(),1,0,true,false,false,false),
                        SupportNativeEffects.control(store,refs.getFirst(),bosses),true);
                if(admission==com.inigmasgames.hytalerpg.combat.status.StatusService.Admission.FORTUNE_STATUS_AVOIDED
                        ||admission==com.inigmasgames.hytalerpg.combat.status.StatusService.Admission.STATUS_CHANCE_FAILED){
                    trace(context,"HOSTILE_STATUS_REJECTED",Map.of("target",context.target().entityId(),"kind",p.kind(),"reason",admission.name()));return;
                }
                if(admission!=com.inigmasgames.hytalerpg.combat.status.StatusService.Admission.ACCEPTED)
                    throw new IllegalStateException(admission.name());
            }
            try{
                if(p.kind()==SupportProfile.Kind.SHIELD){
                    var shield=SupportMagnitude.shieldCalculation(context,masteryMultiplier(context));
                    double capacity=shield.finalCapacity();
                    effects.applyShield(context,ids,seconds,capacity,now);
                    if(context.compiledPlan().supportModifiers().sharedAegis()&&context.request().actorId().equals(context.target().entityId())){
                        var child=effects.shareCreatedShield(context,nearestAlly(context),now);
                        if(child.isPresent()){
                            var childRef=store.getExternalData().getRefFromUUID(child.get().key().target());
                            if(buffer!=null)buffer.ensureComponent(childRef,SupportEffectProjection.getComponentType());else store.ensureComponent(childRef,SupportEffectProjection.getComponentType());
                            traceFinite(child.get(),RpgTraceEventType.BARRIER_CREATED,Map.of("derived",true,"capacity",child.get().shieldRemaining(),"canShareAgain",false));
                        }
                    }
                    if(shield.cyclone()!=null)try{
                        trace(context,"BARRIER_CREATED",SupportMagnitude.cycloneTrace(context,shield));
                    }catch(RuntimeException failure){
                        com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(failure)
                                .log("RPG_CYCLONE_TRACE_FAILED actor=%s",player.getUuid());
                    }
                }else effects.apply(context,ids,seconds,now);
                for(var ref:refs){
                    if(buffer!=null)buffer.ensureComponent(ref,SupportEffectProjection.getComponentType());
                    else store.ensureComponent(ref,SupportEffectProjection.getComponentType());
                }
                for(var id:ids)trace(context,"FINITE_SUPPORT_APPLIED",Map.of("target",id.toString(),"kind",p.kind().name(),
                        "durationSeconds",seconds,"magnitude",p.coefficient(),"movementIncreased",p.movementIncreased(),"authority","RPG_EFFECT_LEDGER","nativeBehaviorVerified",false));
            }catch(RuntimeException failure){
                if(p.kind()==SupportProfile.Kind.FEAR)kernel.statuses().remove(context.target().entityId(),com.inigmasgames.hytalerpg.combat.status.RpgStatusType.FEAR);
                throw failure;
            }
        }
        public void present(SkillExecutionContext context,double radius,double duration){
            if(context.profile().skillId().equals("spirit_shield")&&context.profile().support().kind()==SupportProfile.Kind.SHIELD){
                try{CycloneArmorPresentation.apply(store,actor,(float)FiniteSupportEffects.durationSeconds(context));}
                catch(RuntimeException failure){com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(failure)
                        .log("RPG_CYCLONE_VISUAL_FAILED actor=%s",player.getUuid());}
                return;
            }
            if(context.profile().support().kind()==SupportProfile.Kind.MANTLE_OF_FLAME){
                java.util.function.Consumer<Store<EntityStore>> renew=s->{
                    if(runtime.activeContext(context.request().actorId(),"mantle_of_flame")!=context||!actor.isValid())return;
                    try{MantlePresentation.apply(s,actor,"RPG_Mantle_Aura",.75f);
                        if(mantlePresentationRoots.add(context.skillInstanceId()))mantleOwner(context,true);
                    }catch(RuntimeException failure){mantleVisualFailure(context,failure);}
                };
                if(buffer!=null)buffer.run(renew);else store.getExternalData().getWorld().execute(()->renew.accept(store));
                return;
            }
            if(context.profile().support().kind()==SupportProfile.Kind.MAX_HEALTH_DAMAGE_CAP){
                try{com.hypixel.hytale.server.core.entity.AnimationUtils.playAnimation(actor,com.hypixel.hytale.protocol.AnimationSlot.Action,"Spellbook","CastPushCharged",true,store);}
                catch(RuntimeException ignored){/* Presentation never changes committed protection. */}
                return;
            }
            // Tracking uses an entity-bound tint lease, never a world-broadcast debug outline.
            if(context.profile().support().kind()==SupportProfile.Kind.MARK)return;
            var origin=position(store,actor).add(new Vec3(0,1,0));
            if(!context.profile().support().aura()&&context.target()!=null)origin=context.target().point().add(new Vec3(0,1,0));
            try{vfx.presentConnection(store.getExternalData().getWorld(),ConnectionShape.cylinder(origin,Math.max(.5,radius),radius>0?3:2),
                    context.profile().support().kind()==SupportProfile.Kind.HEAL?"HOLY":"MAGIC","SUPPORT",duration);}
            catch(RuntimeException ignored){/* Missing presentation cannot refund applied healing or reservation. */}
        }
        public void trace(SkillExecutionContext context,String event,Map<String,?> details){
            var values=new HashMap<String,Object>(details);values.put("skillId",context.profile().skillId());
            trace.emit(context.request().actorId(),RpgTraceEventType.valueOf(event),
                    new CombatTrace.Context(context.rootCastId(),context.skillInstanceId(),context.request().correlationId()),values);
        }
    }
    private static List<Ref<EntityStore>> allyRefs(Store<EntityStore> store,Ref<EntityStore> actor,double range){
        return allyRefs(store,actor,range,false);
    }
    private static List<Ref<EntityStore>> allyRefs(Store<EntityStore> store,Ref<EntityStore> actor,double range,boolean cylinder){
        var query=Query.and(UUIDComponent.getComponentType(),EntityStatMap.getComponentType(),TransformComponent.getComponentType(),BoundingBox.getComponentType());
        if(store.getEntityCountFor(query)>4096)throw new IllegalStateException("SUPPORT_SCAN_BUDGET");
        var selected=new ArrayList<Ref<EntityStore>>();selected.add(actor);
        boolean overflow=store.forEachChunk(query,(chunk,buffer)->{
            for(int index=0;index<chunk.size();index++){
                var ref=chunk.getReferenceTo(index);
                if(ref.equals(actor)||!eligibleAlly(store,actor,ref)||!(cylinder?auraInRange(store,actor,ref,range):inRange(store,actor,ref,range)))continue;
                if(selected.size()>=64)return true;selected.add(ref);
            }
            return false;
        });
        if(overflow)throw new IllegalStateException("SUPPORT_TARGET_BUDGET");
        return List.copyOf(selected);
    }
    private final Set<String> warnedCooldownRoots=java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final Map<String,Set<UUID>> cooldownRecipients=new java.util.concurrent.ConcurrentHashMap<>();
    private List<Ref<EntityStore>> hostileRefs(Store<EntityStore> store,Ref<EntityStore> actor,double radius){
        var shape=ConnectionShape.cylinder(position(store,actor).add(new Vec3(0,1.5,0)),radius,3);
        var found=HytaleAreaQueries.query(store,actor,shape::intersects,256);
        if(found.overflow())throw new IllegalStateException("AURA_CANDIDATE_BUDGET");
        var list=found.candidates().stream().filter(c->alive(store,c.ref())&&!SupportNativeEffects.control(store,c.ref(),bosses).protectedEntity()
                &&HytaleAreaQueries.clear(store,position(store,actor).add(new Vec3(0,1.35,0)),c.bounds().centre())).map(HytaleAreaQueries.Candidate::ref).toList();
        if(list.size()>64)throw new IllegalStateException("AURA_TARGET_BUDGET");return list;
    }
    static boolean auraInRange(Store<EntityStore> store,Ref<EntityStore> actor,Ref<EntityStore> target,double radius){
        return actor.equals(target)||ConnectionShape.cylinder(position(store,actor).add(new Vec3(0,1.5,0)),radius,3).intersects(bounds(store,target))
                &&HytaleAreaQueries.clear(store,position(store,actor).add(new Vec3(0,1.35,0)),bounds(store,target).centre());
    }
    static boolean eligibleAlly(Store<EntityStore> store,Ref<EntityStore> actor,Ref<EntityStore> target){
        return eligibleAlly(store,actor,target,false);
    }
    /** Damage immunity is not healing immunity. Only the Heal Tether opts into this policy. */
    static boolean eligibleHealingAlly(Store<EntityStore> store,Ref<EntityStore> actor,Ref<EntityStore> target){
        return eligibleAlly(store,actor,target,true);
    }
    private static boolean eligibleAlly(Store<EntityStore> store,Ref<EntityStore> actor,Ref<EntityStore> target,boolean healing){
        if(target==null||!target.isValid()||!alive(store,target))return false;
        if(actor.equals(target))return true;
        boolean invulnerable=store.getComponent(target,Invulnerable.getComponentType())!=null;
        var faction=store.getComponent(target,WorldSupport.getComponentType());
        // No native Party/Team membership API was found. NEUTRAL/no-PvP is not affirmative ally membership.
        if(faction==null)return false;var attitude=NativeNpcAttitudes.prepared(faction).getAttitude(target,actor,store);
        return permitsAllyAttitude(attitude,invulnerable,healing);
    }
    static boolean permitsAllyAttitude(Attitude attitude,boolean invulnerable,boolean healing){
        return (healing||!invulnerable)&&(attitude==Attitude.FRIENDLY||attitude==Attitude.REVERED);
    }
    static boolean inRange(Store<EntityStore> store,Ref<EntityStore> actor,Ref<EntityStore> target,double range){
        if(actor.equals(target))return true;var origin=position(store,actor).add(new Vec3(0,1.35,0));
        var bounds=bounds(store,target);
        return ConnectionShape.pointDistanceSquared(origin,bounds)<=range*range+1e-9&&HytaleAreaQueries.clear(store,origin,bounds.centre());
    }
    private static Ref<EntityStore> selectHealTarget(Store<EntityStore> store,Ref<EntityStore> actor,double range){
        var origin=position(store,actor).add(new Vec3(0,1.35,0));
        var ray=ConnectionShape.line(origin,origin.add(direction(store,actor).multiply(range)),.02,.02);
        return allyRefs(store,actor,range).stream().filter(ref->!ref.equals(actor)&&ray.intersects(bounds(store,ref)))
                .min(Comparator.<Ref<EntityStore>>comparingDouble(ref->ray.entryDistance(bounds(store,ref)))
                        .thenComparing(ref->store.getComponent(ref,UUIDComponent.getComponentType()).getUuid().toString())).orElse(actor);
    }
    private static Ref<EntityStore> selectHostileTarget(Store<EntityStore> store,Ref<EntityStore> actor,double range){
        var origin=position(store,actor).add(new Vec3(0,1.35,0));
        var ray=ConnectionShape.line(origin,origin.add(direction(store,actor).multiply(range)),.02,.02);
        var found=HytaleAreaQueries.query(store,actor,ray::intersects,64);
        if(found.overflow())throw new IllegalStateException("SUPPORT_TARGET_BUDGET");
        return found.candidates().stream().filter(c->alive(store,c.ref())&&inRange(store,actor,c.ref(),range))
                .min(Comparator.comparingDouble((HytaleAreaQueries.Candidate c)->ray.entryDistance(c.bounds()))
                        .thenComparing(c->store.getComponent(c.ref(),UUIDComponent.getComponentType()).getUuid().toString()))
                .orElseThrow(()->new IllegalStateException("NO_VALID_HOSTILE_TARGET")).ref();
    }
    static boolean alive(Store<EntityStore> store,Ref<EntityStore> ref){
        if(ref==null)return false;
        if(!ref.isValid()||store.getComponent(ref,DeathComponent.getComponentType())!=null)return false;
        var stats=store.getComponent(ref,EntityStatMap.getComponentType());var health=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
        return health!=null&&health.get()>health.getMin();
    }
    private static AreaGeometry.Bounds bounds(Store<EntityStore> store,Ref<EntityStore> ref){
        var box=store.getComponent(ref,BoundingBox.getComponentType()).getBoundingBox();var p=position(store,ref);
        return new AreaGeometry.Bounds(vec(box.min).add(p),vec(box.max).add(p));
    }
    static Vec3 position(Store<EntityStore> store,Ref<EntityStore> ref){return vec(store.getComponent(ref,TransformComponent.getComponentType()).getPosition());}
    private static Vec3 direction(Store<EntityStore> store,Ref<EntityStore> ref){
        var head=store.getComponent(ref,HeadRotation.getComponentType());
        return head!=null?vec(head.getDirection()).normalized():Vec3.FORWARD;
    }
    private static Vec3 vec(org.joml.Vector3dc v){return new Vec3(v.x(),v.y(),v.z());}
    public static final class Absorb extends DamageEventSystem {
        private final HytaleSupportSystem support;
        public Absorb(HytaleSupportSystem support){this.support=support;}
        @Override public Query<EntityStore> getQuery(){return PlayerRef.getComponentType();}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
                new SystemGroupDependency<>(Order.AFTER,DamageModule.get().getFilterDamageGroup()),
                new SystemDependency<>(Order.BEFORE,HytaleDamageLifecycleSystems.Filter.class),
                new SystemDependency<>(Order.BEFORE,DamageSystems.ApplyDamage.class));}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.SUPPORT)){
            if(damage.isCancelled()||damage.getAmount()<=0)return;
            var metadata=HytaleDamageAdapter.metadata(damage);
            if(metadata!=null&&metadata.origin()==HytaleDamageMetadata.Origin.REDIRECTED)return;
            var ref=chunk.getReferenceTo(index);var actor=chunk.getComponent(index,PlayerRef.getComponentType()).getUuid();double now=System.nanoTime()/1e9;
            if(!support.sessionReady(actor))return;
            if(damage.getSource() instanceof Damage.EntitySource source&&HytaleAreaQueries.hostile(store,source.getRef(),ref)){
                support.runtime.hostileDamage(actor,now);support.kernel.hostileCombat().markHostile(actor);
            }
            try{double before=damage.getAmount();var hit=support.runtime.absorbDetailed(actor,damage.getAmount(),now,support.port(store,ref));
                damage.setAmount((float)hit.remainder());
                HytaleDamageLifecycleSystems.combatStage(damage,"Hywind player barrier","BARRIER_ABSORB",before,damage.getAmount(),
                        Map.of("absorbed",before-damage.getAmount(),"identity",hit.absorption()==null?"NONE":hit.absorption().effect().key().toString()));
                SupportDamageSystems.reflectAbsorbed(support,hit.absorption(),damage,ref,store,now);}
            catch(RuntimeException failure){
                // Failed persistence leaves the original native amount unchanged; never grant unrecorded shielding.
                com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(failure).log("RPG_BARRIER_REJECTED player=%s",actor);
            }

        }
    }
    }
    public static final class Removal extends com.hypixel.hytale.component.system.RefSystem<EntityStore>{
        private final HytaleSupportSystem support;
        public Removal(HytaleSupportSystem support){this.support=support;}
        @Override public Query<EntityStore> getQuery(){return Query.and(PlayerRef.getComponentType(),EntityStatMap.getComponentType());}
        @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){}
        @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.SUPPORT)){
            try{support.detach(store,ref,"NATIVE_ENTITY_REMOVE_"+reason);}
            catch(RuntimeException failure){com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(failure)
                    .log("RPG support teardown failed; no active Aura is retained");}

        }
    }
    }
}
