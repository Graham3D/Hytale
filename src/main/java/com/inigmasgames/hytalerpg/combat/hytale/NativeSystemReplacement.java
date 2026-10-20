package com.inigmasgames.hytalerpg.combat.hytale;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.ISystem;

import java.util.Objects;

/** Lifecycle-owned native adapter registration. Reuses the original instance and rejects exact-class dependencies. */
public final class NativeSystemReplacement<E> implements AutoCloseable {
    private final ComponentRegistry<E> registry;
    private final Class<? extends ISystem<E>> originalType,adapterType;
    private final ISystem<E> original,adapter;
    private final String diagnostic;
    private boolean closed;
    private NativeSystemReplacement(ComponentRegistry<E> registry,Class<? extends ISystem<E>> originalType,
            ISystem<E> original,Class<? extends ISystem<E>> adapterType,ISystem<E> adapter,String diagnostic){
        this.registry=registry;this.originalType=originalType;this.original=original;this.adapterType=adapterType;this.adapter=adapter;this.diagnostic=diagnostic;
    }
    public static <E,A extends ISystem<E>> NativeSystemReplacement<E> install(ComponentRegistry<E> registry,
            Class<? extends ISystem<E>> originalType,Class<A> adapterType,A adapter,String diagnostic){
        Objects.requireNonNull(registry);Objects.requireNonNull(adapter);Objects.requireNonNull(diagnostic);
        if(adapter.getClass()!=adapterType||originalType==adapterType)throw new IllegalArgumentException("NATIVE_ADAPTER_TYPE");
        if(find(registry,adapterType)!=null)throw new IllegalStateException("ENEMY_"+diagnostic+"_ALREADY_INSTALLED");
        var original=find(registry,originalType);
        if(original==null)throw new IllegalStateException("NATIVE_"+diagnostic+"_OWNER_MISSING");
        // Plugin lifecycle runs outside world stores; use the immutable published registry snapshot.
        var data=registry._internal_getData();
        for(int i=0;i<data.getSystemSize();i++){
            var system=data.getSystem(i);
            if(system!=null)for(var dependency:system.getDependencies())
                if(dependency instanceof SystemDependency<?,?> exact&&exact.getSystemClass()==originalType)
                    throw new IllegalStateException("NATIVE_"+diagnostic+"_EXACT_DEPENDENCY:"+system.getClass().getName());
        }
        NativeSystemOrder.preserve(registry,original,adapter);
        registry.unregisterSystem(originalType);
        try{registry.registerSystem(adapter);}
        catch(RuntimeException|Error failure){
            try{
                if(registry.hasSystemClass(adapterType))registry.unregisterSystem(adapterType);
                if(!registry.hasSystemClass(originalType))registry.registerSystem(original);
                NativeSystemOrder.restored(registry,original);
            }catch(RuntimeException|Error restore){failure.addSuppressed(restore);}
            throw failure;
        }
        return new NativeSystemReplacement<>(registry,originalType,original,adapterType,adapter,diagnostic);
    }
    private static <E> ISystem<E> find(ComponentRegistry<E> registry,Class<?> type){
        var data=registry._internal_getData();
        for(int i=0;i<data.getSystemSize();i++){var system=data.getSystem(i);if(system!=null&&system.getClass()==type)return system;}
        return null;
    }
    @Override public synchronized void close(){
        if(closed)return;
        if(registry.isShutdown()){closed=true;return;}
        if(find(registry,adapterType)!=adapter||find(registry,originalType)!=null)
            throw new IllegalStateException("ENEMY_"+diagnostic+"_TEARDOWN_OWNERSHIP_CHANGED");
        registry.unregisterSystem(adapterType);
        try{registry.registerSystem(original);}
        catch(RuntimeException|Error failure){
            try{
                if(!registry.hasSystemClass(originalType))registry.registerSystem(adapter);
                else closed=find(registry,originalType)==original;
            }catch(RuntimeException|Error restore){failure.addSuppressed(restore);}
            throw failure;
        }
        closed=true;
        NativeSystemOrder.restored(registry,original);
    }
}
