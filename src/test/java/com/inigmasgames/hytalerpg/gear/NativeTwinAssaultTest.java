package com.inigmasgames.hytalerpg.gear;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Native asset and controlled accepted-snapshot checks; connected contact remains a separate gate. */
class NativeTwinAssaultTest {
    private final GearCatalog catalog = GearCatalog.load();
    private GearInstance item(String base, UUID id, double chance) {
        var definition = catalog.affix("WA-141");
        var roll = new GearInstance.AffixRoll("WA-141",definition.side(),definition.exclusionGroup(),1,chance,
                new GearRequirements.Gate(1,Map.of()),definition.name(),definition.name());
        var definitionBase=catalog.base(base);
        return GearInstance.authoredQa(definitionBase,id,definitionBase.sourceWindow().getFirst(),1000,
                GearRarity.UNCOMMON,List.of(roll),BigDecimal.ZERO);
    }
    private static com.google.gson.JsonObject asset(String id) throws Exception {
        try(var input=NativeTwinAssaultTest.class.getResourceAsStream(
                "/rpg/gear/action-templates/twin-daggers/"+id+".json")) {
            assertNotNull(input,id);
            return JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void acceptedMainAndDistinctUtilityDaggerOwnTheChance() {
        var main=item("gm.daggers_iron.h",UUID.randomUUID(),10);
        var offhand=item("gm.daggers_iron.h",UUID.randomUUID(),8);
        var accepted=new GearEffectSnapshot(List.of(main,offhand));
        assertTrue(NativeTwinAssaultEligibility.accepts(accepted,main,offhand,.099));
        assertFalse(NativeTwinAssaultEligibility.accepts(accepted,main,offhand,.1));
        assertFalse(NativeTwinAssaultEligibility.accepts(accepted,offhand,main,.099));
        assertFalse(NativeTwinAssaultEligibility.accepts(new GearEffectSnapshot(List.of(main)),main,offhand,0));
        assertFalse(NativeTwinAssaultEligibility.accepts(accepted,main,main,0));
        assertFalse(NativeTwinAssaultEligibility.accepts(accepted,main,
                item("gm.daggers_adamantite.h",UUID.randomUUID(),10),0));
        var low=item("gm.daggers_iron.h",UUID.randomUUID(),3.2);
        assertTrue(NativeTwinAssaultEligibility.accepts(new GearEffectSnapshot(List.of(low,offhand)),low,offhand,.031));
        assertFalse(NativeTwinAssaultEligibility.accepts(new GearEffectSnapshot(List.of(low,offhand)),low,offhand,.032));
        assertThrows(IllegalArgumentException.class,()->NativeTwinAssaultEligibility.accepts(accepted,main,offhand,Double.NaN));
        var child=GearCombatEffects.attack(accepted,offhand.identity(),"root/WA141/NoProc",60,.4,
                true,false,0,null,0,1.5,false);
        assertEquals(offhand.identity(),child.itemId());
        assertEquals(24,child.amount(GearCombatEffects.Channel.PHYSICAL),1e-8);
        assertFalse(child.critical());
    }
    @Test void nativeGraphHasSecondSelectorAndServerDecision() throws Exception {
        var root=asset(NativeTwinDaggersAssets.ROOT);
        assertEquals("RPG_Twin_Daggers_Weapon_Daggers_Primary",
                root.getAsJsonArray("Interactions").get(0).getAsString());
        var first=asset("RPG_Twin_Daggers_Weapon_Daggers_Primary_Swing_Left_Selector");
        var gate=first.getAsJsonObject("Next");
        assertEquals(NativeTwinAssaultGate.TYPE,gate.get("Type").getAsString());
        assertEquals("RPG_Twin_Daggers_Offhand_Selector",gate.get("Next").getAsString());
        assertEquals("SwingRight",gate.getAsJsonObject("Effects").get("ItemAnimationId").getAsString());
        var second=asset("RPG_Twin_Daggers_Offhand_Selector");
        assertEquals("Stab",second.getAsJsonObject("Selector").get("Id").getAsString());
        assertTrue(second.getAsJsonObject("Selector").get("TestLineOfSight").getAsBoolean());
        assertEquals(first.getAsJsonObject("HitBlock"),second.getAsJsonObject("HitBlock"));
        assertEquals("SecondaryItem",second.getAsJsonObject("Effects").getAsJsonArray("Trails")
                .get(0).getAsJsonObject().get("TargetEntityPart").getAsString());
        var damage=second.getAsJsonObject("HitEntity").getAsJsonArray("Interactions").get(0).getAsJsonObject();
        assertEquals(ManagedGearDamageInteraction.TYPE,damage.get("Type").getAsString());
        assertTrue(damage.get("Offhand").getAsBoolean());
        assertEquals("Weapon_Daggers_Primary_Swing_Right_Damage",damage.get("Parent").getAsString());
        for(String suffix:List.of("", "_Chain", "_Pounce", "_Pounce_Force", "_Pounce_StaminaCondition",
                "_Pounce_Stab", "_Pounce_Stab_Damage", "_Pounce_Stab_Force", "_Pounce_Stab_Selector",
                "_Pounce_Sweep", "_Pounce_Sweep_Damage", "_Pounce_Sweep_Effect", "_Pounce_Sweep_Selector",
                "_Stab_Left", "_Stab_Left_Damage", "_Stab_Left_Selector", "_Stab_Right", "_Stab_Right_Damage",
                "_Stab_Right_Selector", "_Swing_Left", "_Swing_Left_Damage", "_Swing_Left_Selector",
                "_Swing_Right", "_Swing_Right_Damage", "_Swing_Right_Selector"))
            assertNotNull(asset("RPG_Twin_Daggers_Weapon_Daggers_Primary"+suffix));
    }
    @Test void utilityCarrierKeepsBaseItemAndUsesTwinRoot() throws Exception {
        var source=JsonParser.parseReader(new java.io.InputStreamReader(Objects.requireNonNull(
                getClass().getResourceAsStream("/Server/Item/Items/RPG/Gear/RPG_Gear_daggers_iron_nm.json")),
                java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        var twin=JsonParser.parseString(NativeTwinDaggersAssets.renderCarrier("RPG_Gear_daggers_iron_nm")).getAsJsonObject();
        assertEquals(NativeTwinDaggersAssets.ROOT,twin.getAsJsonObject("Interactions").get("Primary").getAsString());
        assertTrue(twin.getAsJsonObject("Utility").get("Compatible").getAsBoolean());
        assertFalse(twin.getAsJsonObject("Utility").get("Usable").getAsBoolean());
        assertTrue(twin.getAsJsonObject("Weapon").get("RenderDualWielded").getAsBoolean());
        assertEquals(source.get("InteractionVars"),twin.get("InteractionVars"));
        assertEquals(source.get("MaxDurability"),twin.get("MaxDurability"));
        assertEquals(source.get("Model"),twin.get("Model"));
        assertEquals(source.get("Texture"),twin.get("Texture"));
        assertEquals("Weapon_Daggers_Iron",GearNativeItems.nativeId("RPG_Gear_daggers_iron_nm__Twin"));
    }
    @Test void contextualOffhandCarrierKeepsOrdinaryPrimaryAndFrozenPayloadSurface() throws Exception {
        var carrier="RPG_Gear_daggers_iron_nm";
        var source=JsonParser.parseReader(new java.io.InputStreamReader(Objects.requireNonNull(
                getClass().getResourceAsStream("/Server/Item/Items/RPG/Gear/"+carrier+".json")),
                java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        var utility=JsonParser.parseString(NativeTwinUtilityAssets.renderCarrier(carrier)).getAsJsonObject();
        assertTrue(utility.getAsJsonObject("Utility").get("Compatible").getAsBoolean());
        assertFalse(utility.getAsJsonObject("Utility").get("Usable").getAsBoolean());
        assertEquals(source.get("Interactions"),utility.get("Interactions"));
        assertEquals(source.get("InteractionVars"),utility.get("InteractionVars"));
        assertEquals(source.get("MaxDurability"),utility.get("MaxDurability"));
        assertEquals(source.get("Model"),utility.get("Model"));
        assertEquals(source.get("Texture"),utility.get("Texture"));
        assertEquals("Weapon_Daggers_Iron",GearNativeItems.nativeId(carrier+"__Offhand"));
        assertThrows(IllegalArgumentException.class,()->NativeTwinUtilityAssets.renderCarrier("RPG_Gear_sword_iron_nm"));
    }
    @Test void validActiveTwinMainAuthorizesOnlyAnotherSameBaseDaggerForUtility() {
        var main=item("gm.daggers_iron.h",UUID.randomUUID(),10);
        var base=catalog.base("gm.daggers_iron.h");
        var plain=GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getFirst(),1000,
                GearRarity.COMMON,List.of(),BigDecimal.ZERO);
        var accepted=new GearEffectSnapshot(List.of(main));
        assertTrue(NativeTwinUtilityAssets.eligible(accepted,main.identity(),plain));
        assertFalse(NativeTwinUtilityAssets.eligible(accepted,main.identity(),main));
        assertFalse(NativeTwinUtilityAssets.eligible(new GearEffectSnapshot(List.of()),main.identity(),plain));
        assertFalse(NativeTwinUtilityAssets.eligible(accepted,UUID.randomUUID(),plain));
        assertFalse(NativeTwinUtilityAssets.eligible(accepted,main.identity(),
                item("gm.daggers_adamantite.h",UUID.randomUUID(),10)));
    }
}
