package com.inigmasgames.persistentnpcs.scene;

public enum NpcActivityState {
    IDLE,
    WORKING,
    WANDERING,
    RETURNING,
    LISTENING_TO_NPC,
    CONVERSING_WITH_PLAYER,
    COMBAT,
    FLEEING,
    INCAPACITATED;

    public boolean canHearNpcSpeech() {
        return this != COMBAT && this != FLEEING && this != INCAPACITATED
                && this != CONVERSING_WITH_PLAYER;
    }

    public boolean interruptible() {
        return this == IDLE || this == WORKING || this == WANDERING
                || this == RETURNING || this == LISTENING_TO_NPC;
    }
}
