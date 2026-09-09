package com.inigmasgames.hytalerpg.execution;

import java.util.*;

/** Shared finite root admission; bounded scalar/key state only, no native entity references. */
public final class RootEffectBudget {
    private final UUID actor;private final String root;
    private final Set<String> effects=new HashSet<>(Set.of("PRIMARY")),controllers=new HashSet<>();
    private int triggered;
    private final com.inigmasgames.hytalerpg.vfx.ProjectileReadability.Group projectileVisuals=new com.inigmasgames.hytalerpg.vfx.ProjectileReadability.Group();
    public com.inigmasgames.hytalerpg.vfx.ProjectileReadability.Group projectileVisuals(){return projectileVisuals;}
    private final com.inigmasgames.hytalerpg.progress.MasteryRootBudget mastery=new com.inigmasgames.hytalerpg.progress.MasteryRootBudget();
    public com.inigmasgames.hytalerpg.progress.MasteryRootBudget mastery(){return mastery;}
    private final com.inigmasgames.hytalerpg.execution.area.RootDisplacementLedger displacement=new com.inigmasgames.hytalerpg.execution.area.RootDisplacementLedger();
    private final Map<String,Double> statusTimes=new HashMap<>();
    private final Map<String,Double> orbitContacts=new HashMap<>();
    private final Set<String> procContacts=new HashSet<>();
    private final com.inigmasgames.hytalerpg.execution.projectile.ProjectileLifecycleRegistry.Lifetime projectileLifetime=new com.inigmasgames.hytalerpg.execution.projectile.ProjectileLifecycleRegistry.Lifetime();
    private final RootWorkBudget work=projectileLifetime.work();
    public com.inigmasgames.hytalerpg.execution.projectile.ProjectileLifecycleRegistry.Lifetime projectileLifetime(){return projectileLifetime;}
    public synchronized String claimProcContact(String id){
        if(id==null||id.isBlank()||id.length()>512)return "INVALID_PROC_CONTACT";
        if(procContacts.contains(id))return "DUPLICATE_PROC_CONTACT";
        if(procContacts.size()>=256)return "ROOT_PROC_CONTACT_BUDGET";
        procContacts.add(id);return "PASS";
    }
    public synchronized String claimOrbitContact(String target,double now){
        if(target==null||target.isBlank()||target.length()>256||!Double.isFinite(now))return "INVALID_ORBIT_CONTACT";
        if(now-orbitContacts.getOrDefault(target,Double.NEGATIVE_INFINITY)<.75-1e-9)return "ROOT_ORBIT_CONTACT_ICD";
        if(!orbitContacts.containsKey(target)&&orbitContacts.size()>=256)return "ROOT_ORBIT_TARGET_BUDGET";
        orbitContacts.put(target,now);return "PASS";
    }
    public synchronized boolean statusReady(String target,double now,double interval){return Double.isFinite(now)&&Double.isFinite(interval)&&interval>=0
            &&(statusTimes.containsKey(target)||statusTimes.size()<256)&&now-statusTimes.getOrDefault(target,Double.NEGATIVE_INFINITY)>=interval-1e-9;}
    public synchronized void statusApplied(String target,double now){if(statusTimes.containsKey(target)||statusTimes.size()<256)statusTimes.put(target,now);}
    public com.inigmasgames.hytalerpg.execution.area.RootDisplacementLedger displacement(){return displacement;}
    public RootEffectBudget(UUID actor,String root){this.actor=Objects.requireNonNull(actor);this.root=Objects.requireNonNull(root);}
    public boolean owns(UUID actor,String root){return this.actor.equals(actor)&&this.root.equals(root);}
    public synchronized boolean once(String controller){return controller!=null&&!controller.isBlank()&&controller.length()<=64&&controllers.size()<16&&controllers.add(controller);}
    public synchronized String claim(String id,int generation,boolean secondary){
        if(id==null||id.isBlank()||id.length()>512)return "INVALID_EFFECT_ID";
        if(generation<1||generation>3)return "MAX_GENERATION";
        if(effects.contains(id))return "DUPLICATE_EFFECT";
        String capacity=work.additionalEffect(secondary);if(!capacity.equals("PASS"))return capacity;
        effects.add(id);if(secondary)triggered++;return "PASS";
    }
    /** A persistent/authored component can resolve many contacts without becoming a new spawned effect each tick. */
    public synchronized String authoredComponent(String id){
        if(effects.contains(id))return "PASS";
        return claim(id,1,false);
    }
    public synchronized int spawned(){return work.totalEffects();}
    public synchronized int triggered(){return work.totalTriggered();}
}
