package com.inigmasgames.hytalerpg.difficulty;

import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry;
import java.util.*;

/** Native save projection: preserves raw HP across native role/stat initialization on LOAD.
 * The encounter journal still owns the profile/rewards. This component cannot mint an encounter.
 * Store.copySerializableEntity asserts its world thread and clones components for the save worker. */
public final class DifficultyHealthProjection implements Component<EntityStore> {
    private static ComponentType<EntityStore,DifficultyHealthProjection> type;
    private UUID world,enemy;private String profile="";private Double savedHealth;
    private transient EntityStatMap live;
    public static final BuilderCodec<DifficultyHealthProjection> CODEC=BuilderCodec.builder(DifficultyHealthProjection.class,DifficultyHealthProjection::new)
            .append(new KeyedCodec<>("World",Codec.UUID_BINARY),(p,v)->p.world=v,p->p.world).add()
            .append(new KeyedCodec<>("Enemy",Codec.UUID_BINARY),(p,v)->p.enemy=v,p->p.enemy).add()
            .append(new KeyedCodec<>("Profile",Codec.STRING),(p,v)->p.profile=v,p->p.profile).add()
            .append(new KeyedCodec<>("Health",Codec.DOUBLE),(p,v)->p.savedHealth=v==null?null:checked(v),p->p.live==null?p.savedHealth:Double.valueOf(p.currentHealth())).add().build();
    public DifficultyHealthProjection(){}
    public DifficultyHealthProjection(UUID world,UUID enemy,String profile,double health){this.world=Objects.requireNonNull(world);this.enemy=Objects.requireNonNull(enemy);this.profile=Objects.requireNonNull(profile);savedHealth=checked(health);}
    public static void bind(ComponentType<EntityStore,DifficultyHealthProjection> value){type=value;}
    public static ComponentType<EntityStore,DifficultyHealthProjection> getComponentType(){return type;}
    private static double checked(double health){if(!Double.isFinite(health)||health<0||health>Float.MAX_VALUE)throw new IllegalArgumentException("INVALID_SAVED_DIFFICULTY_HEALTH");return health;}
    private double currentHealth(){if(live==null&&savedHealth==null)throw new IllegalStateException("MISSING_SAVED_DIFFICULTY_HEALTH");return checked(live==null?savedHealth:live.get(DefaultEntityStatTypes.getHealth()).get());}
    public double healthFor(EnemyRewardRegistry.Spawn spawn){
        if(!Objects.equals(world,spawn.world())||!Objects.equals(enemy,spawn.enemy())||!profile.equals(spawn.registryProfile())||spawn.combat()==null)
            throw new IllegalStateException("SAVED_HEALTH_PROFILE_MISMATCH");return currentHealth();
    }
    public void follow(EntityStatMap stats){live=Objects.requireNonNull(stats);}
    @Override public DifficultyHealthProjection clone(){return new DifficultyHealthProjection(world,enemy,profile,currentHealth());}
}
