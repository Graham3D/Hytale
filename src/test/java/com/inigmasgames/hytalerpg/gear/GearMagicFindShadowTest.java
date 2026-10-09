package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.progress.ProgressionMath;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GearMagicFindShadowTest {
    @Test void qaDiscoveryKeepsProvenanceAndOnlyChangesIsolatedQualityWeights(){
        var suite=new GearAffixQaSuite(GearCatalog.load());
        var qa=suite.create("ab-wa-151-affixed",UUID.randomUUID());
        assertTrue(qa.qaOnly());
        var snapshot=new GearEffectSnapshot(List.of(qa));
        var source=snapshot.sources(GearEffectSnapshot.Operator.MAGIC_FIND);
        assertEquals(1,source.size());assertTrue(source.getFirst().qaOnly());
        double productionGear=source.stream().filter(s->!s.qaOnly()).mapToDouble(GearEffectSnapshot.Source::fraction).sum();
        double shadowGear=source.stream().mapToDouble(GearEffectSnapshot.Source::fraction).sum();
        assertEquals(0,productionGear);assertTrue(shadowGear>0);
        double production=GearMagicFind.snapshot(100,productionGear);
        double shadow=GearMagicFind.snapshot(100,shadowGear);
        assertEquals(.50,production,1e-12);assertEquals(.50+shadowGear,shadow,1e-12);
        assertEquals(.65,GearMagicFind.snapshot(100,.15),1e-12);
        var normal=GearMagicFind.weights(DifficultyId.NORMAL,35,ProgressionMath.Rank.COMMON,production);
        var isolated=GearMagicFind.weights(DifficultyId.NORMAL,35,ProgressionMath.Rank.COMMON,shadow);
        assertTrue(isolated.normalized().get(GearRarity.RARE)>normal.normalized().get(GearRarity.RARE));
        assertEquals(normal.base(),isolated.base());
        assertEquals(GearMagicFind.opportunity(ProgressionMath.Rank.COMMON),.20);
        assertTrue(qa.qaOnly()); // Shadow evaluation never edits provenance or grants a drop.
        System.out.println("QA_MAGIC_FIND_SHADOW item="+qa.identity()+" qaOnly="+qa.qaOnly()
                +" productionExcludedMf="+production+" isolatedMf="+shadow
                +" productionRareWeight="+normal.normalized().get(GearRarity.RARE)
                +" isolatedRareWeight="+isolated.normalized().get(GearRarity.RARE)+" rewardGranted=false");
    }
}
