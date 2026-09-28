package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.hytale.DerivedStatEntityAdapter;
import com.inigmasgames.hytalerpg.progress.RpgLoadoutService;
import com.inigmasgames.hytalerpg.ui.HytaleResourceViewAdapter;
import com.inigmasgames.hytalerpg.ui.RpgUiProjectionService;
import com.inigmasgames.hytalerpg.ui.hud.RpgHudCoordinator;

import java.util.UUID;

/** Operator-only, self-targeted level fixture and attribute reset. */
public final class RpgLevelCommand extends AbstractPlayerCommand {
    public static final String PERMISSION = "inigmasgames.rpg.level";
    private final RequiredArg<String> value;
    private final RpgLoadoutService levels;
    private final RpgUiProjectionService projection;
    private final RpgHudCoordinator hud;

    public RpgLevelCommand(RpgLoadoutService levels,RpgUiProjectionService projection,RpgHudCoordinator hud){
        super("level","Operator: set your RPG level (1..99) or reset allocated attributes.");
        this.levels=levels;this.projection=projection;this.hud=hud;
        value=withRequiredArg("value","1..99 or reset",ArgTypes.STRING);
        requirePermission(PERMISSION);
    }

    @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> actor,
                                     PlayerRef player,World world){
        UUID owner=player.getUuid();String input=context.get(value);String correlation=UUID.randomUUID().toString();
        try{
            var result=input.equalsIgnoreCase("reset")
                    ?levels.respecAttributes(owner,levels.getPresentationView(owner).state().revision,correlation)
                    :levels.setOperatorLevel(owner,Integer.parseInt(input),correlation);
            if(!result.success()){
                context.sendMessage(Message.raw("Level: "+result.code()+": "+result.message()));return;
            }
            hud.setXpFixture(owner,null);
            var stats=store.getComponent(actor,EntityStatMap.getComponentType());
            if(stats!=null){var view=projection.character(owner,player.getUsername(),new HytaleResourceViewAdapter().read(stats));
                new DerivedStatEntityAdapter().apply(stats,view.derivedStats());}
            var state=levels.getPresentationView(owner).state();
            context.sendMessage(Message.raw((input.equalsIgnoreCase("reset")?"Attributes reset.":"Level set to "+state.level+".")
                    +" Unspent attribute points: "+state.unspentAttributePoints
                    +"; pending level-up points: "+state.pendingLevelUpPoints
                    +". Open /rpg character to allocate them."));
        }catch(NumberFormatException invalid){context.sendMessage(Message.raw("Level must be 1..99, or use /rpg level reset."));}
        catch(RuntimeException error){context.sendMessage(Message.raw("Level: "+error.getMessage()));}
    }
}
