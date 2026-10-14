package com.inigmasgames.hytale.patch;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.ForkedChainId;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.projectile.component.ImpactModifiers;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Read-only native creation seam. The provider may add a receipt to the original spawned entity. */
public final class NativeModernProjectileHook {
    public static final String PATCH_ID="me-modern-projectile-0.7.1-r262me12s3c";
    public record Context(UUID world,UUID executor,UUID owner,int chainId,List<Integer> forkPath,
            String initialRoot,String operationRoot,int operationIndex,int operationCounter,String configId) {
        public Context { Objects.requireNonNull(world);forkPath=List.copyOf(forkPath);Objects.requireNonNull(configId); }
    }
    @FunctionalInterface public interface Provider {
        void attach(Context context,Ref<EntityStore> projectile,CommandBuffer<EntityStore> buffer,ImpactModifiers modifiers);
    }
    private static final AtomicReference<Provider> PROVIDER=new AtomicReference<>();
    private NativeModernProjectileHook(){}
    public static boolean installed(){return PROVIDER.get()!=null;}
    public static AutoCloseable install(Provider provider){
        Objects.requireNonNull(provider);
        if(!PROVIDER.compareAndSet(null,provider))throw new IllegalStateException("NATIVE_MODERN_PROJECTILE_PROVIDER_ALREADY_INSTALLED");
        return ()->{if(!PROVIDER.compareAndSet(provider,null))throw new IllegalStateException("NATIVE_MODERN_PROJECTILE_PROVIDER_CHANGED");};
    }
    private static UUID uuid(CommandBuffer<EntityStore> buffer,Ref<EntityStore> ref){
        if(ref==null||!ref.isValid()||ref.getStore()!=buffer.getStore())return null;
        var id=buffer.getComponent(ref,UUIDComponent.getComponentType());return id==null?null:id.getUuid();
    }
    private static List<Integer> path(ForkedChainId fork){
        var parts=new ArrayList<Integer>();int depth=0;
        while(fork!=null){
            if(++depth>16)throw new IllegalStateException("NATIVE_MODERN_PROJECTILE_FORK_DEPTH");
            parts.add(fork.entryIndex);parts.add(fork.subIndex);fork=fork.forkedId;
        }
        return List.copyOf(parts);
    }
    /** Called after native spawn and native modifier collection, before native launch event. */
    public static void afterSpawn(InteractionContext interaction,Ref<EntityStore> projectile,
            ProjectileConfig config,ImpactModifiers modifiers){
        var provider=PROVIDER.get();if(provider==null)return;
        Objects.requireNonNull(interaction);Objects.requireNonNull(config);
        var buffer=Objects.requireNonNull(interaction.getCommandBuffer());
        InteractionChain chain=interaction.getChain();
        var initial=chain==null?null:chain.getInitialRootInteraction();
        var operation=chain==null?null:chain.getRootInteraction();
        provider.attach(new Context(buffer.getStore().getExternalData().getWorld().getWorldConfig().getUuid(),
                uuid(buffer,interaction.getEntity()),uuid(buffer,interaction.getOwningEntity()),
                chain==null?-1:chain.getChainId(),chain==null?List.of():path(chain.getForkedChainId()),
                initial==null?null:initial.getId(),operation==null?null:operation.getId(),
                chain==null?-1:chain.getOperationIndex(),interaction.getOperationCounter(),config.getId()),
                projectile,buffer,modifiers);
    }
}
