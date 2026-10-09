package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import org.bson.BsonDocument;
import org.junit.jupiter.api.*;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real native codecs in an isolated JVM; no server is started. */
final class NativeAffixTooltipPresentationTest {
    private static NativeAssetTestFixtures assets;
    private static GearAffixQaSuite suite;
    private static final UUID OWNER=UUID.fromString("3b574c31-6d72-4e92-9c22-cade00000176");
    @BeforeAll static void install() throws Exception {
        assets=NativeAssetTestFixtures.open();suite=new GearAffixQaSuite(GearCatalog.load());
        assets.loadInstalledAnimation("Battleaxe");
        var binding=new GearBindings().require("gm.battleaxe_adamantite.n");
        for(var rarity:List.of(GearRarity.COMMON,GearRarity.MAGIC,GearRarity.RARE)) {
            var id=binding.carrier(rarity);
            assets.decodeItem(id,Path.of("src/main/resources/Server/Item/Items/RPG/Gear",id+".json"));
        }
    }
    @AfterAll static void release(){if(assets!=null)assets.close();}
    private GearInstance gear(String band){return suite.create("tooltip-"+band+"-battleaxe",OWNER);}
    @Test void nativeQualitiesOwnBothNameAndVisibleQualityLabel(){
        for(String band:List.of("common","magic","rare")){
            var gear=gear(band);var stack=GearNativeItems.create(gear,99,Map.of(RpgAttribute.STR,999));
            var quality=ItemQuality.getAssetMap().getAsset(gear.rarity().qualityAsset());
            assertTrue(quality.isVisibleQualityLabel());
            assertEquals(ItemQuality.getAssetMap().getIndex(gear.rarity().qualityAsset()),stack.getQualityIndex());
            var color=quality.getTextColor();
            assertEquals(gear.rarity().color.toLowerCase(Locale.ROOT),String.format(Locale.ROOT,"#%02x%02x%02x",color.red&255,color.green&255,color.blue&255));
            var display=stack.getFromMetadataOrNull(ItemDisplayMetadata.KEYED_CODEC);
            assertEquals(gear.displayName(),display.getName().getRawText());
            assertNull(display.getName().getColor(),"ItemQuality alone owns title color");
        }
    }
    @Test void nativeDescriptionCarriesBoldDamageBlueAffixesAndRedFailures(){
        var gear=gear("rare");var stack=GearNativeItems.create(gear,1,Map.of());
        var children=stack.getFromMetadataOrNull(ItemDisplayMetadata.KEYED_CODEC).getDescription().getChildren();
        var range=GearCombatEffects.physical(gear);
        var damage=children.stream().filter(m->m.getRawText().contains(String.format(Locale.ROOT,"%.1f - %.1f",range.minimum(),range.maximum()))).findFirst().orElseThrow();
        assertEquals(Boolean.TRUE,damage.getFormattedMessage().bold);
        assertEquals(GearTooltip.DAMAGE_COLOR,damage.getColor());
        assertTrue(children.stream().map(m->m.getRawText()).filter(Objects::nonNull)
                .noneMatch(text->text.contains("────")||text.contains("????")));
        for(var affix:gear.affixes())assertTrue(children.stream().anyMatch(m->m.getRawText().strip().equals(GearAffixDisplay.format(affix))&&GearRarity.AFFIX_COLOR.equals(m.getColor())));
        assertTrue(children.stream().filter(m->m.getRawText().strip().startsWith("Requires ")).allMatch(m->GearTooltip.ERROR_COLOR.equals(m.getColor())));
    }
    @Test void nativeDurabilityAndServerCodecMetadataArePreserved(){
        var gear=gear("rare");var stack=GearNativeItems.create(gear,99,Map.of(RpgAttribute.STR,999)).withDurability(37);
        var shown=GearNativeItems.present(stack,gear,99,Map.of(RpgAttribute.STR,999));
        var commands=new UICommandBuilder();commands.set("#Grid.Slots",new ItemGridSlot[]{new ItemGridSlot(shown)});
        var encoded=BsonDocument.parse(commands.getCommands()[0].data).getArray("0").get(0).asDocument().getDocument("ItemStack");
        assertEquals(37,encoded.getDouble("Durability").getValue());assertEquals(120,encoded.getDouble("MaxDurability").getValue());
        assertTrue(encoded.get("Metadata").isDocument(),
                "The server codec retains item metadata for persistence");
        assertEquals(gear.toJson(),encoded.getDocument("Metadata").getString(GearNativeItems.KEY).getValue());
    }
    @Test void openingAndReopeningTooltipDoesNotRerollOrChangeCustodyPayload(){
        var gear=gear("rare");var stack=GearNativeItems.create(gear,1,Map.of());
        var before=stack.getMetadata().clone();String frozen=gear.toJson();
        var shown=GearNativeItems.present(stack,gear,99,Map.of(RpgAttribute.STR,999));
        assertEquals(before,stack.getMetadata());assertEquals(frozen,shown.getMetadata().getString(GearNativeItems.KEY).getValue());
        assertEquals(stack.getItemId(),shown.getItemId());assertEquals(stack.getQuantity(),shown.getQuantity());
        assertEquals(shown,GearNativeItems.present(shown,gear,99,Map.of(RpgAttribute.STR,999)));
        assertEquals(gear,GearNativeItems.read(shown));
    }
    @Test void generatedDescriptionDoesNotAddInternalIdOrDuplicateDurability(){
        var gear=gear("common");var stack=GearNativeItems.create(gear,99,Map.of(RpgAttribute.STR,999));
        var body=stack.getFromMetadataOrNull(ItemDisplayMetadata.KEYED_CODEC).getDescription().getChildren().stream()
                .map(m->m.getRawText()).filter(Objects::nonNull).collect(java.util.stream.Collectors.joining());
        assertFalse(body.contains("ID:"));assertFalse(body.contains("Weapon_Battleaxe"));
        assertFalse(body.contains("Cannot salvage"));assertFalse(body.contains("Durability"));
    }
    @Test void frameAndPointerAssetsMatchTheNativeNineSliceDimensions() throws Exception {
        for(String band:List.of("Common","Magic","Rare","Legendary","Uncommon","Epic")){
            var root=Path.of("src/main/resources/Common/UI/ItemQualities/Tooltips/Hywind");
            var frame=ImageIO.read(root.resolve("ItemTooltip"+band+"@2x.png").toFile());
            var arrow=ImageIO.read(root.resolve("ItemTooltip"+band+"Arrow@2x.png").toFile());
            assertEquals(107,frame.getWidth());assertEquals(107,frame.getHeight());assertEquals(66,arrow.getWidth());assertEquals(48,arrow.getHeight());
            assertEquals(0,frame.getRGB(0,0)>>>24);assertEquals(0xff101722,frame.getRGB(53,53));
            for(int y=48;y<59;y++)for(int x=48;x<59;x++)assertEquals(frame.getRGB(53,53),frame.getRGB(x,y));
        }
    }
}
