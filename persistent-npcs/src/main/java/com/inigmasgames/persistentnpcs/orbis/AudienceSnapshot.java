package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.voice.EligibleNpcListener;
import com.inigmasgames.persistentnpcs.voice.PlayerSpeechIntent;
import com.inigmasgames.persistentnpcs.voice.PlayerUtteranceAudienceService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Immutable Orbis-owned audience fact produced once for one final transcript. */
public record AudienceSnapshot(UUID utteranceId, PlayerSpeechIntent speechIntent,
        Set<UUID> directAddressTargets, List<EligibleNpcListener> heardListeners,
        Set<UUID> deliveredListeners, Set<UUID> responseCandidates,
        Set<UUID> speechOwners, Map<UUID, String> suppressionReasons,
        long resolutionMillis) {
    public AudienceSnapshot {
        directAddressTargets = Set.copyOf(directAddressTargets);
        heardListeners = List.copyOf(heardListeners);
        deliveredListeners = Set.copyOf(deliveredListeners);
        responseCandidates = Set.copyOf(responseCandidates);
        speechOwners = Set.copyOf(speechOwners);
        suppressionReasons = Map.copyOf(suppressionReasons);
    }

    public static AudienceSnapshot from(PlayerUtteranceAudienceService.Resolution value) {
        var event = value.event();
        Set<UUID> listeners = event.eligibleNpcListeners().stream()
                .map(EligibleNpcListener::npcId).collect(java.util.stream.Collectors.toSet());
        Set<UUID> owners = value.responseOwners().stream().map(EligibleNpcListener::npcId)
                .collect(java.util.stream.Collectors.toSet());
        return new AudienceSnapshot(event.utteranceId(), event.speechIntent(),
                event.directAddressTargets(), event.eligibleNpcListeners(), listeners,
                listeners, owners, value.suppressionReasons(),
                event.audienceResolutionMillis());
    }
}
