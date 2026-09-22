package com.inigmasgames.hytalerpg.execution.lightning;

/**
 * Classifies the native Spire carrier without treating command-buffer
 * materialization latency as destruction.
 */
public final class LightningSpireEntityLifecycle {
    public enum State { MATERIALIZING, ALIVE, DESTROYED }

    private boolean healthyEntityObserved;

    public State observe(LightningSpireRuntime.Phase phase,boolean refValid,boolean healthAvailable,
                         double currentHealth,double minimumHealth){
        if(refValid&&healthAvailable&&Double.isFinite(currentHealth)&&Double.isFinite(minimumHealth)){
            if(currentHealth>minimumHealth){healthyEntityObserved=true;return State.ALIVE;}
            if(healthyEntityObserved||phase==LightningSpireRuntime.Phase.READY)return State.DESTROYED;
        }
        if(healthyEntityObserved||phase==LightningSpireRuntime.Phase.READY)return State.DESTROYED;
        return State.MATERIALIZING;
    }

    public boolean healthyEntityObserved(){return healthyEntityObserved;}
}
