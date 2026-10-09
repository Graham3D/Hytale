package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;

/** Persisted historical budgets remain readable. New drops use legalNewBudget. */
public enum GearRarity {
    COMMON(GearRarityPresentation.NORMAL, "Common", 0, 0, 1),
    UNCOMMON(GearRarityPresentation.RARE, "Uncommon", 1, 1, 1),
    RARE(GearRarityPresentation.EPIC, "Rare", 2, 3, 10),
    VERY_RARE(GearRarityPresentation.EPIC, "Epic", 4, 5, 35),
    LEGENDARY(GearRarityPresentation.LEGENDARY, "Legendary", 6, 6, 60),
    NORMAL(GearRarityPresentation.NORMAL, "Common", 0, 0, 1),
    MAGIC(GearRarityPresentation.RARE, "Uncommon", 1, 1, 1);

    public static final String AFFIX_COLOR = "#1d4dff";
    public final GearRarityPresentation presentation;
    public final String label, color, nativeParticleTier;
    public final int minAffixes, maxAffixes, minimumLevel;
    GearRarity(GearRarityPresentation presentation, String nativeTier, int min, int max, int level) {
        this.presentation=presentation; this.label=presentation.label; this.color=presentation.color; nativeParticleTier=nativeTier;
        minAffixes=min; maxAffixes=max; minimumLevel=level;
    }
    public String particlePath() {
        String tier=quality()==GearQuality.MAGIC?"Rare":"Common";
        return "Particles/Drop/"+tier+"/Drop_"+tier+".particlesystem";
    }
    public GearQuality quality(){return switch(this){case COMMON,NORMAL->GearQuality.NORMAL;case UNCOMMON,MAGIC->GearQuality.MAGIC;default->GearQuality.RARE;};}
    public String qualityAsset(){if(this==LEGENDARY)return "RPG_Gear_Legendary";return switch(quality()){case NORMAL->"RPG_Gear_Common";case MAGIC->"RPG_Gear_Rare";case RARE->"RPG_Gear_RandomRare";default->throw new IllegalStateException();};}
    public boolean eligible(int itemLevel, DifficultyId era) {
        return itemLevel>=minimumLevel && itemLevel<=99 && (this!=LEGENDARY || era==DifficultyId.HELL);
    }
    public boolean legalBudget(int prefixes, int suffixes) {
        if(prefixes<0 || suffixes<0) return false;
        if(legalNewBudget(prefixes,suffixes)) return true;
        // Version-one saves were issued under the three-band profile. Decode them
        // unchanged; generation never calls this compatibility branch.
        return switch(this) {
            case MAGIC -> prefixes+suffixes==1 || prefixes==1 && suffixes==1;
            case RARE -> prefixes>=1 && suffixes>=1 && prefixes<=3 && suffixes<=3 && prefixes+suffixes>=2 && prefixes+suffixes<=6;
            default -> false;
        };
    }
    public boolean legalNewBudget(int prefixes,int suffixes) {
        if(prefixes<0 || suffixes<0) return false;
        return switch(this) {
            case COMMON,NORMAL -> prefixes==0 && suffixes==0;
            case UNCOMMON,MAGIC -> prefixes+suffixes==1;
            case RARE -> prefixes>=1 && suffixes>=1 && prefixes<=2 && suffixes<=2 && prefixes+suffixes<=3;
            case VERY_RARE -> prefixes>=1 && suffixes>=1 && prefixes<=3 && suffixes<=3 && prefixes+suffixes>=4 && prefixes+suffixes<=5;
            case LEGENDARY -> prefixes==3 && suffixes==3;
        };
    }
}
