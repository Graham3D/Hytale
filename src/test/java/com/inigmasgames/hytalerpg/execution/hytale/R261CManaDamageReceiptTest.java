package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class R261CManaDamageReceiptTest {
    @Test void eachNativeDamageEventIsEligibleAtMostOnce(){
        var first=new Damage(Damage.NULL_SOURCE,0,10f);
        var second=new Damage(Damage.NULL_SOURCE,0,10f);
        assertTrue(SupportDamageSystems.claimManaBeforeLife(first));
        assertFalse(SupportDamageSystems.claimManaBeforeLife(first));
        assertTrue(SupportDamageSystems.claimManaBeforeLife(second));
        second.setAmount(0);
        assertFalse(SupportDamageSystems.claimManaBeforeLife(second));
        var zero=new Damage(Damage.NULL_SOURCE,0,0f);
        assertFalse(SupportDamageSystems.claimManaBeforeLife(zero));
    }
}
