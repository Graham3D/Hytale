package com.inigmasgames.hytalerpg.enemies;

import com.hypixel.hytale.codec.ExtraInfo;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyEngagementClockTest {
    @Test void engagedTimeCrossesOnlyTheAuthoredWindowAndPausesWithoutReset(){
        var clock=new EnemyEngagementClock(new EnemyEngagementClock.State(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),7,7999,0));
        assertFalse(clock.advance(.0005f,true,false,8000));
        assertEquals(7999,clock.state().phaseMillis());
        assertFalse(clock.advance(2f,false,false,8000));
        assertFalse(clock.advance(2f,true,true,8000));
        assertEquals(7999,clock.state().phaseMillis());
        assertTrue(clock.advance(.0005f,true,false,8000));
        assertEquals(8000,clock.state().phaseMillis());
        var saved=clock.clone();
        var reloaded=EnemyEngagementClock.CODEC.decode(
                EnemyEngagementClock.CODEC.encode(saved,new ExtraInfo()),new ExtraInfo());
        assertEquals(saved.state(),reloaded.state());
        assertFalse(saved.advance(3.999f,true,false,8000));
        assertTrue(saved.advance(.001f,true,false,8000));
        assertEquals(0,saved.state().phaseMillis());
        assertEquals(8000,clock.state().phaseMillis());
    }

    @Test void fractionalTicksDoNotLoseTimeAndInvalidOrStaleStateFailsClosed(){
        var world=UUID.randomUUID();var actor=UUID.randomUUID();var entity=UUID.randomUUID();
        var clock=new EnemyEngagementClock(new EnemyEngagementClock.State(world,actor,entity,3,0,0));
        for(int i=0;i<60;i++)clock.advance(1f/60,true,false,8000);
        assertEquals(1000,clock.state().phaseMillis());
        assertThrows(IllegalArgumentException.class,()->clock.advance(Float.NaN,true,false,8000));
        assertThrows(IllegalArgumentException.class,()->clock.advance(1f,true,false,12000));
        assertThrows(IllegalArgumentException.class,()->new EnemyEngagementClock.State(world,actor,entity,3,12000,0));
        assertThrows(IllegalArgumentException.class,()->new EnemyEngagementClock.State(world,actor,entity,3,0,1));
    }
}
