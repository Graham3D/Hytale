package com.inigmasgames.hytalerpg.ui.hud;

import com.inigmasgames.hytalerpg.ui.model.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class R261CResourceHudTest {
    private static RpgHudViewModel model(BarrierView barrier){
        return new RpgHudViewModel(1,new NativeResourceView(27,140),new NativeResourceView(84,388),
                new NativeResourceView(100,100),new XpView(1,0,0,0,1,0,List.of()),0,List.of(),barrier);
    }
    @Test void readsNativeCurrentAndMaximumAndDisplaysAuthoritativeBarrierOnlyWhenActive() throws Exception {
        var ui=Files.readString(Path.of("src/main/resources/Common/UI/Custom/RpgHud.ui"));
        assertTrue(ui.contains("#RpgHealthValue")&&ui.contains("#RpgManaValue")&&ui.contains("#RpgBarrierMeter"));
        assertFalse(ui.contains("#HealthBar")||ui.contains("#ManaBar"));
        var active=model(new BarrierView(8,UUID.randomUUID(),72,160));
        assertEquals("84.0 / 388.0",RpgHud.resourceText(active.health()));
        assertEquals("27.0 / 140.0",RpgHud.resourceText(active.mana()));
        assertEquals(182,RpgHud.barrierFillWidth(active.barrier()));
        var depleted=model(new BarrierView(9,null,0,0));
        assertEquals(0,RpgHud.barrierFillWidth(depleted.barrier()));
    }
    @Test void malformedBarrierStateCannotAppearActive(){
        assertThrows(IllegalArgumentException.class,()->new BarrierView(1,null,10,100));
        assertThrows(IllegalArgumentException.class,()->new BarrierView(1,UUID.randomUUID(),120,100));
    }
    @Test void staleBarrierCannotReappearAfterAnAuthoritativeClear(){
        var generation=UUID.randomUUID();
        var active=new BarrierView(9,generation,30,100);
        var cleared=BarrierView.NONE;
        assertEquals(cleared,RpgHudCoordinator.selectBarrier(active,cleared,9));
        assertEquals(cleared,RpgHudCoordinator.selectBarrier(cleared,new BarrierView(8,generation,45,100),9));
        assertEquals(cleared,RpgHudCoordinator.selectBarrier(cleared,active,9));
        var recast=new BarrierView(10,UUID.randomUUID(),100,100);
        assertEquals(recast,RpgHudCoordinator.selectBarrier(cleared,recast,9));
    }
}
