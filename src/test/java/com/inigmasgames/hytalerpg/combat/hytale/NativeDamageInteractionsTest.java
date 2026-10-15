package com.inigmasgames.hytalerpg.combat.hytale;

import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DamageEntityInteraction;
import com.inigmasgames.hytale.patch.NativeDamageReceiptHook;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeDamageInteractionsTest {
    @Test void exactNativeLeafIsScopedAndRestored() throws Exception {
        var codecs=Interaction.CODEC;
        boolean absent=codecs.getClassFor("DamageEntity")==null;
        if(absent)codecs.register("DamageEntity",DamageEntityInteraction.class,DamageEntityInteraction.CODEC);
        try {
            try(var installed=NativeDamageInteractions.install(context->null)) {
                assertTrue(NativeDamageReceiptHook.installed());
                assertEquals(NativeDamageInteractions.ObservedDamage.class,codecs.getClassFor("DamageEntity"));
                assertThrows(IllegalStateException.class,()->NativeDamageInteractions.install(context->null));
            }
            assertFalse(NativeDamageReceiptHook.installed());
            assertEquals(DamageEntityInteraction.class,codecs.getClassFor("DamageEntity"));
        } finally {if(absent)codecs.remove(DamageEntityInteraction.class);}
    }
}
