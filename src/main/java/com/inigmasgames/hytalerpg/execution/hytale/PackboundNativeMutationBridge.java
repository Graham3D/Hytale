package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytale.patch.NativeMutationHook;
import com.inigmasgames.hytalerpg.enemies.EnemyActorIdentity;
import java.util.*;

/** Binds the exact release native hook set to the existing Packbound protection owner. */
public final class PackboundNativeMutationBridge implements AutoCloseable {
    private static final String PATCH_HASH="23f71f03763422ce7c04df191db49cd0e7d87d43b12baad75c43fdc202410aff";
    /** Exact stock 0.7.2 server accepted for this mod-side interaction codec adapter. */
    public static String patchedServerSha256(){return PATCH_HASH;}
    private final AutoCloseable registration;
    private boolean closed;
    private PackboundNativeMutationBridge(AutoCloseable registration){this.registration=registration;}
    public static Optional<PackboundNativeMutationBridge> tryInstall(HytaleDifficultyCombat combat){
        Objects.requireNonNull(combat);
        try{
            return Optional.of(new PackboundNativeMutationBridge(
                    NativeMutationInteractions.install(mutation->allow(combat,mutation))));
        }catch(LinkageError|RuntimeException invalid){
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(invalid)
                    .log("RPG_PACKBOUND_MUTATION_INTERACTIONS_UNAVAILABLE promotion=false");
            return Optional.empty();
        }
    }
    public boolean verified(){return !closed&&registration instanceof NativeMutationInteractions guarded&&guarded.verified();}
    static boolean allow(HytaleDifficultyCombat combat,NativeMutationHook.Mutation mutation){
        Ref<EntityStore> target=mutation.target(),source=mutation.source();
        if(target==null||!target.isValid())return true; // Preserve native target rejection/no-op.
        var store=target.getStore();
        if(store==null||!store.isInThread())throw new IllegalStateException("PACKBOUND_NATIVE_MUTATION_WORLD_THREAD");
        if(source!=null&&source.isValid()&&source.getStore()==store&&source.getIndex()==target.getIndex())return true;
        var uuid=store.getComponent(target,UUIDComponent.getComponentType());
        if(uuid==null)return true; // Ordinary native target lacking RPG identity.
        var staging=EnemyStaging.getComponentType();
        if(staging!=null&&store.getComponent(target,staging)!=null)return false;
        var identity=EnemyActorIdentity.getComponentType();
        if(identity!=null&&store.getComponent(target,identity)!=null
                &&combat.enemyState(store.getExternalData().getWorld().getWorldConfig().getUuid(),uuid.getUuid()).isEmpty())
            return false;
        UUID sourceId=null;
        if(source!=null&&source.isValid()&&source.getStore()==store){
            var sourceUuid=store.getComponent(source,UUIDComponent.getComponentType());
            if(sourceUuid!=null)sourceId=sourceUuid.getUuid();
        }
        return combat.allowsExternalMutation(sourceId,uuid.getUuid());
    }
    @Override public void close(){
        if(closed)return;closed=true;
        try{registration.close();}catch(Exception failure){throw new IllegalStateException("PACKBOUND_NATIVE_HOOK_TEARDOWN",failure);}
    }
}
