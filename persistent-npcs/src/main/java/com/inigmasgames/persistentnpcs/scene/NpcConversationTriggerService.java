package com.inigmasgames.persistentnpcs.scene;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/** Converts authoritative simulation state into a one-shot NPC dialogue trigger. */
public final class NpcConversationTriggerService {
    private final NpcAssignmentStore assignments;
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    public NpcConversationTriggerService(NpcAssignmentStore assignments) {
        this.assignments = assignments;
    }

    public synchronized Optional<NpcConversationTrigger> overdueReturn(
            UUID assignmentId,
            boolean employerPresent,
            boolean workerAtRelevantWorkplace,
            Instant now) {
        NpcAssignmentState assignment = assignments.get(assignmentId);
        if (assignment == null || !assignment.overdueCompleted()
                || assignment.latenessAddressed() || !employerPresent
                || !workerAtRelevantWorkplace || !pending.add(assignmentId)) {
            return Optional.empty();
        }
        long lateMinutes = Math.max(1, Duration.between(
                assignment.expectedCompletionTime(), assignment.actualCompletionTime())
                .toMinutes());
        String employerFacts = "Assigned task: " + assignment.task()
                + ". Expected return: " + assignment.expectedCompletionTime()
                + ". Actual return: " + assignment.actualCompletionTime()
                + ". Task status: " + assignment.status()
                + ". Lateness: " + lateMinutes + " minutes. Prior warnings: "
                + assignment.priorWarnings() + ".";
        String workerFacts = "I completed the assigned task and returned at "
                + assignment.actualCompletionTime() + "."
                + (assignment.workerKnownReason().isBlank() ? ""
                        : " My remembered reason: " + assignment.workerKnownReason() + ".");
        return Optional.of(new NpcConversationTrigger(assignment.id(),
                assignment.assignedBy(), assignment.assignedTo(), "OVERDUE_ASSIGNMENT",
                employerFacts, workerFacts, now == null ? Instant.now() : now));
    }

    /** Call only after the bounded exchange was actually delivered. */
    public synchronized void markAddressed(UUID assignmentId) {
        NpcAssignmentState assignment = assignments.get(assignmentId);
        if (assignment != null) assignments.put(assignment.addressed());
        pending.remove(assignmentId);
    }

    /** Allows a deterministic retry when generation/delivery failed before speech. */
    public void release(UUID assignmentId) {
        pending.remove(assignmentId);
    }
}
