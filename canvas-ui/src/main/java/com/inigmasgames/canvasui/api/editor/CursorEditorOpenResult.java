package com.inigmasgames.canvasui.api.editor;

import java.nio.file.Path;

public record CursorEditorOpenResult(boolean opened, String message, Path tracePath) { }
