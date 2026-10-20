package com.inigmasgames.hytalerpg.progress;

import java.util.*;

/** Only fields modified by earned awards; resources, cooldowns, support and loadout are excluded. */
public record RewardCheckpoint(long currentXp,int level,int pendingPoints,int unspentPoints,
                               Map<String,Long> mastery,RewardLedger ledger,AcquisitionCheckpoint acquisition) {
    /** Null is deliberately retained for historical v1 intent hashes, never normalized while reading. */
    public RewardCheckpoint(long currentXp,int level,int pendingPoints,int unspentPoints,Map<String,Long> mastery,RewardLedger ledger){
        this(currentXp,level,pendingPoints,unspentPoints,mastery,ledger,null);
    }
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
        if(acquisition!=null)acquisition.progress().availableInsight(ledger.insight());
    }
    public static RewardCheckpoint of(RpgPlayerState state){return new RewardCheckpoint(state.currentXp,state.level,
            state.pendingLevelUpPoints,state.unspentAttributePoints,state.skillMastery,state.rewards,AcquisitionCheckpoint.of(state));}
    /** Only a historical checkpoint may omit fields that did not exist when its intent was written. */
    public boolean matches(RewardCheckpoint current){
        return current!=null&&currentXp==current.currentXp&&level==current.level&&pendingPoints==current.pendingPoints&&unspentPoints==current.unspentPoints
                &&mastery.equals(current.mastery)&&ledger.equals(current.ledger)&&(acquisition==null||acquisition.equals(current.acquisition));
    }
    public RewardCheckpoint advance(EarnedReward reward,String hash){
        var before=ProgressionMath.advance(currentXp,0);
        if(before.level()!=level)throw new IllegalStateException("REWARD_LEVEL_XP_MISMATCH: review legacy state; no automatic points or level reset");
        var advance=ProgressionMath.advance(currentXp,reward.characterXp());
        var nextMastery=new TreeMap<>(mastery);
        reward.mastery().forEach((id,value)->nextMastery.merge(id,value,Math::addExact));
        if(reward.progression()!=null&&acquisition==null)throw new IllegalArgumentException("PROGRESSION_REQUIRES_VERSIONED_CHECKPOINT");
        return new RewardCheckpoint(advance.totalXp(),advance.level(),Math.addExact(pendingPoints,advance.pendingPointAward()),
                Math.addExact(unspentPoints,advance.unspentPointAward()),nextMastery,
                new RewardLedger(Math.addExact(ledger.sequence(),1),hash,Math.addExact(ledger.insight(),reward.insight())),
                acquisition==null?null:acquisition.advance(reward.progression(),Math.addExact(ledger.insight(),reward.insight())));
    }
    public void applyTo(RpgPlayerState state){
        state.currentXp=currentXp;state.level=level;state.pendingLevelUpPoints=pendingPoints;
        state.unspentAttributePoints=unspentPoints;state.skillMastery=new LinkedHashMap<>(mastery);state.rewards=ledger;
        if(acquisition!=null)acquisition.applyTo(state);
    }
}
