package com.inigmasgames.hytalerpg.ui.model;

import java.util.List;

public record RpgHudViewModel(long revision, NativeResourceView mana, NativeResourceView health,
                              NativeResourceView stamina, XpView xp, int pendingLevelUpPoints,
                              List<SkillSlotView> skills, BarrierView barrier) {
    public RpgHudViewModel(long revision,NativeResourceView mana,NativeResourceView health,
                           NativeResourceView stamina,XpView xp,int pendingLevelUpPoints,List<SkillSlotView> skills) {
        this(revision,mana,health,stamina,xp,pendingLevelUpPoints,skills,BarrierView.NONE);
    }
    public RpgHudViewModel { skills = List.copyOf(skills); java.util.Objects.requireNonNull(barrier); }
    public RpgHudViewModel withBarrier(BarrierView next){
        return new RpgHudViewModel(revision,mana,health,stamina,xp,pendingLevelUpPoints,skills,next);
    }
    public boolean showLevelUpNotice() { return pendingLevelUpPoints > 0; }
}
