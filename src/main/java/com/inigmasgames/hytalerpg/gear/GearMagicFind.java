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
    private static final GearRarity[] RANDOM={GearRarity.NORMAL,GearRarity.MAGIC,GearRarity.RARE};
    public static QualityWeights weights(DifficultyId era,int level,ProgressionMath.Rank rank,double mf){
        if(!Double.isFinite(mf)||mf<0)throw new IllegalArgumentException("MF snapshot");
        var profile=GearQualityProfile.CURRENT;double[] base=profile.base(era),ranks=profile.rank(rank),magic=profile.magicFind(mf);
        var b=new EnumMap<GearRarity,Double>(GearRarity.class);var r=new EnumMap<GearRarity,Double>(GearRarity.class);
        var m=new EnumMap<GearRarity,Double>(GearRarity.class);var result=new EnumMap<GearRarity,Double>(GearRarity.class);
        double sum=0;
        for(int i=0;i<RANDOM.length;i++){
            var quality=RANDOM[i];double eligible=quality.eligible(level,era)?base[i]:0;
            b.put(quality,eligible);r.put(quality,ranks[i]);m.put(quality,magic[i]);
            double weighted=eligible*ranks[i]*magic[i];result.put(quality,weighted);sum+=weighted;
        }
        if(sum<=0)throw new IllegalStateException("No eligible gear quality");
        final double total=sum;result.replaceAll((key,value)->value/total);
        return new QualityWeights(Map.copyOf(b),Map.copyOf(r),Map.copyOf(m),Map.copyOf(result));
    }
    public static Map<GearRarity,Double> distribution(DifficultyId era,int level,ProgressionMath.Rank rank,double mf){
        return weights(era,level,rank,mf).normalized();
    }
}
