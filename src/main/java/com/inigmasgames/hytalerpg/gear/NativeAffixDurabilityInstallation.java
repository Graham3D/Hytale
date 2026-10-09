package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Replaces the two stateless native loss producers before worlds run; restores stock writers on unload. */
public final class NativeAffixDurabilityInstallation implements AutoCloseable {
    private final Replacement<EntityStore> replacement;
    public NativeAffixDurabilityInstallation() {
        replacement = Replacement.install(EntityStore.REGISTRY, List.of(
                new DamageSystems.DamageArmor(), new DamageSystems.DamageAttackerTool()), List.of(
                new NativeAffixDurabilitySystems.Armor(), new NativeAffixDurabilitySystems.Attacker()));
    }
    @Override public void close() { replacement.close(); }

    /** Uses the registry's public update lock so no store sees half of the substitution. */
    static final class Replacement<E> implements AutoCloseable {
        private final ComponentRegistry<E> registry;
        private final List<ISystem<E>> originals;
        private final List<ISystem<E>> installed;
        private boolean closed;
        private Replacement(ComponentRegistry<E> registry,List<ISystem<E>> originals,List<ISystem<E>> installed) {
            this.registry=registry;this.originals=List.copyOf(originals);this.installed=List.copyOf(installed);
        }
        static <E> Replacement<E> install(ComponentRegistry<E> registry,
                List<ISystem<E>> stockWriters,List<ISystem<E>> replacements) {
            Objects.requireNonNull(registry);
            var classes=stockWriters.stream().map(Replacement::type).toList();
            if(classes.size()!=replacements.size() || classes.isEmpty()
                    || classes.stream().distinct().count()!=classes.size()
                    || replacements.stream().map(Object::getClass).distinct().count()!=replacements.size())
                throw new IllegalArgumentException("Invalid system substitution");
            var lock=registry.getDataUpdateLock().writeLock();lock.lock();
            try {
                if(registry.isShutdown())throw new IllegalStateException("Native registry is shut down");
                // These native classes have no instance state. Their public constructors allow
                // rollback without reading store-thread-only registry internals during plugin setup.
                var originals=List.copyOf(stockWriters);
                for(var type:classes) {
                    if(!registry.hasSystemClass(type))throw new IllegalStateException("Missing native producer: "+type.getName());
                }
                for(var system:replacements)
                    if(registry.hasSystemClass(type(system)))throw new IllegalStateException("Replacement already registered");
                var removed=new ArrayList<ISystem<E>>();
                try {
                    for(var original:originals) { registry.unregisterSystem(type(original));removed.add(original); }
                    for(var system:replacements) registry.registerSystem(system);
                    return new Replacement<>(registry,originals,replacements);
                } catch(RuntimeException | Error failure) {
                    for(int i=replacements.size()-1;i>=0;i--) {
                        try { if(registry.hasSystem(replacements.get(i)))registry.unregisterSystem(type(replacements.get(i))); }
                        catch(RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                    }
                    for(var original:removed) {
                        try { if(!registry.hasSystemClass(type(original)))registry.registerSystem(original); }
                        catch(RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                    }
                    throw failure;
                }
            } finally { lock.unlock(); }
        }
        @SuppressWarnings("unchecked")
        private static <E> Class<? extends ISystem<E>> type(ISystem<E> system) {
            return (Class<? extends ISystem<E>>)system.getClass();
        }
        @Override public void close() {
            var lock=registry.getDataUpdateLock().writeLock();lock.lock();
            try {
                if(closed)return;
                if(registry.isShutdown()){closed=true;return;}
                for(var system:installed)
                    if(registry.hasSystemClass(type(system)))registry.unregisterSystem(type(system));
                for(var original:originals)
                    if(!registry.hasSystemClass(type(original)))registry.registerSystem(original);
                closed=true;
            } finally { lock.unlock(); }
        }
    }
}
