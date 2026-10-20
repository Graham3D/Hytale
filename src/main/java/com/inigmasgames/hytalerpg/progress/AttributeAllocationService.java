package com.inigmasgames.hytalerpg.progress;

import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.links.ValidationCode;

import java.util.UUID;

/** Authoritative, revision-checked attribute allocation. UI callers submit intent only. */
public final class AttributeAllocationService {
    private final RpgLoadoutService loadouts;

    public AttributeAllocationService(RpgLoadoutService loadouts) {
        this.loadouts = loadouts;
    }

    public MutationResult allocate(UUID player, RpgAttribute attribute, long expectedRevision,
                                   String correlationId) {
        RpgLoadoutView before = loadouts.getLoadout(player);
        if (before.state().revision != expectedRevision) {
            return MutationResult.failure(ValidationCode.STALE_REVISION,
                    "Character changed; refreshed the authoritative revision.", correlationId,
                    before.state().revision);
        }
        if (before.state().unspentAttributePoints <= 0) {
            return MutationResult.failure(ValidationCode.INVALID_REQUEST,
                    "No unspent attribute points are available.", correlationId, before.state().revision);
        }
        return loadouts.mutateProgress(player, expectedRevision, correlationId, candidate -> {
            if (candidate.unspentAttributePoints <= 0) {
                throw new IllegalArgumentException("No unspent attribute points are available.");
            }
            candidate.attributes.merge(attribute.name(), 1, Integer::sum);
            candidate.unspentAttributePoints--;
            if (candidate.pendingLevelUpPoints > 0) candidate.pendingLevelUpPoints--;
        });
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
