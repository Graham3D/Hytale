package com.inigmasgames.canvasui.api;

/** Renderer-neutral node appearance consumed by the current Hytale backend. */
public record NodeVisual(String title, String subtitle, String backgroundColor,
                         String borderColor, String textColor, String searchName,
                         String searchDescription, java.util.List<String> searchTags) {
    public NodeVisual {
        searchName = searchName == null || searchName.isBlank() ? title : searchName;
        searchDescription = searchDescription == null ? "" : searchDescription;
        searchTags = java.util.List.copyOf(searchTags == null ? java.util.List.of() : searchTags);
    }
    public NodeVisual(String title, String subtitle, String backgroundColor,
                      String borderColor, String textColor) {
        this(title, subtitle, backgroundColor, borderColor, textColor, title, subtitle, java.util.List.of());
    }
    public static NodeVisual simple(String title, String color) {
        return new NodeVisual(title, "", color, "#78c6d0", "#eef6ff");
    }
    public NodeVisual withSearchMetadata(String name, String description, java.util.List<String> tags) {
        return new NodeVisual(title, subtitle, backgroundColor, borderColor, textColor, name, description, tags);
    }
}
