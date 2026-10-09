package com.inigmasgames.hytalerpg.ui.hud;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class R208EnemyTargetHudTest {
    private static final Path UI=Path.of("src/main/resources/Common/UI/Custom/RpgEnemyTarget.ui");
    private static final Path ART=Path.of("src/main/resources/Common/UI/Custom/Assets/MonsterHealth");
    @Test void authoredLayersKeepArtAndNoPinnedScreenPanel()throws Exception{
        var ui=Files.readString(UI);
        assertTrue(ui.contains("Anchor: (Horizontal: 0, Vertical: 0, Width: 192, Height: 96)"));
        assertFalse(ui.contains("Top: 128, Width: 720"));
        assertFalse(ui.contains("#RpgEnemyTags"));
        assertFalse(ui.contains("Background: (Color:"));
        int background=ui.indexOf("#RpgEnemyHealthBackground");
        int fill=ui.indexOf("#RpgEnemyHealthFill");
        int frame=ui.indexOf("#RpgEnemyHealthFrame");
        assertTrue(background>0&&background<fill&&fill<frame);
        assertTrue(ui.contains("Left: 4, Top: 0, Width: 120, Height: 24"));
        assertTrue(ui.contains("Left: 0, Top: 0, Width: 128, Height: 24"));
        var backgroundArt=ImageIO.read(ART.resolve("Monster_HB_BG.png").toFile());
        var fillArt=ImageIO.read(ART.resolve("Monster_HB.png").toFile());
        var frameArt=ImageIO.read(ART.resolve("Monster_HB_Frame.png").toFile());
        assertEquals(120,backgroundArt.getWidth());assertEquals(24,backgroundArt.getHeight());
        assertEquals(1,fillArt.getWidth());assertEquals(24,fillArt.getHeight());
        assertEquals(128,frameArt.getWidth());assertEquals(24,frameArt.getHeight());
    }
    @Test void actualHealthFractionRetreatsOnlyAtTheRightEdge(){
        assertEquals(360,RpgHud.enemyHealthFillWidth(100,100));
        assertEquals(270,RpgHud.enemyHealthFillWidth(75,100));
        assertEquals(180,RpgHud.enemyHealthFillWidth(50,100));
        assertEquals(90,RpgHud.enemyHealthFillWidth(25,100));
        assertEquals(0,RpgHud.enemyHealthFillWidth(0,100));
        assertEquals(0,RpgHud.enemyHealthFillWidth(50,0));
        assertEquals(0,RpgHud.enemyHealthFillWidth(Double.NaN,100));
    }
    @Test void projectedBillboardFollowsWorldHeadAndPerspective(){
        var eye=new org.joml.Vector3d(0,1.6,0);
        var forward=new org.joml.Vector3d(0,0,1);
        var right=new org.joml.Vector3d(1,0,0);
        var up=new org.joml.Vector3d(0,1,0);
        var near=EnemyBillboardProjection.project(eye,new org.joml.Vector3d(0,2.5,4),forward,right,up);
        var far=EnemyBillboardProjection.project(eye,new org.joml.Vector3d(0,2.5,12),forward,right,up);
        assertNotNull(near);assertNotNull(far);
        assertTrue(near.frameWidth()>far.frameWidth());
        assertTrue(near.vertical()>far.vertical());
        var moved=EnemyBillboardProjection.project(eye,new org.joml.Vector3d(1,2.5,4),forward,right,up);
        assertTrue(moved.horizontal()>near.horizontal());
        assertNull(EnemyBillboardProjection.project(eye,new org.joml.Vector3d(0,2.5,-4),forward,right,up));
    }
    @Test void nativeLookDirectionProjectsAnActorInFrontOfThePlayer(){
        var rotation=new com.hypixel.hytale.math.vector.Rotation3f();
        var forward=com.hypixel.hytale.math.vector.Transform.getDirection(rotation.pitch(),rotation.yaw());
        assertEquals(-1.0,forward.z(),0.00001);
        var frame=EnemyBillboardProjection.project(new org.joml.Vector3d(0,1.6,0),
                new org.joml.Vector3d(0,2.5,-4),forward,
                rotation.transform(new org.joml.Vector3d(1,0,0)),
                rotation.transform(new org.joml.Vector3d(0,1,0)));
        assertNotNull(frame);
    }
}
