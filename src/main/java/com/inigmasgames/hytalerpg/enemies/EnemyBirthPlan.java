package com.inigmasgames.hytalerpg.enemies;

import java.util.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.require;

/** One immutable incoming-group decision; the encounter store publishes this before its lookup projections. */
public record EnemyBirthPlan(int schemaVersion,UUID world,UUID encounter,long generation,String seed,
        List<UUID> originalNativeEntities,List<EnemyDescriptor> actors,EnemyPackRecord pack,
        Map<UUID,Map<String,Double>> affinityFloors){
    public EnemyBirthPlan{
        Objects.requireNonNull(world);Objects.requireNonNull(encounter);
        require(schemaVersion==1&&generation>=0&&seed!=null&&!seed.isBlank()&&seed.length()<=512,"ENEMY_BIRTH_IDENTITY");
        originalNativeEntities=List.copyOf(originalNativeEntities);actors=List.copyOf(actors);
        require(!originalNativeEntities.isEmpty()&&originalNativeEntities.size()<=8&&actors.size()>=originalNativeEntities.size()&&actors.size()<=8,"ENEMY_BIRTH_SIZE");
        require(new HashSet<>(originalNativeEntities).size()==originalNativeEntities.size(),"ENEMY_BIRTH_DUPLICATE_ORIGINAL");
        var logical=new HashMap<UUID,EnemyDescriptor>();var nativeIds=new HashSet<UUID>();
        for(var actor:actors){
            require(actor.worldId().equals(world)&&actor.encounterId().equals(encounter)&&actor.encounterGeneration()==generation,"ENEMY_BIRTH_ACTOR_IDENTITY");
            require(logical.putIfAbsent(actor.logicalActorId(),actor)==null&&nativeIds.add(actor.entityId()),"ENEMY_BIRTH_DUPLICATE_ACTOR");
            require(Objects.equals(actor.packId(),pack==null?null:pack.packId()),"ENEMY_BIRTH_PACK_BINDING");
        }
        require(nativeIds.containsAll(originalNativeEntities),"ENEMY_BIRTH_DISCARDED_ORIGINAL");
        if(pack==null){
            require(actors.stream().allMatch(actor->actor.enemyRarity()==EnemyRarity.NORMAL||actor.enemyRarity()==EnemyRarity.BOSS)
                    &&actors.size()==originalNativeEntities.size(),"ENEMY_BIRTH_SPECIAL_REQUIRES_PACK");
        }else{
            require(pack.worldId().equals(world)&&pack.encounterId().equals(encounter)&&pack.generation()==generation
                    &&pack.state()==EnemyPackRecord.State.RESERVED&&pack.deadMemberReceipts().isEmpty(),"ENEMY_BIRTH_PACK_RESERVATION");
            require(pack.birthRoster().size()==actors.size(),"ENEMY_BIRTH_ROSTER_SIZE");
            for(var member:pack.birthRoster()){
                var actor=logical.get(member.logicalActorId());
                require(actor!=null&&actor.entityId().equals(member.nativeEntityId())&&actor.canonicalRoleId().equals(member.canonicalRoleId())
                        &&actor.packRole().name().equals(member.role().name())&&Objects.equals(actor.leaderId(),pack.leaderId()),"ENEMY_BIRTH_ROSTER_MISMATCH");
                if(pack.guardIds().contains(actor.logicalActorId()))require(!actor.activeImmuneChannels().contains("PHYSICAL"),"ENEMY_BIRTH_PHYSICAL_IMMUNE_GUARD");
            }
            if(pack.leaderId()==null){
                boolean qa=actors.stream().allMatch(actor->actor.spawnOrigin()==EnemyRewardContext.Origin.QA);
                require(actors.size()>=(qa?1:2)&&actors.size()<=4
                        &&actors.stream().allMatch(actor->actor.enemyRarity()==EnemyRarity.CHAMPION),"ENEMY_BIRTH_CHAMPIONS");
                var affixes=actors.getFirst().ownAffixes();
                require(actors.stream().allMatch(actor->actor.ownAffixes().equals(affixes))
                        &&(qa||affixes.size()==1),"ENEMY_BIRTH_CHAMPION_SHARED_AFFIX");
            }else{
                var leader=logical.get(pack.leaderId());
                require(leader!=null&&(leader.enemyRarity()==EnemyRarity.UNIQUE||leader.enemyRarity()==EnemyRarity.SUPER_UNIQUE),"ENEMY_BIRTH_LEADER_RARITY");
                require(actors.stream().filter(actor->actor!=leader).allMatch(actor->actor.packRole()==EnemyDescriptor.PackRole.MINION
                        &&(actor.spawnOrigin()==EnemyRewardContext.Origin.INITIAL_PACK_MINION||actor.spawnOrigin()==EnemyRewardContext.Origin.QA)),"ENEMY_BIRTH_MINION_PROVENANCE");
                require(leader.own(EnemyAffixRegistry.Operator.PACKBOUND).isPresent()==!pack.guardIds().isEmpty(),"ENEMY_BIRTH_GUARD_AFFIX_MISMATCH");
            }
        }
        var frozen=new TreeMap<UUID,Map<String,Double>>();
        affinityFloors.forEach((actor,values)->{
            require(logical.containsKey(actor),"ENEMY_BIRTH_FOREIGN_AFFINITY");var channels=new TreeMap<String,Double>();
            values.forEach((channel,value)->{
                require(EnemyAffixRegistry.CHANNELS.subList(1,7).contains(channel)&&value!=null&&Double.isFinite(value)&&value>=0&&value<=.75,"ENEMY_BIRTH_AFFINITY_FLOOR");
                channels.put(channel,value);
            });frozen.put(actor,Collections.unmodifiableMap(channels));
        });
        require(frozen.keySet().equals(logical.keySet()),"ENEMY_BIRTH_AFFINITY_SNAPSHOT_MISSING");affinityFloors=Collections.unmodifiableMap(frozen);
    }
    /** The durable death receipts, never ECS absence, choose the actors that may rebind after LOAD. */
    public List<EnemyDescriptor> activeActors(EnemyPackRecord current){
        require(pack!=null&&current!=null&&current.worldId().equals(world)
                &&current.encounterId().equals(encounter)&&current.packId().equals(pack.packId())
                &&current.generation()==generation&&current.birthRoster().equals(pack.birthRoster()),
                "ENEMY_BIRTH_ACTIVE_PACK_IDENTITY");
        var active=actors.stream().filter(actor->!current.deadMemberReceipts()
                .containsKey(actor.logicalActorId())).toList();
        require(!active.isEmpty()||current.state()==EnemyPackRecord.State.DEFEATED,
                "ENEMY_BIRTH_NONTERMINAL_EMPTY_ROSTER");
        return active;
    }
    public List<EnemyDescriptor> activeSubset(EnemyPackRecord current,List<EnemyDescriptor> proposed){
        Objects.requireNonNull(proposed);
        var active=activeActors(current);
        require(!proposed.isEmpty()&&proposed.size()<=active.size()
                &&active.stream().filter(proposed::contains).toList().equals(proposed),
                "ENEMY_BIRTH_ACTIVE_SUBSET");
        return List.copyOf(proposed);
    }
}
