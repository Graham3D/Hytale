package com.inigmasgames.hytalerpg.progress;

import java.util.*;

/** Ownership participates in the same write-ahead transaction as spending and learning. */
public record AcquisitionCheckpoint(AcquisitionProgress progress,Set<String> learnedSkills,Map<String,Integer> ownedPassives) {
    public AcquisitionCheckpoint {
        Objects.requireNonNull(progress);
        if(learnedSkills==null||learnedSkills.size()>89||ownedPassives==null||ownedPassives.size()>67)
            throw new IllegalArgumentException("INVALID_ACQUISITION_OWNERSHIP");
        var learned=new TreeSet<String>();for(String id:learnedSkills){AcquisitionProgress.id(id);learned.add(id);}
        var owned=new TreeMap<String,Integer>();ownedPassives.forEach((id,count)->{
            AcquisitionProgress.id(id);if(count==null||count<0)throw new IllegalArgumentException("INVALID_OWNED_PASSIVE_COUNT");owned.put(id,count);
        });
        learnedSkills=Collections.unmodifiableSet(learned);ownedPassives=Collections.unmodifiableMap(owned);
    }
    public static AcquisitionCheckpoint of(RpgPlayerState state){return new AcquisitionCheckpoint(state.acquisition,state.learnedSkills,state.ownedPassives);}
    public AcquisitionCheckpoint advance(ProgressionDelta delta,long earnedInsight){
        progress.availableInsight(earnedInsight);
        if(delta==null)return this;
        var used=new TreeSet<>(progress.meaningfulSkills());var pity=new TreeMap<>(progress.pity());
        var learned=new TreeSet<>(learnedSkills);var owned=new TreeMap<>(ownedPassives);long spent=progress.spentInsight();
        switch(delta.kind()){
            case MEANINGFUL_USE -> used.add(delta.subject());
            case LEARNING_FAILURE -> {
                if(learned.contains(delta.subject()))throw new IllegalStateException("KNOWN_SKILL_MUST_NOT_REROLL");
                pity.merge(delta.source(),1,Math::addExact);
            }
            case LEARNING_SUCCESS -> {
                if(!learned.add(delta.subject()))throw new IllegalStateException("KNOWN_SKILL_MUST_NOT_REROLL");
                pity.remove(delta.source());
            }
            case PASSIVE_PURCHASE -> {
                if(progress.availableInsight(earnedInsight)<delta.insightCost())throw new IllegalArgumentException("INSUFFICIENT_LINK_INSIGHT");
                spent=Math.addExact(spent,delta.insightCost());owned.merge(delta.subject(),1,Math::addExact);
            }
        }
        return new AcquisitionCheckpoint(new AcquisitionProgress(used,pity,spent),learned,owned);
    }
    public void applyTo(RpgPlayerState state){state.acquisition=progress;state.learnedSkills=new LinkedHashSet<>(learnedSkills);state.ownedPassives=new LinkedHashMap<>(ownedPassives);}
}
