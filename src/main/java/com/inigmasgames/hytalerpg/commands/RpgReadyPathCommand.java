package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.phase00.BuildIdentity;
import com.inigmasgames.hytalerpg.progress.RpgLoadoutService;
import com.inigmasgames.hywind.readypath.ReadyPathProbe;

/** Inspection only; never loads a player, compiles content or changes authority. */
public final class RpgReadyPathCommand extends AbstractPlayerCommand {
    public static final String PERMISSION = "inigmasgames.rpg.readypath";
    private final RpgLoadoutService loadouts;
    private java.util.function.Supplier<java.util.Map<String,Object>> preparation=()->java.util.Map.of("status","UNAVAILABLE");
    public void configurePreparation(java.util.function.Supplier<java.util.Map<String,Object>> value){preparation=java.util.Objects.requireNonNull(value);}

    public RpgReadyPathCommand(RpgLoadoutService loadouts) {
        super("readypath", "Operator: inspect ReadyPath observations and persistence work.");
        this.loadouts = loadouts;
        requirePermission(PERMISSION);
    }

    @Override protected void execute(CommandContext context, Store<EntityStore> store,
            Ref<EntityStore> actor, PlayerRef player, World world) {
        context.sendMessage(Message.raw("ReadyPath " + BuildIdentity.REVISION + ": "
                + ReadyPathProbe.inspect(player.getUuid())));
        context.sendMessage(Message.raw("Persistence hydrated=" + loadouts.ready(player.getUuid())
                + " (not full gameplay readiness); work=" + loadouts.persistenceMetrics()));
        context.sendMessage(Message.raw("Entry preparation="+preparation.get()+"; shared catalog revision="
                +com.inigmasgames.hytalerpg.content.RpgCatalog.prepared().revision()));
        context.sendMessage(Message.raw("Stage 2 admission pipeline incomplete; connected acceptance OPEN. "
                + "Disabled/missing telemetry stays UNKNOWN. No state was changed."));
    }
}
