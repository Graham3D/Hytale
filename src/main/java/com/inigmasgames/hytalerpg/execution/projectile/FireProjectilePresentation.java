package com.inigmasgames.hytalerpg.execution.projectile;

import java.util.Locale;

/** Presentation-only policy for the two authored fire projectiles. */
public final class FireProjectilePresentation {
    public static final String AIM_PARTICLE="RPG_Fireball_Aim_White";
    public static final String FIRE_BOLT_LAUNCH_SOUND="SFX_Staff_Fire_Shoot";
    public static final String FIRE_BOLT_IMPACT_SOUND="SFX_Staff_Flame_Flamethrower_Impact";
    public static final String FIREBALL_LAUNCH_SOUND="SFX_Staff_Flame_Fireball_Launch";
    public static final String FIREBALL_IMPACT_SOUND="SFX_Staff_Flame_Fireball_Impact";

    private FireProjectilePresentation() {}

    public enum Tier {
        SMALL(1.0,"Projectile_Config_RPG_Fireball_Small"),
        MEDIUM(1.5,"Projectile_Config_RPG_Fireball_Medium"),
        LARGE(2.1,"Projectile_Config_RPG_Fireball_Large"),
        EXTRA_LARGE(2.8,"Projectile_Config_RPG_Fireball_Extra_Large");
        private final double visualScale;private final String configId;
        Tier(double visualScale,String configId){this.visualScale=visualScale;this.configId=configId;}
        public double visualScale(){return visualScale;}
        public String configId(){return configId;}
    }

    /** Quick release and authored stage one intentionally share SMALL. */
    public static Tier tier(int chargeStage){
        if(chargeStage<0||chargeStage>FireballCharge.MAX_STAGE)throw new IllegalArgumentException("INVALID_FIREBALL_STAGE");
        return switch(chargeStage){case 0,1->Tier.SMALL;case 2->Tier.MEDIUM;case 3->Tier.LARGE;case 4->Tier.EXTRA_LARGE;default->throw new AssertionError();};
    }
    public static String configId(int chargeStage){return tier(chargeStage).configId();}
    public static boolean suppressesGenericTrail(String skillId){return "fire_bolt".equals(skillId)||"fireball".equals(skillId);}
    public static String launchSound(String skillId,boolean derivedRelease){
        if(derivedRelease)return "";
        return switch(skillId){case "fire_bolt"->FIRE_BOLT_LAUNCH_SOUND;case "fireball"->FIREBALL_LAUNCH_SOUND;default->"";};
    }
    public static String impactSound(String skillId){
        return switch(skillId){case "fire_bolt"->FIRE_BOLT_IMPACT_SOUND;case "fireball"->FIREBALL_IMPACT_SOUND;default->"";};
    }
    /** RootEffectBudget controller keys are bounded to 64 characters. A collision suppresses audio conservatively. */
    public static String impactController(String skillId,String terminalImpactId){
        String skill=skillId==null?"unknown":skillId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]","_");
        return "FIRE_IMPACT_SOUND/"+skill+"/"+Integer.toUnsignedString(String.valueOf(terminalImpactId).hashCode(),16);
    }
}
