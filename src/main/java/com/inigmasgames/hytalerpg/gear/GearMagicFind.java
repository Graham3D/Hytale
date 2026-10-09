package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.progress.ProgressionMath;
import java.util.*;

public final class GearMagicFind {
    private GearMagicFind(){}
    private static final com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile BALANCE=com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile.loadCanonical();
    private static final com.inigmasgames.hytalerpg.combat.attribute.EffectiveAttributeService ATTRIBUTES=new com.inigmasgames.hytalerpg.combat.attribute.EffectiveAttributeService(BALANCE);
    public static double effectiveLuck(double raw){
        if(!Double.isFinite(raw))throw new IllegalArgumentException("Luck");
        return ATTRIBUTES.effective(Math.max(raw,0));
    }
    public static double snapshot(double rawLuck,double gearFraction){if(!Double.isFinite(gearFraction))throw new IllegalArgumentException("MF");return Math.max(0,BALANCE.luckMagicFindPerPoint*effectiveLuck(rawLuck)+gearFraction);}
    public static double opportunity(ProgressionMath.Rank rank){return switch(rank){case COMMON->.20;case SPECIALIST->.35;case ELITE->.65;case MINIBOSS,BOSS->1;};}
    public record QualityWeights(Map<GearRarity,Double> base,Map<GearRarity,Double> rank,
                                 Map<GearRarity,Double> magicFind,Map<GearRarity,Double> normalized) {}
    private static final GearRarity[] RANDOM={GearRarity.NORMAL,GearRarity.MAGIC,GearRarity.RARE,GearRarity.VERY_RARE,GearRarity.LEGENDARY};
    public static QualityWeights weights(DifficultyId era,int level,ProgressionMath.Rank rank,double mf){
        if(!Double.isFinite(mf)||mf<0)throw new IllegalArgumentException("MF snapshot");
        var profile=GearQualityProfile.CURRENT;double[] base=profile.base(era),ranks=profile.rank(rank),magic=profile.magicFind(mf);
        var b=new EnumMap<GearRarity,Double>(GearRarity.class);var r=new EnumMap<GearRarity,Double>(GearRarity.class);
        var m=new EnumMap<GearRarity,Double>(GearRarity.class);var result=new EnumMap<GearRarity,Double>(GearRarity.class);
        double veryShare=GearRarity.VERY_RARE.eligible(level,era)?profile.veryRareShare():0;
        double legendaryShare=GearRarity.LEGENDARY.eligible(level,era)?profile.legendaryShare():0;
        double sum=0;
        for(int i=0;i<RANDOM.length;i++){
            var quality=RANDOM[i];int band=Math.min(i,2);
            double share=switch(quality){case RARE->1-veryShare-legendaryShare;case VERY_RARE->veryShare;
                case LEGENDARY->legendaryShare;default->1;};
            double eligible=quality.eligible(level,era)?base[band]*share:0;
            b.put(quality,eligible);r.put(quality,ranks[band]);m.put(quality,magic[band]);
            double weighted=eligible*ranks[band]*magic[band];result.put(quality,weighted);sum+=weighted;
        }
        if(sum<=0)throw new IllegalStateException("No eligible gear quality");
        final double total=sum;result.replaceAll((key,value)->value/total);
        return new QualityWeights(Collections.unmodifiableMap(b),Collections.unmodifiableMap(r),
                Collections.unmodifiableMap(m),Collections.unmodifiableMap(result));
    }
    public static Map<GearRarity,Double> distribution(DifficultyId era,int level,ProgressionMath.Rank rank,double mf){
        return weights(era,level,rank,mf).normalized();
    }
}
