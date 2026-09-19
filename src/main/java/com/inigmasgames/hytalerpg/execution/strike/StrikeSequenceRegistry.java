package com.inigmasgames.hytalerpg.execution.strike;

import java.util.*;

/** World-thread owner of bounded authored sequences. Derived releases cannot overwrite a live paid pair. */
public final class StrikeSequenceRegistry<T> {
    private record Entry<T>(UUID actor,T value){}
    private final Map<String,Entry<T>> entries=new LinkedHashMap<>();
    public void put(UUID actor,String execution,T value){
        Objects.requireNonNull(actor);Objects.requireNonNull(value);
        if(execution==null||execution.isBlank()||entries.containsKey(execution))throw new IllegalStateException("DUPLICATE_STRIKE_SEQUENCE");
        if(entries.size()>=256||entries.values().stream().filter(e->e.actor.equals(actor)).count()>=7)
            throw new IllegalStateException("STRIKE_SEQUENCE_BUDGET");
        entries.put(execution,new Entry<>(actor,value));
    }
    public List<T> owned(UUID actor){return entries.values().stream().filter(e->e.actor.equals(actor)).map(Entry::value).toList();}
    public T remove(String execution){var e=entries.remove(execution);return e==null?null:e.value;}
    public List<T> cancel(UUID actor){var result=owned(actor);entries.values().removeIf(e->e.actor.equals(actor));return result;}
}
