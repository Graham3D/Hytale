package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry;
import java.util.*;

/** Immutable transaction source: the selected birth and the native contexts it can restore. */
public record EnemyBirthRoot(EnemyBirthPlan plan,List<EnemyRewardRegistry.Spawn> originalSpawns,
        List<EnemyRewardRegistry.Spawn> attachmentSpawns){
    public EnemyBirthRoot(EnemyBirthPlan plan,List<EnemyRewardRegistry.Spawn> originalSpawns){this(plan,originalSpawns,List.of());}
    public EnemyBirthRoot{
        Objects.requireNonNull(plan);originalSpawns=List.copyOf(originalSpawns);
        attachmentSpawns=attachmentSpawns==null?List.of():List.copyOf(attachmentSpawns);
        if(!attachmentSpawns.isEmpty()){
            if(attachmentSpawns.size()!=plan.actors().size())throw new IllegalArgumentException("ENEMY_BIRTH_ROOT_ATTACHMENT_ROSTER");
            for(int i=0;i<attachmentSpawns.size();i++){
                var spawn=attachmentSpawns.get(i);var actor=plan.actors().get(i);
                if(!spawn.world().equals(plan.world())||!spawn.enemy().equals(actor.entityId())||spawn.combat()==null
                        ||!spawn.roleId().equals(actor.nativeRoleId())||spawn.level()!=actor.combatLevel()
                        ||spawn.combat().difficulty()!=actor.difficulty()||!spawn.registryProfile().equals(actor.sourceValidationId()))
                    throw new IllegalArgumentException("ENEMY_BIRTH_ROOT_ATTACHMENT_IDENTITY");
            }
        }
        if(plan.pack()==null||originalSpawns.size()!=plan.originalNativeEntities().size())
            throw new IllegalArgumentException("ENEMY_BIRTH_ROOT_SPECIAL_ROSTER");
        var byEntity=new HashMap<UUID,EnemyDescriptor>();
        for(var actor:plan.actors())byEntity.put(actor.entityId(),actor);
        for(int i=0;i<originalSpawns.size();i++){
            var spawn=originalSpawns.get(i);var actor=byEntity.get(spawn.enemy());
            if(!plan.world().equals(spawn.world())||!plan.originalNativeEntities().get(i).equals(spawn.enemy())
                    ||spawn.combat()==null||spawn.milestone()!=null
                    ||actor==null||!actor.nativeRoleId().equals(spawn.roleId())
                    ||!actor.encounterRank().equals(spawn.rank())
                    ||actor.combatLevel()!=spawn.level()||actor.difficulty()!=spawn.combat().difficulty()
                    ||!actor.sourceValidationId().equals(spawn.registryProfile())
                    ||(actor.spawnOrigin()==EnemyRewardContext.Origin.QA
                            ?!actor.immutableRewardContext().equals(spawn.enemyRewards())
                            :spawn.enemyRewards()!=null))
                throw new IllegalArgumentException("ENEMY_BIRTH_ROOT_NATIVE_CONTEXT");
        }
    }
    /** Legacy roots can recover only from an exact same-role frozen source, never current tuning. */
    public EnemyRewardRegistry.Spawn attachmentSpawn(EnemyDescriptor actor){
        if(!plan.actors().contains(actor))throw new IllegalArgumentException("ENEMY_BIRTH_ROOT_FOREIGN_ACTOR");
        if(!attachmentSpawns.isEmpty())return attachmentSpawns.get(plan.actors().indexOf(actor));
        var source=originalSpawns.stream().filter(s->s.roleId().equals(actor.nativeRoleId())
                &&s.level()==actor.combatLevel()&&s.rank()==actor.encounterRank()
                &&s.registryProfile().equals(actor.sourceValidationId())&&s.combat().difficulty()==actor.difficulty()).findFirst()
                .orElseThrow(()->new IllegalStateException("ENEMY_BIRTH_LEGACY_FROZEN_SOURCE_UNAVAILABLE:"+actor.nativeRoleId()));
        var c=source.combat();
        var combat=new com.inigmasgames.hytalerpg.difficulty.EncounterProfileResolver.Resolved(c.worldId(),actor.entityId(),c.difficulty(),
                c.profileId(),c.worldProfileId(),c.roleId(),c.biomeKey(),c.sourceCombatLevel(),c.maxHealth(),c.attackBasis(),
                c.difficultyHealthFactor(),c.difficultyDamageFactor(),c.resistance(),c.evidence(),c.progression());
        return new EnemyRewardRegistry.Spawn(source.world(),actor.entityId(),source.roleId(),source.combatIdentity(),source.biomeKey(),
                source.level(),source.rank(),source.rarity(),source.registryProfile(),source.spawnedAtMillis(),null,combat);
    }
    public UUID world(){return plan.world();}
    public UUID encounter(){return plan.encounter();}
}
