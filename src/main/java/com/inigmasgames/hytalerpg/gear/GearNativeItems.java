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
    public static com.inigmasgames.hytalerpg.combat.attribute.DerivedStats attributeDerived(
            com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> actor,
            com.hypixel.hytale.component.ComponentAccessor<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> accessor) {
        if(equipment==null)throw new IllegalStateException("Equipment owner is not installed");
        return equipment.attributeDerived(actor,accessor);
    }
    public static GearAffixRuntime.Effects effects(com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> actor,
            com.hypixel.hytale.component.ComponentAccessor<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> accessor) {
        if(actor==null||!actor.isValid()||accessor.getComponent(actor,com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType())==null)
            return GearAffixRuntime.Effects.NONE;
        return equipment==null?GearAffixRuntime.Effects.NONE:equipment.effects(actor,accessor);
    }
    @FunctionalInterface public interface NativeRecipientEffects {
        GearEffectSnapshot effects(com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store,
                                   com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> target);
    }
    private static volatile NativeRecipientEffects nativeRecipientEffects=(store,target)->GearEffectSnapshot.EMPTY;
    public static void bindRecipientEffects(NativeRecipientEffects selector){
        nativeRecipientEffects=Objects.requireNonNull(selector);
    }
    /** Recipient gear only: players retain the existing equipment owner; native actors need a live lease. */
    public static GearEffectSnapshot recipientEffects(
            com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> target,
            com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store){
        if(target==null||!target.isValid()||target.getStore()!=store)return GearEffectSnapshot.EMPTY;
        if(store.getComponent(target,com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType())!=null)
            return effects(target,store).snapshot();
        var source=nativeRecipientEffects.effects(store,target);
        return source==null?GearEffectSnapshot.EMPTY:source;
    }
    public static GearEffectSnapshot recipientEffects(
            com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> target,
            com.hypixel.hytale.component.CommandBuffer<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> buffer){
        if(target==null||!target.isValid()||target.getStore()!=buffer.getStore())return GearEffectSnapshot.EMPTY;
        if(buffer.getComponent(target,com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType())!=null)
            return effects(target,buffer).snapshot();
        var source=nativeRecipientEffects.effects(buffer.getStore(),target);
        return source==null?GearEffectSnapshot.EMPTY:source;
    }
    public static double magicFind(com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> actor,
            com.hypixel.hytale.component.ComponentAccessor<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> accessor) {
        return equipment==null?0:equipment.magicFind(actor,accessor);
    }
    public static HytaleGearEquipment.MagicFindBreakdown magicFindBreakdown(
            com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> actor,
            com.hypixel.hytale.component.ComponentAccessor<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> accessor) {
        return equipment==null?new HytaleGearEquipment.MagicFindBreakdown(0,0):equipment.magicFindBreakdown(actor,accessor);
    }
    public static boolean canUse(ItemStack stack,com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> actor,
                                 com.hypixel.hytale.component.ComponentAccessor<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> accessor) {
        return equipment!=null && equipment.canUse(stack,actor,accessor);
    }
    public static String nativeId(String managedId) {
        var direct=NATIVE_IDS.get(managedId);if(direct!=null)return direct;
        int marker=managedId.indexOf("__S");
        if(marker>0){String carrier=managedId.substring(0,marker);
             if(NativeSwordActionAssets.variantOf(managedId,carrier))return NATIVE_IDS.getOrDefault(carrier,managedId);}
        marker=managedId.indexOf("__A");
        if(marker>0){String carrier=managedId.substring(0,marker);
            if(NativePrimaryActionAssets.variantOf(managedId,carrier))return NATIVE_IDS.getOrDefault(carrier,managedId);}
        marker=managedId.indexOf("__Twin");
        if(marker>0){String carrier=managedId.substring(0,marker);
             if(NativeTwinDaggersAssets.variantOf(managedId,carrier))return NATIVE_IDS.getOrDefault(carrier,managedId);}
        marker=managedId.indexOf("__Offhand");
        if(marker>0){String carrier=managedId.substring(0,marker);
            if(NativeTwinUtilityAssets.variantOf(managedId,carrier))return NATIVE_IDS.getOrDefault(carrier,managedId);}
        return managedId;
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
        String carrier=binding.carrier(gear.rarity());
         if(!binding.mapped() || !stack.getItemId().equals(carrier) && !validActionVariant(stack.getItemId(),carrier,gear)
                 && !validPrimaryVariant(stack.getItemId(),carrier,gear)
                 && !validOffhandVariant(stack.getItemId(),carrier,gear)
                 && !validTwinVariant(stack.getItemId(),carrier,gear))
            throw new IllegalArgumentException("Gear instance/native carrier mismatch");
        return gear;
    }
    private static boolean validActionVariant(String id,String carrier,GearInstance gear){
        if(!NativeSwordActionAssets.variantOf(id,carrier))return false;
        try{var profile=NativeGearActionProfiles.swordPrimary(new GearEffectSnapshot(List.of(gear)),gear.identity());
            return id.equals(NativeSwordActionAssets.variantId(carrier,profile));
        }catch(RuntimeException invalid){return false;}
    }
     private static boolean validTwinVariant(String id,String carrier,GearInstance gear){
        return gear.baseId().startsWith("gm.daggers_") && NativeTwinDaggersAssets.variantOf(id,carrier)
                && gear.affixes().stream().anyMatch(a->a.familyId().equals("WA-141"));
     }
     private static boolean validOffhandVariant(String id,String carrier,GearInstance gear){
         return gear.baseId().startsWith("gm.daggers_") && NativeTwinUtilityAssets.variantOf(id,carrier);
     }
     private static boolean validPrimaryVariant(String id,String carrier,GearInstance gear){
         if(!NativePrimaryActionAssets.variantOf(id,carrier))return false;
         try {var profile=NativeGearActionProfiles.primary(new GearEffectSnapshot(List.of(gear)),gear.identity());
             return id.equals(NativePrimaryActionAssets.variantId(carrier,profile));
         } catch(RuntimeException invalid){return false;}
     }
    /** Rebind a valid held item's native root before the next predicted chain. */
     public static ItemStack actionVariant(ItemStack stack,GearEffectSnapshot validSnapshot){
         return actionVariant(stack,validSnapshot,null);
     }
     public static ItemStack actionVariant(ItemStack stack,GearEffectSnapshot validSnapshot,UUID actualMainhand){
          var gear=read(stack);if(gear==null)return stack;
          String carrier=BINDINGS.require(gear.baseId()).carrier(gear.rarity());
          var local=validSnapshot.forItem(gear.identity());
         if(NativeTwinUtilityAssets.eligible(validSnapshot,actualMainhand,gear))
             return withCarrierId(stack,NativeTwinUtilityAssets.publish(carrier));
         if(local.empty() || local.value("WA-008")==0 && local.value("WA-014")==0
                 && local.value("WA-141")==0)
             return withCarrierId(stack,carrier);
         var profile=NativeGearActionProfiles.primary(validSnapshot,gear.identity());
         String variant=gear.baseId().startsWith("gm.sword_")
                 ? NativeSwordActionAssets.publish(carrier,profile)
                 : NativePrimaryActionAssets.publish(carrier,profile);
        return withCarrierId(stack,variant);
    }
    private static ItemStack withCarrierId(ItemStack stack,String variant){
        if(stack.getItemId().equals(variant))return stack;
        var converted=new ItemStack(variant,stack.getQuantity(),stack.getDurability(),stack.getMaxDurability(),
                stack.getQualityIndex(),stack.getMetadata().clone());
        converted.setOverrideDroppedItemAnimation(stack.getOverrideDroppedItemAnimation());
        return converted;
    }
    public static ItemStack create(GearInstance gear,int actorLevel,Map<RpgAttribute,Integer> permanent) {
        var binding=BINDINGS.require(gear.baseId());
        if(!binding.mapped()) throw new IllegalArgumentException(binding.reason());
        if(Item.getAssetMap().getAsset(binding.carrier(gear.rarity()))==null) throw new IllegalStateException("Managed native item not loaded");
        int quality=ItemQuality.getAssetMap().getIndex(gear.rarity().qualityAsset());
        if(quality<0) throw new IllegalStateException("Managed quality not loaded");
        String carrier=binding.carrier(gear.rarity());
        var stack=new ItemStack(carrier,1).withQuality(quality).withMetadata(KEY,new BsonString(gear.toJson()));
        return present(stack,gear,actorLevel,permanent);
    }
    public static ItemStack present(ItemStack stack,GearInstance gear,int level,Map<RpgAttribute,Integer> permanent) {
        var description=Message.empty();
        var lines=GearTooltip.describe(gear,level,permanent);
        boolean separated=false, first=true;
        for(int i=1;i<lines.size();i++) {
            var line=lines.get(i);
            if(line.style()==GearTooltip.Style.DIVIDER){separated=true;continue;}
            var message=Message.raw((first?"":separated||line.breakBefore()?"\n\n":"\n")+line.text());
            if(line.color()!=null) message.color(line.color());
            if(line.style()==GearTooltip.Style.DAMAGE||line.style()==GearTooltip.Style.HEADING)message.bold(true);
            description.insert(message);
            first=false;separated=false;
        }
        var flavor=authoredFlavor(gear);
        if(flavor!=null)description.insert(Message.raw("\n\n"))
                .insert(flavor.color(GearTooltip.MUTED_COLOR).italic(true));
        var presented=stack.withMetadata(ItemDisplayMetadata.KEYED_CODEC,
                // ItemQuality alone owns title/quality color; the native name
                // label already supplies its bold 18px treatment.
                new ItemDisplayMetadata(Message.raw(lines.getFirst().text()),description));
        int quality=ItemQuality.getAssetMap().getIndex(gear.rarity().qualityAsset());
        return quality<0?presented:presented.withQuality(quality);
    }
    private static Message authoredFlavor(GearInstance gear) {
        var binding=BINDINGS.require(gear.baseId());
        var nativeItem=binding.mapped()?Item.getAssetMap().getAsset(binding.nativeItemId()):null;
        return nativeItem==null||nativeItem.getDescriptionTranslationKey()==null?null:nativeItem.getDescriptionTranslationMessage();
    }
    /** Read-only presentation from the same owners used by equip eligibility and native metadata. */
    public record TooltipView(GearInstance gear,List<GearTooltip.Line> lines,
                              double durability,double maxDurability) { }
    public static TooltipView tooltipView(ItemStack stack,
            com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> actor,
            com.hypixel.hytale.component.ComponentAccessor<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> accessor,
            java.util.function.Function<Message,String> localize) {
        var gear=read(stack);
        if(gear==null||equipment==null)return null;
        var view=equipment.view(actor,accessor);
        var authored=authoredFlavor(gear);
        String flavor=authored==null?null:localize.apply(authored);
        return new TooltipView(gear,GearTooltip.describe(gear,view.level(),
                HytaleGearEquipment.requirementAttributes(view,gear.identity()),flavor),
                stack.getDurability(),stack.getMaxDurability());
    }
    /** Presentation copy only. The inventory's source fingerprints/custody never use this copy. */
    public static java.util.function.UnaryOperator<ItemStack> tooltipViewer(
            com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> actor,
            com.hypixel.hytale.component.ComponentAccessor<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> accessor) {
        if(equipment==null)return java.util.function.UnaryOperator.identity();
        var view=equipment.view(actor,accessor);
        return stack->{
            if(ItemStack.isEmpty(stack)||!managed(stack))return stack;
            try {
                var gear=read(stack);
                return present(stack,gear,view.level(),HytaleGearEquipment.requirementAttributes(view,gear.identity()));
            }catch(IllegalArgumentException unavailable){return stack;}
        };
    }
}
