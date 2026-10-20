package com.inigmasgames.hytalerpg.ui.model;

import com.inigmasgames.hytalerpg.domain.SkillSlot;

public record SkillSlotView(SkillSlot slot, String action, String skillId, String name,
                            String iconKey, double cooldownRemainingSeconds, State state,
                            String unavailableReason) {
    public enum State { EMPTY, READY, COOLDOWN, UNAVAILABLE }
}
