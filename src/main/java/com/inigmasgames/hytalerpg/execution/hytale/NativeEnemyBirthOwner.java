package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.spawning.world.component.SpawnJobData;
import com.inigmasgames.hytalerpg.enemies.*;
import com.inigmasgames.hytalerpg.diagnostics.MonsterSpawnTrace;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Serializes one captured native flock through the existing durable birth and pack owners. */
public final class NativeEnemyBirthOwner implements NativeEnemySpawnGroups.Owner {
    private final EnemyNativeBindings bindings;
    private final EnemyWorldAdmission admission;
    private final HytaleEncounterRewards rewards;
    private final HytaleDifficultyCombat combat;
    private final NativeEnemyActions actions;
    private final NativeEnemyBirthReservation reservation;
    private final NativeEnemyBirthDecision decisions;
    private final NativeEnemyBirthAttachment attachment;
    private final NativeEnemyBirthPublication publication;
    private final boolean nativeHooksReady;
    private final TransientQaEncounters qaEncounters;
    private final Map<UUID,NativeEnemyBirthAttachment.Prepared> liveActors=new HashMap<>();

    public NativeEnemyBirthOwner(EnemyNativeBindings bindings,EnemyWorldAdmission admission,
            HytaleEncounterRewards rewards,HytaleDifficultyCombat combat,NativeEnemyActions actions,
            NativeEnemyBirthReservation reservation,NativeEnemyBirthDecision decisions,
            NativeEnemyBirthAttachment attachment,NativeEnemyBirthPublication publication,
            boolean nativeHooksReady){
        this.bindings=Objects.requireNonNull(bindings);this.admission=Objects.requireNonNull(admission);
        this.rewards=Objects.requireNonNull(rewards);this.combat=Objects.requireNonNull(combat);
        this.actions=Objects.requireNonNull(actions);
        this.reservation=Objects.requireNonNull(reservation);this.decisions=Objects.requireNonNull(decisions);
        this.attachment=Objects.requireNonNull(attachment);this.publication=Objects.requireNonNull(publication);
        this.nativeHooksReady=nativeHooksReady;
        this.qaEncounters=new TransientQaEncounters(combat);
    }
    public TransientQaEncounters qaEncounters(){return qaEncounters;}

    @Override public boolean eligible(NativeEnemySpawnGroups.Job job){
        return nativeHooksReady&&job.expectedMembers()<=8&&admission.admits(job.world())
                &&bindings.productionEligibilityRejection(job.nativeRole()).isEmpty()
                &&bindings.role(job.nativeRole()).map(EnemyNativeBindings.Role::productionPromotionEnabled).orElse(false);
    }

    @Override public Optional<NativeEnemySpawnGroups.Reservation> reserve(NativeEnemySpawnGroups.Job job,
            UUID firstNativeEntity,String nativeRole){
        if(!job.nativeRole().equals(nativeRole)||!eligible(job))return Optional.empty();
        // The native UUID is minted before its holder enters the store. It is stable across replay.
        var encounter=UUID.nameUUIDFromBytes(("me.encounter/"+job.world()+"/"+job.nativeJobId()+"/"
                +firstNativeEntity).getBytes(StandardCharsets.UTF_8));
        return Optional.of(new NativeEnemySpawnGroups.Reservation(job.world(),encounter,1));
    }

    @Override public void captured(Store<EntityStore> store,NativeEnemySpawnGroups.Group group){
        throw new IllegalStateException("ENEMY_BIRTH_NATIVE_JOB_REQUIRED");
    }

    @Override public void captured(Store<EntityStore> store,NativeEnemySpawnGroups.Group group,SpawnJobData nativeJob){
        if(!store.isInThread()||nativeJob==null||!eligible(group.job()))
            throw new IllegalStateException("ENEMY_BIRTH_CAPTURE_OWNER");
        if(MonsterSpawnTrace.enabled())try{MonsterSpawnTrace.event("NATIVE_GROUP_INTERCEPTED",group.job().world(),group.job().environment(),group.job().nativeRole(),
                "job="+group.job().nativeJobId()+" encounter="+group.reservation().encounter()
                +" requested="+group.job().expectedMembers()+" captured="+group.members().size()
                +" nativeFailed="+group.nativeFailed()+" activePackReservations="
                +admission.activePackReservations(group.job().world()));}
        catch(RuntimeException ignored){/* Diagnostics cannot affect the birth handoff. */}
        try{processBirth(store,group,reservation.begin(store,group,nativeJob),null);}
        catch(RuntimeException rejected){
            // A writer-uncertain birth remains quarantined for recovery. Never
            // propagate a HyARPG handoff exception through Hytale's spawn tick.
            fail(group.job().world(),"RESERVE",rejected);
        }
    }

    private record QaSpawnSite(org.joml.Vector3d position,
            com.hypixel.hytale.server.core.asset.type.model.config.Model model){}
    private record QaSpawnPlan(UUID world,UUID encounter,int roleIndex,
            List<QaSpawnSite> sites,List<HytaleEncounterRewards.QaProfile> profiles,
            EnemyBirthPlan preflight) {
        QaSpawnPlan {sites=List.copyOf(sites);profiles=List.copyOf(profiles);}
    }
    private static final class QaNoPosition extends IllegalArgumentException {
        final Map<String,Integer> outcomes;
        QaNoPosition(int attempts,Map<String,Integer> outcomes){
            super("QA_SPAWN_NO_VALID_POSITION attempts="+attempts);
            this.outcomes=Collections.unmodifiableMap(new TreeMap<>(outcomes));
        }
    }
    /** Exactly the native column-spawn admission path, without publishing an NPC during probing. */
    private static QaSpawnSite probeQaColumn(com.hypixel.hytale.server.core.universe.world.World world,
            com.hypixel.hytale.server.spawning.ISpawnableWithModel spawnable,
            int x,int z,double yHint,int candidate,int member,Map<String,Integer> outcomes){
        var context=new com.hypixel.hytale.server.spawning.SpawningContext();
        String outcome="UNKNOWN";
        try{
            if(!context.setSpawnable(spawnable))outcome="SPAWNABLE_"+context.getLastResult();
            else if(!context.set(world,x+.5,yHint,z+.5))outcome="COLUMN_"+context.getLastResult();
            else {
                var result=context.canSpawn();
                if(result!=com.hypixel.hytale.server.spawning.SpawnTestResult.TEST_OK)
                    outcome="NATIVE_"+result+"/"+context.getLastResult()+"/validation="
                            +context.getPositionValidationResult()+"/ground="
                            +(context.hasPositionSurroundings()?context.isOnSolidGround():"UNAVAILABLE");
                else if(context.getModel()==null)outcome="NATIVE_MODEL_UNAVAILABLE";
                else {
                    var site=new QaSpawnSite(new org.joml.Vector3d(context.xSpawn,context.ySpawn,context.zSpawn),
                            context.getModel());
                    com.hypixel.hytale.logger.HytaleLogger.getLogger().at(java.util.logging.Level.FINE).log(
                            "RPG_ENEMY_QA_COLUMN candidate=%s member=%s x=%s z=%s result=TEST_OK y=%s model=%s",
                            candidate,member,x,z,site.position().y(),site.model().getModelAssetId());
                    return site;
                }
            }
        }catch(RuntimeException rejected){outcome="PROBE_"+rootReason(rejected);}
        finally{context.releaseFull();}
        if(outcome.length()>120)outcome=outcome.substring(0,120);
        outcomes.merge(outcomes.size()>=12&&!outcomes.containsKey(outcome)?"OTHER":outcome,1,Integer::sum);
        com.hypixel.hytale.logger.HytaleLogger.getLogger().at(java.util.logging.Level.FINE).log(
                "RPG_ENEMY_QA_COLUMN candidate=%s member=%s x=%s z=%s result=%s",
                candidate,member,x,z,outcome);
        return null;
    }
    private QaSpawnPlan planQa(Store<EntityStore> store,com.hypixel.hytale.server.core.universe.world.World world,
            org.joml.Vector3dc playerPosition,com.hypixel.hytale.math.vector.Rotation3fc facing,
            EnemyQaSpawnRequest qa,EnemyNativeBindings.Role role){
        var npc=com.hypixel.hytale.server.npc.NPCPlugin.get();
        int roleIndex=npc.getIndex(qa.nativeRoleId());
        var builder=npc.tryGetCachedValidRole(roleIndex);
        if(builder==null||!builder.isSpawnable()
                ||!(builder instanceof com.hypixel.hytale.server.spawning.ISpawnableWithModel spawnable))
            throw new IllegalArgumentException("QA_NATIVE_SPAWNABLE_UNAVAILABLE:"+qa.nativeRoleId());
        int count=qa.offeredMembers();
        double fx=-Math.sin(facing.yaw()),fz=-Math.cos(facing.yaw());
        double rx=-fz,rz=fx;
        double[][] offsets={{0,0},{3,0},{-3,0},{0,3},{0,-3},{6,0},{-6,0},{3,3},{-3,3}};
        var outcomes=new TreeMap<String,Integer>();
        List<QaSpawnSite> sites=null;
        for(int attempt=0;attempt<offsets.length;attempt++){
            var offset=offsets[attempt];
            int x=(int)Math.floor(playerPosition.x()+fx*(7+offset[1])+rx*offset[0]);
            int z=(int)Math.floor(playerPosition.z()+fz*(7+offset[1])+rz*offset[0]);
            var anchor=probeQaColumn(world,spawnable,x,z,playerPosition.y(),attempt,0,outcomes);
            if(anchor==null)continue;
            var bounds=anchor.model().getBoundingBox();
            double spacing=Math.max(5,Math.ceil(Math.max(bounds.width(),bounds.depth()))+2);
            var candidate=new ArrayList<QaSpawnSite>();candidate.add(anchor);
            for(int index=1;index<count;index++){
                double angle=2*Math.PI*(index-1)/Math.max(1,count-1);
                int mx=(int)Math.floor(anchor.position().x()+Math.cos(angle)*spacing);
                int mz=(int)Math.floor(anchor.position().z()+Math.sin(angle)*spacing);
                var member=probeQaColumn(world,spawnable,mx,mz,anchor.position().y(),attempt,index,outcomes);
                if(member==null)break;
                candidate.add(member);
            }
            if(candidate.size()==count){sites=List.copyOf(candidate);break;}
        }
        if(sites==null)throw new QaNoPosition(offsets.length,outcomes);
        UUID worldId=world.getWorldConfig().getUuid(),encounter=UUID.randomUUID();
        var profiles=new ArrayList<HytaleEncounterRewards.QaProfile>();
        var previews=new ArrayList<com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry.Spawn>();
        for(int index=0;index<count;index++){
            UUID preview=UUID.nameUUIDFromBytes(("qa.preview/"+encounter+"/"+index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var at=sites.get(index).position();
            var profile=rewards.planQaProfile(store,qa.nativeRoleId(),
                    new com.inigmasgames.hytalerpg.execution.math.Vec3(at.x(),at.y(),at.z()),preview,qa.era());
            profiles.add(profile);previews.add(profile.forActor(preview));
        }
        var at=sites.getFirst().position();
        var preflight=decisions.preflightQa(worldId,encounter,
                new com.inigmasgames.hytalerpg.execution.math.Vec3(at.x(),at.y(),at.z()),previews,qa);
        return new QaSpawnPlan(worldId,encounter,roleIndex,sites,profiles,preflight);
    }
    /** Operator placement is fully preflighted; native actors remain frozen until shared owners attach. */
    /** Read-only publication receipt for owner command feedback; no second affix state owner. */
    public record QaSpawnResult(UUID encounter,List<String> affixIds) {
        public QaSpawnResult { affixIds=List.copyOf(affixIds); }
    }
    public java.util.concurrent.CompletionStage<QaSpawnResult> qaSpawn(Store<EntityStore> store,
            com.hypixel.hytale.server.core.universe.world.World world,org.joml.Vector3dc playerPosition,
            com.hypixel.hytale.math.vector.Rotation3fc facing,EnemyQaSpawnRequest qa){
        if(!store.isInThread()||!world.getWorldConfig().getUuid().equals(store.getExternalData().getWorld().getWorldConfig().getUuid())
                ||!nativeHooksReady)
            throw new IllegalStateException("ENEMY_QA_WORLD_UNAVAILABLE");
        var role=bindings.qaRole(qa.nativeRoleId()).orElseThrow(()->new IllegalArgumentException(
                "Role is absent from the authored monster catalog: "+qa.nativeRoleId()));
        var npc=com.hypixel.hytale.server.npc.NPCPlugin.get();
        npc.validateSpawnableRole(qa.nativeRoleId());
        QaSpawnPlan planned;
        try{planned=planQa(store,world,playerPosition,facing,qa,role);}
        catch(RuntimeException rejected){
            String stage=rejected instanceof QaNoPosition?"PLACE":"PLAN";
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                    "RPG_ENEMY_QA_SPAWN_FAILED stage=%s reason=%s nativeOutcomes=%s",stage,rootReason(rejected),
                    rejected instanceof QaNoPosition place?place.outcomes:Map.of());
            throw rejected;
        }
        UUID worldId=planned.world(),encounter=planned.encounter();
        var reservationId=new NativeEnemySpawnGroups.Reservation(worldId,encounter,1);
        var members=new ArrayList<NativeEnemySpawnGroups.Member>();
        var dropGuarded=new HashSet<UUID>();
        final String[] spawnStage={"NATIVE_SPAWN"};
        try{
            for(int index=0;index<planned.sites().size();index++){
                int expectedIndex=index;
                var site=planned.sites().get(index);
                var result=npc.spawnEntity(store,planned.roleIndex(),site.position(),facing,site.model(),(entity,holder,current)->{
                    spawnStage[0]="PRE_ADD";
                    var staged=EnemyStaging.prepare(holder,worldId,encounter,1);
                    members.add(new NativeEnemySpawnGroups.Member(staged.entity(),qa.nativeRoleId(),staged));
                    holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
                    holder.addComponent(QaTransientMarker.getComponentType(),
                            new QaTransientMarker(worldId,encounter,staged.entity()));
                    spawnStage[0]="NATIVE_SPAWN";
                },(entity,ref,current)->{
                    spawnStage[0]="POST_SPAWN";
                    var roleInstance=entity.getRole();
                    if(roleInstance==null)throw new IllegalStateException("QA_ROLE_UNAVAILABLE_POST_SPAWN");
                    roleInstance.setDeathItemsDropped();
                    if(!roleInstance.hasDroppedDeathItems())
                        throw new IllegalStateException("QA_NATIVE_DROPS_NOT_SUPPRESSED");
                    var id=ref==null||!ref.isValid()?null:current.getComponent(ref,
                            com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
                    if(members.size()!=expectedIndex+1||ref==null||!ref.isValid()
                            ||id==null||!members.get(expectedIndex).entity().equals(id.getUuid()))
                        throw new IllegalStateException("QA_POST_SPAWN_IDENTITY_CHANGED");
                    dropGuarded.add(members.get(expectedIndex).entity());
                    spawnStage[0]="NATIVE_SPAWN";
                });
                if(result==null||members.size()!=index+1||!dropGuarded.contains(members.get(index).entity()))
                    throw new IllegalStateException("QA_NATIVE_SPAWN_NULL");
            }
            var job=new NativeEnemySpawnGroups.Job(worldId,-1,planned.roleIndex(),qa.nativeRoleId(),-1,-1,members.size());
            var group=new NativeEnemySpawnGroups.Group(job,reservationId,members,false);
            return qaCaptured(store,group,qa,planned);
        }catch(RuntimeException rejected){
            discardQaActors(store,members,encounter);
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                    "RPG_ENEMY_QA_SPAWN_FAILED stage=%s encounter=%s reason=%s",
                    spawnStage[0],encounter,rootReason(rejected));
            throw rejected;
        }
    }
    private java.util.concurrent.CompletionStage<QaSpawnResult> qaCaptured(Store<EntityStore> store,
            NativeEnemySpawnGroups.Group group,EnemyQaSpawnRequest qa,QaSpawnPlan planned){
        var done=new java.util.concurrent.CompletableFuture<QaSpawnResult>();
        EnemyBirthPlan birth=null;
        try{
            var frozen=new HashMap<UUID,com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry.Spawn>();
            for(int i=0;i<group.members().size();i++)frozen.put(group.members().get(i).entity(),
                    planned.profiles().get(i).forActor(group.members().get(i).entity()));
            var selected=decisions.selectQa(store,group,qa,frozen);
            birth=selected.root().plan();
            var expected=planned.preflight().actors().getFirst().ownAffixes().stream()
                    .map(EnemyDescriptor.AffixInstance::affixId).toList();
            var actual=birth.actors().getFirst().ownAffixes().stream()
                    .map(EnemyDescriptor.AffixInstance::affixId).toList();
            if(!expected.equals(actual)||birth.actors().size()!=planned.preflight().actors().size())
                throw new IllegalStateException("QA_PREFLIGHT_AFFIX_OR_ROSTER_CHANGED");
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log(
                    "RPG_ENEMY_QA_PLAN role=%s era=%s level=%s affixes=%s members=%s",
                    qa.nativeRoleId(),qa.era(),birth.actors().getFirst().combatLevel(),actual,birth.actors().size());
            QaNativeFlocks.attach(store,birth);
            qaEncounters.reserve(birth);
            var pack=birth.pack();
            var tickets=new ArrayList<Object>();
            for(int i=0;i<birth.actors().size();i++){
                var actor=birth.actors().get(i);
                var nativeRole=bindings.requireActorRole(actor);
                var snapshot=EnemyAffixSnapshot.resolve(actor,EnemyBalance.forRevision(actor.balanceRevision()),pack,0,
                        nativeRole.capabilities().contains(EnemyAffixRegistry.Capability.MOBILE),
                        nativeRole.capabilities().contains(EnemyAffixRegistry.Capability.RECOVERY_TIMELINE));
                var ticket=combat.begin(actor.worldId(),actor.entityId());tickets.add(ticket);
                combat.ready(store,actor.entityId(),ticket,Optional.of(selected.sources().get(i).spawn()),
                        true,actor,snapshot);
                combat.bindEnemyPack(store,actor,pack);
            }
            rewards.bindStagedNativeIdentities(store,birth);
            var capturedBirth=birth;
            attachment.prepareQa(store,birth,qaEncounters::reserveActionRoots).whenComplete((prepared,error)->{
                try{store.getExternalData().getWorld().execute(()->{
                    if(error!=null){qaRejected(store,group,capturedBirth,prepared,"AFFIX_ATTACH",error,done);return;}
                    try{
                        prepared.activate(hit->{},rejected->qaActorFailure(capturedBirth,rejected));
                        publication.publishQa(store,selected,prepared);
                        qaEncounters.publish(capturedBirth,prepared);
                        var nativeWorld=store.getExternalData().getWorld();
                        nativeWorld.execute(()->{
                            var current=nativeWorld.getEntityStore().getStore();
                            for(var actor:capturedBirth.actors()){
                                var nativeRef=current.getExternalData().getRefFromUUID(actor.entityId());
                                combat.verifyQaNativeHealthbar(current,nativeRef,actor.entityId());
                            }
                            QaNativeFlocks.inspect(current,capturedBirth);
                        });
                        done.complete(new QaSpawnResult(capturedBirth.encounter(),actual));
                    }catch(RuntimeException failure){qaRejected(store,group,capturedBirth,prepared,"PUBLISH",failure,done);}
                });}catch(RuntimeException closing){qaRejected(store,group,capturedBirth,prepared,"WORLD_QUEUE",closing,done);}
            });
        }catch(RuntimeException failure){qaRejected(store,group,birth,null,"AFFIX_ATTACH",failure,done);}
        return done.minimalCompletionStage();
    }
    private void qaActorFailure(EnemyBirthPlan birth,Throwable failure){
        com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                "RPG_ENEMY_QA_ACTION_FAILED encounter=%s reason=%s",birth.encounter(),rootReason(failure));
    }
    private static String rootReason(Throwable failure){
        var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        for(int i=0;i<16&&failure.getCause()!=null&&seen.add(failure);i++)failure=failure.getCause();
        String message=String.valueOf(failure.getMessage());
        return failure.getClass().getSimpleName()+":"
                +(message.length()>240?message.substring(0,240):message);
    }
    private void qaRejected(Store<EntityStore> store,NativeEnemySpawnGroups.Group group,EnemyBirthPlan birth,
            NativeEnemyBirthAttachment.Prepared prepared,String stage,Throwable failure,
            java.util.concurrent.CompletableFuture<QaSpawnResult> done){
        com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                "RPG_ENEMY_QA_SPAWN_FAILED stage=%s encounter=%s reason=%s",
                stage,group.reservation().encounter(),rootReason(failure));
        try{
            if(prepared!=null)try{prepared.close();}catch(Exception cleanup){/* Continue local teardown. */}
            if(birth!=null){
                qaEncounters.discard(birth);
                for(var actor:birth.actors())try{combat.detach(actor.worldId(),actor.entityId());}
                catch(RuntimeException cleanup){/* Continue removing the remaining QA actors. */}
            }
            discardQaActors(store,group.members(),group.reservation().encounter());
        }catch(RuntimeException cleanup){
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                    "RPG_ENEMY_QA_CLEANUP_FAILED encounter=%s reason=%s",
                    group.reservation().encounter(),rootReason(cleanup));
        }finally{done.completeExceptionally(failure);}
    }
    private static void discardQaActors(Store<EntityStore> store,List<NativeEnemySpawnGroups.Member> members,UUID encounter){
        for(var member:members){
            try{
                var ref=store.getExternalData().getRefFromUUID(member.entity());
                if(ref==null||!ref.isValid())continue;
                var marker=store.getComponent(ref,QaTransientMarker.getComponentType());
                var staged=store.getComponent(ref,EnemyStaging.getComponentType());
                if(marker!=null&&marker.encounter().equals(encounter)&&marker.actor().equals(member.entity())
                        ||staged!=null&&staged.state().encounter().equals(encounter)
                        &&staged.state().entity().equals(member.entity()))
                    store.removeEntity(ref,RemoveReason.REMOVE);
            }catch(RuntimeException cleanup){
                com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                        "RPG_ENEMY_QA_CLEANUP_FAILED actor=%s reason=%s",member.entity(),rootReason(cleanup));
            }
        }
    }
    private void processBirth(Store<EntityStore> store,NativeEnemySpawnGroups.Group group,
            java.util.concurrent.CompletionStage<Optional<NativeEnemyBirthReservation.Sealed>> start,
            java.util.concurrent.CompletableFuture<UUID> done){
        var world=store.getExternalData().getWorld();
        try{start.whenComplete((maybe,error)->onWorld(world,group.job().world(),()->{
            if(error!=null){fail(group.job().world(),"RESERVE",error);if(done!=null)done.completeExceptionally(error);return;}
            if(maybe.isEmpty()){
                MonsterSpawnTrace.event("ELITE_DECLINED_NATIVE_CONTINUES",group.job().world(),group.job().environment(),group.job().nativeRole(),
                        "job="+group.job().nativeJobId()+" encounter="+group.reservation().encounter()
                                +" originals="+group.members().size());
                if(done!=null){discardQaStaged(world.getEntityStore().getStore(),group);
                    done.completeExceptionally(new IllegalStateException("ENEMY_QA_CAPACITY_UNAVAILABLE"));}
                return; // Natural decision/capacity owner has restored its ordinary native group.
            }
            var sealed=maybe.get();
            try{attachment.prepare(world.getEntityStore().getStore(),sealed).whenComplete((prepared,attachError)->
                    onWorld(world,group.job().world(),()->{
                        if(attachError!=null){compensate(world.getEntityStore().getStore(),sealed,attachError);
                            if(done!=null)done.completeExceptionally(attachError);return;}
                        try{
                            prepared.activate(hit->{},rejected->fail(group.job().world(),"ACTION",rejected));
                        }catch(RuntimeException activationFailure){
                            try{prepared.close();}catch(Exception cleanup){activationFailure.addSuppressed(cleanup);}
                            compensate(world.getEntityStore().getStore(),sealed,activationFailure);
                            if(done!=null)done.completeExceptionally(activationFailure);return;
                        }
                        try{publication.publish(world.getEntityStore().getStore(),sealed,prepared)
                                .whenComplete((ignored,publishError)->onWorld(world,group.job().world(),()->{
                                    if(publishError!=null){
                                        try{stagePrepared(world.getEntityStore().getStore(),prepared);}
                                        catch(RuntimeException staging){publishError.addSuppressed(staging);}
                                        try{prepared.close();}catch(Exception cleanup){publishError.addSuppressed(cleanup);}
                                        fail(group.job().world(),"PUBLISH",publishError);
                                        if(done!=null)done.completeExceptionally(publishError);return;
                                    }
                                    try{registerPublished(world.getEntityStore().getStore(),prepared,
                                            sealed.selected().root().plan().pack().staged().publish());
                                        MonsterSpawnTrace.event("ELITE_PUBLISHED",group.job().world(),group.job().environment(),group.job().nativeRole(),
                                                "job="+group.job().nativeJobId()+" encounter="+sealed.selected().root().encounter()
                                                        +" actors="+prepared.active().size());
                                        if(done!=null)done.complete(sealed.selected().root().encounter());}
                                    catch(RuntimeException lifetimeFailure){
                                        try{stagePrepared(world.getEntityStore().getStore(),prepared);}
                                        catch(RuntimeException staging){lifetimeFailure.addSuppressed(staging);}
                                        try{prepared.close();}catch(Exception cleanup){lifetimeFailure.addSuppressed(cleanup);}
                                        fail(group.job().world(),"LIFETIME",lifetimeFailure);
                                        if(done!=null)done.completeExceptionally(lifetimeFailure);
                                    }
                                }));}
                        catch(RuntimeException publishRejection){
                            try{stagePrepared(world.getEntityStore().getStore(),prepared);}
                            catch(RuntimeException staging){publishRejection.addSuppressed(staging);}
                            try{prepared.close();}catch(Exception cleanup){publishRejection.addSuppressed(cleanup);}
                            fail(group.job().world(),"PUBLISH",publishRejection);
                            if(done!=null)done.completeExceptionally(publishRejection);
                        }
                    }));}
            catch(RuntimeException attachRejection){compensate(world.getEntityStore().getStore(),sealed,attachRejection);
                if(done!=null)done.completeExceptionally(attachRejection);}
        }));}catch(RuntimeException rejection){fail(group.job().world(),"RESERVE",rejection);
            if(done!=null)done.completeExceptionally(rejection);throw rejection;}
    }

    private void compensate(Store<EntityStore> store,NativeEnemyBirthReservation.Sealed sealed,Throwable cause){
        var selected=sealed.selected();var root=selected.root();
        try{rewards.compensateStagedNativeGroup(store,selected.original(),selected.additional(),root)
                .whenComplete((ignored,error)->{
                    if(error!=null){error.addSuppressed(cause);fail(root.world(),"COMPENSATE",error);return;}
                    if(root.plan().actors().stream().allMatch(actor->actor.spawnOrigin()==EnemyRewardContext.Origin.QA)){
                        var world=store.getExternalData().getWorld();
                        onWorld(world,root.world(),()->{
                            for(var member:selected.original().members()){
                                var ref=world.getEntityStore().getStore().getExternalData().getRefFromUUID(member.entity());
                                if(ref!=null&&ref.isValid())world.getEntityStore().getStore().removeEntity(ref,RemoveReason.REMOVE);
                            }
                        });
                    }
                });}
        catch(RuntimeException rejected){rejected.addSuppressed(cause);fail(root.world(),"COMPENSATE",rejected);}
    }
    private void discardQaStaged(Store<EntityStore> store,NativeEnemySpawnGroups.Group group){
        if(!store.isInThread())throw new IllegalStateException("ENEMY_QA_DISCARD_WORLD_THREAD");
        for(var member:group.members()){
            var ref=store.getExternalData().getRefFromUUID(member.entity());
            if(ref!=null&&ref.isValid()){
                var marker=store.getComponent(ref,EnemyStaging.getComponentType());
                if(marker==null||!marker.state().equals(member.staging()))
                    throw new IllegalStateException("ENEMY_QA_DISCARD_IDENTITY_CHANGED");
                store.removeEntity(ref,RemoveReason.REMOVE);
            }
        }
    }

    private void onWorld(com.hypixel.hytale.server.core.universe.world.World world,UUID id,Runnable action){
        var guarded=new Runnable(){@Override public void run(){
            try{action.run();}
            catch(RuntimeException|Error failure){fail(id,"WORLD_CALLBACK",failure);}
        }};
        try{
            // Publication completes on the world thread. Register its lifetime before a later native removal.
            if(world.getEntityStore().getStore().isInThread())guarded.run();
            else world.execute(guarded);
        }
        catch(RuntimeException queueFailure){
            fail(id,"WORLD_QUEUE",queueFailure);
        }
    }

    void fail(UUID world,String phase,Throwable error){
        admission.failClosed(world);
        combat.quarantineEnemyWorld(world);
        actions.quarantineWorld(world);
        var nativeWorld=com.hypixel.hytale.server.core.universe.Universe.get().getWorld(world);
        if(nativeWorld!=null){
            var pause=new Runnable(){@Override public void run(){
                var store=nativeWorld.getEntityStore().getStore();
                List<Map.Entry<UUID,NativeEnemyBirthAttachment.Prepared>> targets;
                synchronized(NativeEnemyBirthOwner.this){targets=List.copyOf(liveActors.entrySet());}
                for(var entry:targets){
                    var birth=entry.getValue().birth();if(!birth.world().equals(world))continue;
                    var actor=birth.actors().stream().filter(a->a.entityId().equals(entry.getKey())).findFirst().orElse(null);
                    if(actor==null)continue;
                    var ref=store.getExternalData().getRefFromUUID(actor.entityId());
                    if(ref==null||!ref.isValid())continue;
                    try{
                        var identity=store.getComponent(ref,EnemyActorIdentity.getComponentType());
                        if(identity==null||!identity.state().equals(EnemyActorIdentity.State.of(actor)))
                            throw new IllegalStateException("ENEMY_QUARANTINE_ACTOR_IDENTITY");
                        var existing=store.getComponent(ref,EnemyStaging.getComponentType());
                        EnemyStaging.prepareExisting(store,ref,world,birth.encounter(),birth.generation(),
                                existing!=null?existing.state().additional():
                                        !birth.originalNativeEntities().contains(actor.entityId()));
                    }catch(RuntimeException stagingFailure){
                        com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(stagingFailure)
                                .log("RPG_ENEMY_QUARANTINE_STAGING_FAILED world=%s entity=%s",world,actor.entityId());
                    }
                }
            }};
            try{if(nativeWorld.getEntityStore().getStore().isInThread())pause.run();else nativeWorld.execute(pause);}
            catch(RuntimeException queueFailure){
                com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(queueFailure)
                        .log("RPG_ENEMY_QUARANTINE_QUEUE_FAILED world=%s",world);
            }
        }
        com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(error)
                .log("RPG_ENEMY_BIRTH_FAILED phase=%s world=%s admission=false",phase,world);
    }

    /** Covers the interval after native release but before the lifetime map accepts the group. */
    void stagePrepared(Store<EntityStore> store,NativeEnemyBirthAttachment.Prepared prepared){
        var birth=prepared.birth();
        if(!store.isInThread()||!store.getExternalData().getWorld().getWorldConfig().getUuid().equals(birth.world()))
            throw new IllegalStateException("ENEMY_QUARANTINE_PREPARED_WORLD");
        RuntimeException failures=null;
        for(var actor:prepared.active())try{
            var ref=store.getExternalData().getRefFromUUID(actor.entityId());
            if(ref==null||!ref.isValid())continue;
            var identity=store.getComponent(ref,EnemyActorIdentity.getComponentType());
            if(identity==null||!identity.state().equals(EnemyActorIdentity.State.of(actor)))
                throw new IllegalStateException("ENEMY_QUARANTINE_PREPARED_IDENTITY");
            var existing=store.getComponent(ref,EnemyStaging.getComponentType());
            boolean additional=existing!=null?existing.state().additional():
                    !birth.originalNativeEntities().contains(actor.entityId());
            EnemyStaging.prepareExisting(store,ref,birth.world(),birth.encounter(),birth.generation(),additional);
        }catch(RuntimeException failure){
            if(failures==null)failures=failure;else failures.addSuppressed(failure);
        }
        if(failures!=null)throw failures;
    }

    /** A later chunk LOAD may rebind one surviving member while its packmates remain live. */
    public synchronized void registerPublished(Store<EntityStore> store,NativeEnemyBirthAttachment.Prepared prepared,
            EnemyPackRecord published){
        if(!store.isInThread()||!store.getExternalData().getWorld().getWorldConfig().getUuid()
                .equals(prepared.birth().world())||prepared.active().isEmpty())
            throw new IllegalStateException("ENEMY_BIRTH_LIFETIME_WORLD");
        var loaded=new ArrayList<EnemyDescriptor>();
        for(var actor:prepared.active()){
            var ref=store.getExternalData().getRefFromUUID(actor.entityId());
            if(ref==null||!ref.isValid())continue; // Unloaded after the durable publish callback.
            var identity=store.getComponent(ref,EnemyActorIdentity.getComponentType());
            if(identity==null||!identity.state().equals(EnemyActorIdentity.State.of(actor)))
                throw new IllegalStateException("ENEMY_BIRTH_LIFETIME_IDENTITY");
            if(liveActors.containsKey(actor.entityId()))throw new IllegalStateException("ENEMY_BIRTH_DUPLICATE_LIVE_ACTOR");
            loaded.add(actor);
        }
        if(liveActors.size()+loaded.size()>4096)throw new IllegalStateException("ENEMY_BIRTH_LIFETIME_CAPACITY");
        for(var actor:prepared.active())if(!loaded.contains(actor))try{prepared.retireActor(actor.entityId());}
        catch(Exception failure){throw new IllegalStateException("ENEMY_BIRTH_UNLOADED_LIFETIME",failure);}
        admission.activatePublished(prepared.birth(),published,loaded.stream().map(EnemyDescriptor::entityId).toList());
        for(var actor:loaded)liveActors.put(actor.entityId(),prepared);
        if(prepared.allRetired())try{prepared.close();}
        catch(Exception failure){throw new IllegalStateException("ENEMY_BIRTH_EMPTY_LIFETIME_CLOSE",failure);}
    }
    public synchronized boolean bound(UUID nativeEntity){return liveActors.containsKey(nativeEntity);}

    /** Remove only loaded QA-spawned actors through the existing native removal owner. */
    public synchronized int clearQa(Store<EntityStore> store) {
        if (!store.isInThread()) throw new IllegalStateException("ENEMY_QA_CLEAR_WRITER_REQUIRED");
        UUID world = store.getExternalData().getWorld().getWorldConfig().getUuid();
        var ids = qaEncounters.loadedActors(world);
        int removed = 0;
        for (UUID id : ids) {
            var ref = store.getExternalData().getRefFromUUID(id);
            if (ref != null && ref.isValid()) {
                store.removeEntity(ref, RemoveReason.REMOVE);
                removed++;
            }
        }
        return removed;
    }

    private synchronized void removed(Store<EntityStore> store,EnemyActorIdentity.State identity,RemoveReason reason){
        if(!store.isInThread())throw new IllegalStateException("ENEMY_BIRTH_REMOVE_WORLD_THREAD");
        if(qaEncounters.contains(identity.world(),identity.nativeEntity())){
            var pack=qaEncounters.pack(identity.world(),identity.nativeEntity());
            boolean defeated=pack!=null&&pack.deadMemberReceipts().containsKey(identity.logicalActor());
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log(
                    "RPG_ENEMY_QA_ACTOR_REMOVED actor=%s encounter=%s reason=%s defeated=%s",
                    identity.nativeEntity(),identity.encounter(),reason,defeated);
            try{qaEncounters.retire(store,identity.nativeEntity());}
            catch(Exception local){com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                    "RPG_ENEMY_QA_RETIRE_FAILED actor=%s reason=%s",identity.nativeEntity(),rootReason(local));}
            if(!defeated&&pack!=null){
                var world=store.getExternalData().getWorld();
                world.execute(()->{
                    var current=world.getEntityStore().getStore();
                    for(var member:pack.birthRoster()){
                        var ref=current.getExternalData().getRefFromUUID(member.nativeEntityId());
                        if(ref!=null&&ref.isValid()&&current.getComponent(ref,QaTransientMarker.getComponentType())!=null)
                            current.removeEntity(ref,RemoveReason.REMOVE);
                    }
                });
            }
            return;
        }
        var prepared=liveActors.get(identity.nativeEntity());if(prepared==null)return;
        var birth=prepared.birth();
        if(!birth.world().equals(identity.world())||!birth.encounter().equals(identity.encounter())
                ||birth.generation()!=identity.generation()
                ||birth.actors().stream().noneMatch(actor->EnemyActorIdentity.State.of(actor).equals(identity))){
            fail(identity.world(),"REMOVE_IDENTITY",new IllegalStateException("ENEMY_BIRTH_REMOVE_GENERATION"));return;
        }
        if(reason==RemoveReason.UNLOAD){
            try{
                var pack=combat.enemyPack(identity.world(),identity.nativeEntity()).orElse(null);
                if(pack==null){
                    fail(identity.world(),"UNLOAD_PACK_MISSING",new IllegalStateException("ENEMY_UNLOAD_PACK_NOT_BOUND"));
                }else if(pack.state()==EnemyPackRecord.State.GUARDED||pack.state()==EnemyPackRecord.State.RELEASED){
                    var suspended=pack.suspend();
                    // Close action/protection admission for surviving loaded packmates on this world thread.
                    combat.refreshEnemyPack(store,suspended);
                    admission.beginSuspension(birth);
                    try{rewards.transitionEnemyPack(identity.world(),identity.pack(),current->{
                        if(!current.worldId().equals(identity.world())||current.generation()!=identity.generation()
                                ||!current.encounterId().equals(identity.encounter())
                                ||!current.deadMemberReceipts().equals(pack.deadMemberReceipts()))
                            throw new IllegalStateException("ENEMY_UNLOAD_PACK_CHANGED");
                        return current.state()==EnemyPackRecord.State.SUSPENDED?current:current.suspend();
                    }).whenComplete((persisted,error)->{
                        if(error!=null){fail(identity.world(),"UNLOAD_SUSPEND",error);return;}
                        var world=store.getExternalData().getWorld();
                        onWorld(world,identity.world(),()->admission.finishSuspension(birth,persisted));
                    });}catch(RuntimeException rejected){fail(identity.world(),"UNLOAD_SUSPEND",rejected);}
                }
            }catch(RuntimeException suspensionFailure){fail(identity.world(),"UNLOAD_PROTECTION",suspensionFailure);}
        }
        try{
            prepared.retireActor(identity.nativeEntity());
            liveActors.remove(identity.nativeEntity());
            admission.memberRemoved(birth,identity.nativeEntity(),reason.name());
            if(prepared.allRetired())prepared.close();
        }catch(Exception failure){fail(identity.world(),"REMOVE_LIFETIME",failure);}
    }

    /** Native removal/unload is a lifetime event, never a defeat or compensation decision. */
    public static final class Removal extends RefSystem<EntityStore> {
        private final NativeEnemyBirthOwner owner;
        public Removal(NativeEnemyBirthOwner owner){this.owner=Objects.requireNonNull(owner);}
        @Override public Query<EntityStore> getQuery(){return EnemyActorIdentity.getComponentType();}
        /** Suspend a live pack before encounter tracking detaches its native actor context. */
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
                new SystemDependency<>(Order.BEFORE,HytaleEncounterRewards.Tracking.class));}
        @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,
                CommandBuffer<EntityStore> buffer){}
        @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,
                CommandBuffer<EntityStore> buffer){
            var identity=store.getComponent(ref,EnemyActorIdentity.getComponentType());
            if(identity!=null){
                var current=store.getExternalData().getRefFromUUID(identity.state().nativeEntity());
                if(current!=null&&current.isValid()&&!current.equals(ref))return;
                owner.removed(store,identity.state(),reason);
            }
        }
    }
}
