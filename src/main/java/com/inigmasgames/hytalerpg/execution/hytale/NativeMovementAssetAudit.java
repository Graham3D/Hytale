package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.WieldingInteraction;
import com.inigmasgames.hytalerpg.execution.Stage04SkillProfiles;
import java.util.*;

/** Resolves a shipped native control without starting it or granting an unverified held-input route. */
public final class NativeMovementAssetAudit {
    private NativeMovementAssetAudit(){}
    public static Map<String,Object> requireAssets(Stage04SkillProfiles profiles){
        var interaction=Interaction.getAssetMap().getAsset("Weapon_Sword_Secondary_Guard_Wield");
        if(!(interaction instanceof WieldingInteraction wield)||wield.getAngledWielding()==null||wield.getStaminaCost()==null)
            throw new IllegalStateException("NATIVE_GUARD_CONTROL_ASSET_MISSING");
        double angle=wield.getAngledWielding().getAngleRad(),distance=wield.getAngledWielding().getAngleDistanceRad();
        if(Math.abs(angle)>1e-6||Math.abs(distance-Math.PI/2)>1e-6)throw new IllegalStateException("NATIVE_GUARD_CONTROL_ARC_CHANGED");
        // Installed StaminaCost exposes only a mutating-context calculation; read pinned fields for audit, never apply them.
        float cost;String type;
        try{var value=wield.getStaminaCost().getClass().getDeclaredField("value");value.setAccessible(true);cost=value.getFloat(wield.getStaminaCost());
            var kind=wield.getStaminaCost().getClass().getDeclaredField("costType");kind.setAccessible(true);type=String.valueOf(kind.get(wield.getStaminaCost()));}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("NATIVE_GUARD_COST_AUDIT_UNAVAILABLE",failure);}
        if(Math.abs(cost-7)>1e-6||!type.equalsIgnoreCase("Damage"))throw new IllegalStateException("NATIVE_GUARD_BASE_DRAIN_CHANGED");
        var guard=profiles.require("guard");
        if(!guard.activationGate().equals("NATIVE_GUARD_HELD_ITEM_RELEASE_ROUTE_UNVERIFIED")||guard.resourceCost()!=0||guard.support()!=null)
            throw new IllegalStateException("NATIVE_GUARD_ROUTE_GATE_WEAKENED");
        return Map.of("nativeControl","Weapon_Sword_Secondary_Guard_Wield","operation",interaction.getClass().getSimpleName(),
                "angleRadians",angle,"angleDistanceRadians",distance,"baseDrain",cost,"costType",type,
                "activationGate",guard.activationGate(),"connectedProof",false);
    }
}
