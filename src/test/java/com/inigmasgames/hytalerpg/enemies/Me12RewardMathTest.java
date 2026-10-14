package com.inigmasgames.hytalerpg.enemies;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Me12RewardMathTest {
    static EnemyRewardContext context(int count,int threat){
        var p=Me12Policies.canonical();
        return new EnemyRewardContext(p.balance().revision(),EnemyRarity.UNIQUE,EnemyRewardContext.Origin.NATURAL,false,count,
                2,1.5,1.35,.10,.15,.15,new Me12Identity.RewardReference(2,p.digest(),p.references(),threat,count));
    }
    @Test void R01AllAd1FactorsAndLearningFixtures(){
        int[] counts={2,4,6,7,8},threat={200,465,680,810,940};
        double[] xp={2.300,3.095,3.740,4.130,4.520},quantity={1.725,2.32125,2.805,3.075,3.075};
        for(int i=0;i<counts.length;i++){var c=context(counts[i],threat[i]);assertEquals(xp[i],c.xpFactor(),1e-12);assertEquals(quantity[i],c.quantityFactor(),1e-12);}
        double[] base={.15,.05,.02},low={.22275,.07425,.0297},high={.2325,.0975,.04968};
        for(int i=0;i<base.length;i++){assertEquals(low[i],context(2,200).learningChance(base[i]),1e-12);assertEquals(high[i],context(8,940).learningChance(base[i]),1e-12);}
        assertEquals(0,context(8,940).learningChance(0));assertEquals(.95,context(8,940).learningChance(.95));
    }
    @Test void R02R03BoundsMonotonicityAndDeterministicQuantity(){
        double prior=0,priorQuantity=0,priorLearning=0;
        for(int t=800;t<=1200;t++){var c=context(8,t);assertTrue(c.xpFactor()>=prior);prior=c.xpFactor();assertTrue(c.quantityFactor()>=priorQuantity);priorQuantity=c.quantityFactor();assertTrue(c.learningChance(.05)>=priorLearning);priorLearning=c.learningChance(.05);assertTrue(c.xpFactor()<=4.7);assertTrue(c.quantityFactor()<=3.075);c.validateEquipmentMaximum(16);}
        assertEquals(4.7,prior);var capped=context(8,940);
        for(int k=0;k<=16;k++)for(int i=0;i<100;i++){int n=k+capped.bonusEquipmentSlots(k,"quantity/"+i);assertTrue(n>=k&&n<=16);assertEquals(n,k+capped.bonusEquipmentSlots(k,"quantity/"+i));if(k==0)assertEquals(0,n);if(k>=6)assertEquals(16,n);}
    }
    @Test void R01FractionalFixturesHavePredeterminedProbabilities(){
        int[] bases={1,1,2,5,6};double[] up={.725,.075,.15,.375,0};int[] floors={1,3,6,15,16};
        for(int row=0;row<bases.length;row++){var c=row==0?context(2,200):context(8,940);int raised=0;
            for(int i=0;i<100000;i++){int n=bases[row]+c.bonusEquipmentSlots(bases[row],"fixed/"+i);assertTrue(n==floors[row]||up[row]>0&&n==floors[row]+1);if(n>floors[row])raised++;}
            assertTrue(Math.abs(raised-100000*up[row])<=7*Math.sqrt(100000*up[row]*(1-up[row]))+7,"row "+row+" observed "+raised);
        }
    }
}
