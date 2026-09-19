package com.inigmasgames.hytalerpg.execution.projectile;

/** Pure authoritative Fireball charge policy. Presentation samples this state; it never owns gameplay. */
public final class FireballCharge {
    public static final int MAX_STAGE=4;
    private FireballCharge(){}
    public static int stage(double heldSeconds){
        if(!Double.isFinite(heldSeconds)||heldSeconds<0)throw new IllegalArgumentException("INVALID_FIREBALL_HOLD");
        return Math.min(MAX_STAGE,(int)Math.floor(heldSeconds));
    }
    public static double damageMultiplier(int stage){validate(stage);return 1+.10*stage;}
    public static double movementMultiplier(int stage){validate(stage);return 1-.10*stage;}
    public static double coefficient(int stage){return .90*damageMultiplier(stage);}
    public static double burningCoefficient(int stage){return coefficient(stage)*1.25;}
    private static void validate(int stage){if(stage<0||stage>MAX_STAGE)throw new IllegalArgumentException("INVALID_FIREBALL_STAGE");}
}
