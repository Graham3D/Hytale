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
    private final Map<DamageCause, DamageSystems.ArmorDamageReduction.ArmorResistanceModifiers> modifiers;

    private NativeArmorDefenseView(Map<DamageCause, DamageSystems.ArmorDamageReduction.ArmorResistanceModifiers> modifiers) {
        this.modifiers = modifiers;
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
        DamageCause cause;
        try { cause = DamageCause.getAssetMap().getAsset(damageCauseId); }
        catch (RuntimeException unavailable) { return OptionalDouble.empty(); }
        if (cause == null) return OptionalDouble.empty();
        var value = modifiers.get(cause);
        if (value == null || value.inheritedParentId != null || !Float.isFinite(value.multiplierModifier))
            return OptionalDouble.empty();
        return OptionalDouble.of(value.multiplierModifier * 100.0);
    }
}
