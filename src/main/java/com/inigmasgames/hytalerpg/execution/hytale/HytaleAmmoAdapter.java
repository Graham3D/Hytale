package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.inigmasgames.hytalerpg.execution.Stage04SkillProfile;
import com.inigmasgames.hytalerpg.ui.inventory.SpatialBagComponent;

/** Atomic authoritative inventory boundary for projectile ammunition. */
public final class HytaleAmmoAdapter {
    public boolean available(Ref<EntityStore> actor, ComponentAccessor<EntityStore> accessor,
                             Stage04SkillProfile.Projectile projectile) {
        if (!projectile.requiresAmmo()) return true;
        if (spatialOwner(actor, accessor)) return false;
        ItemContainer inventory = inventory(actor, accessor);
        return inventory != null && inventory.canRemoveItemStack(
                new ItemStack(projectile.ammoItemId(), projectile.ammoQuantity()), true, true);
    }

    public Token consume(Ref<EntityStore> actor, ComponentAccessor<EntityStore> accessor,
                         Stage04SkillProfile.Projectile projectile) {
        if (!projectile.requiresAmmo()) return Token.NONE;
        if (spatialOwner(actor, accessor)) throw new IllegalStateException("Native ammunition disabled in spatial mode");
        ItemContainer inventory = inventory(actor, accessor);
        ItemStack stack = new ItemStack(projectile.ammoItemId(), projectile.ammoQuantity());
        if (inventory == null || !inventory.removeItemStack(stack, true, true).succeeded())
            throw new IllegalStateException("Required projectile ammunition changed before commit");
        return new Token(projectile.ammoItemId(), projectile.ammoQuantity());
    }

    public void refund(Ref<EntityStore> actor, ComponentAccessor<EntityStore> accessor, Token token) {
        if (token == null || token.quantity() == 0) return;
        if (spatialOwner(actor, accessor)) throw new IllegalStateException("Native ammunition refund disabled in spatial mode");
        ItemContainer inventory = inventory(actor, accessor);
        if (inventory == null || !inventory.addItemStack(
                new ItemStack(token.itemId(), token.quantity()), true, true, true).succeeded())
            throw new IllegalStateException("Could not refund projectile ammunition");
    }

    private static ItemContainer inventory(Ref<EntityStore> actor, ComponentAccessor<EntityStore> accessor) {
        return InventoryComponent.getCombined(accessor, actor, InventoryComponent.HOTBAR_STORAGE_BACKPACK);
    }

    private static boolean spatialOwner(Ref<EntityStore> actor, ComponentAccessor<EntityStore> accessor) {
        var type = SpatialBagComponent.getComponentType();
        if (type == null) return true;
        var bag = accessor.getComponent(actor, type);
        if (bag == null) return false;
        var player = accessor.getComponent(actor, PlayerRef.getComponentType());
        return player == null || bag.mode(player.getUuid()) != SpatialBagComponent.OwnershipMode.NATIVE;
    }

    public record Token(String itemId, int quantity) {
        public static final Token NONE = new Token("", 0);
    }
}
