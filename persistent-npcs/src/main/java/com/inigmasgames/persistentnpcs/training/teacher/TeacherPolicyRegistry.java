package com.inigmasgames.persistentnpcs.training.teacher;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable in-memory view of the versioned teacher policy registry. */
public final class TeacherPolicyRegistry {
    private final Map<String, TeacherSourcePolicy> byId;
    public TeacherPolicyRegistry(List<TeacherSourcePolicy> policies) {
        LinkedHashMap<String, TeacherSourcePolicy> copy = new LinkedHashMap<>();
        for (TeacherSourcePolicy policy : policies == null
                ? List.<TeacherSourcePolicy>of() : policies) {
            if (copy.putIfAbsent(policy.policyId(), policy) != null) {
                throw new IllegalArgumentException("duplicate teacher policy " + policy.policyId());
            }
        }
        byId = Map.copyOf(copy);
    }
    public TeacherSourcePolicy requireApproved(String policyId, String sourceId) {
        TeacherSourcePolicy policy = byId.get(policyId);
        if (policy == null || !policy.sourceId().equals(sourceId)) {
            throw new IllegalStateException("exact teacher policy/source identity not registered");
        }
        policy.requireApproved();
        return policy;
    }
}
