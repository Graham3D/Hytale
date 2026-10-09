package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import java.util.*;

/** The equipment admission step used by the native slot reader and offline lifecycle scenarios. */
public final class GearEquipmentResolution {
    private GearEquipmentResolution() {}
    public record Candidate(GearInstance item, boolean equipped, boolean intact, boolean matchingSlot) {
        public Candidate { Objects.requireNonNull(item); }
    }
    public static final class Result {
        private final List<GearInstance> candidates;
        private final GearRequirements.Validity validity;
        private final Map<UUID,String> rejected;
        private final Set<String> capabilities;
        private Result(List<GearInstance> candidates, GearRequirements.Validity validity,
                       Map<UUID,String> rejected, Set<String> capabilities) {
            this.candidates=List.copyOf(candidates);
            this.validity=Objects.requireNonNull(validity);
            this.rejected=Map.copyOf(rejected);
            this.capabilities=Set.copyOf(capabilities);
        }
        public List<GearInstance> candidates() { return candidates; }
        public GearRequirements.Validity validity() { return validity; }
        public Map<UUID,String> rejected() { return rejected; }
        public List<GearInstance> validItems() {
            return candidates.stream().filter(g->validity.valid().contains(g.identity())).toList();
        }
        public GearAffixRuntime.Effects effects() { return GearAffixRuntime.effects(validItems(),capabilities); }
    }
    public static Result resolve(int level, Map<RpgAttribute,Integer> baseline, Collection<Candidate> slots) {
        return resolve(level,baseline,slots,GearAffixRuntime.ENABLED);
    }
    /** Candidate qualification only. Production callers use the public ENABLED-bound entry point. */
    static Result resolve(int level, Map<RpgAttribute,Integer> baseline, Collection<Candidate> slots,
                          Set<String> capabilities) {
        capabilities=Set.copyOf(capabilities);
        var counts=new HashMap<UUID,Integer>();
        for(var slot:slots)if(slot.equipped())counts.merge(slot.item().identity(),1,Integer::sum);
        var rejected=new LinkedHashMap<UUID,String>();
        var accepted=new ArrayList<GearInstance>();
        for(var slot:slots) {
            var item=slot.item();
            String reason=!slot.equipped()?"NOT_EQUIPPED":counts.getOrDefault(item.identity(),0)!=1?"DUPLICATE_IDENTITY"
                    :!slot.intact()?"BROKEN":!slot.matchingSlot()?"WRONG_SLOT"
                    :!GearAffixRuntime.supported(item,capabilities)?"CAPABILITY_GATED":null;
            if(reason==null)accepted.add(item);else rejected.put(item.identity(),reason);
        }
        var validity=GearRequirements.resolve(level,baseline,accepted.stream().map(g->
                new GearRequirements.Equipped(g.identity(),g.requirements(),GearAffixRuntime.attributes(g))).toList());
        for(var item:accepted)if(!validity.valid().contains(item.identity()))rejected.put(item.identity(),"UNMET_REQUIREMENTS");
        return new Result(accepted,validity,rejected,capabilities);
    }
}
