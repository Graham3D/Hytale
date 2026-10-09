package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry;
import java.util.*;

/** Immutable transaction source: the selected birth and the native contexts it can restore. */
public record EnemyBirthRoot(EnemyBirthPlan plan,List<EnemyRewardRegistry.Spawn> originalSpawns){
    public EnemyBirthRoot{
        Objects.requireNonNull(plan);originalSpawns=List.copyOf(originalSpawns);
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
    public UUID world(){return plan.world();}
    public UUID encounter(){return plan.encounter();}
}
