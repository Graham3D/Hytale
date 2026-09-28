package com.inigmasgames.hytalerpg.progress;

import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.links.ValidationCode;

import java.util.UUID;

/** Authoritative, revision-checked attribute allocation. UI callers submit intent only. */
public final class AttributeAllocationService {
    public record AllocationResult(MutationResult mutation, int requested, int applied) { }
    private final RpgLoadoutService loadouts;

    public AttributeAllocationService(RpgLoadoutService loadouts) {
        this.loadouts = loadouts;
    }

    public MutationResult allocate(UUID player, RpgAttribute attribute, long expectedRevision,
                                   String correlationId) {
        return allocateUpTo(player, attribute, 1, expectedRevision, correlationId).mutation();
    }

    /** One saved mutation for a bounded allocation request, clamped to balance and raw-stat capacity. */
    public AllocationResult allocateUpTo(UUID player, RpgAttribute attribute, int requested,
                                         long expectedRevision, String correlationId) {
        RpgLoadoutView before = loadouts.getLoadout(player);
        if (requested < 1 || requested > 5) {
            return new AllocationResult(MutationResult.failure(ValidationCode.INVALID_REQUEST,
                    "Attribute allocation request must be 1..5.", correlationId,
                    before.state().revision), requested, 0);
        }
        if (before.state().revision != expectedRevision) {
            return new AllocationResult(MutationResult.failure(ValidationCode.STALE_REVISION,
                    "Character changed; refreshed the authoritative revision.", correlationId,
                    before.state().revision), requested, 0);
        }
        if (before.state().unspentAttributePoints <= 0) {
            return new AllocationResult(MutationResult.failure(ValidationCode.INVALID_REQUEST,
                    "No unspent attribute points are available.", correlationId, before.state().revision), requested, 0);
        }
        var applied = new java.util.concurrent.atomic.AtomicInteger();
        var result = loadouts.mutateProgress(player, expectedRevision, correlationId, candidate -> {
            if (candidate.unspentAttributePoints <= 0) {
                throw new IllegalArgumentException("No unspent attribute points are available.");
            }
            int raw = candidate.attributes.getOrDefault(attribute.name(), 10);
            if (raw < 0) throw new IllegalArgumentException("Invalid saved attribute value.");
            int amount = (int) Math.min(Math.min((long) requested, candidate.unspentAttributePoints),
                    (long) Integer.MAX_VALUE - raw);
            if (amount <= 0) throw new IllegalArgumentException("Attribute is at its maximum value.");
            candidate.attributes.put(attribute.name(), raw + amount);
            candidate.unspentAttributePoints -= amount;
            candidate.pendingLevelUpPoints -= Math.min(candidate.pendingLevelUpPoints, amount);
            applied.set(amount);
        });
        return new AllocationResult(result, requested, result.success() ? applied.get() : 0);
    }

    /** Development fixture. It grants no XP and preserves the production level formula. */
    public MutationResult grantDevelopmentPoints(UUID player, int points, String correlationId) {
        if (points <= 0) {
            return MutationResult.failure(ValidationCode.INVALID_REQUEST,
                    "Development point grant must be positive.", correlationId,
                    loadouts.getLoadout(player).state().revision);
        }
        long revision = loadouts.getLoadout(player).state().revision;
        return loadouts.mutateProgress(player, revision, correlationId, candidate -> {
            candidate.unspentAttributePoints = Math.addExact(candidate.unspentAttributePoints, points);
            candidate.pendingLevelUpPoints = Math.addExact(candidate.pendingLevelUpPoints, points);
        });
    }
}
