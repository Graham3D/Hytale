package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.protocol.ItemResourceType;
import java.util.Objects;

/** WA-156 admission for the existing protected stock scan and receipt transfer. */
public final class NativeAffixMaterialPickup {
    private NativeAffixMaterialPickup() {}

    public record ItemKind(String id, int maximumStack, boolean weapon, boolean armor, boolean resource) {}
    public record Candidate(ItemKind kind, double distanceMetres, boolean protectedSource,
                            boolean sameWorld, boolean unclaimed, boolean lineOfAccess) {}

    public static double reach(GearEffectSnapshot validEquipment,double normalReachMetres) {
        Objects.requireNonNull(validEquipment);
        if (!(normalReachMetres>0) || !Double.isFinite(normalReachMetres))
            throw new IllegalArgumentException("Invalid normal pickup reach");
        double bonus=validEquipment.total(GearEffectSnapshot.Operator.PICKUP_RADIUS);
        if (!Double.isFinite(bonus) || bonus<0) throw new IllegalArgumentException("Invalid pickup bonus");
        return normalReachMetres+bonus;
    }

    public static ItemKind describe(ItemStack stack) {
        if (ItemStack.isEmpty(stack) || GearNativeItems.managed(stack)) return null;
        var item=stack.getItem();
        if (item==null) return null;
        ItemResourceType[] resources=item.getResourceTypes();
        boolean resource=false;
        if(resources!=null) for(var row:resources)
            if(row!=null && row.id!=null && !row.id.isBlank()) resource=true;
        return new ItemKind(item.getId(),item.getMaxStack(),item.getWeapon()!=null,item.getArmor()!=null,resource);
    }

    public static boolean allowedMaterial(ItemKind kind) {
        return kind!=null && kind.id()!=null && kind.maximumStack()>1 && !kind.weapon() && !kind.armor()
                && kind.resource() && (kind.id().startsWith("Ingredient_") || kind.id().startsWith("Ore_")
                ||kind.id().startsWith("Rock_")||kind.id().startsWith("Wood_")
                ||kind.id().startsWith("Plant_")||kind.id().startsWith("Metal_")
                ||kind.id().startsWith("Soil_"));
    }

    /** This is admission only. The original claim/protection and atomic transfer remain mandatory. */
    public static boolean admit(GearEffectSnapshot validEquipment,double normalReachMetres,Candidate candidate) {
        Objects.requireNonNull(candidate);
        return candidate.protectedSource() && candidate.sameWorld() && candidate.unclaimed()
                && candidate.lineOfAccess() && Double.isFinite(candidate.distanceMetres())
                && candidate.distanceMetres()>=0 && candidate.distanceMetres()<=reach(validEquipment,normalReachMetres)
                && allowedMaterial(candidate.kind());
    }
}
