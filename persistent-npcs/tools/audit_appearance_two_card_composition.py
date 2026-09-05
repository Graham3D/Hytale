"""Audit the bounded Custom UI flat-mask fallback against native gradient output.

This tool generates no images. It proves whether a single immutable mask plus a
flat UI color could reproduce Hytale's gradient material, even when the mask is
allowed the unrealistically favorable freedom to choose a different opacity for
every tunable pixel.
"""

from __future__ import annotations

import argparse
import io
import json
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image


COSMETICS = (
    "UNDERTOP:Wide_Neck_Shirt",
    "UNDERTOP:VNeck_Shirt",
)
COLORS = ("Green", "Purple")


def rgb(hex_color: str) -> np.ndarray:
    return np.array([int(hex_color[index:index + 2], 16) for index in (1, 3, 5)],
                    dtype=float)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("assets", type=Path)
    parser.add_argument("--sources", type=Path,
                        default=Path("tools/retired-appearance-r154/appearance-color-sources"))
    args = parser.parse_args()

    source_index = json.loads((args.sources / "index.json").read_text(encoding="utf-8"))
    with zipfile.ZipFile(args.assets) as archive:
        gradient_sets = json.loads(archive.read(
            "Cosmetics/CharacterCreator/GradientSets.json"))
        cotton = next(item for item in gradient_sets if item["Id"] == "Colored_Cotton")
        results = []
        for cosmetic in COSMETICS:
            source = next(iter(source_index["entries"][cosmetic]["sources"].values()))
            mask = np.array(Image.open(args.sources / source["mask"]).convert("RGBA"))
            tunable = (mask[:, :, 2] == 255) & (mask[:, :, 3] > 0)
            gray_index = mask[:, :, 0][tunable].astype(int)
            lighting = mask[:, :, 1][tunable].astype(float) / 255.0
            color_results = []
            native_targets = []
            flat_colors = []
            for color_id in COLORS:
                descriptor = cotton["Gradients"][color_id]
                lut = np.array(Image.open(io.BytesIO(archive.read(
                    "Common/" + descriptor["Texture"]))).convert("RGB"))[0].astype(float)
                target = lut[gray_index] * lighting[:, None]
                flat_color = rgb(descriptor["BaseColor"][0])
                native_targets.append(target)
                flat_colors.append(flat_color)
                denominator = float(flat_color @ flat_color)
                scale = np.clip((target @ flat_color) / denominator, 0.0, 1.0)
                best_flat_mask = scale[:, None] * flat_color
                error = np.abs(target - best_flat_mask)
                exact = np.all(np.rint(target) == np.rint(best_flat_mask), axis=1)
                color_results.append({
                    "colorId": color_id,
                    "baseColor": descriptor["BaseColor"][0],
                    "meanAbsoluteRgbErrorLowerBound": round(float(error.mean()), 2),
                    "p95AbsoluteRgbErrorLowerBound": round(float(np.percentile(error, 95)), 2),
                    "maxAbsoluteRgbErrorLowerBound": round(float(error.max()), 2),
                    "exactPixelPercent": round(float(exact.mean() * 100.0), 2),
                })
            target_delta = native_targets[0] - native_targets[1]
            color_delta = flat_colors[0] - flat_colors[1]
            shared_opacity = np.clip((target_delta @ color_delta)
                                     / float(color_delta @ color_delta), 0.0, 1.0)
            immutable_detail = ((native_targets[0] - shared_opacity[:, None] * flat_colors[0])
                                + (native_targets[1] - shared_opacity[:, None] * flat_colors[1])) / 2.0
            shared_predictions = [immutable_detail + shared_opacity[:, None] * color
                                  for color in flat_colors]
            shared_error = np.abs(np.concatenate([
                native_targets[index] - shared_predictions[index]
                for index in range(len(COLORS))
            ], axis=0))
            shared_exact = np.concatenate([
                np.all(np.rint(native_targets[index]) == np.rint(shared_predictions[index]), axis=1)
                for index in range(len(COLORS))
            ])
            results.append({
                "cosmeticId": cosmetic,
                "sourceDimensions": list(Image.open(args.sources / source["mask"]).size),
                "tunablePixelCount": int(tunable.sum()),
                "tunableCoveragePercent": round(float(tunable.mean() * 100.0), 2),
                "colors": color_results,
                "sharedImmutableDetailAndMask": {
                    "meanAbsoluteRgbErrorLowerBound": round(float(shared_error.mean()), 2),
                    "p95AbsoluteRgbErrorLowerBound": round(float(np.percentile(shared_error, 95)), 2),
                    "maxAbsoluteRgbErrorLowerBound": round(float(shared_error.max()), 2),
                    "exactPixelPercent": round(float(shared_exact.mean() * 100.0), 2),
                },
            })

    print(json.dumps({
        "probe": "immutable-base-plus-flat-mask",
        "runtimeImagesCreated": 0,
        "result": "REJECTED_NATIVE_COLOR_PARITY_NOT_REPRODUCIBLE",
        "reason": "Native 256-entry gradient LUT output is not collinear with a flat UI color.",
        "cards": results,
    }, indent=2))


if __name__ == "__main__":
    main()
