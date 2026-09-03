package com.inigmasgames.persistentnpcs.llm;

import java.time.Instant;

public record LlmLatency(
        Instant requestStartedAt,
        long timeToFirstTokenMillis,
        long completionMillis,
        boolean streaming) {
}
