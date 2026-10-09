package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.progress.ProgressionMath;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyQaSpawnRequestTest {
    @Test void eraCountsFollowAuthoredRarityAndSuperUniqueTemplate(){
        var balance=EnemyBalance.canonical();
        assertEquals(1,EnemyQaSpawnRequest.parse("Skeleton_Fighter","unique","normal",List.of()).requiredAffixes(balance));
        assertEquals(2,EnemyQaSpawnRequest.parse("Skeleton_Fighter","unique","nightmare",List.of()).requiredAffixes(balance));
        assertEquals(3,EnemyQaSpawnRequest.parse("Skeleton_Fighter","unique","hell",List.of()).requiredAffixes(balance));
        assertEquals(2,EnemyQaSpawnRequest.parse("Trork_Warrior","superunique","normal",List.of()).requiredAffixes(balance));
        assertEquals(3,EnemyQaSpawnRequest.parse("Trork_Warrior","superunique","nightmare",List.of()).requiredAffixes(balance));
        assertEquals(4,EnemyQaSpawnRequest.parse("Trork_Warrior","superunique","hell",List.of()).requiredAffixes(balance));
    }
    @Test void allAliasesNormalizeAndDuplicatesAreRejected() {
        var aliases=EnemyQaSpawnRequest.canonicalAliases();
        assertEquals(27,aliases.size());
        for(int i=0;i<aliases.size();i++)
            assertEquals("ME-%03d".formatted(i+1),EnemyQaSpawnRequest.parse("Larva_Void","Unique",
                    List.of(aliases.get(i))).affixIds().getFirst());
        assertEquals(List.of("ME-005","ME-023"),EnemyQaSpawnRequest.parse("Larva_Void","superunique",
                List.of("FIRE","Aura")).affixIds());
        assertThrows(IllegalArgumentException.class,()->EnemyQaSpawnRequest.parse("Larva_Void","unique",
                List.of("fire","fireenchanted")));
        assertThrows(IllegalArgumentException.class,()->EnemyQaSpawnRequest.parse("Larva_Void","unique",
                List.of("invented")));
    }

    @Test void qaDescriptorCanFreezeNineDistinctAffixesWithoutChangingProductionCap() {
        var balance=EnemyBalance.canonical();var registry=EnemyAffixRegistry.canonical();
        var ids=EnemyQaSpawnRequest.parse("Larva_Void","unique",List.of("extrafast","extrastrong",
                "stoneskin","fireenchanted","manaburn","knockback","vampiric","reflective","bulwark")).affixIds();
        var own=ids.stream().map(id->EnemyDescriptor.ownInstance(registry.require(id),
                new EnemyAffixSelection.Choice(id,null),DifficultyId.NORMAL,"qa/"+id)).toList();
        var world=UUID.randomUUID();var encounter=UUID.randomUUID();var entity=UUID.randomUUID();var pack=UUID.randomUUID();
        var descriptor=new EnemyDescriptor(1,1,balance.revision(),"test-binding",world,encounter,1,entity,entity,
                "qa-command/test",EnemyRewardContext.Origin.QA,"Larva_Void","Larva_Void",50,DifficultyId.NORMAL,
                ProgressionMath.Rank.COMMON,EnemyRarity.UNIQUE,EnemyDescriptor.PackRole.LEADER,pack,entity,
                "test-profile","Empty",null,"QA Larva","qa-seed",null,own,List.of(),Set.of(),List.of(),
                "Template_Predator",balance.rewards(EnemyRarity.UNIQUE,EnemyRewardContext.Origin.QA,false,own.size()),
                "a".repeat(64),null);
        assertEquals(9,descriptor.ownAffixes().size());
        assertFalse(descriptor.immutableRewardContext().economic());
        assertEquals(0,descriptor.immutableRewardContext().xpFactor());
        assertThrows(IllegalArgumentException.class,()->new EnemyRewardContext(balance.revision(),EnemyRarity.UNIQUE,
                EnemyRewardContext.Origin.NATURAL,false,9,1,1,1,0,0,0));
    }
}
