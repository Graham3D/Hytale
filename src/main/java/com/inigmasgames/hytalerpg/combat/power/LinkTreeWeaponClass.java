package com.inigmasgames.hytalerpg.combat.power;

import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import java.util.Optional;

/** RPG weapon classification; deliberately distinct from Hytale DamageClass.LIGHT. */
public enum LinkTreeWeaponClass {
    LIGHT(RpgAttribute.DEX), HEAVY(RpgAttribute.STR), MAGIC(RpgAttribute.INT),
    INNATE(null), UTILITY(null);
    private final RpgAttribute scalingAttribute;
    LinkTreeWeaponClass(RpgAttribute scalingAttribute) { this.scalingAttribute = scalingAttribute; }
    public Optional<RpgAttribute> scalingAttribute() { return Optional.ofNullable(scalingAttribute); }
}
