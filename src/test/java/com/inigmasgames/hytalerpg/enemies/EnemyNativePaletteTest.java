package com.inigmasgames.hytalerpg.enemies;

import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.math.shape.Box;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleEnemyPalette;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Installed native constructor/packet proof; no server launch, artistic or connected-client acceptance claim. */
class EnemyNativePaletteTest {
    @Test void appendixOverridesChangeOnlyListedMainAndAttachmentTextures(){
        var first=new com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment("NPC/hat.blockymodel","NPC/hat.png","grad","blue",2);
        var second=new com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment("NPC/belt.blockymodel","NPC/belt.png",null,null,1);
        var original=new Model("fixture",1,Map.of("hat","blue"),new com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment[]{first,second},
                new Box(-.5,0,-.5,.5,2,.5),"NPC/fixture.blockymodel","NPC/original.png",null,null,
                1.8f,-.5f,-.8f,-1.2f,Map.of(),null,null,null,null,null,Map.of(),null,null);
        var overrides=List.of(new EnemyVisualVariants.TextureOverride("fixture","NPC/original.png","NPC/qa.png"),
                new EnemyVisualVariants.TextureOverride("fixture","NPC/hat.png","NPC/hat_qa.png"));
        var palette=HytaleEnemyPalette.texturesOnly(original,overrides);
        assertSame(second,palette.getAttachments()[1]);assertEquals("NPC/hat.png",first.getTexture());
        var expected=new com.hypixel.hytale.protocol.Model(original.toPacket());expected.texture="NPC/qa.png";
        expected.attachments[0].texture="NPC/hat_qa.png";assertEquals(expected,palette.toPacket());
        assertThrows(IllegalStateException.class,()->HytaleEnemyPalette.texturesOnly(original,
                List.of(new EnemyVisualVariants.TextureOverride("fixture","NPC/missing.png","NPC/qa.png"))));
    }
    @Test void nativeTextureSubstitutionLeavesEveryOtherPacketFieldAndGeometryUnchanged(){
        var original=new Model("fixture",1.25f,Map.of(),null,new Box(-.5,0,-.5,.5,2,.5),"NPC/fixture.blockymodel","NPC/original.png",null,null,
                1.8f,-.5f,-.8f,-1.2f,Map.of(),null,null,null,null,null,Map.of(),null,null);
        var palette=HytaleEnemyPalette.textureOnly(original,"NPC/qa.png");
        assertEquals("NPC/original.png",original.getTexture());assertEquals("NPC/qa.png",palette.getTexture());
        assertSame(original.getBoundingBox(),palette.getBoundingBox());
        bounds(original.getCrouchBoundingBox(),palette.getCrouchBoundingBox());
        bounds(original.getSittingBoundingBox(),palette.getSittingBoundingBox());
        bounds(original.getSleepingBoundingBox(),palette.getSleepingBoundingBox());
        assertSame(original.getPhysicsValues(),palette.getPhysicsValues());assertSame(original.getRandomAttachmentIds(),palette.getRandomAttachmentIds());
        var expected=new com.hypixel.hytale.protocol.Model(original.toPacket());expected.texture="NPC/qa.png";
        assertEquals(expected,palette.toPacket());assertEquals("NPC/original.png",original.toPacket().texture);
    }
    @Test void rarityModelScaleUsesNativeBaselineWithoutChangingCombatGeometry(){
        var original=new Model("fixture",0.8f,Map.of(),null,new Box(-.5,0,-.5,.5,2,.5),
                "NPC/fixture.blockymodel","NPC/original.png",null,null,
                1.8f,-.5f,-.8f,-1.2f,Map.of(),null,null,null,null,null,Map.of(),null,null);
        assertEquals(1.0f,HytaleEnemyPalette.rarityScale(EnemyRarity.NORMAL));
        assertEquals(1.15f,HytaleEnemyPalette.rarityScale(EnemyRarity.CHAMPION));
        assertEquals(1.30f,HytaleEnemyPalette.rarityScale(EnemyRarity.UNIQUE));
        assertEquals(1.45f,HytaleEnemyPalette.rarityScale(EnemyRarity.SUPER_UNIQUE));
        var unique=HytaleEnemyPalette.visualScaleOnly(original,original.getScale(),EnemyRarity.UNIQUE);
        assertEquals(0.8f*1.30f,unique.getScale());
        assertSame(original.getBoundingBox(),unique.getBoundingBox());
        assertSame(original.getPhysicsValues(),unique.getPhysicsValues());
        assertSame(original.getAttachments(),unique.getAttachments());
        assertEquals(original.getTexture(),unique.getTexture());
        assertEquals(unique.getScale(),unique.toPacket().scale);
        assertSame(unique,HytaleEnemyPalette.visualScaleOnly(unique,original.getScale(),EnemyRarity.UNIQUE));
        assertEquals(unique.getScale(),HytaleEnemyPalette.visualScaleOnly(unique,original.getScale(),EnemyRarity.UNIQUE).getScale());
    }
    private static void bounds(Box expected,Box actual){assertEquals(expected.min,actual.min);assertEquals(expected.max,actual.max);}
}
