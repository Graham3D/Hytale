package com.inigmasgames.persistentnpcs.conversation.contract;

/** Explicit pre-inference contract compilation rejection. */
public final class PlanRejectedException extends IllegalArgumentException {
    public PlanRejectedException(String reason) {
        super("PLAN_REJECTED: " + (reason == null ? "unknown" : reason));
    }
}
