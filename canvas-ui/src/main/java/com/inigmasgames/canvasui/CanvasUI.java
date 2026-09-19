package com.inigmasgames.canvasui;

import com.inigmasgames.canvasui.runtime.CanvasService;
import com.inigmasgames.canvasui.api.editor.CursorCanvasEditor;
import com.inigmasgames.canvasui.api.editor.CursorEditorOpenResult;
import com.inigmasgames.canvasui.runtime.cursor.CursorHudProbeService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Stable entry point used by consuming mods after the CanvasUI plugin is enabled. */
public final class CanvasUI {
    private static final Properties BUILD = loadBuild();
    public static final String REVISION = required("canvasui.revision");
    public static final String VERSION = required("canvasui.version");
    public static final String HYTALE_VERSION = required("hytale.version");
    private static volatile CanvasService service;
    private static volatile CursorHudProbeService cursorEditors;

    private CanvasUI() { }

    public static CanvasService service() {
        CanvasService current = service;
        if (current == null) throw new IllegalStateException("CanvasUI plugin is not enabled");
        return current;
    }

    public static CursorEditorOpenResult openCursorEditor(CursorCanvasEditor editor, Player player,
                                                           PlayerRef playerRef, World world,
                                                           Store<EntityStore> store, Ref<EntityStore> ref) {
        CursorHudProbeService current = cursorEditors;
        if (current == null) return new CursorEditorOpenResult(false, "CanvasUI cursor editor is not enabled.", null);
        return current.openEditor(editor, player, playerRef, world, store, ref);
    }

    public static void install(CanvasService value, CursorHudProbeService editors) {
        service = value;
        cursorEditors = editors;
    }
    public static void uninstall(CanvasService value, CursorHudProbeService editors) {
        if (service == value) service = null;
        if (cursorEditors == editors) cursorEditors = null;
    }

    private static Properties loadBuild() {
        Properties values = new Properties();
        try (InputStream input = CanvasUI.class.getResourceAsStream("/canvasui-build.properties")) {
            if (input == null) throw new IllegalStateException("Missing canvasui-build.properties");
            values.load(input);
            return values;
        } catch (IOException error) {
            throw new IllegalStateException("Unable to load CanvasUI build identity", error);
        }
    }

    private static String required(String key) {
        String value = BUILD.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing CanvasUI build value: " + key);
        return value;
    }
}
