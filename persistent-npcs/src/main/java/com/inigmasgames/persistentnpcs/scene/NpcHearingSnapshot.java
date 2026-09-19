package com.inigmasgames.persistentnpcs.scene;

import java.util.UUID;

/** Authoritative hearing/attention facts resolved by the game adapter. */
public record NpcHearingSnapshot(
        UUID listenerNpcId,
        NpcSpeechLocation location,
        NpcActivityState state,
        boolean lineOfSight,
        UUID activeConversationId) {

    public NpcHearingSnapshot normalized() {
        return new NpcHearingSnapshot(listenerNpcId, location,
                state == null ? NpcActivityState.IDLE : state,
                lineOfSight, activeConversationId);
    }
}
