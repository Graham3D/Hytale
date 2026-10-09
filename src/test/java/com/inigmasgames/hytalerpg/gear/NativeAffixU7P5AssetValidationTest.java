package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.protocol.AbilitySlot;
import com.hypixel.hytale.protocol.AbilityCostType;
import com.hypixel.hytale.server.core.asset.type.item.config.CoreItemAbility;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemAbility;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Installed U7P5 codecs only; no server, world, authentication or save writes. */
class NativeAffixU7P5AssetValidationTest {
    private static final Path SERVER=Path.of("src/main/resources/Server");

    private static NativeAssetTestFixtures fixture;
    private static com.hypixel.hytale.assetstore.AssetStore<?,?,?> blockStore;
    private static final class BlockSets extends com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap<String,com.hypixel.hytale.server.core.asset.type.blockset.config.BlockSet>{
        BlockSets(){super(com.hypixel.hytale.server.core.asset.type.blockset.config.BlockSet[]::new);}
        void seed(String id){putAll("U7P5ReferenceKeys",com.hypixel.hytale.server.core.asset.type.blockset.config.BlockSet.CODEC,
                Map.of(id,new com.hypixel.hytale.server.core.asset.type.blockset.config.BlockSet(id)),Map.of(),Map.of());}
    }
    @org.junit.jupiter.api.BeforeAll static void open()throws Exception{
        fixture=NativeAssetTestFixtures.open();
        fixture.loadInstalledQuality("Rare");
        var map=new BlockSets();
        blockStore=new com.hypixel.hytale.server.core.asset.HytaleAssetStore<>(com.hypixel.hytale.server.core.asset.HytaleAssetStore.builder(
                com.hypixel.hytale.server.core.asset.type.blockset.config.BlockSet.class,map).setPath("BlockSets")
                .setCodec(com.hypixel.hytale.server.core.asset.type.blockset.config.BlockSet.CODEC)
                .setKeyFunction(com.hypixel.hytale.server.core.asset.type.blockset.config.BlockSet::getId)
                .setReplaceOnRemove(com.hypixel.hytale.server.core.asset.type.blockset.config.BlockSet::new)){
            private final com.hypixel.hytale.event.EventBus events=new com.hypixel.hytale.event.EventBus(false);
            @Override protected com.hypixel.hytale.event.EventBus getEventBus(){return events;}
        };
        com.hypixel.hytale.assetstore.AssetRegistry.register(blockStore);
        var zipPath=Path.of(System.getProperty("user.home"),"AppData/Roaming/Hytale/install/pre-release/package/game/latest/Assets.zip");
        try(var zip=new java.util.zip.ZipFile(zipPath.toFile())){
            zip.stream().filter(e->e.getName().startsWith("Server/Item/Block/Sets/")&&e.getName().endsWith(".json"))
                    .forEach(e->map.seed(Path.of(e.getName()).getFileName().toString().replace(".json","")));
        }
    }
    @org.junit.jupiter.api.AfterAll static void close(){
        if(blockStore!=null)com.hypixel.hytale.assetstore.AssetRegistry.unregister(blockStore);
        if(fixture!=null)fixture.close();
    }

    @Test void nativeCodecReproducesLegacyFailureAndAcceptsEveryMigratedBridge() throws Exception {
        var legacy=BsonDocument.parse("{Slot:'Primary',Cost:0,Cooldown:0,CostType:'None',Cast:'Root_RPG_Ability_Bridge'}");
        var failure=assertThrows(com.hypixel.hytale.codec.lookup.ACodecMapCodec.UnknownIdException.class,
                ()->ItemAbility.CODEC.decode(legacy,new ExtraInfo()));
        assertTrue(failure.getMessage().contains("Primary"));
        int count=0;
        try(var paths=Files.list(SERVER.resolve("Item/Items/RPG/Abilities"))){
            for(var path:paths.filter(p->p.toString().endsWith(".json")).sorted().toList()){
                var document=BsonDocument.parse(Files.readString(path));
                fixture.seedAbilityRootReferences(document.toJson());
                var info=new com.hypixel.hytale.assetstore.AssetExtraInfo<>(new com.hypixel.hytale.assetstore.AssetExtraInfo.Data(
                        com.hypixel.hytale.server.core.asset.type.item.config.Item.class,path.getFileName().toString().replace(".json",""),null));
                var ability=assertInstanceOf(CoreItemAbility.class,ItemAbility.CODEC.decode(document.getDocument("Ability"),info),path.toString());
                assertEquals(AbilitySlot.Core,ability.getSlot());
                assertEquals(0,ability.getCost());assertEquals(0,ability.getCooldownS());
                assertEquals(AbilityCostType.None,ability.getCostType());
                assertTrue(Files.isRegularFile(SERVER.resolve("Item/RootInteractions/RPG/"+ability.getCastRootId()+".json")),path.toString());
                assertTrue(info.getUnknownKeys()==null||info.getUnknownKeys().stream().allMatch("Slot"::equals),()->path+" obsolete fields: "+info.getUnknownKeys());
                assertFalse(document.getDocument("Ability").containsKey("Tags"));
                var full=fixture.decodeItem(path.getFileName().toString().replace(".json",""),path);
                assertInstanceOf(CoreItemAbility.class,full.getAbility());
                count++;
            }
        }
        assertEquals(97,count);
    }

    @Test void allManagedCarrierBodiesDecodeWithInstalledItemSchema() throws Exception {
        int count=0;
        try(var paths=Files.list(SERVER.resolve("Item/Items/RPG/Gear"))){
            var animations=new HashSet<String>();
            for(var path:paths.filter(p->p.toString().endsWith(".json")).sorted().toList()){
                String json=Files.readString(path);var document=BsonDocument.parse(json);
                fixture.seedPackagedRootReferences(json);
                fixture.seedActionReferences(json);
                String animation=document.getString("PlayerAnimationsId").getValue();
                if(animations.add(animation))fixture.loadInstalledAnimation(animation);
                String id=path.getFileName().toString().replace(".json","");
                var item=fixture.decodeItem(id,path);
                assertEquals(document.getNumber("MaxDurability").doubleValue(),item.getMaxDurability(),id);
                var stack=new ItemStack(id,1).withDurability(Math.max(0,item.getMaxDurability()-1));
                var encoded=ItemStack.CODEC.encode(stack,new ExtraInfo()).asDocument();
                var roundTrip=ItemStack.CODEC.decode(encoded,new ExtraInfo());
                assertEquals(stack,roundTrip,id);
                assertEquals(stack.getQualityIndex(),roundTrip.getQualityIndex(),id);
                count++;
            }
        }
        assertEquals(2040,count);
    }

    @Test void nativeQualityOverridesAreOptionalAndExplicitOverridesRoundTrip() throws Exception {
        {
            var path=SERVER.resolve("Item/Items/RPG/Gear/RPG_Gear_staff_apprentice_n.json");
            fixture.seedPackagedRootReferences(Files.readString(path));
            fixture.decodeItem("RPG_Gear_staff_apprentice_n",path);
            var inherited=new ItemStack("RPG_Gear_staff_apprentice_n",1);
            var json=ItemStack.CODEC.encode(inherited,new ExtraInfo()).asDocument();
            assertFalse(json.containsKey("QualityOverride"));
            assertEquals(inherited.getItem().getQualityIndex(),ItemStack.CODEC.decode(json,new ExtraInfo()).getQualityIndex());
            var explicit=inherited.withQuality(17);
            var overridden=ItemStack.CODEC.encode(explicit,new ExtraInfo()).asDocument();
            assertEquals(17,overridden.getInt32("QualityOverride").getValue());
            assertEquals(explicit,ItemStack.CODEC.decode(overridden,new ExtraInfo()));
        }
    }

    @Test void packagedProjectileModelsAndConfigsUseInstalledSchemas()throws Exception{
        int models=0,configs=0;
        try(var paths=Files.walk(SERVER.resolve("Models/Projectiles"))){
            for(var path:paths.filter(p->p.toString().endsWith(".json")).sorted().toList()){
                fixture.decodeModel(path.getFileName().toString().replace(".json",""),path);models++;
            }
        }
        fixture.loadCarrierAssets();
        try(var paths=Files.walk(SERVER.resolve("ProjectileConfigs"))){
            for(var path:paths.filter(p->p.toString().endsWith(".json")).sorted().toList()){
                fixture.seedActionReferences(Files.readString(path));
                String model=BsonDocument.parse(Files.readString(path)).getString("Model").getValue();
                if(!model.startsWith("RPG_"))fixture.loadInstalledModel(model);
                fixture.decodeProjectile(path.getFileName().toString().replace(".json",""),path);configs++;
            }
        }
        assertEquals(22,models);assertEquals(48,configs);
    }

    @Test void allGeneratedWeaponAnimationProfilesDecodeOnU7P5()throws Exception{
        int count=0;
        var parents=new HashSet<String>();
        try(var paths=Files.walk(SERVER.resolve("Item/Animations"))){
            for(var path:paths.filter(p->p.toString().endsWith(".json")).sorted().toList()){
                var json=BsonDocument.parse(Files.readString(path));
                if(json.containsKey("Parent")&&parents.add(json.getString("Parent").getValue()))
                    fixture.loadInstalledAnimation(json.getString("Parent").getValue());
                fixture.decodeAnimation(path.getFileName().toString().replace(".json",""),path);count++;
            }
        }
        assertEquals(215,count);
    }

    @Test void existingAbilityActionRootsDecodeWithoutChangingActivationSemantics()throws Exception{
        var codec=com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC;
        var inputs=new com.inigmasgames.hytalerpg.input.HytaleAbilitySkillInputAdapter();
        codec.register("FirstClick",com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.FirstClickInteraction.class,
                com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.FirstClickInteraction.CODEC);
        codec.register("RPG_ActivateSkill",com.inigmasgames.hytalerpg.input.NativeSkillActivationInteraction.class,
                com.inigmasgames.hytalerpg.input.NativeSkillActivationInteraction.codec(inputs));
        codec.register("RPG_FireballCharge",com.inigmasgames.hytalerpg.input.NativeFireballChargeInteraction.class,
                com.inigmasgames.hytalerpg.input.NativeFireballChargeInteraction.codec(inputs));
        codec.register("RPG_HeldChannel",com.inigmasgames.hytalerpg.input.NativeHeldChannelInteraction.class,
                com.inigmasgames.hytalerpg.input.NativeHeldChannelInteraction.codec(inputs));
        for(var animation:List.of("Shortbow","Fire_Stick","Spellbook"))fixture.loadInstalledAnimation(animation);
        for(var id:List.of("Root_RPG_Ability_Bridge","Root_RPG_Fireball_Charge","Root_RPG_Healing_Beam_Held","Root_RPG_Snipe_Release")){
            var path=SERVER.resolve("Item/RootInteractions/RPG/"+id+".json");
            fixture.seedActionReferences(Files.readString(path));
            assertNotNull(fixture.decodeRoot(id,path));
        }
    }
}
