package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.execution.hytale.NativeEnemySpawnGroups;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry;
import com.inigmasgames.hytalerpg.progress.RewardIntent;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

/** Joins one captured native flock with existing authored classifier outputs; no spawn or IO. */
public final class EnemyNativeGroupPreparation {
    public record MemberSource(EnemyRewardRegistry.Spawn spawn,String nativeName){
        public MemberSource{
            Objects.requireNonNull(spawn);Objects.requireNonNull(nativeName);
            if(nativeName.isBlank())
                throw new IllegalArgumentException("ENEMY_NATIVE_MEMBER_SOURCE");
        }
    }
    private EnemyNativeGroupPreparation(){}

    /** An empty result restores the untouched native group before any birth root is written. */
    public static Optional<EnemyBirthPlanner.Request> prepare(NativeEnemySpawnGroups.Group group,
            List<MemberSource> sources,Function<String,Optional<EnemyNativeBindings.Role>> roles,
            Vec3 anchor,EnemyBalance balance,String bindingRevision,boolean specialPackCapacity){
        Objects.requireNonNull(group);Objects.requireNonNull(sources);Objects.requireNonNull(roles);
        Objects.requireNonNull(anchor);Objects.requireNonNull(balance);Objects.requireNonNull(bindingRevision);
        var members=group.members();
        if(group.nativeFailed()||members.isEmpty()||!members.getFirst().nativeRole().equals(group.job().nativeRole())
                ||members.size()!=group.job().expectedMembers()
                ||members.size()!=sources.size()||members.size()>8)return Optional.empty();
        var reservation=group.reservation();
        if(!reservation.world().equals(group.job().world())||!Double.isFinite(anchor.x())
                ||!Double.isFinite(anchor.y())||!Double.isFinite(anchor.z()))return Optional.empty();
        var captured=new HashSet<UUID>();var candidates=new ArrayList<EnemyBirthPlanner.Candidate>();
        String cycle="native-world-job/"+group.job().nativeJobId()+"/"+members.getFirst().entity();
        for(int index=0;index<members.size();index++){
            var member=members.get(index);var source=sources.get(index);var spawn=source.spawn();
            if(!captured.add(member.entity())||!member.staging().entity().equals(member.entity())
                    ||!member.staging().world().equals(reservation.world())
                    ||!member.staging().encounter().equals(reservation.encounter())
                    ||member.staging().generation()!=reservation.generation()
                    ||!spawn.world().equals(reservation.world())||!spawn.enemy().equals(member.entity())
                    ||!spawn.roleId().equals(member.nativeRole()))return Optional.empty();
            var role=roles.apply(member.nativeRole()).orElse(null);
            if(role==null||!role.productionPromotionEnabled())return Optional.empty();
            try{
                candidates.add(role.baseline(spawn,reservation.encounter(),reservation.generation(),cycle,
                        source.nativeName(),balance,bindingRevision));
            }catch(IllegalArgumentException|IllegalStateException rejected){return Optional.empty();}
        }
        var source=new StringBuilder("me.native-group/").append(group.job().world()).append('/')
                .append(group.job().nativeJobId()).append('/').append(group.job().roleIndex()).append('/')
                .append(group.job().nativeRole()).append('/')
                .append(group.job().environment()).append('/').append(group.job().spawnConfiguration());
        for(var member:members)source.append('/').append(member.entity());
        String seed=RewardIntent.digest(source.toString());
        UUID pack=UUID.nameUUIDFromBytes(("me.pack/"+seed).getBytes(StandardCharsets.UTF_8));
        return Optional.of(new EnemyBirthPlanner.Request(seed,pack,anchor,"native-job/"+seed,
                candidates,List.of(),true,0,specialPackCapacity));
    }
    /** Join only native actors added to this same flock after the deterministic Unique demand preview. */
    public static Optional<EnemyBirthPlanner.Request> completeAdditional(NativeEnemySpawnGroups.Group originalGroup,
            EnemyBirthPlanner.Request original,NativeEnemySpawnGroups.Group added,List<MemberSource> addedSources,
            Function<String,Optional<EnemyNativeBindings.Role>> roles,EnemyBalance balance,String bindingRevision){
        Objects.requireNonNull(originalGroup);Objects.requireNonNull(original);Objects.requireNonNull(added);
        Objects.requireNonNull(addedSources);Objects.requireNonNull(roles);Objects.requireNonNull(balance);
        if(added.nativeFailed()||added.members().isEmpty()||added.members().size()!=added.job().expectedMembers()
                ||added.members().size()!=addedSources.size()
                ||original.originals().size()+added.members().size()>8
                ||!originalGroup.reservation().equals(added.reservation())
                ||!originalGroup.job().world().equals(added.job().world())
                ||originalGroup.job().nativeJobId()!=added.job().nativeJobId()
                ||originalGroup.job().roleIndex()!=added.job().roleIndex()
                ||!originalGroup.job().nativeRole().equals(added.job().nativeRole())
                ||originalGroup.job().environment()!=added.job().environment()
                ||originalGroup.job().spawnConfiguration()!=added.job().spawnConfiguration())return Optional.empty();
        var existing=new HashSet<UUID>();
        for(var candidate:original.originals())existing.add(candidate.nativeBaseline().entityId());
        var extra=new ArrayList<EnemyBirthPlanner.Candidate>();
        var baseline=original.originals().getFirst().nativeBaseline();
        for(int index=0;index<added.members().size();index++){
            var member=added.members().get(index);var source=addedSources.get(index);var spawn=source.spawn();
            if(!existing.add(member.entity())||!member.staging().entity().equals(member.entity())
                    ||!member.staging().world().equals(originalGroup.reservation().world())
                    ||!member.staging().encounter().equals(originalGroup.reservation().encounter())
                    ||member.staging().generation()!=originalGroup.reservation().generation()
                    ||!spawn.world().equals(member.staging().world())||!spawn.enemy().equals(member.entity())
                    ||!spawn.roleId().equals(member.nativeRole()))
                return Optional.empty();
            var role=roles.apply(member.nativeRole()).orElse(null);
            if(role==null||!role.productionPromotionEnabled())return Optional.empty();
            try{extra.add(role.baseline(spawn,baseline.encounterId(),baseline.encounterGeneration(),
                    baseline.spawnCycleId(),source.nativeName(),balance,bindingRevision));}
            catch(IllegalArgumentException|IllegalStateException rejected){return Optional.empty();}
        }
        try{return Optional.of(new EnemyBirthPlanner.Request(original.seed(),original.packId(),original.anchor(),
                original.nativePlanReceipt(),original.originals(),extra,true,extra.size(),original.specialPackCapacity()));}
        catch(IllegalArgumentException rejected){return Optional.empty();}
    }
}
