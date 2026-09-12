package com.inigmasgames.hytalerpg;

import com.hypixel.hytale.builtin.beam.AttachedBeam;
import com.hypixel.hytale.builtin.beam.BeamComponent;
import com.inigmasgames.hytalerpg.execution.hytale.ElasticBeamTether;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Installed-API construction check; connected rendering remains a separate evidence gate. */
class Stage13NativeHealingBeamTest {
    @Test void installedNativeBeamAcceptsPersistentPositionAttachmentContract(){
        var target=new Vector3d(4,5,6);
        var attached=AttachedBeam.toPosition(0,.25f,.25f,null,target);
        assertEquals(0,attached.beamIndex());assertEquals(.25f,attached.sourceScale());assertEquals(.25f,attached.targetScale());
        assertNull(attached.targetEntity());assertEquals(target,attached.targetPosition());assertTrue(attached.isValid());
        var component=new BeamComponent(attached);assertEquals(1,component.getBeams().size());assertFalse(component.isEmpty());
        assertEquals(6,ElasticBeamTether.PIECES);assertTrue(BeamComponent.MAX_BEAMS>=ElasticBeamTether.PIECES);
    }
}
