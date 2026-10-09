package com.inigmasgames.hytalerpg.execution.summon;

import com.google.gson.JsonParser;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.assetstore.RawAsset;
import com.hypixel.hytale.assetstore.codec.ContainedAssetCodec;
import com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/** Publishes a per-accepted-Sentinel swing presentation using the installed animation codec.
 * The actual damage claim remains the RPG lease scheduler, including source WA-008 and owner WA-117. */
public final class NativeSentinelActionAssets {
    private static final Set<String> published=new HashSet<>();
    private static final int MAX_VARIANTS=4096;
    private NativeSentinelActionAssets() {}

    public static double rate(SummonRegistry.Lease lease){
        if(!lease.ironSentinel())throw new IllegalArgumentException("Sentinel animation requires bound actor");
        return lease.sentinelStats().attackRateMultiplier()*lease.sentinelStats().attackInterval()/lease.interval();
    }
    public static String id(SummonRegistry.Lease lease){
        double rate=rate(lease);
        if(!Double.isFinite(rate)||rate<=0)throw new IllegalArgumentException("Invalid Sentinel action rate");
        return rate==1?"":"RPG_Sentinel_Swing_"+Long.toHexString(Double.doubleToLongBits(rate));
    }
    public static synchronized void publish(SummonRegistry.Lease lease){
        String id=id(lease);if(id.isEmpty()||ItemPlayerAnimations.getAssetMap().getAsset(id)!=null)return;
        if(published.size()>=MAX_VARIANTS)throw new IllegalStateException("Sentinel action variant capacity exceeded");
        var raw=new RawAsset<>(Path.of("Server","Item","Animations","RPG","Summon",id+".json"),
                id,null,0,render(rate(lease)).toCharArray(),null,ContainedAssetCodec.Mode.NONE);
        var result=ItemPlayerAnimations.getAssetStore().loadBuffersWithKeys("HyArpg",List.of(raw),AssetUpdateQuery.DEFAULT,true);
        if(result.hasFailed()||ItemPlayerAnimations.getAssetMap().getAsset(id)==null)
            throw new IllegalStateException("Sentinel action publication failed: "+result.getFailedToLoadKeys());
        published.add(id);
    }
    public static ItemPlayerAnimations animation(SummonRegistry.Lease lease){
        String id=id(lease);if(id.isEmpty())return null;
        var asset=ItemPlayerAnimations.getAssetMap().getAsset(id);
        if(asset==null)throw new IllegalStateException("Sentinel animation variant missing: "+id);
        return asset;
    }
    public static String render(double rate){
        if(!Double.isFinite(rate)||rate<=0)throw new IllegalArgumentException("Invalid Sentinel action rate");
        var object=template();
        object.getAsJsonObject("Animations").getAsJsonObject("SwingLeft")
                .addProperty("Speed",Math.round(1.5*rate*1_000_000d)/1_000_000d);
        return object.toString();
    }
    private static com.google.gson.JsonObject template(){
        try(var input=NativeSentinelActionAssets.class.getResourceAsStream(
                "/Server/Item/Animations/RPG/Summon/RPG_Sentinel_Swing_Template.json")){
            if(input==null)throw new IllegalStateException("Sentinel action template missing");
            return JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}
    }
}
