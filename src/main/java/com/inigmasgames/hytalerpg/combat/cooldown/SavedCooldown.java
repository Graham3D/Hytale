package com.inigmasgames.hytalerpg.combat.cooldown;

/** Remaining RPG work and its non-Aura recovery rate; no monotonic timestamp or live native cooldown is serialized. */
public record SavedCooldown(double remainingWork,double baseRecovery) {
    public SavedCooldown {
        if(!Double.isFinite(remainingWork)||remainingWork<0||remainingWork>86400||!Double.isFinite(baseRecovery)||baseRecovery<0||baseRecovery>100)
            throw new IllegalArgumentException("Invalid saved cooldown work");
    }
    public static java.util.Map<String,SavedCooldown> validate(java.util.Map<String,SavedCooldown> values){
        if(values==null||values.size()>87)throw new IllegalArgumentException("Invalid saved cooldown count");
        values.forEach((id,value)->{if(id==null||id.isBlank()||id.length()>128||value==null)throw new IllegalArgumentException("Invalid saved cooldown entry");});
        return java.util.Map.copyOf(values);
    }
}
