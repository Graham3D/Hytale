package com.inigmasgames.hytalerpg.execution;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import com.inigmasgames.hytalerpg.execution.projectile.ProjectileFamilyExecutor;

/** One executor per mechanical family, never one executor per fantasy skill. */
public final class SkillExecutorRegistry {
    private final Map<Stage04SkillProfile.Family, SkillFamilyExecutor> executors;
    public SkillExecutorRegistry(List<SkillFamilyExecutor> executors) {
        EnumMap<Stage04SkillProfile.Family, SkillFamilyExecutor> indexed =
                new EnumMap<>(Stage04SkillProfile.Family.class);
        for (SkillFamilyExecutor executor : executors)
            if (indexed.put(executor.family(), executor) != null)
                throw new IllegalArgumentException("Duplicate executor family " + executor.family());
        this.executors = Map.copyOf(indexed);
    }
    public SkillFamilyExecutor require(Stage04SkillProfile.Family family) {
        SkillFamilyExecutor executor = executors.get(family);
        if (executor == null) throw new IllegalStateException("No executor registered for " + family);
        return executor;
    }
    public static SkillExecutorRegistry stage04() {
        return runtime();
    }
    public static SkillExecutorRegistry runtime() {
        return new SkillExecutorRegistry(List.of(
                forwarding(Stage04SkillProfile.Family.STRIKE, SkillExecutionPort::executeStrike),
                forwarding(Stage04SkillProfile.Family.MOVEMENT, SkillExecutionPort::executeMovement),
                forwarding(Stage04SkillProfile.Family.REACTION, SkillExecutionPort::executeReaction),
                new ProjectileFamilyExecutor()));
    }
    private static SkillFamilyExecutor forwarding(Stage04SkillProfile.Family family, Dispatch dispatch) {
        return new SkillFamilyExecutor() {
            @Override public Stage04SkillProfile.Family family() { return family; }
            @Override public SkillExecutionResult execute(SkillExecutionContext context, SkillExecutionPort port) {
                return dispatch.execute(port, context);
            }
        };
    }
    @FunctionalInterface private interface Dispatch {
        SkillExecutionResult execute(SkillExecutionPort port, SkillExecutionContext context);
    }
}
