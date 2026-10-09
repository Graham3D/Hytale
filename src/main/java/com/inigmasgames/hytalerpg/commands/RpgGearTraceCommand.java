package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.gear.GearQaTrace;

/** Per-player gear diagnostics; same author gate as protected gear fixtures. */
public final class RpgGearTraceCommand extends AbstractCommandCollection {
    private record LightProbe(java.util.UUID token,java.util.UUID world,
                              com.hypixel.hytale.protocol.ColorLight before,
                              com.hypixel.hytale.protocol.ColorLight applied,boolean created) {}
    private final java.util.Map<java.util.UUID,LightProbe> lightProbes=new java.util.concurrent.ConcurrentHashMap<>();
    public RpgGearTraceCommand(GearQaTrace trace){
        super("geartrace","Bounded enemy gear, combat, stat and Sentinel QA trace.");
        requirePermission(RpgGearCommand.AUTHOR_PERMISSION);
        addSubCommand(new AbstractPlayerCommand("on","Start a gear trace."){
            @Override protected void execute(CommandContext c,Store<EntityStore> s,Ref<EntityStore> r,PlayerRef p,World w){c.sendMessage(Message.raw(trace.on(p.getUuid())));}
        });
        addSubCommand(new AbstractPlayerCommand("off","Stop and save the gear trace."){
            @Override protected void execute(CommandContext c,Store<EntityStore> s,Ref<EntityStore> r,PlayerRef p,World w){c.sendMessage(Message.raw(trace.off(p.getUuid())));}
        });
        addSubCommand(new AbstractPlayerCommand("status","Show gear trace state."){
            @Override protected void execute(CommandContext c,Store<EntityStore> s,Ref<EntityStore> r,PlayerRef p,World w){c.sendMessage(Message.raw(trace.status(p.getUuid())));}
        });
        addSubCommand(new AbstractPlayerCommand("light","Show WA-155 target metres and native radius units."){
            @Override protected void execute(CommandContext c,Store<EntityStore> s,Ref<EntityStore> r,PlayerRef p,World w){
                var type=com.inigmasgames.hytalerpg.gear.NativeAffixLightSystem.stateType();
                var state=type==null?null:s.getComponent(r,type);
                var nativeLight=s.getComponent(r,com.hypixel.hytale.server.core.modules.entity.component.DynamicLight.getComponentType());
                int nativeUnits=nativeLight==null||nativeLight.getColorLight()==null?0:
                        Byte.toUnsignedInt(nativeLight.getColorLight().radius);
                c.sendMessage(Message.raw("WA-155 targetMetres="+(state==null?"unavailable":state.targetMetres())+
                        " effectiveNativeUnits="+(state==null?"unavailable":state.effectiveNativeRadius())+
                        " actualPacketUnits="+nativeUnits+" rendererMetres=UNVERIFIED"));
            }
        });
        addSubCommand(new AbstractPlayerCommand("lightprobe","Temporarily set native actor light for renderer calibration; zero restores."){
            final com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg<Integer> radius=
                    withRequiredArg("radius","0 restores; 1..127 probes signed-byte native units",ArgTypes.INTEGER);
            @Override protected void execute(CommandContext c,Store<EntityStore> s,Ref<EntityStore> r,PlayerRef p,World w){
                int units=c.get(radius);var owner=p.getUuid();
                if(units<0||units>127){c.sendMessage(Message.raw("Native light probe requires 0..127 units"));return;}
                if(units==0){restoreLightProbe(owner,s,r,w);c.sendMessage(Message.raw("Native light probe restored if still owned"));return;}
                var stateType=com.inigmasgames.hytalerpg.gear.NativeAffixLightSystem.stateType();
                if(stateType!=null&&s.getComponent(r,stateType)!=null){
                    c.sendMessage(Message.raw("Remove WA-155 equipment before measuring native baseline"));return;
                }
                var type=com.hypixel.hytale.server.core.modules.entity.component.DynamicLight.getComponentType();
                var current=s.getComponent(r,type);var previous=lightProbes.get(owner);
                if(previous!=null&&!previous.world().equals(w.getWorldConfig().getUuid())){
                    lightProbes.remove(owner);previous=null;
                }
                if(previous!=null&&(current==null||!previous.applied().equals(current.getColorLight()))){
                    lightProbes.remove(owner);
                    c.sendMessage(Message.raw("Native light changed outside the probe; previous light was preserved"));return;
                }
                if(current!=null&&current.getColorLight()==null){
                    c.sendMessage(Message.raw("Native light baseline unavailable"));return;
                }
                boolean created=previous==null?current==null:previous.created();
                var before=previous==null?(current==null?null:new com.hypixel.hytale.protocol.ColorLight(current.getColorLight())):previous.before();
                var applied=new com.hypixel.hytale.protocol.ColorLight((byte)units,(byte)255,(byte)255,(byte)255);
                if(current==null)s.putComponent(r,type,new com.hypixel.hytale.server.core.modules.entity.component.DynamicLight(applied));
                else current.setColorLight(applied);
                var probe=new LightProbe(java.util.UUID.randomUUID(),w.getWorldConfig().getUuid(),before,applied,created);
                lightProbes.put(owner,probe);
                java.util.concurrent.CompletableFuture.delayedExecutor(30,java.util.concurrent.TimeUnit.SECONDS)
                        .execute(()->w.execute(()->{
                            if(lightProbes.get(owner)==probe){
                                if(r.isValid())restoreLightProbe(owner,s,r,w);
                                else lightProbes.remove(owner,probe);
                            }
                        }));
                c.sendMessage(Message.raw("Native actor light probe="+units+" units for 30 s; use /rpg geartrace lightprobe 0 to restore"));
            }
        });
        addSubCommand(new AbstractPlayerCommand("lightcal","Fit three measured native radius/metre pairs and a verified ceiling."){
            final com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg<String> measurements=
                    withRequiredArg("samples","r1:m1,r2:m2,r3:m3,ceiling,toleranceMetres",ArgTypes.STRING);
            @Override protected void execute(CommandContext c,Store<EntityStore> s,Ref<EntityStore> r,PlayerRef p,World w){
                try{
                    var parts=c.get(measurements).split(",",-1);
                    if(parts.length!=5)throw new IllegalArgumentException("Use r1:m1,r2:m2,r3:m3,ceiling,toleranceMetres");
                    var a=observation(parts[0]);var b=observation(parts[1]);var d=observation(parts[2]);
                    var result=com.inigmasgames.hytalerpg.gear.NativeAffixLightProjection.fit(a,b,d,
                            Integer.parseInt(parts[3]),Double.parseDouble(parts[4]));
                    c.sendMessage(Message.raw("WA-155 measured metresPerNativeUnit="+result.metresPerUnit()+
                            " maximumNativeRadius="+result.maximumNativeRadius()+
                            "; confirm renderer ceiling separately at requested 15, 16 and 18 units."));
                }catch(RuntimeException invalid){c.sendMessage(Message.raw("Light calibration rejected: "+invalid.getMessage()));}
            }
            private com.inigmasgames.hytalerpg.gear.NativeAffixLightProjection.Observation observation(String token){
                var parts=token.split(":",-1);if(parts.length!=2)throw new IllegalArgumentException("Expected radius:metres");
                return new com.inigmasgames.hytalerpg.gear.NativeAffixLightProjection.Observation(
                        Integer.parseInt(parts[0]),Double.parseDouble(parts[1]));
            }
        });
        addSubCommand(new AbstractPlayerCommand("mark","Mark an action in the gear trace."){
            final OptionalArg<String> label=withOptionalArg("label","Optional short label",ArgTypes.GREEDY_STRING);
            @Override protected void execute(CommandContext c,Store<EntityStore> s,Ref<EntityStore> r,PlayerRef p,World w){
                c.sendMessage(Message.raw(trace.mark(p.getUuid(),c.provided(label)?c.get(label):"")));}
        });
    }
    private void restoreLightProbe(java.util.UUID owner,Store<EntityStore> store,Ref<EntityStore> actor,World world){
        var probe=lightProbes.remove(owner);if(probe==null||!probe.world().equals(world.getWorldConfig().getUuid()))return;
        var type=com.hypixel.hytale.server.core.modules.entity.component.DynamicLight.getComponentType();
        var current=store.getComponent(actor,type);
        if(current==null||!probe.applied().equals(current.getColorLight()))return;
        if(probe.created())store.removeComponent(actor,type);
        else current.setColorLight(probe.before());
    }
}
