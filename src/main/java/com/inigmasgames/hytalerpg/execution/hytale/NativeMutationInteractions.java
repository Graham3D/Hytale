package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.ChangeStatInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.ChangeStatWithModifierInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.ClearEntityEffectInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.util.InteractionTarget;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytale.patch.NativeMutationHook;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/** Exact-version codec substitution for only the three native mutation leaves. */
public final class NativeMutationInteractions implements AutoCloseable {
    private static final String SERVER_SHA256 =
            "23f71f03763422ce7c04df191db49cd0e7d87d43b12baad75c43fdc202410aff";
    private static final String STAT = "ChangeStat";
    private static final String MODIFIED = "ChangeStatWithModifier";
    private static final String CLEAR = "ClearEntityEffect";
    private static final MethodHandle CLEAR_TARGET = clearTarget();
    private final AssetBuilderCodec<String, Interaction> statCodec;
    private final AssetBuilderCodec<String, Interaction> modifiedCodec;
    private final AssetBuilderCodec<String, Interaction> clearCodec;
    private final Class<? extends Interaction> statClass, modifiedClass, clearClass;
    private final AutoCloseable policy;
    private boolean closed;

    private NativeMutationInteractions(AssetBuilderCodec<String, Interaction> statCodec,
            AssetBuilderCodec<String, Interaction> modifiedCodec,
            AssetBuilderCodec<String, Interaction> clearCodec,
            Class<? extends Interaction> statClass, Class<? extends Interaction> modifiedClass,
            Class<? extends Interaction> clearClass, AutoCloseable policy) {
        this.statCodec=statCodec;this.modifiedCodec=modifiedCodec;this.clearCodec=clearCodec;
        this.statClass=statClass;this.modifiedClass=modifiedClass;this.clearClass=clearClass;this.policy=policy;
    }

    @SuppressWarnings("unchecked")
    public static synchronized NativeMutationInteractions install(NativeMutationHook.Policy admission) {
        Objects.requireNonNull(admission);
        verifyServer();
        var registry=Interaction.CODEC;
        if(registry.getClassFor(STAT)!=ChangeStatInteraction.class
                ||registry.getClassFor(MODIFIED)!=ChangeStatWithModifierInteraction.class
                ||registry.getClassFor(CLEAR)!=ClearEntityEffectInteraction.class)
            throw new IllegalStateException("NATIVE_MUTATION_CODEC_OWNER_CHANGED");
        var stat=(AssetBuilderCodec<String,Interaction>)registry.getCodecFor(STAT);
        var modified=(AssetBuilderCodec<String,Interaction>)registry.getCodecFor(MODIFIED);
        var clear=(AssetBuilderCodec<String,Interaction>)registry.getCodecFor(CLEAR);
        if(stat==null||modified==null||clear==null)
            throw new IllegalStateException("NATIVE_MUTATION_CODEC_MISSING");
        var hook=NativeMutationHook.install(admission);
        try {
            registry.register(STAT, GuardedStat.class, GuardedStat.CODEC);
            registry.register(MODIFIED, GuardedModifiedStat.class, GuardedModifiedStat.CODEC);
            registry.register(CLEAR, GuardedClearEffect.class, GuardedClearEffect.CODEC);
            if(registry.getClassFor(STAT)!=GuardedStat.class
                    ||registry.getClassFor(MODIFIED)!=GuardedModifiedStat.class
                    ||registry.getClassFor(CLEAR)!=GuardedClearEffect.class)
                throw new IllegalStateException("NATIVE_MUTATION_CODEC_NOT_INSTALLED");
            return new NativeMutationInteractions(stat,modified,clear,
                    ChangeStatInteraction.class,ChangeStatWithModifierInteraction.class,
                    ClearEntityEffectInteraction.class,hook);
        } catch(RuntimeException|Error failure) {
            if(registry.getClassFor(STAT)==GuardedStat.class)registry.remove(GuardedStat.class);
            if(registry.getClassFor(MODIFIED)==GuardedModifiedStat.class)registry.remove(GuardedModifiedStat.class);
            if(registry.getClassFor(CLEAR)==GuardedClearEffect.class)registry.remove(GuardedClearEffect.class);
            registry.register(STAT,ChangeStatInteraction.class,ChangeStatInteraction.CODEC);
            registry.register(MODIFIED,ChangeStatWithModifierInteraction.class,ChangeStatWithModifierInteraction.CODEC);
            registry.register(CLEAR,ClearEntityEffectInteraction.class,ClearEntityEffectInteraction.CODEC);
            try {hook.close();} catch(Exception close){failure.addSuppressed(close);}
            throw failure;
        }
    }

    @Override public synchronized void close() {
        if(closed)return;
        var registry=Interaction.CODEC;
        if(registry.getClassFor(STAT)!=GuardedStat.class
                ||registry.getClassFor(MODIFIED)!=GuardedModifiedStat.class
                ||registry.getClassFor(CLEAR)!=GuardedClearEffect.class)
            throw new IllegalStateException("NATIVE_MUTATION_CODEC_TEARDOWN_OWNER_CHANGED");
        registry.remove(GuardedStat.class);registry.remove(GuardedModifiedStat.class);
        registry.remove(GuardedClearEffect.class);
        registry.register(STAT,statClass,statCodec);
        registry.register(MODIFIED,modifiedClass,modifiedCodec);
        registry.register(CLEAR,clearClass,clearCodec);
        try {policy.close();} catch(Exception failure){throw new IllegalStateException("NATIVE_MUTATION_POLICY_TEARDOWN",failure);}
        closed=true;
    }

    /** A previously decoded stock leaf would bypass this mod-side check. */
    public boolean verified() {
        if(closed)return false;
        var registry=Interaction.CODEC;
        if(registry.getClassFor(STAT)!=GuardedStat.class
                ||registry.getClassFor(MODIFIED)!=GuardedModifiedStat.class
                ||registry.getClassFor(CLEAR)!=GuardedClearEffect.class
                ||!NativeMutationHook.installed())return false;
        var assets=Interaction.getAssetMap();
        if(assets==null)return false;
        for(var leaf:assets.getAssetMap().values())
            if(leaf!=null&&(leaf.getClass()==ChangeStatInteraction.class
                    ||leaf.getClass()==ChangeStatWithModifierInteraction.class
                    ||leaf.getClass()==ClearEntityEffectInteraction.class))return false;
        return true;
    }

    private static MethodHandle clearTarget() {
        try {return MethodHandles.privateLookupIn(ClearEntityEffectInteraction.class,MethodHandles.lookup())
                .findGetter(ClearEntityEffectInteraction.class,"entityTarget",InteractionTarget.class);}
        catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}
    }
    private static InteractionTarget target(ClearEntityEffectInteraction leaf) {
        try{return (InteractionTarget)CLEAR_TARGET.invoke(leaf);}
        catch(Throwable failure){throw new IllegalStateException("NATIVE_CLEAR_EFFECT_TARGET_UNAVAILABLE",failure);}
    }
    private static void deny() {throw new InteractionManager.ChainCancelledException(InteractionState.Failed);}
    private static void verifyServer() {
        try {
            var location=ChangeStatInteraction.class.getProtectionDomain().getCodeSource().getLocation();
            Path source=Path.of(location.toURI());
            if(!Files.isRegularFile(source))throw new IllegalStateException("NATIVE_MUTATION_SERVER_NOT_JAR");
            var digest=MessageDigest.getInstance("SHA-256");
            try(var stream=Files.newInputStream(source)){byte[] bytes=new byte[65536];int n;
                while((n=stream.read(bytes))!=-1)digest.update(bytes,0,n);}
            if(!SERVER_SHA256.equals(HexFormat.of().formatHex(digest.digest())))
                throw new IllegalStateException("NATIVE_MUTATION_SERVER_IDENTITY_MISMATCH");
        }catch(Exception failure){throw new IllegalStateException("NATIVE_MUTATION_SERVER_UNVERIFIED",failure);}
    }

    public static final class GuardedStat extends ChangeStatInteraction {
        public static final BuilderCodec<GuardedStat> CODEC=BuilderCodec.builder(
                GuardedStat.class,GuardedStat::new,ChangeStatInteraction.CODEC).build();
        @Override protected void firstRun(InteractionType type,InteractionContext context,CooldownHandler cooldowns) {
            Ref<EntityStore> victim=context.getEntity();
            if(victim!=null&&victim.isValid()&&!NativeMutationHook.allowStat(context,victim,
                    entityStats==null?new Int2FloatOpenHashMap():entityStats,valueType,changeStatBehaviour))deny();
            super.firstRun(type,context,cooldowns);
        }
    }
    public static final class GuardedModifiedStat extends ChangeStatWithModifierInteraction {
        public static final BuilderCodec<GuardedModifiedStat> CODEC=BuilderCodec.builder(
                GuardedModifiedStat.class,GuardedModifiedStat::new,ChangeStatWithModifierInteraction.CODEC).build();
        @Override protected void firstRun(InteractionType type,InteractionContext context,CooldownHandler cooldowns) {
            Ref<EntityStore> victim=context.getEntity();
            if(victim!=null&&victim.isValid()&&!NativeMutationHook.allowStatWithModifier(context,victim,
                    entityStats==null?new Int2FloatOpenHashMap():entityStats,valueType,changeStatBehaviour))deny();
            super.firstRun(type,context,cooldowns);
        }
    }
    public static final class GuardedClearEffect extends ClearEntityEffectInteraction {
        public static final BuilderCodec<GuardedClearEffect> CODEC=BuilderCodec.builder(
                GuardedClearEffect.class,GuardedClearEffect::new,ClearEntityEffectInteraction.CODEC).build();
        @Override protected void firstRun(InteractionType type,InteractionContext context,CooldownHandler cooldowns) {
            Ref<EntityStore> victim=target(this).getEntity(context,context.getEntity());
            if(victim!=null&&victim.isValid()&&!NativeMutationHook.allowClear(context,victim,entityEffectId))deny();
            super.firstRun(type,context,cooldowns);
        }
    }
}
