package com.inigmasgames.hytalerpg.difficulty;

import java.util.*;

/** Immutable character ledger. Milestone IDs are authored contracts, never guessed native role names. */
public record DifficultyProgress(long revision, Set<DifficultyId> unlocks,
                                 Map<DifficultyId, Set<String>> milestones) {
    public static final DifficultyProgress INITIAL = new DifficultyProgress(0, Set.of(DifficultyId.NORMAL),
            Map.of(DifficultyId.NORMAL, Set.of(), DifficultyId.NIGHTMARE, Set.of(), DifficultyId.HELL, Set.of()));
    public DifficultyProgress {
        if (revision < 0) throw new IllegalArgumentException("INVALID_DIFFICULTY_REVISION");
        unlocks = Collections.unmodifiableSet(unlocks.isEmpty()?EnumSet.noneOf(DifficultyId.class):EnumSet.copyOf(unlocks));
        if (!unlocks.contains(DifficultyId.NORMAL) || unlocks.contains(DifficultyId.HELL) && !unlocks.contains(DifficultyId.NIGHTMARE))
            throw new IllegalArgumentException("INVALID_DIFFICULTY_UNLOCKS");
        var copy = new EnumMap<DifficultyId, Set<String>>(DifficultyId.class);
        milestones.forEach((mode, ids) -> {
            if (ids.size() > 256) throw new IllegalArgumentException("MILESTONE_BUDGET");
            for (String id : ids) if (id == null || !id.matches("[a-z][a-z0-9_.-]{0,127}"))
                throw new IllegalArgumentException("INVALID_MILESTONE_ID");
            copy.put(Objects.requireNonNull(mode), Collections.unmodifiableSet(new TreeSet<>(ids)));
        });
        if (!copy.keySet().equals(EnumSet.allOf(DifficultyId.class))) throw new IllegalArgumentException("INCOMPLETE_DIFFICULTY_LEDGER");
        milestones = Collections.unmodifiableMap(copy);
    }
    public boolean unlocked(DifficultyId difficulty) { return unlocks.contains(Objects.requireNonNull(difficulty)); }
    /** Domain operation only; the existing player persistence owner must durably commit the returned value. */
    public DifficultyProgress complete(DifficultyId mode, String milestone) {
        Objects.requireNonNull(mode);
        if (!unlocked(mode)) throw new IllegalStateException("DIFFICULTY_LOCKED");
        if (milestones.get(mode).contains(milestone)) return this;
        var all = new EnumMap<DifficultyId, Set<String>>(milestones);
        var done = new HashSet<>(all.get(mode)); done.add(milestone); all.put(mode, done);
        return new DifficultyProgress(Math.addExact(revision, 1), unlocks, all);
    }
    /** Stage 2 supplies an audited, nonempty checklist after authoritative encounter ownership succeeds. */
    public DifficultyProgress unlockNext(DifficultyId completedMode, Set<String> requiredMilestones) {
        Objects.requireNonNull(completedMode); Objects.requireNonNull(requiredMilestones);
        if (requiredMilestones.isEmpty() || !unlocked(completedMode) || !milestones.get(completedMode).containsAll(requiredMilestones))
            throw new IllegalStateException("MILESTONES_INCOMPLETE");
        if (completedMode == DifficultyId.HELL) throw new IllegalArgumentException("NO_NEXT_DIFFICULTY");
        var next = DifficultyId.values()[completedMode.ordinal() + 1];
        if (unlocked(next)) return this;
        var allowed = EnumSet.copyOf(unlocks); allowed.add(next);
        return new DifficultyProgress(Math.addExact(revision, 1), allowed, milestones);
    }
}
