package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import java.util.Map;

/** Pinned, finite humanoid animation packets only. Never execute native attack interaction roots. */
public final class NativeStrikeFeedback {
    private static final Map<String,String> PROFILES = Map.of("SWORD","Sword","LONGSWORD","Longsword",
            "DAGGER","Daggers","MACE","Mace","BATTLEAXE","RPG_Strike_Battleaxe","SPEAR","Spear");
    private NativeStrikeFeedback() { }
    public static double quickSlashInterval(String kind){
        return switch(kind){case "SWORD"->25.0/60/2.1;case "LONGSWORD"->25.0/60/1.6;case "DAGGER"->20.0/60/2.52;default->throw new IllegalArgumentException("QUICK_SLASH_WEAPON_KIND");};
    }
    public static void requireAssets() {
        var failures = new java.util.ArrayList<String>();
        var log=com.hypixel.hytale.logger.HytaleLogger.getLogger();
        for (String profile : java.util.stream.Stream.concat(PROFILES.values().stream(), java.util.stream.Stream.of("Staff")).distinct().toList()) {
            for(String action : profile.equals("Spear") ? java.util.List.of("Stab") : java.util.List.of("SwingLeft","SwingRight")) {
                var animations=ItemPlayerAnimations.getAssetMap().getAsset(profile);
                var animation=animations==null?null:animations.getAnimations().get(action);
                log.atInfo().log("RPG_STRIKE_ANIMATION family=%s requested=%s resolvedSet=%s resolvedAsset=%s speed=%s result=%s",
                        PROFILES.entrySet().stream().filter(e->e.getValue().equals(profile)).map(Map.Entry::getKey).findFirst().orElse(profile),action,
                        animations==null?"MISSING":animations.getId(),animation==null?"MISSING":animation.thirdPerson,animation==null?"N/A":animation.speed,animation==null?"MISSING":"FOUND");
                if(animation==null || animation.looping || animation.thirdPerson==null || animation.firstPerson==null
                        || !Float.isFinite(animation.speed) || animation.speed<=0)
                    failures.add(profile+"/"+action+": "+(animation==null?"missing":
                            "looping="+animation.looping+", speed="+animation.speed+", third="+animation.thirdPerson+", first="+animation.firstPerson));
            }
        }
        if(!failures.isEmpty())throw new IllegalStateException("STRIKE_FEEDBACK_ASSET_INVALID:"+String.join("; ",failures));
        for(String kind:java.util.List.of("SWORD","LONGSWORD","DAGGER"))for(String action:java.util.List.of("SwingLeft","SwingRight")){
            var base=ItemPlayerAnimations.getAssetMap().getAsset(PROFILES.get(kind)).getAnimations().get(action);
            var quick=ItemPlayerAnimations.getAssetMap().getAsset("RPG_QuickSlash_"+PROFILES.get(kind)).getAnimations().get(action);
            if(base==null||quick==null)throw new IllegalStateException("QUICK_SLASH_NATIVE_ANIMATION_CONTRACT:"+kind+"/"+action+" base="+(base==null?"MISSING":"FOUND")+" quick="+(quick==null?"MISSING":"FOUND"));
            var first=com.hypixel.hytale.server.core.asset.common.BlockyAnimationCache.getNow(quick.firstPerson);
            var third=com.hypixel.hytale.server.core.asset.common.BlockyAnimationCache.getNow(quick.thirdPerson);
            log.atInfo().log("RPG_QUICK_SLASH_ANIMATION family=%s requested=%s resolvedSet=%s resolvedAsset=%s baseSpeed=%s quickSpeed=%s interval=%s result=%s",
                    kind,action,"RPG_QuickSlash_"+PROFILES.get(kind),quick.thirdPerson,base.speed,quick.speed,
                    Math.max(first==null?0:first.getDurationSeconds(),third==null?0:third.getDurationSeconds())/quick.speed,
                    first!=null&&third!=null?"RESOLVED":"BLOCKY_ANIMATION_MISSING");
            if(!base.firstPerson.equals(quick.firstPerson)||!base.thirdPerson.equals(quick.thirdPerson)||quick.looping
                    ||Math.abs(quick.speed-base.speed*2.0)>1e-5||first==null||third==null
                    ||Math.abs(Math.max(first.getDurationSeconds(),third.getDurationSeconds())/quick.speed-quickSlashInterval(kind))>1e-6)
                throw new IllegalStateException("QUICK_SLASH_NATIVE_ANIMATION_CONTRACT:"+kind+"/"+action);
        }
    }
    static void play(Store<EntityStore> store, Ref<EntityStore> actor, SkillExecutionContext context, int hit) {
        var held=context.equipment().mainHand();
        String profile=context.profile().basePowerSource().equals("INNATE") ? "Staff"
                : held==null ? "Staff" : PROFILES.getOrDefault(held.weaponKind(),"Staff");
        String action=profile.equals("Spear")?"Stab":(hit%2==0?"SwingLeft":"SwingRight");
        if(context.profile().skillId().equals("quick_slash"))profile="RPG_QuickSlash_"+profile;
        // true includes the invoking player; the no-boolean overload excludes that player.
        AnimationUtils.playAnimation(actor,AnimationSlot.Action,profile,action,true,store);
    }
}
