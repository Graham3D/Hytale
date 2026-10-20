# CanvasUI R002 real-client verification

Target: Hytale `0.7.0-pre.1`; source commit
`01caefed8ef7d9772be2b1c148c00d793d66df6d`. The library and demo jars are
installed only in the isolated pre-release `RPG` save. Every screenshot must
show `R002` in the CanvasUI header.

| # | Action | Pass evidence | Result |
|---:|---|---|---|
| 1 | Open `RPG`; run `/canvasui-demo` | Page opens with Source A/B, Transform A/B, Router, Output, five attached connections, and R002. | PENDING |
| 2 | Escape; reopen 10 times | Every page closes/opens safely; balanced `CANVASUI_OPEN`/`CANVASUI_CLOSE`; no stale session/error. | PENDING |
| 3 | Click nodes/background | None/one selection works; hover and selected styling are visible. | PENDING |
| 4 | Left-drag a node from an off-center grab point | Four-unit threshold prevents click jitter; node preserves grab offset and follows smoothly. | PENDING |
| 5 | Middle-drag empty background | Viewport pans freely in X/Y; node canvas coordinates do not change. | PENDING |
| 6 | Drag again after panning | Coordinate transform remains correct; node does not jump. | PENDING |
| 7 | Observe connected node while dragging and panning | Every attached three-segment connector follows both endpoints. | PENDING |
| 8 | Drag Source A output port to Transform B input | Preview follows the pointer, target highlights, release creates a stable new edge. | PENDING |
| 9 | Drag Transform A output to its own input | Target highlights invalid; release shows `REJECTED`; no edge is silently created. | PENDING |
| 10 | Move and pan; Escape; reopen `/canvasui-demo` | Nodes, edges, metadata, selection, and viewport reconstruct from the saved snapshot. | PENDING |
| 11 | Run `/canvasui-topology-proof` | Six Source nodes, four Destination nodes, two three-port Routers, and seeded connections render. | PENDING |
| 12 | Inspect Router A → Router B | Router-to-router connection renders and follows either router when moved. | PENDING |
| 13 | Interactively try allowed and rejected topology-proof connections | Consumer policy permits producer→destination/router and router→router/destination; rejects producer→producer and destination→destination. | PENDING |
| 14 | Two clients open different demos simultaneously | Movement, viewport, selection, preview, persistence, and closure remain isolated by player. | PENDING |
| 15 | Disconnect/world transition/plugin shutdown during drag and preview | Session closes idempotently; no retained session, preview, listener, or exception remains. | PENDING |
| 16 | Close the final page and inspect newest save log | `CANVASUI_CLOSE` reports pointer/UI rates, rebuilds, command count, average/peak server processing latency. | PENDING |

Save screenshots and the newest `RPG/logs` file under
`evidence/canvas-ui/R002/client/01caefe-0.7.0-pre.1/`. True client presentation
latency is not exposed; record visible smoothness/stutter separately from the
server processing measurements.

Stop at the first failure. Preserve the page screenshot and log before retrying
or changing code.
