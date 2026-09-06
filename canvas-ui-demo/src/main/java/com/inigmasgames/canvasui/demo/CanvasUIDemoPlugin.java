package com.inigmasgames.canvasui.demo;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.inigmasgames.canvasui.CanvasUI;

import javax.annotation.Nonnull;
import java.nio.file.Path;

public final class CanvasUIDemoPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public CanvasUIDemoPlugin(@Nonnull JavaPluginInit init) { super(init); }

    @Override
    protected void setup() {
        Path layouts = getDataDirectory().resolve("layouts");
        getCommandRegistry().registerCommand(new CanvasDemoCommand(layouts, false));
        getCommandRegistry().registerCommand(new CanvasDemoCommand(layouts, true));
        LOGGER.atInfo().log("CANVASUI_DEMO_SETUP revision=%s commands=/canvasui-demo,/canvasui-topology-proof",
                CanvasUI.REVISION);
    }
}
