package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.progress.RpgLoadoutService;
import java.util.UUID;

/** Operator-only acquisition until Teleport has an approved enemy source. */
public final class RpgTeleportQaCommand extends AbstractPlayerCommand {
    private final RpgLoadoutService loadouts;

    public RpgTeleportQaCommand(RpgLoadoutService loadouts) {
        super("teleport", "Learn Teleport for connected QA; equip it in a skill slot afterward.");
        this.loadouts=loadouts;
        requirePermission(RpgGearCommand.AUTHOR_PERMISSION);
    }

    @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> actor,
                                     PlayerRef player,World world) {
        try {
            var view=loadouts.getPresentationView(player.getUuid());
            if(view.state().learnedSkills.contains("teleport")){
                context.sendMessage(Message.raw("Teleport is already learned. Use /rpg equip skill01 Teleport."));
                return;
            }
            var result=loadouts.mutateProgress(player.getUuid(),view.state().revision,
                    "qa-teleport/"+UUID.randomUUID(),state->state.learnedSkills.add("teleport"));
            context.sendMessage(Message.raw(result.success()
                    ?"Teleport learned for QA. Use /rpg equip skill01 Teleport."
                    :"Teleport QA grant: "+result.message()));
        }catch(RuntimeException error){context.sendMessage(Message.raw("Teleport QA grant: "+error.getMessage()));}
    }
}
