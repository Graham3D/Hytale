package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.protocol.SoundCategory;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.function.Consumer;

/** One cosmetic arrival cue per native-confirmed Teleport; playback cannot own cast cleanup. */
final class TeleportLandingSound {
    static final String EVENT_ID = "SFX_Portal_Neutral_Teleport_Local";

    @FunctionalInterface interface Playback {
        void play(String eventId, SoundCategory category, Vec3 origin);
    }

    private boolean attempted;

    void present(NativeTeleportReceipt.State state, Vec3 landing, Playback playback,
                 Consumer<Throwable> reportFailure) {
        if (state != NativeTeleportReceipt.State.SUCCEEDED) return;
        synchronized (this) {
            if (attempted) return;
            attempted = true;
        }
        try {
            playback.play(EVENT_ID, SoundCategory.SFX, landing);
        } catch (RuntimeException | LinkageError failure) {
            try { reportFailure.accept(failure); }
            catch (RuntimeException ignored) { /* Diagnostics cannot retain a completed cast. */ }
        }
    }
}
