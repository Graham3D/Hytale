package com.inigmasgames.hytalerpg.enemies;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/** AD1 reward evaluator v2. Decimal inputs are canonical; rounding belongs to existing owners. */
public final class Me12RewardMath {
    private Me12RewardMath() {}
    private static final BigDecimal RATE=new BigDecimal("0.15"), ONE=BigDecimal.ONE;
    public record Factors(String xp,String quantity) {
        public Factors {Objects.requireNonNull(xp);Objects.requireNonNull(quantity);}
    }
    private static BigDecimal bonus(int threatHundredths){return BigDecimal.valueOf(Math.max(0,threatHundredths-100),2);}
    public static Factors freeze(int threatHundredths,double rarityXp,double rarityQuantity){
        var b=bonus(threatHundredths);
        return new Factors(BigDecimal.valueOf(rarityXp).multiply(ONE.add(RATE.multiply(b.min(BigDecimal.valueOf(9))))).toPlainString(),
                BigDecimal.valueOf(rarityQuantity).multiply(ONE.add(RATE.multiply(b)).min(new BigDecimal("2.05"))).toPlainString());
    }
    public static double learning(double rarityChance,int threatHundredths){
        var p=BigDecimal.valueOf(rarityChance);
        return p.add(p.multiply(new BigDecimal("0.10")).multiply(bonus(threatHundredths)).min(new BigDecimal("0.03")))
                .min(new BigDecimal("0.95")).doubleValue();
    }
    public static int equipment(int base,Factors factors,String policyDigest,String defeatId){
        Objects.requireNonNull(defeatId);
        var mean=new BigDecimal(factors.quantity()).multiply(BigDecimal.valueOf(base)).min(BigDecimal.valueOf(16));
        return roundEquipment(mean,com.inigmasgames.hytalerpg.progress.RewardIntent.digest(policyDigest+"\n"+defeatId));
    }
    static int roundEquipment(BigDecimal mean,String seed){
        int floor=mean.intValue();var fraction=mean.subtract(BigDecimal.valueOf(floor)).stripTrailingZeros();
        if(fraction.signum()==0)return floor;
        var denominator=BigInteger.TEN.pow(fraction.scale());
        return floor+(new Me12Random(Me12Random.QUANTITY,seed).uniformBelow(denominator).compareTo(fraction.unscaledValue())<0?1:0);
    }
}
