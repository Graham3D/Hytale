package com.inigmasgames.hytalerpg.input;

import com.google.gson.*;
import com.hypixel.hytale.server.core.asset.type.particle.config.*;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;

/** Native asset contract: color-only derivatives and stock loop routed through cosmetic effects. */
public final class NativeHealingPolishAudit {
    public static void requireAssets(){
        var gson=new Gson();
        String[][] pairs={{"RPG_Heal_World_Sparks","RPG_Heal_Red_Sparks"},{"RPG_Heal_World_Glow","RPG_Heal_Red_Glow"},
            {"RPG_Heal_World_Plus","RPG_Heal_Red_Plus"},{"Heal2","RPG_Heal_Red_Heal2"},{"Heal_Rays","RPG_Heal_Red_Heal_Rays"},
            {"Staff_Bronze_Air","RPG_Heal_Red_Staff_Air"},{"Staff_Bronze_Sparks","RPG_Heal_Red_Staff_Sparks"}};
        for(var pair:pairs){
            var original=ParticleSpawner.getAssetMap().getAsset(pair[0]);var red=ParticleSpawner.getAssetMap().getAsset(pair[1]);
            if(original==null||red==null)throw new IllegalStateException("HEAL_RED_ASSET:"+pair[1]);
            var a=gson.toJsonTree(original.toPacket());var b=gson.toJsonTree(red.toPacket());
            stripColors(a);stripColors(b);
            // Native packet IDs differ, all non-color spawner behavior must match.
            a.getAsJsonObject().remove("id");b.getAsJsonObject().remove("id");
            if(!a.equals(b))throw new IllegalStateException("HEAL_RED_NONCOLOR_CHANGE:"+pair[1]);
        }
        String soundId="SFX_Deployable_Totem_Heal_Effect_Local";
        var sound=SoundEvent.getAssetMap().getAsset(soundId);
        if(sound==null||sound.getLayers().length==0||java.util.Arrays.stream(sound.getLayers()).anyMatch(l->!l.isLooping()))throw new IllegalStateException("HEAL_AUDIO_NOT_LOOPED");
        for(int i=0;i<8;i++){
            var effect=EntityEffect.getAssetMap().getAsset("RPG_Healing_Audio_"+i);
            if(effect==null||!effect.isInfinite()||effect.getDamageCalculator()!=null
                    ||effect.getStatModifiers()!=null&&!effect.getStatModifiers().isEmpty()
                    ||effect.getApplicationEffects().toPacket().soundEventIndexLocal!=SoundEvent.getAssetMap().getIndex(soundId))
                throw new IllegalStateException("HEAL_AUDIO_EFFECT:"+i);
        }
        com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log("RPG_HEAL_POLISH_ASSETS revision=R032-AP result=PASS colorOnly=true nativeRecipient=true stockLoop=true connectedProof=false");
    }
    private static void stripColors(JsonElement node){
        if(node.isJsonObject()){var object=node.getAsJsonObject();object.remove("color");for(var value:object.entrySet())stripColors(value.getValue());}
        else if(node.isJsonArray())for(var value:node.getAsJsonArray())stripColors(value);
    }
    private NativeHealingPolishAudit(){}
}
