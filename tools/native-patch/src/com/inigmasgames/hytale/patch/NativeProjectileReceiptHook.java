package com.inigmasgames.hytale.patch;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.ForkedChainId;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Neutral holder seam before native addEntity. Hywind owns accepted-root matching and metadata. */
public final class NativeProjectileReceiptHook {
    public static final String PATCH_ID="me-projectile-receipt-0.7.0-pre.5.1-1";
    public record Context(UUID world,UUID executor,UUID owner,UUID projectile,
            int chainId,List<Integer> forkPath,String initialRoot,String operationRoot,
            int operationIndex,int operationCounter){
        public Context{
            Objects.requireNonNull(world);forkPath=List.copyOf(forkPath);
        }
    }
    @FunctionalInterface public interface Provider { void attach(Context context,Holder<EntityStore> originalHolder); }
    private static final AtomicReference<Provider> PROVIDER=new AtomicReference<>();
    private NativeProjectileReceiptHook(){}
    public static boolean installed(){return PROVIDER.get()!=null;}
    public static AutoCloseable install(Provider provider){
        Objects.requireNonNull(provider);
        if(!PROVIDER.compareAndSet(null,provider))throw new IllegalStateException("NATIVE_PROJECTILE_RECEIPT_PROVIDER_ALREADY_INSTALLED");
        return ()->{if(!PROVIDER.compareAndSet(provider,null))throw new IllegalStateException("NATIVE_PROJECTILE_RECEIPT_PROVIDER_CHANGED");};
    }
    private static UUID uuid(InteractionContext context,Ref<EntityStore> ref){
        if(ref==null||!ref.isValid())return null;
        var id=context.getCommandBuffer().getComponent(ref,UUIDComponent.getComponentType());
        return id==null?null:id.getUuid();
    }
    private static List<Integer> path(ForkedChainId fork){
        var parts=new ArrayList<Integer>();int depth=0;
        while(fork!=null){
            if(++depth>16)throw new IllegalStateException("NATIVE_PROJECTILE_RECEIPT_FORK_DEPTH");
            parts.add(fork.entryIndex);parts.add(fork.subIndex);fork=fork.forkedId;
        }
        return List.copyOf(parts);
    }
    /** Called after native shoot and immediately before native addEntity on the original holder. */
    public static void beforeQueue(InteractionContext interaction,Holder<EntityStore> holder){
        var provider=PROVIDER.get();if(provider==null)return;
        Objects.requireNonNull(interaction);Objects.requireNonNull(holder);
        InteractionChain chain=interaction.getChain();
        var projectileId=holder.getComponent(UUIDComponent.getComponentType());
        var initial=chain==null?null:chain.getInitialRootInteraction();
        var operation=chain==null?null:chain.getRootInteraction();
        var frozen=new Context(interaction.getCommandBuffer().getStore().getExternalData().getWorld().getWorldConfig().getUuid(),
                uuid(interaction,interaction.getEntity()),uuid(interaction,interaction.getOwningEntity()),
                projectileId==null?null:projectileId.getUuid(),chain==null?-1:chain.getChainId(),
                chain==null?List.of():path(chain.getForkedChainId()),
                initial==null?null:initial.getId(),operation==null?null:operation.getId(),
                chain==null?-1:chain.getOperationIndex(),interaction.getOperationCounter());
        provider.attach(frozen,holder);
        var after=holder.getComponent(UUIDComponent.getComponentType());
        if(!Objects.equals(frozen.projectile(),after==null?null:after.getUuid()))
            throw new IllegalStateException("NATIVE_PROJECTILE_RECEIPT_NATIVE_ID_CHANGED");
    }
}
