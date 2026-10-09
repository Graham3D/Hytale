package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.ui.inventory.NativeInventoryDropTraceProbe;

/** One-shot, read-only native inventory packet and drop event observation. */
public final class RpgNativeDropTraceCommand extends AbstractPlayerCommand {
    private final NativeInventoryDropTraceProbe probe;
    public RpgNativeDropTraceCommand(NativeInventoryDropTraceProbe probe) {
        super("nativedroptrace", "Trace one vanilla Inventory outside-window drop.");
        this.probe = probe;
        setPermissionGroup(GameMode.Adventure);
    }
    @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                     PlayerRef player, World world) {
        context.sendMessage(Message.raw(probe.arm(player, world)));
    }
}
