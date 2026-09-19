package com.inigmasgames.persistentnpcs.quest;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Selects only server-configured or issuer-owned item rewards within a deterministic budget. */
public final class RewardResolver {
    private final RewardBudget budget;

    public RewardResolver(RewardBudget budget) {
        this.budget = budget;
    }

    public Optional<QuestReward> resolve(
            QuestProposal untrustedProposal, List<RewardCandidate> authoritativePool) {
        QuestProposal proposal = untrustedProposal.normalized();
        int points = budget.calculate(proposal);
        String preference = normalize(proposal.requestedRewardKind());
        return authoritativePool.stream().map(RewardCandidate::normalized)
                .filter(candidate -> candidate.unitValue() <= points)
                .sorted(Comparator
                        .comparingInt((RewardCandidate candidate) ->
                                preference.isBlank() ? 0
                                        : normalize(candidate.displayName()).contains(preference)
                                                ? 2 : normalize(candidate.itemId()).contains(preference)
                                                        ? 1 : 0)
                        .thenComparingInt(RewardCandidate::unitValue).reversed())
                .findFirst()
                .map(candidate -> {
                    int quantity = Math.max(1, Math.min(candidate.maximumQuantity(),
                            points / candidate.unitValue()));
                    return new QuestReward(candidate.itemId(), candidate.displayName(), quantity,
                            quantity * candidate.unitValue(), candidate.source()).normalized();
                });
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ").strip();
    }
}
