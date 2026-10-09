package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.inigmasgames.hytalerpg.spawning.NativeWorldSpawnDensity;
import com.inigmasgames.hytalerpg.spawning.WorldSpawnDensitySettings;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Admin-only native environmental population control. */
public final class RpgSpawnsCommand extends AbstractAsyncCommand {
    private final NativeWorldSpawnDensity density;
    private final RequiredArg<String> action;

    public RpgSpawnsCommand(NativeWorldSpawnDensity density) {
        super("spawns", "Set, reset, or inspect native environmental NPC population density.");
        this.density = density;
        action = withRequiredArg("multiplier|status|reset", "0.25..8.0, status, or reset", ArgTypes.STRING);
        requirePermission("inigmasgames.rpg.spawns");
    }

    @Override protected CompletableFuture<Void> executeAsync(CommandContext context) {
        String input = context.get(action).trim();
        if (input.equalsIgnoreCase("status")) return density.status().thenAccept(snapshots -> {
            context.sendMessage(Message.raw(String.format(Locale.ROOT,
                    "World spawn density %.2fx (persisted); native default cap %d, effective default cap %d; loaded spawning worlds: %d.",
                    density.multiplier(), density.defaultBaselineCap(),
                    WorldSpawnDensitySettings.scaledCap(density.defaultBaselineCap(), density.multiplier()), snapshots.size())));
            for (var snapshot : snapshots) context.sendMessage(Message.raw(String.format(Locale.ROOT,
                    "%s: environmental NPCs %d, target %.1f (native %.1f), cap %d (native %d), active jobs %d.",
                    snapshot.world(), snapshot.actual(), snapshot.effectiveTarget(), snapshot.nativeTarget(),
                    snapshot.effectiveCap(), snapshot.baselineCap(), snapshot.jobs())));
        });
        double value;
        if (input.equalsIgnoreCase("reset")) value = 1.0;
        else {
            try { value = Double.parseDouble(input); WorldSpawnDensitySettings.validate(value); }
            catch (IllegalArgumentException error) {
                context.sendMessage(Message.raw("Spawn density must be a number from 0.25 to 8.0, status, or reset."));
                return CompletableFuture.completedFuture(null);
            }
        }
        try{return density.set(value).handle((ignored,error)->{
            if(error==null)context.sendMessage(Message.raw(String.format(Locale.ROOT,
                    "World spawn density set to %.2fx in world-config.json.", value)));
            else context.sendMessage(Message.raw("Spawn density change failed: "+error.getMessage()));
            return null;
        });}catch(RuntimeException error){
            context.sendMessage(Message.raw("Spawn density change rejected: "+error.getMessage()));
            return CompletableFuture.completedFuture(null);
        }
    }
}
