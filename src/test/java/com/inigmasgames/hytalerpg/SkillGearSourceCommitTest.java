package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import com.inigmasgames.hytalerpg.execution.SkillExecutionPort;
import com.inigmasgames.hytalerpg.execution.SkillExecutionResult;
import com.inigmasgames.hytalerpg.gear.*;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the paid SkillExecutionService acceptance, not a constructed context alone. */
class SkillGearSourceCommitTest {
    private static GearInstance sword(){
        var base=GearCatalog.load().base("gm.sword_adamantite.h");
        return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.COMMON,List.of(),BigDecimal.ZERO);
    }
    private static final class H extends QuickSlashLightProfileTest.Harness {
        GearEffectSnapshot gear;
        UUID selected;
        H(GearEffectSnapshot gear,UUID selected){this.gear=gear;this.selected=selected;}
        @Override public GearAffixRuntime.Effects gearEffects(){
            return new GearAffixRuntime.Effects(Map.of(),0,0,0,0,0,0,gear);
        }
        @Override public Equipment equipment(){
            var source=gear.items().getFirst();
            String carrier=new GearBindings().require(source.baseId()).carrier(source.rarity());
            return new Equipment(new Item(carrier,"SWORD",
                    new ItemPowerDescriptor(carrier,Set.of("RPG_WEAPON_LIGHT"),20d,20d),selected),null);
        }
    }
    @Test void sameCarrierCommitsExactEquippedInstanceBeforePaymentAndChildrenKeepIt(){
        var first=sword();var second=sword();
        var h=new H(new GearEffectSnapshot(List.of(first,second)),second.identity());
        assertEquals(SkillExecutionResult.Status.COMMITTED,h.cast().status());
        var committed=h.last();
        assertEquals(second.identity(),committed.gearSourceItemId());
        assertEquals(second.identity(),committed.withSnapshot(committed.snapshot()).gearSourceItemId());
        assertEquals(95,h.current(ResourceType.STAMINA));
    }
    @Test void missingOrAmbiguousManagedIdentityRejectsBeforePayment(){
        var first=sword();var second=sword();var gear=new GearEffectSnapshot(List.of(first,second));
        for(UUID identity:new UUID[]{null,UUID.randomUUID()}){
            var h=new H(gear,identity);
            assertEquals("COMMITTED_GEAR_SOURCE_UNAVAILABLE",h.cast().code());
            assertEquals(100,h.current(ResourceType.STAMINA));
            assertTrue(h.contexts.isEmpty());
        }
        var unique=new H(new GearEffectSnapshot(List.of(first)),null);
        assertEquals(SkillExecutionResult.Status.COMMITTED,unique.cast().status());
        assertEquals(first.identity(),unique.last().gearSourceItemId());
    }
}
