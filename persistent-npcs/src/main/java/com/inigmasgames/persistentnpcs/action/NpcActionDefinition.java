package com.inigmasgames.persistentnpcs.action;

import com.google.gson.JsonObject;
import java.util.Set;
import java.util.function.Predicate;

public record NpcActionDefinition(
        String id,
        String descriptionForLlm,
        JsonObject parameterSchema,
        Set<String> capabilityRequirements,
        Set<String> roleRequirements,
        Predicate<NpcActionContext> eligibility,
        NpcActionValidator validator,
        NpcActionExecutor executor,
        String resultEventDescription) {

    public boolean isEligible(NpcActionContext context) {
        boolean capabilities = capabilityRequirements == null
                || capabilityRequirements.stream().allMatch(context.profile()::hasCapability);
        boolean roles = roleRequirements == null || roleRequirements.isEmpty()
                || roleRequirements.stream().anyMatch(context.profile()::hasRole);
        return capabilities && roles && eligibility.test(context);
    }
}
