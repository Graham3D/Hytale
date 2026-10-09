package com.inigmasgames.hytalerpg.combat.hytale;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.system.ISystem;

import java.util.*;

/** Registry-lifetime ordering metadata; no events, ticks, actor state or gameplay callbacks. */
final class NativeSystemOrder<E> implements ISystem<E> {
    private final Map<SystemGroup<E>,List<ISystem<E>>> lanes=new IdentityHashMap<>();
    private final Map<ISystem<E>,ISystem<E>> aliases=new IdentityHashMap<>();
    private final Set<Dependency<E>> dependencies=Set.of(new Dependency<>(Order.AFTER,OrderPriority.NORMAL){
        @Override public void validate(ComponentRegistry<E> registry){}
        @Override public void resolveGraphEdge(ComponentRegistry<E> registry,ISystem<E> owner,
                DependencyGraph<E> graph){
            var present=Collections.newSetFromMap(new IdentityHashMap<ISystem<E>,Boolean>());
            Collections.addAll(present,graph.getSystems());
            for(var lane:lanes.values()){
                ISystem<E> previous=null;
                for(var original:lane){
                    var current=present.contains(original)?original:aliases.get(original);
                    if(current==null||!present.contains(current))continue;
                    if(previous!=null)graph.addEdge(previous,current,getPriority());
                    previous=current;
                }
            }
        }
    });
    @Override public Set<Dependency<E>> getDependencies(){return dependencies;}

    @SuppressWarnings("unchecked")
    private static <E> NativeSystemOrder<E> cast(NativeSystemOrder<?> value){return (NativeSystemOrder<E>)value;}

    static <E> void preserve(ComponentRegistry<E> registry,ISystem<E> original,ISystem<E> adapter){
        if(original.getGroup()!=adapter.getGroup())
            throw new IllegalStateException("NATIVE_ADAPTER_GROUP_CHANGED");
        var data=registry._internal_getData();NativeSystemOrder<E> ledger=null;
        for(int i=0;i<data.getSystemSize();i++)if(data.getSystem(i) instanceof NativeSystemOrder<?> found)ledger=cast(found);
        boolean register=ledger==null;
        if(register)ledger=new NativeSystemOrder<>();
        if(!ledger.lanes.containsKey(original.getGroup())){
            var lane=new ArrayList<ISystem<E>>();
            for(int i=0;i<data.getSystemSize();i++){
                var system=data.getSystem(i);
                if(!(system instanceof NativeSystemOrder<?>)&&system.getGroup()==original.getGroup())lane.add(system);
            }
            ledger.lanes.put(original.getGroup(),List.copyOf(lane));
        }
        ledger.aliases.put(original,adapter);
        // Unregister/register changes raw SDK positions. Keep this metadata through adapter teardown
        // so restoration cannot reorder native filters either. The registry releases it at shutdown.
        if(register)registry.registerSystem(ledger);
    }
    static <E> void restored(ComponentRegistry<E> registry,ISystem<E> original){
        var data=registry._internal_getData();
        for(int i=0;i<data.getSystemSize();i++)if(data.getSystem(i) instanceof NativeSystemOrder<?> ledger)
            ledger.aliases.remove(original);
    }
}
