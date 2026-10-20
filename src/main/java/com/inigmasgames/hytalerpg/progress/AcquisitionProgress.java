package com.inigmasgames.hytalerpg.progress;

import java.util.*;

/** Durable eligibility/pity and spent (not earned) Insight. No equipment-derived counters. */
public record AcquisitionProgress(Set<String> meaningfulSkills,Map<String,Integer> pity,long spentInsight) {
    public static final AcquisitionProgress INITIAL=new AcquisitionProgress(Set.of(),Map.of(),0);
    public AcquisitionProgress {
        if(meaningfulSkills==null||meaningfulSkills.size()>87||pity==null||pity.size()>66||spentInsight<0)
            throw new IllegalArgumentException("INVALID_ACQUISITION_PROGRESS");
        var used=new TreeSet<String>();for(String id:meaningfulSkills){id(id);used.add(id);}
        var failures=new TreeMap<String,Integer>();pity.forEach((source,count)->{
            id(source);if(count==null||count<0||count>150)throw new IllegalArgumentException("INVALID_PITY_COUNTER");
            failures.put(source,count);
        });
        meaningfulSkills=Collections.unmodifiableSet(used);pity=Collections.unmodifiableMap(failures);
    }
    public long availableInsight(long earned){
        if(earned<spentInsight)throw new IllegalStateException("INSIGHT_SPENT_EXCEEDS_EARNED");
        return earned-spentInsight;
    }
    static void id(String value){if(value==null||!value.matches("[a-z][a-z0-9_]{0,95}"))throw new IllegalArgumentException("INVALID_PROGRESSION_CONTENT_ID");}
}
