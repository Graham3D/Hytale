package com.inigmasgames.hytalerpg;

import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.ImpactConsumer;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Stage05NativeApiTest {
    @Test void pinnedServerExposesRequiredNativeProjectileAndAmmoBoundaries() {
        assertTrue(Arrays.stream(ProjectileModule.class.getMethods())
                .anyMatch(method -> method.getName().equals("spawnProjectile") && method.getParameterCount() == 5));
        assertTrue(Arrays.stream(ProjectileModule.class.getMethods())
                .anyMatch(method -> method.getName().equals("getStandardPhysicsProviderComponentType")
                        && method.getParameterCount() == 0));
        assertTrue(Arrays.stream(ProjectileConfig.class.getMethods())
                .anyMatch(method -> method.getName().equals("getAssetMap")));
        assertTrue(Arrays.stream(StandardPhysicsProvider.class.getMethods())
                .anyMatch(method -> method.getName().equals("setImpactConsumer")
                        && method.getParameterTypes()[0] == ImpactConsumer.class));
        assertTrue(Arrays.stream(ImpactConsumer.class.getMethods())
                .anyMatch(method -> method.getName().equals("onImpact") && method.getParameterCount() == 6));
        assertTrue(Arrays.stream(InventoryComponent.class.getMethods())
                .anyMatch(method -> method.getName().equals("getCombined")));
        assertTrue(Arrays.stream(ItemContainer.class.getMethods())
                .anyMatch(method -> method.getName().equals("removeItemStack") && method.getParameterCount() == 3));
        assertTrue(Arrays.stream(ItemContainer.class.getMethods())
                .anyMatch(method -> method.getName().equals("addItemStack") && method.getParameterCount() == 4));
    }

    @Test void ownedProjectileAssetsContainNoNativeDamageInteraction() throws Exception {
        for (String path : new String[] {
                "/Server/ProjectileConfigs/RPG/Projectile_Config_RPG_Fire_Bolt.json",
                "/Server/ProjectileConfigs/RPG/Projectile_Config_RPG_Snipe.json" }) {
            try (var stream = Stage05NativeApiTest.class.getResourceAsStream(path)) {
                assertNotNull(stream, path);
                String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(json.matches("(?s).*\\\"Interactions\\\"\\s*:\\s*\\{\\s*}.*"), path);
                assertFalse(json.contains("Damage"), path);
            }
        }
        try (var stream = Stage05NativeApiTest.class.getResourceAsStream(
                "/Server/Entity/Effects/RPG/RPG_Burn_Visual.json")) {
            assertNotNull(stream);
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(json.contains("DamageCalculator"));
        }
    }
}
