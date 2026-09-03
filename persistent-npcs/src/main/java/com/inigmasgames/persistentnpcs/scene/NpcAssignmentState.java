package com.inigmasgames.persistentnpcs.scene;

import java.time.Instant;
import java.util.UUID;

/** Deterministic assignment facts used to trigger grounded employer/worker dialogue. */
public record NpcAssignmentState(
        UUID id,
        UUID assignedBy,
        UUID assignedTo,
        String task,
        Instant assignedTime,
        Instant expectedCompletionTime,
        Instant actualCompletionTime,
        NpcAssignmentStatus status,
        int priorWarnings,
        boolean latenessAddressed,
        String workplace,
        String workerKnownReason) {

    public NpcAssignmentState normalized() {
        if (assignedBy == null || assignedTo == null || assignedBy.equals(assignedTo)) {
            throw new IllegalArgumentException("Assignment requires distinct employer and worker");
        }
        String resolvedTask = clean(task, 240);
        if (resolvedTask.isBlank() || expectedCompletionTime == null) {
            throw new IllegalArgumentException("Assignment task and expected time are required");
        }
        return new NpcAssignmentState(id == null ? UUID.randomUUID() : id,
                assignedBy, assignedTo, resolvedTask,
                assignedTime == null ? Instant.now() : assignedTime,
                expectedCompletionTime, actualCompletionTime,
                status == null ? NpcAssignmentStatus.ASSIGNED : status,
                Math.max(0, priorWarnings), latenessAddressed, clean(workplace, 120),
                clean(workerKnownReason, 240));
    }

    public NpcAssignmentState addressed() {
        return new NpcAssignmentState(id, assignedBy, assignedTo, task, assignedTime,
                expectedCompletionTime, actualCompletionTime, status, priorWarnings,
                true, workplace, workerKnownReason).normalized();
    }

    public boolean overdueCompleted() {
        return status == NpcAssignmentStatus.COMPLETED && actualCompletionTime != null
                && actualCompletionTime.isAfter(expectedCompletionTime);
    }

    private static String clean(String value, int maximum) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }
}
