package com.inigmasgames.canvasui.demo;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.canvasui.CanvasUI;
import com.inigmasgames.canvasui.api.Canvas;
import com.inigmasgames.canvasui.api.CanvasDefinition;

import java.nio.file.Path;

public final class CanvasDemoCommand extends AbstractPlayerCommand {
    private final Path layouts;
    private final boolean topologyProof;

    public CanvasDemoCommand(Path layouts, boolean topologyProof) {
        super(topologyProof ? "canvasui-topology-proof" : "canvasui-demo",
                topologyProof ? "Open the generic 12-node routing topology proof." : "Open the generic six-node CanvasUI demo.");
        this.layouts = layouts; this.topologyProof = topologyProof;
        setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                           PlayerRef playerRef, World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) { context.sendMessage(Message.raw("CanvasUI demo failed: Player unavailable.")); return; }
        String id = (topologyProof ? "topology-proof-" : "generic-demo-") + playerRef.getUuid();
        FileCanvasPersistenceAdapter persistence = new FileCanvasPersistenceAdapter(layouts.resolve(id + ".properties"));
        CanvasDefinition definition = topologyProof
                ? DemoDefinitions.topologyProof(id, persistence) : DemoDefinitions.generic(id, persistence);
        CanvasUI.service().open(player, definition, canvas -> {
            if (canvas.nodes().isEmpty()) {
                if (topologyProof) DemoDefinitions.seedTopologyProof(canvas);
                else DemoDefinitions.seedGeneric(canvas);
            }
        });
        context.sendMessage(Message.raw("CanvasUI " + CanvasUI.REVISION + " opened. Existing saved layout is restored when present."));
    }
}
