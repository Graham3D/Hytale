package com.inigmasgames.persistentnpcs.plan;

public enum SharedPlanStatus {
    PROPOSED,
    ACCEPTED,
    SCHEDULED,
    ACTIVE,
    COMPLETED,
    CANCELLED,
    FAILED;

    public boolean terminal() {
        return this == COMPLETED || this == CANCELLED || this == FAILED;
    }
}
