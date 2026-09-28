package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import org.bson.BsonString;
import java.util.*;

/** Item identity and frozen values live in native inventory metadata, never a second inventory file. */
public final class GearNativeItems {
    public static final String KEY="RpgGearV1";
    private static final GearBindings BINDINGS=new GearBindings();
    private static final Map<String,String> NATIVE_IDS=nativeIds();
    private static Map<String,String> nativeIds() {
        var map=new HashMap<String,String>();
        for(var binding:BINDINGS.all()) if(binding.mapped())
            for(var rarity:GearRarity.values()) map.put(binding.carrier(rarity),binding.nativeItemId());
        return Map.copyOf(map);
    }
    private static volatile HytaleGearEquipment equipment;
    // Immutable decode cache only. Custody, carrier identity and validity are checked again on every read.
    private static final Map<String,GearInstance> DECODED=new LinkedHashMap<>(64,.75f,true);
    private static synchronized GearInstance decode(String json){
        var cached=DECODED.get(json);if(cached!=null)return cached;
        var item=GearInstance.fromJson(json);
        if(DECODED.size()>=512)DECODED.remove(DECODED.keySet().iterator().next());
        DECODED.put(json,item);return item;
    }
    private static volatile java.util.function.Predicate<GearInstance> authority=GearInstance::qaOnly;
    public static void bindAuthority(java.util.function.Predicate<GearInstance> check){authority=Objects.requireNonNull(check);}
    public static void bind(HytaleGearEquipment owner) { equipment=owner; }
    public static GearAffixRuntime.Effects effects(com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> actor,
            com.hypixel.hytale.component.ComponentAccessor<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> accessor) {
        return equipment==null?GearAffixRuntime.Effects.NONE:equipment.effects(actor,accessor);
    }
    public static boolean canUse(ItemStack stack,com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> actor,
                                 com.hypixel.hytale.component.ComponentAccessor<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> accessor) {
        return equipment!=null && equipment.canUse(stack,actor,accessor);
    }
    public static String nativeId(String managedId) {
        return NATIVE_IDS.getOrDefault(managedId,managedId);
    }
    private GearNativeItems() {}
    public static boolean managed(ItemStack stack) {
        return stack!=null && (stack.getItemId().startsWith("RPG_Gear_") || stack.getMetadata()!=null && stack.getMetadata().containsKey(KEY));
    }
    /** Malformed managed stacks throw rather than falling back to native power or protection. */
    public static GearInstance read(ItemStack stack) {
        if(!managed(stack)) return null;
        if(stack.getQuantity()!=1 || stack.getMetadata()==null || !stack.getMetadata().containsKey(KEY))
            throw new IllegalArgumentException("Managed gear requires one frozen instance");
        var gear=decode(stack.getMetadata().getString(KEY).getValue());
        if(stack.getMetadata().containsKey(HytaleGearLoot.PROJECTION)||!authority.test(gear))throw new IllegalArgumentException("Gear custody pending or consumed");
        var binding=BINDINGS.require(gear.baseId());
        if(!binding.mapped() || !stack.getItemId().equals(binding.carrier(gear.rarity())))
            throw new IllegalArgumentException("Gear instance/native carrier mismatch");
        return gear;
    }
    public static ItemStack create(GearInstance gear,int actorLevel,Map<RpgAttribute,Integer> permanent) {
        var binding=BINDINGS.require(gear.baseId());
        if(!binding.mapped()) throw new IllegalArgumentException(binding.reason());
        if(Item.getAssetMap().getAsset(binding.carrier(gear.rarity()))==null) throw new IllegalStateException("Managed native item not loaded");
        int quality=ItemQuality.getAssetMap().getIndex(gear.rarity().qualityAsset());
        if(quality<0) throw new IllegalStateException("Managed quality not loaded");
        var stack=new ItemStack(binding.carrier(gear.rarity()),1).withQuality(quality).withMetadata(KEY,new BsonString(gear.toJson()));
        return present(stack,gear,actorLevel,permanent);
    }
    public static ItemStack present(ItemStack stack,GearInstance gear,int level,Map<RpgAttribute,Integer> permanent) {
        var description=Message.empty();
        var lines=GearTooltip.describe(gear,level,permanent);
        for(int i=1;i<lines.size();i++) {
            var line=lines.get(i);var message=Message.raw((i==1?"":line.breakBefore()?"\n\n":"\n")+line.text());
            if(line.color()!=null) message.color(line.color());
            // Reuse the existing RpgCharacter.ui #Status feedback color.
            if(line.style()==GearTooltip.Style.ERROR) message.color("#e4b861");
            description.insert(message);
        }
        var presented=stack.withMetadata(ItemDisplayMetadata.KEYED_CODEC,
                new ItemDisplayMetadata(Message.raw(lines.getFirst().text()).color(gear.rarity().color).bold(true),description));
        int quality=ItemQuality.getAssetMap().getIndex(gear.rarity().qualityAsset());
        return quality<0?presented:presented.withQuality(quality);
    }
}
