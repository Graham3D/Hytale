package com.inigmasgames.persistentnpcs.voice;

/** The complete event vocabulary exposed by the official Chatterbox Turbo demo. */
public enum ParalinguisticEvent {
    CLEAR_THROAT("[clear throat]"),
    SIGH("[sigh]"),
    SHUSH("[shush]"),
    COUGH("[cough]"),
    GROAN("[groan]"),
    SNIFF("[sniff]"),
    GASP("[gasp]"),
    CHUCKLE("[chuckle]"),
    LAUGH("[laugh]");

    private final String tag;

    ParalinguisticEvent(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }
}
