package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.diagnostics.*;

/** Admin-only session diagnostic switch; no save mutation, gameplay or native input changes. */
public final class RpgTraceCommand extends AbstractPlayerCommand {
    private final RpgSkillTraceService trace;
    private final RequiredArg<String> mode;
    public RpgTraceCommand(RpgSkillTraceService trace){
        this(trace,null);
    }
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value="EI_EXPOSE_REP2",
            justification="The admin command intentionally shares the plugin-owned trace services used by native callbacks.")
    public RpgTraceCommand(RpgSkillTraceService trace,CombatDamageTraceService combat){
        super("rpg-trace","Set NORMAL, DETAILED or PERFORMANCE skill telemetry; STATUS inspects current mode.");
        this.trace=trace;mode=withRequiredArg("mode","normal, detailed, performance or status",ArgTypes.STRING);
        // Deliberately keep the native explicit/admin command permission.
        if(combat!=null)addSubCommand(new com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection(
                "combat","Opt-in personal native combat damage diagnostics."){{
            addSubCommand(new AbstractPlayerCommand("start","Capture incoming and outgoing damage for 60 seconds."){
                @Override protected void execute(CommandContext c,Store<EntityStore> s,Ref<EntityStore> r,PlayerRef p,World w){
                    try{c.sendMessage(Message.raw("Combat trace started: "+combat.start(p.getUuid(),p.getWorldUuid())+
                            " (60 seconds, 2048 events maximum)."));}
                    catch(IllegalStateException capacity){c.sendMessage(Message.raw(
                            "Combat trace unavailable: 16 active player sessions are already recording."));}
                }
            });
            addSubCommand(new AbstractPlayerCommand("stop","Stop personal combat capture."){
                @Override protected void execute(CommandContext c,Store<EntityStore> s,Ref<EntityStore> r,PlayerRef p,World w){
                    c.sendMessage(Message.raw("Combat trace stopped: "+combat.stop(p.getUuid())));
                }
            });
            addSubCommand(new AbstractPlayerCommand("status","Show capture, queue and completeness counters."){
                @Override protected void execute(CommandContext c,Store<EntityStore> s,Ref<EntityStore> r,PlayerRef p,World w){
                    c.sendMessage(Message.raw("Combat trace: "+combat.status(p.getUuid())));
                }
            });
            addSubCommand(new AbstractPlayerCommand("last","Show recent per-hit equations and Health receipts."){
                final OptionalArg<Integer> count=withOptionalArg("count","1 through 32",ArgTypes.INTEGER);
                @Override protected void execute(CommandContext c,Store<EntityStore> s,Ref<EntityStore> r,PlayerRef p,World w){
                    try{var hits=combat.last(p.getUuid(),c.provided(count)?c.get(count):1);
                        if(hits.isEmpty()){c.sendMessage(Message.raw("No combat hits captured in this session."));return;}
                        for(var hit:hits){c.sendMessage(Message.raw("#"+hit.sequence()+" "+hit.direction()+" "+hit.cause()+
                                " "+hit.sourceId()+" -> "+hit.targetId()+" attempted="+hit.attempted()+
                                " accepted="+hit.accepted()+" postAbsorption="+hit.postAbsorption()+
                                " HP "+hit.healthBefore()+" -> "+hit.healthAfter()+
                                " lost="+hit.healthLost()+" overkill="+hit.overkill()+" reconciliation="+hit.reconciliation()+
                                " cancelled="+hit.cancelled()));
                            for(var stage:hit.stages())c.sendMessage(Message.raw("  "+stage.owner()+" "+stage.operation()+
                                    " "+stage.input()+" -> "+stage.output()+" "+stage.details()));
                            if(!hit.unobservable().isEmpty())c.sendMessage(Message.raw("  UNOBSERVABLE: "+hit.unobservable()));
                        }
                    }catch(IllegalArgumentException invalid){c.sendMessage(Message.raw("Count must be 1 through 32."));}
                }
            });
            addSubCommand(new AbstractPlayerCommand("inspect","Inspect the targeted nearby NPC's native and RPG Health profile."){
                @Override protected void execute(CommandContext c,Store<EntityStore> s,Ref<EntityStore> r,PlayerRef p,World w){
                    var target=TargetUtil.getTargetEntity(r,24.0f,s);
                    if(target==null||!target.isValid()||target.getStore()!=s){c.sendMessage(Message.raw("No loaded NPC targeted within 24 m."));return;}
                    var npc=s.getComponent(target,NPCEntity.getComponentType());var id=s.getComponent(target,UUIDComponent.getComponentType());
                    if(npc==null||id==null){c.sendMessage(Message.raw("Target is not an identified NPC."));return;}
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
                    c.sendMessage(Message.raw("Combat profile outcome="+outcome+" "+profile+
                            " actualHealth="+(hp==null?"UNOBSERVABLE":hp.get())+" actualMaximum="+(hp==null?"UNOBSERVABLE":hp.getMax())+
                            " nativeEnvironment="+npc.getEnvironment()+" nativeSpawnConfiguration="+npc.getSpawnConfiguration()));
                }
            });
        }});
    }
    @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> ref,PlayerRef player,World world){
        String requested=context.get(mode);
        try{if(!requested.equalsIgnoreCase("status"))trace.setLevel(SkillTraceLevel.parse(requested));
            context.sendMessage(Message.raw("RPG trace="+trace.level()+" metrics="+trace.metrics()+". PERFORMANCE raw ticks required for percentile qualification; TRACE_GAP invalidates completeness."));
        }catch(IllegalArgumentException|IllegalStateException error){context.sendMessage(Message.raw("Trace mode unavailable. Use normal, detailed, performance or status. No gameplay changed."));}
    }
}
