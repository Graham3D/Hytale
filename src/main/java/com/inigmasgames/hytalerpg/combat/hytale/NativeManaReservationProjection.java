package com.inigmasgames.hytalerpg.combat.hytale;

import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;

/** Gameplay capacity projection into native Mana. No HUD controls or visibility ownership. */
public final class NativeManaReservationProjection {
    public static final String KEY="hytalerpg:reserved-mana-capacity";
    private NativeManaReservationProjection(){}
    public static double totalMaximum(EntityStatValue mana){
        var own=mana.getModifier(KEY);
        if(own==null)return mana.getMax();
        if(!(own instanceof StaticModifier value)||value.getTarget()!=Modifier.ModifierTarget.MAX
                ||value.getCalculationType()!=StaticModifier.CalculationType.ADDITIVE)
            throw new IllegalStateException("Invalid owned Mana capacity modifier");
        return Math.max(0,mana.getMax()-value.getAmount()*maximumMultiplier(mana));
    }
    public static double reserved(EntityStatValue mana){return Math.max(0,totalMaximum(mana)-mana.getMax());}
    /** Native computeModifiers sums MULTIPLICATIVE amounts, then calls its own CalculationType.compute. */
    public static double maximumMultiplier(EntityStatValue mana){
        float sum=0;boolean found=false;
        if(mana.getModifiers()!=null)for(var modifier:mana.getModifiers().values()){
            if(modifier.getTarget()!=Modifier.ModifierTarget.MAX)continue;
            if(!(modifier instanceof StaticModifier value))throw new IllegalStateException("NON_STATIC_MANA_MAXIMUM_UNSUPPORTED");
            if(value.getCalculationType()==StaticModifier.CalculationType.MULTIPLICATIVE){sum+=value.getAmount();found=true;}
        }
        double factor=found?StaticModifier.CalculationType.MULTIPLICATIVE.compute(1,sum):1;
        if(!Double.isFinite(factor)||factor<=0)throw new IllegalStateException("NON_POSITIVE_MANA_MAXIMUM_MULTIPLIER");
        return factor;
    }
    public static void project(EntityStatMap stats,double reserved){
        int index=DefaultEntityStatTypes.getMana();var mana=stats.get(index);
        if(mana==null)throw new IllegalStateException("MANA_STAT_MISSING");
        double total=totalMaximum(mana),factor=maximumMultiplier(mana),before=mana.get();
        if(!Double.isFinite(reserved)||reserved<0||reserved>total+1e-4)throw new IllegalArgumentException("Invalid reserved Mana capacity");
        var prior=mana.getModifier(KEY);
        float amount=(float)(-reserved/factor);
        if(reserved==0){if(prior==null)return;stats.removeModifier(index,KEY);}
        else {
            if(prior instanceof StaticModifier old&&Math.abs(old.getAmount()-amount)<1e-6)return;
            stats.putModifier(index,KEY,new StaticModifier(Modifier.ModifierTarget.MAX,StaticModifier.CalculationType.ADDITIVE,amount));
        }
        stats.update();
        if(Math.abs(stats.get(index).getMax()-(total-reserved))>1e-3)
            throw new IllegalStateException("NATIVE_MANA_CAPACITY_PROJECTION_MISMATCH");
        // Releasing the modifier raises only capacity. Never restore a percentage or reserved current Mana.
        stats.setStatValue(index,(float)Math.max(0,Math.min(before,stats.get(index).getMax())));
    }
}
