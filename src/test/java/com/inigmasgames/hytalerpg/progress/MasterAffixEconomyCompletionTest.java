package com.inigmasgames.hytalerpg.progress;

import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.difficulty.EncounterProfileResolver;
import com.inigmasgames.hytalerpg.difficulty.MonsterResistanceProfile;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.gear.*;
import com.inigmasgames.hytalerpg.links.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class MasterAffixEconomyCompletionTest {
    @TempDir Path directory;
    private static final GearCatalog CATALOG=GearCatalog.load();
    private static final GearAffixQaSuite QA=new GearAffixQaSuite(CATALOG);

    private static GearInstance preview(String id){
        return QA.preview(QA.fixtures().stream().filter(f->f.fixtureId().equals("ab-"+id.toLowerCase(Locale.ROOT)+"-affixed"))
                .findFirst().orElseThrow());
    }
    private static GearInstance production(GearInstance qa){
        return new GearInstance(qa.schemaVersion(),qa.identity(),qa.definitionRevision(),qa.baseId(),qa.baseName(),
                qa.category(),qa.sourceEra(),qa.itemLevel(),qa.rarity(),qa.intrinsicThousandths(),
                qa.intrinsicStats(),qa.requirements(),qa.affixes(),GearRandom.VERSION,false);
    }

    @Test void wa151ProductionSourceChangesRealLootWeightsAndSelection(){
        var sourceItem=production(preview("WA-151"));
        assertFalse(sourceItem.qaOnly());
        var equipped=GearEquipmentResolution.resolve(99,highAttributes(),
                List.of(new GearEquipmentResolution.Candidate(sourceItem,true,true,true)));
        assertEquals(List.of(sourceItem),equipped.validItems());
        double bonus=equipped.effects().snapshot().percent(GearEffectSnapshot.Operator.MAGIC_FIND);
        assertTrue(bonus>0);
        var removed=GearEquipmentResolution.resolve(99,highAttributes(),List.of());
        assertEquals(0,removed.effects().snapshot().percent(GearEffectSnapshot.Operator.MAGIC_FIND));
        double ordinary=HytaleGearEquipment.magicFindBreakdown(10,removed.validItems()).total();
        double boosted=HytaleGearEquipment.magicFindBreakdown(10,equipped.validItems()).total();
        assertEquals(GearMagicFind.snapshot(10,bonus),boosted,1e-9);
        assertEquals(boosted,HytaleGearEquipment.magicFindBreakdown(10,List.of(preview("WA-151"))).total(),1e-9,
                "equipped QA gear must exercise the same Magic Find mechanic as looted gear");
        var generator=new GearDropGenerator(CATALOG,new GearBindings(),GearDropGenerator.STAGE_TWO_CANDIDATES);
        var player=UUID.randomUUID();
        GearLootService.Loot changed=null,control=null;
        // Find a deterministic source whose production rarity draw crosses the changed weight.
        for(int seed=1;seed<=2000&&changed==null;seed++){
            var spawn=spawn(seed);var lootSource=spawn.lootSource().orElseThrow();
            var low=generator.generate(lootSource,ordinary,spawn.eventId(),Set.of());
            var high=generator.generate(lootSource,boosted,spawn.eventId(),Set.of());
            if(low.item()==null||high.item()==null||low.item().rarity()==high.item().rarity())continue;
            var plan=plan(spawn,player);
            try(var store=new FileEncounterStore(directory.resolve("loot-"+seed))){
                var service=new GearLootService(store,generator,()->1000);
                service.contribute(spawn.world(),spawn.enemy(),player,GearClaims.Policy.solo(player),100);
                service.freezeMagicFind(spawn.eventId(),Map.of(player,boosted));
                // Equipment was removed before deferred delivery; first death capture remains authoritative.
                service.freezeMagicFind(spawn.eventId(),Map.of(player,
                        HytaleGearEquipment.magicFindBreakdown(10,removed.validItems()).total()));
                changed=service.deliver(plan);
                assertEquals(changed,service.deliver(plan),"duplicate cannot reroll frozen loot");
            }
            try(var store=new FileEncounterStore(directory.resolve("control-"+seed))){
                var service=new GearLootService(store,generator,()->1000);
                service.contribute(spawn.world(),spawn.enemy(),player,GearClaims.Policy.solo(player),100);
                control=service.death(plan,Map.of(player,ordinary));
            }
            assertEquals(high.item().rarity(),changed.result().item().rarity());
            assertEquals(low.item().rarity(),control.result().item().rarity());
            assertFalse(sourceItem.qaOnly());
            assertEquals(boosted,changed.magicFind(),1e-9);
            assertEquals(ordinary,control.magicFind(),1e-9);
            assertEquals(changed.source(),control.source());
            assertNotEquals(changed.result().rarityDistribution(),control.result().rarityDistribution());
        }
        assertNotNull(changed,"production seed must change the rarity selection");
    }

    @Test void authoredFarwandererSetContributesToDisplayedAndCapturedMagicFind(){
        var slots=List.of("head","chest","hands","legs");
        var candidates=new ArrayList<GearEquipmentResolution.Candidate>();
        for(String slot:slots){
            var base=CATALOG.base("gm.garb_wayfarer."+slot+".h");
            int itemLevel=base.sourceWindow()==null?base.requiredLevel():base.sourceWindow().getLast();
            var item=GearQaFixtures.create(CATALOG,base,UUID.randomUUID(),itemLevel,1000,GearRarity.RARE);
            assertTrue(item.qaOnly());
            assertEquals(4.8,item.affixes().stream().filter(affix->affix.familyId().equals("WA-151"))
                    .findFirst().orElseThrow().value(),1e-9);
            candidates.add(new GearEquipmentResolution.Candidate(item,true,true,true));
        }
        var attributes=new EnumMap<RpgAttribute,Integer>(RpgAttribute.class);
        attributes.putAll(highAttributes());
        attributes.put(RpgAttribute.LUCK,320);
        var equipped=GearEquipmentResolution.resolve(99,attributes,candidates);
        assertEquals(4,equipped.validItems().size());
        var magicFind=HytaleGearEquipment.magicFindBreakdown(320,equipped.validItems());
        assertEquals(.192,magicFind.equippedGear(),1e-9);
        assertEquals(GearMagicFind.snapshot(320,.192),magicFind.total(),1e-9);
    }

    @Test void wa152CreditedGoldAwardIsOncePerDeathAndControlHasNoBonus(){
        var source=production(preview("WA-152"));
        var admitted=candidate(source);
        assertEquals(List.of(source),admitted.validItems());
        var gold=HytaleGearEquipment.goldFind(admitted.effects().snapshot());assertTrue(gold>0);
        assertEquals(admitted.effects().snapshot().value("WA-152"),gold);
        assertEquals(gold,HytaleGearEquipment.goldFind(candidate(preview("WA-152")).effects().snapshot()),1e-9,
                "equipped QA gear must exercise the same Gold Find mechanic as looted gear");
        var controlItem=QA.preview(QA.fixtures().stream().filter(f->f.fixtureId().equals("ab-wa-152-control"))
                .findFirst().orElseThrow());
        assertEquals(source.baseId(),controlItem.baseId());
        var unrolled=candidate(controlItem);
        assertEquals(List.of(controlItem),unrolled.validItems());
        assertEquals(0,unrolled.effects().snapshot().value("WA-152"));
        var player=UUID.randomUUID();
        var capture=new HashMap<UUID,Double>();capture.put(player,gold);
        var plan=plan(spawn(4001),player).withGoldFind(capture);
        capture.put(player,HytaleGearEquipment.goldFind(GearEffectSnapshot.EMPTY));
        assertEquals(0,capture.get(player)); // removing equipment cannot change the frozen death plan
        var control=plan(spawn(4002),player).withGoldFind(Map.of(player,unrolled.effects().snapshot().value("WA-152")));
        var catalog=RpgCatalog.loadCanonical();var compatibility=new CompatibilityService();
        var graph=new RpgLinkGraphService(catalog,compatibility);
        var players=new RpgLoadoutService(catalog,new FileRpgPlayerStateRepository(directory.resolve("players")),graph,
                new LinkCompiler(catalog,graph,compatibility),new OwnershipEntitlementPolicy(true),
                record->{});
        players.configureEarnedRewards(new FileEarnedRewardStore(directory.resolve("awards")));
        var reward=plan.reward(plan.shares().getFirst());
        assertEquals(EarnedRewardStore.Outcome.COMMITTED,players.awardEarned(player,reward).outcome());
        var state=new FileRpgPlayerStateRepository(directory.resolve("players")).load(player).state();
        var first=state.goldBalance;
        var expectedGold=reward.goldPot().baseShare().multiply(BigDecimal.ONE.add(BigDecimal.valueOf(gold).movePointLeft(2)));
        var creditedGold=BigDecimal.valueOf(first.gold()).add(first.remainder());
        assertEquals(0,expectedGold.compareTo(creditedGold));
        assertTrue(creditedGold.compareTo(reward.goldPot().baseShare())>0,"WA-152 adds the exact credited bonus");
        assertEquals(EarnedRewardStore.Outcome.DUPLICATE,players.awardEarned(player,reward).outcome());
        assertEquals(first,new FileRpgPlayerStateRepository(directory.resolve("players")).load(player).state().goldBalance);
        var controlReward=control.reward(control.shares().getFirst());
        assertEquals(EarnedRewardStore.Outcome.COMMITTED,players.awardEarned(player,controlReward).outcome());
        var after=new FileRpgPlayerStateRepository(directory.resolve("players")).load(player).state().goldBalance;
        assertTrue(first.gold()>0);
        assertEquals(controlReward.goldPot().baseShare().longValueExact(),after.gold()-first.gold());
    }

    @Test void wa154OnlyLegalReducedItemEquipsLowerStatActor(){
        var reduced=production(preview("WA-154"));var base=CATALOG.base(reduced.baseId());
        assertFalse(reduced.qaOnly());
        var fullGate=GearRequirements.combine(new GearRequirements.Gate(base.requiredLevel(),base.requiredAttributes()),
                reduced.affixes().stream().map(GearInstance.AffixRoll::requirements).toList(),BigDecimal.ZERO);
        var unrolled=new GearInstance(reduced.schemaVersion(),UUID.randomUUID(),reduced.definitionRevision(),reduced.baseId(),
                reduced.baseName(),reduced.category(),reduced.sourceEra(),reduced.itemLevel(),GearRarity.COMMON,
                reduced.intrinsicThousandths(),reduced.intrinsicStats(),fullGate,List.of(),reduced.rngVersion(),false);
        var lower=new EnumMap<RpgAttribute,Integer>(RpgAttribute.class);lower.putAll(fullGate.attributes());
        var eased=reduced.requirements().attributes();
        var lowered=fullGate.attributes().keySet().stream().filter(a->eased.getOrDefault(a,0)<fullGate.attributes().get(a)).findFirst().orElseThrow();
        lower.put(lowered,eased.get(lowered));
        int level=Math.max(reduced.requirements().level(),fullGate.level());
        var yes=GearEquipmentResolution.resolve(level,lower,List.of(new GearEquipmentResolution.Candidate(reduced,true,true,true)));
        var no=GearEquipmentResolution.resolve(level,lower,List.of(new GearEquipmentResolution.Candidate(unrolled,true,true,true)));
        assertEquals(List.of(reduced),yes.validItems());
        assertEquals("UNMET_REQUIREMENTS",no.rejected().get(unrolled.identity()));
        assertTrue(no.validItems().isEmpty());
        assertTrue(GearEquipmentResolution.resolve(level,lower,List.of()).effects().snapshot().empty());
        assertEquals("BROKEN",GearEquipmentResolution.resolve(level,lower,
                List.of(new GearEquipmentResolution.Candidate(reduced,true,false,true))).rejected().get(reduced.identity()));
    }

    private static EnemyRewardRegistry.Spawn spawn(int n){
        var world=new UUID(2,3);var enemy=new UUID(4,n);
        var combat=new EncounterProfileResolver.Resolved(world,enemy,DifficultyId.HELL,"fixture","fixture",
                "fixture","fixture/biome",95,100,10,1,1,MonsterResistanceProfile.NONE,
                EncounterProfileResolver.Evidence.FIXTURE_ONLY);
        return new EnemyRewardRegistry.Spawn(world,enemy,"fixture","fixture","fixture/biome",95,
                ProgressionMath.Rank.BOSS,ProgressionMath.Rarity.ORDINARY,"fixture",0,null,combat);
    }
    private static EncounterContributions.DeathPlan plan(EnemyRewardRegistry.Spawn spawn,UUID player){
        long xp=ProgressionMath.equalShare(ProgressionMath.enemyReward(spawn.level(),spawn.rank(),spawn.rarity(),95),1);
        var share=new EncounterContributions.Share(player,xp,spawn.rank().insight,95,1);
        return new EncounterContributions.DeathPlan(spawn,Vec3.ZERO,100,List.of(share));
    }
    private static Map<RpgAttribute,Integer> highAttributes(){
        var values=new EnumMap<RpgAttribute,Integer>(RpgAttribute.class);
        for(var attribute:RpgAttribute.values())values.put(attribute,500);
        return values;
    }
    private static GearEquipmentResolution.Result candidate(GearInstance source){
        try{
            var resolve=GearEquipmentResolution.class.getDeclaredMethod("resolve",int.class,Map.class,Collection.class,Set.class);
            resolve.setAccessible(true);
            return (GearEquipmentResolution.Result)resolve.invoke(null,99,highAttributes(),
                    List.of(new GearEquipmentResolution.Candidate(source,true,true,true)),Set.of("WA-152"));
        }catch(ReflectiveOperationException error){throw new AssertionError("Candidate equipment admission unavailable",error);}
    }
}
