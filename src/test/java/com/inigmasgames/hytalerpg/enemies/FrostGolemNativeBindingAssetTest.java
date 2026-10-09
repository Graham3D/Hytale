package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Checks the shipped native attack graph and the exact adapter-only role overlay. */
class FrostGolemNativeBindingAssetTest {
    private static final Path GAME=Path.of(System.getProperty("user.home"),"AppData","Roaming","Hytale",
            "install","pre-release","package","game","latest","Assets.zip");
    private static final String ROLE="Server/NPC/Roles/Elemental/Golem/Golem_Crystal_Frost.json";
    private static final List<String> STRIKES=List.of("Swing_Left_Damage","Swing_Right_Damage","Spin_Damage",
            "Ground_Slam_Damage","Stomp_Damage","Spin_Heavy_Damage","Clap_Damage");
    private static JsonObject read(ZipFile zip,String path)throws Exception{
        var entry=zip.getEntry(path);assertNotNull(entry,path);
        try(var reader=new InputStreamReader(zip.getInputStream(entry),StandardCharsets.UTF_8)){
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
    private static JsonObject leaf(JsonObject role,String strike){
        return role.getAsJsonObject("Modify").getAsJsonObject("_InteractionVars")
                .getAsJsonObject(strike).getAsJsonArray("Interactions").get(0).getAsJsonObject();
    }
    @Test void certifiesAllNativeVariables()throws Exception{
        var binding=EnemyNativeBindings.load().role("Golem_Crystal_Frost").orElseThrow();
        try(var zip=new ZipFile(GAME.toFile())){
            var original=read(zip,ROLE);
            var digest=MessageDigest.getInstance("SHA-256").digest(zip.getInputStream(zip.getEntry(ROLE)).readAllBytes());
            assertEquals(HexFormat.of().formatHex(digest),binding.sourceAssetSha256());
            assertEquals("Template_Intelligent",original.get("Reference").getAsString());
            assertEquals("Root_NPC_Golem_Crystal_Attack",original.getAsJsonObject("Modify").get("Attack").getAsString());
            var overlay=JsonParser.parseReader(Files.newBufferedReader(Path.of("src/main/resources").resolve(ROLE)))
                    .getAsJsonObject();
            for(var strike:STRIKES){
                var nativeLeaf=leaf(original,strike);var routed=leaf(overlay,strike);
                assertEquals("Golem_Crystal_"+strike,nativeLeaf.get("Parent").getAsString());
                assertEquals(10,nativeLeaf.getAsJsonObject("DamageCalculator").getAsJsonObject("BaseDamage")
                        .get("Physical").getAsInt());
                assertEquals("RPG_EnemyDamage",routed.get("Type").getAsString());
                nativeLeaf.addProperty("Type","RPG_EnemyDamage");
            }
            assertEquals(original,overlay,"Only the seven native damage leaves may gain the existing adapter type");
            var root=read(zip,"Server/Item/RootInteractions/NPCs/Elemental/Golem/Root_NPC_Golem_Crystal_Attack.json");
            var chain=root.getAsJsonArray("Interactions").get(0).getAsJsonObject();
            assertEquals("Chaining",chain.get("Type").getAsString());
            assertEquals(8,chain.getAsJsonArray("Next").size());
            assertEquals(2,java.util.stream.StreamSupport.stream(chain.getAsJsonArray("Next").spliterator(),false)
                    .filter(value->value.getAsString().equals("Golem_Crystal_Spin")).count());
            assertEquals(7,binding.actions().getFirst().strikeIds().size());
            assertFalse(binding.productionPromotionEnabled());
        }
    }
}
