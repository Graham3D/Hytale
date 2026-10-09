package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.OptionalDouble;

/** Reads the engine's current armor/effect mitigation projection without simulating damage. */
final class NativeArmorDefenseView {
    private final Map<String,Double> directPercent;

    private NativeArmorDefenseView(Map<DamageCause, DamageSystems.ArmorDamageReduction.ArmorResistanceModifiers> modifiers) {
        var values=new java.util.LinkedHashMap<String,Double>();
        for(var entry:modifiers.entrySet()){
            var value=entry.getValue();
            if(entry.getKey()!=null&&value!=null&&value.inheritedParentId==null&&Float.isFinite(value.multiplierModifier))
                values.put(entry.getKey().getId(),value.multiplierModifier*100.0);
        }
        this.directPercent=Map.copyOf(values);
    }
    private NativeArmorDefenseView(Map<String,Double> percentages,boolean frozen){this.directPercent=Map.copyOf(percentages);}
    static NativeArmorDefenseView frozen(Map<String,Double> percentages){
        if(percentages.values().stream().anyMatch(v->v==null||!Double.isFinite(v)))throw new IllegalArgumentException("Invalid native defense projection");
        return new NativeArmorDefenseView(percentages,true);
    }

    static NativeArmorDefenseView read(Ref<EntityStore> ref, Store<EntityStore> store) {
        var armor = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
        var effects = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (armor == null || effects == null) return new NativeArmorDefenseView(Map.of());
        try {
            return new NativeArmorDefenseView(DamageSystems.ArmorDamageReduction.getResistanceModifiers(
                    store.getExternalData().getWorld(), armor.getInventory(), false, effects));
        } catch (RuntimeException unavailable) {
            return new NativeArmorDefenseView(Map.of());
        }
    }

    OptionalDouble directPercent(String damageCauseId) {
        var value=directPercent.get(damageCauseId);
        return value==null?OptionalDouble.empty():OptionalDouble.of(value);
    }
}
