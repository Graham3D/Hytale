"""Bounded standalone ExLlamaV3/Nemotron Phase A feasibility benchmark.

This is deliberately independent of the ImmersiveNPCs runtime. It exercises the
exact candidate model on the target GPU without changing the production provider.
"""

from __future__ import annotations

import argparse
import json
import os
import platform
import subprocess
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import asdict, dataclass
from pathlib import Path

import psutil
import torch
import triton
from exllamav3 import Cache, Config, Generator, Job, Model, Tokenizer, TopPSampler


SYSTEM = """You are Mara, an adult human apprentice blacksmith in Hytale. You are Mara, not the
player and not Lycander. You are curious, mechanically minded, warm, direct, and dryly funny.
Lycander is your grandfather and only remaining close family. Treat supplied world state,
relationships, memories, capabilities, and constraints as authoritative. Never invent current
objects, events, actions, memories, locations, relationships, or tool results. Speak one short,
natural, in-character reply without labels, formatting, narration, or stage directions."""

PROBES = (
    ("FAST", "World: player nearby; no danger.", "Hello Mara."),
    (
        "GROUNDED",
        "Held item: Onyxium dagger. Visible nearby: player only.",
        "Can you see what's in my hand?",
    ),
)


@dataclass
class ProbeResult:
    name: str
    repetition: int
    prompt_tokens: int
    cached_tokens: int
    output_tokens: int
    ttft_ms: float
    completion_ms: float
    prefill_ms: float
    generation_ms: float
    tokens_per_second: float
    output: str


def gpu_snapshot() -> dict:
    command = [
        "nvidia-smi",
        "--query-gpu=index,name,memory.total,memory.used,memory.free,utilization.gpu",
        "--format=csv,noheader,nounits",
    ]
    try:
        values = subprocess.check_output(command, text=True, timeout=5).strip().splitlines()[0]
        index, name, total, used, free, utilization = [part.strip() for part in values.split(",")]
        return {
            "measurementStatus": "AVAILABLE",
            "index": int(index),
            "name": name,
            "totalVramMiB": int(total),
            "usedVramMiB": int(used),
            "freeVramMiB": int(free),
            "utilizationPercent": int(utilization),
        }
    except Exception as exc:  # diagnostic truth is preferable to invented data
        return {
            "measurementStatus": "UNKNOWN",
            "error": f"{type(exc).__name__}: {exc}",
            "totalVramMiB": -1,
            "usedVramMiB": -1,
            "freeVramMiB": -1,
            "utilizationPercent": -1,
        }


def process_snapshot() -> dict:
    process = psutil.Process(os.getpid())
    memory = process.memory_info()
    return {
        "pid": process.pid,
        "rssMiB": round(memory.rss / 1024 / 1024, 1),
        "privateMiB": round(getattr(memory, "private", memory.rss) / 1024 / 1024, 1),
        "torchAllocatedMiB": round(torch.cuda.memory_allocated() / 1024 / 1024, 1),
        "torchReservedMiB": round(torch.cuda.memory_reserved() / 1024 / 1024, 1),
    }


def prompt_for(model: Model, context: str, user: str) -> str:
    return model.default_chat_prompt(
        user,
        system_prompt=f"{SYSTEM}\nAUTHORITATIVE CONTEXT:\n{context}",
    )


def generate(
    generator: Generator,
    tokenizer: Tokenizer,
    prompt: str,
    name: str,
    repetition: int,
    max_new_tokens: int = 32,
) -> ProbeResult:
    input_ids = tokenizer.encode(prompt, encode_special_tokens=True)
    job = Job(
        input_ids=input_ids,
        max_new_tokens=max_new_tokens,
        sampler=TopPSampler(top_p=0.95, temperature=0.3, temperature_last=True),
        seed=1234 + repetition,
        stop_conditions=generator.model.config.eos_token_id_list,
        stop_on_loop=(12, 3),
    )
    started = time.perf_counter()
    generator.enqueue(job)
    first_token_at = None
    output = ""
    final = None
    while generator.num_remaining_jobs():
        for result in generator.iterate():
            if result["job"] is not job:
                continue
            if result["stage"] == "streaming":
                token_ids = result.get("token_ids")
                if first_token_at is None and token_ids is not None and token_ids.shape[-1] > 0:
                    first_token_at = time.perf_counter()
                output += result.get("text", "")
            if result.get("eos"):
                final = result
    finished = time.perf_counter()
    if first_token_at is None or final is None:
        raise RuntimeError(f"{name} completed without a first token/final result")
    generated = int(final.get("new_tokens", 0))
    generation_seconds = float(final.get("time_generate", 0.0))
    return ProbeResult(
        name=name,
        repetition=repetition,
        prompt_tokens=int(final.get("prompt_tokens", input_ids.shape[-1])),
        cached_tokens=int(final.get("cached_tokens", 0)),
        output_tokens=generated,
        ttft_ms=round((first_token_at - started) * 1000, 2),
        completion_ms=round((finished - started) * 1000, 2),
        prefill_ms=round(float(final.get("time_prefill", 0.0)) * 1000, 2),
        generation_ms=round(generation_seconds * 1000, 2),
        tokens_per_second=round(generated / generation_seconds, 2) if generation_seconds else -1,
        output=output.strip(),
    )


def cancel_and_recover(
    generator: Generator,
    model: Model,
    tokenizer: Tokenizer,
) -> tuple[dict, ProbeResult]:
    long_context = "authoritative forge observation " * 220
    prompt = prompt_for(model, long_context, "Summarize the observation in detail.")
    abort = threading.Event()
    with ThreadPoolExecutor(max_workers=1, thread_name_prefix="exl3-cancel") as executor:
        future = executor.submit(
            generator.generate,
            prompt,
            max_new_tokens=256,
            sampler=TopPSampler(top_p=0.95, temperature=0.3, temperature_last=True),
            seed=9090,
            encode_special_tokens=True,
            stop_conditions=model.config.eos_token_id_list,
            abort_event=abort,
            completion_only=True,
            stop_on_loop=(12, 3),
        )
        time.sleep(0.05)
        cancel_started = time.perf_counter()
        abort.set()
        outcome = future.result(timeout=10)
        drained_at = time.perf_counter()
    drained = generator.num_remaining_jobs() == 0
    recovery_prompt = prompt_for(model, "World: player nearby; no danger.", "Hello again, Mara.")
    recovery = generate(generator, tokenizer, recovery_prompt, "POST_CANCEL", 1)
    return (
        {
            "requestedAfterMillis": 50,
            "cancelToDrainedMillis": round((drained_at - cancel_started) * 1000, 2),
            "generatorDrained": drained,
            "abortedResultWasNone": outcome is None,
        },
        recovery,
    )


def average(values: list[ProbeResult], field: str) -> float:
    return round(sum(float(getattr(value, field)) for value in values) / len(values), 2)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("model_dir", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    report: dict = {
        "startedAtEpochMillis": int(time.time() * 1000),
        "platform": platform.platform(),
        "python": platform.python_version(),
        "torch": torch.__version__,
        "cuda": torch.version.cuda,
        "triton": triton.__version__,
        "modelDirectory": str(args.model_dir.resolve()),
        "beforeLoad": {"gpu": gpu_snapshot(), "process": process_snapshot()},
    }

    config = Config.from_directory(str(args.model_dir))
    model = Model.from_config(config)
    model.check_compat()
    cache = Cache(model, max_num_tokens=4096, max_batch_size=1)
    load_started = time.perf_counter()
    model.load(progressbar=True)
    torch.cuda.synchronize()
    report["coldLoadMillis"] = round((time.perf_counter() - load_started) * 1000, 2)
    report["afterLoad"] = {"gpu": gpu_snapshot(), "process": process_snapshot()}

    tokenizer = Tokenizer.from_config(config)
    generator = Generator(
        model=model,
        cache=cache,
        tokenizer=tokenizer,
        max_batch_size=1,
        max_chunk_size=2048,
    )
    prompts = {
        name: prompt_for(model, context, user)
        for name, context, user in PROBES
    }

    # First request includes any remaining graph/kernel warmup and is reported separately.
    cold_probe = generate(generator, tokenizer, prompts["FAST"], "COLD_FIRST_INFERENCE", 0)
    samples: list[ProbeResult] = []
    for repetition, name in ((1, "FAST"), (1, "GROUNDED"), (2, "FAST")):
        samples.append(generate(generator, tokenizer, prompts[name], name, repetition))

    cancellation, recovery = cancel_and_recover(generator, model, tokenizer)
    report.update(
        {
            "architecture": config.architecture,
            "coldFirstInference": asdict(cold_probe),
            "warmSamples": [asdict(sample) for sample in samples],
            "warmAverage": {
                "ttftMillis": average(samples, "ttft_ms"),
                "completionMillis": average(samples, "completion_ms"),
                "tokensPerSecond": average(samples, "tokens_per_second"),
            },
            "cancellation": cancellation,
            "postCancellationRequest": asdict(recovery),
            "afterBenchmark": {"gpu": gpu_snapshot(), "process": process_snapshot()},
        }
    )

    unload_started = time.perf_counter()
    model.unload()
    torch.cuda.empty_cache()
    report["unloadMillis"] = round((time.perf_counter() - unload_started) * 1000, 2)
    report["afterUnload"] = {"gpu": gpu_snapshot(), "process": process_snapshot()}
    report["finishedAtEpochMillis"] = int(time.time() * 1000)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report["warmAverage"], indent=2))
    print(f"REPORT={args.output.resolve()}")


if __name__ == "__main__":
    main()
