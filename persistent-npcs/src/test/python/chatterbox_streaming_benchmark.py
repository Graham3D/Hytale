"""Isolated benchmark for the unmerged Chatterbox Turbo streaming pull request."""

import argparse
import json
import time
from pathlib import Path

import torch
from chatterbox.tts_turbo import ChatterboxTurboTTS


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("reference", type=Path)
    args = parser.parse_args()
    model = ChatterboxTurboTTS.from_pretrained(device="cuda")
    model.prepare_conditionals(str(args.reference), exaggeration=0.0)
    started = time.perf_counter()
    first_ms = None
    chunks = []
    with torch.inference_mode():
        for chunk in model.stream(
                "Hello, Graham. It is good to see you.", chunk_tokens=12):
            if first_ms is None:
                first_ms = round((time.perf_counter() - started) * 1000)
            chunks.append({
                "samples": int(chunk.audio.numel()),
                "final": bool(chunk.is_final),
                "tokens": int(chunk.generated_tokens),
            })
    print(json.dumps({
        "firstAudioMs": first_ms,
        "totalMs": round((time.perf_counter() - started) * 1000),
        "chunks": chunks,
    }, indent=2))


if __name__ == "__main__":
    main()
