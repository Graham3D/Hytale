package com.inigmasgames.hytalerpg.enemies;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.google.gson.JsonParser;
import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.asset.type.projectile.config.Projectile;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.LaunchProjectileInteraction;
import com.inigmasgames.hytalerpg.execution.hytale.NativeBasicAttackPaths;
import com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyProjectileDamage;
import com.inigmasgames.hytalerpg.combat.damage.WeaponDamageExecution;
import java.util.Map;
import java.util.UUID;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.io.InputStreamReader;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Certifies native graph reachability separately from the launch-to-impact receipt. */
class EnemyNativeProjectilePathTest {
    private static final class PacketCauses extends IndexedLookupTableAssetMap<String,DamageCause>{
        PacketCauses(){super(DamageCause[]::new);}
        void seed(DamageCause projectile,DamageCause fire){putAll("ScoutPacketFixture",DamageCause.CODEC,
                Map.of("Projectile",projectile,"Fire",fire),Map.of(),Map.of());}
    }
    @Test void originalNativePacketAcceptsOneFrozenPhysicalScalarAndRejectsChangedImpact() throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        var source=UUID.randomUUID();var projectileId=UUID.randomUUID();var world=UUID.randomUUID();
        var arrowConstructor=Projectile.class.getDeclaredConstructor();arrowConstructor.setAccessible(true);
        var arrow=arrowConstructor.newInstance();
        var damageField=Projectile.class.getDeclaredField("damage");damageField.setAccessible(true);damageField.setInt(arrow,13);
        var nativeArrow=new ProjectileComponent("Skeleton_Scout_Arrow");
        var nativeAsset=ProjectileComponent.class.getDeclaredField("projectile");nativeAsset.setAccessible(true);nativeAsset.set(nativeArrow,arrow);
        var creator=ProjectileComponent.class.getDeclaredField("creatorUuid");creator.setAccessible(true);creator.set(nativeArrow,source);
        var identity=new WeaponDamageExecution.Identity(world,UUID.randomUUID(),"accepted-root-1","ScoutRoot","arrow-0");
        var offense=new EnemyOffenseSnapshot(identity,1,"native-binding","channels","balance",1,
                Map.of("Projectile",13d),13,Map.of("Projectile",15d),null,0);
        var receipt=new EnemyProjectileReceipt.State(projectileId,source,"Skeleton_Scout_Arrow",13,offense,1,0);
        var originalCause=DamageCause.PROJECTILE;var projectileCause=new DamageCause("Projectile");
        var fireCause=new DamageCause("Fire");var causeMap=new PacketCauses();causeMap.seed(projectileCause,fireCause);
        var causeBuilder=HytaleAssetStore.builder(DamageCause.class,(IndexedLookupTableAssetMap<String,DamageCause>)causeMap)
                .setPath("Entity/Damage").setCodec(DamageCause.CODEC).setKeyFunction(DamageCause::getId)
                .setReplaceOnRemove(DamageCause::new);
        var causes=new HytaleAssetStore<>(causeBuilder){final EventBus events=new EventBus(false);
            @Override protected EventBus getEventBus(){return events;}};
        try{
            AssetRegistry.register(causes);
            DamageCause.PROJECTILE=projectileCause;
            var packet=new Damage(Damage.NULL_SOURCE,projectileCause,13);
            assertEquals(15,NativeEnemyProjectileDamage.requireOriginalPacket(receipt,nativeArrow,packet));
            assertEquals(13,packet.getAmount()); // Certification never adds a packet or changes native damage.
            assertThrows(IllegalStateException.class,()->NativeEnemyProjectileDamage.requireOriginalPacket(
                    receipt,nativeArrow,new Damage(Damage.NULL_SOURCE,projectileCause,6.5f)));
            assertThrows(IllegalStateException.class,()->NativeEnemyProjectileDamage.requireOriginalPacket(
                    receipt,nativeArrow,new Damage(Damage.NULL_SOURCE,fireCause,13)));
            creator.set(nativeArrow,UUID.randomUUID());
            assertThrows(IllegalStateException.class,()->NativeEnemyProjectileDamage.requireOriginalPacket(receipt,nativeArrow,packet));
        }finally{DamageCause.PROJECTILE=originalCause;AssetRegistry.unregister(causes);}
    }
    @Test void nativeOriginalHolderPersistsCreatorAlongsideTheAcceptedReceipt() throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        var creator=UUID.randomUUID();
        var nativeArrow=new ProjectileComponent("Skeleton_Scout_Arrow");
        var creatorField=ProjectileComponent.class.getDeclaredField("creatorUuid");
        creatorField.setAccessible(true);creatorField.set(nativeArrow,creator);
        var loaded=ProjectileComponent.CODEC.decode(ProjectileComponent.CODEC.encode(nativeArrow,new ExtraInfo()),new ExtraInfo());
        assertEquals(creator,loaded.getCreatorUuid());
        assertEquals("Skeleton_Scout_Arrow",loaded.getProjectileAssetName());
        creatorField.set(loaded,UUID.randomUUID());
        assertNotEquals(creator,loaded.getCreatorUuid());
    }
    @Test void installedScoutKeepsItsOriginalSingleArrowWithoutNativeDisplacement() throws Exception {
        var game=Path.of(System.getProperty("user.home"),"AppData","Roaming","Hytale","install",
                "pre-release","package","game","latest","Assets.zip");
        try(var zip=new ZipFile(game.toFile())){
            var role=asset(zip,"Server/NPC/Roles/Undead/Skeleton/Skeleton/Skeleton_Scout.json");
            var binding=EnemyNativeBindings.load().role("Skeleton_Scout").orElseThrow();
            var roleBytes=zip.getInputStream(zip.getEntry(binding.sourceAssetPath())).readAllBytes();
            assertEquals(binding.sourceAssetSha256(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(roleBytes)));
            assertTrue(binding.productionPromotionEnabled());
            assertEquals(Map.of("Projectile","PHYSICAL"),binding.actions().getFirst().channels());
            for(var texture:binding.textures())assertNotNull(zip.getEntry("Common/"+texture),texture);
            assertEquals("Skeleton_Scout_Bow_Shoot",role.getAsJsonObject("Modify").get("Attack").getAsString());
            var root=asset(zip,"Server/Item/RootInteractions/NPCs/OLD_INTERACTIONS/Undead/Skeleton_Scout/Skeleton_Scout_Bow_Shoot.json");
            assertEquals(1,root.getAsJsonArray("Interactions").size());
            assertEquals("Skeleton_Scout_Bow_Shoot",root.getAsJsonArray("Interactions").get(0).getAsString());
            var attack=asset(zip,"Server/Item/Interactions/NPCs/Undead/Skeleton_Scout/Skeleton_Scout_Bow_Shoot.json");
            assertEquals("Simple",attack.get("Type").getAsString());
            assertEquals("Skeleton_Bow",attack.getAsJsonObject("Effects").get("ItemPlayerAnimationsId").getAsString());
            assertEquals("Shoot",attack.getAsJsonObject("Effects").get("ItemAnimationId").getAsString());
            var launch=attack.getAsJsonObject("Next");
            assertEquals("LaunchProjectile",launch.get("Type").getAsString());
            assertEquals("SFX_Unarmed_Swing",launch.getAsJsonObject("Effects").get("WorldSoundEventId").getAsString());
            assertEquals("Skeleton_Scout_Arrow",launch.get("ProjectileId").getAsString());
            assertFalse(launch.has("DamageEffects"));
            var arrow=asset(zip,"Server/Projectiles/NPCs/Undead/Skeleton_Scout/Skeleton_Scout_Arrow.json");
            assertEquals("Arrow_FullCharge",arrow.get("Parent").getAsString());
            assertEquals(13,arrow.get("Damage").getAsInt());
            assertFalse(arrow.has("Knockback"));
            var parent=asset(zip,"Server/Projectiles/Arrow_FullCharge.json");
            assertFalse(parent.has("Knockback"));
            assertFalse(parent.has("DamageEffects"));
        }
    }
    private static com.google.gson.JsonObject asset(ZipFile zip,String path)throws Exception{
        var entry=zip.getEntry(path);assertNotNull(entry,path);
        try(var reader=new InputStreamReader(zip.getInputStream(entry),StandardCharsets.UTF_8)){
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
    private static final class Interactions extends IndexedLookupTableAssetMap<String,Interaction> {
        Interactions(){super(Interaction[]::new);}
        void seed(Interaction launch){putAll("ScoutFixture",Interaction.CODEC,Map.of(launch.getId(),launch),Map.of(),Map.of());}
    }
    @Test void installedScoutActionHydratesThroughNativeAssetStore() throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        var game=Path.of(System.getProperty("user.home"),"AppData","Roaming","Hytale","install",
                "pre-release","package","game","latest","Assets.zip");
        try(var zip=new ZipFile(game.toFile())){
            var entry=zip.getEntry("Server/Item/Interactions/NPCs/Undead/Skeleton_Scout/Skeleton_Scout_Bow_Shoot.json");
            assertNotNull(entry);
            String json;
            try(var input=zip.getInputStream(entry)){json=new String(input.readAllBytes(),StandardCharsets.UTF_8);}
            boolean simpleAdded=Interaction.CODEC.getCodecFor("Simple")==null;
            boolean launchAdded=Interaction.CODEC.getCodecFor("LaunchProjectile")==null;
            if(simpleAdded)Interaction.CODEC.register("Simple",SimpleInteraction.class,SimpleInteraction.CODEC);
            if(launchAdded)Interaction.CODEC.register("LaunchProjectile",LaunchProjectileInteraction.class,LaunchProjectileInteraction.CODEC);
            try{
                var map=new Interactions();
                var builder=HytaleAssetStore.builder(Interaction.class,(IndexedLookupTableAssetMap<String,Interaction>)map)
                        .setPath("Item/Interactions").setCodec(Interaction.CODEC).setKeyFunction(Interaction::getId)
                        .setReplaceOnRemove(ignored->{throw new AssertionError("Fixture asset removed");});
                var assets=new HytaleAssetStore<>(builder){final EventBus events=new EventBus(false);
                    @Override protected EventBus getEventBus(){return events;}};
                AssetRegistry.register(assets);NativeFixtureAssetCache.clear(Interaction.class);
                try{
                    var route=org.bson.BsonDocument.parse(json);
                    assertTrue(route.containsKey("Effects"));
                    assertTrue(route.getDocument("Next").containsKey("Effects"));
                    route.remove("Effects");
                    route.getDocument("Next").remove("Effects"); // Unregistered visual/sound asset keys are outside route certification.
                    var decoded=assets.decode("ScoutFixture","Skeleton_Scout_Bow_Shoot",route);
                    assertNotNull(decoded);
                    assets.loadAssets("ScoutFixture",java.util.List.of(decoded));
                    var root=new RootInteraction("Skeleton_Scout_Bow_Shoot",decoded.getId());root.build();
                    var graph=NativeBasicAttackPaths.resolve(InteractionContext.withoutEntity(),root,InteractionType.Primary);
                    assertTrue(graph.damageOccurrences().isEmpty());
                    assertEquals(1,graph.projectileOccurrences().size());
                    var launch=graph.projectileOccurrences().keySet().iterator().next();
                    assertEquals("Skeleton_Scout_Arrow",launch.getProjectileId());
                    assertEquals(1,graph.projectileOccurrences().get(launch));
                    assertEquals(NativeBasicAttackPaths.Kind.NORMAL,graph.classify(launch).orElseThrow());
                }finally{AssetRegistry.unregister(assets);NativeFixtureAssetCache.clear(Interaction.class);}
            }finally{
                if(launchAdded)Interaction.CODEC.remove(LaunchProjectileInteraction.class);
                if(simpleAdded)Interaction.CODEC.remove(SimpleInteraction.class);
            }
        }
    }
    @Test void countsOriginalNativeLaunchAndRejectsRepeatedRoutes() throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        var launch=new LaunchProjectileInteraction();
        var id=Interaction.class.getDeclaredField("id");id.setAccessible(true);id.set(launch,"ScoutNativeLaunch");
        var map=new Interactions();map.seed(launch);
        var builder=HytaleAssetStore.builder(Interaction.class,(IndexedLookupTableAssetMap<String,Interaction>)map)
                .setPath("Item/Interactions").setCodec(Interaction.CODEC).setKeyFunction(Interaction::getId)
                .setReplaceOnRemove(ignored->{throw new AssertionError("Fixture asset removed");});
        var assets=new HytaleAssetStore<>(builder){final EventBus events=new EventBus(false);
            @Override protected EventBus getEventBus(){return events;}};
        AssetRegistry.register(assets);NativeFixtureAssetCache.clear(Interaction.class);
        try{
            var context=InteractionContext.withoutEntity();
            var single=new RootInteraction("ScoutSingle",launch.getId());single.build();
            var normal=NativeBasicAttackPaths.resolve(context,single,InteractionType.Primary);
            assertEquals(1,normal.projectileOccurrences().get(launch));
            assertEquals(NativeBasicAttackPaths.Kind.NORMAL,normal.classify(launch).orElseThrow());
            assertTrue(normal.damageOccurrences().isEmpty());
            var repeated=new RootInteraction("ScoutRepeated",launch.getId(),launch.getId());repeated.build();
            assertEquals(2,NativeBasicAttackPaths.resolve(context,repeated,InteractionType.Primary)
                    .projectileOccurrences().get(launch));
        }finally{AssetRegistry.unregister(assets);NativeFixtureAssetCache.clear(Interaction.class);}
    }
}
