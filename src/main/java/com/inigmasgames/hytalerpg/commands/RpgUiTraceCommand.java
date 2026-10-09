package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.ui.trace.UiInteractionTrace;

/** Explicit developer-only control for per-player Inventory/CanvasUI diagnostics. */
public final class RpgUiTraceCommand extends AbstractCommandCollection {
    public static final String PERMISSION = "inigmasgames.rpg.uitrace";

    public RpgUiTraceCommand(UiInteractionTrace trace) {
        super("uitrace", "Opt-in Inventory and CanvasUI interaction trace.");
        requirePermission(PERMISSION);
        addSubCommand(new AbstractPlayerCommand("on", "Start a bounded personal UI trace.") {
            @Override protected void execute(CommandContext context, Store<EntityStore> store,
                                             Ref<EntityStore> ref, PlayerRef player, World world) {
                context.sendMessage(Message.raw(trace.on(player.getUuid())));
            }
        });
        addSubCommand(new AbstractPlayerCommand("off", "Stop and save your UI trace.") {
            @Override protected void execute(CommandContext context, Store<EntityStore> store,
                                             Ref<EntityStore> ref, PlayerRef player, World world) {
                context.sendMessage(Message.raw(trace.off(player.getUuid())));
            }
        });
        addSubCommand(new AbstractPlayerCommand("mark", "Mark the current UI trace.") {
            final OptionalArg<String> label = withOptionalArg("label", "Optional reproduction label", ArgTypes.GREEDY_STRING);
            @Override protected void execute(CommandContext context, Store<EntityStore> store,
                                             Ref<EntityStore> ref, PlayerRef player, World world) {
                context.sendMessage(Message.raw(trace.mark(player.getUuid(),
                        context.provided(label) ? context.get(label) : "")));
            }
        });
    }
}
