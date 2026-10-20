package com.inigmasgames.hytalerpg.combat.resource;

import java.util.UUID;

/** Lifetime-owned native root receipt. No global ever-growing attack-ID set or expiry/replay loophole. */
public final class RootWeaponHit {
    private final UUID actor;
    private final String id;
    private boolean observed,charged,recovered;
    public RootWeaponHit(UUID actor){this.actor=java.util.Objects.requireNonNull(actor);this.id="native-basic/"+UUID.randomUUID();}
    public UUID actor(){return actor;}
    public String id(){return id;}
    public synchronized boolean observe(double before,double after,boolean cancelled,boolean hostile,boolean charged){
        if(observed||cancelled||!hostile||!Double.isFinite(before)||!Double.isFinite(after)||before<=0||after<0||after>=before)return false;
        observed=true;this.charged=charged;return true;
    }
    public synchronized boolean charged(){return charged;}
    public synchronized boolean claimRecovery(){if(!observed||recovered)return false;recovered=true;return true;}
}
