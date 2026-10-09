package com.inigmasgames.hytalerpg.ui.inventory;

import com.inigmasgames.hytalerpg.combat.attribute.*;
import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.gear.*;
import com.inigmasgames.hytalerpg.ui.model.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.execution.GearResourceModifiers;
import com.inigmasgames.hytalerpg.execution.GearSupportModifiers;
import com.inigmasgames.hytalerpg.execution.connection.ConnectionProfile;
import com.inigmasgames.hytalerpg.combat.status.GearStatusRuntime;
import static org.junit.jupiter.api.Assertions.*;

class AdvancedStatsAffixProjectionTest {
    private static GearInstance item(String id,double value) {
        var c=GearCatalog.load();var a=c.affix(id);
        return GearInstance.authoredQa(c.base("gm.plate_mithril.head.h"),UUID.randomUUID(),99,1000,
                GearRarity.MAGIC,List.of(new GearInstance.AffixRoll(id,a.side(),a.exclusionGroup(),1,value,
                        new GearRequirements.Gate(1,Map.of()),a.name(),a.name())),BigDecimal.ZERO);
    }
    private static CharacterSheetViewModel sheet(DerivedStats d,double health,double mana,double stamina) {
        return new CharacterSheetViewModel(1,"Fixture",new XpView(99,0,0,0,0,1,List.of()),0,0,d,
                new NativeResourceView(50,mana),new NativeResourceView(50,health),new NativeResourceView(50,stamina));
    }
    private static DerivedStatService derivedOwner() {
        var balance=CombatBalanceProfile.loadCanonical();
        return new DerivedStatService(balance,new EffectiveAttributeService(balance));
    }
    private static GearAffixRuntime.Effects effects(GearEffectSnapshot snapshot) {
        return new GearAffixRuntime.Effects(Map.of(),0,0,0,0,0,0,snapshot);
    }
    private static AdvancedStatsViewModel model(GearEffectSnapshot snapshot) {
        var d=derivedOwner().derive(Map.of());
        return AdvancedStatsViewModel.resolve(sheet(d,100,100,100),effects(snapshot),
                NativeArmorDefenseView.frozen(Map.of()),new HytaleGearEquipment.MagicFindBreakdown(0,0));
    }
    private static String row(AdvancedStatsViewModel model,String id) {
        return model.rows().stream().filter(r->r.descriptor().id().equals(id))
                .findFirst().orElseThrow(()->new AssertionError(id)).value();
    }
    private static boolean has(AdvancedStatsViewModel model,String id) {
        return model.rows().stream().anyMatch(r->r.descriptor().id().equals(id));
    }

    @ParameterizedTest(name="singleAcceptedAffixDisplaysItsAuthoritativeValueAndRestores({0})")
    @org.junit.jupiter.params.provider.ValueSource(strings={"WA-009","WA-010","WA-011","WA-012","WA-085","WA-086","WA-087","WA-088","WA-089","WA-090","WA-091","WA-092","WA-093","WA-151","GA-159","GA-160"})
    void singleAcceptedAffixDisplaysItsAuthoritativeValueAndRestores(String id) {
        var item=MasterAffixTestEquipment.fixture(id,false);
        // Reward modifiers on production items are separate from protected QA fixture drops.
        if(id.equals("WA-151"))item=new GearInstance(item.schemaVersion(),item.identity(),item.definitionRevision(),
                item.baseId(),item.baseName(),item.category(),item.sourceEra(),item.itemLevel(),item.rarity(),
                item.intrinsicThousandths(),item.intrinsicStats(),item.requirements(),item.affixes(),item.rngVersion(),false);
        var accepted=MasterAffixTestEquipment.accepted(item);
        var control=MasterAffixTestEquipment.accepted(MasterAffixTestEquipment.fixture(id,true));
        var removed=MasterAffixTestEquipment.accepted(null);
        var actual=qualifiedModel(accepted); var plain=qualifiedModel(control); var empty=qualifiedModel(removed);
        String field=switch(id){
            case "WA-009"->"cast";case "WA-010","WA-089"->"critical";case "WA-011"->"criticalDamage";
            case "WA-012"->"cooldown";case "WA-085","WA-090"->"heavy";case "WA-086"->"light";
            case "WA-087"->"magic";case "WA-088"->"healing";case "WA-091"->"health";
            case "WA-092"->"mana";case "WA-093"->"stamina";case "WA-151"->"find";default->"defense";};
        assertTrue(has(actual,field),id);
        if(id.equals("WA-009")){assertFalse(has(plain,field));assertFalse(has(empty,field));
            assertEquals(String.format(Locale.ROOT,"%+.1f%%",(2/accepted.windup(2)-1)*100),row(actual,field));}
        else assertNotEquals(row(plain,field),row(actual,field),id+" isolated change");
        assertEquals(row(qualifiedModel(GearAffixRuntime.Effects.NONE),"health"),row(empty,"health"));
        assertTrue(removed.snapshot().empty());
        if(id.equals("WA-085")) {
            var derived=accepted.derive(derivedOwner(),MasterAffixTestEquipment.BASELINE);
            var unrolled=control.derive(derivedOwner(),MasterAffixTestEquipment.BASELINE);
            var visible=sheet(derived,derived.maxHealth(),derived.maxMana(),derived.maxStamina());
            assertEquals(derived.raw(RpgAttribute.STR),visible.derivedStats().rawAttributes().get(RpgAttribute.STR),1e-9);
            assertEquals(item.affixes().getFirst().value(),
                    visible.derivedStats().raw(RpgAttribute.STR)-unrolled.raw(RpgAttribute.STR),1e-9);
            var withdrawn=removed.derive(derivedOwner(),MasterAffixTestEquipment.BASELINE);
            assertEquals(withdrawn.raw(RpgAttribute.STR),sheet(withdrawn,withdrawn.maxHealth(),
                    withdrawn.maxMana(),withdrawn.maxStamina()).derivedStats().raw(RpgAttribute.STR));
        }
        if(id.equals("WA-090"))for(var stat:RpgAttribute.values())
            assertTrue(accepted.derive(derivedOwner(),MasterAffixTestEquipment.BASELINE).raw(stat)>
                    control.derive(derivedOwner(),MasterAffixTestEquipment.BASELINE).raw(stat),stat.name());
    }
    private static AdvancedStatsViewModel qualifiedModel(GearAffixRuntime.Effects effects) {
        var d=effects.derive(derivedOwner(),MasterAffixTestEquipment.BASELINE);
        double armor=effects.snapshot().items().stream().filter(i->i.category()==GearCatalog.Category.ARMOR)
                .mapToDouble(GearAffixRuntime::protection).sum();
        var defense=GearDefenseEffects.resolve(effects.snapshot(),99,Math.min(.6,armor/100),0,0);
        double nativePercent=Math.round(defense.managedProtection()*1000)/10.0;
        var mf=HytaleGearEquipment.magicFindBreakdown((int)d.raw(RpgAttribute.LUCK),effects.snapshot().items());
        var model=AdvancedStatsViewModel.resolve(sheet(d,d.maxHealth(),d.maxMana(),d.maxStamina()),effects,
                NativeArmorDefenseView.frozen(Map.of("Physical",nativePercent)),mf);
        assertEquals(String.format(Locale.ROOT,"%.1f%%",d.criticalChance()*100),row(model,"critical"));
        assertEquals(String.format(Locale.ROOT,"%.1f%%",d.criticalMultiplier()*100),row(model,"criticalDamage"));
        assertEquals(String.format(Locale.ROOT,"%.1f%%",d.cooldownRecovery()*100),row(model,"cooldown"));
        assertEquals(String.format(Locale.ROOT,"%+.1f%%",(d.heavyDamageMultiplier()-1)*100),row(model,"heavy"));
        assertEquals(String.format(Locale.ROOT,"%+.1f%%",(d.lightDamageMultiplier()-1)*100),row(model,"light"));
        assertEquals(String.format(Locale.ROOT,"%+.1f%%",(d.magicDamageMultiplier()-1)*100),row(model,"magic"));
        assertEquals(String.format(Locale.ROOT,"%+.1f%%",(d.healingMultiplier()-1)*100),row(model,"healing"));
        assertEquals(d.maxHealth(),Double.parseDouble(row(model,"health")),.05);
        assertEquals(d.maxMana(),Double.parseDouble(row(model,"mana")),.05);
        assertEquals(d.maxStamina(),Double.parseDouble(row(model,"stamina")),.05);
        assertEquals(String.format(Locale.ROOT,"%.1f%%",nativePercent),row(model,"defense"));
        assertEquals(String.format(Locale.ROOT,"%.1f%%",mf.total()*100),row(model,"find"));
        return model;
    }

    @Test void derivedCriticalCooldownAttributesAndNativeCapacitiesMatchOwnerAndRestore() {
        var owner=derivedOwner();var baseline=owner.derive(Map.of());
        var snapshot=new GearEffectSnapshot(List.of(item("WA-010",10),item("WA-011",25),
                item("WA-012",15),item("WA-085",3),item("WA-091",20)));
        var equipped=new GearAffixRuntime.Effects(Map.of(RpgAttribute.STR,3),20,0,0,0,.15,0,snapshot);
        var actual=equipped.derive(owner,baseline.rawAttributes());
        var projection=AdvancedStatsViewModel.resolve(sheet(actual,actual.maxHealth(),actual.maxMana(),actual.maxStamina()),
                equipped,NativeArmorDefenseView.frozen(Map.of()),new HytaleGearEquipment.MagicFindBreakdown(0,0));
        assertEquals(String.format(Locale.ROOT,"%.1f%%",actual.criticalChance()*100),row(projection,"critical"));
        assertEquals(String.format(Locale.ROOT,"%.1f%%",actual.criticalMultiplier()*100),row(projection,"criticalDamage"));
        assertEquals(String.format(Locale.ROOT,"%.1f%%",actual.cooldownRecovery()*100),row(projection,"cooldown"));
        assertEquals(actual.maxHealth(),Double.parseDouble(row(projection,"health")),.05);
        assertEquals(baseline.rawAttributes().getOrDefault(RpgAttribute.STR,0)+3,
                actual.rawAttributes().get(RpgAttribute.STR));
        var removed=AdvancedStatsViewModel.resolve(sheet(baseline,baseline.maxHealth(),baseline.maxMana(),baseline.maxStamina()),
                GearAffixRuntime.Effects.NONE,NativeArmorDefenseView.frozen(Map.of()),
                new HytaleGearEquipment.MagicFindBreakdown(0,0));
        assertEquals(String.format(Locale.ROOT,"%.1f%%",baseline.criticalChance()*100),row(removed,"critical"));
        assertEquals(String.format(Locale.ROOT,"%.1f%%",baseline.cooldownRecovery()*100),row(removed,"cooldown"));
        assertEquals(baseline.maxHealth(),Double.parseDouble(row(removed,"health")),.05);
        var capSnapshot=new GearEffectSnapshot(List.of(item("WA-010",100),item("WA-011",50)));
        var cappedEffects=new GearAffixRuntime.Effects(Map.of(),0,0,0,0,1,0,capSnapshot);
        var capped=cappedEffects.derive(owner,baseline.rawAttributes());
        var capModel=AdvancedStatsViewModel.resolve(sheet(capped,capped.maxHealth(),capped.maxMana(),capped.maxStamina()),
                cappedEffects,NativeArmorDefenseView.frozen(Map.of()),new HytaleGearEquipment.MagicFindBreakdown(0,0));
        assertEquals(CombatBalanceProfile.loadCanonical().criticalChanceCap,capped.criticalChance(),1e-9);
        assertEquals(CombatBalanceProfile.loadCanonical().cooldownRecoveryCap,capped.cooldownRecovery(),1e-9);
        assertEquals(String.format(Locale.ROOT,"%.1f%%",capped.criticalChance()*100),row(capModel,"critical"));
        assertEquals(String.format(Locale.ROOT,"%.1f%%",capped.cooldownRecovery()*100),row(capModel,"cooldown"));
    }

    @Test void acceptedEffectsCollectionProjectsCurrentCooldownAttributeAndCapacity() {
        var owner=derivedOwner();var baseline=owner.derive(Map.of());
        var accepted=GearAffixRuntime.effects(List.of(item("WA-012",10),item("WA-085",2),item("WA-091",15)));
        assertEquals(3,accepted.snapshot().items().size());
        var derived=accepted.derive(owner,baseline.rawAttributes());
        var projected=AdvancedStatsViewModel.resolve(sheet(derived,derived.maxHealth(),derived.maxMana(),derived.maxStamina()),
                accepted,NativeArmorDefenseView.frozen(Map.of()),new HytaleGearEquipment.MagicFindBreakdown(0,0));
        assertEquals(String.format(Locale.ROOT,"%.1f%%",derived.cooldownRecovery()*100),row(projected,"cooldown"));
        assertEquals(derived.maxHealth(),Double.parseDouble(row(projected,"health")),.05);
        assertEquals(baseline.raw(RpgAttribute.STR)+2,derived.raw(RpgAttribute.STR));
        var removed=GearAffixRuntime.effects(List.of());
        assertTrue(removed.snapshot().empty());
        assertEquals(baseline.cooldownRecovery(),removed.derive(owner,baseline.rawAttributes()).cooldownRecovery(),1e-9);
    }

    @Test void everyGlobalGearRowUsesFrozenOperatorValueAndVanishesOnUnequip() {
        // Each row is an actor-wide input read by its named gameplay owner. Source-local
        // flats/conversions/status procs are deliberately absent from this global view.
        var cases=Map.ofEntries(
                Map.entry("spellDamage","WA-004"),Map.entry("physicalDamage","WA-006"),
                Map.entry("dotDamage","WA-013"),Map.entry("statusDuration","WA-068"),
                Map.entry("statusPenetration","WA-064"),Map.entry("statusResistance","WA-079"),
                Map.entry("manaRegen","WA-099"),Map.entry("staminaRegen","WA-100"),
                Map.entry("healingDone","WA-104"),Map.entry("healingReceived","WA-111"),
                Map.entry("barrierStrength","WA-105"),Map.entry("supportDuration","WA-110"),
                Map.entry("tetherReach","WA-106"),Map.entry("channelRamp","WA-108"),
                Map.entry("minionDamage","WA-113"),Map.entry("minionHealth","WA-114"),
                Map.entry("minionDefense","WA-115"),Map.entry("minionResistance","WA-116"),
                Map.entry("minionAttackSpeed","WA-117"),Map.entry("minionMovement","WA-118"),
                Map.entry("minionDuration","WA-119"),Map.entry("currencyQuantity","WA-152"));
        var empty=model(GearEffectSnapshot.EMPTY);
        for(var entry:cases.entrySet()) {
            var snapshot=new GearEffectSnapshot(List.of(item(entry.getValue(),15)));
            var operator=GearEffectSnapshot.Operator.valueOf(GearCatalog.load().affix(entry.getValue()).operator());
            assertEquals("+15.0%",row(model(snapshot),entry.getKey()),entry.toString());
            assertEquals(.15,snapshot.percent(operator),1e-9,entry.toString());
            assertFalse(has(empty,entry.getKey()),entry.toString());
        }
        assertEquals("+8",row(model(new GearEffectSnapshot(List.of(item("WA-103",8)))),"healingPower"));
        assertEquals("+0.80 m",row(model(new GearEffectSnapshot(List.of(item("WA-156",.8)))),"pickupReachBonus"));
        assertFalse(has(empty,"healingPower"));assertFalse(has(empty,"pickupReachBonus"));
        assertFalse(has(model(new GearEffectSnapshot(List.of(item("WA-155",1)))),"lightRadius"));
    }

    @Test void resourceRowsUsePaymentOwnerIncludingStackedReductionsAndZeroControl() {
        var snapshot=new GearEffectSnapshot(List.of(item("WA-101",10),item("WA-102",15),
                item("WA-107",20),item("WA-120",25)));
        var equipped=model(snapshot);var removed=model(GearEffectSnapshot.EMPTY);
        assertEquals("+10.0%",row(equipped,"manaCost"));
        assertEquals("+15.0%",row(equipped,"staminaCost"));
        assertEquals("+30.0%",row(equipped,"channelCost"));
        assertEquals("+35.0%",row(equipped,"summonCost"));
        assertEquals(.30,GearResourceModifiers.reduction(snapshot,ResourceType.MANA,true,false),1e-9);
        assertEquals(.35,GearResourceModifiers.reduction(snapshot,ResourceType.MANA,false,true),1e-9);
        assertEquals(.15,GearResourceModifiers.reduction(snapshot,ResourceType.STAMINA,false,false),1e-9);
        for(String id:List.of("manaCost","staminaCost","channelCost","summonCost"))assertFalse(has(removed,id));
    }

    @Test void elementalRowsMatchCommittedHitBucketsAndRestoreForAllSixChannels() {
        for(var channel:GearCombatEffects.Channel.values()) if(channel!=GearCombatEffects.Channel.PHYSICAL) {
            String name=channel.name().charAt(0)+channel.name().substring(1).toLowerCase(Locale.ROOT);
            String specific="WA-"+String.format(Locale.ROOT,"%03d",22+channel.ordinal());
            String penetration="WA-"+String.format(Locale.ROOT,"%03d",28+channel.ordinal());
            var snapshot=new GearEffectSnapshot(List.of(item("WA-007",10),item(specific,15),item(penetration,20)));
            var hit=GearCombatEffects.attack(snapshot,null,"ui-"+name,100,1,false,false,0,null,0,1.5,false);
            assertEquals("+25.0%",row(model(snapshot),"damage"+name));
            assertEquals("+20.0%",row(model(snapshot),"penetration"+name));
            assertEquals(.25,hit.increased(channel),1e-9);
            assertEquals(.20,hit.penetration(channel),1e-9);
            assertFalse(has(model(GearEffectSnapshot.EMPTY),"damage"+name));
            assertFalse(has(model(GearEffectSnapshot.EMPTY),"penetration"+name));
        }
    }
    @Test void statusSupportAndPickupRowsAgreeWithAdmissionAndReachOwners() {
        var penetration=new GearEffectSnapshot(List.of(item("WA-064",15)));
        var resistance=new GearEffectSnapshot(List.of(item("WA-079",25)));
        assertEquals("+15.0%",row(model(penetration),"statusPenetration"));
        assertEquals("+25.0%",row(model(resistance),"statusResistance"));
        var chance=GearStatusRuntime.chance(.5,0,1,.2,penetration,resistance);
        assertEquals(.30,chance.effectiveResistance(),1e-9);
        assertEquals(.35,chance.any(),1e-9);
        assertEquals(.40,GearStatusRuntime.chance(.5,0,1,.2,GearEffectSnapshot.EMPTY,
                GearEffectSnapshot.EMPTY).any(),1e-9);
        var tether=new ConnectionProfile(ConnectionProfile.Kind.TETHER,10,1,1,0,0,10,0,1,0,0,0,
                "PHYSICAL",ConnectionProfile.Details.NONE);
        var reach=new GearEffectSnapshot(List.of(item("WA-106",25)));
        assertEquals("+25.0%",row(model(reach),"tetherReach"));
        assertEquals(12.5,GearSupportModifiers.primaryReach(tether,reach),1e-9);
        assertEquals(10,GearSupportModifiers.primaryReach(tether,GearEffectSnapshot.EMPTY),1e-9);
        var pickup=new GearEffectSnapshot(List.of(item("WA-156",.75)));
        assertEquals("+0.75 m",row(model(pickup),"pickupReachBonus"));
        assertEquals(2.5,NativeAffixMaterialPickup.reach(pickup,1.75),1e-9);
        assertEquals(1.75,NativeAffixMaterialPickup.reach(GearEffectSnapshot.EMPTY,1.75),1e-9);
        for(String id:List.of("statusPenetration","statusResistance","tetherReach","pickupReachBonus"))
            assertFalse(has(model(GearEffectSnapshot.EMPTY),id));
    }
    @ParameterizedTest(name="equipmentResistanceUsesCombatResolverAndReturnsToNativeBaseline({0})")
    @EnumSource(value=GearCombatEffects.Channel.class,names="PHYSICAL",mode=EnumSource.Mode.EXCLUDE)
    void equipmentResistanceUsesCombatResolverAndReturnsToNativeBaseline(GearCombatEffects.Channel channel) {
        String affix="WA-"+String.format(Locale.ROOT,"%03d",71+channel.ordinal());
        var snapshot=new GearEffectSnapshot(List.of(item(affix,15),item("WA-078",10)));
        var effects=new GearAffixRuntime.Effects(Map.of(),0,0,0,0,0,0,snapshot);
        var balance=CombatBalanceProfile.loadCanonical();
        var d=new DerivedStatService(balance,new EffectiveAttributeService(balance)).derive(Map.of());
        var sheet=new CharacterSheetViewModel(1,"Fixture",new XpView(99,0,0,0,0,1,List.of()),0,0,d,
                new NativeResourceView(50,100),new NativeResourceView(50,100),new NativeResourceView(50,100));
        var defense=NativeArmorDefenseView.frozen(Map.of(GearCombatEffects.nativeCause(channel),10d));
        var model=AdvancedStatsViewModel.resolve(sheet,effects,defense,new HytaleGearEquipment.MagicFindBreakdown(0,0));
        String title=channel.name().charAt(0)+channel.name().substring(1).toLowerCase(Locale.ROOT);
        var row=model.rows().stream().filter(r->r.descriptor().id().equals("resist"+title)).findFirst().orElseThrow();
        assertEquals("35.0%",row.value());
        assertEquals(65,GearCombatEffects.resisted(100,snapshot,channel,.10,0),1e-8);
        var removed=AdvancedStatsViewModel.resolve(sheet,GearAffixRuntime.Effects.NONE,defense,new HytaleGearEquipment.MagicFindBreakdown(0,0));
        assertEquals("10.0%",removed.rows().stream().filter(r->r.descriptor().id().equals("resist"+title)).findFirst().orElseThrow().value());
        assertEquals(90,GearCombatEffects.resisted(100,GearEffectSnapshot.EMPTY,channel,.10,0),1e-8);
        var capped=AdvancedStatsViewModel.resolve(sheet,effects,NativeArmorDefenseView.frozen(
                Map.of(GearCombatEffects.nativeCause(channel),70d)),new HytaleGearEquipment.MagicFindBreakdown(0,0));
        assertEquals("75.0%",capped.rows().stream().filter(r->r.descriptor().id().equals("resist"+title)).findFirst().orElseThrow().value());
    }
}
