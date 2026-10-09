package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Raw gates; does not change the existing attribute allocation or derived-stat owners. */
public final class GearRequirements {
    private GearRequirements() {}
    public record Gate(int level,Map<RpgAttribute,Integer> attributes) {
        public Gate {
            if(level<1 || level>99) throw new IllegalArgumentException("Invalid required level");
            attributes=Map.copyOf(attributes);
            if(attributes.values().stream().anyMatch(v->v<0)) throw new IllegalArgumentException("Negative requirement");
        }
        public List<String> failures(int actorLevel,Map<RpgAttribute,Integer> permanent,boolean armor) {
            var errors=new ArrayList<String>();
            if(actorLevel<level) errors.add("Requires Character Level "+level);
            for(var stat:RpgAttribute.values()) if(permanent.getOrDefault(stat,0)<attributes.getOrDefault(stat,0))
                errors.add("Insufficient "+stat+" to "+(armor?"don":"wield")+" this item");
            return List.copyOf(errors);
        }
        public List<RpgAttribute> missingAttributes(Map<RpgAttribute,Integer> permanent) {
            return Arrays.stream(RpgAttribute.values())
                    .filter(stat -> permanent.getOrDefault(stat, 0) < attributes.getOrDefault(stat, 0))
                    .toList();
        }
        public String playerFeedback(int actorLevel, Map<RpgAttribute,Integer> permanent) {
            var missing = missingAttributes(permanent).stream().map(stat -> switch (stat) {
                case STR -> "Strength";
                case DEX -> "Dexterity";
                case INT -> "Intelligence";
                case WIS -> "Wisdom";
                case LUCK -> "Luck";
            }).toList();
            if (!missing.isEmpty()) {
                String joined = missing.size() == 1 ? missing.get(0)
                        : missing.size() == 2 ? String.join(" and ", missing)
                        : String.join(", ", missing.subList(0, missing.size() - 1))
                                + ", and " + missing.get(missing.size() - 1);
                return "Insufficient " + joined;
            }
            return actorLevel < level ? "Requires level " + level : null;
        }
    }
    public static Gate combine(Gate base,List<Gate> affixes,BigDecimal localReduction) {
        if(localReduction==null || localReduction.signum()<0 || localReduction.compareTo(BigDecimal.ONE)>=0)
            throw new IllegalArgumentException("Invalid local requirement reduction");
        var attributes=new EnumMap<RpgAttribute,Integer>(RpgAttribute.class); attributes.putAll(base.attributes());
        int level=base.level();
        for(var affix:affixes) {
            level=Math.max(level,affix.level());
            affix.attributes().forEach((stat,value)->attributes.merge(stat,value,Math::max));
        }
        // Diversity is tested before reduction. Never discard a fourth requirement after generation.
        if(attributes.values().stream().filter(v->v>10).count()>3) throw new IllegalArgumentException("More than three above-baseline gates");
        attributes.replaceAll((stat,value)->value==0?0:Math.max(10,BigDecimal.valueOf(value)
                .multiply(BigDecimal.ONE.subtract(localReduction)).setScale(0,RoundingMode.CEILING).intValueExact()));
        return new Gate(level,attributes);
    }
    public record Equipped(UUID identity,Gate gate,Map<RpgAttribute,Integer> permanentBonuses) {
        public Equipped {
            Objects.requireNonNull(identity); Objects.requireNonNull(gate); permanentBonuses=Map.copyOf(permanentBonuses);
            if(permanentBonuses.values().stream().anyMatch(v->v<0)) throw new IllegalArgumentException("Negative gear attribute bonus unsupported");
        }
    }
    public record Validity(Set<UUID> valid,Map<RpgAttribute,Integer> permanentAttributes) {}
    /** Fresh least fixed point. No candidate/self bonus, temporary buff, old result, or circular bootstrap. */
    public static Validity resolve(int level,Map<RpgAttribute,Integer> baseline,List<Equipped> equipped) {
        var valid=new LinkedHashSet<UUID>(); var attrs=new EnumMap<RpgAttribute,Integer>(RpgAttribute.class); attrs.putAll(baseline);
        if(baseline.values().stream().anyMatch(v->v<0)) throw new IllegalArgumentException("Negative baseline");
        if(equipped.stream().map(Equipped::identity).distinct().count()!=equipped.size()) throw new IllegalArgumentException("Duplicate equipped identity");
        boolean changed;
        do {
            changed=false;
            for(var item:equipped) if(!valid.contains(item.identity()) && item.gate().failures(level,attrs,false).isEmpty()) {
                valid.add(item.identity()); item.permanentBonuses().forEach((stat,value)->attrs.merge(stat,value,Math::addExact)); changed=true;
            }
        } while(changed);
        return new Validity(Set.copyOf(valid),Map.copyOf(attrs));
    }
}
