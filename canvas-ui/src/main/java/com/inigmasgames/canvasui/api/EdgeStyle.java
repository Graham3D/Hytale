package com.inigmasgames.canvasui.api;

public record EdgeStyle(int thickness, String color, String semanticType, String material, String state) {
    public EdgeStyle {
        if (thickness < 1) throw new IllegalArgumentException("thickness must be positive");
        if (color == null || semanticType == null) throw new IllegalArgumentException("color/type required");
        material = material == null ? "" : material;
        state = state == null ? "normal" : state;
    }
    public EdgeStyle(int thickness, String color, String semanticType, String material) {
        this(thickness, color, semanticType, material, "normal");
    }
    public static EdgeStyle standard(String semanticType) { return new EdgeStyle(3, "#78c6d0", semanticType, "", "normal"); }
}
