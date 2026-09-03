package com.inigmasgames.persistentnpcs.epistemic;

/** E6 pre-realization disclosure authority. Intentional deception is not enabled. */
public enum DisclosureDecision {
    SHARE, SHARE_WITH_UNCERTAINTY, WITHHOLD, EVADE, ASK_PERMISSION, DECEIVE;

    public DisclosureDecision safe() {
        return this == DECEIVE ? WITHHOLD : this;
    }
}
