package com.inigmasgames.hytalerpg.execution.hytale;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
/** Resolved-asset check only, never connected presentation proof. */
public final class NativeHitProcAssets {
    private NativeHitProcAssets(){}
    public static void requireAssets(){
        var effect=EntityEffect.getAssetMap().getAsset("RPG_Bleed_Visual");
        if(effect==null||effect.getApplicationEffects()==null||effect.getDamageCalculator()!=null)throw new IllegalStateException("BLEED_VISUAL_ASSET_MISSING_OR_NATIVE_DAMAGE");
        var app=effect.getApplicationEffects().toPacket();
        if(app.movementEffects!=null||app.abilityEffects!=null||app.horizontalSpeedMultiplier!=1)throw new IllegalStateException("BLEED_VISUAL_MUTATES_NATIVE_GAMEPLAY");
    }
}
