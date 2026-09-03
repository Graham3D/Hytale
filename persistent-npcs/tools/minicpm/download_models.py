import argparse
import hashlib
import json
from pathlib import Path

from huggingface_hub import snapshot_download


FILES = {
    "MiniCPM-o-4_5-Q4_K_M.gguf": 5_026_714_400,
    "audio/MiniCPM-o-4_5-audio-F16.gguf": 660_167_904,
    "tts/MiniCPM-o-4_5-tts-F16.gguf": 1_157_244_416,
    "tts/MiniCPM-o-4_5-projector-F16.gguf": 14_948_640,
    "token2wav-gguf/encoder.gguf": 151_339_008,
    "token2wav-gguf/flow_extra.gguf": 13_663_328,
    "token2wav-gguf/flow_matching.gguf": 458_250_240,
    "token2wav-gguf/hifigan2.gguf": 83_242_816,
    "token2wav-gguf/prompt_cache.gguf": 211_613_152,
    # The packaged Comni worker initializes omni media_type=2 once before it can
    # switch a realtime audio session to media_type=1. It is therefore a current
    # structural startup dependency, but no Hytale image frames are forwarded.
    "vision/MiniCPM-o-4_5-vision-F16.gguf": 1_095_113_184,
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(8 * 1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest().upper()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", required=True)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--model-dir", required=True)
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()

    model_dir = Path(args.model_dir).resolve()
    model_dir.mkdir(parents=True, exist_ok=True)
    if not args.verify_only:
        snapshot_download(
            repo_id=args.repository,
            revision=args.revision,
            allow_patterns=list(FILES),
            local_dir=str(model_dir),
            max_workers=4,
        )

    artifacts = []
    errors = []
    for relative, expected_size in FILES.items():
        path = model_dir / relative
        if not path.is_file():
            errors.append(f"missing: {relative}")
            continue
        actual_size = path.stat().st_size
        if actual_size != expected_size:
            errors.append(f"size mismatch: {relative} expected={expected_size} actual={actual_size}")
            continue
        artifacts.append({
            "path": relative,
            "bytes": actual_size,
            "sha256": sha256(path),
        })

    manifest = {
        "repository": args.repository,
        "revision": args.revision,
        "modelDirectory": str(model_dir),
        "artifacts": artifacts,
        "errors": errors,
        "valid": not errors,
    }
    Path(args.manifest).write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(json.dumps(manifest, indent=2))
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())

