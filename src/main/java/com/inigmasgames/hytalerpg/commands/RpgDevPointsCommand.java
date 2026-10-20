package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.progress.AttributeAllocationService;
import com.inigmasgames.hytalerpg.ui.trace.RpgUiTraceService;

import java.util.Map;
import java.util.UUID;

public final class RpgDevPointsCommand extends AbstractCommandCollection {
    public RpgDevPointsCommand(AttributeAllocationService allocation, RpgUiTraceService trace) {
        super("points", "Development-only attribute point fixtures.");
        addSubCommand(new Grant(allocation, trace));
    }

    private static final class Grant extends AbstractPlayerCommand {
        private final AttributeAllocationService allocation;
        private final RpgUiTraceService trace;
        private final RequiredArg<String> amount;
        private Grant(AttributeAllocationService allocation, RpgUiTraceService trace) {
            super("grant", "Grant pending/unspent development attribute points without XP.");
            this.allocation = allocation; this.trace = trace;
            amount = withRequiredArg("amount", "positive development point count", ArgTypes.STRING);
            setPermissionGroup(GameMode.Adventure);
        }
        @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                         PlayerRef playerRef, World world) {
            String correlation = UUID.randomUUID().toString().substring(0, 12);
            try {
                int points = Integer.parseInt(context.get(amount));
                var result = allocation.grantDevelopmentPoints(playerRef.getUuid(), points, correlation);
                trace.trace(playerRef.getUuid(), result.success() ? "DEV_POINTS_GRANTED" : "DEV_POINTS_REJECTED",
                        correlation, Map.of("points", points, "result", result.code().name(), "revision", result.revision()));
                context.sendMessage(Message.raw(result.success()
                        ? "Granted " + points + " pending/unspent attribute points. RPG revision " + result.revision() + '.'
                        : result.code() + ": " + result.message()));
            } catch (RuntimeException error) { context.sendMessage(Message.raw(error.getMessage())); }
        }
    }
}
