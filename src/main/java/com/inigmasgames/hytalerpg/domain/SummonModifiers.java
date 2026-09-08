package com.inigmasgames.hytalerpg.domain;

import java.util.List;

/** Applies only to created summon components, never the caster or a corpse source. */
public record SummonModifiers(boolean swarm,boolean empowerment,boolean deathPact) {
    public static SummonModifiers from(List<PassiveId> order){return new SummonModifiers(
            order.stream().anyMatch(p->p.value().equals("swarm")),order.stream().anyMatch(p->p.value().equals("minion_empowerment")),
            order.stream().anyMatch(p->p.value().equals("death_pact")));}
    public int count(int base){return base+(swarm?1:0);}
    public double healthAndPowerFactor(){return (swarm?.75:1)*(empowerment?1.3:1);}
    public double lifetimeFactor(){return empowerment?.75:1;}
}
