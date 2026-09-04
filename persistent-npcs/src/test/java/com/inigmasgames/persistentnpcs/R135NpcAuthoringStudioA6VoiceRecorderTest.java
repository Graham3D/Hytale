package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.voice.NpcVoiceSamplePersistenceService;
import com.inigmasgames.persistentnpcs.voice.NpcVoiceRecordingService;
import com.inigmasgames.persistentnpcs.voice.VoiceCaptureLeaseManager;
import com.inigmasgames.persistentnpcs.voice.VoiceCaptureLeaseManager.VoiceCaptureMode;
import com.inigmasgames.persistentnpcs.voice.VoiceDraftAudio;
import com.inigmasgames.persistentnpcs.voice.VoicePresetRepository;
import com.inigmasgames.persistentnpcs.voice.VoiceRecordingPolicy;
import com.inigmasgames.persistentnpcs.voice.VoiceSampleType;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Deterministic A6 gate: exclusive capture, durable samples, privacy, and UI wiring. */
public final class R135NpcAuthoringStudioA6VoiceRecorderTest {
    private R135NpcAuthoringStudioA6VoiceRecorderTest() { }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("r135-voice-recorder-");
        try {
            System.out.println("R135 stage=exclusive-capture-lease");
            List<String> diagnostics = new ArrayList<>();
            VoiceCaptureLeaseManager leases = new VoiceCaptureLeaseManager(diagnostics::add);
            UUID player = UUID.randomUUID();
            UUID owner = UUID.randomUUID();
            assert leases.mode(player) == VoiceCaptureMode.NONE;
            try (var recording = leases.acquireRecording(player, owner)) {
                assert recording.valid();
                assert leases.mode(player) == VoiceCaptureMode.VOICE_SAMPLE_RECORDING;
                assert !leases.admitConversationFrame(player)
                        : "Recorder-owned frames must never enter Orbis conversation capture";
                assert leases.dualAdmissionRejections() == 1;
                boolean secondRejected = false;
                try { leases.acquireRecording(player, UUID.randomUUID()); }
                catch (IllegalStateException expected) { secondRejected = true; }
                assert secondRejected : "Only one capture owner is allowed per player";
            }
            assert leases.mode(player) == VoiceCaptureMode.NONE;
            assert diagnostics.stream().anyMatch(line -> line.contains("LEASE_ACQUIRED"));
            assert diagnostics.stream().anyMatch(line -> line.contains("LEASE_RELEASED"));

            System.out.println("R135 stage=input-disabled-and-idempotent-shutdown");
            VoicePresetRepository unavailableRepository = new VoicePresetRepository(
                    root.resolve("unavailable").resolve("ImmersiveNPCs"));
            NpcVoiceRecordingService unavailable = new NpcVoiceRecordingService(null,
                    leases, unavailableRepository, null, diagnostics::add);
            boolean inputDisabled = false;
            try {
                unavailable.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        "NoInputNpc", 1, 1);
            } catch (IllegalStateException expected) {
                inputDisabled = expected.getMessage().contains("voice input is unavailable");
            }
            assert inputDisabled;
            unavailable.close();
            unavailable.close();
            assert leases.activeRecordingLeases() == 0;

            System.out.println("R135 stage=frame-sequence-quality-policy");
            assert !VoiceRecordingPolicy.validOpusFrame(null)
                    && !VoiceRecordingPolicy.validOpusFrame(new byte[0])
                    && VoiceRecordingPolicy.validOpusFrame(new byte[512])
                    && !VoiceRecordingPolicy.validOpusFrame(new byte[513]);
            var cleanSequence = VoiceRecordingPolicy.sequences(List.of(65_534, 65_535, 0, 1));
            assert cleanSequence.gaps() == 0 && cleanSequence.duplicates() == 0
                    && cleanSequence.outOfOrder() == 0 : "Sequence wrap must remain ordered";
            var damagedSequence = VoiceRecordingPolicy.sequences(List.of(10, 10, 13, 12));
            assert damagedSequence.duplicates() == 1 && damagedSequence.gaps() == 2
                    && damagedSequence.outOfOrder() == 1;
            assert VoiceRecordingPolicy.qualityIssue(
                    new VoiceDraftAudio(new byte[0], 4_999, -10, -20, 0, 0,
                            List.of(), 1), cleanSequence, 12)
                    .contains("longer");
            assert VoiceRecordingPolicy.qualityIssue(
                    new VoiceDraftAudio(new byte[0], 6_000, -10, -20, 0, 0.86,
                            List.of(), 1), cleanSequence, 12)
                    .contains("silent");
            assert VoiceRecordingPolicy.qualityIssue(
                    new VoiceDraftAudio(new byte[0], 6_000, -1, -10, 0.021, 0,
                            List.of(), 1), cleanSequence, 12)
                    .contains("too loud");
            assert VoiceRecordingPolicy.qualityIssue(audio(wave(0.12)),
                    cleanSequence, 12) == null;
            assert VoiceRecordingPolicy.qualityIssue(audio(wave(0.12)),
                    damagedSequence, 12).contains("dropped");
            assert Arrays.equals(Arrays.stream(NpcVoiceRecordingService.State.values())
                    .map(Enum::name).toArray(String[]::new), new String[] {
                            "IDLE", "ARMED", "RECORDING", "FINALIZING", "READY",
                            "PLAYING", "SAVING", "SAVED", "FAILED", "CANCELLED"
                    });

            System.out.println("R135 stage=atomic-save-conflict-delete");
            Path data = root.resolve("mods").resolve("ImmersiveNPCs");
            Files.createDirectories(data);
            VoicePresetRepository repository = new VoicePresetRepository(data);
            List<Path> invalidations = new ArrayList<>();
            NpcVoiceSamplePersistenceService persistence =
                    new NpcVoiceSamplePersistenceService(repository,
                            invalidations::add, diagnostics::add);
            Path staleDirectory = repository.profileVoiceDirectory("A6TestNpc")
                    .resolve(".voice-drafts");
            Files.createDirectories(staleDirectory);
            Path expiredDraft = staleDirectory.resolve("expired-reference.wav");
            Files.write(expiredDraft, wave(0.1));
            Files.setLastModifiedTime(expiredDraft, FileTime.fromMillis(
                    System.currentTimeMillis() - 7_200_000));
            assert persistence.cleanupStaleDrafts("A6TestNpc", 3_600_000) == 1
                    && !Files.exists(expiredDraft);
            UUID stableNpcId = UUID.randomUUID();
            byte[] firstWave = wave(0.16);
            byte[] secondWave = wave(0.27);
            VoiceDraftAudio protectedAudio = audio(firstWave);
            byte original = protectedAudio.wav()[44];
            byte[] returned = protectedAudio.wav();
            returned[44] ^= 0x7f;
            assert protectedAudio.wav()[44] == original
                    : "Draft audio must not expose mutable authoritative bytes";

            var firstDraft = persistence.writeDraft("A6TestNpc", UUID.randomUUID(),
                    VoiceSampleType.REFERENCE, protectedAudio);
            assert Files.isRegularFile(firstDraft.path());
            var firstSave = persistence.save("A6TestNpc", stableNpcId, firstDraft, "MISSING");
            assert Files.isRegularFile(firstSave.path());
            assert repository.scan("A6TestNpc").ready();
            assert firstSave.revision().equals(repository.sampleRevision(
                    "A6TestNpc", VoiceSampleType.REFERENCE));

            for (VoiceSampleType type : VoiceSampleType.values()) {
                if (type == VoiceSampleType.REFERENCE) continue;
                var emotionDraft = persistence.writeDraft("A6TestNpc", UUID.randomUUID(),
                        type, audio(firstWave));
                persistence.save("A6TestNpc", stableNpcId, emotionDraft, "MISSING");
            }
            for (VoiceSampleType type : VoiceSampleType.values()) {
                assert repository.scan("A6TestNpc").samples().get(type).present()
                        : "Canonical emotion did not persist: " + type;
            }

            var replacementDraft = persistence.writeDraft("A6TestNpc", UUID.randomUUID(),
                    VoiceSampleType.REFERENCE, audio(secondWave));
            var replacement = persistence.save("A6TestNpc", stableNpcId,
                    replacementDraft, firstSave.revision());
            assert Files.isRegularFile(replacement.rollback())
                    : "Replacing a canonical sample must preserve rollback evidence";
            assert !replacement.revision().equals(firstSave.revision());

            String openedRevision = replacement.revision();
            var staleDraft = persistence.writeDraft("A6TestNpc", UUID.randomUUID(),
                    VoiceSampleType.REFERENCE, audio(firstWave));
            Files.write(replacement.path(), wave(0.38));
            byte[] externalWriter = Files.readAllBytes(replacement.path());
            boolean conflict = false;
            try {
                persistence.save("A6TestNpc", stableNpcId, staleDraft, openedRevision);
            } catch (IllegalStateException expected) {
                conflict = true;
            }
            assert conflict : "Stale recorder drafts must not overwrite another writer";
            assert Arrays.equals(externalWriter, Files.readAllBytes(replacement.path()));
            persistence.discard(staleDraft);

            Path amused = repository.canonicalSamplePath("A6TestNpc", VoiceSampleType.AMUSED);
            String amusedRevision = repository.sampleRevision("A6TestNpc", VoiceSampleType.AMUSED);
            byte[] amusedBeforeFailure = Files.readAllBytes(amused);
            var missingSourceDraft = persistence.writeDraft("A6TestNpc", UUID.randomUUID(),
                    VoiceSampleType.AMUSED, audio(secondWave));
            Files.delete(missingSourceDraft.path());
            boolean injectedWriteFailure = false;
            try {
                persistence.save("A6TestNpc", stableNpcId, missingSourceDraft, amusedRevision);
            } catch (IllegalStateException expected) {
                injectedWriteFailure = true;
            }
            assert injectedWriteFailure && Arrays.equals(amusedBeforeFailure,
                    Files.readAllBytes(amused))
                    : "Failed saves must retain the prior authoritative sample";

            var optionalDelete = persistence.deleteSaved("A6TestNpc", stableNpcId,
                    VoiceSampleType.AMUSED, amusedRevision);
            assert optionalDelete.deleted() && Files.isRegularFile(
                    optionalDelete.recoverableTrash());
            assert repository.scan("A6TestNpc").ready()
                    : "Deleting an optional emotion must retain Reference readiness";

            String currentRevision = repository.sampleRevision(
                    "A6TestNpc", VoiceSampleType.REFERENCE);
            var deleted = persistence.deleteSaved("A6TestNpc", stableNpcId,
                    VoiceSampleType.REFERENCE, currentRevision);
            assert deleted.deleted() && Files.isRegularFile(deleted.recoverableTrash());
            assert !Files.exists(replacement.path());
            assert !repository.scan("A6TestNpc").ready();
            assert invalidations.size() == 10
                    : "Every canonical save/replacement/delete must invalidate conditioning";
            Path audit = repository.profileVoiceDirectory("A6TestNpc")
                    .resolve("voice-authoring-audit.jsonl");
            String auditText = Files.readString(audit);
            assert auditText.contains("\"action\":\"SAVE\"")
                    && auditText.contains("\"action\":\"DELETE\"");
            boolean escapedPathRejected = false;
            try { repository.profileVoiceDirectory("../Escape"); }
            catch (IllegalArgumentException expected) { escapedPathRejected = true; }
            assert escapedPathRejected : "Untrusted NPC names must not escape profile storage";
            try (var paths = Files.walk(repository.profileVoiceDirectory("A6TestNpc"))) {
                assert paths.noneMatch(path -> path.getFileName().toString().endsWith(".tmp"))
                        : "Failed or successful commits must not leak sibling temp files";
            }

            System.out.println("R135 stage=privacy-worker-ui-contract");
            String recorder = source("src/main/java/com/inigmasgames/persistentnpcs/voice/"
                    + "NpcVoiceRecordingService.java");
            String orbis = source("src/main/java/com/inigmasgames/persistentnpcs/orbis/"
                    + "OrbisRuntime.java");
            assert recorder.contains("EventPriority.LAST")
                    && recorder.contains("requireFinalRoutingOwnership")
                    && recorder.contains("playerVoiceInterceptors")
                    && recorder.contains("PRIVACY_REJECTED")
                    && recorder.contains("frame.drop()")
                    && recorder.contains("ArrayBlockingQueue")
                    && recorder.contains("MAX_QUEUED_BYTES")
                    && recorder.contains("ARMED_TIMEOUT_MILLIS")
                    && recorder.contains("MAX_DURATION_MILLIS")
                    && recorder.contains("MAX_DRAFT_AGE_MILLIS")
                    && recorder.contains("QUEUE_OVERFLOW")
                    && recorder.contains("PLUGIN_SHUTDOWN")
                    && recorder.contains("PLAYER_DISCONNECTED")
                    && recorder.contains("EDITOR_CLOSED")
                    && recorder.contains("NPC_AUTHORING_VOICE_QUALITY")
                    && recorder.contains("NPC_AUTHORING_VOICE_RESCAN")
                    && recorder.contains("openDirectVoice(Set.of(session.playerId))")
                    && recorder.contains("decodeVoiceDraft")
                    && !recorder.contains(".transcribe(")
                    : "Recorder ingress must remain bounded, private, and model-free";
            assert orbis.indexOf("admitConversationFrame(playerId)")
                    < orbis.indexOf("frame.opus()")
                    : "Capture ownership must be resolved before Orbis copies audio";
            String worker = source("src/main/resources/tools/immersive_voice_worker.py");
            assert worker.contains("def decode_recording")
                    && worker.contains("def encode_saved_wav")
                    && worker.contains("def invalidate_conditioning")
                    && worker.substring(worker.indexOf("def decode_recording"),
                            worker.indexOf("def encode_saved_wav"))
                            .contains("decode_opus")
                    && !worker.substring(worker.indexOf("def decode_recording"),
                            worker.indexOf("def encode_saved_wav"))
                            .contains("transcribe(");
            String ui = source("src/main/resources/Common/UI/Custom/Pages/"
                    + "ImmersiveNpcProfile.ui");
            String page = source("src/main/java/com/inigmasgames/persistentnpcs/ui/"
                    + "NpcProfilePage.java");
            for (String selector : List.of("#VoiceRecorderPage", "#VoiceRecordButton",
                    "#VoiceStopButton", "#VoicePlayDraftButton", "#VoiceSaveButton",
                    "#VoiceDeleteSavedButton", "#VoiceDeleteConfirmPage",
                    "#VoiceRecordingIndicator", "#VoiceQualityMetrics",
                    "#VoiceWaveformText")) {
                assert ui.contains(selector) : "Missing A6 UI selector " + selector;
            }
            assert page.contains("#VoiceDeleteConfirmPage.Visible\", false")
                    : "Saved-sample confirmation must dismiss on confirm and cancel";
            for (VoiceSampleType type : VoiceSampleType.values()) {
                assert ui.contains("#VoiceEmotion" + type.name())
                        && ui.contains("#VoiceSaved" + type.name());
            }
            String manifest = source("src/main/resources/manifest.json");
            String installer = source("install.ps1");
            assert manifest.contains("R135-npc-authoring-studio-a6-voice-recorder");
            assert installer.contains("R135-NPC-AUTHORING-STUDIO-A6-VOICE-RECORDER.jar");
            System.out.println("R135 NPC Authoring Studio A6 Voice Recorder gate passed.");
        } finally {
            deleteTree(root);
        }
    }

    private static VoiceDraftAudio audio(byte[] wav) {
        return new VoiceDraftAudio(wav, 6_000, -11.0, -19.0, 0.0, 0.05,
                List.of(0.05, 0.2, 0.4, 0.2, 0.05), 8);
    }

    private static byte[] wave(double amplitude) {
        int sampleRate = 48_000;
        int samples = sampleRate * 6;
        int dataBytes = samples * 2;
        ByteBuffer out = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        out.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.putInt(36 + dataBytes);
        out.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        out.putInt(16).putShort((short) 1).putShort((short) 1);
        out.putInt(sampleRate).putInt(sampleRate * 2).putShort((short) 2).putShort((short) 16);
        out.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataBytes);
        for (int index = 0; index < samples; index++) {
            double value = Math.sin(2.0 * Math.PI * 220.0 * index / sampleRate);
            out.putShort((short) Math.round(value * amplitude * Short.MAX_VALUE));
        }
        return out.array();
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
