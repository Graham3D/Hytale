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
from pathlib import Path

PROTOCOL_OUT = sys.stdout


def emit(value):
    PROTOCOL_OUT.write(json.dumps(value, separators=(",", ":")) + "\n")
    PROTOCOL_OUT.flush()


def log(message):
    print(message, file=sys.stderr, flush=True)


def selected_device(requested, torch):
    if requested.lower() == "auto":
        return "cuda" if torch.cuda.is_available() else "cpu"
    return requested.lower()


class VoiceWorker:
    def __init__(self, args):
        with contextlib.redirect_stdout(sys.stderr):
            import av
            import numpy as np
            import torch
            import torchaudio
            from chatterbox.tts_turbo import ChatterboxTurboTTS
            from faster_whisper import WhisperModel

        self.av = av
        self.np = np
        self.torch = torch
        self.torchaudio = torchaudio
        self.tts_device = selected_device(args.tts_device, torch)
        self.whisper_device = args.whisper_device
        self.opus_bitrate = args.opus_bitrate
        self.export_wav = args.export_debug_wav
        self.export_dir = Path(args.export_directory) if args.export_directory else None

        started = time.perf_counter()
        log(f"Loading ChatterboxTurboTTS once on {self.tts_device}")
        with contextlib.redirect_stdout(sys.stderr):
            self.tts = ChatterboxTurboTTS.from_pretrained(device=self.tts_device)
        self.tts_load_ms = round((time.perf_counter() - started) * 1000)

        started = time.perf_counter()
        log(f"Loading Whisper once model={args.whisper_model} "
            f"device={args.whisper_device} compute={args.whisper_compute_type}")
        with contextlib.redirect_stdout(sys.stderr):
            self.whisper = WhisperModel(
                args.whisper_model,
                device=args.whisper_device,
                compute_type=args.whisper_compute_type,
            )
        self.whisper_load_ms = round((time.perf_counter() - started) * 1000)

    def synthesize(self, request):
        started = time.perf_counter()
        text = str(request.get("text", "")).strip()
        reference = Path(str(request.get("reference", "")))
        if not text:
            raise ValueError("synthesis text is empty")
        if not reference.is_file():
            raise ValueError(f"voice reference does not exist: {reference}")

        with contextlib.redirect_stdout(sys.stderr), self.torch.inference_mode():
            wav = self.tts.generate(text, audio_prompt_path=str(reference))
        generated_ms = round((time.perf_counter() - started) * 1000)
        source_rate = int(self.tts.sr)

        if wav.ndim == 1:
            wav = wav.unsqueeze(0)
        if wav.shape[0] > 1:
            wav = wav.mean(dim=0, keepdim=True)
        wav = wav.detach().to(dtype=self.torch.float32, device="cpu")
        if source_rate != 48000:
            wav = self.torchaudio.functional.resample(wav, source_rate, 48000)
        wav = wav.clamp(-1.0, 1.0)
        pcm = (wav.squeeze(0).numpy() * 32767.0).astype(self.np.int16)

        if self.export_wav and self.export_dir:
            self.export_dir.mkdir(parents=True, exist_ok=True)
            target = self.export_dir / f"voice-{request['id']}.wav"
            self.torchaudio.save(str(target), wav, 48000)

        encoded_started = time.perf_counter()
        frames = self.encode_opus(pcm)
        encoded_ms = round((time.perf_counter() - encoded_started) * 1000)
        return {
            "frames": [base64.b64encode(frame).decode("ascii") for frame in frames],
            "frameCount": len(frames),
            "sourceRate": source_rate,
            "samples48k": int(pcm.size),
            "ttsMs": generated_ms,
            "encodeMs": encoded_ms,
            "device": self.tts_device,
            "reference": str(reference),
        }

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
        started = time.perf_counter()
        encoded = [base64.b64decode(value) for value in request.get("frames", [])]
        if not encoded:
            return {"text": "", "decodeMs": 0, "whisperMs": 0}

        decoded_started = time.perf_counter()
        pcm48 = self.decode_opus(encoded)
        decode_ms = round((time.perf_counter() - decoded_started) * 1000)
        if pcm48.size == 0:
            return {"text": "", "decodeMs": decode_ms, "whisperMs": 0}
        audio16 = self.torchaudio.functional.resample(
            self.torch.from_numpy(pcm48.astype(self.np.float32) / 32768.0),
            48000, 16000).numpy()

        whisper_started = time.perf_counter()
        with contextlib.redirect_stdout(sys.stderr):
            segments, info = self.whisper.transcribe(
                audio16, beam_size=1, vad_filter=True, condition_on_previous_text=False)
            text = " ".join(segment.text.strip() for segment in segments).strip()
        whisper_ms = round((time.perf_counter() - whisper_started) * 1000)
        return {
            "text": text,
            "decodeMs": decode_ms,
            "whisperMs": whisper_ms,
            "language": getattr(info, "language", ""),
            "totalMs": round((time.perf_counter() - started) * 1000),
        }

    def decode_opus(self, encoded):
        codec = self.av.CodecContext.create("opus", "r")
        codec.sample_rate = 48000
        codec.layout = "mono"
        codec.open()
        resampler = self.av.AudioResampler(format="s16", layout="mono", rate=48000)
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


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--tts-device", default="auto")
    parser.add_argument("--whisper-model", default="base.en")
    parser.add_argument("--whisper-device", default="cpu")
    parser.add_argument("--whisper-compute-type", default="int8")
    parser.add_argument("--opus-bitrate", type=int, default=24000)
    parser.add_argument("--export-debug-wav", action="store_true")
    parser.add_argument("--export-directory", default="")
    args = parser.parse_args()

    try:
        worker = VoiceWorker(args)
        emit({
            "type": "ready",
            "ttsDevice": worker.tts_device,
            "torch": worker.torch.__version__,
            "cuda": worker.torch.version.cuda,
            "gpu": worker.torch.cuda.get_device_name(0)
                    if worker.torch.cuda.is_available() else "",
            "ttsLoadMs": worker.tts_load_ms,
            "whisperLoadMs": worker.whisper_load_ms,
        })
    except Exception as failure:
        emit({"type": "fatal", "error": str(failure)})
        traceback.print_exc(file=sys.stderr)
        return 2

    for line in sys.stdin:
        try:
            request = json.loads(line)
            operation = request.get("op")
            if operation == "synthesize":
                result = worker.synthesize(request)
            elif operation == "transcribe":
                result = worker.transcribe(request)
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
