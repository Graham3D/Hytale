package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.progress.ProgressionMath;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GearLootProfilesTest {
    @Test void exactRankDefaultsAndEmptyCanonicalOverrides() {
        var profiles=GearLootProfiles.CURRENT;
        double[] chances={.30,.50,.75,.25,.75};
        int[] guaranteed={0,0,0,1,1};
        int[] caps={1,1,1,2,2};
        var ranks=ProgressionMath.Rank.values();
        for(int i=0;i<ranks.length;i++){
            var profile=profiles.rank(ranks[i]);
            assertEquals(guaranteed[i],profile.guaranteedPicks());
            assertEquals(List.of(chances[i]),profile.optionalPickChances());
            assertEquals(caps[i],profile.maxEquipment());
            assertEquals(1+guaranteed[i],profile.picks());
            assertEquals(profile,profiles.resolve("any-enemy",ranks[i]));
        }
        assertEquals(profiles.rank(ProgressionMath.Rank.COMMON),
                profiles.resolve("Bear_Voidtaken_D1",ProgressionMath.Rank.COMMON));
        assertEquals(profiles.rank(ProgressionMath.Rank.BOSS),
                profiles.resolve("Golem_Crystal_Earth",ProgressionMath.Rank.BOSS));
    }

    @Test void guaranteedFirstPickNeverRollsAndAllChildrenAreStableAndCapped() {
        var profiles=GearLootProfiles.CURRENT;
        for(var rank:ProgressionMath.Rank.values())for(int kill=0;kill<1000;kill++){
            String event="enemy-death/test/"+rank+"/"+kill;
            var decision=profiles.decide("test",rank,event);
            assertEquals(decision,profiles.decide("test",rank,event));
            assertTrue(decision.succeeded()<=decision.maxEquipment());
            assertEquals(decision.picks(),decision.rolls().stream().map(GearLootProfiles.Pick::childEventId).distinct().count());
            for(var pick:decision.rolls()){
                assertEquals(event+"/gear-pick/"+pick.index(),pick.childEventId());
                if(pick.guaranteed()){
                    assertTrue(pick.opportunity());
                    assertNull(pick.optionalRoll());
                    assertNull(pick.optionalChance());
                }else{
                    assertNotNull(pick.optionalRoll());
                    assertEquals(pick.optionalRoll()<pick.optionalChance(),pick.opportunity());
                }
            }
            if(rank==ProgressionMath.Rank.BOSS||rank==ProgressionMath.Rank.MINIBOSS)
                assertTrue(decision.succeeded()>=1);
        }
    }

    @Test void hundredThousandKillsPerRankAtEachMagicFindSnapshot() throws Exception {
        final int kills=100_000;double[] magicFind={0,1.60,1.792};
        double[] expectedZero={.70,.50,.25,0,0};
        double[] expectedOne={.30,.50,.75,.75,.25};
        double[] expectedTwo={0,0,0,.25,.75};
        var csv=new StringBuilder("rank,mf,kills,zero,one,two,threePlus,avgItems,normal,magic,rare\n");
        var ranks=ProgressionMath.Rank.values();
        for(int rankIndex=0;rankIndex<ranks.length;rankIndex++){
            var rank=ranks[rankIndex];long[] baseline=null;long baselineItems=0;
            long rareAtZero=0;
            for(double mf:magicFind){
                var weights=GearMagicFind.distribution(DifficultyId.NORMAL,25,rank,mf);
                long[] quantity=new long[4];long[] quality=new long[3];long items=0;
                for(int kill=0;kill<kills;kill++){
                    var decision=GearLootProfiles.CURRENT.decide("simulation",rank,"loot-simulation/"+rank+"/"+kill);
                    int count=(int)decision.succeeded();quantity[Math.min(count,3)]++;items+=count;
                    for(var pick:decision.rolls())if(pick.opportunity()){
                        var rarity=new GearRandom(pick.seed()).weighted(weights,"rarity").quality();
                        quality[switch(rarity){case NORMAL->0;case MAGIC->1;case RARE->2;
                            default->throw new AssertionError("Unexpected production quality "+rarity);} ]++;
                    }
                }
                assertEquals(kills,Arrays.stream(quantity).sum());
                assertEquals(items,Arrays.stream(quality).sum());
                assertEquals(0,quantity[3],"No three-item deaths in this revision");
                assertEquals(expectedZero[rankIndex],(double)quantity[0]/kills,.01);
                assertEquals(expectedOne[rankIndex],(double)quantity[1]/kills,.01);
                assertEquals(expectedTwo[rankIndex],(double)quantity[2]/kills,.01);
                if(baseline==null){baseline=quantity.clone();baselineItems=items;rareAtZero=quality[2];}
                else {assertArrayEquals(baseline,quantity,"MF must not change quantity");assertEquals(baselineItems,items);}
                if(mf==1.792)assertTrue(quality[2]>rareAtZero,"MF must improve quality");
                csv.append(rank).append(',').append(mf).append(',').append(kills);
                for(long count:quantity)csv.append(',').append(count);
                csv.append(',').append((double)items/kills);
                for(long count:quality)csv.append(',').append((double)count/items);
                csv.append('\n');
            }
        }
        var report=Path.of("build/reports/gear-loot-simulation.csv");Files.createDirectories(report.getParent());
        Files.writeString(report,csv.toString());
    }
}
