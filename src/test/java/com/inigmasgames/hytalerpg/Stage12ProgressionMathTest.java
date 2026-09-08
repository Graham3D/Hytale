package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.progress.*;
import com.inigmasgames.hytalerpg.ui.CharacterXpProjectionService;
import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.inigmasgames.hytalerpg.progress.ProgressionMath.*;

class Stage12ProgressionMathTest {
    final CharacterXpProjectionService curve=new CharacterXpProjectionService();
    @Test void all99PublishedMasterRowsMatchExactly()throws Exception{
        try(var reader=new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/progression/master-xp-table.csv"),StandardCharsets.UTF_8))){
            var lines=reader.lines().skip(1).toList();assertEquals(99,lines.size());
            for(var line:lines){var values=line.split(",");int level=Integer.parseInt(values[0]);assertEquals(Long.parseLong(values[1]),curve.xpToNext(level),"next:"+level);
                assertEquals(Long.parseLong(values[2]),curve.levelStartXp(level),"total:"+level);assertEquals(Long.parseLong(values[3]),commonReward(level),"reward:"+level);}
        }
    }
    @Test void lateGamePressureOnlyInRequirementNotReward(){assertEquals(920690,curve.xpToNext(98));assertEquals(4795,commonReward(98));assertEquals(192.01,(double)curve.xpToNext(98)/commonReward(98),.001);}
    @Test void interpolatedKAnchorsAndMidpoints(){assertEquals(10,killsScale(1));assertEquals(10,killsScale(20));assertEquals(12,killsScale(30));assertEquals(14,killsScale(40));assertEquals(16,killsScale(50));assertEquals(18,killsScale(60));assertEquals(21,killsScale(70));assertEquals(24,killsScale(80));assertEquals(28,killsScale(89));assertEquals(32,killsScale(98));assertEquals(32,killsScale(99));}
    @Test void higherLevelCommonEnemyNeverRewardsLess(){for(int level=2;level<=99;level++)assertTrue(commonReward(level)>=commonReward(level-1),"level"+level);}
    @Test void rewardRoundingIsHalfUp(){assertEquals(23,enemyReward(1,Rank.SPECIALIST,Rarity.RARE_SPAWN,1));}
    @Test void rankMultipliersAndInsightAreExplicit(){assertEquals(10,enemyReward(1,Rank.COMMON,Rarity.ORDINARY,1));assertEquals(15,enemyReward(1,Rank.SPECIALIST,Rarity.ORDINARY,1));assertEquals(25,enemyReward(1,Rank.ELITE,Rarity.ORDINARY,1));assertEquals(60,enemyReward(1,Rank.MINIBOSS,Rarity.ORDINARY,1));assertEquals(150,enemyReward(1,Rank.BOSS,Rarity.ORDINARY,1));assertEquals(List.of(1,2,3,8,15),Arrays.stream(Rank.values()).map(r->r.insight).toList());}
    @Test void bossDoesNotStackUniqueRarity(){assertEquals(enemyReward(40,Rank.BOSS,Rarity.ORDINARY,40),enemyReward(40,Rank.BOSS,Rarity.UNIQUE,40));assertEquals(20,enemyReward(1,Rank.COMMON,Rarity.UNIQUE,1));}
    @Test void exactLevelDifferenceBoundaries(){assertEquals(1.15,levelDifference(25,20));assertEquals(1,levelDifference(24,20));assertEquals(1,levelDifference(18,20));assertEquals(.75,levelDifference(17,20));assertEquals(.75,levelDifference(15,20));assertEquals(.4,levelDifference(14,20));assertEquals(.4,levelDifference(10,20));assertEquals(.1,levelDifference(9,20));}
    @Test void invalidCombatLevelsFail(){for(int n:new int[]{0,-1,100}){assertThrows(IllegalArgumentException.class,()->commonReward(n));assertThrows(IllegalArgumentException.class,()->killsScale(n));assertThrows(IllegalArgumentException.class,()->levelDifference(1,n));}}
    @Test void positivePartyPotIsSharedWithoutBonus(){assertEquals(25,equalShare(100,4));assertEquals(33,equalShare(100,3));assertEquals(0,equalShare(0,4));assertEquals(1,equalShare(1,4),"Master explicitly guarantees minimum1 for positive pot");}
    @Test void partyCountAndPotBounds(){assertThrows(IllegalArgumentException.class,()->equalShare(-1,1));assertThrows(IllegalArgumentException.class,()->equalShare(1,0));assertThrows(IllegalArgumentException.class,()->equalShare(1,257));}
    @Test void advanceWithinLevelAwardsNoPoints(){var r=advance(0,99);assertEquals(99,r.totalXp());assertEquals(1,r.level());assertEquals(0,r.earnedLevels());assertEquals(0,r.unspentPointAward());assertEquals(0,r.pendingPointAward());}
    @Test void exactThresholdAwardsFiveUnspentAndFivePending(){var r=advance(99,1);assertEquals(2,r.level());assertEquals(1,r.earnedLevels());assertEquals(5,r.unspentPointAward());assertEquals(5,r.pendingPointAward());}
    @Test void multiLevelGrantDerivesFromCumulativeXp(){var r=advance(0,1900);assertEquals(5,r.level());assertEquals(4,r.earnedLevels());assertEquals(20,r.unspentPointAward());assertEquals(20,r.pendingPointAward());assertEquals(0,curve.project(r.totalXp()).progress());}
    @Test void capPreservesEarnedTotalWithoutMoreAttributeAwards(){var r=advance(9509490,12345);assertEquals(99,r.level());assertEquals(9521835,r.totalXp());assertEquals(0,r.earnedLevels());assertEquals(1,curve.project(r.totalXp()).progress());}
    @Test void reachingCapInOneGrantAwardsExactly490Points(){var r=advance(0,9509490);assertEquals(99,r.level());assertEquals(490,r.unspentPointAward());assertEquals(490,r.pendingPointAward());}
    @Test void negativeOrOverflowXpNeverWraps(){assertThrows(IllegalArgumentException.class,()->advance(-1,1));assertThrows(IllegalArgumentException.class,()->advance(0,-1));assertThrows(ArithmeticException.class,()->advance(Long.MAX_VALUE,1));}
    @Test void masteryUsesCanonicalHalfUpThresholds(){assertEquals(50,masteryNext(1));assertEquals(141,masteryNext(2));assertEquals(260,masteryNext(3));assertEquals(400,masteryNext(4));assertEquals(0,masteryNext(20));}
    @Test void masteryCapsAt20AndMagnitudeAt38Percent(){assertEquals(1,masteryLevel(49));assertEquals(2,masteryLevel(50));assertEquals(3,masteryLevel(191));assertEquals(1.02,masteryMagnitude(50));assertEquals(20,masteryLevel(Long.MAX_VALUE));assertEquals(1.38,masteryMagnitude(Long.MAX_VALUE),1e-9);}
    @Test void masteryRejectsInvalidInput(){assertThrows(IllegalArgumentException.class,()->masteryNext(0));assertThrows(IllegalArgumentException.class,()->masteryNext(21));assertThrows(IllegalArgumentException.class,()->masteryLevel(-1));}
    @Test void learningWisdomIsAdditiveNotMultiplicative(){assertEquals(.165,learnChance(AcquisitionRarity.COMMON,10),1e-12);assertEquals(.065,learnChance(AcquisitionRarity.RARE,10),1e-12);assertEquals(.035,learnChance(AcquisitionRarity.UNIQUE,10),1e-12);assertEquals(.30,learnChance(AcquisitionRarity.COMMON,100),1e-12);}
    @Test void learningBonusCappedAtPointFour(){assertEquals(.55,learnChance(AcquisitionRarity.COMMON,10000),1e-12);assertEquals(.45,learnChance(AcquisitionRarity.RARE,10000),1e-12);assertEquals(.42,learnChance(AcquisitionRarity.UNIQUE,10000),1e-12);}
    @Test void pityUsesUnmodifiedSourceBaseNotEquipmentChance(){assertEquals(20,pityFailures(AcquisitionRarity.COMMON));assertEquals(60,pityFailures(AcquisitionRarity.RARE));assertEquals(150,pityFailures(AcquisitionRarity.UNIQUE));}
    @Test void invalidLearningWisdomRejects(){for(double wisdom:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->learnChance(AcquisitionRarity.COMMON,wisdom));}
    @Test void insightPricesAndRankTierCeilings(){assertEquals(20,insightCost("FOUNDATION"));assertEquals(40,insightCost("ADVANCED"));assertEquals(80,insightCost("SPECIALIST"));assertThrows(IllegalArgumentException.class,()->insightCost("UNAPPROVED"));assertEquals(List.of(1,2,3,4,5),Arrays.stream(Rank.values()).map(r->r.signatureTierCeiling).toList());}
    @Test void biomeBandsRequireAuditedQualifiedIds(){var p=ProgressionProfiles.load();assertEquals(5,p.biomeBands().size());for(int level=1;level<=99;level++){final int n=level;assertEquals(1,p.biomeBands().stream().filter(b->b.contains(n)).count());}assertTrue(p.nativeBiome("made_up_native_biome").isEmpty());assertEquals(4,p.biomeBands().stream().mapToInt(b->b.verifiedNativeBiomeIds().size()).sum());}
    @Test void onlyNormalDifficultyIsInitiallyEnabled(){var p=ProgressionProfiles.load();assertTrue(p.difficulty("NORMAL").enabled());assertEquals(1,p.difficulty("NORMAL").healthMultiplier());assertFalse(p.difficulty("NIGHTMARE").enabled());assertFalse(p.difficulty("HELL").enabled());assertEquals(1.8,p.difficulty("NIGHTMARE").healthMultiplier());assertEquals(1.35,p.difficulty("NIGHTMARE").damageMultiplier());assertEquals(3,p.difficulty("HELL").healthMultiplier());assertEquals(1.8,p.difficulty("HELL").damageMultiplier());}
    @Test void difficultyCannotEnableWithoutAuthoredEncounterData(){assertThrows(IllegalArgumentException.class,()->new ProgressionProfiles.Difficulty("HELL",true,3,1.8,"",""));assertThrows(IllegalArgumentException.class,()->new ProgressionProfiles.Difficulty("HELL",false,3,1.8,"",""));}
}
