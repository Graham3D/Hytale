package com.inigmasgames.hytalerpg.execution.summon;

import com.google.gson.Gson;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.inigmasgames.hytalerpg.gear.GearBindings;
import com.inigmasgames.hytalerpg.gear.GearCatalog;
import com.inigmasgames.hytalerpg.gear.GearInstance;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Build-audited iron material identity, checked again against loaded native assets. */
public final class IronSentinelMaterial {
    public record AuditedBase(String baseId,String nativeItemId,String model,String sourceAsset,String sourceSha256) {}
    private record Audit(int schemaVersion,String assetsSha256,List<AuditedBase> bases) {}
    private static final Map<String,AuditedBase> BASES=load();
    private static final GearBindings BINDINGS=new GearBindings();
    private IronSentinelMaterial() {}

    private static Map<String,AuditedBase> load(){
        try(var stream=IronSentinelMaterial.class.getResourceAsStream("/rpg/iron-sentinel/material-audit-v1.json")){
            if(stream==null)throw new IllegalStateException("Missing Iron Sentinel material audit");
            var audit=new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),Audit.class);
            if(audit.schemaVersion()!=1||audit.assetsSha256()==null||audit.assetsSha256().isBlank())
                throw new IllegalStateException("Invalid Iron Sentinel material audit");
            var rows=new HashMap<String,AuditedBase>();
            for(var row:audit.bases()){
                if(row.baseId()==null||row.nativeItemId()==null||row.model()==null||row.sourceAsset()==null||row.sourceSha256()==null
                        ||row.sourceSha256().length()!=64||rows.put(row.baseId(),row)!=null)
                    throw new IllegalStateException("Invalid audited iron base: "+row.baseId());
            }
            return Map.copyOf(rows);
        }catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
    }
    public static Set<String> auditedBases(){return BASES.keySet();}
    public static AuditedBase require(GearInstance item){
        Objects.requireNonNull(item);
        if(item.category()!=GearCatalog.Category.HELD&&item.category()!=GearCatalog.Category.ARMOR)
            throw new IllegalArgumentException("Sentinel requires a weapon or armor source");
        var row=BASES.get(item.baseId());
        if(row==null)throw new IllegalArgumentException("No audited iron material identity for "+item.baseId());
        var binding=BINDINGS.require(item.baseId());
        if(!binding.mapped()||!row.nativeItemId().equals(binding.nativeItemId())
                ||!row.sourceAsset().equals(binding.sourceAsset()))
            throw new IllegalArgumentException("Iron base/native binding mismatch");
        return row;
    }
    /** The manifest also records recipe and source hash evidence from the installed archive. */
    public static AuditedBase requireLoaded(GearInstance item){
        var row=require(item);
        var nativeItem=Item.getAssetMap().getAsset(row.nativeItemId());
        var carrier=Item.getAssetMap().getAsset(BINDINGS.require(item.baseId()).carrier(item.rarity()));
        if(nativeItem==null||carrier==null||!row.model().equals(nativeItem.getModel())
                ||!row.model().equals(carrier.getModel()))
            throw new IllegalArgumentException("Audited iron material is not loaded with the expected model");
        var recipes=new ArrayList<CraftingRecipe>();
        nativeItem.collectRecipesToGenerate(recipes);
        if(recipes.stream().noneMatch(recipe->recipe.getInput()!=null&&Arrays.stream(recipe.getInput())
                .anyMatch(input->"Ingredient_Bar_Iron".equals(input.getItemId()))))
            throw new IllegalArgumentException("Loaded native item has no audited iron-bar recipe");
        return row;
    }
}
