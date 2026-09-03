package com.inigmasgames.persistentnpcs.scene;

import java.time.Instant;
import java.util.UUID;

/** Factual asymmetric context: each NPC only receives facts it legitimately knows. */
public record NpcConversationTrigger(
        UUID triggerId,
        UUID speakerNpcId,
        UUID listenerNpcId,
        String topic,
        String speakerFacts,
        String listenerFacts,
        Instant createdAt) { }
