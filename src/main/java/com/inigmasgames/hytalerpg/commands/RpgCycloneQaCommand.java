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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.UUID;

/** Self-only operator grant while the proposed Scarak source is not connected-certified. */
public final class RpgCycloneQaCommand extends AbstractPlayerCommand {
    @SuppressFBWarnings(value="EI_EXPOSE_REP2",justification="The command intentionally shares the injected player-state authority with other RPG commands.")
    private final RpgLoadoutService loadouts;

    public RpgCycloneQaCommand(RpgLoadoutService loadouts){
        super("cyclone", "Learn Cyclone Armor for connected QA; equip it in a skill slot afterward.");
        this.loadouts=loadouts;
        requirePermission(RpgGearCommand.AUTHOR_PERMISSION);
    }

    @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> actor,
                                     PlayerRef player,World world){
        try{
            var view=loadouts.getPresentationView(player.getUuid());
            if(view.state().learnedSkills.contains("spirit_shield")){
                context.sendMessage(Message.raw("Cyclone Armor is already learned. Use /rpg equip skill01 Cyclone Armor."));
                return;
            }
            var result=loadouts.mutateProgress(player.getUuid(),view.state().revision,
                    "qa-cyclone/"+UUID.randomUUID(),state->state.learnedSkills.add("spirit_shield"));
            context.sendMessage(Message.raw(result.success()
                    ?"Cyclone Armor learned for QA. Use /rpg equip skill01 Cyclone Armor."
                    :"Cyclone Armor QA grant: "+result.message()));
        }catch(RuntimeException error){context.sendMessage(Message.raw("Cyclone Armor QA grant: "+error.getMessage()));}
    }
}
