"""Persistent local Turbo/Whisper/Opus worker for Immersive AI.

Protocol: one compact JSON request/response per line on stdio. Model/library output is
redirected to stderr so stdout remains machine-readable. Normal synthesis stays in memory.
"""

import argparse
import base64
import contextlib
import io
import json
import os
import sys
import time
import traceback
import hashlib
import tempfile
import threading
import importlib.metadata
from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PROTOCOL_OUT = sys.stdout


def emit(value):
    PROTOCOL_OUT.write(json.dumps(value, separators=(",", ":")) + "\n")
    PROTOCOL_OUT.flush()


def log(message):
    print(message, file=sys.stderr, flush=True)


CONDITIONING_CACHE_SCHEMA = "chatterbox-conditioning-v1"


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def profile_revision_hash(reference):
    digest = hashlib.sha256()
    for candidate in sorted(reference.parent.glob("*.json")):
        if candidate.name.lower() in ("ss_skin_character.json", "npc-inventory.json"):
            continue
        digest.update(candidate.name.encode("utf-8"))
        digest.update(sha256_file(candidate).encode("ascii"))
    return digest.hexdigest()


def profile_stable_identity(reference):
    """Read only authored identity metadata; never infer identity from another WAV."""
    for candidate in sorted(reference.parent.glob("*.json")):
        if candidate.name.lower() in ("ss_skin_character.json", "npc-inventory.json"):
            continue
        try:
            value = json.loads(candidate.read_text(encoding="utf-8"))
            identity = value.get("stableId") or value.get("id")
            if identity:
                return str(identity)
        except (OSError, ValueError, TypeError):
            continue
    return "legacy-preset:" + reference.parent.name.lower()


def conditioning_cache_key(reference):
    resolved = reference.resolve()
    try:
        chatterbox_version = importlib.metadata.version("chatterbox-tts")
    except importlib.metadata.PackageNotFoundError:
        chatterbox_version = "unknown"
    # The resolved path/sample filename keeps emotional conditionings distinct even
    # when recordings happen to contain identical bytes. The authored stable identity
    # and profile hash prevent cross-NPC reuse and invalidate profile/rename revisions.
    identity = "|".join((CONDITIONING_CACHE_SCHEMA,
                         hashlib.sha256(chatterbox_version.encode("utf-8")).hexdigest(),
                         profile_stable_identity(resolved),
                         str(resolved).lower(), resolved.name.lower(),
                         sha256_file(resolved), profile_revision_hash(resolved)))
    return hashlib.sha256(identity.encode("utf-8")).hexdigest()


def selected_device(requested, torch):
    if requested.lower() == "auto":
        return "cuda" if torch.cuda.is_available() else "cpu"
    return requested.lower()


def dbfs(amplitude):
    return -120.0 if amplitude <= 1.0e-9 else 20.0 * __import__("math").log10(amplitude)


def apply_output_gain(samples, gain_db, np):
    """Apply bounded gain and a transparent whole-clip peak limiter at -0.5 dBFS."""
    audio = np.asarray(samples, dtype=np.float32)
    gain_db = max(-24.0, min(12.0, float(gain_db)))
    source_peak = float(np.max(np.abs(audio))) if audio.size else 0.0
    source_rms = float(np.sqrt(np.mean(np.square(audio, dtype=np.float64)))) \
        if audio.size else 0.0
    scaled = audio * (10.0 ** (gain_db / 20.0))
    requested_peak = float(np.max(np.abs(scaled))) if scaled.size else 0.0
    limit_peak = 10.0 ** (-0.5 / 20.0)
    limiter_scale = min(1.0, limit_peak / requested_peak) if requested_peak > 0.0 else 1.0
    processed = np.clip(scaled * limiter_scale, -limit_peak, limit_peak).astype(np.float32)
    output_peak = float(np.max(np.abs(processed))) if processed.size else 0.0
    output_rms = float(np.sqrt(np.mean(np.square(processed, dtype=np.float64)))) \
        if processed.size else 0.0
    return processed, {
        "sourcePeakDbfs": round(dbfs(source_peak), 2),
        "sourceRmsDbfs": round(dbfs(source_rms), 2),
        "gainDb": round(gain_db, 2),
        "limiterReductionDb": round(dbfs(limiter_scale), 2),
        "preOpusPeakDbfs": round(dbfs(output_peak), 2),
        "preOpusRmsDbfs": round(dbfs(output_rms), 2),
    }


class VoiceWorker:
    def __init__(self, args):
        self.worker_role = args.worker_role.lower()
        self.tts_enabled = self.worker_role in ("combined", "tts")
        self.stt_enabled = self.worker_role in ("combined", "stt")
        with contextlib.redirect_stdout(sys.stderr):
            import av
            import numpy as np
            if self.tts_enabled:
                import torch
                import torchaudio
            else:
                torch = None
                torchaudio = None

        self.av = av
        self.np = np
        self.torch = torch
        self.torchaudio = torchaudio
        self.tts_device = selected_device(args.tts_device, torch) \
            if self.tts_enabled else "cpu"
        self.whisper_device = args.whisper_device
        self.whisper_compute_type = args.whisper_compute_type
        self.whisper_model = args.whisper_model
        self.opus_bitrate = args.opus_bitrate
        self.export_wav = args.export_debug_wav
        self.export_dir = Path(args.export_directory) if args.export_directory else None
        self.voice_conditionals = {}
        self.reference_identity_cache = {}
        self.conditioning_cache_directory = Path(args.conditioning_cache_directory).resolve() \
            if args.conditioning_cache_directory else None
        if self.conditioning_cache_directory:
            self.conditioning_cache_directory.mkdir(parents=True, exist_ok=True)
        self.streaming_stt_sessions = {}
        self.tts = None
        self.chatterbox_class = None
        self.whisper = None
        self.tts_load_ms = 0
        self.tts_probe_complete = False
        self.voice_conditioning_ms = 0
        self.whisper_load_ms = 0
        self.streaming_stt_provider = "DISABLED"
        self.requested_stt_provider = args.stt_provider.upper()
        self.stt_fallback = False
        self.stt_fallback_reason = ""
        self.moonshine = None
        self.prewarm_references = []

        if self.tts_enabled:
            with contextlib.redirect_stdout(sys.stderr):
                from chatterbox.tts_turbo import ChatterboxTurboTTS
            self.chatterbox_class = ChatterboxTurboTTS
            if args.prewarm_reference:
                reference = Path(args.prewarm_reference).resolve()
                if reference.is_file():
                    self.prewarm_references.append(reference)

        if self.stt_enabled:
            if args.stt_provider.upper() in ("AUTO", "MOONSHINE"):
                try:
                    import moonshine_voice as moonshine
                    model_arch = getattr(moonshine.ModelArch, args.moonshine_model.upper())
                    model_path, resolved_arch = moonshine.get_model_for_language("en", model_arch)
                    self.moonshine = moonshine.Transcriber(
                        model_path, resolved_arch, update_interval=0.25)
                    self.streaming_stt_provider = "MOONSHINE"
                    log(f"Moonshine streaming STT ready model={args.moonshine_model}")
                except Exception as failure:
                    self.stt_fallback = True
                    self.stt_fallback_reason = f"{type(failure).__name__}: {failure}"
                    log(f"Moonshine unavailable; Faster-Whisper fallback remains active: {failure}")
                    if args.stt_provider.upper() == "MOONSHINE":
                        log("Configured MOONSHINE could not start; continuing fail-safe fallback")
            if self.moonshine is None:
                self.ensure_whisper()

    def ensure_whisper(self):
        """Lazy explicit fallback: Moonshine startup never pays Whisper's cold-load cost."""
        if self.whisper is not None:
            return self.whisper
        with contextlib.redirect_stdout(sys.stderr):
            from faster_whisper import WhisperModel
        started = time.perf_counter()
        log(f"Loading Faster-Whisper fallback model={self.whisper_model} "
            f"device={self.whisper_device} compute={self.whisper_compute_type}")
        with contextlib.redirect_stdout(sys.stderr):
            self.whisper = WhisperModel(self.whisper_model, device=self.whisper_device,
                                        compute_type=self.whisper_compute_type)
        self.whisper_load_ms = round((time.perf_counter() - started) * 1000)
        if self.moonshine is None:
            self.streaming_stt_provider = "FASTER_WHISPER"
        return self.whisper

    def load_conditionals(self, reference_key):
        if not self.conditioning_cache_directory:
            return None, "DISABLED"
        target = self.conditioning_cache_directory / f"{reference_key}.pt"
        if not target.is_file():
            return None, "NOT_FOUND"
        try:
            with contextlib.redirect_stdout(sys.stderr):
                value = self.torch.load(str(target), map_location=self.tts_device,
                                        weights_only=False)
            return value, "HIT"
        except Exception as failure:
            log(f"Invalid conditioning cache removed path={target} reason={failure}")
            target.unlink(missing_ok=True)
            return None, f"CORRUPT:{type(failure).__name__}"

    def conditioning_key(self, reference):
        resolved = reference.resolve()
        profile_files = sorted(resolved.parent.glob("*.json"))
        signature = ((resolved.stat().st_size, resolved.stat().st_mtime_ns), tuple(
            (item.name, item.stat().st_size, item.stat().st_mtime_ns)
            for item in profile_files))
        cached = self.reference_identity_cache.get(str(resolved))
        if cached and cached[0] == signature:
            return cached[1]
        key = conditioning_cache_key(resolved)
        self.reference_identity_cache[str(resolved)] = (signature, key)
        return key

    def save_conditionals(self, reference_key, value):
        if not self.conditioning_cache_directory:
            return
        target = self.conditioning_cache_directory / f"{reference_key}.pt"
        temporary = target.with_suffix(f".tmp-{os.getpid()}-{time.time_ns()}")
        try:
            self.torch.save(value, str(temporary))
            os.replace(temporary, target)
        finally:
            temporary.unlink(missing_ok=True)

    def synthesize(self, request):
        if not self.tts_enabled:
            raise RuntimeError("synthesis is unavailable on the STT worker")
        if self.tts is None:
            self.warm_tts({})
        started = time.perf_counter()
        queued_at = float(request.get("queuedAtEpochMillis", 0) or 0)
        worker_queue_ms = max(0, round(time.time() * 1000 - queued_at)) if queued_at else 0
        text = str(request.get("text", "")).strip()
        reference = Path(str(request.get("reference", "")))
        if not text:
            raise ValueError("synthesis text is empty")
        if not reference.is_file():
            raise ValueError(f"voice reference does not exist: {reference}")

        resolved_reference = reference.resolve()
        reference_key = self.conditioning_key(resolved_reference)
        conditioning_started = time.perf_counter()
        cached = reference_key in self.voice_conditionals
        cache_reason = "MEMORY_HIT" if cached else "NOT_FOUND"
        with contextlib.redirect_stdout(sys.stderr), self.torch.inference_mode():
            if not cached:
                persisted, cache_reason = self.load_conditionals(reference_key)
                if persisted is not None:
                    self.tts.conds = persisted
                    self.voice_conditionals[reference_key] = persisted
                    cached = True
                else:
                    self.tts.prepare_conditionals(str(resolved_reference), exaggeration=0.0)
                    self.voice_conditionals[reference_key] = self.tts.conds
                    self.save_conditionals(reference_key, self.tts.conds)
            else:
                self.tts.conds = self.voice_conditionals[reference_key]
            conditioning_ms = round((time.perf_counter() - conditioning_started) * 1000)
            wav = self.tts.generate(text)
        generated_ms = round((time.perf_counter() - started) * 1000)
        source_rate = int(self.tts.sr)

        if wav.ndim == 1:
            wav = wav.unsqueeze(0)
        if wav.shape[0] > 1:
            wav = wav.mean(dim=0, keepdim=True)
        wav = wav.detach().to(dtype=self.torch.float32, device="cpu")
        if source_rate != 48000:
            wav = self.torchaudio.functional.resample(wav, source_rate, 48000)
        source = wav.clamp(-1.0, 1.0).squeeze(0).numpy()
        processed, levels = apply_output_gain(source, request.get("gainDb", 0.0), self.np)
        log("PCM_LEVELS preOpus npc={} preset={} sourcePeakDbfs={} sourceRmsDbfs={} "
            "gainDb={} limiterReductionDb={} preOpusPeakDbfs={} preOpusRmsDbfs={}".format(
                request.get("npcId", "unknown"), request.get("voicePresetId", "unknown"),
                levels["sourcePeakDbfs"], levels["sourceRmsDbfs"], levels["gainDb"],
                levels["limiterReductionDb"], levels["preOpusPeakDbfs"],
                levels["preOpusRmsDbfs"]))
        pcm = self.np.rint(processed * 32767.0).astype(self.np.int16)

        if self.export_wav and self.export_dir:
            self.export_dir.mkdir(parents=True, exist_ok=True)
            target = self.export_dir / f"voice-{request['id']}.wav"
            self.torchaudio.save(str(target), self.torch.from_numpy(processed).unsqueeze(0), 48000)

        encoded_started = time.perf_counter()
        frames = self.encode_opus(pcm)
        encoded_ms = round((time.perf_counter() - encoded_started) * 1000)
        cuda_allocated_mb = 0
        cuda_reserved_mb = 0
        cuda_peak_allocated_mb = 0
        cuda_peak_reserved_mb = 0
        if self.tts_device == "cuda" and self.torch.cuda.is_available():
            cuda_allocated_mb = round(self.torch.cuda.memory_allocated() / (1024 * 1024))
            cuda_reserved_mb = round(self.torch.cuda.memory_reserved() / (1024 * 1024))
            cuda_peak_allocated_mb = round(
                self.torch.cuda.max_memory_allocated() / (1024 * 1024))
            cuda_peak_reserved_mb = round(
                self.torch.cuda.max_memory_reserved() / (1024 * 1024))
        return {
            "frames": [base64.b64encode(frame).decode("ascii") for frame in frames],
            "frameCount": len(frames),
            "sourceRate": source_rate,
            "samples48k": int(pcm.size),
            "ttsMs": generated_ms,
            "encodeMs": encoded_ms,
            "device": self.tts_device,
            "reference": str(reference),
            "conditioningMs": conditioning_ms,
            "conditionalsCached": cached,
            "conditioningCacheReason": cache_reason,
            "workerQueueWaitMs": worker_queue_ms,
            "cudaAllocatedMb": cuda_allocated_mb,
            "cudaReservedMb": cuda_reserved_mb,
            "cudaPeakAllocatedMb": cuda_peak_allocated_mb,
            "cudaPeakReservedMb": cuda_peak_reserved_mb,
            "workerPid": os.getpid(),
            "modelResident": self.tts is not None,
            "conditioningCacheEntries": len(self.voice_conditionals),
            "modelLoadCount": 1,
            **levels,
        }

    def unload_tts(self, request):
        if not self.tts_enabled:
            return {"changed": False, "reason": "TTS_DISABLED", "workerPid": os.getpid()}
        changed = self.tts is not None
        self.tts = None
        self.voice_conditionals.clear()
        import gc
        gc.collect()
        if self.torch.cuda.is_available():
            self.torch.cuda.empty_cache()
        return {"changed": changed, "device": self.tts_device,
                "workerPid": os.getpid(), "modelResident": False,
                "conditioningCacheEntries": 0,
                "cudaAllocatedMb": round(self.torch.cuda.memory_allocated() / (1024 * 1024))
                    if self.torch.cuda.is_available() else 0,
                "cudaReservedMb": round(self.torch.cuda.memory_reserved() / (1024 * 1024))
                    if self.torch.cuda.is_available() else 0}

    def warm_tts(self, request):
        if not self.tts_enabled or self.chatterbox_class is None:
            raise RuntimeError("synthesis is unavailable on the STT worker")
        changed = self.tts is None
        load_ms = 0
        if changed:
            started = time.perf_counter()
            log(f"Loading ChatterboxTurboTTS once on {self.tts_device} role={self.worker_role}")
            # ChatterboxTurboTTS.from_pretrained is deliberately deferred to this warm phase.
            with contextlib.redirect_stdout(sys.stderr):
                self.tts = self.chatterbox_class.from_pretrained(device=self.tts_device)
            load_ms = round((time.perf_counter() - started) * 1000)
            self.tts_load_ms = load_ms
        requested = [Path(str(value)).resolve()
                     for value in request.get("references", []) if str(value).strip()]
        references = []
        for reference in [*self.prewarm_references, *requested]:
            if reference.is_file() and reference not in references:
                references.append(reference)
        conditioning_ms = 0
        persistent_hits = 0
        cache_misses = []
        for reference in references:
            reference_key = self.conditioning_key(reference)
            if reference_key in self.voice_conditionals:
                continue
            conditioning_started = time.perf_counter()
            with contextlib.redirect_stdout(sys.stderr), self.torch.inference_mode():
                persisted, reason = self.load_conditionals(reference_key)
                if persisted is not None:
                    self.tts.conds = persisted
                    persistent_hits += 1
                else:
                    cache_misses.append(reason)
                    self.tts.prepare_conditionals(str(reference), exaggeration=0.0)
                    self.save_conditionals(reference_key, self.tts.conds)
                self.voice_conditionals[reference_key] = self.tts.conds
            elapsed = round((time.perf_counter() - conditioning_started) * 1000)
            conditioning_ms += elapsed
            log(f"Prepared and cached voice conditionals reference={reference} "
                f"conditioningMs={elapsed}")
        self.voice_conditioning_ms += conditioning_ms
        warmup_inference_ms = 0
        if references and not self.tts_probe_complete:
            probe_started = time.perf_counter()
            first_key = self.conditioning_key(references[0])
            self.tts.conds = self.voice_conditionals[first_key]
            with contextlib.redirect_stdout(sys.stderr), self.torch.inference_mode():
                _discarded_probe = self.tts.generate("Ready.")
            warmup_inference_ms = round((time.perf_counter() - probe_started) * 1000)
            self.tts_probe_complete = True
        return {"changed": changed, "device": self.tts_device,
                "workerPid": os.getpid(), "modelResident": self.tts is not None,
                "conditioningCacheEntries": len(self.voice_conditionals),
                "loadMs": load_ms,
                "conditioningMs": conditioning_ms,
                "persistentConditioningCacheHits": persistent_hits,
                "conditioningCacheMissReasons": cache_misses,
                "warmupInferenceMs": warmup_inference_ms,
                "warmSynthesisReady": self.tts_probe_complete,
                "cudaAllocatedMb": round(self.torch.cuda.memory_allocated() / (1024 * 1024))
                    if self.torch.cuda.is_available() else 0,
                "cudaReservedMb": round(self.torch.cuda.memory_reserved() / (1024 * 1024))
                    if self.torch.cuda.is_available() else 0}

    def warm_stt(self, request):
        """Exercises Moonshine's real stream/inference path before physical speech."""
        if not self.stt_enabled:
            raise RuntimeError("transcription is unavailable on the TTS worker")
        if self.moonshine is None:
            raise RuntimeError("Moonshine is unavailable: " +
                               (self.stt_fallback_reason or "not initialized"))
        started = time.perf_counter()
        stream = self.moonshine.create_stream(0.25)
        try:
            stream.start()
            # Bounded silence initializes kernels/session state. Its result is discarded and
            # can never become an authoritative player transcript.
            stream.add_audio([0.0] * 12000, 16000)
            stream.stop()
        finally:
            stream.close()
        return {"warmed": True, "actualEngine": "MOONSHINE", "device": "cpu",
                "computeMode": "moonshine-quantized", "workerPid": os.getpid(),
                "warmupInferenceMs": round((time.perf_counter() - started) * 1000)}

    def encode_opus(self, pcm):
        codec = self.av.CodecContext.create("libopus", "w")
        codec.sample_rate = 48000
        codec.layout = "mono"
        codec.format = "s16"
        codec.bit_rate = self.opus_bitrate
        codec.options = {"application": "voip", "frame_duration": "20"}
        codec.open()

        packet_bytes = []
        pts = 0
        for offset in range(0, len(pcm), 960):
            samples = pcm[offset:offset + 960]
            if len(samples) < 960:
                samples = self.np.pad(samples, (0, 960 - len(samples)))
            frame = self.av.AudioFrame.from_ndarray(
                samples.reshape(1, -1), format="s16", layout="mono")
            frame.sample_rate = 48000
            frame.pts = pts
            pts += 960
            for packet in codec.encode(frame):
                packet_bytes.append(bytes(packet))
        for packet in codec.encode(None):
            packet_bytes.append(bytes(packet))
        if not packet_bytes:
            raise RuntimeError("Opus encoder returned no frames")
        invalid = [len(frame) for frame in packet_bytes if not 1 <= len(frame) <= 512]
        if invalid:
            raise RuntimeError(f"invalid Opus frame sizes (never truncated): {invalid[:8]}")
        return packet_bytes

    def transcribe(self, request):
        if not self.stt_enabled:
            raise RuntimeError("transcription is unavailable on the TTS worker")
        started = time.perf_counter()
        encoded = [base64.b64decode(value) for value in request.get("frames", [])]
        if not encoded:
            return {"text": "", "decodeMs": 0, "whisperMs": 0,
                    "requestedEngine": self.requested_stt_provider,
                    "actualEngine": self.streaming_stt_provider,
                    "fallback": self.stt_fallback,
                    "fallbackReason": self.stt_fallback_reason,
                    "device": "cpu" if self.streaming_stt_provider == "MOONSHINE"
                        else self.whisper_device,
                    "computeMode": "moonshine-quantized"
                        if self.streaming_stt_provider == "MOONSHINE"
                        else self.whisper_compute_type,
                    "workerPid": os.getpid(), "language": ""}

        decoded_started = time.perf_counter()
        pcm16 = self.decode_opus(encoded, 16000)
        decode_ms = round((time.perf_counter() - decoded_started) * 1000)
        if pcm16.size == 0:
            return {"text": "", "decodeMs": decode_ms, "whisperMs": 0,
                    "requestedEngine": self.requested_stt_provider,
                    "actualEngine": self.streaming_stt_provider,
                    "fallback": self.stt_fallback,
                    "fallbackReason": self.stt_fallback_reason,
                    "device": "cpu" if self.streaming_stt_provider == "MOONSHINE"
                        else self.whisper_device,
                    "computeMode": "moonshine-quantized"
                        if self.streaming_stt_provider == "MOONSHINE"
                        else self.whisper_compute_type,
                    "workerPid": os.getpid(), "language": ""}
        # CPU STT workers deliberately avoid importing torch/torchaudio. PyAV performs the
        # same authoritative 16 kHz resampling used by the streaming path.
        audio16 = pcm16.astype(self.np.float32) / 32768.0

        inference_started = time.perf_counter()
        actual_engine = "FASTER_WHISPER"
        fallback = self.stt_fallback
        fallback_reason = self.stt_fallback_reason
        language = "en"
        if self.moonshine is not None:
            stream = None
            try:
                stream = self.moonshine.create_stream(0.25)
                stream.start()
                stream.add_audio(audio16.tolist(), 16000)
                result = stream.stop()
                text = " ".join(line.text.strip() for line in result.lines).strip()
                actual_engine = "MOONSHINE"
                fallback = False
                fallback_reason = ""
            except Exception as failure:
                fallback = True
                fallback_reason = f"Moonshine inference failed: {type(failure).__name__}: {failure}"
                log(f"Moonshine inference failed; using Faster-Whisper for this utterance: {failure}")
                self.ensure_whisper()
                with contextlib.redirect_stdout(sys.stderr):
                    segments, info = self.whisper.transcribe(
                        audio16, beam_size=1, vad_filter=True,
                        condition_on_previous_text=False)
                    text = " ".join(segment.text.strip() for segment in segments).strip()
                    language = getattr(info, "language", "")
            finally:
                if stream is not None:
                    stream.close()
        else:
            self.ensure_whisper()
            with contextlib.redirect_stdout(sys.stderr):
                segments, info = self.whisper.transcribe(
                    audio16, beam_size=1, vad_filter=True,
                    condition_on_previous_text=False)
                text = " ".join(segment.text.strip() for segment in segments).strip()
                language = getattr(info, "language", "")
        whisper_ms = round((time.perf_counter() - inference_started) * 1000)
        return {
            "text": text,
            "decodeMs": decode_ms,
            "whisperMs": whisper_ms,
            "language": language,
            "requestedEngine": self.requested_stt_provider,
            "actualEngine": actual_engine,
            "fallback": fallback,
            "fallbackReason": fallback_reason,
            "device": "cpu" if actual_engine == "MOONSHINE" else self.whisper_device,
            "computeMode": "moonshine-quantized" if actual_engine == "MOONSHINE"
                else str(getattr(self, "whisper_compute_type", "unknown")),
            "workerPid": os.getpid(),
            "totalMs": round((time.perf_counter() - started) * 1000),
        }

    def start_streaming_stt(self, request):
        if self.moonshine is None:
            return {"available": False, "provider": "FASTER_WHISPER"}
        stream_id = str(request.get("streamId", "")).strip()
        if not stream_id:
            raise ValueError("streamId is required")
        previous = self.streaming_stt_sessions.pop(stream_id, None)
        if previous is not None:
            previous.close()
        state = MoonshineStreamingSession(self, self.moonshine.create_stream(0.25))
        state.stream.start()
        self.streaming_stt_sessions[stream_id] = state
        return {"available": True, "provider": "MOONSHINE"}

    def append_streaming_stt(self, request):
        stream_id = str(request.get("streamId", "")).strip()
        state = self.streaming_stt_sessions.get(stream_id)
        if state is None:
            raise ValueError(f"unknown streaming STT session: {stream_id}")
        started = time.perf_counter()
        state.add_opus(request.get("frames", []))
        return {"provider": "MOONSHINE", "partial": state.partial,
                "batchMs": round((time.perf_counter() - started) * 1000)}

    def finish_streaming_stt(self, request):
        stream_id = str(request.get("streamId", "")).strip()
        state = self.streaming_stt_sessions.pop(stream_id, None)
        if state is None:
            raise ValueError(f"unknown streaming STT session: {stream_id}")
        started = time.perf_counter()
        try:
            state.flush_decoder()
            result = state.stream.stop()
            text = " ".join(line.text.strip() for line in result.lines).strip()
            final_ms = round((time.perf_counter() - started) * 1000)
            return {"text": text, "decodeMs": state.decode_ms,
                    "whisperMs": final_ms, "provider": "MOONSHINE",
                    "requestedEngine": self.requested_stt_provider,
                    "actualEngine": "MOONSHINE", "fallback": False,
                    "fallbackReason": "", "device": "cpu",
                    "computeMode": "moonshine-quantized",
                    "workerPid": os.getpid(), "language": "en"}
        finally:
            state.close()

    def decode_opus(self, encoded, output_rate=48000):
        codec = self.av.CodecContext.create("opus", "r")
        codec.sample_rate = 48000
        codec.layout = "mono"
        codec.open()
        resampler = self.av.AudioResampler(format="s16", layout="mono", rate=output_rate)
        chunks = []
        for value in encoded:
            packet = self.av.Packet(value)
            for frame in codec.decode(packet):
                for converted in resampler.resample(frame):
                    chunks.append(converted.to_ndarray().reshape(-1)
                                  .astype(self.np.int16, copy=False))
        for frame in codec.decode(None):
            for converted in resampler.resample(frame):
                chunks.append(converted.to_ndarray().reshape(-1)
                              .astype(self.np.int16, copy=False))
        for converted in resampler.resample(None):
            chunks.append(converted.to_ndarray().reshape(-1)
                          .astype(self.np.int16, copy=False))
        return self.np.concatenate(chunks) if chunks else self.np.zeros(0, dtype=self.np.int16)


class MoonshineStreamingSession:
    def __init__(self, worker, stream):
        self.worker = worker
        self.stream = stream
        self.partial = ""
        self.decode_ms = 0
        self.codec = worker.av.CodecContext.create("opus", "r")
        self.codec.sample_rate = 48000
        self.codec.layout = "mono"
        self.codec.open()
        self.resampler = worker.av.AudioResampler(format="s16", layout="mono", rate=16000)
        self.stream.add_listener(self._event)

    def _event(self, event):
        line = getattr(event, "line", None)
        if line is not None and getattr(line, "text", None):
            self.partial = line.text.strip()

    def add_opus(self, encoded_values):
        started = time.perf_counter()
        for value in encoded_values:
            packet = self.worker.av.Packet(base64.b64decode(value))
            for frame in self.codec.decode(packet):
                self._feed_resampled(self.resampler.resample(frame))
        self.decode_ms += round((time.perf_counter() - started) * 1000)

    def flush_decoder(self):
        started = time.perf_counter()
        for frame in self.codec.decode(None):
            self._feed_resampled(self.resampler.resample(frame))
        self._feed_resampled(self.resampler.resample(None))
        self.decode_ms += round((time.perf_counter() - started) * 1000)

    def _feed_resampled(self, frames):
        for frame in frames:
            pcm = frame.to_ndarray().reshape(-1).astype(
                self.worker.np.float32, copy=False) / 32768.0
            if pcm.size:
                self.stream.add_audio(pcm.tolist(), 16000)

    def close(self):
        try:
            self.stream.close()
        except Exception:
            pass


class RemoteVoiceServer:
    """HTTP transport around the same VoiceWorker used by stdio mode."""
    MAX_BODY_BYTES = 64 * 1024 * 1024

    def __init__(self, worker, host, port, cache_directory=""):
        self.worker = worker
        self.host = host
        self.port = port
        self.cache_directory = Path(cache_directory).resolve() if cache_directory \
            else Path(tempfile.gettempdir()).resolve() / "immersive-npcs-voice-cache"
        self.cache_directory.mkdir(parents=True, exist_ok=True)
        self.cancelled = set()
        self.cancel_lock = threading.Lock()
        self.tts_lock = threading.Lock()
        self.stt_lock = threading.Lock()
        outer = self

        class Handler(BaseHTTPRequestHandler):
            server_version = "ImmersiveNPCVoice/1"

            def do_GET(self):
                if self.path.rstrip("/") != "/health":
                    self._json(404, {"error": "not found"})
                    return
                self._json(200, outer.health())

            def do_POST(self):
                try:
                    length = int(self.headers.get("Content-Length", "0"))
                    if length < 0 or length > outer.MAX_BODY_BYTES:
                        self._json(413, {"error": "request body is too large"})
                        return
                    body = json.loads(self.rfile.read(length) or b"{}")
                    status, result = outer.dispatch(self.path.rstrip("/"), body)
                    self._json(status, result)
                except Exception as failure:
                    traceback.print_exc(file=sys.stderr)
                    self._json(500, {"error": str(failure)})

            def _json(self, status, value):
                encoded = json.dumps(value, separators=(",", ":")).encode("utf-8")
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(encoded)))
                self.end_headers()
                try:
                    self.wfile.write(encoded)
                except (BrokenPipeError, ConnectionResetError):
                    pass

            def log_message(self, fmt, *args):
                log("REMOTE_HTTP " + (fmt % args))

        self.httpd = ThreadingHTTPServer((host, port), Handler)

    def health(self):
        return {
            "status": "healthy",
            "workerRole": self.worker.worker_role,
            "ttsDevice": self.worker.tts_device,
            "sttProvider": self.worker.streaming_stt_provider,
            "ttsEnabled": self.worker.tts_enabled,
            "sttEnabled": self.worker.stt_enabled,
        }

    def dispatch(self, path, request):
        request_id = str(request.get("requestId", "")).strip()
        if path == "/v1/cancel":
            if request_id:
                with self.cancel_lock:
                    self.cancelled.add(request_id)
                state = self.worker.streaming_stt_sessions.pop(request_id, None)
                if state is not None:
                    state.close()
            return 200, {"cancelled": bool(request_id), "requestId": request_id}
        if not request_id:
            return 400, {"error": "requestId is required"}
        if self._cancelled(request_id):
            return 409, {"error": "request was cancelled", "requestId": request_id}
        if path == "/v1/tts/synthesize":
            operation = dict(request)
            operation["id"] = request_id
            operation["npcId"] = request.get("npcStableId", "")
            operation["queuedAtEpochMillis"] = request.get(
                "queuedAtEpochMillis", round(time.time() * 1000))
            operation["reference"] = str(self._reference_file(request))
            with self.tts_lock:
                if self._cancelled(request_id):
                    return 409, {"error": "request was cancelled", "requestId": request_id}
                result = self.worker.synthesize(operation)
            result["inferenceMs"] = result.get("ttsMs", 0)
        elif path in ("/v1/stt/transcribe", "/v1/stt/stream/start",
                      "/v1/stt/stream/audio", "/v1/stt/stream/finish"):
            operation = dict(request)
            operation["id"] = request_id
            operation["frames"] = request.get("opusFrames", [])
            operation["streamId"] = str(request.get("sessionId", request_id))
            with self.stt_lock:
                if self._cancelled(request_id):
                    return 409, {"error": "request was cancelled", "requestId": request_id}
                if path == "/v1/stt/transcribe":
                    result = self.worker.transcribe(operation)
                elif path == "/v1/stt/stream/start":
                    result = self.worker.start_streaming_stt(operation)
                elif path == "/v1/stt/stream/audio":
                    result = self.worker.append_streaming_stt(operation)
                else:
                    result = self.worker.finish_streaming_stt(operation)
            result["inferenceMs"] = result.get("whisperMs", 0)
        else:
            return 404, {"error": "not found"}
        if self._cancelled(request_id):
            return 409, {"error": "request was cancelled", "requestId": request_id}
        result["requestId"] = request_id
        return 200, result

    def _reference_file(self, request):
        encoded = str(request.get("referenceWavBase64", ""))
        if not encoded:
            raise ValueError("referenceWavBase64 is required")
        wav = base64.b64decode(encoded, validate=True)
        if len(wav) < 44 or wav[:4] != b"RIFF" or wav[8:12] != b"WAVE":
            raise ValueError("referenceWavBase64 is not a valid WAV container")
        digest = hashlib.sha256(wav).hexdigest()
        target = self.cache_directory / f"reference-{digest}.wav"
        if not target.is_file():
            temporary = target.with_suffix(".tmp")
            temporary.write_bytes(wav)
            os.replace(temporary, target)
        return target

    def _cancelled(self, request_id):
        with self.cancel_lock:
            return request_id in self.cancelled

    def serve(self):
        log(f"Remote inference HTTP ready host={self.host} port={self.httpd.server_port} "
            f"role={self.worker.worker_role}")
        try:
            self.httpd.serve_forever(poll_interval=0.25)
        finally:
            self.httpd.server_close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--tts-device", default="auto")
    parser.add_argument("--whisper-model", default="base.en")
    parser.add_argument("--whisper-device", default="cpu")
    parser.add_argument("--whisper-compute-type", default="int8")
    parser.add_argument("--stt-provider", default="AUTO")
    parser.add_argument("--moonshine-model", default="TINY_STREAMING")
    parser.add_argument("--worker-role", choices=("combined", "tts", "stt"),
                        default="combined")
    parser.add_argument("--prewarm-reference", default="")
    parser.add_argument("--opus-bitrate", type=int, default=24000)
    parser.add_argument("--export-debug-wav", action="store_true")
    parser.add_argument("--export-directory", default="")
    parser.add_argument("--transport", choices=("stdio", "http"), default="stdio")
    parser.add_argument("--listen-host", default="127.0.0.1")
    parser.add_argument("--listen-port", type=int, default=8765)
    parser.add_argument("--remote-cache-directory", default="")
    parser.add_argument("--conditioning-cache-directory", default="")
    args = parser.parse_args()

    try:
        worker = VoiceWorker(args)
        ready = {
            "type": "ready",
            "workerRole": worker.worker_role,
            "ttsDevice": worker.tts_device,
            "torch": worker.torch.__version__ if worker.torch is not None else "NOT_LOADED",
            "cuda": worker.torch.version.cuda if worker.torch is not None else "",
            "gpu": worker.torch.cuda.get_device_name(0)
                    if worker.torch is not None and worker.torch.cuda.is_available() else "",
            "sttProvider": worker.streaming_stt_provider,
            "requestedSttEngine": worker.requested_stt_provider,
            "actualSttEngine": worker.streaming_stt_provider,
            "sttFallback": worker.stt_fallback,
            "sttFallbackReason": worker.stt_fallback_reason,
            "sttDevice": "cpu" if worker.streaming_stt_provider == "MOONSHINE"
                    else worker.whisper_device,
            "sttComputeMode": "moonshine-quantized"
                    if worker.streaming_stt_provider == "MOONSHINE"
                    else worker.whisper_compute_type,
            "workerPid": os.getpid(),
            "ttsModelResident": worker.tts is not None,
            "conditioningCacheEntries": len(worker.voice_conditionals),
            "cudaAllocatedMb": round(worker.torch.cuda.memory_allocated() / (1024 * 1024))
                    if worker.torch is not None and worker.tts_device == "cuda"
                    and worker.torch.cuda.is_available() else 0,
            "cudaReservedMb": round(worker.torch.cuda.memory_reserved() / (1024 * 1024))
                    if worker.torch is not None and worker.tts_device == "cuda"
                    and worker.torch.cuda.is_available() else 0,
            "cudaPeakAllocatedMb": round(
                    worker.torch.cuda.max_memory_allocated() / (1024 * 1024))
                    if worker.torch is not None and worker.tts_device == "cuda"
                    and worker.torch.cuda.is_available() else 0,
            "cudaPeakReservedMb": round(
                    worker.torch.cuda.max_memory_reserved() / (1024 * 1024))
                    if worker.torch is not None and worker.tts_device == "cuda"
                    and worker.torch.cuda.is_available() else 0,
            "ttsLoadMs": worker.tts_load_ms,
            "voiceConditioningMs": worker.voice_conditioning_ms,
            "whisperLoadMs": worker.whisper_load_ms,
        }
        if args.transport == "stdio":
            emit(ready)
        else:
            log(json.dumps(ready, separators=(",", ":")))
    except Exception as failure:
        if args.transport == "stdio":
            emit({"type": "fatal", "error": str(failure)})
        traceback.print_exc(file=sys.stderr)
        return 2

    if args.transport == "http":
        RemoteVoiceServer(worker, args.listen_host, args.listen_port,
                          args.remote_cache_directory).serve()
        return 0

    for line in sys.stdin:
        try:
            request = json.loads(line)
            operation = request.get("op")
            if operation == "synthesize":
                result = worker.synthesize(request)
            elif operation == "unload_tts":
                result = worker.unload_tts(request)
            elif operation == "warm_tts":
                result = worker.warm_tts(request)
            elif operation == "warm_stt":
                result = worker.warm_stt(request)
            elif operation == "transcribe":
                result = worker.transcribe(request)
            elif operation == "stt_stream_start":
                result = worker.start_streaming_stt(request)
            elif operation == "stt_stream_audio":
                result = worker.append_streaming_stt(request)
            elif operation == "stt_stream_finish":
                result = worker.finish_streaming_stt(request)
            elif operation == "ping":
                result = {"ready": True, "device": worker.tts_device}
            elif operation == "shutdown":
                emit({"id": request.get("id"), "ok": True})
                return 0
            else:
                raise ValueError(f"unknown operation: {operation}")
            emit({"id": request.get("id"), "ok": True, **result})
        except Exception as failure:
            emit({"id": request.get("id"), "ok": False, "error": str(failure)})
            traceback.print_exc(file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
