package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.inigmasgames.hytalerpg.spawning.*;
import com.inigmasgames.hytalerpg.enemies.EnemyWorldAdmission;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Operator-only validation/reload and read-only native population diagnostics. */
public final class RpgWorldConfigCommand extends AbstractAsyncCommand {
    private final HywindWorldConfiguration config;
    private final NativeWorldSpawnDensity density;
    private final NativePopulationBalance population;
    private final EnemyWorldAdmission packs;
    private final RequiredArg<String> action;
    public RpgWorldConfigCommand(HywindWorldConfiguration config,NativeWorldSpawnDensity density,
                                 NativePopulationBalance population,EnemyWorldAdmission packs){
        super("worldconfig","Reload or inspect the RPG save's world-config.json.");
        this.config=config;this.density=density;this.population=population;this.packs=packs;
        action=withRequiredArg("reload|status","Reload settings or show effective settings",ArgTypes.STRING);
        requirePermission("inigmasgames.rpg.worldconfig");
    }
    @Override protected CompletableFuture<Void> executeAsync(CommandContext context){
        String input=context.get(action).trim().toLowerCase(Locale.ROOT);
        if(input.equals("reload")){
            try{
                var active=config.reload();
                return density.applyLoaded().handle((ignored,error)->{
                    if(error!=null)context.sendMessage(Message.raw("World configuration loaded; native density projection will retry: "+error.getMessage()));
                    else context.sendMessage(Message.raw("World configuration loaded: "+active.configRevision()+
                            "; new encounters use "+active.enemyBalance().revision()+"; density "+active.density()+"x."));
                    return null;
                });
            }catch(RuntimeException error){
                context.sendMessage(Message.raw("World configuration rejected; previous settings remain active: "+error.getMessage()));
                return CompletableFuture.completedFuture(null);
            }
        }
        if(input.equals("status")){
            var active=config.snapshot();
            context.sendMessage(Message.raw(String.format(Locale.ROOT,
                    "World config %s: density %.2fx, population balance %s (hostile %.0f%% / wildlife %.0f%%); new-encounter balance %s; file %s",
                    active.configRevision(),active.density(),active.population().enabled()?"on":"off",
                    active.population().hostileShare()*100,active.population().wildlifeShare()*100,
                    active.enemyBalance().revision(),config.path())));
            for(var world:com.hypixel.hytale.server.core.universe.Universe.get().getWorlds().values()){
                var packStatus=packs.status(world.getWorldConfig().getUuid());var leases=packStatus.leases();
                context.sendMessage(Message.raw(world.getName()+" Elite admission: "+(packStatus.admissionOpen()?"open":"closed")
                        +"; pending birth transactions "+packStatus.pendingTransactions()+"; oldest "+packStatus.oldestPendingMillis()+" ms."));
                context.sendMessage(Message.raw(String.format(Locale.ROOT,
                        "%s (%s) Elite packs: active %d, pending %d, dormant %d, historical QA %d, limit %d, cell limit %d, over-cap %d; cells %s; denied world %d / cell %d",
                        world.getName(),world.getWorldConfig().getUuid(),leases.loadedActiveProductionPacks(),leases.pendingNewBirths(),
                        packStatus.dormantNonterminalProductionPacks(),packStatus.qaExcludedRecords(),
                        leases.configuredActiveLimit(),leases.configuredCellLimit(),leases.grandfatheredOverCapPacks(),
                        leases.activeBy64mCell(),leases.newBirthRejectionsWorldCap(),leases.newBirthRejectionsCellCap())));
                var observations=population.snapshot(world.getWorldConfig().getUuid());
                for(var row:observations.values())context.sendMessage(Message.raw(String.format(Locale.ROOT,
                        "%s environment %d: native %d/%.1f (headroom %.1f), hostile %d, wildlife %d, birds %d, other %d; expected hostile %.1f / wildlife %.1f; %s%s",
                        world.getName(),row.environment(),row.nativeActual(),row.nativeExpected(),
                        Math.max(0,row.nativeExpected()-row.nativeActual()),row.hostileActual(),row.wildlifeActual(),
                        row.avianActual(),row.otherActual(),row.targetHostile(),row.targetWildlife(),row.reason(),
                        row.unknownRoles()>0?"; unclassified="+row.unknownRoles():"")));
            }
            return CompletableFuture.completedFuture(null);
        }
        context.sendMessage(Message.raw("Use /rpg worldconfig reload or /rpg worldconfig status."));
        return CompletableFuture.completedFuture(null);
    }
}
