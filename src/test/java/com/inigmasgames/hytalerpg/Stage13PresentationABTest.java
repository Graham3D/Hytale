package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.hytale.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.ui.hud.CooldownSweep;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage13PresentationABTest {
    @Test void curvePinsEndpointsSagsAndEveryNegativeZVelocityFollowsItsOwnTangent(){
        var a=new Vec3(1,5,3);var b=new Vec3(13,8,-9);
        assertEquals(a,NativeBeamTransform.curve(a,b,0));assertEquals(b,NativeBeamTransform.curve(a,b,1));
        assertTrue(NativeBeamTransform.curve(a,b,.5).y()<(a.y()+b.y())*.5);
        var samples=NativeBeamTransform.stream(a,b);assertEquals(23,samples.size());
        for(int i=0;i<samples.size();i++){
            double t=(double)i/samples.size();var sample=samples.get(i);
            var tangent=NativeBeamTransform.curve(a,b,t+.001).subtract(sample.position()).normalized();
            var actual=sample.rotation().transform(new org.joml.Vector3d(0,0,-1));
            assertEquals(tangent.x(),actual.x,1e-6);assertEquals(tangent.y(),actual.y,1e-6);assertEquals(tangent.z(),actual.z,1e-6);
        }
        assertEquals(24,NativeBeamTransform.stream(Vec3.ZERO,new Vec3(1000,0,0)).size());
        assertTrue(NativeBeamTransform.stream(a,a).isEmpty());
    }
    @Test void sweepUsesAuthoritativeRemainingRatherThanAnotherTimer(){
        assertEquals(0,CooldownSweep.progress(3,3));assertEquals(.5,CooldownSweep.progress(1.5,3));
        assertEquals(.9,CooldownSweep.progress(.3,3),1e-6);assertEquals(0,CooldownSweep.progress(0,3));
        assertEquals(0,CooldownSweep.progress(6,3));assertEquals(0,CooldownSweep.progress(Double.NaN,3));
    }
    @Test void blizzardThreeSecondProfileCannotBeRecastDuringItsActiveLifetime(){
        var h=new Stage13HealingBlizzardTest.Falling();assertEquals(3,h.c.profile().cooldownSeconds());assertEquals(3,h.c.profile().area().lifetimeSeconds());
        h.start();assertEquals(3,h.runtime.activeRemaining(h.c.request().actorId(),"blizzard",0));
        assertEquals("BLIZZARD_ALREADY_ACTIVE",h.runtime.admission(h.c.request().actorId(),"blizzard",false));
        for(int i=1;i<60;i++)h.step(i*.05);
        assertEquals("BLIZZARD_ALREADY_ACTIVE",h.runtime.admission(h.c.request().actorId(),"blizzard",false));
        h.step(3);assertEquals(0,h.runtime.activeRemaining(h.c.request().actorId(),"blizzard",3));
        assertEquals("PASS",h.runtime.admission(h.c.request().actorId(),"blizzard",false));
    }
    @Test void snowPacketBeginsAtRootAndBoundsEmissionPlusParticleTailByRemainingLifetime(){
        var h=new Stage13HealingBlizzardTest.Falling();var geometry=h.c.profile().area().footprint(Vec3.ZERO,Vec3.FORWARD,1);
        var first=NativeBlizzardVisuals.stormPacket(geometry,3);assertEquals("RPG_Blizzard_Snow",first.particleSystemId);
        assertEquals(.1,first.maxDuration,1e-6);assertEquals(6,first.scale);assertEquals(2,first.position.y);
        var last=NativeBlizzardVisuals.stormPacket(geometry,.24);assertEquals(.04,last.maxDuration,1e-6);
        assertTrue(last.maxDuration+.2<=.24+1e-6);
    }
    @Test void nativeRadialDocumentHasOnlyTwoReadOnlyOverlaysAndNoResourceControls() throws Exception {
        try(var input=getClass().getResourceAsStream("/Common/UI/Custom/RpgCooldownSweep.ui")){
            assertNotNull(input);var text=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(2,text.split("CircularProgressBar #",-1).length-1);
            assertFalse(text.contains("Button"));assertFalse(text.contains("#Mana"));assertFalse(text.contains("#Health"));assertFalse(text.contains("#Stamina"));
            assertTrue(text.contains("Right: 176"));assertTrue(text.contains("Right: 82"));
        }
    }
}
