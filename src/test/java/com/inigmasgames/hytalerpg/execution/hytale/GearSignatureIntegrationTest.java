package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.combat.status.ControlledGearSnapshot;
import com.inigmasgames.hytalerpg.gear.GearCombatEffects;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GearSignatureIntegrationTest {
    @Test void cancelledFirstChannelCannotSuppressAcceptedLaterChannel(){
        var aggregate=new GearSignatureBindings.Aggregate(null,Long.MAX_VALUE);
        assertFalse(aggregate.observe(GearCombatEffects.Channel.PHYSICAL,0,0,100,true,false,2));
        assertTrue(aggregate.observe(GearCombatEffects.Channel.FIRE,7,11,93,false,false,2));
        assertEquals(7,aggregate.loss);
        assertEquals(11,aggregate.admitted);
        assertFalse(aggregate.blocked);
        assertThrows(IllegalStateException.class,()->aggregate.observe(
                GearCombatEffects.Channel.FIRE,7,11,86,false,false,2));
    }

    @Test void zeroAuthoredChannelDoesNotDelayCompletionAndAbsorptionIsAccepted(){
        var snapshot=ControlledGearSnapshot.with("WA-053");
        var hit=new GearCombatEffects.Hit(snapshot.items().getFirst().identity(),"revision","root",
                Map.of(GearCombatEffects.Channel.PHYSICAL,12d,GearCombatEffects.Channel.FIRE,0d),
                Map.of(),Map.of(),false,1.5,snapshot,null);
        assertEquals(1,GearSignatureBindings.positiveChannels(hit));
        var aggregate=new GearSignatureBindings.Aggregate(null,Long.MAX_VALUE);
        assertTrue(aggregate.observe(GearCombatEffects.Channel.PHYSICAL,0,12,100,false,false,
                GearSignatureBindings.positiveChannels(hit)));
        assertFalse(aggregate.blocked);
        assertEquals(12,aggregate.admitted);
        assertEquals(0,aggregate.loss);
    }
}
