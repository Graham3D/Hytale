# Topology integration proof

The development demo command `/canvasui-topology-proof` creates a generic graph
with six producer nodes, four destination nodes, and two three-port routers. It
contains no skills, passives, progression, or gameplay authority.

Its consumer-side policy permits:

```text
producer -> destination
producer -> router
router   -> router
router   -> destination
```

It rejects producer-to-producer and destination-to-destination connections with
`REJECT_TYPE`. This proves that a future consumer can express source-like,
destination-like, and routing topology without CanvasUI knowing those meanings.
Router-to-router behavior is ordinary port-based graph behavior, not a special
library node class.

Run `/canvasui-demo` for the smaller six-node Source/Transform/Router/Output
board. Both demos use only `CanvasUI`, public `api` types, public `runtime`
session/service types, and a consumer persistence adapter—never CanvasUI's
`internal` package.
