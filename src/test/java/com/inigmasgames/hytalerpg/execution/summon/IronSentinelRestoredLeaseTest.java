package com.inigmasgames.hytalerpg.execution.summon;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.gear.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class IronSentinelRestoredLeaseTest {
    private static com.inigmasgames.hytalerpg.gear.NativeAssetTestFixtures nativeAssets;
    @org.junit.jupiter.api.BeforeAll static void installNativeAssets() throws Exception {
        nativeAssets = com.inigmasgames.hytalerpg.gear.NativeAssetTestFixtures.open();
    }
    @org.junit.jupiter.api.AfterAll static void closeNativeAssets() {
        if (nativeAssets != null) nativeAssets.close();
    }

    @Test void boundElementalSourceBuildsOneAuthenticatedNativeChannel(){
        var catalog=GearCatalog.load();var base=catalog.base("gm.staff_prismatic.h");
        var affix=catalog.affix("WA-017");
        var roll=new GearInstance.AffixRoll("WA-017",affix.side(),affix.exclusionGroup(),1,6,
                new GearRequirements.Gate(1,Map.of()),"Bound wind",affix.name());
        var bound=GearInstance.authoredQa(base,UUID.randomUUID(),95,1000,GearRarity.MAGIC,
                List.of(roll),BigDecimal.ZERO);
        assertDoesNotThrow(()->IronSentinelAffixes.requireAdapted(bound));
        var binding=new IronSentinelBinding(3,UUID.randomUUID(),UUID.randomUUID(),"event",bound,
                IronSentinelBinding.State.RESTORING,40,UUID.randomUUID(),Vec3.ZERO,1,0,25,1,2,List.of());
        var registry=new SummonRegistry();var lease=registry.restoreIronSentinel(binding,10);
        UUID actor=UUID.randomUUID();assertTrue(registry.activate(lease,actor,10));
        String root=lease.rootCastId()+"/attack/1",contact=lease.correlationId()+"/attack/1";
        var hit=GearCombatEffects.attack(lease.boundEffects(),bound.identity(),root,20,lease.coefficient(),
                true,false,0,null,0,1.5,false);
        assertEquals(6*lease.coefficient(),hit.amount(GearCombatEffects.Channel.WIND),1e-9);
        assertEquals(20*lease.coefficient(),hit.amount(GearCombatEffects.Channel.PHYSICAL),1e-9);
        var metadata=new com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata(actor,root,
                lease.skillInstanceId(),contact,hit.amount(GearCombatEffects.Channel.WIND),Double.NaN,
                lease.token()+"/attack/1",true,
                com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata.Origin.DIRECT);
        var witness=new com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.GearHitSource(
                hit,contact,GearCombatEffects.Channel.WIND);
        assertTrue(com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem.authorizedSentinelAttack(
                lease,metadata,witness));
        assertFalse(com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem.authorizedSentinelAttack(
                lease,new com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata(lease.owner(),root,
                        lease.skillInstanceId(),contact,metadata.preMitigationDamage(),Double.NaN,
                        metadata.effectInstanceId(),true,metadata.origin()),witness));
    }
    @Test void versionThreeBindingRetainsAcceptedOwnerSourceAcrossDormancyAndRestore(){
        var catalog=GearCatalog.load();
        var boundBase=catalog.base("gm.sword_iron.n");
        var bound=GearInstance.authoredQa(boundBase,UUID.randomUUID(),boundBase.sourceWindow().getLast(),1000,
                GearRarity.COMMON,List.of(),BigDecimal.ZERO);
        var ownerBase=catalog.base("gm.staff_oracle.h");
        var affix=catalog.affix("WA-116");
        var roll=new GearInstance.AffixRoll("WA-116",affix.side(),affix.exclusionGroup(),1,10,
                new GearRequirements.Gate(80,Map.of(com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.WIS,85)),
                "Restored source",affix.name());
        var defenseAffix=catalog.affix("WA-115");
        var defenseRoll=new GearInstance.AffixRoll("WA-115",defenseAffix.side(),defenseAffix.exclusionGroup(),1,20,
                new GearRequirements.Gate(80,Map.of(com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.WIS,85)),
                "Restored defense source",defenseAffix.name());
        var equipped=GearInstance.authoredQa(ownerBase,UUID.randomUUID(),ownerBase.sourceWindow().getLast(),1000,
                GearRarity.RARE,List.of(roll,defenseRoll),BigDecimal.ZERO);
        UUID owner=UUID.randomUUID(),world=UUID.randomUUID();
        var created=new IronSentinelBinding(3,UUID.randomUUID(),owner,"event",bound,
                IronSentinelBinding.State.PREPARED,40,world,new Vec3(0,70,0),1,0,25,1.25,2,List.of(equipped));
        var dormant=created.withState(IronSentinelBinding.State.DORMANT,30,world,new Vec3(1,70,0));
        var restoring=dormant.withState(IronSentinelBinding.State.RESTORING,30,world,new Vec3(2,70,0));
        restoring=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(restoring),IronSentinelBinding.class);
        var lease=new SummonRegistry().restoreIronSentinel(restoring,10);
        assertEquals(3,restoring.schemaVersion());
        assertEquals(equipped.identity(),lease.ownerEffects().sources(GearEffectSnapshot.Operator.MINION_RESISTANCE).getFirst().itemId());
        assertEquals(10,lease.ownerEffects().value("WA-116"),1e-9);
        var view=com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem.sentinelDefenseView(lease);
        var baseline=com.inigmasgames.hytalerpg.gear.GearDefenseEffects.resolve(GearEffectSnapshot.EMPTY,25,
                lease.sentinelStats().finalProtection(),0,0);
        assertEquals(baseline.totalRating()*1.2,
                view.totalRating(),1e-9);
        assertEquals(view.managedProtection(),
                com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem.incomingResistance(lease,"Physical"),1e-9);
        assertEquals(.10,
                com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem.incomingResistance(lease,"Ice"),1e-9);
        assertTrue(com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem.incomingResistance(lease,"Physical")
                >lease.sentinelStats().finalProtection());
        assertThrows(IllegalArgumentException.class,()->new IronSentinelBinding(3,UUID.randomUUID(),owner,"event",bound,
                IronSentinelBinding.State.PREPARED,40,world,new Vec3(0,70,0),1,0,25,1.25,2,List.of(bound)));
        var legacy=new IronSentinelBinding(2,UUID.randomUUID(),owner,"legacy",bound,
                IronSentinelBinding.State.RESTORING,40,world,new Vec3(0,70,0),1,0,25,1.25,2);
        var legacyJson=com.google.gson.JsonParser.parseString(new com.google.gson.Gson().toJson(legacy)).getAsJsonObject();
        legacyJson.remove("ownerItems");
        var readLegacy=new com.google.gson.Gson().fromJson(legacyJson,IronSentinelBinding.class);
        assertTrue(new SummonRegistry().restoreIronSentinel(readLegacy,10).ownerEffects().empty());
    }
    @Test void restoredLeaseRetainsOwnerBoundItemRankPowerAndOneAttackClock(){
        var base=GearCatalog.load().base("gm.sword_iron.n");
        var item=GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.COMMON,List.of(),BigDecimal.ZERO);
        UUID owner=UUID.randomUUID(),world=UUID.randomUUID(),instance=UUID.randomUUID();
        var binding=new IronSentinelBinding(2,instance,owner,"event",item,IronSentinelBinding.State.RESTORING,
                42,world,new Vec3(0,70,0),System.currentTimeMillis(),4,25,1.25,2);
        var registry=new SummonRegistry();var lease=registry.restoreIronSentinel(binding,10);
        assertEquals(owner,lease.owner());assertEquals(world,lease.world());assertEquals(item,lease.boundItem());
        assertEquals(item.identity(),lease.boundEffects().items().getFirst().identity());
        assertEquals(25,lease.sentinelStats().effectiveLevel());
        assertEquals(IronSentinelStatProjection.project(25,item,2).finalMaxHealth()*1.25,lease.maximumHealth(),1e-9);
        assertEquals(1.25,lease.coefficient(),1e-9);
        assertThrows(IllegalStateException.class,()->registry.restoreIronSentinel(binding,10));
        assertTrue(registry.activate(lease,UUID.randomUUID(),10));
        assertEquals(0,registry.claimAttack(lease.token(),10));
        assertEquals(1,registry.claimAttack(lease.token(),12));
        assertEquals(0,registry.claimAttack(lease.token(),12));
        assertEquals(1,registry.iron(owner).stream().count());
    }

    @Test void nativeSentinelAttackWitnessAuthenticatesActorAndBoundSourceWithoutOwnerOnlyLeak(){
        var catalog=GearCatalog.load();var base=catalog.base("gm.staff_oracle.h");var affix=catalog.affix("WA-113");
        var ownerOnly=new GearInstance.AffixRoll("WA-113",affix.side(),affix.exclusionGroup(),1,25,
                new GearRequirements.Gate(80,Map.of(com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.WIS,85)),
                "Owner only",affix.name());
        var bound=GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.MAGIC,List.of(ownerOnly),BigDecimal.ZERO);
        var control=GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.COMMON,List.of(),BigDecimal.ZERO);
        UUID owner=UUID.randomUUID(),world=UUID.randomUUID(),actor=UUID.randomUUID();
        var binding=new IronSentinelBinding(3,UUID.randomUUID(),owner,"event",bound,
                IronSentinelBinding.State.RESTORING,40,world,Vec3.ZERO,1,0,25,1,2,List.of());
        var registry=new SummonRegistry();var lease=registry.restoreIronSentinel(binding,10);
        assertTrue(registry.activate(lease,actor,10));
        String root=lease.rootCastId()+"/attack/1",contact=lease.correlationId()+"/attack/1";
        var hit=GearCombatEffects.attack(lease.boundEffects(),bound.identity(),root,20,lease.coefficient(),
                true,false,0,null,0,1.5,false);
        var baseline=GearCombatEffects.attack(new GearEffectSnapshot(List.of(control)),control.identity(),root,20,
                lease.coefficient(),true,false,0,null,0,1.5,false);
        assertEquals(baseline.amount(GearCombatEffects.Channel.PHYSICAL),hit.amount(GearCombatEffects.Channel.PHYSICAL),1e-9);
        var meta=new com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata(actor,root,
                lease.skillInstanceId(),contact,hit.amount(GearCombatEffects.Channel.PHYSICAL),Double.NaN,
                lease.token()+"/attack/1",true,
                com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata.Origin.DIRECT);
        var nativeHit=com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.prepareGearDamage(
                null, com.hypixel.hytale.server.core.modules.entity.damage.DamageCause.PHYSICAL,
                meta, hit, GearCombatEffects.Channel.PHYSICAL, null, null);
        assertTrue(Double.isNaN(com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.metadata(nativeHit).targetHealthBefore()));
        assertFalse(nativeHit.getIfPresentMetaObject(com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.RPG_METADATA).contains("NaN"));
        assertTrue(com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem.authorizedSentinelAttack(lease,
                com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.metadata(nativeHit),
                com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.gearHit(nativeHit)));
        var foreign=new com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata(owner,root,
                meta.skillInstanceId(),contact,meta.preMitigationDamage(),Double.NaN,meta.effectInstanceId(),true,meta.origin());
        assertFalse(com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem.authorizedSentinelAttack(lease,foreign,
                com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.gearHit(nativeHit)));
        assertFalse(com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem.authorizedSentinelAttack(lease,meta,null));
        assertEquals(bound.identity(),lease.boundEffects().items().getFirst().identity());
    }
}
