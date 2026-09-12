package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.hytale.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class Stage13HealingTetherALTest {
    @Test void allSupportedExtentsUseActualEndpointsNotFixedParticleScale(){
        for(double length:new double[]{2,6,12,18,25.2}){
            var a=new Vec3(3,83,-9);var b=a.add(new Vec3(length,0,0));
            var points=new ElasticBeamTether().update(a,b,0);
            for(double t:new double[]{0,.05,.1,3,60,3600}){
                var spans=HealingTetherGeometry.frame(points,t);
                assertEquals(6,spans.stream().filter(s->!s.highlight()).count());
                assertTrue(spans.size()<=HealingTetherGeometry.MAX_SPANS);
                var core=spans.stream().filter(s->!s.highlight()).toList();
                assertEquals(a,core.getFirst().from());assertEquals(b,core.getLast().to());
                for(int i=1;i<core.size();i++)assertEquals(core.get(i-1).to(),core.get(i).from());
                for(var s:spans)for(var p:java.util.List.of(s.from(),s.to())){
                    assertTrue(p.x()>=a.x()-1e-9&&p.x()<=b.x()+1e-9);assertEquals(a.y(),p.y(),1e-9);assertEquals(a.z(),p.z(),1e-9);
                }
            }
        }
    }
    @Test void highlightsMoveTowardRecipientWhileContinuousCoreNeverChanges(){
        var points=new ElasticBeamTether().update(Vec3.ZERO,new Vec3(18,0,0),0);
        var a=HealingTetherGeometry.frame(points,.1);var b=HealingTetherGeometry.frame(points,.15);
        assertEquals(a.stream().filter(s->!s.highlight()).toList(),b.stream().filter(s->!s.highlight()).toList());
        double first=a.stream().filter(HealingTetherGeometry.Span::highlight).findFirst().orElseThrow().to().x();
        double next=b.stream().filter(HealingTetherGeometry.Span::highlight).findFirst().orElseThrow().to().x();
        assertEquals(.2,next-first,1e-9);
    }
    @Test void movementEnvelopePinsEndpointsAndSettlesWithoutPermanentSag(){
        for(double length:new double[]{2,18}){
            var tether=new ElasticBeamTether();var a=Vec3.ZERO;var b=new Vec3(length,0,0);tether.update(a,b,0);
            for(int step=1;step<100;step++){
                var moved=new Vec3(0,Math.sin(step*.2),Math.cos(step*.2));var points=tether.update(a.add(moved),b.add(moved),step*.05);
                assertEquals(a.add(moved),points.getFirst());assertEquals(b.add(moved),points.getLast());
                for(int i=1;i<6;i++)assertTrue(points.get(i).subtract(ElasticBeamTether.linear(points.getFirst(),points.getLast(),i/6.0)).length()<=Math.min(.5,.08*length)*Math.sin(Math.PI*i/6)+1e-9);
            }
            // Stalls and teleports reset instead of building unbounded catch-up work.
            var reset=tether.update(a,b,20);for(int i=0;i<7;i++)assertEquals(ElasticBeamTether.linear(a,b,i/6.0),reset.get(i));
        }
    }
    @Test void degenerateAndInvalidFramesRemainBounded(){
        var points=new ElasticBeamTether().update(Vec3.ZERO,Vec3.ZERO,0);
        assertTrue(HealingTetherGeometry.frame(points,0).isEmpty());
        assertThrows(IllegalArgumentException.class,()->HealingTetherGeometry.frame(points,Double.NaN));
        assertThrows(IllegalArgumentException.class,()->HealingTetherGeometry.frame(points,-1));
    }
    @Test void materialsAreTransparentSeamlessGreenNotRejectedNativeRibbons() throws Exception {
        for(String name:new String[]{"Core","Flow"}){
            var png=ImageIO.read(Path.of("src/main/resources/Common/Trails/RPG_Healing_"+name+".png").toFile());
            assertEquals(128,png.getWidth());assertEquals(16,png.getHeight());assertTrue(png.getColorModel().hasAlpha());
            for(int y=0;y<16;y++)for(int x=1;x<128;x++)assertEquals(png.getRGB(0,y),png.getRGB(x,y));
            assertTrue((png.getRGB(64,0)>>>24)<32);assertTrue((png.getRGB(64,8)>>>24)>150);
            int rgb=png.getRGB(64,8);assertTrue(((rgb>>8)&255)>(rgb&255));
        }
    }
    @Test void productionAndBadgeUseRepairWhileLegacyControlsRemainAvailable() throws Exception {
        assertEquals("R032-AL",HealingTetherPresentation.REVISION);
        var source=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSkillExecutionSystem.java"));
        assertTrue(source.contains("new HealingTetherPresentation()"));assertFalse(source.contains("new HealingParticleVisuals()"));
        var hud=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/ui/hud/RpgHud.java"));
        assertTrue(hud.contains("HealingTetherPresentation.REVISION"));
    }
}
