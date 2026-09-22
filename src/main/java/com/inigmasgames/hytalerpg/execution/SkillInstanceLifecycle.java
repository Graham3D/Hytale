package com.inigmasgames.hytalerpg.execution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Instance-keyed action ownership. Active effects may coexist; only action-lock phases gate a new cast. */
public final class SkillInstanceLifecycle {
    private final Map<UUID, LinkedHashMap<String,Active>> active = new HashMap<>();
    public synchronized boolean begin(UUID actor, String instanceId, Phase phase) {
        var values=active.computeIfAbsent(actor,ignored->new LinkedHashMap<>());
        if(values.containsKey(instanceId)||values.values().stream().anyMatch(value->value.phase.blocksNewCast()))return false;
        values.put(instanceId,new Active(instanceId,phase));return true;
    }
    public synchronized boolean transition(UUID actor, String instanceId, Phase expected, Phase next) {
        var values=active.get(actor);Active current=values==null?null:values.get(instanceId);
        if (current == null || current.phase != expected) return false;
        values.put(instanceId, new Active(instanceId, next)); return true;
    }
    public synchronized boolean terminate(UUID actor, String instanceId) {
        var values=active.get(actor);if(values==null||values.remove(instanceId)==null)return false;
        if(values.isEmpty())active.remove(actor);return true;
    }
    public synchronized List<Active> cancelAll(UUID actor) {
        var values=active.remove(actor);return values==null?List.of():List.copyOf(new ArrayList<>(values.values()));
    }
    public synchronized Optional<Active> active(UUID actor) {
        var values=active.get(actor);if(values==null)return Optional.empty();
        return values.values().stream().filter(value->value.phase.blocksNewCast()).findFirst()
                .or(()->values.values().stream().findFirst());
    }
    public synchronized boolean owns(UUID actor,String instanceId){var values=active.get(actor);return values!=null&&values.containsKey(instanceId);}
    public synchronized List<Active> all(UUID actor){var values=active.get(actor);return values==null?List.of():List.copyOf(values.values());}
    public enum Phase {
        WINDUP(true), COMMITTED(true), STRIKE_REPEAT(true), MOVEMENT(true), REACTION(true), PROJECTILE(false), CHANNEL(true);
        private final boolean blocksNewCast;Phase(boolean blocksNewCast){this.blocksNewCast=blocksNewCast;}
        public boolean blocksNewCast(){return blocksNewCast;}
    }
    public record Active(String instanceId, Phase phase) { }
}
