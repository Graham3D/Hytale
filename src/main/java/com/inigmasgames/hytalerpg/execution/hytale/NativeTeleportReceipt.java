package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Waits for Hytale's Teleport system to process the player component before reporting movement. */
final class NativeTeleportReceipt {
    enum State { WAITING, SUCCEEDED, FAILED }

    private static final long TIMEOUT_NANOS = 5_000_000_000L;
    private final CompletableFuture<Void> completion;
    private final Vec3 destination;
    private final long startedNanos;
    private String failure;

    NativeTeleportReceipt(CompletableFuture<Void> completion, Vec3 destination, long startedNanos) {
        this.completion = Objects.requireNonNull(completion);
        this.destination = Objects.requireNonNull(destination);
        this.startedNanos = startedNanos;
    }

    State inspect(Vec3 observed, boolean ownerValid, long nowNanos) {
        if (failure != null) return State.FAILED;
        if (!ownerValid) return fail("TELEPORT_OWNER_INVALID");
        if (completion.isCompletedExceptionally() || completion.isCancelled()) return fail("NATIVE_TELEPORT_FAILED");
        if (completion.isDone())
            // Physics may advance once before this world's next player tick.
            return observed != null && observed.distanceSquared(destination) <= 1.0
                    ? State.SUCCEEDED : fail("TELEPORT_NATIVE_POSITION_MISMATCH");
        return nowNanos - startedNanos >= TIMEOUT_NANOS
                ? fail("NATIVE_TELEPORT_TIMEOUT") : State.WAITING;
    }

    String failure() { return failure; }

    private State fail(String reason) { failure = reason; return State.FAILED; }
}
