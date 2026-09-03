package com.inigmasgames.persistentnpcs.director;

import java.util.Set;

public final class WorldStoryValidator {
    private final Set<String> allowedEventTypes;
    private final Set<String> registeredActionIds;

    public WorldStoryValidator(Set<String> allowedEventTypes, Set<String> registeredActionIds) {
        this.allowedEventTypes = Set.copyOf(allowedEventTypes);
        this.registeredActionIds = Set.copyOf(registeredActionIds);
    }

    public boolean valid(WorldStoryProposal proposal) {
        return proposal != null && proposal.groundingEventId() != null
                && !proposal.groundingEventId().isBlank()
                && allowedEventTypes.contains(proposal.eventType())
                && registeredActionIds.contains(proposal.actionId())
                && proposal.arguments() != null;
    }
}
