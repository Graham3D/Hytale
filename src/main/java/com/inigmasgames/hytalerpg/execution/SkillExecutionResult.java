package com.inigmasgames.hytalerpg.execution;

public record SkillExecutionResult(Status status, String code, boolean committed, int affectedTargets,
                                   double movementDistance) {
    public static SkillExecutionResult rejected(String code) {
        return new SkillExecutionResult(Status.REJECTED, code, false, 0, 0.0);
    }
    public static SkillExecutionResult pending(String code) {
        return new SkillExecutionResult(Status.PENDING, code, false, 0, 0.0);
    }
    public static SkillExecutionResult committed(String code, int targets, double distance) {
        return new SkillExecutionResult(Status.COMMITTED, code, true, targets, distance);
    }
    public enum Status { REJECTED, PENDING, COMMITTED, TERMINATED, CANCELLED }
}
