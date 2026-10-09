package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class NativeAffixDurabilitySystemsTest {
    private static NativeAssetTestFixtures assets;
    @org.junit.jupiter.api.BeforeAll static void nativeAssets() throws Exception { assets=NativeAssetTestFixtures.open(); }
    @org.junit.jupiter.api.AfterAll static void releaseNativeAssets() { if(assets!=null)assets.close(); }
    private final GearCatalog catalog=GearCatalog.load();
    private GearEffectSnapshot lasting(double value) {
        var definition=catalog.affix("WA-153");
        var roll=new GearInstance.AffixRoll(definition.id(),definition.side(),definition.exclusionGroup(),
                1,value,new GearRequirements.Gate(1,Map.of()),definition.name(),definition.name());
        var item=GearInstance.authoredQa(catalog.base("gm.battleaxe_adamantite.h"),UUID.randomUUID(),99,1000,
                GearRarity.UNCOMMON,List.of(roll),BigDecimal.ZERO);
        return new GearEffectSnapshot(List.of(item));
    }
    private static Damage hit(){return new Damage(Damage.NULL_SOURCE,0,1f);}

    @Test void managedLossIsPreventedBeforeTheNativeStackWouldBreak() {
        var snapshot=lasting(30);
        var stack=new AtomicReference<>(new ItemStack("RPG_Gear_battleaxe_adamantite_h",1,1,225,null));
        var calls=new AtomicInteger();
        Runnable nativeDebit=()->{calls.incrementAndGet();stack.updateAndGet(s->s.withIncreasedDurability(-1));};
        var damage=hit();
        assertTrue(NativeAffixDurabilitySystems.debit(damage,NativeAffixDurabilitySystems.ATTACKER_DONE,
                snapshot,()->.29,nativeDebit));
        assertEquals(1,stack.get().getDurability());
        assertEquals(0,calls.get());
        assertFalse(NativeAffixDurabilitySystems.debit(damage,NativeAffixDurabilitySystems.ATTACKER_DONE,
                snapshot,()->.99,nativeDebit));
        assertEquals(0,calls.get(),"one damage event is one loss opportunity");
    }

    @Test void failedRollAndUnaffixedControlTakeTheFullNativeDebit() {
        var stack=new AtomicReference<>(new ItemStack("RPG_Gear_battleaxe_adamantite_h",1,1,225,null));
        Runnable nativeDebit=()->stack.updateAndGet(s->s.withIncreasedDurability(-1));
        assertFalse(NativeAffixDurabilitySystems.debit(hit(),NativeAffixDurabilitySystems.ATTACKER_DONE,
                lasting(30),()->.30,nativeDebit));
        assertTrue(stack.get().isBroken());
        stack.set(new ItemStack("RPG_Gear_battleaxe_adamantite_h",1,1,225,null));
        assertFalse(NativeAffixDurabilitySystems.debit(hit(),NativeAffixDurabilitySystems.ATTACKER_DONE,
                GearEffectSnapshot.EMPTY,()->{fail("stock path must not roll");return 0;},nativeDebit));
        assertTrue(stack.get().isBroken());
    }

    @Test void armorAndAttackerUseSeparateNativeOpportunities() {
        var damage=hit();var calls=new AtomicInteger();
        assertFalse(NativeAffixDurabilitySystems.debit(damage,NativeAffixDurabilitySystems.ARMOR_DONE,
                GearEffectSnapshot.EMPTY,()->0,calls::incrementAndGet));
        assertFalse(NativeAffixDurabilitySystems.debit(damage,NativeAffixDurabilitySystems.ATTACKER_DONE,
                GearEffectSnapshot.EMPTY,()->0,calls::incrementAndGet));
        assertEquals(2,calls.get());
        assertFalse(NativeAffixDurabilitySystems.debit(damage,NativeAffixDurabilitySystems.ARMOR_DONE,
                GearEffectSnapshot.EMPTY,()->0,calls::incrementAndGet));
        assertEquals(2,calls.get());
    }
    @Test void attackerNeedsTheActualManagedHitItemIdentity() {
        var candidate=lasting(30);
        var missing=hit();
        assertSame(GearEffectSnapshot.EMPTY,NativeAffixDurabilitySystems.attackerSource(missing,candidate));
        var wrong=hit();
        var wrongHit=new GearCombatEffects.Hit(UUID.randomUUID(),candidate.revision(),"wrong",Map.of(),
                Map.of(),Map.of(),false,1,candidate,null);
        HytaleDamageAdapter.attachGearHit(wrong,new HytaleDamageAdapter.GearHitSource(
                wrongHit,"wrong-contact",GearCombatEffects.Channel.PHYSICAL));
        assertSame(GearEffectSnapshot.EMPTY,NativeAffixDurabilitySystems.attackerSource(wrong,candidate));
        var correct=hit();
        var rightHit=new GearCombatEffects.Hit(candidate.items().getFirst().identity(),candidate.revision(),
                "right",Map.of(),Map.of(),Map.of(),false,1,candidate,null);
        HytaleDamageAdapter.attachGearHit(correct,new HytaleDamageAdapter.GearHitSource(
                rightHit,"right-contact",GearCombatEffects.Channel.PHYSICAL));
        assertSame(candidate,NativeAffixDurabilitySystems.attackerSource(correct,candidate));
    }
    @Test void nativeArmorCandidateSelectionExcludesBrokenItemsButKeepsStockItems() {
        var inventory=new SimpleItemContainer((short)3);
        var broken=new ItemStack("RPG_Gear_armor",1,0,100,null);
        var stock=new ItemStack("Armor_Stock",1,1,100,null);
        var managed=new ItemStack("RPG_Gear_armor",1,1,100,null);
        assertTrue(inventory.setItemStackForSlot((short)0,broken).succeeded());
        assertTrue(inventory.setItemStackForSlot((short)1,stock).succeeded());
        assertTrue(inventory.setItemStackForSlot((short)2,managed).succeeded());
        assertEquals(1,NativeAffixDurabilitySystems.armorSlot(inventory,count->{assertEquals(2,count);return 0;}));
        assertEquals(2,NativeAffixDurabilitySystems.armorSlot(inventory,count->{assertEquals(2,count);return 1;}));
    }
}
