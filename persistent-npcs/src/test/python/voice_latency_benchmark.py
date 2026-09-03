"""Targeted local worker benchmark; intentionally excludes Hytale and the full suite."""

import argparse
import importlib.util
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("worker", type=Path)
    parser.add_argument("reference", type=Path)
    parser.add_argument("--text", action="append",
                        help="Text to synthesize; repeat to compare exact variants")
    args = parser.parse_args()
    spec = importlib.util.spec_from_file_location("immersive_voice_worker", args.worker)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    worker_args = argparse.Namespace(
        tts_device="auto",
        whisper_model="base.en",
        whisper_device="cpu",
        whisper_compute_type="int8",
        stt_provider="AUTO",
        moonshine_model="TINY_STREAMING",
        worker_role="tts",
        opus_bitrate=24000,
        export_debug_wav=False,
        export_directory="",
        prewarm_reference=str(args.reference),
    )
    worker = module.VoiceWorker(worker_args)
    results = []
    texts = args.text or ["Hello, Graham. It is good to see you."] * 2
    for index, benchmark_text in enumerate(texts):
        result = worker.synthesize({
            "id": f"tts-{index}",
            "text": benchmark_text,
            "reference": str(args.reference),
            "npcId": "benchmark-mara",
            "voicePresetId": "mara",
            "gainDb": 4.0,
        })
        results.append({"inputText": benchmark_text, **{
            key: value for key, value in result.items() if key != "frames"}})
        if worker.worker_role != "tts":
            results[-1]["transcript"] = worker.transcribe({
                "frames": result["frames"]})
        if worker.worker_role != "tts" and worker.moonshine is not None:
            stream_id = f"benchmark-{index}"
            worker.start_streaming_stt({"streamId": stream_id})
            frames = result["frames"]
            partials = []
            for offset in range(0, len(frames), 10):
                update = worker.append_streaming_stt({
                    "streamId": stream_id,
                    "frames": frames[offset:offset + 10],
                })
                if update["partial"]:
                    partials.append(update["partial"])
            final = worker.finish_streaming_stt({"streamId": stream_id})
            results[-1]["moonshinePartials"] = partials
            results[-1]["moonshineFinal"] = final
    print(json.dumps({
        "sttProvider": worker.streaming_stt_provider,
        "ttsLoadMs": worker.tts_load_ms,
        "voiceConditioningMs": worker.voice_conditioning_ms,
        "runs": results,
    }, indent=2))


if __name__ == "__main__":
    main()
