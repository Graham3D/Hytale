"""Real Moonshine + Chatterbox Phase 2 calibration; not part of deterministic tests.

This deliberately uses the shipped worker implementation and installed provider environment.
Synthetic Chatterbox speech verifies the transport/streaming contract, but is not claimed as a
replacement for the required connected-client physical PTT matrix.
"""

import argparse
import gc
import importlib.util
import json
import time
from pathlib import Path


def worker_args(role, reference):
    return argparse.Namespace(
        tts_device="auto",
        whisper_model="base.en",
        whisper_device="cpu",
        whisper_compute_type="int8",
        stt_provider="MOONSHINE",
        moonshine_model="TINY_STREAMING",
        worker_role=role,
        opus_bitrate=24000,
        export_debug_wav=False,
        export_directory="",
        prewarm_reference=str(reference) if role != "stt" else "",
    )


def timed(operation):
    started = time.perf_counter()
    result = operation()
    return round((time.perf_counter() - started) * 1000), result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("worker", type=Path)
    parser.add_argument("reference", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    spec = importlib.util.spec_from_file_location("immersive_voice_worker", args.worker)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)

    report = {"physicalPttValidated": False, "physicalPttReason":
              "Synthetic provider calibration cannot originate a physical Hytale PTT utterance."}
    tts_init_ms, tts = timed(lambda: module.VoiceWorker(
        worker_args("tts", args.reference)))
    warm_ms, warm = timed(lambda: tts.warm_tts({"references": [str(args.reference)]}))
    phrases = [
        "Hello. It is good to see you.",
        "The dagger in your hand has an Onyxium edge.",
        "I can help, but I need to finish this work first.",
    ]
    renders = []
    encoded_for_stt = None
    for index, phrase in enumerate(phrases):
        elapsed, rendered = timed(lambda p=phrase, i=index: tts.synthesize({
            "id": f"phase2-{i}", "text": p, "reference": str(args.reference),
            "npcId": "mara", "voicePresetId": "mara", "gainDb": 4.0,
        }))
        if encoded_for_stt is None:
            encoded_for_stt = list(rendered["frames"])
        renders.append({"text": phrase, "wallMs": elapsed, **{
            key: value for key, value in rendered.items() if key != "frames"}})
    unload_ms, unload = timed(lambda: tts.unload_tts({}))
    report["chatterbox"] = {"constructorMs": tts_init_ms, "warmWallMs": warm_ms,
                            "warm": warm, "renders": renders,
                            "unloadWallMs": unload_ms, "unload": unload}
    del tts
    gc.collect()

    stt_init_ms, stt = timed(lambda: module.VoiceWorker(
        worker_args("stt", args.reference)))
    stt_warm_ms, stt_warm = timed(lambda: stt.warm_stt({}))
    stream_id = "phase2-streaming-stt"
    start_ms, start = timed(lambda: stt.start_streaming_stt({"streamId": stream_id}))
    batches = []
    for offset in range(0, len(encoded_for_stt), 10):
        elapsed, update = timed(lambda o=offset: stt.append_streaming_stt({
            "streamId": stream_id, "frames": encoded_for_stt[o:o + 10]}))
        batches.append({"offset": offset, "wallMs": elapsed, **update})
    final_wall_ms, final = timed(lambda: stt.finish_streaming_stt({
        "streamId": stream_id}))
    report["moonshine"] = {
        "constructorMs": stt_init_ms, "warmWallMs": stt_warm_ms,
        "warm": stt_warm, "streamStartWallMs": start_ms, "streamStart": start,
        "batchCount": len(batches), "maximumBatchWallMs": max(
            (batch["wallMs"] for batch in batches), default=0),
        "partials": [{"offset": batch["offset"], "partial": batch["partial"]}
                     for batch in batches if batch.get("partial")],
        "finalWallMs": final_wall_ms, "final": final,
        "expectedSyntheticText": phrases[0],
    }

    encoded = json.dumps(report, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded + "\n", encoding="utf-8")
    print(encoded)


if __name__ == "__main__":
    main()
