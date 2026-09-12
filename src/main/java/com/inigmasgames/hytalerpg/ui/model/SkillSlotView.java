package com.inigmasgames.hytalerpg.ui.model;

import com.inigmasgames.hytalerpg.domain.SkillSlot;

public record SkillSlotView(SkillSlot slot, String action, String skillId, String name,
                            String iconKey, double cooldownRemainingSeconds, State state,
                            String unavailableReason, double cooldownDurationSeconds) {
    public SkillSlotView(SkillSlot slot,String action,String skillId,String name,String iconKey,double remaining,State state,String reason){
        this(slot,action,skillId,name,iconKey,remaining,state,reason,remaining);
    }
    public enum State { EMPTY, READY, COOLDOWN, INSUFFICIENT_RESOURCE, UNAVAILABLE }
}
