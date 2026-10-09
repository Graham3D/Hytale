package com.inigmasgames.hytale.patch;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Neutral seam after native target reconstruction and before native random selection. */
public final class NativePopulationProjectionHook {
    public static final String PATCH_ID="population-projection-0.7.0-pre.5.1-r244";
    private static final AtomicReference<Consumer<Store<ChunkStore>>> PROVIDER=new AtomicReference<>();
    private NativePopulationProjectionHook(){}
    public static AutoCloseable install(Consumer<Store<ChunkStore>> provider){
        Objects.requireNonNull(provider);
        if(!PROVIDER.compareAndSet(null,provider))throw new IllegalStateException("NATIVE_POPULATION_PROVIDER_DUPLICATE");
        return ()->PROVIDER.compareAndSet(provider,null);
    }
    public static void beforeSelection(Store<ChunkStore> store){
        var provider=PROVIDER.get();if(provider!=null)provider.accept(store);
    }
}
