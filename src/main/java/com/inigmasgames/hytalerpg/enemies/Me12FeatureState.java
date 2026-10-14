package com.inigmasgames.hytalerpg.enemies;

/** Configuration vocabulary; Stage 2 has no production activation authority. */
public enum Me12FeatureState {
    DISABLED, SHADOW, ENABLED, DRAIN;
    public boolean allowsNewV2Births(){return false;}
    public boolean evaluatesShadow(){return this==SHADOW;}
    public void validateStage2(){if(this==ENABLED)throw new IllegalArgumentException("ME12_PRODUCTION_RELEASE_GATE_OPEN");}
}
