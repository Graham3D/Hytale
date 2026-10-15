package com.inigmasgames.hytalerpg.combat.hytale;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DamageEntityInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytale.patch.NativeDamageReceiptHook;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/** Native 0.7.2 damage leaf scope; the original calculator and Health application remain native. */
public final class NativeDamageInteractions implements AutoCloseable {
    private static final String SERVER_SHA256=
            "23f71f03763422ce7c04df191db49cd0e7d87d43b12baad75c43fdc202410aff";
    private static final ThreadLocal<Frame> CURRENT=new ThreadLocal<>();
    private record Frame(InteractionContext context,Frame parent,int[] index){}
    private final Class<? extends Interaction> originalClass;
    private final com.hypixel.hytale.assetstore.codec.AssetBuilderCodec<String,Interaction> originalCodec;
    private final AutoCloseable provider;
    private boolean closed;
    private NativeDamageInteractions(Class<? extends Interaction> type,
            com.hypixel.hytale.assetstore.codec.AssetBuilderCodec<String,Interaction> codec,
            AutoCloseable provider){this.originalClass=type;this.originalCodec=codec;this.provider=provider;}

    public static AutoCloseable enter(InteractionContext context){
        var prior=CURRENT.get();var frame=new Frame(Objects.requireNonNull(context),prior,new int[1]);
        CURRENT.set(frame);
        return ()->{if(CURRENT.get()!=frame)throw new IllegalStateException("NATIVE_DAMAGE_SCOPE_CHANGED");
            if(prior==null)CURRENT.remove();else CURRENT.set(prior);};
    }
    /** Called by the native Gather event only while its own leaf synchronously invokes Damage. */
    public static void stamp(CommandBuffer<EntityStore> buffer,Ref<EntityStore> target,Damage damage){
        var frame=CURRENT.get();if(frame==null||damage==null||target==null)return;
        var context=frame.context();
        if(context.getCommandBuffer()!=buffer||context.getTargetEntity()!=target
                ||damage.getIfPresentMetaObject(Damage.INTERACTION_TYPE)==null
                ||!(damage.getSource() instanceof Damage.EntitySource source)
                ||source.getRef()!=context.getOwningEntity())return;
        int ordinal=frame.index()[0]++;
        if(ordinal<0||ordinal>32)throw new IllegalStateException("NATIVE_DAMAGE_COMPONENT_BOUNDS");
        NativeDamageReceiptHook.stamp(context,target,damage,ordinal);
    }
    @SuppressWarnings("unchecked")
    public static synchronized NativeDamageInteractions install(NativeDamageReceiptHook.Provider callback){
        Objects.requireNonNull(callback);verifyServer();
        var registry=Interaction.CODEC;
        if(registry.getClassFor("DamageEntity")!=DamageEntityInteraction.class)
            throw new IllegalStateException("NATIVE_DAMAGE_CODEC_OWNER_CHANGED");
        var prior=(com.hypixel.hytale.assetstore.codec.AssetBuilderCodec<String,Interaction>)
                registry.getCodecFor("DamageEntity");
        if(prior==null)throw new IllegalStateException("NATIVE_DAMAGE_CODEC_MISSING");
        var lease=NativeDamageReceiptHook.install(callback);
        try{
            registry.register("DamageEntity",ObservedDamage.class,ObservedDamage.CODEC);
            if(registry.getClassFor("DamageEntity")!=ObservedDamage.class)
                throw new IllegalStateException("NATIVE_DAMAGE_CODEC_NOT_INSTALLED");
            return new NativeDamageInteractions(DamageEntityInteraction.class,prior,lease);
        }catch(RuntimeException|Error failure){
            if(registry.getClassFor("DamageEntity")==ObservedDamage.class)registry.remove(ObservedDamage.class);
            registry.register("DamageEntity",DamageEntityInteraction.class,DamageEntityInteraction.CODEC);
            try{lease.close();}catch(Exception close){failure.addSuppressed(close);}throw failure;
        }
    }
    public boolean verified(){
        if(closed||Interaction.CODEC.getClassFor("DamageEntity")!=ObservedDamage.class
                ||!NativeDamageReceiptHook.installed()||Interaction.getAssetMap()==null)return false;
        return Interaction.getAssetMap().getAssetMap().values().stream()
                .noneMatch(leaf->leaf!=null&&leaf.getClass()==DamageEntityInteraction.class);
    }
    @Override public synchronized void close(){
        if(closed)return;
        var registry=Interaction.CODEC;
        if(registry.getClassFor("DamageEntity")!=ObservedDamage.class)
            throw new IllegalStateException("NATIVE_DAMAGE_CODEC_TEARDOWN_OWNER_CHANGED");
        registry.remove(ObservedDamage.class);
        registry.register("DamageEntity",originalClass,originalCodec);
        try{provider.close();}catch(Exception failure){throw new IllegalStateException("NATIVE_DAMAGE_PROVIDER_TEARDOWN",failure);}
        closed=true;
    }
    private static void verifyServer(){
        try{
            var location=DamageEntityInteraction.class.getProtectionDomain().getCodeSource().getLocation();
            Path source=Path.of(location.toURI());
            if(!Files.isRegularFile(source))throw new IllegalStateException("NATIVE_DAMAGE_SERVER_NOT_JAR");
            var digest=MessageDigest.getInstance("SHA-256");
            try(var stream=Files.newInputStream(source)){byte[] bytes=new byte[65536];int n;
                while((n=stream.read(bytes))!=-1)digest.update(bytes,0,n);}
            if(!SERVER_SHA256.equals(HexFormat.of().formatHex(digest.digest())))
                throw new IllegalStateException("NATIVE_DAMAGE_SERVER_IDENTITY_MISMATCH");
        }catch(Exception failure){throw new IllegalStateException("NATIVE_DAMAGE_SERVER_UNVERIFIED",failure);}
    }
    public static final class ObservedDamage extends DamageEntityInteraction {
        public static final BuilderCodec<ObservedDamage> CODEC=BuilderCodec.builder(
                ObservedDamage.class,ObservedDamage::new,DamageEntityInteraction.CODEC).build();
        @Override protected void tick0(boolean first,float dt,InteractionType type,InteractionContext context,
                CooldownHandler cooldowns){
            try(var ignored=enter(context)){super.tick0(first,dt,type,context,cooldowns);}
            catch(RuntimeException|Error failure){throw failure;}
            catch(Exception failure){throw new IllegalStateException("NATIVE_DAMAGE_SCOPE_TEARDOWN",failure);}
        }
    }
}
