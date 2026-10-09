package com.inigmasgames.hytalerpg.combat.hytale;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.RegeneratingValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.status.StatusService;
import java.time.Instant;
import java.util.UUID;

/** Decorates positive native Health regeneration; native timing and conditions remain authoritative. */
public final class NativeHealthRegenerationSuppression {
    private NativeHealthRegenerationSuppression() { }
    public static void install(EntityStatMap stats,UUID target,StatusService statuses) {
        if(stats==null||target==null||statuses==null)throw new IllegalArgumentException("INVALID_HEALTH_REGEN_OWNER");
        var health=stats.get(DefaultEntityStatTypes.getHealth());
        if(health==null||health.getRegeneratingValues()==null)return;
        var entries=health.getRegeneratingValues();
        for(int i=0;i<entries.length;i++) {
            if(entries[i] instanceof Entry existing){existing.target=target;existing.statuses=statuses;continue;}
            if(entries[i].getRegenerating().getAmount()>0)entries[i]=new Entry(entries[i],target,statuses);
        }
    }
    public static final class Entry extends RegeneratingValue {
        private final RegeneratingValue delegate;
        private UUID target;
        private StatusService statuses;
        public Entry(RegeneratingValue delegate,UUID target,StatusService statuses) {
            super(delegate.getRegenerating());this.delegate=delegate;this.target=target;this.statuses=statuses;
        }
        @Override public float regenerate(ComponentAccessor<EntityStore> accessor,Ref<EntityStore> ref,Instant now,
                                           float delta,EntityStatValue stat,float accumulated) {
            float nativeAmount=delegate.regenerate(accessor,ref,now,delta,stat,accumulated);
            return nativeAmount<=0?nativeAmount:(float)(nativeAmount*statuses.healthRegenerationFactor(target));
        }
    }
}
