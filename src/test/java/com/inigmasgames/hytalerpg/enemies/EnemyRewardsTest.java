package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.progress.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyRewardContext.Origin.*;

class EnemyRewardsTest {
    @Test void rarityReplacesXpSlotAndInitialMinionsNeverInheritLeaderRewardCount(){
        var unique=EnemyRewardContext.canonical(EnemyRarity.UNIQUE,NATURAL,false,3);
        assertEquals(2.6,unique.xpFactor(),1e-12);assertEquals(1.95,unique.quantityFactor(),1e-12);
        var minion=EnemyRewardContext.canonical(EnemyRarity.NORMAL,INITIAL_PACK_MINION,true,0);
        assertEquals(1.05,minion.xpFactor());assertEquals(1,minion.quantityFactor());
        var old=EnemyRewardRegistry.load().classify(UUID.randomUUID(),UUID.randomUUID(),"Trork_Warrior","Default/Zone1_Tier1/Forest_Birch",EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,0).orElseThrow();
        var spawn=new EnemyRewardRegistry.Spawn(old.world(),old.enemy(),old.roleId(),old.combatIdentity(),old.biomeKey(),old.level(),old.rank(),
                ProgressionMath.Rarity.UNIQUE,old.registryProfile(),0,null,null,unique);
        assertEquals(ProgressionMath.enemyReward(old.level(),old.rank(),2.6,old.level()),spawn.rewardXp(old.level()));
        assertThrows(IllegalArgumentException.class,()->EnemyRewardContext.canonical(EnemyRarity.UNIQUE,INITIAL_PACK_MINION,true,3));
    }
    @Test void learningUsesRelativeBonusLimitedByRarityPointsAndNoAffixBonus(){
        var unique=EnemyRewardContext.canonical(EnemyRarity.UNIQUE,NATURAL,false,3);
        assertEquals(.2025,unique.learningChance(.15),1e-12);
        assertEquals(.61,unique.learningChance(.55),1e-12);
        assertEquals(unique.learningChance(.15),EnemyRewardContext.canonical(EnemyRarity.UNIQUE,NATURAL,false,1).learningChance(.15));
        var boss=EnemyRewardContext.canonical(EnemyRarity.BOSS,AUTHORED_ENCOUNTER,false,0);
        assertEquals(.95,boss.learningChance(.94));
        assertEquals(.005,EnemyRewardContext.canonical(EnemyRarity.NORMAL,INITIAL_PACK_MINION,true,0).learningChance(.55)-.55,1e-12);
    }
    @Test void quantityOnlyAddsSlotsToFinalizedBaseAndNeverRerollsNoDrop(){
        var champion=EnemyRewardContext.canonical(EnemyRarity.CHAMPION,NATURAL,false,1);
        int bonus=0;for(int i=0;i<1000;i++){
            String defeat="defeat/"+i;assertEquals(0,champion.bonusEquipmentSlots(0,defeat));
            int n=champion.bonusEquipmentSlots(1,defeat);assertTrue(n==0||n==1);bonus+=n;
            assertEquals(n,champion.bonusEquipmentSlots(1,defeat));
        }
        assertTrue(bonus>140&&bonus<260,"quantity distribution "+bonus);
        var unique=EnemyRewardContext.canonical(EnemyRarity.SUPER_UNIQUE,AUTHORED_ENCOUNTER,false,4);
        assertThrows(IllegalArgumentException.class,()->unique.bonusEquipmentSlots(16,"cap"));
        assertThrows(IllegalArgumentException.class,()->unique.bonusEquipmentSlots(15,"cap"));
        assertThrows(IllegalArgumentException.class,()->unique.validateEquipmentMaximum(7));
        unique.validateEquipmentMaximum(6);
        for(int i=0;i<50;i++){int n=unique.bonusEquipmentSlots(6,"cap/"+i);assertTrue(n==9||n==10);}
    }
    @Test void existingSourcePityThresholdStillGuaranteesTheNextRoll(){
        for(var rarity:ProgressionMath.AcquisitionRarity.values()){
            UUID player=UUID.randomUUID();var state=RpgPlayerState.create(player);
            state.acquisition=new AcquisitionProgress(Set.of(),Map.of("goblin_scrapper",ProgressionMath.pityFailures(rarity)),0);
            var op=new LearningSources.Opportunity("quick_slash","goblin_scrapper",rarity,0)
                    .withEnemyRewards(EnemyRewardContext.canonical(EnemyRarity.UNIQUE,NATURAL,false,3));
            var defeat=new EarnedReward("defeat",10,1,Map.of(),"ENEMY_DEATH","","","defeat");
            var outcome=op.decide(defeat,RewardCheckpoint.of(state),()->{fail("Pity must not consume another random draw");return 0;});
            assertEquals(ProgressionDelta.Kind.LEARNING_SUCCESS,outcome.progression().kind());
        }
    }
    @Test void qaCombatSummonedRevivedAndOwnedActorsCannotLearnEvenAtPity(){
        for(var origin:List.of(QA,COMBAT_SUMMON,REVIVED,PLAYER_OWNED)){
            var context=EnemyRewardContext.canonical(EnemyRarity.UNIQUE,origin,false,3);
            assertEquals(0,context.xpFactor());assertEquals(0,context.quantityFactor());assertEquals(0,context.learningChance(.95));
            var state=RpgPlayerState.create(UUID.randomUUID());state.acquisition=new AcquisitionProgress(Set.of(),Map.of("goblin_scrapper",150),0);
            // A valid existing award is a fixture for the learning decorator; QA never reaches reward delivery.
            var defeat=new EarnedReward("defeat",1,0,Map.of(),"LEARNING_DECORATOR_FIXTURE","","","defeat");
            assertSame(defeat,new LearningSources.Opportunity("quick_slash","goblin_scrapper",ProgressionMath.AcquisitionRarity.UNIQUE,0,context)
                    .decide(defeat,RewardCheckpoint.of(state),()->{fail("Ineligible source rolled");return 0;}));
        }
    }
}
