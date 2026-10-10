package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.*;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.difficulty.*;
import com.inigmasgames.hytalerpg.progress.RpgLoadoutService;
import java.util.*;
import java.util.concurrent.*;

/** Public inspection/return; placement and forced testing require explicit operator permissions. */
public final class RpgDifficultyCommand extends AbstractCommandCollection {
    public static final String AUTHOR_PERMISSION="inigmasgames.rpg.difficulty.author";
    public static final String FORCE_PERMISSION="inigmasgames.rpg.difficulty.force";
    public RpgDifficultyCommand(DifficultyRuntime runtime,RpgLoadoutService players,HytaleDifficultyWorlds worlds,
                                DifficultyTravel travel,DifficultyPortals portals,Executor io,HytaleDifficultyTravel nativeTravel) {
        this(runtime,players,worlds,travel,portals,io,nativeTravel,null);
    }
    public RpgDifficultyCommand(DifficultyRuntime runtime,RpgLoadoutService players,HytaleDifficultyWorlds worlds,
                                DifficultyTravel travel,DifficultyPortals portals,Executor io,HytaleDifficultyTravel nativeTravel,
                                com.inigmasgames.hytalerpg.execution.hytale.HytaleDifficultyCombat combat) {
        super("difficulty","Campaign golems, portals and world travel.");
        if(combat!=null&&!System.getProperty("rpg.difficulty.probeRoot","").isBlank())addSubCommand(new DifficultyNativeProbe(nativeTravel,combat,runtime.encounters()));
        addSubCommand(new AbstractPlayerCommand("inspect","Inspect frozen profiles of nearby authored monsters."){
            {setPermissionGroup(GameMode.Adventure);}
            @Override protected void execute(CommandContext c,Store<EntityStore> s,Ref<EntityStore> r,PlayerRef p,World w){
                if(combat==null){c.sendMessage(Message.raw("Combat profiles unavailable."));return;}
                var origin=s.getComponent(r,TransformComponent.getComponentType()).getPosition();int count=0;
                for(var spawn:combat.snapshots(p.getWorldUuid())){
                    var target=w.getEntityRef(spawn.enemy());if(target==null||!target.isValid())continue;
                    var transform=s.getComponent(target,TransformComponent.getComponentType());if(transform==null||transform.getPosition().distanceSquared(origin.x(),origin.y(),origin.z())>32*32)continue;
                    var profile=spawn.combat();c.sendMessage(Message.raw(spawn.roleId()+" / "+spawn.enemy()+": "+profile.difficulty()+" level "+spawn.level()+" "+spawn.rank()
                            +"; HP "+profile.maxHealth()+"; native attack x"+profile.difficultyDamageFactor()+"; resistance "+profile.resistance().resistance()
                            +"; IMMUNE "+profile.resistance().immunities()+"; source "+spawn.registryProfile()+"; evidence "+profile.evidence()));
                    if(++count==8)break;
                }
                if(count==0)c.sendMessage(Message.raw("No frozen authored combat profiles within 32 m. Historical or unauthored NPCs retain their native baseline."));
            }
        });
        addSubCommand(new AbstractPlayerCommand("status","Inspect world difficulty and all golem checklists.") {
            {setPermissionGroup(GameMode.Adventure);}
            @Override protected void execute(CommandContext c,Store<EntityStore> s,Ref<EntityStore> r,PlayerRef p,World w){try{
                var b=runtime.currentWorld(p.getWorldUuid());var progress=players.getPresentationView(p.getUuid()).state().difficulty;
                c.sendMessage(Message.raw("Difficulty: "+b.difficulty()+"; world: "+b.worldName()+" / "+b.worldId()+"; unlocked: "+progress.unlocks()
                        +"; recommended level: "+b.difficulty().recommendedLevel()+" (no level gate). Transfer pending: "+travel.busy(p.getUuid())));
                var catalog=GolemMilestones.load();for(var mode:DifficultyId.values())
                    c.sendMessage(Message.raw(mode+": "+String.join(", ",catalog.golems().stream().map(g->(progress.milestones().get(mode).contains(g.id())?"[done] ":"[missing] ")+g.label()).toList())));
            }catch(RuntimeException e){c.sendMessage(Message.raw("Difficulty unavailable: "+e.getMessage()));}}
        });
        addSubCommand(new AbstractPlayerCommand("return","Return to the initial Normal campaign spawn.") {
            {setPermissionGroup(GameMode.Adventure);}
            @Override protected void execute(CommandContext c,Store<EntityStore> s,Ref<EntityStore> r,PlayerRef p,World w){reply(c,travel.request(p.getUuid(),DifficultyId.NORMAL,false),"Returned to Normal.");}
        });
        addSubCommand(new AbstractPlayerCommand("unlock","Complete golem requirements and permanently unlock Nightmare or Hell.") {
            final RequiredArg<String> value=withRequiredArg("difficulty","Nightmare or Hell.",ArgTypes.STRING);
            {requirePermission(AUTHOR_PERMISSION);}
            @Override protected void execute(CommandContext c,Store<EntityStore> s,Ref<EntityStore> r,PlayerRef p,World w){try{
                var target=DifficultyOperatorUnlock.target(c.get(value));
                var view=players.getPresentationView(p.getUuid());
                var before=view.state().difficulty;
                var after=DifficultyOperatorUnlock.apply(before,target);
                if(after.equals(before)){c.sendMessage(Message.raw(target+" is already unlocked with its required golem milestones complete."));return;}
                var result=players.mutateProgress(p.getUuid(),view.state().revision,"difficulty-operator-unlock/"+UUID.randomUUID(),
                        state->state.difficulty=DifficultyOperatorUnlock.apply(state.difficulty,target));
                if(!result.success()){c.sendMessage(Message.raw("Difficulty unlock: "+result.message()));return;}
                com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log(
                        "RPG_DIFFICULTY_OPERATOR_UNLOCK actor=%s target=%s progressRevision=%s rewardsGranted=false",
                        p.getUuid(),target,after.revision());
                c.sendMessage(Message.raw(target+" unlocked. Required golem milestones are complete; no combat rewards granted."));
            }catch(RuntimeException e){c.sendMessage(Message.raw("Difficulty unlock: "+e.getMessage()));}}
        });
        addSubCommand(new AbstractAsyncCommand("prepare","Prepare persistent Nightmare and Hell worlds."){
            {requirePermission(AUTHOR_PERMISSION);}
            @Override protected CompletableFuture<Void> executeAsync(CommandContext c){
                CompletableFuture<Void> checks=CompletableFuture.completedFuture(null);
                for(var mode:DifficultyId.values())checks=checks.thenCompose(v->nativeTravel.prepare(new UUID(0,0),mode).thenAccept(d->
                        c.sendMessage(Message.raw("RPG_DIFFICULTY_SPAWN_CHECK mode="+mode+" world="+d.world()+" at="+d.x()+","+d.y()+","+d.z()+" connectedProof=false"))).toCompletableFuture());
                return checks.handle((h,e)->{c.sendMessage(Message.raw(e==null?"RPG_DIFFICULTY_PREPARED NIGHTMARE HELL persistent=true":HytaleDifficultyPortals.failure(e)));return null;});}
        });
        for(String action:List.of("portal","remove","force","encounter"))addSubCommand(new AbstractPlayerCommand(action,
                switch(action){case "portal"->"Place a persistent difficulty portal on clear ground ahead.";case "remove"->"Remove an authored portal by UUID.";case "force"->"Test travel without granting milestones or an unlock.";default->"Place an earned campaign golem encounter: earth/flame/frost/sand/thunder.";}){
            final RequiredArg<String> value=withRequiredArg("value","Difficulty, portal UUID, or golem element.",ArgTypes.STRING);
            {requirePermission(action.equals("force")?FORCE_PERMISSION:AUTHOR_PERMISSION);}
            @Override protected void execute(CommandContext c,Store<EntityStore> s,Ref<EntityStore> r,PlayerRef p,World w){try{
                String text=c.get(value);runtime.currentWorld(p.getWorldUuid());
                if(action.equals("remove")){UUID id=UUID.fromString(text);reply(c,CompletableFuture.runAsync(()->portals.remove(id),io),"Removed portal "+id);return;}
                if(action.equals("force")){
                    if(!Boolean.getBoolean("rpg.difficulty.testProfile"))throw new IllegalStateException("Forced travel requires the explicit server test profile (-Drpg.difficulty.testProfile=true).");
                    var mode=DifficultyId.valueOf(text.toUpperCase(Locale.ROOT));
                    com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log("RPG_DIFFICULTY_FORCED_TEST actor=%s source=%s target=%s permanentUnlock=false",p.getUuid(),p.getWorldUuid(),mode);
                    reply(c,travel.request(p.getUuid(),mode,true),"Forced test travel complete; no unlock granted.");return;
                }
                var t=s.getComponent(r,TransformComponent.getComponentType());double yaw=t.getRotation().yaw();var pos=t.getPosition();
                double distance=action.equals("encounter")?8:4;
                var at=HytaleDifficultyTravel.findSafe(w,pos.x()-Math.sin(yaw)*distance,pos.y(),pos.z()-Math.cos(yaw)*distance);
                if(action.equals("portal")){
                    var mode=DifficultyId.valueOf(text.toUpperCase(Locale.ROOT));var portal=new DifficultyPortals.Portal(UUID.randomUUID(),p.getUuid(),p.getWorldUuid(),mode,at.x(),at.y(),at.z());
                    reply(c,worlds.prepare(mode).thenRunAsync(()->portals.add(portal),io),"Placed "+portal.label()+". Portal ID: "+portal.id());return;
                }
                var golem=GolemMilestones.load().require(text.toLowerCase(Locale.ROOT));
                // Explicit campaign authoring, distinct from /npc spawn and forced travel. Native role stats are retained.
                var npc=com.hypixel.hytale.server.npc.NPCPlugin.get();npc.validateSpawnableRole(golem.roleId());UUID placement=UUID.randomUUID();
                // NPCPlugin receives no explicit Model, so its pre-add holder does not yet have ModelComponent.
                // The audited role Appearance IDs match these native model asset IDs; keep native NPC model selection.
                var modelAsset=com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset.getAssetMap().getAsset(golem.roleId());
                if(modelAsset==null)throw new IllegalStateException("NATIVE_GOLEM_ASSET_MISSING:"+golem.roleId());
                HytaleDifficultyTravel.requireClear(w,at,modelAsset.getBoundingBox());
                var result=npc.spawnEntity(s,npc.getIndex(golem.roleId()),at,t.getRotation(),null,
                        (entity,holder,store)->{
                            holder.addComponent(CampaignEncounterProjection.getComponentType(),new CampaignEncounterProjection(p.getWorldUuid(),placement,golem.roleId()));
                        },null);
                if(result==null)throw new IllegalStateException("CAMPAIGN_GOLEM_SPAWN_FAILED");
                c.sendMessage(Message.raw("Placed campaign encounter "+golem.label()+" in "+runtime.currentWorld(p.getWorldUuid()).difficulty()+"; placement "+placement+". Eligible contributors earn completion on native death."));
            }catch(RuntimeException e){c.sendMessage(Message.raw("Difficulty command: "+e.getMessage()));}}
        });
    }
    private static void reply(CommandContext c,CompletionStage<?> task,String success){task.whenComplete((v,e)->c.sendMessage(Message.raw(e==null?success:"Difficulty: "+HytaleDifficultyPortals.failure(e))));}
}
