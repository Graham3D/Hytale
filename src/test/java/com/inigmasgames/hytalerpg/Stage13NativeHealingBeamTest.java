package com.inigmasgames.hytalerpg;

import com.hypixel.hytale.builtin.beam.AttachedBeam;
import com.hypixel.hytale.builtin.beam.BeamComponent;
import com.inigmasgames.hytalerpg.execution.hytale.ElasticBeamTether;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Installed-API construction check; connected rendering remains a separate evidence gate. */
class Stage13NativeHealingBeamTest {
    @Test void productionAppearanceUsesNarrowUniformNativeEndpointsAndDedicatedAsset(){
        var target=new com.inigmasgames.hytalerpg.execution.math.Vec3(12,83,-54);
        var beam=com.inigmasgames.hytalerpg.execution.hytale.NativeHealingBeamVisuals.attachment(7,target);
        assertEquals("RPG_Healing",com.inigmasgames.hytalerpg.execution.hytale.NativeHealingBeamVisuals.ASSET_ID);
        assertEquals(.025f,beam.sourceScale());assertEquals(beam.sourceScale(),beam.targetScale());
        assertEquals(7,beam.beamIndex());assertNull(beam.targetEntity());assertNull(beam.sourceNode());
        assertEquals(new Vector3d(12,83,-54),beam.targetPosition());
        assertEquals(0,beam.sourceOffset().lengthSquared());assertEquals(0,beam.targetOffset().lengthSquared());
    }
    @Test void installedNativeBeamAcceptsPersistentPositionAttachmentContract(){
        var target=new Vector3d(4,5,6);
        var attached=AttachedBeam.toPosition(0,.25f,.25f,null,target);
        assertEquals(0,attached.beamIndex());assertEquals(.25f,attached.sourceScale());assertEquals(.25f,attached.targetScale());
        assertNull(attached.targetEntity());assertEquals(target,attached.targetPosition());assertTrue(attached.isValid());
        var component=new BeamComponent(attached);assertEquals(1,component.getBeams().size());assertFalse(component.isEmpty());
        assertEquals(6,ElasticBeamTether.PIECES);assertTrue(BeamComponent.MAX_BEAMS>=ElasticBeamTether.PIECES);
    }
}
