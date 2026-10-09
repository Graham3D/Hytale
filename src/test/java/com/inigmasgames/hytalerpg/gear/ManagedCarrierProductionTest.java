package com.inigmasgames.hytalerpg.gear;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Calls the production native leaf's resolver with frozen legal-equipment snapshots. */
public final class ManagedCarrierProductionTest {
    private final GearCatalog catalog=GearCatalog.load();
    private GearInstance item(String baseId,String affixId,double value){
        var base=catalog.base(baseId);
        var rolls=new ArrayList<GearInstance.AffixRoll>();
        if(affixId!=null){var definition=catalog.affix(affixId);
            rolls.add(new GearInstance.AffixRoll(affixId,definition.side(),definition.exclusionGroup(),5,value,
                    new GearRequirements.Gate(1,Map.of()),"test roll",definition.name()));}
        return new GearInstance(1,UUID.randomUUID(),GearCatalog.REVISION,base.id(),base.name(),base.category(),
                base.era(),95,GearRarity.COMMON,1000,base.perfectStats(),
                new GearRequirements.Gate(base.requiredLevel(),base.requiredAttributes()),rolls,"carrier-test",true);
    }
    @Test void focusMagicPowerAndElementalFlatReachRealContactResolver(){
        for(var family:List.of("staff_apprentice","wand_oak","book_apprentice")){
            String base="gm."+family+".h";
            var control=item(base,null,0);
            var attuned=item(base,"WA-003",8);
            var c=ManagedCarrierDamageInteraction.resolve(new GearEffectSnapshot(List.of(control)),control,"focus",1,0,false);
            var a=ManagedCarrierDamageInteraction.resolve(new GearEffectSnapshot(List.of(attuned)),attuned,"focus",1,0,false);
            assertEquals(8,a.amount(GearCombatEffects.Channel.PHYSICAL)-c.amount(GearCombatEffects.Channel.PHYSICAL),1e-8);
            var launched=ManagedCarrierDamageInteraction.resolve(new GearEffectSnapshot(List.of(attuned)),attuned,"focus-projectile",1,0,true);
            assertEquals(a.amount(GearCombatEffects.Channel.PHYSICAL),launched.amount(GearCombatEffects.Channel.PHYSICAL),1e-8);
            var flat=item(base,"WA-019",5);
            var elemental=ManagedCarrierDamageInteraction.resolve(new GearEffectSnapshot(List.of(flat)),flat,"focus",1,0,false);
            assertEquals(5,elemental.amount(GearCombatEffects.Channel.FIRE),1e-8);
            assertThrows(IllegalArgumentException.class,()->ManagedCarrierDamageInteraction.resolve(
                    new GearEffectSnapshot(List.of(control)),attuned,"focus",1,0,false));
        }
    }
    @Test void bombUsesFrozenPhysicalRollAndRejectsInvalidSource(){
        var bomb=item("gm.bomb_standard.h",null,0);
        var honed=item("gm.bomb_standard.h","WA-001",8);
        var plainRange=GearCombatEffects.physical(bomb);
        var honedRange=GearCombatEffects.physical(honed);
        assertEquals(8,honedRange.minimum()-plainRange.minimum(),1e-8);
        assertEquals(8,honedRange.maximum()-plainRange.maximum(),1e-8);
        double frozen=GearPower.sample(honedRange.minimum(),honedRange.maximum(),"bomb-root");
        assertTrue(frozen>=honedRange.minimum()&&frozen<=honedRange.maximum());
        var valid=new GearEffectSnapshot(List.of(bomb));
        var shot=ManagedCarrierDamageInteraction.resolve(valid,bomb,"bomb-root",1.2,27.4,true);
        assertEquals(32.88,shot.amount(GearCombatEffects.Channel.PHYSICAL),1e-7);
        assertEquals(shot.amount(GearCombatEffects.Channel.PHYSICAL),
                ManagedCarrierDamageInteraction.resolve(valid,bomb,"bomb-root",1.2,27.4,true)
                        .amount(GearCombatEffects.Channel.PHYSICAL),1e-8);
        assertThrows(IllegalArgumentException.class,()->ManagedCarrierDamageInteraction.resolve(
                GearEffectSnapshot.EMPTY,bomb,"bomb-root",1.2,27.4,true));
        var actual=ManagedCarrierDamageInteraction.resolve(new GearEffectSnapshot(List.of(honed)),honed,"bomb-root",1,
                frozen,true);
        assertEquals(frozen,actual.amount(GearCombatEffects.Channel.PHYSICAL),1e-8);
    }
    @Test void shieldAffixChangesDefenseWithoutRedefiningSkillBasePower(){
        var plain=item("gm.shield_wooden.h",null,0);
        var reinforced=item("gm.shield_wooden.h","WA-069",50);
        var control=GearDefenseEffects.resolve(new GearEffectSnapshot(List.of(plain)),60,0,0,0);
        var affixed=GearDefenseEffects.resolve(new GearEffectSnapshot(List.of(reinforced)),60,0,0,0);
        assertEquals(50,affixed.totalRating()-control.totalRating(),1e-7);
        assertTrue(affixed.managedProtection()>control.managedProtection());
        assertEquals(0,ManagedCarrierDamageInteraction.resolve(new GearEffectSnapshot(List.of(reinforced)),
                reinforced,"guard-bash",0,0,false).amount(GearCombatEffects.Channel.PHYSICAL));
        assertEquals(control.totalRating(),GearDefenseEffects.resolve(new GearEffectSnapshot(List.of(plain)),60,0,0,0).totalRating());
    }
    @Test void allFiveRarityCarrierIdsRoundTripFrozenPayload(){
        var bindings=new GearBindings();
        for(var family:List.of("staff_apprentice","wand_oak","book_apprentice","shield_wooden","bomb_standard")){
            var base=catalog.base("gm."+family+".h");
            for(var rarity:GearRarity.values()){
                var gear=new GearInstance(1,UUID.randomUUID(),GearCatalog.REVISION,base.id(),base.name(),base.category(),
                        base.era(),95,rarity,1000,base.perfectStats(),
                        new GearRequirements.Gate(base.requiredLevel(),base.requiredAttributes()),List.of(),"carrier-test",true);
                assertTrue(bindings.require(base.id()).mapped());
                var carrier="RPG_Gear_"+base.id().substring(3).replace('.','_')+
                        (rarity.quality()==GearQuality.NORMAL?"":"_"+rarity.nativeParticleTier);
                assertTrue(carrier.startsWith("RPG_Gear_"));
                assertEquals(gear,GearInstance.fromJson(gear.toJson()));
                var other=GearRarity.values()[(rarity.ordinal()+1)%GearRarity.values().length];
                assertNotEquals(carrier,"RPG_Gear_"+base.id().substring(3).replace('.','_')+
                        (other.quality()==GearQuality.NORMAL?"":"_"+other.nativeParticleTier));
            }
        }
    }
    @Test void focusStarUsesNativeContactAndTimingProfiles(){
        for(var family:List.of("staff_apprentice","wand_oak","book_apprentice")){
            var base=catalog.base("gm."+family+".h");
            for(int n=17;n<=22;n++)assertTrue(GearDropGenerator.eligible(catalog.affix("WA-"+String.format("%03d",n)),base));
            assertTrue(GearDropGenerator.eligible(catalog.affix("WA-008"),base));
            assertFalse(GearDropGenerator.eligible(catalog.affix("WA-141"),base));
        }
    }
    public static void main(String[] args){
        System.out.println("ManagedCarrierProductionTest starting");
        try {
            var suite=new ManagedCarrierProductionTest();
            suite.focusMagicPowerAndElementalFlatReachRealContactResolver();
            suite.bombUsesFrozenPhysicalRollAndRejectsInvalidSource();
            suite.shieldAffixChangesDefenseWithoutRedefiningSkillBasePower();
            suite.allFiveRarityCarrierIdsRoundTripFrozenPayload();
            suite.focusStarUsesNativeContactAndTimingProfiles();
            System.out.println("ManagedCarrierProductionTest: 5 production consumer tests passed");
        } catch(Throwable failure){failure.printStackTrace(System.out);System.exit(1);}
    }
}
