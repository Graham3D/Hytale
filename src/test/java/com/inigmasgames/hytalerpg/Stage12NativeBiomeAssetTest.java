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
        try(var zip=FileSystems.newFileSystem(assets())){
            var registry=EnemyRewardRegistry.load();
            for(var biome:registry.biomes())assertEquals(biome.assetSha256(),hash(Files.readAllBytes(zip.getPath("/"+biome.assetPath()))));
            for(var role:registry.roles())assertEquals(role.assetSha256(),hash(Files.readAllBytes(zip.getPath("/"+role.assetPath()))));
        }
    }
    private static String hash(byte[] bytes)throws Exception{return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
