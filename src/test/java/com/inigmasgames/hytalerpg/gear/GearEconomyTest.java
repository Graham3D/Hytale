package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.progress.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GearEconomyTest {
    @TempDir Path directory;
    private final GearCatalog catalog=GearCatalog.load();
    private GearDropGenerator generator(){return new GearDropGenerator(catalog,new GearBindings(),GearDropGenerator.STAGE_TWO_CANDIDATES);}
    private EnemyRewardRegistry.LootSource source(DifficultyId era,int level,ProgressionMath.Rank rank,int seed){return new EnemyRewardRegistry.LootSource("enemy-death/"+seed,new UUID(0,1),new UUID(0,seed),era,level,"test",rank,ProgressionMath.Rarity.ORDINARY,"fixture");}
    @Test void allYieldBoundariesAndRecipesMatchAuthoredTable(){
        int[] yields={1,2,3,4,8,12,20,28,40,56};for(int i=1;i<=99;i++)assertEquals(yields[i/10],GearEconomy.yield(i));
        int[] costs={4,6,8,10,8,12,16,20,24,24,32,40,48,56,40,56,72,88,112};
        int[] resets={0,0,0,0,10,11,12,13,14,15,16,18,19,20,21,23,25,28,30};
        for(int rank=2;rank<=20;rank++){var recipe=GearEconomy.recipe(rank);assertEquals(costs[rank-2],recipe.cost());assertEquals(resets[rank-2],recipe.resetPercent());
            if(rank>=16){assertEquals(GearEconomy.Component.SINGULAR_CORE,recipe.type());assertEquals(GearEconomy.Grade.ASCENDANT,recipe.minimumGrade());}}
    }
    @Test void provenanceCannotPromoteAndLowestSufficientGradePaysFirst(){
        var wallet=Map.of("RESONANT_SHARD/CAMPAIGN",999L,"RESONANT_SHARD/TEMPERED",20L,"RESONANT_SHARD/ASCENDANT",40L);
        assertEquals(Map.of("RESONANT_SHARD/TEMPERED",20L,"RESONANT_SHARD/ASCENDANT",4L),GearEconomy.debit(wallet,GearEconomy.recipe(11)));
        assertThrows(IllegalArgumentException.class,()->GearEconomy.debit(Map.of("SINGULAR_CORE/TEMPERED",9999L),GearEconomy.recipe(16)));
        assertThrows(IllegalArgumentException.class,()->GearEconomy.debit(Map.of("RESONANT_SHARD/ASCENDANT",9999L),GearEconomy.recipe(16)));
    }
    @Test void upgradeDebitResultAndResetAreImmutableAndDoNotReplay(){
        var progress=new GearEconomyProgress(Map.of("ARCANE_DUST/CAMPAIGN",40L),Map.of(),Map.of());
        var failed=progress.upgrade("request","fireball",5,0);assertEquals(1,failed.baseRanks().get("fireball"));assertEquals(32L,failed.materials().get("ARCANE_DUST/CAMPAIGN"));
        assertEquals(failed,failed.upgrade("request","fireball",1,9999));
        assertEquals(6,progress.upgrade("success","fireball",5,1000).baseRanks().get("fireball"));
        assertThrows(IllegalArgumentException.class,()->failed.upgrade("request","other",1,9999));
        assertThrows(IllegalArgumentException.class,()->progress.upgrade("invalid","fireball",19,9999));
    }
    @Test void mfChangesOnlyRarityStreamAndZeroWeightsStayZero(){
        var generator=generator();
        for(int seed=0;seed<80;seed++){
            var source=source(DifficultyId.HELL,95,ProgressionMath.Rank.COMMON,seed);
            var low=generator.generate(source,0,source.eventId(),Set.of());var high=generator.generate(source,100,source.eventId(),Set.of());
            assertEquals(low.item()==null,high.item()==null);
            if(low.item()!=null&&high.item()!=null){
                assertEquals(low.item().baseId(),high.item().baseId());
                assertEquals(low.item().intrinsicThousandths(),high.item().intrinsicThousandths());
                assertEquals(95,high.item().itemLevel());assertEquals(DifficultyId.HELL,high.item().sourceEra());
            }
        }
        assertEquals(0.0,GearMagicFind.distribution(DifficultyId.NORMAL,35,ProgressionMath.Rank.BOSS,999)
                .getOrDefault(GearRarity.LEGENDARY,0.0),1e-9);
        assertEquals(.75,GearMagicFind.snapshot(150,0),1e-9);assertEquals(1.125,GearMagicFind.snapshot(250,0),1e-9);
    }
    @Test void candidateGenerationIsDeterministicAndEveryEraHasLegalBases(){
        var g=generator();for(var era:DifficultyId.values())for(int level:era==DifficultyId.NORMAL?new int[]{1,10,20,30,40}:era==DifficultyId.NIGHTMARE?new int[]{40,49,59}:new int[]{60,79,89,99}){
            var source=source(era,level,ProgressionMath.Rank.BOSS,level);var result=g.generate(source,.5,"matrix/"+era+level,Set.of());
            assertEquals(result,g.generate(source,.5,"matrix/"+era+level,Set.of()));assertEquals(era,result.item().sourceEra());assertEquals(level,result.item().itemLevel());
            assertEquals(result.item(),GearInstance.fromJson(result.item().toJson()));
        }
    }
    @Test void commonPerfectBaseAndQaCannotMintSalvage(){
        var item=GearInstance.authoredQa(catalog.base("gm.sword_mithril.h"),UUID.randomUUID(),95,1000,GearRarity.COMMON,List.of(),java.math.BigDecimal.ZERO);
        assertTrue(item.perfectCommon());assertTrue(item.affixes().isEmpty());assertTrue(GearEconomy.salvage(item).isEmpty());
        for(var rarity:GearRarity.values()){var qa=GearQaFixtures.create(catalog,catalog.base("gm.sword_mithril.h"),UUID.randomUUID(),95,1000,rarity);assertTrue(GearEconomy.salvage(qa).isEmpty());}
    }
    @Test void allFourSalvageTypesUseStoredLevelAndEraAndReplayIsRejected(){
        for(var rarity:List.of(GearRarity.UNCOMMON,GearRarity.RARE,GearRarity.VERY_RARE,GearRarity.LEGENDARY)){
            var qa=GearQaFixtures.create(catalog,catalog.base("gm.sword_mithril.h"),UUID.randomUUID(),95,900,rarity);
            var item=new GearInstance(qa.schemaVersion(),qa.identity(),qa.definitionRevision(),qa.baseId(),qa.baseName(),qa.category(),qa.sourceEra(),qa.itemLevel(),qa.rarity(),qa.intrinsicThousandths(),qa.intrinsicStats(),qa.requirements(),qa.affixes(),GearRandom.VERSION,false);
            var payout=GearEconomy.salvage(item).orElseThrow();assertEquals(56,payout.quantity());assertEquals(GearEconomy.Grade.ASCENDANT,payout.material().grade());assertEquals(rarity.ordinal()-1,payout.material().type().ordinal());
            var credited=GearEconomyProgress.INITIAL.salvage("one",item);assertEquals(credited,credited.salvage("one",item));assertThrows(IllegalArgumentException.class,()->credited.salvage("two",item));
        }
    }
    @Test void nativeEncounterReceiptsSurviveRestartAndCasHasOneWinner(){
        var a=UUID.randomUUID();var b=UUID.randomUUID();var policy=new GearClaims.Policy("party",4,GearClaims.Mode.ROUND_ROBIN,List.of(a,b),a,Set.of(),true,false);
        var allocation=GearClaims.allocate(new GearClaims.Claim(policy,0,1000),Set.of(a,b),0,false,1000,new GearRandom("event"));
        try(var store=new FileEncounterStore(directory)){
            store.gearTransaction("claims","test",GearClaims.Allocation.class,old->allocation);
            assertThrows(IllegalArgumentException.class,()->allocation.pickup(b,0,2000));
            var picked=store.gearTransaction("claims","test",GearClaims.Allocation.class,old->old.orElseThrow().pickup(a,0,2000));assertEquals(a,picked.claimedBy());
            assertThrows(IllegalArgumentException.class,()->store.gearTransaction("claims","test",GearClaims.Allocation.class,old->old.orElseThrow().pickup(b,0,32000)));
        }
        try(var store=new FileEncounterStore(directory)){assertEquals(a,store.gearRead("claims","test",GearClaims.Allocation.class).orElseThrow().claimedBy());}
    }
    @Test void frozenClaimsAndPartyPoliciesRejectRetroactiveTheft(){
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),outsider=UUID.randomUUID();
        var policy=new GearClaims.Policy("party",1,GearClaims.Mode.ROUND_ROBIN,List.of(a,b),a,Set.of(),true,false);
        var ledger=GearClaims.Ledger.EMPTY.contribute(a,policy,1000).contribute(b,GearClaims.Policy.solo(b),1200).contribute(outsider,GearClaims.Policy.solo(outsider),1300);
        assertEquals(policy,ledger.select(Set.of(a,b,outsider),1400).orElseThrow().policy());
        var allocation=GearClaims.allocate(ledger.claims().getFirst(),Set.of(a,b,outsider),0,false,2000,new GearRandom("party"));
        assertFalse(allocation.denial(outsider,32000).isEmpty());assertEquals("",allocation.denial(b,32000));assertFalse(allocation.denial(a,182000).isEmpty());
        assertThrows(IllegalArgumentException.class,()->new GearClaims.Policy("party",2,GearClaims.Mode.PARTY_FFA,List.of(a,b),a,Set.of(a),true,false));
        var leader=new GearClaims.Policy("party",2,GearClaims.Mode.LEADER,List.of(a,b),a,Set.of(a,b),true,false);
        var assigned=GearClaims.allocate(new GearClaims.Claim(leader,0,1000),Set.of(a,b),0,true,2000,new GearRandom("leader"));
        var moved=assigned.assign(a,b,0,3000);assertEquals(assigned.sponsor(),moved.sponsor());assertEquals(b,moved.assigned());assertFalse(moved.denial(a,4000).isEmpty());
        assertThrows(IllegalArgumentException.class,()->moved.assign(a,a,0,4000));
    }
}
