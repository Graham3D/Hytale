package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.Gson;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** Exact accepted root and frozen offense on Hytale's original projectile holder. No damage owner. */
public final class EnemyProjectileReceipt implements Component<EntityStore> {
    public record State(UUID projectile,UUID sourceEntity,String nativeProjectileAsset,int nativeBaseDamage,
            EnemyOffenseSnapshot offense,double forwardX,double forwardZ){
        public State{
            Objects.requireNonNull(projectile);Objects.requireNonNull(sourceEntity);Objects.requireNonNull(offense);
            if(nativeProjectileAsset==null||nativeProjectileAsset.isBlank()||nativeProjectileAsset.length()>256)
                throw new IllegalArgumentException("ENEMY_PROJECTILE_ASSET_ID");
            if(nativeBaseDamage<=0)throw new IllegalArgumentException("ENEMY_PROJECTILE_NATIVE_BASE_DAMAGE");
            double length=Math.hypot(forwardX,forwardZ);
            if(!Double.isFinite(length)||Math.abs(length-1)>1e-5)
                throw new IllegalArgumentException("ENEMY_PROJECTILE_LAUNCH_DIRECTION");
        }
        public void require(UUID actualProjectile,UUID actualSource,UUID world,long generation,String bindingRevision,
                String projectileAsset){
            if(!projectile.equals(actualProjectile)||!sourceEntity.equals(actualSource)
                    ||!offense.identity().worldId().equals(world)||offense.generation()!=generation
                    ||!offense.nativeBindingRevision().equals(bindingRevision)
                    ||!nativeProjectileAsset.equals(projectileAsset))
                throw new IllegalStateException("ENEMY_PROJECTILE_RECEIPT_IDENTITY_MISMATCH");
        }
    }
    private static final Gson JSON=new Gson();
    private static ComponentType<EntityStore,EnemyProjectileReceipt> type;
    private int version=1;
    private UUID projectile,sourceEntity;
    private String nativeProjectileAsset,offenseJson;
    private int nativeBaseDamage;
    private double forwardX,forwardZ;
    public static final BuilderCodec<EnemyProjectileReceipt> CODEC=BuilderCodec.builder(
            EnemyProjectileReceipt.class,EnemyProjectileReceipt::new)
            .append(new KeyedCodec<>("Version",Codec.INTEGER),(p,v)->p.version=v,p->p.version).add()
            .append(new KeyedCodec<>("Projectile",Codec.UUID_BINARY),(p,v)->p.projectile=v,p->p.projectile).add()
            .append(new KeyedCodec<>("SourceEntity",Codec.UUID_BINARY),(p,v)->p.sourceEntity=v,p->p.sourceEntity).add()
            .append(new KeyedCodec<>("NativeProjectileAsset",Codec.STRING),(p,v)->p.nativeProjectileAsset=v,p->p.nativeProjectileAsset).add()
            .append(new KeyedCodec<>("NativeBaseDamage",Codec.INTEGER),(p,v)->p.nativeBaseDamage=v,p->p.nativeBaseDamage).add()
            .append(new KeyedCodec<>("Offense",Codec.STRING),(p,v)->p.offenseJson=v,p->p.offenseJson).add()
            .append(new KeyedCodec<>("ForwardX",Codec.DOUBLE),(p,v)->p.forwardX=v,p->p.forwardX).add()
            .append(new KeyedCodec<>("ForwardZ",Codec.DOUBLE),(p,v)->p.forwardZ=v,p->p.forwardZ).add()
            .afterDecode(EnemyProjectileReceipt::validateDecoded).build();
    public EnemyProjectileReceipt(){}
    public EnemyProjectileReceipt(State state){
        projectile=state.projectile();sourceEntity=state.sourceEntity();nativeProjectileAsset=state.nativeProjectileAsset();
        nativeBaseDamage=state.nativeBaseDamage();
        offenseJson=JSON.toJson(state.offense());forwardX=state.forwardX();forwardZ=state.forwardZ();
    }
    private void validateDecoded(){
        if(version==1&&projectile==null&&sourceEntity==null&&nativeProjectileAsset==null
                &&offenseJson==null&&nativeBaseDamage==0&&forwardX==0&&forwardZ==0)return;
        state();
    }
    public State state(){
        if(version!=1||offenseJson==null||offenseJson.length()>16384)
            throw new IllegalStateException("ENEMY_PROJECTILE_RECEIPT_VERSION_OR_SIZE");
        EnemyOffenseSnapshot decoded;
        try{
            var raw=JSON.fromJson(offenseJson,EnemyOffenseSnapshot.class);
            decoded=new EnemyOffenseSnapshot(raw.identity(),raw.generation(),raw.nativeBindingRevision(),
                    raw.channelMappingRevision(),raw.balanceRevision(),raw.procCoefficient(),raw.sourceVector(),
                    raw.sourcePower(),raw.affixedVector(),raw.spectralChannel(),raw.directWeakening());
        }catch(RuntimeException invalid){throw new IllegalStateException("ENEMY_PROJECTILE_RECEIPT_OFFENSE_INVALID",invalid);}
        return new State(projectile,sourceEntity,nativeProjectileAsset,nativeBaseDamage,decoded,forwardX,forwardZ);
    }
    public static void bind(ComponentType<EntityStore,EnemyProjectileReceipt> value){type=Objects.requireNonNull(value);}
    public static ComponentType<EntityStore,EnemyProjectileReceipt> getComponentType(){return type;}
    @Override public EnemyProjectileReceipt clone(){return new EnemyProjectileReceipt(state());}
}
