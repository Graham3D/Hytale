# OrbisLLM Phase 1 sidecar

This is the isolated, experimental llama.cpp provider described by
`Orbis LLM Runtime - llama.cpp Sidecar Integration.docx`.

- Pinned llama.cpp: `b10701`, commit `cc231cb0da565440cf6a3e5b55dfeba477972cb6`.
- Transport: current-user-only Windows named pipe, protocol `1.0`, bounded binary frames with
  UTF-8 JSON payloads and request/process generations.
- Model/context/request lifetimes are independent.
- One decode is active at a time; cancellation is acknowledged only after the decode loop stops.
- Nemotron chat rendering is pinned to the official template revision and preserves the
  `enable_thinking` distinction that libllama's basic chat helper cannot express.
- Structured output uses a fixed, prevalidated NPC-decision GBNF syntax guard and remains subject
  to the existing authoritative Java schema/action validation.

The worker is not a public server and has no network listener. Ollama remains the default provider
and production rollback throughout Phase 1.
