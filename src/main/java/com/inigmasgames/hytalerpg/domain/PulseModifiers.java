package com.inigmasgames.hytalerpg.domain;

import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import java.util.List;

/** Pulse magnitude is component-scoped; constant Aura benefits and resource clocks are not pulses. */
public record PulseModifiers(boolean rapidPulse) {
    public static PulseModifiers from(List<PassiveId> order){return new PulseModifiers(order.stream().anyMatch(p->p.value().equals("rapid_pulse")));}
    public SkillExecutionContext payload(SkillExecutionContext context){
        return rapidPulse?context.withSnapshot(context.snapshot().withMagnitudeFactor(.8)):context;
    }
}
