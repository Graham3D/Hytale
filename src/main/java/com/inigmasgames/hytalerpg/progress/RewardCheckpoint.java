package com.inigmasgames.hytalerpg.progress;

import java.util.*;

/** Only fields modified by earned awards; resources, cooldowns, support and loadout are excluded. */
public record RewardCheckpoint(long currentXp,int level,int pendingPoints,int unspentPoints,
                               Map<String,Long> mastery,RewardLedger ledger) {
    public RewardCheckpoint {
        Objects.requireNonNull(ledger);
        if(currentXp<0||level<1||level>99||pendingPoints<0||unspentPoints<0||mastery==null||mastery.size()>87)
            throw new IllegalArgumentException("INVALID_REWARD_STATE");
        TreeMap<String,Long> sorted=new TreeMap<>();
        mastery.forEach((skill,value)->{
            if(skill==null||!skill.matches("[a-z][a-z0-9_]{0,95}")||value==null||value<0)
                throw new IllegalArgumentException("INVALID_SAVED_MASTERY");
            sorted.put(skill,value);
        });
        mastery=Collections.unmodifiableMap(sorted);
    }
    public static RewardCheckpoint of(RpgPlayerState state){return new RewardCheckpoint(state.currentXp,state.level,
            state.pendingLevelUpPoints,state.unspentAttributePoints,state.skillMastery,state.rewards);}
    public RewardCheckpoint advance(EarnedReward reward,String hash){
        var before=ProgressionMath.advance(currentXp,0);
        if(before.level()!=level)throw new IllegalStateException("REWARD_LEVEL_XP_MISMATCH: review legacy state; no automatic points or level reset");
        var advance=ProgressionMath.advance(currentXp,reward.characterXp());
        var nextMastery=new TreeMap<>(mastery);
        reward.mastery().forEach((id,value)->nextMastery.merge(id,value,Math::addExact));
        return new RewardCheckpoint(advance.totalXp(),advance.level(),Math.addExact(pendingPoints,advance.pendingPointAward()),
                Math.addExact(unspentPoints,advance.unspentPointAward()),nextMastery,
                new RewardLedger(Math.addExact(ledger.sequence(),1),hash,Math.addExact(ledger.insight(),reward.insight())));
    }
    public void applyTo(RpgPlayerState state){
        state.currentXp=currentXp;state.level=level;state.pendingLevelUpPoints=pendingPoints;
        state.unspentAttributePoints=unspentPoints;state.skillMastery=new LinkedHashMap<>(mastery);state.rewards=ledger;
    }
}
