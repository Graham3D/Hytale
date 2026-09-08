package com.inigmasgames.hytalerpg.domain;
import java.util.List;
import java.util.Set;
/** Victim conditions are evaluated at native Gather, never sampled while compiling or committing. */
public record HitConditionModifiers(boolean executioner,boolean opportunist){
    public static HitConditionModifiers from(List<PassiveId> passives){
        return new HitConditionModifiers(passives.stream().anyMatch(p->p.value().equals("executioner")),passives.stream().anyMatch(p->p.value().equals("opportunist")));
    }
    public boolean active(){return executioner||opportunist;}
    public double increased(double health,double maximum,Set<String> statuses){
        boolean low=Double.isFinite(health)&&Double.isFinite(maximum)&&health>0&&maximum>0&&health<maximum*.30;
        boolean controlled=statuses.stream().anyMatch(Set.of("ROOT","FROZEN","STUN","FEAR")::contains);
        return (executioner&&low?.35:0)+(opportunist&&controlled?.25:0);
    }
}
