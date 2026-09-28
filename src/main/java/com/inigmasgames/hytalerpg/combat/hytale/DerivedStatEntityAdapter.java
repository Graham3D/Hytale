package com.inigmasgames.hytalerpg.combat.hytale;

import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.inigmasgames.hytalerpg.combat.attribute.DerivedStats;

/** Projects RPG maxima into native EntityStatMap modifiers while preserving current percentage. */
public final class DerivedStatEntityAdapter {
    private static final String GEAR_KEY = "hytalerpg:gear-capacity";
    private static final String HEALTH_KEY = "hytalerpg:derived-max-health";
    private static final String STAMINA_KEY = "hytalerpg:derived-max-stamina";
    private static final String MANA_KEY = "hytalerpg:derived-max-mana";

    public void apply(EntityStatMap stats, DerivedStats derived) {
        applyOne(stats, DefaultEntityStatTypes.getHealth(), HEALTH_KEY, derived.maxHealth());
        applyOne(stats, DefaultEntityStatTypes.getStamina(), STAMINA_KEY, derived.maxStamina());
        int manaIndex=DefaultEntityStatTypes.getMana();
        var mana=stats.get(manaIndex);
        boolean reserved=mana!=null&&mana.getModifier(NativeManaReservationProjection.KEY)!=null;
        double priorCurrent=mana==null?0:mana.get();
        double priorReserved=reserved?NativeManaReservationProjection.reserved(mana):0;
        if(reserved)NativeManaReservationProjection.project(stats,0);
        applyOne(stats, manaIndex, MANA_KEY, derived.maxMana());
        if(reserved){
            // Updating INT cannot turn a capacity reservation into a refill or feed spendable max back into total.
            stats.setStatValue(manaIndex,(float)Math.min(priorCurrent,stats.get(manaIndex).getMax()));
            NativeManaReservationProjection.project(stats,Math.min(priorReserved,stats.get(manaIndex).getMax()));
        }
    }
    private static void applyOne(EntityStatMap stats, int index, String key, double desiredMaximum) {
        EntityStatValue before = stats.get(index);
        if (before == null) throw new IllegalStateException("EntityStatMap is missing required stat index " + index);
        var gear=before.getModifier(GEAR_KEY);
        double priorCurrent=before.get();
        if(gear instanceof StaticModifier modifier) desiredMaximum+=modifier.getAmount()*NativeManaReservationProjection.maximumMultiplier(before);
        double percentage = before.getMax() <= 0.0 ? 1.0 : before.get() / before.getMax();
        stats.removeModifier(index, key);
        stats.update();
        EntityStatValue base = stats.get(index);
        float additive = (float) (desiredMaximum - base.getMax());
        stats.putModifier(index, key, new StaticModifier(Modifier.ModifierTarget.MAX,
                StaticModifier.CalculationType.ADDITIVE, additive));
        stats.update();
        EntityStatValue after = stats.get(index);
        stats.setStatValue(index, (float) Math.max(after.getMin(), Math.min(after.getMax(), gear==null?percentage * after.getMax():priorCurrent)));
    }

    /** Gear adds native capacity without granting resource. Existing reservations remain authoritative. */
    public static void applyGearCapacity(EntityStatMap stats,double health,double mana,double stamina) {
        gearCapacity(stats,DefaultEntityStatTypes.getHealth(),health);
        var nativeMana=stats.get(DefaultEntityStatTypes.getMana());
        double reserved=nativeMana==null?0:NativeManaReservationProjection.reserved(nativeMana);
        var manaModifier=nativeMana==null?null:nativeMana.getModifier(GEAR_KEY);
        double manaAmount=nativeMana==null?0:mana/NativeManaReservationProjection.maximumMultiplier(nativeMana);
        boolean manaChanged=nativeMana!=null && !(manaModifier==null&&mana==0 || manaModifier instanceof StaticModifier prior&&prior.getAmount()==(float)manaAmount);
        if(manaChanged) {
            if(reserved>0) NativeManaReservationProjection.project(stats,0);
            gearCapacity(stats,DefaultEntityStatTypes.getMana(),mana);
            if(reserved>0) NativeManaReservationProjection.project(stats,Math.min(reserved,stats.get(DefaultEntityStatTypes.getMana()).getMax()));
        }
        gearCapacity(stats,DefaultEntityStatTypes.getStamina(),stamina);
    }
    private static void gearCapacity(EntityStatMap stats,int index,double amount) {
        if(!Double.isFinite(amount)||amount<0) throw new IllegalArgumentException("Invalid gear pool capacity");
        var stat=stats.get(index); if(stat==null) return;
        amount/=NativeManaReservationProjection.maximumMultiplier(stat);
        var old=stat.getModifier(GEAR_KEY);
        if(old==null&&amount==0 || old instanceof StaticModifier modifier&&modifier.getAmount()==(float)amount) return;
        float current=stat.get();
        // Retain a zero marker once managed gear has participated: later allocation/respec must not refill it.
        stats.putModifier(index,GEAR_KEY,new StaticModifier(Modifier.ModifierTarget.MAX,StaticModifier.CalculationType.ADDITIVE,(float)amount));
        stats.update(); stats.setStatValue(index,Math.max(stats.get(index).getMin(),Math.min(current,stats.get(index).getMax())));
    }
}
