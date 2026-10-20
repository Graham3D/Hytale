package com.inigmasgames.hytalerpg.execution.projectile;

import java.util.*;

/** Root-lifetime budgets include promised Echo launches; carrier replacement is not a new gameplay effect. */
public final class ProjectileLifecycleRegistry {
    public static final int OWNER_CAP=24,GLOBAL_CAP=512;
    private final Map<String,ProjectileInstance> active=new LinkedHashMap<>();
    private final Map<RootKey,Root> roots=new LinkedHashMap<>();
    private record RootKey(UUID owner,String id) { }
    private static RootKey key(ProjectileExecutionPlan p) { return new RootKey(p.ownerId(),p.rootCastId()); }
    /** Retains bounded original registry accounting while an owning skill/status context can still trigger a release. */
    public static final class Lifetime {
        private RootKey key;private Root state;
        private final com.inigmasgames.hytalerpg.execution.RootWorkBudget work=new com.inigmasgames.hytalerpg.execution.RootWorkBudget();
        public com.inigmasgames.hytalerpg.execution.RootWorkBudget work(){return work;}
    }
    private static final class Root {
        int pending,spent,triggered;final Set<String> seen=new HashSet<>();final Lifetime lifetime;
        final com.inigmasgames.hytalerpg.execution.RootWorkBudget work;
        Root(int launches){this(launches,null);}
        Root(int launches,Lifetime lifetime){pending=launches;spent=launches;this.lifetime=lifetime;work=lifetime==null?new com.inigmasgames.hytalerpg.execution.RootWorkBudget():lifetime.work();}
        Root(Root prior){pending=prior.pending;spent=prior.spent;triggered=prior.triggered;seen.addAll(prior.seen);lifetime=prior.lifetime;work=prior.work;}
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
        registerAll(batch,0);
    }
    public synchronized void registerAll(List<ProjectileInstance> batch,Lifetime lifetime){registerAll(batch,0,lifetime,false);}
    public synchronized void registerConditionalAll(List<ProjectileInstance> batch,Lifetime lifetime){registerAll(batch,batch.size(),lifetime,true);}
    public synchronized void registerTriggeredAll(List<ProjectileInstance> batch) { registerAll(batch,batch.size()); }
    private void registerAll(List<ProjectileInstance> batch,int triggered) {
        registerAll(batch,triggered,null,false);
    }
    private void registerAll(List<ProjectileInstance> batch,int triggered,Lifetime lifetime,boolean conditional) {
        if(batch.isEmpty())return;
        var first=batch.getFirst().plan();RootKey key=key(first);Root prior=roots.get(key);
        if(lifetime!=null&&lifetime.key!=null&&!lifetime.key.equals(key))throw new IllegalStateException("FOREIGN_PROJECTILE_LIFETIME");
        if(prior!=null&&lifetime!=null&&prior.lifetime!=lifetime)throw new IllegalStateException("PROJECTILE_LIFETIME_CHANGED");
        boolean restored=false;
        if(conditional){
            if(lifetime==null||lifetime.state==null)throw new IllegalStateException("CONDITIONAL_PROJECTILE_ROOT_MISSING");
            if(prior==null){prior=lifetime.state;restored=true;}
        }else if(prior==null&&lifetime!=null&&lifetime.state!=null)throw new IllegalStateException("PROJECTILE_ROOT_ALREADY_STARTED");
        if(prior==null && first.remainingContinuationBudgets().getOrDefault("IS_LAUNCH",1)==0)
            throw new IllegalStateException("UNKNOWN_PROJECTILE_ROOT");
        int launches=first.remainingContinuationBudgets().getOrDefault("ROOT_LAUNCHES",1);
        if(launches<1||launches>48)throw new IllegalStateException("INVALID_ROOT_LAUNCH_COUNT");
        Root next=prior==null?new Root(launches,lifetime):new Root(prior);
        if(conditional){next.pending+=batch.size();next.spent+=batch.size();}
        if(next.triggered+triggered>16)throw new IllegalStateException("ROOT_TRIGGERED_SECONDARY_BUDGET");
        next.triggered+=triggered;
        int extra=conditional?batch.size():prior==null?launches:0;
        if(restored&&next.pending!=batch.size())throw new IllegalStateException("ORPHANED_PROJECTILE_PROMISE");
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
        String shared=next.work.projectiles(next.spent,next.triggered);if(!shared.equals("PASS"))throw new IllegalStateException(shared);
        roots.put(key,next);if(next.lifetime!=null){next.lifetime.key=key;next.lifetime.state=next;}for(var instance:batch)active.put(instance.plan().projectileInstanceId(),instance);
    }
    /** Instant secondary effects spend root budgets, but hold no native carrier capacity. */
    public synchronized String reserveSecondary(ProjectileInstance parent,String id) {
        var root=roots.get(key(parent.plan()));
        if(root==null||active.get(parent.plan().projectileInstanceId())!=parent)return "UNKNOWN_PROJECTILE_ROOT";
        if(root.seen.contains(id))return "DUPLICATE_SECONDARY";
        if(parent.plan().generation()>=3)return "MAX_GENERATION";
        if(root.triggered>=16)return "ROOT_TRIGGERED_SECONDARY_BUDGET";
        if(root.spent>=48)return "ROOT_SPAWN_EFFECT_BUDGET";
        String shared=root.work.projectiles(root.spent+1,root.triggered+1);if(!shared.equals("PASS"))return shared;
        root.seen.add(id);root.spent++;root.triggered++;return "PASS";
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
    public synchronized int triggered(UUID owner,String root){var state=roots.get(new RootKey(owner,root));return state==null?0:state.triggered;}
    public synchronized int spent(UUID owner,String root){var state=roots.get(new RootKey(owner,root));return state==null?0:state.spent;}
}
