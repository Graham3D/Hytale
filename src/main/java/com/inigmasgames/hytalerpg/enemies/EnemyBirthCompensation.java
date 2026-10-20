package com.inigmasgames.hytalerpg.enemies;

import java.util.*;

/** Durable whole-group decision to restore the frozen original native spawn contexts. */
public record EnemyBirthCompensation(UUID world,UUID encounter,long generation,String seed){
    public EnemyBirthCompensation{
        Objects.requireNonNull(world);Objects.requireNonNull(encounter);
        if(generation<0||seed==null||seed.isBlank()||seed.length()>512)
            throw new IllegalArgumentException("ENEMY_BIRTH_COMPENSATION_IDENTITY");
    }
    public static EnemyBirthCompensation of(EnemyBirthRoot root){
        return new EnemyBirthCompensation(root.world(),root.encounter(),root.plan().generation(),root.plan().seed());
    }
}
