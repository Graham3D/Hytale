package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.math.shape.Box;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class MonsterPresentationLayoutTest {
    @Test void rowsShareHitboxCenterAndRemainOrderedAcrossActorSizes() {
        for(double height:new double[]{0.8,2.1,4.0}) {
            var rows=MonsterPresentationLayout.resolve(new Vector3d(4,12,8),
                    new Box(-0.6,0,-0.6,0.6,height,0.6));
            assertEquals(4,rows.centerX());
            assertEquals(8,rows.centerZ());
            assertEquals(12+height,rows.visualTopY());
            assertTrue(rows.healthbarY()<rows.affixY());
            assertTrue(rows.affixY()<rows.nameY());
            // The carrier compensates for native Nameplate lift; visible rows stay close.
            assertEquals(rows.affixY()-MonsterPresentationLayout.nativeNameplateLift,
                    rows.affixAnchorPosition().y,1e-9);
            assertEquals(MonsterPresentationLayout.monsterPresentationAffixGap,
                    rows.affixY()-rows.healthbarY(),1e-9);
            assertEquals(MonsterPresentationLayout.monsterPresentationNameGap,
                    rows.nameY()-rows.affixY(),1e-9);
            assertEquals(rows.centerX(),rows.affixAnchorPosition().x);
            assertEquals(rows.centerZ(),rows.affixAnchorPosition().z);
            assertTrue(rows.nameY()>rows.affixY());
            assertEquals(rows.centerX(),rows.nameAnchorPosition().x);
            assertEquals(rows.centerZ(),rows.nameAnchorPosition().z);
            assertEquals(rows.nameY(),rows.nameAnchorPosition().y);
            assertEquals(height/2+MonsterPresentationLayout.monsterPresentationBaseMargin,
                    MonsterPresentationLayout.desiredHealthbarOffsetFromHitboxCenter(
                            new Vector3d(4,12,8),new Box(-0.6,0,-0.6,0.6,height,0.6)),1e-9);
        }
    }

    @Test void packagedNativeBarUsesTheSingleAuthoredHitboxOffset() throws Exception {
        try(var stream=getClass().getClassLoader().getResourceAsStream("Server/Entity/UI/Healthbar.json")) {
            assertNotNull(stream);
            String asset=new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(asset.contains("\"EntityStat\": \"Health\""));
            assertTrue(asset.contains("\"Y\": " + (int)MonsterPresentationLayout.NATIVE_HEALTHBAR_HITBOX_OFFSET_Y));
        }
    }
    @Test void enlargedVisualTopLiftsSharedNameAndAffixRowsWithoutChangingNativeBox(){
        var box=new Box(-.5,0,-.5,.5,2,.5);
        var normal=MonsterPresentationLayout.resolve(new Vector3d(4,12,8),box);
        var unique=MonsterPresentationLayout.resolve(new Vector3d(4,12,8),box,1.30);
        assertEquals(14.6,unique.visualTopY(),1e-9);
        assertEquals(0.6,unique.nameY()-normal.nameY(),1e-9);
        assertEquals(0.6,unique.affixY()-normal.affixY(),1e-9);
        assertEquals(normal.centerX(),unique.centerX());
        assertEquals(normal.centerZ(),unique.centerZ());
        assertEquals(2,box.max.y);
    }
}
