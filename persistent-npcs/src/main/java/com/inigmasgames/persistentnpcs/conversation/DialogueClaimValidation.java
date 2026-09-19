package com.inigmasgames.persistentnpcs.conversation;

public record DialogueClaimValidation(
        String dialogue,
        boolean claimedCurrentAction,
        boolean rewritten,
        String reason) {
}
