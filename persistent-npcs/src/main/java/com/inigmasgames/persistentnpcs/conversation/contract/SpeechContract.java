package com.inigmasgames.persistentnpcs.conversation.contract;

/** Canonical response and TTS delivery constraints for one turn. */
public record SpeechContract(
        Mode mode,
        boolean earlySpeech,
        int maximumSegmentCharacters,
        boolean visibleDialogue) {
    public enum Mode { PLAIN_STREAMING, VALIDATED_AFTER_CONTRACT, NONE }

    public SpeechContract {
        mode = mode == null ? Mode.NONE : mode;
        if (mode != Mode.NONE && (maximumSegmentCharacters < 80
                || maximumSegmentCharacters > 280)) {
            throw new IllegalArgumentException("speech segment ceiling must be 80-280 chars");
        }
        if (earlySpeech && mode != Mode.PLAIN_STREAMING) {
            throw new IllegalArgumentException("early speech requires plain streaming");
        }
    }

    public static SpeechContract plain(boolean early) {
        return new SpeechContract(Mode.PLAIN_STREAMING, early, 220, true);
    }

    public static SpeechContract afterValidation() {
        return new SpeechContract(Mode.VALIDATED_AFTER_CONTRACT, false, 220, true);
    }

    public static SpeechContract silent() {
        return new SpeechContract(Mode.NONE, false, 0, false);
    }
}
