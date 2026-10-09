package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.asset.type.trail.config.Trail;
import com.hypixel.hytale.server.core.asset.type.camera.CameraEffect;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The actual native gate commit decision and decoded offhand leaf contract. */
class NativeAffixTwinCommitTest {
    private static final GearCatalog CATALOG = GearCatalog.load();
    private static final Path TEMPLATES = Path.of("src/main/resources/rpg/gear/action-templates/twin-daggers");

    private static GearInstance item(String baseId, boolean rolled) {
        var base = CATALOG.base(baseId);
        var affix = CATALOG.affix("WA-141");
        var roll = new GearInstance.AffixRoll("WA-141",affix.side(),affix.exclusionGroup(),1,12,
                new GearRequirements.Gate(1,Map.of()),affix.name(),affix.name());
        return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getFirst(),1000,
                GearRarity.UNCOMMON,rolled?List.of(roll):List.of(),BigDecimal.ZERO);
    }

    @Test void realGateCommitKeepsDistinctAcceptedOffhandAndRejectsInvalidSources() {
        var main = item("gm.daggers_iron.nm",true);
        var offhand = item("gm.daggers_iron.nm",false);
        var plain = item("gm.daggers_iron.nm",false);
        var wrong = item("gm.daggers_adamantite.nm",false);
        var actor = UUID.randomUUID();
        var accepted = new GearEffectSnapshot(List.of(main,offhand));
        var commit = NativeTwinAssaultGate.decide(actor,42,main,offhand,accepted,true,0);
        assertNotNull(commit);
        assertEquals(actor,commit.actor());
        assertEquals(42,commit.chainId());
        assertEquals(main.identity(),commit.mainId());
        assertEquals(offhand.identity(),commit.offhand().identity());
        assertNotEquals(commit.mainId(),commit.offhand().identity());
        assertSame(accepted,commit.snapshot());
        var chain=new NativeGearAttackAcceptance.Chain(UUID.randomUUID(),actor,main.identity(),accepted,
                1,2,"accepted-primary");
        var hit=ManagedGearDamageInteraction.offhandContact(commit,42,actor,main,chain,"accepted-strike");
        assertNotNull(hit);
        assertEquals(offhand.identity(),hit.itemId());
        assertEquals(ManagedGearDamageInteraction.samplePower(offhand,offhand.identity()+"/accepted-strike")*.4,
                hit.amount(GearCombatEffects.Channel.PHYSICAL),1e-8);
        assertTrue(ManagedGearDamageInteraction.noProcChild(hit));
        assertFalse(hit.critical(),"offhand is noncritical even when baseline chance is 100 percent");
        assertNull(ManagedGearDamageInteraction.offhandContact(commit,43,actor,main,chain,"accepted-strike"));
        assertNull(ManagedGearDamageInteraction.offhandContact(commit,42,UUID.randomUUID(),main,chain,"accepted-strike"));
        assertNull(ManagedGearDamageInteraction.offhandContact(commit,42,actor,plain,chain,"accepted-strike"));
        assertNull(ManagedGearDamageInteraction.offhandContact(commit,42,actor,main,
                new NativeGearAttackAcceptance.Chain(chain.world(),actor,main.identity(),
                        new GearEffectSnapshot(List.of(main)),1,2,"accepted-primary"),"accepted-strike"));
        assertNull(NativeTwinAssaultGate.decide(actor,42,main,offhand,accepted,false,0));
        assertNull(NativeTwinAssaultGate.decide(actor,42,main,main,accepted,true,0));
        assertNull(NativeTwinAssaultGate.decide(actor,42,main,wrong,accepted,true,0));
        assertNull(NativeTwinAssaultGate.decide(actor,42,plain,offhand,
                new GearEffectSnapshot(List.of(plain,offhand)),true,0));
        assertNull(NativeTwinAssaultGate.decide(actor,42,main,offhand,accepted,true,.12));
        assertNull(NativeTwinAssaultGate.decide(actor,42,main,offhand,
                new GearEffectSnapshot(List.of(main)),true,0));
    }

    @Test void nativeCodecBindsGateCheckpointAndManagedOffhandDamageLeaf() throws Exception {
        try (var fixture = NativeAssetTestFixtures.open()) {
            var trailField = NativeAssetTestFixtures.class.getDeclaredField("trails");
            trailField.setAccessible(true);
            var trails = trailField.get(fixture);
            var seed = trails.getClass().getDeclaredMethod("seed",Map.class);
            seed.setAccessible(true);
            seed.invoke(trails,Map.of("Dagger_Basic",
                    new Trail("Dagger_Basic",null,null,null,1,0,0,false,null,null,null)));
            var cameraField = NativeAssetTestFixtures.class.getDeclaredField("cameras");
            cameraField.setAccessible(true);
            var cameras = cameraField.get(fixture);
            var seedCamera = cameras.getClass().getDeclaredMethod("seed",Map.class);
            seedCamera.setAccessible(true);
            var camera = NativeAssetTestFixtures.class.getDeclaredMethod("cameraEffect",String.class);
            camera.setAccessible(true);
            seedCamera.invoke(cameras,Map.of("Daggers_Swing_Horizontal",
                    (CameraEffect)camera.invoke(null,"Daggers_Swing_Horizontal")));
            var first = fixture.decodeInteraction("RPG_Twin_Daggers_Weapon_Daggers_Primary_Swing_Left_Selector",
                    TEMPLATES.resolve("RPG_Twin_Daggers_Weapon_Daggers_Primary_Swing_Left_Selector.json"));
            assertNotNull(first);
            var gateJson = JsonParser.parseString(Files.readString(TEMPLATES.resolve(
                    "RPG_Twin_Daggers_Weapon_Daggers_Primary_Swing_Left_Selector.json"))).getAsJsonObject();
            assertEquals(NativeTwinAssaultGate.TYPE,
                    gateJson.getAsJsonObject("Next").get("Type").getAsString());
            var second = fixture.decodeInteraction("RPG_Twin_Daggers_Offhand_Selector",
                    TEMPLATES.resolve("RPG_Twin_Daggers_Offhand_Selector.json"));
            assertNotNull(second);
            var offhandJson = JsonParser.parseString(Files.readString(TEMPLATES.resolve(
                    "RPG_Twin_Daggers_Offhand_Selector.json"))).getAsJsonObject()
                    .getAsJsonObject("HitEntity").getAsJsonArray("Interactions").get(0).getAsJsonObject();
            assertEquals(ManagedGearDamageInteraction.TYPE,offhandJson.get("Type").getAsString());
            assertTrue(offhandJson.get("Offhand").getAsBoolean());
            var asset = Interaction.getAssetMap().getAsset("RPG_Twin_Daggers_Offhand_Selector");
            assertSame(second,asset);
        }
    }
}
