package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.protocol.SoundCategory;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TeleportLandingSoundTest {
    private static final Vec3 ORIGIN = new Vec3(0, 100, 0);
    private static final Vec3 LANDING = new Vec3(6, 101, -4);

    record Played(String eventId, SoundCategory category, Vec3 point) { }

    @Test void confirmedArrivalPlaysNativeSfxOnceAtDestination() {
        var played = new ArrayList<Played>();
        var cue = new TeleportLandingSound();
        TeleportLandingSound.Playback playback=(id,category,point)->played.add(new Played(id,category,point));
        cue.present(NativeTeleportReceipt.State.WAITING, ORIGIN, playback, ignored->fail("unexpected failure"));
        cue.present(NativeTeleportReceipt.State.SUCCEEDED, LANDING, playback, ignored->fail("unexpected failure"));
        cue.present(NativeTeleportReceipt.State.SUCCEEDED, LANDING, playback, ignored->fail("unexpected failure"));
        assertEquals(List.of(new Played("SFX_Portal_Neutral_Teleport_Local",SoundCategory.SFX,LANDING)),played);
    }

    @Test void failedCancelledAndTimedOutReceiptsCannotPresentArrival() {
        var played = new ArrayList<Played>();
        TeleportLandingSound.Playback playback=(id,category,point)->played.add(new Played(id,category,point));
        var nativeFailure=new CompletableFuture<Void>();
        nativeFailure.completeExceptionally(new IllegalStateException("native relocation failed"));
        var cancelled=new CompletableFuture<Void>();
        cancelled.cancel(false);
        for(var completion:List.of(nativeFailure,cancelled)){
            var receipt=new NativeTeleportReceipt(completion,LANDING,0);
            new TeleportLandingSound().present(receipt.inspect(LANDING,true,1),LANDING,playback,
                    ignored->fail("unexpected failure"));
        }
        var timeout=new NativeTeleportReceipt(new CompletableFuture<>(),LANDING,0);
        new TeleportLandingSound().present(timeout.inspect(LANDING,true,5_000_000_100L),LANDING,playback,
                ignored->fail("unexpected failure"));
        assertTrue(played.isEmpty());
    }

    @Test void audioFailureIsReportedOnceAndCannotEscapeCompletion() {
        var errors=new ArrayList<Throwable>();
        var cue=new TeleportLandingSound();
        assertDoesNotThrow(()->cue.present(NativeTeleportReceipt.State.SUCCEEDED,LANDING,
                (id,category,point)->{throw new IllegalStateException("audio unavailable");},errors::add));
        assertEquals(1,errors.size());
        assertDoesNotThrow(()->cue.present(NativeTeleportReceipt.State.SUCCEEDED,LANDING,
                (id,category,point)->fail("duplicate playback"),errors::add));
        assertEquals(1,errors.size());
        assertDoesNotThrow(()->new TeleportLandingSound().present(NativeTeleportReceipt.State.SUCCEEDED,LANDING,
                (id,category,point)->{throw new IllegalStateException("audio unavailable");},
                ignored->{throw new IllegalStateException("diagnostics unavailable");}));
    }
}
