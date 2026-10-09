package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytale.patch.NativeMutationHook;
import com.inigmasgames.hytalerpg.enemies.EnemyActorIdentity;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Binds the exact native three-leaf hook to the existing Packbound protection owner. */
public final class PackboundNativeMutationBridge implements AutoCloseable {
    private static final String PATCH_HASH="6f4233203e804d6b0071a6416d26dd867f5cfb0c60d3e78b69a6a91415c1a59e";
    private final AutoCloseable registration;
    private boolean closed;
    private PackboundNativeMutationBridge(AutoCloseable registration){this.registration=registration;}
    public static Optional<PackboundNativeMutationBridge> tryInstall(HytaleDifficultyCombat combat){
        Objects.requireNonNull(combat);
        try{
            Class.forName("com.inigmasgames.hytale.patch.NativeMutationHook",false,
                    PackboundNativeMutationBridge.class.getClassLoader());
            if(!PATCH_HASH.equals(codeSourceHash()))
                throw new IllegalStateException("PACKBOUND_NATIVE_PATCH_VERSION_MISMATCH");
            return Optional.of(new PackboundNativeMutationBridge(NativeMutationHook.install(mutation->allow(combat,mutation))));
        }catch(ClassNotFoundException|LinkageError missing){
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(missing)
                    .log("RPG_PACKBOUND_NATIVE_PATCH_UNAVAILABLE promotion=false");
            return Optional.empty();
        }catch(Exception invalid){
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(invalid)
                    .log("RPG_PACKBOUND_NATIVE_PATCH_INVALID promotion=false");
            return Optional.empty();
        }
    }
    private static String codeSourceHash()throws Exception{
        var source=NativeMutationHook.class.getProtectionDomain().getCodeSource();
        if(source==null)throw new IllegalStateException("PACKBOUND_NATIVE_PATCH_SOURCE_MISSING");
        var path=Path.of(source.getLocation().toURI());
        if(!Files.isRegularFile(path))throw new IllegalStateException("PACKBOUND_NATIVE_PATCH_NOT_JAR");
        var digest=MessageDigest.getInstance("SHA-256");
        try(var input=Files.newInputStream(path)){byte[] block=new byte[65536];int length;
            while((length=input.read(block))!=-1)digest.update(block,0,length);}
        return HexFormat.of().formatHex(digest.digest());
    }
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
