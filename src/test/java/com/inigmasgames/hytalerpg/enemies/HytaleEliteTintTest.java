package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.JsonParser;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleEliteTint;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Offline native effect contract; final rendered color still requires the connected client. */
class HytaleEliteTintTest {
    private record Sample(String role,EnemyRarity rarity,String color,String nativeRolePath){}
    private static final Sample[] SAMPLES={
        new Sample("Larva_Void",EnemyRarity.CHAMPION,"#1d4dff","Server/NPC/Roles/Void/Larva_Void.json"),
        new Sample("Skeleton_Scout",EnemyRarity.UNIQUE,"#a000ff","Server/NPC/Roles/Undead/Skeleton/Skeleton/Skeleton_Scout.json"),
        new Sample("Golem_Firesteel",EnemyRarity.SUPER_UNIQUE,"#ff9100","Server/NPC/Roles/Elemental/Golem/Golem_Firesteel.json")
    };

    @Test void sameRoleIndependentNativeTintPathCoversThreeDifferentModels()throws Exception{
        var installed=Path.of(System.getProperty("user.home"),"AppData","Roaming","Hytale",
                "install","pre-release","package","game","latest","Assets.zip");
        try(var zip=new ZipFile(installed.toFile())){
            for(var sample:SAMPLES){
                assertNotNull(zip.getEntry(sample.nativeRolePath()),sample.role());
                assertEquals(sample.color(),MonsterVisualProfile.resolve(sample.rarity(),java.util.List.of()).skinTint());
                var id=HytaleEliteTint.assetId(sample.rarity());
                assertNotNull(id,sample.role());
                try(var stream=getClass().getResourceAsStream("/Server/Entity/Effects/RPG/"+id+".json")){
                    assertNotNull(stream,sample.role());
                    var asset=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
                    assertEquals(Set.of("Infinite","Debuff","ApplicationEffects"),asset.keySet());
                    assertTrue(asset.get("Infinite").getAsBoolean());
                    assertFalse(asset.get("Debuff").getAsBoolean());
                    var visual=asset.getAsJsonObject("ApplicationEffects");
                    assertEquals(Set.of("EntityBottomTint","EntityTopTint"),visual.keySet());
                    assertEquals(sample.color(),visual.get("EntityBottomTint").getAsString());
                    assertEquals(sample.color(),visual.get("EntityTopTint").getAsString());
                    assertNull(zip.getEntry("Server/Entity/Effects/RPG/"+id+".json"));
                }
            }
        }
    }

    @Test void ordinaryAndBossActorsHaveNoEliteTint(){
        assertNull(HytaleEliteTint.assetId(EnemyRarity.NORMAL));
        assertNull(HytaleEliteTint.assetId(EnemyRarity.BOSS));
        assertNotEquals(HytaleEliteTint.assetId(EnemyRarity.CHAMPION),HytaleEliteTint.assetId(EnemyRarity.UNIQUE));
        assertNotEquals(HytaleEliteTint.assetId(EnemyRarity.UNIQUE),HytaleEliteTint.assetId(EnemyRarity.SUPER_UNIQUE));
    }

    @Test void everyProductionCertifiedRoleSharesTheSameRarityTintAndVisualScale(){
        var certified=new EnemyEliteRoleMatrix().rows().stream().filter(EnemyEliteRoleMatrix.Row::certified).toList();
        assertFalse(certified.isEmpty());
        for(var role:certified)for(var rarity:new EnemyRarity[]{EnemyRarity.CHAMPION,EnemyRarity.UNIQUE,EnemyRarity.SUPER_UNIQUE}){
            assertNotNull(HytaleEliteTint.assetId(rarity),role.roleId());
            assertEquals(switch(rarity){case CHAMPION->1.15f;case UNIQUE->1.30f;case SUPER_UNIQUE->1.45f;default->throw new AssertionError();},
                    com.inigmasgames.hytalerpg.execution.hytale.HytaleEnemyPalette.rarityScale(rarity),role.roleId());
            assertNotNull(MonsterVisualProfile.resolve(rarity,java.util.List.of()).skinTint(),role.roleId());
        }
    }
}
