package com.inigmasgames.hytale.patch;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.ForkedChainId;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Neutral causal-identity seam. Hywind owns acceptance and durable receipt allocation. */
public final class NativeDamageReceiptHook {
    public static final String PATCH_ID="me-damage-receipt-0.7.0-pre.5.1-1";
    private static volatile MetaKey<String> receiptKey;
    public record Context(UUID world,UUID executor,UUID owner,UUID source,UUID target,
            int chainId,List<Integer> forkPath,String initialRoot,String operationRoot,
            int operationIndex,int operationCounter,int componentIndex,String cause){
        public Context{Objects.requireNonNull(world);Objects.requireNonNull(target);
            forkPath=List.copyOf(forkPath);Objects.requireNonNull(initialRoot);
            Objects.requireNonNull(operationRoot);Objects.requireNonNull(cause);}
    }
    @FunctionalInterface public interface Provider { String receipt(Context context); }
    private static final AtomicReference<Provider> PROVIDER=new AtomicReference<>();
    private NativeDamageReceiptHook(){}
    public static boolean installed(){return PROVIDER.get()!=null;}
    public static synchronized AutoCloseable install(Provider provider){
        Objects.requireNonNull(provider);
        if(PROVIDER.get()!=null)throw new IllegalStateException("NATIVE_DAMAGE_RECEIPT_PROVIDER_ALREADY_INSTALLED");
        if(receiptKey==null)receiptKey=Damage.META_REGISTRY.registerMetaObject(
                ignored->null,false,"InigmasGames:NativeCausalDamageReceipt",null);
        if(!PROVIDER.compareAndSet(null,provider))throw new IllegalStateException("NATIVE_DAMAGE_RECEIPT_PROVIDER_ALREADY_INSTALLED");
        return ()->{if(!PROVIDER.compareAndSet(provider,null))throw new IllegalStateException("NATIVE_DAMAGE_RECEIPT_PROVIDER_CHANGED");};
    }
    public static String receipt(Damage damage){var key=receiptKey;return damage==null||key==null?null:damage.getIfPresentMetaObject(key);}
    private static UUID uuid(InteractionContext context,Ref<EntityStore> ref){
        if(ref==null||!ref.isValid())return null;
        var id=context.getCommandBuffer().getComponent(ref,UUIDComponent.getComponentType());
        return id==null?null:id.getUuid();
    }
    private static List<Integer> path(ForkedChainId fork){
        var parts=new ArrayList<Integer>();int depth=0;
        while(fork!=null){
            if(++depth>16)throw new IllegalStateException("NATIVE_DAMAGE_RECEIPT_FORK_DEPTH");
            parts.add(fork.entryIndex);parts.add(fork.subIndex);fork=fork.forkedId;
        }
        return List.copyOf(parts);
    }
    /** Called immediately before the native invoke. An absent consumer makes no event mutation. */
    public static void stamp(InteractionContext interaction,Ref<EntityStore> target,Damage damage,int componentIndex){
        var provider=PROVIDER.get();if(provider==null)return;
        Objects.requireNonNull(interaction);Objects.requireNonNull(damage);
        InteractionChain chain=interaction.getChain();
        if(chain==null||target==null||!target.isValid()||damage.getCause()==null)return;
        var initial=chain.getInitialRootInteraction();var operation=chain.getRootInteraction();
        if(initial==null||operation==null)return;
        var source=damage.getSource() instanceof Damage.EntitySource entity?entity.getRef():null;
        var frozen=new Context(interaction.getCommandBuffer().getStore().getExternalData().getWorld().getWorldConfig().getUuid(),
                uuid(interaction,interaction.getEntity()),uuid(interaction,interaction.getOwningEntity()),
                uuid(interaction,source),uuid(interaction,target),chain.getChainId(),path(chain.getForkedChainId()),
                initial.getId(),operation.getId(),chain.getOperationIndex(),interaction.getOperationCounter(),
                componentIndex,damage.getCause().getId());
        if(frozen.target()==null)return;
        var receipt=provider.receipt(frozen);
        if(receipt==null)return;
        if(receipt.isBlank()||receipt.length()>512)throw new IllegalArgumentException("NATIVE_DAMAGE_RECEIPT_INVALID");
        var key=receiptKey;
        if(key==null||damage.getIfPresentMetaObject(key)!=null)throw new IllegalStateException("NATIVE_DAMAGE_RECEIPT_DUPLICATE_STAMP");
        damage.putMetaObject(key,receipt);
    }
}
