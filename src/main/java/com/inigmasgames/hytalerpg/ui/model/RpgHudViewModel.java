package com.inigmasgames.hytalerpg.ui.model;

import java.util.List;

public record RpgHudViewModel(long revision, NativeResourceView mana, NativeResourceView health,
                              NativeResourceView stamina, XpView xp, int pendingLevelUpPoints,
                              List<SkillSlotView> skills) {
    public RpgHudViewModel { skills = List.copyOf(skills); }
    public boolean showLevelUpNotice() { return pendingLevelUpPoints > 0; }
}
