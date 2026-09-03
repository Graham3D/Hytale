package com.inigmasgames.persistentnpcs.sentinel;

import com.inigmasgames.persistentnpcs.sentinel.SentinelContracts.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Sanitized event forwarded into the existing operator trace pipeline only. */
public record SentinelEvent(String event, UUID npcId, String invariantId,
        VerdictStatus verdict, Severity severity, Confidence confidence,
        String scopeKey, String reasonCode, String failureSignature,
        int occurrenceCount, List<String> correlationIds, long evaluationMicros,
        Instant at) { }
