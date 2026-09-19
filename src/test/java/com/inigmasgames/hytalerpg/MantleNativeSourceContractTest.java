package com.inigmasgames.hytalerpg;

import com.google.gson.JsonParser;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSpawnerGroup;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.combat.DamageCalculator;
import com.hypixel.hytale.server.core.modules.projectile.event.ProjectileLaunchEvent;
import com.hypixel.hytale.protocol.InteractionType;
import com.inigmasgames.hytalerpg.combat.hytale.*;
import com.inigmasgames.hytalerpg.combat.damage.*;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Installed native calculator + shipped Flame Longsword values. Not a connected combat test. */
class MantleNativeSourceContractTest {
    @Test void pinnedNativeCodecSerializesBothCameraVariantsWithoutHudOrGameplayEffects()throws Exception{
        var json=JsonParser.parseString(Files.readString(Path.of("src/main/resources/Server/Entity/Effects/RPG/RPG_Mantle_Aura.json"))).getAsJsonObject();
        var app=com.hypixel.hytale.server.core.asset.type.entityeffect.config.ApplicationEffects.CODEC.decode(
                org.bson.BsonDocument.parse(json.get("ApplicationEffects").toString()),new com.hypixel.hytale.codec.ExtraInfo()).toPacket();
        assertEquals(1,app.particles.length);assertEquals(1,app.firstPersonParticles.length);
        assertEquals(app.particles[0],app.firstPersonParticles[0]);
        assertEquals(.5f,app.firstPersonParticles[0].scale);assertEquals(com.hypixel.hytale.protocol.EntityPart.Self,app.firstPersonParticles[0].targetEntityPart);
        assertTrue(app.firstPersonParticles[0].clearParticlesOnRemove);assertFalse(app.firstPersonParticles[0].detachedFromModel);
        assertNull(app.screenEffect);assertNull(app.abilityEffects);assertNull(app.movementEffects);assertEquals(1,app.horizontalSpeedMultiplier);
    }
    @Test void pinnedNativeCodecPreservesTargetImpactOffsetAndDefaultScale()throws Exception{
        var json=JsonParser.parseString(Files.readString(Path.of("src/main/resources/Server/Entity/Effects/RPG/RPG_Mantle_Impact.json"))).getAsJsonObject();
        var app=com.hypixel.hytale.server.core.asset.type.entityeffect.config.ApplicationEffects.CODEC.decode(
                org.bson.BsonDocument.parse(json.get("ApplicationEffects").toString()),new com.hypixel.hytale.codec.ExtraInfo()).toPacket();
        assertEquals(1,app.particles.length);var p=app.particles[0];
        assertEquals("Impact_Fire",p.systemId);assertEquals(1.25f,p.positionOffset.y());assertEquals(1,p.scale);
        assertTrue(p.clearParticlesOnRemove);assertFalse(p.detachedFromModel);assertNull(app.abilityEffects);
    }
    static final DamageCause FIRE=new DamageCause("Fire"),PHYSICAL=new DamageCause("Physical");
    static HytaleAssetStore<String,DamageCause,IndexedLookupTableAssetMap<String,DamageCause>> assets;
    static HytaleAssetStore<String,ParticleSystem,DefaultAssetMap<String,ParticleSystem>> particleAssets;
    static HytaleAssetStore<String,SoundEvent,IndexedLookupTableAssetMap<String,SoundEvent>> soundAssets;
    /** Reference-key fixtures only; real stock child resolution is enforced by the packaged native smoke. */
    static class ParticleKeys extends DefaultAssetMap<String,ParticleSystem> {
        void seed(){
            var values=new HashMap<String,ParticleSystem>();
            for(var id:List.of("RPG_Mantle_Aura","Impact_Fire"))values.put(id,new ParticleSystem(id,1,new ParticleSpawnerGroup[0],64,10,true));
            putAll("MantleCodecKeys",ParticleSystem.CODEC,values,Map.of(),Map.of());
        }
    }
    static float authoredBase,authoredVariance;
    static class Causes extends IndexedLookupTableAssetMap<String,DamageCause> {
        Causes(){super(DamageCause[]::new);}
        void seed(){putAll("MantleSourceFixture",DamageCause.CODEC,Map.of("Fire",FIRE,"Physical",PHYSICAL),Map.of(),Map.of());}
    }
    @BeforeAll static void setup() throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        var map=new Causes();map.seed();
        var builder=HytaleAssetStore.builder(DamageCause.class,(IndexedLookupTableAssetMap<String,DamageCause>)map)
                .setPath("Entity/Damage").setCodec(DamageCause.CODEC).setKeyFunction(DamageCause::getId).setReplaceOnRemove(DamageCause::new);
        assets=new HytaleAssetStore<>(builder){final EventBus events=new EventBus(false);@Override protected EventBus getEventBus(){return events;}};
        AssetRegistry.register(assets);
        var particles=new ParticleKeys();particles.seed();
        var particleBuilder=HytaleAssetStore.builder(ParticleSystem.class,(DefaultAssetMap<String,ParticleSystem>)particles)
                .setPath("Particles").setCodec(ParticleSystem.CODEC).setKeyFunction(ParticleSystem::getId);
        particleAssets=new HytaleAssetStore<>(particleBuilder){final EventBus events=new EventBus(false);@Override protected EventBus getEventBus(){return events;}};
        AssetRegistry.register(particleAssets);
        var soundBuilder=HytaleAssetStore.builder(SoundEvent.class,new IndexedLookupTableAssetMap<String,SoundEvent>(SoundEvent[]::new))
                .setPath("Audio/SoundEvents").setCodec(SoundEvent.CODEC).setKeyFunction(SoundEvent::getId).setReplaceOnRemove(SoundEvent::new);
        soundAssets=new HytaleAssetStore<>(soundBuilder){final EventBus events=new EventBus(false);@Override protected EventBus getEventBus(){return events;}};
        AssetRegistry.register(soundAssets);
        var path=Path.of(System.getProperty("user.home"),"AppData/Roaming/Hytale/install/pre-release/package/game/latest/Assets.zip");
        try(var zip=new ZipFile(path.toFile());var stream=zip.getInputStream(zip.getEntry("Server/Item/Items/Weapon/Longsword/Weapon_Longsword_Flame.json"))){
            var json=JsonParser.parseString(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var calculator=json.getAsJsonObject("InteractionVars").getAsJsonObject("Longsword_Swing_Left_Damage")
                    .getAsJsonArray("Interactions").get(0).getAsJsonObject().getAsJsonObject("DamageCalculator");
            authoredBase=calculator.getAsJsonObject("BaseDamage").get("Fire").getAsFloat();
            authoredVariance=calculator.get("RandomPercentageModifier").getAsFloat();
        }
    }
    @AfterAll static void cleanup(){if(assets!=null)AssetRegistry.unregister(assets);if(particleAssets!=null)AssetRegistry.unregister(particleAssets);if(soundAssets!=null)AssetRegistry.unregister(soundAssets);}
    /** Protected fixture configuration only: native calculateDamage/computeDamageRange are NOT overridden. */
    static class NativeCalculator extends DamageCalculator {
        NativeCalculator(){type=Type.ABSOLUTE;baseDamageRaw=new Object2FloatOpenHashMap<>();baseDamageRaw.put("Fire",authoredBase);
            baseDamage=new Int2FloatOpenHashMap();baseDamage.put(DamageCause.getAssetMap().getIndex("Fire"),authoredBase);randomPercentageModifier=authoredVariance;}
    }
    @Test void realFlameLongswordIsVariableNotAFixedAuditedPowerScalar(){
        assertEquals(31,authoredBase);assertEquals(.15f,authoredVariance);
        var calc=new NativeCalculator();float[] range=new float[2];calc.computeDamageRange(1,range);
        assertEquals(26.35,range[0],1e-4);assertEquals(35.65,range[1],1e-4);
    }
    @Test void installedCalculatorEvaluatesIntoFreshMapsNotAReadOnlyResolvedReceipt()throws Exception{
        var calc=new NativeCalculator();var first=calc.calculateDamage(1);var second=calc.calculateDamage(1);assertNotSame(first,second);
        var samples=new ArrayList<Float>();for(int i=0;i<32;i++){
            float value=calc.calculateDamage(1).getFloat(FIRE);assertTrue(value>=26.3499f&&value<=35.6501f);samples.add(value);
        }
        // Samples document stochastic behavior; no probabilistic "must differ" pass criterion.
        var path=Path.of("build/mantle-source/native-calculator-observations.json");Files.createDirectories(path.getParent());
        Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "item","Weapon_Longsword_Flame","base",authoredBase,"variance",authoredVariance,"samples",samples,
                "sourceReceiptCaptured",false,"connectedVerified",false)));
    }
    @Test void rangedLaunchFlagIsRealButNotAResolvedDamageReceipt(){
        var weapon=new ProjectileLaunchEvent(null,InteractionType.Primary,true);
        var spell=new ProjectileLaunchEvent(null,InteractionType.Ability2,false);
        assertTrue(weapon.isLaunchedByWeapon());assertFalse(spell.isLaunchedByWeapon());
        assertEquals(InteractionType.Primary,weapon.getInteractionType());
        // Null entity reference makes this explicitly an API value test, not a production launch claim.
    }
    @Test void pinnedCodecBindsManagedCalculatorWithoutReplacingNativeDamageLeaf(){
        var codec=ManagedWeaponFireInteraction.codec((context,leaf,attribute,base,variance)->base);
        var json=org.bson.BsonDocument.parse("{\"DamageCalculator\":{\"Type\":\"Absolute\",\"BaseDamage\":{\"Fire\":31},\"RandomPercentageModifier\":0.15}}");
        var leaf=codec.decode(json,new com.hypixel.hytale.codec.ExtraInfo());
        assertInstanceOf(com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DamageEntityInteraction.class,leaf);
        assertInstanceOf(ManagedWeaponFireInteraction.FireCalculator.class,leaf.getDamageCalculator());
        assertEquals("MANAGED_FIRE_OUTSIDE_NATIVE_LEAF",assertThrows(IllegalStateException.class,
                ()->leaf.getDamageCalculator().calculateDamage(1)).getMessage());
    }
    @Test void packagedWeaponChangesOnlyFourAuthoredFireLeafTypes()throws Exception{
        var path=Path.of(System.getProperty("user.home"),"AppData/Roaming/Hytale/install/pre-release/package/game/latest/Assets.zip");
        String entry="Server/Item/Items/Weapon/Longsword/Weapon_Longsword_Flame.json";
        try(var zip=new ZipFile(path.toFile());var stream=zip.getInputStream(zip.getEntry(entry))){
            var nativeItem=JsonParser.parseString(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            var packaged=JsonParser.parseString(Files.readString(Path.of("src/main/resources",entry))).getAsJsonObject();
            int changed=0;
            for(var variable:packaged.getAsJsonObject("InteractionVars").entrySet())
                for(var value:variable.getValue().getAsJsonObject().getAsJsonArray("Interactions")){
                    var leaf=value.getAsJsonObject();
                    if(leaf.has("Type")&&leaf.get("Type").getAsString().equals(ManagedWeaponFireInteraction.TYPE)){
                        leaf.remove("Type");changed++;
                    }
                }
            assertEquals(4,changed);assertEquals(nativeItem,packaged,"Collision, animation, charge, durability and all original source definitions must be identical");
        }
    }
    @Test void unsupportedConditionalAndNonFireCalculatorsAreRejectedAtDecode(){
        var codec=ManagedWeaponFireInteraction.codec((context,leaf,attribute,base,variance)->base);
        for(String calculator:List.of("{\"Type\":\"Absolute\",\"BaseDamage\":{\"Physical\":31}}",
                "{\"Type\":\"Absolute\",\"BaseDamage\":{\"Fire\":31},\"RandomPercentageModifier\":2}"))
            assertThrows(RuntimeException.class,()->codec.decode(org.bson.BsonDocument.parse("{\"DamageCalculator\":"+calculator+"}"),new com.hypixel.hytale.codec.ExtraInfo()));
    }
    @Test void nativeDamageCanCarryCompleteExecutionWitnessWithoutChangingScalar(){
        var execution=WeaponFireDecisionTest.execution();var decision=new WeaponFireDecision(execution);
        var damage=new Damage(Damage.NULL_SOURCE,FIRE,31);
        var metadata=new HytaleDamageMetadata(execution.identity().actorId(),execution.identity().rootId(),"skill","correlation",31,100);
        var witness=new HytaleDamageAdapter.WeaponComponent(decision,"fire");
        HytaleDamageAdapter.attachWeaponComponent(damage,metadata,witness);
        assertSame(witness,HytaleDamageAdapter.weaponComponent(damage));assertEquals(31,damage.getAmount());
        assertEquals(100,HytaleDamageAdapter.weaponComponent(damage).decision().execution().sourceFire());
    }
    @Test void foreignOwnerAndPhysicalDamageCannotBeMislabeledAsTheFireComponent(){
        var execution=WeaponFireDecisionTest.execution();var witness=new HytaleDamageAdapter.WeaponComponent(new WeaponFireDecision(execution),"fire");
        var metadata=new HytaleDamageMetadata(execution.identity().actorId(),"root","skill","correlation",31,100);
        var physical=new Damage(Damage.NULL_SOURCE,PHYSICAL,400);
        assertThrows(IllegalArgumentException.class,()->HytaleDamageAdapter.attachWeaponComponent(physical,metadata,witness));assertEquals(400,physical.getAmount());
        var foreign=new HytaleDamageMetadata(UUID.randomUUID(),"root","skill","correlation",31,100);
        assertThrows(IllegalArgumentException.class,()->HytaleDamageAdapter.attachWeaponComponent(new Damage(Damage.NULL_SOURCE,FIRE,31),foreign,witness));
    }
}
