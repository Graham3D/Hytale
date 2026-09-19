package com.inigmasgames.persistentnpcs.voice;

/** The only authored Chatterbox conditioning categories owned by an NPC profile. */
public enum VoiceSampleType {
    REFERENCE("Reference", "reference"),
    AFFECTIONATE("Affectionate", "affectionate"),
    AMUSED("Amused", "amused"),
    EXCITED("Excited", "excited"),
    ANGRY("Angry", "angry"),
    SAD("Sad", "sad"),
    SCARED("Scared", "scared");

    private final String label;
    private final String filenameToken;

    VoiceSampleType(String label, String filenameToken) {
        this.label = label;
        this.filenameToken = filenameToken;
    }

    public String label() { return label; }
    public String filenameToken() { return filenameToken; }

    /** Legacy cognition emotions remain valid, but no longer own separate WAV categories. */
    public static VoiceSampleType forEmotion(VocalEmotion emotion) {
        return switch (emotion == null ? VocalEmotion.CALM : emotion) {
            case AFFECTIONATE, FRIENDLY, TENDER -> AFFECTIONATE;
            case AMUSED -> AMUSED;
            case EXCITED -> EXCITED;
            case ANGRY -> ANGRY;
            case SAD -> SAD;
            case SCARED, AFRAID, UNEASY -> SCARED;
            case CALM, CURIOUS, WHISPERING -> REFERENCE;
        };
    }
}
