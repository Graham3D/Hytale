package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.gear.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The existing decoy is unchanged; the ordinary finite-payment owner consumes WA-120. */
class SimulacrumFiniteAffixTest {
    private static GearEffectSnapshot discounted() {
        var catalog=GearCatalog.load();var base=catalog.base("gm.staff_prismatic.h");
        var affix=catalog.affix("WA-120");var tier=GearAffixTiers.compile(affix).getLast();
        var roll=new GearInstance.AffixRoll(affix.id(),affix.side(),affix.exclusionGroup(),tier.tier(),16,
                new GearRequirements.Gate(tier.requiredLevel(),Map.of(affix.requirementAttribute(base),
                        affix.attributeFloor(5-tier.tier(),tier.minimumItemLevel()))),affix.name(),affix.name());
        return new GearEffectSnapshot(List.of(GearInstance.authoredQa(base,UUID.randomUUID(),99,1000,
                GearRarity.MAGIC,List.of(roll),BigDecimal.ZERO)));
    }
    private static final class Harness extends Stage10SummonTest.Harness {
        GearEffectSnapshot gear;
        Harness(GearEffectSnapshot gear){super("simulacrum");this.gear=gear;}
        @Override public GearAffixRuntime.Effects gearEffects(){
            return new GearAffixRuntime.Effects(Map.of(),0,0,0,0,0,0,gear);
        }
    }
    @Test void finiteTwentyManaCostIsDiscountedWithoutChangingDecoyMechanics() {
        var control=new Harness(GearEffectSnapshot.EMPTY);var affixed=new Harness(discounted());
        assertTrue(control.cast().committed());assertTrue(affixed.cast().committed());
        assertEquals(80,control.mana);assertEquals(83,affixed.mana);
        assertEquals(20,affixed.context.profile().resourceCost());
        assertEquals(control.context.profile(),affixed.context.profile());
        var normal=control.leases.getFirst();var modified=affixed.leases.getFirst();
        assertEquals(normal.maximumHealth(),modified.maximumHealth());
        assertEquals(normal.expires(),modified.expires());assertEquals(normal.roleId(),modified.roleId());
        assertFalse(affixed.cast().committed());assertEquals(83,affixed.mana);
    }
    @Test void insufficientFinitePaymentDoesNotCreateDecoyAndUnequippedSourceDoesNotDiscount() {
        var affixed=new Harness(discounted());affixed.mana=16;
        assertFalse(affixed.cast().committed());assertEquals(16,affixed.mana);assertEquals(0,affixed.summons.size());
        var control=new Harness(discounted());control.gear=GearEffectSnapshot.EMPTY;control.mana=19;
        assertFalse(control.cast().committed());assertEquals(19,control.mana);assertEquals(0,control.summons.size());
        var exact=new Harness(discounted());exact.mana=17;
        assertTrue(exact.cast().committed());assertEquals(0,exact.mana);assertEquals(1,exact.summons.size());
    }
}
