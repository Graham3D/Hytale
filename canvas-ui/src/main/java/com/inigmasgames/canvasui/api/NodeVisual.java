package com.inigmasgames.canvasui.api;

/** Renderer-neutral node appearance consumed by the current Hytale backend. */
public record NodeVisual(String title, String subtitle, String backgroundColor,
                         String borderColor, String textColor) {
    public static NodeVisual simple(String title, String color) {
        return new NodeVisual(title, "", color, "#78c6d0", "#eef6ff");
    }
}
