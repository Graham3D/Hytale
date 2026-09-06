package com.inigmasgames.hytalerpg.ui.model;

import com.inigmasgames.hytalerpg.combat.attribute.DerivedStats;

public record CharacterSheetViewModel(long revision, String displayName, XpView xp,
                                      int unspentAttributePoints, int pendingLevelUpPoints,
                                      DerivedStats derivedStats, NativeResourceView mana,
                                      NativeResourceView health, NativeResourceView stamina) { }
