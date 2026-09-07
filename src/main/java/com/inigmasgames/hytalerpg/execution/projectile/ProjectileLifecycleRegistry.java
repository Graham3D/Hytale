package com.inigmasgames.hytalerpg.execution.projectile;

import java.util.*;

/** Root-lifetime budgets include promised Echo launches; carrier replacement is not a new gameplay effect. */
public final class ProjectileLifecycleRegistry {
    public static final int OWNER_CAP=24,GLOBAL_CAP=512;
    private final Map<String,ProjectileInstance> active=new LinkedHashMap<>();
    private final Map<RootKey,Root> roots=new LinkedHashMap<>();
    private record RootKey(UUID owner,String id) { }
    private static RootKey key(ProjectileExecutionPlan p) { return new RootKey(p.ownerId(),p.rootCastId()); }
    private static final class Root {
        int pending,spent;final Set<String> seen=new HashSet<>();
        Root(int launches){pending=launches;spent=launches;}
        Root(Root prior){pending=prior.pending;spent=prior.spent;seen.addAll(prior.seen);}
    }
    public synchronized String admission(UUID owner,int launches) {
        if(launches<1||launches>48)return "INVALID_PROJECTILE_BATCH";
        long promised=roots.entrySet().stream().filter(e->e.getKey().owner.equals(owner)).mapToInt(e->e.getValue().pending).sum();
        if(ownedBy(owner).size()+promised+launches>OWNER_CAP)return "OWNER_PROJECTILE_BUDGET";
        if(active.size()+roots.values().stream().mapToInt(r->r.pending).sum()+launches>GLOBAL_CAP)return "GLOBAL_PROJECTILE_BUDGET";
        return "PASS";
    }
    public synchronized void register(ProjectileInstance instance){registerAll(List.of(instance));}
    public synchronized void registerAll(List<ProjectileInstance> batch) {
        if(batch.isEmpty())return;
        var first=batch.getFirst().plan();RootKey key=key(first);Root prior=roots.get(key);
        if(prior==null && first.remainingContinuationBudgets().getOrDefault("IS_LAUNCH",1)==0)
            throw new IllegalStateException("UNKNOWN_PROJECTILE_ROOT");
        int launches=first.remainingContinuationBudgets().getOrDefault("ROOT_LAUNCHES",1);
        if(launches<1||launches>48)throw new IllegalStateException("INVALID_ROOT_LAUNCH_COUNT");
        Root next=prior==null?new Root(launches):new Root(prior);
        int extra=prior==null?launches:0;
        for(var instance:batch) {
            var plan=instance.plan();
            if(!key(plan).equals(key)||plan.generation()>3||plan.remainingSpawnedEffects()>=48||plan.remainingTriggeredSecondaries()>16)
                throw new IllegalStateException("Projectile recursion budget exceeds canonical bounds");
            if(active.containsKey(plan.projectileInstanceId())||!next.seen.add(plan.projectileInstanceId()))
                throw new IllegalStateException("Duplicate projectile instance ID");
            if(plan.remainingContinuationBudgets().getOrDefault("IS_LAUNCH",1)>0) {
                if(next.pending<=0)throw new IllegalStateException("ROOT_LAUNCH_RESERVATION_EXHAUSTED");
                next.pending--;
            } else {next.spent++;extra++;}
        }
        if(next.spent>48)throw new IllegalStateException("ROOT_SPAWN_EFFECT_BUDGET");
        if(extra>0) {String admission=admission(first.ownerId(),extra);if(!admission.equals("PASS"))throw new IllegalStateException(admission);}
        roots.put(key,next);for(var instance:batch)active.put(instance.plan().projectileInstanceId(),instance);
    }
    public synchronized Optional<ProjectileInstance> get(String id){return Optional.ofNullable(active.get(id));}
    public synchronized boolean remove(ProjectileInstance instance) {
        boolean removed=active.remove(instance.plan().projectileInstanceId(),instance);
        cleanup(key(instance.plan()));return removed;
    }
    public synchronized void abandonLaunch(UUID owner,String rootId) {
        var key=new RootKey(owner,rootId);var root=roots.get(key);
        // A rejected scheduled launch cancels this root's remaining promised launch batch, idempotently.
        if(root!=null)root.pending=0;cleanup(key);
    }
    private void cleanup(RootKey key) {
        var root=roots.get(key);
        if(root!=null && root.pending==0 && active.values().stream().noneMatch(i->key(i.plan()).equals(key))) roots.remove(key);
    }
    public synchronized List<ProjectileInstance> ownedBy(UUID owner){return active.values().stream().filter(i->i.plan().ownerId().equals(owner)).toList();}
    public synchronized List<ProjectileInstance> removeOwnedBy(UUID owner) {
        var removed=ownedBy(owner);removed.forEach(i->active.remove(i.plan().projectileInstanceId()));
        roots.keySet().removeIf(k->k.owner.equals(owner));return removed;
    }
    public synchronized int size(){return active.size();}
    public synchronized int rootCount(){return roots.size();}
    public synchronized int spent(UUID owner,String root){var state=roots.get(new RootKey(owner,root));return state==null?0:state.spent;}
}
