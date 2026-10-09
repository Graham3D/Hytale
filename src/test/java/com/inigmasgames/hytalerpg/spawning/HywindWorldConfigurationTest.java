package com.inigmasgames.hytalerpg.spawning;

import com.google.gson.JsonParser;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.enemies.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class HywindWorldConfigurationTest {
    @TempDir Path root;
    @AfterEach void restoreSharedBalance(){EnemyBalance.activate(EnemyBalance.base(),root.resolve("reset-balance"));}
    private com.google.gson.JsonObject defaults() throws Exception{
        try(var input=getClass().getResourceAsStream("/rpg/world-config-default.json")){
            assertNotNull(input);return JsonParser.parseString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void acceptedDefaultsAndLegacyDensityMigration() throws Exception{
        var legacy=root.resolve("mods/legacy/world-spawn-density.json");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy,"{\"schemaVersion\":1,\"worldSpawnDensityMultiplier\":8.0}");
        var service=new HywindWorldConfiguration(root,legacy);
        assertEquals(8,service.multiplier());
        assertEquals(.016,service.snapshot().enemyBalance().promotion().weights(DifficultyId.NORMAL).get(EnemyRarity.CHAMPION)/1000.0);
        assertEquals(.16,service.snapshot().enemyBalance().promotion().weights(DifficultyId.HELL).get(EnemyRarity.UNIQUE)/1000.0);
        assertEquals(.65,service.snapshot().population().hostileShare());
        service.set(4);
        assertEquals(4,service.multiplier());
        assertEquals(4,JsonParser.parseString(Files.readString(root.resolve("world-config.json"))).getAsJsonObject()
                .getAsJsonObject("world").get("nativeEnvironmentSpawnMultiplier").getAsDouble());
    }
    @Test void rejectedReloadKeepsActivePolicy() throws Exception{
        var legacy=root.resolve("mods/legacy/world-spawn-density.json");
        var service=new HywindWorldConfiguration(root,legacy);
        var bad=defaults();bad.getAsJsonObject("world").addProperty("unimplementedControl",8);
        Files.writeString(service.path(),bad.toString());
        assertThrows(IllegalArgumentException.class,service::reload);
        assertEquals(1,service.multiplier());
    }
    @Test void unsupportedWildlifeAdmissionAndInvalidSharesFailValidation() throws Exception{
        var proposed=defaults();
        proposed.getAsJsonObject("spawnPopulationBalance").addProperty("wildlifeSoftUpperShare",.4);
        assertThrows(IllegalArgumentException.class,()->HywindWorldConfiguration.parse(proposed));
        proposed.getAsJsonObject("spawnPopulationBalance").remove("wildlifeSoftUpperShare");
        proposed.getAsJsonObject("spawnPopulationBalance").addProperty("hostileTargetShare",.7);
        assertThrows(IllegalArgumentException.class,()->HywindWorldConfiguration.parse(proposed));
    }
    @Test void changedRarityPublishesOnlyOnReload() throws Exception{
        var service=new HywindWorldConfiguration(root,root.resolve("mods/legacy/world-spawn-density.json"));
        var edited=defaults();
        var normal=edited.getAsJsonObject("monsterRarity").getAsJsonObject("perEra").getAsJsonObject("NORMAL");
        normal.addProperty("championChance",.02);normal.addProperty("uniqueChance",.06);
        Files.writeString(service.path(),edited.toString());
        assertEquals(16,service.snapshot().enemyBalance().promotion().weights(DifficultyId.NORMAL).get(EnemyRarity.CHAMPION));
        service.reload();
        assertEquals(20,service.snapshot().enemyBalance().promotion().weights(DifficultyId.NORMAL).get(EnemyRarity.CHAMPION));
        assertEquals(service.snapshot().enemyBalance().revision(),EnemyBalance.canonical().revision());
    }
}
