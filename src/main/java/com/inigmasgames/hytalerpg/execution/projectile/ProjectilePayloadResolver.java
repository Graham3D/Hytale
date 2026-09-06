package com.inigmasgames.hytalerpg.execution.projectile;

/** Server-authoritative payload boundary invoked only after target validation and deduplication. */
@FunctionalInterface
public interface ProjectilePayloadResolver<T> {
    Result resolve(ProjectileExecutionPlan plan, T target);

    record Result(double preMitigationDamage, double actualHealthLoss, String statusResult,
                  double requestedKnockback, double appliedKnockback) { }
}
