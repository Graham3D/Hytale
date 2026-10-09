package com.inigmasgames.hytalerpg.ui.model;

import com.inigmasgames.hytalerpg.combat.attribute.DerivedStats;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;

public record CharacterSheetViewModel(long revision, String displayName, XpView xp,
                                      int unspentAttributePoints, int pendingLevelUpPoints,
                                      DerivedStats derivedStats, NativeResourceView mana,
                                      NativeResourceView health, NativeResourceView stamina) {
    /** Player-facing attributes precede diminishing returns, as equipment gates do. */
    public String attributeText(RpgAttribute attribute) {
        return Integer.toString(derivedStats.rawAttributes().get(attribute));
    }
}
