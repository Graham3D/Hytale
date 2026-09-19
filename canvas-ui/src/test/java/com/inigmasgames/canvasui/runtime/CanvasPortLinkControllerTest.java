package com.inigmasgames.canvasui.runtime;

import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.inigmasgames.canvasui.api.Canvas;
import com.inigmasgames.canvasui.api.CanvasDefinition;
import com.inigmasgames.canvasui.api.CanvasPoint;
import com.inigmasgames.canvasui.api.CanvasPort;
import com.inigmasgames.canvasui.api.CanvasRenderBackend;
import com.inigmasgames.canvasui.api.NodeDefinition;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CanvasPortLinkControllerTest {
    @Test void portHasPriorityPreviewSnapsAndValidReleaseCommitsExactlyOnce(){
        Canvas canvas=canvas();ProbeBackend backend=new ProbeBackend();AtomicInteger persists=new AtomicInteger();
        CanvasInputController input=new CanvasInputController(canvas,backend,persists::incrementAndGet,()->true,n->{ });
        input.button(CanvasPoint.of(100,25),MouseButtonType.Left,MouseButtonState.Pressed);
        assertEquals(CanvasInputController.LinkDragState.PORT_ARMED,input.linkState());
        input.motion(CanvasPoint.of(195,25),null,null);
        assertEquals(CanvasInputController.LinkDragState.LINK_DRAGGING,input.linkState());
        assertTrue(backend.valid);assertEquals(CanvasPoint.of(200,25),backend.target);
        input.button(CanvasPoint.of(200,25),MouseButtonType.Left,MouseButtonState.Released);
        assertEquals(CanvasInputController.LinkDragState.IDLE,input.linkState());
        assertEquals(1,canvas.edges().size());assertEquals(1,persists.get());
    }

    @Test void incompatibleOrEmptyReleaseCancelsWithoutMutation(){
        Canvas canvas=canvas();ProbeBackend backend=new ProbeBackend();AtomicInteger persists=new AtomicInteger();
        CanvasInputController input=new CanvasInputController(canvas,backend,persists::incrementAndGet,()->true,n->{ });
        input.button(CanvasPoint.of(100,25),MouseButtonType.Left,MouseButtonState.Pressed);
        input.motion(CanvasPoint.of(150,100),null,null);
        assertFalse(backend.valid);assertEquals(CanvasPoint.of(150,100),backend.target);
        input.button(CanvasPoint.of(150,100),MouseButtonType.Left,MouseButtonState.Released);
        assertTrue(canvas.edges().isEmpty());assertEquals(0,persists.get());
        assertEquals(CanvasInputController.LinkDragState.IDLE,input.linkState());
    }

    private static Canvas canvas(){
        var source=NodeDefinition.builder("passive").size(100,50).port(CanvasPort.output("out","rpg",2,100,25)).build();
        var target=NodeDefinition.builder("skill").size(100,50).port(CanvasPort.input("in","rpg",2,0,25)).build();
        Canvas canvas=new Canvas(CanvasDefinition.builder("tree").registerNodeType(source).registerNodeType(target).build());
        canvas.createNode("passive","passive",CanvasPoint.of(0,0),Map.of());
        canvas.createNode("skill","skill",CanvasPoint.of(200,0),Map.of());return canvas;
    }

    private static final class ProbeBackend implements CanvasRenderBackend{
        CanvasPoint target;boolean valid;
        @Override public String id(){return "test";}@Override public void topologyChanged(){}
        @Override public void updateNodeAndEdges(String nodeId){}@Override public void updateViewport(){}
        @Override public void updatePreview(CanvasPoint source,CanvasPoint target,boolean valid){this.target=target;this.valid=valid;}
    }
}
