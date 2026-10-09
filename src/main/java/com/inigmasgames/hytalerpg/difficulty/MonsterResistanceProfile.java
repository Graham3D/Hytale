package com.inigmasgames.hytalerpg.difficulty;

import java.util.*;

/** Authored elemental contract. Physical defense, status avoidance and control immunity have other owners. */
public record MonsterResistanceProfile(Map<Channel, Double> resistance, Set<Channel> immunities) {
    /** Legacy constants are retained for checksum-stable reads; all consumers resolve canonical semantics. */
    public enum Channel {
        FIRE, WATER, LIGHTNING, WIND, VOID, EARTH, COLD, NATURE, NECROTIC, POISON;
        public Channel canonical(){return switch(this){case COLD->WATER;case NATURE,POISON->EARTH;case NECROTIC->VOID;default->this;};}
    }
    public static final double CAP = .75;
    public static final MonsterResistanceProfile NONE = new MonsterResistanceProfile(Map.of(), Set.of());
    public MonsterResistanceProfile {
        var checked = new EnumMap<Channel, Double>(Channel.class);
        var canonicalValues = new EnumMap<Channel, Double>(Channel.class);
        resistance.forEach((channel, value) -> {
            if (value == null || !Double.isFinite(value) || value < 0) throw new IllegalArgumentException("INVALID_RESISTANCE");
            var canonical=Objects.requireNonNull(channel).canonical();
            var previous=canonicalValues.putIfAbsent(canonical,value);
            if(previous!=null&&Double.compare(previous,value)!=0)
                throw new IllegalArgumentException("CONFLICTING_LEGACY_CHANNEL_VALUES:"+canonical);
            checked.put(channel,value);
        });
        resistance = Collections.unmodifiableMap(checked);
        var ordered=EnumSet.noneOf(Channel.class);ordered.addAll(immunities);immunities=Collections.unmodifiableSet(ordered);
    }
    public double raw(Channel channel){var canonical=Objects.requireNonNull(channel).canonical();
        return resistance.entrySet().stream().filter(e->e.getKey().canonical()==canonical).mapToDouble(Map.Entry::getValue).findFirst().orElse(0);}
    public double effective(Channel channel) { return Math.min(CAP,raw(channel)); }
    public boolean immune(Channel channel){var canonical=Objects.requireNonNull(channel).canonical();return immunities.stream().anyMatch(c->c.canonical()==canonical);}
    /** Compose the affinity floor and additive providers in this existing owner, before the ordinary cap. */
    public MonsterResistanceProfile withProviders(Map<Channel,Double> floors,Map<Channel,Double> additions,Set<Channel> grants){
        var result=new EnumMap<Channel,Double>(Channel.class);resistance.forEach((channel,value)->result.put(channel.canonical(),value));
        var normalizedFloors=new MonsterResistanceProfile(floors,Set.of());
        normalizedFloors.resistance.forEach((channel,value)->result.merge(channel.canonical(),value,Math::max));
        var normalizedAdds=new MonsterResistanceProfile(additions,Set.of());
        // Collapse equal aliases before adding a provider; they are names for one value, not separate bonuses.
        normalizedAdds.resistance.keySet().stream().map(Channel::canonical).distinct().forEach(channel->{
            double baseline=Math.max(raw(channel),normalizedFloors.raw(channel));result.put(channel,baseline+normalizedAdds.raw(channel));
        });
        var flags=EnumSet.noneOf(Channel.class);immunities.forEach(c->flags.add(c.canonical()));grants.forEach(c->flags.add(c.canonical()));
        return new MonsterResistanceProfile(result,flags);
    }
    /** Pure preview/adapter seam, not a second native Health mutation or an automatic damage filter. */
    public Mitigation resolve(Channel channel, double amount) {
        return resolve(channel, amount, 0);
    }
    /** Penetration belongs to this direct hit and is subtracted in the single authored mitigation pass. */
    public Mitigation resolve(Channel channel, double amount, double penetration) {
        Objects.requireNonNull(channel);
        if (!Double.isFinite(amount) || amount < 0 || !Double.isFinite(penetration) || penetration < 0)
            throw new IllegalArgumentException("INVALID_DAMAGE_OR_PENETRATION");
        return immune(channel) ? new Mitigation(0, "AUTHORED_ELEMENTAL_IMMUNITY")
                : new Mitigation(amount * (1 - Math.max(0, effective(channel)-penetration)), "ORDINARY_RESISTANCE");
    }
    public record Mitigation(double amount, String reason) {}
}
