package com.inigmasgames.persistentnpcs.voice;

import com.inigmasgames.persistentnpcs.voice.VoicePresetRepository.SampleState;

/** Single source of truth for the compact recorder control states. */
public final class VoiceRecorderControlPolicy {
    private VoiceRecorderControlPolicy() { }

    public enum PlayMode { PLAY, STOP }
    public enum DeleteMode { DRAFT, SAVED, NONE }

    public record Controls(boolean recordDisabled, PlayMode playMode,
            boolean playDisabled, DeleteMode deleteMode, boolean deleteDisabled,
            boolean saveDisabled) { }

    public static Controls forSnapshot(NpcVoiceRecordingService.Snapshot snapshot) {
        var state = snapshot.state();
        boolean recording = state == NpcVoiceRecordingService.State.ARMED
                || state == NpcVoiceRecordingService.State.RECORDING;
        boolean playing = state == NpcVoiceRecordingService.State.PLAYING;
        boolean busy = recording || playing
                || state == NpcVoiceRecordingService.State.FINALIZING
                || state == NpcVoiceRecordingService.State.SAVING;
        SampleState saved = snapshot.savedStates().getOrDefault(
                snapshot.selected(), SampleState.MISSING);
        DeleteMode delete = snapshot.draftAvailable() ? DeleteMode.DRAFT
                : saved == SampleState.MISSING ? DeleteMode.NONE : DeleteMode.SAVED;
        boolean playable = snapshot.draftAvailable() || saved == SampleState.FOUND;
        return new Controls(busy, recording || playing ? PlayMode.STOP : PlayMode.PLAY,
                !(recording || playing) && (!playable || busy), delete, delete == DeleteMode.NONE
                        || busy, !snapshot.draftAvailable()
                        || state != NpcVoiceRecordingService.State.READY);
    }
}
