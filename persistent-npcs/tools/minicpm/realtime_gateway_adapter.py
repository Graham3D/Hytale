"""Expose OpenBMB's public audio realtime protocol over Comni's Windows runtime.

The v1.0.22 Windows package has the maintained quantized C++ compute path but
predates OpenBMB's public /v1/realtime envelope. This isolated control-plane
adapter translates only documented audio-session lifecycle/events. It does not
pretend that llama-server's private /v1/stream endpoints are the public API.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import time
import uuid
from typing import Any

import httpx
import uvicorn
import websockets
from fastapi import FastAPI, WebSocket, WebSocketDisconnect


def create_app(packaged_gateway: str) -> FastAPI:
    app = FastAPI(title="ImmersiveNPCs MiniCPM Realtime Adapter")

    @app.get("/health")
    async def health() -> dict[str, Any]:
        upstream = False
        detail = ""
        try:
            async with httpx.AsyncClient(proxy=None, trust_env=False, timeout=3.0) as client:
                response = await client.get(f"{packaged_gateway}/health")
                upstream = response.status_code == 200
                detail = response.text[:500]
        except Exception as exc:
            detail = str(exc)
        return {
            "healthy": upstream,
            "public_protocol": "/v1/realtime?mode=audio",
            "upstream": packaged_gateway,
            "upstream_detail": detail,
        }

    @app.websocket("/v1/realtime")
    async def realtime(client: WebSocket) -> None:
        mode = client.query_params.get("mode", "")
        await client.accept()
        if mode != "audio":
            await client.send_json({"type": "error", "error": {"code": "unsupported_mode", "message": "Only mode=audio is enabled."}})
            await client.close(code=1003)
            return

        session_id = f"orbis_{uuid.uuid4().hex}"
        packaged_ws = packaged_gateway.replace("http://", "ws://").replace("https://", "wss://")
        packaged_ws = f"{packaged_ws}/ws/duplex/{session_id}"
        response_number = 0
        active_response_id: str | None = None
        current_input_id: str | None = None
        closed = False

        async def send(event: dict[str, Any]) -> None:
            if not closed:
                await client.send_json(event)

        try:
            async with websockets.connect(packaged_ws, proxy=None, max_size=128 * 1024 * 1024) as upstream:
                async def client_to_upstream() -> None:
                    nonlocal current_input_id, closed
                    while True:
                        event = json.loads(await client.receive_text())
                        event_type = event.get("type")
                        if event_type == "session.init":
                            payload = event.get("payload") or {}
                            voice = payload.get("voice") or {}
                            prepare: dict[str, Any] = {
                                "type": "prepare",
                                "system_prompt": payload.get("system_prompt", "Streaming Duplex Conversation."),
                                "config": payload.get("config") or {"length_penalty": 1.05},
                            }
                            if voice.get("ref_audio_base64"):
                                prepare["ref_audio_base64"] = voice["ref_audio_base64"]
                            if voice.get("tts_ref_audio_base64"):
                                prepare["tts_ref_audio_base64"] = voice["tts_ref_audio_base64"]
                            await upstream.send(json.dumps(prepare))
                        elif event_type == "input.append":
                            input_payload = event.get("input") or {}
                            current_input_id = event.get("input_id") or f"input_{uuid.uuid4().hex}"
                            await upstream.send(json.dumps({
                                "type": "audio_chunk",
                                "audio_base64": input_payload.get("audio", ""),
                                "force_listen": bool(input_payload.get("force_listen") or (input_payload.get("hints") or {}).get("force_listen")),
                            }))
                        elif event_type == "session.close":
                            await upstream.send(json.dumps({"type": "stop"}))
                            closed = True
                            await client.send_json({"type": "session.closed", "session_id": session_id, "reason": event.get("reason", "client_close")})
                            return
                        else:
                            await send({"type": "error", "error": {"code": "unsupported_event", "message": f"Unsupported event: {event_type}"}})

                async def upstream_to_client() -> None:
                    nonlocal response_number, active_response_id, closed
                    async for raw in upstream:
                        event = json.loads(raw)
                        event_type = event.get("type")
                        if event_type == "queued":
                            await send({"type": "session.queued", **{k: event.get(k) for k in ("position", "estimated_wait_s", "ticket_id", "queue_length") if k in event}})
                        elif event_type == "queue_update":
                            await send({"type": "session.queue_update", **{k: event.get(k) for k in ("position", "estimated_wait_s", "queue_length") if k in event}})
                        elif event_type == "queue_done":
                            await send({"type": "session.queue_done", "session_id": session_id})
                        elif event_type == "prepared":
                            await send({"type": "session.created", "session_id": session_id, "created_at": time.time()})
                        elif event_type == "result":
                            if event.get("is_listen"):
                                active_response_id = None
                                await send({
                                    "type": "response.output.delta", "kind": "listen",
                                    "session_id": session_id, "input_id": current_input_id,
                                    "metrics": _metrics(event),
                                })
                                continue
                            if active_response_id is None:
                                response_number += 1
                                active_response_id = f"{session_id}_response_{response_number}"
                            common = {
                                "type": "response.output.delta", "session_id": session_id,
                                "response_id": active_response_id, "input_id": current_input_id,
                                "metrics": _metrics(event),
                            }
                            if event.get("text"):
                                await send({**common, "kind": "text", "text": event["text"]})
                            if event.get("audio_data"):
                                await send({**common, "kind": "audio", "audio": event["audio_data"], "sample_rate": 24000, "format": "float32le"})
                        elif event_type == "audio_only" and event.get("audio_data"):
                            if active_response_id is None:
                                response_number += 1
                                active_response_id = f"{session_id}_response_{response_number}"
                            await send({
                                "type": "response.output.delta", "kind": "audio",
                                "audio": event["audio_data"], "sample_rate": 24000,
                                "format": "float32le", "session_id": session_id,
                                "response_id": active_response_id, "input_id": current_input_id,
                            })
                        elif event_type in ("stopped", "timeout"):
                            closed = True
                            await client.send_json({"type": "session.closed", "session_id": session_id, "reason": event.get("reason", event_type)})
                            return
                        elif event_type == "error":
                            await send({"type": "error", "session_id": session_id, "error": {"code": "upstream_error", "message": str(event.get("error", "unknown upstream error"))}})

                tasks = [asyncio.create_task(client_to_upstream()), asyncio.create_task(upstream_to_client())]
                done, pending = await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
                for task in pending:
                    task.cancel()
                for task in done:
                    task.result()
        except WebSocketDisconnect:
            pass
        except Exception as exc:
            try:
                await send({"type": "error", "session_id": session_id, "error": {"code": "adapter_failure", "message": str(exc)}})
            except Exception:
                pass
        finally:
            if not closed:
                try:
                    await client.close()
                except Exception:
                    pass

    return app


def _metrics(event: dict[str, Any]) -> dict[str, Any]:
    return {key: event[key] for key in ("cost_all_ms", "wall_clock_ms", "kv_cache_length") if key in event}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8006)
    parser.add_argument("--packaged-gateway", default="http://127.0.0.1:8005")
    args = parser.parse_args()
    uvicorn.run(create_app(args.packaged_gateway), host=args.host, port=args.port, log_level="info")


if __name__ == "__main__":
    main()

