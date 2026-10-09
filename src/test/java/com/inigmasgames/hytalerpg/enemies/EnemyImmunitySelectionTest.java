package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyImmunitySelectionTest {
    private final EnemyImmunitySelection selection=new EnemyImmunitySelection(EnemyBalance.canonical(),EnemyAffinityRegistry.canonical());
    private EnemyImmunitySelection.Result resolve(String role,EnemyRarity rarity,DifficultyId mode,String affix,String seed){
        return selection.select(role,rarity,mode,false,Set.of(),Set.of(),affix==null?List.of():List.of(new EnemyAffixSelection.Choice(affix,null)),true,false,false,true,seed);
    }
    @Test void normalHasFloorsButNoNewImmunityAndOnlyExactRolesMatch(){
        var ordinary=resolve("Golem_Crystal_Flame",EnemyRarity.NORMAL,DifficultyId.NORMAL,null,"one");
        assertEquals(Map.of("FIRE",.2),ordinary.affinityFloors());assertTrue(ordinary.active().isEmpty());
        assertTrue(resolve("Golem_Crystal_Flame_Fake",EnemyRarity.NORMAL,DifficultyId.HELL,null,"one").affinityFloors().isEmpty());
        var boss=selection.select("Golem_Crystal_Flame",EnemyRarity.BOSS,DifficultyId.HELL,true,Set.of("FIRE"),Set.of(),List.of(),false,false,false,true,"one");
        assertEquals(Set.of("FIRE"),boss.active());assertTrue(boss.affinityFloors().isEmpty());
    }
    @Test void hellAffinityIsGuaranteedAndDuplicateAffixCandidateDoesNotReroll(){
        var same=resolve("Golem_Crystal_Flame",EnemyRarity.UNIQUE,DifficultyId.HELL,"ME-005","one");
        assertEquals(Set.of("FIRE"),same.active());assertEquals(1,same.selected().size());
        assertEquals(EnemyImmunitySelection.Reason.ALREADY_IMMUNE,same.decisions().getLast().reason());
        assertNull(same.decisions().getLast().draw());
        boolean second=false;
        for(int i=0;i<50;i++){
            var value=resolve("Golem_Crystal_Flame",EnemyRarity.UNIQUE,DifficultyId.HELL,"ME-006","seed"+i);
            assertEquals(value,resolve("Golem_Crystal_Flame",EnemyRarity.UNIQUE,DifficultyId.HELL,"ME-006","seed"+i));
            assertTrue(value.active().size()<=2);assertFalse(value.active().contains("PHYSICAL"));second|=value.active().size()==2;
        }
        assertTrue(second);
    }
    @Test void nightmareHasOneSlotAndNonCandidateAffixesCannotGrant(){
        for(int seed=0;seed<100;seed++)assertTrue(resolve("Spirit_Frost",EnemyRarity.UNIQUE,DifficultyId.NIGHTMARE,"ME-005","seed"+seed).active().size()<=1);
        for(String affix:List.of("ME-012","ME-003"))assertTrue(resolve("Trork_Warrior",EnemyRarity.UNIQUE,DifficultyId.HELL,affix,"one").active().isEmpty());
        assertTrue(resolve("Trork_Warrior",EnemyRarity.CHAMPION,DifficultyId.HELL,"ME-005","one").active().isEmpty());
    }
    @Test void packboundSafetyPreservesExplicitFlagsAndSkipsUnsafeNewGrants(){
        var result=selection.select("Spirit_Frost",EnemyRarity.SUPER_UNIQUE,DifficultyId.HELL,false,Set.of(),Set.of("FIRE"),
                List.of(new EnemyAffixSelection.Choice("ME-006",null)),false,true,false,true,"one");
        assertTrue(result.accepted());assertEquals(Set.of("FIRE"),result.active());
        assertTrue(result.decisions().stream().allMatch(d->d.reason()==EnemyImmunitySelection.Reason.SAFETY_REJECTED));
        assertFalse(selection.select("Trork_Warrior",EnemyRarity.NORMAL,DifficultyId.HELL,false,Set.of("PHYSICAL"),Set.of(),List.of(),false,false,true,true,"one").accepted());
        assertFalse(selection.select("Trork_Warrior",EnemyRarity.UNIQUE,DifficultyId.NORMAL,false,Set.of("FIRE"),Set.of(),List.of(),true,false,false,true,"one").accepted());
    }
    @Test void vampiricAndBulwarkSkipTheSecondGrantWithoutRerollingOrDroppingNativeFlags(){
        for(String survival:List.of("ME-016","ME-027"))for(int seed=0;seed<40;seed++){
            var affixes=List.of(new EnemyAffixSelection.Choice(survival,null),new EnemyAffixSelection.Choice("ME-005",null));
            var result=selection.select("Spirit_Frost",EnemyRarity.UNIQUE,DifficultyId.HELL,false,Set.of(),Set.of(),affixes,true,false,false,true,"seed/"+seed);
            assertTrue(result.accepted());assertEquals(Set.of("WATER"),result.active());
            assertEquals(EnemyImmunitySelection.Reason.SAFETY_REJECTED,result.decisions().getLast().reason());assertNull(result.decisions().getLast().draw());
            var invalid=selection.select("Spirit_Frost",EnemyRarity.UNIQUE,DifficultyId.HELL,false,Set.of("FIRE","WATER"),Set.of(),affixes,true,false,false,true,"seed/"+seed);
            assertFalse(invalid.accepted());assertEquals(Set.of("FIRE","WATER"),invalid.preservedNative());
        }
    }
    @Test void unknownFieldsMissingRolesAndUnprovenAuthoredImmunityReject(){
        var json=EnemyAffixRegistry.resource("affinities-v1.json");json.addProperty("guessFromRoleName",true);
        assertThrows(IllegalArgumentException.class,()->new EnemyAffinityRegistry(json));
        assertThrows(IllegalArgumentException.class,()->EnemyAffinityRegistry.canonical().validateRoles(role->false));
        assertFalse(selection.select("Trork_Warrior",EnemyRarity.SUPER_UNIQUE,DifficultyId.HELL,false,Set.of(),Set.of("FIRE"),List.of(),false,false,false,false,"one").accepted());
    }
}
