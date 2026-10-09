package com.inigmasgames.hytalerpg.gear;

import java.util.*;

/** Immutable, source-preserving input to existing gameplay owners. Values are frozen item units.
 * Construction receives ONLY equipment accepted by GearRequirements, never backpack contents.
 * This record does not itself establish capability: production admission remains independently gated. */
public final class GearEffectSnapshot {
    public enum Operator {
        ALL_RESISTANCE, ARMOR_BREAK, ATTACK_DAMAGE, ATTRIBUTE, BARRIER_STRENGTH, BLOCK_COST,
        CAST_SPEED, CHANNEL_COST, CHANNEL_RAMP, CLEANSE_RESPITE, CONDITIONAL_DAMAGE,
        CONTROL_DURATION_TAKEN, CONVERSION, COOLDOWN_RECOVERY, CRIT_CHANCE, CRIT_DAMAGE_TAKEN,
        CRIT_MULTIPLIER, CRUSHING_BLOW, CULL, DAMAGING_STATUS_DURATION, DOT_DAMAGE,
        DOUBLE_HIT_DAMAGE, DUAL_STRIKE, DURABILITY_EFFICIENCY, ELEMENT_DAMAGE,
        ELEMENT_PENETRATION, ELEMENTAL_DAMAGE, FINITE_SUPPORT_DURATION, FORTIFYING_HIT,
        GLOBAL_DEFENSE, GOLD_FIND, HEALING_DONE, HEALING_POWER, HEALING_RECEIVED, IMPALE,
        ITEM_AURA, ITEM_GRANT, ITEM_TRIGGER, KILL_BURST, KNOCKBACK_TAKEN, LIFE_LEECH,
        LIFE_ON_HIT, LIFE_ON_KILL, LIGHT_RADIUS, LOCAL_ARMOR_FLAT, LOCAL_ARMOR_INCREASED,
        LOCAL_ATTACK_SPEED, LOCAL_ELEMENT_FLAT, LOCAL_MAGIC_FLAT, LOCAL_PHYS_FLAT,
        LOCAL_PHYS_INC, LOCAL_PHYS_MAX, LOCAL_PHYS_MIN, MAGIC_FIND, MANA_COST, MANA_LEECH,
        MANA_ON_HIT, MANA_REGEN, MAX_HEALTH, MAX_MANA, MAX_STAMINA, MELEE_REACH,
        MINION_ATTACK_SPEED, MINION_DAMAGE, MINION_DEFENSE, MINION_DURATION, MINION_HEALTH,
        MINION_MOVEMENT, MINION_RESISTANCE, PHYSICAL_DAMAGE_GLOBAL, PICKUP_RADIUS,
        PROJECTILE_RANGE, PROJECTILE_SPEED, REDUCED_REQUIREMENTS, RESIST_EARTH, RESIST_FIRE,
        RESIST_LIGHTNING, RESIST_VOID, RESIST_WATER, RESIST_WIND, SHIELD_DEFENSE_FLAT,
        SHIELD_DEFENSE_INC, SKILLER, SLOW_EFFECT_TAKEN, SPELL_DAMAGE, STAMINA_COST,
        STAMINA_REGEN, STATUS_PAYLOAD, STATUS_PENETRATION, STATUS_POTENCY,
        STATUS_RESISTANCE, SUMMON_COST, TETHER_REACH, THORNS
    }
    public record Source(UUID itemId, boolean qaOnly, GearCatalog.Affix definition,
                         GearInstance.AffixRoll roll) {
        public Source { Objects.requireNonNull(itemId); Objects.requireNonNull(definition); Objects.requireNonNull(roll); }
        public String affixId() { return roll.familyId(); }
        public double value() { return roll.value(); }
        public double fraction() { return value()/100.0; }
        public Operator operator() { return Operator.valueOf(definition.operator()); }
    }
    private static final GearCatalog CATALOG=GearCatalog.load();
    public static final GearEffectSnapshot EMPTY=new GearEffectSnapshot(List.of());
    private final List<GearInstance> items;
    private final Map<Operator,List<Source>> sources;
    private final Map<String,Double> values;
    private final Map<Operator,Double> operatorTotals;
    private final Map<UUID,GearEffectSnapshot> localSnapshots;
    private final String revision;

    public GearEffectSnapshot(Collection<GearInstance> validEquipment) {
        var unique=new LinkedHashMap<UUID,GearInstance>();
        for(var item:validEquipment) {
            Objects.requireNonNull(item);
            if(unique.putIfAbsent(item.identity(),item)!=null)
                throw new IllegalArgumentException("DUPLICATE_EQUIPMENT_IDENTITY");
        }
        items=List.copyOf(unique.values());
        var groups=new EnumMap<Operator,List<Source>>(Operator.class);
        var totals=new TreeMap<String,Double>();
        var revisionParts=new StringBuilder("master-affix-v1/");
        for(var item:items.stream().sorted(Comparator.comparing(g->g.identity().toString())).toList()) {
            revisionParts.append(canonical(com.google.gson.JsonParser.parseString(item.toJson())));
            for(var roll:item.affixes()) {
                var source=new Source(item.identity(),item.qaOnly(),CATALOG.affix(roll.familyId()),roll);
                groups.computeIfAbsent(source.operator(),ignored->new ArrayList<>()).add(source);
                totals.merge(source.affixId(),source.value(),Double::sum);
            }
        }
        groups.replaceAll((operator,list)->List.copyOf(list));
        sources=Collections.unmodifiableMap(groups); values=Map.copyOf(totals);
        var sums=new EnumMap<Operator,Double>(Operator.class);
        groups.forEach((operator,list)->sums.put(operator,list.stream().mapToDouble(Source::value).sum()));
        operatorTotals=Collections.unmodifiableMap(sums);
        var locals=new HashMap<UUID,GearEffectSnapshot>();
        if(items.size()>1)for(var item:items)locals.put(item.identity(),new GearEffectSnapshot(List.of(item)));
        localSnapshots=Map.copyOf(locals);
        revision=com.inigmasgames.hytalerpg.progress.RewardIntent.digest(revisionParts.toString());
    }
    /** Object/map iteration order is not item identity; reload must preserve accepted revisions. */
    private static com.google.gson.JsonElement canonical(com.google.gson.JsonElement value) {
        if (value.isJsonObject()) {
            var sorted=new com.google.gson.JsonObject();
            value.getAsJsonObject().keySet().stream().sorted().forEach(key ->
                    sorted.add(key,canonical(value.getAsJsonObject().get(key))));
            return sorted;
        }
        if (value.isJsonArray()) {
            var array=new com.google.gson.JsonArray();
            for(var child:value.getAsJsonArray()) array.add(canonical(child));
            return array;
        }
        return value;
    }
    public List<GearInstance> items() { return items; }
    public String revision() { return revision; }
    public List<Source> sources(Operator operator) { return sources.getOrDefault(operator,List.of()); }
    public double total(Operator operator) { return operatorTotals.getOrDefault(operator,0.0); }
    public double percent(Operator operator) { return total(operator)/100.0; }
    public double value(String affixId) { return values.getOrDefault(affixId,0.0); }
    public double percent(String affixId) { return value(affixId)/100.0; }
    public GearEffectSnapshot forItem(UUID itemId) {
        if(items.size()==1&&items.getFirst().identity().equals(itemId))return this;
        return localSnapshots.getOrDefault(itemId,EMPTY);
    }
    public boolean empty() { return items.isEmpty(); }
    @Override public boolean equals(Object value) { return value instanceof GearEffectSnapshot other && items.equals(other.items); }
    @Override public int hashCode() { return items.hashCode(); }
}
