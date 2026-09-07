package com.inigmasgames.hytalerpg.execution;

import java.util.*;

/** One capacity authority for finite area/connection fields, not a second allowance per executor family. */
public final class OwnedFieldBudget {
    public static final int OWNER_CAP=8,GLOBAL_CAP=128;
    private record Key(UUID owner,String instance) { }
    private final Set<Key> active=new HashSet<>();
    public synchronized String admission(UUID owner) {
        if(active.size()>=GLOBAL_CAP)return "GLOBAL_FIELD_BUDGET";
        if(active.stream().filter(key->key.owner.equals(owner)).count()>=OWNER_CAP)return "OWNER_FIELD_BUDGET";
        return "PASS";
    }
    public synchronized void reserve(UUID owner,String instance) {
        if(owner==null||instance==null||instance.isBlank())throw new IllegalArgumentException("Invalid field owner");
        var key=new Key(owner,instance);if(active.contains(key))throw new IllegalStateException("DUPLICATE_FIELD_INSTANCE");
        String verdict=admission(owner);if(!verdict.equals("PASS"))throw new IllegalStateException(verdict);active.add(key);
    }
    public synchronized boolean release(UUID owner,String instance){return active.remove(new Key(owner,instance));}
    public synchronized int size(){return active.size();}
}
