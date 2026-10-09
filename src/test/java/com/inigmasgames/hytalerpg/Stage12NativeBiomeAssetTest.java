package com.inigmasgames.hytalerpg;
import com.hypixel.hytale.server.worldgen.loader.context.FileContextLoader;
import com.hypixel.hytale.procedurallib.file.FileIO;
import com.hypixel.hytale.procedurallib.file.FileIOSystem;
import com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual installed loader/asset checks. NOT connected spawn-location or reward execution proof. */
class Stage12NativeBiomeAssetTest {
    @Test void installedDamageSupportsTransientRootContextWithIdentityValidation(){
        var fixture=new Stage10SummonTest.Harness();assertTrue(fixture.cast().committed());var context=fixture.context;
        var damage=new com.hypixel.hytale.server.core.modules.entity.damage.Damage(com.hypixel.hytale.server.core.modules.entity.damage.Damage.NULL_SOURCE,0,1f);
        var metadata=new com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata(context.request().actorId(),context.rootCastId(),context.skillInstanceId(),context.request().correlationId(),1,100);
        com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.attachExecutionContext(damage,metadata,context);
        assertSame(context,com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.executionContext(damage));
        var foreign=new com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata(UUID.randomUUID(),context.rootCastId(),context.skillInstanceId(),context.request().correlationId(),1,100);
        assertThrows(IllegalArgumentException.class,()->com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.attachExecutionContext(damage,foreign,context));
        assertNull(com.hypixel.hytale.server.core.modules.entity.damage.Damage.META_REGISTRY.getMetaKeyForCodecKey("InigmasGames:RpgMeaningfulRoot"));
        var next=new com.hypixel.hytale.server.core.modules.entity.damage.Damage(com.hypixel.hytale.server.core.modules.entity.damage.Damage.NULL_SOURCE,0,1f);
        assertNull(com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.executionContext(next));
    }
    private Path assets(){return Path.of(System.getProperty("user.home"),"AppData","Roaming","Hytale","install","pre-release","package","game","latest","Assets.zip");}
    @Test void installedFileContextLoaderResolvesAllFourQualifiedPilotBiomes()throws Exception{
        try(var zip=FileSystems.newFileSystem(assets());var io=FileIO.openFileIOSystem(new FileIOSystem(){
            public Path baseRoot(){return zip.getPath("/Server/World/Default");}
            public FileIOSystem.PathArray roots(){return new FileIOSystem.PathArray(new Path[]{baseRoot()});}
        })){
            var registry=EnemyRewardRegistry.load();
            var zones=registry.biomes().stream().map(EnemyRewardRegistry.Biome::zoneId).collect(java.util.stream.Collectors.toSet());
            var context=new FileContextLoader("Default",zip.getPath("/Server/World/Default"),zones).load();
            for(var biome:registry.biomes()){
                var zone=context.getZones().get(biome.zoneId());assertNotNull(zone);assertEquals(biome.zoneId(),zone.getName());
                var resolved=zone.getTileBiomes().get(biome.biomeId());assertNotNull(resolved);assertEquals(biome.biomeId(),resolved.getName());
                assertEquals(zip.getPath("/"+biome.assetPath()),resolved.getPath());
            }
        }
    }
    @Test void exactInstalledAssetsMatchRegistryPins()throws Exception{
        // R031's gameplay baselines remain frozen. U7P5 removed three native roles and
        // edited eight reference assets; qualify those explicitly without remapping rewards.
        var audit=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/test/resources/u7p5-native-references.json"))).getAsJsonObject();
        var retired=new HashSet<String>();
        audit.getAsJsonArray("retiredRoles").forEach(id->retired.add(id.getAsString()));
        var observed=new HashSet<String>();
        try(var zip=FileSystems.newFileSystem(assets())){
            var registry=EnemyRewardRegistry.load();
            for(var biome:registry.biomes())assertEquals(biome.assetSha256(),hash(Files.readAllBytes(zip.getPath("/"+biome.assetPath()))));
            for(var role:registry.roles()){
                var path=zip.getPath("/"+role.assetPath());
                if(!Files.exists(path)){assertTrue(retired.contains(role.roleId()),"Unreviewed missing native role "+role.roleId());observed.add(role.roleId());continue;}
                var overrides=audit.getAsJsonObject("updatedReferences");
                String pin=overrides.has(role.assetPath())?overrides.get(role.assetPath()).getAsString():role.assetSha256();
                assertEquals(pin,hash(Files.readAllBytes(path)),role.roleId());
            }
            for(var alias:registry.aliases()){
                var path=zip.getPath("/"+alias.assetPath());
                if(!Files.exists(path)){assertTrue(retired.contains(alias.roleId()),"Unreviewed missing native role "+alias.roleId());observed.add(alias.roleId());continue;}
                var overrides=audit.getAsJsonObject("updatedReferences");
                String pin=overrides.has(alias.assetPath())?overrides.get(alias.assetPath()).getAsString():alias.assetSha256();
                assertEquals(pin,hash(Files.readAllBytes(path)),alias.roleId());
            }
        }
        assertEquals(retired,observed,"Retired roles stay historical; do not silently remap authored gameplay baselines");
    }
    private static String hash(byte[] bytes)throws Exception{return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    @Test void actualDefaultWorldgenCodecIdentifiesDefaultWithoutFolderGuess(){
        var provider=new com.hypixel.hytale.server.worldgen.HytaleWorldGenProvider();
        assertEquals("Default",com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards.generatorIdentity(provider));
    }
    @Test void actualNamedWorldgenCodecDoesNotPretendToBeDefault(){
        var provider=com.hypixel.hytale.server.worldgen.HytaleWorldGenProvider.CODEC.decode(org.bson.BsonDocument.parse("{\"Name\":\"Custom\"}"),new com.hypixel.hytale.codec.ExtraInfo());
        assertEquals("Custom",com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards.generatorIdentity(provider));
    }
    @Test void customNativeWorldgenPathRequiresOwnAssetAudit(){
        var provider=com.hypixel.hytale.server.worldgen.HytaleWorldGenProvider.CODEC.decode(org.bson.BsonDocument.parse("{\"Name\":\"Default\",\"Path\":\"custom-world\"}"),new com.hypixel.hytale.codec.ExtraInfo());
        assertEquals("CUSTOM_WORLDGEN_PATH_UNAUDITED",com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards.generatorIdentity(provider));
    }
    @Test void nonLegacyGeneratorCannotInheritBiomeIdentity(){assertEquals("UNSUPPORTED_WORLD_GENERATOR",com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards.generatorIdentity(new com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider()));}
    @Test void nativeWorldSpawnRequiresFreshSpawnAndBothInitializedProvenanceFields(){
        assertTrue(com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards.nativeWorldSpawnEvidence(com.hypixel.hytale.component.AddReason.SPAWN,0,0));
        assertFalse(com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards.nativeWorldSpawnEvidence(com.hypixel.hytale.component.AddReason.LOAD,0,0));
        assertFalse(com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards.nativeWorldSpawnEvidence(com.hypixel.hytale.component.AddReason.SPAWN,Integer.MIN_VALUE,0));
        assertFalse(com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards.nativeWorldSpawnEvidence(com.hypixel.hytale.component.AddReason.SPAWN,0,Integer.MIN_VALUE));
    }
    @Test void missingOptionalNativeHealthSnapshotSkipsOnlyThatContribution(){
        var player=UUID.randomUUID();
        assertFalse(com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards.creditableNativeHealth(player,Double.NaN,20,100,10));
        assertFalse(com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards.creditableNativeHealth(null,30,20,100,10));
        assertTrue(com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards.creditableNativeHealth(player,30,20,100,10));
    }
}
