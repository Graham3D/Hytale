package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.progress.RewardIntent;
import java.util.*;

/** Deterministic child receipts beneath a writer-bound player action or projectile root. */
public final class PlayerOriginalHitIdentity {
    private PlayerOriginalHitIdentity(){}
    public static String skill(String root,String instance,String effect,int hit,UUID victim){
        if(root==null||root.isBlank()||instance==null||instance.isBlank()
                ||effect==null||effect.isBlank()||hit<0||victim==null)
            throw new IllegalArgumentException("PLAYER_SKILL_HIT_IDENTITY");
        return RewardIntent.digest(new com.google.gson.Gson().toJson(
                List.of("player.skill.original",root,instance,effect,hit,victim)));
    }
    public static String projectile(String root,String projectile,UUID victim){
        if(root==null||root.isBlank()||projectile==null||projectile.isBlank()||victim==null)
            throw new IllegalArgumentException("PLAYER_PROJECTILE_HIT_IDENTITY");
        return RewardIntent.digest(new com.google.gson.Gson().toJson(
                List.of("player.projectile.original",root,projectile,victim)));
    }
}
