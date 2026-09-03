package com.inigmasgames.persistentnpcs.orbis;

/** Immutable physical/logical ingress provenance captured before a turn is created. */
public enum TurnIngressSource {
    VOICE_CAPTURE("VOICE_CAPTURE -> STT -> AUTHORITATIVE_TRANSCRIPT", true),
    NATIVE_TEXT_CHAT("NATIVE_TEXT_CHAT -> AUTHORITATIVE_TRANSCRIPT", false),
    MANUAL_SUBMISSION("MANUAL_SUBMISSION -> AUTHORITATIVE_TRANSCRIPT", false),
    NPC_INITIATED_INTERNAL("NPC_INITIATED_INTERNAL -> AUTHORITATIVE_TRANSCRIPT", false),
    AUTHORITATIVE_EVALUATION_TEXT(
            "AUTHORITATIVE_EVALUATION_TEXT -> AUTHORITATIVE_TRANSCRIPT", false),
    UNKNOWN_TEXT("UNKNOWN_TEXT -> AUTHORITATIVE_TRANSCRIPT", false);

    private final String chain;
    private final boolean physicalVoice;

    TurnIngressSource(String chain, boolean physicalVoice) {
        this.chain = chain;
        this.physicalVoice = physicalVoice;
    }

    public String chain() { return chain; }
    public boolean physicalVoice() { return physicalVoice; }
}
