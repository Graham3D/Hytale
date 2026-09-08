package com.inigmasgames.hytalerpg.combat.hytale;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.RegeneratingValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.util.function.DoubleSupplier;

/** Decorates the live per-player native regeneration entries, preserving their timer and Conditions.
 * Native Regenerate remains the only scheduler/writer. Does not touch Health/Stamina or recovery calls. */
public final class NativeManaRegenerationAdapter {
    private NativeManaRegenerationAdapter(){}
    public static void install(EntityStatMap stats,DoubleSupplier manaRegenIncreased){
        var mana=stats.get(DefaultEntityStatTypes.getMana());
        if(mana==null||mana.getRegeneratingValues()==null)return;
        var entries=mana.getRegeneratingValues();
        for(int i=0;i<entries.length;i++){
            if(entries[i] instanceof Entry existing){existing.increased=manaRegenIncreased;continue;}
            if(entries[i].getRegenerating().getAmount()>0)entries[i]=new Entry(entries[i],manaRegenIncreased);
        }
    }
    public static final class Entry extends RegeneratingValue {
        private final RegeneratingValue delegate;
        private DoubleSupplier increased;
        public Entry(RegeneratingValue delegate,DoubleSupplier increased){super(delegate.getRegenerating());this.delegate=delegate;this.increased=increased;}
        @Override public float regenerate(ComponentAccessor<EntityStore> accessor,Ref<EntityStore> ref,Instant now,
                                           float delta,EntityStatValue stat,float accumulated){
            // Exactly one call keeps the existing native phase/condition state. Positive raw deltas are not
            // maximum-clamped by RegeneratingValue in 0.7.0-pre.1; native addStatValue performs that clamp.
            float nativeDelta=delegate.regenerate(accessor,ref,now,delta,stat,accumulated);
            if(nativeDelta<=0)return nativeDelta;
            double bonus=increased.getAsDouble();
            if(!Double.isFinite(bonus)||bonus<0)throw new IllegalStateException("Invalid Mana regeneration Increased bucket");
            double normalization=1;
            if(getRegenerating().getRegenType()==EntityStatType.Regenerating.RegenType.PERCENTAGE){
                double span=stat.getMax()-stat.getMin();
                normalization=span>0?(NativeManaReservationProjection.totalMaximum(stat)-stat.getMin())/span:0;
            }
            return (float)(nativeDelta*normalization*(1+bonus));
        }
    }
}
