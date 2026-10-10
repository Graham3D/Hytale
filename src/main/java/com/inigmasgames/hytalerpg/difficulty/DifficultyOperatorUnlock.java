package com.inigmasgames.hytalerpg.difficulty;

import java.util.Locale;
import java.util.Objects;

/** Operator-authored checklist completion; never records a kill or awards a reward. */
public final class DifficultyOperatorUnlock {
    private DifficultyOperatorUnlock() {}

    public static DifficultyId target(String argument) {
        if (argument == null) throw new IllegalArgumentException("Use nightmare or hell.");
        return switch (argument.trim().toLowerCase(Locale.ROOT)) {
            case "nightmare" -> DifficultyId.NIGHTMARE;
            case "hell" -> DifficultyId.HELL;
            default -> throw new IllegalArgumentException("Use nightmare or hell.");
        };
    }

    public static DifficultyProgress apply(DifficultyProgress current, DifficultyId target) {
        Objects.requireNonNull(current);
        if (target != DifficultyId.NIGHTMARE && target != DifficultyId.HELL)
            throw new IllegalArgumentException("Use nightmare or hell.");
        var next = completeAndUnlock(current, DifficultyId.NORMAL);
        if (target == DifficultyId.HELL) next = completeAndUnlock(next, DifficultyId.NIGHTMARE);
        return next;
    }

    private static DifficultyProgress completeAndUnlock(DifficultyProgress current, DifficultyId mode) {
        var next = current;
        for (String milestone : GolemMilestones.REQUIRED_V1.stream().sorted().toList())
            next = next.complete(mode, milestone);
        return next.unlockNext(mode, GolemMilestones.REQUIRED_V1);
    }
}
