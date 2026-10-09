package com.inigmasgames.hytalerpg.gear;

import java.math.*;
import java.util.*;

/** Definition compilation only. Does not mark an operator gameplay-capable or enable a drop pool. */
public final class GearAffixTiers {
    private GearAffixTiers() {}
    public record Tier(int tier,int minimumItemLevel,int requiredLevel,int weight,double low,double high,double grid) {}
    public static List<Tier> compile(GearCatalog.Affix affix) {
        if(affix.tierModel().equals("Q")) {
            if(affix.topRange()==null) throw new IllegalArgumentException("Q affix lacks range "+affix.id());
            var result=new ArrayList<Tier>();
            String unit=affix.topAndGroup();double grid=affix.operator().equals("ATTRIBUTE")?1:unit.contains(" m ")?.01:.1;
            double[] factors={.40,.55,.70,.85,1};int[] weights={100,60,30,12,4};
            for(int j=0;j<5;j++) {
                int minimum=affix.ordinaryTierMinimum(j);
                double low=quantize(affix.topRange().getFirst(),factors[j],grid,RoundingMode.CEILING);
                double high=quantize(affix.topRange().getLast(),factors[j],grid,RoundingMode.FLOOR);
                if(low>high) throw new IllegalArgumentException("Empty affix grid "+affix.id());
                result.add(new Tier(5-j,minimum,(int)Math.ceil(.8*minimum),weights[j],low,high,grid));
            }
            return List.copyOf(result);
        }
        return switch(affix.tierModel()) {
            case "NAMED" -> ranks(new int[]{35,60,82,94},new int[]{28,48,66,80},new int[]{100,30,6,1});
            case "FAMILY" -> ranks(new int[]{55,80,94},new int[]{44,64,80},new int[]{100,20,2});
            case "ALL" -> ranks(new int[]{78,92},new int[]{63,80},new int[]{100,4});
            case "FIXED" -> List.of(new Tier(1,affix.firstItemLevel(),Math.max(1,(int)Math.ceil(.8*affix.firstItemLevel())),1,
                    affix.id().equals("WA-139")?5:1,affix.id().equals("WA-139")?5:1,1));
            default -> throw new IllegalArgumentException("Unknown affix tier model "+affix.tierModel());
        };
    }
    private static List<Tier> ranks(int[] ilvl,int[] level,int[] weight) {
        var tiers=new ArrayList<Tier>();for(int i=0;i<ilvl.length;i++) tiers.add(new Tier(i+1,ilvl[i],level[i],weight[i],i+1,i+1,1));return List.copyOf(tiers);
    }
    public static boolean rarityAllows(GearCatalog.Affix affix,Tier tier,GearRarity rarity) {
        if(rarity.quality()==GearQuality.NORMAL) return false;
        return switch(affix.tierModel()) {
            case "NAMED" -> tier.tier()==1 || rarity!=GearRarity.MAGIC && rarity!=GearRarity.UNCOMMON
                    && (tier.tier()<4 || rarity==GearRarity.RARE);
            case "FAMILY" -> rarity!=GearRarity.MAGIC && rarity!=GearRarity.UNCOMMON
                    && (tier.tier()<3 || rarity==GearRarity.RARE);
            case "ALL" -> tier.tier()==1
                    ? rarity==GearRarity.VERY_RARE || rarity==GearRarity.LEGENDARY
                    : rarity==GearRarity.LEGENDARY;
            default -> true;
        };
    }
    private static double quantize(double value,double factor,double grid,RoundingMode mode) {
        return BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(factor)).divide(BigDecimal.valueOf(grid),0,mode).multiply(BigDecimal.valueOf(grid)).doubleValue();
    }
}
