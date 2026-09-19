import base64
import importlib.util
import json
import tempfile
import threading
import urllib.error
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WORKER_PATH = ROOT / "main" / "resources" / "tools" / "immersive_voice_worker.py"
spec = importlib.util.spec_from_file_location("immersive_voice_worker", WORKER_PATH)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class FakeWorker:
    worker_role = "combined"
    tts_device = "test"
    streaming_stt_provider = "MOONSHINE"
    tts_enabled = True
    stt_enabled = True

    def __init__(self):
        self.streaming_stt_sessions = {}

    def transcribe(self, request):
        assert request["frames"] == [base64.b64encode(b"opus").decode("ascii")]
        return {"text": "heard", "decodeMs": 1, "whisperMs": 2, "language": "en"}

    def start_streaming_stt(self, request):
        self.streaming_stt_sessions[request["streamId"]] = FakeSession()
        return {"available": True, "provider": "MOONSHINE"}

    def append_streaming_stt(self, request):
        assert request["streamId"] in self.streaming_stt_sessions
        return {"partial": "hello"}

    def finish_streaming_stt(self, request):
        self.streaming_stt_sessions.pop(request["streamId"], None)
        return {"text": "hello", "decodeMs": 1, "whisperMs": 2, "language": "en"}

    def synthesize(self, request):
        assert Path(request["reference"]).is_file()
        return {
            "frames": [base64.b64encode(b"opus").decode("ascii")],
            "sourceRate": 48000,
            "ttsMs": 3,
            "encodeMs": 1,
            "conditioningMs": 1,
            "conditionalsCached": False,
            "device": "test",
        }


class FakeSession:
    def close(self):
        pass


def request(base, path, body=None):
    data = None if body is None else json.dumps(body).encode("utf-8")
    method = "GET" if body is None else "POST"
    value = urllib.request.Request(base + path, data=data, method=method,
                                   headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(value, timeout=2) as response:
        return response.status, json.loads(response.read())


def main():
    with tempfile.TemporaryDirectory(prefix="r036-worker-") as directory:
        server = module.RemoteVoiceServer(FakeWorker(), "127.0.0.1", 0, directory)
        thread = threading.Thread(target=server.httpd.serve_forever, daemon=True)
        thread.start()
        base = f"http://127.0.0.1:{server.httpd.server_port}"
        try:
            assert request(base, "/health")[1]["status"] == "healthy"
            transcript = request(base, "/v1/stt/transcribe", {
                "requestId": "stt-1", "sessionId": "speech-1",
                "opusFrames": [base64.b64encode(b"opus").decode("ascii")],
            })[1]
            assert transcript["text"] == "heard" and transcript["inferenceMs"] == 2

            wav = b"RIFF" + (b"\x00" * 4) + b"WAVE" + (b"\x00" * 40)
            speech = request(base, "/v1/tts/synthesize", {
                "requestId": "tts-1", "responseId": "response-1",
                "npcStableId": "npc-1", "text": "hello",
                "referenceWavBase64": base64.b64encode(wav).decode("ascii"),
            })[1]
            assert speech["inferenceMs"] == 3 and len(speech["frames"]) == 1

            assert request(base, "/v1/cancel", {"requestId": "cancel-me"})[1]["cancelled"]
            try:
                request(base, "/v1/stt/transcribe", {
                    "requestId": "cancel-me", "opusFrames": []})
                raise AssertionError("cancelled request was accepted")
            except urllib.error.HTTPError as failure:
                assert failure.code == 409
        finally:
            server.httpd.shutdown()
            server.httpd.server_close()
            thread.join(timeout=2)
    print("R036 Python remote worker transport test passed.")


if __name__ == "__main__":
    main()
