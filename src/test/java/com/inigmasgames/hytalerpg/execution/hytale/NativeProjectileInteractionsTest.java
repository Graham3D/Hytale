package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.LaunchProjectileInteraction;
import com.hypixel.hytale.server.core.modules.projectile.interaction.ProjectileInteraction;
import com.inigmasgames.hytale.patch.NativeModernProjectileHook;
import com.inigmasgames.hytale.patch.NativeProjectileReceiptHook;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class NativeProjectileInteractionsTest {
    @Test void installedTurretAndSeekerModernLeavesDecodeWithExactModOwner() throws Exception {
        var codecs=Interaction.CODEC;
        boolean legacyAbsent=codecs.getClassFor("LaunchProjectile")==null;
        boolean modernAbsent=codecs.getClassFor("Projectile")==null;
        if(legacyAbsent)codecs.register("LaunchProjectile",LaunchProjectileInteraction.class,LaunchProjectileInteraction.CODEC);
        if(modernAbsent)codecs.register("Projectile",ProjectileInteraction.class,ProjectileInteraction.CODEC);
        var assets=Path.of(System.getProperty("user.home"),"AppData/Roaming/Hytale/install/release/package/game/latest/Assets.zip");
        try(var zip=new ZipFile(assets.toFile());var installed=NativeProjectileInteractions.install(
                (context,holder)->{},(context,projectile,buffer,modifiers)->{})){
            var configs=new String[]{"Projectile_Config_Goblin_Turret_Trash","Projectile_Config_Scarak_Seeker_Spitball"};
            var configField=ProjectileInteraction.class.getDeclaredField("config");configField.setAccessible(true);
            int role=0;
            for(var path:new String[]{
                    "Server/Item/Interactions/NPCs/Intelligent/Goblin/Goblin_Turret/Goblin_Turret_Trash_Throw.json",
                    "Server/Item/Interactions/NPCs/Flying_Beast/Scarak_Seeker/Scarak_Seeker_Spit_Projectile.json"}){
                var entry=zip.getEntry(path);assertNotNull(entry,path);
                org.bson.BsonDocument source;
                try(var input=zip.getInputStream(entry)){
                    source=org.bson.BsonDocument.parse(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
                var leaf=source.containsKey("Next")?source.getDocument("Next"):source;
                leaf.remove("Effects");
                leaf.remove("Tags"); // Asset-store metadata requires native registration; config ownership is the target here.
                var decoded=codecs.decode(leaf,new ExtraInfo());
                assertInstanceOf(NativeProjectileInteractions.ObservedModern.class,decoded,path);
                assertEquals(configs[role++],configField.get(decoded));
            }
        } finally {
            if(legacyAbsent)codecs.remove(LaunchProjectileInteraction.class);
            if(modernAbsent)codecs.remove(ProjectileInteraction.class);
        }
    }
    @Test void bothNativeRoutesHaveOneOwnerAndRestore() throws Exception {
        var codecs=Interaction.CODEC;
        boolean legacyAbsent=codecs.getClassFor("LaunchProjectile")==null;
        boolean modernAbsent=codecs.getClassFor("Projectile")==null;
        if(legacyAbsent)codecs.register("LaunchProjectile",LaunchProjectileInteraction.class,LaunchProjectileInteraction.CODEC);
        if(modernAbsent)codecs.register("Projectile",ProjectileInteraction.class,ProjectileInteraction.CODEC);
        try {
            try(var installed=NativeProjectileInteractions.install((context,holder)->{},
                    (context,projectile,buffer,modifiers)->{})) {
                assertTrue(NativeProjectileReceiptHook.installed());
                assertTrue(NativeModernProjectileHook.installed());
                assertEquals(NativeProjectileInteractions.ObservedLegacy.class,codecs.getClassFor("LaunchProjectile"));
                assertEquals(NativeProjectileInteractions.ObservedModern.class,codecs.getClassFor("Projectile"));
                assertThrows(IllegalStateException.class,()->NativeProjectileInteractions.install(
                        (context,holder)->{},(context,projectile,buffer,modifiers)->{}));
            }
            assertFalse(NativeProjectileReceiptHook.installed());
            assertFalse(NativeModernProjectileHook.installed());
            assertEquals(LaunchProjectileInteraction.class,codecs.getClassFor("LaunchProjectile"));
            assertEquals(ProjectileInteraction.class,codecs.getClassFor("Projectile"));
        } finally {
            if(legacyAbsent)codecs.remove(LaunchProjectileInteraction.class);
            if(modernAbsent)codecs.remove(ProjectileInteraction.class);
        }
    }
}
