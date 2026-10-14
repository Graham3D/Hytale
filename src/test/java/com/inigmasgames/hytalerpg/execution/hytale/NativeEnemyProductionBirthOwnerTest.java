package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.*;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsModule;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import com.hypixel.hytale.server.spawning.SpawningPlugin;
import com.hypixel.hytale.server.spawning.world.WorldEnvironmentSpawnData;
import com.hypixel.hytale.server.spawning.world.ChunkEnvironmentSpawnData;
import com.hypixel.hytale.server.spawning.world.component.*;
import com.hypixel.hytale.server.core.asset.type.gameplay.GameplayConfig;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.spawning.assets.spawns.config.NPCSpawn;
import com.hypixel.hytale.server.spawning.world.component.SpawnJobData;
import com.hypixel.hytale.server.spawning.wrappers.SpawnWrapper;
import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.diagnostics.RpgSkillTracer;
import com.inigmasgames.hytalerpg.diagnostics.MonsterSpawnTrace;
import com.inigmasgames.hytalerpg.enemies.*;
import com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects;
import com.inigmasgames.hytalerpg.gear.NativeAssetTestFixtures;
import com.inigmasgames.hytalerpg.difficulty.*;
import com.inigmasgames.hytalerpg.links.*;
import com.inigmasgames.hytalerpg.progress.*;
import com.inigmasgames.hytalerpg.spawning.HywindWorldConfiguration;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Joins native capture to the actual durable birth, attachment, and publication owners. */
class NativeEnemyBirthOwnerFixture {
    @TempDir Path root;

    private static class PreparedJobWrapper extends SpawnWrapper<NPCSpawn> {
        private PreparedJobWrapper() { super(7, null); }
    }
    private static class ProbeWorld extends World {
        WorldConfig config;
        EntityStore entities;
        ChunkStore chunks;
        GameplayConfig gameplay;
        Thread owner;
        BlockingQueue<Runnable> pending;
        private ProbeWorld() { super("unused",Path.of("unused"),null); throw new AssertionError("Never start native world"); }
        @Override public WorldConfig getWorldConfig() { return config; }
        @Override public EntityStore getEntityStore() { return entities; }
        @Override public ChunkStore getChunkStore() { return chunks; }
        @Override public GameplayConfig getGameplayConfig() { return gameplay; }
        @Override public void execute(Runnable task) { pending.add(task); }
        void drainUntil(java.util.function.BooleanSupplier finished) throws InterruptedException {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
            while(!finished.getAsBoolean()&&System.nanoTime()<deadline) {
                var task=pending.poll(100,TimeUnit.MILLISECONDS);
                if(task!=null){assertSame(owner,Thread.currentThread());task.run();}
            }
            assertTrue(finished.getAsBoolean(),"Native world callback chain did not complete");
        }
    }
    private static class ProbeChunks extends ChunkStore {
        com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator generator;
        Store<ChunkStore> nativeStore;
        Ref<ChunkStore> center;
        private ProbeChunks() { super(null); throw new AssertionError("Never start native chunks"); }
        @Override public com.hypixel.hytale.server.core.universe.world.worldgen.IWorldGen getGenerator() { return generator; }
        @Override public Store<ChunkStore> getStore() { return nativeStore; }
        @Override public Ref<ChunkStore> getChunkReference(long index) {
            return index==com.hypixel.hytale.math.util.ChunkUtil.indexChunk(0,0)?center:null;
        }
    }
    private static class ProbeGenerator extends com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator {
        com.hypixel.hytale.server.worldgen.chunk.ZoneBiomeResult result;
        private ProbeGenerator() { super(null,Path.of("unused")); throw new AssertionError("Never start world generation"); }
        @Override public com.hypixel.hytale.server.worldgen.chunk.ZoneBiomeResult getZoneBiomeResultAt(int seed,int x,int z) { return result; }
    }
    private static class ProbeBiome extends com.hypixel.hytale.server.worldgen.biome.Biome {
        private ProbeBiome() { super(0,"Plains_Birch_Trork",null,null,null,null,null,null,null,null,null,null,0);throw new AssertionError("Never generate terrain"); }
    }
    private static final class VolatileResources implements IResourceStorage {
        @Override public <T extends Resource<E>,E> CompletableFuture<T> load(Store<E> store,ComponentRegistry.Data<E> data,ResourceType<E,T> type) {
            return CompletableFuture.completedFuture(data.createResource(type));
        }
        @Override public <T extends Resource<E>,E> CompletableFuture<Void> save(Store<E> store,ComponentRegistry.Data<E> data,ResourceType<E,T> type,T resource) {
            return CompletableFuture.completedFuture(null);
        }
        @Override public <T extends Resource<E>,E> CompletableFuture<Void> remove(Store<E> store,ComponentRegistry.Data<E> data,ResourceType<E,T> type) {
            return CompletableFuture.completedFuture(null);
        }
    }
    private static Object allocate(Class<?> type) throws Exception {
        var unsafe=Class.forName("sun.misc.Unsafe");var slot=unsafe.getDeclaredField("theUnsafe");slot.setAccessible(true);
        return unsafe.getMethod("allocateInstance",Class.class).invoke(slot.get(null),type);
    }
    private static final class BlockAssets implements AutoCloseable {
        final Object previous;
        final HytaleAssetStore<String,BlockType,com.hypixel.hytale.assetstore.map.BlockTypeAssetMap<String,BlockType>> assets;
        BlockAssets() throws Exception {
            var field=BlockType.class.getDeclaredField("ASSET_STORE");field.setAccessible(true);previous=field.get(null);
            var map=new com.hypixel.hytale.assetstore.map.BlockTypeAssetMap<String,BlockType>(BlockType[]::new,BlockType::getId);
            var builder=HytaleAssetStore.builder(BlockType.class,map).setPath("Item/Block")
                    .setCodec(BlockType.CODEC).setKeyFunction(BlockType::getId).setReplaceOnRemove(BlockType::getUnknownFor);
            assets=new HytaleAssetStore<>(builder) {
                final com.hypixel.hytale.event.EventBus events=new com.hypixel.hytale.event.EventBus(false);
                @Override protected com.hypixel.hytale.event.EventBus getEventBus() { return events; }
            };
            AssetRegistry.register(assets);field.set(null,assets);
        }
        @Override public void close() {
            try {var field=BlockType.class.getDeclaredField("ASSET_STORE");field.setAccessible(true);field.set(null,previous);}
            catch(ReflectiveOperationException failure) { throw new AssertionError(failure); }
            AssetRegistry.unregister(assets);
        }
    }
    private static void set(Class<?> owner,Object target,String name,Object value) throws Exception {
        var slot=owner.getDeclaredField(name);slot.setAccessible(true);slot.set(target,value);
    }
    private static Object get(Class<?> owner,Object target,String name) throws Exception {
        var slot=owner.getDeclaredField(name);slot.setAccessible(true);return slot.get(target);
    }
    private static RpgLoadoutService loadouts() {
        var catalog=RpgCatalog.loadCanonical();var compatibility=new CompatibilityService();
        var graph=new RpgLinkGraphService(catalog,compatibility);var compiler=new LinkCompiler(catalog,graph,compatibility);
        RpgPlayerStateRepository repository=new RpgPlayerStateRepository() {
            @Override public LoadResult load(UUID player) { return new LoadResult(RpgPlayerState.create(player),false,false,RpgPlayerState.CURRENT_SCHEMA,List.of()); }
            @Override public void save(RpgPlayerState state) { throw new AssertionError("No reward is due in this capture case"); }
        };
        return new RpgLoadoutService(catalog,repository,graph,compiler,new OwnershipEntitlementPolicy(true),record->{});
    }
    private void loadRoleActions(NativeAssetTestFixtures assets,EnemyNativeBindings.Role role,String roleId) throws Exception {
        var zipPath=Path.of(System.getProperty("user.home"),"AppData/Roaming/Hytale/install/release/package/game/latest/Assets.zip");
        assets.loadInstalledAnimation(roleId.equals("Larva_Void")?"Larva_Void_Default":"Trork_Battleaxe",zipPath);
        try(var zip=new ZipFile(zipPath.toFile())) {
            if(roleId.equals("Larva_Void")){
                for(var interrupt:List.of("Hit_Interrupt_Begin_Protected_Attack","Hit_Interrupt_Finish_Protected_Attack")){
                    var asset=zip.getEntry("Server/Item/Interactions/Weapons/"+interrupt+".json");
                    try(var input=zip.getInputStream(asset)){
                        assets.loadInteractionGraph(interrupt,
                                new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
            }
            var damage=zip.stream().filter(value->value.getName().startsWith("Server/Item/Interactions/")
                    &&value.getName().endsWith("/NPC_Attack_Melee_Damage.json")).findFirst().orElseThrow();
            String damageJson;try(var input=zip.getInputStream(damage)){damageJson=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
            assets.loadInstalledParentReferences(damageJson);
            var inheritedDamage=org.bson.BsonDocument.parse(damageJson);
            inheritedDamage.put("Type",new org.bson.BsonString("DamageEntity"));
            assets.loadInteractionGraph("NPC_Attack_Melee_Damage",inheritedDamage.toJson());
            var rolePath=roleId.equals("Larva_Void")
                    ?"src/main/resources/Server/NPC/Roles/Void/Larva_Void.json"
                    :"src/main/resources/Server/NPC/Roles/Intelligent/Faction/Trork/Trork_Warrior.json";
            var roleDocument=org.bson.BsonDocument.parse(Files.readString(Path.of(rolePath)));
            var variable=roleDocument.getDocument("Modify").getDocument("_InteractionVars").getDocument("Melee_Damage");
            assets.loadRootGraph("Me12_"+roleId+"_Melee_Damage",variable.toJson());
            for(var action:role.actions()) {
                var id=action.nativeActionId();
                for(var section:List.of("Server/Item/Interactions/","Server/Item/RootInteractions/")) {
                    var entry=zip.stream().filter(value->value.getName().startsWith(section)
                            &&value.getName().endsWith("/"+id+".json")).findFirst().orElseThrow();
                    var target=root.resolve("assets").resolve(id+(section.contains("Root")?"-root":"-interaction")+".json");
                    Files.createDirectories(target.getParent());
                    try(var input=zip.getInputStream(entry)){Files.copy(input,target);}
                    if(section.contains("Root"))assets.loadRootGraph(id,Files.readString(target));
                    else {var json=Files.readString(target);assets.loadInstalledParentReferences(json);assets.loadInteractionGraph(id,json);}
                }
            }
        }
    }
    void exercise(boolean interruptAfterRoot,String roleId) throws Exception {
        String biome=roleId.equals("Larva_Void")?"Forest_Birch":"Plains_Birch_Trork";
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        var registry=EntityStore.REGISTRY;
        var old=EntityModule.class.getDeclaredField("instance");old.setAccessible(true);var previous=old.get(null);
        var npcInstance=NPCPlugin.class.getDeclaredField("instance");npcInstance.setAccessible(true);var previousNpc=npcInstance.get(null);
        var universeInstance=Universe.class.getDeclaredField("instance");universeInstance.setAccessible(true);var previousUniverse=universeInstance.get(null);
        var statInstance=EntityStatsModule.class.getDeclaredField("instance");statInstance.setAccessible(true);var previousStats=statInstance.get(null);
        var spawnInstance=SpawningPlugin.class.getDeclaredField("instance");spawnInstance.setAccessible(true);var previousSpawning=spawnInstance.get(null);
        var damageInstance=com.hypixel.hytale.server.core.modules.entity.damage.DamageModule.class.getDeclaredField("instance");
        damageInstance.setAccessible(true);var previousDamage=damageInstance.get(null);
        var module=allocate(EntityModule.class);
        try(var trace=new MonsterSpawnTrace(root.resolve("trace"));var assets=NativeAssetTestFixtures.open();var blocks=new BlockAssets();
                var disk=FileEncounterStore.durableV2(root.resolve("encounters"));var players=loadouts()) {
            trace.start();
            assets.seedActionReferences("{\"EntityStatId\":\"Health\"}");DefaultEntityStatTypes.update();
            var frozen=registry.registerComponent(Frozen.class,Frozen::get);
            var immune=registry.registerComponent(Invulnerable.class,()->Invulnerable.INSTANCE);
            var intangible=registry.registerComponent(Intangible.class,()->Intangible.INSTANCE);
            var uuid=registry.registerComponent(UUIDComponent.class,()->new UUIDComponent(UUID.randomUUID()));
            set(EntityModule.class,module,"frozenComponentType",frozen);
            set(EntityModule.class,module,"invulnerableComponentType",immune);
            set(EntityModule.class,module,"intangibleComponentType",intangible);
            set(EntityModule.class,module,"uuidComponentType",uuid);
            old.set(null,module);
            EnemyStaging.bind(registry.registerComponent(EnemyStaging.class,EnemyStaging::new));
            EnemyActorIdentity.bind(registry.registerComponent(EnemyActorIdentity.class,EnemyActorIdentity::new));
            EnemyEngagementClock.bind(registry.registerComponent(EnemyEngagementClock.class,EnemyEngagementClock::new));
            EnemyShieldProjection.bind(registry.registerComponent(EnemyShieldProjection.class,EnemyShieldProjection::new));
            QaTransientMarker.bind(registry.registerComponent(QaTransientMarker.class,QaTransientMarker::new));
            SummonProjection.bind(registry.registerComponent(SummonProjection.class,SummonProjection::new));
            ConversionProjection.bind(registry.registerComponent(ConversionProjection.class,ConversionProjection::new));
            var npcType=registry.registerComponent(NPCEntity.class,()->{throw new AssertionError("Native NPC supplied by test");});
            var transformType=registry.registerComponent(TransformComponent.class,TransformComponent::new);
            set(EntityModule.class,module,"transformComponentType",transformType);
            set(EntityModule.class,module,"persistentDisplayNameComponentType",
                    registry.registerComponent(PersistentDisplayName.class,()->new PersistentDisplayName(com.hypixel.hytale.server.core.Message.raw(""))));
            set(EntityModule.class,module,"classToComponentType",Map.of(NPCEntity.class,npcType));
            var supportType=registry.registerComponent(WorldSupport.class,()->{throw new AssertionError("Native support supplied by test");});
            var npcPlugin=allocate(NPCPlugin.class);set(NPCPlugin.class,npcPlugin,"worldSupportComponentType",supportType);
            npcInstance.set(null,npcPlugin);
            var playerType=registry.registerComponent(PlayerRef.class,()->{throw new AssertionError("No player in native birth fixture");});
            var universe=allocate(Universe.class);set(Universe.class,universe,"playerRefComponentType",playerType);
            universeInstance.set(null,universe);
            var statType=registry.registerComponent(EntityStatMap.class,EntityStatMap::new);
            var statModule=allocate(EntityStatsModule.class);set(EntityStatsModule.class,statModule,"entityStatMapComponentType",statType);
            statInstance.set(null,statModule);
            var damageModule=allocate(com.hypixel.hytale.server.core.modules.entity.damage.DamageModule.class);
            set(com.hypixel.hytale.server.core.modules.entity.damage.DamageModule.class,damageModule,"deathComponentType",
                    registry.registerComponent(com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent.class,
                            ()->{throw new AssertionError("No native death in birth fixture");}));
            damageInstance.set(null,damageModule);
            DifficultyHealthProjection.bind(registry.registerComponent(DifficultyHealthProjection.class,DifficultyHealthProjection::new));
            var populationType=registry.registerResource(WorldSpawnData.class,WorldSpawnData::new);
            var chunkRegistry=ChunkStore.REGISTRY;
            var chunkSystems=chunkRegistry._internal_getData();
            for(int i=0;i<chunkSystems.getSystemSize();i++) {
                var system=chunkSystems.getSystem(i);
                if(system!=null)chunkRegistry.unregisterSystem((Class)system.getClass());
            }
            var chunkSpawnType=chunkRegistry.registerComponent(ChunkSpawnData.class,ChunkSpawnData::new);
            var chunkCountType=chunkRegistry.registerComponent(ChunkSpawnedNPCData.class,ChunkSpawnedNPCData::new);
            var spawning=allocate(SpawningPlugin.class);
            set(SpawningPlugin.class,spawning,"worldSpawnDataResourceType",populationType);
            set(SpawningPlugin.class,spawning,"chunkSpawnDataComponentType",chunkSpawnType);
            set(SpawningPlugin.class,spawning,"chunkSpawnedNPCDataComponentType",chunkCountType);
            spawnInstance.set(null,spawning);
            var worldId=UUID.randomUUID();var world=(ProbeWorld)allocate(ProbeWorld.class);
            world.owner=Thread.currentThread();world.pending=new LinkedBlockingQueue<>();
            set(com.hypixel.hytale.server.core.util.thread.TickingThread.class,world,"currentRun",
                    new AtomicReference<>());
            var bindThread=com.hypixel.hytale.server.core.util.thread.TickingThread.class
                    .getDeclaredMethod("setThread",Thread.class);
            bindThread.setAccessible(true);bindThread.invoke(world,Thread.currentThread());
            set(Universe.class,universe,"worldsByUuid",new HashMap<>(Map.of(worldId,world)));
            world.config=(WorldConfig)allocate(WorldConfig.class);world.config.setUuid(worldId);
            world.config.setWorldGenProvider(new com.hypixel.hytale.server.worldgen.HytaleWorldGenProvider());
            var terrain=(ProbeGenerator)allocate(ProbeGenerator.class);
            var nativeBiome=(ProbeBiome)allocate(ProbeBiome.class);set(com.hypixel.hytale.server.worldgen.biome.Biome.class,nativeBiome,"name",biome);
            var zone=new com.hypixel.hytale.server.worldgen.zone.Zone(1,"Zone1_Tier1",null,null,null,null);
            terrain.result=new com.hypixel.hytale.server.worldgen.chunk.ZoneBiomeResult(
                    new com.hypixel.hytale.server.worldgen.zone.ZoneGeneratorResult(zone,0),nativeBiome,0,0);
            var chunks=(ProbeChunks)allocate(ProbeChunks.class);chunks.generator=terrain;world.chunks=chunks;
            world.gameplay=new GameplayConfig();set(GameplayConfig.class,world.gameplay,"maxEnvironmentalNPCSpawns",4);
            chunks.nativeStore=chunkRegistry.addStore(chunks,new VolatileResources());
            var chunkData=new ChunkSpawnData();var environmentData=new ChunkEnvironmentSpawnData();
            set(ChunkEnvironmentSpawnData.class,environmentData,"expectedNPCs",4d);
            chunkData.getChunkEnvironmentSpawnDataMap().put(3,environmentData);
            var counted=new ChunkSpawnedNPCData();counted.setEnvironmentSpawnCount(3,4);
            var chunkHolder=chunkRegistry.newHolder();chunkHolder.addComponent(chunkSpawnType,chunkData);
            chunkHolder.addComponent(chunkCountType,counted);chunks.center=chunks.nativeStore.addEntity(chunkHolder,AddReason.SPAWN);
            world.entities=new EntityStore(world);
            set(World.class,world,"entityStore",world.entities);
            var store=registry.addStore(world.entities,new VolatileResources());
            set(EntityStore.class,world.entities,"store",store);
            var population=store.getResource(populationType);
            var environmentPopulation=new WorldEnvironmentSpawnData(3,1);
            set(WorldEnvironmentSpawnData.class,environmentPopulation,"actualNPCs",4);
            set(WorldEnvironmentSpawnData.class,environmentPopulation,"expectedNPCs",4d);
            @SuppressWarnings("unchecked") var byEnvironment=(it.unimi.dsi.fastutil.ints.Int2ObjectMap<WorldEnvironmentSpawnData>)
                    get(WorldSpawnData.class,population,"worldEnvironmentSpawnData");
            byEnvironment.put(3,environmentPopulation);
            set(WorldSpawnData.class,population,"actualNPCs",4);
            set(WorldSpawnData.class,population,"expectedNPCs",4d);
            var index=EntityStore.class.getDeclaredField("entitiesByUuid");index.setAccessible(true);
            @SuppressWarnings("unchecked") var nativeIndex=(Map<UUID,Ref<EntityStore>>)index.get(world.entities);

            var balance=EnemyBalance.canonical();var bindings=EnemyNativeBindings.load();
            loadRoleActions(assets,bindings.role(roleId).orElseThrow(),roleId);
            var admission=new EnemyWorldAdmission(id->CompletableFuture.completedFuture(disk.recoverEnemyWorld(id)),new EnemyPackCapacity(balance));
            admission.begin(worldId).toCompletableFuture().join();
            admission.rebindComplete(worldId,List.of());
            var tracer=(RpgSkillTracer)record->{};var rewards=new HytaleEncounterRewards(disk,players,tracer,RpgCombatKernel.createProduction());
            var combat=new HytaleDifficultyCombat(tracer);rewards.configureCombat(combat);rewards.configureEnemyAdmission(admission);
            var worlds=new WorldDifficultyRegistry(root.resolve("worlds.json"));
            worlds.register(new WorldDifficultyRegistry.Binding(worldId,"fixture-world",WorldDifficultyRegistry.Kind.CAMPAIGN,
                    DifficultyId.NORMAL,"rpg.encounters.r031.pilot","shared",true));
            var profiles=AuthoredEncounterCatalog.load().resolver(worlds);
            rewards.configureDifficulty(profiles);
            var effects=new FiniteSupportEffects();var actions=new NativeEnemyActions(new NativeEnemyOutgoingEffects(),ignored->{});
            var config=new HywindWorldConfiguration(root.resolve("QaWorld"),root.resolve("legacy/world-spawn-density.json"));
            var authority=Me12QaAuthorityFixture.activate(root,config,admission,bindings,worldId,roleId);
            var policy=config.snapshot();
            var decision=new NativeEnemyBirthDecision(rewards,bindings,balance,()->policy,admission);
            decision.configureNativeHooks(()->true);
            decision.configureQaAuthority(authority);
            var actualCapture=new AtomicReference<NativeEnemyBirthDecision.V2Capture>();
            var captureFailure=new AtomicReference<RuntimeException>();
            decision.configureV2ContextSource((current,group,input)->{
                try{var captured=decision.captureProduction(current,group,input);actualCapture.set(captured);return captured;}
                catch(RuntimeException failure){captureFailure.set(failure);throw failure;}
            });
            var reservation=new NativeEnemyBirthReservation(decision,admission,rewards);
            var attachment=new NativeEnemyBirthAttachment(rewards,new NativeEnemyStateAttachment(effects),
                    new NativeEnemyActionAttachment(rewards,combat,actions,effects,bindings),balance,bindings);
            var publication=new NativeEnemyBirthPublication(rewards,combat,admission,EnemyVisualVariants.canonical(),bindings,new HytaleEnemyWeaponVisuals());
            var owner=new NativeEnemyBirthOwner(bindings,admission,rewards,combat,actions,reservation,decision,attachment,publication,true);
            assertTrue(owner.eligible(new NativeEnemySpawnGroups.Job(worldId,42,4,roleId,3,7,4)));

            var nativeJob=new SpawnJobData();var wrapper=(PreparedJobWrapper)allocate(PreparedJobWrapper.class);
            set(SpawnWrapper.class,wrapper,"spawnIndex",7);
            if(roleId.equals("Larva_Void")){
                var zipPath=Path.of(System.getProperty("user.home"),"AppData/Roaming/Hytale/install/release/package/game/latest/Assets.zip");
                try(var zip=new ZipFile(zipPath.toFile())){
                    var entry=Objects.requireNonNull(zip.getEntry("Server/NPC/Spawn/World/Void/Tier2_Night_NPC1.json"));
                    String json;try(var input=zip.getInputStream(entry)){
                        json=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                    }
                    var source=org.bson.BsonDocument.parse(json);
                    assertEquals("Larva_Void",source.getArray("NPCs").get(0).asDocument().getString("Id").getValue());
                    assertEquals(4,source.getArray("NPCs").get(0).asDocument().getDocument("Flock")
                            .getArray("Size").get(1).asInt32().getValue());
                    assertTrue(source.getArray("Environments").stream().anyMatch(value->value.asString().getValue()
                            .equals("Env_Zone1_Forests")));
                    var nativeSpawn=new com.hypixel.hytale.server.spawning.assets.spawns.config.WorldNPCSpawn("Tier2_Night_NPC1");
                    var nativeNpc=source.getArray("NPCs").get(0).asDocument();
                    var nativeParams=new com.hypixel.hytale.server.spawning.assets.spawns.config.RoleSpawnParameters(
                            nativeNpc.getString("Id").getValue(),nativeNpc.getNumber("Weight").doubleValue(),
                            nativeNpc.getString("SpawnBlockSet").getValue(),null);
                    set(NPCSpawn.class,nativeSpawn,"npcs",new com.hypixel.hytale.server.spawning.assets.spawns.config.RoleSpawnParameters[]{nativeParams});
                    set(NPCSpawn.class,nativeSpawn,"environments",source.getArray("Environments").stream()
                            .map(value->value.asString().getValue()).toArray(String[]::new));
                    set(SpawnWrapper.class,wrapper,"spawn",nativeSpawn);
                    set(SpawnWrapper.class,wrapper,"roles",it.unimi.dsi.fastutil.ints.Int2ObjectMaps.singleton(4,
                            nativeParams));
                    assertEquals(roleId,wrapper.getRoles().get(4).getId());
                }
            }
            nativeJob.init(4,null,3,wrapper,null,4);
            var job=new NativeEnemySpawnGroups.Job(worldId,nativeJob.getJobId(),nativeJob.getRoleIndex(),roleId,
                    nativeJob.getEnvironmentIndex(),nativeJob.getSpawnConfigIndex(),nativeJob.getFlockSize());
            var planRarity=new EnemyBirthPlanner(balance,EnemyAffixRegistry.canonical(),EnemyAffinityRegistry.canonical(),
                    EnemyNamePools.canonical(),EnemyVisualVariants.canonical());
            UUID[] actors=null;
            for(int n=1;n<10_000;n++) {
                var candidate=new UUID[]{new UUID(0,n),new UUID(1,1),new UUID(1,2),new UUID(1,3)};
                var seedText=new StringBuilder("me.native-group/").append(worldId).append('/').append(nativeJob.getJobId())
                        .append("/4/").append(roleId).append("/3/7");
                for(var id:candidate)seedText.append('/').append(id);
                var seed=RewardIntent.digest(seedText.toString());
                if(planRarity.rarity(seed,DifficultyId.NORMAL)==EnemyRarity.UNIQUE
                        &&new com.inigmasgames.hytalerpg.gear.GearRandom("master-enemies-v1.1/"+balance.revision()+"/"+seed)
                        .stream("me.minion-count").nextInt(3,6)==3){actors=candidate;break;}
            }
            assertNotNull(actors,"Deterministic natural job must select a Unique with exactly three minions");
            var nativeRole=(com.hypixel.hytale.server.npc.role.Role)allocate(com.hypixel.hytale.server.npc.role.Role.class);
            set(com.hypixel.hytale.server.npc.role.Role.class,nativeRole,"initialMaxHealth",100);
            set(com.hypixel.hytale.server.npc.role.Role.class,nativeRole,"interactionVars",
                    Map.of("Melee_Damage","Me12_"+roleId+"_Melee_Damage"));
            var hook=new NativeEnemySpawnGroups.Capture();
            var selectedActors=actors;
            NativeEnemySpawnGroups.capture(job,owner,store,()->{
                for(var actor:selectedActors) {
                var npc=new NPCEntity();npc.setRoleName(roleId);npc.setRole(nativeRole);
                try {set(NPCEntity.class,npc,"environmentIndex",3);set(NPCEntity.class,npc,"spawnConfigurationIndex",7);
                    set(NPCEntity.class,npc,"spawnRoleIndex",4);}catch(Exception failure){throw new AssertionError(failure);}
                WorldSupport support;
                try{support=(WorldSupport)allocate(WorldSupport.class);set(WorldSupport.class,support,"defaultPlayerAttitude",Attitude.HOSTILE);}
                catch(Exception failure){throw new AssertionError(failure);}
                var holder=registry.newHolder();holder.addComponent(uuid,new UUIDComponent(actor));holder.addComponent(npcType,npc);
                holder.addComponent(supportType,support);
                holder.addComponent(transformType,new TransformComponent(new org.joml.Vector3d(16,70,16),new com.hypixel.hytale.math.vector.Rotation3f()));
                var nativeHealth=new EntityStatMap();nativeHealth.update();nativeHealth.setStatValue(DefaultEntityStatTypes.getHealth(),100);
                holder.addComponent(statType,nativeHealth);
                hook.onEntityAdd(holder,AddReason.SPAWN,store);
                nativeIndex.put(actor,store.addEntity(holder,AddReason.SPAWN));
                var ref=nativeIndex.get(actor);
                assertNotNull(store.getComponent(ref,EnemyStaging.getComponentType()));
                assertEquals(Attitude.HOSTILE,store.getComponent(ref,supportType).getDefaultPlayerAttitude());
                assertNull(store.getComponent(ref,registry.getNonSerializedComponentType()));
                assertEquals(EncounterProfileResolver.Admission.READY,profiles.classifyNatural(EnemyRewardRegistry.load(),worldId,actor,
                        roleId,"Default/Zone1_Tier1/"+biome,3,EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,1).reason());
                assertTrue(rewards.classifyStagedNative(store,nativeIndex.get(actor),bindings.role(roleId).orElseThrow()).isPresent(),
                        "Native classifier must recognize the prepared hostile role and installed biome");
                }
            },nativeJob);
            assertNotNull(actualCapture.get(),"Native job must be captured on the owner thread before async planning");
            // Hytale pools job data after the native tick. Planning must retain the frozen capture.
            nativeJob.init(6,null,5,wrapper,null,2);
            if(interruptAfterRoot){
                var encounter=UUID.nameUUIDFromBytes(("me.encounter/"+worldId+"/"+nativeJob.getJobId()+"/"
                        +selectedActors[0]).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                world.drainUntil(()->disk.enemyBirthRoot(worldId,encounter).isPresent());
                var interrupted=disk.recoverEnemyWorld(worldId);
                assertEquals(1,interrupted.births().size());
                assertEquals(2,interrupted.births().getFirst().schemaVersion());
                assertEquals(EnemyPackRecord.State.RESERVED,interrupted.packs().getFirst().state());
                for(var id:selectedActors){var ref=nativeIndex.get(id);assertNotNull(store.getComponent(ref,EnemyStaging.getComponentType()));
                    assertNull(store.getComponent(ref,EnemyActorIdentity.getComponentType()));}
                admission.worldUnload(worldId);decision.close();rewards.close();disk.close();world.pending.clear();
                var restoredEntities=new EntityStore(world);world.entities=restoredEntities;
                set(World.class,world,"entityStore",restoredEntities);
                var restored=registry.addStore(restoredEntities,new VolatileResources());
                set(EntityStore.class,restoredEntities,"store",restored);
                @SuppressWarnings("unchecked") var restoredIndex=(Map<UUID,Ref<EntityStore>>)index.get(restoredEntities);
                for(var id:selectedActors){
                    var sourceRef=nativeIndex.get(id);
                    var originalStaging=store.getComponent(sourceRef,EnemyStaging.getComponentType()).state();
                    var npc=new NPCEntity();npc.setRoleName(roleId);npc.setRole(nativeRole);
                    set(NPCEntity.class,npc,"environmentIndex",3);
                    set(NPCEntity.class,npc,"spawnConfigurationIndex",7);
                    set(NPCEntity.class,npc,"spawnRoleIndex",4);
                    var support=(WorldSupport)allocate(WorldSupport.class);
                    set(WorldSupport.class,support,"defaultPlayerAttitude",Attitude.HOSTILE);
                    var holder=registry.newHolder();holder.addComponent(uuid,new UUIDComponent(id));
                    holder.addComponent(npcType,npc);holder.addComponent(supportType,support);
                    holder.addComponent(transformType,new TransformComponent(new org.joml.Vector3d(16,70,16),
                            new com.hypixel.hytale.math.vector.Rotation3f()));
                    var nativeHealth=new EntityStatMap();nativeHealth.update();
                    nativeHealth.setStatValue(DefaultEntityStatTypes.getHealth(),100);holder.addComponent(statType,nativeHealth);
                    holder.addComponent(EnemyStaging.getComponentType(),new EnemyStaging(originalStaging));
                    holder.ensureComponent(Frozen.getComponentType());holder.ensureComponent(Invulnerable.getComponentType());
                    holder.ensureComponent(Intangible.getComponentType());
                    restoredIndex.put(id,restored.addEntity(holder,AddReason.LOAD));
                }
                registry.removeStore(store);
                try(var reopened=FileEncounterStore.durableV2(root.resolve("encounters"));var restoredPlayers=loadouts()){
                    var restoredAdmission=new EnemyWorldAdmission(id->CompletableFuture.completedFuture(reopened.recoverEnemyWorld(id)),
                            new EnemyPackCapacity(balance),true);
                    var restoredRewards=new HytaleEncounterRewards(reopened,restoredPlayers,tracer,RpgCombatKernel.createProduction());
                    var restoredCombat=new HytaleDifficultyCombat(tracer);
                    restoredRewards.configureCombat(restoredCombat);restoredRewards.configureEnemyAdmission(restoredAdmission);
                    restoredRewards.configureDifficulty(profiles);
                    var restoredEffects=new FiniteSupportEffects();
                    var restoredActions=new NativeEnemyActions(new NativeEnemyOutgoingEffects(),ignored->{});
                    var restoredDecision=new NativeEnemyBirthDecision(restoredRewards,bindings,balance,()->policy,restoredAdmission);
                    var restoredReservation=new NativeEnemyBirthReservation(restoredDecision,restoredAdmission,restoredRewards);
                    var restoredAttachment=new NativeEnemyBirthAttachment(restoredRewards,new NativeEnemyStateAttachment(restoredEffects),
                            new NativeEnemyActionAttachment(restoredRewards,restoredCombat,restoredActions,restoredEffects,bindings),balance,bindings);
                    var restoredPublication=new NativeEnemyBirthPublication(restoredRewards,restoredCombat,restoredAdmission,
                            EnemyVisualVariants.canonical(),bindings,new HytaleEnemyWeaponVisuals());
                    var restoredOwner=new NativeEnemyBirthOwner(bindings,restoredAdmission,restoredRewards,restoredCombat,
                            restoredActions,restoredReservation,restoredDecision,restoredAttachment,restoredPublication,true);
                    var rebind=new NativeEnemyWorldRebind(restoredAdmission,restoredOwner,true,true);
                    var recovery=new NativeEnemyWholeBirthRecovery(restoredAdmission,restoredRewards,bindings,
                            restoredAttachment,restoredPublication,restoredOwner);
                    recovery.onPublished(rebind::retryInitial);
                    rebind.begin(world);
                    for(var id:selectedActors)recovery.onEntityAdded(restoredIndex.get(id),AddReason.LOAD,restored,null);
                    world.drainUntil(()->actorsPublished(restored,restoredIndex,selectedActors)
                            &&restoredOwner.bound(selectedActors[0]));
                    var recovered=reopened.recoverEnemyWorld(worldId);
                    assertEquals(1,recovered.births().size());
                    assertEquals(interrupted.births().getFirst(),recovered.births().getFirst());
                    assertTrue(Set.of(EnemyPackRecord.State.GUARDED,EnemyPackRecord.State.RELEASED)
                            .contains(recovered.packs().getFirst().state()));
                    for(var id:selectedActors){assertTrue(restoredOwner.bound(id));
                        assertNotNull(restored.getComponent(restoredIndex.get(id),EnemyActorIdentity.getComponentType()));}
                    restoredDecision.close();restoredRewards.close();
                }
                return;
            }
            world.drainUntil(()->actorsPublished(store,nativeIndex,selectedActors));
            assertNull(captureFailure.get(),()->"Production native context rejection: "+captureFailure.get());
            assertNotNull(actualCapture.get(),"Production native capture must reach the v2 planner context");
            var inventory=disk.recoverEnemyWorld(worldId);
            var tracePath=Path.of(trace.stop().lastFile());
            var birthPhases=Files.readString(tracePath).lines().filter(line->line.contains("BIRTH_")).toList();
            assertEquals(1,inventory.births().size());
            var birth=inventory.births().getFirst();
            assertEquals(2,birth.schemaVersion());
            assertEquals(4,birth.originalNativeEntities().size());
            assertEquals(List.of(actors),birth.originalNativeEntities());
            assertEquals(EnemyRarity.UNIQUE,birth.actors().getFirst().enemyRarity());
            assertEquals(4,birth.actors().size());
            assertEquals(1,inventory.packs().size());
            assertTrue(Set.of(EnemyPackRecord.State.GUARDED,EnemyPackRecord.State.RELEASED)
                    .contains(inventory.packs().getFirst().state()),
                    ()->"pack="+inventory.packs().getFirst().state()+" admissionFailed="+admission.failed(worldId)
                            +" phases="+birthPhases
                            +" identities="+Arrays.toString(Arrays.stream(selectedActors).map(actor->{
                                var ref=nativeIndex.get(actor);return ref==null?null:store.getComponent(ref,EnemyActorIdentity.getComponentType());
                            }).toArray()));
            assertEquals(birth,disk.enemyBirthRoot(worldId,birth.encounter()).orElseThrow().plan());
            for(var actor:actors){assertTrue(nativeIndex.get(actor).isValid());
                assertNull(store.getComponent(nativeIndex.get(actor),EnemyStaging.getComponentType()));
                assertNotNull(store.getComponent(nativeIndex.get(actor),EnemyActorIdentity.getComponentType()));
                assertTrue(owner.bound(actor));}
            decision.close();rewards.close();
        } finally { registry.shutdown();ChunkStore.REGISTRY.shutdown();old.set(null,previous);npcInstance.set(null,previousNpc);
            universeInstance.set(null,previousUniverse);statInstance.set(null,previousStats);spawnInstance.set(null,previousSpawning);
            damageInstance.set(null,previousDamage); }
    }
    private static boolean actorsPublished(Store<EntityStore> store,Map<UUID,Ref<EntityStore>> index,UUID[] actors){
        for(var id:actors){var ref=index.get(id);if(ref==null||!ref.isValid()
                ||store.getComponent(ref,EnemyStaging.getComponentType())!=null)return false;}
        return true;
    }
}

/** The durable-root interruption is recovered through the production LOAD owner. */
class NativeEnemyProductionBirthOwnerTest extends NativeEnemyBirthOwnerFixture {
    @Test void interruptedNaturalVoidSpawnRecoversSameBirth() throws Exception {
        exercise(true,"Larva_Void");
    }
}
