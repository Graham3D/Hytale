package com.inigmasgames.persistentnpcs.monster;

public record MonsterIntentResult(
        boolean accepted,
        ImmersiveEntityAgent agent,
        boolean nativeBehaviorContinues,
        String reason) {
}
