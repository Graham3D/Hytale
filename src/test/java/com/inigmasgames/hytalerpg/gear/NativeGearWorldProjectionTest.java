package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.item.*;
import com.hypixel.hytale.server.core.modules.physics.component.*;
import com.hypixel.hytale.server.core.modules.time.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.*;
import com.hypixel.hytale.server.core.universe.world.storage.*;
import com.hypixel.hytale.server.core.asset.type.gameplay.GameplayConfig;
import com.inigmasgames.hytalerpg.progress.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real release item generation/ECS; inert world and chunk availability, no server or saved world. */
class NativeGearWorldProjectionTest {
    @TempDir Path root;
    static NativeAssetTestFixtures assets;
    static Object previousEntityModule,previousTimeModule;
    static final ComponentRegistry<EntityStore> entities=EntityStore.REGISTRY;
    static final ComponentRegistry<ChunkStore> chunks=ChunkStore.REGISTRY;

    @BeforeAll @SuppressWarnings({"unchecked","rawtypes"}) static void setup() throws Exception {
        assets=NativeAssetTestFixtures.open();
        // Chunk availability is a fixture input. Do not instantiate its disk loaders,
        // savers or streaming scheduler in this isolated native-control JVM.
        var chunkData=chunks._internal_getData();
        for(int i=0;i<chunkData.getSystemSize();i++){
            var system=chunkData.getSystem(i);
            if(system!=null)chunks.unregisterSystem((Class)system.getClass());
        }
        previousEntityModule=get(EntityModule.class,null,"instance");
        var module=allocate(EntityModule.class);set(EntityModule.class,null,"instance",module);
        bind(module,"itemComponentType",ItemComponent.class,ItemComponent::new);
        bind(module,"transformComponentType",TransformComponent.class,TransformComponent::new);
        bind(module,"velocityComponentType",Velocity.class,Velocity::new);
        bind(module,"physicsValuesComponentType",PhysicsValues.class,PhysicsValues::new);
        bind(module,"uuidComponentType",UUIDComponent.class,()->new UUIDComponent(UUID.randomUUID()));
        bind(module,"intangibleComponentType",Intangible.class,()->Intangible.INSTANCE);
        bind(module,"preventPickupComponentType",PreventPickup.class,()->PreventPickup.INSTANCE);
        bind(module,"despawnComponentComponentType",DespawnComponent.class,()->new DespawnComponent(java.time.Instant.EPOCH));
        previousTimeModule=get(TimeModule.class,null,"instance");
        var time=allocate(TimeModule.class);set(TimeModule.class,null,"instance",time);
        set(TimeModule.class,time,"timeResourceType",entities.registerResource(TimeResource.class,TimeResource::new));
    }
    @AfterAll static void cleanup() throws Exception {
        entities.shutdown();chunks.shutdown();
        set(EntityModule.class,null,"instance",previousEntityModule);
        set(TimeModule.class,null,"instance",previousTimeModule);
        if(assets!=null)assets.close();
    }

    @Test void unloadedAndNonTickingReceiptsNeverReachPickupAndRetryKeepsTheFrozenItem() throws Exception {
        var fixture=new GearTransactionsTest();var plan=fixture.plan(1);
        try(var disk=new FileEncounterStore(root)){
            var loot=new GearLootService(disk,fixture.generator(),System::currentTimeMillis);
            fixture.claims(loot,plan);var row=loot.death(plan,Map.of(fixture.a,0.,fixture.b,0.));
            assertEquals("WORLD",row.state());
            try(var runtime=new HytaleGearLoot(loot,null,null,root.resolve("no-qa-marker"))){
                runtime.prepareInitialView().join();
                var world=world(row.source().world());
                project(runtime,world);
                assertEquals(0,world.pickupScans,"Unloaded receipt must not collect an invisible item at its saved coordinates");
                assertTrue(displays(runtime,world).isEmpty());assertEquals(row,loot.inspect(row.source().eventId()).orElseThrow());
                var section=chunks.newHolder();section.ensureComponent(chunks.getNonTickingComponentType());
                world.sections.section=world.sections.nativeStore.addEntity(section,AddReason.SPAWN);
                project(runtime,world);
                assertEquals(0,world.pickupScans,"Addressable but non-ticking chunk must still defer pickup");
                assertTrue(displays(runtime,world).isEmpty());
                world.sections.nativeStore.removeComponent(world.sections.section,chunks.getNonTickingComponentType());
                var item=row.result().item();
                var binding=new GearBindings().require(item.baseId());
                var carrier=binding.carrier(item.rarity());
                var definition=Path.of("src/main/resources/Server/Item/Items/RPG/Gear",carrier+".json");
                assets.seedPackagedRootReferences(java.nio.file.Files.readString(definition));
                assets.decodeItem(carrier,definition);
                var projected=new ArrayList<GearLootService.Loot>();runtime.configureProjectionNotification(projected::add);
                project(runtime,world);
                var shown=displays(runtime,world);assertEquals(1,shown.size());
                var ref=shown.get(row.source().eventId());assertTrue(ref.isValid());
                assertNotNull(world.nativeStore.getComponent(ref,PreventPickup.getComponentType()));
                assertNotNull(world.nativeStore.getComponent(ref,entities.getNonSerializedComponentType()));
                var projectedStack=world.nativeStore.getComponent(ref,ItemComponent.getComponentType()).getItemStack();
                assertEquals(item,GearInstance.fromJson(projectedStack.getMetadata().getString(GearNativeItems.KEY).getValue()));
                assertThrows(IllegalArgumentException.class,()->GearNativeItems.read(projectedStack),"World custody must prohibit inventory use");
                assertEquals(List.of(row),projected);assertEquals(1,world.pickupScans);
                project(runtime,world);assertSame(ref,shown.get(row.source().eventId()));assertEquals(1,projected.size());
                // Native despawn/unload invalidates the old projection; the receipt recreates the same gear, never rerolls it.
                world.nativeStore.removeEntity(ref,RemoveReason.REMOVE);project(runtime,world);
                assertFalse(ref.isValid());assertEquals(2,projected.size());
                assertEquals(item,GearInstance.fromJson(world.nativeStore.getComponent(shown.get(row.source().eventId()),ItemComponent.getComponentType())
                        .getItemStack().getMetadata().getString(GearNativeItems.KEY).getValue()));
                // Custody reservation removes the presentation before inventory saving/acknowledgement.
                loot.reservePickup(row.source().eventId(),row.allocation().assigned(),row.allocation().revision(),System.currentTimeMillis(),true);
                invoke(runtime,"refresh",new Class<?>[0]);project(runtime,world);
                assertTrue(shown.isEmpty());assertEquals("PICKUP_PENDING_NATIVE_SAVE",loot.inspect(row.source().eventId()).orElseThrow().state());
            }
        }
    }

    @Test void invalidOrPositionlessProjectionCannotFallBackToReceiptCoordinates() throws Exception {
        var fixture=new GearTransactionsTest();var plan=fixture.plan(1);
        try(var disk=new FileEncounterStore(root)){
            var loot=new GearLootService(disk,fixture.generator(),System::currentTimeMillis);fixture.claims(loot,plan);
            var row=loot.death(plan,Map.of(fixture.a,0.,fixture.b,0.));
            try(var runtime=new HytaleGearLoot(loot,null,null,root.resolve("no-qa-marker"))){
                runtime.prepareInitialView().join();var world=world(row.source().world());
                var ref=world.nativeStore.addEntity(entities.newHolder(),AddReason.SPAWN);
                pickup(runtime,world,row,ref);assertEquals(0,world.pickupScans);
                world.nativeStore.removeEntity(ref,RemoveReason.REMOVE);
                pickup(runtime,world,row,ref);assertEquals(0,world.pickupScans);
                pickup(runtime,world,row,null);assertEquals(0,world.pickupScans);
                assertEquals(row,loot.inspect(row.source().eventId()).orElseThrow());
            }
        }
    }

    static ProbeWorld world(UUID id) throws Exception {
        var world=allocate(ProbeWorld.class);world.config=allocate(WorldConfig.class);world.config.setUuid(id);
        world.gameplay=new GameplayConfig();world.external=new EntityStore(world);
        world.nativeStore=entities.addStore(world.external,new Resources());set(EntityStore.class,world.external,"store",world.nativeStore);
        world.sections=allocate(Sections.class);world.sections.nativeStore=chunks.addStore(world.sections,new Resources());return world;
    }
    static void pickup(HytaleGearLoot runtime,ProbeWorld world,GearLootService.Loot row,Ref<EntityStore> ref) throws Exception {
        invoke(runtime,"autoPickup",new Class<?>[]{World.class,Store.class,GearLootService.Loot.class,Ref.class,long.class},world,world.nativeStore,row,ref,System.currentTimeMillis());
    }
    static void project(HytaleGearLoot runtime,World world) throws Exception {invoke(runtime,"project",new Class<?>[]{World.class},world);}
    @SuppressWarnings("unchecked") static Map<String,Ref<EntityStore>> displays(HytaleGearLoot runtime,ProbeWorld world) throws Exception {
        return ((Map<UUID,Map<String,Ref<EntityStore>>>)get(HytaleGearLoot.class,runtime,"displays")).get(world.config.getUuid());
    }
    static void invoke(Object target,String name,Class<?>[] types,Object...args) throws Exception {
        var method=target.getClass().getDeclaredMethod(name,types);method.setAccessible(true);method.invoke(target,args);
    }
    static <T> T allocate(Class<T> type) throws Exception {
        var unsafe=Class.forName("sun.misc.Unsafe");var field=unsafe.getDeclaredField("theUnsafe");field.setAccessible(true);
        return type.cast(unsafe.getMethod("allocateInstance",Class.class).invoke(field.get(null),type));
    }
    static Object get(Class<?> type,Object target,String name) throws Exception {var field=type.getDeclaredField(name);field.setAccessible(true);return field.get(target);}
    static void set(Class<?> type,Object target,String name,Object value) throws Exception {var field=type.getDeclaredField(name);field.setAccessible(true);field.set(target,value);}
    static <T extends Component<EntityStore>> void bind(Object module,String field,Class<T> type,java.util.function.Supplier<T> factory) throws Exception {
        set(EntityModule.class,module,field,entities.registerComponent(type,factory));
    }
    static class ProbeWorld extends World {
        WorldConfig config;GameplayConfig gameplay;EntityStore external;Store<EntityStore> nativeStore;Sections sections;int pickupScans;
        private ProbeWorld(){super("unused",Path.of("unused"),null);throw new AssertionError("Never construct or start a World");}
        @Override public WorldConfig getWorldConfig(){return config;}
        @Override public GameplayConfig getGameplayConfig(){return gameplay;}
        @Override public EntityStore getEntityStore(){return external;}
        @Override public ChunkStore getChunkStore(){return sections;}
        @Override public Collection<PlayerRef> getPlayerRefs(){pickupScans++;return List.of();}
    }
    static class Sections extends ChunkStore {
        Store<ChunkStore> nativeStore;Ref<ChunkStore> section;
        private Sections(){super(null);throw new AssertionError("Never start chunk persistence");}
        @Override public Store<ChunkStore> getStore(){return nativeStore;}
        @Override public Ref<ChunkStore> getChunkSectionReferenceAtBlock(int x,int y,int z){return section;}
    }
    static final class Resources implements IResourceStorage {
        public <T extends Resource<E>,E> CompletableFuture<T> load(Store<E> store,ComponentRegistry.Data<E> data,ResourceType<E,T> type){return CompletableFuture.completedFuture(data.createResource(type));}
        public <T extends Resource<E>,E> CompletableFuture<Void> save(Store<E> store,ComponentRegistry.Data<E> data,ResourceType<E,T> type,T resource){return CompletableFuture.completedFuture(null);}
        public <T extends Resource<E>,E> CompletableFuture<Void> remove(Store<E> store,ComponentRegistry.Data<E> data,ResourceType<E,T> type){return CompletableFuture.completedFuture(null);}
    }
}
