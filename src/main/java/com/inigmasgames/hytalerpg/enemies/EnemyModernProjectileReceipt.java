package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.Gson;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** Launch identity on Hytale's original nonserialized modern projectile entity. */
public final class EnemyModernProjectileReceipt implements Component<EntityStore> {
    public record State(UUID projectile,UUID sourceEntity,String configId,String hitRoot,String missRoot,
            String nativeCause,float nativeMinimum,float nativeMaximum,EnemyOffenseSnapshot offense,
            double forwardX,double forwardZ) {
        public State {
            Objects.requireNonNull(projectile);Objects.requireNonNull(sourceEntity);Objects.requireNonNull(offense);
            for(var id:List.of(configId,hitRoot,missRoot,nativeCause))
                if(id.isBlank()||id.length()>256)throw new IllegalArgumentException("ENEMY_MODERN_PROJECTILE_ASSET_ID");
            if(!Float.isFinite(nativeMinimum)||!Float.isFinite(nativeMaximum)||nativeMinimum<=0
                    ||nativeMaximum<nativeMinimum||!offense.affixedVector().keySet().equals(Set.of(nativeCause)))
                throw new IllegalArgumentException("ENEMY_MODERN_PROJECTILE_DAMAGE_VECTOR");
            double length=Math.hypot(forwardX,forwardZ);
            if(!Double.isFinite(length)||Math.abs(length-1)>1e-5)
                throw new IllegalArgumentException("ENEMY_MODERN_PROJECTILE_DIRECTION");
        }
        public void require(UUID actualProjectile,UUID actualSource,UUID world,long generation,
                String revision,String actualConfig){
            if(!projectile.equals(actualProjectile)||!sourceEntity.equals(actualSource)
                    ||!offense.identity().worldId().equals(world)||offense.generation()!=generation
                    ||!offense.nativeBindingRevision().equals(revision)||!configId.equals(actualConfig))
                throw new IllegalStateException("ENEMY_MODERN_PROJECTILE_IDENTITY_CHANGED");
        }
    }
    private static final Gson JSON=new Gson();
    private static ComponentType<EntityStore,EnemyModernProjectileReceipt> type;
    private int version=1;
    private UUID projectile,sourceEntity;
    private String configId,hitRoot,missRoot,nativeCause,offenseJson;
    private float nativeMinimum,nativeMaximum;
    private double forwardX,forwardZ;
    private transient boolean impactStarted,damageClaimed;
    private transient boolean live;
    private transient String startedRoot;
    private final transient Object claimLock=new Object();
    public static final BuilderCodec<EnemyModernProjectileReceipt> CODEC=BuilderCodec.builder(
            EnemyModernProjectileReceipt.class,EnemyModernProjectileReceipt::new)
            .append(new KeyedCodec<>("Version",Codec.INTEGER),(p,v)->p.version=v,p->p.version).add()
            .append(new KeyedCodec<>("Projectile",Codec.UUID_BINARY),(p,v)->p.projectile=v,p->p.projectile).add()
            .append(new KeyedCodec<>("SourceEntity",Codec.UUID_BINARY),(p,v)->p.sourceEntity=v,p->p.sourceEntity).add()
            .append(new KeyedCodec<>("Config",Codec.STRING),(p,v)->p.configId=v,p->p.configId).add()
            .append(new KeyedCodec<>("HitRoot",Codec.STRING),(p,v)->p.hitRoot=v,p->p.hitRoot).add()
            .append(new KeyedCodec<>("MissRoot",Codec.STRING),(p,v)->p.missRoot=v,p->p.missRoot).add()
            .append(new KeyedCodec<>("NativeCause",Codec.STRING),(p,v)->p.nativeCause=v,p->p.nativeCause).add()
            .append(new KeyedCodec<>("NativeMinimum",Codec.FLOAT),(p,v)->p.nativeMinimum=v,p->p.nativeMinimum).add()
            .append(new KeyedCodec<>("NativeMaximum",Codec.FLOAT),(p,v)->p.nativeMaximum=v,p->p.nativeMaximum).add()
            .append(new KeyedCodec<>("Offense",Codec.STRING),(p,v)->p.offenseJson=v,p->p.offenseJson).add()
            .append(new KeyedCodec<>("ForwardX",Codec.DOUBLE),(p,v)->p.forwardX=v,p->p.forwardX).add()
            .append(new KeyedCodec<>("ForwardZ",Codec.DOUBLE),(p,v)->p.forwardZ=v,p->p.forwardZ).add()
            .afterDecode(EnemyModernProjectileReceipt::validateDecoded).build();
    public EnemyModernProjectileReceipt(){}
    public EnemyModernProjectileReceipt(State state){
        projectile=state.projectile();sourceEntity=state.sourceEntity();configId=state.configId();
        hitRoot=state.hitRoot();missRoot=state.missRoot();nativeCause=state.nativeCause();
        nativeMinimum=state.nativeMinimum();nativeMaximum=state.nativeMaximum();
        offenseJson=JSON.toJson(state.offense());forwardX=state.forwardX();forwardZ=state.forwardZ();
        live=true;
    }
    private void validateDecoded(){
        if(version==1&&projectile==null&&sourceEntity==null&&configId==null&&offenseJson==null)return;
        state();
    }
    public State state(){
        if(version!=1||offenseJson==null||offenseJson.length()>16384)
            throw new IllegalStateException("ENEMY_MODERN_PROJECTILE_RECEIPT_VERSION_OR_SIZE");
        EnemyOffenseSnapshot decoded;
        try{
            var raw=JSON.fromJson(offenseJson,EnemyOffenseSnapshot.class);
            decoded=new EnemyOffenseSnapshot(raw.identity(),raw.generation(),raw.nativeBindingRevision(),
                    raw.channelMappingRevision(),raw.balanceRevision(),raw.procCoefficient(),raw.sourceVector(),
                    raw.sourcePower(),raw.affixedVector(),raw.spectralChannel(),raw.directWeakening());
        }catch(RuntimeException invalid){throw new IllegalStateException("ENEMY_MODERN_PROJECTILE_OFFENSE_INVALID",invalid);}
        return new State(projectile,sourceEntity,configId,hitRoot,missRoot,nativeCause,nativeMinimum,nativeMaximum,
                decoded,forwardX,forwardZ);
    }
    /** Called at native impact root start, before any ApplyEffect or DamageEntity leaf. */
    public boolean beginImpact(String root){
        synchronized(claimLock){
            if(!live)throw new IllegalStateException("ENEMY_MODERN_PROJECTILE_RESTART_ORPHAN");
            if(!state().hitRoot().equals(root)&&!state().missRoot().equals(root))
                throw new IllegalStateException("ENEMY_MODERN_PROJECTILE_IMPACT_ROOT_CHANGED");
            if(impactStarted)return false;
            impactStarted=true;startedRoot=root;return true;
        }
    }
    /** Exactly one native damage packet may enter Health for the one started hit root. */
    public boolean claimDamage(String root){
        synchronized(claimLock){
            if(!live)throw new IllegalStateException("ENEMY_MODERN_PROJECTILE_RESTART_ORPHAN");
            if(!impactStarted||!Objects.equals(startedRoot,state().hitRoot()))
                throw new IllegalStateException("ENEMY_MODERN_PROJECTILE_HIT_NOT_STARTED");
            if(!startedRoot.equals(root)||damageClaimed)return false;
            damageClaimed=true;return true;
        }
    }
    public static void bind(ComponentType<EntityStore,EnemyModernProjectileReceipt> value){type=Objects.requireNonNull(value);}
    public static ComponentType<EntityStore,EnemyModernProjectileReceipt> getComponentType(){return type;}
    @Override public EnemyModernProjectileReceipt clone(){
        synchronized(claimLock){
            var result=new EnemyModernProjectileReceipt(state());
            result.impactStarted=impactStarted;result.damageClaimed=damageClaimed;result.startedRoot=startedRoot;
            result.live=live;
            return result;
        }
    }
}
