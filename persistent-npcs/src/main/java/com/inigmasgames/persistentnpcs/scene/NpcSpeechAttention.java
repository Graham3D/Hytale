package com.inigmasgames.persistentnpcs.scene;

import java.util.UUID;

/** Adapter boundary for pausing/resuming interruptible Hytale behavior and head focus. */
public interface NpcSpeechAttention {
    boolean beginListening(UUID listenerNpcId, UUID speakerNpcId, UUID conversationId);
    void finishListening(UUID listenerNpcId, UUID conversationId, boolean interrupted);

    static NpcSpeechAttention noOp() {
        return new NpcSpeechAttention() {
            @Override public boolean beginListening(UUID listener, UUID speaker, UUID scene) {
                return true;
            }
            @Override public void finishListening(UUID listener, UUID scene, boolean interrupted) {
            }
        };
    }
}
