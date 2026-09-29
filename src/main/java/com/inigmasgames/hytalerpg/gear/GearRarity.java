package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;

/** Frozen legacy values remain decodable; new random rolls use NORMAL, MAGIC, RARE only. */
public enum GearRarity {
    COMMON("Normal", "#ffffff", "Common", 0, 0, 1),
    UNCOMMON("Magic", "#1d4dff", "Uncommon", 1, 1, 1),
    RARE("Rare", "#a000ff", "Rare", 3, 6, 10),
    VERY_RARE("Rare", "#a000ff", "Epic", 4, 5, 35),
    LEGENDARY("Legendary", "#ff9100", "Legendary", 6, 6, 60),
    NORMAL("Normal", "#ffffff", "Common", 0, 0, 1),
    MAGIC("Magic", "#1d4dff", "Uncommon", 1, 2, 1);

    public static final String AFFIX_COLOR = "#1d4dff";
    public final String label, color, nativeParticleTier;
    public final int minAffixes, maxAffixes, minimumLevel;
    GearRarity(String label, String color, String nativeTier, int min, int max, int level) {
        this.label=label; this.color=color; nativeParticleTier=nativeTier;
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
        return switch(this) {
            case COMMON,NORMAL -> prefixes==0 && suffixes==0;
            case UNCOMMON -> prefixes+suffixes==1;
            case MAGIC -> prefixes+suffixes==1 || prefixes==1 && suffixes==1;
            case RARE -> prefixes>=1 && suffixes>=1 && prefixes<=3 && suffixes<=3 && prefixes+suffixes>=2 && prefixes+suffixes<=6;
            case VERY_RARE -> prefixes>=2 && suffixes>=2 && prefixes<=3 && suffixes<=3 && prefixes+suffixes<=5;
            case LEGENDARY -> prefixes==3 && suffixes==3;
        };
    }
}
