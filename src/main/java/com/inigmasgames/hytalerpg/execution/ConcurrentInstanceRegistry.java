package com.inigmasgames.hytalerpg.execution;

import com.inigmasgames.hytalerpg.domain.ConcurrentInstancePolicy;
import java.util.*;

/** Bounded, instance-exact owner for authored singleton/MAX_N active-effect policies. */
public final class ConcurrentInstanceRegistry {
    private static final int GLOBAL_CAP=4096;
    private final Map<Key,LinkedHashSet<String>> active=new HashMap<>();
    public synchronized String admission(UUID owner,String skill,ConcurrentInstancePolicy policy){
        int count=count(owner,skill);return switch(policy.mode()){
            case UNRESTRICTED, REPLACE_EXISTING -> "PASS";
            case COMMAND_EXISTING -> count==0?"PASS":"INSTANCE_COMMAND_REQUIRED";
            case SINGLETON -> count==0?"PASS":"INSTANCE_LIMIT_REACHED";
            case MAX_N -> count<policy.maximum()?"PASS":"INSTANCE_LIMIT_REACHED";
        };
    }
    public synchronized void activate(UUID owner,String skill,String instance,ConcurrentInstancePolicy policy){
        if(policy.mode()==ConcurrentInstancePolicy.Mode.UNRESTRICTED)return;
        String verdict=admission(owner,skill,policy);
        if(!verdict.equals("PASS")&&policy.mode()!=ConcurrentInstancePolicy.Mode.REPLACE_EXISTING)throw new IllegalStateException(verdict);
        if(size()>=GLOBAL_CAP)throw new IllegalStateException("GLOBAL_INSTANCE_BUDGET_REJECTED");
        var values=active.computeIfAbsent(new Key(owner,skill),ignored->new LinkedHashSet<>());
        if(policy.mode()==ConcurrentInstancePolicy.Mode.REPLACE_EXISTING)values.clear();
        if(!values.add(instance))throw new IllegalStateException("DUPLICATE_ACTIVE_INSTANCE");
    }
    public synchronized boolean terminate(UUID owner,String skill,String instance){var key=new Key(owner,skill);var values=active.get(key);
        if(values==null||!values.remove(instance))return false;if(values.isEmpty())active.remove(key);return true;}
    public synchronized int count(UUID owner,String skill){var values=active.get(new Key(owner,skill));return values==null?0:values.size();}
    public synchronized void cancel(UUID owner){active.keySet().removeIf(key->key.owner.equals(owner));}
    public synchronized int size(){return active.values().stream().mapToInt(Set::size).sum();}
    private record Key(UUID owner,String skill){Key{Objects.requireNonNull(owner);if(skill==null||skill.isBlank())throw new IllegalArgumentException("INSTANCE_SKILL_MISSING");}}
}
