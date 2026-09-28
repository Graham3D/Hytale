package com.inigmasgames.hytalerpg.difficulty;

import java.util.*;

/** Durable, world-owned death receipt; only constructed after authoritative contribution/death validation. */
public record MilestoneAward(UUID world,UUID enemy,String eventId,long completedAt,GolemEncounter encounter) {
    public MilestoneAward {
        Objects.requireNonNull(world);Objects.requireNonNull(enemy);Objects.requireNonNull(encounter);
        if(!("enemy-death/"+world+"/"+enemy).equals(eventId)||completedAt<0)throw new IllegalArgumentException("INVALID_MILESTONE_DEATH");
    }
    public DifficultyProgress apply(DifficultyProgress current){
        var next=current.complete(encounter.difficulty(),encounter.milestone());
        if(encounter.difficulty()!=DifficultyId.HELL&&next.milestones().get(encounter.difficulty()).containsAll(GolemMilestones.REQUIRED_V1))
            next=next.unlockNext(encounter.difficulty(),GolemMilestones.REQUIRED_V1);
        return next;
    }
}
