package com.inigmasgames.hytalerpg.execution.hytale;

import com.google.gson.*;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.*;
import com.inigmasgames.hytalerpg.combat.power.*;
import com.inigmasgames.hytalerpg.execution.SkillExecutionPort;
import java.util.*;

/** Decodes the actual resolved native asset graph, including item Replace variables and inheritance. */
public final class NativeWeaponLightProfiles {
    private NativeWeaponLightProfiles(){}
    public static WeaponLightAttackProfile capture(SkillExecutionPort.Equipment equipment){
        if(equipment==null||equipment.mainHand()==null)throw new IllegalArgumentException("UNSUPPORTED_LIGHT_ATTACK_WEAPON_MISSING");
        var held=equipment.mainHand();var item=Item.getAssetMap().getAsset(held.itemId());
        if(item==null)throw new IllegalArgumentException("UNSUPPORTED_LIGHT_ATTACK_ITEM_MISSING");
        String root=item.getInteractions().get(InteractionType.Primary);
        if(root==null)throw new IllegalArgumentException("UNSUPPORTED_LIGHT_ATTACK_PRIMARY_MISSING");
        var graph=new WeaponLightAttackProfileResolver.Assets(){
            public JsonObject root(String id){var value=RootInteraction.getAssetMap().getAsset(id);return value==null?null:
                JsonParser.parseString(RootInteraction.CODEC.encode(value,new ExtraInfo()).asDocument().toJson()).getAsJsonObject();}
            public JsonObject interaction(String id){var value=Interaction.getAssetMap().getAsset(id);return value==null?null:
                JsonParser.parseString(Interaction.CODEC.encode(value,new ExtraInfo()).asDocument().toJson()).getAsJsonObject();}
        };
        var profile=new WeaponLightAttackProfileResolver(graph,item.getInteractionVars()).resolve(held.itemId(),held.weaponKind(),root,"native-0.7-pre2/"+root);
        for(var component:profile.components())if(com.hypixel.hytale.server.core.modules.entity.damage.DamageCause.getAssetMap()
                .getAsset(nativeCause(component.channel()))==null)throw new IllegalArgumentException("UNSUPPORTED_LIGHT_ATTACK_DAMAGE_CHANNEL");
        return profile;
    }
    public static String nativeCause(String channel){return switch(channel){case "PHYSICAL"->"Physical";case "FIRE"->"Fire";case "COLD"->"Ice";
        default->channel.substring(0,1)+channel.substring(1).toLowerCase(Locale.ROOT);};}
    public static void audit(){
        var supported=new TreeMap<String,WeaponLightAttackProfile>();var unsupported=new TreeMap<String,String>();
        for(var entry:NativeItemPowerRegistry.loadProduction().all())if(Set.of("SWORD","LONGSWORD","DAGGER").contains(entry.kind()))try{
            supported.put(entry.itemId(),capture(new SkillExecutionPort.Equipment(new SkillExecutionPort.Item(entry.itemId(),entry.kind(),entry.descriptor()),null)));
        }catch(RuntimeException failure){unsupported.put(entry.itemId(),failure.getClass().getSimpleName()+":"+failure.getMessage());}
        var details=Map.of("supported",supported,"unsupported",unsupported,"connectedProof",false);
        com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log("RPG_LIGHT_ATTACK_PROFILES %s",new Gson().toJson(details));
        for(String kind:List.of("SWORD","LONGSWORD","DAGGER"))if(supported.values().stream().noneMatch(p->p.weaponClass().equals(kind)))
            throw new IllegalStateException("LIGHT_PROFILE_CLASS_UNSUPPORTED:"+kind);
        if(!supported.containsKey("Weapon_Longsword_Flame"))throw new IllegalStateException("LIGHT_PROFILE_FLAME_QA_CASE_UNSUPPORTED");
        // Concrete installed fixtures: validate resolved inheritance, not just nonempty JSON traversal.
        auditReference(supported.get("Weapon_Sword_Iron"),"PHYSICAL",10,.334,.117);
        auditReference(supported.get("Weapon_Daggers_Iron"),"PHYSICAL",6,.207,.069);
        auditReference(supported.get("Weapon_Longsword_Flame"),"FIRE",31,.520,.229);
        com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log("RPG_LIGHT_ATTACK_PROFILES result=PASS supported=%s unsupported=%s connectedProof=false",supported.size(),unsupported.size());
    }
    private static void auditReference(WeaponLightAttackProfile profile,String channel,double mean,double duration,double contact){
        if(profile==null||profile.components().size()!=1||!profile.components().getFirst().channel().equals(channel)
                ||Math.abs(profile.normalDuration()-duration)>1e-6||Math.abs(profile.contactTime()-contact)>1e-6
                ||Math.abs(profile.sample(.375,()->.5,true).getFirst().sourceAmount()-mean*.375)>1e-6)
            throw new IllegalStateException("NATIVE_LIGHT_REFERENCE_MISMATCH");
    }
}
