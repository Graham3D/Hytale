package com.inigmasgames.hytalerpg.combat.resource;

public record ResourceCost(ResourceType type, double amount) {
    public static final ResourceCost NONE = new ResourceCost(ResourceType.NONE, 0.0);
    public ResourceCost {
        if (type == null || !Double.isFinite(amount) || amount < 0.0)
            throw new IllegalArgumentException("Resource cost must have a type and finite non-negative amount");
        if (type == ResourceType.NONE && amount != 0.0)
            throw new IllegalArgumentException("NONE resource cost must be zero");
    }
    public static ResourceCost fromDeclaration(double mana, double stamina) {
        if (!Double.isFinite(mana) || !Double.isFinite(stamina) || mana < 0.0 || stamina < 0.0)
            throw new IllegalArgumentException("Declared costs must be finite and non-negative");
        if (mana > 0.0 && stamina > 0.0)
            throw new IllegalArgumentException("One activation may not consume Mana and Stamina simultaneously");
        return mana > 0.0 ? new ResourceCost(ResourceType.MANA, mana)
                : stamina > 0.0 ? new ResourceCost(ResourceType.STAMINA, stamina) : NONE;
    }
    /** Master integer upfront-cost rule: zero stays zero; a nonzero modified cost floors at one after ceiling. */
    public ResourceCost modified(double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier < 0.0) throw new IllegalArgumentException("Invalid cost multiplier");
        if (amount == 0.0) return this;
        return new ResourceCost(type, Math.max(1.0, Math.ceil(amount * multiplier)));
    }
}
