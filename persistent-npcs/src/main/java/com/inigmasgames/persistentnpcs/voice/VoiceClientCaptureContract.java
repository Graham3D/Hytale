package com.inigmasgames.persistentnpcs.voice;

import com.hypixel.hytale.protocol.packets.voice.VoiceInputMode;

/** Read-only description of the current 0.6.3 client microphone transmission contract. */
public record VoiceClientCaptureContract(VoiceInputMode inputMode) {
    public static VoiceClientCaptureContract unknown() { return new VoiceClientCaptureContract(null); }

    public boolean speakWithoutPushToTalk() {
        return inputMode == VoiceInputMode.VoiceActivity;
    }

    /** Hytale 0.6.3 has no server-to-client packet/API that starts microphone capture. */
    public boolean serverActivationSupported() { return false; }

    public String display() {
        if (inputMode == VoiceInputMode.VoiceActivity) {
            return "Client microphone: Voice Activity; speak normally after Record.";
        }
        if (inputMode == VoiceInputMode.PushToTalk
                || inputMode == VoiceInputMode.PushToTalkToggle) {
            return "Client microphone: " + inputMode
                    + "; Hytale 0.6.3 does not let the server activate capture.";
        }
        return "Client microphone mode unavailable; server-side activation is unsupported.";
    }
}
