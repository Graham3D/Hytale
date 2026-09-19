package com.inigmasgames.persistentnpcs.plan;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Persistent social commitment. Navigation is an execution detail, not its purpose. */
public record SharedPlan(
        UUID id,
        String purpose,
        List<UUID> participants,
        UUID initiator,
        UUID leader,
        SharedPlanDestination destination,
        SharedPlanStartMode startMode,
        Instant scheduledTime,
        SharedPlanStatus status,
        Instant createdAt,
        Map<String, String> relevantContext) {

    public SharedPlan normalized() {
        UUID resolvedId = id == null ? UUID.randomUUID() : id;
        String resolvedPurpose = compact(purpose, 240);
        if (resolvedPurpose.isBlank()) {
            throw new IllegalArgumentException("Shared-plan purpose is required");
        }
        List<UUID> people = participants == null ? List.of() : participants.stream()
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (people.size() < 2 || initiator == null || leader == null
                || !people.contains(initiator) || !people.contains(leader)) {
            throw new IllegalArgumentException(
                    "Shared plan requires two participants and server-resolved initiator/leader");
        }
        SharedPlanStartMode mode = startMode == null ? SharedPlanStartMode.NOW : startMode;
        if (mode == SharedPlanStartMode.SCHEDULED && scheduledTime == null) {
            throw new IllegalArgumentException("Scheduled shared plan requires a time");
        }
        SharedPlanStatus resolvedStatus = status == null
                ? (mode == SharedPlanStartMode.NOW
                        ? SharedPlanStatus.ACCEPTED : SharedPlanStatus.SCHEDULED)
                : status;
        return new SharedPlan(resolvedId, resolvedPurpose, people, initiator, leader,
                destination == null ? null : destination.normalized(), mode, scheduledTime,
                resolvedStatus, createdAt == null ? Instant.now() : createdAt,
                observableContext(relevantContext));
    }

    public SharedPlan withStatus(SharedPlanStatus next) {
        if (next == null) throw new IllegalArgumentException("Plan status is required");
        if (status != null && status.terminal() && next != status) {
            throw new IllegalStateException("A terminal shared plan cannot be reopened");
        }
        return new SharedPlan(id, purpose, participants, initiator, leader, destination,
                startMode, scheduledTime, next, createdAt, relevantContext).normalized();
    }

    public boolean involves(UUID entityId) {
        return participants != null && participants.contains(entityId);
    }

    public String contextSummary(UUID npcId, UUID playerId) {
        String role = leader.equals(npcId) ? "I am leading"
                : leader.equals(playerId) ? "I am following the player" : "We are traveling";
        String timing = startMode == SharedPlanStartMode.SCHEDULED
                ? "scheduled for " + scheduledTime : "starting now";
        String where = destination == null ? "" : " Destination: " + destination.describe() + ".";
        return "Plan " + status + ": " + role + " because " + purpose + "; " + timing
                + "." + where;
    }

    /** Rejects hidden/private intent keys; only expressed or observable context may persist. */
    private static Map<String, String> observableContext(Map<String, String> source) {
        if (source == null || source.isEmpty()) return Map.of();
        Map<String, String> safe = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String normalizedKey = compact(key, 64);
            String lower = normalizedKey.toLowerCase(Locale.ROOT);
            if (normalizedKey.isBlank() || lower.contains("secret")
                    || lower.contains("hidden") || lower.contains("private")
                    || lower.contains("internal") || lower.contains("chain_of_thought")) {
                return;
            }
            String normalizedValue = compact(value, 300);
            if (!normalizedValue.isBlank()) safe.put(normalizedKey, normalizedValue);
        });
        return Map.copyOf(safe);
    }

    private static String compact(String value, int maximum) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }
}
