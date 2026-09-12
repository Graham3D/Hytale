package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.hytale.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.ui.hud.CooldownSweep;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage13PresentationABTest {
    @Test void stationaryTetherIsPerfectlyStraightAndPinsBothEndpoints(){
        var tether=new ElasticBeamTether();var a=new Vec3(1,5,3);var b=new Vec3(13,8,-9);var points=tether.update(a,b,0);
        assertEquals(ElasticBeamTether.PIECES+1,points.size());assertEquals(a,points.getFirst());assertEquals(b,points.getLast());
        for(int i=0;i<points.size();i++)assertEquals(ElasticBeamTether.linear(a,b,i/(double)ElasticBeamTether.PIECES),points.get(i));
        assertEquals(points,tether.update(a,b,.05));
    }
    @Test void endpointMotionCreatesBoundedOpposingLagThenCriticallyDampsToStraight(){
        var tether=new ElasticBeamTether();var start=Vec3.ZERO;var end=new Vec3(6,0,0);tether.update(start,end,0);
        var movedStart=new Vec3(0,2,1);var movedEnd=new Vec3(8,3,1);var moving=tether.update(movedStart,movedEnd,.05);
        assertEquals(movedStart,moving.getFirst());assertEquals(movedEnd,moving.getLast());
        boolean lag=false;
        for(int i=1;i<ElasticBeamTether.PIECES;i++){
            var desired=ElasticBeamTether.linear(movedStart,movedEnd,i/(double)ElasticBeamTether.PIECES);
            assertTrue(moving.get(i).subtract(desired).length()<=1.35+1e-9);lag|=moving.get(i).distanceSquared(desired)>1e-4;
        }
        assertTrue(lag);
        List<Vec3> settled=moving;
        for(int i=2;i<=240;i++)settled=tether.update(movedStart,movedEnd,i*.05);
        for(int i=0;i<settled.size();i++)assertEquals(ElasticBeamTether.linear(movedStart,movedEnd,i/(double)ElasticBeamTether.PIECES),settled.get(i));
    }
    @Test void branchesOwnIndependentMotionHistoryAndAbruptReversalStaysBounded(){
        var primary=new ElasticBeamTether();var branch=new ElasticBeamTether();var a=Vec3.ZERO;var b=new Vec3(6,0,0);
        primary.update(a,b,0);branch.update(a,b,0);var first=primary.update(new Vec3(2,0,0),new Vec3(8,0,0),.05);
        var untouched=branch.update(a,b,.05);for(int i=0;i<untouched.size();i++)assertEquals(ElasticBeamTether.linear(a,b,i/(double)ElasticBeamTether.PIECES),untouched.get(i));
        var reversed=primary.update(new Vec3(-2,0,0),new Vec3(4,0,0),.10);
        assertNotEquals(first.get(3),reversed.get(3));
        for(int i=1;i<ElasticBeamTether.PIECES;i++)assertTrue(reversed.get(i).subtract(ElasticBeamTether.linear(new Vec3(-2,0,0),new Vec3(4,0,0),i/(double)ElasticBeamTether.PIECES)).length()<=1.35+1e-9);
    }
    @Test void packagedNativeBeamUsesAuditedShippedNatureTexture() throws Exception {
        try(var input=getClass().getResourceAsStream("/Server/Entity/Beams/RPG_Healing.json")){
            assertNotNull(input);var text=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(text.contains("Trails/Void_Green.png"));
        }
        var source=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSkillExecutionSystem.java"));
        assertFalse(source.contains("spawnParticleEffect(\"Beam_Heal_Green\""));assertTrue(source.contains("healingBeamVisuals.present"));
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
