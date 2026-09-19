package com.inigmasgames.canvasui.runtime.cursor;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.inigmasgames.canvasui.api.Canvas;
import com.inigmasgames.canvasui.api.CanvasDefinition;
import com.inigmasgames.canvasui.api.CanvasEventType;
import com.inigmasgames.canvasui.api.CanvasPoint;
import com.inigmasgames.canvasui.api.CanvasPort;
import com.inigmasgames.canvasui.api.CanvasRenderBackend;
import com.inigmasgames.canvasui.api.NodeDefinition;
import com.inigmasgames.canvasui.runtime.CanvasInputController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CursorProbePhaseATest {
    @Test void fiveLandmarkTransformIsResolutionAndUiScaleIndependent() {
        verifyTransform(1280, 720, 1.0);
        verifyTransform(1920, 1080, 1.25);
        verifyTransform(2560, 1440, 1.5);
    }

    @Test void closeAndCanvasLocalHitTestingUseTheSameAcceptedTransform() {
        CanvasPointerTransform transform = calibrated(1920, 1080, 1.0);
        CanvasPoint closeRaw = raw(892, 69, 1920, 1080, 1.0);
        assertTrue(transform.closeHit(closeRaw.x(), closeRaw.y()));
        CanvasPoint canvasRaw = raw(185, 256, 1920, 1080, 1.0);
        CanvasPointerTransform.Coordinates converted = transform.convert(canvasRaw.x(), canvasRaw.y(),
                com.inigmasgames.canvasui.api.CanvasViewport.ORIGIN);
        assertEquals(125.0, converted.local().x(), 0.001);
        assertEquals(140.0, converted.local().y(), 0.001);
        assertEquals(converted.local(), converted.canvas());
    }

    @Test void gameplayGuardIsSessionScopedAndCoversAuthoredPlayerRoutes() {
        CanvasInputGuard guard = new CanvasInputGuard();
        UUID player = UUID.randomUUID();
        List<CanvasInputGuard.Observation> observed = new ArrayList<>();
        guard.acquire(player, "owner", observed::add);
        for (InteractionType type : List.of(InteractionType.Primary, InteractionType.Secondary,
                InteractionType.Ability1, InteractionType.Ability2, InteractionType.Ability3,
                InteractionType.Ability4, InteractionType.Use, InteractionType.Pick, InteractionType.Dodge)) {
            assertTrue(guard.guardInteraction(player, type, "root"));
        }
        assertFalse(guard.guardInteraction(player, InteractionType.ProjectileHit, "internal"));
        assertEquals(9, observed.size());
        assertTrue(guard.release(player, "owner"));
        assertFalse(guard.active(player));
        assertFalse(guard.guardInteraction(player, InteractionType.Primary, "root"));
        assertEquals(9, observed.size());
    }

    @Test void sharedGraphControllerPersistsExactlyOnceAtDragTerminalEvenWhenReleasedOutsideNode() {
        AtomicInteger begins = new AtomicInteger();
        AtomicInteger ends = new AtomicInteger();
        AtomicInteger persists = new AtomicInteger();
        NodeDefinition node = NodeDefinition.builder("node").size(150, 72)
                .port(CanvasPort.output("out", "proof", 2, 150, 36)).build();
        Canvas canvas = new Canvas(CanvasDefinition.builder("drag-test").pannable(false).zoomable(false)
                .registerNodeType(node).listener(event -> {
                    if (event.type() == CanvasEventType.DRAG_STARTED) begins.incrementAndGet();
                    if (event.type() == CanvasEventType.DRAG_ENDED) ends.incrementAndGet();
                }).build());
        canvas.createNode("proof", "node", CanvasPoint.of(100, 100), java.util.Map.of());
        CanvasInputController input = new CanvasInputController(canvas, new NoopBackend(),
                persists::incrementAndGet, () -> true, ignored -> { });

        input.button(CanvasPoint.of(120, 120), MouseButtonType.Left, MouseButtonState.Pressed);
        input.motion(CanvasPoint.of(300, 260), null, null);
        input.button(CanvasPoint.of(810, 460), MouseButtonType.Left, MouseButtonState.Released);
        input.button(CanvasPoint.of(810, 460), MouseButtonType.Left, MouseButtonState.Released);

        assertEquals(1, begins.get());
        assertEquals(1, ends.get());
        assertEquals(1, persists.get());
        assertEquals(CanvasPoint.of(280, 240), canvas.node("proof").position());
    }

    @Test void boundedQueueDropsMotionBeforeItLosesAButtonTransition() {
        CursorProbeInputBuffer buffer = new CursorProbeInputBuffer(3);
        assertEquals(CursorProbeInputBuffer.OfferResult.ACCEPTED, buffer.offer(motion(1)));
        assertEquals(CursorProbeInputBuffer.OfferResult.COALESCED_MOTION, buffer.offer(motion(2)));
        assertEquals(CursorProbeInputBuffer.OfferResult.ACCEPTED, buffer.offer(button(3, "Pressed")));
        assertEquals(CursorProbeInputBuffer.OfferResult.ACCEPTED, buffer.offer(button(4, "Released")));
        List<CursorProbeSample> drained = buffer.drain(10);
        assertEquals(List.of(2L, 3L, 4L), drained.stream().map(CursorProbeSample::sequence).toList());
        assertEquals(0, buffer.droppedMotion());
        assertEquals(1, buffer.coalescedMotion());
    }

    @Test void fullTransitionQueueFailsClosedInsteadOfGuessingGestureOrder() {
        CursorProbeInputBuffer buffer = new CursorProbeInputBuffer(2);
        buffer.offer(button(1, "Pressed"));
        buffer.offer(button(2, "Released"));
        assertEquals(CursorProbeInputBuffer.OfferResult.REJECTED_TRANSITION,
                buffer.offer(button(3, "Pressed")));
        assertEquals(List.of(1L, 2L), buffer.drain(10).stream().map(CursorProbeSample::sequence).toList());
    }

    @Test void redundantMotionOverflowIsCountedAndFinalTransitionsRemainOrdered() {
        CursorProbeInputBuffer buffer = new CursorProbeInputBuffer(2);
        buffer.offer(motion(1));
        buffer.offer(motion(2));
        assertEquals(CursorProbeInputBuffer.OfferResult.COALESCED_MOTION, buffer.offer(motion(3)));
        assertEquals(CursorProbeInputBuffer.OfferResult.ACCEPTED, buffer.offer(button(4, "Released")));
        assertEquals(List.of(3L, 4L), buffer.drain(10).stream().map(CursorProbeSample::sequence).toList());
        assertEquals(0, buffer.droppedMotion());
        assertEquals(2, buffer.coalescedMotion());
    }

    private static CursorProbeSample motion(long sequence) {
        return new CursorProbeSample(sequence, System.nanoTime(), CursorProbeSample.Source.PACKET, CursorProbeSample.Kind.MOTION,
                true, sequence, sequence, 1, 1, "UNKNOWN", "UNKNOWN", 0, "NONE",
                false, false, false);
    }

    private static CursorProbeSample button(long sequence, String state) {
        return new CursorProbeSample(sequence, System.nanoTime(), CursorProbeSample.Source.PACKET, CursorProbeSample.Kind.BUTTON,
                true, sequence, sequence, null, null, "Left", state, 1, "UNKNOWN",
                false, false, false);
    }

    private static void verifyTransform(int physicalWidth, int physicalHeight, double uiScale) {
        CanvasPointerTransform transform = calibrated(physicalWidth, physicalHeight, uiScale);
        assertTrue(transform.ready());
        assertEquals(CanvasPointerTransform.State.READY, transform.state());
        assertEquals(0.0, transform.calibration().orElseThrow().centerError(), 0.001);
        for (CanvasPointerTransform.Landmark landmark : CanvasPointerTransform.Landmark.values()) {
            CanvasPoint raw = raw(landmark.expected().x(), landmark.expected().y(),
                    physicalWidth, physicalHeight, uiScale);
            CanvasPoint converted = transform.toViewport(raw.x(), raw.y());
            assertEquals(landmark.expected().x(), converted.x(), 0.001);
            assertEquals(landmark.expected().y(), converted.y(), 0.001);
        }
    }

    private static CanvasPointerTransform calibrated(int physicalWidth, int physicalHeight, double uiScale) {
        CanvasPointerTransform transform = new CanvasPointerTransform();
        for (CanvasPointerTransform.Landmark landmark : CanvasPointerTransform.Landmark.values()) {
            CanvasPoint sample = raw(landmark.expected().x(), landmark.expected().y(),
                    physicalWidth, physicalHeight, uiScale);
            transform.captureNext(sample.x(), sample.y());
        }
        return transform;
    }

    private static CanvasPoint raw(double logicalX, double logicalY,
                                   int physicalWidth, int physicalHeight, double uiScale) {
        double logicalWidth = physicalWidth / uiScale;
        double logicalHeight = physicalHeight / uiScale;
        return CanvasPoint.of(logicalX / logicalWidth * 2.0 - 1.0,
                1.0 - logicalY / logicalHeight * 2.0);
    }

    private static final class NoopBackend implements CanvasRenderBackend {
        @Override public String id() { return "test"; }
        @Override public void topologyChanged() { }
        @Override public void updateNodeAndEdges(String nodeId) { }
        @Override public void updateViewport() { }
    }
}
