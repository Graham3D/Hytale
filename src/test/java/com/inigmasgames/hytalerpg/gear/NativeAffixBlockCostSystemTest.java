package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeAffixBlockCostSystemTest {
    private static NativeAssetTestFixtures assets;
    @org.junit.jupiter.api.BeforeAll static void nativeAssets() throws Exception { assets=NativeAssetTestFixtures.open(); }
    @org.junit.jupiter.api.AfterAll static void releaseNativeAssets() { if(assets!=null)assets.close(); }
    private final GearCatalog catalog=GearCatalog.load();

    private GearEffectSnapshot guard(double value){
        var definition=catalog.affix("WA-083");
        var roll=new GearInstance.AffixRoll(definition.id(),definition.side(),definition.exclusionGroup(),
                1,value,new GearRequirements.Gate(1,Map.of()),definition.name(),definition.name());
        var item=GearInstance.authoredQa(catalog.base("gm.battleaxe_adamantite.h"),UUID.randomUUID(),99,1000,
                GearRarity.UNCOMMON,List.of(roll),BigDecimal.ZERO);
        return new GearEffectSnapshot(List.of(item));
    }
    private static Damage block(){
        var damage=new Damage(Damage.NULL_SOURCE,0,10f);
        damage.putMetaObject(Damage.BLOCKED,true);
        return damage;
    }
    @Test void nativeBlockDebitMultiplierIsChangedExactlyOnce(){
        var snapshot=guard(15);
        var hit=block();hit.putMetaObject(Damage.STAMINA_DRAIN_MULTIPLIER,2f);
        assertTrue(NativeAffixBlockCostSystem.apply(hit,snapshot));
        assertEquals(1.7f,hit.getIfPresentMetaObject(Damage.STAMINA_DRAIN_MULTIPLIER),.00001f);
        assertFalse(NativeAffixBlockCostSystem.apply(hit,snapshot));
        assertEquals(1.7f,hit.getIfPresentMetaObject(Damage.STAMINA_DRAIN_MULTIPLIER),.00001f);
    }
    @Test void noAffixFailedBlockAndCancelledDamageRetainNativeCost(){
        var noAffix=block();assertFalse(NativeAffixBlockCostSystem.apply(noAffix,GearEffectSnapshot.EMPTY));
        assertNull(noAffix.getIfPresentMetaObject(Damage.STAMINA_DRAIN_MULTIPLIER));
        var failed=new Damage(Damage.NULL_SOURCE,0,10f);
        assertFalse(NativeAffixBlockCostSystem.apply(failed,guard(15)));
        assertNull(failed.getIfPresentMetaObject(Damage.STAMINA_DRAIN_MULTIPLIER));
        var cancelled=block();cancelled.setCancelled(true);
        assertFalse(NativeAffixBlockCostSystem.apply(cancelled,guard(15)));
    }
    @Test void nativeGuardChainAttributesUtilityShieldInsteadOfHotbar(){
        var hotbar=new ItemStack("RPG_Gear_sword",1);
        var shield=new ItemStack("RPG_Gear_shield",1);
        var chain=new NativeAffixBlockCostSystem.GuardChain(InteractionType.Secondary,
                InteractionState.NotFinished,4,1,shield);
        var source=NativeAffixBlockCostSystem.guardSource(List.of(chain),1,2,hotbar,4,1,shield);
        assertSame(shield,source);
        var hit=block();
        assertTrue(NativeAffixBlockCostSystem.apply(hit,guard(15)));
        assertEquals(.85f,hit.getIfPresentMetaObject(Damage.STAMINA_DRAIN_MULTIPLIER),.00001f);
    }
    @Test void staleOrAmbiguousGuardChainCannotBorrowAnotherItemAffix(){
        var hotbar=new ItemStack("RPG_Gear_sword",1);
        var shield=new ItemStack("RPG_Gear_shield",1);
        var stale=new NativeAffixBlockCostSystem.GuardChain(InteractionType.Secondary,
                InteractionState.NotFinished,4,1,new ItemStack("RPG_Gear_old_shield",1));
        var active=new NativeAffixBlockCostSystem.GuardChain(InteractionType.Secondary,
                InteractionState.NotFinished,4,1,shield);
        assertNull(NativeAffixBlockCostSystem.guardSource(List.of(stale),1,2,hotbar,4,1,shield));
        assertNull(NativeAffixBlockCostSystem.guardSource(List.of(active,active),1,2,hotbar,4,1,shield));
        assertNull(NativeAffixBlockCostSystem.guardSource(List.of(new NativeAffixBlockCostSystem.GuardChain(
                InteractionType.Secondary,InteractionState.Finished,4,1,shield)),1,2,hotbar,4,1,shield));
        assertNull(NativeAffixBlockCostSystem.guardSource(List.of(active),1,2,hotbar,4,2,shield));
    }
}
