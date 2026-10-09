package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixSelection.*;

class EnemyAffixSelectionTest {
    private static final EnemyAffixRegistry REGISTRY=EnemyAffixRegistry.canonical();
    private static final Binding FULL=new Binding("test-binding",EnumSet.allOf(Capability.class),100,true,false,false,4,true);
    private static Request request(List<Choice> fixed){return new Request(FULL,DifficultyId.HELL,EnemyRarity.SUPER_UNIQUE,4,fixed,true);}
    @Test void everyCardLoadsWithItsDeclaredPositiveAndNegativeIdentity(){
        assertEquals(27,REGISTRY.definitions().size());
        for(var op:Operator.values())assertEquals(op,REGISTRY.require(op.id()).operator());
        assertThrows(IllegalArgumentException.class,()->REGISTRY.require("ME-028"));
        assertFalse(REGISTRY.require(Operator.EMPOWERED_MINIONS).resolvedNumbers(DifficultyId.HELL).containsKey("defenseIncrease"));
    }
    @Test void rejectsUnknownKeysWrongTypesUnadmittedEffectsAndDuplicateDefinitions(){
        var json=EnemyAffixRegistry.resource("monster-affixes-v1.json");
        json.addProperty("ignoreSafety",true);assertThrows(IllegalArgumentException.class,()->new EnemyAffixRegistry(json));json.remove("ignoreSafety");
        var first=json.getAsJsonArray("definitions").get(0).getAsJsonObject();
        first.getAsJsonObject("parameters").addProperty("attack", "new-attack");
        assertThrows(IllegalArgumentException.class,()->new EnemyAffixRegistry(json));
        first.getAsJsonObject("parameters").remove("attack");
        first.getAsJsonArray("randomWeights").set(0,new JsonPrimitive("100"));
        assertThrows(IllegalArgumentException.class,()->new EnemyAffixRegistry(json));
        first.getAsJsonArray("randomWeights").set(0,new JsonPrimitive(100));
        json.getAsJsonArray("definitions").add(first.deepCopy());
        assertThrows(IllegalArgumentException.class,()->new EnemyAffixRegistry(json));
    }
    @Test void seedAlwaysProducesCompleteLegalSetsAndChampionsNeverGetLeaders(){
        var selection=new EnemyAffixSelection(REGISTRY);
        for(var mode:DifficultyId.values())for(int seed=0;seed<100;seed++) {
            var request=new Request(FULL,mode,EnemyRarity.UNIQUE,mode.ordinal()+1,List.of(),true);
            var result=selection.select(request,"birth/"+seed).orElseThrow();
            assertEquals(request.count(),result.size());assertTrue(selection.legal(request,result));
            assertEquals(result,new EnemyAffixSelection(REGISTRY).select(request,"birth/"+seed).orElseThrow());
            var champion=new Request(FULL,mode,EnemyRarity.CHAMPION,1,List.of(),true);
            var chosen=selection.select(champion,"birth/"+seed).orElseThrow().getFirst();
            assertFalse(semanticGroups(REGISTRY.require(chosen.affixId()).operator(),chosen.selector()).contains(Group.LEADER));
        }
    }
    @Test void operatorRandomAffixPolicyKeepsExplicitQaCompatibility(){
        var selection=new EnemyAffixSelection(REGISTRY,
                new WeightPolicy(Map.of("ME-002",2.0),Set.of("ME-004")));
        var random=new Request(FULL,DifficultyId.NORMAL,EnemyRarity.CHAMPION,1,List.of(),true);
        assertTrue(selection.weightedSets(random).stream().noneMatch(set->
                set.choices().stream().anyMatch(choice->choice.affixId().equals("ME-004"))));
        assertTrue(selection.eligibleQa(FULL,EnemyRarity.UNIQUE,Choice.of(Operator.STONE_SKIN)));
        var explicit=new Request(FULL,DifficultyId.NORMAL,EnemyRarity.UNIQUE,1,
                List.of(Choice.of(Operator.STONE_SKIN)),false);
        assertEquals(explicit.fixed(),selection.select(explicit,"explicit").orElseThrow());
    }
    @Test void rejectsAllHardExclusionsAndSelectorDependentGroups(){
        var selection=new EnemyAffixSelection(REGISTRY);var request=request(List.of());
        for(var pair:List.of(List.of(Operator.FIRE_ENCHANTED,Operator.SPECTRAL_HIT),List.of(Operator.COLD_ENCHANTED,Operator.KNOCKBACK),
                List.of(Operator.STONE_SKIN,Operator.BULWARK),List.of(Operator.HORDE,Operator.PACKBOUND),List.of(Operator.FRENZIED,Operator.AVENGER),
                List.of(Operator.REFLECTIVE,Operator.VAMPIRIC),List.of(Operator.REFLECTIVE,Operator.PACKBOUND),List.of(Operator.MANA_BURN,Operator.CURSED)))
            assertFalse(selection.legal(request,pair.stream().map(Choice::of).toList()),pair.toString());
        assertFalse(selection.legal(request,List.of(new Choice("ME-023",Selector.WARD),Choice.of(Operator.STONE_SKIN))));
        assertFalse(selection.legal(request,List.of(new Choice("ME-023",Selector.MIGHT),Choice.of(Operator.EXTRA_STRONG),Choice.of(Operator.FIRE_ENCHANTED))));
        assertTrue(selection.legal(request,List.of(new Choice("ME-023",Selector.HASTE),Choice.of(Operator.EXTRA_STRONG),Choice.of(Operator.FIRE_ENCHANTED))));
    }
    @Test void impossibleBindingsDoNotSilentlyShortenRequestedCount(){
        var binding=new Binding("inert",Set.of(),0,false,true,true,0,false);
        var request=new Request(binding,DifficultyId.HELL,EnemyRarity.UNIQUE,3,List.of(),true);
        assertTrue(new EnemyAffixSelection(REGISTRY).select(request,"fixed").isEmpty());
        assertThrows(IllegalArgumentException.class,()->new EnemyAffixSelection(REGISTRY).select(new Request(binding,DifficultyId.HELL,
                EnemyRarity.SUPER_UNIQUE,1,List.of(Choice.of(Operator.STONE_SKIN)),false),"fixed"));
    }
    @Test void revisedStoneSkinIsMeaningfulOnZeroBaselineDefenseButRequiresTheSharedMitigationPath(){
        var selection=new EnemyAffixSelection(REGISTRY);var fixed=List.of(Choice.of(Operator.STONE_SKIN));
        var binding=new Binding("unarmored-d01",Set.of(Capability.DEFENSE_STAT),0,false,false,false,0,false);
        var request=new Request(binding,DifficultyId.NORMAL,EnemyRarity.UNIQUE,1,fixed,false);
        assertEquals(fixed,selection.select(request,"stone").orElseThrow());
        var missing=new Binding("no-defense-owner",Set.of(),100,false,false,false,0,false);
        assertFalse(selection.eligible(new Request(missing,DifficultyId.HELL,EnemyRarity.UNIQUE,1,List.of(),true),fixed.getFirst()));
    }
    @Test void nativeImpulseProfileCanEnhanceItsExistingKnockback(){
        var impulseOnly=new Binding("native-impulse-only",FULL.capabilities(),FULL.usableDefense(),true,false,false,4,false);
        var request=new Request(impulseOnly,DifficultyId.NORMAL,EnemyRarity.UNIQUE,1,List.of(),true);
        var selection=new EnemyAffixSelection(REGISTRY);
        assertTrue(selection.eligible(request,Choice.of(Operator.KNOCKBACK)));
        assertTrue(selection.eligible(request,Choice.of(Operator.COLD_ENCHANTED)));
        assertTrue(selection.eligible(new Request(FULL,DifficultyId.NORMAL,EnemyRarity.UNIQUE,1,List.of(),true),
                Choice.of(Operator.KNOCKBACK)));
    }
    @Test void scoutActionCapabilityExcludesUnsupportedChannelsBeforeSelection(){
        var allowed=Set.of("ME-002","ME-003","ME-004","ME-017");
        var capabilities=EnumSet.copyOf(FULL.capabilities());capabilities.remove(Capability.MOBILE);
        capabilities.remove(Capability.NATIVE_KNOCKBACK);
        var scout=new Binding("scout-projectile",capabilities,0,true,false,false,0,true,allowed);
        var selection=new EnemyAffixSelection(REGISTRY);
        var request=new Request(scout,DifficultyId.NORMAL,EnemyRarity.CHAMPION,1,List.of(),true);
        for(var choice:selection.weightedSets(request))
            assertTrue(allowed.contains(choice.choices().getFirst().affixId()));
        assertFalse(selection.eligible(request,Choice.of(Operator.KNOCKBACK)));
        assertEquals("NATIVE_KNOCKBACK_NOT_CERTIFIED",selection.rejectionReason(scout,EnemyRarity.CHAMPION,
                Choice.of(Operator.KNOCKBACK)).orElseThrow());
        for(var excluded:List.of(Operator.EXTRA_FAST,Operator.UNSTOPPABLE,Operator.FIRE_ENCHANTED,
                Operator.COLD_ENCHANTED,Operator.LIGHTNING_ENCHANTED,Operator.SPECTRAL_HIT,Operator.POISON_ENCHANTED))
            assertFalse(selection.eligible(request,Choice.of(excluded)),excluded.name());
        assertThrows(IllegalArgumentException.class,()->selection.select(new Request(scout,DifficultyId.NORMAL,
                EnemyRarity.CHAMPION,1,List.of(Choice.of(Operator.FIRE_ENCHANTED)),false),"fixed"));
    }
    @Test void authoredGrimgorKeepsBothFixedAffixesWhileBacktrackingForHellSlots(){
        var fixed=List.of(Choice.of(Operator.FIRE_ENCHANTED),Choice.of(Operator.PACKBOUND));
        var selection=new EnemyAffixSelection(REGISTRY);var req=request(fixed);
        for(int seed=0;seed<50;seed++) {var result=selection.select(req,"grimgor/"+seed).orElseThrow();assertEquals(4,result.size());assertTrue(result.containsAll(fixed));}
    }
    @Test void enumeratesEachLegalSetOnceWithExactProductAndSelectorIntegerWeights(){
        var selection=new EnemyAffixSelection(REGISTRY);
        var req=new Request(FULL,DifficultyId.HELL,EnemyRarity.UNIQUE,2,List.of(),true);
        var sets=selection.weightedSets(req);var unique=new HashSet<List<Choice>>();
        for(var set:sets){
            assertTrue(unique.add(set.choices()));assertTrue(selection.legal(req,set.choices()));
            long product=1;for(var choice:set.choices()){
                var definition=REGISTRY.require(choice.affixId());product=Math.multiplyExact(product,definition.weight(req.difficulty()));
                if(choice.selector()!=null)product=Math.multiplyExact(product,(long)definition.selectorWeight(choice.selector()));
            }
            assertEquals(product,set.weight());
        }
        var choices=new ArrayList<Choice>();for(var definition:REGISTRY.definitions()){
            if(definition.operator()==Operator.AURA_ENCHANTED)for(var selector:Selector.values())choices.add(new Choice(definition.id(),selector));
            else choices.add(new Choice(definition.id(),null));
        }
        int complete=0;for(int i=0;i<choices.size();i++)for(int j=i+1;j<choices.size();j++){
            var pair=List.of(choices.get(i),choices.get(j));
            if(selection.legal(req,pair)&&pair.stream().allMatch(c->REGISTRY.require(c.affixId()).weight(req.difficulty())>0)){
                complete++;assertTrue(unique.contains(pair));
            }
        }
        assertEquals(complete,sets.size());
    }
    @Test void contentCannotRemoveRuntimeCapabilitiesOrExceedProviderBounds(){
        var json=EnemyAffixRegistry.resource("monster-affixes-v1.json");
        var fast=json.getAsJsonArray("definitions").get(0).getAsJsonObject();
        fast.add("requiredCapabilities",new JsonArray());assertThrows(IllegalArgumentException.class,()->new EnemyAffixRegistry(json));
        fast.getAsJsonArray("requiredCapabilities").add("MOBILE");
        fast.getAsJsonObject("parameters").getAsJsonArray("movementIncrease").set(0,new JsonPrimitive(.51));
        assertThrows(IllegalArgumentException.class,()->new EnemyAffixRegistry(json));
    }
}
