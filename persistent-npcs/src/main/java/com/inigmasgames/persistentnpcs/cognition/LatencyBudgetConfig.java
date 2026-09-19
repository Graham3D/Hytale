package com.inigmasgames.persistentnpcs.cognition;

import java.util.EnumMap;
import java.util.Map;

/** Configurable warning budgets in milliseconds. */
public record LatencyBudgetConfig(Map<ResponseLatencyStage, Long> stageMillis) {
    public LatencyBudgetConfig {
        EnumMap<ResponseLatencyStage, Long> values = defaultsMap();
        if (stageMillis != null) {
            stageMillis.forEach((stage, value) -> {
                if (stage != null && value != null && value >= 0) values.put(stage, value);
            });
        }
        stageMillis = Map.copyOf(values);
    }

    public static LatencyBudgetConfig defaults() {
        return new LatencyBudgetConfig(Map.of());
    }

    public long budget(ResponseLatencyStage stage) {
        return stageMillis.getOrDefault(stage, Long.MAX_VALUE);
    }

    private static EnumMap<ResponseLatencyStage, Long> defaultsMap() {
        EnumMap<ResponseLatencyStage, Long> values = new EnumMap<>(ResponseLatencyStage.class);
        values.put(ResponseLatencyStage.VOICE_FRAME_CAPTURE, 20L);
        values.put(ResponseLatencyStage.UTTERANCE_ENDPOINT, 350L);
        values.put(ResponseLatencyStage.STT_TRANSCRIPTION, 1_200L);
        values.put(ResponseLatencyStage.AUDIENCE_RESOLUTION, 20L);
        values.put(ResponseLatencyStage.PERCEPTION_CAPTURE, 75L);
        values.put(ResponseLatencyStage.SEMANTIC_NORMALIZATION, 20L);
        values.put(ResponseLatencyStage.RELATIONSHIP_RETRIEVAL, 15L);
        values.put(ResponseLatencyStage.MEMORY_RETRIEVAL, 40L);
        values.put(ResponseLatencyStage.BELIEF_UPDATE, 25L);
        values.put(ResponseLatencyStage.OPPORTUNITY_GENERATION, 20L);
        values.put(ResponseLatencyStage.INTENT_SELECTION, 20L);
        values.put(ResponseLatencyStage.PROMPT_CONTEXT_CONSTRUCTION, 40L);
        values.put(ResponseLatencyStage.LLM_QUEUE_WAIT, 250L);
        values.put(ResponseLatencyStage.NEMOTRON_REQUEST_START, 250L);
        values.put(ResponseLatencyStage.NEMOTRON_TTFT, 2_500L);
        values.put(ResponseLatencyStage.LLM_GENERATION, 8_000L);
        values.put(ResponseLatencyStage.FIRST_CANONICAL_SPEECH_CHUNK, 3_500L);
        values.put(ResponseLatencyStage.TTS_CONDITIONING_LOOKUP, 150L);
        values.put(ResponseLatencyStage.TTS_QUEUE_WAIT, 500L);
        values.put(ResponseLatencyStage.TTS_WORKER_QUEUE_WAIT, 250L);
        values.put(ResponseLatencyStage.TTS_SYNTHESIS_DURATION, 3_000L);
        values.put(ResponseLatencyStage.TTS_SYNTHESIS_START, 250L);
        values.put(ResponseLatencyStage.FIRST_PCM_OPUS_AVAILABILITY, 3_000L);
        values.put(ResponseLatencyStage.FIRST_AUDIBLE_HYTALE_VOICE_FRAME, 5_000L);
        values.put(ResponseLatencyStage.TOTAL_RESPONSE_COMPLETION, 8_000L);
        return values;
    }
}
