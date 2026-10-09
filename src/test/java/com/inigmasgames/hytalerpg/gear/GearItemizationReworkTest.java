package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry;
import com.inigmasgames.hytalerpg.progress.ProgressionMath;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GearItemizationReworkTest {
    private final GearCatalog catalog=GearCatalog.load();
    private final GearDropGenerator generator=new GearDropGenerator(catalog,new GearBindings(),GearAffixRuntime.ENABLED);
    private EnemyRewardRegistry.LootSource source(DifficultyId era,int level,ProgressionMath.Rank rank,int event){
        return new EnemyRewardRegistry.LootSource("itemization-test/"+event,new UUID(1,1),new UUID(1,event),era,level,
                "test",rank,ProgressionMath.Rarity.ORDINARY,"test");
    }
    @Test void pureLuckSnapshotOpportunityAndQualityGates(){
        int rawLuck=10+98*5;
        assertEquals(500,rawLuck);assertEquals(320,GearMagicFind.effectiveLuck(rawLuck));
        assertEquals(1.60,GearMagicFind.snapshot(rawLuck,0),1e-10);
        assertEquals(.20,GearMagicFind.opportunity(ProgressionMath.Rank.COMMON));
        assertEquals(.35,GearMagicFind.opportunity(ProgressionMath.Rank.SPECIALIST));
        assertEquals(.65,GearMagicFind.opportunity(ProgressionMath.Rank.ELITE));
        assertEquals(1,GearMagicFind.opportunity(ProgressionMath.Rank.MINIBOSS));
        assertEquals(1,GearMagicFind.opportunity(ProgressionMath.Rank.BOSS));
        var low=GearMagicFind.distribution(DifficultyId.NORMAL,8,ProgressionMath.Rank.COMMON,1.6);
        assertEquals(0,low.get(GearRarity.RARE));
        assertTrue(low.get(GearRarity.MAGIC)>GearMagicFind.distribution(DifficultyId.NORMAL,8,ProgressionMath.Rank.COMMON,0).get(GearRarity.MAGIC));
        for(var mf:new double[]{0,.25,.5,.75,1,1.6,2,3}){
            var weights=GearMagicFind.weights(DifficultyId.NORMAL,35,ProgressionMath.Rank.COMMON,mf);
            assertEquals(1,weights.normalized().values().stream().mapToDouble(Double::doubleValue).sum(),1e-12);
            assertEquals(Set.of(GearRarity.NORMAL,GearRarity.MAGIC,GearRarity.RARE,GearRarity.VERY_RARE,GearRarity.LEGENDARY),weights.normalized().keySet());
        }
        var zero=GearMagicFind.distribution(DifficultyId.NORMAL,35,ProgressionMath.Rank.COMMON,0);
        assertEquals(.70,zero.get(GearRarity.NORMAL),1e-12);assertEquals(.27,zero.get(GearRarity.MAGIC),1e-12);
        assertEquals(.03,zero.get(GearRarity.RARE)+zero.get(GearRarity.VERY_RARE),1e-12);
        assertEquals(0,zero.get(GearRarity.LEGENDARY));
        var high=GearMagicFind.distribution(DifficultyId.NORMAL,35,ProgressionMath.Rank.COMMON,1.6);
        assertTrue(high.get(GearRarity.NORMAL)>=.35&&high.get(GearRarity.NORMAL)<=.50);
        assertTrue(high.get(GearRarity.MAGIC)>=.40&&high.get(GearRarity.MAGIC)<=.50);
        assertTrue(high.get(GearRarity.RARE)+high.get(GearRarity.VERY_RARE)>=.08
                &&high.get(GearRarity.RARE)+high.get(GearRarity.VERY_RARE)<=.15);
    }
    @Test void productionGeneratorMatchesWeightsAndKeepsQuantityAndBaseIndependentOfMf(){
        var counts=new EnumMap<GearRarity,int[]>(GearRarity.class);
        for(var rarity:List.of(GearRarity.NORMAL,GearRarity.MAGIC,GearRarity.RARE,GearRarity.VERY_RARE,GearRarity.LEGENDARY))counts.put(rarity,new int[2]);
        int trials=1200;
        for(int i=0;i<trials;i++){
            var source=source(DifficultyId.NORMAL,35,ProgressionMath.Rank.BOSS,i);
            var low=generator.generate(source,0,source.eventId(),Set.of()).item();
            var high=generator.generate(source,1.6,source.eventId(),Set.of()).item();
            assertNotNull(low);assertNotNull(high);
            assertEquals(low.baseId(),high.baseId());assertEquals(low.intrinsicThousandths(),high.intrinsicThousandths());
            assertEquals(35,high.itemLevel());assertEquals(low.identity(),high.identity());
            counts.get(low.rarity())[0]++;counts.get(high.rarity())[1]++;
            assertTrue(low.rarity().quality().random());assertTrue(high.rarity().quality().random());
        }
        for(var rarity:counts.keySet())for(int index=0;index<2;index++){
            double expected=GearMagicFind.distribution(DifficultyId.NORMAL,35,ProgressionMath.Rank.BOSS,index==0?0:1.6).get(rarity);
            assertEquals(expected,counts.get(rarity)[index]/(double)trials,.035,rarity+"/"+index);
        }
        System.out.println("ITEMIZATION_NORMAL35_BOSS trials="+trials+" normal="+Arrays.toString(counts.get(GearRarity.NORMAL))
                +" magic="+Arrays.toString(counts.get(GearRarity.MAGIC))+" rare="+Arrays.toString(counts.get(GearRarity.RARE)));
    }
    @Test void affixBudgetsAndLegacySalvageStayInTheirBands(){
        assertTrue(AuthoredGearDefinition.registered().isEmpty());
        assertFalse(GearQuality.NORMAL.fortuneEligible());assertTrue(GearQuality.MAGIC.fortuneEligible());
        assertTrue(GearQuality.RARE.fortuneEligible());assertFalse(GearQuality.SET.fortuneEligible());
        assertFalse(GearQuality.UNIQUE.fortuneEligible());
        assertEquals("#51c534",GearQuality.SET.color());assertEquals("#ff9100",GearQuality.UNIQUE.color());
        assertEquals(Optional.empty(),GearEconomy.componentFor(GearQuality.NORMAL));
        assertEquals(Optional.of(GearEconomy.Component.PLAIN_SCRAP),GearEconomy.componentFor(GearQuality.MAGIC));
        assertEquals(Optional.of(GearEconomy.Component.ARCANE_DUST),GearEconomy.componentFor(GearQuality.RARE));
        assertEquals(Optional.of(GearEconomy.Component.RESONANT_SHARD),GearEconomy.componentFor(GearQuality.SET));
        assertEquals(Optional.of(GearEconomy.Component.SINGULAR_CORE),GearEconomy.componentFor(GearQuality.UNIQUE));
        var base=catalog.base("gm.sword_mithril.h");
        for(var rarity:List.of(GearRarity.COMMON,GearRarity.UNCOMMON,GearRarity.RARE,GearRarity.VERY_RARE,GearRarity.LEGENDARY)){
            var qa=GearQaFixtures.create(catalog,base,UUID.randomUUID(),95,950,rarity);
            var saved=GearInstance.fromJson(qa.toJson());
            assertEquals(qa.identity(),saved.identity());assertEquals(qa.affixes(),saved.affixes());
            assertEquals(qa.intrinsicThousandths(),saved.intrinsicThousandths());
            assertEquals(rarity.quality(),saved.quality());
            if(rarity==GearRarity.VERY_RARE||rarity==GearRarity.LEGENDARY){
                var owned=new GearInstance(qa.schemaVersion(),qa.identity(),qa.definitionRevision(),qa.baseId(),qa.baseName(),
                        qa.category(),qa.sourceEra(),qa.itemLevel(),qa.rarity(),qa.intrinsicThousandths(),qa.intrinsicStats(),
                        qa.requirements(),qa.affixes(),qa.rngVersion(),false);
                assertEquals(rarity==GearRarity.VERY_RARE?GearEconomy.Component.RESONANT_SHARD:GearEconomy.Component.SINGULAR_CORE,
                        GearEconomy.salvage(owned).orElseThrow().material().type());
            }
        }
        assertThrows(IllegalArgumentException.class,()->new AuthoredGearDefinition("fake",GearQuality.UNIQUE,"gm.sword_mithril.h",null,null,List.of("boss")));
    }
    @Test void highLevelProductionAffixCountsFollowConfiguredWeights(){
        var magic=new int[3];var rare=new int[7];var veryRare=new int[7];var legendary=new int[7];int[] totals=new int[4];
        for(int i=0;i<2400;i++){
            var source=source(DifficultyId.HELL,95,ProgressionMath.Rank.BOSS,10_000+i);
            var item=generator.generate(source,1.6,source.eventId(),Set.of()).item();
            assertNotNull(item);
            if(item.rarity()==GearRarity.MAGIC){magic[item.affixes().size()]++;totals[0]++;}
            if(item.rarity()==GearRarity.RARE){
                rare[item.affixes().size()]++;totals[1]++;
                assertTrue(item.affixes().stream().filter(a->a.side()==GearCatalog.Side.PREFIX).count()<=3);
                assertTrue(item.affixes().stream().filter(a->a.side()==GearCatalog.Side.SUFFIX).count()<=3);
            }
            if(item.rarity()==GearRarity.VERY_RARE){veryRare[item.affixes().size()]++;totals[2]++;}
            if(item.rarity()==GearRarity.LEGENDARY){legendary[item.affixes().size()]++;totals[3]++;}
        }
        assertTrue(totals[0]>500&&totals[1]>300&&totals[2]>30&&totals[3]>10);
        assertEquals(totals[0],magic[1]);
        assertEquals(.60,rare[2]/(double)totals[1],.10);
        assertEquals(.40,rare[3]/(double)totals[1],.10);
        assertEquals(totals[2],veryRare[4]+veryRare[5]);
        assertEquals(totals[3],legendary[6]);
        System.out.println("ITEMIZATION_HELL95_BOSS trials=2400 magicCounts="+Arrays.toString(magic)
                +" rareCounts="+Arrays.toString(rare));
    }
    @Test void savedUncommonItemMigratesConceptuallyWithoutChangingFrozenPayload() throws Exception {
        String original;
        try(var stream=getClass().getResourceAsStream("/gear/scout-leather-leggings-ga160-v11.json")){
            assertNotNull(stream);original=new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        var item=GearInstance.fromJson(original);
        assertEquals(GearRarity.UNCOMMON,item.rarity());
        assertEquals(GearQuality.MAGIC,item.quality());
        assertEquals("8925cba6-57ea-3d37-8b38-f21a14b189bc",item.identity().toString());
        assertEquals(item,GearInstance.fromJson(item.toJson()));
        assertEquals(item.affixes(),GearInstance.fromJson(item.toJson()).affixes());
    }
}
