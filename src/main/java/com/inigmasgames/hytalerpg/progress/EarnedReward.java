package com.inigmasgames.hytalerpg.progress;

import java.util.*;

/** Internal award request, never a public client grant API. Caller must establish native eligibility. */
public record EarnedReward(String eventId,long characterXp,long insight,Map<String,Long> mastery,
                          String reason,String rootCastId,String skillInstanceId,String correlationId,ProgressionDelta progression,
                          com.inigmasgames.hytalerpg.difficulty.MilestoneAward milestone) {
    public EarnedReward(String eventId,long characterXp,long insight,Map<String,Long> mastery,String reason,String rootCastId,String skillInstanceId,String correlationId,ProgressionDelta progression){
        this(eventId,characterXp,insight,mastery,reason,rootCastId,skillInstanceId,correlationId,progression,null);
    }
    /** Retain the exact v1 JSON/hash shape for old pending intents and receipts (Gson omits null). */
    public EarnedReward(String eventId,long characterXp,long insight,Map<String,Long> mastery,String reason,String rootCastId,String skillInstanceId,String correlationId){
        this(eventId,characterXp,insight,mastery,reason,rootCastId,skillInstanceId,correlationId,null);
    }
    public EarnedReward {
        text(eventId,256);text(reason,128);text(correlationId,128);
        optionalId(rootCastId);optionalId(skillInstanceId);
        if(characterXp<0||insight<0||mastery==null||mastery.size()>com.inigmasgames.hytalerpg.content.RpgCatalog.EXPECTED_SKILLS)throw new IllegalArgumentException("INVALID_REWARD");
        TreeMap<String,Long> sorted=new TreeMap<>();
        mastery.forEach((skill,value)->{
            if(skill==null||!skill.matches("[a-z][a-z0-9_]{0,95}")||value==null||value<=0)
                throw new IllegalArgumentException("INVALID_MASTERY_AWARD");
            sorted.put(skill,value);
        });
        mastery=Collections.unmodifiableMap(sorted);
        if(milestone!=null&&!eventId.equals(milestone.eventId()))throw new IllegalArgumentException("MILESTONE_REWARD_ID_MISMATCH");
        if(characterXp==0&&insight==0&&mastery.isEmpty()&&progression==null&&milestone==null)throw new IllegalArgumentException("EMPTY_REWARD");
    }
    private static void optionalId(String value){if(value==null)throw new IllegalArgumentException("NULL_REWARD_ID");if(!value.isEmpty())text(value,128);}
    private static void text(String value,int limit){
        if(value==null||value.isBlank()||value.length()>limit||value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("INVALID_REWARD_ID");
    }
}
