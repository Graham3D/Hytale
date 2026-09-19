package com.inigmasgames.persistentnpcs.ai;

public record AiProviderMetrics(
        long requestCount,
        long failureCount,
        int activeRequests,
        int queueDepth,
        int concurrencyLimit,
        long latestTotalLatencyMillis,
        long latestNetworkLatencyMillis,
        long latestInferenceLatencyMillis,
        String latestFailure,
        String latestProviderId,
        boolean fallbackActive) {

    public AiProviderMetrics {
        latestFailure = latestFailure == null ? "" : latestFailure;
        latestProviderId = latestProviderId == null ? "" : latestProviderId;
    }

    public static AiProviderMetrics empty(int concurrencyLimit) {
        return new AiProviderMetrics(0, 0, 0, 0, Math.max(1, concurrencyLimit),
                0, 0, 0, "", "", false);
    }
}
