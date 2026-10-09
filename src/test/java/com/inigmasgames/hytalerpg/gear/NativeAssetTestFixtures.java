package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemReticleConfig;
import com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations;
import com.hypixel.hytale.server.core.asset.type.itemsound.config.ItemSoundSet;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.camera.CameraEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.trail.config.Trail;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSpawnerGroup;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.SelectInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.SerialInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.ParallelInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.ConditionInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ResetCooldownInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.selector.SelectorType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.selector.AOECircleSelector;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.selector.HorizontalSelector;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.combat.Knockback;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.combat.ForceKnockback;
import com.hypixel.hytale.server.core.modules.interaction.interaction.UnarmedInteractions;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.modules.projectile.config.PhysicsConfig;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsConfig;
import org.bson.BsonDocument;
import org.bson.BsonValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipFile;

/**
 * Plain JUnit fixture for native codec tests, including GearCombatProductionTest and
 * proc/summon tests. It installs real decoded damage causes and minimal native asset
 * maps in the unit JVM. It does not create a server or authenticate a client.
 * Open once in a fresh nativeControlTest JVM and share the instance across test methods:
 * Hytale caches asset stores in static fields. Close at the end of that test class.
 * nativeControlTest supplies Hytale's logger.
 */
public final class NativeAssetTestFixtures implements AutoCloseable {
    private static final String PACK = "NativeOfflineQualification";
    private static final Path RESOURCE_ROOT = Path.of(System.getProperty(
            "nativeCarrier.serverRoot", "src/main/resources/Server"));
    private final Map<String, DamageCause> oldStatic = new LinkedHashMap<>();
    private final Set<String> commonAssets = new LinkedHashSet<>();
    private Set<String> installedRoots;
    private final CauseMap causes = new CauseMap();
    private final QualityMap qualities = new QualityMap();
    private final ItemMap items = new ItemMap();
    private final InteractionMap interactions = new InteractionMap();
    private final RootMap roots = new RootMap();
    private final ProjectileMap projectiles = new ProjectileMap();
    private final ParticleMap particles = new ParticleMap();
    private final AnimationMap animations = new AnimationMap();
    private final SoundSetMap soundSets = new SoundSetMap();
    private final SoundMap sounds = new SoundMap();
    private final DefaultAssetMap<String, UnarmedInteractions> unarmed = new DefaultAssetMap<>();
    private final ModelMap models = new ModelMap();
    private final ReticleMap reticles = new ReticleMap();
    private final CameraMap cameras = new CameraMap();
    private final TrailMap trails = new TrailMap();
    private final StatMap stats = new StatMap();
    private final EffectMap effects = new EffectMap();
    private final HytaleAssetStore<String, DamageCause, IndexedLookupTableAssetMap<String, DamageCause>> causeStore;
    private final HytaleAssetStore<String, ItemQuality, IndexedLookupTableAssetMap<String, ItemQuality>> qualityStore;
    private final HytaleAssetStore<String, Item, DefaultAssetMap<String, Item>> itemStore;
    private final HytaleAssetStore<String, Interaction, IndexedLookupTableAssetMap<String, Interaction>> interactionStore;
    private final HytaleAssetStore<String, RootInteraction, IndexedLookupTableAssetMap<String, RootInteraction>> rootStore;
    private final HytaleAssetStore<String, ProjectileConfig, DefaultAssetMap<String, ProjectileConfig>> projectileStore;
    private final HytaleAssetStore<String, ParticleSystem, DefaultAssetMap<String, ParticleSystem>> particleStore;
    private final HytaleAssetStore<String, ItemPlayerAnimations, DefaultAssetMap<String, ItemPlayerAnimations>> animationStore;
    private final HytaleAssetStore<String, ItemSoundSet, IndexedLookupTableAssetMap<String, ItemSoundSet>> soundSetStore;
    private final HytaleAssetStore<String, SoundEvent, IndexedLookupTableAssetMap<String, SoundEvent>> soundStore;
    private final HytaleAssetStore<String, UnarmedInteractions, DefaultAssetMap<String, UnarmedInteractions>> unarmedStore;
    private final HytaleAssetStore<String, ModelAsset, DefaultAssetMap<String, ModelAsset>> modelStore;
    private final HytaleAssetStore<String, ItemReticleConfig, IndexedLookupTableAssetMap<String, ItemReticleConfig>> reticleStore;
    private final HytaleAssetStore<String, CameraEffect, IndexedLookupTableAssetMap<String, CameraEffect>> cameraStore;
    private final HytaleAssetStore<String, Trail, DefaultAssetMap<String, Trail>> trailStore;
    private final HytaleAssetStore<String, EntityStatType, IndexedLookupTableAssetMap<String, EntityStatType>> statStore;
    private final HytaleAssetStore<String, EntityEffect, IndexedLookupTableAssetMap<String, EntityEffect>> effectStore;

    private NativeAssetTestFixtures() throws IOException {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        Interaction.CODEC.register(ManagedCarrierDamageInteraction.TYPE,
                ManagedCarrierDamageInteraction.class, ManagedCarrierDamageInteraction.CODEC);
        Interaction.CODEC.register(ManagedGearDamageInteraction.TYPE,
                ManagedGearDamageInteraction.class, ManagedGearDamageInteraction.CODEC);
        Interaction.CODEC.register(ManagedCarrierProjectile.TYPE,
                ManagedCarrierProjectile.class, ManagedCarrierProjectile.CODEC);
        Interaction.CODEC.register(ManagedGearProjectile.TYPE,
                ManagedGearProjectile.class, ManagedGearProjectile.CODEC);
        Interaction.CODEC.register(NativeTwinAssaultGate.TYPE,
                NativeTwinAssaultGate.class, NativeTwinAssaultGate.CODEC);
        Interaction.CODEC.register("Simple", SimpleInteraction.class, SimpleInteraction.CODEC);
        Interaction.CODEC.register("Serial", SerialInteraction.class, SerialInteraction.CODEC);
        Interaction.CODEC.register("Parallel", ParallelInteraction.class, ParallelInteraction.CODEC);
        Interaction.CODEC.register("Condition", ConditionInteraction.class, ConditionInteraction.CODEC);
        Interaction.CODEC.register("Selector", SelectInteraction.class, SelectInteraction.CODEC);
        Interaction.CODEC.register("ResetCooldown", ResetCooldownInteraction.class, ResetCooldownInteraction.CODEC);
        Interaction.CODEC.register("ApplyForce", com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ApplyForceInteraction.class,
                com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ApplyForceInteraction.CODEC);
        Interaction.CODEC.register("BreakBlock", com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.BreakBlockInteraction.class,
                com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.BreakBlockInteraction.CODEC);
        Interaction.CODEC.register("Chaining", com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ChainingInteraction.class,
                com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ChainingInteraction.CODEC);
        Interaction.CODEC.register("Charging", com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ChargingInteraction.class,
                com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ChargingInteraction.CODEC);
        Interaction.CODEC.register("ChangeState", com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ChangeStateInteraction.class,
                com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ChangeStateInteraction.CODEC);
        Interaction.CODEC.register("MovementCondition", com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.MovementConditionInteraction.class,
                com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.MovementConditionInteraction.CODEC);
        Interaction.CODEC.register("UseBlock", com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.UseBlockInteraction.class,
                com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.UseBlockInteraction.CODEC);
        Interaction.CODEC.register("Wielding", com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.WieldingInteraction.class,
                com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.WieldingInteraction.CODEC);
        Interaction.CODEC.register("ChangeActiveSlot", com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.ChangeActiveSlotInteraction.class,
                com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.ChangeActiveSlotInteraction.CODEC);
        Interaction.CODEC.register("Repeat", com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.RepeatInteraction.class,
                com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.RepeatInteraction.CODEC);
        Interaction.CODEC.register("ChangeStat", com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.ChangeStatInteraction.class,
                com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.ChangeStatInteraction.CODEC);
        Interaction.CODEC.register("ClearEntityEffect", com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.ClearEntityEffectInteraction.class,
                com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.ClearEntityEffectInteraction.CODEC);
        Interaction.CODEC.register("DamageEntity", com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DamageEntityInteraction.class,
                com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DamageEntityInteraction.CODEC);
        Interaction.CODEC.register("LaunchProjectile", com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.LaunchProjectileInteraction.class,
                com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.LaunchProjectileInteraction.CODEC);
        Interaction.CODEC.register("ApplyEffect", com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.simple.ApplyEffectInteraction.class,
                com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.simple.ApplyEffectInteraction.CODEC);
        Interaction.CODEC.register("DurabilityCondition", com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.DurabilityConditionInteraction.class,
                com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.DurabilityConditionInteraction.CODEC);
        Interaction.CODEC.register("Replace", com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.ReplaceInteraction.class,
                com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.ReplaceInteraction.CODEC);
        Interaction.CODEC.register("StatsCondition", com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.StatsConditionInteraction.class,
                com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.StatsConditionInteraction.CODEC);
        SelectorType.CODEC.register("AOECircle", AOECircleSelector.class, AOECircleSelector.CODEC);
        SelectorType.CODEC.register("Horizontal", HorizontalSelector.class, HorizontalSelector.CODEC);
        SelectorType.CODEC.register("Stab", com.hypixel.hytale.server.core.modules.interaction.interaction.config.selector.StabSelector.class,
                com.hypixel.hytale.server.core.modules.interaction.interaction.config.selector.StabSelector.CODEC);
        Knockback.CODEC.register("Force", ForceKnockback.class, ForceKnockback.CODEC);
        PhysicsConfig.CODEC.register("Standard", StandardPhysicsConfig.class, StandardPhysicsConfig.CODEC);
        causeStore = new HytaleAssetStore<>(HytaleAssetStore.builder(DamageCause.class,
                (IndexedLookupTableAssetMap<String, DamageCause>) causes).setPath("Entity/Damage")
                .setCodec(DamageCause.CODEC).setKeyFunction(DamageCause::getId).setReplaceOnRemove(DamageCause::new)) {
            private final EventBus events = new EventBus(false);
            @Override protected EventBus getEventBus() { return events; }
        };
        qualityStore = new HytaleAssetStore<>(HytaleAssetStore.builder(ItemQuality.class,
                (IndexedLookupTableAssetMap<String, ItemQuality>) qualities).setPath("Item/Qualities")
                .setCodec(ItemQuality.CODEC).setKeyFunction(ItemQuality::getId).setReplaceOnRemove(ItemQuality::new)) {
            private final EventBus events = new EventBus(false);
            @Override protected EventBus getEventBus() { return events; }
        };
        itemStore = new HytaleAssetStore<>(HytaleAssetStore.builder(Item.class,
                (DefaultAssetMap<String, Item>) items).setPath("Item/Items")
                .setCodec(Item.CODEC).setKeyFunction(Item::getId)) {
            private final EventBus events = new EventBus(false);
            @Override protected EventBus getEventBus() { return events; }
        };
        interactionStore = new HytaleAssetStore<>(HytaleAssetStore.builder(Interaction.class,
                (IndexedLookupTableAssetMap<String, Interaction>) interactions).setPath("Item/Interactions")
                .setCodec(Interaction.CODEC).setKeyFunction(Interaction::getId)
                .setReplaceOnRemove(SimpleInteraction::new)) {
            private final EventBus events = new EventBus(false);
            @Override protected EventBus getEventBus() { return events; }
        };
        rootStore = new HytaleAssetStore<>(HytaleAssetStore.builder(RootInteraction.class,
                (IndexedLookupTableAssetMap<String, RootInteraction>) roots).setPath("Item/RootInteractions")
                .setCodec(RootInteraction.CODEC).setKeyFunction(RootInteraction::getId)
                .setReplaceOnRemove(RootInteraction::new)) {
            private final EventBus events = new EventBus(false);
            @Override protected EventBus getEventBus() { return events; }
        };
        projectileStore = new HytaleAssetStore<>(HytaleAssetStore.builder(ProjectileConfig.class,
                (DefaultAssetMap<String, ProjectileConfig>) projectiles).setPath("ProjectileConfigs")
                .setCodec(ProjectileConfig.CODEC).setKeyFunction(ProjectileConfig::getId)) {
            private final EventBus events = new EventBus(false);
            @Override protected EventBus getEventBus() { return events; }
        };
        particleStore = new HytaleAssetStore<>(HytaleAssetStore.builder(ParticleSystem.class,
                (DefaultAssetMap<String, ParticleSystem>) particles).setPath("Particles")
                .setCodec(ParticleSystem.CODEC).setKeyFunction(ParticleSystem::getId)) {
            private final EventBus events = new EventBus(false);
            @Override protected EventBus getEventBus() { return events; }
        };
        animationStore = new HytaleAssetStore<>(HytaleAssetStore.builder(ItemPlayerAnimations.class,
                (DefaultAssetMap<String, ItemPlayerAnimations>) animations).setPath("Item/Animations")
                .setCodec(ItemPlayerAnimations.CODEC).setKeyFunction(ItemPlayerAnimations::getId)) {
            private final EventBus events = new EventBus(false);
            @Override protected EventBus getEventBus() { return events; }
        };
        soundSetStore = new HytaleAssetStore<>(HytaleAssetStore.builder(ItemSoundSet.class,
                (IndexedLookupTableAssetMap<String, ItemSoundSet>) soundSets).setPath("Item/SoundSets")
                .setCodec(ItemSoundSet.CODEC).setKeyFunction(ItemSoundSet::getId)
                .setReplaceOnRemove(ItemSoundSet::new)) {
            private final EventBus events = new EventBus(false);
            @Override protected EventBus getEventBus() { return events; }
        };
        soundStore = new HytaleAssetStore<>(HytaleAssetStore.builder(SoundEvent.class,
                (IndexedLookupTableAssetMap<String, SoundEvent>) sounds).setPath("Audio/SoundEvents")
                .setCodec(SoundEvent.CODEC).setKeyFunction(SoundEvent::getId)
                .setReplaceOnRemove(SoundEvent::new)) {
            private final EventBus events = new EventBus(false);
            @Override protected EventBus getEventBus() { return events; }
        };
        unarmedStore = new HytaleAssetStore<>(HytaleAssetStore.builder(UnarmedInteractions.class,
                unarmed).setPath("Item/UnarmedInteractions")
                .setCodec(UnarmedInteractions.CODEC).setKeyFunction(UnarmedInteractions::getId)) {
            private final EventBus events = new EventBus(false);
            @Override protected EventBus getEventBus() { return events; }
        };
        modelStore = new HytaleAssetStore<>(HytaleAssetStore.builder(ModelAsset.class,
                (DefaultAssetMap<String, ModelAsset>) models).setPath("Models")
                .setCodec(ModelAsset.CODEC).setKeyFunction(ModelAsset::getId)) {
            private final EventBus events = new EventBus(false);
            @Override protected EventBus getEventBus() { return events; }
        };
        reticleStore = new HytaleAssetStore<>(HytaleAssetStore.builder(ItemReticleConfig.class,
                (IndexedLookupTableAssetMap<String, ItemReticleConfig>) reticles).setPath("Item/Reticles")
                .setCodec(ItemReticleConfig.CODEC).setKeyFunction(ItemReticleConfig::getId)
                .setReplaceOnRemove(ItemReticleConfig::new)) {
            private final EventBus events = new EventBus(false);
            @Override protected EventBus getEventBus() { return events; }
        };
        cameraStore = new HytaleAssetStore<>(HytaleAssetStore.builder(CameraEffect.class,
                (IndexedLookupTableAssetMap<String, CameraEffect>) cameras).setPath("CameraEffects")
                .setCodec(CameraEffect.CODEC).setKeyFunction(CameraEffect::getId)
                .setReplaceOnRemove(NativeAssetTestFixtures::cameraEffect)) {
            private final EventBus events = new EventBus(false);
            @Override protected EventBus getEventBus() { return events; }
        };
        trailStore = new HytaleAssetStore<>(HytaleAssetStore.builder(Trail.class,
                (DefaultAssetMap<String, Trail>) trails).setPath("Trails")
                .setCodec(Trail.CODEC).setKeyFunction(Trail::getId)) {
            private final EventBus events = new EventBus(false);
            @Override protected EventBus getEventBus() { return events; }
        };
        statStore = new HytaleAssetStore<>(HytaleAssetStore.builder(EntityStatType.class,
                (IndexedLookupTableAssetMap<String, EntityStatType>) stats).setPath("Entity/Stats")
                .setCodec(EntityStatType.CODEC).setKeyFunction(EntityStatType::getId)
                .setReplaceOnRemove(EntityStatType::getUnknownFor)) {
            private final EventBus events = new EventBus(false);
            @Override protected EventBus getEventBus() { return events; }
        };
        effectStore = new HytaleAssetStore<>(HytaleAssetStore.builder(EntityEffect.class,
                (IndexedLookupTableAssetMap<String, EntityEffect>) effects).setPath("Entity/Effects")
                .setCodec(EntityEffect.CODEC).setKeyFunction(EntityEffect::getId)
                .setReplaceOnRemove(EntityEffect::new)) {
            private final EventBus events = new EventBus(false);
            @Override protected EventBus getEventBus() { return events; }
        };
        AssetRegistry.register(causeStore);
        AssetRegistry.register(particleStore);
        AssetRegistry.register(animationStore);
        AssetRegistry.register(soundStore);
        AssetRegistry.register(soundSetStore);
        AssetRegistry.register(unarmedStore);
        AssetRegistry.register(modelStore);
        AssetRegistry.register(reticleStore);
        AssetRegistry.register(qualityStore);
        AssetRegistry.register(interactionStore);
        AssetRegistry.register(rootStore);
        AssetRegistry.register(projectileStore);
        AssetRegistry.register(itemStore);
        AssetRegistry.register(cameraStore);
        AssetRegistry.register(trailStore);
        AssetRegistry.register(statStore);
        AssetRegistry.register(effectStore);
        Interaction.CODEC.register("ModifyInventory", com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.ModifyInventoryInteraction.class,
                com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.ModifyInventoryInteraction.CODEC);
    }

    public static NativeAssetTestFixtures open() throws IOException {
        var fixture = new NativeAssetTestFixtures();
        boolean ready = false;
        try {
            fixture.loadDamageCauses();
            fixture.loadQualities();
            for (var id : List.of("Staff", "Wand", "Spellbook", "Shield", "Item", "Default"))
                fixture.animations.seed(Map.of(id, new ItemPlayerAnimations(id, Map.of(), null, null, null, false)));
            fixture.loadProjectileModels();
            ready = true;
            return fixture;
        } finally {
            if (!ready) fixture.close();
        }
    }

    public void seedPackagedRootReferences(String json) {
        var item = BsonDocument.parse(json);
        for (var section : List.of("Interactions", "InteractionVars")) {
            if (!item.containsKey(section)) continue;
            for (var value : item.getDocument(section).values()) {
                if (!value.isString()) continue;
                String id = value.asString().getValue();
                if ((id.startsWith("RPG_GearRoute_R_") || id.startsWith("RPG_Carrier_"))
                        && roots.getAsset(id) == null)
                    roots.seed(Map.of(id, new RootInteraction(id)));
            }
        }
    }

    public void seedAbilityRootReferences(String json) {
        var ability=BsonDocument.parse(json).getDocument("Ability");
        String id=ability.getString("Cast").getValue();
        if (!Files.isRegularFile(RESOURCE_ROOT.resolve("Item/RootInteractions/RPG/"+id+".json")))
            throw new IllegalStateException("Missing owned ability root: "+id);
        if (roots.getAsset(id)==null) roots.seed(Map.of(id,new RootInteraction(id)));
    }

    /** Decode the installed animation profile and its common assets for variant publication. */
    public void loadInstalledAnimation(String id) throws IOException {
        var zipPath = Path.of(System.getProperty("user.home"), "AppData", "Roaming", "Hytale",
                "install", "pre-release", "package", "game", "latest", "Assets.zip");
        try (var zip = new ZipFile(zipPath.toFile())) {
            var entry = zip.stream().filter(e -> e.getName().startsWith("Server/Item/Animations/")
                    && e.getName().endsWith("/"+id+".json")).findFirst().orElseThrow(() -> new IOException("Installed animation "+id));
            String json;
            try (var input = zip.getInputStream(entry)) {
                json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            var document = BsonDocument.parse(json);
            if (document.containsKey("Parent")) loadInstalledAnimation(document.getString("Parent").getValue());
            registerCommonAssets(document);
            // decode reads Parent from the source document. Dynamic RawAsset buffers
            // used by action publication are already flattened and deliberately have no parent key.
            animations.seed(Map.of(id,Objects.requireNonNull(animationStore.decode(PACK,id,document),id)));
        }
    }
    public void loadInstalledDamageParents() throws IOException {
        var zipPath = Path.of(System.getProperty("user.home"), "AppData", "Roaming", "Hytale",
                "install", "pre-release", "package", "game", "latest", "Assets.zip");
        try (var zip = new ZipFile(zipPath.toFile())) {
            for (var id : List.of("DamageEntityParent", "DamageEntityParent_Hit_Interrupt")) {
                var entry = Objects.requireNonNull(zip.getEntry(
                        "Server/Item/Interactions/Weapons/" + id + ".json"), id);
                String json;
                try (var input = zip.getInputStream(entry)) {
                    json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
                var document = BsonDocument.parse(json);
                if (!document.containsKey("Type")) document.put("Type", new org.bson.BsonString("DamageEntity"));
                seedActionReferences(document);
                NativeSwordActionAssets.load(Interaction.getAssetStore(), Map.of(id, document.toJson()));
            }
        }
    }
    public void loadInstalledParentReferences(String json) throws IOException {
        var zipPath = Path.of(System.getProperty("user.home"), "AppData", "Roaming", "Hytale",
                "install", "pre-release", "package", "game", "latest", "Assets.zip");
        try (var zip = new ZipFile(zipPath.toFile())) {
            var index = new java.util.HashMap<String, java.util.zip.ZipEntry>();
            zip.stream().filter(entry -> entry.getName().startsWith("Server/Item/Interactions/")
                    && entry.getName().endsWith(".json"))
                    .forEach(entry -> index.putIfAbsent(entry.getName().substring(
                            entry.getName().lastIndexOf('/') + 1, entry.getName().length() - 5), entry));
            loadParentReferences(BsonDocument.parse(json), zip, index, new LinkedHashSet<>());
        }
    }
    private void loadParentReferences(BsonValue value, ZipFile zip,
                                      Map<String, java.util.zip.ZipEntry> index, Set<String> seen) throws IOException {
        if (value.isArray()) {
            for (var child : value.asArray()) loadParentReferences(child, zip, index, seen);
            return;
        }
        if (!value.isDocument()) return;
        var document = value.asDocument();
        if (document.containsKey("Parent") && document.get("Parent").isString()) {
            String id = document.getString("Parent").getValue();
            if (!id.startsWith("RPG_Action_") && Interaction.getAssetMap().getAsset(id) == null && seen.add(id)) {
                if (id.startsWith("RPG_Carrier_")) {
                    var path = RESOURCE_ROOT.resolve("Item/Interactions/RPG/Carriers/" + id + ".json");
                    String source = Files.readString(path);
                    seedActionReferences(source);
                    NativeSwordActionAssets.load(Interaction.getAssetStore(), Map.of(id, source));
                    for (var child : document.values()) loadParentReferences(child, zip, index, seen);
                    return;
                }
                var entry = Objects.requireNonNull(index.get(id), "Installed interaction parent " + id);
                String source;
                try (var input = zip.getInputStream(entry)) {
                    source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
                var parent = BsonDocument.parse(source);
                loadParentReferences(parent, zip, index, seen);
                if (!parent.containsKey("Type") && id.contains("Damage"))
                    parent.put("Type", new org.bson.BsonString("DamageEntity"));
                seedActionReferences(parent);
                NativeSwordActionAssets.load(Interaction.getAssetStore(), Map.of(id, parent.toJson()));
            }
        }
        for (var child : document.values()) loadParentReferences(child, zip, index, seen);
    }

    /** Supply installed reference keys used by rendered action nodes. */
    public void seedActionReferences(String json) {
        seedActionReferences(BsonDocument.parse(json));
    }
    public void loadActionCommonReferences(String json) throws IOException {
        registerCommonAssets(BsonDocument.parse(json));
    }
    private void seedActionReferences(BsonValue value) {
        if (value.isArray()) { value.asArray().forEach(this::seedActionReferences); return; }
        if (!value.isDocument()) return;
        for (var entry : value.asDocument().entrySet()) {
            if (Set.of("Stamina", "StaminaRegenDelay", "SignatureEnergy", "SignatureCharges", "Ammo", "Mana").contains(entry.getKey())
                    && stats.getAsset(entry.getKey()) == null)
                stats.seed(Map.of(entry.getKey(), new EntityStatType(entry.getKey(), 0, 0, 100,
                        false, null, null, null, null)));
            if (entry.getValue().isString()) {
                String id = entry.getValue().asString().getValue();
                switch (entry.getKey()) {
                    case "SoundEventId", "WorldSoundEventId", "LocalSoundEventId", "HitSoundLayer" -> seedSound(id);
                    case "SystemId" -> { if (particles.getAsset(id) == null)
                        particles.seed(Map.of(id, new ParticleSystem(id, 1, new ParticleSpawnerGroup[0], 64, 10, true))); }
                    case "CameraEffect" -> { if (cameras.getAsset(id) == null)
                        cameras.seed(Map.of(id, cameraEffect(id))); }
                    case "TrailId" -> { if (trails.getAsset(id) == null)
                        trails.seed(Map.of(id, new Trail(id, null, null, null, 1, 0, 0, false, null, null, null))); }
                    case "EntityStatId" -> { if (stats.getAsset(id) == null)
                        stats.seed(Map.of(id, new EntityStatType(id, 0, 0, 100, false, null, null, null, null))); }
                    case "EffectId" -> { if (effects.getAsset(id) == null)
                        effects.seed(Map.of(id, new EntityEffect(id))); }
                }
            }
            seedActionReferences(entry.getValue());
        }
    }
    private static CameraEffect cameraEffect(String id) {
        return new CameraEffect() {
            { this.id = id; }
            @Override public com.hypixel.hytale.protocol.packets.camera.CameraShakeEffect createCameraShakePacket() { return null; }
            @Override public com.hypixel.hytale.protocol.packets.camera.CameraShakeEffect createCameraShakePacket(float scale) { return null; }
        };
    }

    /** Public setup for native cause fields that are initialized by the server in production. */
    public void loadDamageCauses() throws IOException {
        var selected = List.of("Physical", "Projectile", "Elemental", "Fire", "Ice", "Wind",
                "Lightning", "RPG_Nature", "RPG_Void", "Command", "Environment", "Drowning",
                "Fall", "OutOfWorld", "Suffocation", "Bludgeoning", "Earth", "Poison", "Water");
        var decoded = new LinkedHashMap<String, DamageCause>();
        var zipPath = Path.of(System.getProperty("user.home"), "AppData", "Roaming", "Hytale",
                "install", "pre-release", "package", "game", "latest", "Assets.zip");
        try (var zip = new ZipFile(zipPath.toFile())) {
            for (var id : selected) {
                String json;
                var local = RESOURCE_ROOT.resolve("Entity/Damage/" + id + ".json");
                if (Files.isRegularFile(local)) json = Files.readString(local);
                else {
                    var entry = Objects.requireNonNull(zip.getEntry("Server/Entity/Damage/" + id + ".json"), id);
                    try (var input = zip.getInputStream(entry)) {
                        json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
                var cause = Objects.requireNonNull(causeStore.decode(PACK, id, BsonDocument.parse(json)), id);
                decoded.put(id, cause);
                causes.seed(Map.of(id, cause));
            }
        }
        oldStatic.put("Physical", DamageCause.PHYSICAL);
        oldStatic.put("Projectile", DamageCause.PROJECTILE);
        oldStatic.put("Command", DamageCause.COMMAND);
        oldStatic.put("Drowning", DamageCause.DROWNING);
        oldStatic.put("Environment", DamageCause.ENVIRONMENT);
        oldStatic.put("Fall", DamageCause.FALL);
        oldStatic.put("OutOfWorld", DamageCause.OUT_OF_WORLD);
        oldStatic.put("Suffocation", DamageCause.SUFFOCATION);
        DamageCause.PHYSICAL = decoded.get("Physical");
        DamageCause.PROJECTILE = decoded.get("Projectile");
        DamageCause.COMMAND = decoded.get("Command");
        DamageCause.DROWNING = decoded.get("Drowning");
        DamageCause.ENVIRONMENT = decoded.get("Environment");
        DamageCause.FALL = decoded.get("Fall");
        DamageCause.OUT_OF_WORLD = decoded.get("OutOfWorld");
        DamageCause.SUFFOCATION = decoded.get("Suffocation");
        for (var id : selected) if (causes.getAsset(id) == null)
            throw new IllegalStateException("Native damage cause absent: " + id);
    }

    public DamageCause damageCause(String id) {
        return Objects.requireNonNull(DamageCause.getAssetMap().getAsset(id), "Native cause " + id);
    }

    private void loadQualities() throws IOException {
        for (var id : List.of("Drop_Common", "Drop_Uncommon", "Drop_Rare", "Drop_Epic", "Drop_Legendary"))
            particles.seed(Map.of(id, new ParticleSystem(id, 1, new ParticleSpawnerGroup[0], 64, 10, true)));
        for (var id : List.of("RPG_Gear_Common", "RPG_Gear_Uncommon", "RPG_Gear_Rare",
                "RPG_Gear_RandomRare", "RPG_Gear_Epic", "RPG_Gear_Legendary")) {
            var json = BsonDocument.parse(Files.readString(RESOURCE_ROOT.resolve("Item/Qualities/" + id + ".json")));
            registerCommonAssets(json);
            var quality = Objects.requireNonNull(qualityStore.decode(PACK, id, json), id);
            qualities.seed(Map.of(id, quality));
            if (qualities.getIndex(id) < 0) throw new IllegalStateException("Native quality absent: " + id);
        }
    }

    public Item decodeItem(String id, Path path) throws IOException {
        var json = BsonDocument.parse(Files.readString(path));
        registerCommonAssets(json);
        seedInstalledRootKeys(json);
        if (json.containsKey("ItemSoundSetId")) seedSoundSet(json.getString("ItemSoundSetId").getValue());
        if (json.containsKey("SoundEventId")) seedSound(json.getString("SoundEventId").getValue());
        if (json.containsKey("Reticle")) {
            var key = json.getString("Reticle").getValue();
            if (reticles.getAsset(key) == null) reticles.seed(Map.of(key, new ItemReticleConfig(key)));
        }
        seedParticleReferences(json);
        if (json.containsKey("SoundEventId") &&
                SoundEvent.getAssetMap().getAsset(json.getString("SoundEventId").getValue()) == null)
            throw new IllegalStateException("Sound fixture key not installed for " + id +
                    ": local=" + sounds.getAssetMap().keySet() + " static=" +
                    SoundEvent.getAssetMap().getAssetMap().keySet());
        var item = Objects.requireNonNull(itemStore.decode(PACK, id, json), id);
        items.seed(Map.of(id, item));
        return item;
    }

    public void loadInstalledQuality(String id) throws IOException {
        var zipPath=Path.of(System.getProperty("user.home"),"AppData/Roaming/Hytale/install/pre-release/package/game/latest/Assets.zip");
        try(var zip=new ZipFile(zipPath.toFile())){
            var entry=Objects.requireNonNull(zip.getEntry("Server/Item/Qualities/"+id+".json"),id);
            try(var input=zip.getInputStream(entry)){
                var document=BsonDocument.parse(new String(input.readAllBytes(),StandardCharsets.UTF_8));
                registerCommonAssets(document);
                qualities.seed(Map.of(id,Objects.requireNonNull(qualityStore.decode(PACK,id,document),id)));
            }
        }
    }

    private void seedInstalledRootKeys(BsonDocument item) throws IOException {
        var keys = new LinkedHashSet<String>();
        for (var section : List.of("Interactions", "InteractionVars")) {
            if (!item.containsKey(section)) continue;
            item.getDocument(section).values().stream().filter(BsonValue::isString)
                    .map(value -> value.asString().getValue()).filter(value -> !value.startsWith("RPG_"))
                    .forEach(keys::add);
        }
        if (keys.isEmpty()) return;
        if (installedRoots == null) {
            var zipPath = Path.of(System.getProperty("user.home"), "AppData", "Roaming", "Hytale",
                    "install", "pre-release", "package", "game", "latest", "Assets.zip");
            try (var zip = new ZipFile(zipPath.toFile())) {
                installedRoots = zip.stream().map(java.util.zip.ZipEntry::getName)
                        .filter(name -> name.startsWith("Server/Item/RootInteractions/") && name.endsWith(".json"))
                        .map(name -> name.substring(name.lastIndexOf('/') + 1, name.length() - 5))
                        .collect(java.util.stream.Collectors.toSet());
            }
        }
        for (var key : keys) {
            if (!installedRoots.contains(key)) throw new IllegalStateException("Missing installed root reference: " + key);
            if (roots.getAsset(key) == null) roots.seed(Map.of(key, new RootInteraction(key)));
        }
    }

    public Interaction decodeInteraction(String id, Path path) throws IOException {
        var json = BsonDocument.parse(Files.readString(path));
        registerCommonAssets(json);
        seedSoundReferences(json);
        seedParticleReferences(json);
        var value = Objects.requireNonNull(interactionStore.decode(PACK, id, json), id);
        interactions.seed(Map.of(id, value));
        return value;
    }

    public RootInteraction decodeRoot(String id, Path path) throws IOException {
        var value = Objects.requireNonNull(rootStore.decode(PACK, id,
                BsonDocument.parse(Files.readString(path))), id);
        roots.seed(Map.of(id, value));
        return value;
    }

    public ProjectileConfig decodeProjectile(String id, Path path) throws IOException {
        var json = BsonDocument.parse(Files.readString(path));
        registerCommonAssets(json);
        seedSoundReferences(json);
        seedParticleReferences(json);
        var value = Objects.requireNonNull(projectileStore.decode(PACK, id, json), id);
        projectiles.seed(Map.of(id, value));
        return value;
    }

    public ModelAsset decodeModel(String id, Path path) throws IOException {
        var json=BsonDocument.parse(Files.readString(path));
        registerCommonAssets(json);seedActionReferences(json);seedSoundReferences(json);seedParticleReferences(json);
        var value=Objects.requireNonNull(modelStore.decode(PACK,id,json),id);
        models.seed(Map.of(id,value));return value;
    }

    public void loadInstalledModel(String id) throws IOException {
        if(models.getAsset(id)!=null)return;
        var zipPath=Path.of(System.getProperty("user.home"),"AppData/Roaming/Hytale/install/pre-release/package/game/latest/Assets.zip");
        try(var zip=new ZipFile(zipPath.toFile())){
            var entry=zip.stream().filter(e->e.getName().startsWith("Server/Models/")&&e.getName().endsWith("/"+id+".json"))
                    .findFirst().orElseThrow(()->new IOException("Missing installed model "+id));
            try(var input=zip.getInputStream(entry)){
                var json=BsonDocument.parse(new String(input.readAllBytes(),StandardCharsets.UTF_8));
                registerCommonAssets(json);seedActionReferences(json);seedSoundReferences(json);seedParticleReferences(json);
                models.seed(Map.of(id,Objects.requireNonNull(modelStore.decode(PACK,id,json),id)));
            }
        }
    }

    public ItemPlayerAnimations decodeAnimation(String id, Path path) throws IOException {
        var json=BsonDocument.parse(Files.readString(path));
        registerCommonAssets(json);seedActionReferences(json);seedSoundReferences(json);seedParticleReferences(json);
        var value=Objects.requireNonNull(animationStore.decode(PACK,id,json),id);
        animations.seed(Map.of(id,value));return value;
    }

    /** Decode and register every staged graph and carrier asset for native stack tests. */
    public int loadCarrierAssets() throws IOException {
        var server = RESOURCE_ROOT;
        for (var section : List.of("Item/Interactions/RPG/Carriers", "Item/RootInteractions/RPG/Carriers",
                "ProjectileConfigs/RPG/Carriers")) {
            try (var paths = Files.list(server.resolve(section))) {
                for (var path : paths.filter(file -> file.getFileName().toString().endsWith(".json")).sorted().toList()) {
                    var name = path.getFileName().toString();
                    var id = name.substring(0, name.length() - 5);
                    if (section.startsWith("Item/Interactions")) decodeInteraction(id, path);
                    else if (section.startsWith("Item/RootInteractions")) decodeRoot(id, path);
                    else decodeProjectile(id, path);
                }
            }
        }
        int count = 0;
        for (var binding : new GearBindings().all()) {
            if (!binding.baseId().matches("gm\\.(?:staff|wand|book|shield|bomb)_.+\\.(?:n|nm|h)")) continue;
            var stem = "RPG_Gear_" + binding.baseId().substring(3).replace('.', '_');
            for (var suffix : List.of("", "_Uncommon", "_Rare", "_Epic", "_Legendary")) {
                var id = stem + suffix;
                decodeItem(id, server.resolve("Item/Items/RPG/Gear").resolve(id + ".json"));
                count++;
            }
        }
        if (count != 450) throw new IllegalStateException("Expected 450 native carrier assets, got " + count);
        return count;
    }

    private void loadProjectileModels() throws IOException {
        var zipPath = Path.of(System.getProperty("user.home"), "AppData", "Roaming", "Hytale",
                "install", "pre-release", "package", "game", "latest", "Assets.zip");
        try (var zip = new ZipFile(zipPath.toFile())) {
            for (var entryName : List.of("Server/Models/Projectiles/Weapons/Bomb/Bomb.json",
                    "Server/Models/Projectiles/NPCs/Undead/Skeleton_Mage/Skeleton_Mage_Corruption_Orb.json")) {
                var id = entryName.substring(entryName.lastIndexOf('/') + 1, entryName.length() - 5);
                var entry = Objects.requireNonNull(zip.getEntry(entryName), entryName);
                String json;
                try (var input = zip.getInputStream(entry)) { json = new String(input.readAllBytes(), StandardCharsets.UTF_8); }
                var document = BsonDocument.parse(json);
                registerCommonAssets(document);
                // The projectile decoder needs the real model key and geometry. Its particle/trail
                // registries are unrelated to collision qualification, so keep those out of this fixture.
                document.remove("Trails");
                document.remove("Particles");
                document.remove("AnimationSets");
                document.remove("DefaultAttachments");
                var model = Objects.requireNonNull(modelStore.decode(PACK, id, document), id);
                models.seed(Map.of(id, model));
            }
        }
    }

    @Override public void close() {
        DamageCause.PHYSICAL = oldStatic.get("Physical");
        DamageCause.PROJECTILE = oldStatic.get("Projectile");
        DamageCause.COMMAND = oldStatic.get("Command");
        DamageCause.DROWNING = oldStatic.get("Drowning");
        DamageCause.ENVIRONMENT = oldStatic.get("Environment");
        DamageCause.FALL = oldStatic.get("Fall");
        DamageCause.OUT_OF_WORLD = oldStatic.get("OutOfWorld");
        DamageCause.SUFFOCATION = oldStatic.get("Suffocation");
        AssetRegistry.unregister(itemStore);
        AssetRegistry.unregister(projectileStore);
        AssetRegistry.unregister(rootStore);
        AssetRegistry.unregister(interactionStore);
        AssetRegistry.unregister(qualityStore);
        AssetRegistry.unregister(causeStore);
        AssetRegistry.unregister(particleStore);
        AssetRegistry.unregister(animationStore);
        AssetRegistry.unregister(soundSetStore);
        AssetRegistry.unregister(soundStore);
        AssetRegistry.unregister(unarmedStore);
        AssetRegistry.unregister(modelStore);
        AssetRegistry.unregister(reticleStore);
        AssetRegistry.unregister(statStore);
        AssetRegistry.unregister(effectStore);
        AssetRegistry.unregister(trailStore);
        AssetRegistry.unregister(cameraStore);
        for (var id : commonAssets) CommonAssetRegistry.removeCommonAssetByName(PACK, id);
    }

    private void registerCommonAssets(BsonValue json) throws IOException {
        var paths = new LinkedHashSet<String>();
        collectCommonPaths(json, paths);
        if (paths.isEmpty()) return;
        var zipPath = Path.of(System.getProperty("user.home"), "AppData", "Roaming", "Hytale",
                "install", "pre-release", "package", "game", "latest", "Assets.zip");
        try (var zip = new ZipFile(zipPath.toFile())) {
            for (var id : paths) {
                if (CommonAssetRegistry.hasCommonAsset(id)) continue;
                var localRoot = Path.of("src/main/resources/Common").toAbsolutePath().normalize();
                var local = localRoot.resolve(id).normalize();
                if (!local.startsWith(localRoot)) throw new IOException("Invalid common asset path: " + id);
                if (!Files.exists(local) && id.endsWith(".png"))
                    local = localRoot.resolve(id.substring(0, id.length() - 4) + "@2x.png");
                if (Files.exists(local)) {
                    CommonAssetRegistry.addCommonAsset(PACK, new ZipCommonAsset(id, Files.readAllBytes(local)));
                    commonAssets.add(id);
                    continue;
                }
                var entry = zip.getEntry("Common/" + id);
                if (entry == null && id.endsWith(".png"))
                    entry = zip.getEntry("Common/" + id.substring(0, id.length() - 4) + "@2x.png");
                if (entry == null) continue;
                byte[] bytes;
                try (var input = zip.getInputStream(entry)) { bytes = input.readAllBytes(); }
                CommonAssetRegistry.addCommonAsset(PACK, new ZipCommonAsset(id, bytes));
                commonAssets.add(id);
            }
        }
    }

    private void seedSound(String id) {
        if (sounds.getAsset(id) == null) sounds.seed(Map.of(id, new SoundEvent(id) {
            @Override public int getHighestNumberOfChannels() { return id.startsWith("SFX_Player_") ? 2 : 1; }
        }));
    }
    private void seedSoundSet(String id) {
        if (soundSets.getAsset(id) == null) soundSets.seed(Map.of(id, new ItemSoundSet(id)));
    }
    private void seedSoundReferences(BsonValue json) {
        if (json.isDocument()) for (var entry : json.asDocument().entrySet()) {
            if (entry.getKey().endsWith("SoundEventId") && entry.getValue().isString())
                seedSound(entry.getValue().asString().getValue());
            else seedSoundReferences(entry.getValue());
        }
        else if (json.isArray()) json.asArray().forEach(this::seedSoundReferences);
    }
    private void seedParticleReferences(BsonValue json) {
        if (json.isDocument()) for (var entry : json.asDocument().entrySet()) {
            if (entry.getKey().equals("SystemId") && entry.getValue().isString()) {
                var id = entry.getValue().asString().getValue();
                if (particles.getAsset(id) == null)
                    particles.seed(Map.of(id, new ParticleSystem(id, 1, new ParticleSpawnerGroup[0], 64, 10, true)));
            } else seedParticleReferences(entry.getValue());
        }
        else if (json.isArray()) json.asArray().forEach(this::seedParticleReferences);
    }

    private static void collectCommonPaths(BsonValue value, Set<String> paths) {
        if (value.isDocument()) value.asDocument().values().forEach(child -> collectCommonPaths(child, paths));
        else if (value.isArray()) value.asArray().forEach(child -> collectCommonPaths(child, paths));
        else if (value.isString()) {
            var string = value.asString().getValue();
            if (string.endsWith(".png") || string.endsWith(".blockymodel") ||
                    string.endsWith(".blockyanim")) paths.add(string);
        }
    }

    private static final class ZipCommonAsset extends CommonAsset {
        private final byte[] bytes;
        ZipCommonAsset(String id, byte[] bytes) { super(id, bytes); this.bytes = bytes; }
        @Override protected java.util.concurrent.CompletableFuture<byte[]> getBlob0() {
            return java.util.concurrent.CompletableFuture.completedFuture(bytes);
        }
    }

    private static final class CauseMap extends IndexedLookupTableAssetMap<String, DamageCause> {
        CauseMap() { super(DamageCause[]::new); }
        void seed(Map<String, DamageCause> values) { putAll(PACK, DamageCause.CODEC, values, Map.of(), Map.of()); }
    }
    private static final class QualityMap extends IndexedLookupTableAssetMap<String, ItemQuality> {
        QualityMap() { super(ItemQuality[]::new); }
        void seed(Map<String, ItemQuality> values) { putAll(PACK, ItemQuality.CODEC, values, Map.of(), Map.of()); }
    }
    private static final class ItemMap extends DefaultAssetMap<String, Item> {
        void seed(Map<String, Item> values) { putAll(PACK, Item.CODEC, values, Map.of(), Map.of()); }
    }
    private static final class InteractionMap extends IndexedLookupTableAssetMap<String, Interaction> {
        InteractionMap() { super(Interaction[]::new); }
        void seed(Map<String, Interaction> values) { putAll(PACK, Interaction.CODEC, values, Map.of(), Map.of()); }
    }
    private static final class RootMap extends IndexedLookupTableAssetMap<String, RootInteraction> {
        RootMap() { super(RootInteraction[]::new); }
        void seed(Map<String, RootInteraction> values) { putAll(PACK, RootInteraction.CODEC, values, Map.of(), Map.of()); }
    }
    private static final class ProjectileMap extends DefaultAssetMap<String, ProjectileConfig> {
        void seed(Map<String, ProjectileConfig> values) { putAll(PACK, ProjectileConfig.CODEC, values, Map.of(), Map.of()); }
    }
    private static final class ParticleMap extends DefaultAssetMap<String, ParticleSystem> {
        void seed(Map<String, ParticleSystem> values) { putAll(PACK, ParticleSystem.CODEC, values, Map.of(), Map.of()); }
    }
    private static final class AnimationMap extends DefaultAssetMap<String, ItemPlayerAnimations> {
        void seed(Map<String, ItemPlayerAnimations> values) { putAll(PACK, ItemPlayerAnimations.CODEC, values, Map.of(), Map.of()); }
    }
    private static final class SoundSetMap extends IndexedLookupTableAssetMap<String, ItemSoundSet> {
        SoundSetMap() { super(ItemSoundSet[]::new); }
        void seed(Map<String, ItemSoundSet> values) { putAll(PACK, ItemSoundSet.CODEC, values, Map.of(), Map.of()); }
    }
    private static final class SoundMap extends IndexedLookupTableAssetMap<String, SoundEvent> {
        SoundMap() { super(SoundEvent[]::new); }
        void seed(Map<String, SoundEvent> values) { putAll(PACK, SoundEvent.CODEC, values, Map.of(), Map.of()); }
    }
    private static final class ModelMap extends DefaultAssetMap<String, ModelAsset> {
        void seed(Map<String, ModelAsset> values) { putAll(PACK, ModelAsset.CODEC, values, Map.of(), Map.of()); }
    }
    private static final class ReticleMap extends IndexedLookupTableAssetMap<String, ItemReticleConfig> {
        ReticleMap() { super(ItemReticleConfig[]::new); }
        void seed(Map<String, ItemReticleConfig> values) { putAll(PACK, ItemReticleConfig.CODEC, values, Map.of(), Map.of()); }
    }
    private static final class CameraMap extends IndexedLookupTableAssetMap<String, CameraEffect> {
        CameraMap() { super(CameraEffect[]::new); }
        void seed(Map<String, CameraEffect> values) { putAll(PACK, CameraEffect.CODEC, values, Map.of(), Map.of()); }
    }
    private static final class TrailMap extends DefaultAssetMap<String, Trail> {
        void seed(Map<String, Trail> values) { putAll(PACK, Trail.CODEC, values, Map.of(), Map.of()); }
    }
    private static final class StatMap extends IndexedLookupTableAssetMap<String, EntityStatType> {
        StatMap() { super(EntityStatType[]::new); }
        void seed(Map<String, EntityStatType> values) { putAll(PACK, EntityStatType.CODEC, values, Map.of(), Map.of()); }
    }
    private static final class EffectMap extends IndexedLookupTableAssetMap<String, EntityEffect> {
        EffectMap() { super(EntityEffect[]::new); }
        void seed(Map<String, EntityEffect> values) { putAll(PACK, EntityEffect.CODEC, values, Map.of(), Map.of()); }
    }
}
