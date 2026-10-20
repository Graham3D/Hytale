package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.ui.hud.RpgHudCoordinator;

/** Presentation-only XP fixture; this command cannot award or persist XP. */
public final class RpgDevXpDisplayCommand extends AbstractPlayerCommand {
    private final RpgHudCoordinator hud;
    private final RequiredArg<String> value;
    public RpgDevXpDisplayCommand(RpgHudCoordinator hud) {
        super("xp-display", "Set a nonpersistent HUD XP percentage fixture, or clear it.");
        this.hud = hud;
        value = withRequiredArg("percent", "0..100 or clear", ArgTypes.STRING);
        setPermissionGroup(GameMode.Adventure);
    }
    @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                     PlayerRef playerRef, World world) {
        try {
            String requested = context.get(value);
            if (requested.equalsIgnoreCase("clear")) {
                hud.setXpFixture(playerRef.getUuid(), null);
                context.sendMessage(Message.raw("Cleared the presentation-only XP fixture."));
            } else {
                double percent = Double.parseDouble(requested);
                if (!Double.isFinite(percent) || percent < 0.0 || percent > 100.0)
                    throw new IllegalArgumentException("XP display percent must be 0..100.");
                hud.setXpFixture(playerRef.getUuid(), percent);
                context.sendMessage(Message.raw("Set presentation-only XP display to " + percent + "%. No XP was awarded."));
            }
        } catch (RuntimeException error) { context.sendMessage(Message.raw(error.getMessage())); }
    }
}
