package com.inigmasgames.hytalerpg.combat.hytale;

import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;

/** Direct adapter over Hytale's live EntityStatMap. */
public final class EntityStatResourcePort implements NativeResourcePort {
    private final EntityStatMap stats;
    public EntityStatResourcePort(EntityStatMap stats) { this.stats = stats; }
    @Override public double current(ResourceType type) { return value(type).get(); }
    @Override public double maximum(ResourceType type) {
        return type==ResourceType.MANA?NativeManaReservationProjection.totalMaximum(value(type)):value(type).getMax();
    }
    @Override public void setReservedMana(double amount){NativeManaReservationProjection.project(stats,amount);}
    @Override public void setCurrent(ResourceType type, double value) {
        int index = index(type);
        EntityStatValue stat = require(stats.get(index), type);
        if(!Double.isFinite(value))throw new IllegalArgumentException("Non-finite native resource value");
        if(type==ResourceType.HEALTH)com.inigmasgames.hytalerpg.combat.resource.RpgResourceService.nativeHealthTarget(stat.get(),value);
        stats.setStatValue(index, (float) Math.max(stat.getMin(), Math.min(stat.getMax(), value)));
    }
    private EntityStatValue value(ResourceType type) { return require(stats.get(index(type)), type); }
    private static EntityStatValue require(EntityStatValue value, ResourceType type) {
        if (value == null) throw new IllegalStateException("Hytale EntityStatMap lacks " + type);
        return value;
    }
    private static int index(ResourceType type) {
        return switch (type) {
            case MANA -> DefaultEntityStatTypes.getMana();
            case STAMINA -> DefaultEntityStatTypes.getStamina();
            case HEALTH -> DefaultEntityStatTypes.getHealth();
            case NONE -> throw new IllegalArgumentException("NONE has no Hytale EntityStat");
        };
    }
}
