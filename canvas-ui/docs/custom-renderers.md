# Custom renderers

`NodeRenderer` receives the immutable node and visual state and returns a
renderer-neutral `NodeVisual`:

```java
NodeRenderer renderer = context -> {
    String color = switch (context.state()) {
        case SELECTED -> "#586f2aee";
        case HOVERED -> "#375674ee";
        case DISABLED -> "#30343aaa";
        case INVALID_CONNECTION_TARGET -> "#7d2f35ee";
        default -> "#28476aee";
    };
    return new NodeVisual("Processor", "custom", color,
        "#78c6d0", "#eef6ff");
};
```

`EdgeRenderer` converts source/target screen points and an `EdgeStyle` into
segments. Consumers can supply geometry renderers without touching graph state.
The current Hytale backend accepts safe hex colors and axis-aligned segments.
Sprite/material is retained in `EdgeStyle` for future backends; current custom
sprite rendering is experimental because the installed public UI API does not
expose a general vector/path primitive.
