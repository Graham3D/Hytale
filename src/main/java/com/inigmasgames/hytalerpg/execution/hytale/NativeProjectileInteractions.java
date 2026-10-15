package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.LaunchProjectileInteraction;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.projectile.component.ImpactModifiers;
import com.hypixel.hytale.server.core.modules.projectile.event.ProjectileLaunchEvent;
import com.hypixel.hytale.server.core.modules.projectile.interaction.ProjectileInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.inigmasgames.hytale.patch.NativeProjectileReceiptHook;
import com.inigmasgames.hytale.patch.NativeModernProjectileHook;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/** Scoped native 0.7.2 launch provenance; Hytale still creates, shoots and impacts the projectile. */
public final class NativeProjectileInteractions implements AutoCloseable {
    private static final String SERVER_SHA256=
            "23f71f03763422ce7c04df191db49cd0e7d87d43b12baad75c43fdc202410aff";
    private static final ThreadLocal<InteractionContext> LEGACY=new ThreadLocal<>();
    private static final ThreadLocal<ModernFrame> MODERN=new ThreadLocal<>();
    private static final class ModernFrame {
        final InteractionContext context;final ProjectileInteraction leaf;final ModernFrame parent;
        boolean claimed;
        ModernFrame(InteractionContext context,ProjectileInteraction leaf,ModernFrame parent){
            this.context=context;this.leaf=leaf;this.parent=parent;}
    }
    private final AssetBuilderCodec<String,Interaction> legacyCodec,modernCodec;
    private final AutoCloseable legacyProvider,modernProvider;
    private boolean closed;
    private NativeProjectileInteractions(AssetBuilderCodec<String,Interaction> legacyCodec,
            AssetBuilderCodec<String,Interaction> modernCodec,AutoCloseable legacyProvider,
            AutoCloseable modernProvider){this.legacyCodec=legacyCodec;this.modernCodec=modernCodec;
        this.legacyProvider=legacyProvider;this.modernProvider=modernProvider;}

    @SuppressWarnings("unchecked")
    public static synchronized NativeProjectileInteractions install(NativeProjectileReceiptHook.Provider legacy,
            NativeModernProjectileHook.Provider modern){
        Objects.requireNonNull(legacy);Objects.requireNonNull(modern);verifyServer();
        var registry=Interaction.CODEC;
        if(registry.getClassFor("LaunchProjectile")!=LaunchProjectileInteraction.class
                ||registry.getClassFor("Projectile")!=ProjectileInteraction.class)
            throw new IllegalStateException("NATIVE_PROJECTILE_CODEC_OWNER_CHANGED");
        var oldLegacy=(AssetBuilderCodec<String,Interaction>)registry.getCodecFor("LaunchProjectile");
        var oldModern=(AssetBuilderCodec<String,Interaction>)registry.getCodecFor("Projectile");
        if(oldLegacy==null||oldModern==null)throw new IllegalStateException("NATIVE_PROJECTILE_CODEC_MISSING");
        var legacyLease=NativeProjectileReceiptHook.install(legacy);
        AutoCloseable modernLease=null;
        try{
            modernLease=NativeModernProjectileHook.install(modern);
            registry.register("LaunchProjectile",ObservedLegacy.class,ObservedLegacy.CODEC);
            registry.register("Projectile",ObservedModern.class,ObservedModern.CODEC);
            if(registry.getClassFor("LaunchProjectile")!=ObservedLegacy.class
                    ||registry.getClassFor("Projectile")!=ObservedModern.class)
                throw new IllegalStateException("NATIVE_PROJECTILE_CODEC_NOT_INSTALLED");
            return new NativeProjectileInteractions(oldLegacy,oldModern,legacyLease,modernLease);
        }catch(RuntimeException|Error failure){
            if(registry.getClassFor("LaunchProjectile")==ObservedLegacy.class)registry.remove(ObservedLegacy.class);
            if(registry.getClassFor("Projectile")==ObservedModern.class)registry.remove(ObservedModern.class);
            registry.register("LaunchProjectile",LaunchProjectileInteraction.class,LaunchProjectileInteraction.CODEC);
            registry.register("Projectile",ProjectileInteraction.class,ProjectileInteraction.CODEC);
            try{if(modernLease!=null)modernLease.close();legacyLease.close();}
            catch(Exception close){failure.addSuppressed(close);}throw failure;
        }
    }
    public boolean verified(){
        if(closed||Interaction.CODEC.getClassFor("LaunchProjectile")!=ObservedLegacy.class
                ||Interaction.CODEC.getClassFor("Projectile")!=ObservedModern.class
                ||!NativeProjectileReceiptHook.installed()||!NativeModernProjectileHook.installed()
                ||Interaction.getAssetMap()==null)return false;
        return Interaction.getAssetMap().getAssetMap().values().stream().noneMatch(leaf->leaf!=null
                &&(leaf.getClass()==LaunchProjectileInteraction.class||leaf.getClass()==ProjectileInteraction.class));
    }
    @Override public synchronized void close(){
        if(closed)return;
        var registry=Interaction.CODEC;
        if(registry.getClassFor("LaunchProjectile")!=ObservedLegacy.class
                ||registry.getClassFor("Projectile")!=ObservedModern.class)
            throw new IllegalStateException("NATIVE_PROJECTILE_CODEC_TEARDOWN_OWNER_CHANGED");
        registry.remove(ObservedLegacy.class);registry.remove(ObservedModern.class);
        registry.register("LaunchProjectile",LaunchProjectileInteraction.class,legacyCodec);
        registry.register("Projectile",ProjectileInteraction.class,modernCodec);
        try{modernProvider.close();legacyProvider.close();}
        catch(Exception failure){throw new IllegalStateException("NATIVE_PROJECTILE_PROVIDER_TEARDOWN",failure);}
        closed=true;
    }
    private static void verifyServer(){
        try{
            Path source=Path.of(ProjectileInteraction.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if(!Files.isRegularFile(source))throw new IllegalStateException("NATIVE_PROJECTILE_SERVER_NOT_JAR");
            var digest=MessageDigest.getInstance("SHA-256");
            try(var stream=Files.newInputStream(source)){byte[] bytes=new byte[65536];int n;
                while((n=stream.read(bytes))!=-1)digest.update(bytes,0,n);}
            if(!SERVER_SHA256.equals(HexFormat.of().formatHex(digest.digest())))
                throw new IllegalStateException("NATIVE_PROJECTILE_SERVER_IDENTITY_MISMATCH");
        }catch(Exception failure){throw new IllegalStateException("NATIVE_PROJECTILE_SERVER_UNVERIFIED",failure);}
    }
    public static final class ObservedLegacy extends LaunchProjectileInteraction {
        public static final BuilderCodec<ObservedLegacy> CODEC=BuilderCodec.builder(
                ObservedLegacy.class,ObservedLegacy::new,LaunchProjectileInteraction.CODEC).build();
        @Override protected void firstRun(InteractionType type,InteractionContext context,CooldownHandler cooldowns){
            var previous=LEGACY.get();LEGACY.set(context);
            try{super.firstRun(type,context,cooldowns);}
            finally{if(previous==null)LEGACY.remove();else LEGACY.set(previous);}
        }
    }
    public static final class ObservedModern extends ProjectileInteraction {
        public static final BuilderCodec<ObservedModern> CODEC=BuilderCodec.builder(
                ObservedModern.class,ObservedModern::new,ProjectileInteraction.CODEC).build();
        @Override protected void firstRun(InteractionType type,InteractionContext context,CooldownHandler cooldowns){
            var previous=MODERN.get();var frame=new ModernFrame(context,this,previous);MODERN.set(frame);
            try{super.firstRun(type,context,cooldowns);}
            finally{if(previous==null)MODERN.remove();else MODERN.set(previous);}
        }
    }
    /** The original fully initialized legacy holder, before CommandBuffer.addEntity returns. */
    public static final class LegacyHolder extends HolderSystem<EntityStore> {
        @Override public Query<EntityStore> getQuery(){return ProjectileComponent.getComponentType();}
        @Override public void onEntityAdd(Holder<EntityStore> holder,AddReason reason,Store<EntityStore> store){
            var context=LEGACY.get();
            if(context!=null&&reason==AddReason.SPAWN&&context.getCommandBuffer().getStore()==store)
                NativeProjectileReceiptHook.beforeQueue(context,holder);
        }
        @Override public void onEntityRemoved(Holder<EntityStore> holder,RemoveReason reason,Store<EntityStore> store){}
    }
    /** Native launch event supplies the exact modern ref after Hytale creates it. */
    public static final class ModernLaunch extends EntityEventSystem<EntityStore,ProjectileLaunchEvent> {
        public ModernLaunch(){super(ProjectileLaunchEvent.class);}
        @Override public Query<EntityStore> getQuery(){return NPCEntity.getComponentType();}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
                CommandBuffer<EntityStore> buffer,ProjectileLaunchEvent event){
            var frame=MODERN.get();if(frame==null)return;
            var context=frame.context;
            if(context.getCommandBuffer()!=buffer||context.getEntity()!=chunk.getReferenceTo(index))return;
            if(frame.claimed)throw new IllegalStateException("NATIVE_MODERN_PROJECTILE_LAUNCH_REPLAY");
            frame.claimed=true;
            var source=context.getOwningEntity();
            if(source==null||!source.isValid())source=context.getEntity();
            var modifiers=ImpactModifiers.collect(context,source,buffer);
            NativeModernProjectileHook.afterSpawn(context,event.getProjectileRef(),frame.leaf.getConfig(),modifiers);
        }
    }
}
