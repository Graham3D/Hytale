package com.inigmasgames.hytalerpg.spawning;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.server.core.modules.LegacyModule;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.spawning.world.system.WorldSpawningSystem;
import java.lang.reflect.Field;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Registration against the installed native owner, with its original instance restored on close. */
class NativePopulationSelectionTest {
    @Test void exactInstalledServerReplacesSelectionOnceAndRestoresTheOriginal() {
        var registry = new ComponentRegistry<ChunkStore>();
        var balance = new NativePopulationBalance(() -> new NativePopulationBalance.Policy(true, .7, .3));
        try (var nativeTypes = nativeWorldChunkType(registry)) {
            var original = new WorldSpawningSystem(null, null, null, null);
            registry.registerSystem(original);
            var adapter = new NativePopulationSelection(null, null, null, null, balance);
            var lease = adapter.install(registry);
            assertFalse(registry.hasSystemClass(WorldSpawningSystem.class));
            assertTrue(registry.hasSystemClass(NativePopulationSelection.class));
            assertThrows(IllegalStateException.class, () -> adapter.install(registry));
            lease.close();
            lease.close();
            assertTrue(registry.hasSystemClass(WorldSpawningSystem.class));
            assertFalse(registry.hasSystemClass(NativePopulationSelection.class));
        } catch (Exception failure) { throw new AssertionError(failure); }
        finally { balance.close(); registry.shutdown(); }
    }

    private static final class NativeDependent implements ISystem<ChunkStore> {
        @Override public Set<com.hypixel.hytale.component.dependency.Dependency<ChunkStore>> getDependencies() {
            return Set.of(new SystemDependency<>(Order.BEFORE, WorldSpawningSystem.class));
        }
    }

    @Test void exactNativeDependencyFailsClosedWithoutChangingRegistry() {
        var registry = new ComponentRegistry<ChunkStore>();
        var balance = new NativePopulationBalance(() -> new NativePopulationBalance.Policy(true, .7, .3));
        try (var nativeTypes = nativeWorldChunkType(registry)) {
            registry.registerSystem(new WorldSpawningSystem(null, null, null, null));
            registry.registerSystem(new NativeDependent());
            var adapter = new NativePopulationSelection(null, null, null, null, balance);
            assertThrows(IllegalStateException.class, () -> adapter.install(registry));
            assertTrue(registry.hasSystemClass(WorldSpawningSystem.class));
            assertFalse(registry.hasSystemClass(NativePopulationSelection.class));
        } catch (Exception failure) { throw new AssertionError(failure); }
        finally { balance.close(); registry.shutdown(); }
    }

    /** Initialize only the native component-type owner needed by the real native constructor. */
    private static AutoCloseable nativeWorldChunkType(ComponentRegistry<ChunkStore> registry) throws Exception {
        var singleton = LegacyModule.class.getDeclaredField("instance"); singleton.setAccessible(true);
        var prior = singleton.get(null);
        var unsafeType = Class.forName("sun.misc.Unsafe");
        Field field = unsafeType.getDeclaredField("theUnsafe"); field.setAccessible(true);
        var module = (LegacyModule) unsafeType.getMethod("allocateInstance", Class.class)
                .invoke(field.get(null), LegacyModule.class);
        var typeField = LegacyModule.class.getDeclaredField("worldChunkComponentType"); typeField.setAccessible(true);
        typeField.set(module, registry.registerComponent(WorldChunk.class, () -> null));
        singleton.set(null, module);
        return () -> singleton.set(null, prior);
    }
}
