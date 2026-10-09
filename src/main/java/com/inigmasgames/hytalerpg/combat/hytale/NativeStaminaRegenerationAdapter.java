package com.inigmasgames.hytalerpg.combat.hytale;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.RegeneratingValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.util.function.DoubleSupplier;

/** Scales only native positive passive Stamina regeneration, retaining native delay and conditions. */
public final class NativeStaminaRegenerationAdapter {
    private NativeStaminaRegenerationAdapter() { }
    public static void install(EntityStatMap stats, DoubleSupplier increased) {
        var stamina=stats.get(DefaultEntityStatTypes.getStamina());
        if(stamina==null||stamina.getRegeneratingValues()==null)return;
        var entries=stamina.getRegeneratingValues();
        for(int i=0;i<entries.length;i++){
            if(entries[i] instanceof Entry existing){existing.increased=increased;continue;}
            if(entries[i].getRegenerating().getAmount()>0)entries[i]=new Entry(entries[i],increased);
        }
    }
    public static final class Entry extends RegeneratingValue {
        private final RegeneratingValue delegate;
        private DoubleSupplier increased;
        public Entry(RegeneratingValue delegate,DoubleSupplier increased){super(delegate.getRegenerating());this.delegate=delegate;this.increased=increased;}
        @Override public float regenerate(ComponentAccessor<EntityStore> accessor,Ref<EntityStore> ref,Instant now,
                                          float delta,EntityStatValue stat,float accumulated){
            float amount=delegate.regenerate(accessor,ref,now,delta,stat,accumulated);
            if(amount<=0)return amount;
            double bonus=increased.getAsDouble();
            if(!Double.isFinite(bonus)||bonus<0)throw new IllegalStateException("Invalid Stamina regeneration increase");
            return (float)(amount*(1+bonus));
        }
    }
}
