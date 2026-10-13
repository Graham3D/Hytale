package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.inigmasgames.hytalerpg.diagnostics.CombatDamageTraceService;
import com.inigmasgames.hytalerpg.diagnostics.MonsterSpawnTrace;
import com.inigmasgames.hytalerpg.diagnostics.RpgSkillTraceService;
import com.inigmasgames.hytalerpg.diagnostics.SkillTraceLevel;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem;
import com.inigmasgames.hytalerpg.gear.GearQaTrace;
import com.inigmasgames.hytalerpg.spawning.NativeWorldSpawnDensity;
import com.inigmasgames.hytalerpg.ui.inventory.NativeInventoryDropTraceProbe;
import com.inigmasgames.hytalerpg.ui.inventory.TabTraceProbe;
import com.inigmasgames.hytalerpg.ui.trace.UiInteractionTrace;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** The single diagnostic command surface. Recorders retain their own bounded evidence and lifecycle. */
public final class RpgTraceCommand extends AbstractCommandCollection {
    public static final String PATCH_PERMISSION="inigmasgames.rpg.trace.patch";
    private static final String UI_PERMISSION="inigmasgames.rpg.uitrace";
    private static final String SPAWN_PERMISSION="inigmasgames.rpg.spawntrace";
    private static final String GEAR_PERMISSION=RpgGearCommand.AUTHOR_PERMISSION;
    @FunctionalInterface private interface PlayerAction {
        void run(CommandContext context,Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world);
    }
    private static final class Group extends AbstractCommandCollection {
        Group(String name,String description,String permission,boolean adventure){
            super(name,description);
            if(permission!=null){requirePermission(permission);setPermissionGroups();}
            else if(adventure)setPermissionGroup(GameMode.Adventure);
        }
    }
    private static AbstractPlayerCommand player(String name,String description,String permission,
                                                 boolean adventure,PlayerAction action){
        return new AbstractPlayerCommand(name,description){
            {if(permission!=null){requirePermission(permission);setPermissionGroups();}
             else if(adventure)setPermissionGroup(GameMode.Adventure);}
            @Override protected void execute(CommandContext c,Store<EntityStore> s,Ref<EntityStore> r,PlayerRef p,World w){
                action.run(c,s,r,p,w);
            }
        };
    }
    private static AbstractAsyncCommand async(String name,String description,String permission,Consumer<CommandContext> action){
        return new AbstractAsyncCommand(name,description){
            {requirePermission(permission);setPermissionGroups();}
            @Override protected CompletableFuture<Void> executeAsync(CommandContext context){
                action.accept(context);return CompletableFuture.completedFuture(null);
            }
        };
    }
    private static void say(CommandContext context,Object value){context.sendMessage(Message.raw(String.valueOf(value)));}

    public RpgTraceCommand(RpgSkillTraceService skill,CombatDamageTraceService combat,
                           UiInteractionTrace ui,GearQaTrace gear,GearQaTrace sentinel,HytaleSummonSystem summons,
                           MonsterSpawnTrace spawn,NativeWorldSpawnDensity density,
                           TabTraceProbe tab,NativeInventoryDropTraceProbe drop){
        super("rpg-trace","Bounded Hywind diagnostic capture and inspection.");
        var patch = new Group("patch","Cross-system HRO capture for the invoking player.",PATCH_PERMISSION,false);
        patch.addSubCommand(player("start","Start HRO for at most ten minutes.",PATCH_PERMISSION,false,(c,s,r,p,w)->
                say(c,com.inigmasgames.hytalerpg.diagnostics.hro.HroRuntime.start(p.getUuid(),p.getWorldUuid()))));
        patch.addSubCommand(player("stop","Stop admission; finalize evidence asynchronously.",PATCH_PERMISSION,false,(c,s,r,p,w)->
                say(c,com.inigmasgames.hytalerpg.diagnostics.hro.HroRuntime.stop())));
        patch.addSubCommand(player("status","Show recorder state, losses and output folder.",PATCH_PERMISSION,false,(c,s,r,p,w)->
                say(c,com.inigmasgames.hytalerpg.diagnostics.hro.HroRuntime.status())));
        patch.addSubCommand(player("mark","Record a checkpoint in the active HRO capture.",PATCH_PERMISSION,false,(c,s,r,p,w)->
                say(c,com.inigmasgames.hytalerpg.diagnostics.hro.HroRuntime.mark(p.getUuid()))));
        addSubCommand(patch);
        addSubCommand(player("status","Summarize every diagnostic category.",null,false,(c,s,r,p,w)->{
            UUID owner=p.getUuid();
            say(c,"Skill: "+skill.status(owner));say(c,"Combat: "+combat.status(owner)+"; file="+combat.path());
            for(UiInteractionTrace.Scope scope:UiInteractionTrace.Scope.values())say(c,scope+": "+ui.status(owner,scope));
            say(c,"Gear: "+gear.status(owner));say(c,"Sentinel: "+sentinel.status(owner));
            say(c,"Spawn: "+spawnStatus(spawn.status(),density));
            say(c,"Inventory Tab: "+tab.status(owner));say(c,"Inventory drop: "+drop.status(owner));
        }));
        addSubCommand(player("list","List operational and limited trace categories.",null,true,(c,s,r,p,w)->
                say(c,"skill="+(skill.available()?"OPERATIONAL":"UNAVAILABLE_CONFIG_DISABLED")
                        +" [admin]; combat=OPERATIONAL_WITH_NATIVE_GAPS [admin]; "
                        +"skilltree, inventory, ui=OPERATIONAL_FILTERED [inigmasgames.rpg.uitrace]; "
                        +"gear, sentinel=OPERATIONAL [inigmasgames.rpg.gear.author]; "
                        +"spawn=OPERATIONAL_GLOBAL [inigmasgames.rpg.spawntrace]; "
                        +"inventory-tab, inventory-drop=OPERATIONAL_ADAPTER_LIMITED [Adventure].")));
        addSubCommand(player("help","Show diagnostic syntax.",null,true,(c,s,r,p,w)->
                say(c,"/rpg-trace <patch|skill|combat|skilltree|inventory|ui|gear|sentinel|spawn|inventory-tab|inventory-drop> "
                        +"<start|stop|status> [arguments]; /rpg-trace status|list|help. "
                        +"Skill start supports detailed/performance; ui/gear support mark; sentinel supports snapshot; "
                        +"combat supports last [count] and inspect.")));
        addSkill(skill);addCombat(combat);
        addUi(ui,"ui",UiInteractionTrace.Scope.UI);
        addUi(ui,"inventory",UiInteractionTrace.Scope.INVENTORY);
        addUi(ui,"skilltree",UiInteractionTrace.Scope.SKILLTREE);
        addGear(gear);addSentinel(sentinel,summons);addSpawn(spawn,density);
        addOneShot("inventory-tab",tab,drop,true);addOneShot("inventory-drop",tab,drop,false);
    }
    private void addSkill(RpgSkillTraceService trace){
        var group=new Group("skill","Personal skill capture; background global telemetry is separately reported.",null,false);
        var start=player("start","Start a 60-second NORMAL personal capture.",null,false,(c,s,r,p,w)->
                say(c,trace.start(p.getUuid(),p.getWorldUuid(),SkillTraceLevel.NORMAL)));
        start.addSubCommand(player("detailed","Capture compile stages and skill details.",null,false,(c,s,r,p,w)->
                say(c,trace.start(p.getUuid(),p.getWorldUuid(),SkillTraceLevel.DETAILED))));
        start.addSubCommand(player("performance","Capture raw native tick samples for this world.",null,false,(c,s,r,p,w)->
                say(c,trace.start(p.getUuid(),p.getWorldUuid(),SkillTraceLevel.PERFORMANCE))));
        group.addSubCommand(start);
        group.addSubCommand(player("stop","Stop and flush the personal skill capture.",null,false,(c,s,r,p,w)->say(c,trace.stop(p.getUuid()))));
        group.addSubCommand(player("status","Show personal and background skill capture state.",null,false,(c,s,r,p,w)->say(c,trace.status(p.getUuid()))));
        addSubCommand(group);
    }
    private void addCombat(CombatDamageTraceService combat){
        var group=new Group("combat","Personal authoritative damage-boundary receipts.",null,false);
        group.addSubCommand(player("start","Capture incoming and outgoing hits for 60 seconds.",null,false,(c,s,r,p,w)->{
            try{say(c,"Combat trace started: "+combat.start(p.getUuid(),p.getWorldUuid())+
                    " (60 seconds, 2048 events maximum); file="+combat.path());}
            catch(IllegalStateException capacity){say(c,"Combat trace unavailable: "+capacity.getMessage());}
        }));
        group.addSubCommand(player("stop","Stop personal combat capture.",null,false,(c,s,r,p,w)->
                say(c,"Combat trace stopped: "+combat.stop(p.getUuid())+"; file="+combat.path()+"; queued writes drain asynchronously")));
        group.addSubCommand(player("status","Show capture and completeness counters.",null,false,(c,s,r,p,w)->
                say(c,"Combat trace: "+combat.status(p.getUuid())+"; file="+combat.path())));
        group.addSubCommand(new AbstractPlayerCommand("last","Show recent hit formulas and Health receipts."){
            final OptionalArg<Integer> count=withOptionalArg("count","1 through 32",ArgTypes.INTEGER);
            @Override protected void execute(CommandContext c,Store<EntityStore> s,Ref<EntityStore> r,PlayerRef p,World w){
                try{var hits=combat.last(p.getUuid(),c.provided(count)?c.get(count):1);
                    if(hits.isEmpty()){say(c,"No combat hits captured in this session.");return;}
                    for(var hit:hits){say(c,"#"+hit.sequence()+" "+hit.direction()+" "+hit.cause()+
                            " "+hit.sourceId()+" -> "+hit.targetId()+" attempted="+hit.attempted()+
                            " accepted="+hit.accepted()+" postAbsorption="+hit.postAbsorption()+
                            " HP "+hit.healthBefore()+" -> "+hit.healthAfter()+
                            " lost="+hit.healthLost()+" overkill="+hit.overkill()+" reconciliation="+hit.reconciliation()+
                            " cancelled="+hit.cancelled());
                        for(var stage:hit.stages())say(c,"  "+stage.owner()+" "+stage.operation()+
                                " "+stage.input()+" -> "+stage.output()+" "+stage.details());
                        if(!hit.unobservable().isEmpty())say(c,"  UNOBSERVABLE: "+hit.unobservable());
                    }
                }catch(IllegalArgumentException invalid){say(c,"Count must be 1 through 32.");}
            }
        });
        group.addSubCommand(player("inspect","Compare a targeted NPC's profile with live Health.",null,false,(c,s,r,p,w)->{
            var target=TargetUtil.getTargetEntity(r,24.0f,s);
            if(target==null||!target.isValid()||target.getStore()!=s){say(c,"No loaded NPC targeted within 24 m.");return;}
            var npc=s.getComponent(target,NPCEntity.getComponentType());var id=s.getComponent(target,UUIDComponent.getComponentType());
            if(npc==null||id==null){say(c,"Target is not an identified NPC.");return;}
            var stats=s.getComponent(target,EntityStatMap.getComponentType());var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
            var profile=combat.profile(p.getWorldUuid(),id.getUuid(),npc.getRoleName());
            String outcome;
            if(!"APPLIED".equals(profile.get("profileOutcome")))outcome=
                    com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards.excluded(s,target)
                            ?"INAPPLICABLE_EXCLUDED":"UNAPPLIED_OR_UNSUPPORTED_SPAWN";
            else {double expected=(double)profile.get("expectedMaximumHealth");
                if(profile.containsKey("rarityHealthFactor"))expected*=((double)profile.get("rarityHealthFactor"))
                        *(1+(double)profile.get("maxHealthIncrease"));
                outcome=hp==null?"APPLIED_HEALTH_UNOBSERVABLE":Math.abs(hp.getMax()-expected)>.02
                        ?"APPLIED_BUT_MAXIMUM_DIVERGED":"APPLIED_AND_MAXIMUM_MATCHES";
            }
            say(c,"Combat profile outcome="+outcome+" "+profile+
                    " actualHealth="+(hp==null?"UNOBSERVABLE":hp.get())+" actualMaximum="+(hp==null?"UNOBSERVABLE":hp.getMax())+
                    " nativeEnvironment="+npc.getEnvironment()+" nativeSpawnConfiguration="+npc.getSpawnConfiguration());
        }));
        addSubCommand(group);
    }
    private void addUi(UiInteractionTrace trace,String name,UiInteractionTrace.Scope scope){
        var group=new Group(name,"Filtered personal CanvasUI interaction capture.",UI_PERMISSION,false);
        group.addSubCommand(player("start","Start a bounded ten-minute capture.",UI_PERMISSION,false,(c,s,r,p,w)->
                say(c,trace.on(p.getUuid(),scope))));
        group.addSubCommand(player("stop","Stop and save this category only.",UI_PERMISSION,false,(c,s,r,p,w)->
                say(c,trace.off(p.getUuid(),scope))));
        group.addSubCommand(player("status","Show capture, file, count and drops.",UI_PERMISSION,false,(c,s,r,p,w)->
                say(c,trace.status(p.getUuid(),scope))));
        if(scope==UiInteractionTrace.Scope.UI)group.addSubCommand(new AbstractPlayerCommand("mark","Mark active UI evidence."){
            final OptionalArg<String> label=withOptionalArg("label","Optional QA label",ArgTypes.GREEDY_STRING);
            {requirePermission(UI_PERMISSION);setPermissionGroups();}
            @Override protected void execute(CommandContext c,Store<EntityStore> s,Ref<EntityStore> r,PlayerRef p,World w){
                say(c,trace.mark(p.getUuid(),scope,c.provided(label)?c.get(label):""));
            }
        });
        addSubCommand(group);
    }
    private void addGear(GearQaTrace trace){
        var group=new Group("gear","Bounded equipment, affix and combat evidence.",GEAR_PERMISSION,false);
        group.addSubCommand(player("start","Start personal gear capture.",GEAR_PERMISSION,false,(c,s,r,p,w)->say(c,trace.on(p.getUuid()))));
        group.addSubCommand(player("stop","Stop and save gear capture.",GEAR_PERMISSION,false,(c,s,r,p,w)->say(c,trace.off(p.getUuid()))));
        group.addSubCommand(player("status","Show gear evidence state.",GEAR_PERMISSION,false,(c,s,r,p,w)->say(c,trace.status(p.getUuid()))));
        group.addSubCommand(new AbstractPlayerCommand("mark","Mark active gear evidence."){
            final OptionalArg<String> label=withOptionalArg("label","Optional QA label",ArgTypes.GREEDY_STRING);
            {requirePermission(GEAR_PERMISSION);setPermissionGroups();}
            @Override protected void execute(CommandContext c,Store<EntityStore> s,Ref<EntityStore> r,PlayerRef p,World w){
                say(c,trace.mark(p.getUuid(),c.provided(label)?c.get(label):""));
            }
        });addSubCommand(group);
    }
    private void addSentinel(GearQaTrace trace,HytaleSummonSystem summons){
        var group=new Group("sentinel","Separate Iron Sentinel inheritance and combat evidence.",GEAR_PERMISSION,false);
        group.addSubCommand(player("start","Start Sentinel capture.",GEAR_PERMISSION,false,(c,s,r,p,w)->say(c,trace.on(p.getUuid()))));
        group.addSubCommand(player("stop","Stop and save Sentinel capture.",GEAR_PERMISSION,false,(c,s,r,p,w)->say(c,trace.off(p.getUuid()))));
        group.addSubCommand(player("status","Show Sentinel evidence state.",GEAR_PERMISSION,false,(c,s,r,p,w)->say(c,trace.status(p.getUuid()))));
        group.addSubCommand(player("snapshot","Record the owned Sentinel's live inherited state.",GEAR_PERMISSION,false,(c,s,r,p,w)->{
            try{say(c,trace.snapshot(p.getUuid(),summons.qaSnapshot(s,p.getUuid())));}
            catch(RuntimeException error){say(c,"Sentinel snapshot unavailable: "+error.getMessage());}
        }));addSubCommand(group);
    }
    private void addSpawn(MonsterSpawnTrace trace,NativeWorldSpawnDensity density){
        var group=new Group("spawn","Global 120-second native spawn and Elite admission capture.",SPAWN_PERMISSION,false);
        group.addSubCommand(async("start","Start the global spawn capture.",SPAWN_PERMISSION,c->{
            try{trace.start();MonsterSpawnTrace.event("CONFIG",null,-1,"all","nativeDensityMultiplier="+density.multiplier());
                say(c,spawnStatus(trace.status(),density));}
            catch(IllegalStateException error){say(c,error.getMessage());}
        }));
        group.addSubCommand(async("stop","Stop and save spawn capture.",SPAWN_PERMISSION,c->say(c,spawnStatus(trace.stop(),density))));
        group.addSubCommand(async("status","Show global capture and saved file.",SPAWN_PERMISSION,c->say(c,spawnStatus(trace.status(),density))));
        addSubCommand(group);
    }
    private static String spawnStatus(MonsterSpawnTrace.Status state,NativeWorldSpawnDensity density){
        return "Spawn trace "+(state.active()?"running ("+state.secondsRemaining()+"s left)":"stopped")
                +"; events="+state.events()+"; detailed="+state.detailedEvents()+"/"+MonsterSpawnTrace.MAX_DETAILS
                +"; density="+density.multiplier()+"x; file="+state.lastFile()+"; error="+state.lastError();
    }
    private void addOneShot(String name,TabTraceProbe tab,NativeInventoryDropTraceProbe drop,boolean isTab){
        var group=new Group(name,isTab?"Two-second native Inventory-action probe.":"Ten-second native drop probe.",null,true);
        group.addSubCommand(player("start","Arm the one-shot capture.",null,true,(c,s,r,p,w)->
                say(c,isTab?tab.arm(p,r,s,w):drop.arm(p,w))));
        group.addSubCommand(player("stop","Cancel and save collected evidence.",null,true,(c,s,r,p,w)->
                say(c,isTab?tab.cancel(p.getUuid()):drop.cancel(p.getUuid()))));
        group.addSubCommand(player("status","Show active or saved evidence.",null,true,(c,s,r,p,w)->
                say(c,isTab?tab.status(p.getUuid()):drop.status(p.getUuid()))));
        addSubCommand(group);
    }
}
