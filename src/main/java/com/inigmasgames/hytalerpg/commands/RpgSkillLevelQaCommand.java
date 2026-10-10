package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.content.CatalogResolution;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.SkillDefinition;
import com.inigmasgames.hytalerpg.progress.RpgLoadoutService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.UUID;

/** Operator-only, self-targeted base skill rank fixture. */
public final class RpgSkillLevelQaCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> skill;
    private final RequiredArg<String> level;
    private final RpgCatalog catalog;
    @SuppressFBWarnings(value="EI_EXPOSE_REP2",justification="The command intentionally shares the single injected player-state authority with other RPG commands.")
    private final RpgLoadoutService loadouts;

    public RpgSkillLevelQaCommand(RpgCatalog catalog,RpgLoadoutService loadouts){
        super("skilllevel","Operator: set one learned skill's base level from 1 to 20 for QA.");
        this.catalog=catalog;this.loadouts=loadouts;
        skill=withRequiredArg("skill","skill ID or one-word alias",ArgTypes.STRING);
        level=withRequiredArg("level","1..20",ArgTypes.STRING);
        requirePermission(RpgLevelCommand.PERMISSION);
    }

    @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> actor,
                                     PlayerRef player,World world){
        try{
            CatalogResolution<SkillDefinition> resolved=catalog.resolveSkill(context.get(skill));
            if(resolved.status()!=CatalogResolution.Status.RESOLVED){
                context.sendMessage(Message.raw(resolved.message()));return;
            }
            int target=Integer.parseInt(context.get(level));
            String id=resolved.value().id().value();
            var result=loadouts.setOperatorSkillRank(player.getUuid(),id,target,"qa-skill-level/"+UUID.randomUUID());
            context.sendMessage(Message.raw(result.success()
                    ?resolved.value().name()+" base skill level: "+loadouts.baseSkillRank(player.getUuid(),id)+"."
                    :"Skill level: "+result.code()+": "+result.message()));
        }catch(NumberFormatException invalid){context.sendMessage(Message.raw("Skill level must be 1..20."));}
        catch(RuntimeException error){context.sendMessage(Message.raw("Skill level: "+error.getMessage()));}
    }
}
