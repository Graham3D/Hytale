package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NativeTeleportReceiptTest {
    private static final Vec3 LANDING = new Vec3(5, 120, -3);

    @Test void nativeCompletionAndObservedLandingAreBothRequired() {
        var nativeCall = new CompletableFuture<Void>();
        var receipt = new NativeTeleportReceipt(nativeCall, LANDING, 100);
        assertEquals(NativeTeleportReceipt.State.WAITING, receipt.inspect(LANDING, true, 101));
        nativeCall.complete(null);
        assertEquals(NativeTeleportReceipt.State.SUCCEEDED,
                receipt.inspect(LANDING.add(new Vec3(0, -.2, 0)), true, 102));
    }

    @Test void serverSideMovementAloneCannotReportTeleportSuccess() {
        var receipt = new NativeTeleportReceipt(new CompletableFuture<>(), LANDING, 100);
        assertEquals(NativeTeleportReceipt.State.WAITING, receipt.inspect(LANDING, true, 101));
        assertEquals(NativeTeleportReceipt.State.FAILED, receipt.inspect(LANDING, true, 5_000_000_100L));
        assertEquals("NATIVE_TELEPORT_TIMEOUT", receipt.failure());
    }

    @Test void exceptionalNativeCompletionAndPositionMismatchFail() {
        var failed = new CompletableFuture<Void>();
        failed.completeExceptionally(new IllegalStateException("native failure"));
        var receipt = new NativeTeleportReceipt(failed, LANDING, 0);
        assertEquals(NativeTeleportReceipt.State.FAILED, receipt.inspect(LANDING, true, 1));
        assertEquals("NATIVE_TELEPORT_FAILED", receipt.failure());

        var misplaced = new NativeTeleportReceipt(CompletableFuture.completedFuture(null), LANDING, 0);
        assertEquals(NativeTeleportReceipt.State.FAILED, misplaced.inspect(Vec3.ZERO, true, 1));
        assertEquals("TELEPORT_NATIVE_POSITION_MISMATCH", misplaced.failure());
    }

    @Test void invalidOwnerCannotCompleteOrPresentArrival() {
        var receipt = new NativeTeleportReceipt(CompletableFuture.completedFuture(null), LANDING, 0);
        assertEquals(NativeTeleportReceipt.State.FAILED, receipt.inspect(LANDING, false, 1));
        assertEquals("TELEPORT_OWNER_INVALID", receipt.failure());
    }
}
